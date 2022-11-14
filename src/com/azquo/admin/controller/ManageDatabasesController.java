package com.azquo.admin.controller;

import com.azquo.RowColumn;
import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.HeadingWithInterimLookup;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.csvreader.CsvWriter;
import com.github.rcaller.rstuff.RCaller;
import com.github.rcaller.rstuff.RCode;
import io.keikai.api.Books;
import io.keikai.api.Importers;
import io.keikai.api.Ranges;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipException;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;
import org.zkoss.zk.ui.Sessions;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import io.keikai.api.model.Book;


/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * New HTML admin, upload files and manage databases
 * <p>
 * CRUD type stuff though databases will be created/deleted etc. server side.
 * <p>
 * Edd note 24/10/2018 - this is getting a little cluttered, should it be refactored? todo
 * <p>
 * Current responsibilities :
 * <p>
 * create and delete dbs/upload files and manage uploads/backup/restore/pending uploads
 * <p>
 * pending uploads should perhaps be taken out of here
 * <p>
 * Also deals with import templates
 */
@Controller
@RequestMapping("/ManageDatabases")
public class ManageDatabasesController {

    @Autowired
    ServletContext servletContext;

    //    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);
    private static DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

    public static final String IMPORTRESULT = "importResult";
    public static final String IMPORTURLSUFFIX = "importUrlSuffix";
    public static final String IMPORTSTATUS = "importStatus";

    // to play nice with velocity or JSP - so I don't want it to be private as Intellij suggests
    public static class DisplayDataBase {
        private final boolean loaded;
        private final Database database;

        DisplayDataBase(boolean loaded, Database database) {
            this.loaded = loaded;
            this.database = database;
        }

        public boolean getLoaded() {
            return loaded;
        }

        public int getId() {
            return database.getId();
        }

        public int getBusinessId() {
            return database.getBusinessId();
        }

        public String getName() {
            return database.getName();
        }

        public String getPersistenceName() {
            return database.getPersistenceName();
        }

        public int getNameCount() {
            return database.getNameCount();
        }

        public int getValueCount() {
            return database.getValueCount();
        }

        public String getUrlEncodedName() {
            return database.getUrlEncodedName();
        }

        public String getLastProvenance() {
            return database.getLastProvenance() != null ? database.getLastProvenance() : "";
        }

        public boolean getAutobackup() {
            return database.getAutoBackup();
        }

        public String getCreated() {
            return format.format(database.getCreated());
        }

