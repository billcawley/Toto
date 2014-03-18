package com.azquo.controller;

import com.azquo.jsonrequestentities.LoginJsonRequest;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLDecoder;

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

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "database", required = false) String database,
                                @RequestParam(value = "useremail", required = false)  String userEmail,
                                @RequestParam(value = "password", required = false)  String password,
                                @RequestParam(value = "spreadsheetname", required = false) String spreadsheetName,
                                @RequestParam(value = "timeout", required = false)  String timeout,
                                @RequestParam(value = "connectionid", required = false)  String connectionId,
                                @RequestParam(value = "json", required = false) final String json,
                                @RequestParam(value = "checkconnectionid", required = false) final String checkConnectionId) throws Exception {


        if (json!=null && json.length() > 0){
            // for Google sheets, better to send all parameters as JSON
            LoginJsonRequest loginJsonRequest;
            try {
                loginJsonRequest = jacksonMapper.readValue(json, LoginJsonRequest.class);
            } catch (Exception e) {
                logger.error("name json parse problem", e);
                return "error:badly formed json " + e.getMessage();
            }

            if (loginJsonRequest.database != null) database = loginJsonRequest.database;
            if (loginJsonRequest.user!= null) userEmail = loginJsonRequest.user;
            if (loginJsonRequest.password != null) password = loginJsonRequest.password;
            if (loginJsonRequest.spreadsheetname != null) spreadsheetName = loginJsonRequest.spreadsheetname;
            if (loginJsonRequest.timeout != null) timeout = loginJsonRequest.timeout;
            if (loginJsonRequest.connectionid !=null) connectionId = loginJsonRequest.connectionid;


        }

        if (connectionId != null && connectionId.length() > 0 && loginService.getConnection(connectionId) != null) {
                return connectionId;
        }
        if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
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

            final LoggedInConnection loggedInConnection = loginService.login(database, userEmail, password, minutesTimeout, spreadsheetName, false);
            if (loggedInConnection != null) {
                return loggedInConnection.getConnectionId();
            }
        }
        if (checkConnectionId != null && checkConnectionId.length() > 0) {
            if (loginService.getConnection(checkConnectionId) != null) {
                return "ok";
            } else {
                return "error:expired or incorrect connection id";
            }
        }
        return "error:incorrect login details";
    }
}