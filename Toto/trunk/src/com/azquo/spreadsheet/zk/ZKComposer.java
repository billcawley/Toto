package com.azquo.spreadsheet.zk;

import com.azquo.TypedPair;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.memorydb.Constants;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.*;
import org.apache.commons.lang.StringEscapeUtils;
import org.zkoss.chart.ChartsEvent;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.*;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.*;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.model.*;
import org.zkoss.zss.model.chart.SChartData;
import org.zkoss.zss.model.chart.SSeries;
import org.zkoss.zss.model.impl.chart.GeneralChartDataImpl;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.*;
import org.zkoss.zssex.ui.widget.ChartsWidget;
import org.zkoss.zssex.ui.widget.Ghost;
import org.zkoss.zssex.ui.widget.WidgetCtrl;
import org.zkoss.zul.*;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Used to programatically configure the ZK Sheet as we'd like it. Some of this, for example adding additional context menus, might be rather hacky and prone to breaking as ZK is updated.
 * <p>
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;

    private Menupopup editPopup = new Menupopup();
    //Label provenanceLabel = new Label();
    private Label instructionsLabel = new Label();
    private Popup provenancePopup = null;
    private Popup debugPopup = null;
    private Popup highlightPopup = null;
    private Popup instructionsPopup = null;
    private Popup filterPopup = null;

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        // todo - check ZK to see if there's a better way to do this
        editPopup.setId("editPopup");
        editPopup.setStyle("background-color:#ffffff");
        editPopup.setStyle("border: 5px solid #F58030");
        provenancePopup = new Popup();
        provenancePopup.setId("provenancePopup");
        provenancePopup.setDraggable("true");
        provenancePopup.setDroppable("true");
        provenancePopup.setStyle("background-color:#ffffff");
        provenancePopup.setStyle("border: 5px solid #F58030");
        debugPopup = new Popup();
        debugPopup.setId("debugPopup");
        debugPopup.setDraggable("true");
        debugPopup.setDroppable("true");
        debugPopup.setStyle("background-color:#ffffff");
        debugPopup.setStyle("border: 5px solid #F58030");
        instructionsPopup = new Popup();
        instructionsPopup.setId("instructionsPopup");
        instructionsPopup.setStyle("background-color:#ffffff");
        instructionsPopup.setStyle("border: 5px solid #F58030");
        instructionsLabel.setMultiline(true);
        instructionsPopup.appendChild(instructionsLabel);
        highlightPopup = new Popup();
        highlightPopup.setStyle("background-color:#ffffff");
        highlightPopup.setStyle("border: 5px solid #F58030");
        highlightPopup.setId("highlightPopup");
        filterPopup = new Popup();
        filterPopup.setId("filterPopup");
        filterPopup.setStyle("background-color:#ffffff");
        filterPopup.setStyle("border: 5px solid #F58030");

        // much hacking went into getting an appropriate object to hook into to make our extra contextual menu
        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
            myzss.getFirstChild().appendChild(provenancePopup);
            myzss.getFirstChild().appendChild(debugPopup);
            myzss.getFirstChild().appendChild(instructionsPopup);
            myzss.getFirstChild().appendChild(highlightPopup);
            myzss.getFirstChild().appendChild(filterPopup);
        } else { // it took some considerable time to work out this hack
            Ghost g = new Ghost();
            g.setAttribute("zsschildren", "");
            myzss.appendChild(g);
            g.appendChild(editPopup);
            g.appendChild(provenancePopup);
            g.appendChild(debugPopup);
            g.appendChild(instructionsPopup);
            g.appendChild(highlightPopup);
            g.appendChild(filterPopup);
        }
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
                                        if (range.getRowCount() == 0) {
                                            provenanceForRowAndColumn(range.getRow() + pointIndex, range.getColumn(), chartsEvent.getTarget());
                                            //myzss.focusTo(range.getRow() + pointIndex, range.getColumn());
                                        } else {
                                            //myzss.focusTo(range.getRow(), range.getColumn() + pointIndex);
                                            provenanceForRowAndColumn(range.getRow(), range.getColumn() + pointIndex, chartsEvent.getTarget());
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
        if (myzss.getSelectedSheet().isHidden()) {
            for (SSheet s : myzss.getSBook().getSheets()) {
                if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                    myzss.setSelectedSheet(s.getSheetName());
                    break;
                }
            }
        }
    }

    private static String MULTI = "Multi";
    private static String CHOICE = "Choice";
    private static String RESULT = "Result";

    /* Bit of an odd one this : on a cell click "wake" the log between the client and report server back up as there may be activity shortly
    In addition I now want to now deal with the filter multi choice pop ups
     */

    @Listen("onCellClick = #myzss")
    public void onCellClick(CellMouseEvent event) {
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        String selectionName = ReportUIUtils.pivotItem(event, ReportRenderer.AZPIVOTFILTERS, ReportRenderer.AZPIVOTHEADINGS, 3);//OLD STYLE
        if (selectionName == null) {
            selectionName = ReportUIUtils.pivotItem(event, ReportRenderer.AZCONTEXTFILTERS, ReportRenderer.AZCONTEXTHEADINGS, 3);
        }
        String selectionList = null;
        CellRegion queryResultRegion = null;
        if (selectionName != null) { // we have a pivot menu for that cell. Either the dropdown at the top or a row heading - todo address the row heading having excel style dropdown as well as our pivot style box
            selectionList = "`" + selectionName + "` children sorted";
            selectionName = "az_" + selectionName.trim();
            queryResultRegion = new CellRegion(event.getRow(), event.getColumn());
        } else {
            SName allowableReports = myzss.getBook().getInternalBook().getNameByName(ReportService.ALLOWABLE_REPORTS);
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
                                Clients.evalJavaScript("window.open(\"/api/Online?permissionid=" + URLEncoder.encode(cellValue) + "\")");
                            }
                        }
                    } catch (Exception e) {
                        //in case some cells are numeric
                    }
                }
            }
            // check to see if it's a non-pivot multi
            List<SName> names = ReportUIUtils.getNamedRegionForRowAndColumnSelectedSheet(myzss, event.getRow(), event.getColumn());
            for (SName name : names) {
                if (name.getName().toLowerCase().endsWith(MULTI.toLowerCase())) { // a new style
                    selectionName = name.getName();
                    queryResultRegion = BookUtils.getCellRegionForSheetAndName(event.getSheet(), selectionName + RESULT);
                    final SName filterQueryCell = myzss.getBook().getInternalBook().getNameByName(name.getName().substring(0, name.getName().length() - MULTI.length()) + CHOICE);
                    if (filterQueryCell != null) {
                        selectionList = BookUtils.getSnameCell(filterQueryCell).getStringValue();
                    }
                    break;
                }
            }
        }
        if (selectionList != null) {
            // finals for thread safety - use in lambdas
            final CellRegion queryResultRegion2 = queryResultRegion;
            final String selectionName2 = selectionName;
            final String selectionList2 = selectionList;
            while (filterPopup.getChildren().size() > 0) { // clear it out
                filterPopup.removeChild(filterPopup.getLastChild());
            }
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
            listbox.setRows(10);
            Listhead listhead = new Listhead();
            Listheader listheader = new Listheader();
            listheader.setLabel("Select All");
            listheader.setAlign("left");
            listhead.appendChild(listheader);
            listbox.setMultiple(true);
            listbox.setCheckmark(true);
            listbox.setWidth("350px");
            listbox.appendChild(listhead);
            try {
                List<FilterTriple> filterOptions = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                        .getFilterListForQuery(loggedInUser.getDataAccessToken(), selectionList, selectionName, loggedInUser.getUser().getEmail(), loggedInUser.getLanguages());
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
                        loggedInUser.userLog("Multi select : " + selectionName2 + "," + selectedForLog.toString());
                        RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), selectionName2, loggedInUser.getUser().getEmail(), childIds);
                        filterPopup.close();
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
                        loggedInUser.userLog("Multi select : " + selectionName2 + "," + selectedForLog.toString());
                        RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), selectionName2, loggedInUser.getUser().getEmail(), childIds);
                        filterPopup.close();
                        // factor the reload here?
                        try {
                            // new book from same source
                            final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                            for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                                newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                            }
                            if (ReportRenderer.populateBook(newBook, 0)) { // check if formulae made saveable data
                                // direct calls to the HTML are perhaps not ideal. Could be replaced with calls to expected functions?
                                Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
                            }
                            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
                            if (myzss.getSelectedSheet().isHidden()) {
                                for (SSheet s : myzss.getSBook().getSheets()) {
                                    if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                                        myzss.setSelectedSheet(s.getSheetName());
                                        break;
                                    }
                                }
                            }
                            // check to see if we need to set the selected values in a cell - or in the main cell?
                            if (queryResultRegion2 != null) {
                                String resultDescription = ChoicesService.multiList(loggedInUser, selectionName2, selectionList2);
                                final SCell cell = myzss.getSelectedSheet().getInternalSheet().getCell(queryResultRegion2.getRow(), queryResultRegion2.getColumn());
                                cell.setStringValue(resultDescription);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
            Button cancel = new Button();
            cancel.setWidth("80px");
            cancel.setLabel("Cancel");
            cancel.addEventListener("onClick",
                    event1 -> filterPopup.close());
            filterPopup.appendChild(new Separator());
            filterPopup.appendChild(save);
            filterPopup.appendChild(new Space());
            filterPopup.appendChild(cancel);
            filterPopup.appendChild(new Separator());
            filterPopup.appendChild(saveAndReload);
            // "after_start" is the position we'd want
            filterPopup.open(event.getPageX(), event.getPageY());
        }
        Clients.evalJavaScript("window.skipSetting = 0;window.skipMarker = 0;");
    }

    // In theory could just have on cellchange but this seems to have broken the dropdowns onchange stuff, ergh. Luckily it seems there's no need for code duplication
    // checking for save stuff in the onchange and the other stuff here

    @Listen("onStopEditing = #myzss")
    public void onStopEditing(StopEditingEvent event) {
        String chosen = (String) event.getEditingValue();
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        List<SName> names = ReportUIUtils.getNamedRegionForRowAndColumnSelectedSheet(myzss, event.getRow(), event.getColumn());
        boolean reload = false;
        for (SName name : names) {
            if (name.getName().endsWith("Chosen") && name.getRefersToCellRegion().getRowCount() == 1) {// would have been a one cell name
                //and it cannot be in an existing data region
                if (BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), myzss.getSelectedSheet(), ReportRenderer.AZREPEATSCOPE).size() == 0
                        && BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), myzss.getSelectedSheet(), "az_dataregion").size() == 0) {

                    String choice = name.getName().substring(0, name.getName().length() - "Chosen".length());
                    loggedInUser.userLog("Choice select : " + choice + "," + chosen);
                    SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choice, chosen.trim());
                    // I'm not sure exactly why blankDependantChoices was commented, commenting the other two redundant lines
                    //List<String> changedChoice = new ArrayList<>();
                    //changedChoice.add(choice);
                    // hopefully self explanatory :)
                    //zkAzquoBookUtils.blankDependantChoices(loggedInUser, changedChoice, event.getSheet());
                    reload = true;
                    break;
                }
            }
            // todo, add row heading later if required
            if (name.getName().startsWith("az_DisplayColumnHeadings")) { // ok going to try for a sorting detect
                String region = name.getName().substring("az_DisplayColumnHeadings".length());
                UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    SName optionsRegion = event.getSheet().getBook().getInternalBook().getNameByName(ReportRenderer.AZOPTIONS + region);
                    String source = null;
                    if (optionsRegion != null) {
                        source = BookUtils.getSnameCell(optionsRegion).getStringValue();
                    }
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);
                }
                // ok here's the thing, the value on the spreadsheet (heading) is no good, it could be just for display, I want what the database would call the heading
                // so I'd better get the headings.
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region); // maybe jam this object against the book? Otherwise multiple books could cause problems
                if (cellsAndHeadingsForDisplay != null) {
                    //int localRow = event.getRow() - name.getRefersToCellRegion().getRow();
                    int localRow = cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1;//assume the bottom row
                    int localCol = event.getColumn() - name.getRefersToCellRegion().getColumn();
                    if (cellsAndHeadingsForDisplay.getColumnHeadings().get(localRow) != null) {
                        String originalHeading = cellsAndHeadingsForDisplay.getColumnHeadings().get(localRow).get(localCol);
                        if (originalHeading != null) {
                            //← → ↑ ↓ ↔ ↕
                            //new behaviour, store the column number rather than the name. - WFC
                            originalHeading = localCol + "";
                            if (chosen.endsWith("↑")) {
                                userRegionOptions.setSortColumn(originalHeading);
                                userRegionOptions.setSortColumnAsc(true);
                                UserRegionOptionsDAO.store(userRegionOptions);
                                reload = true;
                            }
                            if (chosen.endsWith("↓")) {
                                userRegionOptions.setSortColumn(originalHeading);
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
            try {
                // new book from same source
                final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                }
                newBook.getInternalBook().setAttribute(OnlineController.LOCKED_RESULT, null); // zap the locked result, it will be checked below and we only want it there if  populate book put it there
                if (ReportRenderer.populateBook(newBook, 0)) { // check if formulae made saveable data
                    Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
                }
                if (newBook.getInternalBook().getAttribute(OnlineController.LOCKED_RESULT) != null) {
                    String message = (String) newBook.getInternalBook().getAttribute(OnlineController.LOCKED_RESULT);
                    Clients.evalJavaScript("document.getElementById(\"lockedResult\").innerHTML='<textarea class=\"public\" style=\"height:60px;width:400px;font:10px monospace;overflow:auto;font-family:arial;background:#f58030;color:#fff;font-size:14px;border:0\">" + StringEscapeUtils.escapeJavaScript(message) + "</textarea>';");
                } else {
                    Clients.evalJavaScript("document.getElementById(\"lockedResult\").innerHTML='';");
                }
                myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
                if (myzss.getSelectedSheet().isHidden()) {
                    for (SSheet s : myzss.getSBook().getSheets()) {
                        if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                            myzss.setSelectedSheet(s.getSheetName());
                            break;
                        }
                    }
                }

                // ok there is a danger right here : on some sheets the spreadsheet gets kind of frozen or rather the cells don't calculate until something like a scroll happens. Not a problem when not full screen either
                // really something to send to ZK? Could be a apin to prepare. TODO.
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // used directly below, I need a list of the following
    static class RegionRowCol {
        public final String region;
        public final int row;
        public final int col;

        RegionRowCol(String region, int row, int col) {
            this.region = region;
            this.row = row;
            this.col = col;
        }
    }

    // to detect data changes.
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
        List<SName> names = BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), myzss.getSelectedSheet());
        List<SName> repeatRegionNames = BookUtils.getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), myzss.getSelectedSheet(), ReportRenderer.AZREPEATSCOPE);
        if (names == null && repeatRegionNames == null) return;
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        // regions may overlap - update all EXCEPT where there are repeat regions, in which case just do that as repeat regions will have overlap by their nature
        List<RegionRowCol> regionRowColsToSave = new ArrayList<>(); // a list to save - list due to the possibility of overlapping data regions
        if (repeatRegionNames != null && !repeatRegionNames.isEmpty()) {
            final RegionRowCol regionRowColForRepeatRegion = ReportUIUtils.getRegionRowColForRepeatRegion(book, row, col, repeatRegionNames.get(0));
            if (regionRowColForRepeatRegion != null) {
                regionRowColsToSave.add(regionRowColForRepeatRegion);
            }
        }
        if (regionRowColsToSave.isEmpty() && names != null) { // no repeat regions but there are normal ones
            for (SName name : names) {
                regionRowColsToSave.add(new RegionRowCol(name.getName().substring("az_DataRegion".length()), row - name.getRefersToCellRegion().getRow(), col - name.getRefersToCellRegion().getColumn()));
            }
        }
        for (RegionRowCol regionRowCol : regionRowColsToSave) {
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, regionRowCol.region);
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
    }

    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) {
        provenanceForRowAndColumn(cellMouseEvent.getRow(), cellMouseEvent.getColumn(), cellMouseEvent.getClientx(), cellMouseEvent.getClienty());
    }


    private void provenanceForRowAndColumn(int cellRow, int cellCol, int mouseX, int mouseY) {
        provenanceForRowAndColumn(cellRow, cellCol, mouseX, mouseY, null);
    }

    private void provenanceForRowAndColumn(int cellRow, int cellCol, Component ref) {
        provenanceForRowAndColumn(cellRow, cellCol, 0, 0, ref);
    }

    // 17/01/17 : this function is over 300 lines and must be broken up really . . .
    private void provenanceForRowAndColumn(int cellRow, int cellCol, int mouseX, int mouseY, Component ref) {
        while (editPopup.getChildren().size() > 0) { // clear it out
            editPopup.removeChild(editPopup.getLastChild());
        }
        Component popupChild = provenancePopup.getFirstChild();
        while (popupChild != null) {
            provenancePopup.removeChild(popupChild);
            popupChild = provenancePopup.getFirstChild();
        }
        // clear debug too, factor at some point?
        popupChild = debugPopup.getFirstChild();
        while (popupChild != null) {
            debugPopup.removeChild(popupChild);
            popupChild = debugPopup.getFirstChild();
        }
        SCell sCell = myzss.getSelectedSheet().getInternalSheet().getCell(cellRow, cellCol);
        if (sCell.getType() == SCell.CellType.FORMULA) {
            String formula = sCell.getFormulaValue();
            Label provenanceLabel = new Label();
            provenanceLabel.setMultiline(true);
            provenanceLabel.setValue(ReportUIUtils.trimString("Spreadsheet formula: =" + formula));
            provenancePopup.appendChild(provenanceLabel);
            Menuitem auditItem = new Menuitem("Audit");
            editPopup.appendChild(auditItem);
            auditItem.setPopup(provenancePopup);
//            auditItem.addEventListener("onClick",
//                    event -> System.out.println("audit menu item clicked, formula bit . . ."));
            if (ref != null) {
                editPopup.open(ref, "at_pointer");
            } else {
                editPopup.open(mouseX - 140, mouseY);
            }
        } else {
            Book book = myzss.getBook();
            int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
            LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
            String region = null;
            int regionRow = 0;
            int regionColumn = 0;
            // adding support for repeat regions. There's an additional check for row headings in a normal data region but I think this is redundant in repeat regions
            List<SName> repeatRegionNames = BookUtils.getNamedRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet(), ReportRenderer.AZREPEATSCOPE);
            if (repeatRegionNames != null && !repeatRegionNames.isEmpty()) {
                RegionRowCol regionRowColForRepeatRegion = ReportUIUtils.getRegionRowColForRepeatRegion(myzss.getBook(), cellRow, cellCol, repeatRegionNames.get(0));
                if (regionRowColForRepeatRegion != null) {
                    region = regionRowColForRepeatRegion.region;
                    regionRow = regionRowColForRepeatRegion.row;
                    regionColumn = regionRowColForRepeatRegion.col;
                }
            } else { // standard
                List<SName> names = BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet());
                for (SName name : names) {
                    SName rowHeadingsName = myzss.getSelectedSheet().getBook().getInternalBook().getNameByName("az_rowheadings" + name.getName().substring(13));
                    if (rowHeadingsName != null) {
                        region = name.getName().substring("az_DataRegion".length());
                        // ok this is a bit nasty, after Azquobook is zapped we could try something different (regarding the way strings are built manually here)
                        regionRow = cellRow - name.getRefersToCellRegion().getRow();
                        regionColumn = cellCol - name.getRefersToCellRegion().getColumn();
                        break;
                    }
                }
            }
            try {
                final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, reportId, region, regionRow, regionColumn, 1000);
                if (provenanceDetailsForDisplay.getProcenanceForDisplayList() != null && !provenanceDetailsForDisplay.getProcenanceForDisplayList().isEmpty()) {
                    Label provenanceLabel;
                    if (provenanceDetailsForDisplay.getFunction() != null){
                        provenanceLabel = new Label();
                        provenanceLabel.setMultiline(true);
                        provenanceLabel.setValue(provenanceDetailsForDisplay.getFunction() + "\n");
                        provenancePopup.appendChild(provenanceLabel);
                    }
                    int count = 0;
                    for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getProcenanceForDisplayList()){
                        boolean breakLoop = false;
                        provenanceLabel = new Label();
                        provenanceLabel.setMultiline(true);
                        provenanceLabel.setValue(provenanceForDisplay.toString() + "\n");
                        provenancePopup.appendChild(provenanceLabel);
                        if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()){
                            StringBuilder names  = new StringBuilder();
                            for (String name : provenanceForDisplay.getNames()){
                                names.append("\t" + name);
                            }
                            provenanceLabel = new Label();
                            provenanceLabel.setMultiline(true);
                            provenanceLabel.setValue(names.toString() + "\n");
                            provenancePopup.appendChild(provenanceLabel);
                        }
                        if (provenanceForDisplay.getValuesWithIdsAndNames() != null && !provenanceForDisplay.getValuesWithIdsAndNames().isEmpty()){
                            for (TypedPair<Integer, List<String>> value : provenanceForDisplay.getValuesWithIdsAndNames()){
                                if (value.getSecond() != null && !value.getSecond().isEmpty()){
                                    Iterator<String> it = value.getSecond().iterator();
                                    if (provenanceForDisplay.isInSpreadsheet() && value.getFirst() != null && value.getFirst() > 0){
                                        final Toolbarbutton provButton = new Toolbarbutton("\t" + it.next());
                                        provButton.addEventListener("onClick",
                                                event -> showProvenance(provenanceForDisplay.getName(), provenanceForDisplay.getContext(), value.getFirst()));
                                        provenancePopup.appendChild(provButton);
                                    } else {
                                        provenanceLabel = new Label();
                                        provenanceLabel.setMultiline(true);
                                        provenanceLabel.setValue("\t" + it.next());
                                        provenancePopup.appendChild(provenanceLabel);
                                    }
                                    StringBuilder names = new StringBuilder();
                                    while (it.hasNext()){
                                        if (names.length() > 0){
                                            names.append(", ");
                                        }
                                        names.append(it.next());
                                    }
                                    provenanceLabel = new Label();
                                    provenanceLabel.setMultiline(true);
                                    provenanceLabel.setValue("\t\t" + names.toString() + "\n");
                                    provenancePopup.appendChild(provenanceLabel);
                                    count++;
                                    if (count > 20){
                                        provenanceLabel = new Label();
                                        provenanceLabel.setMultiline(true);
                                        provenanceLabel.setValue("\t\t.......\n");
                                        provenancePopup.appendChild(provenanceLabel);
                                        breakLoop = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (breakLoop){
                            break;
                        }
                    }
                    Toolbarbutton button = new Toolbarbutton("Download Full Audit");
                    // todo - factor with very similar code to downloiad debug info
                    button.addEventListener("onClick",
                            event ->
                            {
                                OnlineReport or = OnlineReportDAO.findById(reportId);
                                Book book1 = Importers.getImporter().imports(Sessions.getCurrent().getWebApp().getResourceAsStream("/WEB-INF/DebugAudit.xlsx"), "Report name");
                                for (int sheetNumber = 0; sheetNumber < book1.getNumberOfSheets(); sheetNumber++) {
                                    Sheet sheet = book1.getSheetAt(sheetNumber);
                                    List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
                                    for (SName sName : namesForSheet) {
                                        if (sName.getName().equalsIgnoreCase("Title")) {
                                            final SCell cell = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
                                            cell.setStringValue("Audit " + or.getReportName());
                                        }
                                        if (sName.getName().equalsIgnoreCase("Data")) {
                                            int yOffset = 0;
                                            for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getProcenanceForDisplayList()){
                                                int xOffset = 0;
                                                boolean breakLoop = false;
                                                sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(provenanceForDisplay.toString());
                                                yOffset++;
                                                if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()){
                                                    for (String name : provenanceForDisplay.getNames()){
                                                        xOffset++;
                                                        sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(name);
                                                    }
                                                    yOffset++;
                                                }
                                                if (provenanceForDisplay.getValuesWithIdsAndNames() != null && !provenanceForDisplay.getValuesWithIdsAndNames().isEmpty()){
                                                    for (TypedPair<Integer, List<String>> value : provenanceForDisplay.getValuesWithIdsAndNames()){
                                                        if (value.getSecond() != null && !value.getSecond().isEmpty()){
                                                            for (String valueOrName : value.getSecond()) {
                                                                xOffset++;
                                                                sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(valueOrName);
                                                            }
                                                            yOffset++;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Exporter exporter = Exporters.getExporter();
                                File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                                FileOutputStream fos = null;
                                try {
                                    fos = new FileOutputStream(file);
                                    exporter.export(book1, fos);
                                } finally {
                                    if (fos != null) {
                                        fos.close();
                                    }
                                }
                                Filedownload.save(new AMedia("Audit " + or.getReportName() + ".xlsx", null, null, file, true));
                            });
                    provenancePopup.appendChild(button);
                    Menuitem auditItem = new Menuitem("Audit");
                    editPopup.appendChild(auditItem);
                    auditItem.setPopup(provenancePopup);
//                            auditItem.addEventListener("onClick",
//                                    event -> System.out.println("audit menu item clicked"));
                    // only check for drilldown on proper data, that which could have provenance
                    for (SName sName : myzss.getBook().getInternalBook().getNames()) {
                        if (sName.getName().toLowerCase().startsWith("az_drilldown" + region.toLowerCase())) {
                            String qualifier = sName.getName().substring(("az_drilldown" + region).length()).replace("_", " ");
                            String drillDownString = BookUtils.getSnameCell(sName).getStringValue();
                            if (drillDownString.length() > 0) {
                                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
                                final List<String> rowHeadings = cellsAndHeadingsForDisplay.getRowHeadings().get(regionRow);
                                List<String> colHeadings = new ArrayList<>();
                                for (int rowNo = 0; rowNo< cellsAndHeadingsForDisplay.getColumnHeadings().size();rowNo++){
                                    colHeadings.add(cellsAndHeadingsForDisplay.getColumnHeadings().get(rowNo).get(regionColumn)); // last one is the bottom row of col headings
                                }
                                //String rowHeading = rowHeadings.get(rowHeadings.size() - 1); // the right of the row headings for that cell
                                for (int colNo = 0; colNo < rowHeadings.size(); colNo++){
                                    drillDownString = ReportUIUtils.replaceAll(drillDownString,"[rowheading" + (colNo + 1) + "]", rowHeadings.get(colNo));
                                }
                                drillDownString = ReportUIUtils.replaceAll(drillDownString,"[rowheading]", rowHeadings.get(rowHeadings.size()-1));
                                for (int rowNo = 0; rowNo < colHeadings.size();rowNo++){
                                    drillDownString = ReportUIUtils.replaceAll(drillDownString,"[columnheading" + (rowNo + 1) + "]", colHeadings.get(rowNo));
                                }
                                drillDownString = ReportUIUtils.replaceAll(drillDownString,"[columnheading]", colHeadings.get(colHeadings.size()-1));

                                Menuitem ddItem = new Menuitem("Drill Down" + qualifier);
                                editPopup.appendChild(ddItem);
                                // right, showProvenance has been simplified a little, I need to parse out the report name here
                                String reportName = "";
                                if (drillDownString.contains(Constants.IN_SPREADSHEET)){
                                    drillDownString = drillDownString.substring(drillDownString.indexOf(Constants.IN_SPREADSHEET) + Constants.IN_SPREADSHEET.length()).trim();
                                    if (drillDownString.contains(" with ")){
                                        reportName = drillDownString.substring(0, drillDownString.indexOf(" with "));
                                        drillDownString = drillDownString.substring(drillDownString.indexOf(" with ") + 6);
                                    }
                                }
                                final String freportName = reportName;
                                final String context = drillDownString; // by now it should be context
                                ddItem.addEventListener("onClick",
                                        event -> showProvenance(freportName, context, 0));
                                // now need to find the headings - is this easy?
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // ok, adding new debug info here, it doens't require values in the cell unlike provenance
            try {
                String debugString = SpreadsheetService.getDebugForCell(loggedInUser, reportId, region, regionRow, regionColumn);
                Label debugLabel = new Label();
                debugLabel.setMultiline(true);
                debugLabel.setValue("Debug\n");
                debugPopup.appendChild(debugLabel);
                debugLabel = new Label();
                debugLabel.setMultiline(true);
                debugLabel.setValue(ReportUIUtils.trimString(debugString));
                debugPopup.appendChild(debugLabel);
                Toolbarbutton button = new Toolbarbutton("Download Full Debug");
                button.addEventListener("onClick",
                        event -> {
                            OnlineReport or = OnlineReportDAO.findById(reportId);
                            Book book1 = Importers.getImporter().imports(Sessions.getCurrent().getWebApp().getResourceAsStream("/WEB-INF/DebugAudit.xlsx"), "Report name");
                            for (int sheetNumber = 0; sheetNumber < book1.getNumberOfSheets(); sheetNumber++) {
                                Sheet sheet = book1.getSheetAt(sheetNumber);
                                List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
                                for (SName sName : namesForSheet) {
                                    if (sName.getName().equalsIgnoreCase("Title")) {
                                        final SCell cell = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
                                        cell.setStringValue("Debug " + or.getReportName());
                                    }
                                    if (sName.getName().equalsIgnoreCase("Data")) {
                                        // here we parse out the string into cells. It could be passed as arrays but I'm not sure how much help that is
                                        final String[] split = debugString.split("\n");
                                        int yOffset = 0;
                                        for (String line : split) {
                                            int xOffset = 0;
                                            String[] cells = line.split("\t");
                                            for (String cell : cells) {
                                                sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(cell);
                                                xOffset++;
                                            }
                                            yOffset++;
                                        }
                                    }
                                }
                            }

                            Exporter exporter = Exporters.getExporter();
                            File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                            FileOutputStream fos = null;
                            try {
                                fos = new FileOutputStream(file);
                                exporter.export(book1, fos);
                            } finally {
                                if (fos != null) {
                                    fos.close();
                                }
                            }
                            Filedownload.save(new AMedia("Debug " + or.getReportName() + ".xlsx", null, null, file, true));
                        });
                debugPopup.appendChild(button);
                Menuitem debugItem = new Menuitem("Debug");
                editPopup.appendChild(debugItem);
                debugItem.setPopup(debugPopup);
                instructionsLabel.setValue("");
            } catch (Exception e) {
                e.printStackTrace();
            }

            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, region);
            if (sentCells != null && sentCells.getData().size() > 0) {
                StringBuilder instructionsText = new StringBuilder();
                instructionsText.append("COLUMN HEADINGS\n\n");
                List<List<String>> headings = sentCells.getColHeadingsSource();
                if (headings != null) {
                    for (List<String> row : sentCells.getColHeadingsSource()) { // flatten for the mo
                        for (String cell : row) {
                            instructionsText.append(cell + "\n");
                        }
                    }
                }
                instructionsText.append("\nROW HEADINGS\n\n");
                headings = sentCells.getRowHeadingsSource();
                if (headings != null) {
                    for (List<String> row : sentCells.getRowHeadingsSource()) { // flatten for the mo
                        for (String cell : row) {
                            instructionsText.append(cell + "\n");
                        }
                    }
                }
                headings = sentCells.getContextSource();
                instructionsText.append("\nCONTEXT\n\n");
                if (headings != null) {
                    for (List<String> row : sentCells.getContextSource()) { // flatten for the mo
                        for (String cell : row) {
                            instructionsText.append(cell + "\n");
                        }
                    }
                }
                instructionsLabel.setValue(instructionsText.toString());
            }
            Menuitem instructionsItem = new Menuitem("Region definition");
            editPopup.appendChild(instructionsItem);
            instructionsItem.setPopup(instructionsPopup);

            popupChild = highlightPopup.getFirstChild();
            while (popupChild != null) {
                highlightPopup.removeChild(popupChild);
                popupChild = highlightPopup.getFirstChild();
            }
            UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
            int highlightDays = 0;
            if (userRegionOptions != null) {
                highlightDays = userRegionOptions.getHighlightDays();
            }
            String highlightString = "no highlight";
            if (highlightDays > 0) highlightString = highlightDays + " days";
            final String highlightList = "Current highlight is " + highlightString + "\n";
            Label highlightLabel = new Label();
            highlightLabel.setMultiline(true);
            highlightLabel.setValue(highlightList);
            highlightPopup.appendChild(highlightLabel);
            addHighlight(highlightPopup, 0);
            addHighlight(highlightPopup, 2);
            addHighlight(highlightPopup, 1);
            addHighlight(highlightPopup, 7);
            addHighlight(highlightPopup, 30);
            addHighlight(highlightPopup, 90);
            Menuitem highlightItem = new Menuitem("Highlight");
            editPopup.appendChild(highlightItem);
            highlightItem.setPopup(highlightPopup);
            if (ref != null) {
                editPopup.open(ref, "at_pointer");
            } else {
                editPopup.open(mouseX - 140, mouseY);
            }
        }
    }

    private void showProvenance(String reportName, String context, int valueId) {
        final Book book = myzss.getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        ChoicesService.setChoices(loggedInUser, context);
        OnlineReport or = null;
        String permissionId = null;
        if (reportName != null) {
            if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                int databaseId = loggedInUser.getDatabase().getId();
                or = OnlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
                if (or == null) {
                    or = OnlineReportDAO.findForDatabaseIdAndName(0, reportName);
                }
            } else if (loggedInUser.getPermissionsFromReport() != null) {
                if (loggedInUser.getPermissionsFromReport().get(reportName.toLowerCase()) != null) {
                    permissionId = reportName;
                }
            }
        } else {
            reportName = "unspecified";
        }
        if (permissionId != null) { // database removed from permission, redundant
            Clients.evalJavaScript("window.open(\"/api/Online?permissionid=" + permissionId + (valueId != 0 ? "&valueid=" + valueId : "") + "\")");
        } else if (or != null) {
            Clients.evalJavaScript("window.open(\"/api/Online?reportid=" + or.getId() + "&database=" + loggedInUser.getDatabase().getName() + (valueId != 0 ? "&valueid=" + valueId : "") + "\")");
        } else {
            Clients.evalJavaScript("alert(\"the report '" + reportName + "` is no longer available\")");
        }
    }

    private void addHighlight(Popup highlightPopup, final int days) {
        String hDays = days + " days";
        if (days == 0) hDays = "none";
        if (days == 2) hDays = "1 hour";
        final Toolbarbutton highlightButton = new Toolbarbutton("highlight " + hDays);
        highlightButton.addEventListener("onClick",
                event -> showHighlight(days));
        highlightPopup.appendChild(highlightButton);
        Label highlightLabel = (new Label("\n\n"));
        highlightLabel.setMultiline(true);
        highlightPopup.appendChild(highlightLabel);
    }

    private void showHighlight(int days) {
        Book book = myzss.getBook();
        boolean reload = false;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith("az_dataregion")) {
                String region = name.getName().substring(13);
                UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    if (days == 0) break;
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, "highlight=" + days + "\n");
                } else {
                    if (userRegionOptions.getHighlightDays() == days) break;
                    userRegionOptions.setHighlightDays(days);
                }
                UserRegionOptionsDAO.store(userRegionOptions);
                reload = true;
            }
        }
        if (!reload) return;
        try {
            // new book from same source
            final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
            for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
            }
            if (ReportRenderer.populateBook(newBook, 0)) { // check if formulae made saveable data
                Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
            }
            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
            if (myzss.getSelectedSheet().isHidden()) {
                for (SSheet s : myzss.getSBook().getSheets()) {
                    if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                        myzss.setSelectedSheet(s.getSheetName());
                        break;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}