        public String getImportTemplate() {
            ImportTemplate importTemplate = database.getImportTemplateId() != -1 ? ImportTemplateDAO.findById(database.getImportTemplateId()) : null;
            if (importTemplate != null) {
                return importTemplate.getTemplateName();
            }
            return "";
        }
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "createDatabase", required = false) String createDatabase
            , @RequestParam(value = "databaseServerId", required = false) String databaseServerId
            , @RequestParam(value = "emptyId", required = false) String emptyId
            , @RequestParam(value = "checkId", required = false) String checkId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "unloadId", required = false) String unloadId
            , @RequestParam(value = "toggleAutobackup", required = false) String toggleAutobackup
                                // todo - address whether we're still using such parameters and associated functions
            , @RequestParam(value = "fileSearch", required = false) String fileSearch
            , @RequestParam(value = "deleteUploadRecordId", required = false) String deleteUploadRecordId
            , @RequestParam(value = "sort", required = false) String sort
            , @RequestParam(value = "pendingUploadSearch", required = false) String pendingUploadSearch
            , @RequestParam(value = "deleteTemplateId", required = false) String deleteTemplateId
            , @RequestParam(value = "templateassign", required = false) String templateassign
            , @RequestParam(value = "withautos", required = false) String withautos
            , @RequestParam(value = "newdesign", required = false) String newdesign
    ) {
        // I assume secure until we move to proper spring security
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            StringBuilder error = new StringBuilder();
            StringBuilder results = new StringBuilder();
            // EFC - I can't see a way around this one currently. I want to use @SuppressWarnings very sparingly
            List<UploadedFile> importResult = null;
            try {
                importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
            } catch (Exception e) {
                //if the error is a singleton, it causes a problem - ignore it at the moment.
            }
            if (importResult != null) {
                results.append(formatUploadedFiles(importResult, -1, false, null));
                request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULT);
            }
            if ("1".equals(templateassign)) {
                List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
                for (Database db : databaseList) {
                    String templateName = request.getParameter("templateName-" + db.getId());
                    if (templateName != null) {
                        ImportTemplate it = ImportTemplateDAO.findForNameAndBusinessId(templateName, loggedInUser.getUser().getBusinessId());
                        if (it == null) {
                            db.setImportTemplateId(-1);
                        } else {
                            db.setImportTemplateId(it.getId());
                        }
                        DatabaseDAO.store(db);
                    }
                }
            }

            try {
                final List<DatabaseServer> allServers = DatabaseServerDAO.findAll();
                if (createDatabase != null && !createDatabase.isEmpty() && (allServers.size() == 1 || (databaseServerId != null && !databaseServerId.isEmpty()))) {
                    if (allServers.size() == 1) {
                        AdminService.createDatabase(createDatabase, loggedInUser, allServers.get(0));
                    } else {
                        AdminService.createDatabase(createDatabase, loggedInUser, DatabaseServerDAO.findById(Integer.parseInt(databaseServerId)));
                    }
                }
                if (NumberUtils.isNumber(emptyId)) {
                    AdminService.emptyDatabaseByIdWithBasicSecurity(loggedInUser, Integer.parseInt(emptyId));
                }
                if (NumberUtils.isNumber(checkId)) {
                    AdminService.checkDatabaseByIdWithBasicSecurity(loggedInUser, Integer.parseInt(checkId));
                }
                if (NumberUtils.isNumber(deleteId)) {
                    AdminService.removeDatabaseByIdWithBasicSecurity(loggedInUser, Integer.parseInt(deleteId));
                }
                if (NumberUtils.isNumber(deleteTemplateId)) {
                    AdminService.removeImportTemplateByIdWithBasicSecurity(loggedInUser, Integer.parseInt(deleteTemplateId));
                }
                if (NumberUtils.isNumber(unloadId)) {
                    AdminService.unloadDatabaseWithBasicSecurity(loggedInUser, Integer.parseInt(unloadId));
                }
                if (NumberUtils.isNumber(toggleAutobackup)) {
                    AdminService.toggleAutoBackupWithBasicSecurity(loggedInUser, Integer.parseInt(toggleAutobackup));
                }
                if (NumberUtils.isNumber(deleteUploadRecordId)) {
                    AdminService.deleteUploadRecord(loggedInUser, Integer.parseInt(deleteUploadRecordId));
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                model.put("error", error.toString());
            }
            if (results.length() > 0) {
                model.put("results", results.toString());
            }
            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                if (databaseList != null) {
                    for (Database database : databaseList) {
                        boolean isLoaded = AdminService.isDatabaseLoaded(loggedInUser, database);
                        // it seems sometimes databases get stuck on 0 when they are clearly not 0, this is a quick hack to try to catch this when it happens
                        if (isLoaded && database.getNameCount() == 0) {
                            AdminService.updateNameAndValueCounts(loggedInUser, database);
                        }
                        displayDataBases.add(new DisplayDataBase(isLoaded, database));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }

            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("databases", displayDataBases);
            final List<DatabaseServer> allServers = DatabaseServerDAO.findAll();
            model.put("databaseServers", allServers);
            if (allServers.size() == 1) {
                model.put("serverList", false);
            } else {
                model.put("serverList", true);
            }
            // another bit of utility functionality that perhaps should be moved from here in a bit : automated tests
            Path testDir = Paths.get(SpreadsheetService.getHomeDir() + "/systemtests");
            if (testDir.toFile().isDirectory()) { // sso find directories in there
                try (Stream<Path> testDirList = Files.list(testDir)) {
                    Iterator<Path> testDirListIterator = testDirList.iterator();
                    // go through the main directory looking for directories that match a DB name
                    while (testDirListIterator.hasNext()) {
                        Path specificTestDir = testDirListIterator.next();
                        if (specificTestDir.toFile().isDirectory()) {
                            Path scriptFile = specificTestDir.resolve("script.txt");
                            if (scriptFile.toFile().exists()) {
                                // todo - create test db
                                List<String> testLines = Files.readAllLines(scriptFile, Charset.defaultCharset());
                                for (String testLine : testLines) {
                                    if (testLine.toLowerCase().startsWith("load ")) { // then straight load that file into the database
                                        // this could be import template or report or data or more likely a zip file

                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplayForBusiness = AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, fileSearch, "true".equalsIgnoreCase(withautos));
            if (uploadRecordsForDisplayForBusiness != null) {
                if ("database".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getDatabaseName));
                }
                if ("databasedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getDatabaseName().compareTo(o1.getDatabaseName())));
                }
                if ("date".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getDate));
                }
                if ("datedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getDate().compareTo(o1.getDate())));
                }
                if ("businessname".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getBusinessName));
                }
                if ("businessnamedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getBusinessName().compareTo(o1.getBusinessName())));
                }
                if ("username".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getUserName));
                }
                if ("usernamedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getUserName().compareTo(o1.getUserName())));
                }
            }

            model.put("dbsort", "database".equals(sort) ? "databasedown" : "database");
            model.put("datesort", "date".equals(sort) ? "datedown" : "date");
            model.put("businessnamesort", "businessname".equals(sort) ? "businessnamedown" : "businessname");
            model.put("usernamesort", "username".equals(sort) ? "usernamedown" : "username");
            model.put("uploads", uploadRecordsForDisplayForBusiness);
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, pendingUploadSearch, request.getParameter("allteams") != null, request.getParameter("uploadreports") != null)); // no search for the mo
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            model.put("importTemplates", ImportTemplateDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()));
            AdminService.setBanner(model, loggedInUser);
            model.put("reports", AdminService.getReportList(loggedInUser, true));
            if ("databases".equalsIgnoreCase(newdesign)) {
                return "databases";
            }
            if ("maintenance".equalsIgnoreCase(newdesign)) {
                return "maintenance";
            }
            if ("imports".equalsIgnoreCase(newdesign)) {
                StringBuilder jsImportsList = new StringBuilder();
                for (UploadRecord.UploadRecordForDisplay uploadRecord : uploadRecordsForDisplayForBusiness) {
                    jsImportsList.append("new f({\n" +
                            "                        id: " + uploadRecord.id + ",\n" +
                            "                        filename: \"" + uploadRecord.getFileName() + "\",\n" +
                            "                        database: \"" + uploadRecord.getDatabaseName() + "\",\n" +
                            "                        user: \"" + uploadRecord.getUserName() + "\",\n" +
                            "                        date: " + uploadRecord.getDate().toEpochSecond(ZoneOffset.of("Z")) + ",\n" +
                            "                        }),");
                }
                StringBuilder databasesList = new StringBuilder();
//                new d({ id: 1, name: "Demo1dhdhrt", date: 1546563989 }),
                for (Database d : databaseList) {
                    databasesList.append("new d({ id: "+ d.getId() +", name: \"" + d.getName() + "\", date: 0 }),"); // date jammed as zero for the mo, does the JS use it?
                }

                /* need to make JS array like this of the uploads

                new f({
                        id: 1,
                        filename: "co11111111111sts_amendment_SD23.xlsx",
                        database: "Atos PIP",
                        user: "Phil Stubbs",
                        date: 1555860815,
                        }),
                */
                List<OnlineReport> reportList = AdminService.getReportList(loggedInUser, true);
                StringBuilder reportsList = new StringBuilder();
                for (OnlineReport or : reportList){
                    reportsList.append("new m({ id: " + or.getId() + ", name: \"" + or.getUntaggedReportName() + "\", database: \"" + or.getDatabase() + "\", author: \"" + or.getAuthor() + "\", description: \"" + or.getExplanation().replace("\n","").replace("\r","") + "\" }),\n");
                }

                try {
                    InputStream resourceAsStream = servletContext.getResourceAsStream("/WEB-INF/includes/newappjavascript.js");
                    model.put("newappjavascript", IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8)
                            .replace("###IMPORTSLIST###", jsImportsList.toString())
                            .replace("###DATABASESLIST###", databasesList.toString())
                            .replace("###REPORTSLIST###", reportsList.toString()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (model.get("error") != null){
                    String escapedError = (String) model.get("error");
                    model.put("error", escapedError.replace("\"", "\\\"").replace("\n","").replace("<br/>","\\n").replace("<b>","\\n").replace("</b>","\\n"));
                }

                if (model.get("results") != null){
                    String escapedResults = (String) model.get("results");
                    model.put("results", escapedResults.replace("\"", "\\\"").replace("\n","").replace("<br/>","\\n").replace("<b>","\\n").replace("</b>","\\n"));
                }
                model.put("pageUrl", "/imports");
                return "imports";
            }
            // todo - if newdesign = pending uploads. And then later we must refactor this into different controllers
            if ("pendinguploads".equalsIgnoreCase(newdesign)) {
                return "pendinguploads";
            }
            if ("importtemplates".equalsIgnoreCase(newdesign)) {
                return "importtemplates";
            }
            return "managedatabases2";
        } else {
            return "redirect:/api/Login";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile[] uploadFiles
            , @RequestParam(value = "backup", required = false) String backup
            , @RequestParam(value = "template", required = false) String template
            , @RequestParam(value = "team", required = false) String team
            , @RequestParam(value = "userComment", required = false) String userComment
            , @RequestParam(value = "preprocessorTest", required = false) MultipartFile[] preprocessorTest
            , @RequestParam(value = "newdesign", required = false) String newdesign
    ) {

        if (database != null && database.length()> 0) {
            request.getSession().setAttribute("lastSelected", database);
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        MultipartFile uploadFile = null;
        if (uploadFiles.length > 0) {
            uploadFile = uploadFiles[0];
        }
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (uploadFile != null && !uploadFile.isEmpty()) {
                try {
                    if ("true".equals(backup) && uploadFiles.length == 1) {
                        // duplicated fragment, could maybe be factored
                        String fileName = uploadFile.getOriginalFilename();
                        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                        uploadFile.transferTo(moved);
                        model.put("results", BackupService.loadBackup(loggedInUser, moved, database));
                        // todo - "import templates" being detected internally also - do we need to do it here? I guess it's to do with user feedback
                    } else if ("true".equals(template) || uploadFile.getOriginalFilename().toLowerCase().contains("import templates") && uploadFiles.length == 1) {
                        try {
                            String fileName = uploadFile.getOriginalFilename();
                            File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                            uploadFile.transferTo(moved);
                            Workbook book;
                            UploadedFile uploadedFile = new UploadedFile(moved.getAbsolutePath(), Collections.singletonList(fileName), new HashMap<>(), false, false);
                            // wary of windows locking files, need to see how it goes
                            FileInputStream fs = new FileInputStream(new File(uploadedFile.getPath()));
                            /*

                            Caused by: org.apache.xmlbeans.XmlException: error: Content is not allowed in trailing section.
	at org.apache.xmlbeans.impl.store.Locale$SaxLoader.load(Locale.java:3448)
	at org.apache.xmlbeans.impl.store.Locale.parseToXmlObject(Locale.java:1272)
	at org.apache.xmlbeans.impl.store.Locale.parseToXmlObject(Locale.java:1259)
	at org.apache.xmlbeans.impl.schema.SchemaTypeLoaderBase.parse(SchemaTypeLoaderBase.java:345)
	at org.apache.xmlbeans.XmlObject$Factory.parse(XmlObject.java:722)
	at org.zkoss.poi.xssf.usermodel.XSSFVMLDrawing.read(XSSFVMLDrawing.java:108)
	at org.zkoss.poi.xssf.usermodel.XSSFVMLDrawing.<init>(XSSFVMLDrawing.java:103)
	... 16 more
Caused by: org.xml.sax.SAXParseException; systemId: file://; lineNumber: 28; columnNumber: 18; Content is not allowed in trailing section.
	at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.createSAXParseException(ErrorHandlerWrapper.java:204)
	at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.fatalError(ErrorHandlerWrapper.java:178)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:400)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:327)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLScanner.reportFatalError(XMLScanner.java:1471)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl$TrailingMiscDriver.next(XMLDocumentScannerImpl.java:1433)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:605)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.next(XMLNSDocumentScannerImpl.java:112)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:541)
	at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:888)
	at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:824)
	at java.xml/com.sun.org.apache.xerces.internal.parsers.XMLParser.parse(XMLParser.java:141)
	at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.parse(AbstractSAXParser.java:1216)
	at java.xml/com.sun.org.apache.xerces.internal.jaxp.SAXParserImpl$JAXPSAXParser.parse(SAXParserImpl.java:635)
	at org.apache.xmlbeans.impl.store.Locale$SaxLoader.load(Locale.java:3422)

                             */

                            if (uploadedFile.getFileName().toLowerCase().endsWith("xlsx")) {
                                OPCPackage opcPackage = OPCPackage.open(fs);
                                book = new XSSFWorkbook(opcPackage);
                            } else {
                                book = new HSSFWorkbook(fs);
                            }
                            // detect an import template by a sheet name
                            boolean isImportTemplate = false;
                            for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                                Sheet sheet = book.getSheetAt(sheetNo);
                                if (sheet.getSheetName().equalsIgnoreCase(ImportService.IMPORTMODEL)) {
                                    isImportTemplate = true;
                                    break;
                                }
                            }
                            //detect from workbook name
                            String lcName = uploadedFile.getFileName().toLowerCase();
                            if (!lcName.contains("=") && (lcName.contains("import template") || lcName.contains("workbook processor") || lcName.contains("preprocessor") || lcName.contains("headings") || lcName.contains("lookups"))) {
                                isImportTemplate = true;
                            }
                            assignDatabaseToUser(loggedInUser, database);

                            if (!isImportTemplate) {
                                model.put("error", "That does not appear to be an import template.");
                            } else {
                                model.put("results", formatUploadedFiles(Collections.singletonList(ImportService.uploadImportTemplate(uploadedFile, loggedInUser, userComment)), -1, false, null));
                            }
                        } catch (Exception e) {
                            model.put("error", e.getMessage());
                            e.printStackTrace();
                        }
                    } else if (database != null) {
                        if (database.isEmpty()) {
                            model.put("error", "Please select a database");
                        } else {
                            // data file or zip. New thing - it can be added to the Pending Uploads manually
                            // todo - security hole here, a developer could hack a file onto a different db by manually editing the database parameter. . .
                            LoginService.switchDatabase(loggedInUser, database); // could be blank now
                            if (team != null && uploadFiles.length == 1) { // Pending uploads
                                String targetPath = SpreadsheetService.getFilesToImportDir() + "/tagged/" + System.currentTimeMillis() + uploadFile.getOriginalFilename();
                                uploadFile.transferTo(new File(targetPath));
                                // ok it's moved now make the pending upload record
                                // todo - assign the database and team automatically!
                                PendingUpload pendingUpload = new PendingUpload(0, loggedInUser.getUser().getBusinessId()
                                        , LocalDateTime.now()
                                        , null
                                        , uploadFile.getOriginalFilename()
                                        , targetPath
                                        , loggedInUser.getUser().getId()
                                        , -1
                                        , loggedInUser.getDatabase().getId()
                                        , null, team);
                                PendingUploadDAO.store(pendingUpload);

                            } else { // a straight upload, this is the only place that can deal with multiple files being selected for upload
                                HttpSession session = request.getSession();
                                String result = handleImport(loggedInUser, session, model, userComment, uploadFiles, newdesign);
                                if (loggedInUser.getWizardInfo()!=null && loggedInUser.getWizardInfo().getPreprocessor()!=null){
                                    request.setAttribute(OnlineController.BOOK, loggedInUser.getWizardInfo().getPreprocessor());
                                }
                                return result;
                            }
                        }
                    }
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                    return "managedatabases2";
                }
            } else {
                model.put("error", "Please select a file to upload");
                return "managedatabases2";
            }
            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                assert databaseList != null;
                for (Database database1 : databaseList) {
                    boolean isLoaded = AdminService.isDatabaseLoaded(loggedInUser, database1);
                    displayDataBases.add(new DisplayDataBase(isLoaded, database1));
/*                    if (isLoaded && (AdminService.getNameCountWithBasicSecurity(loggedInUser, database1) != database1.getNameCountWithBasicSecurity()
                            || AdminService.getValueCountWithBasicSecurity(loggedInUser, database1) != database1.getValueCountWithBasicSecurity())) { // then update the counts
                        database1.setNameCount(AdminService.getNameCountWithBasicSecurity(loggedInUser, database1));
                        database1.setValueCount(AdminService.getValueCountWithBasicSecurity(loggedInUser, database1));
                        DatabaseDAO.store(database1);
                    }*/
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // now preprocessor test - should this be factored? Can it bounce to a straight download? One thing at a time . . .
            // also the copied code todo
            if (preprocessorTest.length > 0) {
                 Preprocessor.preprocesorTest(preprocessorTest, model, loggedInUser);
            }

            model.put("databases", displayDataBases);
            final List<DatabaseServer> allServers = DatabaseServerDAO.findAll();
            if (allServers.size() > 1) {
                model.put("databaseServers", allServers);
                model.put("serverList", true);
            } else {
                model.put("serverList", false);
            }
            // todo factor
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("uploads", AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, null, false));
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, null, request.getParameter("allteams") != null, request.getParameter("uploadedreports") != null));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            model.put("importTemplates", ImportTemplateDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()));
            AdminService.setBanner(model, loggedInUser);
            model.put("reports", AdminService.getReportList(loggedInUser, true));
            if ("databases".equalsIgnoreCase(newdesign)) {
                return "databases";
            }
            if ("maintenance".equalsIgnoreCase(newdesign)) {
                return "maintenance";
            }
            if ("pendinguploads".equalsIgnoreCase(newdesign)) {
                return "pendinguploads";
            }
            if ("importtemplates".equalsIgnoreCase(newdesign)) {
                return "importtemplates";
            }

            if ("imports".equalsIgnoreCase(newdesign)) {

                if (model.get("error") != null){
                    String escapedError = (String) model.get("error");
                    model.put("error", escapedError.replace("\"", "\\\"").replace("\n","").replace("<br/>","\\n").replace("<b>","\\n").replace("</b>","\\n"));
                }

                if (model.get("results") != null){
                    String escapedResults = (String) model.get("results");
                    model.put("results", escapedResults.replace("\"", "\\\"").replace("\n","").replace("<br/>","\\n").replace("<b>","\\n").replace("</b>","\\n"));
                }
                model.put("pageUrl", "/imports");
                return "imports";
            }
            return "managedatabases2";
        } else {
            return "redirect:/api/Login";
        }

    }

    private static void assignDatabaseToUser(LoggedInUser loggedInUser, String database)throws Exception{

        if (database != null && !database.isEmpty() && !database.equalsIgnoreCase("none")) {
            LoginService.switchDatabase(loggedInUser, database); // could be blank now
         }

    }

    // factored due to pending uploads, need to check the factoring after the prototype is done
    private static String handleImport(LoggedInUser loggedInUser, HttpSession session, ModelMap model, String userComment, final MultipartFile[] uploadFiles, String newDesign) {
        // need to add in code similar to report loading to give feedback on imports
        final List<UploadedFile> uploadedFiles = new ArrayList<>();
        UploadedFile uf = null;
        try {
            // have to move the files first or it seems they get lost
            for (MultipartFile uploadFile : uploadFiles) {
                String fileName = uploadFile.getOriginalFilename();
                // always move uploaded files now, they'll need to be transferred to the DB server after code split
                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                final Map<String, String> fileNameParams = new HashMap<>();
                ImportService.addFileNameParametersToMap(fileName, fileNameParams);
                String filePath = moved.getAbsolutePath();
                uf = new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false, false);
                uploadFile.transferTo(moved);
                uploadedFiles.add(uf);
            }
            new Thread(() -> {
                List<UploadedFile> toSetInSession = new ArrayList<>();
                for (UploadedFile uploadedFile : uploadedFiles) {
                    // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
                    try {
                        toSetInSession.addAll(ImportService.importTheFile(loggedInUser, uploadedFile, session, null, userComment));
                        Map<String, String> params = new HashMap<>();
                        params.put("File", uploadedFile.getFileName());
                        UploadRecord mostRecentForUser = UploadRecordDAO.findMostRecentForUser(loggedInUser.getUser().getId());
                        if (mostRecentForUser != null) {
                            params.put("Link", "/api/DownloadFile?uploadRecordId=" + mostRecentForUser.getId());
                        }
                        loggedInUser.userLog("Upload file", params);
                    } catch (Exception e) {// this would stop the files in their tracks, I think that's right given that the type of error that would hit here is serious?
                        e.printStackTrace();
                        uploadedFile.setError(CommonReportUtils.getErrorFromServerSideException(e));
                        toSetInSession.add(uploadedFile);
                    }
                }
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, toSetInSession);
            }).start();
        } catch (IOException e) {
            uf.setError(CommonReportUtils.getErrorFromServerSideException(e));
            session.setAttribute(ManageDatabasesController.IMPORTRESULT, Collections.singleton(uf));
        }

        // edd pasting in here to get the banner colour working
        AdminService.setBanner(model, loggedInUser);
        model.addAttribute("targetController", "ManageDatabases");
        if (newDesign != null || session.getAttribute("newdesign") != null) {
            return "importrunning";
        }

        return "importrunning2";
    }

    // as it says make something for users to read from a list of uploaded files.
    public static String formatUploadedFiles(List<UploadedFile> uploadedFiles, int checkboxId, boolean noClickableHeadings, Set<String> comments) {
        StringBuilder errorList = new StringBuilder();
        StringBuilder toReturn = new StringBuilder();
        for (UploadedFile uploadedFile : uploadedFiles) {
            int id = uploadedFile.hashCode(); // stop clash with other div ids
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            boolean first = true;
            toReturn.append("<b>");
            for (String name : names) {
                if (!first) {
                    toReturn.append(", ");
                }
                toReturn.append(name);
                first = false;
            }
            toReturn.append("</b>");
            if (uploadedFile.isConvertedFromWorksheet()) {
                toReturn.append(" - Converted from worksheet");
            }

            toReturn.append("<br/>\n");
            // todo - table this? initial attempt was too wide
            toReturn.append("Validation Summary<br/>\n");
            // improved list
            if (!uploadedFile.getErrorHeadings().isEmpty()) {
                for (String errorHeading : uploadedFile.getErrorHeadings()) {
                    int linesWithThatError = 0;
                    for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                        if (warningLine.getErrors().keySet().contains(errorHeading)) {
                            linesWithThatError++;
                        }
                    }
                    toReturn.append(errorHeading).append(" : ");
                    if (linesWithThatError > 0) {
                        toReturn.append("<span style=\"background-color: #FFAAAA; color: #000000\">").append(linesWithThatError).append(" Warnings</span>");
                    } else {
                        toReturn.append("<span style=\"background-color: #AAFFAA; color: #000000\">Pass</span>");
                    }
                    toReturn.append("\n<br/>");
                }
            }

            // jump it out past the end for actual info
            first = true;
            for (String key : uploadedFile.getParameters().keySet()) {
                if (!first) {
                    toReturn.append(", ");
                }
                toReturn.append(key).append(" = ").append(uploadedFile.getParameter(key));
                first = false;
            }
            toReturn.append("<br/>\n");
            toReturn.append("Time to process : ").append(uploadedFile.getProcessingDuration()).append(" ms, ");
            toReturn.append("Number of lines imported : ").append(uploadedFile.getNoLinesImported()).append(", ");
            toReturn.append("Number of values adjusted: ").append(uploadedFile.getNoValuesAdjusted()).append(", ");
            toReturn.append("Number of names adjusted: ").append(uploadedFile.getNoNamesAdjusted()).append("\n<br/>");

            if (uploadedFile.getTopHeadings() != null && !uploadedFile.getTopHeadings().isEmpty()) {
                if (noClickableHeadings) {
                    toReturn.append("Top headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('topHeadings").append(id).append("'); return false;\">Top headings</a> : \n<br/><div id=\"topHeadings").append(id).append("\" style=\"display : none\">");
                }
                for (RowColumn rowColumn : uploadedFile.getTopHeadings().keySet()) {
                    toReturn.append(BookUtils.rangeToText(rowColumn.getRow(), rowColumn.getColumn())).append("\t->\t").append(uploadedFile.getTopHeadings().get(rowColumn)).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) {
                if (noClickableHeadings) {
                    toReturn.append("Headings with file headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('fileHeadings" + id + "'); return false;\">Headings with file headings</a>\n<br/><div id=\"fileHeadings" + id + "\" style=\"display : none\">");
                }

                Collection<List<String>> toShow;
                if (uploadedFile.getFileHeadings() != null) {
                    toShow = uploadedFile.getFileHeadings();
                } else {
                    toShow = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().keySet();
                }

                for (List<String> fileHeading : toShow) {
                    for (String subHeading : fileHeading) {
                        toReturn.append(subHeading.replaceAll("\n", " ")).append(" ");
                    }
                    HeadingWithInterimLookup headingWithInterimLookup = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                    if (headingWithInterimLookup != null) {
                        if (headingWithInterimLookup.getInterimLookup() != null) {
                            toReturn.append("\t->\t").append(headingWithInterimLookup.getInterimLookup().replaceAll("\n", " "));
                        }
                        toReturn.append("\t->\t").append(headingWithInterimLookup.getHeading().replaceAll("\n", " ")).append("\n<br/>");
                    } else {
                        toReturn.append("\t->\t").append(" ** UNUSED ** \n<br/>");
                    }
                }

                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null) {
                if (noClickableHeadings) {
                    toReturn.append("Headings without file headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('noFileHeadings" + id + "'); return false;\">Headings without file headings</a>\n<br/><div id=\"noFileHeadings" + id + "\" style=\"display : none\">");
                }
                for (HeadingWithInterimLookup headingWithInterimLookup : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
                    if (headingWithInterimLookup.getInterimLookup() != null) { // it could be null now as we support a non file heading azquo heading on the Import Model sheet
                        toReturn.append(headingWithInterimLookup.getInterimLookup());
                        toReturn.append(" -> ");
                    }
                    toReturn.append(headingWithInterimLookup.getHeading()).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getSimpleHeadings() != null) {
                if (noClickableHeadings) {
                    toReturn.append("Simple Headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('simpleHeadings" + id + "'); return false;\">Simple Headings</a>\n<br/><div id=\"simpleHeadings" + id + "\" style=\"display : none\">");
                }
                for (String heading : uploadedFile.getSimpleHeadings()) {
                    toReturn.append(heading).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (!uploadedFile.getLinesRejected().isEmpty()) {
                String error = "Line errors: " + uploadedFile.getLinesRejected().size() + "\n<br/>";
                error += "</b>";
                if (uploadedFile.getLinesRejected().size() < 100) {
                    for (UploadedFile.RejectedLine lineRejected : uploadedFile.getLinesRejected()) {
                        error += lineRejected.getLineNo() + ":" + lineRejected.getErrors() + "\n<br/>";
                    }
                }
                error += "<b>";
                errorList.append(updateErrorList(uploadedFile, errorList, error));
                if (noClickableHeadings) {
                    toReturn.append("Line Errors : ").append(uploadedFile.getNoLinesRejected()).append("\n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('rejectedLines" + id + "'); return false;\">Line Errors : ").append(uploadedFile.getNoLinesRejected()).append("</a>\n<br/><div id=\"rejectedLines" + id + "\" style=\"display : none\">");
                }
                int maxLineWidth = 0;
                for (UploadedFile.RejectedLine lineRejected : uploadedFile.getLinesRejected()) {
                    if (lineRejected.getLine().split("\t").length > maxLineWidth) {
                        maxLineWidth = lineRejected.getLine().split("\t").length;
                    }
                }

                toReturn.append("<div style='overflow: auto;max-height:400px;width:90vw'><table style='font-size:90%'><thead><tr>");
                toReturn.append("<th style='position: sticky; top: 0;'>Error</th>");
                toReturn.append("<th style='position: sticky; top: 0;'>#</th>");
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        toReturn.append("<th style='position: sticky; top: 0;'>");
                        first = true;
                        for (String heading : headingCol) {
                            if (!first) {
                                toReturn.append("<br/>");
                            }
                            toReturn.append(heading);
                            first = false;
                        }
                        toReturn.append("</th>");
                    }
                    int leftOver = maxLineWidth - uploadedFile.getFileHeadings().size();
                    for (int i = 0; i < leftOver; i++) {
                        toReturn.append("<th></th>");
                    }
                } else {
                    toReturn.append("<th style='position: sticky; top: 0;' colspan=\"" + maxLineWidth + "\"></th>");
                }
                toReturn.append("</tr></thead><tbody>");
                for (UploadedFile.RejectedLine lineRejected : uploadedFile.getLinesRejected()) {
                    toReturn.append("<tr><td nowrap><span style=\"background-color: #FFAAAA; color: #000000\">" + lineRejected.getErrors() + "</span></td><td>" + lineRejected.getLineNo() + "</td>");
                    String[] split = lineRejected.getLine().split("\t");
                    for (String cell : split) {
                        toReturn.append("<td nowrap>").append(cell).append("</td>");
                    }
                    int leftOver = maxLineWidth - split.length;
                    for (int i = 0; i < leftOver; i++) {
                        toReturn.append("<td nowrap></td>");
                    }
                    toReturn.append("</tr>");
                }
                toReturn.append("\n</tbody></table>" + (uploadedFile.getLinesRejected().size() > 1000 ? "Lines rejected greater than 1000, stopping list." : "") + "</div><br/>");
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }
            if (uploadedFile.getPostProcessingResult() != null) {

                if (noClickableHeadings) {
                    toReturn.append("Post processing result : ").append(uploadedFile.getPostProcessingResult()).append("\n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('ppr" + id + "'); return false;\">Post processing result</a>\n<br/><div id=\"ppr" + id + "\" style=\"display : none\">").append(uploadedFile.getPostProcessingResult()).append("</div>");
                }

            }
            // now the warning lines that will be a little more complex and have tickboxes
            // todo - factor later!
            if (!uploadedFile.getWarningLines().isEmpty()) {
                // We're going to need descriptions of the errors, put them all in a set
                // todo factor with error summary above
                Set<String> errorsSet = new HashSet<>();
                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    errorsSet.addAll(warningLine.getErrors().keySet());
                }
                List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe

                if (noClickableHeadings) {
                    toReturn.append("Line Warnings : ").append(uploadedFile.getWarningLines().size()).append("\n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('warningLines" + id + "'); return false;\">Line Warnings : ").append(uploadedFile.getWarningLines().size()).append("</a>\n<br/><div id=\"warningLines" + id + "\" style=\"display : none\">");
                }

                int maxLineWidth = uploadedFile.getFileHeadings() != null ? uploadedFile.getFileHeadings().size() : 0;
                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    if (warningLine.getLine().split("\t").length > maxLineWidth) {
                        maxLineWidth = warningLine.getLine().split("\t").length;
                    }
                }

                toReturn.append("<div style='overflow: auto;max-height:400px;width:90vw'><table style='font-size:90%'><thead><tr>");
                if (checkboxId != -1) {
                    toReturn.append("<th style='position: sticky; top: 0; z-index: 1;'>Load</th>");
                    toReturn.append("<th style='position: sticky; top: 0;'>Comment</th>");
                }

                for (String error : errors) {
                    if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                        error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                    }
                    toReturn.append("<th style='position: sticky; top: 0;'>" + error + "</th>");
                }

                toReturn.append("<th style='position: sticky; top: 0;'>#</th>");
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        toReturn.append("<th style='position: sticky; top: 0;'>");
                        first = true;
                        for (String heading : headingCol) {
                            if (!first) {
                                toReturn.append("<br/>");
                            }
                            toReturn.append(heading);
                            first = false;
                        }
                        toReturn.append("</th>");
                    }
                    int leftOver = maxLineWidth - uploadedFile.getFileHeadings().size();
                    for (int i = 0; i < leftOver; i++) {
                        toReturn.append("<th></th>");
                    }

                } else {
                    toReturn.append("<th  style='position: sticky; top: 0;' colspan=\"" + maxLineWidth + "\"></th>");
                }
                toReturn.append("</tr></thead><tbody>");

