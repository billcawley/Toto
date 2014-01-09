package com.azquo.toto.controller;

import com.azquo.toto.service.BusinessService;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 * For logging in, should verify against a DB then return a connection id which expires if the credentials check out.
 */
@Controller
@RequestMapping("/Maintain")

public class AdminController {
    @Autowired
    private BusinessService businessService;
    @Autowired
    private LoginService loginService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    private static final String SIGNON = "signon";
    private static final String NEWDATABASE = "newdatabase";
    private static final String NEWUSER = "newuser";
    private static final String USERACCESS = "useraccess";

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
                                @RequestParam(value = "connectionid") final String connectionId) throws Exception {

        if(op.equalsIgnoreCase(SIGNON)){
            if (key != null && key.length() > 0 && businessName != null && businessName.length() > 0){
                return businessService.confirmKey(businessName,key) + "";
            } else if (email != null && email.length() > 0 && userName != null && userName.length() > 0 && businessName != null && businessName.length() > 0){
                return businessService.registerBusiness(email,businessName,password,businessName,address1 != null ? address1 : "", address2 != null ? address2 : "",
                        address3 != null ? address3 : "", address4 != null ? address4 : "",postcode != null ? postcode : "",telephone != null ? telephone : "");
            }
        } else {
            if (connectionId == null) {
                return "error:no connection id";
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }
            if(op.equalsIgnoreCase(NEWDATABASE)){
                if (database != null && database.length() > 0){
                    return businessService.createDatabase(database, loggedInConnection) + "";
                }
            }
            if(op.equalsIgnoreCase(NEWUSER)){
                if (email != null && email.length() > 0 && userName != null && userName.length() > 0
                        && status != null && status.length() > 0 && password != null && password.length() > 0){
                    return businessService.createUser(email,userName,status,password, loggedInConnection) + "";
                }
            }
            if(op.equalsIgnoreCase(USERACCESS)){
                if (email != null && email.length() > 0 && readList != null && readList.length() > 0
                        && writeList != null && writeList.length() > 0 ){
                    return businessService.createUserAccess(email,readList,writeList,loggedInConnection) + "";
                }
            }

        }
        return "";

    }
}