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
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 03/03/15.
 * <p>
 * To render the report in ZK.
 */
public class ReportRenderer {

    // all case insensetive now so make these lower case and make the names from the reports .toLowerCase().startsWith().
    public static final String AZDATAREGION = "az_dataregion";
    public static final String AZOPTIONS = "az_options";
    public static final String AZREPEATREGION = "az_repeatregion";
    public static final String AZREPEATSCOPE = "az_repeatscope";
    private static final String AZREPEATITEM = "az_repeatitem";
    private static final String AZREPEATLIST = "az_repeatlist";
    private static final String AZDISPLAYROWHEADINGS = "az_displayrowheadings";
    private static final String AZDISPLAYCOLUMNHEADINGS = "az_displaycolumnheadings";
    static final String AZCOLUMNHEADINGS = "az_columnheadings";
    static final String AZROWHEADINGS = "az_rowheadings";
    private static final String AZCONTEXT = "az_context";
    static final String AZPIVOTFILTERS = "az_pivotfilters";//old version - not to be continued
    static final String AZCONTEXTFILTERS = "az_contextfilters";
    static final String AZCONTEXTHEADINGS = "az_contextheadings";
    static final String AZPIVOTHEADINGS = "az_pivotheadings";//old version
    private static final String AZREPORTNAME = "az_reportname";
    public static final String EXECUTE = "az_Execute";
    public static final String FOLLOWON = "az_Followon";

    /*
    CONTENTS is used to handle dependent ranges within the data region.   Thus one column in the data region may ask for a category, and the next a subcategory, which should be determined by the category
    For single cell 'chosen' ranges there is no problem - the choice for the subcategory may be defined as an Excel formula.
    For multicell ranges, use 'CONTENTS(rangechosen) to specify the subcategory....

    Not to do with pivot tables, initially for the expenses sheet, let us say that the first column is to select a project and then the second to select something based off that, you'd use contents. Not used that often.
     */
    static final String CONTENTS = "contents(";

    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");


    public static boolean populateBook(Book book, int valueId) {
        return populateBook(book, valueId, false, false);
    }

