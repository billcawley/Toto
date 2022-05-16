package com.azquo.spreadsheet.zk;

import com.azquo.ExternalConnector;
import com.azquo.StringLiterals;
import com.azquo.StringUtils;
import com.azquo.admin.onlinereport.ExternalDataRequest;
import com.azquo.admin.onlinereport.ExternalDataRequestDAO;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import io.keikai.api.CellOperationUtil;
import io.keikai.api.Range;
import io.keikai.api.Ranges;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.api.model.Validation;
import io.keikai.model.CellRegion;
import io.keikai.model.SCell;
import io.keikai.model.SName;
import io.keikai.model.SSheet;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * Created by edward on 10/01/17.
 * <p>
 * Bigger state changing functions related to the choice dropdown and multi selects.
 */
public class ChoicesService {

    /*
    CONTENTS is used to handle dependent ranges within the data region.   Thus one column in the data region may ask for a category, and the next a subcategory, which should be determined by the category
    For single cell 'chosen' ranges there is no problem - the choice for the subcategory may be defined as an Excel formula.
    For multicell ranges, use 'CONTENTS(rangechosen) to specify the subcategory....

    Not to do with pivot tables, initially for the expenses sheet, let us say that the first column is to select a project and then the second to select something based off that, you'd use contents. Not used that often.
     */
    private static final String CONTENTS = "contents(";

    public static final String VALIDATION_SHEET = "VALIDATION_SHEET";

