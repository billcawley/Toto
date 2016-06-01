package com.azquo.spreadsheet.view;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.zkoss.chart.ChartsEvent;
import org.zkoss.web.servlet.http.Encodes;
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
import com.azquo.memorydb.TreeNode;

import java.io.File;
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
    private SpreadsheetService spreadsheetService;
    private LoginService loginService;
    private RMIClient rmiClient;
    private UserChoiceDAO userChoiceDAO;
    private UserRegionOptionsDAO userRegionOptionsDAO;
    private Popup provenancePopup = null;
    private Popup highlightPopup = null;
    private Popup instructionsPopup = null;
    private Popup filterPopup = null;

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        Session session = Sessions.getCurrent();
        ApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(session.getWebApp().getServletContext());
        // todo - check ZK to see if there's a better way to do this
        spreadsheetService = (SpreadsheetService) applicationContext.getBean("spreadsheetService");
        loginService = (LoginService) applicationContext.getBean("loginService");
        userChoiceDAO = (UserChoiceDAO) applicationContext.getBean("userChoiceDao");
        userRegionOptionsDAO = (UserRegionOptionsDAO) applicationContext.getBean("userRegionOptionsDao");
        rmiClient = (RMIClient) applicationContext.getBean("rmiClient");
        editPopup.setId("editPopup");
        editPopup.setStyle("background-color:#ffffff");
        editPopup.setStyle("border: 5px solid #F58030");
        /*
        Menuitem item1 = new Menuitem("Audit");
        item1.setId("provenance");
        Menuitem item2 = new Menuitem("Region Spec");
        Menuitem item3 = new Menuitem("highlight options");
        item2.setId("regionSpec");
        item3.setId("highlightOptions");
//        Menuitem item3 = new Menuitem("Region Options");
//        item3.setId("regionOptions");
        editPopup.appendChild(item1);
        editPopup.appendChild(item2);
        editPopup.appendChild(item3);
*/
        provenancePopup = new Popup();
        provenancePopup.setId("provenancePopup");
        provenancePopup.setDraggable("true");
        provenancePopup.setDroppable("true");
        provenancePopup.setStyle("background-color:#ffffff");
        provenancePopup.setStyle("border: 5px solid #F58030");
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
        //item2.setPopup(instructionsPopup);
        //item3.setPopup(highlightPopup);
        // much hacking went into getting an appropriate object to hook into to make our extra contextual menu
        filterPopup = new Popup();
        filterPopup.setId("filterPopup");
        filterPopup.setStyle("background-color:#ffffff");
        filterPopup.setStyle("border: 5px solid #F58030");

        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
            myzss.getFirstChild().appendChild(provenancePopup);
            myzss.getFirstChild().appendChild(instructionsPopup);
            myzss.getFirstChild().appendChild(highlightPopup);
            myzss.getFirstChild().appendChild(filterPopup);
        } else { // it took some considerable time to work out this hack
            Ghost g = new Ghost();
            g.setAttribute("zsschildren", "");
            myzss.appendChild(g);
            g.appendChild(editPopup);
            g.appendChild(provenancePopup);
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
    }

    private static String MULTI = "Multi";
    private static String CHOICE = "Choice";
    private static String RESULT = "Result";

    /* Bit of an odd one this : on a cell click "wake" the log between the client and report server back up as there may be activity shortly
    In addition I now want to now deal with the new filter things - this was by a cell area a la WASPS but now we'll do it by a popup
     */

    @Listen("onCellClick = #myzss")
    public void onCellClick(CellMouseEvent event) {
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        final ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, loginService, userChoiceDAO, userRegionOptionsDAO, rmiClient); // used in more than one place
        String selectionName = pivotItem(zkAzquoBookUtils, event);
        String selectionList = null;
        CellRegion queryResultRegion = null;
        if (selectionName != null) { // we have a pivot menu for that cell. Either the dropdown at the top or a row heading - todo address the row heading having excel style dropdown as well as our pivot style box
            selectionList = "`" + selectionName + "` children sorted";
            selectionName = "az_" + selectionName.trim();
            queryResultRegion = new CellRegion(event.getRow(), event.getColumn());
        } else { // check to see if it's a non-pivot multi
            List<SName> names = getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn());
            for (SName name : names) {
                if (name.getName().toLowerCase().endsWith(MULTI.toLowerCase())) { // a new style
                    selectionName = name.getName();
                    queryResultRegion = ZKAzquoBookUtils.getCellRegionForSheetAndName(event.getSheet(), selectionName + RESULT);
                    final SName filterQueryCell = myzss.getBook().getInternalBook().getNameByName(name.getName().substring(0, name.getName().length() - MULTI.length()) + CHOICE);
                    if (filterQueryCell != null) {
                        final SCell cell = myzss.getSelectedSheet().getInternalSheet().getCell(filterQueryCell.getRefersToCellRegion().getRow(), filterQueryCell.getRefersToCellRegion().getColumn());
                        selectionList = cell.getStringValue();
                    }
                    break;
                }
                if (name.getName().equalsIgnoreCase(ZKAzquoBookUtils.ALLOWABLE_REPORTS)){
                    String cellValue = "";
                    final SCell cell = myzss.getSelectedSheet().getInternalSheet().getCell(event.getRow(), name.getRefersToCellRegion().getColumn());// we care about the contents of the left most cell
                    if (!cell.isNull()){
                        cellValue = cell.getStringValue();
                    }
                    if (!cellValue.isEmpty()){ // deal with security in the online controller
                        Clients.evalJavaScript("window.open(\"/api/Online?permissionid=" + URLEncoder.encode(cellValue) + "\")");
                    }
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
                List<FilterTriple> filterOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
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
                        for (Listitem listItem : selectedItems) {
                            childIds.add(Integer.parseInt(listItem.getValue())); // should never fail on the parse
                        }
                        rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), selectionName2, loggedInUser.getUser().getEmail(), childIds);
                        filterPopup.close();
                    });

            Button saveAndReload = new Button();
