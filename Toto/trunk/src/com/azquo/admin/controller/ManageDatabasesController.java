package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.ExcelController;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.CommonReportUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
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
 *
 * pending uploads should perhaps be taken out of here
 */
@Controller
@RequestMapping("/ManageDatabases")
public class ManageDatabasesController {

    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);
    private static DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

    public static final String IMPORTRESULT = "importResult";
    public static final String IMPORTURLSUFFIX = "importUrlSuffix";
    private static final String IMPORTRESULTPREFIX = "importResultPrefix";
    private static final String PENDINGUPLOADID = "pendingUploadId";

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
            , @RequestParam(value = "checkId", required = false) String checkId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "unloadId", required = false) String unloadId
            , @RequestParam(value = "toggleAutobackup", required = false) String toggleAutobackup
                                // todo - address whether we're still using such parameters and associated functions
            , @RequestParam(value = "fileSearch", required = false) String fileSearch
            , @RequestParam(value = "deleteUploadRecordId", required = false) String deleteUploadRecordId
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "sort", required = false) String sort
            , @RequestParam(value = "pendingUploadId", required = false) String pendingUploadId
            , @RequestParam(value = "pendingUploadSearch", required = false) String pendingUploadSearch
            , @RequestParam(value = "rejectId", required = false) String rejectId
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "revert", required = false) String revert
            , @RequestParam(value = "commit", required = false) String commit
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
            if ("true".equalsIgnoreCase(revert)) {
                for (PendingUpload pendingUpload : PendingUploadDAO.findForBusinessIdAndComitted(loggedInUser.getUser().getBusinessId(), false)) {
                    pendingUpload.setStatusChangedDate(LocalDateTime.now());
                    pendingUpload.setUserId(loggedInUser.getUser().getId());
                    pendingUpload.setStatus(PendingUpload.WAITING);
                    PendingUploadDAO.store(pendingUpload);
                }
                // now restore and delete backups
                Path backups = Paths.get(SpreadsheetService.getScanDir() + "/dbbackups");
                try {
                    if (Files.exists(backups)) {
                        try (Stream<Path> list = Files.list(backups)) {
                            list.forEach(path -> {
                                Database forPersistenceName = DatabaseDAO.findForPersistenceName(path.getFileName().toString());
                                if (forPersistenceName != null) { // then restore
                                    loggedInUser.setDatabaseWithServer(DatabaseServerDAO.findById(forPersistenceName.getDatabaseServerId()), forPersistenceName);
                                    BackupService.loadDBBackup(loggedInUser, path.toFile(), null, new StringBuilder(), true);
                                }
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if ("true".equalsIgnoreCase(commit)) {
                for (PendingUpload pendingUpload : PendingUploadDAO.findForBusinessIdAndComitted(loggedInUser.getUser().getBusinessId(), false)) {
                    if (!pendingUpload.getStatus().equals(PendingUpload.WAITING)) {
                        pendingUpload.setStatusChangedDate(LocalDateTime.now());
                        pendingUpload.setUserId(loggedInUser.getUser().getId());
                        pendingUpload.setCommitted(true);
                        PendingUploadDAO.store(pendingUpload);
                    }
                }
                // now restore and delete backups
                Path backups = Paths.get(SpreadsheetService.getScanDir() + "/dbbackups");
                try {
                    if (Files.exists(backups)) {
                        try (Stream<Path> list = Files.list(backups)) {
                            list.forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // can a developer do pending?? todo
            if (NumberUtils.isNumber(pendingUploadId) && NumberUtils.isNumber(databaseId)) {
                PendingUpload pendingUpload = PendingUploadDAO.findById(Integer.parseInt(pendingUploadId));
                if (pendingUpload.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                    System.out.println("pending upload id " + pendingUploadId);
                    System.out.println("database " + databaseId);
                    Enumeration<String> params = request.getParameterNames();
                    Map<String, String> paramsFromUser = new HashMap<>();
                    while (params.hasMoreElements()) {
                        String param = params.nextElement();
                        if (param.startsWith("pendingupload-")) {
                            System.out.println("param : " + param.substring("pendingupload-".length()) + " value : " + request.getParameter(param));
                            paramsFromUser.put(param.substring("pendingupload-".length()).toLowerCase().trim(), request.getParameter(param)); // lower case important, it's the convention when grabbing from the file name
                        }
                    }
                    // todo - security hole here, a developer could hack a file onto a different db by manually editing the database parameter. . .
                    Database byId = DatabaseDAO.findById(Integer.parseInt(databaseId));
                    if (byId != null) {
                        LoginService.switchDatabase(loggedInUser, byId);
                        // todo backup the db for a revert if it hasn't been already
                        Path backups = Paths.get(SpreadsheetService.getScanDir() + "/dbbackups");
                        try {
                            if (!Files.exists(backups)) {
                                Files.createDirectories(backups);
                            }
                            // now, is there a backup already?
                            Path backupFile = backups.resolve(byId.getPersistenceName());
                            if (!Files.exists(backupFile)) {
                                BackupService.createDBBackupFile(loggedInUser.getDatabase().getName(), loggedInUser.getDataAccessToken(), backupFile.toString(), loggedInUser.getDatabaseServer().getIp());
                                request.getSession().setAttribute(IMPORTRESULTPREFIX, "** Created revert backup for database " + byId.getName() + " **");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        request.getSession().setAttribute(IMPORTURLSUFFIX, "#tab4");
                        request.getSession().setAttribute(PENDINGUPLOADID, pendingUpload.getId());
                        pendingUpload.setParameters(paramsFromUser);
                        pendingUpload.setDatabaseId(byId.getId());
                        pendingUpload.setStatusChangedDate(LocalDateTime.now());
                        pendingUpload.setUserId(loggedInUser.getUser().getId());
                        PendingUploadDAO.store(pendingUpload);
                        return handleImport(loggedInUser, request.getSession(), model, pendingUpload.getFileName(), pendingUpload.getFilePath(), paramsFromUser);
                    }
                }
            }
            if (NumberUtils.isNumber(rejectId)) {
                PendingUpload pendingUpload = PendingUploadDAO.findById(Integer.parseInt(rejectId));
                if (pendingUpload.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                    pendingUpload.setStatus(PendingUpload.REJECTED);
                    pendingUpload.setStatusChangedDate(LocalDateTime.now());
                    pendingUpload.setUserId(loggedInUser.getUser().getId());
                    PendingUploadDAO.store(pendingUpload);
                }
            }
            StringBuilder error = new StringBuilder();
            // EFC - I can't see a way around this one currently. I want to use @SuppressWarnings very sparingly
            @SuppressWarnings("unchecked")
            List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
            if (importResult != null) {
                if (request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULTPREFIX) != null) {
                    error.append(request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULTPREFIX)).append("<br/>");
                    request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULTPREFIX);
                }
                error.append(formatUploadedFiles(importResult));
                if (request.getSession().getAttribute(ManageDatabasesController.PENDINGUPLOADID) != null) {
                    int pendingUploadImportedId = (Integer) request.getSession().getAttribute(ManageDatabasesController.PENDINGUPLOADID);
                    PendingUpload pendingUpload = PendingUploadDAO.findById(pendingUploadImportedId);
                    if (pendingUpload != null) {
                        pendingUpload.setImportResult(formatUploadedFiles(importResult));// should html be in here?
                        boolean modified = false;
                        boolean errorInResult = false;
                        for (UploadedFile uploadedFile : importResult){
                            if (uploadedFile.isDataModified()){
                                modified = true;
                            }
                            if (uploadedFile.getError() != null){
                                errorInResult = true;
                            }
                        }
                        if (!modified || errorInResult) {
                            if (!modified) {
                                error.append("<span style=\"background-color: #FF8888; color: #000000\">** Marked as rejected as no data was modified **</span><br/>");
                            }
                            if (errorInResult) {
                                error.append("<span style=\"background-color: #FF8888; color: #000000\">** Marked as rejected due to an error. Depending on the error data may have modified **</span><br/>");
                            }
                            pendingUpload.setStatus(PendingUpload.REJECTED);
                        } else {
                            pendingUpload.setStatus(PendingUpload.PROVISIONALLY_LOADED);
                        }
                        pendingUpload.setStatusChangedDate(LocalDateTime.now());
                        pendingUpload.setUserId(loggedInUser.getUser().getId());
                        PendingUploadDAO.store(pendingUpload);
                    }
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
                if (NumberUtils.isNumber(checkId)) {
                    AdminService.checkDatabaseByIdWithBasicSecurity(loggedInUser, Integer.parseInt(checkId));
                }
                if (NumberUtils.isNumber(deleteId)) {
                    AdminService.removeDatabaseByIdWithBasicSecurity(loggedInUser, Integer.parseInt(deleteId));
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
/*                    if (isLoaded && (AdminService.getNameCountWithBasicSecurity(loggedInUser, database) != database.getNameCountWithBasicSecurity()
                            || AdminService.getValueCountWithBasicSecurity(loggedInUser, database) != database.getValueCountWithBasicSecurity())) { // then update the counts
                        database.setNameCount(AdminService.getNameCountWithBasicSecurity(loggedInUser, database));
                        database.setValueCount(AdminService.getValueCountWithBasicSecurity(loggedInUser, database));
                        DatabaseDAO.store(database);
                    }*/
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
                        while (stringTokenizer1.hasMoreTokens()) {
                            values.add(stringTokenizer1.nextToken().trim());
                        }
                        paramsMap.put(name, values);
                    }
                }
            }
            model.put("params", paramsMap.entrySet()); // no search for the mo
            // ok revert info - what backups there are and what date they're from
            Path backups = Paths.get(SpreadsheetService.getScanDir() + "/dbbackups");
            StringBuilder stringBuilder = new StringBuilder();
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.UK)
                            .withZone(ZoneId.systemDefault());
            if (Files.exists(backups)) {
                try (Stream<Path> list = Files.list(backups)) {
                    list.forEach(path -> {
                        stringBuilder.append("\\n");
                        stringBuilder.append(path.getFileName());
                        stringBuilder.append(" - ");
                        try {
                            stringBuilder.append(formatter.format(Files.getLastModifiedTime(path).toInstant()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            model.put("revertlist", stringBuilder.toString());

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
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o1.getDatabaseName().compareTo(o2.getDatabaseName())));
            }
            if ("databasedown".equals(sort)) {
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getDatabaseName().compareTo(o1.getDatabaseName())));
            }
            if ("date".equals(sort)) {
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o1.getDate().compareTo(o2.getDate())));
            }
            if ("datedown".equals(sort)) {
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getDate().compareTo(o1.getDate())));
            }
            if ("businessname".equals(sort)) {
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o1.getBusinessName().compareTo(o2.getBusinessName())));
            }
            if ("businessnamedown".equals(sort)) {
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getBusinessName().compareTo(o1.getBusinessName())));
            }
            if ("username".equals(sort)) {
                uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o1.getUserName().compareTo(o2.getUserName())));
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
                        // todo - like a normal import give feedback to the user . . .
                        model.put("error", BackupService.loadBackup(loggedInUser, moved, database));
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
                            return handleImport(loggedInUser, session, model, fileName, moved.getAbsolutePath(), null);
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
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("uploads", AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, null));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            AdminService.setBanner(model, loggedInUser);
            return "managedatabases";
        } else {
            return "redirect:/api/Login";
        }
    }

    // factored due to pending uploads, need to check the factoring after the prototype is done
    private static String handleImport(LoggedInUser loggedInUser, HttpSession session, ModelMap model, String fileName, String filePath, final Map<String, String> paramsFromUser) {
        // need to add in code similar to report loading to give feedback on imports
        final Map<String, String> fileNameParams;
        if (paramsFromUser == null) {
            fileNameParams = new HashMap<>();
            ImportService.addFileNameParametersToMap(fileName, fileNameParams);
        } else {
            fileNameParams = paramsFromUser;
        }
        UploadedFile uploadedFile = new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false);
        new Thread(() -> {
            // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
            try {
/*                String result = ImportService.formatUploadedFiles(
                        ImportService.importTheFile(loggedInUser, new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false))
                ).replace("\n", "<br/>");*/
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, ImportService.importTheFile(loggedInUser, uploadedFile));
            } catch (Exception e) {
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

    public static String formatUploadedFiles(List<UploadedFile> uploadedFiles) {
        StringBuilder toReturn = new StringBuilder();
        List<String> lastNames = null;
        for (UploadedFile uploadedFile : uploadedFiles) {
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            int indent = 0;
            if (lastNames != null && names.size() == lastNames.size()) { // for formatting don't repeat names (e.g. the zip name)
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

            if (uploadedFile.getParameters() != null && !uploadedFile.getParameters().isEmpty()) {
                for (String key : uploadedFile.getParameters().keySet()) {
                    toReturn.append(indentSb);
                    toReturn.append(key).append(" = ").append(uploadedFile.getParameter(key)).append("\n<br/>");
                }
            }

            if (uploadedFile.isConvertedFromWorksheet()) {
                toReturn.append(indentSb);
                toReturn.append("Converted from worksheet.\n<br/>");
            }

            toReturn.append(indentSb);
            toReturn.append("Time to process : ").append(uploadedFile.getProcessingDuration()).append(" ms\n<br/>");

            toReturn.append(indentSb);
            toReturn.append("Number of lines imported : ").append(uploadedFile.getNoLinesImported()).append("\n<br/>");

            toReturn.append(indentSb);
            toReturn.append("Number of values adjusted: ").append(uploadedFile.getNoValuesAdjusted()).append("\n<br/>");

            if (uploadedFile.getLinesRejected() != null && !uploadedFile.getLinesRejected().isEmpty()) {
                toReturn.append(indentSb);
                toReturn.append("Rejected lines : ").append(uploadedFile.getLinesRejected().size()).append("\n<br/>");
                for (String lineRejected : uploadedFile.getLinesRejected()) {
                    toReturn.append(indentSb);
                    toReturn.append(lineRejected).append("\n<br/>");
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

            if (uploadedFile.getExecute() != null) {
                toReturn.append(indentSb);
                toReturn.append("Execute result : ").append(uploadedFile.getExecute()).append("\n<br/>");
            }
            lastNames = uploadedFile.getFileNames();
        }
        return toReturn.toString();
    }
}