package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.ReportSchedule;
import com.azquo.admin.onlinereport.ReportScheduleDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 21/09/15.
 *
 * Report Schedule CRUD.
 *
 */
@Controller
@RequestMapping("/ManageReportSchedules")
public class ManageReportSchedulesController {

//    private static final Logger logger = Logger.getLogger(ManageReportsController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    )
    {
        if (request.getParameter("testsend") != null){
            try {
                SpreadsheetService.runScheduledReports();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (request.getParameter("new") != null){
                // note, this will fail with no reports or databases
                ReportSchedule reportSchedule = new ReportSchedule(0,"DAILY", "", LocalDateTime.now().plusYears(30)
                        , AdminService.getDatabaseListForBusiness(loggedInUser).get(0).getId(), AdminService.getReportList(loggedInUser).get(0).getId(),"","","");
                ReportScheduleDAO.store(reportSchedule);
            }
            final List<ReportSchedule> reportSchedules = AdminService.getReportScheduleList(loggedInUser);
            StringBuilder error = new StringBuilder();
            if (request.getParameter("submit") != null){
                for (ReportSchedule reportSchedule : reportSchedules) {
                    boolean store = false;
                    String period = request.getParameter("period" + reportSchedule.getId());
                    if (period != null && !period.equals(reportSchedule.getPeriod())) {
                        reportSchedule.setPeriod(period);
                        store = true;
                    }
                    String recipients = request.getParameter("recipients" + reportSchedule.getId());
                    if (recipients != null && !recipients.equals(reportSchedule.getRecipients())) {
                        reportSchedule.setRecipients(recipients);
                        store = true;
                    }
                    String nextDue = request.getParameter("nextDue" + reportSchedule.getId());
                    try{
                        if (!reportSchedule.getNextDueFormatted().equals(nextDue)){
                            reportSchedule.setNextDue(LocalDateTime.parse(nextDue, ReportSchedule.dateFormatter));
                            store = true;
                        }
                    } catch (DateTimeParseException e) {
                        error.append("End date format not yyyy-MM-dd hh:mm<br/>");
                    }
                    String databaseId = request.getParameter("databaseId" + reportSchedule.getId());
                    if (databaseId != null && !databaseId.equals(reportSchedule.getDatabaseId() + "")) {
                        try{
                            Database database = AdminService.getDatabaseById(Integer.parseInt(databaseId), loggedInUser);
                            if (database != null){
                                reportSchedule.setDatabaseId(Integer.parseInt(databaseId));
                                store = true;
                            }
                        } catch (NumberFormatException e) {
                            error.append("Database id is not a number<br/>");
                        }
                    }
                    String reportId = request.getParameter("reportId" + reportSchedule.getId());
                    if (reportId != null && !reportId.equals(reportSchedule.getReportId() + "")) {
                        try{
                            OnlineReport onlineReport = AdminService.getReportById(Integer.parseInt(reportId), loggedInUser);
                            if (onlineReport != null){
                                reportSchedule.setReportId(Integer.parseInt(reportId));
                                store = true;
                            }
                        } catch (NumberFormatException e) {
                            error.append("Report id is not a number<br/>");
                        }
                    }
                    String type = request.getParameter("type" + reportSchedule.getId());
                    if (type != null && !type.equals(reportSchedule.getType())) {
                        reportSchedule.setType(type);
                        store = true;
                    }
                    String parameters = request.getParameter("parameters" + reportSchedule.getId());
                    if (parameters != null && !parameters.equals(reportSchedule.getParameters())) {
                        reportSchedule.setParameters(parameters);
                        store = true;
                    }
                    String emailSubject = request.getParameter("emailsubject" + reportSchedule.getId());
                    if (emailSubject != null && !emailSubject.equals(reportSchedule.getEmailSubject())) {
                        reportSchedule.setEmailSubject(emailSubject);
                        store = true;
                    }
                   if (store) {
                       ReportScheduleDAO.store(reportSchedule);
                    }
                }
            }
            model.put("reportSchedules", reportSchedules);
            if (error.length() > 0){
                model.put("error", error.toString());
            }
            model.put("reports", AdminService.getReportList(loggedInUser));
            model.put("databases", AdminService.getDatabaseListForBusiness(loggedInUser));
            return "managereportschedules";
        }
    }
}