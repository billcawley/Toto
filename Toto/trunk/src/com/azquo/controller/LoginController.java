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
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 * Ok now we're not using Excel the purpose changes, it will serve a basic Login screen then jam a logged in conneciton against the session
 * I plan to get rid of connection id!
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
    public String handleRequest(ModelMap model, HttpServletRequest request
             , @RequestParam(value = "user", required = false)  String userEmail
            , @RequestParam(value = "password", required = false)  String password
            , @RequestParam(value = "online", required = false, defaultValue = "false")  boolean online

                                ) throws Exception {
        String result;
        String callerId = request.getRemoteAddr();
        if (callerId != null && userEmail != null && userEmail.equals("demo@user.com")) {
            userEmail += callerId;
        }

        LoggedInConnection loggedInConnection;
        if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
            model.put("userEmail", userEmail);
            loggedInConnection = loginService.login(null, userEmail, password, 60, null, false);
            if (loggedInConnection != null){
                // redirect to online, I want to zap the connection id if I can
                return "redirect:Online?connectionid=" + loggedInConnection.getConnectionId() + "&reportid=1";
            } else {// feedback to users about incorrect details
                model.put("error", "incorrect login details");
            }
        }
        return "login";
    }
}