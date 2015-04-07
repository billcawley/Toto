package com.azquo.view;

import com.azquo.admindao.UserChoiceDAO;
import com.azquo.adminentities.UserChoice;
import com.azquo.controller.OnlineController;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Value;
import com.azquo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.api.model.Validation;
import org.zkoss.zss.jsp.BookProvider;
import org.zkoss.zss.model.*;
import org.zkoss.zss.model.impl.HyperlinkImpl;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cawley on 03/03/15.
 * TO manipulate the ZK book, practically speak a lot of what's in here might be functionally similar to what was in AzquoBook
 */
public class ZKAzquoBookUtils {

    public static final String azDataRegion = "az_DataRegion";
    public static final String azOptions = "az_Options";
    public static final String azHeadings = "az_headings";
    public static final String azInput = "az_Input";
    public static final String azNext = "az_Next";
    public static final String OPTIONPREFIX = "!";


    private static final ObjectMapper jacksonMapper = new ObjectMapper();


    final ValueService valueService;
    final NameService nameService;
    final UserChoiceDAO userChoiceDAO;
    final AdminService adminService;

    public ZKAzquoBookUtils(ValueService valueService, NameService nameService, UserChoiceDAO userChoiceDAO, AdminService adminService) {
        this.valueService = valueService;
        this.nameService = nameService;
        this.userChoiceDAO = userChoiceDAO;
        this.adminService = adminService;
    }


    // kind of like azquo book prepare sheet, load data bits, will aim to replicate the basics from there

