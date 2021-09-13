package com.azquo.spreadsheet.controller;

import com.azquo.StringLiterals;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.zk.*;
import com.csvreader.CsvWriter;
import io.keikai.api.*;
import io.keikai.api.model.CellData;
import io.keikai.json.JSONValue;
import io.keikai.jsp.SmartUpdateBridge;
import io.keikai.model.SName;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.apache.poi.ss.util.AreaReference;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.json.JSONObject;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.util.Clients;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.jsp.JsonUpdateBridge;
import io.keikai.model.CellRegion;
import io.keikai.model.SCell;
import io.keikai.model.SSheet;
import io.keikai.ui.Spreadsheet;
import org.zkoss.zul.Filedownload;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 05/03/15
 *
 * Adapted from a ZK example - so buttons in the jsp can interact with the ZK sheet object
 */
@Controller
@RequestMapping("/ZKSpreadsheetCommand")
public class ZKSpreadsheetCommandController {

/*    static String base64int(int input){
        // I'm using tilda on the end
        String base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+~";
        // so I have my int - 4 bytes, should be able to represent that as 6 chars max. Normal base 64 encoding won't do it this way hence the manual fix.
        char[] result = new char[6];
        for (int i = 5; i >= 0; i--){
            int charLookup = input%64;
            result[i] = base64.charAt(charLookup);
            input = input - (input%64);
            input /= 64;
        }
        // equivalent or removing 0s at the beginning
        String s = new String(result);
        while (s.startsWith("A")){
            s = s.substring(1);
        }
        return s;
    }*/


