package com.azquo.spreadsheet.zk;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.OnlineController;
import org.apache.commons.lang.StringEscapeUtils;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.SSheet;
import org.zkoss.zss.ui.Spreadsheet;

import java.io.File;

/**
 * Created by edward on 26/01/17.
 * <p>
 * Just some small static functions factored off the big old ZKcomposer
 */
class ZKComposerUtils {
    static void reloadBook(Spreadsheet myzss, Book book) {
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
            Ranges.range(myzss.getSelectedSheet()).notifyChange(); // try to update the lot - sometimes it seems it does not!
            // ok there is a danger right here : on some sheets the spreadsheet gets kind of frozen or rather the cells don't calculate until something like a scroll happens. Not a problem when not full screen either
            // really something to send to ZK? Could be a a pain to prepare. TODO.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void openDrillDown(LoggedInUser loggedInUser, String reportName, String context, int valueId) {
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
            } else if (loggedInUser.getPermission(reportName) != null) {
                permissionId = reportName;
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
}