package com.azquo.spreadsheet.controller;

import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.controller.PendingUploadController;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 18/08/15.
 * <p>
 * Will be used to inform the user what's going on on the database server and to allow them to stop it if necessary.
 */
@Controller
@RequestMapping("/SpreadsheetStatus")

public class SpreadsheetStatusController {

    public static final String LOGGED_IN_USER_SESSION = "LOGGED_IN_USER_SESSION";

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "action", required = false) String action, @RequestParam(value = "reportid", required = false) String reportid, HttpServletRequest request) throws Exception {
        // used when preparing a database for download for example
        if ("working".equals(action)) {
            if (request.getSession().getAttribute("working") != null) {
                return "{\"status\":\"" + request.getSession().getAttribute("working") + "\"}";
            }
            return "{\"status\":\"\"}";
        }

        if ("sheetReady".equals(action) && reportid != null) {
            if (request.getSession().getAttribute(reportid) != null) {
                return "true";
            }
        }
        // be more specific? todo
        if ("pendingReady".equals(action)) {
            if (request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT) != null) {
                return "true";
            }
        }
        // not strictly a spreadsheet status, may move this later
        if (ManageDatabasesController.IMPORTRESULT.equals(action)) {
            if (request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT) != null) {
                // url suffix to jam an href anchor on, in then first place #tab4 probably
                String toReturn = "true" + (request.getSession().getAttribute(ManageDatabasesController.IMPORTURLSUFFIX) != null ? request.getSession().getAttribute(ManageDatabasesController.IMPORTURLSUFFIX) : "");
                request.getSession().removeAttribute(ManageDatabasesController.IMPORTURLSUFFIX); // it will cause trouble if left!!
                return toReturn;
            } else { // a kind of headline on status - currently used for a number of files being processed in a zip
                if (request.getSession().getAttribute(ManageDatabasesController.IMPORTSTATUS) != null){
                    return (String) request.getSession().getAttribute(ManageDatabasesController.IMPORTSTATUS);
                }
                return "";
            }
        }
        if ("log".equals(action)) {
            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            if (loggedInUser != null) {
                String sessionLog = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getSessionLog(loggedInUser.getDataAccessToken());
                if (sessionLog.length() > 10_000){
                    sessionLog = sessionLog.substring(1000);
                }
                return sessionLog.replace("\n", "<br>"); // note - I am deliberately not doing <br/>, it seems javascript messes with it and then I can't detect changes
            }
        }
        if ("stop".equals(action)) {
            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            if (loggedInUser != null) {
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).sendStopMessageToLog(loggedInUser.getDataAccessToken());
                return "stopsent";
            }
        }
        return "session lost";
    }
}
