package com.azquo.admin.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.ReportSchedule;
import com.azquo.admin.onlinereport.ReportScheduleDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportRenderer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.util.AreaReference;
import org.zkoss.poi.xssf.usermodel.XSSFName;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by edward on 21/09/15.
 * <p>
 * Report Schedule CRUD.
 */
@Controller
@RequestMapping("/ManageReportSchedules")
public class ManageReportSchedulesController {

    //    private static final Logger logger = Logger.getLogger(ManageReportsController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    ) {
        if (request.getParameter("testsend") != null) {
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
            final List<Database> databaseListForBusiness = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            if (request.getParameter("deleteId") != null){
                // todo - move to admin service
                try{
                    ReportSchedule rs = ReportScheduleDAO.findById(Integer.parseInt(request.getParameter("deleteId")));
                    Database d = DatabaseDAO.findById(rs.getDatabaseId());
                    if (d.getBusinessId() == loggedInUser.getUser().getBusinessId()){
                        ReportScheduleDAO.removeById(rs);
                    }
                } catch (Exception ignored){
                }
            }
            if (request.getParameter("new") != null && databaseListForBusiness != null) {
                // note, this will fail with no reports or databases
                ReportSchedule reportSchedule = new ReportSchedule(0, "DAILY", "", LocalDateTime.now().plusYears(30)
                        , databaseListForBusiness.get(0).getId(), AdminService.getReportList(loggedInUser).get(0).getId(), "", "", "");
                ReportScheduleDAO.store(reportSchedule);
            }
            final List<ReportSchedule> reportSchedules = AdminService.getReportScheduleList(loggedInUser);
            StringBuilder error = new StringBuilder();
            if (request.getParameter("submit") != null) {
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
                    try {
                        if (!reportSchedule.getNextDueFormatted().equals(nextDue)) {
                            reportSchedule.setNextDue(LocalDateTime.parse(nextDue, ReportSchedule.dateFormatter));
                            store = true;
                        }
                    } catch (DateTimeParseException e) {
                        error.append("End date format not yyyy-MM-dd hh:mm<br/>");
                    }
                    String databaseId = request.getParameter("databaseId" + reportSchedule.getId());
                    if (databaseId != null && !databaseId.equals(reportSchedule.getDatabaseId() + "")) {
                        try {
                            Database database = AdminService.getDatabaseByIdWithBasicSecurityCheck(Integer.parseInt(databaseId), loggedInUser);
                            if (database != null) {
                                reportSchedule.setDatabaseId(Integer.parseInt(databaseId));
                                store = true;
                            }
                        } catch (NumberFormatException e) {
                            error.append("Database id is not a number<br/>");
                        }
                    }
                    String reportId = request.getParameter("reportId" + reportSchedule.getId());
                    if (reportId != null && !reportId.equals(reportSchedule.getReportId() + "")) {
                        try {
                            OnlineReport onlineReport = AdminService.getReportByIdWithBasicSecurityCheck(Integer.parseInt(reportId), loggedInUser);
                            if (onlineReport != null) {
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
            if (error.length() > 0) {
                model.put("error", error.toString());
            }
            model.put("reports", AdminService.getReportList(loggedInUser));
            model.put("databases", databaseListForBusiness);
            AdminService.setBanner(model,loggedInUser);
            return "managereportschedules";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && loggedInUser.getUser().isAdministrator()) {
            if (uploadFile != null) {
                try {
                    String fileName = uploadFile.getOriginalFilename();
                    File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                    uploadFile.transferTo(moved);

                    // as with the users upload this has been moved in from Import service.
                    // Maybe it shouldn't be in a controller but it does make more sense in there than mixed with the import logic

                    FileInputStream fs = new FileInputStream(moved);
                    OPCPackage opcPackage = OPCPackage.open(fs);
                    XSSFWorkbook book = new XSSFWorkbook(opcPackage);
                    Sheet schedulesSheet = book.getSheet("ReportSchedules"); // literals not best practice, could it be factored between this and the xlsx file?
                    if (schedulesSheet != null) {
                        int row = 1;
//                SName listRegion = book.getInternalBook().getNameByName("data");
                        Name listRegion = BookUtils.getName(book,"data");
                        if (listRegion != null && listRegion.getRefersToFormula() != null) {
                            AreaReference aref = new AreaReference(listRegion.getRefersToFormula());
                            row = aref.getFirstCell().getRow();
                        }

                        final List<ReportSchedule> reportSchedules = AdminService.getReportScheduleList(loggedInUser);
                        for (ReportSchedule reportSchedule : reportSchedules) {
                            ReportScheduleDAO.removeById(reportSchedule);
                        }
                        while (schedulesSheet.getRow(row).getCell(0).getStringCellValue() != null && schedulesSheet.getRow(row).getCell(0).getStringCellValue().length() > 0) {
                            String period = schedulesSheet.getRow(row).getCell(0).getStringCellValue();
                            String recipients = schedulesSheet.getRow(row).getCell(1).getStringCellValue();
                            LocalDateTime nextDue = LocalDateTime.now();
                            try {
                                nextDue = LocalDateTime.parse(schedulesSheet.getRow(row).getCell(2).getStringCellValue(), CreateExcelForDownloadController.dateTimeFormatter);
                            } catch (Exception ignored) {
                            }
                            String database = schedulesSheet.getRow(row).getCell(3).getStringCellValue();
                            Database database1 = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                            if (database1 != null) {
                                String report = schedulesSheet.getRow(row).getCell(4).getStringCellValue();
                                OnlineReport onlineReport = OnlineReportDAO.findForDatabaseIdAndName(database1.getId(), report);
                                if (onlineReport != null) {
                                    String type = schedulesSheet.getRow(row).getCell(5).getStringCellValue();
                                    String parameters = schedulesSheet.getRow(row).getCell(6).getStringCellValue();
                                    String emailSubject = schedulesSheet.getRow(row).getCell(7).getStringCellValue();
                                    ReportSchedule rs = new ReportSchedule(0, period, recipients, nextDue, database1.getId(), onlineReport.getId(), type, parameters, emailSubject);
                                    ReportScheduleDAO.store(rs);
                                }
                            }
                            row++;
                        }
                    }
                    opcPackage.revert();
                    // this chunk moved from ImportService - perhaps it could be moved from here but
                    model.put("error", "Report schedules file uploaded");
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                }
            }
            model.put("users", AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser));
            AdminService.setBanner(model, loggedInUser);
            return "managereportschedules";
        } else {
            return "redirect:/api/Login";
        }
    }


}