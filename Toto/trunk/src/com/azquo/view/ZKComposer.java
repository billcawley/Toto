package com.azquo.view;

import com.azquo.controller.OnlineController;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.OnlineService;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.StopEditingEvent;

import java.io.File;


/**
 * Created by cawley on 02/03/15
 */
public class ZKComposer extends SelectorComposer<Component> {
    @Wire
    Spreadsheet myzss;

    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
    }

    @Listen("onStopEditing = #myzss")
    public void onStopEditing(StopEditingEvent event) {
        System.out.println("thing?" + event);
        // save the choice.
        String chosen = (String) event.getEditingValue();
        int row = event.getRow();
        int col = event.getColumn();
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = event.getSheet().getBook();
        LoggedInConnection loggedInConnection = (LoggedInConnection) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_CONNECTION);
        OnlineService onlineService = (OnlineService) book.getInternalBook().getAttribute(OnlineController.ONLINE_SERVICE);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getRefersToSheetName().equals(event.getSheet().getSheetName())
                    && name.getRefersToCellRegion() != null
                    && name.getRefersToCellRegion().getRow() == row
                    && name.getRefersToCellRegion().getColumn() == col) {
                onlineService.setUserChoice(loggedInConnection.getUser().getId(), reportId, name.getName().substring(0, name.getName().length() - "Chosen".length()), chosen);
            }
        }
        try {
            // new book from same source
            final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
            for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
            }
            ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils();
            zkAzquoBookUtils.populateBook(newBook); // reload the data
            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