//            saveAndReload.setWidth("80px");
            saveAndReload.setLabel("Save and reload");
            saveAndReload.addEventListener("onClick",
                    event1 -> {
                        List<Integer> childIds = new ArrayList<>();
                        final Set<Listitem> selectedItems = listbox.getSelectedItems();
                        for (Listitem listItem : selectedItems) {
                            childIds.add(Integer.parseInt(listItem.getValue())); // should never fail on the parse
                        }
                        rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), selectionName2, loggedInUser.getUser().getEmail(), childIds);
                        filterPopup.close();
                        // factor the reload here?
                        try {
                            // new book from same source
                            final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                            for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                                newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                            }
                            if (zkAzquoBookUtils.populateBook(newBook, 0)) { // check if formulae made saveable data
                                // direct calls to the HTML are perhaps not ideal. Could be replaced with calls to expected functions?
                                Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
                            }
                            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
                            // check to see if we need to set the selected values in a cell - or in the main cell?
                            if (queryResultRegion2 != null) {
                                String resultDescription = zkAzquoBookUtils.multiList(loggedInUser, selectionName2, selectionList2);
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
        final ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, loginService, userChoiceDAO, userRegionOptionsDAO, rmiClient); // used in more than one place
        String chosen = (String) event.getEditingValue();
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        List<SName> names = getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn());
        boolean reload = false;
        for (SName name : names) {
            if (name.getName().endsWith("Chosen") && name.getRefersToCellRegion().getRowCount() == 1) {// would have been a one cell name
                String choice = name.getName().substring(0, name.getName().length() - "Chosen".length());
                spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choice, chosen);
                // I'm not sure exactly why blankDependantChoices was commented, commenting the other two redundant lines
                //List<String> changedChoice = new ArrayList<>();
                //changedChoice.add(choice);
                // hopefully self explanatory :)
                //zkAzquoBookUtils.blankDependantChoices(loggedInUser, changedChoice, event.getSheet());
                reload = true;
                break;
            }
            // todo, add row heading later if required
            if (name.getName().startsWith("az_DisplayColumnHeadings")) { // ok going to try for a sorting detect
                String region = name.getName().substring("az_DisplayColumnHeadings".length());
                UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    SName optionsRegion = event.getSheet().getBook().getInternalBook().getNameByName(ZKAzquoBookUtils.azOptions + region);
                    String source = null;
                    if (optionsRegion != null) {
                        source = zkAzquoBookUtils.getSnameCell(optionsRegion).getStringValue();
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
                            if (chosen.endsWith("↑")) {
                                userRegionOptions.setSortColumn(originalHeading);
                                userRegionOptions.setSortColumnAsc(true);
                                userRegionOptionsDAO.store(userRegionOptions);
                                reload = true;
                            }
                            if (chosen.endsWith("↓")) {
                                userRegionOptions.setSortColumn(originalHeading);
                                userRegionOptions.setSortColumnAsc(false);
                                userRegionOptionsDAO.store(userRegionOptions);
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
                if (zkAzquoBookUtils.populateBook(newBook, 0)) { // check if formulae made saveable data
                    Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
                }
                myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        List<SName> names = ZKAzquoBookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn(), myzss.getSelectedSheet());
        if (names == null) return;
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);

        for (SName name : names) { // regions may overlap - update all!
            String region = name.getName().substring("az_DataRegion".length());
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, region);
            if (sentCells != null) {
                // the data region as defined on the cheet may be larger than the sent cells
                if (sentCells.getData().size() > row - name.getRefersToCellRegion().getRow()
                        && sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).size() > col - name.getRefersToCellRegion().getColumn()) {
                    CellForDisplay cellForDisplay = sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).get(col - name.getRefersToCellRegion().getColumn());
                    Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
                    if (isDouble) {
                        cellForDisplay.setDoubleValue(doubleValue);
                    }
                    cellForDisplay.setStringValue(chosen);
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

    // Commented, why?
    @Listen("onSheetSelect = #myzss")
    public void onSheetSelect(SheetSelectEvent sheetSelectEvent) {
        // now here's the thing, I need to re add the validation as it gets zapped for some reason
        //ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO, rmiClient);
        // Book book = sheetSelectEvent.getSheet().getBook();
        // final Map<String, List<String>> choiceOptions = zkAzquoBookUtils.resolveChoiceOptions(sheetSelectEvent.getSheet().getBook(), (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER));
        // zkAzquoBookUtils.addValidation(sheetSelectEvent.getSheet().getBook(), choiceOptions, null);
    }

    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) {
        provenanceForRowAndColumn(cellMouseEvent.getRow(), cellMouseEvent.getColumn(), cellMouseEvent.getClientx(), cellMouseEvent.getClienty());
    }


    private String pivotItem(ZKAzquoBookUtils zkAzquoBookUtils, CellMouseEvent event) {
        SName pivotFilters = event.getSheet().getBook().getInternalBook().getNameByName("az_PivotFilters");
        if (pivotFilters != null) {
            String[] filters = ZKAzquoBookUtils.getSnameCell(pivotFilters).getStringValue().split(",");
            CellRegion pivotHeadings = ZKAzquoBookUtils.getCellRegionForSheetAndName(event.getSheet(), "az_PivotHeadings");
            if (pivotHeadings != null) {
                int headingRow = pivotHeadings.getRow();
                int headingCol = pivotHeadings.getColumn();
                int headingRows = pivotHeadings.getRowCount();
                int filterCount = 0;
                //on the top of pivot tables, the options are shown as pair groups separated by a space, sometimes on two rows, also separated by a space
                // this logic is repeated from add validation, dedupe?
                for (String filter : filters) {
                    List<String> optionsList = zkAzquoBookUtils.getDropdownListForQuery((LoggedInUser) event.getSheet().getBook().getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER), "`" + filter + "` children");
                    if (optionsList != null && optionsList.size() > 1) {
                        int rowOffset = filterCount % headingRows;
                        int colOffset = filterCount / headingRows;
                        int chosenRow = headingRow + rowOffset;
                        int chosenCol = headingCol + 3 * colOffset + 1;
                        if (chosenRow == event.getRow() && chosenCol == event.getColumn()) {
                            return filter.trim();
                        }
                        filterCount++;
                    }
                }
            }
        }
        //look in the row headings.   probably should also be in the column headings....
        for (SName name : event.getSheet().getBook().getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith("az_rowheadings")) {
                //surely there must be a better way of getting the first cell off a region!
                String firstItem = name.getBook().getSheetByName(name.getRefersToSheetName()).getCell(name.getRefersToCellRegion().getRow(), name.getRefersToCellRegion().getColumn()).getStringValue();
                if (firstItem.toLowerCase().startsWith("permute(")) {
                    String[] rowHeadings = firstItem.substring("permute(".length(), firstItem.length() - 1).split(",");
                    String displayRowHeadingsString = "az_Display" + name.getName().substring(3);
                    CellRegion displayRowHeadings = zkAzquoBookUtils.getCellRegionForSheetAndName(event.getSheet(), displayRowHeadingsString);
                    if (displayRowHeadings != null) {
                        int hrow = displayRowHeadings.getRow() - 1;
                        int hcol = displayRowHeadings.getColumn();
                        for (String rowHeading : rowHeadings) {
                            if (hrow == event.getRow() && hcol++ == event.getColumn()) {
                                return rowHeading.replace("`", "");
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void provenanceForRowAndColumn(int cellRow, int cellCol, int mouseX, int mouseY) {
        provenanceForRowAndColumn(cellRow, cellCol, mouseX, mouseY, null);
    }

    private void provenanceForRowAndColumn(int cellRow, int cellCol, Component ref) {
        provenanceForRowAndColumn(cellRow, cellCol, 0, 0, ref);
    }

    private void provenanceForRowAndColumn(int cellRow, int cellCol, int mouseX, int mouseY, Component ref) {
        // right now a right click gets provenance ready, dunno if I need to do this
        List<SName> names = ZKAzquoBookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet());
        while (editPopup.getChildren().size() > 0) { // clear it out
            editPopup.removeChild(editPopup.getLastChild());
        }
        Component popupChild = provenancePopup.getFirstChild();
        while (popupChild != null) {
            provenancePopup.removeChild(popupChild);
            popupChild = provenancePopup.getFirstChild();
        }
        SCell sCell = myzss.getSelectedSheet().getInternalSheet().getCell(cellRow, cellCol);
        if (sCell.getType() == SCell.CellType.FORMULA) {
            String formula = sCell.getFormulaValue();
            Label provenanceLabel = new Label();
            provenanceLabel.setMultiline(true);
            provenanceLabel.setValue(trimString("Spreadsheet formula: =" + formula));
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
            for (SName name : names) {
                SName rowHeadingsName = myzss.getSelectedSheet().getBook().getInternalBook().getNameByName("az_rowheadings" + name.getName().substring(13));
                if (rowHeadingsName != null) {
                    String region = name.getName().substring("az_DataRegion".length());
                    Book book = myzss.getBook();
                    int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                    LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
                    try {
                        // ok this is a bit nasty, after Azquobook is zapped we could try something different
                        int regionRow = cellRow - name.getRefersToCellRegion().getRow();
                        int regionColumn = cellCol - name.getRefersToCellRegion().getColumn();
                        List<TreeNode> treeNodes = spreadsheetService.getTreeNode(loggedInUser, reportId, region, regionRow, regionColumn, 1000);
                        if (!treeNodes.isEmpty()) {
                            StringBuilder toShow = new StringBuilder();
                            for (TreeNode TreeNode : treeNodes) {
                                resolveTreeNode(0, toShow, TreeNode);
                            }
                            String stringToShow = toShow.toString();
                            final String fullProvenance = stringToShow;
                            stringToShow = stringToShow.replace("\t", "....");
                            int spreadPos = stringToShow.indexOf("in spreadsheet"); // this kind of thing needs to be re coded.
                            int nextBlock;
                            while (spreadPos >= 0) {
                                int valueId = getLastValueInt(treeNodes.get(0)); // should be a root of 1 in this case. In fact that applies above?
                                int endLine = stringToShow.indexOf("\n");
                                if (endLine == -1) endLine = stringToShow.length();
                                nextBlock = stringToShow.indexOf("in spreadsheet", endLine);
                                if (nextBlock < 0) {
                                    nextBlock = stringToShow.length();
                                } else {
                                    nextBlock = stringToShow.lastIndexOf("\n", nextBlock) + 1;
                                }
                                final String provLine = stringToShow.substring(0, endLine);
                                final Toolbarbutton provButton = new Toolbarbutton(provLine);
                                provButton.addEventListener("onClick",
                                        event -> showProvenance(provLine, valueId));
                                provenancePopup.appendChild(provButton);
                                Label provenanceLabel = new Label();
                                provenanceLabel.setMultiline(true);

                                provenanceLabel.setValue(trimString(stringToShow.substring(endLine, nextBlock)));
                                provenancePopup.appendChild(provenanceLabel);
                                stringToShow = stringToShow.substring(nextBlock);
                                spreadPos = stringToShow.indexOf("in spreadsheet");
                            }
                            Label provenanceLabel = new Label();
                            provenanceLabel.setMultiline(true);
                            provenanceLabel.setValue(trimString(stringToShow));
                            provenancePopup.appendChild(provenanceLabel);

                            final Toolbarbutton button = new Toolbarbutton("Download Full Audit");
                            button.addEventListener("onClick",
                                    event -> Filedownload.save(fullProvenance, "text/csv", "audit " + name.getRefersToSheetName() + ".txt"));
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

                                    String drillDownString = ZKAzquoBookUtils.getSnameCell(sName).getStringValue();
                                    if (drillDownString.length() > 0) {
                                        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
                                        final List<String> rowHeadings = cellsAndHeadingsForDisplay.getRowHeadings().get(cellRow - name.getRefersToCellRegion().getRow());
                                        final List<String> colHeadings = cellsAndHeadingsForDisplay.getColumnHeadings().get(cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1); // last one is the bottom row of col headings
                                        String rowHeading = rowHeadings.get(rowHeadings.size() - 1); // the right of the row headings for that cell
                                        String colHeading = colHeadings.get(cellCol - name.getRefersToCellRegion().getColumn());
                                        String filler = "";
                                        while (drillDownString.toLowerCase().contains("[rowheading")) {
                                            for (int colNo = 0; colNo < rowHeadings.size(); colNo++) {
                                                String rh = "[rowheading" + filler + "]";
                                                if (drillDownString.toLowerCase().contains(rh)) {
                                                    int start = drillDownString.toLowerCase().indexOf(rh);
                                                    drillDownString = drillDownString.substring(0, start) + rowHeadings.get(colNo) + drillDownString.substring(start + rh.length());
                                                }
                                                filler = (colNo + 2) + "";
                                            }

                                        }
                                        filler = "";
                                        while (drillDownString.toLowerCase().contains("[columnheading")) {
                                            for (int rowNo = 0; rowNo < colHeadings.size(); rowNo++) {
                                                String ch = "[columnheading" + filler + "]";
                                                if (drillDownString.toLowerCase().contains(ch)) {
                                                    int start = drillDownString.toLowerCase().indexOf(ch);
                                                    drillDownString = drillDownString.substring(0, start) + colHeading + drillDownString.substring(start + ch.length());
                                                }
                                                filler = (rowNo + 2) + "";
                                            }

                                        }
                                        final String stringToPass = drillDownString;
                                        Menuitem ddItem = new Menuitem("Drill Down" + qualifier);
                                        editPopup.appendChild(ddItem);
                                        ddItem.addEventListener("onClick",
                                                event -> showProvenance(stringToPass, 0));
                                        // now need to find the headings - is this easy?
                                    }

                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    instructionsLabel.setValue("");
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
                    UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
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
        }
    }

    private void showProvenance(String provline, int valueId) {
        final Book book = myzss.getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        String reportName = spreadsheetService.setChoices(loggedInUser, provline);
        OnlineReport or = null;
        Permission permission = null;
        Session session = Sessions.getCurrent();
        ApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(session.getWebApp().getServletContext());
        if (reportName != null) {
            if (loggedInUser.getUser().isAdministrator()) {
                int databaseId = loggedInUser.getDatabase().getId();
                OnlineReportDAO onlineReportDAO = (OnlineReportDAO) applicationContext.getBean("onlineReportDao");
                or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
                if (or == null) {
                    or = onlineReportDAO.findForDatabaseIdAndName(0, reportName);
                }
            } else { // need to try to find the permission given the new rules
                PermissionDAO permissionDAO = (PermissionDAO) applicationContext.getBean("permissionDao");
                final List<Permission> forUserId = permissionDAO.findForUserId(loggedInUser.getUser().getId());
                for (Permission permission1 : forUserId) {
                    if (permission1.getDatabaseId() == loggedInUser.getDatabase().getId()) {
                        permission = permission1;
                        break;
                    }
                }
            }
        } else {
            reportName = "unspecified";
        }
        if (permission != null) { // database removed from permission, redundant
            Clients.evalJavaScript("window.open(\"/api/Online?permissionid=" + permission.getId() + "&opcode=loadsheet" + (valueId != 0 ? "&valueid=" + valueId : "") + "\")");
        } else if (or != null) {
            Clients.evalJavaScript("window.open(\"/api/Online?reporttoload=" + or.getId() + "&opcode=loadsheet&database=" + loggedInUser.getDatabase().getName() + (valueId != 0 ? "&valueid=" + valueId : "") + "\")");
        } else {
            Clients.evalJavaScript("alert(\"the report '" + reportName + "` is no longer available\")");
        }
    }

    private List<SName> getNamedRegionForRowAndColumnSelectedSheet(int row, int col) {
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = myzss.getBook();
        List<SName> found = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) { // seems best to loop through names checking which matches I think
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(myzss.getSelectedSheet().getSheetName())
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                found.add(name);
            }
        }
        return found;
    }

    private String trimString(String stringToShow) {
        if (stringToShow.length() > 200) {
            stringToShow = stringToShow.substring(0, 200) + " . . .\n";
        }
        return stringToShow;
    }

    private static void resolveTreeNode(int tab, StringBuilder stringBuilder, com.azquo.memorydb.TreeNode treeNode) {
        for (int i = 0; i < tab; i++) {
            stringBuilder.append("\t");
        }
        if (treeNode.getName() != null) {
            stringBuilder.append(treeNode.getName());
            String value = treeNode.getValue();
            if (value != null) {
                stringBuilder.append("\t");

                stringBuilder.append(treeNode.getValue());
            }
            stringBuilder.append("\n");
        }
        if (treeNode.getHeading() != null) { // then assume we have items too!
            stringBuilder.append(treeNode.getHeading());
            //stringBuilder.append("\n");
            tab++;
            for (TreeNode treeNode1 : treeNode.getChildren()) {
                resolveTreeNode(tab, stringBuilder, treeNode1);
            }
        }
    }

    private static int getLastValueInt(com.azquo.memorydb.TreeNode treeNode) {
        if (treeNode.getHeading() != null && !treeNode.getChildren().isEmpty()) { // then assume we have items too!
            return getLastValueInt(treeNode.getChildren().get(treeNode.getChildren().size() - 1));
        }
        return treeNode.getValueId();
    }

    private void addHighlight(Popup highlightPopup, final int days) {
        String hDays = days + " days";
        if (days == 0) hDays = "none";
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
                UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    if (days == 0) break;
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, "highlight=" + days + "\n");
                } else {
                    if (userRegionOptions.getHighlightDays() == days) break;
                    userRegionOptions.setHighlightDays(days);
                }
                userRegionOptionsDAO.store(userRegionOptions);
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
            ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, loginService, userChoiceDAO, userRegionOptionsDAO, rmiClient);
            if (zkAzquoBookUtils.populateBook(newBook, 0)) { // check if formulae made saveable data
                Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"block\";document.getElementById(\"restoreDataButton\").style.display=\"block\";");
            }
            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}