package com.azquo.spreadsheet.view;

import com.azquo.admin.AdminService;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.jsonentities.DisplayValuesForProvenance;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
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
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.CellMouseEvent;
import org.zkoss.zss.ui.event.SheetSelectEvent;
import org.zkoss.zss.ui.event.StopEditingEvent;
import org.zkoss.zssex.ui.widget.Ghost;
import org.zkoss.zul.*;

import java.io.File;
import java.util.List;

/**
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;

    Menupopup editPopup = new Menupopup();
    Label provenanceLabel = new Label();
    Label instructionsLabel = new Label();
    SpreadsheetService spreadsheetService;
    UserChoiceDAO userChoiceDAO;
    UserRegionOptionsDAO userRegionOptionsDAO;
    AdminService adminService;

    String fullProvenance = "";


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
        Menuitem item1 = new Menuitem("Audit");
        item1.setId("provenance");
        Menuitem item2 = new Menuitem("Region Spec");
        item2.setId("regionSpec");
//        Menuitem item3 = new Menuitem("Region Options");
//        item3.setId("regionOptions");
        editPopup.appendChild(item1);
        editPopup.appendChild(item2);

        Popup provenancePopup = new Popup();
        provenancePopup.setId("provenancePopup");
        provenanceLabel.setMultiline(true);
        provenancePopup.appendChild(provenanceLabel);

        final Button button = new Button("Download Full Audit");
        button.addEventListener("onClick",
                new EventListener<Event>() {
                    public void onEvent(Event event) throws Exception {
                        Filedownload.save(fullProvenance, "text/csv", "provenance.csv");
                    }
                });

        provenancePopup.appendChild(button);
        item1.setPopup(provenancePopup); // I think that will automatically work??

        Popup instructionsPopup = new Popup();
        instructionsPopup.setId("instructionsPopup");
        instructionsLabel.setMultiline(true);
        instructionsPopup.appendChild(instructionsLabel);
        item2.setPopup(instructionsPopup);

        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
            myzss.getFirstChild().appendChild(provenancePopup);
            myzss.getFirstChild().appendChild(instructionsPopup);
        } else { // it took some considerable time to work out this hack
            Ghost g = new Ghost();
            g.setAttribute("zsschildren", "");
            myzss.appendChild(g);
            g.appendChild(editPopup);
            g.appendChild(provenancePopup);
            g.appendChild(instructionsPopup);
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
        System.out.println("init myzss : " + myzss);

    }

    @Listen("onStopEditing = #myzss")
    public void onStopEditing(StopEditingEvent event) {
        boolean reload = false;
        String chosen = (String) event.getEditingValue();
        int row = event.getRow();
        int col = event.getColumn();
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        SName name = getNamedRegionForRowAndColumnSelectedSheet(event.getRow(), event.getColumn());
        if (name != null) { // as it stands regions should not overlap, we find a name that means we know what to (try) to do
            // ok it matches a name
            if (name.getName().endsWith("Chosen")) {// would have been a one cell name
                spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), name.getName().substring(0, name.getName().length() - "Chosen".length()), chosen);
                reload = true;
            }
            if (name.getName().startsWith("az_DataRegion")) { // then I assume they're editing data
                String region = name.getName().substring("az_DataRegion".length());
                final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(region);
                if (sentCells != null) {
                    // the data region as defined on the cheet may be larger than the sent cells
                    if (sentCells.getData().size() > row - name.getRefersToCellRegion().getRow()
                            && sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).size() > col - name.getRefersToCellRegion().getColumn()){
                        CellForDisplay cellForDisplay = sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).get(col - name.getRefersToCellRegion().getColumn());
                        Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
                        if (NumberUtils.isNumber(chosen)) {
                            cellForDisplay.setDoubleValue(Double.parseDouble(chosen));
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
            // todo, add row heading later if required
            if (name.getName().startsWith("az_DisplayColumnHeadings")) { // ok going to try for a sorting detect
                String region = name.getName().substring("az_DisplayColumnHeadings".length());
                UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null){
                    CellRegion optionsRegion = ZKAzquoBookUtils.getCellRegionForSheetAndName(event.getSheet(), ZKAzquoBookUtils.azOptions + region);
                    String source = null;
                    if (optionsRegion != null) {
                        source = event.getSheet().getInternalSheet().getCell(optionsRegion.getRow(), optionsRegion.getColumn()).getStringValue();
                    }
                    userRegionOptions = new UserRegionOptions(0,loggedInUser.getUser().getId(), reportId, region, source);
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
                final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                }
                ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO);
                if (zkAzquoBookUtils.populateBook(newBook)){ // check if formulae made saveable data
                    Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
                }
                myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Listen("onSheetSelect = #myzss")
    public void onSheetSelect(SheetSelectEvent sheetSelectEvent) {
        // now here's the thing, I need to re add the validation as it gets zapped for some reason
        ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO);
        Book book = sheetSelectEvent.getSheet().getBook();
        zkAzquoBookUtils.addValidation(zkAzquoBookUtils.getNamesForSheet(sheetSelectEvent.getSheet()), sheetSelectEvent.getSheet(),
                (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER));
    }

    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) {
        // roght now a right click gets provenance ready, dunno if I need to do this
        SName name = getNamedRegionForRowAndColumnSelectedSheet(cellMouseEvent.getRow(), cellMouseEvent.getColumn());
        if (name != null && name.getName().startsWith("az_DataRegion")) { // then I assume they're editing data
            provenanceLabel.setValue("");
            String region = name.getName().substring("az_DataRegion".length());
            Book book = myzss.getBook();
            LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
            try {
                // ok this is a bit nasty, after Azquobook is zapped we could try something different
                // todo - sort after zapping azquobook! Maybe clickable again?
                List<DisplayValuesForProvenance> displayValuesForProvenances = spreadsheetService.getDisplayValuesForProvenance(loggedInUser, region
                        , cellMouseEvent.getRow() - name.getRefersToCellRegion().getRow(), cellMouseEvent.getColumn() - name.getRefersToCellRegion().getColumn());
                if (!displayValuesForProvenances.isEmpty()){
                    StringBuilder toShow = new StringBuilder();
                    for (DisplayValuesForProvenance displayValuesForProvenance : displayValuesForProvenances){
                        resolveDisplayValuesForProvenance(0,toShow, displayValuesForProvenance);
                    }
                    fullProvenance = toShow.toString();
                    fullProvenance = fullProvenance.replace("<br/>", " ");
                    fullProvenance = fullProvenance.replace("<b>", "");
                    fullProvenance = fullProvenance.replace("</b>", "");
                    String stringToShow = fullProvenance;
                    if (stringToShow.length() > 200){
                        stringToShow = stringToShow.substring(0,200) + " . . .";
                    }
                    stringToShow = stringToShow.replace("\t", "    ");
                    provenanceLabel.setValue(stringToShow);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            instructionsLabel.setValue("");
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(region);
            if (sentCells != null){
                StringBuilder instructionsText = new StringBuilder();
                instructionsText.append("Column headings\n\n");
                for (List<String> row : sentCells.getColHeadingsSource()){ // flatten for the mo
                    for (String cell : row){
                        instructionsText.append(cell + "\n");
                    }
                }
                instructionsText.append("\nRow headings\n\n");
                for (List<String> row : sentCells.getRowHeadingsSource()){ // flatten for the mo
                    for (String cell : row){
                        instructionsText.append(cell + "\n");
                    }
                }
                instructionsText.append("\nContext\n\n");
                for (List<String> row : sentCells.getContextSource()){ // flatten for the mo
                    for (String cell : row){
                        instructionsText.append(cell + "\n");
                    }
                }
                instructionsLabel.setValue(instructionsText.toString());
            }
        }
        editPopup.open(cellMouseEvent.getClientx() - 130, cellMouseEvent.getClienty());
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

    public static void resolveDisplayValuesForProvenance(int tab, StringBuilder stringBuilder, DisplayValuesForProvenance displayValuesForProvenance){
        for (int i = 0; i < tab; i++){
            stringBuilder.append("\t");
        }
        if (displayValuesForProvenance.name != null){
            stringBuilder.append(displayValuesForProvenance.name);
            stringBuilder.append("\t");
            stringBuilder.append(displayValuesForProvenance.value);
            stringBuilder.append("\n");
        }
        if (displayValuesForProvenance.heading != null){ // then assume we have items too!
            stringBuilder.append(displayValuesForProvenance.heading);
            stringBuilder.append("\n");
            tab++;
            for (DisplayValuesForProvenance displayValuesForProvenance1 : displayValuesForProvenance.items){
                resolveDisplayValuesForProvenance(tab, stringBuilder, displayValuesForProvenance1);
            }
        }
    }
}