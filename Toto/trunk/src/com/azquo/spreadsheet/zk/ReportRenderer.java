package com.azquo.spreadsheet.zk;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.user.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.rmi.RemoteException;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 03/03/15.
 * <p>
 * To render the report in ZK, the online excel view
 */
public class ReportRenderer {

    // all case insensetive now so make these lower case and make the names from the reports .toLowerCase().startsWith().
    public static final String AZDATAREGION = "az_dataregion";
    public static final String AZOPTIONS = "az_options";
    public static final String AZREPEATREGION = "az_repeatregion";
    public static final String AZREPEATSCOPE = "az_repeatscope";
    static final String AZREPEATITEM = "az_repeatitem";
    static final String AZREPEATLIST = "az_repeatlist";
    private static final String AZDISPLAYROWHEADINGS = "az_displayrowheadings";
    private static final String AZDISPLAYCOLUMNHEADINGS = "az_displaycolumnheadings";
    static final String AZCOLUMNHEADINGS = "az_columnheadings";
    static final String AZROWHEADINGS = "az_rowheadings";
    private static final String AZCONTEXT = "az_context";
    static final String AZPIVOTFILTERS = "az_pivotfilters";//old version - not to be continued
    static final String AZCONTEXTFILTERS = "az_contextfilters";
    static final String AZCONTEXTHEADINGS = "az_contextheadings";
    static final String AZPIVOTHEADINGS = "az_pivotheadings";//old version
    static final String AZREPORTNAME = "az_reportname";
    public static final String EXECUTE = "az_Execute";
    public static final String FOLLOWON = "az_Followon";
    public static final String AZSAVE = "az_save";

    public static boolean populateBook(Book book, int valueId) {
        return populateBook(book, valueId, false, false);
    }

