package com.azquo.spreadsheet.view;

import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserChoice;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 03/03/15.
 * <p>
 * To manipulate the ZK book, practically speaking a lot of what's in here might be functionally similar to what is in AzquoBook (the old Aspose based rendering that needs to be removed)
 */
public class ZKAzquoBookUtils {

    private static final String azDataRegion = "az_DataRegion";
    static final String azOptions = "az_Options";
    private static final String CONTENTS = "contents(";
    /*
    CONTENTS is used to handle dependent ranges within the data region.   Thus one column in the data region may ask for a category, and the next a subcategory, which should be determined by the category
    For single cell 'chosen' ranges there is no problem - the choice for the subcategory may be defined as an Excel formula.
    For multicell ranges, use 'CONTENTS(rangechosen) to specify the subcategory....


     */

    private final SpreadsheetService spreadsheetService;
    private final UserChoiceDAO userChoiceDAO;
    private final UserRegionOptionsDAO userRegionOptionsDAO;
    private final RMIClient rmiClient;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    private final String reportParameters;

    public ZKAzquoBookUtils(SpreadsheetService spreadsheetService, UserChoiceDAO userChoiceDAO, UserRegionOptionsDAO userRegionOptionsDAO, RMIClient rmiClient) {
        this(spreadsheetService, userChoiceDAO, userRegionOptionsDAO, null, rmiClient);// no report parameters
    }

    private ZKAzquoBookUtils(SpreadsheetService spreadsheetService, UserChoiceDAO userChoiceDAO, UserRegionOptionsDAO userRegionOptionsDAO, String reportParameters, RMIClient rmiClient) {
        this.spreadsheetService = spreadsheetService;
        this.userChoiceDAO = userChoiceDAO;
        this.userRegionOptionsDAO = userRegionOptionsDAO;
        this.rmiClient = rmiClient;
        this.reportParameters = reportParameters;
    }

    public boolean populateBook(Book book, int valueId) {
        return populateBook(book, valueId, false);
    }

