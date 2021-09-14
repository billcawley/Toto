package com.azquo.spreadsheet.zk;

import com.azquo.StringLiterals;
import com.azquo.admin.user.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang.StringEscapeUtils;
import org.zkoss.chart.ChartsEvent;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.*;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import io.keikai.api.*;
import io.keikai.api.model.*;
import io.keikai.model.*;
import io.keikai.model.chart.SChartData;
import io.keikai.model.chart.SSeries;
import io.keikai.model.impl.chart.GeneralChartDataImpl;
import io.keikai.ui.Spreadsheet;
import io.keikai.ui.event.*;
import io.keikaiex.ui.widget.ChartsWidget;
import io.keikaiex.ui.widget.WidgetCtrl;
import org.zkoss.zul.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Used to programatically configure the ZK Sheet as we'd like it.
 * <p>
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;
    private ZKContextMenu zkContextMenu;
    private Window filterPopup;

    // could this later incorporate the fast loading and no snapping of charts?
    public static String AZSHEETOPTIONS = "az_sheetoptions";

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        zkContextMenu = new ZKContextMenu(myzss);
        filterPopup = new Window();
        filterPopup.setMode(Window.Mode.OVERLAPPED);
        filterPopup.setVisible(false);
        filterPopup.setId("filterPopup");
        //ZKContextMenu.setPopupStyle(filterPopup);
        // new thing as per advice from ZK
        filterPopup.setPage(myzss.getPage());
        setChartClickProxies();
        // does the book say a particular cell should be selected?
        // maybe improve moving this number?
        if (myzss.getBook().getInternalBook().getAttribute(OnlineController.CELL_SELECT) != null) {
            String cellSelect = (String) myzss.getBook().getInternalBook().getAttribute(OnlineController.CELL_SELECT);
            myzss.getBook().getInternalBook().setAttribute(OnlineController.CELL_SELECT, null); // zap it
            int row = Integer.parseInt(cellSelect.substring(0, cellSelect.indexOf(",")));
            int col = Integer.parseInt(cellSelect.substring(cellSelect.indexOf(",") + 1));
//            myzss.setSelection(new AreaRef(row, col, row, col));
//            myzss.setCellFocus(new CellRef(row,col));
            myzss.focusTo(row, col);
        }
        if(BookUtils.getNameByName(AZSHEETOPTIONS, myzss.getSelectedSheet()) != null){
            SName azSheetOptionsName = BookUtils.getNameByName(AZSHEETOPTIONS, myzss.getSelectedSheet());
            String azSheetOptions = BookUtils.getRegionValue(myzss.getSelectedSheet(), azSheetOptionsName.getRefersToCellRegion());
            if (azSheetOptions.toLowerCase().contains("nosurround")){
                myzss.setShowFormulabar(false);
                myzss.setHidecolumnhead(true);
                myzss.setHiderowhead(true);
                myzss.setShowContextMenu(false);
            }
        }
        // finally a quick check - is the currently selected sheet hidden? If so select another sheet! Unlike Excel ZK can have a hidden sheet selected and this makes no sense to the user
        if (myzss.getSelectedSheet().isHidden()) {
            for (SSheet s : myzss.getSBook().getSheets()) {
                if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                    myzss.setSelectedSheet(s.getSheetName());
                    break;
                }
            }
        }
        for (int i = 0; i < myzss.getBook().getNumberOfSheets(); i++) {
            Ranges.range(myzss.getBook().getSheetAt(i)).notifyChange(); // try to update the lot - sometimes it seems it does not!
        }
        LoggedInUser loggedInUser = (LoggedInUser) myzss.getBook().getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) myzss.getBook().getInternalBook().getAttribute(OnlineController.REPORT_ID);
        List<CellsAndHeadingsForDisplay> sentForReport = loggedInUser.getSentForReport(reportId);
        for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : sentForReport){
            if (cellsAndHeadingsForDisplay.getOptions().dynamicUpdate){
                // so we are saying somewhere in this book is a data region we need to check for dynamic updates. Set this going on the client side
                Clients.evalJavaScript("setInterval(function(){ postAjax('CHECKCDYNAMICUPDATE'); }, 5000);");
                break;
            }
        }


        ZKComposerUtils.checkCSVDownload(myzss.getBook());
        if (myzss.getBook().getInternalBook().getAttribute("csvdownload") != null){
            Filedownload.save(new AMedia(System.currentTimeMillis() + "csvexport.zip", "zip", "application/zip", (File) myzss.getBook().getInternalBook().getAttribute("csvdownload"), true));
            myzss.getBook().getInternalBook().setAttribute("csvdownload",null);
        }
