package com.azquo.spreadsheet.controller;

import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.user.User;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.util.AzquoMailer;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

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
    // right, due to requirements from Ed Broking a user can exist in multiple businesses in which case, if their credentials are correct, they can select between them
    public static final String LOGGED_IN_USERS_SESSION = "LOGGED_IN_USERS_SESSION";

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpSession session, HttpServletResponse response
            , @RequestParam(value = "user", required = false) String userEmail
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "logoff", required = false) String logoff
            , @RequestParam(value = "connectionid", required = false) String connectionid // only for the magento plugin and Javascript (connectionId = "javascript")
            , @RequestParam(value = "userid", required = false) String userid // if a user exists in more than one business then
            , @RequestParam(value = "select", required = false) String select
            , @RequestParam(value = "azure", required = false) String azure
    ) throws Exception {
        // stack overflow code, will be modified
        NumberFormat nf = NumberFormat.getNumberInstance();
        long percentUsable = 100; // if it cna't be worked out default to ok. Maybe change this . . .
        try {
            FileStore store = Files.getFileStore(Paths.get(SpreadsheetService.getHomeDir()));
            percentUsable = (100 * store.getUsableSpace()) / store.getTotalSpace();

        } catch (IOException e) {
            System.out.println("error querying space: " + e.toString());
        }
        if (percentUsable <= 10) {
            // log as well
            System.out.println("***WARNING, LOW DISK SPACE***");
            System.out.println("***WARNING, LOW DISK SPACE***");
            System.out.println("***WARNING, LOW DISK SPACE***");
            model.put("error", "***WARNING, LOW DISK SPACE***");
        }
        if (session.getAttribute(LOGGED_IN_USERS_SESSION) != null) {
            List<LoggedInUser> loggedInUsers = (List<LoggedInUser>) session.getAttribute(LOGGED_IN_USERS_SESSION);
            if ("true".equals(azure)) {
                return "utf8page";
            }

            if ("true".equals(select)) {
                List<User> usersToShow = new ArrayList<>();
                for (LoggedInUser l : loggedInUsers) {
                    usersToShow.add(l.getUser());
                }
                model.put("users", usersToShow);
                return "loginuserselect";
            }
            if (NumberUtils.isNumber(userid)) {
                for (LoggedInUser loggedInUser : loggedInUsers) {
                    if (loggedInUser.getUser().getId() == Integer.parseInt(userid)) {
                        session.setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                            return "redirect:/api/ManageReports";
                        } else {
                            return "redirect:/api/Online?reportid=1"; // redirect to menu, will need to be changed when we sort the parameters out
                        }
                    }
                }
            }
        }
        if ("true".equals(logoff)) {
            if (session.getAttribute(LOGGED_IN_USER_SESSION) != null) {
                if (SpreadsheetService.inProduction() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().startsWith("0")) { // if it's from us don't email us :)
                    LoggedInUser loggedInUser = (LoggedInUser) session.getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                    Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                    String title = SpreadsheetService.getAlias() + " Logout from " + loggedInUser.getUser().getEmail() + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr();
                    String userAgent = request.getHeader("User-Agent");
                    AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, userAgent);
                    AzquoMailer.sendEMail("bruce.cooper@azquo.com", "Bruce", title, userAgent);
                    AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, userAgent);
                }
                session.removeAttribute(LOGGED_IN_USER_SESSION);
                session.removeAttribute(LOGGED_IN_USERS_SESSION);
            }
        }
        if (connectionid != null && connectionid.length() > 0 && !connectionid.equals("javascript")) { // nasty hack to support connection id from the plugin
            if (request.getServletContext().getAttribute(connectionid) != null) { // then pick up the temp logged in conneciton
                LoggedInUser loggedInUser = (LoggedInUser) request.getServletContext().getAttribute(connectionid);
                session.setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
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
                LoggedInUser loggedInUser = null;
                List<LoggedInUser> loggedInUsers = LoginService.loginLoggedInUser(session.getId(), null, userEmail, password);
                if (!loggedInUsers.isEmpty()) {
                    if (loggedInUsers.size() > 1) { // new criteria, need to pick the user! todo . . .
                        session.setAttribute(LOGGED_IN_USERS_SESSION, loggedInUsers);
                        List<User> usersToShow = new ArrayList<>();
                        for (LoggedInUser l : loggedInUsers) {
                            usersToShow.add(l.getUser());
                        }
                        model.put("users", usersToShow);
                        return "loginuserselect";
                    }
                    loggedInUser = loggedInUsers.get(0);
                }
                if (loggedInUser != null) {
                    // same checks as magento controller
                    if (!"nic@azquo.com".equalsIgnoreCase(userEmail) && SpreadsheetService.inProduction() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().startsWith("0")) { // if it's from us don't email us :)
                        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                        String title = SpreadsheetService.getAlias() + " Login to the server " + userEmail + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr();
                        String userAgent = request.getHeader("User-Agent");
                        AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, userAgent);
                        AzquoMailer.sendEMail("bruce.cooper@azquo.com", "Bruce", title, userAgent);
                        AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, userAgent);
                    }
                    session.setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                    if (connectionid != null && connectionid.equals("javascript")) {
                        String jsonFunction = "azquojsonresponse";
                        String userType = "user";
                        if (loggedInUser.getUser().isAdministrator()) {
                            userType = "administrator";

                        } else {
                            if (loggedInUser.getUser().isDeveloper()) {
                                userType = "developer";

                            } else {
                                if (loggedInUser.getUser().isMaster()) {
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
        String page = "login";

        if (SpreadsheetService.getLogonPageOverride() != null && !SpreadsheetService.getLogonPageOverride().isEmpty()) {
            page = SpreadsheetService.getLogonPageOverride();
        }
        if (SpreadsheetService.getLogonPageColour() != null && !SpreadsheetService.getLogonPageColour().isEmpty()) {
            model.put("logoncolour", SpreadsheetService.getLogonPageColour());
        }
        if (SpreadsheetService.getLogonPageMessage() != null) {
            model.put("logonmessage", SpreadsheetService.getLogonPageMessage());
        }
        return page;
    }
}