    public boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae) {
        long track = System.currentTimeMillis();
        String imageStoreName = "";
        boolean showSave = false;
        //book.getInternalBook().getAttribute(ZKAzquoBookProvider.BOOK_PATH);
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);

        Map<String, String> userChoices = new HashMap<>();
        // get the user choices for the report. Can be drop down values, sorting/highlighting etc.
        // a notable point here is that the user choices don't distinguish between sheets
        List<UserChoice> allChoices = userChoiceDAO.findForUserId(loggedInUser.getUser().getId());
        for (UserChoice uc : allChoices) {
            userChoices.put(uc.getChoiceName().toLowerCase(), uc.getChoiceValue()); // make case insensitive
        }
        /* right, we need to deal with setting the choices according to report parameters
        category = `Men's suits`, month = last
        currently allowing first or last, if first or last required as names add quotes
        I could rip off the parsing from string utils though it seems rather verbose, really what I want is tokenizing based on , but NOT in the quoted areas
        */

        final String FIRST_PLACEHOLDER = "||FIRST||";
        final String LAST_PLACEHOLDER = "||LAST||";

        if (reportParameters != null) {
            boolean inQuotes = false;
            int ruleStart = 0;
            int currentIndex = 0;
            for (char c : reportParameters.toCharArray()) {
                if (c == '`') { // can't use Name.Quote, it's on the DB server hmmmmm . . .
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) { // try to parse out the rule
                    String pair = reportParameters.substring(ruleStart, currentIndex).trim();
                    if (pair.indexOf("=") > 0) {
                        final String[] split = pair.split("=");
                        String value = split[1].trim();
                        if (value.equalsIgnoreCase("first")) {
                            value = FIRST_PLACEHOLDER;
                        } else if (value.startsWith("last")) {
                            value = LAST_PLACEHOLDER;
                        } else if (value.startsWith("`")) { // then zap the quotes
                            value = value.replace('`', ' ').trim();
                        }
                        userChoices.put(split[0].toLowerCase().trim(), value); // ok and I need to add special cases for first and last
                    }
                    ruleStart = currentIndex + 1;
                }
                currentIndex++;
            }
        }

        String context = "";
        Map<String, List<String>> choiceOptionsMap = resolveChoiceOptions(book, loggedInUser);

        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            // these two lines moved from below the unmerge command, shouldn't be a big problem - I need the options to check that we're setting valid options directly below
            // names are per book, not sheet. Perhaps we could make names the big outside loop but for the moment I'll go by sheet - convenience function
            List<SName> namesForSheet = getNamesForSheet(sheet);
            // we must resolve the options here before filling the ranges as they might feature "as" name populating queries

            /* commenting locked for the mo
            // run through every cell unlocking to I can later lock. Setting locking on a large selection seems to zap formatting
            for (int i = 0; i <= sheet.getLastRow(); i++) {
                for (int j = 0; j < sheet.getLastColumn(i); j++){
                    Range selection =  Ranges.range(sheet, i,j);
                    CellStyle oldStyle = selection.getCellStyle();
                    EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                    newStyle.setLocked(false);
                    selection.setCellStyle(newStyle);
                }
            }*/

            // choices can be a real pain, I effectively need to keep resolving them until they don't change due to choices being based on choices (dependencies in excel)
            boolean resolveChoices = true;
            int attempts = 0;
            while (resolveChoices) {
                context = "";
                // Now I need to run through all choices setting from the user options IF it is valid and the first on the menu if it is not
                for (SName sName : namesForSheet) {
                    if (sName.getName().endsWith("Chosen")) {
                        CellRegion chosen = getCellRegionForSheetAndName(sheet, sName.getName());
                        String choiceName = sName.getName().substring(0, sName.getName().length() - "Chosen".length()).toLowerCase();
                        if (chosen != null) {
                            if (chosen.getRowCount() == 1 && chosen.getColumnCount() == 1) { // I think I may keep this constraint even after
                                // need to check that this choice is actually valid, so we need the choice query - should this be using the query as a cache?
                                List<String> validOptions = choiceOptionsMap.get(choiceName + "choice");
                                if (validOptions != null) {
                                    String userChoice = userChoices.get(choiceName);
                                    if (FIRST_PLACEHOLDER.equals(userChoice)) {
                                        userChoice = validOptions.get(0);
                                    }
                                    if (LAST_PLACEHOLDER.equals(userChoice)) {
                                        userChoice = validOptions.get(validOptions.size() - 1);
                                    }
                                    while (userChoice != null && !validOptions.contains(userChoice) && userChoice.contains("->")) {
                                        //maybe the user choice is over -specified. (e.g from drilldown or removal of conflicting names)  Try removing the super-sets
                                        userChoice = userChoice.substring(userChoice.indexOf("->") + 2);
                                    }
                                    if (userChoice != null && validOptions.contains(userChoice)) {
                                        sheet.getInternalSheet().getCell(chosen.getRow(), chosen.getColumn()).setStringValue(userChoice);
                                        context += choiceName + " = " + userChoice + ";";
                                    } else if (validOptions != null && !validOptions.isEmpty()) { // just set the first for the mo.
                                        sheet.getInternalSheet().getCell(chosen.getRow(), chosen.getColumn()).setStringValue(validOptions.get(0));
                                    }
                                }
                            }
                        }
                    }
                }
                resolveChoices = false;
                // ok so we've set them but now derived choices may have changed, no real option except to resolve again and see if there's a difference
                Map<String, List<String>> newChoiceOptionsMap = resolveChoiceOptions(book, loggedInUser);
                if (!newChoiceOptionsMap.equals(choiceOptionsMap)) { // I think the equals will do the job correctly here
                    System.out.println("choices changed as a result of chosen, resolving again");
                    resolveChoices = true;
                    choiceOptionsMap = newChoiceOptionsMap;
                }
                attempts++;
                if (attempts > 10) {
                    System.out.println("10 attempts at resolving choices, odds on there's some kind of circular reference, stopping");
                }
            }
            resolveQueries(sheet, loggedInUser); // after all options sorted should be ok
            // ok the plan here is remove all the merges then put them back in after the regions are expanded.
            List<CellRegion> merges = new ArrayList<>(sheet.getInternalSheet().getMergedRegions());
            for (CellRegion merge : merges) {
                CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
            }
            // and now we want to run through all regions for this sheet, will look at the old code for this, I think it's fill region that we take cues from
            boolean fastLoad = false; // skip some checks, initially related to saving
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on the prefix
                if (name.getName().equalsIgnoreCase("az_FastLoad")) {
                    fastLoad = true;
                }
                if (name.getName().equals("ImageStoreName")) {
                    imageStoreName = getRegionValue(sheet, name.getRefersToCellRegion());

                }
                if (name.getName().startsWith(azDataRegion)) { // then we have a data region to deal with here
                    String region = name.getName().substring(azDataRegion.length()); // might well be an empty string
                    SName optionsRegion = sheet.getBook().getInternalBook().getNameByName(azOptions + region);
                    String source = "";
                    if (optionsRegion != null) {
                        source = getSnameCell(optionsRegion).getStringValue();
                    }
                    UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);
                    UserRegionOptions userRegionOptions2 = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                    //only these five fields to be taken from the table.   other fields - hide_rows, sortable, row_limit, column_limit should be removed from SQL table
                    if (userRegionOptions2 != null) {
                        if (userRegionOptions.getSortColumn() == null) {
                            userRegionOptions.setSortColumn(userRegionOptions2.getSortColumn());
                            userRegionOptions.setSortColumnAsc(userRegionOptions2.getSortColumnAsc());
                        }
                        if (userRegionOptions.getSortRow() == null) {
                            userRegionOptions.setSortRow(userRegionOptions2.getSortRow());
                            userRegionOptions.setSortRowAsc(userRegionOptions2.getSortRowAsc());
                        }
                        userRegionOptions.setHighlightDays(userRegionOptions2.getHighlightDays());
                    }

                    fillRegion(sheet, reportId, region, valueId, userRegionOptions, loggedInUser, userChoices);
                }
            }
            System.out.println("regions populated in : " + (System.currentTimeMillis() - track) + "ms");

            // this is a pain, it seems I need to call 2 functions on each formula cell or the formula may not be calculated. ANNOYING!
            // can't do this in the fill region as formulae need to be dealt with outside
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
            /* Now formulae should have been calculated check if anything might need to be saved
            I'd avoided doing this but now I am it's useful for restoring values and checking for overlapping data regions.
            so similar loop to above - also we want to check for the logic of using the loaded values
            */
            if (!fastLoad) {
                for (SName name : namesForSheet) {
                    if (name.getName().startsWith(azDataRegion)) {
                        String region = name.getName().substring(azDataRegion.length());
                        CellRegion displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);
                        final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, region);
                        if (displayDataRegion != null && sentCells != null) {
                            int startRow = displayDataRegion.getRow();
                            int endRow = displayDataRegion.getLastRow();
                            int startCol = displayDataRegion.getColumn();
                            int endCol = displayDataRegion.getLastColumn();
                            for (int row = startRow; row <= endRow; row++) {
                                for (int col = startCol; col <= endCol; col++) {
                                    SCell sCell = sheet.getInternalSheet().getCell(row, col);
                                    if (sentCells.getData() != null && sentCells.getData().size() > row - name.getRefersToCellRegion().getRow() // as ever check ranges of the data region vs actual data sent.
                                            && sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).size() > col - name.getRefersToCellRegion().getColumn()) {
                                        CellForDisplay cellForDisplay = sentCells.getData().get(row - startRow).get(col - startCol);
                                        if (cellForDisplay.getSelected()) {
                                            book.getInternalBook().setAttribute(OnlineController.CELL_SELECT, row + "," + col);
                                        }
                                        if (sCell.getType() == SCell.CellType.FORMULA) {
                                            if (sCell.getFormulaResultType() == SCell.CellType.NUMBER) { // then check it's value against the DB one . . .
                                                if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB, only if not ignored
                                                        sCell.setNumberValue(cellForDisplay.getDoubleValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        cellForDisplay.setDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                        showSave = true;
                                                    }

                                                }
                                                if (sCell.getCellStyle().getDataFormat().toLowerCase().contains("m") && cellForDisplay.getStringValue().length() == 0) {
                                                    cellForDisplay.setStringValue(df.format(sCell.getDateValue()));//set a string value as our date for saving purposes
                                                }
                                            } else if (sCell.getFormulaResultType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB
                                                        sCell.setStringValue(cellForDisplay.getStringValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        cellForDisplay.setStringValue(sCell.getStringValue());
                                                        showSave = true;
                                                    }
                                                }
                                            }
                                        } else {
                                            // we now want to compare in the case of non formulae changes - a value from one data region importing into another,
                                            // the other typically being of the "ad hoc" no row headings type
                                            // notably this will hit a lot of cells (all the rest)
                                            if (sCell.getType() == SCell.CellType.NUMBER) {
                                                if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                    cellForDisplay.setDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                    showSave = true;
                                                }
                                            } else if (sCell.getType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    cellForDisplay.setStringValue(sCell.getStringValue());
                                                    showSave = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
/* commenting locking for the moment
            // now protect. Doing so before seems to cause problems
            Ranges.range(sheet).protectSheet("azquo",
                    true, //allowSelectingLockedCells
                    true, //allowSelectingUnlockedCells,
                    true, //allowFormattingCells
                    true, //allowFormattingColumns
                    true, //allowFormattingRows
                    true, //allowInsertColumns
                    true, //allowInsertRows
                    true, //allowInsertingHyperlinks
                    true, //allowDeletingColumns
                    true, //boolean allowDeletingRows
                    true, //allowSorting
                    true, //allowFiltering
                    true, //allowUsingPivotTables
                    true, //drawingObjects
                    true  //boolean scenarios
            );*/

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
                // the boolean meant JUST horizontally, I don't know why. Hence false.
                CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), false);
            }
        }
        List<SName> dependentRanges = addValidation(loggedInUser, book, choiceOptionsMap, userChoices);
        if (dependentRanges.size() > 0) {
            resolveDependentChoiceOptions(dependentRanges, book, loggedInUser);
        }

        loggedInUser.setImageStoreName(imageStoreName);
        loggedInUser.setContext(context);

        // after stripping off some redundant exception throwing this was the only possibility left, ignore it
        try {
            rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearSessionLog(loggedInUser.getDataAccessToken());
        } catch (Exception ignored) {
        }
        return showSave;
    }

    // like rangeToStringLists in azquobook


    private List<List<String>> nameToStringLists(SName sName) {
        List<List<String>> toReturn = new ArrayList<>();
        if (sName == null) return toReturn;
        CellRegion region = sName.getRefersToCellRegion();
        if (region == null) return toReturn;
        SSheet sheet = sName.getBook().getSheetByName(sName.getRefersToSheetName());
        for (int rowIndex = region.getRow(); rowIndex <= region.getLastRow(); rowIndex++) {
            List<String> row = new ArrayList<>();
            toReturn.add(row);
            for (int colIndex = region.getColumn(); colIndex <= region.getLastColumn(); colIndex++) {
                SCell cell = sheet.getCell(rowIndex, colIndex);
                // being paraniod?
                if (cell != null && cell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    cell.getFormulaResultType();
                    cell.clearFormulaResultCache();
                }
                // I assume non null cell has a non null string value, this may not be true. Also will I get another type of exception?
                try {
                    if (cell != null) {
                        if (cell.getType() == SCell.CellType.NUMBER) {
                            String numberGuess = cell.getNumberValue() + "";
                            if (numberGuess.endsWith(".0")) {
                                numberGuess = numberGuess.substring(0, numberGuess.length() - 2);
                            }
                            row.add(numberGuess);
                        } else {
                            row.add(cell.getStringValue());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return toReturn;
    }

    private static String getRegionValue(Sheet sheet, CellRegion region) {
        try {
            return sheet.getInternalSheet().getCell(region.getRow(), region.getColumn()).getStringValue();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void fillRegion(Sheet sheet, int reportId, String region, int valueId, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser, Map<String, String> userChoices) {
        SName columnHeadingsDescription = sheet.getBook().getInternalBook().getNameByName("az_ColumnHeadings" + region);
        SName rowHeadingsDescription = sheet.getBook().getInternalBook().getNameByName("az_RowHeadings" + region);
        SName contextDescription = sheet.getBook().getInternalBook().getNameByName("az_Context" + region);
        String errorMessage = null;
        if (columnHeadingsDescription != null && rowHeadingsDescription == null) {
            List<List<String>> colHeadings = nameToStringLists(columnHeadingsDescription);
            List<List<CellForDisplay>> dataRegionCells = new ArrayList<>();
            CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);
            for (int rowNo = 0; rowNo < dataRegion.getRowCount(); rowNo++) {
                List<CellForDisplay> oneRow = new ArrayList<>();
                for (int colNo = 0; colNo < dataRegion.getColumnCount(); colNo++) {
                    oneRow.add(new CellForDisplay(false, "", 0, false, rowNo, colNo, true, false)); // make these ignored. Edd note : I'm not praticularly happy about this, sent data should be sent data, this is just made up . . .
                }
                dataRegionCells.add(oneRow);
            }
            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(colHeadings, null, dataRegionCells, null, null, null);
            loggedInUser.setSentCells(reportId, region, cellsAndHeadingsForDisplay);
            return;
        }

        if (columnHeadingsDescription != null) {
            try {

                List<List<String>> contextList = nameToStringLists(contextDescription);
                List<List<String>> rowHeadingList = nameToStringLists(rowHeadingsDescription);
                //check if this is a pivot - if so, then add in any additional filter needed
                SName pivotFilters = sheet.getBook().getInternalBook().getNameByName("az_PivotFilters");
                if (pivotFilters != null) {
                    String[] filters = getSnameCell(pivotFilters).getStringValue().split(",");
                    for (String filter : filters) {
                        filter = filter.trim();
                        try {
                           List<String> possibleOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), "`" + filter + "` children", loggedInUser.getLanguages());
                            List<String> choiceOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), "`az_" + filter + "` children", loggedInUser.getLanguages());
                            if (possibleOptions.size() != choiceOptions.size() && choiceOptions.size() > 0) {
                                List<String> additionalContext = new ArrayList<>();
                                additionalContext.add("az_" + filter);
                                contextList.add(additionalContext);
                            }
                        } catch (Exception e) {
                            //ignore - no choices yet made
                        }
                    }

                }
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = spreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), region, valueId, rowHeadingList, nameToStringLists(columnHeadingsDescription),
                        contextList, userRegionOptions);
                loggedInUser.setSentCells(reportId, region, cellsAndHeadingsForDisplay);
                // now, put the headings into the sheet!
                // might be factored into fill range in a bit
                CellRegion displayRowHeadings = getCellRegionForSheetAndName(sheet, "az_DisplayRowHeadings" + region);
                CellRegion displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);

                int rowsToAdd;
                int colsToAdd;

                if (displayDataRegion != null) {
                    // add rows
                    int maxCol = 0;
                    for (int i = 0; i <= sheet.getLastRow(); i++) {
                        if (sheet.getLastColumn(i) > maxCol) {
                            maxCol = sheet.getLastColumn(i);
                        }
                    }
                    if (cellsAndHeadingsForDisplay.getRowHeadings() != null && (displayDataRegion.getRowCount() < cellsAndHeadingsForDisplay.getRowHeadings().size()) && displayDataRegion.getRowCount() > 2) { // then we need to expand, and there is space to do so (3 or more allocated already)
                        rowsToAdd = cellsAndHeadingsForDisplay.getRowHeadings().size() - (displayDataRegion.getRowCount());
                        int insertRow = displayDataRegion.getRow() + displayDataRegion.getRowCount() - 1; // last but one row
                        Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
                        Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
                        CellOperationUtil.insertRow(insertRange);
                        CellOperationUtil.paste(copySource, insertRange);
                        int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
                        if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                            insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
                        }
                    }
                    // add columns
                    int maxRow = sheet.getLastRow();
                    if (displayDataRegion.getColumnCount() < cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() && displayDataRegion.getColumnCount() > 2) { // then we need to expand
                        colsToAdd = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() - (displayDataRegion.getColumnCount());
                        int insertCol = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1; // I think this is correct, just after the second column?
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
                    displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);

                    int row;
                    // ok there should be the right space for the headings
                    if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
                        boolean isHierarchy = isHierarchy(cellsAndHeadingsForDisplay.getRowHeadingsSource());
                        int rowHeadingCols = cellsAndHeadingsForDisplay.getRowHeadings().get(0).size();
                        colsToAdd = rowHeadingCols - displayRowHeadings.getColumnCount();
                        if (colsToAdd > 0) {
                            int insertCol = displayRowHeadings.getColumn() + displayRowHeadings.getColumnCount() - 1;
                            Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1); //
                            CellOperationUtil.insert(insertRange.toColumnRange(), Range.InsertShift.RIGHT, Range.InsertCopyOrigin.FORMAT_LEFT_ABOVE);
                        }
                        row = displayRowHeadings.getRow();
                        int lineNo = 0;
                        List<String> lastRowHeadings = new ArrayList<>(cellsAndHeadingsForDisplay.getRowHeadings().size());
                        for (List<String> rowHeading : cellsAndHeadingsForDisplay.getRowHeadings()) {

                            int col = displayRowHeadings.getColumn();
                            int startCol = col;
                            for (String heading : rowHeading) {
                                if (heading != null && (sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty())) { // as with AzquoBook don't overwrite existing cells when it comes to headings
                                    sheet.getInternalSheet().getCell(row, col).setValue(heading);
                                    if (lineNo > 0 && lastRowHeadings.get(col - startCol) != null && lastRowHeadings.get(col - startCol).equals(heading)) {
                                        //disguise the heading by making foreground colour = background colour
                                        Range selection = Ranges.range(sheet, row, col, row, col);
                                        CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, col).getCellStyle().getBackColor().getHtmlColor());

                                    }
                                }
                                col++;

                            }
                            lineNo++;
                            if (isHierarchy) {
                                int sameValues = 0;
                                if (lineNo > 0)
                                    for (sameValues = 0; sameValues < lastRowHeadings.size(); sameValues++) {
                                        if (!rowHeading.get(sameValues).equals(lastRowHeadings.get(sameValues))) {
                                            break;
                                        }
                                    }

                                //format the row headings for hierarchy.  Each total level has a different format.   clear visible names in all but on eheading
                                if (sameValues < rowHeading.size() - 1) {
                                    int totalCount = rowHeading.size() - sameValues;
                                    //this is a total line
                                    Range selection = Ranges.range(sheet, row - 1, displayRowHeadings.getColumn(), row - 1, displayDataRegion.getColumn() + displayDataRegion.getColumnCount());
                                    SCell lineFormat = getSnameCell(sheet.getBook().getInternalBook().getNameByName("az_totalFormat" + totalCount + region));

                                    if (lineFormat != null) {
                                        CellOperationUtil.applyBackColor(selection, lineFormat.getCellStyle().getBackColor().getHtmlColor());
                                        CellOperationUtil.applyFontColor(selection, lineFormat.getCellStyle().getFont().getColor().getHtmlColor());
                                    } else {
                                        CellOperationUtil.applyFontBoldweight(selection, Font.Boldweight.BOLD);
                                    }
                                    Ranges.range(sheet, row - 1, displayRowHeadings.getColumn() + sameValues + 1, row - 1, displayRowHeadings.getColumn() + displayRowHeadings.getColumnCount()).clearContents();
                                }
                                if (sameValues > 0) {
                                    Range selection = Ranges.range(sheet, row, displayRowHeadings.getColumn(), row, displayRowHeadings.getColumn() + sameValues - 1);
                                    CellOperationUtil.clearStyles(selection);
                                    CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, displayRowHeadings.getColumn()).getCellStyle().getBackColor().getHtmlColor());

                                }
                            }
                            lastRowHeadings = rowHeading;
                            row++;
                        }
                        if (isHierarchy) {
                            //paste in row headings
                            String rowHeadingsString = cellsAndHeadingsForDisplay.getRowHeadingsSource().get(0).get(0);
                            if (rowHeadingsString.toLowerCase().startsWith("permute(")) {
                                String[] rowHeadings = rowHeadingsString.substring("permute(".length(), rowHeadingsString.length() - 1).split(",");
                                int hrow = displayRowHeadings.getRow() - 1;
                                int hcol = displayRowHeadings.getColumn();
                                for (String rowHeading : rowHeadings) {
                                    rowHeading = rowHeading.replace("`","");
                                    String colHeading = multiList(loggedInUser,"az_" + rowHeading,"`" + rowHeading + "` children");
                                    if (colHeading.equals("[all]")) colHeading = rowHeading;
                                    sheet.getInternalSheet().getCell(hrow, hcol++).setStringValue(colHeading);
                                }

                            }
                        }
                    }
                    //← → ↑ ↓ ↔ ↕ ah I can just paste it here, thanks IntelliJ :)
                    CellRegion displayColumnHeadings = getCellRegionForSheetAndName(sheet, "az_DisplayColumnHeadings" + region);
                    if (displayColumnHeadings != null) {
                        row = displayColumnHeadings.getRow();
                        for (List<String> colHeading : cellsAndHeadingsForDisplay.getColumnHeadings()) {
                            boolean columnSort = false;
                            if (row - displayColumnHeadings.getRow() == cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1 && userRegionOptions.getSortable()) { // meaning last row of headings and sortable
                                columnSort = true;
                            }
                            int col = displayColumnHeadings.getColumn();
                            for (String heading : colHeading) {
                                if (columnSort && !heading.equals(".")) { // don't sort "." headings they are probably derived in the spreadsheet
                                    String sortArrow = " ↕";
                                    if (heading.equals(userRegionOptions.getSortColumn())) {
                                        sortArrow = userRegionOptions.getSortColumnAsc() ? " ↑" : " ↓";
                                    }
                                    if (sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty()) {
                                        sheet.getInternalSheet().getCell(row, col).setValue(heading + sortArrow);
                                    } else {
                                        sheet.getInternalSheet().getCell(row, col).setValue(sheet.getInternalSheet().getCell(row, col).getValue() + sortArrow);
                                    }
                                    String value = sheet.getInternalSheet().getCell(row, col).getStringValue();
                                    value = value.substring(0, value.length() - 2);
                                    Range chosenRange = Ranges.range(sheet, row, col, row, col);
                                    // todo, investigate how commas would fit in in a heading name
                                    // think I'll just zap em for the mo.
                                    value = value.replace(",", "");
                                    chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true,
                                            value + " ↕," + value + " ↑," + value + " ↓", null,
                                            true, "Select Sorting", "",
                                            true, Validation.AlertStyle.WARNING, "Sort Column", "This is a sortable column, its value should not be manually altered.");
                                } else if (heading != null && (sheet.getInternalSheet().getCell(row, col).isNull() || sheet.getInternalSheet().getCell(row, col).getStringValue().length() == 0)) { // vanilla, overwrite if not
                                    sheet.getInternalSheet().getCell(row, col).setValue(heading);
                                }
                                col++;
                            }
                            row++;
                        }

                        // for the moment don't allow user coolum sorting (row heading sorting). Shouldn't be too difficult to add

