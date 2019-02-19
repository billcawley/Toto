package com.azquo.admin.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.dataimport.*;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "sort", required = false) String sort
            , @RequestParam(value = "pendingUploadSearch", required = false) String pendingUploadSearch
            , @RequestParam(value = "deleteTemplateId", required = false) String deleteTemplateId
            , @RequestParam(value = "templateassign", required = false) String templateassign
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
                error.append(formatUploadedFiles(importResult, -1, false, null));
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
            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                if (databaseList != null) {
                    for (Database database : databaseList) {
                        boolean isLoaded = AdminService.isDatabaseLoaded(loggedInUser, database);
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
            }
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplayForBusiness = AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, fileSearch);
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
            , @RequestParam(value = "team", required = false) String team
    ) {
        if (database != null) {
            request.getSession().setAttribute("lastSelected", database);
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (uploadFile != null && !uploadFile.isEmpty()) {
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
                            UploadedFile uploadedFile = new UploadedFile(moved.getAbsolutePath(), Collections.singletonList(fileName), new HashMap<>(), false, false);
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
                            boolean assignTemplateToDatabase = false;
                            if (database != null && !database.isEmpty()) {
                                LoginService.switchDatabase(loggedInUser, database); // could be blank now
                                assignTemplateToDatabase = true;
                            }

                            if (!isImportTemplate) {
                                model.put("error", "That does not appear to be an import template.");
                            } else {
                                model.put("error", formatUploadedFiles(Collections.singletonList(ImportService.uploadImportTemplate(uploadedFile, loggedInUser, assignTemplateToDatabase)), -1, false, null));
                            }
                        } catch (Exception e) {
                            model.put("error", e.getMessage());
                        }
                    } else if (database != null) {
                        if (database.isEmpty()) {
                            model.put("error", "Please select a database");
                        } else {
                            // data file or zip. New thing - it can be added to the Pending Uploads manually
                            // todo - security hole here, a developer could hack a file onto a different db by manually editing the database parameter. . .
                            LoginService.switchDatabase(loggedInUser, database); // could be blank now
                            if (team != null) { // Pending uploads
                                String targetPath = SpreadsheetService.getScanDir() + "/tagged/" + System.currentTimeMillis() + uploadFile.getOriginalFilename();
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

                            } else {
                                HttpSession session = request.getSession();
                                String fileName = uploadFile.getOriginalFilename();
                                loggedInUser.userLog("Upload file : " + fileName);
                                // always move uplaoded files now, they'll need to be transferred to the DB server after code split
                                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                                uploadFile.transferTo(moved);
                                return handleImport(loggedInUser, session, model, fileName, moved.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                }
            } else {
                model.put("error", "Please select a file to upload");
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
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("uploads", AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, null));
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, null, request.getParameter("allteams") != null, request.getParameter("uploadedreports") != null));
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
        UploadedFile uploadedFile = new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false, false);
        new Thread(() -> {
            // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
            try {
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, ImportService.importTheFile(loggedInUser, uploadedFile, session, null));
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

    // as it says make something for users to read from a list of uploaded files.
    public static String formatUploadedFiles(List<UploadedFile> uploadedFiles, int checkboxId, boolean noClickableHeadings, Set<String> comments) {
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
            toReturn.append("Number of values adjusted: ").append(uploadedFile.getNoValuesAdjusted()).append("\n<br/>");

            if (uploadedFile.getTopHeadings() != null && !uploadedFile.getTopHeadings().isEmpty()) {
                if (noClickableHeadings) {
                    toReturn.append("Top headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('topHeadings").append(id).append("'); return false;\">Top headings</a> : \n<br/><div id=\"topHeadings").append(id).append("\" style=\"display : none\">");
                }
                for (TypedPair<Integer, Integer> key : uploadedFile.getTopHeadings().keySet()) {
                    toReturn.append(BookUtils.rangeToText(key.getFirst(), key.getSecond())).append("\t->\t").append(uploadedFile.getTopHeadings().get(key)).append("\n<br/>");
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
                if (noClickableHeadings) {
                    toReturn.append("Headings without file headings : \n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('noFileHeadings" + id + "'); return false;\">Headings without file headings</a>\n<br/><div id=\"noFileHeadings" + id + "\" style=\"display : none\">");
                }
                for (TypedPair<String, String> stringStringTypedPair : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
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
                if (noClickableHeadings) {
                    toReturn.append("Line Errors : ").append(uploadedFile.getLinesRejected().size()).append(uploadedFile.getLinesRejected().size() == 100 ? "+" : "").append("\n<br/>");
                } else {
                    toReturn.append("<a href=\"#\" onclick=\"showHideDiv('rejectedLines" + id + "'); return false;\">Line Errors : ").append(uploadedFile.getLinesRejected().size()).append(uploadedFile.getLinesRejected().size() == 100 ? "+" : "").append("</a>\n<br/><div id=\"rejectedLines" + id + "\" style=\"display : none\">");
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
                toReturn.append("\n</tbody></table></div><br/>");
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
                        toReturn.append("<td><div align=\"center\"><input type=\"checkbox\" name=\"" + checkboxId + "-" + warningLine.getLineNo() + "\" /></div></td>");
                        toReturn.append("<td nowrap><a href=\"#\" onclick=\"doComment('" + checkboxId + "-" + warningLine.getLineNo() + "'); return false;\">" + (comments != null && comments.contains(warningLine.getIdentifier()) ? "See Comment(s)" : "Add Comment") + "</a></td>");
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
                toReturn.append("\n</tbody></table></div><br/>");
                if (!noClickableHeadings) {
                    toReturn.append("</div>");
                }
            }

            if (uploadedFile.getError() != null) {
                toReturn.append("ERROR : ").append(uploadedFile.getError()).append("\n<br/>");
            }

            if (!uploadedFile.isDataModified()) {
                if (uploadedFile.isNoData()){
                    toReturn.append("NO DATA.\n<br/>");
                } else {
                    toReturn.append("NO DATA MODIFIED.\n<br/>");
                }
            }

            if (uploadedFile.getReportName() != null) {
                toReturn.append("Report uploaded : ").append(uploadedFile.getReportName()).append("\n<br/>");
            }

            if (uploadedFile.isImportTemplate()) {
                toReturn.append("Import template uploaded\n<br/>");
            }
        }
        return toReturn.toString();
    }
}