    public void populateBook(Book book) throws Exception {
        //book.getInternalBook().getAttribute(ZKAzquoBookProvider.BOOK_PATH);
        LoggedInConnection loggedInConnection = (LoggedInConnection) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_CONNECTION);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);

        Map<String, String> userChoices = new HashMap<String, String>();
        // get the user choices for the report. Can be drop down values, sorting/highlighting etc.
        // a notable point here is that the user choices don't distinguish between sheets
        List<UserChoice> allChoices = userChoiceDAO.findForUserIdAndReportId(loggedInConnection.getUser().getId(), reportId);
        for (UserChoice uc : allChoices) {
            userChoices.put(uc.getChoiceName(), uc.getChoiceValue());
        }
        // I guess run through all sheets?

        if (loggedInConnection.getReportId()==1){
            for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
                Sheet sheet = book.getSheetAt(sheetNumber);
                loadAdminData(loggedInConnection, sheet);
            }
            //fill admin date
        }else {


            for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {


                Sheet sheet = book.getSheetAt(sheetNumber);
                // see if we can impose the user choices on the sheet
                for (String choiceName : userChoices.keySet()) {
                    CellRegion choice = getCellRegionForSheetAndName(sheet, choiceName + "Chosen");
                    if (choice != null) {
                        sheet.getInternalSheet().getCell(choice.getRow(), choice.getColumn()).setStringValue(userChoices.get(choiceName));
                    }
                }

                // ok the plan here is remove all the merges then put them back in after the regions are expanded.
                List<CellRegion> merges = new ArrayList<CellRegion>(sheet.getInternalSheet().getMergedRegions());

                for (CellRegion merge : merges) {
                    CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
                }

                // and now we want to run through all regions for this sheet, will look at the old code for this, I think it's fill region that we take cues from
                // names are per book, not sheet. Perhaps we could make names the big outside loop but for the moment I'll go by sheet - convenience function
                List<SName> namesForSheet = getNamesForSheet(sheet);
                for (SName name : namesForSheet) {
                    // Old one was case insensitive - not so happy about this. Will allow it on the prefix
                    if (name.getName().startsWith(azDataRegion)) { // then we have a data region to deal with here
                        String region = name.getName().substring(azDataRegion.length()); // might well be an empty string
                        String optionsForRegion = null;
                        CellRegion optionsRegion = getCellRegionForSheetAndName(sheet, azOptions + region);
                        if (optionsRegion != null) {
                            optionsForRegion = sheet.getInternalSheet().getCell(optionsRegion.getRow(), optionsRegion.getColumn()).getStringValue();
                            if (optionsForRegion.isEmpty()) {
                                optionsForRegion = null;
                            }
                        }
                        fillRegion(sheet, region, userChoices, optionsForRegion, loggedInConnection);
                    }
                }

                // all data for that sheet should be populated
                // snap the charts - currently just top left, do bottom right also?
                for (SChart chart : sheet.getInternalSheet().getCharts()) {
                    ViewAnchor oldAnchor = chart.getAnchor();
                    int row = oldAnchor.getRowIndex();
                    int col = oldAnchor.getColumnIndex();
                    int width = oldAnchor.getWidth();
                    int height = oldAnchor.getHeight();
                    chart.setAnchor(new ViewAnchor(row, col, 0, 0, width, height));
                }
                // now remerge? Should work
                for (CellRegion merge : merges) {
                    // I think we do want to merge horizontally (the boolean flag)
                    CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), true);
                }
                // now sort out dropdowns
                // todo, sort out saved choiced on the dropdowns
                /*                     UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, choiceName);
                if (userChoice != null) {
                    range.setValue(userChoice.getChoiceValue());
                }*/

                addValidation(namesForSheet, sheet, loggedInConnection);

            }
        }
    }

    // taking the function from old AzquoBook and rewriting

    private void fillRegion(Sheet sheet, String region, Map<String, String> userChoices, String optionsForRegion, LoggedInConnection loggedInConnection) throws Exception {
        int filterCount = asNumber(getOption(region, "hiderows", userChoices, optionsForRegion));
        if (filterCount == 0)
            filterCount = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second.
        int maxRows = asNumber(getOption(region, "maxrows", userChoices, optionsForRegion));
        //if (maxRows == 0) loggedInConnection.clearSortCols();//clear it.  not sure why this is done - pushes the sort from the load into the spreadsheet if there is no restriction on si
        int maxCols = asNumber(getOption(region, "maxcols", userChoices, optionsForRegion));


        CellRegion columnHeadingsDescription = getCellRegionForSheetAndName(sheet, "az_ColumnHeadings" + region);
        CellRegion rowHeadingsDescription = getCellRegionForSheetAndName(sheet, "az_RowHeadings" + region);
        CellRegion contextDescription = getCellRegionForSheetAndName(sheet, "az_Context" + region);

        // ok the old style got the region as an Excel copy/paste string then converted that into headers
        // I think I'll try to skip the middle man
        // adapted from createnames list form excel region
        if (columnHeadingsDescription != null && rowHeadingsDescription != null) {
            // instead of valueService.setupRowHeadings, used not for straight display but for the excel data bit, it will constain them and after we output.
            // while running in parallel with the old code it might work without this as the headings would have been set up in a previous call to the logged in connection

            List<List<List<DataRegionHeading>>> columnHeadings = getHeadingsLists(columnHeadingsDescription, sheet, loggedInConnection);
            List<List<List<DataRegionHeading>>> rowHeadings = getHeadingsLists(rowHeadingsDescription, sheet, loggedInConnection);
            // transpose, expand, transpose again
            List<List<Object>> displayObjectsForNewSheet = new ArrayList<List<Object>>();

            loggedInConnection.setRowHeadings(region, valueService.expandHeadings(rowHeadings));
            loggedInConnection.setColumnHeadings(region, valueService.expandHeadings(valueService.transpose2DList(columnHeadings)));
            // deal with context
            // copied from valueservice.getdatareegion. Seems a bit odd but this is the old behavior
            final List<Name> contextNames = new ArrayList<Name>();
            if (contextDescription != null) {
                String contextString = sheet.getInternalSheet().getCell(contextDescription.getRow(), contextDescription.getColumn()).getStringValue();
                if (contextString != null) {
                    final StringTokenizer st = new StringTokenizer(contextString, "\n");
                    while (st.hasMoreTokens()) {
                        final List<Name> thisContextNames = nameService.parseQuery(loggedInConnection, st.nextToken().trim());
                        if (thisContextNames.size() > 1) {
                            throw new Exception("error: context names must be individual - use 'as' to put sets in context");
                        }
                        if (thisContextNames.size() > 0) {
                            //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                            contextNames.add(thisContextNames.get(0));
                        }
                    }
                }
            }
            loggedInConnection.setContext(region, contextNames);

            // ok now ready for the data
            valueService.getExcelDataForColumnsRowsAndContext(loggedInConnection, loggedInConnection.getContext(region), region, filterCount, maxRows, maxCols, displayObjectsForNewSheet);


            List<List<DataRegionHeading>> expandedColumnHeadings = valueService.getColumnHeadingsAsArray(loggedInConnection, region);
            List<List<DataRegionHeading>> expandedRowHeadings = valueService.getRowHeadingsAsArray(loggedInConnection, region, filterCount);
            // todo : how to indicate sortable rows/cols
            // now, put the headings into the sheet!
            // might be factored into fill range in a bit
            CellRegion displayColumnHeadings = getCellRegionForSheetAndName(sheet, "az_DisplayColumnHeadings" + region);
            CellRegion displayRowHeadings = getCellRegionForSheetAndName(sheet, "az_DisplayRowHeadings" + region);
            CellRegion displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);

            int rowsToAdd;
            int colsToAdd;

            if (displayColumnHeadings != null && displayRowHeadings != null && displayDataRegion != null) {
                // add rows
                int maxCol = 0;
                for (int i = 0; i <= sheet.getLastRow(); i++) {
                    if (sheet.getLastColumn(i) > maxCol) {
                        maxCol = sheet.getLastColumn(i);
                    }
                }
                if ((displayRowHeadings.getRowCount() < expandedRowHeadings.size()) && displayRowHeadings.getRowCount() > 2) { // then we need to expand, and there is space to do so (3 or more allocated already)
                    rowsToAdd = expandedRowHeadings.size() - (displayRowHeadings.getRowCount());
                    int insertRow = displayRowHeadings.getRow() + 2; // I think this is correct, middle row of 3?
                    Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
                    Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1 , maxCol); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
                    CellOperationUtil.insertRow(insertRange);
                    // will this paste the lot?
                    CellOperationUtil.paste(copySource, insertRange);
                    int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
                    if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                        insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
                    }
/*                    for (int i = 0; i < rowsToAdd; i++) {
                        int rowToCopy = displayRowHeadings.getRow() + 1; // I think this is correct, middle row of 3?
                        Range copySource = Ranges.range(sheet, rowToCopy, 0, rowToCopy, maxCol);
                        Range insertRange = Ranges.range(sheet, rowToCopy + 1, 0, rowToCopy + 5, maxCol); // insert at the 3rd row
                        CellOperationUtil.insertRow(insertRange); // get formatting from above
                        if (sheet.getInternalSheet().getRow(rowToCopy).getHeight() != sheet.getInternalSheet().getRow(rowToCopy + 1).getHeight()) { // height may not match on insert/paste, if not set it
                            sheet.getInternalSheet().getRow(rowToCopy + 1).setHeight(sheet.getInternalSheet().getRow(rowToCopy).getHeight());
                        }
                        // ok now copy contents, this should (ha!) copy the row that was just shifted down to the one just created
                        CellOperationUtil.paste(copySource, insertRange);
                    }*/
                }
                // add columns
                int maxRow = sheet.getLastRow();
                if (displayColumnHeadings.getColumnCount() < expandedColumnHeadings.get(0).size() && displayColumnHeadings.getColumnCount() > 2) { // then we need to expand

                    colsToAdd = expandedColumnHeadings.get(0).size() - (displayColumnHeadings.getColumnCount());
                    int insertCol = displayColumnHeadings.getColumn() + 2; // I think this is correct, just after the second column?
                    Range copySource = Ranges.range(sheet, 0, insertCol - 1, maxRow, insertCol - 1);
                    Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1); // insert just before the 3rd col
                    CellOperationUtil.insertColumn(insertRange);
                    // will this paste the lot?
                    CellOperationUtil.paste(copySource, insertRange);
                    int originalWidth = sheet.getInternalSheet().getColumn(insertCol).getWidth();
                    if (originalWidth != sheet.getInternalSheet().getColumn(insertCol).getWidth()) { // height may not match on insert
                        insertRange.setColumnWidth(originalWidth); // hopefully set the lot in one go??
                    }
                }
                // ok there should be the right space for the headings
                int row = displayRowHeadings.getRow();
                for (List<DataRegionHeading> rowHeading : expandedRowHeadings) {
                    int col = displayRowHeadings.getColumn();
                    for (DataRegionHeading heading : rowHeading) {
                        if (heading!=null){
                            sheet.getInternalSheet().getCell(row, col).setValue(heading.getAttribute() != null ? heading.getAttribute() : heading.getName().getDisplayNameForLanguages(loggedInConnection.getLanguages()));
                        }
                        col++;
                    }
                    row++;
                }
                row = displayColumnHeadings.getRow();
                for (List<DataRegionHeading> colHeading : expandedColumnHeadings) {
                    int col = displayColumnHeadings.getColumn();
                    for (DataRegionHeading heading : colHeading) {
                        if (heading!=null){
                            sheet.getInternalSheet().getCell(row, col).setValue(heading.getAttribute() != null ? heading.getAttribute() : heading.getName().getDisplayNameForLanguages(loggedInConnection.getLanguages()));
                        }
                        col++;
                    }
                    row++;
                }
                row = displayDataRegion.getRow();
                for (List<Object> rowCellValues : displayObjectsForNewSheet) {
                    int col = displayDataRegion.getColumn();
                    for (Object cellValue : rowCellValues) {
                        sheet.getInternalSheet().getCell(row, col).setValue(cellValue);
                        col++;
                    }
                    row++;
                }
                // this is a pain, it seems I need to call 2 functions on each formula cell or the formaul may not be calculated. ANNOYING!
                Iterator<SRow> rowIterator = sheet.getInternalSheet().getRowIterator(); // only rows with values in them
                while (rowIterator.hasNext()) {
                    Iterator<SCell> cellIterator = sheet.getInternalSheet().getCellIterator(rowIterator.next().getIndex());
                    while (cellIterator.hasNext()) {
                        SCell cell = cellIterator.next();
                        if (cell.getType() == SCell.CellType.FORMULA) {
                            //System.out.println("doing the cell thing on " + cell);
                            cell.getFormulaResultType();
                            cell.clearFormulaResultCache();
                        }
                    }
                }
            }
        } else {
            CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);// this function should not be called without a valid data region
            if (dataRegion != null){
                sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching header and context regions for this data region : az_DataRegion" + region);
            } else {
                System.out.println("no region found for az_DataRegion" + region);
            }
        }
    }

    public List<SName> getNamesForSheet(Sheet sheet) {
        List<SName> names = new ArrayList<SName>();
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name.getRefersToSheetName()!= null && name.getRefersToSheetName().equals(sheet.getSheetName())) {
                names.add(name);
            }
        }
        return names;
    }

    // this works out case insensitive based on the API

    public CellRegion getCellRegionForSheetAndName(Sheet sheet, String name){
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        if (toReturn == null){// often may fail with explicit sheet name
            toReturn = sheet.getBook().getInternalBook().getNameByName(name);
        }
        if (toReturn != null && toReturn.getRefersToSheetName().equals(sheet.getSheetName())){
            return toReturn.getRefersToCellRegion();
        }
        return null;
    }

    public int asNumber(String string) {
        try {
            return string != null ? Integer.parseInt(string) : 0;
        } catch (Exception ignored) {
        }
        return 0;
    }

    public String getOption(String region, String optionName, Map<String, String> userChoices, String optionsForRegion) {
        String option = userChoices.get(OPTIONPREFIX + optionName + region); // first try from user choices, if not go for the sheet option
        if (option != null && option.length() > 0) {
            return option;
        }
        if (optionsForRegion != null) {
            int foundPos = optionsForRegion.toLowerCase().indexOf(optionName.toLowerCase());
            if (foundPos != -1 && optionsForRegion.length() > foundPos + optionName.length()) {
                optionsForRegion = optionsForRegion.substring(foundPos + optionName.length());//allow for a space or '=' at the end of the option name
                char operator = optionsForRegion.charAt(0);
                if (operator=='>' ) {//interpret the '>' symbol as '-' to create an integer
                    optionsForRegion = "-" + optionsForRegion.substring(1);
                }else{
                    //ignore '=' or a space
                    optionsForRegion = optionsForRegion.substring(1);
                }
                foundPos = optionsForRegion.indexOf(",");
                if (foundPos > 0) {
                    optionsForRegion = optionsForRegion.substring(0, foundPos);
                }
                return optionsForRegion.trim();
            }
        }
        return null;
    }

    public List<List<List<DataRegionHeading>>> getHeadingsLists(CellRegion headings, Sheet sheet, LoggedInConnection loggedInConnection) {
        List<List<List<DataRegionHeading>>> headingsLists = new ArrayList<List<List<DataRegionHeading>>>();
        for (int rowIndex = headings.getRow(); rowIndex <= headings.getLastRow(); rowIndex++) {
            List<List<DataRegionHeading>> row = new ArrayList<List<DataRegionHeading>>();
            for (int colIndex = headings.getColumn(); colIndex <= headings.getLastColumn(); colIndex++) {
                SCell cell = sheet.getInternalSheet().getCell(rowIndex, colIndex);
                String cellString = cell.getStringValue();
                if (cellString.length() == 0) {
                    row.add(null);
                } else {
                    // was just a name expression, now we allow an attribute also. May be more in future.
                    if (cellString.startsWith(".")) {
                        // currently only one attribute per cell, I suppose it could be many in future (available attributes for a name, a list maybe?)
                        row.add(Arrays.asList(new DataRegionHeading(cellString, true))); // we say that an attribuite heading defaults to writeable, it will defer to the name
                    } else {
                        try {

                            row.add(valueService.dataRegionHeadingsFromNames(nameService.parseQuery(loggedInConnection, cellString, loggedInConnection.getLanguages()), loggedInConnection));
                        } catch (Exception e) {
                            // todo, error handling??
                            e.printStackTrace();
                            row.add(null);
                        }
                    }
                }
            }
            headingsLists.add(row);
        }
        return headingsLists;
    }

    public static final String VALIDATION_SHEET = "VALIDATION_SHEET";

    public void addValidation(List<SName> namesForSheet, Sheet sheet, LoggedInConnection loggedInConnection){
        if (sheet.getBook().getSheet(VALIDATION_SHEET) == null){
            sheet.getBook().getInternalBook().createSheet(VALIDATION_SHEET);
        }
        Sheet validationSheet = sheet.getBook().getSheet(VALIDATION_SHEET);
        int numberOfValidationsAdded = 0;
        for (SName name : namesForSheet) {
            if (name.getRefersToSheetName().equals(sheet.getSheetName())) {
                if (name.getName().endsWith("Choice")) {
                    CellRegion choice = getCellRegionForSheetAndName(sheet, name.getName());
                    CellRegion chosen = getCellRegionForSheetAndName(sheet, name.getName().substring(0, name.getName().length() - "Choice".length()) + "Chosen");
                    if (choice != null && chosen != null) {
                        // ok I assume choice is a single cell
                        try{
                            List<Name> choiceNames = nameService.parseQuery(loggedInConnection, sheet.getInternalSheet().getCell(choice.getRow(), choice.getColumn()).getStringValue());// I think this will do it??
                            int row = 0;
                            for (Name name1 : choiceNames) {
                                validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded).setStringValue(name1.getDisplayNameForLanguages(loggedInConnection.getLanguages()));
                                row++;
                            }
                            Range validationValues = Ranges.range(validationSheet, 0, numberOfValidationsAdded, row, numberOfValidationsAdded);
                            validationValues.createName("az_Validation" + numberOfValidationsAdded);
                            Range chosenRange = Ranges.range(sheet, chosen.getRow(), chosen.getColumn(), chosen.getLastRow(), chosen.getLastColumn());

                            //chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "\"az_Validation" + numberOfValidationsAdded +"\"", null,
                            chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                    true, "title", "msg",
                                    true, Validation.AlertStyle.WARNING, "alert title", "alert msg");
/*                                    book.getInternalBook().addEventListener(
                                            new ModelEventListener() {
                                                @Override
                                                public void onEvent(ModelEvent modelEvent) {
                                                    System.out.println(modelEvent);
                                                    if (modelEvent.getName().equals("onDataValidationContentChange")){
                                                        modelEvent.getObjectId();
                                                    }
                                                }
                                            });*/
                        } catch (Exception e){
                            Range chosenRange = Ranges.range(sheet, chosen.getRow(), chosen.getColumn(), chosen.getLastRow(), chosen.getLastColumn());
                            chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "{\"" + e.getMessage() + "\"}", null,
                                    true, "title", "msg",
                                    true, Validation.AlertStyle.WARNING, "alert title", "alert msg");

                        }
                        numberOfValidationsAdded++;
                    }
                }
            }
        }
    }


    private void loadAdminData(LoggedInConnection loggedInConnection, Sheet sheet) throws Exception {
        List<SName> namesForSheet = getNamesForSheet(sheet);
        for (SName name : namesForSheet) {
            // Old one was case insensitive - not so happy about this. Will allow it on the prefix
            if (name.getName().toLowerCase().startsWith(azHeadings)) { // then we have a data region to deal with here
                String region = name.getName().substring(azHeadings.length()).toLowerCase(); // might well be an empty string
                fillAdminData(loggedInConnection, sheet, region, valueService, adminService);
            }
        }
    }


    public String fillAdminData(LoggedInConnection loggedInConnection, Sheet sheet, String region, ValueService valueService, AdminService adminService) throws Exception {
        String data = null;
        CellRegion headingsRange = getCellRegionForSheetAndName(sheet, "az_Headings" + region);
        int headingsRow = headingsRange.getRow();
        int headingsCol = headingsRange.getColumn();
        int firstHeading = headingsRange.getColumn();
        if (region.equals("data") && loggedInConnection.getNamesToSearch() != null) {
            Map<Set<com.azquo.memorydb.Name>, Set<Value>> shownValues = valueService.getSearchValues(loggedInConnection.getNamesToSearch());
            loggedInConnection.setValuesFound(shownValues);
            LinkedHashSet<com.azquo.memorydb.Name> nameHeadings = valueService.getHeadings(shownValues);
            int colNo = firstHeading + 1;
            int rowNo = 0;
            for (com.azquo.memorydb.Name name : nameHeadings) {
                sheet.getInternalSheet().getCell(headingsRow, headingsCol + colNo).setValue(name.getDefaultDisplayName());
                colNo++;
            }
            for (Set<com.azquo.memorydb.Name> names : shownValues.keySet()) {
                rowNo++;
                colNo = firstHeading;
                sheet.getInternalSheet().getCell(headingsRow + rowNo, headingsCol + colNo).setValue(valueService.addValues(shownValues.get(names)));
                colNo++;
                for (com.azquo.memorydb.Name name : nameHeadings) {
                    for (com.azquo.memorydb.Name valueName : names) {
                        if (valueName.findAllParents().contains(name)) {
                            sheet.getInternalSheet().getCell(headingsRow + rowNo, headingsCol + colNo).setValue(valueName.getDefaultDisplayName());
                        }
                    }
                    colNo++;
                }
            }
        } else {
            if (region.equals("databases")) {
                data = jacksonMapper.writeValueAsString(adminService.getDatabaseListForBusiness(loggedInConnection));
            } else if (region.equals("uploads")) {
                data = jacksonMapper.writeValueAsString(adminService.getUploadRecordsForDisplayForBusiness(loggedInConnection));
            } else if (region.equals("users")) {
                data = jacksonMapper.writeValueAsString(adminService.getUserListForBusiness(loggedInConnection));
            } else if (region.equals("permissions")) {
                data = jacksonMapper.writeValueAsString(adminService.getPermissionList(loggedInConnection));
            } else if (region.equals("reports")) {
                data = jacksonMapper.writeValueAsString(adminService.getReportList(loggedInConnection));
            }
            if (data == null) return "";
            int lastHeading = headingsRange.getColumnCount();
            int rowNo = 0;
            //strip the square brackets
            while (data.length() > 2) {
                Map<String, String> pairs = new HashMap<String, String>();
                data = readJsonData(data, pairs);
                if (data.startsWith("error:")) return data;
                rowNo++;
                for (int colNo = firstHeading; colNo < lastHeading; colNo++) {
                    String heading = sheet.getInternalSheet().getCell(headingsRow, headingsCol + colNo).getStringValue();
                    SCell cell = sheet.getInternalSheet().getCell(headingsRow + rowNo, headingsCol  + colNo);
                    String link = null;
                    int nameEnd = heading.indexOf(";");
                    if (nameEnd > 0) {
                        //this is all left over from older versions of the spreadsheet.....
                        link = heading.substring(nameEnd + 1);
                        heading = heading.substring(0, nameEnd);

                        if (link.startsWith("href=")) {
                            link = link.substring(5);
//                        linkStart = "<a href=";
                    } else if (link.startsWith("onclick=")) {
                        link = link.substring(8);
                        //linkStart = "<a href='#' onclick=";
                    } else {
                        link = null;
                    }
                }
                String valFound = pairs.get(heading);
                if (link != null) {
                    //new universal link...  REPORTS ONLY!
                    String linkAddr = "/api/Online/?opcode=loadsheet&connectionid=" + loggedInConnection.getConnectionId() + "&reportid=" + pairs.get("id");
                    //String linkAddr = evaluateExpression(link.replace("“", "\"").replace("”", "\""), pairs);//excel uses fancy quotes
                        cell.setHyperlink(new HyperlinkImpl(SHyperlink.HyperlinkType.URL,linkAddr,"link"));
                }
                if (valFound != null) {
                    try {
                        //if it can be parsed as a date, it is a date!
                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                        Date date = df.parse(valFound);
                        cell.setDateValue(date);
                    } catch (Exception e) {


                        cell.setValue(valFound);
                    }
                }
            }
        }
    }
    return "";
}

    public String readJsonData(String data, Map<String, String> pairs) throws Exception{
        //should use Jackson for this,....
        /*
        final String json = "{}";
       final ObjectMapper mapper = new ObjectMapper();
       final MapType type = mapper.getTypeFactory().constructMapType(
       Map.class, String.class, Object.class);
       final Map<String, Object> data = mapper.readValue(json, type);


        */
        int pos = 1;
        if (data.charAt(pos++) != '{') return jsonError(data, pos);
        while (data.charAt(pos++) == '"') {
            int endName = data.indexOf("\"", pos);
            if (endName < 0) return jsonError(data, pos);
            String jsonName = data.substring(pos, endName);
            pos = endName + 1;
            if (data.charAt(pos++) != ':') return jsonError(data, pos);
            String jsonValue;
            if (data.charAt(pos) != '"') {
                int endLine = data.indexOf("}", pos);
                endName = data.indexOf(",", pos);
                if (endName < 0 || (endName > 0 && endLine < endName)) endName = endLine;
                if (endName < 0) return jsonError(data, pos);
                jsonValue = data.substring(pos, endName);
                pos = endName;
            } else {
                pos++;
                endName = data.indexOf("\"", pos);
                if (endName < 0) return jsonError(data, pos);
                jsonValue = data.substring(pos, endName);
                pos = endName + 1;
            }
            pairs.put(jsonName, jsonValue);
            if (data.charAt(pos) == '}') {
                break;
            }
            if (data.charAt(pos++) != ',') return jsonError(data, pos);
        }
        return data.substring(pos + 1);


    }

    public String jsonError(String data, int pos) throws Exception{
        int end = data.length();
        if (pos < end - 30) {
            end = pos + 30;
        }
        throw new Exception("error: json parsing problem at " + data.substring(pos, end));
    }

    private String evaluateExpression(String expression, Map<String, String> pairs) throws Exception {
        //evaluates simple text expressions
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < expression.length()) {
            if (expression.charAt(pos) == '"') {
                int endQuote = expression.indexOf("\"", ++pos);
                if (endQuote < 0) throw new Exception("error: expression not understood " + expression);
                sb.append(expression.substring(pos, endQuote));
                pos = endQuote + 1;
            } else if (expression.charAt(pos) == '&' || expression.charAt(pos) == ' ') {
                pos++;
            } else {
                int endTerm = (expression + "&").indexOf("&", pos);
                String val = pairs.get(expression.substring(pos, endTerm).trim());
                if (val == null) throw new Exception("error: expression not understood " + expression);
                sb.append(val);
                pos = endTerm;
            }
        }
        return sb.toString();
    }



}

