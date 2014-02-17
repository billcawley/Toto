package com.azquo.toto.controller;

import com.azquo.toto.adminentities.Permission;
import com.azquo.toto.adminentities.User;
import com.azquo.toto.service.AdminService;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @Autowired
    private AdminService adminService;
    @Autowired
    private LoginService loginService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    private static final String SIGNON = "signon";
    private static final String NEWDATABASE = "newdatabase";
    private static final String NEWUSER = "newuser";
    private static final String USERACCESS = "useraccess";
    private static final String DATABASELIST = "databaselist";
    private static final String USERLIST = "userlist";
    private static final String SAVEUSERS = "saveusers";
    private static final String PERMISSIONLIST = "permissionlist";
    private static final String SAVEPERMISSIONS = "savepermissions";
    private static final String UPLOADSLIST = "uploadslist";

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "op") final String op, @RequestParam(value = "email", required = false) final String email,
                                @RequestParam(value = "username", required = false) final String userName, @RequestParam(value = "password", required = false) final String password,
                                @RequestParam(value = "businessname", required = false) final String businessName, @RequestParam(value = "address1", required = false) final String address1,
                                @RequestParam(value = "address2", required = false) final String address2, @RequestParam(value = "address3", required = false) final String address3,
                                @RequestParam(value = "address4", required = false) final String address4, @RequestParam(value = "postcode", required = false) final String postcode,
                                @RequestParam(value = "telephone", required = false) final String telephone, @RequestParam(value = "key", required = false) final String key,
                                @RequestParam(value = "database", required = false) final String database, @RequestParam(value = "status", required = false) final String status,
                                @RequestParam(value = "readlist", required = false) final String readList, @RequestParam(value = "writelist", required = false) final String writeList,
                                @RequestParam(value = "json", required = false) final String json,
                                @RequestParam(value = "spreadsheetname", required = false) final String spreadsheetName,
                                @RequestParam(value = "connectionid", required = false) final String connectionId) throws Exception {

        System.out.println("request to admin controller : " + op);
        if (op.equalsIgnoreCase(SIGNON)) {
            if (key != null && key.length() > 0 && businessName != null && businessName.length() > 0) {
                return adminService.confirmKey(businessName, email, password, key, spreadsheetName) + "";
            } else if (email != null && email.length() > 0 && userName != null && userName.length() > 0 && businessName != null && businessName.length() > 0 && password != null && password.length() > 0) {
                return adminService.registerBusiness(email, userName, password, businessName, address1 != null ? address1 : "", address2 != null ? address2 : "",
                        address3 != null ? address3 : "", address4 != null ? address4 : "", postcode != null ? postcode : "", telephone != null ? telephone : "");
            }
        } else {
            System.out.println("conneciton id : " + connectionId);
            if (connectionId == null) {
                return "error:no connection id";
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
                if (email != null && email.length() > 0 && userName != null && userName.length() > 0
                        && status != null && status.length() > 0 && password != null && password.length() > 0) {
                    return adminService.createUser(email, userName, status, password, loggedInConnection) + "";
                }
            }
            if (op.equalsIgnoreCase(USERACCESS)) {
                if (email != null && email.length() > 0 && readList != null && readList.length() > 0
                        && writeList != null && writeList.length() > 0) {
                    return adminService.createUserAccess(email, readList, writeList, loggedInConnection) + "";
                }
            }
            if (op.equalsIgnoreCase(DATABASELIST)) {
                return jacksonMapper.writeValueAsString(adminService.getDatabaseListForBusiness(loggedInConnection));
            }
            if (op.equalsIgnoreCase(USERLIST)) {
                return jacksonMapper.writeValueAsString(adminService.getUserListForBusiness(loggedInConnection));
            }
            if (op.equalsIgnoreCase(SAVEUSERS) ) {
                if (json != null && json.length() > 0) {
                    List<User> usersFromJson = jacksonMapper.readValue(json, new TypeReference<List<User>>(){});
                    adminService.setUserListForBusiness(loggedInConnection, usersFromJson);
                }
            }
            if (op.equalsIgnoreCase(PERMISSIONLIST)) {
                // ok needs to work without email . .
                String toReturn =jacksonMapper.writeValueAsString(adminService.getPermissionList(loggedInConnection));
                System.out.println("returned access list : " + toReturn);
               return toReturn;
            }
            if (op.equalsIgnoreCase(SAVEPERMISSIONS) ) {
                System.out.println("save permissions json " + json);
                if (json != null && json.length() > 0) {
                    try{
                        List<Permission> permissionFromJson = jacksonMapper.readValue(json, new TypeReference<List<Permission>>(){});
                        return adminService.setPermissionListForBusiness(loggedInConnection, permissionFromJson);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
            if (op.equalsIgnoreCase(UPLOADSLIST)) {
                return jacksonMapper.writeValueAsString(adminService.getUploadRecordsForDisplayForBusiness(loggedInConnection));
            }

        }
        return "";
   }
}