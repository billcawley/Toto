package com.azquo.spreadsheet.view;

import com.azquo.admin.AdminService;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.zkoss.zk.ui.*;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.*;
import org.zkoss.zssex.ui.widget.Ghost;
import org.zkoss.zul.*;
import com.azquo.memorydb.TreeNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;

    Menupopup editPopup = new Menupopup();
    //Label provenanceLabel = new Label();
    Label instructionsLabel = new Label();
    SpreadsheetService spreadsheetService;
    UserChoiceDAO userChoiceDAO;
    OnlineReportDAO onlineReportDAO;
    UserRegionOptionsDAO userRegionOptionsDAO;
    AdminService adminService;


    Popup provenancePopup = null;
    Popup highlightPopup = null;

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        // perhaps a bit long winded but it gets us the spreadsheet
        Session session = Sessions.getCurrent();
        ApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(session.getWebApp().getServletContext());
        // todo - check ZK to see if there's a better way to do this
        spreadsheetService = (SpreadsheetService) applicationContext.getBean("spreadsheetService");
        userChoiceDAO = (UserChoiceDAO) applicationContext.getBean("userChoiceDao");
        userRegionOptionsDAO = (UserRegionOptionsDAO) applicationContext.getBean("userRegionOptionsDao");
        adminService = (AdminService) applicationContext.getBean("adminService");
        editPopup.setId("editPopup");
        editPopup.setStyle("background-color:#ffffcc");
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

        provenancePopup = new Popup();
        provenancePopup.setId("provenancePopup");
        provenancePopup.setDraggable("true");
        provenancePopup.setDroppable("true");
        provenancePopup.setStyle("background:#ffffcc");
        item1.setPopup(provenancePopup); // I think that will automatically work??


        Popup instructionsPopup = new Popup();
        instructionsPopup.setId("instructionsPopup");
        instructionsLabel.setMultiline(true);
        instructionsPopup.appendChild(instructionsLabel);
        highlightPopup = new Popup();
        highlightPopup.setId("highlightPopup");
        item2.setPopup(instructionsPopup);
        item3.setPopup(highlightPopup);

        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
            myzss.getFirstChild().appendChild(provenancePopup);
            myzss.getFirstChild().appendChild(instructionsPopup);
            myzss.getFirstChild().appendChild(highlightPopup);
        } else { // it took some considerable time to work out this hack
            Ghost g = new Ghost();
            g.setAttribute("zsschildren", "");
            myzss.appendChild(g);
            g.appendChild(editPopup);
            g.appendChild(provenancePopup);
            g.appendChild(instructionsPopup);
            g.appendChild(highlightPopup);
        }

//        myzss.appendChild(editPopup);
        //myzss.getParent().appendChild(editPopup);
//        myzss.appendChild();
/*        final Button button = new Button("XLS");
        button.addEventListener("onClick",
                new EventListener() {
                    public void onEvent(Event event) throws Exception {
                        System.out.println("thing");
                    }
                });

        myzss.getFirstChild().appendChild(button);*/
//        myzss.getPage().getFirstRoot().appendChild(editPopup);
    }

    // Bit of an odd one this : on a cell click "wake" the log back up as there may be activity shortly

    @Listen("onCellClick = #myzss")
    public void onCellClick(Event event) {
        Clients.evalJavaScript("window.skipSetting = 0;window.skipMarker = 0;");
    }