    public static boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae, boolean executeMode) {
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

        String context = "";
        // ok the map needs to be defined initially out here to be accessible by the addValidation function
        // these two lines moved from below the unmerge command, shouldn't be a big problem - I need the options to check that we're setting valid options directly below
        Map<String, List<String>> choiceOptionsMap = resolveChoiceOptions(book, loggedInUser);
        Map<String, String> userChoices = CommonBookUtils.getUserChoicesMap(loggedInUser);
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            ReportService.checkForPermissionsInSheet(loggedInUser,sheet);
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

            // choices can be a real pain, I effectively need to keep resolving them until they don't change due to choices being based on choices (dependencies in excel)
            boolean resolveChoices = true;
            int attempts = 0;
            List<CellRegion> regionsToWatchForMerge = new ArrayList<>();
            while (resolveChoices) {
                context = "";
                // Now I need to run through all choices setting from the user options IF it is valid and the first on the menu if it is not
                for (SName sName : namesForSheet) {
                    if (sName.getName().endsWith("Chosen")) {
                        CellRegion chosen = sName.getRefersToCellRegion();
                        String choiceName = sName.getName().substring(0, sName.getName().length() - "Chosen".length()).toLowerCase();
                        if (chosen != null) {
                            if (chosen.getRowCount() == 1 && chosen.getColumnCount() == 1) { // I think I may keep this constraint even after
                                // need to check that this choice is actually valid, so we need the choice query - should this be using the query as a cache?
                                List<String> validOptions = choiceOptionsMap.get(choiceName + "choice");
                                String userChoice = userChoices.get(choiceName);
                                LocalDate date = BookUtils.isADate(userChoice);
                                if (validOptions != null) {
                                    if (SpreadsheetService.FIRST_PLACEHOLDER.equals(userChoice)) {
                                        userChoice = validOptions.get(0);
                                    }
                                    if (SpreadsheetService.LAST_PLACEHOLDER.equals(userChoice)) {
                                        userChoice = validOptions.get(validOptions.size() - 1);
                                    }
                                    while (userChoice != null && !validOptions.contains(userChoice) && userChoice.contains("->")) {
                                        //maybe the user choice is over -specified. (e.g from drilldown or removal of conflicting names)  Try removing the super-sets
                                        userChoice = userChoice.substring(userChoice.indexOf("->") + 2);
                                    }
                                    if ((userChoice == null || !validOptions.contains(userChoice)) && !validOptions.isEmpty()) { // just set the first for the mo.
                                        //check that userChoice is not a valid date...
                                        if (date == null || !validOptions.contains(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))) {
                                            userChoice = validOptions.get(0);
                                        }
                                    }
                                }
                                if (userChoice != null) {
                                    SCell sCell = sheet.getInternalSheet().getCell(chosen.getRow(), chosen.getColumn());
                                    BookUtils.setValue(sCell, userChoice);
                                    context += choiceName + " = " + userChoice + ";";
                                }
                            }
                        }
                        regionsToWatchForMerge.add(chosen);
                    }
                    if (sName.getName().equalsIgnoreCase(AZREPORTNAME)) {
                        regionsToWatchForMerge.add(sName.getRefersToCellRegion());
                    }
                }
                resolveChoices = false;
                // ok so we've set them but now derived choices may have changed, no real option except to resolve again and see if there's a difference
                Map<String, List<String>> newChoiceOptionsMap = resolveChoiceOptions(book, loggedInUser);
                if (!newChoiceOptionsMap.equals(choiceOptionsMap)) { // equals is fune as Java is senseible about these things unlike C# . . .
                    System.out.println("choices changed as a result of chosen, resolving again");
                    resolveChoices = true;
                    choiceOptionsMap = newChoiceOptionsMap;
                }
                attempts++;
                if (attempts > 10) {
                    System.out.println("10 attempts at resolving choices, odds on there's some kind of circular reference, stopping");
                }
            }
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
                    if (databaseName != null) {
                        Database origDatabase = loggedInUser.getDatabase();
                        DatabaseServer origServer = loggedInUser.getDatabaseServer();
                        try {
                            LoginService.switchDatabase(loggedInUser, databaseName);
                            fillRegion(sheet, reportId, region, valueId, userRegionOptions, loggedInUser, executeMode); // in this case execute mode is telling the logs to be quiet
                        } catch (Exception e) {
                            String eMessage = "Unknown database " + databaseName + " for region " + region;
                            BookUtils.setValue(sheet.getInternalSheet().getCell(0, 0), eMessage);
                        }
                        loggedInUser.setDatabaseWithServer(origServer, origDatabase);
                    } else {
                        fillRegion(sheet, reportId, region, valueId, userRegionOptions, loggedInUser, executeMode);
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
                    lockWarnings.append("by " + cellsAndHeadingsForDisplay.getLockResult());
                }
            }
            if (lockWarnings.length() > 0) {
                sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED_RESULT, interpretWarnings(lockWarnings.toString()));
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
            // this is a pain, it seems I need to call 2 functions on each formula cell or the formula may not be calculated. ANNOYING!
            // can't do this in the fill region as formulae need to be dealt with outside
            final Iterator<SRow> rowIterator = sheet.getInternalSheet().getRowIterator();// only rows with values in them
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
                    if (name.getName().toLowerCase().startsWith(AZDATAREGION)) {
                        String region = name.getName().substring(AZDATAREGION.length());
                        CellRegion displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
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
                                                        cellForDisplay.setNewDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                        showSave = true;
                                                    }
                                                }
                                                if (sCell.getCellStyle().getDataFormat().toLowerCase().contains("m") && cellForDisplay.getStringValue().length() == 0) {
                                                    cellForDisplay.setNewStringValue(df.format(sCell.getDateValue()));//set a string value as our date for saving purposes
                                                }
                                            } else if (sCell.getFormulaResultType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB
                                                        BookUtils.setValue(sCell, cellForDisplay.getStringValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        cellForDisplay.setNewStringValue(sCell.getStringValue());
                                                        showSave = true;
                                                    }
                                                }
                                            }
                                        } else {
                                            // we now want to compare in the case of non formulae changes - a value from one data region importing into another,
                                            // the other typically being of the "ad hoc" no row headings type
                                            // notably this will hit a lot of cells (all the rest)
                                            String cellString = BookUtils.getCellString(sheet, row, col);
                                            if (sCell.getType() == SCell.CellType.NUMBER) {
                                                if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                    cellForDisplay.setNewStringValue(cellString);//to cover dates as well as numbers -EFC, I don't really understand but I'm moving this inside the conditional
                                                    cellForDisplay.setNewDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                    showSave = true;
                                                }
                                            } else if (sCell.getType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    cellForDisplay.setNewStringValue(sCell.getStringValue());
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
            for (CellRegion merge : mergesToTemporarilyRemove) {
                // the boolean meant JUST horizontally, I don't know why. Hence false.
                CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), false);
            }
        }
        List<SName> dependentRanges = ReportService.addValidation(loggedInUser, book, choiceOptionsMap);
        if (dependentRanges.size() > 0) {
            resolveDependentChoiceOptions(dependentRanges, book, loggedInUser);
        }
        //
        loggedInUser.setImageStoreName(imageStoreName);
        loggedInUser.setContext(context);
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

    private static String interpretWarnings(String lockWarnings) {
        //makes the warning more 'user-friendly'
        int pos = 0;
        while (lockWarnings.indexOf("by ", pos) > 0) {
            int startName = lockWarnings.indexOf("by ", pos);
            int endName = lockWarnings.indexOf(",", startName);
            String user = lockWarnings.substring(startName + 3, endName);
            String userName;
            try {
                userName = UserDAO.findByEmail(user.trim()).getName();
            } catch (Exception e) {
                userName = user;
            }
            lockWarnings = lockWarnings.substring(0, startName + 3) + userName + lockWarnings.substring(endName).replaceFirst("T", " at ");
            pos = startName + 3 + userName.length();
        }
        return lockWarnings.substring(0, lockWarnings.lastIndexOf(":"));//this should be on every line, but usually there'll only be one line
    }

    private static void fillRegion(Sheet sheet, int reportId, final String region, int valueId, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser, boolean quiet) {
        // ok need to deal with the repeat region things
        // the region to be repeated, will contain headings and an item which changes for each repetition
        SName repeatRegion = sheet.getBook().getInternalBook().getNameByName(AZREPEATREGION + region);
        // the target space we can repeat into. Can expand down but not across
        SName repeatScope = sheet.getBook().getInternalBook().getNameByName(AZREPEATSCOPE + region);
        // the list of items we can repeat over
        SName repeatList = sheet.getBook().getInternalBook().getNameByName(AZREPEATLIST + region);
        // the cell we'll put the items in the list in
        SName repeatItem = sheet.getBook().getInternalBook().getNameByName(AZREPEATITEM + region);

        if (userRegionOptions.getUserLocked()) { // then put the flag on the book, remember to take it off (and unlock!) if there was an error
            sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED, true);
        }
        // probably will be required later so declare out here
        int repeatRegionWidth = 0;
        int repeatScopeWidth = 0;
        int repeatRegionHeight = 0;
        int repeatScopeHeight = 0;
        int repeatColumns = 0;
        int repeatItemRowOffset = 0;
        int repeatItemColumnOffset = 0;
        List<String> repeatListItems = null;
        if (repeatRegion != null && repeatScope != null && repeatList != null && repeatItem != null) { // then the repeat thing
            repeatRegionWidth = repeatRegion.getRefersToCellRegion().getColumnCount();
            repeatScopeWidth = repeatScope.getRefersToCellRegion().getColumnCount();
            repeatRegionHeight = repeatRegion.getRefersToCellRegion().getRowCount();
            repeatScopeHeight = repeatScope.getRefersToCellRegion().getRowCount();
            repeatColumns = repeatScopeWidth / repeatRegionWidth;
            // we'll need the relative location of the item to populate it in each instance
            repeatItemRowOffset = repeatItem.getRefersToCellRegion().getRow() - repeatRegion.getRefersToCellRegion().getRow();
            repeatItemColumnOffset = repeatItem.getRefersToCellRegion().getColumn() - repeatRegion.getRefersToCellRegion().getColumn();

            String repeatListText = BookUtils.getSnameCell(repeatList).getStringValue();
            repeatListItems = CommonBookUtils.getDropdownListForQuery(loggedInUser, repeatListText);
            // so the expansion within each region will be dealt with by fill region internally but out here I need to ensure that there's enough space for the regions unexpanded
            int repeatRowsRequired = repeatListItems.size() / repeatColumns;
            if (repeatListItems.size() % repeatColumns > 0) {
                repeatRowsRequired++;
            }
            int rowsRequired = repeatRowsRequired * repeatRegionHeight;
            if (rowsRequired > repeatScopeHeight) {
                // slight copy paste from below, todo factor?
                int maxCol = BookUtils.getMaxCol(sheet);
                int rowsToAdd = rowsRequired - repeatScopeHeight;
                int insertRow = repeatScope.getRefersToCellRegion().getRow() + repeatScope.getRefersToCellRegion().getRowCount() - 1; // last row, inserting here shifts the row down - should it be called last row? -1 on the row count as rows are inclusive. Rows 3-4 is not 1 row it's 2!
//                int insertRow = repeatScope.getRefersToCellRegion().getLastRow(); // last row (different API call, was using the firt row plus the row count - 1, a simple mistake?)
                Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
                Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert at the 3rd row - rows to add - 1 as rows are inclusive
                CellOperationUtil.insertRow(insertRange);
                CellOperationUtil.paste(copySource, insertRange);
                int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
                if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                    insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
                }
                // and do hidden
                boolean hidden = sheet.getInternalSheet().getRow(insertRow - 1).isHidden();
                if (hidden) {
                    for (int row = insertRange.getRow(); row <= insertRange.getLastRow(); row++) {
                        sheet.getInternalSheet().getRow(row).setHidden(true);
                    }
                }
            }
            // so the space should be prepared for multi
        }
        SName columnHeadingsDescription = sheet.getBook().getInternalBook().getNameByName(AZCOLUMNHEADINGS + region);
        SName rowHeadingsDescription = sheet.getBook().getInternalBook().getNameByName(AZROWHEADINGS + region);
        SName contextDescription = sheet.getBook().getInternalBook().getNameByName(AZCONTEXT + region);
        String errorMessage = null;
        // make a blank area for data to be populated from, an upload in the sheet so to speak
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
            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(region, colHeadings, null, dataRegionCells, null, null, null, 0, userRegionOptions.getRegionOptionsForTransport(), null);// todo - work out what to do with the timestamp here! Might be a moot point given now row headings
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
                            List<String> choiceOptions = CommonBookUtils.getDropdownListForQuery(loggedInUser, "`az_" + filter + "` children");
                            // This means if it's not empty and not full ignore the filter. Works as the permute function, which needs to be used with pivots,
                            // will constrain by this set, if created, falling back on the set it's passed if not. Means what's in the permute will be a subset of the filters
                            // does this mean a double check? That it's constrained by selection here in context and again by the permute? I suppose no harm.
                            //if (possibleOptions.size() != choiceOptions.size() && choiceOptions.size() > 0) {
                            if (choiceOptions.size() == 0) {
                                //THis should create the required bits server side . . .
                                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                        .getFilterListForQuery(loggedInUser.getDataAccessToken(), "`" + filter + "` children", "az_" + filter, loggedInUser.getUser().getEmail(), loggedInUser.getLanguages());
                                choiceOptions = CommonBookUtils.getDropdownListForQuery(loggedInUser, "`az_" + filter + "` children");

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

                int rowsToAdd;
                int colsToAdd;
                if (displayDataRegion != null) {
                    // add rows
                    int maxCol = BookUtils.getMaxCol(sheet);
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
                        colsToAdd = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() - (displayDataRegion.getColumnCount());
                        int topRow = 0;
                        CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYCOLUMNHEADINGS + region);
                        if (displayColumnHeadings != null) {
                            topRow = displayColumnHeadings.getRow();
                        }
                        int insertCol = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1; // I think this is correct, just after the second column?
                        Range copySource = Ranges.range(sheet, topRow, insertCol - 1, maxRow, insertCol - 1);
                        int repeatCount = getRepeatCount(loggedInUser, cellsAndHeadingsForDisplay.getColHeadingsSource());
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
                    // these re loadings are because the region may have changed
                    displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
                    int row;
                    // ok there should be the right space for the headings
                    if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
                        int rowHeadingsColumn = displayRowHeadings.getColumn();
                        boolean isHierarchy = isHierarchy(cellsAndHeadingsForDisplay.getRowHeadingsSource());
                        int rowHeadingCols = cellsAndHeadingsForDisplay.getRowHeadings().get(0).size();
                        colsToAdd = rowHeadingCols - displayRowHeadings.getColumnCount();
                        if (colsToAdd > 0) {
                            int insertCol = displayRowHeadings.getColumn() + displayRowHeadings.getColumnCount() - 1;
                            Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1); //
                            CellOperationUtil.insert(insertRange.toColumnRange(), Range.InsertShift.RIGHT, Range.InsertCopyOrigin.FORMAT_LEFT_ABOVE);
                            displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
                        }
                        row = displayRowHeadings.getRow();
                        int lineNo = 0;
                        // why -1?
                        int lastTotalRow = row - 1;
                        int totalRow = -1;
                        List<String> lastRowHeadings = new ArrayList<>(cellsAndHeadingsForDisplay.getRowHeadings().size());
                        for (List<String> rowHeading : cellsAndHeadingsForDisplay.getRowHeadings()) {
                            int col = displayRowHeadings.getColumn();
                            int startCol = col;
                            for (String heading : rowHeading) {
                                if (heading != null && !heading.equals(".") && (sheet.getInternalSheet().getCell(row, col).getType() != SCell.CellType.STRING || sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty())) { // as with AzquoBook don't overwrite existing cells when it comes to headings
                                    SCell cell = sheet.getInternalSheet().getCell(row, col);
                                    cell.setValue(heading);
                                    if (lineNo > 0 && lastRowHeadings.get(col - startCol) != null && lastRowHeadings.get(col - startCol).equals(heading)) {
                                        //disguise the heading by making foreground colour = background colour
                                        Range selection = Ranges.range(sheet, row, col, row, col);
                                        CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, col).getCellStyle().getBackColor().getHtmlColor());
                                    }
                                }
                                col++;
                            }
                            lineNo++;
                            if (isHierarchy && lineNo > 1) {
                                int sameValues = 0;
                                for (sameValues = 0; sameValues < lastRowHeadings.size(); sameValues++) {
                                    if (!rowHeading.get(sameValues).equals(lastRowHeadings.get(sameValues))) {
                                        break;
                                    }
                                }
                                //format the row headings for hierarchy.  Each total level has a different format.   clear visible names in all but on eheading
                                if (sameValues < rowHeading.size() - 1) {
                                    int totalCount = rowHeading.size() - sameValues - 1;
                                    //this is a total line
                                    int selStart = displayRowHeadings.getColumn();
                                    int selEnd = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1 + cloneCols;
                                    SCell lineFormat = BookUtils.getSnameCell(sheet.getBook().getInternalBook().getNameByName("az_totalFormat" + totalCount + region));
                                    Range selection = Ranges.range(sheet, row - 1, selStart, row - 1, selEnd);
                                    Range headingRange = Ranges.range(sheet, row - 1, selStart, row - 1, selStart + displayRowHeadings.getColumnCount() - 1);
                                    if (lineFormat == null) {
                                        CellOperationUtil.applyFontBoldweight(selection, Font.Boldweight.BOLD);
                                    } else {
                                        if (totalCount == 1 && totalRow == -1) {
                                            totalRow = lineFormat.getRowIndex();
                                        }
                                        if (totalCount == 1 && totalRow > displayRowHeadings.getRow()) {
                                            //copy the total row and the line above to the current position, then copy the line above into the intermediate rows
                                            int dataStart = displayDataRegion.getColumn();
                                            int copyRows = row - lastTotalRow - 2;
                                            if (copyRows > 2) {
                                                copyRows = 2;
                                            }

                                            Range copySource = Ranges.range(sheet, totalRow - 2, dataStart, totalRow, selEnd);
                                            Range destination = Ranges.range(sheet, row - copyRows - 1, dataStart, row - 1, selEnd);
                                            int tempStart = selEnd + 10;
                                            Range tempPos = Ranges.range(sheet, totalRow - 2, tempStart, totalRow, tempStart + selEnd - selStart);
                                            if (copyRows == 1) {
                                                //delete the middle line
                                                CellOperationUtil.delete(Ranges.range(sheet, totalRow - 1, tempStart, totalRow - 1, tempStart + selEnd - selStart), Range.DeleteShift.UP);
                                                tempPos = Ranges.range(sheet, totalRow - 2, tempStart, totalRow - 1, tempStart + selEnd - selStart);
                                            }
                                            CellOperationUtil.paste(copySource, tempPos);
                                            CellOperationUtil.cut(tempPos, destination);
                                            if (row - lastTotalRow > 4) {
                                                //zap cells above, and fill in cells below
                                                CellOperationUtil.delete(Ranges.range(sheet, lastTotalRow + 1, dataStart, row - 4, selEnd), Range.DeleteShift.UP);
                                                CellOperationUtil.insert(Ranges.range(sheet, lastTotalRow + 2, dataStart, row - 3, selEnd), Range.InsertShift.DOWN, Range.InsertCopyOrigin.FORMAT_NONE);
                                                CellOperationUtil.paste(Ranges.range(sheet, lastTotalRow + 1, dataStart, lastTotalRow + 1, selEnd), Ranges.range(sheet, lastTotalRow + 2, dataStart, row - 3, selEnd));
                                            }
                                        }
                                        CellOperationUtil.applyBackColor(selection, lineFormat.getCellStyle().getBackColor().getHtmlColor());
                                        CellOperationUtil.applyFontColor(selection, lineFormat.getCellStyle().getFont().getColor().getHtmlColor());
                                        Range formatRange = Ranges.range(sheet.getBook().getSheet(lineFormat.getSheet().getSheetName()), lineFormat.getRowIndex(), lineFormat.getColumnIndex());
                                        CellOperationUtil.pasteSpecial(formatRange, headingRange, Range.PasteType.FORMATS, Range.PasteOperation.NONE, false, false);
                                        int blankout = sameValues;
                                        if (blankout > 0) {
                                            Range blanks = Ranges.range(sheet, row - 1, rowHeadingsColumn, row - 1, rowHeadingsColumn + blankout - 1);
                                            CellOperationUtil.applyFontColor(blanks, sheet.getInternalSheet().getCell(row - 1, rowHeadingsColumn).getCellStyle().getBackColor().getHtmlColor());
                                        }
                                        SCell first = sheet.getInternalSheet().getCell(row, rowHeadingsColumn);
                                        //the last line of the pivot table shows the set name of the first set, which is not very useful!
                                        try {
                                            if (first.getStringValue().startsWith("az_")) {
                                                first.setStringValue("TOTAL");
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    if (row > displayRowHeadings.getRow()) {
                                        Ranges.range(sheet, row - 1, selStart + sameValues + 1, row - 1, selStart + displayRowHeadings.getColumnCount() - 1).clearContents();
                                    }
                                    lastTotalRow = row - 1;
                                }
                                if (sameValues > 0) {
                                    Range selection = Ranges.range(sheet, row, rowHeadingsColumn, row, rowHeadingsColumn + sameValues - 1);
                                    //CellOperationUtil.clearStyles(selection);
                                    CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, rowHeadingsColumn).getCellStyle().getBackColor().getHtmlColor());
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
                                int hcol = rowHeadingsColumn;
                                for (String rowHeading : rowHeadings) {
                                    rowHeading = rowHeading.replace("`", "").trim();
                                    if (rowHeading.contains(" sorted")) { // maybe factor the string literal? Need to make it work for the mo
                                        rowHeading = rowHeading.substring(0, rowHeading.indexOf(" sorted")).trim();
                                    }
                                    String colHeading = multiList(loggedInUser, "az_" + rowHeading, "`" + rowHeading + "` children");
                                    if (colHeading == null || colHeading.equals("[all]")) colHeading = rowHeading;
                                    BookUtils.setValue(sheet.getInternalSheet().getCell(hrow, hcol++), colHeading);
                                }

                            }
                        }
                    }
                    //← → ↑ ↓ ↔ ↕ ah I can just paste it here, thanks IntelliJ :)
                    CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYCOLUMNHEADINGS + region);
                    if (displayColumnHeadings != null) {
                        row = displayColumnHeadings.getRow();
                        int ignoreRow = cellsAndHeadingsForDisplay.getColumnHeadings().size() - displayColumnHeadings.getRowCount();
                        for (List<String> colHeading : cellsAndHeadingsForDisplay.getColumnHeadings()) {
                            String lastHeading = "";
                            if (--ignoreRow < 0) {
                                boolean columnSort = false;
                                if (row - displayColumnHeadings.getRow() == displayColumnHeadings.getRowCount() - 1 && userRegionOptions.getSortable()) { // meaning last row of headings and sortable
                                    columnSort = true;
                                }
                                int col = displayColumnHeadings.getColumn();
                                for (String heading : colHeading) {
                                    if (heading.equals(lastHeading) || heading.equals(".")) {
                                        heading = "";
                                    } else {
                                        lastHeading = heading;
                                    }

                                    SCell sCell = sheet.getInternalSheet().getCell(row, col);
                                    if (sCell.getType() != SCell.CellType.STRING && sCell.getType() != SCell.CellType.NUMBER) {
                                        BookUtils.setValue(sCell, "");
                                    } else {//new behaviour - if there's a filled in heading, this can be used to detect the sort.
                                        try {
                                            if (sCell.getStringValue().length() > 0) {
                                                heading = sCell.getStringValue();
                                            }
                                        } catch (Exception e) {//to catch the rare cases where someone has hardcoded a year into the headings
                                            if (sCell.getNumberValue() > 0) {
                                                heading = sCell.getNumberValue() + "";
                                                if (heading.length() > 5 && heading.endsWith(".0")) {
                                                    heading = heading.substring(0, heading.length() - 2);
                                                }
                                            }
                                        }
                                    }
                                    if (columnSort && !heading.equals(".")) { // don't sort "." headings they are probably derived in the spreadsheet
                                        String sortArrow = " ↕";
                                        if (heading.equals(userRegionOptions.getSortColumn())) {
                                            sortArrow = userRegionOptions.getSortColumnAsc() ? " ↑" : " ↓";
                                        }
                                        if (!sCell.getStringValue().contains(sortArrow)) {
                                            if (sCell.getType() == SCell.CellType.STRING && sCell.getStringValue().isEmpty()) {
                                                sCell.setValue(heading + sortArrow);
                                            } else {
                                                sCell.setValue(sCell.getValue() + sortArrow);
                                            }
                                        }
                                        String value = sCell.getStringValue();
                                        value = value.substring(0, value.length() - 2);
                                        Range chosenRange = Ranges.range(sheet, row, col, row, col);
                                        // todo, investigate how commas would fit in in a heading name
                                        // think I'll just zap em for the mo.
                                        value = value.replace(",", "");
                                        chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true,
                                                value + " ↕," + value + " ↑," + value + " ↓", null,
                                                true, "Select Sorting", "",
                                                true, Validation.AlertStyle.WARNING, "Sort Column", "This is a sortable column, its value should not be manually altered.");
                                    } else {
                                        if (heading != null && (!sCell.getType().equals(SCell.CellType.NUMBER) && (sCell.isNull() || sCell.getStringValue().length() == 0))) { // vanilla, overwrite if not
                                            sCell.setValue(heading);
                                        }
                                    }
                                    col++;
                                }
                                row++;
                            }
                        }
                        // for the moment don't allow user column sorting (row heading sorting). Shouldn't be too difficult to add