//        Clients.evalJavaScript("window.skipSetting = 0;window.skipMarker = 0;");

    }

    public static String MULTI = "Multi";
    // chosentree
    public static String CHOSENTREE = "ChosenTree";
    private static String CHOICE = "Choice";

    /*
    Check for a pivot/multi select box to show or a report name clicked on to open. Finally "wake" the log to refresh more often in anticipation of things happening there.
     */
    @Listen("onCellClick = #myzss")
    public void onCellClick(CellMouseEvent event) {
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        String selectionName = null;
        if (book.getInternalBook().getNameByName(StringLiterals.AZMULTISELECTHEADINGS) != null){
            selectionName = ReportUIUtils.pivotItem(event, StringLiterals.AZPIVOTFILTERS, StringLiterals.AZPIVOTHEADINGS, 3);//OLD STYLE
            if (selectionName == null) {
                selectionName = ReportUIUtils.pivotItem(event, StringLiterals.AZCONTEXTFILTERS, StringLiterals.AZCONTEXTHEADINGS, 3);
            }
        }

        String selectionList = null;
        if (selectionName != null) {
            selectionList = "`" + selectionName + "` children sorted";
            selectionName = "az_" + selectionName.trim();
        } else {
            // check to see if it's a non-pivot multi
            List<SName> names = ReportUIUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getSheet(), event.getRow(), event.getColumn());
            for (SName name : names) {
                if (name.getName().toLowerCase().endsWith(MULTI.toLowerCase())) { // a new style
                    selectionName = name.getName();
                    final SName filterQueryCell = event.getSheet().getBook().getInternalBook().getNameByName(name.getName().substring(0, name.getName().length() - MULTI.length()) + CHOICE, name.getApplyToSheetName());
                    if (filterQueryCell != null) {
                        selectionList = BookUtils.getSnameCell(filterQueryCell).getStringValue();
                    }
                    break;
                }
                if (name.getName().toLowerCase().endsWith(CHOSENTREE.toLowerCase())) { // aim is to pop open the inspect to get a name
                    selectionName = name.getName();
                    final SName filterQueryCell = event.getSheet().getBook().getInternalBook().getNameByName(name.getName().substring(0, name.getName().length() - CHOSENTREE.length()) + CHOICE, name.getApplyToSheetName());
                    //System.out.println(BookUtils.getSnameCell(filterQueryCell).getStringValue());
                    Clients.evalJavaScript("chosenTree('" + StringEscapeUtils.escapeJavaScript(BookUtils.getSnameCell(filterQueryCell).getStringValue()) + "')");
                    break;
                }
            }
        }
        if (selectionList != null) {
            showMultiSelectionList(loggedInUser, selectionName, selectionList, event.getPageX(), event.getPageY());
        } else { // if not a multi check for a clickable report name
            SName allowableReports = myzss.getBook().getInternalBook().getNameByName(ReportService.ALLOWABLE_REPORTS, myzss.getSelectedSheetName());//try local
            if (allowableReports == null) {
                allowableReports = myzss.getBook().getInternalBook().getNameByName(ReportService.ALLOWABLE_REPORTS);//try global

            }
            if (allowableReports != null) {
                String cellValue = "";
                final SCell cell = myzss.getSelectedSheet().getInternalSheet().getCell(event.getRow(), event.getColumn());// we care about the contents of the left most cell
                if (!cell.isNull() && cell.getType().equals(SCell.CellType.STRING)) {
                    cellValue = cell.getStringValue();
                }
                if (cellValue.length() > 0) {
                    CellRegion allowedRegion = allowableReports.getRefersToCellRegion();
                    Sheet allowedSheet = myzss.getBook().getSheet(allowableReports.getRefersToSheetName());
                    try {
                        for (int row1 = allowedRegion.getRow(); row1 < allowedRegion.getRow() + allowedRegion.getRowCount(); row1++) {
                            if (allowedSheet.getInternalSheet().getCell(row1, allowedRegion.getColumn()).getStringValue().equals(cellValue)) {// deal with security in the online controller
                                if (allowedRegion.getLastColumn() - allowedRegion.getColumn()>=3){
                                    String choices = allowedSheet.getInternalSheet().getCell(row1,allowedRegion.getColumn()+3).getStringValue();
                                    if (choices != null){
                                        ChoicesService.setChoices(loggedInUser,choices);
                                    }
                                }
                                Clients.evalJavaScript("window.open(\"/api/Online?permissionid=" + URLEncoder.encode(cellValue, "UTF-8") + "\")");
                            }
                        }
                    } catch (Exception e) {
                        //in case some cells are numeric (I think as in getStringValue throwing an exception - should I be checking the cell type instead?)
                    }
                }
            }
            final SCell cell = myzss.getSelectedSheet().getInternalSheet().getCell(event.getRow(), event.getColumn());// we care about the contents of the left most cell
            if (!cell.isNull() && cell.getType().equals(SCell.CellType.FORMULA)) {
                String formula = cell.getFormulaValue();
                if (formula.contains("HYPERLINK")){
                    int startIndex = formula.indexOf("(") + 1;
                    int endIndex = formula.indexOf(",");
                    String linkName = formula.substring(startIndex,endIndex);
                    SName linkSName = event.getSheet().getBook().getInternalBook().getNameByName(linkName);
                    if (linkSName != null){

                        Clients.evalJavaScript("window.open(\"" + BookUtils.getRegionValue(myzss.getSelectedSheet(), linkSName.getRefersToCellRegion()) + "\")");
                    }
                }
            }

            // and now check for the cell being a save button
            SName saveName = event.getSheet().getBook().getInternalBook().getNameByName(StringLiterals.AZSAVE);
            if (saveName != null && saveName.getRefersToSheetName().equals(myzss.getSelectedSheetName())
                    && event.getRow() >= saveName.getRefersToCellRegion().getRow()
                    && event.getRow() <= saveName.getRefersToCellRegion().getLastRow()
                    && event.getColumn() >= saveName.getRefersToCellRegion().getColumn()
                    && event.getColumn() <= saveName.getRefersToCellRegion().getLastColumn()
            ) {
                try {
                    String saveResult = ReportService.save(myzss, loggedInUser);
                    if (saveResult.startsWith("Success")) {
                        // todo - processing warning here? The button isn't used much . . .
                        ZKComposerUtils.reloadBook(myzss, book);
                        // think this wil;l do it
                        Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"none\";document.getElementById(\"restoreDataButton\").style.display=\"none\";");
                        Clients.evalJavaScript("alert(\"" + saveResult + "\")");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // reset the log refresh, things might be about to happen
        Clients.evalJavaScript("window.skipSetting = 0;window.skipMarker = 0;");
    }

    // In theory could just have on cellchange but this seems to have broken the dropdowns onchange stuff. Luckily it seems there's no need for code duplication
    // checking for save stuff in the onchange and the other stuff here - action on choice select and column sorting
    @Listen("onStopEditing = #myzss")
    public void onStopEditing(StopEditingEvent event) {
        String chosen = (String) event.getEditingValue();
        // so the following bit of code was parsing a date on, for example 2020-11-24 policies which we wouldn't want it to. For the mo will check for spaces and not do the parse then
        if (!chosen.trim().contains(" ")) {
            LocalDate date = ReportUtils.isADate(chosen);
            if (date != null) {
                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                chosen = dateTimeFormatter.format(date);
            }
        }
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        List<SName> names = ReportUIUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getSheet(), event.getRow(), event.getColumn());
        boolean reload = false;
        // so run through all the names associated with that cell
        for (SName name : names) {
            if (name.getName().endsWith("Chosen") && name.getRefersToCellRegion().getRowCount() == 1) {// it ends chosen and is one row tall
                //and it cannot be in an existing data region
                if (BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), event.getSheet(), StringLiterals.AZREPEATSCOPE).size() == 0
                        && BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), event.getSheet(), StringLiterals.AZDATAREGION).size() == 0) {
                    // therefore it's a choice change, set the choice and the reload flag and break
                    String choice = name.getName().substring(0, name.getName().length() - "Chosen".length());
                    Map<String, String> params = new HashMap<>();
                    params.put(choice, chosen);
                    loggedInUser.userLog("Choice select", params);
                    SpreadsheetService.setUserChoice(loggedInUser, choice, chosen.trim());
                    reload = true;
                    break;
                }
            }
            // We may add row heading later but it's not a requirement currently
            if (name.getName().toLowerCase().startsWith(StringLiterals.AZDISPLAYCOLUMNHEADINGS)) { // ok going to try for a sorting on column heading detect
                String region = name.getName().substring(StringLiterals.AZDISPLAYCOLUMNHEADINGS.length());
                UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    SName optionsRegion = BookUtils.getNameByName(StringLiterals.AZOPTIONS + region, book.getSheet(name.getRefersToSheetName()));
                    String source = null;
                    if (optionsRegion != null) {
                        source = BookUtils.getSnameCell(optionsRegion).getStringValue();
                    }
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);
                }
                // ok here's the thing, the value on the spreadsheet (heading) is no good, it could be just for display, I want what the database would call the heading
                // so I'd better get the headings.
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, name.getRefersToSheetName(), region); // maybe jam this object against the book? Otherwise multiple books could cause problems
                if (cellsAndHeadingsForDisplay != null) {
                    //int localRow = event.getRow() - name.getRefersToCellRegion().getRow();
                    int localRow = cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1;//assume the bottom row
                    int localCol = event.getColumn() - name.getRefersToCellRegion().getColumn();
                    if (cellsAndHeadingsForDisplay.getColumnHeadings().get(localRow) != null) {
                        if (cellsAndHeadingsForDisplay.getColumnHeadings().get(localRow).get(localCol) != null) {
                            //← → ↑ ↓ ↔ ↕
                            //new behaviour, store the column number rather than the name. - WFC
                            // EFC - this is a bit of hack, the sort cols are incremented and decremented later to make them human readable and left as a string to allow 1&2&3 multiple column sorting.
                            if (chosen.startsWith("↑")) {
                                userRegionOptions.setSortColumn((localCol + 1) + "");
                                userRegionOptions.setSortColumnAsc(true);
                                UserRegionOptionsDAO.store(userRegionOptions);
                                reload = true;
                            }
                            if (chosen.startsWith("↓")) {
                                userRegionOptions.setSortColumn((localCol + 1) + "");
                                userRegionOptions.setSortColumnAsc(false);
                                UserRegionOptionsDAO.store(userRegionOptions);
                                reload = true;
                            }
                            if (chosen.startsWith("↕")) {
                                userRegionOptions.setSortColumn("");
                                userRegionOptions.setSortColumnAsc(false);
                                UserRegionOptionsDAO.store(userRegionOptions);
                                reload = true;
                            }
                        }
                    }
                }
            }
        }
        if (reload) {
            Clients.showBusy(myzss, "Reloading . . .");
            org.zkoss.zk.ui.event.Events.echoEvent("onReloadWhileClientProcessing", myzss, null);
        }
    }

    @Listen("onReloadWhileClientProcessing = #myzss")
    public void onReloadWhileClientProcessing() {
        ZKComposerUtils.reloadBook(myzss, myzss.getBook());
        Clients.clearBusy(myzss);
        if (myzss.getBook().getInternalBook().getAttribute("csvdownload") != null){
            try {
                Filedownload.save(new AMedia(System.currentTimeMillis() + "csvexport.zip", "zip", "application/zip", (File) myzss.getBook().getInternalBook().getAttribute("csvdownload"), true));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            myzss.getBook().getInternalBook().setAttribute("csvdownload",null);
        }

    }

    // used directly below, I need a list of the following
    static class RegionRowCol {
        public final String sheetName;
        public final String region;
        public final int row;
        public final int col;

        RegionRowCol(String sheetName, String region, int row, int col) {
            this.sheetName = sheetName;
            this.region = region;
            this.row = row;
            this.col = col;
        }
    }

    // to detect data changes. In a nutshell is doing the work required to find the right cell(s) for display and to set the new values on them
    // 26/01/17 acceptable currently.
    @Listen("onAfterCellChange = #myzss")
    public void onAfterCellChange(CellAreaEvent event) {
        int row = event.getRow();
        int col = event.getColumn();
        if (row != event.getLastRow() || col != event.getLastColumn()) { // I believe we're only interested in single cells changing
            return;
        }
        CellData cellData = Ranges.range(event.getSheet(), row, col).getCellData();
        if (cellData == null) {
            return;
        }
        String chosen;
        boolean isDouble = false;
        double doubleValue = 0.0;
        try {
            chosen = cellData.getStringValue();
        } catch (Exception e) {
            try {
                doubleValue = cellData.getDoubleValue();
                isDouble = true;
                chosen = doubleValue + "";
            } catch (Exception e2) {
                chosen = "";
            }
        }
        String dataFormat = Ranges.range(event.getSheet(), row, col).getCellStyle().getDataFormat();
        if (dataFormat.toLowerCase().contains("mm")) {
            //it's a date
            isDouble = false;
            chosen = cellData.getFormatText();
        }
        //System.out.println("after cell change : " + row + " col " + col + " chosen");
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        List<SName> names = BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), event.getSheet());
        List<SName> repeatRegionNames = BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), event.getSheet(), StringLiterals.AZREPEATSCOPE);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        checkRegionSizes(event.getSheet(), loggedInUser, reportId);
        // regions may overlap - update all EXCEPT where there are repeat regions, in which case just do that as repeat regions will have overlap by their nature
        List<RegionRowCol> regionRowColsToSave = new ArrayList<>(); // a list to save - list due to the possibility of overlapping data regions
        List<RegionRowCol> headingRowColsToSave = new ArrayList<>();
        for (SName name : repeatRegionNames) {
            final RegionRowCol regionRowColForRepeatRegion = ReportUIUtils.getRegionRowColForRepeatRegion(book, row, col, name);
            if (regionRowColForRepeatRegion != null) {
                regionRowColsToSave.add(regionRowColForRepeatRegion);
            }
        }
        if (regionRowColsToSave.isEmpty() && !names.isEmpty()) { // no repeat regions but there are normal ones
            for (SName name : names) {
                String regionName = name.getName().substring(StringLiterals.AZDATAREGION.length());
                regionRowColsToSave.add(new RegionRowCol(name.getRefersToSheetName(), regionName, row - name.getRefersToCellRegion().getRow(), col - name.getRefersToCellRegion().getColumn()));
            }
        }
        List<SName> headingNames = BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), event.getSheet(), StringLiterals.AZDISPLAYROWHEADINGS);
        for (SName name : headingNames) {
            headingRowColsToSave.add(new RegionRowCol(name.getRefersToSheetName(), name.getName().substring(StringLiterals.AZDISPLAYROWHEADINGS.length()), row - name.getRefersToCellRegion().getRow(), col - name.getRefersToCellRegion().getColumn()));
        }
        for (RegionRowCol regionRowCol : regionRowColsToSave) {
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, regionRowCol.sheetName, regionRowCol.region);
            if (sentCells != null) { // a good start!
                if (regionRowCol.row >= 0 && regionRowCol.col >= 0 &&
                        sentCells.getData().size() > regionRowCol.row && sentCells.getData().get(regionRowCol.row).size() > regionRowCol.col) {
                    CellForDisplay cellForDisplay = sentCells.getData().get(regionRowCol.row).get(regionRowCol.col);
                    // todo address locking here - maybe revert the cell
                    Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
                    if (isDouble) {
                        cellForDisplay.setNewDoubleValue(doubleValue);
                        // copying a few lines of server side code to try and ensure that successive saves don't cause a mismatch in the string values compared to what they would be on a fresh report load
                        String numericValue = doubleValue + "";
                        if (numericValue.endsWith(".0")) {
                            numericValue = numericValue.substring(0, numericValue.length() - 2);
                        }
                        cellForDisplay.setNewStringValue(numericValue);
                    } else {
                        cellForDisplay.setNewDoubleValue(0);
                        cellForDisplay.setNewStringValue(chosen);
                    }
                    int highlightDays = 0;
                    if (book.getInternalBook().getAttribute("highlightDays") != null) { // maybe factor the string literals??
                        highlightDays = (Integer) book.getInternalBook().getAttribute("highlightDays");
                    }
                    if (highlightDays > 0) {
                        cellForDisplay.setHighlighted(true);
                        CellOperationUtil.applyFontColor(Ranges.range(event.getSheet(), row, col), "#FF0000");
                    }
                }
            }
        }
        for (RegionRowCol headingRowCol : headingRowColsToSave) { //recording any edited row heading
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, headingRowCol.sheetName, headingRowCol.region);
            if (sentCells != null) { // a good start!
                if (headingRowCol.row >= 0 && headingRowCol.col >= 0 &&
                        sentCells.getRowHeadings().size() > headingRowCol.row && sentCells.getRowHeadings().get(headingRowCol.row).size() > headingRowCol.col) {
                    sentCells.setRowHeading(headingRowCol.row, headingRowCol.col, chosen);
                }
            }
        }
    }

    private void checkRegionSizes(Sheet sheet, LoggedInUser loggedInUser, int reportId) {
        //we do not seem to be able to detect insertion or deletion of rows, so this routine detects row insertion/deletion after the event
        SBook book = sheet.getInternalSheet().getBook();
        for (SName name : book.getNames()) {
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(sheet.getSheetName()) && name.getName() != null && name.getName().toLowerCase().startsWith(StringLiterals.AZDISPLAYROWHEADINGS)) {
                String region = name.getName().substring(StringLiterals.AZDISPLAYROWHEADINGS.length());
                int size = name.getRefersToCellRegion().getRowCount();
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, sheet.getSheetName(), region); // maybe jam this object against the book? Otherwise multiple books could cause problems
                if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) { // apparently it can be??
                    int oldSize = cellsAndHeadingsForDisplay.getRowHeadings().size();
                    CellRegion newHeadings = name.getRefersToCellRegion();
                    List<List<String>> oldHeadings = cellsAndHeadingsForDisplay.getRowHeadings();
                    //WE ASSUME ONLY ONE COLUMN OF HEADINGS!
                    if (oldSize != size) {
                        List<List<String>> revisedHeadings = new ArrayList<>();
                        List<List<CellForDisplay>> revisedData = new ArrayList<>();
                        List<List<CellForDisplay>> oldData = cellsAndHeadingsForDisplay.getData();
                        if (oldSize < size) {
                            boolean inserted = false;
                            //assuming that inserts/deletes cannot happen on the bottom line - the region would not adjust
                            int added = 0;

                            for (int rowNo = 0; rowNo < size; rowNo++) {
                                if (!inserted && (rowNo >= oldSize || !sheet.getInternalSheet().getCell(newHeadings.getRow() + rowNo, newHeadings.getColumn()).getStringValue().equals(oldHeadings.get(rowNo).get(0)))) {
                                    for (int rowNo3 = 0; rowNo3 < size - oldSize; rowNo3++) {
                                        List<String> blankHeading = new ArrayList<>();
                                        blankHeading.add("");
                                        List<CellForDisplay> blankData = new ArrayList<>();
                                        for (int colNo = 0; colNo < cellsAndHeadingsForDisplay.getData().get(0).size(); colNo++) {
                                            blankData.add(new CellForDisplay(false, "", 0, false, 0, colNo, false, false, "", 0));//there might be problems if the columns have been sorted!
                                        }
                                        revisedHeadings.add(blankHeading);
                                        revisedData.add(blankData);
                                        added++;
                                        rowNo++;
                                    }
                                    inserted = true;
                                }
                                if (rowNo - added < oldData.size()) {
                                    revisedData.add(oldData.get(rowNo - added));
                                    revisedHeadings.add(oldHeadings.get(rowNo - added));
                                }

                            }
                        } else {
                            boolean deleted = false;
                            int rowNo = 0;
                            while (rowNo < oldSize) {
                                if (!deleted && (rowNo >= size || !sheet.getInternalSheet().getCell(newHeadings.getRow() + rowNo, newHeadings.getColumn()).getStringValue().equals(oldHeadings.get(rowNo).get(0)))) {
                                    rowNo += oldSize - size;
                                    deleted = true;
                                    // EFC note - I do not fully understand the code here but I do know it was causing an index out of bounds exception by pushing rowNo >= oldData size so breakif that's the case
                                    if (rowNo >= oldSize) {
                                        break;
                                    }
                                }
                                revisedData.add(oldData.get(rowNo));
                                revisedHeadings.add(oldHeadings.get(rowNo));
                                rowNo++;
                            }
                        }
                        cellsAndHeadingsForDisplay.setData(revisedData);
                        cellsAndHeadingsForDisplay.setRowHeadings(revisedHeadings);
                    }
                }
            }
        }
    }


    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) throws JsonProcessingException {
        //Clients.evalJavaScript("zk.Widget.$('$myzss').setShowContextMenu(true);");
        //Clients.evalJavaScript("alert(zk.Widget.$('$myzss').getShowContextMenu());");
        showAzquoContextMenu(cellMouseEvent.getRow(), cellMouseEvent.getColumn(), cellMouseEvent.getClientx(), cellMouseEvent.getClienty());
    }


    private void showAzquoContextMenu(int cellRow, int cellCol, int mouseX, int mouseY) throws JsonProcessingException {
        zkContextMenu.showAzquoContextMenu(cellRow, cellCol, mouseX, mouseY, null, myzss);
    }

    private void showAzquoContextMenu(int cellRow, int cellCol, Component ref) throws JsonProcessingException {
        zkContextMenu.showAzquoContextMenu(cellRow, cellCol, 0, 0, ref, myzss);
    }


    // When someone clicks on a chart point find the cell it is populated from and show the provenance if available
    public void setChartClickProxies() {
        Map<String, SChart> usefulChartObjects = new HashMap<>();
        final List<SSheet> sheets = myzss.getSBook().getSheets();
        for (SSheet sSheet : sheets) {
            final List<SChart> charts = sSheet.getCharts();
            for (SChart chart : charts) {
                usefulChartObjects.put(chart.getId(), chart);
            }
        }
        for (Component component : myzss.getChildren()) {
            for (Component component1 : component.getChildren()) {
                if (component1 instanceof WidgetCtrl) { // hacky, I just need to get to the sodding event listener
                    WidgetCtrl widgetCtrl = (WidgetCtrl) component1; // should work??
                    if (widgetCtrl.getWidget().getWidgetType().equalsIgnoreCase("chart")) {
                        ChartsWidget chartsWidget = (ChartsWidget) widgetCtrl.getWidget();
                        final SChart usefulChart = usefulChartObjects.get(chartsWidget.getId());
                        chartsWidget.addEventListener("onPlotClick", event -> {
                            ChartsEvent chartsEvent = (ChartsEvent) event;
                            if (usefulChart != null) {
                                final SChartData data = usefulChart.getData();
                                if (data instanceof GeneralChartDataImpl) {
                                    GeneralChartDataImpl generalChartData = (GeneralChartDataImpl) data;
                                    final SSeries series = generalChartData.getSeries(0);
                                    try {
                                        final Range range = Ranges.range(myzss.getSelectedSheet(), series.getValuesFormula());
                                        int pointIndex = chartsEvent.getPointIndex();
                                        if (range.getRowCount() == 1) {
                                            //myzss.focusTo(range.getRow(), range.getColumn() + pointIndex);
                                            showAzquoContextMenu(range.getRow(), range.getColumn() + pointIndex, chartsEvent.getTarget());
                                        } else {
                                            //myzss.focusTo(range.getRow() + pointIndex, range.getColumn());
                                            showAzquoContextMenu(range.getRow() + pointIndex, range.getColumn(), chartsEvent.getTarget());
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    // as it says, show the multi selection list used by pivots and multi selections
    private void showMultiSelectionList(LoggedInUser loggedInUser, final String selectionName, final String selectionList,
                                        int pageX, int pageY) {
        Components.removeAllChildren(filterPopup);
        Listbox listbox = new Listbox();
        // this is to make select all go across hidden pages - relevant IF pagination is used
/*            listbox.addEventListener("onCheckSelectAll", event1 -> {
                CheckEvent event2 = (CheckEvent)event1;
                if (event2.isChecked()){
                    listbox.selectAll();
                } else {
                    listbox.clearSelection();
                }
            });
            listbox.setMold("paging");
            listbox.setPageSize(10);*/
        Listhead listhead = new Listhead();
        Listheader listheader = new Listheader();
        listheader.setLabel("Select All");
        listheader.setAlign("left");
        listhead.appendChild(listheader);
        listbox.setMultiple(true);
        listbox.setCheckmark(true);
        listbox.setWidth("350px");
        listbox.setHeight("550px");
        listbox.appendChild(listhead);
        try {
            List<FilterTriple> filterOptions = CommonReportUtils.getFilterListForQuery(loggedInUser, selectionList, selectionName);
            Set<Listitem> selectedItems = new HashSet<>();
            int index = 0;
            for (FilterTriple filterTriple : filterOptions) {
                listbox.appendItem(filterTriple.name, filterTriple.nameId + ""); // it seems to allow me to jam the name id in there, useful (though as ever it would be nice for the client side to know nothing about name ids)
                if (filterTriple.selected) {
                    selectedItems.add(listbox.getItemAtIndex(index));
                }
                index++;
            }
            listbox.setSelectedItems(selectedItems);
        } catch (Exception e) {
            listbox.appendItem("error : " + e.getMessage(), "");
            e.printStackTrace();
        }
        filterPopup.appendChild(listbox);
        Button save = new Button();
        save.setWidth("80px");
        save.setLabel("Save");
        save.addEventListener("onClick",
                event1 -> {
                    List<Integer> childIds = new ArrayList<>();
                    final Set<Listitem> selectedItems = listbox.getSelectedItems();
                    StringBuilder selectedForLog = new StringBuilder();
                    for (Listitem listItem : selectedItems) {
                        childIds.add(Integer.parseInt(listItem.getValue())); // should never fail on the parse
                        selectedForLog.append(listItem.getLabel() + " ");
                    }
                    Map<String, String> params = new HashMap<>();
                    params.put(selectionName, selectedForLog.toString());
                    loggedInUser.userLog("Multi select", params);
                    // new logic - set the first as the vanilla choice
                    if (!selectedItems.isEmpty()) {
                        Listitem first = selectedItems.iterator().next();
                        SpreadsheetService.setUserChoice(loggedInUser, selectionName, first.getLabel());
                    }
                    RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), selectionName, loggedInUser.getUser().getEmail(), childIds);
                    filterPopup.setVisible(false);
                });

        Button saveAndReload = new Button();
//            saveAndReload.setWidth("80px");
        saveAndReload.setLabel("Save and reload");
        saveAndReload.addEventListener("onClick",
                event1 -> {
                    List<Integer> childIds = new ArrayList<>();
                    final Set<Listitem> selectedItems = listbox.getSelectedItems();
                    StringBuilder selectedForLog = new StringBuilder();
                    for (Listitem listItem : selectedItems) {
                        childIds.add(Integer.parseInt(listItem.getValue())); // should never fail on the parse
                        selectedForLog.append(listItem.getLabel() + " ");
                    }
                    Map<String, String> params = new HashMap<>();
                    params.put(selectionName, selectedForLog.toString());
                    loggedInUser.userLog("Multi select", params);
                    // new logic - set the first as the vanilla choice
                    if (!selectedItems.isEmpty()) {
                        Listitem first = selectedItems.iterator().next();
                        SpreadsheetService.setUserChoice(loggedInUser, selectionName, first.getLabel());
                    }
                    RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), selectionName, loggedInUser.getUser().getEmail(), childIds);
                    filterPopup.setVisible(false);
                    try {
                        Clients.showBusy(myzss, "Reloading . . .");
                        org.zkoss.zk.ui.event.Events.echoEvent("onReloadWhileClientProcessing", myzss, null);
//                        ZKComposerUtils.reloadBook(myzss, book);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        Button cancel = new Button();
        cancel.setWidth("80px");
        cancel.setLabel("Cancel");
        cancel.addEventListener("onClick", event1 -> filterPopup.setVisible(false));
        filterPopup.appendChild(new Separator());
        filterPopup.appendChild(save);
        filterPopup.appendChild(new Space());
        filterPopup.appendChild(cancel);
        filterPopup.appendChild(new Separator());
        filterPopup.appendChild(saveAndReload);
        // "after_start" is the position we'd want
        filterPopup.setLeft(pageX + "px");
        pageY -= 300;
        if (pageY < 0) {
            pageY = 0;
        }
        filterPopup.setTop(pageY + "px");
        filterPopup.setVisible(true);
        //filterPopup.open(pageX, pageY);
    }
/* not used might as well comment for the mo
    @Listen("onCellSelection = #myzss")
    public void onCellSelection(CellSelectionEvent event){
        StringBuilder info = new StringBuilder();
        info.append("Select on[")
                .append(Ranges.getAreaRefString(event.getSheet(), event.getArea())).append("]");

        //show info...
    }
*/
}