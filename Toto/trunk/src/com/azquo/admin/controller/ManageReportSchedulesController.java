package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.ReportSchedule;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.xml.crypto.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Created by edward on 21/09/15.
 *
 */
@Controller
@RequestMapping("/ManageReportSchedules")
public class ManageReportSchedulesController {

    @Autowired
    private AdminService adminService;
    private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    )

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (request.getParameter("new") != null){
                // note, this will fail with no reports or databases
                ReportSchedule reportSchedule = new ReportSchedule(0,"DAILY", "", LocalDateTime.now().plusYears(30)
                        , adminService.getDatabaseListForBusiness(loggedInUser).get(0).getId(), adminService.getReportList(loggedInUser).get(0).getId(),"","");
                adminService.storeReportSchedule(reportSchedule);
            }
            final List<ReportSchedule> reportSchedules = adminService.getReportScheduleList(loggedInUser);
            StringBuilder error = new StringBuilder();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm");
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
                        if (!formatter.format(reportSchedule.getNextDue()).equals(nextDue)){
                            reportSchedule.setNextDue(LocalDateTime.parse(nextDue, formatter));
                            store = true;
                        }
                    } catch (DateTimeParseException e) {
                        error.append("End date format not yyyy-MM-dd hh:mm<br/>");
                    }

                    String databaseId = request.getParameter("databaseId" + reportSchedule.getId());
                    if (databaseId != null && !databaseId.equals(reportSchedule.getDatabaseId() + "")) {
                        try{
                            Database database = adminService.getDatabaseById(Integer.parseInt(databaseId), loggedInUser);
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
                            OnlineReport onlineReport = adminService.getReportById(Integer.parseInt(reportId), loggedInUser);
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

                    if (store) {
                        adminService.storeReportSchedule(reportSchedule);
                    }
                }
            }
            model.put("reportSchedules", reportSchedules);
            if (error.length() > 0){
                model.put("error", error.toString());
            }
            return "managereportschedules";
        }
    }
}