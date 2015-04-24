package com.azquo.spreadsheet.view;

import com.azquo.admin.AdminService;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.*;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.CellMouseEvent;
import org.zkoss.zss.ui.event.SheetSelectEvent;
import org.zkoss.zss.ui.event.StopEditingEvent;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Menupopup;

import java.io.File;

/**
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;

    Menupopup editPopup = new Menupopup();
    SpreadsheetService spreadsheetService;
    NameService nameService;
    ValueService valueService;
    UserChoiceDAO userChoiceDAO;
    AdminService adminService;

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        // perhaps a bit long winded but it gets us the spreadsheet
        Session session = Sessions.getCurrent();
        ApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(session.getWebApp().getServletContext());
        spreadsheetService = (SpreadsheetService) applicationContext.getBean("onlineService");
        nameService = (NameService) applicationContext.getBean("nameService");
        valueService = (ValueService) applicationContext.getBean("valueService");
        userChoiceDAO = (UserChoiceDAO) applicationContext.getBean("userChoiceDao");
        adminService = (AdminService) applicationContext.getBean("adminService");
        editPopup.setId("editPopup");
        Menuitem item1 = new Menuitem("Provenance");
        item1.setId("viewInfo1");
        // this seems unreliable. Rather annoying

        editPopup.appendChild(item1);
        // by trial and error this gave us an element we could show. Not sure baout best practice etc. here
        // can't seem to find the context menu to edit. No matter, have one or the other.
        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
        }

/*        final Button button = new Button("XLS");
        button.addEventListener("onClick",
                new EventListener() {
                    public void onEvent(Event event) throws Exception {
                        System.out.println("thing");
                    }
                });

        myzss.getFirstChild().appendChild(button);*/

        System.out.println("init myzss : " + myzss);

    }

    @Listen("onStopEditing = #myzss")
    public void onStopEditing(StopEditingEvent event) {
        Clients.evalJavaScript("document.getElementById(\"saveData\").style.display=\"block\";");
        //Clients.evalJavaScript("alert('hello');");
        boolean reload = false;
        String chosen = (String) event.getEditingValue();
        int row = event.getRow();
        int col = event.getColumn();
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        LoggedInConnection loggedInConnection = (LoggedInConnection) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_CONNECTION);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getRefersToSheetName().equals(event.getSheet().getSheetName())
                    && name.getRefersToCellRegion() != null
                    && name.getRefersToCellRegion().getRow() == row
                    && name.getRefersToCellRegion().getColumn() == col) {
                // ok it matches a name
                if (name.getName().endsWith("Chosen")) {
                    spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), reportId, name.getName().substring(0, name.getName().length() - "Chosen".length()), chosen);
                    reload = true;
                }
            }
        }
        if (reload) {
            try {
                // new book from same source
                final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                }
                ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(valueService, spreadsheetService, nameService, userChoiceDAO);
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
        ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(valueService, spreadsheetService, nameService, userChoiceDAO);
        Book book = sheetSelectEvent.getSheet().getBook();
        zkAzquoBookUtils.addValidation(zkAzquoBookUtils.getNamesForSheet(sheetSelectEvent.getSheet()), sheetSelectEvent.getSheet(),
                (LoggedInConnection) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_CONNECTION));
    }

    // to deal with provenance
    @Listen("onCellRightClick = #myzss")
    public void onCellRightClick(CellMouseEvent cellMouseEvent) {
        if (!myzss.isShowContextMenu()) { // then show ours :)
            SCell cell = cellMouseEvent.getSheet().getInternalSheet().getCell(cellMouseEvent.getRow(), cellMouseEvent.getColumn());
            // now we need to check if it's in a data region
            editPopup.open(cellMouseEvent.getClientx(), cellMouseEvent.getClienty());
        }
    }

    // to deal with provenance
    public void doSaveBook$myzss(Event event) {
        System.out.println("any luck??");
    }

}
