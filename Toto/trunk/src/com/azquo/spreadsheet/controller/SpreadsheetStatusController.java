package com.azquo.spreadsheet.controller;

import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 18/08/15.
 *
 * Will be used to inform the user what's going on on the database server and to allow them to stop it if necessary.
 *
 */
@Controller
@RequestMapping("/SpreadsheetStatus")

public class SpreadsheetStatusController {

    public static final String LOGGED_IN_USER_SESSION = "LOGGED_IN_USER_SESSION";

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "action", required = false) String action,@RequestParam(value = "reportid", required = false) String reportid, HttpServletRequest request) throws Exception {
        if ("sheetReady".equals(action) && reportid != null){
            if (request.getSession().getAttribute(reportid) != null){
                return "true";
            }
        }
        // not strictly a spreadsheet status, may move this later
        if ("importResult".equals(action)){
            if (request.getSession().getAttribute("importResult") != null){
                return "true";
            }
        }
        if ("log".equals(action)){
            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            // todo - limit the amount returned?
            if (loggedInUser != null) {
                return RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getSessionLog(loggedInUser.getDataAccessToken()).replace("\n","<br>"); // note - I am deliberately not doing <br/>, it seems javascript messes with it and then I can't detect changes
            }
        }
        if ("stop".equals(action)){
            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            if (loggedInUser != null) {
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).sendStopMessageToLog(loggedInUser.getDataAccessToken());
                return "stopsent";
            }
        }
        return "session lost";
    }
}