    // now adds one validation sheet per sheet so to speak - validation in Excel terms, putting validation on a cell adds a dropdown to it
    static List<SName> addValidation(Sheet sheet, LoggedInUser loggedInUser, Book book, Map<String, List<String>> choiceOptionsMap) {
        String sheetName = sheet.getSheetName().replace(" ", "");
        // trim the sheet name as it can't be longer than 31 chars when appended to VALIDATION_SHEET
        // should I be replacing spaces commas etc?
        if (sheetName.length() > 10) {
            sheetName = sheetName.substring(0, 10);
        }
        String validationSheetName = sheetName + VALIDATION_SHEET;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        if (book.getSheet(validationSheetName) == null) {
            book.getInternalBook().createSheet(validationSheetName);
        }
        Sheet validationSheet = book.getSheet(validationSheetName);
        validationSheet.getInternalSheet().setSheetVisible(SSheet.SheetVisible.HIDDEN);
        int numberOfValidationsAdded = 0;
        List<SName> dependentRanges = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().endsWith("chosen")) {
                String choiceName = name.getName().substring(0, name.getName().length() - "chosen".length());
                List<String> choiceOptions = loadExternalSelections(loggedInUser,choiceName + "choice");
                if (choiceOptions!=null){
                    return null;
                }

            }
        }
        for (SName chosen : book.getInternalBook().getNames()) {
            String rangeName = chosen.getName().toLowerCase();
            // are pivot headings being used at all now? Shall I zap? TODO
            if (rangeName.toLowerCase().startsWith(StringLiterals.AZPIVOTFILTERS) || rangeName.toLowerCase().startsWith(StringLiterals.AZCONTEXTFILTERS)) {//the correct version should be 'az_ContextFilters'
                String[] filters = BookUtils.getSnameCell(chosen).getStringValue().split(",");
                SName contextChoices = book.getInternalBook().getNameByName(StringLiterals.AZCONTEXTHEADINGS);
                if (contextChoices == null) {
                    //original name...
                    contextChoices = book.getInternalBook().getNameByName(StringLiterals.AZPIVOTHEADINGS);
                }
                if (contextChoices != null) {
                    showChoices(loggedInUser, book, contextChoices, filters );
                }
            }
            if (chosen.getName().toLowerCase().endsWith("chosen")) {
                String choiceName = chosen.getName().substring(0, chosen.getName().length() - "choice".length());
   //                System.out.println("debug:  trying to find the region " + choiceName + "chosen");
                SName choice = BookUtils.getNameByName(choiceName + "choice", sheet);// as ever I do wonder about these string literals
                SCell choiceCell = BookUtils.getSnameCell(choice);
                if (choice.getRefersToCellRegion() != null) {
                    CellRegion chosenRegion = chosen.getRefersToCellRegion();
                    if (chosenRegion != null) {
                        // EFC note - I made a hack for bonza to check this wasn't null but
                        List<String> choiceOptions = choiceOptionsMap.get(choice.getName().toLowerCase());
                        boolean dataRegionDropdown = !BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(chosenRegion.getRow(), chosenRegion.getColumn(), sheet).isEmpty();
                        if (choiceCell.getType() != SCell.CellType.ERROR && (choiceCell.getType() != SCell.CellType.FORMULA || choiceCell.getFormulaResultType() != SCell.CellType.ERROR)) {
                            // check to make it work for Darren after some recent changes - todo - address what's actually going on here
                            if (choiceCell.getType() == SCell.CellType.FORMULA) {
                                choiceCell.clearFormulaResultCache();
                            }
                            String query = choiceCell.getStringValue();
                            int contentPos = query.toLowerCase().indexOf(CONTENTS);
                            if ((chosenRegion.getRowCount() == 1 || dataRegionDropdown) && (choiceOptions != null || contentPos >= 0)) {// dataregiondropdown is to determine if it's in a data region, the choice drop downs are sometimes used (abused?) in such a manner, a bank of drop downs in a data region
                                if (contentPos < 0) {//not a dependent range
                                    BookUtils.setValue(validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded), choice.getName());
                                    int row = 0;
                                    // changing comment - I don't think this can NPE - IntelliJ just can't see the logic that either this isn't null or contentPos < 0, this is in a contentPos < 0 condition. Could rejig the logic?
                                    for (String choiceOption : choiceOptions) {
                                        row++;// like starting at 1
                                        SCell vCell = validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded);
                                        vCell.setCellStyle(sheet.getInternalSheet().getCell(chosenRegion.getRow(), chosenRegion.getColumn()).getCellStyle());
                                        // so the following bit of code was parsing a date on, for example 2020-11-24 policies which we wouldn't want it to. For the mo will check for spaces and not do the parse then
                                        if (choiceOption.trim().contains(" ")){
                                            BookUtils.setValue(vCell, choiceOption);
                                        } else {
                                            try {
                                                Date date = df.parse(choiceOption);
                                                vCell.setDateValue(date);
                                            } catch (Exception e) {
                                                BookUtils.setValue(vCell, choiceOption);
                                            }
                                        }
                                    }
                                    if (row > 0) { // if choice options is empty this will not work
                                        Range validationValues = Ranges.range(validationSheet, 1, numberOfValidationsAdded, row, numberOfValidationsAdded);
                                        //validationValues.createName("az_Validation" + numberOfValidationsAdded);
                                        for (int rowNo = chosenRegion.getRow(); rowNo < chosenRegion.getRow() + chosenRegion.getRowCount(); rowNo++) {
                                            for (int colNo = chosenRegion.getColumn(); colNo < chosenRegion.getColumn() + chosenRegion.getColumnCount(); colNo++) {
                                                Range chosenRange = Ranges.range(sheet, rowNo, colNo, rowNo, colNo);
                                                //chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "\"az_Validation" + numberOfValidationsAdded +"\"", null,
                                                chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                                        //true, "title", "msg",
                                                        true, "", "",
                                                        false, Validation.AlertStyle.WARNING, "alert title", "alert msg");
                                            }
                                        }
                                        numberOfValidationsAdded++;
                                    } else {
                                        System.out.println("no choices for : " + choiceCell.getStringValue());
                                    }
                                } else {
                                    dependentRanges.add(choice);
                                }
                            }
                        }// do anything in the case of excel error?
                    }
                }
            }
        }
        // should this be being dealt with in here?
        return dependentRanges;
    }

    private static List<String> loadExternalSelections(LoggedInUser loggedInUser, String selectionName){
        List<ExternalDataRequest>externalDataRequests = ExternalDataRequestDAO.findForReportId(loggedInUser.getOnlineReport().getId());
        for (ExternalDataRequest externalDataRequest:externalDataRequests){
            if (externalDataRequest.getSheetRangeName().equalsIgnoreCase(selectionName)){
                try {
                    List<List<String>> data = ExternalConnector.getData(loggedInUser, externalDataRequest.getConnectorId(), CommonReportUtils.replaceUserChoicesInQuery(loggedInUser, externalDataRequest.getReadSQL()), null, null);
                    if (data != null) {
                        List<String> toReturn = new ArrayList<>();
                        for (List<String> dataline : data) {
                            toReturn.add(dataline.get(0));
                        }
                        return toReturn;
                    }
                }catch(Exception e){
                    List<String> toReturn = new ArrayList<>();
                    toReturn.add(e.getMessage());
                    return toReturn;
                }
                break;
            }

        }
        return null;
    }


    // shows choices on a pivot but need to be clearer on exactly what this is
    private static void showChoices(LoggedInUser loggedInUser, Book book, SName contextChoices, String[] filters) {
        int headingWidth = 3; // it can be made a parameter again later if it will ever be different
        Sheet cSheet = book.getSheet(contextChoices.getRefersToSheetName());
        CellRegion chRange = contextChoices.getRefersToCellRegion();
        int headingRow = chRange.getRow();
        int headingCol = chRange.getColumn();
        int headingRows = chRange.getRowCount();
        int filterCount = 0;
        //on the top of pivot tables, the options are shown as pair groups separated by a space, sometimes on two rows, also separated by a space
        for (String filter : filters) {
            filter = filter.trim();
            List<String> optionsList = CommonReportUtils.getDropdownListForQuery(loggedInUser, "`" + filter + "` children sorted");
            if (optionsList != null && optionsList.size() > 1) {
                String selected = multiList(loggedInUser, "az_" + filter, "`" + filter + "` children sorted");//leave out any with single choices
                int rowOffset = filterCount % headingRows;
                int colOffset = filterCount / headingRows;
                int chosenRow = headingRow + rowOffset;
                int chosenCol = headingCol + headingWidth * colOffset;
                if (filterCount > 0) {
                    Range copySource = Ranges.range(cSheet, headingRow, headingCol, headingRow, headingCol + 1);
                    Range copyTarget = Ranges.range(cSheet, chosenRow, chosenCol, chosenRow, chosenCol + 1);
                    CellOperationUtil.paste(copySource, copyTarget);
                    //Ranges.range(pSheet, chosenRow, chosenCol + 1).setNameName(filter + "Chosen");

                }
                BookUtils.setValue(cSheet.getInternalSheet().getCell(chosenRow, chosenCol), filter);
//                if (headingWidth > 1) {
                    BookUtils.setValue(cSheet.getInternalSheet().getCell(chosenRow, chosenCol + 1), selected);
//                }
                filterCount++;
            }
        }
    }

    // choices can be a real pain, I effectively need to keep resolving them until they don't change due to choices being based on choices (dependencies in excel)
    static Map<String, List<String>> resolveAndSetChoiceOptions(LoggedInUser loggedInUser, Sheet sheet, List<CellRegion> regionsToWatchForMerge) throws RemoteException {
        int attempts = 0;
        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
        boolean resolveChoices = true;
        Map<String, List<String>> choiceOptionsMap = resolveChoiceOptions(namesForSheet, loggedInUser);
         Map<String, String> userChoices = CommonReportUtils.getUserChoicesMap(loggedInUser);
        // will explain this below where it's used near the end of the function
        Map<String, String> choicesSet = new HashMap<>();

        StringBuilder context = new StringBuilder();
        while (resolveChoices) {
            context = new StringBuilder();
            // Now I need to run through all choices setting from the user options IF it is valid and the first on the menu if it is not
            for (SName sName : namesForSheet) {
                if (sName.getName().endsWith("Chosen")) {
                    CellRegion chosen = sName.getRefersToCellRegion();
                    if (BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(chosen.getRow(), chosen.getColumn(), sheet).isEmpty()) {
                        String choiceName = sName.getName().substring(0, sName.getName().length() - "Chosen".length()).toLowerCase();
                        if (chosen.getRowCount() == 1 && chosen.getColumnCount() == 1) { // I think I may keep this constraint even after
                            // need to check that this choice is actually valid, so we need the choice query - should this be using the query as a cache?
                            List<String> validOptions = choiceOptionsMap.get(choiceName + "choice") != null ? new ArrayList<>(choiceOptionsMap.get(choiceName + "choice")) : null;
                            String userChoice = userChoices.get(choiceName.startsWith("az_") ? choiceName.substring(3) : choiceName);
                            LocalDate date = ReportUtils.isADate(userChoice);
                            // todo - tidy after the valid options ower case thing
                            if (validOptions != null && validOptions.size() > 0 && validOptions.get(0) != null) {//a single null element returned when list consists of an attribute that does not exis
                                if (SpreadsheetService.FIRST_PLACEHOLDER.equals(userChoice)) {
                                    userChoice = validOptions.get(0);
                                }
                                if (SpreadsheetService.LAST_PLACEHOLDER.equals(userChoice)) {
                                    userChoice = validOptions.get(validOptions.size() - 1);
                                }
                                String first = validOptions.get(0); // before lower case
                                // hack - valid options needs to be case insensitive. Allow dec-18 for Dec-18
                                for (int i = 0; i < validOptions.size(); i++){
                                    try {
                                        validOptions.set(i, validOptions.get(i).toLowerCase().trim());
                                    } catch (Exception e) {
                                        System.out.println("choices problem " + choiceName + "choice, valid options " + validOptions);
                                        for (String vo : validOptions){
                                            System.out.println(vo);
                                        }
                                        throw e;
                                    }
                                }
                                while (userChoice != null && !validOptions.contains(userChoice.toLowerCase()) && userChoice.contains("->")) {
                                    //maybe the user choice is over -specified. (e.g from drilldown or removal of conflicting names)  Try removing the super-sets
                                    userChoice = userChoice.substring(userChoice.indexOf("->") + 2);
                                }
                                if ((userChoice == null || !validOptions.contains(userChoice.toLowerCase())) && !validOptions.isEmpty()) { // just set the first for the mo.
                                    //check that userChoice is not a valid date...
                                    if (date == null || !validOptions.contains(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")).toLowerCase())) {
                                        userChoice = first;
                                    }
                                }
                            }
                            if (userChoice != null) {
                                SCell sCell = sheet.getInternalSheet().getCell(chosen.getRow(), chosen.getColumn());
                                BookUtils.setValue(sCell, userChoice);
                                Ranges.range(sheet, sCell.getRowIndex(), sCell.getColumnIndex()).notifyChange(); // might well be formulae related to the choice setting
                                if (choiceName.startsWith("az_")) {
                                    choiceName = choiceName.substring(3);
                                }
                                context.append(choiceName).append(" = ").append(userChoice).append(";");
                                choicesSet.put(choiceName, userChoice);
                            }
                        }
                        regionsToWatchForMerge.add(chosen);
                    }
                } else if (sName.getName().toLowerCase().endsWith(ZKComposer.MULTI.toLowerCase())) { // set the multi here - it was during validation which makes no sense
                    SCell resultCell = BookUtils.getSnameCell(sName);
                    String choiceLookup = sName.getName().substring(0, sName.getName().length() - ZKComposer.MULTI.length());
                    SName choiceName = BookUtils.getNameByName(choiceLookup + "Choice", sheet);
                    SCell choiceCell = BookUtils.getSnameCell(choiceName);
                    if (choiceName != null) {
                        // all multi list is is a fancy way of saying to the user what is selected, e.g. all, various, all but or a list of those selected. The actual selection box is created in the composer, onclick
                        // pointless strip and re add of "Multi"? copied, code - todo address
                        BookUtils.setValue(resultCell, multiList(loggedInUser, choiceLookup + "Multi", choiceCell.getStringValue()));
                    }
                } else if (sName.getName().toLowerCase().endsWith(ZKComposer.CHOSENTREE.toLowerCase())) { // set chosen tree - no validations, just set from the choice in the user DB, may update later
                    SCell resultCell = BookUtils.getSnameCell(sName);
                    String choiceLookup = sName.getName().substring(0, sName.getName().length() - ZKComposer.CHOSENTREE.length()).toLowerCase();
                    String userChoice = userChoices.get(choiceLookup.startsWith("az_") ? choiceLookup.substring(3) : choiceLookup);
                    SName choiceName = BookUtils.getNameByName(choiceLookup + "Choice", sheet);
                    SCell choiceCell = BookUtils.getSnameCell(choiceName);
                    String query = choiceCell.getStringValue();
                    if (userChoice != null){
                        // we need to check if this choice is in the tree that would be shown
                        try{
                            if (RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).nameValidForChosenTree(loggedInUser.getDataAccessToken(), userChoice, query)) {
                                BookUtils.setValue(resultCell, userChoice);
                                choicesSet.put(choiceLookup, userChoice);

                            } else { // set default
                                BookUtils.setValue(resultCell, RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getFirstChoiceForChosenTree(loggedInUser.getDataAccessToken(), query));
                            }
                        }catch(Exception e){
                            BookUtils.setValue(resultCell, RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getFirstChoiceForChosenTree(loggedInUser.getDataAccessToken(), query));

                        }

                    }
                }
                if (sName.getName().equalsIgnoreCase(StringLiterals.AZREPORTNAME)) {
                    regionsToWatchForMerge.add(sName.getRefersToCellRegion());
                }
            }
            resolveChoices = false;
            // ok so we've set them but now derived choices may have changed, no real option except to resolve again and see if there's a difference
            Map<String, List<String>> newChoiceOptionsMap = resolveChoiceOptions(namesForSheet, loggedInUser);
            if (!newChoiceOptionsMap.equals(choiceOptionsMap)) { // equals is fine as Java is sensible about these things unlike C# . . .
                System.out.println("choices changed as a result of chosen, resolving again");
                resolveChoices = true;
                choiceOptionsMap = newChoiceOptionsMap;
            } else { // no changes from excel formulae, check user choices setting
        /* according to a comment by WFC on this class on 25/06/2019 "Update the user choice saved to disk as soon as an automatic allocation is made."
         this was above when assigning to the cell but junk got in the sql user choices as choices depend on choices,
         it runs until choices are not affecting other choices, in the mean time you'd get something like
         "Error : Exception: could not parse `` children - `Transaction type` level 2(error: Cannot resolve reference to a name )"
         put in the choice. I don't know why it is required to save the automatic allocations, it would be nice to know. In the mean
         time preserve the functionality by comparing the choices as set with user choices from the sql

         EFC further addition : since there's the [user choice] syntax then *if* choices are set after the excel stuff has happened then send the loop around again
         */

                for (String choiceName : choicesSet.keySet()){
                    if (!choicesSet.get(choiceName).equals(userChoices.get(choiceName))){
                        System.out.println(choiceName + " " + choicesSet.get(choiceName) + " doesn't match previous set choice " + userChoices.get(choiceName) + " resolve again");
                        SpreadsheetService.setUserChoice(loggedInUser,choiceName,choicesSet.get(choiceName));
                        userChoices.put(choiceName,choicesSet.get(choiceName)); // update the map also or we'll keep looping
                        resolveChoices = true;
                    }
                }
            }
            attempts++;
            if (attempts > 20) {
                System.out.println("20 attempts at resolving choices, odds on there's some kind of circular reference, stopping");
                break;
            }
        }
        loggedInUser.setContext(context.toString());
        return choiceOptionsMap;
    }

    /* This did return a map with the query as the key but I'm going to change this to the name, it will save unnecessary query look ups later.
     This adds one caveat : the options match the choice name at the time the function ran - if the query in the choice cell updates this needs to be run again - resolveAndSetChoiceOptions checks for this
       */

    private static Map<String, List<String>> resolveChoiceOptions(List<SName> names, LoggedInUser loggedInUser) throws RemoteException{
        Map<String, List<String>> toReturn = new HashMap<>();
        for (SName name : names) {
            //check to create pivot filter choices.... TODO - is this a redundant comment, aren't the filters being sorted anyway?
            if (name.getName().endsWith("Choice") && name.getRefersToCellRegion() != null) {
                String fieldName = name.getName().substring(0, name.getName().length() - 6);
                // ok I assume choice is a single cell
                List<String> choiceOptions = new ArrayList<>(); // was null, see no help in that
                // new lines from edd to try to resolve choice stuff
                SCell choiceCell = BookUtils.getSnameCell(name);
                //System.out.println("Choice cell : " + choiceCell);
                // check to make it work for Darren after some recent changes - todo - address what's actually going on here
                if (choiceCell.getType() == SCell.CellType.FORMULA) {
                    choiceCell.clearFormulaResultCache();
                }
                if (choiceCell.getType() != SCell.CellType.ERROR && (choiceCell.getType() != SCell.CellType.FORMULA || choiceCell.getFormulaResultType() != SCell.CellType.ERROR)) {
                    String query;
                    query = choiceCell.getStringValue();
                    if (query.toLowerCase(Locale.ROOT).startsWith("external:")){
                        toReturn.put(name.getName().toLowerCase(Locale.ROOT),loadExternalSelections(loggedInUser,query.substring(9)));
                    }else if (!query.toLowerCase().contains(CONTENTS)) {//FIRST PASS - MISS OUT ANY QUERY CONTAINING 'contents('
                        if (query.toLowerCase().contains(" default")) {
                            query = query.substring(0, query.toLowerCase().indexOf(" default"));
                        }
                        try {
                            if (query.startsWith("\"") || query.startsWith("“")) {
                                //crude - if there is a comma in any option this will fail
                                query = query.replace("\"", "").replace("“", "").replace("”", "");
                                String[] choices = query.split(",");
                                Collections.addAll(choiceOptions, choices);
                            } else {
                                choiceOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, query, fieldName, null);
                            }
                        } catch (Exception e) {
                            choiceOptions.add(e.getMessage());
                        }
                        toReturn.put(name.getName().toLowerCase(), choiceOptions);
                    }
                } else {
                    System.out.println("Error trying to resolve choice option - error on cell " + choiceCell.getReferenceString() + " " + choiceCell.getErrorValue().getMessage());
                }
            }
        }
        return toReturn;
    }

    /*
    Related to Contents( which is a dropdown dependant on other dropdowns - it makes the dropdown a composite I think
     e.g. `All Projects` children * `Contents(az_ClientChosen)

     Combine into addvalidation?
     */

    static void resolveDependentChoiceOptions(String sheetName, List<SName> dependentRanges, Book book, LoggedInUser loggedInUser) {
        if (sheetName.length() > 10) {
            sheetName = sheetName.substring(0, 10);
        }
        Sheet validationSheet = book.getSheet(sheetName + VALIDATION_SHEET);
        SSheet vSheet = validationSheet.getInternalSheet();
        for (SName name : dependentRanges) {
            String dependentName = name.getName().substring(0, name.getName().length() - 6);//remove 'choice'
            SCell choiceCell = BookUtils.getSnameCell(name);
            String query = choiceCell.getStringValue();
            int contentPos = query.toLowerCase().indexOf(CONTENTS);
            int catEnd = query.substring(contentPos + CONTENTS.length()).indexOf(")");
            if (catEnd > 0) {
                String choiceSourceString = query.substring(contentPos + CONTENTS.length()).substring(0, catEnd - 6).toLowerCase();//assuming that the expression concludes ...Chosen)
                SName choiceSource = book.getInternalBook().getNameByName(choiceSourceString + "Chosen");
                SName choiceList = book.getInternalBook().getNameByName(dependentName + "List");//TODO - NEEDS AN ERROR IF THESE RANGES ARE NOT FOUND
                int validationSourceColumn = 0;
                String listName = vSheet.getCell(0, validationSourceColumn).getStringValue();
                SName chosen = book.getInternalBook().getNameByName(dependentName + "Chosen");
                while (listName != null && !listName.toLowerCase().equals(choiceSourceString + "choice") && listName.length() > 0 && validationSourceColumn < 1000) {
                    listName = vSheet.getCell(0, ++validationSourceColumn).getStringValue();
                }
                if (chosen != null && choiceSource != null && choiceList != null && listName != null && listName.length() > 0 && validationSourceColumn < 1000) {
                    int targetCol = validationSourceColumn;
                    while (vSheet.getCell(0, targetCol).getStringValue().length() > 0 && targetCol < 1000) targetCol++;
                    int maxSize = 0;
                    int optionNo = 0;
                    //create an array of the options....
                    while (vSheet.getCell(optionNo + 1, validationSourceColumn).getStringValue().length() > 0) {
                        String optionVal = vSheet.getCell(optionNo + 1, validationSourceColumn).getStringValue();
                        BookUtils.setValue(vSheet.getCell(0, targetCol + optionNo), optionVal);
                        String newQuery = query.substring(0, contentPos) + optionVal + query.substring(contentPos + catEnd + CONTENTS.length() + 1);
                        try {
                            List<String> optionList = CommonReportUtils.getDropdownListForQuery(loggedInUser, newQuery);
                            if (optionList.size() > maxSize) maxSize = optionList.size();
                            int rowOffset = 1;
                            for (String option : optionList) {
                                BookUtils.setValue(vSheet.getCell(rowOffset++, targetCol + optionNo), option);
                            }
                        } catch (Exception e) {
                            BookUtils.setValue(vSheet.getCell(1, validationSourceColumn), e.getMessage());
                            return;
                        }
                        optionNo++;
                    }
                    String lookupRange = validationSheet.getSheetName() + "!" + BookUtils.rangeToText(0, targetCol) + ":" + BookUtils.rangeToText(maxSize, targetCol + optionNo - 1);
                    //fill in blanks - they may come through as '0'
                    for (int col = 0; col < optionNo; col++) {
                        for (int row = 1; row < maxSize + 1; row++) {
                            if (vSheet.getCell(row, targetCol + col).getStringValue().length() == 0) {
                                BookUtils.setValue(vSheet.getCell(row, targetCol + col), " ");
                            }
                        }
                    }
                    CellRegion choiceListRange = choiceList.getRefersToCellRegion();
                    CellRegion choiceSourceRange = choiceSource.getRefersToCellRegion();
                    int listStart = choiceListRange.getColumn();
                    CellRegion chosenRange = chosen.getRefersToCellRegion();
                    int chosenCol = chosenRange.getColumn();
                    Sheet chosenSheet = book.getSheet(chosen.getRefersToSheetName());
                    for (int row = chosenRange.getRow(); row < chosenRange.getRow() + chosenRange.getRowCount(); row++) {
                        String source = BookUtils.rangeToText(row, choiceSourceRange.getColumn());
                        for (int option = 0; option < maxSize; option++) {
                            chosenSheet.getInternalSheet().getCell(row, listStart + option).setFormulaValue("HLOOKUP(" + source + "," + lookupRange + "," + (option + 2) + ",false)");
                        }
                        Range chosenCell = Ranges.range(chosenSheet, row, chosenCol, row, chosenCol);
                        Range validationValues = Ranges.range(chosenSheet, row, listStart, row, listStart + maxSize - 1);
                        //chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "\"az_Validation" + numberOfValidationsAdded +"\"", null,
                        chosenCell.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                //true, "title", "msg",
                                true, "", "",
                                false, Validation.AlertStyle.WARNING, "alert title", "alert msg");
                    }
                }
            }
        }
    }

    // filter name is what could be selected for this user and source set is the query of the possibilities

    public static String multiList(LoggedInUser loggedInUser, String filterName, String sourceSet) {
        try {
            List<String> allOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, sourceSet);
            List<String> chosenOptions;
            // justUser = true meaning server side JUST use the user email in languages. Not 100% sure how important this but as I refactored I wanted to keep the logic
            chosenOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, "`" + filterName + "` children", loggedInUser.getUser().getEmail(), true, -1);
            // try to make multis behave as dropdowns change
            if (!allOptions.isEmpty() && !allOptions.get(0).startsWith("Error") && // if the options are good and
                    ((chosenOptions.size() == 1 && chosenOptions.get(0).startsWith("Error"))// the existing set doens't exist
                            || !allOptions.containsAll(chosenOptions))){// OR it has values that are not allowed
                // then set it to all
                chosenOptions = allOptions;
                // and create the set server side, it will no doubt be referenced
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSetWithQuery(loggedInUser.getDataAccessToken(), filterName, loggedInUser.getUser().getEmail(), CommonReportUtils.replaceUserChoicesInQuery(loggedInUser,sourceSet));

            }
            if (allOptions.size() < 2)
                return "[all]"; // EFC - this did return null which I think is inconsistent. If there's only one option should it maybe be all?
            if (chosenOptions.size() == 0 || chosenOptions.size() == allOptions.size()) return "[all]";
            if (chosenOptions.size() < 6) {
                StringBuilder toReturn = new StringBuilder();
                boolean firstElement = true;
                for (String chosen : chosenOptions) {
                    if (!firstElement) {
                        toReturn.append(", ");
                    }
                    toReturn.append(chosen);
                    firstElement = false;
                }
                return toReturn.toString();
            }
            if (allOptions.size() - chosenOptions.size() > 5) return "[various]";
            StringBuilder toReturn = new StringBuilder();
            toReturn.append("[all] but ");
            List<String> remaining = new ArrayList<>(allOptions);
            boolean firstElement = true;
            remaining.removeAll(chosenOptions);
            for (String choice : remaining) {
                if (!firstElement) {
                    toReturn.append(", ");
                }
                toReturn.append(choice);
                firstElement = false;
            }
            return toReturn.toString();
        } catch (Exception e) {
            return "[all]";
        }
    }

    static Map<String, String> parseChoicesFromDrillDownContextString(String context) {
        Map<String, String> map = new HashMap<>();
        int equalsPos = context.indexOf("=");//used to have spaces in case names contained '=', but this resulted in confusion
        while (equalsPos > 0) {
            int endParam = context.indexOf(";");//todo - maybe consider using comma here as alternative
            if (endParam < 0) endParam = context.length();
            String paramName = context.substring(0, equalsPos).trim();
            String paramValue = context.substring(equalsPos + 1, endParam).trim();
            map.put(paramName, paramValue);
            context = context.substring(endParam);
            if (context.length() > 0) context = context.substring(1);//remove the semicolon
            equalsPos = context.indexOf("=");
        }
        return map;
    }

    public static void setChoices(LoggedInUser loggedInUser, String context) throws Exception{
        Map<String, String> stringStringMap = parseChoicesFromDrillDownContextString(context);
        for (Map.Entry<String, String> choiceChosen : stringStringMap.entrySet()) {
            // EFC note 07/04/2021 we need to support attributes here e.g. in spreadsheet Customer Record with customer = `[rowheading]`.`all customers`
            // so by here (this function is only used for drilldown) we'll be at something like `DFKKHJKJH`.`all customers`. Try to resolve it.
            String choice = choiceChosen.getKey();

            String chosen = choiceChosen.getValue();

            if (chosen.contains("`.`")){
                DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
                try{
                    chosen = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, chosen.substring(chosen.indexOf("`"), chosen.indexOf("`.`")), chosen.substring( chosen.indexOf("`.`") + 3, chosen.lastIndexOf("`")));
                } catch (Exception e){
                    chosen = e.getMessage(); // I guess give them a clue when it doesn't work
                }
            }

            if (choice.toLowerCase(Locale.ROOT).equals("database")){
                LoginService.switchDatabase(loggedInUser,chosen);
            }else{
                SpreadsheetService.setUserChoice(loggedInUser, choice, chosen);
            }
        }
    }



}