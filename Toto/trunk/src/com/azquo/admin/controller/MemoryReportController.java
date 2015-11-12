package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by edward on 30/09/15.
 *
 * Pushes a database server memory report through to the user, I don't think many non Azquo people would look at this at the moment.
 */
@Controller
@RequestMapping("/MemoryReport")
public class MemoryReportController {

    @Autowired
    AdminService adminService;

    @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "serverIp", required = false) String serverIp
            , @RequestParam(value = "gc", required = false) String gc
    ) throws Exception

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            modelMap.addAttribute("memoryReport", adminService.getMemoryReport(serverIp, "true".equalsIgnoreCase(gc)));
            modelMap.addAttribute("serverIp", serverIp);
            return "memoryreport";
        }
    }
}