    public static boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae, boolean executeMode) {
        BookUtils.removeNamesWithNoRegion(book); // should protect against some errors.
        book.getInternalBook().setAttribute(OnlineController.LOCKED, false); // by default
        long track = System.currentTimeMillis();
        String imageStoreName = "";
        boolean showSave = false;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        // unlock data on every report load. Maybe make this clear to the user?
        // is the exception a concern here?
        try {
            SpreadsheetService.unlockData(loggedInUser);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        //String context = "";
        // why a sheet loop at the outside, why not just run all the names? Need to have a think . . .
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            // check we're not hitting a validation sheet we added!
            if (sheet.getSheetName().endsWith(ChoicesService.VALIDATION_SHEET)) {
                continue;
            }
            // options and validation now sorted per sheet
            ReportService.checkForPermissionsInSheet(loggedInUser, sheet);
            /* with the protect sheet command below commented this is currently pointless. I think ZK had some misbehavior while locked so leave this for the moment.
            int maxRow = sheet.getLastRow();
            int maxCol = 0;
            for (int row = 0; row <= maxRow; row++) {
                if (maxCol < sheet.getLastColumn(row)) {
                    maxCol = sheet.getLastColumn(row);
                }
            }
            for (int row = 0; row <= maxRow; row++) {
                for (int col = 0; col <= maxCol; col++) {
                    Range selection = Ranges.range(sheet, row, col);
                    CellStyle oldStyle = selection.getCellStyle();
                    EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                    newStyle.setLocked(false);
                    selection.setCellStyle(newStyle);
                }
            }*/
            // names are per book, not sheet. Perhaps we could make names the big outside loop but for the moment I'll go by sheet - convenience function
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            // we must resolve the options here before filling the ranges as they might feature "as" name populating queries
            List<CellRegion> regionsToWatchForMerge = new ArrayList<>();
            // it will return the properly resolved choice options map as well as flagging the regions to merge by adding to the list
            Map<String, List<String>> choiceOptionsMap = ChoicesService.resolveAndSetChoiceOptions(loggedInUser, sheet, regionsToWatchForMerge);
            ReportService.resolveQueries(sheet, loggedInUser); // after all options sorted should be ok
            // ok the plan here is remove merges that might be adversely affected by regions expanding then put them back in after the regions are expanded.
            List<CellRegion> mergesToTemporarilyRemove = new ArrayList<>(sheet.getInternalSheet().getMergedRegions());
            Iterator<CellRegion> it = mergesToTemporarilyRemove.iterator();
            while (it.hasNext()) {
                CellRegion merge = it.next();
                if (regionsToWatchForMerge.contains(merge)) {
                    CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
                } else {
                    it.remove(); // a merge we just leave alone
                }
            }
            // and now we want to run through all regions for this sheet
            boolean fastLoad = false; // skip some checks, initially related to saving
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on prefixes. (fast load being set outside the loop so is there a problem with it not being found before data regions??)
                if (name.getName().equalsIgnoreCase("az_FastLoad")) {
                    fastLoad = true;
                }
                if (name.getName().equals("az_ImageStoreName")) {
                    imageStoreName = BookUtils.getRegionValue(sheet, name.getRefersToCellRegion());
                }
                if (name.getName().toLowerCase().startsWith(AZDATAREGION)) { // then we have a data region to deal with here
                    String region = name.getName().substring(AZDATAREGION.length()); // might well be an empty string
                    if (sheet.getBook().getInternalBook().getNameByName(AZROWHEADINGS + region) == null) { // if no row headings then this is an ad hoc region, save possible by default
                        showSave = true;
                    }
                    SName optionsRegion = sheet.getBook().getInternalBook().getNameByName(AZOPTIONS + region);
                    String optionsSource = "";
                    if (optionsRegion != null) {
                        optionsSource = BookUtils.getSnameCell(optionsRegion).getStringValue();
                    }
                    // better way to combine user region options from the sheet and report database
                    UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, optionsSource);
                    // UserRegionOptions from MySQL will have limited fields filled
                    UserRegionOptions userRegionOptions2 = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                    // only these five fields are taken from the table
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
                    String databaseName = userRegionOptions.getDatabaseName();
                    // fairly simple addition to allow multiple databases on the same report
                    // todo - support when saving . . .
                    if (databaseName != null) { // then switch database, fill and switch back!
                        Database origDatabase = loggedInUser.getDatabase();
                        DatabaseServer origServer = loggedInUser.getDatabaseServer();
                        try {
                            LoginService.switchDatabase(loggedInUser, databaseName);
                            populateRegionSet(sheet, reportId, region, valueId, userRegionOptions, loggedInUser, executeMode); // in this case execute mode is telling the logs to be quiet
                        } catch (Exception e) {
                            String eMessage = "Unknown database " + databaseName + " for region " + region;
                            BookUtils.setValue(sheet.getInternalSheet().getCell(0, 0), eMessage);
                        }
                        loggedInUser.setDatabaseWithServer(origServer, origDatabase);
                    } else {
                        populateRegionSet(sheet, reportId, region, valueId, userRegionOptions, loggedInUser, executeMode);
                    }
                }
            }
            // after loading deal with lock stuff
            final List<CellsAndHeadingsForDisplay> sentForReport = loggedInUser.getSentForReport(reportId);
            StringBuilder lockWarnings = new StringBuilder();
            for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : sentForReport) {
                if (cellsAndHeadingsForDisplay.getLockResult() != null) {
                    if (lockWarnings.length() == 0) {
                        lockWarnings.append("Data on this sheet is locked\n");
                    }
                    lockWarnings.append("by ").append(cellsAndHeadingsForDisplay.getLockResult());
                }
            }
            if (lockWarnings.length() > 0) {
                sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED_RESULT, ReportUtils.interpretLockWarnings(lockWarnings.toString()));
                // any locks applied need to be let go
                if (sheet.getBook().getInternalBook().getAttribute(OnlineController.LOCKED) == Boolean.TRUE) { // I think that's allowed, it was locked then unlock and switch the flag
                    try {
                        SpreadsheetService.unlockData(loggedInUser);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED, false);
                }
            }
            System.out.println("regions populated in : " + (System.currentTimeMillis() - track) + "ms");
// commenting just this line for the mo
            // now protect. Doing so before seems to cause problems
