package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
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
@RequestMapping("/ManagePermissions")
public class ManagePermissionsController {
    @Autowired
    private AdminService adminService;

    private static final Logger logger = Logger.getLogger(ManagePermissionsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "permission", required = false) String permission
    )

    {
        LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);

        if (loggedInConnection == null || !loggedInConnection.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            model.put("permissions", adminService.getPermissionList(loggedInConnection));
            return "managepermissions";
        }
    }

}
