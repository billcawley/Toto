package com.azquo.spreadsheet.controller;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zkoss.json.JSONObject;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.jsp.JsonUpdateBridge;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zul.Filedownload;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 05/03/15
 * .
 * Adapted from a ZK example - so buttons in the jsp can interact with the ZK sheet object
 */
@Controller
@RequestMapping("/ZKSpreadsheetCommand")
public class ZKSpreadsheetCommandController {

    @RequestMapping
    public void handleRequest(final HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // really necessary? Maybe check
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        //parameter from ajax request, you have to pass it in AJAX request
        //necessary parameter to get ZK server side desktop
        final String desktopId = req.getParameter("desktopId");
        //necessary parameter to get ZK server side spreadsheet
        final String zssUuid = req.getParameter("zssUuid");
        final String action = req.getParameter("action");
        // use utility class to wrap zk in servlet request and
        // get access and response result
        // prepare a json result object, it can contain your ajax result and
        // also the necessary zk component update result
        final JSONObject result = new JSONObject();
        JsonUpdateBridge bridge = new JsonUpdateBridge(req.getServletContext(), req, resp,
                desktopId) {
            @Override
            protected void process(Desktop desktop) {
                Spreadsheet ss = (Spreadsheet) desktop.getComponentByUuidIfAny(zssUuid);
                try {
                    if ("FREEZE".equals(action)) {
                        Ranges.range(ss.getSelectedSheet()).setFreezePanel(ss.getSelection().getRow(), ss.getSelection().getColumn());
                    }
                    if ("UNFREEZE".equals(action)) {
                        Ranges.range(ss.getSelectedSheet()).setFreezePanel(0,0);
                    }
                    if ("XLS".equals(action)) {
                        Exporter exporter = Exporters.getExporter();
                        Book book = ss.getBook();
                        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(file);
                            exporter.export(book, fos);
                        } finally {
                            if (fos != null) {
                                fos.close();
                            }
                        }
                        Filedownload.save(new AMedia(ss.getSelectedSheetName() + ".xlsx", null, null, file, true));
                    }

                    if ("SaveTemplate".equals(action)) { // similar to above but we're overwriting the report
                        LoggedInUser loggedInUser = (LoggedInUser) req.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper() || loggedInUser.getUser().isMaster()){
                            Exporter exporter = Exporters.getExporter();
                            Book book = ss.getBook();
                            int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                            OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
                            String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + onlineReport.getFilenameForDisk(); // as in the online controller
                            FileOutputStream fos = null;
                            try {
                                fos = new FileOutputStream(bookPath); // overwrite the report, should work
                                exporter.export(book, fos);
                            } finally {
                                if (fos != null) {
                                    fos.close();
                                }
                            }
                            Clients.evalJavaScript("window.location.assign(\"/api/Online?reportid=" + reportId + "&database=" + onlineReport.getDatabase() + "\")");
                        }
                    }
                    boolean pdfDefault = false;
                    if (action != null && action.startsWith("PDFMerge")) {
                        Book book = ss.getBook();
                        // Look for the relevant name in the sheet
                        CellRegion pdfRules = ZKAzquoBookUtils.getCellRegionForSheetAndName(ss.getSelectedSheet(), "az_PDF" + action.substring("PDFMerge".length()).replace(" ", "_")); // just reverse what I did for the UI
                        List<String> choices = new ArrayList<>();
                        if (pdfRules != null) {
                            final String stringValue = ss.getSelectedSheet().getInternalSheet().getCell(pdfRules.getRow(), pdfRules.getColumn()).getStringValue();
                            StringTokenizer st = new StringTokenizer(stringValue, ",");
                            while (st.hasMoreTokens()) {
                                choices.add(st.nextToken().trim());
                            }
                        }
                        if (!choices.isEmpty()) {
                            PDFMergerUtility merger = new PDFMergerUtility();
                            // ok this is where things get interesting, need to work out how to express the logic.
                            List<String> filesCreated = new ArrayList<>();
                            // the filesCreated is added to internally and the other arraylist is just to track choices
                            resolveAndRenderChoices(filesCreated, book, choices, new ArrayList<>());
                            filesCreated.forEach(merger::addSource);
                            File merged = File.createTempFile(Long.toString(System.currentTimeMillis()), "merged");
                            merger.setDestinationFileName(merged.getAbsolutePath());
                            merger.mergeDocuments();
                            Filedownload.save(new AMedia(ss.getSelectedSheetName() + "merged.pdf", "pdf", "application/pdf", merged, true));
                        } else {
                            pdfDefault = true;
                        }
                    }

                    if ("PDF".equals(action) || pdfDefault) {
                        Exporter exporter = Exporters.getExporter("pdf");
                        // I think these bits are commented as we're exporting the sheet not the book
                        //Book book = ss.getBook();
                        // zapping validation in this way throws an arror, it is annoying
/*                        Sheet validationSheet = book.getSheet(ZKAzquoBookUtils.VALIDATION_SHEET);
                        if (validationSheet != null) {
                            try{
                                book.getInternalBook().deleteSheet(validationSheet.getInternalSheet());
                            } catch (Exception ignored){
                                // todo - bring this up with ZK?
                            }
                        }*/
                        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(file);
//                            exporter.export(book, file);
                            // depreciated, why?
                            exporter.export(ss.getSelectedSheet(), fos);
                        } finally {
                            if (fos != null) {
                                fos.close();
                            }
                        }
                        Filedownload.save(new AMedia(ss.getSelectedSheetName() + ".pdf", "pdf", "application/pdf", file, true));
                    }

                    boolean reloadAfterSave = false;

                    if ("Save".equals(action)) {
                        LoggedInUser loggedInUser = (LoggedInUser) req.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                        // todo - provenance?
                        final Book book = ss.getBook();
                        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
                        boolean saveOk = true;
                        for (SName name : book.getInternalBook().getNames()) {
                            if (name.getName().toLowerCase().startsWith(ZKAzquoBookUtils.AZDATAREGION.toLowerCase())) { // I'm saving on all sheets, this should be fine with zk
                                String region = name.getName().substring(ZKAzquoBookUtils.AZDATAREGION.length());
                                // todo - factor this chunk?
                                // this is a bit annoying given that I should be able to get the options from the sent cells but there may be no sent cells. Need to investigate this - nosave is currently being used for different databases, that's the problem
                                SName optionsRegion = book.getInternalBook().getNameByName(ZKAzquoBookUtils.AZOPTIONS + region);
                                String optionsSource = "";
                                boolean noSave = false;
                                if (optionsRegion != null) {
                                    optionsSource = ZKAzquoBookUtils.getSnameCell(optionsRegion).getStringValue();
                                    UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, optionsSource);
                                    noSave = userRegionOptions.getNoSave();
                                }
                                if (!noSave){
                                    final String result = SpreadsheetService.saveData(loggedInUser, region.toLowerCase(), reportId, onlineReport != null ? onlineReport.getReportName() : "");
                                    if (!result.equals("true")){
                                        Clients.evalJavaScript("alert(\"Save error : " + result + "\")");
                                        saveOk = false;
                                    }
                                }
                            }
                            // deal with repeats, annoying!
                            if (name.getName().toLowerCase().startsWith(ZKAzquoBookUtils.AZREPEATSCOPE.toLowerCase())) { // then try to find the "sub" regions. todo, lower/upper case? Consistency . . .
                                String region = name.getName().substring(ZKAzquoBookUtils.AZREPEATSCOPE.length());
                                final SName repeatRegion = book.getInternalBook().getNameByName(ZKAzquoBookUtils.AZREPEATREGION + region);
                                if (repeatRegion != null){
                                    int regionRows = repeatRegion.getRefersToCellRegion().getRowCount();
                                    int regionCols = repeatRegion.getRefersToCellRegion().getColumnCount();
                                    // integer division is fine will give the number of complete region rows and cols ( rounds down)
                                    int repeatRows = name.getRefersToCellRegion().getRowCount() / regionRows;
                                    int repeatCols = name.getRefersToCellRegion().getColumnCount() / regionCols;
                                    for (int row = 0; row < repeatRows; row++){
                                        for (int col = 0; col < repeatCols; col++){
                                            //region + "-" + repeatRow + "-" + repeatColumn
                                            if (loggedInUser.getSentCells(reportId, region.toLowerCase() + "-" + row + "-" + col) != null){ // the last ones on the repeat scope might be blank
                                                final String result = SpreadsheetService.saveData(loggedInUser, region.toLowerCase() + "-" + row + "-" + col, reportId, onlineReport != null ? onlineReport.getReportName() : "");
                                                if (!result.equals("true")){
                                                    Clients.evalJavaScript("alert(\"Save error : " + result + "\")");
                                                    saveOk = false;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (!saveOk){
                                break; // stop looping through the names if a save failed
                            }
                        }
                        // new thing, look for followon, guess we need an instance of ZK azquobook utils
                        // need to show readout like executing todo. On that topic could the executing loading screen say "running command?" or something similar?
                        if (saveOk){
                            ZKAzquoBookUtils.runExecuteCommandForBook(book, ZKAzquoBookUtils.FOLLOWON); // that SHOULD do it. It will fail gracefully in the vast majority of times there is no followon
                            // unlock here makes sense think, if duff save probably leave locked
                            SpreadsheetService.unlockData(loggedInUser);
                            reloadAfterSave = true;
                        }
                    }

                    if ("RestoreSavedValues".equals(action) || reloadAfterSave) {
                        final Book book = ss.getBook();
                        final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                        for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                            newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                        }
                        ZKAzquoBookUtils.populateBook(newBook, 0, true, null, false);
                        ss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
                        Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"none\";document.getElementById(\"restoreDataButton\").style.display=\"none\";");
                        if (reloadAfterSave){
                            Clients.evalJavaScript("alert(\"Save successful\")");
                        }
                    }

                    if ("Unlock".equals(action)) {
                        LoggedInUser loggedInUser = (LoggedInUser) req.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                        // should be all that's required
                        SpreadsheetService.unlockData(loggedInUser);
                        Clients.evalJavaScript("document.getElementById(\"unlockButton\").style.display=\"none\";");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        /*
        * Generate ZK update result in given JSON object. An AJAX response
        * handler at client side, zssjsp, will 'eval' this result to update ZK
        * components.
        */
        bridge.process(result);
        Writer w = resp.getWriter();
        w.append(result.toJSONString());
    }

    /*
    Note : a method I didn't follow for this involved making the extra sheets and rendering in one go
            SSheet newSheet = book.getInternalBook().createSheet("edd_test", book.getSheetAt(0).getInternalSheet());
    and then copy the names (which are not copied by doing this). It wasn't practical (can't really remember why!) but worth remembering the ability to copy sheets.
     */

    private void resolveAndRenderChoices(List<String> toReturn, Book book, List<String> choices, List<String> selectedChoices) throws Exception {
        // will be nothing to set first time round
        int index = 0;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        for (String selectedChoice : selectedChoices) {
            SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choices.get(index), selectedChoice);
            index++;
        }
        // ok the options are set, run the book to find our choices
        Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
        for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
            newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
        }
        ZKAzquoBookUtils.populateBook(newBook, 0);
        // here should be the list we're after
        final List<String> choiceList = getChoiceList(newBook, choices.get(selectedChoices.size()));
        if (choiceList.isEmpty()) { // ok if no options on the choice list we want then I guess render this one and return
            toReturn.add(renderBook(newBook));
        } else { // ok there's a list
            if (selectedChoices.size() == choices.size() - 1) { // that means that with this new list we're at the last level, create pdfs for each option
                for (String selectedChoice : choiceList) {
                    // previous choices will have been set, just do this last one
                    SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choices.get(choices.size() - 1), selectedChoice);
                    // ok ALL the choices are set, run the book
                    newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                    for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                        newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                    }
                    ZKAzquoBookUtils.populateBook(newBook, 0);
                    // and render to PDF
                    toReturn.add(renderBook(newBook));
                }
            } else { // not the last level, add this choice and recurse, take it off after, think this workd
                for (String selectedChoice : choiceList) {
                    selectedChoices.add(selectedChoice);
                    resolveAndRenderChoices(toReturn, book, choices, selectedChoices);
                    selectedChoices.remove(selectedChoices.size() - 1);
                }
            }
        }
    }

