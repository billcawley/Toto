package com.azquo.controller;

import com.azquo.adminentities.OnlineReport;
import com.azquo.adminentities.Permission;
import com.azquo.adminentities.User;
import com.azquo.memorydb.Name;
import com.azquo.service.AdminService;
import com.azquo.service.ImportService;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * User: cawley
 * Created 7th or 8th jan, that's what I get for pasting a whole file as a start
 * Admin. Register users, verify them,
 * Fairly vanilla stuff
 */
@Controller
@RequestMapping("/Maintain")

public class AdminController {

    private static final Logger logger = Logger.getLogger(AdminController.class);

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @Autowired
    private AdminService adminService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ImportService importService;

    private static final String SIGNON = "signon";
    private static final String NEWDATABASE = "newdatabase";
    private static final String NEWUSER = "newuser";
    private static final String USERPERMISSION = "userpermission";
    private static final String DATABASELIST = "databaselist";
    private static final String USERLIST = "userlist";
    private static final String SAVEUSERS = "saveusers";
    private static final String PERMISSIONLIST = "permissionlist";
    private static final String SAVEPERMISSIONS = "savepermissions";
    private static final String UPLOADSLIST = "uploadslist";
    private static final String COPYDATABASE = "copydatabase";
    private static final String SAVEONLINEREPORTS = "saveonlinereports";
    private static final String REPORTLIST ="reportlist";
    private static final String UPLOADFILE = "uploadfile";

