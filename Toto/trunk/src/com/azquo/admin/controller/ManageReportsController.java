package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.LoggedInConnection;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.controller.OnlineController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by cawley on 24/04/15.
 *
 * recreating what was in the excel/azquo book as simple pages. Calls to get data initially will simply be what was in fillAdminData in AzquoBook.
 */

@Controller
@RequestMapping("/ManageReports")
public class ManageReportsController {

    @Autowired
    private AdminService adminService;
    private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "report", required = false) String report
    )

    {
        LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);

        if (loggedInConnection == null || !loggedInConnection.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            model.put("reports", adminService.getReportList(loggedInConnection));

            return "managereports";
        }
    }
}