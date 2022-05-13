package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.zk.ReportAnalysis;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zkoss.poi.hssf.usermodel.HSSFFont;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (C) 2022 Azquo Ltd.
 * <p>
 * Created by cawley on 10/05/22.
 * <p>
 * Quickly test creating of 2d ranges in Azquo
 */

@Controller
@RequestMapping("/RangeTest")
public class RangeTestController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response,
                                @RequestParam(value = "databaseId", required = false) String databaseId
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            model.put("databases", AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser));
            return "rangetest";
        } else {
            return "redirect:/api/Login";
        }
    }
}