    // maybe change all this to JSON later?

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "op") final String op
            , @RequestParam(value = "email", required = false) final String email
            , @RequestParam(value = "username", required = false) final String user
            , @RequestParam(value = "password", required = false) final String password
            , @RequestParam(value = "businessname", required = false) final String businessName
            , @RequestParam(value = "address1", required = false) final String address1
            , @RequestParam(value = "address2", required = false) final String address2
            , @RequestParam(value = "address3", required = false) final String address3
            , @RequestParam(value = "address4", required = false) final String address4
            , @RequestParam(value = "postcode", required = false) final String postcode
            , @RequestParam(value = "telephone", required = false) final String telephone
            , @RequestParam(value = "key", required = false) final String key
            , @RequestParam(value = "database", required = false) final String database
            , @RequestParam(value = "status", required = false) final String status
            , @RequestParam(value = "readlist", required = false) final String readList
            , @RequestParam(value = "writelist", required = false) final String writeList
            , @RequestParam(value = "json", required = false) final String json
            , @RequestParam(value = "jsonfunction", required = false) final String jsonFunction //this is the return function for Javascript
            , @RequestParam(value = "spreadsheetname", required = false) final String spreadsheetName
            , @RequestParam(value = "namelist", required = false) final String nameList
            , @RequestParam(value = "filename", required = false) final String fileName
            , @RequestParam(value = "filetype", required = false) final String fileType
            , @RequestParam(value = "online", required = false) final String online
            , @RequestParam(value = "connectionid", required = false) String connectionId) throws Exception {

        logger.info("request to admin controller : " + op);

        if (op.equalsIgnoreCase(SIGNON)) {
            String result;
            if (key != null && key.length() > 0 && businessName != null && businessName.length() > 0) {
                return adminService.confirmKey(businessName
                        , email
                        , password
                        , key
                        , spreadsheetName);
            } else if (email != null && email.length() > 0
                    && user != null && user.length() > 0
                    && businessName != null && businessName.length() > 0
                    && password != null && password.length() > 0) {
                     result =  adminService.registerBusiness(email
                        , user
                        , password
                        , businessName
                        , address1 != null ? address1 : ""
                        , address2 != null ? address2 : ""
                        , address3 != null ? address3 : ""
                        , address4 != null ? address4 : ""
                        , postcode != null ? postcode : ""
                        , telephone != null ? telephone : "");
            }else{
                String missing = "";

                if (email == null){
                    missing += "email address, ";
                }
                if (user == null){
                    missing += "user name, ";
                }
                if (businessName == null || businessName.length()==0){
                    missing += "business name, ";
                }
                if (password == null || password.length() == 0){
                    missing += "password, ";
                }
                result = "error: Please send: " + missing;
            }
            if (online != null && online.toLowerCase().equals("true")){
                return "azquojson({\"registrationreply\":\"" + result + "\"})";
            }else{
                return result;
            }
        } else {
            logger.info("connection id : " + connectionId);
            if (connectionId == null) {
                LoggedInConnection loggedInConnection = loginService.login(database, user, password, 0, spreadsheetName, false);
                if (loggedInConnection == null) {
                    return "error:no connection id";
                }
                connectionId = loggedInConnection.getConnectionId();
             }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);
            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }

            if (op.equalsIgnoreCase(NEWDATABASE)) {
                if (database != null && database.length() > 0) {
                    return adminService.createDatabase(database, loggedInConnection) + "";
                }
            }

            if (op.equalsIgnoreCase(NEWUSER)) {
                if (email != null && email.length() > 0
                        && user != null && user.length() > 0
                        && status != null && status.length() > 0
                        && password != null && password.length() > 0) {
                    return adminService.createUser(email, user, status, password, loggedInConnection) + "";
                }
            }

            if (op.equalsIgnoreCase(USERPERMISSION)) {
                if (email != null && email.length() > 0
                        && readList != null && readList.length() > 0
                        && writeList != null && writeList.length() > 0) {
                    return adminService.createUserPermission(email, readList, writeList, loggedInConnection) + "";
                }
            }

            if (op.equalsIgnoreCase(DATABASELIST)) {
                return jacksonMapper.writeValueAsString(adminService.getDatabaseListForBusiness(loggedInConnection));
            }

            if (op.equalsIgnoreCase(USERLIST)) {
                return jacksonMapper.writeValueAsString(adminService.getUserListForBusiness(loggedInConnection));
            }

            if (op.equalsIgnoreCase(COPYDATABASE)) {
                return adminService.copyDatabase(loggedInConnection, database, nameList);
            }
            if (op.equalsIgnoreCase(SAVEUSERS)) {
                if (json != null && json.length() > 0) {
                    List<User> usersFromJson = jacksonMapper.readValue(json, new TypeReference<List<User>>() {});
                    adminService.setUserListForBusiness(loggedInConnection, usersFromJson);
                }
            }

            if (op.equalsIgnoreCase(PERMISSIONLIST)) {
                // ok needs to work without email . .
                String toReturn = jacksonMapper.writeValueAsString(adminService.getPermissionList(loggedInConnection));
                logger.info("returned permission list : " + toReturn);
                return toReturn;
            }

            if (op.equalsIgnoreCase(SAVEPERMISSIONS)) {
                logger.info("save permissions json " + json);
                if (json != null && json.length() > 0) {
                    try {
                        List<Permission> permissionFromJson = jacksonMapper.readValue(json, new TypeReference<List<Permission>>() {});
                        return adminService.setPermissionListForBusiness(loggedInConnection, permissionFromJson);
                    } catch (Exception e) {
                        logger.error("problem saving permissions", e);
                    }
                }
            }

            if (op.equalsIgnoreCase(UPLOADSLIST)) {
                return jacksonMapper.writeValueAsString(adminService.getUploadRecordsForDisplayForBusiness(loggedInConnection));
            }
            if (op.equalsIgnoreCase(SAVEONLINEREPORTS)) {
                logger.info("save online report json " + json);
                if (json != null && json.length() > 0) {
                    try {
                        List<OnlineReport> onlineReportsFromJson = jacksonMapper.readValue(json, new TypeReference<List<OnlineReport>>() {});
                        return adminService.saveOnlineReportList(loggedInConnection, onlineReportsFromJson);
                    } catch (Exception e) {
                        logger.error("problem saving online reports", e);
                    }
                }
            }
            if (op.equalsIgnoreCase(REPORTLIST)) {
                // ok needs to work without email . .
                String result = jacksonMapper.writeValueAsString(adminService.getReportList(loggedInConnection));
                logger.info("returned report list : " + result);
                return jsonFunction + "({\"username\":\"" + loggedInConnection.getUser().getName() + "\",\"reportlist\":" + result + "})";
             }

            if (op.equalsIgnoreCase(UPLOADFILE)) {
                // just a quick way to load for WFC
               //InputStream uploadFile = new FileInputStream("/home/azquo/import/" + fileName);
               InputStream uploadFile = new FileInputStream("/home/bill/azquo/" + fileName);
                 return  importService.importTheFile(loggedInConnection, fileName, uploadFile, fileType,"true", true, loggedInConnection.getLanguages());
            }



        }
        return "";
    }
}