/*            Ranges.range(sheet).protectSheet("azquo",
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
            // returns true if data changed by formulae
            if (ReportService.resolveFormulaeAndSnapCharts(loggedInUser, reportId, book, sheet, fastLoad, useSavedValuesOnFormulae)) {
                showSave = true;
            }
            // now remerge
            for (CellRegion merge : mergesToTemporarilyRemove) {
                // the boolean meant JUST horizontally, I don't know why. Hence false.
                CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), false);
            }
            List<SName> dependentRanges = ChoicesService.addValidation(sheet.getSheetName().replace(" ",""), loggedInUser, book, choiceOptionsMap);
            if (dependentRanges.size() > 0) {
                ChoicesService.resolveDependentChoiceOptions(sheet.getSheetName().replace(" ",""), dependentRanges, book, loggedInUser);
            }
        }
        //
        loggedInUser.setImageStoreName(imageStoreName);
        // we won't clear the log in the case of execute
        if (!executeMode) {
            // after stripping off some redundant exception throwing this was the only possibility left, ignore it
            try {
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearSessionLog(loggedInUser.getDataAccessToken());
            } catch (Exception ignored) {
            }
        }
        return showSave;
    }

    private static void populateRegionSet(Sheet sheet, int reportId, final String region, int valueId, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser, boolean quiet) {
        if (userRegionOptions.getUserLocked()) { // then put the flag on the book, remember to take it off (and unlock!) if there was an error
            sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED, true);
        }
        SName columnHeadingsDescription = sheet.getBook().getInternalBook().getNameByName(AZCOLUMNHEADINGS + region);
        SName rowHeadingsDescription = sheet.getBook().getInternalBook().getNameByName(AZROWHEADINGS + region);
        SName contextDescription = sheet.getBook().getInternalBook().getNameByName(AZCONTEXT + region);
        String errorMessage = null;
        // make a blank area for data to be populated from, an upload in the sheet so to speak (ad hoc)
        if (columnHeadingsDescription != null && rowHeadingsDescription == null) {
            List<List<String>> colHeadings = BookUtils.nameToStringLists(columnHeadingsDescription);
            List<List<CellForDisplay>> dataRegionCells = new ArrayList<>();
            CellRegion dataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
            for (int rowNo = 0; rowNo < dataRegion.getRowCount(); rowNo++) {
                List<CellForDisplay> oneRow = new ArrayList<>();
                for (int colNo = 0; colNo < dataRegion.getColumnCount(); colNo++) {
                    oneRow.add(new CellForDisplay(false, "", 0, false, rowNo, colNo, true, false, null)); // make these ignored. Edd note : I'm not particularly happy about this, sent data should be sent data, this is just made up . . .
                }
                dataRegionCells.add(oneRow);
            }
            // note the col headings source is going in here as is without processing as in the case of ad-hoc it is not dynamic (i.e. an Azquo query), it's import file column headings
            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(region, colHeadings, null, null, null, dataRegionCells, null, null, null, 0, userRegionOptions.getRegionOptionsForTransport(), null);// todo - work out what to do with the timestamp here! Might be a moot point given now row headings
            loggedInUser.setSentCells(reportId, region, cellsAndHeadingsForDisplay);
            return;
        }
        if (columnHeadingsDescription != null) {
            try {
                List<List<String>> contextList = BookUtils.nameToStringLists(contextDescription);
                List<List<String>> rowHeadingList = BookUtils.nameToStringLists(rowHeadingsDescription);
                //check if this is a pivot - if so, then add in any additional filter needed
                SName contextFilters = sheet.getBook().getInternalBook().getNameByName(AZCONTEXTFILTERS);
                if (contextFilters == null) {
                    contextFilters = sheet.getBook().getInternalBook().getNameByName(AZPIVOTFILTERS);
                }
                // a comma separated list of names
                if (contextFilters != null) {
                    String[] filters = BookUtils.getSnameCell(contextFilters).getStringValue().split(",");
                    for (String filter : filters) {
                        filter = filter.trim();
                        try {
                            //List<String> possibleOptions = getDropdownListForQuery(loggedInUser, "`" + filter + "` children");
                            // the names selected off the pivot filter are jammed into a name with "az_" before the filter name
                            List<String> choiceOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, "`az_" + filter + "` children");
                            // This means if it's not empty and not full ignore the filter. Works as the permute function, which needs to be used with pivots,
                            // will constrain by this set, if created, falling back on the set it's passed if not. Means what's in the permute will be a subset of the filters
                            // does this mean a double check? That it's constrained by selection here in context and again by the permute? I suppose no harm.
                            //if (possibleOptions.size() != choiceOptions.size() && choiceOptions.size() > 0) {
                            if (choiceOptions.size() == 0) {
                                //THis should create the required bits server side . . .
                                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                        .getFilterListForQuery(loggedInUser.getDataAccessToken(), "`" + filter + "` children", "az_" + filter, loggedInUser.getUser().getEmail(), loggedInUser.getLanguages());
                                choiceOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, "`az_" + filter + "` children");

                            }
                            if (choiceOptions.size() > 0) {//conditional should now be irrelevant
                                List<String> additionalContext = new ArrayList<>();
                                additionalContext.add("az_" + filter);
                                // so add it to the context. Could perhaps be one list that's added? I suppose it doesn't really matter.
                                contextList.add(additionalContext);
                            }
                        } catch (Exception e) {
                            //ignore - no choices yet made
                            // todo, make this a bit more elegant?
                        }
                    }
                }
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), region, valueId, rowHeadingList, BookUtils.nameToStringLists(columnHeadingsDescription),
                        contextList, userRegionOptions, quiet);
                loggedInUser.setSentCells(reportId, region, cellsAndHeadingsForDisplay);
                // now, put the headings into the sheet!
                // might be factored into fill range in a bit
                CellRegion displayRowHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYROWHEADINGS + region);
                CellRegion displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);

                int colsToAdd;
                int maxRow = sheet.getLastRow();
                if (displayDataRegion != null) {
                    int maxCol = BookUtils.getMaxCol(sheet);
                    // todo - find out what the hell cloneCols is!
                    int cloneCols = expandRowAndColumnHeadings(loggedInUser, sheet, region, displayDataRegion, cellsAndHeadingsForDisplay, maxCol);
                    // these re loadings are because the region may have changed
                    // why reload displayDataRegion but not displayRowHeadings for example? todo - check, either both need reloading or both don't
                    displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
                    // ok there should be the right space for the headings
                    if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
                        int rowHeadingCols = cellsAndHeadingsForDisplay.getRowHeadings().get(0).size();
                        colsToAdd = rowHeadingCols - displayRowHeadings.getColumnCount();
                        if (colsToAdd > 0) {
                            int insertCol = displayRowHeadings.getColumn() + displayRowHeadings.getColumnCount() - 1;
                            Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1); //
                            CellOperationUtil.insert(insertRange.toColumnRange(), Range.InsertShift.RIGHT, Range.InsertCopyOrigin.FORMAT_LEFT_ABOVE);
                            displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, ReportRenderer.AZDATAREGION + region);
                        }
                        RegionFillerService.fillRowHeadings(loggedInUser, sheet, region, displayRowHeadings, displayDataRegion, cellsAndHeadingsForDisplay, cloneCols);
                    }
                    CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYCOLUMNHEADINGS + region);
                    if (displayColumnHeadings != null) {
                        RegionFillerService.fillColumnHeadings(sheet, userRegionOptions, displayColumnHeadings, cellsAndHeadingsForDisplay);
                    }
                    displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
                    // now factored off to the filler class that deals with repeat if applicable
                    RegionFillerService.fillData(loggedInUser, reportId, sheet, region, userRegionOptions, cellsAndHeadingsForDisplay, displayDataRegion, rowHeadingsDescription, columnHeadingsDescription, contextDescription, maxCol, valueId, quiet);
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
                String eMessage = "Unable to find matching header and context regions for this data region : " + AZDATAREGION + region + " : " + errorMessage;
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
            CellRegion dataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);// this function should not be called without a valid data region
            if (dataRegion != null) {
                sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching header and context regions for this data region : " + AZDATAREGION + region);
            } else {
                System.out.println("no region found for " + AZDATAREGION + region);
            }
        }
    }

    // work out what clonecols is for!
    private static int expandRowAndColumnHeadings(LoggedInUser loggedInUser, Sheet sheet, String region, CellRegion displayDataRegion, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, int maxCol) {
        // add rows
        if (cellsAndHeadingsForDisplay.getRowHeadings() != null && (displayDataRegion.getRowCount() < cellsAndHeadingsForDisplay.getRowHeadings().size()) && displayDataRegion.getRowCount() > 2) { // then we need to expand, and there is space to do so (3 or more allocated already)
            int rowsToAdd = cellsAndHeadingsForDisplay.getRowHeadings().size() - (displayDataRegion.getRowCount());
            int insertRow = displayDataRegion.getRow() + displayDataRegion.getRowCount() - 1; // last but one row
            Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
            Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
            CellOperationUtil.insertRow(insertRange);
            CellOperationUtil.paste(copySource, insertRange);
            int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
            if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
            }
            boolean hidden = sheet.getInternalSheet().getRow(insertRow - 1).isHidden();
            if (hidden) {
                for (int row = insertRange.getRow(); row <= insertRange.getLastRow(); row++) {
                    sheet.getInternalSheet().getRow(row).setHidden(true);
                }
            }
        }
        // add columns
        int maxRow = sheet.getLastRow();
        int cloneCols = 0;
        if (displayDataRegion.getColumnCount() < cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() && displayDataRegion.getColumnCount() > 2) { // then we need to expand
            int colsToAdd = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() - (displayDataRegion.getColumnCount());
            int topRow = 0;
            CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYCOLUMNHEADINGS + region);
            if (displayColumnHeadings != null) {
                topRow = displayColumnHeadings.getRow();
            }
            int insertCol = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1; // I think this is correct, just after the second column?
            Range copySource = Ranges.range(sheet, topRow, insertCol - 1, maxRow, insertCol - 1);
            int repeatCount = ReportUtils.getRepeatCount(loggedInUser, cellsAndHeadingsForDisplay.getColHeadingsSource());
            if (repeatCount > 1 && repeatCount < cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size()) {//the column headings have been expanded because the top left element is a set.  Check for secondary expansion, then copy the whole region
                int copyCount = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() / repeatCount;
                cloneCols = colsToAdd;
                if (repeatCount > displayDataRegion.getColumnCount()) {
                    colsToAdd = repeatCount - displayDataRegion.getColumnCount();
                    Range insertRange = Ranges.range(sheet, topRow, insertCol, maxRow, insertCol + colsToAdd - 1); // insert just before the last col
                    CellOperationUtil.insertColumn(insertRange);
                    // will this paste the lot?
                    CellOperationUtil.paste(copySource, insertRange);
                    insertCol += colsToAdd;
                    colsToAdd = repeatCount * (copyCount - 1);
                    cloneCols = colsToAdd;
                }
                insertCol++;
                copySource = Ranges.range(sheet, topRow, displayDataRegion.getColumn(), maxRow, insertCol - 1);
            }
            Range insertRange = Ranges.range(sheet, topRow, insertCol, maxRow, insertCol + colsToAdd - 1); // insert just before the last col, except for permuted headings
            CellOperationUtil.insertColumn(insertRange);
            // will this paste the lot?
            CellOperationUtil.paste(copySource, insertRange);
            int originalWidth = sheet.getInternalSheet().getColumn(insertCol - 1).getWidth();
            if (originalWidth != sheet.getInternalSheet().getColumn(insertCol).getWidth()) { // height may not match on insert
                insertRange.setColumnWidth(originalWidth); // hopefully set the lot in one go??
            }
            boolean hidden = sheet.getInternalSheet().getColumn(insertCol - 1).isHidden();
            if (hidden) {
                for (int col = insertRange.getColumn(); col <= insertRange.getLastColumn(); col++) {
                    sheet.getInternalSheet().getColumn(col).setHidden(true);
                }
            }
        }
        return cloneCols;
    }
}