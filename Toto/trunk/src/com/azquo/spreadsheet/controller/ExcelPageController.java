package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.ExcelService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.util.List;

import static com.azquo.spreadsheet.controller.LoginController.LOGGED_IN_USER_SESSION;

@Controller
@RequestMapping("/ExcelPage")
public class ExcelPageController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "reportname", required = false) String reportName
            , @RequestParam(value = "sheetname", required = false) String sheetName
            , @RequestParam(value = "region", required = false) String region
            , @RequestParam(value = "regionrow", required = false) String regionrow
            , @RequestParam(value = "regioncol", required = false) String regioncol
            , @RequestParam(value = "choice", required = false) String choice
            , @RequestParam(value = "chosen", required = false) String chosen

    ) {


        String result = "no action taken";
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "application/json");
        final ObjectMapper jacksonMapper = new ObjectMapper();

        try {

            LoggedInUser loggedInUser = null;
            if (sessionId != null) {
                loggedInUser = ExcelController.excelConnections.get(sessionId);
                if (loggedInUser==null){
                    return "no session";
                }
                request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
             }
             if (database != null && database.length() > 0) {
                LoginService.switchDatabase(loggedInUser, database);
            }else{
                database = loggedInUser.getDatabase().getName();
             }

            if (reportName != null && reportName.length() > 0) {
                loggedInUser.setOnlineReport(OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName.trim()));
            }
            if (op.equals("admin")) {
                //ManageDatabasesController.handleRequest(request);
                response.sendRedirect("/api/ManageDatabases");
                return "";

            }

            if (op.equals("inspect")){
                loggedInUser.userLog("Inspect DB : " + database);

                if (database != null && database.length() > 0) {
                    Database newDB = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                    LoginService.switchDatabase(loggedInUser, newDB);
                    database = newDB.getName();
                }
                model.addAttribute("database",database);
                model.addAttribute("op","inspect");
                model.addAttribute("sessionid",sessionId);
                model.addAttribute("audit","{}");
                return "Excel";

            }

            if (op.equals("audit")) {
                UserRegionOptions userRegionOptions = ExcelService.getUserRegionOptions(loggedInUser, "", loggedInUser.getOnlineReport().getId(), region);
                jacksonMapper.registerModule(new JavaTimeModule());
                jacksonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


                //jacksonMapper.registerModule(new JavaTimeModule());
                final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, loggedInUser.getOnlineReport().getId(), sheetName,
                        region, userRegionOptions, Integer.parseInt(regionrow), Integer.parseInt(regioncol), 1000);
                if (provenanceDetailsForDisplay.getAuditForDisplayList() != null && !provenanceDetailsForDisplay.getAuditForDisplayList().isEmpty()) {
                    model.addAttribute("audit", jacksonMapper.writeValueAsString(provenanceDetailsForDisplay));
                    model.addAttribute("op", "audit");
                    return ("Excel");
                }
                return "no details";
                //buildContextMenuProvenanceDownload(provenanceDetailsForDisplay, reportId);
            }

        } catch (Exception e) {
            return "online controller error";
        }
        return "";


    }
}