    @RequestMapping
    public void handleRequest(final HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // really necessary? Maybe check
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        // new keikai style
        Map<String, String> bodyData = (Map) JSONValue.parse(req.getReader().lines().collect(Collectors.joining()));

        final String action = bodyData.get("action");
        final String nameIdForChosenTree = bodyData.get("nameIdForChosenTree");

//        System.out.println("name id in command controller " + nameIdForChosenTree);

        // add custom response message, it depends on your logic.
        final JSONObject appResponse = new JSONObject();
        appResponse.put("action", action);

        final Map keikaiResponse = SmartUpdateBridge.Builder.create(req.getServletContext(), req, resp, bodyData).withComponent(ss -> {
            LoggedInUser loggedInUser = (LoggedInUser) req.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            try {
                if ("FREEZE".equals(action)) {
                    Ranges.range(ss.getSelectedSheet()).setFreezePanel(ss.getSelection().getRow(), ss.getSelection().getColumn());
                }
                if ("UNFREEZE".equals(action)) {
                    Ranges.range(ss.getSelectedSheet()).setFreezePanel(0, 0);
                }
                // now just pops up the processing, bounces back to actually save below
                if ("XLS".equals(action)) {
                    Clients.showBusy(ss, "Processing ...");
                    Clients.evalJavaScript("postAjax('ActuallyXLS');");
                }
                if ("ActuallyXLS".equals(action)) {
                    Exporter exporter = Exporters.getExporter();
                    Book book = ss.getBook();
                    File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        exporter.export(book, fos);
                    }
                    if (book.getInternalBook().getAttribute(OnlineController.REPORT_ID) != null){
                        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
                        Map<String, String> params = new HashMap<>();
                        params.put("File", onlineReport.getReportName() + ".xlsx");
                        loggedInUser.userLog("Save", params);
                        Filedownload.save(new AMedia(onlineReport.getReportName().replace("/", "").replace("\\", "") + ".xlsx", null, null, file, true));
                    } else { // for test.jsp
                        Filedownload.save(new AMedia(book.getBookName(), null, null, file, true));
                    }
                    Clients.clearBusy(ss);
                }

                if ("SaveTemplate".equals(action)) { // similar to above but we're overwriting the report
                    if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper() || loggedInUser.getUser().isMaster()) {
                        Exporter exporter = Exporters.getExporter();
                        Book book = ss.getBook();
                        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
                        Map<String, String> params = new HashMap<>();
                        params.put("Report", onlineReport.getReportName() + ".xlsx");
                        loggedInUser.userLog("SaveTemplate", params);
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk(); // as in the online controller
                        try (FileOutputStream fos = new FileOutputStream(bookPath)) {
                            // overwrite the report, should work
                            exporter.export(book, fos);
                        }
                        Clients.evalJavaScript("window.location.assign(\"/api/Online?reportid=" + reportId + "&database=" + onlineReport.getDatabase() + "\")");
                    }
                }
                boolean pdfDefault = false;
                if (action != null && action.startsWith("PDFMerge")) {
                    Book book = ss.getBook();
                    // Look for the relevant name in the sheet
                    CellRegion pdfRules = BookUtils.getCellRegionForSheetAndName(ss.getSelectedSheet(), "az_PDF" + action.substring("PDFMerge".length()).replace(" ", "_")); // just reverse what I did for the UI
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
                    Book book = ss.getBook();
                    // zapping validation in this way throws an arror, it is annoying
                    Sheet validationSheet = book.getSheet(ChoicesService.VALIDATION_SHEET);
                    if (validationSheet != null) {
                        try{
                            book.getInternalBook().deleteSheet(validationSheet.getInternalSheet());
                        } catch (Exception ignored){
                            // todo - bring this up with ZK?
                        }
                    }
                    File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                    exporter.export(book, file);
                    Map<String, String> params = new HashMap<>();
                    params.put("PDF", ss.getSelectedSheetName() + ".pdf");
                    loggedInUser.userLog("Download", params);
                    Filedownload.save(new AMedia(ss.getSelectedSheetName() + ".pdf", "pdf", "application/pdf", file, true));
                }
                // actually pattern . . .
                if ("XML".equals(action)) {
                    Clients.showBusy(ss,"Processing ...");
                    Clients.evalJavaScript("postAjax('ActuallyXML');");
                }
                if ("XMLZIP".equals(action)) {
                    Clients.showBusy(ss,"Processing ...");
                    Clients.evalJavaScript("postAjax('ActuallyXMLZIP');");
                }
                if (action != null && action.startsWith("ActuallyXML")) {
                    Path destdir;
                    boolean zipMode = true;
                    if (SpreadsheetService.getXMLDestinationDir() != null && !SpreadsheetService.getXMLDestinationDir().isEmpty() && !action.contains("ZIP")){
                        destdir = Paths.get(SpreadsheetService.getXMLDestinationDir());
                        zipMode = false;
                    } else {
                        destdir = Files.createTempDirectory("zipforxmlfiles");
                    }
                    int fileCount = ReportExecutor.generateXMLFilesAndSupportingReports(loggedInUser,ss.getSelectedSheet(), destdir);
                    // it was building a zip, compress and set for download
                    if (fileCount> 0) {
                        if (zipMode) {
                            ZipUtil.unexplode(destdir.toFile());
                            Filedownload.save(new AMedia(ss.getSelectedSheetName() + "xml.zip", "zip", "application/zip", destdir.toFile(), true));
                            Clients.clearBusy(ss);
                        } else {
                            Clients.clearBusy(ss);
                            Clients.alert(fileCount + " files created in " + SpreadsheetService.getXMLDestinationDir());
                        }
                    } else {
                        Clients.clearBusy(ss);
                        Clients.alert("No files created. Either no lines were selected or lines with empty supporting reports were skipped.");
                    }
                }
                String saveMessage = "";

                // now just pops up the processing, bounces back to actually save below
                if ("Save".equals(action)) {
                    Clients.showBusy(ss,"Processing ...");
                    Clients.evalJavaScript("postAjax('ActuallySave');");
                }

                if ("ActuallySave".equals(action)) {
                    saveMessage = ReportService.save(ss,loggedInUser);
                    Clients.clearBusy(ss);
                }

                if ("ExecuteSave".equals(action)) {
                    ReportService.save(ss,loggedInUser);
                    Clients.clearBusy(ss);
                }

                if ("RestoreSavedValues".equals(action) || saveMessage.startsWith("Success")) {
                    final Book book = ss.getBook();
                    final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                    for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                        // don't move zss internal stuff, it might interfere
                        if (!key.toLowerCase().contains("zss")){
                            newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                        }
                    }
                    ReportRenderer.populateBook(newBook, 0, true, false, null);
                    ss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
                    if (ss.getSelectedSheet().isHidden()) {
                        for (SSheet s : ss.getSBook().getSheets()) {
                            if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                                ss.setSelectedSheet(s.getSheetName());
                                break;
                            }
                        }
                    }
                    Ranges.range(ss.getSelectedSheet()).notifyChange(); // try to update the lot - sometimes it seems it does not!
                    Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"none\";document.getElementById(\"restoreDataButton\").style.display=\"none\";");
                    if (saveMessage.length() > 0) {
                        Clients.evalJavaScript("alert(\"" + saveMessage + "\")");
                    }
                }

                if ("Unlock".equals(action)) {
                    // should be all that's required
                    SpreadsheetService.unlockData(loggedInUser);
                    Clients.evalJavaScript("document.getElementById(\"unlockButton\").style.display=\"none\";");
                }

                if ("nameIdForChosenTree".equals(action) && NumberUtils.isDigits(nameIdForChosenTree)){
                    String toAssign = SpreadsheetService.getUniqueNameFromId(loggedInUser, Integer.parseInt(nameIdForChosenTree));
                    CellRef pos = ss.getCellFocus();
                    List<SName> names = ReportUIUtils.getNamedRegionForRowAndColumnSelectedSheet(ss.getSelectedSheet(), pos.getRow(), pos.getColumn());
                    for (SName name : names) {
                        if (name.getName().toLowerCase().endsWith(ZKComposer.CHOSENTREE.toLowerCase())) { // match the composer code
                            String choiceName = name.getName().substring(0, name.getName().length() - ZKComposer.CHOSENTREE.length());
                            SpreadsheetService.setUserChoice(loggedInUser, choiceName, toAssign);
                            Clients.showBusy(ss, "Reloading . . .");
                            org.zkoss.zk.ui.event.Events.echoEvent("onReloadWhileClientProcessing", ss, null);
                            break;
                        }
                    }
//                    ss.getSelection().
                }

                // note, this is vulnerable to changing region sizes, duplicate names etc. Should be used sparingly and carefully
                if ("CHECKCDYNAMICUPDATE".equalsIgnoreCase(action)) { // then run through the regions seeing if any have the dynamic update flag and if they do check whether they need updating
                    int reportId = (Integer) ss.getBook().getInternalBook().getAttribute(OnlineController.REPORT_ID);
                    List<CellsAndHeadingsForDisplay> sentForReport = loggedInUser.getSentForReport(reportId);
                    for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : sentForReport){
                        if (cellsAndHeadingsForDisplay.getOptions().dynamicUpdate){ // ok we need to check
                            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplayLatest = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, cellsAndHeadingsForDisplay.getRegion()
                                    , cellsAndHeadingsForDisplay.getRowHeadingsSource(),
                                    cellsAndHeadingsForDisplay.getColHeadingsSource(),
                                    cellsAndHeadingsForDisplay.getContextSource(), cellsAndHeadingsForDisplay.getOptions());
                            CellRegion region = BookUtils.getNameByName(StringLiterals.AZDATAREGION + cellsAndHeadingsForDisplay.getRegion(), ss.getBook());
                            Sheet sheetFor = BookUtils.getSheetFor(StringLiterals.AZDATAREGION + cellsAndHeadingsForDisplay.getRegion(), ss.getBook());
                            if (region != null && sheetFor != null){
                                if (cellsAndHeadingsForDisplay.equals(cellsAndHeadingsForDisplayLatest)){
                                    System.out.println(cellsAndHeadingsForDisplay.getRegion() +  " dynamic update check the data was the same");
                                } else {
                                    RegionFillerService.fillData(sheetFor, cellsAndHeadingsForDisplayLatest, region, null);
                                    loggedInUser.setSentCells(reportId, sheetFor.getSheetName(), cellsAndHeadingsForDisplayLatest.getRegion(), cellsAndHeadingsForDisplayLatest);
                                    System.out.println(cellsAndHeadingsForDisplay.getRegion() +  " dynamic update check the data was NOT the same");
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        })
                // build a keikai response for Book change which will be processed by kkjsp
                .build(appResponse);

        resp.getWriter().append(JSONObject.toJSONString(keikaiResponse));
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
            SpreadsheetService.setUserChoice(loggedInUser, choices.get(index), selectedChoice);
            index++;
        }
        // ok the options are set, run the book to find our choices
        Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
        for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
            // don't move zss internal stuff, it might interfere
            if (!key.toLowerCase().contains("zss")){
                newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
            }
        }
        ReportRenderer.populateBook(newBook, 0);
        // here should be the list we're after
        final List<String> choiceList = getChoiceList(newBook, choices.get(selectedChoices.size()));
        if (choiceList.isEmpty()) { // ok if no options on the choice list we want then I guess render this one and return
            toReturn.add(renderBook(newBook));
        } else { // ok there's a list
            if (selectedChoices.size() == choices.size() - 1) { // that means that with this new list we're at the last level, create pdfs for each option
                for (String selectedChoice : choiceList) {
                    // previous choices will have been set, just do this last one
                    SpreadsheetService.setUserChoice(loggedInUser, choices.get(choices.size() - 1), selectedChoice);
                    // ok ALL the choices are set, run the book
                    newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                    for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                        // don't move zss internal stuff, it might interfere
                        if (!key.toLowerCase().contains("zss")){
                            newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                        }
                    }
                    ReportRenderer.populateBook(newBook, 0);
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
        Sheet validationSheet = book.getSheet(ChoicesService.VALIDATION_SHEET);
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
        Sheet validationSheet = book.getSheet(ChoicesService.VALIDATION_SHEET);
        if (validationSheet != null) {
            try {
                book.getInternalBook().deleteSheet(validationSheet.getInternalSheet());
            } catch (Exception ignored) {
                // todo - bring this up with ZK?
            }
        }
        Exporter exporter = Exporters.getExporter("pdf");
        //((PdfExporter) exporter).getPrintSetup().setLandscape(false);
        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            exporter.export(book, fos);
        }
        return file.getAbsolutePath();
    }
}
