package com.azquo.spreadsheet.controller;

import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.util.AzquoMailer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 * <p>
 * Basic login form and support for a magento server side call to pass a session to the user that clicked the plugin
 */
@Controller
@RequestMapping("/Login")

public class LoginController {

    //   private static final Logger logger = Logger.getLogger(LoginController.class);

    public static final String LOGGED_IN_USER_SESSION = "LOGGED_IN_USER_SESSION";

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "user", required = false) String userEmail
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "logoff", required = false) String logoff
            , @RequestParam(value = "connectionid", required = false) String connectionid // only for the magento plugin and Javascript (connectionId = "javascript")
    ) throws Exception {
        String url = request.getRequestURL().toString();
        String page = "login";
        if (url.contains("edbroking")){
            page = "edbroking_login";
        }

        if ("true".equals(logoff)) {
            if (request.getSession().getAttribute(LOGGED_IN_USER_SESSION) != null) {
                if (!SpreadsheetService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().startsWith("0")) { // if it's from us don't email us :)
                    LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                    Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                    String title = "Logout from " + loggedInUser.getUser().getEmail() + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr();
                    String userAgent = request.getHeader("User-Agent");
                    AzquoMailer.sendEMail("edd@azquo.com", "Edd", title, userAgent);
                    AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, userAgent);
                    AzquoMailer.sendEMail("bill@azquo.com", "Bill", title, userAgent);
                    AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, userAgent);
                    AzquoMailer.sendEMail("bruce.cooper@azquo.com", "Bruce", title, userAgent);
                }
                request.getSession().removeAttribute(LOGGED_IN_USER_SESSION);
            }
        }
        if (connectionid != null && connectionid.length() > 0 && !connectionid.equals("javascript")) { // nasty hack to support connection id from the plugin
            if (request.getServletContext().getAttribute(connectionid) != null) { // then pick up the temp logged in conneciton
                LoggedInUser loggedInUser = (LoggedInUser) request.getServletContext().getAttribute(connectionid);
                request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                request.getServletContext().removeAttribute(connectionid); // take it off the context
                if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                    return "redirect:/api/ManageReports";
                } else {
                    return "redirect:/api/Online?reportid=1"; // redirect to menu, will need to be changed when we sort the parameters out
                }
            }
        } else {
            if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
                model.put("userEmail", userEmail);
                LoggedInUser loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), null, userEmail, password, false);
                if (loggedInUser != null) {
                    // same checks as magento controller
                    if (!"nic@azquo.com".equalsIgnoreCase(userEmail) && !SpreadsheetService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().startsWith("0")) { // if it's from us don't email us :)
                        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                        String title = "Login to the server " + userEmail + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr();
                        String userAgent = request.getHeader("User-Agent");
                        AzquoMailer.sendEMail("edd@azquo.com", "Edd", title, userAgent);
                        AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, userAgent);
                        AzquoMailer.sendEMail("bill@azquo.com", "Bill", title, userAgent);
                        AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, userAgent);
                        AzquoMailer.sendEMail("bruce.cooper@azquo.com", "Bruce", title, userAgent);
                    }
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                    if (connectionid != null && connectionid.equals("javascript")) {
                        String jsonFunction = "azquojsonresponse";
                        String userType = "user";
                        if (loggedInUser.getUser().isAdministrator()){
                            userType = "administrator";

                        }else{
                            if (loggedInUser.getUser().isDeveloper()){
                                userType = "developer";

                            }else{
                                if (loggedInUser.getUser().isMaster()){
                                    userType = "master";
                                }
                            }
                        }
                         model.addAttribute("content", jsonFunction + "({\"usertype\":\"" + userType + "})");
                        return "utf8page";

                    } else {
                        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                            return "redirect:/api/ManageReports";
                        } else {
                            return "redirect:/api/Online?reportid=1"; // redirect to menu, will need to be changed when we sort the parameters out
                        }
                    }
                } else {// feedback to users about incorrect details
                    if (connectionid != null && connectionid.equals("javascript")) {
                        String jsonFunction = "azquojsonresponse";
                        model.addAttribute("content", jsonFunction + "({\"usertype\":\"failed\"})");
                        response.setHeader("Access-Control-Allow-Origin", "*");
                        response.setHeader("Content-type", "application/json");
                        return "utf8page";

                    } else {
                        model.put("error", "incorrect login details");
                    }
                }
            }
        }
        return page;
    }
}