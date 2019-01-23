package com.azquo.admin.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.ExcelController;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportRenderer;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
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

    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);
    private static DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

    public static final String IMPORTRESULT = "importResult";
    public static final String IMPORTURLSUFFIX = "importUrlSuffix";
    private static final String IMPORTRESULTPREFIX = "importResultPrefix";

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
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "createDatabase", required = false) String createDatabase
            , @RequestParam(value = "databaseServerId", required = false) String databaseServerId
            , @RequestParam(value = "emptyId", required = false) String emptyId
            , @RequestParam(value = "copyId", required = false) String copyId
            , @RequestParam(value = "checkId", required = false) String checkId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "unloadId", required = false) String unloadId
            , @RequestParam(value = "toggleAutobackup", required = false) String toggleAutobackup
                                // todo - address whether we're still using such parameters and associated functions
            , @RequestParam(value = "fileSearch", required = false) String fileSearch
            , @RequestParam(value = "deleteUploadRecordId", required = false) String deleteUploadRecordId
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "sort", required = false) String sort
            , @RequestParam(value = "pendingUploadSearch", required = false) String pendingUploadSearch
            , @RequestParam(value = "deleteTemplateId", required = false) String deleteTemplateId
    ) {
        LoggedInUser possibleUser = null;
        if (sessionId != null) {
            possibleUser = ExcelController.excelConnections.get(sessionId);
        }
        if (possibleUser == null) {
            possibleUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        }
        // I assume secure until we move to proper spring security
        final LoggedInUser loggedInUser = possibleUser;
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            StringBuilder error = new StringBuilder();
            // EFC - I can't see a way around this one currently. I want to use @SuppressWarnings very sparingly
            @SuppressWarnings("unchecked")
            List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
            if (importResult != null) {
                if (request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULTPREFIX) != null) { // pending, set the pending summary
                    int count = 0;
                    for (UploadedFile uf : importResult){
                        request.getSession().setAttribute("resultCache" + count, formatUploadedFiles(Collections.singletonList(uf), true));
                        count++;
                    }
                    error.append(formatUploadedFilesForPendingUploads(importResult));
                    error.append(request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULTPREFIX)).append("<br/>");
                    request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULTPREFIX);
                } else {
                    error.append(formatUploadedFiles(importResult, false));
                }
                request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULT);
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
                if (NumberUtils.isNumber(copyId)) {
                    Database db = DatabaseDAO.findById(Integer.parseInt(copyId));
                    DatabaseServer databaseServer = DatabaseServerDAO.findById(db.getDatabaseServerId());
                    RMIClient.getServerInterface(databaseServer.getIp()).copyDatabaseTest(loggedInUser.getDatabase().getPersistenceName());
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
            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                for (Database database : databaseList) {
                    boolean isLoaded = AdminService.isDatabaseLoaded(loggedInUser, database);
                    displayDataBases.add(new DisplayDataBase(isLoaded, database));
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }

            // ok, so, for pending uploads we need to know what parameters the user can set
            Map<String, List<String>> paramsMap = new HashMap<>();
            String scanParams = SpreadsheetService.getScanParams();
            if (!scanParams.isEmpty()) {
                StringTokenizer stringTokenizer = new StringTokenizer(scanParams, "|");
                while (stringTokenizer.hasMoreTokens()) {
                    String name = stringTokenizer.nextToken().trim();
                    if (stringTokenizer.hasMoreTokens()) {
                        String list = stringTokenizer.nextToken().trim();
                        List<String> values = new ArrayList<>();
                        StringTokenizer stringTokenizer1 = new StringTokenizer(list, ",");
                        values.add("N/A");
                        while (stringTokenizer1.hasMoreTokens()) {
                            values.add(stringTokenizer1.nextToken().trim());
                        }
                        paramsMap.put(name, values);
                    }
                }
            }
            model.put("params", paramsMap.entrySet()); // no search for the mo
            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("databases", displayDataBases);
            final List<DatabaseServer> allServers = DatabaseServerDAO.findAll();
            model.put("databaseServers", allServers);
            if (allServers.size() == 1) {
                model.put("serverList", false);
            }
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplayForBusiness = AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, fileSearch);
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
            model.put("dbsort", "database".equals(sort) ? "databasedown" : "database");
            model.put("datesort", "date".equals(sort) ? "datedown" : "date");
            model.put("businessnamesort", "businessname".equals(sort) ? "businessnamedown" : "businessname");
            model.put("usernamesort", "username".equals(sort) ? "usernamedown" : "username");
            model.put("uploads", uploadRecordsForDisplayForBusiness);
            AtomicBoolean canCommit = new AtomicBoolean(false);
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, pendingUploadSearch, canCommit)); // no search for the mo
            model.put("commit", canCommit.get());
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            model.put("importTemplates", ImportTemplateDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()));
            AdminService.setBanner(model, loggedInUser);
            return "managedatabases";
        } else {
            return "redirect:/api/Login";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
            , @RequestParam(value = "backup", required = false) String backup
            , @RequestParam(value = "template", required = false) String template
    ) {
        if (database != null) {
            request.getSession().setAttribute("lastSelected", database);
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (uploadFile != null) {
                try {
                    if ("true".equals(backup)) {
                        // duplicated fragment, could maybe be factored
                        String fileName = uploadFile.getOriginalFilename();
                        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                        uploadFile.transferTo(moved);
                        model.put("error", BackupService.loadBackup(loggedInUser, moved, database));
                    } else if ("true".equals(template)) {
                        try {
                            String fileName = uploadFile.getOriginalFilename();
                            File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                            uploadFile.transferTo(moved);
                            Workbook book;
                            UploadedFile uploadedFile = new UploadedFile(moved.getAbsolutePath(), Collections.singletonList(fileName), new HashMap<>(), false);
                            FileInputStream fs = new FileInputStream(new File(uploadedFile.getPath()));
                            if (uploadedFile.getFileName().endsWith("xlsx")) {
                                OPCPackage opcPackage = OPCPackage.open(fs);
                                book = new XSSFWorkbook(opcPackage);
                            } else {
                                book = new HSSFWorkbook(fs);
                            }
                            // detect an import template by a sheet name
                            boolean isImportTemplate = false;
                            for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                                Sheet sheet = book.getSheetAt(sheetNo);
                                if (sheet.getSheetName().equalsIgnoreCase("Import Model")) {
                                    isImportTemplate = true;
                                    break;
                                }
                            }
                            //detect from workbook name
                            if (uploadedFile.getFileName().toLowerCase().endsWith("import templates.xlsx")) {
                                isImportTemplate = true;
                            }
                            // detect Ben Jones contract style template

                            Name importName = BookUtils.getName(book, ReportRenderer.AZIMPORTNAME);
                            if (importName != null) {
                                isImportTemplate = true;
                            }
                            if (!isImportTemplate) {
                                model.put("error", "That does not appear to be an import template.");
                            } else {
                                model.put("error", formatUploadedFiles(Collections.singletonList(ImportService.uploadImportTemplate(uploadedFile, loggedInUser)), false));
                            }
                        } catch (Exception e) {
                            model.put("error", e.getMessage());
                        }
                    } else if (database != null) {
                        if (database.isEmpty()) {
                            model.put("error", "Please select a database");
                        } else if (uploadFile.isEmpty()) {
                            model.put("error", "Please select a file to upload");
                        } else {
                            HttpSession session = request.getSession();
                            // todo - security hole here, a developer could hack a file onto a different db by manually editing the database parameter. . .
                            LoginService.switchDatabase(loggedInUser, database); // could be blank now
                            String fileName = uploadFile.getOriginalFilename();
                            loggedInUser.userLog("Upload file : " + fileName);
                            // always move uplaoded files now, they'll need to be transferred to the DB server after code split
                            File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                            uploadFile.transferTo(moved);
                            return handleImport(loggedInUser, session, model, fileName, moved.getAbsolutePath());
                        }
                    }
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                }
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
            model.put("databases", displayDataBases);
            final List<DatabaseServer> allServers = DatabaseServerDAO.findAll();
            if (allServers.size() > 1) {
                model.put("databaseServers", allServers);
            } else {
                model.put("serverList", false);
            }
            // todo factor
            // ok, so, for pending uploads we need to know what parameters the user can set
            Map<String, List<String>> paramsMap = new HashMap<>();
            String scanParams = SpreadsheetService.getScanParams();
            if (!scanParams.isEmpty()) {
                StringTokenizer stringTokenizer = new StringTokenizer(scanParams, "|");
                while (stringTokenizer.hasMoreTokens()) {
                    String name = stringTokenizer.nextToken().trim();
                    if (stringTokenizer.hasMoreTokens()) {
                        String list = stringTokenizer.nextToken().trim();
                        List<String> values = new ArrayList<>();
                        values.add("N/A");
                        StringTokenizer stringTokenizer1 = new StringTokenizer(list, ",");
                        while (stringTokenizer1.hasMoreTokens()) {
                            values.add(stringTokenizer1.nextToken().trim());
                        }
                        paramsMap.put(name, values);
                    }
                }
            }
            model.put("params", paramsMap.entrySet()); // no search for the mo
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("uploads", AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, null));
            AtomicBoolean canCommit = new AtomicBoolean(false);
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, null, canCommit));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            model.put("importTemplates", ImportTemplateDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()));
            AdminService.setBanner(model, loggedInUser);
            return "managedatabases";
        } else {
            return "redirect:/api/Login";
        }
    }

    // factored due to pending uploads, need to check the factoring after the prototype is done
    private static String handleImport(LoggedInUser loggedInUser, HttpSession session, ModelMap model, String fileName, String filePath) {
        // need to add in code similar to report loading to give feedback on imports
        final Map<String, String> fileNameParams = new HashMap<>();
        ImportService.addFileNameParametersToMap(fileName, fileNameParams);
        UploadedFile uploadedFile = new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false);
        new Thread(() -> {
            // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
            try {
/*                String result = ImportService.formatUploadedFiles(
                        ImportService.importTheFile(loggedInUser, new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false))
                ).replace("\n", "<br/>");*/
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, ImportService.importTheFile(loggedInUser, uploadedFile));
            } catch (Exception e) {
                e.printStackTrace();
                uploadedFile.setError(CommonReportUtils.getErrorFromServerSideException(e));
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, Collections.singletonList(uploadedFile));
            }
        }).start();
        // edd pasting in here to get the banner colour working
        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
        String bannerColor = business.getBannerColor();
        if (bannerColor == null || bannerColor.length() == 0) bannerColor = "#F58030";
        String logo = business.getLogo();
        if (logo == null || logo.length() == 0) logo = "logo_alt.png";
        model.addAttribute("bannerColor", bannerColor);
        model.addAttribute("logo", logo);
        return "importrunning";
    }

    // todo - get the "view" (HTML) out of here . . .
    // A table which is more readable
    public static String formatUploadedFilesForPendingUploads(List<UploadedFile> uploadedFiles) {
        StringBuilder toReturn = new StringBuilder();
        toReturn.append("<table><thead>");
        toReturn.append("<tr><td>File Name</td><td>Initial Import Result</td><td>Validation Result</td><td>Details</td></tr></thead>");
        int count = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            toReturn.append("<tr>");
            toReturn.append("<td>");
            for (int index = 0; index < uploadedFile.getFileNames().size(); index++) {
                if (index > 0){
                    toReturn.append(" -> ");
                }
                toReturn.append(uploadedFile.getFileNames().get(index));
            }
            toReturn.append("</td>");
            toReturn.append("<td>");
            if (uploadedFile.getError() != null) {
                toReturn.append("<span style=\"background-color: #FF8888; color: #000000\">Error : ").append(uploadedFile.getError()).append("</span>");
            } else if (!uploadedFile.isDataModified()) {
                toReturn.append("<span style=\"background-color: #FF8888; color: #000000\">No data modified.</span>");
            } else if (uploadedFile.getLinesRejected() != null && !uploadedFile.getLinesRejected().isEmpty()) {
                toReturn.append("Line Errors : ").append(uploadedFile.getLinesRejected().size());
            } else {
                toReturn.append("<span style=\"background-color: #88FF88; color: #000000\">Success.</span>");
            }
            toReturn.append("</td>");
            toReturn.append("<td>");
            if (uploadedFile.getPostProcessingResult() != null && !uploadedFile.getPostProcessingResult().isEmpty()){
                toReturn.append(uploadedFile.getPostProcessingResult());
            } else {
                toReturn.append("N/A");
            }
            toReturn.append("</td>");
            toReturn.append("<td>");
            toReturn.append("<a href=\"/api/ImportResults?count=" + count + "\" target=\"new\" class=\"button inspect small\" data-title=\"Import Results\" title=\"View Import Results\">");
            toReturn.append("<div align=\"center\">View</div></a>");
            toReturn.append("</td>");
            toReturn.append("</tr>");
            count++;
        }
        toReturn.append("</table>");
        return toReturn.toString();
    }

    // as it says make something for users to read from a list of uploaded files.

    public static String formatUploadedFiles(List<UploadedFile> uploadedFiles, boolean noClickableHeadings) {
        StringBuilder toReturn = new StringBuilder();
        List<String> lastNames = null;
        int counter = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            counter++;
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            int indent = 0;
            if (lastNames != null && (names.size() == lastNames.size() && names.get(0).equals(lastNames.get(0)))) { // for formatting don't repeat names (e.g. the zip name)
                for (int index = 0; index < lastNames.size(); index++) {
                    if (lastNames.get(index).equals(names.get(index))) {
                        indent++;
                    }
                }
            }
            for (int i = 0; i < indent; i++) {
                toReturn.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
            }
            for (int index = indent; index < names.size(); index++) {
                toReturn.append(names.get(index));
                if ((index + 1) != names.size()) {
                    toReturn.append("\n<br/>");
                    for (int i = 0; i <= index; i++) {
                        toReturn.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    }
                }
            }
            StringBuilder indentSb = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                indentSb.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
            }
            // jump it out past the end for actual info
            toReturn.append("\n<br/>");

            for (String key : uploadedFile.getParameters().keySet()) {
                toReturn.append(indentSb);
                toReturn.append(key).append(" = ").append(uploadedFile.getParameter(key)).append("\n<br/>");
            }

            if (uploadedFile.isConvertedFromWorksheet()) {
                toReturn.append(indentSb);
                toReturn.append("Converted from worksheet.\n<br/>");
            }

            if (uploadedFile.getTopHeadings() != null && !uploadedFile.getTopHeadings().isEmpty()) {
                toReturn.append(indentSb);
                if (noClickableHeadings) {
                    toReturn.append("Top headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('topHeadings" + counter + "'); return false;\">Top headings</a> : \n<br/><div id=\"topHeadings" + counter + "\" style=\"display : none\">");
                }
                for (TypedPair<Integer, Integer> key : uploadedFile.getTopHeadings().keySet()) {
                    toReturn.append(indentSb).append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    toReturn.append(BookUtils.rangeToText(key.getFirst(), key.getSecond())).append("\t->\t").append(uploadedFile.getTopHeadings().get(key)).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) {
                toReturn.append(indentSb);
                if (noClickableHeadings) {
                    toReturn.append("Headings with file headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('fileHeadings" + counter + "'); return false;\">Headings with file headings</a> : \n<br/><div id=\"fileHeadings" + counter + "\" style=\"display : none\">");
                }

                Collection<List<String>> toShow;
                if (uploadedFile.getFileHeadings() != null) {
                    toShow = uploadedFile.getFileHeadings();
                } else {
                    toShow = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().keySet();
                }

                for (List<String> fileHeading : toShow) {
                    toReturn.append(indentSb).append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    for (String subHeading : fileHeading) {
                        toReturn.append(subHeading.replaceAll("\n", " ")).append(" ");
                    }
                    TypedPair<String, String> stringStringTypedPair = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                    if (stringStringTypedPair != null) {
                        if (stringStringTypedPair.getSecond() != null) {
                            toReturn.append("\t->\t").append(stringStringTypedPair.getSecond().replaceAll("\n", " "));
                        }
                        toReturn.append("\t->\t").append(stringStringTypedPair.getFirst().replaceAll("\n", " ")).append("\n<br/>");
                    } else {
                        toReturn.append("\t->\t").append(" ** UNUSED ** \n<br/>");
                    }
                }

                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null) {
                toReturn.append(indentSb);
                if (noClickableHeadings) {
                    toReturn.append("Headings without file headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('noFileHeadings" + counter + "'); return false;\">Headings without file headings</a> : \n<br/><div id=\"noFileHeadings" + counter + "\" style=\"display : none\">");
                }
                for (TypedPair<String, String> stringStringTypedPair : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
                    toReturn.append(indentSb).append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    if (stringStringTypedPair.getSecond() != null) { // it could be null now as we support a non file heading azquo heading on the Import Model sheet
                        toReturn.append(stringStringTypedPair.getSecond());
                        toReturn.append(" -> ");
                    }
                    toReturn.append(stringStringTypedPair.getFirst()).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getSimpleHeadings() != null) {
                toReturn.append(indentSb);
                if (noClickableHeadings) {
                    toReturn.append("Simple Headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('simpleHeadings" + counter + "'); return false;\">Simple Headings</a> : \n<br/><div id=\"simpleHeadings" + counter + "\" style=\"display : none\">");
                }

                for (String heading : uploadedFile.getSimpleHeadings()) {
                    toReturn.append(indentSb).append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    ;
                    toReturn.append(heading).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            toReturn.append(indentSb);
            toReturn.append("Time to process : ").append(uploadedFile.getProcessingDuration()).append(" ms\n<br/>");

            toReturn.append(indentSb);
            toReturn.append("Number of lines imported : ").append(uploadedFile.getNoLinesImported()).append("\n<br/>");

            toReturn.append(indentSb);
            toReturn.append("Number of values adjusted: ").append(uploadedFile.getNoValuesAdjusted()).append("\n<br/>");

            if (uploadedFile.getLinesRejected() != null && !uploadedFile.getLinesRejected().isEmpty()) {
                toReturn.append(indentSb);
                if (noClickableHeadings) {
                    toReturn.append("Line Errors : ").append(uploadedFile.getLinesRejected().size()).append(" : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('rejectedLines" + counter + "'); return false;\">Line Errors : ").append(uploadedFile.getLinesRejected().size()).append("</a> : \n<br/><div id=\"rejectedLines" + counter + "\" style=\"display : none\">");
                }
                for (String lineRejected : uploadedFile.getLinesRejected()) {
                    toReturn.append(indentSb);
                    toReturn.append(lineRejected).append("\n<br/>");
                }
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getError() != null) {
                toReturn.append(indentSb);
                toReturn.append("ERROR : ").append(uploadedFile.getError()).append("\n<br/>");
            }

            if (!uploadedFile.isDataModified()) {
                toReturn.append(indentSb);
                toReturn.append("NO DATA MODIFIED.\n<br/>");
            }

            if (uploadedFile.getReportName() != null) {
                toReturn.append(indentSb);
                toReturn.append("Report uploaded : ").append(uploadedFile.getReportName()).append("\n<br/>");
            }

            if (uploadedFile.isImportTemplate()) {
                toReturn.append(indentSb);
                toReturn.append("Import template uploaded\n<br/>");
            }

            if (uploadedFile.getPostProcessingResult() != null) {
                toReturn.append(indentSb);
                toReturn.append("Post processing result : ").append(uploadedFile.getPostProcessingResult()).append("\n<br/>");
            }
            lastNames = uploadedFile.getFileNames();
        }
        return toReturn.toString();
    }
}