//org.zkoss.zss.ui.event.Events


    // In theory could just have on cellchange but this seems to have broken the dropdowns onchange stuff, ergh. Luckily it seems there's no need for code duplication
    // checking for save stuff in the onchange and the other stuff here

    @Listen("onStopEditing = #myzss")
    public void onStopEditing(StopEditingEvent event) {
        final ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO); // used in more than one place
        String chosen = (String) event.getEditingValue();
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        SName name = getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn());
        boolean reload = false;
        if (name != null) { // as it stands regions should not overlap, we find a name that means we know what to (try) to do
            // ok it matches a name
            if (name.getName().endsWith("Chosen") && name.getRefersToCellRegion().getRowCount() == 1) {// would have been a one cell name
                String choice = name.getName().substring(0, name.getName().length() - "Chosen".length());
                spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choice, chosen);
                List<String> changedChoice = new ArrayList<>();
                changedChoice.add(choice);
                // hopefully self explanatory :)
                zkAzquoBookUtils.blankDependantChoices(loggedInUser, changedChoice, event.getSheet());
                reload = true;
            }
            // todo, add row heading later if required
            if (name.getName().startsWith("az_DisplayColumnHeadings")) { // ok going to try for a sorting detect
                String region = name.getName().substring("az_DisplayColumnHeadings".length());
                UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    CellRegion optionsRegion = ZKAzquoBookUtils.getCellRegionForSheetAndName(event.getSheet(), ZKAzquoBookUtils.azOptions + region);
                    String source = null;
                    if (optionsRegion != null) {
                        source = event.getSheet().getInternalSheet().getCell(optionsRegion.getRow(), optionsRegion.getColumn()).getStringValue();
                    }
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);
                }
                // ok here's the thing, the value on the spreadsheet (heading) is no good, it could be just for display, I want what the database would call the heading
                // so I'd better get the headings.
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(region); // maybe jam this object against the book? Otherwise multiple books could cause problems
                if (cellsAndHeadingsForDisplay != null) {
                    int localRow = event.getRow() - name.getRefersToCellRegion().getRow();
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
        // could maybe be moved up to where the boolean is set.
        if (reload) {
            try {
                // new book from same source
//                Clients.evalJavaScript("zUtl.progressbox('test1', 'edd testing',true, '')"); // make another?
//                Clients.evalJavaScript("zUtl.destroyProgressbox('test1')");
                final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                }
                if (zkAzquoBookUtils.populateBook(newBook)) { // check if formulae made saveable data
                    Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
                }
                myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
            } catch (Exception e) {
                e.printStackTrace();
            }
//            new Thread(() -> reload(book, zkAzquoBookUtils)).start(); // java 8 notation, NICE!
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
        System.out.println("after cell change : " + row + " col " + col + " chosen");
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        List<SName> names = getNamedDataRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn());
        if (names == null) return;
        for (SName name : names) { // regions may overlap - update all!
            LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
            String region = name.getName().substring("az_DataRegion".length());
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(region);
            if (sentCells != null) {
                // the data region as defined on the cheet may be larger than the sent cells
                if (sentCells.getData().size() > row - name.getRefersToCellRegion().getRow()
                        && sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).size() > col - name.getRefersToCellRegion().getColumn()) {
                    CellForDisplay cellForDisplay = sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).get(col - name.getRefersToCellRegion().getColumn());
                    Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
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

    @Listen("onSheetSelect = #myzss")
    public void onSheetSelect(SheetSelectEvent sheetSelectEvent) {
        // now here's the thing, I need to re add the validation as it gets zapped for some reason
        ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO);
        Book book = sheetSelectEvent.getSheet().getBook();
        final Map<String, List<String>> choiceOptions = zkAzquoBookUtils.resolveChoiceOptionsQueries(ZKAzquoBookUtils.getNamesForSheet(sheetSelectEvent.getSheet()), sheetSelectEvent.getSheet(), (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER));
        zkAzquoBookUtils.addValidation(ZKAzquoBookUtils.getNamesForSheet(sheetSelectEvent.getSheet()), sheetSelectEvent.getSheet(),choiceOptions);
    }

    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) {
        // roght now a right click gets provenance ready, dunno if I need to do this
        List<SName> names = getNamedDataRegionForRowAndColumnSelectedSheet(cellMouseEvent.getRow(), cellMouseEvent.getColumn());
        for (SName name : names) {
            if (ZKAzquoBookUtils.getCellRegionForSheetAndName(myzss.getSelectedSheet(), "az_rowheadings" + name.getName().substring(13)) != null) {

                Component popupChild = provenancePopup.getFirstChild();
                while (popupChild != null) {
                    provenancePopup.removeChild(popupChild);
                    popupChild = provenancePopup.getFirstChild();
                }
                String region = name.getName().substring("az_DataRegion".length());
                Book book = myzss.getBook();
                LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
                try {
                    // ok this is a bit nasty, after Azquobook is zapped we could try something different
                    // todo - sort after zapping azquobook! Maybe clickable again?
                    List<TreeNode> TreeNodes = spreadsheetService.getTreeNode(loggedInUser, region
                            , cellMouseEvent.getRow() - name.getRefersToCellRegion().getRow(), cellMouseEvent.getColumn() - name.getRefersToCellRegion().getColumn(), 1000);
                    if (!TreeNodes.isEmpty()) {
                        StringBuilder toShow = new StringBuilder();
                        for (TreeNode TreeNode : TreeNodes) {
                            resolveTreeNode(0, toShow, TreeNode);
                        }
                        String stringToShow = toShow.toString();
                        final String fullProvenance = stringToShow;
                        stringToShow = stringToShow.replace("\t", "....");
                        int spreadPos = stringToShow.indexOf("in spreadsheet");
                        int nextBlock;
                        while (spreadPos >= 0) {
                            int endLine = stringToShow.indexOf("\n");
                            nextBlock = stringToShow.indexOf("in spreadsheet", endLine);
                            if (nextBlock < 0) {
                                nextBlock = stringToShow.length();
                            } else {
                                nextBlock = stringToShow.lastIndexOf("\n", nextBlock) + 1;
                            }
                            final String provLine = stringToShow.substring(0, endLine);
                            final Toolbarbutton provButton = new Toolbarbutton(provLine);
                            provButton.addEventListener("onClick",
                                    event -> showProvenance(provLine));
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
                                event -> Filedownload.save(fullProvenance, "text/csv", "provenance.csv"));

                        provenancePopup.appendChild(button);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                instructionsLabel.setValue("");
                final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(region);
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

                popupChild = highlightPopup.getFirstChild();
                while (popupChild != null) {
                    highlightPopup.removeChild(popupChild);
                    popupChild = highlightPopup.getFirstChild();
                }
                int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
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


            }
            editPopup.open(cellMouseEvent.getClientx() - 130, cellMouseEvent.getClienty());
        }
    }

    private void showProvenance(String provline) {
        final Book book = myzss.getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        String reportName = spreadsheetService.setChoices(loggedInUser, provline);
        OnlineReport or = null;
        if (reportName != null) {
            Session session = Sessions.getCurrent();
            int databaseId = loggedInUser.getDatabase().getId();
            ApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(session.getWebApp().getServletContext());
            onlineReportDAO = (OnlineReportDAO) applicationContext.getBean("onlineReportDao");
            or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
            if (or == null) {
                or = onlineReportDAO.findForDatabaseIdAndName(0, reportName);
            }
        } else {
            reportName = "unspecified";
        }
        if (or != null) {
            Clients.evalJavaScript("window.open(\"/api/Online?reporttoload=" + or.getId() + "&opcode=loadsheet&database=" + loggedInUser.getDatabase().getName() + "\")");

        } else {
            Clients.evalJavaScript("alert(\"the report '" + reportName + "` is no longer available\")");
        }
    }

    private SName getNamedRegionForRowAndColumnSelectedSheet(int row, int col) {
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = myzss.getBook();
        for (SName name : book.getInternalBook().getNames()) { // seems best to loop through names checking which matches I think
            if (name.getRefersToSheetName().equals(myzss.getSelectedSheet().getSheetName())
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                return name;
            }
        }
        return null;
    }


    private List<SName> getNamedDataRegionForRowAndColumnSelectedSheet(int row, int col) {
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = myzss.getBook();
        List<SName> found = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) { // seems best to loop through names checking which matches I think
            if (name.getRefersToSheetName().equals(myzss.getSelectedSheet().getSheetName())
                    && name.getName().toLowerCase().startsWith("az_dataregion")
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                //check that there are some row headings
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

    public static void resolveTreeNode(int tab, StringBuilder stringBuilder, com.azquo.memorydb.TreeNode treeNode) {
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
            ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO);
            if (zkAzquoBookUtils.populateBook(newBook)) { // check if formulae made saveable data
                Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
            }
            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