/*                if (sortable != null && sortable.equalsIgnoreCase("all")) { // criteria from azquobook to make row heading sortable
                }*/
                    }
                    displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
                    if (repeatListItems != null) { // then we need to prepare the repeated regions. Row and col headings have been made so copy paste them where required
                        int rootRow = repeatRegion.getRefersToCellRegion().getRow();
                        int rootCol = repeatRegion.getRefersToCellRegion().getColumn();
                        int repeatColumn = 0;
                        int repeatRow = 0;

                        // work out where the data region is relative to the repeat region, we wait until now as it may have been expanded
                        int repeatDataRowOffset = 0;
                        int repeatDataColumnOffset = 0;
                        int repeatDataLastRowOffset = 0;
                        int repeatDataLastColumnOffset = 0;

                        repeatDataRowOffset = displayDataRegion.getRow() - repeatRegion.getRefersToCellRegion().getRow();
                        repeatDataColumnOffset = displayDataRegion.getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
                        repeatDataLastRowOffset = displayDataRegion.getLastRow() - repeatRegion.getRefersToCellRegion().getRow();
                        repeatDataLastColumnOffset = displayDataRegion.getLastColumn() - repeatRegion.getRefersToCellRegion().getColumn();

                        Range copySource = Ranges.range(sheet, rootRow, rootCol, repeatRegion.getRefersToCellRegion().getLastRow(), repeatRegion.getRefersToCellRegion().getLastColumn());
                        // yes the first will simply be copying over itself
                        for (String item : repeatListItems) {
                            Range insertRange = Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow), rootCol + (repeatRegionWidth * repeatColumn), rootRow + (repeatRegionHeight * repeatRow) + repeatRegionHeight - 1, rootCol + (repeatRegionWidth * repeatColumn) + repeatRegionWidth - 1);
                            if (repeatRow > 0 || repeatColumn > 0) {
                                CellOperationUtil.paste(copySource, insertRange);
                            }
                            // and set the item
                            BookUtils.setValue(sheet.getInternalSheet().getCell(rootRow + (repeatRegionHeight * repeatRow) + repeatItemRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItemColumnOffset), item);
                            repeatColumn++;
                            if (repeatColumn == repeatColumns) { // zap if back to the first column
                                repeatColumn = 0;
                                repeatRow++;
                            }
                        }
                        // reset tracking among the repeat regions
                        repeatColumn = 0;
                        repeatRow = 0;
                        // and now do the data, separate loop otherwise I'll be copy/pasting data
                        for (String item : repeatListItems) {
                            contextList.add(Collections.singletonList(item)); // item added to the context
                            // yes this is little inefficient as the headings are being resolved each time but that's just how it is for the moment
                            cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), region, valueId, rowHeadingList, BookUtils.nameToStringLists(columnHeadingsDescription),
                                    contextList, userRegionOptions, quiet);
                            displayDataRegion = new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatDataRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataColumnOffset
                                    , rootRow + (repeatRegionHeight * repeatRow) + repeatDataLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataLastColumnOffset);
                            loggedInUser.setSentCells(reportId, region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay); // todo- perhaps address that this is a bit of a hack!
                            dataFill(sheet, cellsAndHeadingsForDisplay, displayDataRegion);
                            contextList.remove(contextList.size() - 1); // item removed from the context
                            repeatColumn++;
                            if (repeatColumn == repeatColumns) { // zap if back to the first column
                                repeatColumn = 0;
                                repeatRow++;
                            }
                        }
                    } else {
                        dataFill(sheet, cellsAndHeadingsForDisplay, displayDataRegion);
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

    // factored off to enable multiple regions

    private static void dataFill(Sheet sheet, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, CellRegion displayDataRegion) {
        int row = displayDataRegion.getRow();
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
                        if (cell.getType() != SCell.CellType.FORMULA) {
                            if (cell.getCellStyle().getFont().getName().equalsIgnoreCase("Code EAN13")) { // then a special case, need to use barcode encoding
                                cell.setValue(SpreadsheetService.prepareEAN13Barcode(cellValue.getStringValue()));// guess we'll see how that goes!
                            } else {
                                BookUtils.setValue(cell, cellValue.getStringValue());
                            }
                        }
                        // see if this works for highlighting
                        if (cellValue.isHighlighted()) {
                            CellOperationUtil.applyFontColor(Ranges.range(sheet, row, col), "#FF0000");
                        }
                        // commented for the moment, requires the overall unlock per sheet followed by the protect later
                        if (cellValue.isLocked()) {
                            Range selection = Ranges.range(sheet, row, col);
                            CellStyle oldStyle = selection.getCellStyle();
                            EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                            newStyle.setLocked(true);
                            selection.setCellStyle(newStyle);
                        }
                    }
                    col++;
                    localCol++;
                }
                row++;
            }
        }
    }

    private static int getRepeatCount(LoggedInUser loggedInUser, List<List<String>> colHeadingsSource) {
        int colHeadingRow = colHeadingsSource.size();
        String lastColHeading = colHeadingsSource.get(colHeadingRow - 1).get(0);
        int repeatCount = 1;
        if (!lastColHeading.startsWith(".")) {
            if (lastColHeading.toLowerCase().startsWith("permute")) {
                return 1;//permutes are unpredictable unless followed by a set
            }
            repeatCount = CommonBookUtils.getDropdownListForQuery(loggedInUser, lastColHeading).size();
        }
        colHeadingRow--;
        //if the last line is a set, is one of the lines above also a set - if so this is a permutation
        while (colHeadingRow-- > 0) {
            String colHeading = colHeadingsSource.get(colHeadingRow).get(0);
            if (colHeading.toLowerCase().startsWith("permute(") || CommonBookUtils.getDropdownListForQuery(loggedInUser, colHeading).size() > 0) {
                return repeatCount;
            }
        }
        return repeatCount;
    }

    private static boolean isHierarchy(List<List<String>> headings) {
        for (List<String> oneCol : headings) {
            for (String oneHeading : oneCol) {
                if (oneHeading.toLowerCase().contains("hierarchy ") || oneHeading.toLowerCase().contains("permute")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final String VALIDATION_SHEET = "VALIDATION_SHEET";

    /* This did return a map with the query as the key but I'm going to change this to the name, it will save unnecessary query look ups later.
     This adds one caveat : the options match the choice name at the time the function ran - if the query in the choice cell updates this needs to be run again
       */

    private static Map<String, List<String>> resolveChoiceOptions(Book book, LoggedInUser loggedInUser) {
        Map<String, List<String>> toReturn = new HashMap<>();
        for (SName name : book.getInternalBook().getNames()) {
            //check to create pivot filter choices.... TODO - is this a redundant comment, aren't the filters being sorted anyway?

            if (name.getName().endsWith("Choice") && name.getRefersToCellRegion() != null) {
                // ok I assume choice is a single cell
                List<String> choiceOptions = new ArrayList<>(); // was null, see no help in that
                // new lines from edd to try to resolve choice stuff
                SCell choiceCell = BookUtils.getSnameCell(name);
                // as will happen to the whole sheet later
                if (choiceCell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    choiceCell.getFormulaResultType();
                    choiceCell.clearFormulaResultCache();
                }
                //System.out.println("Choice cell : " + choiceCell);
                if (choiceCell.getType() != SCell.CellType.ERROR && (choiceCell.getType() != SCell.CellType.FORMULA || choiceCell.getFormulaResultType() != SCell.CellType.ERROR)) {
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
                                choiceOptions = CommonBookUtils.getDropdownListForQuery(loggedInUser, query);
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

    private static void resolveDependentChoiceOptions(List<SName> dependentRanges, Book book, LoggedInUser loggedInUser) {
        Sheet validationSheet = book.getSheet(VALIDATION_SHEET);
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
                            List<String> optionList = CommonBookUtils.getDropdownListForQuery(loggedInUser, newQuery);
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
                    String lookupRange = "VALIDATION_SHEET!" + BookUtils.rangeToText(0, targetCol) + ":" + BookUtils.rangeToText(maxSize, targetCol + optionNo - 1);
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

    public static String multiList(LoggedInUser loggedInUser, String filterName, String sourceSet) {
        try {
            List<String> languages = new ArrayList<>();
            languages.add(loggedInUser.getUser().getEmail());
            List<String> chosenOptions = CommonBookUtils.getDropdownListForQuery(loggedInUser, "`" + filterName + "` children", languages);
            List<String> allOptions = CommonBookUtils.getDropdownListForQuery(loggedInUser, sourceSet);
            if (allOptions.size() < 2) return null;
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