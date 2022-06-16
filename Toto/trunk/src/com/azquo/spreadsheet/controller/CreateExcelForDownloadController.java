package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.ReportSchedule;
import com.azquo.admin.user.User;
import com.azquo.dataimport.WizardField;
import com.azquo.dataimport.WizardInfo;
import com.azquo.spreadsheet.LoggedInUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import io.keikai.api.Exporter;
import io.keikai.api.Exporters;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.SName;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by edward on 19/01/16.
 * <p>
 * To allow downloading of some admin info in an Excel file that can then be reuploaded. Simply a more convenient way of managing things like user accounts.
 */
@Controller
@RequestMapping("/CreateExcelForDownload")
public class CreateExcelForDownloadController {

    @Autowired
    ServletContext servletContext;

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String USERSFILENAME = "AzquoUsers";

    public static final String WIZARDFILENAME = "AzquoImportWizard";

    public static final String REPORTSCHEDULESFILENAME = "AzquoReportSchedules.xlsx";

    @RequestMapping
    public void handleRequest(final HttpServletRequest request, HttpServletResponse response) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isMaster() || loggedInUser.getUser().isAdministrator())) {
            if ("DOWNLOADUSERS".equals(request.getParameter("action"))) { // then limited users editing for a master user
                Book book = Importers.getImporter().imports(servletContext.getResourceAsStream("/WEB-INF/" + USERSFILENAME + ".xlsx"), "Report name");
                // modify book to add the users and permissions
                Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
                if (userSheet != null) {
                    final List<User> userListForBusiness = AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser);
                    if (userListForBusiness != null) {
                        userListForBusiness.sort(Comparator.comparing(User::getEmail));
                    }
                    int row = 1;
                    SName listRegion = book.getInternalBook().getNameByName("az_ListStart");
                    SName business = book.getInternalBook().getNameByName("az_Business");
                    if (listRegion != null) {
                        row = listRegion.getRefersToCellRegion().getRow();
                    }
                    Business businessById = null;
                    for (User user : userListForBusiness) {
                        if (loggedInUser.getUser().getStatus().equals("ADMINISTRATOR") || loggedInUser.getUser().getStatus().equals("MASTER") || loggedInUser.getUser().getEmail().equals(user.getCreatedBy())) {
                            // todo don't show admins if not admin
                            userSheet.getInternalSheet().getCell(row, 0).setStringValue(user.getEmail());
                            userSheet.getInternalSheet().getCell(row, 1).setStringValue(user.getName());
                            //password not there
                            userSheet.getInternalSheet().getCell(row, 3).setStringValue(dateTimeFormatter.format(user.getEndDate()));
                            businessById = AdminService.getBusinessById(user.getBusinessId());
                            userSheet.getInternalSheet().getCell(row, 4).setStringValue(user.getStatus());
                            final Database databaseById = DatabaseDAO.findById(user.getDatabaseId());
                            userSheet.getInternalSheet().getCell(row, 5).setStringValue(databaseById != null ? databaseById.getName() : "");
                            final OnlineReport reportById = OnlineReportDAO.findById(user.getReportId());
                            userSheet.getInternalSheet().getCell(row, 6).setStringValue(reportById != null ? reportById.getReportName() : "");
                            userSheet.getInternalSheet().getCell(row, 7).setStringValue(user.getSelections() != null ? user.getSelections() : "");
                            row++;
                        }
                    }
                    if (business != null && businessById != null) {
                        book.getSheetAt(0).getInternalSheet().getCell(business.getRefersToCellRegion().getRow(), business.getRefersToCellRegion().getColumn()).setStringValue(businessById.getBusinessName());
                    }
                }
                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=" + USERSFILENAME + ".xlsx");
                OutputStream out = response.getOutputStream();
                Exporter exporter = Exporters.getExporter();
                exporter.export(book, out);
            }else if ("DOWNLOADIMPORTWIZARD".equals(request.getParameter("action"))) {
                Book book = Importers.getImporter().imports(servletContext.getResourceAsStream("/WEB-INF/" + WIZARDFILENAME + ".xlsx"), "Report name");
                // modify book to add the users and permissions
                Sheet importSheet = book.getSheet("Import"); // literals not best practice, could it be factored between this and the xlsx file?
                if (importSheet != null) {
                    int row = 0;
                    int col = 0;
                    WizardInfo wizardInfo = loggedInUser.getWizardInfo();
                    SName headingRegion = book.getInternalBook().getNameByName("az_Headings");
                    SName business = book.getInternalBook().getNameByName("az_Business");
                    if (headingRegion != null) {
                        row = headingRegion.getRefersToCellRegion().getRow() + 1;
                        col = headingRegion.getRefersToCellRegion().getColumn();
                    }
                    Business businessById = null;
                    for (String field: wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        if (!wizardField.getIgnore()) {
                            importSheet.getInternalSheet().getCell(row, col).setStringValue(wizardField.getImportedName());
                            importSheet.getInternalSheet().getCell(row, col + 1).setStringValue(wizardField.getName());
                            if (wizardField.getAdded()) {
                                importSheet.getInternalSheet().getCell(row, col + 2).setStringValue("added");
                            }
                            importSheet.getInternalSheet().getCell(row, col + 3).setStringValue(wizardField.getInterpretation());
                            row++;
                        }
                    }
                    if (business != null && businessById != null) {
                        book.getSheetAt(0).getInternalSheet().getCell(business.getRefersToCellRegion().getRow(), business.getRefersToCellRegion().getColumn()).setStringValue(businessById.getBusinessName());
                    }
                }
                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=" + WIZARDFILENAME + ".xlsx");
                OutputStream out = response.getOutputStream();
                Exporter exporter = Exporters.getExporter();
                exporter.export(book, out);
            } else if ("DOWNLOADREPORTSCHEDULES".equals(request.getParameter("action"))) { // then limited users editing for a master user
                Book book = Importers.getImporter().imports(servletContext.getResourceAsStream("/WEB-INF/" + REPORTSCHEDULESFILENAME), "Report name");
                // modify book to add the user
                Sheet schedulesSheet = book.getSheet("ReportSchedules");
                final List<ReportSchedule> reportSchedules = AdminService.getReportScheduleList(loggedInUser);
                if (schedulesSheet != null) {
                    int row = 1;
                    SName listRegion = book.getInternalBook().getNameByName("data");
                    if (listRegion != null) {
                        row = listRegion.getRefersToCellRegion().getRow();
                    }
                    for (ReportSchedule reportSchedule : reportSchedules) {
                        final Database databaseById = AdminService.getDatabaseByIdWithBasicSecurityCheck(reportSchedule.getDatabaseId(), loggedInUser);
                        final OnlineReport reportById = AdminService.getReportByIdWithBasicSecurityCheck(reportSchedule.getReportId(), loggedInUser);
                        if (databaseById != null && reportById != null) {
                            schedulesSheet.getInternalSheet().getCell(row, 0).setStringValue(reportSchedule.getPeriod());
                            schedulesSheet.getInternalSheet().getCell(row, 1).setStringValue(reportSchedule.getRecipients());
                            schedulesSheet.getInternalSheet().getCell(row, 2).setStringValue(dateTimeFormatter.format(reportSchedule.getNextDue()));
                            schedulesSheet.getInternalSheet().getCell(row, 3).setStringValue(databaseById.getName());
                            schedulesSheet.getInternalSheet().getCell(row, 4).setStringValue(reportById.getReportName());
                            schedulesSheet.getInternalSheet().getCell(row, 5).setStringValue(reportSchedule.getType());
                            schedulesSheet.getInternalSheet().getCell(row, 6).setStringValue(reportSchedule.getParameters());
                            schedulesSheet.getInternalSheet().getCell(row, 7).setStringValue(reportSchedule.getEmailSubject());
                            row++;
                        }
                    }
                }
                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=" + REPORTSCHEDULESFILENAME);
                OutputStream out = response.getOutputStream();
                Exporter exporter = Exporters.getExporter();
                exporter.export(book, out);
            }
        } else {
            response.sendRedirect("/api/Login");
        }
    }
}