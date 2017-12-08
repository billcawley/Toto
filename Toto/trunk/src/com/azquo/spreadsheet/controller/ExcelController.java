package com.azquo.spreadsheet.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.FilterTriple;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.azquo.spreadsheet.controller.LoginController.LOGGED_IN_USER_SESSION;

/**
 * Created by edward on 29/03/16.
 * <p>
 * For API style access, I'm going to copy the online controller and modify
 */
@Controller
@RequestMapping("/Excel")
public class ExcelController {
    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;
    public static final Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it

    // note : I'm now thinking dao objects are acceptable in controllers if moving the call to the service would just be a proxy
    /* Parameters are sent as Json . . . posted via https should be secure enough for the moment */

    public static class DatabaseReport implements Serializable{
        public String sheetName;
        public String reportName;
        public String database;

        public DatabaseReport(String sheetName, String reportName, String database){
            this.sheetName = sheetName;
            this.reportName = reportName;
            this.database = database;
        }

        public String getSheetName(){return this.sheetName;};
        public String getReportName(){return this.reportName;};
        public String getDatabase(){return this.database;}
     }


     public static class LoginInfo{
        public String sessionId;
        public String userType;

        public LoginInfo(String sessionId, String userType){
            this.sessionId = sessionId;
            this.userType = userType;
        }
     }
    public static class MultiChoice{
        public String choice;
        public boolean chosen;
        public int id;

        public MultiChoice(String choice, boolean chosen){
            this.choice = choice;
            this.id = id;
            this.chosen = chosen;
        }
    }


    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String BOOK_PATH = "BOOK_PATH";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";



