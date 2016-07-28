package com.azquo.spreadsheet.controller;

import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 *
 * Basic login form and support for a magento server side call to pass a session to the user that clicked the plugin
 *
 */
@Controller
@RequestMapping("/Login")

public class LoginController {

    //   private static final Logger logger = Logger.getLogger(LoginController.class);

    public static final String LOGGED_IN_USER_SESSION = "LOGGED_IN_USER_SESSION";

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String userEmail
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "logoff", required = false) String logoff
            , @RequestParam(value = "connectionid", required = false) String connectionid // only for the magento plugin
    ) throws Exception {
        if ("true".equals(logoff)){
            request.getSession().removeAttribute(LOGGED_IN_USER_SESSION);
        }
        if (connectionid != null && connectionid.length() > 0) { // nasty hack to support connection id from the plugin
            if (request.getServletContext().getAttribute(connectionid) != null) { // then pick up the temp logged in conneciton
                LoggedInUser loggedInUser = (LoggedInUser)request.getServletContext().getAttribute(connectionid);
                request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                request.getServletContext().removeAttribute(connectionid); // take it off the context
                if (!loggedInUser.getUser().isAdministrator()) {
                    return "redirect:/api/Online?opcode=loadsheet&reportid=1"; // redirect to menu, will need to be changed when we sort the parameters out
                } else {
                    return "redirect:/api/ManageReports";
                }
            }
        } else {
            if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
                model.put("userEmail", userEmail);
                LoggedInUser loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), null, userEmail, password, false);
                if (loggedInUser != null) {
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                    if (!loggedInUser.getUser().isAdministrator()) {
                        return "redirect:/api/Online?opcode=loadsheet&reportid=1"; // redirect to menu, will need to be changed when we sort the parameters out
                    } else {
                        return "redirect:/api/ManageReports";
                    }
                } else {// feedback to users about incorrect details
                    model.put("error", "incorrect login details");
                }
            }
        }
        return "login";
    }
}