//                todo - add in the identifiers then I can jam them on ignored lines and extract the comments. Since it's stored like so warningLine.getIdentifier().replace("\"", "") it will superficially be safe in HTML.
                if (checkboxId != -1 && !uploadedFile.getWarningLines().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    StringBuilder identifierSb = new StringBuilder();
                    first = true;
                    for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                        if (!first) {
                            sb.append(",");
                            identifierSb.append("||"); // I'm assuming double pipe won't be used in loaded data
                        }
                        sb.append(warningLine.getLineNo());
                        identifierSb.append(warningLine.getIdentifier());
                        first = false;
                    }
                    toReturn.append("<input type=\"hidden\" name=\"" + checkboxId + "-lines\" value=\"" + sb.toString() + "\"/>");
                    toReturn.append("<input type=\"hidden\" name=\"" + checkboxId + "-identifier\" value=\"" + identifierSb.toString() + "\"/>");
                }

                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    toReturn.append("<tr>");
                    if (checkboxId != -1) {
                        toReturn.append("<td><div align=\"center\"><input type=\"checkbox\" name=\"" + checkboxId + "-" + warningLine.getLineNo() + "\" id=\"" + checkboxId + "-" + warningLine.getLineNo() + "\" /></div></td>");
                        toReturn.append("<td nowrap><a href=\"#\" onclick=\"doComment('" + checkboxId + "-" + warningLine.getLineNo() + "'); return false;\">" + (comments != null && comments.contains(warningLine.getIdentifier()) ? "Edit" : "Add Comment") + "</a></td>");
                    }
                    for (String error : errors) {
                        toReturn.append("<td nowrap>" + (warningLine.getErrors().containsKey(error) ? "<span style=\"background-color: #FFAAAA; color: #000000\">" + warningLine.getErrors().get(error) + "</span>" : "") + "</td>");
                    }
                    toReturn.append("<td nowrap>").append(warningLine.getLineNo()).append("</td>");
                    String[] split = warningLine.getLine().split("\t");
                    for (String cell : split) {
                        toReturn.append("<td nowrap>").append(cell).append("</td>");
                    }
                    int leftOver = maxLineWidth - split.length;
                    for (int i = 0; i < leftOver; i++) {
                        toReturn.append("<td nowrap></td>");
                    }
                    toReturn.append("</tr>");
                }


                if (checkboxId != -1) {
                    toReturn.append("<tr>");
                    toReturn.append("<td></td>");
                    toReturn.append("<td nowrap><a href=\"#\" onclick=\"");
                    for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                        toReturn.append("document.getElementById('" + checkboxId + "-" + warningLine.getLineNo() + "').checked = true;");
                    }
                    toReturn.append("return false;\">Select All</a></td>");
                    for (String ignored : errors) {
                        toReturn.append("<td nowrap></td>");
                    }
                    toReturn.append("<td nowrap></td>");
                    for (int i = 0; i < maxLineWidth; i++) {
                        toReturn.append("<td nowrap></td>");
                    }
                    toReturn.append("</tr>");
                }


                toReturn.append("\n</tbody></table></div><br/>");
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getError() != null) {
                if (uploadedFile.getError().toLowerCase().contains("empty sheet")) {
                    toReturn.append("WARNING :").append(uploadedFile.getError()).append("\n<br>");
                } else {
                    errorList.append(updateErrorList(uploadedFile, errorList, uploadedFile.getError()));
                    toReturn.append("ERROR : ").append(uploadedFile.getError()).append("\n<br/>");
                }
            }

            if (!uploadedFile.isDataModified()) {
                if (uploadedFile.isNoData()) {
                    toReturn.append("NO DATA.\n<br/>");
                } else {
                    toReturn.append("NO DATA MODIFIED.\n<br/>");
                }
            }

            if (uploadedFile.getReportName() != null) {
                toReturn.append("Report uploaded : ").append(uploadedFile.getReportName()).append("\n<br/>");
//                toReturn.append("Analysis : ").append(uploadedFile.getE()).append("\n<br/>");
            }

            if (uploadedFile.isImportTemplate()) {
                toReturn.append("Import template uploaded\n<br/>");
            }
        }
        if (errorList.length() > 0) {
            return "<b>" + errorList.toString() + "</b>\n<br/>" + toReturn.toString();
        }
        return toReturn.toString();
    }

    private static String updateErrorList(UploadedFile uploadedFile, StringBuilder errorList, String error) {
        StringBuilder appendString = new StringBuilder();
        if (errorList.length() == 0) {
            appendString.append("ERRORS FOUND : ").append("\n<br/>");
        }
        for (String fileName : uploadedFile.getFileNames()) {
            appendString.append(fileName + ".");
        }
        appendString.append(error).append("\n<br/>");
        return appendString.toString();

    }
}