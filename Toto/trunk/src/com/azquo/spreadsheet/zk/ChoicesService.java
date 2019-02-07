package com.azquo.spreadsheet.zk;

import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.api.model.Validation;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;

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
            String rangeName = name.getName().toLowerCase();
            // are pivot headings being used at all now? Shall I zap? TODO
            if (rangeName.toLowerCase().startsWith(ReportRenderer.AZPIVOTFILTERS) || rangeName.toLowerCase().startsWith(ReportRenderer.AZCONTEXTFILTERS)) {//the correct version should be 'az_ContextFilters'
                String[] filters = BookUtils.getSnameCell(name).getStringValue().split(",");
                SName contextChoices = book.getInternalBook().getNameByName(ReportRenderer.AZCONTEXTHEADINGS);
                if (contextChoices == null) {
                    //original name...
                    contextChoices = book.getInternalBook().getNameByName(ReportRenderer.AZPIVOTHEADINGS);
                }
                if (contextChoices != null) {
                    showChoices(loggedInUser, book, contextChoices, filters, 3);
                }
            }
            if (name.getName().toLowerCase().endsWith("choice")) {
                String choiceName = name.getName().substring(0, name.getName().length() - "choice".length());
                SCell choiceCell = BookUtils.getSnameCell(name);
//                System.out.println("debug:  trying to find the region " + choiceName + "chosen");
                SName chosen = BookUtils.getNameByName(choiceName + "chosen", sheet);// as ever I do wonder about these string literals
                if (name.getRefersToCellRegion() != null && chosen != null) {
                    CellRegion chosenRegion = chosen.getRefersToCellRegion();
                    if (chosenRegion != null) {
                        List<String> choiceOptions = choiceOptionsMap.get(name.getName().toLowerCase());
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
                                    BookUtils.setValue(validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded), name.getName());
                                    int row = 0;
                                    // changing comment - I don't think this can NPE - IntelliJ just can't see the logic that either this isn't null or contentPos < 0, this is in a contentPos < 0 condition. Could rejig the logic?
                                    for (String choiceOption : choiceOptions) {
                                        row++;// like starting at 1
                                        SCell vCell = validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded);
                                        vCell.setCellStyle(sheet.getInternalSheet().getCell(chosenRegion.getRow(), chosenRegion.getColumn()).getCellStyle());
                                        try {
                                            Date date = df.parse(choiceOption);
                                            vCell.setDateValue(date);
                                        } catch (Exception e) {
                                            BookUtils.setValue(vCell, choiceOption);
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
                                    dependentRanges.add(name);
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

    // shows choices on a pivot but need to be clearer on exactly what this is
    private static void showChoices(LoggedInUser loggedInUser, Book book, SName contextChoices, String[] filters, int headingWidth) {
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
                if (headingWidth > 1) {
                    BookUtils.setValue(cSheet.getInternalSheet().getCell(chosenRow, chosenCol + 1), selected);
                }
                filterCount++;
            }
        }
    }

    // choices can be a real pain, I effectively need to keep resolving them until they don't change due to choices being based on choices (dependencies in excel)
    static Map<String, List<String>> resolveAndSetChoiceOptions(LoggedInUser loggedInUser, Sheet sheet, List<CellRegion> regionsToWatchForMerge) {
        int attempts = 0;
        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
        boolean resolveChoices = true;
        Map<String, List<String>> choiceOptionsMap = resolveChoiceOptions(namesForSheet, loggedInUser);
        Map<String, String> userChoices = CommonReportUtils.getUserChoicesMap(loggedInUser);
        String context = "";
        while (resolveChoices) {
            context = "";
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
                            // todo - tidy after the valid motpions ower case thing
                            if (validOptions != null) {
                                if (SpreadsheetService.FIRST_PLACEHOLDER.equals(userChoice)) {
                                    userChoice = validOptions.get(0);
                                }
                                if (SpreadsheetService.LAST_PLACEHOLDER.equals(userChoice)) {
                                    userChoice = validOptions.get(validOptions.size() - 1);
                                }
                                String first = !validOptions.isEmpty() ? validOptions.get(0) : null; // before lower case
                                // hack - valid options needs to be case insensitive. Allow dec-18 for Dec-18
                                for (int i = 0; i < validOptions.size(); i++){
                                    validOptions.set(i, validOptions.get(i).toLowerCase());
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
                                context += choiceName + " = " + userChoice + ";";
                            }
                        }
                        regionsToWatchForMerge.add(chosen);
                    }
                } else if (sName.getName().toLowerCase().endsWith("multi")) { // set the multi here - it was during validation which makes no sense
                    SCell resultCell = BookUtils.getSnameCell(sName);
                    String choiceLookup = sName.getName().substring(0, sName.getName().length() - 5);
                    SName choiceName = BookUtils.getNameByName(choiceLookup + "Choice", sheet);
                    SCell choiceCell = BookUtils.getSnameCell(choiceName);
                    if (choiceName != null) {
                        // all multi list is is a fancy way of saying to the user what is selected, e.g. all, various, all but or a list of those selected. The actual selection box is created in the composer, onclick
                        // pointless strip and re add of "Multi"? copied, code - todo address
                        BookUtils.setValue(resultCell, multiList(loggedInUser, choiceLookup + "Multi", choiceCell.getStringValue()));
                    }
                }
                if (sName.getName().equalsIgnoreCase(ReportRenderer.AZREPORTNAME)) {
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
            }
            attempts++;
            if (attempts > 10) {
                System.out.println("10 attempts at resolving choices, odds on there's some kind of circular reference, stopping");
            }
        }
        loggedInUser.setContext(context);
        return choiceOptionsMap;
    }

    /* This did return a map with the query as the key but I'm going to change this to the name, it will save unnecessary query look ups later.
     This adds one caveat : the options match the choice name at the time the function ran - if the query in the choice cell updates this needs to be run again - resolveAndSetChoiceOptions checks for this
       */

    private static Map<String, List<String>> resolveChoiceOptions(List<SName> names, LoggedInUser loggedInUser) {
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
                if (choiceCell.getType() != SCell.CellType.ERROR && (choiceCell.getType() != SCell.CellType.FORMULA || choiceCell.getFormulaResultType() != SCell.CellType.ERROR)) {
                    String query;
                    // check to make it work for Darren after some recent changes - todo - address what's actually going on here
                    if (choiceCell.getType() == SCell.CellType.FORMULA) {
                        choiceCell.clearFormulaResultCache();

                    }
                    query = choiceCell.getStringValue();
                    if (!query.toLowerCase().contains(CONTENTS)) {//FIRST PASS - MISS OUT ANY QUERY CONTAINING 'contents('
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
                                choiceOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, query, fieldName);
                            }
                        } catch (Exception e) {
                            choiceOptions.add(e.getMessage());
                        }
                        toReturn.put(name.getName().toLowerCase(), choiceOptions);
                    }
                } // do anything in case of an error?
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
                    if (targetCol == 1000) {
                        //throw exception "too many set lists"
                    }
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
            if (chosenOptions.size() == 1 && chosenOptions.get(0).startsWith("Error")) { // this stops the error on making the drop down list
                chosenOptions = allOptions;
                // and create the set server side, it will no doubt be referenced
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), filterName, loggedInUser.getUser().getEmail(), sourceSet);
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
            List<String> remaining = new ArrayList<String>(allOptions);
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
        int equalsPos = context.indexOf(" = ");
        while (equalsPos > 0) {
            int endParam = context.indexOf(";");
            if (endParam < 0) endParam = context.length();
            String paramName = context.substring(0, equalsPos).trim();
            String paramValue = context.substring(equalsPos + 3, endParam).trim();
            map.put(paramName, paramValue);
            context = context.substring(endParam);
            if (context.length() > 0) context = context.substring(1);//remove the semicolon
            equalsPos = context.indexOf(" = ");
        }
        return map;
    }

    static void setChoices(LoggedInUser loggedInUser, String context) {
        Map<String, String> stringStringMap = parseChoicesFromDrillDownContextString(context);
        for (Map.Entry<String, String> choiceChosen : stringStringMap.entrySet()) {
            SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceChosen.getKey(), choiceChosen.getValue());
        }
    }
}