package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 21/07/16.
 * <p>
 * Changed 03/10/2018 to use new backup system - old MySQL based one obsolete
 *
 * todo - spinning cog on restoring
 */
@Controller
@RequestMapping("/ReportWizard")
public class ReportWizardController {


//    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");


    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    ) {

          LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            Database db = loggedInUser.getDatabase();
            if (db==null){
                model.put("error","You have not selected a database");
                return "reportwizard";
            }
            List<String> dataList = CommonReportUtils.getDropdownListForQuery(loggedInUser,"data level 2");
            model.put("datavalues",dataList);
            String submit = request.getParameter("submit");
            if ("submit".equals(submit)){
                String data = request.getParameter("data");
                String rows = request.getParameter("rows");
                String columns = request.getParameter("columns");
                String template = request.getParameter("templates");
                String reportName = request.getParameter("reportName");
                try{
                    int reportId = ReportWizard.createReport(loggedInUser, data,rows,columns,template,reportName);
                    model.put("reportid", reportId);
                    return "redirect:/api/Online";
                }catch(Exception e){
                    model.put("error", e.getMessage());
                }


            }


             return "reportwizard";
        } else {
            return "redirect:/api/Login";
        }
    }


}




