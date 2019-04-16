package com.azquo.spreadsheet.controller;

import com.azquo.DateUtils;
import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.dataimport.ImportService;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.zk.ChoicesService;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportService;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.json.JSONObject;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.jsp.JsonUpdateBridge;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zul.Filedownload;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 05/03/15
 *
 * Adapted from a ZK example - so buttons in the jsp can interact with the ZK sheet object
 */
@Controller
@RequestMapping("/ZKSpreadsheetCommand")
public class ZKSpreadsheetCommandController {

    static String base64int(int input){
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
    }


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
            protected void process(Desktop desktop) throws IOException {
                Spreadsheet ss = (Spreadsheet) desktop.getComponentByUuidIfAny(zssUuid);
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
                        Clients.showBusy("Processing ...");
                        Clients.evalJavaScript("postAjax('ActuallyXLS');");
                    }
                    if ("ActuallyXLS".equals(action)) {
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
                        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
                        loggedInUser.userLog("Save : " + onlineReport.getReportName() + ".xlsx");
                        Filedownload.save(new AMedia(onlineReport.getReportName() + ".xlsx", null, null, file, true));
                        Clients.clearBusy();
                    }

                    if ("SaveTemplate".equals(action)) { // similar to above but we're overwriting the report
                        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper() || loggedInUser.getUser().isMaster()) {
                            Exporter exporter = Exporters.getExporter();
                            Book book = ss.getBook();
                            int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                            OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
                            loggedInUser.userLog("SaveTemplate : " + onlineReport.getReportName());
                            String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk(); // as in the online controller
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
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            exporter.export(book, file);
                        }
                        loggedInUser.userLog("Download PDF : " + ss.getSelectedSheetName() + ".pdf");
                        Filedownload.save(new AMedia(ss.getSelectedSheetName() + ".pdf", "pdf", "application/pdf", file, true));
                    }
                    if ("XML".equals(action)) {
                        // ok try to find the relevant regions
                        Clients.showBusy("Processing ...");
                        List<SName> namesForSheet = BookUtils.getNamesForSheet(ss.getSelectedSheet());
                        Path zipforxmlfiles = Files.createTempDirectory("zipforxmlfiles");
                        for (SName name : namesForSheet) {
                            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZDATAREGION)) { // then we have a data region to deal with here
                                String region = name.getName().substring(ReportRenderer.AZDATAREGION.length()); // might well be an empty string
                                // we don't actually need to do anything with this now but we need to switch on the XML button
                                SName xmlHeadings = BookUtils.getNameByName(ReportRenderer.AZXML + region, ss.getSelectedSheet());
                                Map<String,Integer> xmlToColMap = new HashMap<>();
                                if (xmlHeadings != null) {
                                    Map<String,Integer> reportSelectionsColMap = new HashMap<>();
                                    String reportName = null;
                                    SName filePrefixName = BookUtils.getNameByName(ReportRenderer.AZXMLFILENAME + region, ss.getSelectedSheet());

                                    String filePrefix = null;
                                    boolean multiRowPrefix = false; // a multi row prefix means the prefix will be different for each line
                                    if (filePrefixName != null){
                                        if ((filePrefixName.getRefersToCellRegion().lastRow - filePrefixName.getRefersToCellRegion().row) > 0){ // only one row normally
                                            multiRowPrefix = true;
                                        } else {
                                            SCell snameCell = BookUtils.getSnameCell(filePrefixName);
                                            if (snameCell != null){
                                                // dash not allowed
                                                filePrefix = snameCell.getStringValue().replace("-","");
                                            }
                                        }
                                    }

                                    for (int col = xmlHeadings.getRefersToCellRegion().column; col <= xmlHeadings.getRefersToCellRegion().lastColumn; col++){
                                        CellData cellData = Ranges.range(ss.getSelectedSheet(), xmlHeadings.getRefersToCellRegion().row,col).getCellData();
//                                        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
                                        //if (colCount++ > 0) bw.write('\t');
                                        if (cellData != null && cellData.getFormatText().length() > 0) {
                                            xmlToColMap.put(cellData.getFormatText(), col);// I assume means formatted text
                                        }
                                    }

                                    SName supportReportName = BookUtils.getNameByName(ReportRenderer.AZSUPPORTREPORTNAME + region, ss.getSelectedSheet());
                                    SName supportReportSelections = BookUtils.getNameByName(ReportRenderer.AZSUPPORTREPORTSELECTIONS + region, ss.getSelectedSheet());
                                    if (supportReportName != null && supportReportSelections != null){
                                        // then we have xlsx files to generate along side the XML files
                                        SCell snameCell = BookUtils.getSnameCell(supportReportName);
                                        if (snameCell != null){
                                            reportName = snameCell.getStringValue();
                                        }
                                        for (int col = supportReportSelections.getRefersToCellRegion().column; col <= supportReportSelections.getRefersToCellRegion().lastColumn; col++){
                                            CellData cellData = Ranges.range(ss.getSelectedSheet(), supportReportSelections.getRefersToCellRegion().row,col).getCellData();
//                                        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
                                            //if (colCount++ > 0) bw.write('\t');
                                            if (cellData != null && cellData.getFormatText().length() > 0) {
                                                reportSelectionsColMap.put(cellData.getFormatText(), col);// I assume means formatted text
                                            }
                                        }
                                    }


                                    boolean rootInSheet = true;

                                    String rootCandidate = null;

                                    for (String xmlName : xmlToColMap.keySet()){
                                        String test;
                                        if (xmlName.indexOf("/", 1) > 0){
                                            test = xmlName.substring(0, xmlName.indexOf("/", 1));
                                        } else {
                                            test = xmlName;
                                        }
                                        if (rootCandidate == null){
                                            rootCandidate = test;
                                        } else if (!rootCandidate.equals(test)){
                                            rootInSheet = false;
                                        }
                                    }

                                    if (rootInSheet){ // then strip off the first tag, it will be used as root
                                        for (String xmlName : new ArrayList<>(xmlToColMap.keySet())) { // copy the keys, I'm going to modify them!
                                            Integer remove = xmlToColMap.remove(xmlName);
                                            xmlToColMap.put(xmlName.substring(xmlName.indexOf("/", 1)), remove);
                                        }
                                    }

                                    List<String> xmlNames = new ArrayList<>(xmlToColMap.keySet());
                                    Collections.sort(xmlNames);

                                    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                                    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                                    Transformer transformer = transformerFactory.newTransformer();
                                    // matching brekasure example
                                    transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
                                    // going for one file per line as per brokersure, zip at the end
                                    // tracking where we are in the xml, what elements we're in
                                    // new criteria for filenames - do a base 64 hash
                                    LocalDateTime start = LocalDateTime.of(2019, Month.JANUARY, 1,0,0);
                                    LocalDateTime now = LocalDateTime.now();
                                    int filePointer = (int) (now.toEpochSecond(ZoneOffset.UTC) - start.toEpochSecond(ZoneOffset.UTC));

                                    for (int row = name.getRefersToCellRegion().row; row <= name.getRefersToCellRegion().lastRow; row++) {
                                        String fileName = base64int(filePointer) + ".xml";
                                        // if multi row try and find a prefix value in the range
                                        if (multiRowPrefix){
                                            CellRegion refersToCellRegion = filePrefixName.getRefersToCellRegion();
                                            if (row >= refersToCellRegion.row && row <= refersToCellRegion.getLastRow()){
                                                SCell cell = ss.getSelectedSheet().getInternalSheet().getCell(row, refersToCellRegion.column);
                                                if (cell != null){
                                                    // dash not allowed
                                                    filePrefix = cell.getStringValue().replace("-","");
                                                }

                                            }
                                        }

                                        if (filePrefix != null){
                                            fileName = filePrefix + fileName;
                                        }
                                        //System.out.println("file name : " + fileName);
                                        while (zipforxmlfiles.resolve(fileName).toFile().exists()){
                                            filePointer++;
                                            fileName = base64int(filePointer) + ".xml";
                                            if (filePrefix != null){
                                                fileName = filePrefix + fileName;
                                            }
                                        }
                                        Document doc = docBuilder.newDocument();
                                        doc.setXmlStandalone(true);
                                        Element rootElement = doc.createElement(rootInSheet ? rootCandidate.replace("/","") : "ROOT");
                                        doc.appendChild(rootElement);
                                        List<Element> xmlContext = new ArrayList<>();
                                        for (String xmlName : xmlNames){
                                            CellData cellData = Ranges.range(ss.getSelectedSheet(), row, xmlToColMap.get(xmlName)).getCellData();
                                            String value = "";
                                            if (cellData != null) {
                                                value = cellData.getFormatText();// I assume means formatted text
                                            }
                                            // note - this logic assumes he mappings are sorted
                                            StringTokenizer st = new StringTokenizer(xmlName, "/");
                                            int i = 0;
                                            while (st.hasMoreTokens()){
                                                String nameElement = st.nextToken();
                                                if (i >= xmlContext.size() || !xmlContext.get(i).getTagName().equals(nameElement)){
                                                    if (i < xmlContext.size()){ // it didn't match, need to chop
                                                        xmlContext = xmlContext.subList(0,i);// trim the list of bits we don't need
                                                    }
                                                    Element element = doc.createElement(nameElement);
                                                    if (xmlContext.isEmpty()){
                                                        rootElement.appendChild(element);
                                                    } else {
                                                        xmlContext.get(xmlContext.size() - 1).appendChild(element);
                                                    }
                                                    xmlContext.add(element);
                                                }
                                                i++;
                                            }
                                            xmlContext.get(xmlContext.size() - 1).appendChild(doc.createTextNode(value));
                                                //test.append(name).append(value).append("\n");
                                        }
                                        DOMSource source = new DOMSource(doc);
                                        BufferedWriter bufferedWriter = Files.newBufferedWriter(zipforxmlfiles.resolve(fileName));
                                        StreamResult result = new StreamResult(bufferedWriter);
                                        transformer.transform(source, result);
                                        bufferedWriter.close();
                                        // do reports if applicable
                                        if (reportName != null && !reportSelectionsColMap.isEmpty()){ // I can't see how the reportSelectionsColMap could be empty and valid
                                            for (String choiceName : reportSelectionsColMap.keySet()) {
                                                CellData cellData = Ranges.range(ss.getSelectedSheet(), row, reportSelectionsColMap.get(choiceName)).getCellData();
                                                String value = "";
                                                if (cellData != null) {
                                                    value = cellData.getFormatText();// I assume means formatted text
                                                }
                                                if (!value.isEmpty()){
                                                    SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceName, value);
                                                }
                                            }
                                            OnlineReport onlineReport = null;
                                            Database oldDatabase = null;
                                            if (loggedInUser.getPermission(reportName.toLowerCase()) != null) {
                                                TypedPair<OnlineReport, Database> permission = loggedInUser.getPermission(reportName.toLowerCase());
                                                onlineReport = permission.getFirst();
                                                if (permission.getSecond() != null) {
                                                    oldDatabase = loggedInUser.getDatabase();
                                                    loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), permission.getSecond());
                                                }
                                            } else { // otherwise try a straight lookup - stick on whatever db we're currently on
                                                onlineReport = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName);
                                                if (onlineReport == null){
                                                    onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                                                }
                                            }
                                            if (onlineReport != null) { // need to prepare it as in the controller todo - factor?
                                                String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
                                                final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                                                book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                                                book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                                                book.getInternalBook().setAttribute(OnlineController.REPORT_ID, onlineReport.getId());
                                                StringBuilder errorLog = new StringBuilder();
                                                ReportRenderer.populateBook(book, 0, false, true, errorLog); // note true at the end here - keep on logging so users can see changes as they happen
                                                Exporter exporter = Exporters.getExporter();
                                                File file = zipforxmlfiles.resolve((filePrefix != null ? filePrefix : "") + base64int(filePointer) + ".xlsx").toFile();
                                                try (FileOutputStream fos = new FileOutputStream(file)) {
                                                    exporter.export(book, fos);
                                                }
                                                // now get the file . . .
                                                if (oldDatabase != null) {
                                                    loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), oldDatabase);
                                                }
                                            }
                                        }
                                        filePointer++;
                                    }
                                }
                            }
                        }
                        ZipUtil.unexplode(zipforxmlfiles.toFile());
                        Filedownload.save(new AMedia(ss.getSelectedSheetName() + "xml.zip", "zip", "application/zip", zipforxmlfiles.toFile(), true));
                        Clients.clearBusy();
                    }

                    String saveMessage = "";

                    // now just pops up the processing, bounces back to actually save below
                    if ("Save".equals(action)) {
                        Clients.showBusy("Processing ...");
                        Clients.evalJavaScript("postAjax('ActuallySave');");
                    }

                    if ("ActuallySave".equals(action)) {
                        saveMessage = ReportService.save(ss,loggedInUser);
                        Clients.clearBusy();
                    }

                    if ("ExecuteSave".equals(action)) {
                        ReportService.save(ss,loggedInUser);
                        Clients.clearBusy();
                    }

                    if ("RestoreSavedValues".equals(action) || saveMessage.startsWith("Success")) {
                        final Book book = ss.getBook();
                        final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                        for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                            newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
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
        ReportRenderer.populateBook(newBook, 0);
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
