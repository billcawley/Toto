package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by cawley on 24/04/15.
 *
 * New HTML admin, upload files and manage databases
 *
 * CRUD type stuff though databases will be created/deleted etc. server side.
 */
@Controller
@RequestMapping("/ManageDatabases")
public class ManageDatabasesController {
    @Autowired
    private AdminService adminService;
    @Autowired
    private DatabaseServerDAO databaseServerDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ImportService importService;

    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    public class DisplayDataBase {
        private final boolean loaded;
        private final Database database;

        public DisplayDataBase(boolean loaded, Database database) {
            this.loaded = loaded;
            this.database = database;
        }

        public boolean getLoaded() {
            return loaded;
        }

        public int getId(){
            return database.getId();
        }
        public LocalDateTime getStartDate(){
            return database.getStartDate();
        }
        public LocalDateTime getEndDate(){
            return database.getEndDate();
        }
        public int getBusinessId(){
            return database.getBusinessId();
        }
        public String getName(){
            return database.getName();
        }
        public String getPersistenceName(){
            return database.getPersistenceName();
        }
        public String getDatabaseType() { return database.getDatabaseType(); }
        public int getNameCount(){
            return database.getNameCount();
        }
        public int getValueCount(){
            return database.getValueCount();
        }
        public String getUrlEncodedName(){
            return database.getUrlEncodedName();
        }
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "createDatabase", required = false) String createDatabase
            , @RequestParam(value = "databaseType", required = false) String databaseType
            , @RequestParam(value = "databaseServerId", required = false) String databaseServerId
            , @RequestParam(value = "emptyId", required = false) String emptyId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "unloadId", required = false) String unloadId
            , @RequestParam(value = "convertId", required = false) String convertId
            , @RequestParam(value = "backupTarget", required = false) String backupTarget
            , @RequestParam(value = "summaryLevel", required = false) String summaryLevel
    )

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            StringBuilder error = new StringBuilder();
            if (request.getSession().getAttribute("importResult") != null){
                error.append(request.getSession().getAttribute("importResult"));
                request.getSession().removeAttribute("importResult");
            }
            try {
                final List<DatabaseServer> allServers = databaseServerDAO.findAll();
                if (createDatabase != null && !createDatabase.isEmpty() && (allServers.size() == 1 || (databaseServerId != null && !databaseServerId.isEmpty()))) {
                    if (allServers.size() == 1){
                        adminService.createDatabase(createDatabase, databaseType, loggedInUser, allServers.get(0));
                    } else {
                        adminService.createDatabase(createDatabase, databaseType, loggedInUser, databaseServerDAO.findById(Integer.parseInt(databaseServerId)));
                    }
                }
                if (emptyId != null && NumberUtils.isNumber(emptyId)) {
                    adminService.emptyDatabaseById(loggedInUser, Integer.parseInt(emptyId));
                }
                if (deleteId != null && NumberUtils.isNumber(deleteId)) {
                    adminService.removeDatabaseById(loggedInUser, Integer.parseInt(deleteId));
                }
                if (unloadId != null && NumberUtils.isNumber(unloadId)) {
                    adminService.unloadDatabase(loggedInUser, Integer.parseInt(unloadId));
                }
                if (backupTarget != null) {
                    LoggedInUser loggedInUserTarget = loginService.loginLoggedInUser(request.getSession().getId(), backupTarget, loggedInUser.getUser().getEmail(), "", true); // targetted to destinationDB
                    adminService.copyDatabase(loggedInUser.getDataAccessToken(), loggedInUserTarget.getDataAccessToken(), summaryLevel, loggedInUserTarget.getLanguages());// re languages I should just be followign what was there before . . .
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                model.put("error", error.toString());
            }
            List<Database> databaseList = adminService.getDatabaseListForBusiness(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                for (Database database : databaseList){
                    boolean isLoaded = adminService.isDatabaseLoaded(loggedInUser, database);
                    displayDataBases.add(new DisplayDataBase(isLoaded, database));
                    if (isLoaded && (adminService.getNameCount(loggedInUser, database) != database.getNameCount()
                            || adminService.getValueCount(loggedInUser, database) != database.getValueCount())){ // then update the counts
                        database.setNameCount(adminService.getNameCount(loggedInUser, database));
                        database.setValueCount(adminService.getValueCount(loggedInUser, database));
                        databaseDAO.store(database);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }

            if (error.length() > 0) {
                String exceptionError = error.toString();
                //trim off the javaspeak
                if (exceptionError.contains("error:"))
                    exceptionError = exceptionError.substring(exceptionError.indexOf("error:"));
                model.put("error", exceptionError);
            }
            model.put("databases", displayDataBases);
            final List<DatabaseServer> allServers = databaseServerDAO.findAll();
            if (allServers.size() > 1){
                model.put("databaseServers", allServers);
            } else {
                model.put("serverList", false);
            }
            model.put("uploads", adminService.getUploadRecordsForDisplayForBusiness(loggedInUser));
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            return "managedatabases";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile

    ){
        if (database  != null){
            request.getSession().setAttribute("lastSelected", database);
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (database != null && uploadFile != null) {
                if (database.isEmpty()){
                    model.put("error", "Please select a database");
                } else if (uploadFile.isEmpty()){
                    model.put("error", "Please select a file to upload");
                } else {
                    try{
                        HttpSession session = request.getSession();
                        loginService.switchDatabase(loggedInUser, database); // could be blank now
                        String fileName = uploadFile.getOriginalFilename();
                        // always move uplaoded files now, they'll need to be transferred to the DB server after code split
                        File moved = new File(spreadsheetService.getHomeDir() + "/temp/" + fileName);
                        uploadFile.transferTo(moved);
                        // need to add in code similar to report loading to give feedback on imports
                            new Thread(() -> {
                                // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
                                try {
                                    List<String> languages = new ArrayList<String>(loggedInUser.getLanguages());
                                    languages.remove(loggedInUser.getUser().getEmail());
                                    session.setAttribute("importResult",
                                            importService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), languages, false)
                                    );
                                } catch (Exception e) {
                                    String exceptionError = e.getMessage();
                                    e.printStackTrace();
                                    if (exceptionError != null && exceptionError.contains("error:"))
                                        exceptionError = exceptionError.substring(exceptionError.indexOf("error:"));
                                    session.setAttribute("importResult", exceptionError);
                                }
                            }).start();
                        return "importrunning";
                    } catch (Exception e){ // now the import has it's on exception catching
                        String exceptionError = e.getMessage();
                        e.printStackTrace();
                        //trim off the javaspeak
                        if (exceptionError != null && exceptionError.contains("error:"))
                            exceptionError = exceptionError.substring(exceptionError.indexOf("error:"));
                        model.put("error", exceptionError);
                    }
                }
            }
            List<Database> databaseList = adminService.getDatabaseListForBusiness(loggedInUser);
            List<DisplayDataBase> displayDataBases = new ArrayList<>();
            try {
                for (Database database1 : databaseList){
                    boolean isLoaded = adminService.isDatabaseLoaded(loggedInUser, database1);
                    displayDataBases.add(new DisplayDataBase(isLoaded, database1));
                    if (isLoaded && (adminService.getNameCount(loggedInUser, database1) != database1.getNameCount()
                            || adminService.getValueCount(loggedInUser, database1) != database1.getValueCount())){ // then update the counts
                        database1.setNameCount(adminService.getNameCount(loggedInUser, database1));
                        database1.setValueCount(adminService.getValueCount(loggedInUser, database1));
                        databaseDAO.store(database1);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            model.put("databases", displayDataBases);
            final List<DatabaseServer> allServers = databaseServerDAO.findAll();
            if (allServers.size() > 1){
                model.put("databaseServers", allServers);
            } else {
                model.put("serverList", false);
            }
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("uploads", adminService.getUploadRecordsForDisplayForBusiness(loggedInUser));
            return "managedatabases";
        }
    }
}
