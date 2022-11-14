package com.azquo.admin.controller;

import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.UploadedFile;
import io.keikai.api.Exporters;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zkoss.zk.ui.Sessions;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
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
            List<OnlineReport> templates = OnlineReportDAO.findTemplatesForBusinessId(loggedInUser.getBusiness().getId());
            if (templates.size() == 0){
                //need at least one template
                try {
                    Book book1 = Importers.getImporter().imports(request.getServletContext().getResourceAsStream("/WEB-INF/BasicReportTemplate.xlsx"), "Report name");
                    String filePath = SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + "BasicReportTemplate.xlsx";                    // timestamp to stop file overwriting
                    File moved = new File(filePath);
                    Exporters.getExporter().export(book1,moved);
                    UploadedFile uf = new UploadedFile(filePath,Collections.singletonList("Basic Report Template.xlsx"), false);
                    ImportService.uploadReport(loggedInUser, "Basic Report Template", uf);

                }catch (Exception e){
                    model.put("error","No templates available");
                }

            }
             String submit = request.getParameter("submit");
            String database = request.getParameter("databases");
            if ("submit".equals(submit)){
                String data = request.getParameter("datavalues");
                String function = request.getParameter("functions");
                String rows = request.getParameter("rows");
                String columns = request.getParameter("columns");
                String template = request.getParameter("templates");
                String reportName = request.getParameter("reportName");
                try{
                    int reportId = ReportWizard.createReport(loggedInUser, data,function, rows,columns,template,reportName);
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





