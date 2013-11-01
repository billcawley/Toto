package com.azquo.toto.controller;

import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
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
    public String handleRequest(@RequestParam(value = "database", required = false) String database, @RequestParam(value = "user", required = false) String user, @RequestParam(value = "password", required = false) String password) throws Exception {
        LoggedInConnection loggedInConnection = loginService.login(database,user,password);
        if (loggedInConnection != null){
            return loggedInConnection.getConnectionId();
        }
        return "false";
    }
}