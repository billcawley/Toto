package com.azquo.controller;

import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.jsonrequestentities.LoginJsonRequest;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 * For logging in, should verify against a DB then return a connection id which expires if the credentials check out.
 */
@Controller
@RequestMapping("/Login")

public class LoginController {

    private static final Logger logger = Logger.getLogger(LoginController.class);
    private static final ObjectMapper jacksonMapper = new ObjectMapper();
    @Autowired
    private LoginService loginService;

    @Autowired
    private OnlineService onlineService;

    @Autowired
    private OnlineReportDAO onlineReportDAO;

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request) throws Exception {
        String userEmail = request.getParameter("useremail");
        String password = request.getParameter("password");
        String spreadsheetName = request.getParameter("spreadsheetname");
        String timeout = request.getParameter("timeout");
        String connectionId = request.getParameter("connectionid");
        String json = request.getParameter("json");
        String database = request.getParameter("database");
        String checkConnectionId = request.getParameter("checkconnectionid");
        String result;
        boolean online = request.getParameter("online") != null;
        if (userEmail != null && userEmail.equals("convert")) {
            return "done";
        }
        String callerId = request.getRemoteAddr();
        if (callerId != null && userEmail != null && userEmail.equals("demo@user.com")) {
            userEmail += callerId;
        }
        if (json != null && json.length() > 0) {
            // for Google sheets, better to send all parameters as JSON
            LoginJsonRequest loginJsonRequest;
            try {
                loginJsonRequest = jacksonMapper.readValue(json, LoginJsonRequest.class);
            } catch (Exception e) {
                logger.error("name json parse problem", e);
                return "error:badly formed json " + e.getMessage();
            }

            if (loginJsonRequest.database != null) database = loginJsonRequest.database;
            if (loginJsonRequest.user != null) userEmail = loginJsonRequest.user;
            if (loginJsonRequest.password != null) password = loginJsonRequest.password;
            if (loginJsonRequest.spreadsheetname != null) spreadsheetName = loginJsonRequest.spreadsheetname;
            if (loginJsonRequest.timeout != null) timeout = loginJsonRequest.timeout;
            if (loginJsonRequest.connectionid != null) connectionId = loginJsonRequest.connectionid;
        }

        if (!online && connectionId != null && connectionId.length() > 0 && loginService.getConnection(connectionId) != null) {
            return connectionId;
        }
        LoggedInConnection loggedInConnection;
        if ((connectionId == null || connectionId.length() == 0 || connectionId.equals("aborted")) && userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
            logger.info("spreadsheet name " + spreadsheetName);
            if (database == null || database.length() == 0) {
                database = "unknown";
            }
            int minutesTimeout = 0;
            if (timeout != null && timeout.length() > 0) {
                try {
                    minutesTimeout = Integer.parseInt(timeout);
                } catch (Exception ignored) {
                    return "error:timeout is not an integer";
                }
            }

            loggedInConnection = loginService.login(database, userEmail, password, minutesTimeout, spreadsheetName, false);
        } else {
            loggedInConnection = loginService.getConnection(connectionId);
        }
        if (loggedInConnection != null) {
            result = loggedInConnection.getConnectionId();
            if (spreadsheetName != null && spreadsheetName.equals("createmaster")) {
                loginService.createAzquoMaster();
                return connectionId;
            }

            if (online) {
                OnlineReport onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                return onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName, "");
            }
            return result;
        }
        if (checkConnectionId != null && checkConnectionId.length() > 0) {
            if (loginService.getConnection(checkConnectionId) != null) {
                result = "ok";
            } else {
                result = "error:expired or incorrect connection id";
            }
            return result;
        }
        return "error:incorrect login details";
    }
}