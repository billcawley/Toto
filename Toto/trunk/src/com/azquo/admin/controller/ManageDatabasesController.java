package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
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
import javax.swing.text.DateFormatter;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * New HTML admin, upload files and manage databases
 * <p>
 * CRUD type stuff though databases will be created/deleted etc. server side.
 */
@Controller
@RequestMapping("/ManageDatabases")
public class ManageDatabasesController {

    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);
    private static DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

    // to play nice with velocity or JSP - so I don't want it to be private as Intellij suggests
    public static class DisplayDataBase {
        private final boolean loaded;
        private final Database database;

        public DisplayDataBase(boolean loaded, Database database) {
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

        public String getDatabaseType() {
            return database.getDatabaseType();
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


        public String getCreated() {
            return format.format(database.getCreated());
        }
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "createDatabase", required = false) String createDatabase
            , @RequestParam(value = "databaseType", required = false) String databaseType
            , @RequestParam(value = "databaseServerId", required = false) String databaseServerId
            , @RequestParam(value = "emptyId", required = false) String emptyId
            , @RequestParam(value = "checkId", required = false) String checkId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "unloadId", required = false) String unloadId
                                // todo - address whether we're still using such parameters and associated functions
            , @RequestParam(value = "backupTarget", required = false) String backupTarget
            , @RequestParam(value = "summaryLevel", required = false) String summaryLevel
            , @RequestParam(value = "fileSearch", required = false) String fileSearch
            , @RequestParam(value = "deleteUploadRecordId", required = false) String deleteUploadRecordId
            , @RequestParam(value = "uploadAnyway", required = false) String uploadAnyway
            , @RequestParam(value = "sessionid", required = false) String sessionId
    )

    {
        LoggedInUser possibleUser = null;
        if (sessionId != null){
            possibleUser = ExcelController.excelConnections.get(sessionId);
        }
        if (possibleUser == null){
            possibleUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        }
        // I assume secure until we move to proper spring security
        final LoggedInUser loggedInUser = possibleUser;
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (uploadAnyway != null){
                // todo - factor?
                try{
                    UploadRecord ur = UploadRecordDAO.findById(Integer.parseInt(uploadAnyway));
                    if (ur != null && ur.getUserId() == loggedInUser.getUser().getId()){
                        HttpSession session = request.getSession();
                        new Thread(() -> {
                            // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
                            try {
                                List<String> languages = new ArrayList<>(loggedInUser.getLanguages());
                                languages.remove(loggedInUser.getUser().getEmail());
                                session.setAttribute("importResult",
                                        ImportService.importTheFile(loggedInUser, ur.getFileName(), ur.getTempPath(), languages, false, false, true)
                                );
                            } catch (Exception e) {
                                session.setAttribute("importResult", CommonReportUtils.getErrorFromServerSideException(e));
                            }
                        }).start();
                        return "importrunning";
                    }
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                }
            }
            StringBuilder error = new StringBuilder();
            if (request.getSession().getAttribute("importResult") != null) {
                error.append(request.getSession().getAttribute("importResult"));
                request.getSession().removeAttribute("importResult");
            }
            try {
                final List<DatabaseServer> allServers = DatabaseServerDAO.findAll();
                if (createDatabase != null && !createDatabase.isEmpty() && (allServers.size() == 1 || (databaseServerId != null && !databaseServerId.isEmpty()))) {
                    if (allServers.size() == 1) {
                        AdminService.createDatabase(createDatabase, databaseType, loggedInUser, allServers.get(0));
                    } else {
                        AdminService.createDatabase(createDatabase, databaseType, loggedInUser, DatabaseServerDAO.findById(Integer.parseInt(databaseServerId)));
                    }
                }
                if (emptyId != null && NumberUtils.isNumber(emptyId)) {
                    AdminService.emptyDatabaseById(loggedInUser, Integer.parseInt(emptyId));
                }
                if (checkId != null && NumberUtils.isNumber(checkId)) {
                    AdminService.checkDatabaseById(loggedInUser, Integer.parseInt(checkId));
                }
                if (deleteId != null && NumberUtils.isNumber(deleteId)) {
                    AdminService.removeDatabaseById(loggedInUser, Integer.parseInt(deleteId));
                }
                if (unloadId != null && NumberUtils.isNumber(unloadId)) {
                    AdminService.unloadDatabase(loggedInUser, Integer.parseInt(unloadId));
                }
                if (backupTarget != null) {
                    LoggedInUser loggedInUserTarget = LoginService.loginLoggedInUser(request.getSession().getId(), backupTarget, loggedInUser.getUser().getEmail(), "", true); // targetted to destinationDB
                    AdminService.copyDatabase(loggedInUser.getDataAccessToken(), loggedInUserTarget.getDataAccessToken(), summaryLevel, loggedInUserTarget.getLanguages());// re languages I should just be followign what was there before . . .
                }
                if (deleteUploadRecordId != null && NumberUtils.isNumber(deleteUploadRecordId)) {
                    AdminService.deleteUploadRecord(loggedInUser, Integer.parseInt(deleteUploadRecordId));
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                model.put("error", error.toString());
            }
            List<Database> databaseList = AdminService.getDatabaseListForBusiness(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                for (Database database : databaseList) {
                    boolean isLoaded = AdminService.isDatabaseLoaded(loggedInUser, database);
                    displayDataBases.add(new DisplayDataBase(isLoaded, database));
/*                    if (isLoaded && (AdminService.getNameCount(loggedInUser, database) != database.getNameCount()
                            || AdminService.getValueCount(loggedInUser, database) != database.getValueCount())) { // then update the counts
                        database.setNameCount(AdminService.getNameCount(loggedInUser, database));
                        database.setValueCount(AdminService.getValueCount(loggedInUser, database));
                        DatabaseDAO.store(database);
                    }*/
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
            if (allServers.size() > 1) {
                model.put("databaseServers", allServers);
            } else {
                model.put("serverList", false);
            }
            model.put("uploads", AdminService.getUploadRecordsForDisplayForBusiness(loggedInUser, fileSearch));
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            AdminService.setBanner(model,loggedInUser);
            return "managedatabases";
        } else {
            return "redirect:/api/Login";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
            , @RequestParam(value = "setup", required = false) String setup
    ) {
        if (database != null) {
            request.getSession().setAttribute("lastSelected", database);
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (database != null && uploadFile != null) {
                if (database.isEmpty()) {
                    model.put("error", "Please select a database");
                } else if (uploadFile.isEmpty()) {
                    model.put("error", "Please select a file to upload");
                } else {
                    try {
                        HttpSession session = request.getSession();
                        // todo - security hole here, a developer could hack a file onto a different db . . .
                        LoginService.switchDatabase(loggedInUser, database); // could be blank now
                        String fileName = uploadFile.getOriginalFilename();
                        loggedInUser.userLog("Upload file : " + fileName);
                        // always move uplaoded files now, they'll need to be transferred to the DB server after code split
                        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                        uploadFile.transferTo(moved);
                        // if flagged as setup we simply park the file to be reloaded each time regardless of checking it
                        if ("true".equals(setup)){
                            String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : "";
                            Path fullPath = Paths.get(SpreadsheetService.getHomeDir() +  ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.databaseSetupSheetsDir + "Setup" + loggedInUser.getDatabase().getName() + extension);
                            Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
                            Files.copy(Paths.get(moved.getPath()), fullPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        // need to add in code similar to report loading to give feedback on imports
                        new Thread(() -> {
                            // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
                            try {
                                List<String> languages = new ArrayList<>(loggedInUser.getLanguages());
                                languages.remove(loggedInUser.getUser().getEmail());
                                session.setAttribute("importResult",
                                        ImportService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), languages, false).replace("\n","<br/>")
                                );
                            } catch (Exception e) {
                                session.setAttribute("importResult", CommonReportUtils.getErrorFromServerSideException(e));
                            }
                        }).start();
                        return "importrunning";
                    } catch (Exception e) { // now the import has it's on exception catching
                        String exceptionError = e.getMessage();
                        e.printStackTrace();
                        model.put("error", exceptionError);
                    }
                }
            }
            List<Database> databaseList = AdminService.getDatabaseListForBusiness(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                for (Database database1 : databaseList) {
                    boolean isLoaded = AdminService.isDatabaseLoaded(loggedInUser, database1);
                    displayDataBases.add(new DisplayDataBase(isLoaded, database1));
/*                    if (isLoaded && (AdminService.getNameCount(loggedInUser, database1) != database1.getNameCount()
                            || AdminService.getValueCount(loggedInUser, database1) != database1.getValueCount())) { // then update the counts
                        database1.setNameCount(AdminService.getNameCount(loggedInUser, database1));
                        database1.setValueCount(AdminService.getValueCount(loggedInUser, database1));
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
            model.put("uploads", AdminService.getUploadRecordsForDisplayForBusiness(loggedInUser, null));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            AdminService.setBanner(model,loggedInUser);
            return "managedatabases";
        } else {
            return "redirect:/api/Login";

        }
    }
}