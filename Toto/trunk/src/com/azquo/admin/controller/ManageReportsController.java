package com.azquo.admin.controller;

import com.azquo.ExternalConnector;
import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.*;
import com.azquo.dataimport.ImportService;
import com.azquo.dataimport.ImportTemplateDAO;
import com.azquo.dataimport.PendingUpload;
import com.azquo.dataimport.PendingUploadDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.ReportAnalysis;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.poi.hssf.usermodel.HSSFFont;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

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


    @Autowired
    ServletContext servletContext;
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
                                //menuappearance values
            , @RequestParam(value = "menuAppearanceId", required = false) String menuAppearanceId
            , @RequestParam(value = "menuAppearanceDeleteId", required = false) String menuAppearanceDeleteId
            , @RequestParam(value = "submenuName", required = false) String submenuName
            , @RequestParam(value = "importance", required = false) String importance
            , @RequestParam(value = "showname", required = false) String showname
                                //external data request values
            , @RequestParam(value = "externaldatarequestId", required = false) String externaldatarequestId
            , @RequestParam(value = "externaldatarequestDeleteId", required = false) String externaldatarequestDeleteId
            , @RequestParam(value = "sheetRangeName", required = false) String sheetRangeName
            , @RequestParam(value = "connectorName", required = false) String connectorName
            , @RequestParam(value = "readSQL", required = false) String readSQL
            , @RequestParam(value = "saveKeyfield", required = false) String saveKeyfield
            , @RequestParam(value = "saveFilename", required = false) String saveFilename
            , @RequestParam(value = "saveInsertKeyValue", required = false) String saveInsertKeyValue
            , @RequestParam(value = "saveAllowDelete", required = false) String saveAllowDelete
                                //overall report values
              , @RequestParam(value = "createnewreport", required = false) String createNewReport
            , @RequestParam(value = "submit", required = false) String submit
            , @RequestParam(value = "test", required = false) String test
            , @RequestParam(value = "deleteId", required = false) String deleteId

    ) {
        if ("true".equalsIgnoreCase(request.getParameter("testmode"))){
            request.getSession().setAttribute("test", "true");
        }
        if ("false".equalsIgnoreCase(request.getParameter("testmode"))){
            request.getSession().removeAttribute("test");
        }

        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            if (createNewReport!=null){
                model.put("id", "0");
                model.put("databasesSelected",databaseList(loggedInUser,0));

                return "editreport2";
            }
            int reportId = 0;
            if (editId != null) {
                reportId = Integer.parseInt(editId);
            }
            if (deleteId != null) {
                AdminService.removeReportByIdWithBasicSecurity(loggedInUser, Integer.parseInt(deleteId));
                model.put("reports", AdminService.getReportList(loggedInUser, true));
                AdminService.setBanner(model, loggedInUser);
                return "managereports2";
            }
            if (menuAppearanceDeleteId != null) {
                MenuAppearanceDAO.removeById(Integer.parseInt(menuAppearanceDeleteId));
            }
            if (externaldatarequestDeleteId != null) {
                ExternalDataRequestDAO.removeById(Integer.parseInt(externaldatarequestDeleteId));
            }
            if ((submit==null || submit.equals("save")) && (NumberUtils.isDigits(menuAppearanceId) || submenuName != null)) {
                OnlineReport editOr = OnlineReportDAO.findById(reportId);

                MenuAppearance toEdit = null;
                if (menuAppearanceId != null) {
                    toEdit = MenuAppearanceDAO.findById(Integer.parseInt(menuAppearanceId));
                }
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                boolean appearanceEdited = false;
                if (submit != null ) {
                    submit = null;
                    if (submenuName == null || submenuName.isEmpty()) {
                        error.append("Role/Menu name required<br/>");
                    }
                    int importanceVal = 0;
                    try {
                        importanceVal = Integer.parseInt(importance);
                    } catch (Exception e) {
                        error.append("importance must be a number<br/>");
                    }
                    int menuAppearance = 0;
                    if (menuAppearanceId != null) {
                        menuAppearance = Integer.parseInt(menuAppearanceId);
                    }
                    if (error.length() == 0) {
                        // then store, it might be new
                        if (menuAppearance == 0) {
                            MenuAppearanceDAO.store(new MenuAppearance(0, loggedInUser.getBusiness().getId(), reportId, submenuName, importanceVal, showname));
                        } else {
                            toEdit.setSubmenuName(submenuName);
                            toEdit.setImportance(importanceVal);

                            toEdit.setShowname(showname);
                            MenuAppearanceDAO.store(toEdit);
                        }
                        appearanceEdited = true;
                    } else {
                        model.put("error", error.toString());
                    }

                    model.put("id", menuAppearanceId);
                    model.put("reportid", editId);
                    model.put("reportname", editOr.getReportName());
                    model.put("submenuname", submenuName);
                    model.put("importance", importance);
                     model.put("showname", showname);
                } else ;
                {
                    if (toEdit != null) {
                        model.put("reportid", editId);
                        model.put("id", menuAppearanceId);
                        model.put("reportname", editOr.getReportName());
                        model.put("submenuName", toEdit.getSubmenuName());
                        model.put("importance", toEdit.getImportance());
                        model.put("showname", toEdit.getShowname());
                    } else {
                        model.put("reportid", editId);
                        model.put("id", "0");
                        model.put("reportname", editOr.getReportName());
                    }
                }
                if (!appearanceEdited) {
                    return "editmenuappearance";
                }
            }
            if ((submit==null || submit.equals("save")) && (NumberUtils.isDigits(externaldatarequestId) || sheetRangeName != null)) {
                OnlineReport editOr = OnlineReportDAO.findById(reportId);

                ExternalDataRequest toEdit = null;
                if (externaldatarequestId != null) {
                    toEdit = ExternalDataRequestDAO.findById(Integer.parseInt(externaldatarequestId));
                }
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                boolean requestEdited = false;
                if (submit != null) {
                    submit = null;
                    if (sheetRangeName == null || sheetRangeName.isEmpty()) {
                        error.append("Sheet or range name required<br/>");
                    }
                    ExternalDatabaseConnection externalConnector = null;
                    try {
                        if (connectorName != null && !connectorName.isEmpty()) {
                            externalConnector = ExternalDatabaseConnectionDAO.findForNameAndBusinessId(connectorName.trim(), loggedInUser.getBusiness().getId());
                        }
                    } catch (Exception e) {

                    }
                    if (externalConnector == null && readSQL != null && !readSQL.isEmpty()) {
                        error.append("no connector called " + connectorName + " is set up<br/>");
                    }
                    if (externalConnector != null && (readSQL == null || !readSQL.toLowerCase(Locale.ROOT).startsWith("select "))) {
                        error.append("the read SQL should start 'select ...'<br/>");
                    }
                    String allowDelete = "n";
                    boolean isDelete = false;
                    if (saveAllowDelete != null && !saveAllowDelete.isEmpty()) {
                        allowDelete = saveAllowDelete.toLowerCase(Locale.ROOT).substring(0, 1);
                    }
                    if (allowDelete.charAt(0) != 'y' && allowDelete.charAt(0) != 'n') {
                        error.append("Allow delete must start 'Y' or 'N'<br/>");
                    }
                    if (allowDelete.charAt(0)=='y'){
                        isDelete = true;
                    }
                    int externalConnectorId = 0;
                    if (externalConnector != null) {
                        externalConnectorId = externalConnector.getId();
                    }
                    if (error.length() == 0) {
                        // then store, it might be new
                        if (toEdit == null) {
                            ExternalDataRequestDAO.store(new ExternalDataRequest(0, reportId, sheetRangeName, externalConnectorId, readSQL, saveKeyfield, saveFilename, saveInsertKeyValue, isDelete));
                        } else {
                            toEdit.setSheetRangeName(sheetRangeName);
                            toEdit.setConnectorId(externalConnector.getId());
                            toEdit.setReadSQL(readSQL);
                            toEdit.setSaveKeyfield(saveKeyfield);
                            toEdit.setSaveFilename(saveFilename);
                            toEdit.setSaveInsertkeyValue(saveInsertKeyValue);
                            toEdit.setAllowDelete(isDelete);
                            ExternalDataRequestDAO.store(toEdit);
                        }
                        requestEdited = true;
                    } else {
                        model.put("error", error.toString());
                    }

                    model.put("id", externaldatarequestId);
                    model.put("reportid", editId);
                    model.put("reportname", editOr.getReportName());
                    model.put("sheetRangeName", sheetRangeName);
                    model.put("connectorName", connectorName);
                    model.put("readSQL", readSQL);
                    model.put("saveKeyfield", saveKeyfield);
                    model.put("saveFilename", saveFilename);
                    model.put("saveInsertKeyValue", saveInsertKeyValue);
                    model.put("saveAllowDelete", saveAllowDelete);
                } else ;
                {
                    if (toEdit != null) {
                        ExternalDatabaseConnection externalDatabaseConnection = ExternalDatabaseConnectionDAO.findById(toEdit.getConnectorId());
                        model.put("reportid", editId);
                        model.put("id", externaldatarequestId);
                        model.put("reportname", editOr.getReportName());
                        model.put("sheetRangeName", toEdit.getSheetRangeName());
                        model.put("connectorName", externalDatabaseConnection.getName());
                        model.put("readSQL", fixQuotes(toEdit.getReadSQL()));
                        model.put("saveKeyfield", toEdit.getSaveKeyfield());
                        model.put("saveFilename", toEdit.getSaveFilename());
                        model.put("saveInsertKeyValue", toEdit.getSaveInsertKeyValue());
                        model.put("saveAllowDelete", toEdit.getAllowDelete()?"Y":"N");
                    } else {
                        model.put("reportid", editId);
                        model.put("id", "0");
                        model.put("reportname", editOr.getReportName());
                    }
                }
                if (!requestEdited) {
                    return "editexternaldatarequest";
                }
            }
            if (editId != null) {
                final OnlineReport theReport = OnlineReportDAO.findById(Integer.parseInt(editId)); // yes could exception, don't care at the mo
                if (theReport != null && ((loggedInUser.getUser().isAdministrator() && theReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                        || (loggedInUser.getUser().isDeveloper() && theReport.getUserId() == loggedInUser.getUser().getId()))) {
                    // ok check to see if data was submitted
                     final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(theReport.getId());
                     model.put("id", editId);
                    model.put("databasesSelected", databaseList(loggedInUser,theReport.getId()));
                    model.put("newReportName", theReport.getReportName());
                    model.put("category", theReport.getCategory());
                    model.put("explanation",theReport.getExplanation());
                    model.put("file", theReport.getFilename());
                     model.put("menuappearances", MenuAppearanceDAO.findForReportId(reportId));
                    model.put("externaldatarequests", ExternalDataRequestDAO.findForReportId(reportId));
                    AdminService.setBanner(model, loggedInUser);
                    return "editreport2";
                }
            }
            // if not editing then very simple
            List<OnlineReport> reportList = AdminService.getReportList(loggedInUser, true);
            boolean showExplanation = false;
            for (OnlineReport or : reportList) {
                if (or.getExplanation() != null && !or.getExplanation().isEmpty()) {
                    showExplanation = true;
                    break;
                }
            }
            if ("test".equals(test)) {
                Workbook wb = new XSSFWorkbook();
                Sheet analysis = wb.createSheet("Analysis");
                analysis.setColumnWidth(0, 256 * 40); // 256 per character . . .
                analysis.setColumnWidth(1, 256 * 40);
                analysis.setColumnWidth(2, 256 * 40);
                int rowIndex = 0;
                int cellIndex = 0;
                for (OnlineReport or : reportList) {
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
            model.put("showexplanation", showExplanation);
            AdminService.setBanner(model, loggedInUser);



            if (request.getParameter("newdesign") != null){
                try {
                    StringBuilder reportsList = new StringBuilder();
                    for (OnlineReport or : reportList){
                        reportsList.append("new m({ id: " + or.getId() + ", name: \"" + or.getReportName() + "\", database: \"" + or.getDatabase() + "\", author: \"" + or.getAuthor() + "\", description: \"" + or.getExplanation() + "\" }),");
                    }
                    InputStream resourceAsStream = servletContext.getResourceAsStream("/WEB-INF/includes/newappjavascript.js");
                    model.put("newappjavascript", IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8)
                            .replace("###IMPORTSLIST###", "{}")
                            .replace("###DATABASESLIST###", "{}")
                            .replace("###REPORTSLIST###", reportsList.toString()));


                } catch (IOException e) {
                    e.printStackTrace();
                }
                model.put("pageUrl", "/reports");
                return "managereports";
            }
            return "managereports2";
        } else {
            return "redirect:/api/Login";
        }
    }

    private String fixQuotes(String orig){
        return orig.replace("\"","&quot;");
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            ,@RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "newReportName", required = false) String newReportName
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
            , @RequestParam(value = "databaseName", required = false) String databaseName
            , @RequestParam(value = "iframe", required = false) String iframe
            , @RequestParam(value = "submit", required = false) String submit
            , @RequestParam(value = "databaseIdList", required = false) String[] databaseIdList // I think this is correct?
            , @RequestParam(value = "explanation", required = false) String explanation
            , @RequestParam(value = "category", required = false) String category

    ) {

        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        StringBuffer error = new StringBuffer();
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            int editOr = 0;
            OnlineReport theReport = null;
            if (NumberUtils.isDigits(editId)){
                editOr = Integer.parseInt(editId);
                theReport = OnlineReportDAO.findById(editOr);
            }
            if (!submit.equals("cancel")&& newReportName!=null && newReportName.length()>0) {
                if (databaseIdList == null || databaseIdList.length == 0) {
                    databaseName = "DefaultDatabase";
                    Database db = DatabaseDAO.findForNameAndBusinessId(databaseName, loggedInUser.getBusiness().getId());
                    if (db == null) {
                        try {
                            db = AdminService.createDatabase(databaseName, loggedInUser, loggedInUser.getDatabaseServer());
                        } catch (Exception e) {
                            error.append("cannot create a default database</br>");
                            return "editreport2";
                        }
                    }
                    databaseIdList = new String[1];
                    databaseIdList[0] = db.getId()+"";
                    LoginService.switchDatabase(loggedInUser, db);
                }
                if (error.length() > 0) {
                    model.put("error", error);
                    model.put("newReportName", newReportName);
                    model.put("iframe", iframe);
                    model.put("category", category);
                    model.put("explanation", explanation);
                    model.put("id", editId);
                    model.put("databasesSelected", databaseList(loggedInUser,editOr));
                    model.put("newReportName", newReportName);
                    if (editOr > 0) {
                        model.put("file", theReport.getFilename());
                        model.put("menuappearances", MenuAppearanceDAO.findForReportId(editOr));
                        model.put("externaldatarequests", ExternalDataRequestDAO.findForReportId(editOr));
                    }
                    AdminService.setBanner(model, loggedInUser);
                    return "editreport2";
                }
                if (iframe != null && iframe.length() > 0) {
                    OnlineReport or = new OnlineReport(0, LocalDateTime.now(), loggedInUser.getBusiness().getId(), loggedInUser.getUser().getId(), null, newReportName, "IFRAME:" + iframe, "", "");
                    OnlineReportDAO.store(or);
                    DatabaseReportLinkDAO.link(loggedInUser.getDatabase().getId(),or.getId());
                } else {
                    if (uploadFile != null && !uploadFile.isEmpty()) {
                        try {
                            String fileName = uploadFile.getOriginalFilename();
                            File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                            uploadFile.transferTo(moved);
                            Workbook book;
                            UploadedFile uploadedFile = new UploadedFile(moved.getAbsolutePath(), Collections.singletonList(fileName), new HashMap<>(), false, false);
                            // wary of windows locking files, need to see how it goes
                            ImportService.importTheFile(loggedInUser, uploadedFile, request.getSession(), null, "Report name = " + newReportName);
                            theReport = OnlineReportDAO.findForNameAndBusinessId(newReportName, loggedInUser.getBusiness().getId());
                            editOr = theReport.getId();
                        } catch (Exception e) {
                            String exceptionError = e.getMessage();
                            e.printStackTrace();
                            model.put("error", exceptionError);
                        }
                    }
                    if (editOr > 0) {
                        theReport.setExplanation(explanation);
                        theReport.setCategory(category);
                        OnlineReportDAO.store(theReport);
                        DatabaseReportLinkDAO.unLinkReport(theReport.getId());
                        for (String databaseId : databaseIdList) {
                            int dbId = Integer.parseInt(databaseId);
                            Database byId = DatabaseDAO.findById(dbId);
                            if (byId != null && ((loggedInUser.getUser().isAdministrator() && byId.getBusinessId() == loggedInUser.getUser().getBusinessId())
                                    || (loggedInUser.getUser().isDeveloper() && byId.getUserId() == loggedInUser.getUser().getId()))) {
                                DatabaseReportLinkDAO.link(byId.getId(), theReport.getId());
                            }

                        }
                    }
                }
            }
            model.put("reports", AdminService.getReportList(loggedInUser, true));
            AdminService.setBanner(model, loggedInUser);
            return "managereports2";
        } else {
            return "redirect:/api/Login";
        }


    }

    private static List<DatabaseSelected> databaseList(LoggedInUser loggedInUser, int reportId){
        final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(reportId);
        List<DatabaseSelected> databasesSelected = new ArrayList<>();
        final List<Database> forUser = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
        if (forUser != null) {
            for (Database database : forUser) {
                databasesSelected.add(new DatabaseSelected(databaseIdsForReportId.contains(database.getId()), database));
            }
        }
        return databasesSelected;

    }


}