    private List<String> getChoiceList(Book book, String choice) {
        List<String> toReturn = new ArrayList<>();
        Sheet validationSheet = book.getSheet(ZKAzquoBookUtils.VALIDATION_SHEET);
        if (validationSheet != null) {
            int col = 0;
            SCell cell = validationSheet.getInternalSheet().getCell(0, col);
            if (cell.isNull()) { // first was null don't bother searching
                return toReturn;
            }
            while (!cell.isNull()) { // go along the top columns looking for the choice we're interested in
                if (cell.getStringValue().equals(choice + "Choice")) {
                    break;
                }
                col++;
                cell = validationSheet.getInternalSheet().getCell(0, col);
            }
            if (!cell.isNull()) { // then we found it
                int row = 1;
                cell = validationSheet.getInternalSheet().getCell(row, col);
                while (!cell.isNull()) {
                    toReturn.add(cell.getStringValue());
                    row++;
                    cell = validationSheet.getInternalSheet().getCell(row, col);
                }
            }
        }
        return toReturn;
    }

    private String renderBook(Book book) throws IOException {
        Sheet validationSheet = book.getSheet(ZKAzquoBookUtils.VALIDATION_SHEET);
        if (validationSheet != null) {
            try{
                book.getInternalBook().deleteSheet(validationSheet.getInternalSheet());
            } catch (Exception ignored){
                // todo - bring this up with ZK?
            }
        }
        Exporter exporter = Exporters.getExporter("pdf");
        //((PdfExporter) exporter).getPrintSetup().setLandscape(false);
        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            exporter.export(book, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        return file.getAbsolutePath();
    }
}
