package com.azquo.spreadsheet.view;

import com.azquo.admin.AdminService;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.impl.PageImpl;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.CellMouseEvent;
import org.zkoss.zss.ui.event.SheetSelectEvent;
import org.zkoss.zss.ui.event.StopEditingEvent;
import org.zkoss.zssex.ui.widget.Ghost;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Menupopup;

import java.io.File;
import java.util.List;

/**
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;

    Menupopup editPopup = new Menupopup();
    SpreadsheetService spreadsheetService;
    UserChoiceDAO userChoiceDAO;
    AdminService adminService;

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        // perhaps a bit long winded but it gets us the spreadsheet
        Session session = Sessions.getCurrent();
        ApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(session.getWebApp().getServletContext());
        spreadsheetService = (SpreadsheetService) applicationContext.getBean("spreadsheetService");
        userChoiceDAO = (UserChoiceDAO) applicationContext.getBean("userChoiceDao");
        adminService = (AdminService) applicationContext.getBean("adminService");
        editPopup.setId("editPopup");
        Menuitem item1 = new Menuitem("Provenance");
        item1.setId("provenance");
        Menuitem item2 = new Menuitem("Region Spec");
        item2.setId("regionSpec");
//        Menuitem item3 = new Menuitem("Region Options");
//        item3.setId("regionOptions");
        // this seems unreliable. Rather annoying

//        editPopup.appendChild(item1);
//        editPopup.appendChild(item2);
//        editPopup.appendChild(item3);
//        System.out.println(myzss.getPopup());
//        myzss.setPopup(editPopup);
//        System.out.println(myzss.getPopup());
//        editPopup.setPage(myzss.getPage());
//        editPopup.setParent(myzss);
        // by trial and error this gave us an element we could show. Not sure baout best practice etc. here
        // can't seem to find the context menu to edit. No matter, have one or the other.
        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
        } else {
            Ghost g = new Ghost();
            g.setAttribute("zsschildren", "");
            myzss.appendChild(g);
            g.appendChild(editPopup);
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
        for (SName name : book.getInternalBook().getNames()) { // seems best to loop through names checking which matches I think
            if (name.getRefersToSheetName().equals(event.getSheet().getSheetName())
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                // ok it matches a name
                if (name.getName().endsWith("Chosen")) {// would have been a one cell name
                    spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), reportId, name.getName().substring(0, name.getName().length() - "Chosen".length()), chosen);
                    reload = true;
                }
                if (name.getName().startsWith("az_DataRegion")) { // then I assume they're editing data
                    String region = name.getName().substring("az_DataRegion".length());
                    if (loggedInUser.getSentCells(region) != null) {
                        CellForDisplay cellForDisplay = loggedInUser.getSentCells(region).getData().get(row - name.getRefersToCellRegion().getRow()).get(col - name.getRefersToCellRegion().getColumn());
                        Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
                        if (NumberUtils.isNumber(chosen)) {
                            cellForDisplay.setDoubleValue(Double.parseDouble(chosen));
                        }
                        cellForDisplay.setStringValue(chosen);
                        int highlightDays = 0;
                        if (book.getInternalBook().getAttribute("highlightDays") != null){ // maybe factor the string literals??
                            highlightDays = (Integer)book.getInternalBook().getAttribute("highlightDays");
                        }
                        if (highlightDays > 0) {
                            cellForDisplay.setHighlighted(true);
                            CellOperationUtil.applyFontColor(Ranges.range(event.getSheet(), row, col), "#FF0000");
                        }
                    }
                }
                // todo, add row heading later if required
                if (name.getName().startsWith("az_DisplayColumnHeadings")) { // ok going to try for a sorting detect
                    String region = name.getName().substring("az_DisplayColumnHeadings".length());
                    // ok here's the thing, the value on the spreadsheet (heading) is no good, it could be just for display, I want what the database would call the heading
                    // so I'd better get the headings.
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(region); // maybe jam this object agains the book? Otherwise multiple books could cause problems
                    if (cellsAndHeadingsForDisplay != null){
                        int localRow = event.getRow() - name.getRefersToCellRegion().getRow();
                        int localCol = event.getColumn() - name.getRefersToCellRegion().getColumn();
                        if (cellsAndHeadingsForDisplay.getColumnHeadings().get(localRow) != null){
                            String originalHeading = cellsAndHeadingsForDisplay.getColumnHeadings().get(localRow).get(localCol);
                            if (originalHeading != null) {
                                //← → ↑ ↓ ↔ ↕
                                if (chosen.endsWith("↑")){
                                    spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), reportId, "sort " + region + " by column", originalHeading);
                                    reload = true;
                                    break;
                                }
                                if (chosen.endsWith("↓")){
                                    spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), reportId, "sort " + region + " by column", originalHeading + "-desc");
                                    reload = true;
                                }
                            }
                        }
                    }
                }
                break; // break teh loop as soon as we hit the relevant name
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
                ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService,userChoiceDAO);
                zkAzquoBookUtils.populateBook(newBook); // reload the data
                myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Listen("onSheetSelect = #myzss")
    public void onSheetSelect(SheetSelectEvent sheetSelectEvent) {
        // now here's the thing, I need to re add the validation as it gets zapped for some reason
        ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO);
        Book book = sheetSelectEvent.getSheet().getBook();
        zkAzquoBookUtils.addValidation(zkAzquoBookUtils.getNamesForSheet(sheetSelectEvent.getSheet()), sheetSelectEvent.getSheet(),
                (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER));
    }

    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) {
//        if (!myzss.isShowContextMenu()) { // then show ours :)
            SCell cell = cellMouseEvent.getSheet().getInternalSheet().getCell(cellMouseEvent.getRow(), cellMouseEvent.getColumn());
            // now we need to check if it's in a data region
            editPopup.open(cellMouseEvent.getClientx() - 150, cellMouseEvent.getClienty());
//        }
    }

}
