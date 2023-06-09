package com.azquo.admin.controller;

import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by edward on 30/09/15.
 * <p>
 * For users to see how much memory a database server is using.
 */
@Controller
@RequestMapping("/MemoryReport")
public class MemoryReportController {

    @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "serverIp", required = false) String serverIp
            , @RequestParam(value = "gc", required = false) String gc
    ) throws Exception

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            modelMap.addAttribute("memoryReport", RMIClient.getServerInterface(serverIp).getMemoryReport(false));
            modelMap.addAttribute("cpuReport", RMIClient.getServerInterface(serverIp).getCPUReport());
            // not allowing gc
//            modelMap.addAttribute("memoryReport", RMIClient.getServerInterface(serverIp).getMemoryReport("true".equalsIgnoreCase(gc)));
            modelMap.addAttribute("serverIp", serverIp);
            return "memoryreport";
        } else {
            return "redirect:/api/Login";
        }
    }
}