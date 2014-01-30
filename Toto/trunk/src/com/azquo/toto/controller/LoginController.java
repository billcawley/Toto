package com.azquo.toto.controller;

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
@RequestMapping("/Login")

public class LoginController {
    @Autowired
    private LoginService loginService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "database", required = false)  String database,
                                @RequestParam(value = "useremail", required = false) final String userEmail,
                                @RequestParam(value = "password", required = false) final String password,
                                @RequestParam(value = "timeout", required = false) final String timeout,
                                @RequestParam(value = "checkconnectionid", required = false) final String checkConnectionId) throws Exception {

        if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
            if (database == null || database.length()== 0){
                database="unknown";
            }
            int minutesTimeout = 0;
            if (timeout != null && timeout.length() > 0) {
                try {
                    minutesTimeout = Integer.parseInt(timeout);
                } catch (Exception ignored) {
                    return "error:timeout is not an integer";
                }
            }
            final LoggedInConnection loggedInConnection = loginService.login(database, userEmail, password, minutesTimeout);
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