/*                if (sortable != null && sortable.equalsIgnoreCase("all")) { // criteria from azquobook to make row heading sortable
                }*/
                    }
                    displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);

                    row = displayDataRegion.getRow();
                    List<String> bottomColHeadings = cellsAndHeadingsForDisplay.getColumnHeadings().get(cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1); // bottom of the col headings if they are multi layered
                    if (cellsAndHeadingsForDisplay.getData() != null) {
                        for (List<CellForDisplay> rowCellValues : cellsAndHeadingsForDisplay.getData()) {
                            int col = displayDataRegion.getColumn();
                            int localCol = 0;
                            for (CellForDisplay cellValue : rowCellValues) {
                                if (!cellValue.getStringValue().isEmpty() && !bottomColHeadings.get(localCol).equals(".")) { // then something to set. Note : if col heading ON THE DB SIDE is . then don't populate
                                    // the notable thing ehre is that ZK uses the object type to work out data type
                                    SCell cell = sheet.getInternalSheet().getCell(row, col);
                                    // logic I didn't initially implement : don't overwrite if there's a formulae in there
                                    boolean hasValue = false;
                                    if (cell.getType() != SCell.CellType.FORMULA) {
                                        String format = cell.getCellStyle().getDataFormat();
                                        if (format.toLowerCase().contains("m")) {//allow users to format their own dates.  All dates on file as values are yyyy-MM-dd
                                            try {
                                                Date date;
                                                if (format.contains("h")) {
                                                    SimpleDateFormat dfLocal = new SimpleDateFormat(format);
                                                    date = dfLocal.parse(cellValue.getStringValue());
                                                } else {
                                                    date = df.parse(cellValue.getStringValue());
                                                }
                                                if (date != null) {
                                                    cell.setValue(date.getTime() / (1000 * 3600 * 24) + 25569);//convert date to days relative to 1970
                                                    hasValue = true;
                                                }
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        if (!hasValue) {
                                            if (cell.getCellStyle().getFont().getName().equalsIgnoreCase("Code EAN13")) { // then a special case, need to use barcode encoding
                                                cell.setValue(SpreadsheetService.prepareEAN13Barcode(cellValue.getStringValue()));// guess we'll see how that goes!
                                            } else {
                                                if (NumberUtils.isNumber(cellValue.getStringValue())) {
                                                    cell.setValue(cellValue.getDoubleValue());// think that works . . .
                                                } else {
                                                    cell.setValue(cellValue.getStringValue());// think that works . . .
                                                }
                                            }
                                        }
                                        // see if this works for highlighting
                                        if (cellValue.isHighlighted()) {
                                            CellOperationUtil.applyFontColor(Ranges.range(sheet, row, col), "#FF0000");
                                        }
                                    /* commented for the moment, requires the overall unlock per sheet followed by the protect later
                                    if (cellValue.isLocked()){
                                        Range selection =  Ranges.range(sheet, row, col);
                                        CellStyle oldStyle = selection.getCellStyle();
                                        EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                                        newStyle.setLocked(true);
                                        selection.setCellStyle(newStyle);
                                    }*/
                                    }
                                }
                                col++;
                                localCol++;
                            }
                            row++;
                        }
                    }
                }
            } catch (RemoteException re) {
                // is printing the stack trace going to jam the logs unnecessarily?
                Throwable t = re.detail.getCause();
                if (t != null) {
                    errorMessage = t.getLocalizedMessage();
                    t.printStackTrace();
                } else {
                    errorMessage = re.getMessage();
                    re.printStackTrace();
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
                e.printStackTrace();
            }
            if (errorMessage != null) {
                // maybe move to the top left? Unsure - YES!
                int rowNo = 0;
                int colNo = 0;
                while (sheet.getInternalSheet().getColumn(colNo).isHidden() && colNo < 100) colNo++;
                while (sheet.getInternalSheet().getRow(rowNo).isHidden() && rowNo < 100) rowNo++;
                String eMessage = "Unable to find matching header and context regions for this data region : az_DataRegion" + region + " : " + errorMessage;
                sheet.getInternalSheet().getCell(rowNo, colNo).setStringValue(eMessage);
                /*
                CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);// this function should not be called without a valid data region
                if (dataRegion != null) {
                    sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching header and context regions for this data region : az_DataRegion" + region + " : " + errorMessage);
                } else {
                    System.out.println("no region found for az_DataRegion" + region);
                }
                */
            }
        } else {
            CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);// this function should not be called without a valid data region
            if (dataRegion != null) {
                sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching header and context regions for this data region : az_DataRegion" + region);
            } else {
                System.out.println("no region found for az_DataRegion" + region);
            }
        }
    }

    static List<SName> getNamesForSheet(Sheet sheet) {
        List<SName> names = new ArrayList<>();
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(sheet.getSheetName())) {
                names.add(name);
            }
        }
        Collections.sort(names, (o1, o2) -> (o1.getName().toUpperCase().compareTo(o2.getName().toUpperCase())));
        return names;
    }

    private boolean isHierarchy(List<List<String>> headings) {
        for (List<String> oneCol : headings) {
            for (String oneHeading : oneCol) {
                if (oneHeading.toLowerCase().contains("hierarchy ") || oneHeading.toLowerCase().contains("permute")) {
                    return true;
                }
            }
        }
        return false;
    }

    // this works out case insensitive based on the API, I've made this static for convenience? Is is a problem? Maybe the code calling it should be in here
    // todo - reslove static question!

    public static CellRegion getCellRegionForSheetAndName(Sheet sheet, String name) {
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        try {
            if (toReturn == null) {// often may fail with explicit sheet name
                toReturn = sheet.getBook().getInternalBook().getNameByName(name);
            }
            if (toReturn != null && toReturn.getRefersToSheetName().equals(sheet.getSheetName())) {
                return toReturn.getRefersToCellRegion();
            }
            return null;
        } catch (Exception e) {
            return null;//the name exists, but has a dud pointer to a range
        }
    }

    public static SCell getSnameCell(SName sName) {
        if (sName == null) return null;
        return sName.getBook().getSheetByName(sName.getRefersToSheetName()).getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
    }


    public static final String VALIDATION_SHEET = "VALIDATION_SHEET";

    /* This did return a map with the query as the key but I'm going to change this to the name, it will save unnecessary query look ups later.
     This adds one caveat : the options match the choice name at the time the function ran - if the query in the choice cell updates this needs to be run again
       */

    Map<String, List<String>> resolveChoiceOptions(Book book, LoggedInUser loggedInUser) {
        Map<String, List<String>> toReturn = new HashMap<>();
        for (SName name : book.getInternalBook().getNames()) {
            //check to create pivot filter choices....

            if (name.getName().endsWith("Choice") && name.getRefersToCellRegion() != null) {
                // ok I assume choice is a single cell
                List<String> choiceOptions = new ArrayList<>(); // was null, see no help in that
                // new lines from edd to try to resolve choice stuff
                SCell choiceCell = getSnameCell(name);
                // as will happen to the whole sheet later
                if (choiceCell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    choiceCell.getFormulaResultType();
                    choiceCell.clearFormulaResultCache();
                }
                //System.out.println("Choice cell : " + choiceCell);
                String query = choiceCell.getStringValue();
                if (!query.toLowerCase().contains("contents(")) {//FIRST PASS - MISS OUT ANY QUERY CONTAINING 'contents('
                    if (query.toLowerCase().contains("default")) {
                        query = query.substring(0, query.toLowerCase().indexOf("default"));
                    }
                    try {
                        if (query.startsWith("\"") || query.startsWith("“")) {
                            //crude - if there is a comma in any option this will fail
                            query = query.replace("\"", "").replace("“", "").replace("”", "");
                            String[] choices = query.split(",");
                            Collections.addAll(choiceOptions, choices);
                        } else {
                            choiceOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getLanguages());
                        }
                    } catch (Exception e) {
                        choiceOptions.add(e.getMessage());
                    }
                    toReturn.put(name.getName().toLowerCase(), choiceOptions);
                }
            }

        }
        return toReturn;
    }

    private String rangeToText(int row, int col) {
        if (col > 26) {
            int hbit = (col - 1) / 26;
            return "$" + (char) (hbit + 64) + (char) (col - hbit * 26 + 64) + "$" + (row + 1);
        }
        return "$" + (char) (col + 65) + "$" + (row + 1);
    }


    private void resolveDependentChoiceOptions(List<SName> dependentRanges, Book book, LoggedInUser loggedInUser) {
        Sheet validationSheet = book.getSheet(VALIDATION_SHEET);
        SSheet vSheet = validationSheet.getInternalSheet();
        for (SName name : dependentRanges) {
            String dependentName = name.getName().substring(0, name.getName().length() - 6);//remove 'choice'
            SCell choiceCell = getSnameCell(name);
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
                        vSheet.getCell(0, targetCol + optionNo).setStringValue(optionVal);
                        String newQuery = query.substring(0, contentPos) + optionVal + query.substring(contentPos + catEnd + CONTENTS.length() + 1);
                        try {
                            List<String> optionList = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), newQuery, loggedInUser.getLanguages());
                            if (optionList.size() > maxSize) maxSize = optionList.size();
                            int rowOffset = 1;
                            for (String option : optionList) {
                                vSheet.getCell(rowOffset++, targetCol + optionNo).setStringValue(option);
                            }
                        } catch (Exception e) {
                            vSheet.getCell(1, validationSourceColumn).setStringValue(e.getMessage());
                            return;
                        }
                        optionNo++;
                    }
                    String lookupRange = "VALIDATION_SHEET!" + rangeToText(0, targetCol) + ":" + rangeToText(maxSize, targetCol + optionNo - 1);
                    //fill in blanks - they may come through as '0'
                    for (int col = 0; col < optionNo; col++) {
                        for (int row = 1; row < maxSize + 1; row++) {
                            if (vSheet.getCell(row, targetCol + col).getStringValue().length() == 0) {
                                vSheet.getCell(row, targetCol + col).setStringValue(" ");
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
                        String source = rangeToText(row, choiceSourceRange.getColumn());
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
                    //TODO NOW SET FORMULAE IN ORIGINAL SHEET AND NAME RANGE SELECTION, EFC 14/03/2016, need to clarify, perhaps
                }
            }
        }
    }

    private void resolveQueries(Sheet sheet, LoggedInUser loggedInUser) {
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name!=null && name.getName()!=null && name.getName().endsWith("Query")) {
                SCell queryCell = getSnameCell(name);
                // as will happen to the whole sheet later
                if (queryCell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    queryCell.getFormulaResultType();
                    queryCell.clearFormulaResultCache();
                }
                String queryString = queryCell.getStringValue();
                try {
                    rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                            .resolveQuery(loggedInUser.getDataAccessToken(), queryString, loggedInUser.getLanguages());// sending the same as choice but the goal here is execute server side. Generally to set an "As"
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    List<SName> addValidation(LoggedInUser loggedInUser, Book book, Map<String, List<String>> choiceOptionsMap, Map<String, String> userChoices) {
        if (book.getSheet(VALIDATION_SHEET) == null) {
            book.getInternalBook().createSheet(VALIDATION_SHEET);
        }
        Sheet validationSheet = book.getSheet(VALIDATION_SHEET);
        validationSheet.getInternalSheet().setSheetVisible(SSheet.SheetVisible.HIDDEN);
        int numberOfValidationsAdded = 0;
        List<SName> dependentRanges = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith("az_pivotfilters")) {
                String region = name.getName().substring("az_pivotFilters".length());
                String[] filters = getSnameCell(name).getStringValue().split(",");
                SName pivotHeadings = book.getInternalBook().getNameByName("az_PivotHeadings" + region);
                if (pivotHeadings != null) {
                    Sheet pSheet = book.getSheet(pivotHeadings.getRefersToSheetName());
                    CellRegion phRange = pivotHeadings.getRefersToCellRegion();
                    int headingRow = phRange.getRow();
                    int headingCol = phRange.getColumn();
                    int headingRows = phRange.getRowCount();
                    int filterCount = 0;
                    //on the top of pivot tables, the options are shown as pair groups separated by a space, sometimes on two rows, also separated by a space
                    for (String filter : filters) {
                        filter = filter.trim();
                        int rowOffset = filterCount % headingRows;
                        int colOffset = filterCount / headingRows;
                        int chosenRow = headingRow + rowOffset;
                        int chosenCol = headingCol + 3 * colOffset;
                        if (filterCount > 0) {
                            Range copySource = Ranges.range(pSheet, headingRow, headingCol, headingRow, headingCol + 1);
                            Range copyTarget = Ranges.range(pSheet, chosenRow, chosenCol, chosenRow, chosenCol + 1);
                            CellOperationUtil.paste(copySource, copyTarget);
                            //Ranges.range(pSheet, chosenRow, chosenCol + 1).setNameName(filter + "Chosen");

                        }
                        pSheet.getInternalSheet().getCell(chosenRow, chosenCol).setStringValue(filter);
                        String selected = multiList(loggedInUser, "az_" + filter, "`" + filter + "` children"); // forced case insensitive, a bit hacky but names in excel are case insensetive I think
                        if (selected == null || selected.length() == 0) {
                            selected = "[all]";
                        }
                        pSheet.getInternalSheet().getCell(chosenRow, chosenCol + 1).setStringValue(selected);
                         /*
                        validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded).setStringValue(filter);
                        int row = 0;
                        List<String> choiceOptions = choiceOptionsMap.get(filter.toLowerCase());
                        for (String choiceOption : choiceOptions) {
                            row++;// like starting at 1
                            validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded).setStringValue(choiceOption);
                        }
                        if (row > 0) { // if choice options is empty this will not work
                            Range validationValues = Ranges.range(validationSheet, 1, numberOfValidationsAdded, row, numberOfValidationsAdded);
                            Ranges.range(pSheet, chosenRow, chosenCol+1, chosenRow, chosenCol+1).setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                    //true, "title", "msg",
                                    true, "", "",
                                    false, Validation.AlertStyle.WARNING, "alert title", "alert msg");

                        }
                        numberOfValidationsAdded++;
                         */
                        filterCount++;
                    }
                }
            }

            if (name.getName().toLowerCase().endsWith("choice")) {
                String choiceName = name.getName().substring(0, name.getName().length() - "choice".length());
                SCell choiceCell = getSnameCell(name);
                System.out.println("debug:  trying to find the region " + choiceName + "chosen");
                SName chosen = book.getInternalBook().getNameByName(choiceName + "chosen"); // as ever I do wonder about these string literals
                if (name.getRefersToCellRegion() != null && chosen != null) {
                    Sheet sheet = book.getSheet(chosen.getRefersToSheetName());
                    CellRegion chosenRegion = chosen.getRefersToCellRegion();
                    if (chosenRegion!=null) {
                        List<String> choiceOptions = choiceOptionsMap.get(name.getName().toLowerCase());
                        boolean dataRegionDropdown = !getNamedDataRegionForRowAndColumnSelectedSheet(chosenRegion.getRow(), chosenRegion.getColumn(), sheet).isEmpty();
                        if (chosenRegion.getRowCount() == 1 || dataRegionDropdown) {// the second bit is to determine if it's in a data region, the choice drop downs are sometimes used (abused?) in such a manner, a bank of drop downs in a data region
                              String query = choiceCell.getStringValue();
                            int contentPos = query.toLowerCase().indexOf(CONTENTS);
                            if (contentPos < 0) {//not a dependent range
                                validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded).setStringValue(name.getName());
                                int row = 0;
                                // yes, this can null pointer but if it does something is seriously wrong
                                for (String choiceOption : choiceOptions) {
                                    row++;// like starting at 1
                                    validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded).setStringValue(choiceOption);
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
                            /*Unlike above where the setting of the choice has already been done we need to set the
                            existing choices here, hence why user choices is required for this. The check for userChoices not being null
                             is that it might be null when re adding the validation sheet when switching between sheets which can happen.
                             Under these circumstances I assume we won't need to re-do the filter adding. I guess need to test.
                             */
                        }
                    }
                }else {
                    SName multi = book.getInternalBook().getNameByName(choiceName + "multiresult"); // as ever I do wonder about these string literals
                    if (multi != null) {
                        SCell resultCell = getSnameCell(multi);
                        resultCell.setStringValue(multiList(loggedInUser, choiceName + "Multi", choiceCell.getStringValue()));

                    }
                }

            } else if (name.getName().toLowerCase().endsWith(ZKComposer.RESULT.toLowerCase())) {
                final SName filterQueryCell = book.getInternalBook().getNameByName(name.getName().substring(0, name.getName().length() - ZKComposer.RESULT.length()) + ZKComposer.CHOICE);
                if (filterQueryCell != null) {
                    final SCell choiceCell = getSnameCell(filterQueryCell);
                    final SCell resultCell = getSnameCell(name);
                    String choiceString = multiList(loggedInUser, name.getName(), choiceCell.getStringValue());
                    resultCell.setStringValue(choiceString);
                }
            }

        }
        return dependentRanges;
    }

    // moved/adapted from ZKComposer and made static
    static List<SName> getNamedDataRegionForRowAndColumnSelectedSheet(int row, int col, Sheet sheet) {
        List<SName> found = new ArrayList<>();
        for (SName name : getNamesForSheet(sheet)) { // seems best to loop through names checking which matches I think
            if (name.getName().toLowerCase().startsWith("az_dataregion")
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                //check that there are some row headings
                found.add(name);
            }
        }
        return found;
    }

    public String multiList(LoggedInUser loggedInUser, String filterName, String sourceSet) {
        try {
            List<String> chosenOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), "`" + filterName + "` children", loggedInUser.getLanguages());
            List<String> allOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), sourceSet, loggedInUser.getLanguages());
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
}



