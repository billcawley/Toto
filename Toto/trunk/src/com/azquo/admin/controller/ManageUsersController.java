package com.azquo.admin.controller;

import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.LoggedInConnection;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by cawley on 24/04/15.
 */
@Controller
@RequestMapping("/ManageUsers")
public class ManageUsersController {
    @Autowired
    private NameService nameService;
    @Autowired
    private ValueService valueService;
    @Autowired
    private LoginService loginService;
    @Autowired
    // TODO : break up into separate functions

    private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String user
    )

    {
        LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);

        if (loggedInConnection == null || !loggedInConnection.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            return "manageusers";
        }
    }

}
