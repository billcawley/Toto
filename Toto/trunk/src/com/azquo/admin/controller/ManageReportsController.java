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
import org.springframework.security.access.method.P;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zkoss.poi.hssf.usermodel.HSSFFont;
import org.zkoss.poi.hssf.util.HSSFColor;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * Delete/updating of reports. Simple as all on one page, a list. May need pagination at some point.
 */

@Controller
@RequestMapping("/ManageReports")
public class ManageReportsController {

    //private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    public static class DatabaseSelected {
        private final boolean selected;
        private final Database database;

        public DatabaseSelected(boolean selected, Database database) {
            this.selected = selected;
            this.database = database;
        }

        public boolean isSelected() {
            return selected;
        }

        public Database getDatabase() {
            return database;
        }
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response,
                                @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "databaseIdList", required = false) String[] databaseIdList // I think this is correct?
            , @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "explanation", required = false) String explanation
            , @RequestParam(value = "category", required = false) String category
            , @RequestParam(value = "submit", required = false) String submit
            , @RequestParam(value = "test", required = false) String test
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (deleteId != null) {
                AdminService.removeReportByIdWithBasicSecurity(loggedInUser, Integer.parseInt(deleteId));
            }
            if (editId != null) {
                final OnlineReport theReport = OnlineReportDAO.findById(Integer.parseInt(editId)); // yes could exception, don't care at the mo
                if (theReport != null && ((loggedInUser.getUser().isAdministrator() && theReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                        || (loggedInUser.getUser().isDeveloper() && theReport.getUserId() == loggedInUser.getUser().getId()))) {
                    // ok check to see if data was submitted
                    if (submit != null) {
                        // to keep intelliJ happy, I'm not sure if this is a good idea or not? I suppose protects against logic above being changed unpredictably
                        DatabaseReportLinkDAO.unLinkReport(theReport.getId());
                        if (databaseIdList != null){
                            for (String databaseId : databaseIdList) {
                                int dbId = Integer.parseInt(databaseId);
                                Database byId = DatabaseDAO.findById(dbId);
                                if (byId != null && ((loggedInUser.getUser().isAdministrator() && byId.getBusinessId() == loggedInUser.getUser().getBusinessId())
                                        || (loggedInUser.getUser().isDeveloper() && byId.getUserId() == loggedInUser.getUser().getId()))){
                                    DatabaseReportLinkDAO.link(byId.getId(), theReport.getId());
                                }
                            }
                        }
                        theReport.setReportName(name);
                        theReport.setExplanation(explanation);
                        theReport.setCategory(category);
                        OnlineReportDAO.store(theReport);
                        model.put("reports", AdminService.getReportList(loggedInUser, true));
                        AdminService.setBanner(model,loggedInUser);
                        if (request.getSession().getAttribute("newui") != null){
                            return "managereports2";
                        }
                        return "managereports";
                    }
                    final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(theReport.getId());
                    model.put("id", editId);
                    List<DatabaseSelected> databasesSelected = new ArrayList<>();
                    final List<Database> forUser = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
                    if (forUser != null){
                        for (Database database : forUser) {
                            databasesSelected.add(new DatabaseSelected(databaseIdsForReportId.contains(database.getId()), database));
                        }
                    }
                    model.put("databasesSelected", databasesSelected);
                    model.put("name", theReport.getReportName());
                    model.put("file", theReport.getFilename());
                    model.put("category", theReport.getCategory() != null ?theReport.getCategory() : "");
                    model.put("explanation", theReport.getExplanation() != null ? theReport.getExplanation() : "");
                    AdminService.setBanner(model,loggedInUser);
                    if (request.getSession().getAttribute("newui") != null){
                        return "editreport2";
                    }
                    return "editreport";
                }
            }
            // if not editing then very simple
            List<OnlineReport> reportList = AdminService.getReportList(loggedInUser, true);
            if ("test".equals(test)){
                Workbook wb = new XSSFWorkbook();
                Sheet analysis = wb.createSheet("Analysis");
                analysis.setColumnWidth(0, 256*40); // 256 per character . . .
                analysis.setColumnWidth(1, 256*40);
                analysis.setColumnWidth(2, 256*40);
                int rowIndex = 0;
                int cellIndex = 0;
                for (OnlineReport or : reportList){
                    Row row = analysis.createRow(rowIndex++);
                    Cell cell = row.createCell(cellIndex);
                    cell.setCellValue(or.getReportName());

                    CellStyle style = wb.createCellStyle();
                    Font font = wb.createFont();
                    font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
                    //font.setFontHeightInPoints((short)(20*20));
                    style.setFont(font);
                    cell.setCellStyle(style);


                    cellIndex = 0;
                    analysis.createRow(rowIndex++);
                    try {
                        rowIndex = ReportAnalysis.analyseReport(loggedInUser, or, rowIndex, analysis);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    analysis.createRow(rowIndex++);
                }
                response.setContentType("application/vnd.ms-excel");
                response.setHeader("Content-Disposition", "inline; filename=\"ReportAnalysis.xlsx\"");
                try {
                    ServletOutputStream outputStream = response.getOutputStream();
                    wb.write(outputStream);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            model.put("reports", reportList);
            model.put("developer", loggedInUser.getUser().isDeveloper());
            AdminService.setBanner(model,loggedInUser);
            if (request.getSession().getAttribute("newui") != null){
                return "managereports2";
            }
            return "managereports";
        } else {
            return "redirect:/api/Login";
        }
    }
}