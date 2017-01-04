package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 09/07/15.
 * <p/>
 * A simple page to create a new business. Currently for internal use, it has not been beautified.
 */
@Controller
@RequestMapping("/NewBusiness")
public class NewBusinessController {

    //private static final Logger logger = Logger.getLogger(ManageUsersController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "businessName", required = false) String businessName
            , @RequestParam(value = "address1", required = false) String address1
            , @RequestParam(value = "address2", required = false) String address2
            , @RequestParam(value = "address3", required = false) String address3
            , @RequestParam(value = "address4", required = false) String address4
            , @RequestParam(value = "postcode", required = false) String postcode
            , @RequestParam(value = "telephone", required = false) String telephone
            , @RequestParam(value = "fax", required = false) String fax
            , @RequestParam(value = "website", required = false) String website
            , @RequestParam(value = "emailUsername", required = false) String emailUsername
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "confirmPassword", required = false) String confirmPassword
            , @RequestParam(value = "submit", required = false) String submit
    ) throws Exception

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (submit != null){
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                if (businessName == null || businessName.isEmpty()){
                    error.append("Business name required<br/>");
                }
                if (emailUsername == null || emailUsername.isEmpty()){
                    error.append("Email/Username required<br/>");
                }
                if (password == null || password.isEmpty()){
                    error.append("password required<br/>");
                } else if (!password.equals(confirmPassword)){
                    error.append("password and confirm do not match<br/>");
                }
                if (error.length() == 0){
                    AdminService.registerBusiness(emailUsername,businessName,password,businessName,address1,address2,address3,address4,postcode,telephone,website);
                    loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(),null, emailUsername, password, false);
                    if (loggedInUser != null) {
                        request.getSession().setAttribute( LoginController.LOGGED_IN_USER_SESSION, loggedInUser);
                    }

                    return "redirect:/api/Online?reportid=1";
                } else {
                    model.put("error", error.toString());
                }
            }
            model.put("businessName", businessName);
            model.put("address1", address1);
            model.put("address2", address2);
            model.put("address3", address3);
            model.put("address4", address4);
            model.put("postcode", postcode);
            model.put("telephone", telephone);
            model.put("fax", fax);
            model.put("website", website);
            model.put("emailUsername", emailUsername);
            return "newbusiness";
        }
    }
}