    @RequestMapping
    @ResponseBody
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "reportname", required = false) String reportName
            , @RequestParam(value = "logon", required = false) String logon
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "region", required = false) String region
            , @RequestParam(value = "regionrow", required = false) String regionrow
            , @RequestParam(value = "regioncol", required = false) String regioncol
            , @RequestParam(value = "choice", required = false) String choice
            , @RequestParam(value = "chosen", required = false) String chosen
            , @RequestParam(value = "template", required = false) String template

    ) {


        String result = "no action taken";
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "application/json");
        final ObjectMapper jacksonMapper = new ObjectMapper();

        try {

             try {
                LoggedInUser loggedInUser = null;
                if (sessionId!=null){
                    loggedInUser = excelConnections.get(sessionId);
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);

                }
                if (loggedInUser == null) {
                    loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), database, logon, password, false);
                    if (loggedInUser == null) {
                        return jsonError("incorrect login details");
                    }else {
                         //find existing if already logged in, and remember the current report, database, server
                        boolean newUser = true;
                        for (String existingSessionId : excelConnections.keySet()){
                            LoggedInUser existingUser = excelConnections.get(existingSessionId);
                            if (existingUser.getUser().getId()==loggedInUser.getUser().getId()){
                                excelConnections.put(request.getSession().getId() ,existingUser);
                                if (!existingSessionId.equals(request.getSession().getId())){
                                    excelConnections.remove(existingSessionId);
                                }
                                loggedInUser = existingUser;
                                newUser = false;
                            }
                        }
                        request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                        if (newUser){

                            excelConnections.put(request.getSession().getId() + "", loggedInUser);
                        }
                        if (op.equals("logon")) {
                            /*
                            List<OnlineReport> reports = AdminService.getReportList(loggedInUser);
                            List<JsonReturn> reportList = new ArrayList<>();
                            for (OnlineReport report:reports){
                                reportList.add(new JsonReturn(null,report.getDatabase(),report.getReportName(), null,null));
                            }
                            String returnString = "{\"error\":\"\",\"sessionid\":\"" + request.getSession().getId() + "\",\"reports\":" + jacksonMapper.writeValueAsString(reportList) + "}";
                            return returnString;
                            */
                            LoginInfo li = new LoginInfo(request.getSession().getId(), loggedInUser.getUser().getStatus());
                            return jacksonMapper.writeValueAsString(li);
                        }
                    }
                }
                if (database!=null && database.length() > 0){
                    LoginService.switchDatabase(loggedInUser,database);
                }
                if (reportName!=null && reportName.length() > 0){
                    loggedInUser.setOnlineReport(OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(),reportName.trim()));
                }
                if (op.equals("admin")){
                    //ManageDatabasesController.handleRequest(request);
                      response.sendRedirect("/api/ManageDatabases");
                      return "";

                 }
                if (op.equals("audit")) {
                    UserRegionOptions userRegionOptions = ExcelService.getUserRegionOptions(loggedInUser,"",loggedInUser.getOnlineReport().getId(),region);
                    jacksonMapper.registerModule(new JavaTimeModule());
                    jacksonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


                    //jacksonMapper.registerModule(new JavaTimeModule());
                    final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, loggedInUser.getOnlineReport().getId(), reportName,
                            region, userRegionOptions, Integer.parseInt(regionrow), Integer.parseInt(regioncol), 1000);
                    if (provenanceDetailsForDisplay.getAuditForDisplayList() != null && !provenanceDetailsForDisplay.getAuditForDisplayList().isEmpty()) {
                         return jacksonMapper.writeValueAsString(provenanceDetailsForDisplay);
                    }
                    return(jsonError("no details"));
                        //buildContextMenuProvenanceDownload(provenanceDetailsForDisplay, reportId);
                }
                if (op.equals("getchoices")){
                    List<FilterTriple> filterOptions = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                            .getFilterListForQuery(loggedInUser.getDataAccessToken(), choice, chosen, loggedInUser.getUser().getEmail(), loggedInUser.getLanguages());//choice is the name of the range, chosen= the value from the 'choice' cell
                    return jacksonMapper.writeValueAsString(filterOptions);
                }

                if (op.equals("setchoices")){
                    List<Integer>childIds = jacksonMapper.readValue(chosen,jacksonMapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
                    RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), choice, loggedInUser.getUser().getEmail(), childIds);
                    return jsonError("done");

                }
                if (op.equals("setchoice")) {
                         SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choice, chosen);
                         return jsonError("done");
                }
                 if (op.equals("sethighlight")) {
                    int hDays = 0;
                    if (chosen.toLowerCase().contains(" day")){
                        hDays = Integer.parseInt(chosen.substring(0,chosen.toLowerCase().indexOf(" day")));
                    }
                    if (loggedInUser.getOnlineReport()==null || region==null||region.length()==0){
                         return "not enough details to highlight";
                     }
                     UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), loggedInUser.getOnlineReport().getId(), region);
                     if (userRegionOptions == null) {
                          userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), loggedInUser.getOnlineReport().getId(), region, "highlight=" + hDays + "\n");
                         UserRegionOptionsDAO.store(userRegionOptions);
                     } else {
                         if (userRegionOptions.getHighlightDays() != hDays){
                             userRegionOptions.setHighlightDays(hDays);
                             UserRegionOptionsDAO.store(userRegionOptions);
                         }
                     }
                     return jsonError("done");
                 }
                 // database switching should be done by being logged in
                 String downloadName = "";

                 if (op.equals("download")) {
                     try{
                         File file = null;
                         OnlineReport onlineReport = null;
                         if ((reportName==null || reportName.length()==0) && (database==null||database.length()==0)){
                              //get initial menu
                             List<OnlineReport> reports = AdminService.getReportList(loggedInUser);
                             if (reports.size()==1){
                                 onlineReport = reports.get(0);
                                 downloadName = onlineReport.getReportName();
                             }else{
                                 //list the reports
                                 file = ExcelService.listReports(request,reports);
                                 downloadName = "Available reports";
                             }

                         }else {

                             reportName = reportName.trim();

                             if (reportName.contains(" uploaded by")){
                                 reportName = reportName.substring(0,reportName.indexOf(" uploaded by"));
                             }
                              if (reportName == null || reportName.length() == 0) {
                                 onlineReport = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                                 if (onlineReport != null) {
                                     onlineReport.setDatabase(loggedInUser.getDatabase().getName());
                                 }
                             } else {
                                  if (database==null || database.length()==0){
                                      database = loggedInUser.getDatabase().getName();
                                  }
                                 if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                                     //report id is assumed to be integer - sent from the website
                                     onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                                     onlineReport.setDatabase(database);
                                 }else{
                                      Database db = DatabaseDAO.findForNameAndBusinessId(database,loggedInUser.getUser().getBusinessId());
                                      onlineReport = OnlineReportDAO.findForDatabaseIdAndName(db.getId(),reportName);
                                      boolean allowed = false;
                                      for (String key:loggedInUser.getPermissionsFromReport().keySet() ){
                                          TypedPair<OnlineReport,Database> dbreport = loggedInUser.getPermissionsFromReport().get(key);
                                          if (dbreport.getFirst().getId()==onlineReport.getId() && dbreport.getSecond().getId()==db.getId()){
                                              allowed = true;
                                              onlineReport.setDatabase(db.getName());
                                              break;
                                          }
                                      }
                                      if (!allowed){
                                          onlineReport = null;
                                      }
                                 }

                             }
                             if (onlineReport!=null){
                                 downloadName = onlineReport.getReportName();
                             }
                         }
                         if (downloadName.length()==0) {
                             response.setHeader("Access-Control-Allow-Origin", "*");
                             response.setHeader("Content-type", "application/json");
                             return jsonError("incorrect report name"); // probably need to add json
                         }
                         boolean isTemplate = false;
                         if (template!=null && template.equals("true")) isTemplate = true;
                         if (file==null){
                             file = ExcelService.createReport(loggedInUser,onlineReport, isTemplate);
                         }

                         SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh.mm.ss");
                         response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // Set up mime type
                         try {
                             DownloadController.streamFileToBrowser(file.toPath(), response, downloadName + " " + df.format(new Date()) + ".xlsx");
                         } catch (IOException ex) {
                             ex.printStackTrace();
                         }
                         return jsonError("OK");


                     } catch (Exception e) { // changed to overall exception handling
                         e.printStackTrace(); // Could be when importing the book, just log it
                         result = e.getMessage(); // put it here to puck up instead of the report
                     }
                 }
                 if (op.equals("allowedreports")){

                     List<DatabaseReport> databaseReports = new ArrayList<>();
                     if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()){
                         List<OnlineReport>reports = AdminService.getReportList(loggedInUser);
                         for (OnlineReport or:reports){
                             databaseReports.add(new DatabaseReport(or.getDatabase()+ " :   "+or.getReportName(), or.getReportName(),or.getDatabase()));
                         }

                     }else{
                         Map<String,TypedPair<OnlineReport, Database>> permitted = loggedInUser.getPermissionsFromReport();
                         for (String st:permitted.keySet()){
                             TypedPair<OnlineReport,Database> tp = permitted.get(st);
                              databaseReports.add(new DatabaseReport(st, tp.getFirst().getReportName(), tp.getSecond().getName()));
                         }
                     }
                     return jacksonMapper.writeValueAsString(databaseReports);
                 }
             } catch (Exception e) {
                 logger.error("online controller error", e);
                 result = e.getMessage();
             }
        } catch (NullPointerException npe) {
            result = "null pointer, json passed?";
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return jsonError(result);

    }

    String jsonError(String error){
        return "{\"error\":\""+error+"\"}";

    }

  }