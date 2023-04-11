package com.azquo.spreadsheet.controller;

import com.azquo.SessionListener;
import com.azquo.admin.AdminService;
import com.azquo.admin.DisplayService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.controller.PendingUploadController;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import serilogj.Log;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by edward on 29/03/16.
 * <p>
 * For API style access, I'm going to copy the online controller and modify
 */
@Controller
@RequestMapping("/Display")
public class DisplayController {

    @Autowired
    ServletContext servletContext;
    public static final Map<String, LoggedInUser> reactConnections = new ConcurrentHashMap<>();// simple, for the moment should do it
    // ok we need support for a user being in more than one business
    public static final Map<String, List<LoggedInUser>> reactMultiUserConnections = new ConcurrentHashMap<>();


    /* Parameters are sent as Json . . . posted via https should be secure enough for the moment */

    @ResponseBody
    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "files", required = false) MultipartFile[] uploadFiles


    ) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "application/json");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String,Object>toReturn = new HashMap<>();
        if (op.length()==0) {

            //return theme only
            ImportTemplateData importTemplateData = loadMetaData(null, request);
            Map<String, Object> permissions = DisplayService.sheetInfoToObject(importTemplateData);
            Map<String, String> theme = (Map<String, String>) permissions.get("Theme");
            Map<String,Object> items = new HashMap<>();
            items.put("theme", theme);
            return DisplayService.reactVersion(items,"show");
        }
        String fieldId = java.net.URLDecoder.decode(request.getParameter("fieldid"));
        String fieldValue = java.net.URLDecoder.decode(request.getParameter("fieldvalue"));
        LoggedInUser loggedInUser = null;
        if ("login".equals(op)){
            try {

                List<LoggedInUser> users = LoginService.loginLoggedInUser(null, fieldId, fieldValue);
                if (users.size() > 0) {
                    List<String>businessNames = new ArrayList<>();
                         loggedInUser = users.get(0);
                    ImportTemplateData importTemplateData = loadMetaData(loggedInUser, request);
                    Map<String, Object> permissions = DisplayService.sheetInfoToObject(importTemplateData);
                    loggedInUser.setPermissions(permissions);
                    if (sessionId!=null){
                        reactConnections.put(sessionId,loggedInUser);
                    }
                    if (users.size()>1){
                        for (LoggedInUser lu:users) {
                            reactMultiUserConnections.put(request.getSession().getId(), users);
                            businessNames.add(lu.getBusiness().getBusinessName());
                        }
                         toReturn.put("businesses",businessNames);
                        toReturn.put("theme",permissions.get("Theme"));
                        toReturn.put("sessionid",request.getSession().getId());
                        return DisplayService.reactVersion(toReturn,"show");



                    }
                    DisplayService.recordConnection(reactConnections,loggedInUser,sessionId);
                    op="fieldchanged";
                   fieldId="action:";//to return the dashboard screen
                }
            }catch(Exception e){
                return reactError("server exception");
            }
        }
        try {
           if (loggedInUser == null){
               loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
           }

            if (loggedInUser == null && sessionId != null) {
                loggedInUser = reactConnections.get(sessionId);
                if (loggedInUser==null && sessionId.length()>0) {
                    List<LoggedInUser> users = reactMultiUserConnections.get(sessionId);
                    for (LoggedInUser lu : users) {
                        if (lu.getBusiness().getBusinessName().equals(fieldValue)) {
                            loggedInUser = lu;
                            DisplayService.recordConnection(reactConnections, loggedInUser, sessionId);
                            ImportTemplateData importTemplateData = loadMetaData(loggedInUser, request);
                            Map<String, Object> permissions = DisplayService.sheetInfoToObject(importTemplateData);
                            loggedInUser.setPermissions(permissions);
                            if (sessionId!=null){
                                reactConnections.put(sessionId,loggedInUser);
                            }
                            fieldId = "action:";//to redisplay the dashboard
                            break;
                        }

                    }
                }
            }

            if (loggedInUser == null) {
                return reactDoNothing();
            }

            if ("fieldchanged".equals(op)) {


                 if (fieldId.startsWith("action:") || fieldId.startsWith("new:")) {

                    String version = "Report";
                    String table = "";
                    int id = 0;
                    if (fieldId.startsWith("action:")) {
                        table = fieldId.substring(7);
                        if (table.length()==0){
                            table = "Overview";
                        }
                    } else {
                        if (fieldId.startsWith("new:")) {
                            table = fieldId.substring(4);
                            version = "Edit";
                        }
                    }

                    return DisplayService.getJson(loggedInUser, table, version, id);

                }


                String[] fieldInfo = fieldId.split("__");
                if (fieldInfo.length < 3){
                    return reactDoNothing();
                }
                if (fieldInfo.length == 4){
                    fieldValue = fieldInfo[3];
                }
                if (fieldInfo[0].equals("Recently Viewed")){
                    fieldInfo[0]="Report";
                }
                String table = fieldInfo[0].replace("-", " ").toLowerCase(Locale.ROOT);
                if (table.startsWith("dropdown:")) {
                    table = table.substring(9);
                }
                String secondaryField = null;
                int secondaryId = 0;
                String database = "";
                String rId = fieldInfo[1];
                int colonPos = rId.indexOf(":");
                if (colonPos >0){
                    database = rId.substring(colonPos + 1);
                    rId = rId.substring(0,colonPos);
                }
                int id = Integer.parseInt(rId);
                if (fieldInfo.length >= 5) {
                    secondaryField = fieldInfo[4];
                    secondaryId = Integer.parseInt(fieldInfo[3]);
                    if (fieldInfo.length == 6){
                        fieldValue = fieldInfo[5];
                    }
                }

                String fieldName = fieldInfo[2];
                if (fieldName.equals("reportName")&& table.equals("report")){
                   String href = "/api/Online?reportid=" + id+"&database="+database;
                   Map<String,String> table2 = new HashMap<>();
                   table2.put("hRef", href);
                   Map<String,Object>section = new HashMap<>();
                   section.put("Report", table2);
                   toReturn.put("table",section);
                   List<String>recordsToShow = new ArrayList<>();
                   recordsToShow.add("Report");
                   toReturn.put("recordsToShow",recordsToShow);
;                   toReturn.put("theme", loggedInUser.getPermissions().get("Theme"));
                   toReturn.put("menus", loggedInUser.getPermissions().get("Menus"));
                   return DisplayService.reactVersion(toReturn,"show");
                }
                if ("menu".equals(fieldName)) {
                    //SELECTIONS FROM DROPDOWN MENU ON A LINE


                    if ("report".equals(table)) {
                        OnlineReport onlineReport = OnlineReportDAO.findForIdAndBusinessId(id, loggedInUser.getUser().getBusinessId());
                        if ("Delete".equals(op)) {
                            OnlineReportDAO.removeById(onlineReport);
                            return DisplayService.getJson(loggedInUser, table, "Report", id);
                        }


                    }

                    if ("pending upload".equals(table)) {

                        PendingUpload pendingUpload = PendingUploadDAO.findById(id);
                        if ("Download".equals(fieldValue)) {
                            Path filePath;
                            String name;
                            if (pendingUpload.getImportResultPath() != null) {
                                filePath = Paths.get(pendingUpload.getImportResultPath());
                                name = filePath.getFileName().toString();
                            } else {
                                filePath = Paths.get(pendingUpload.getFilePath());
                                name = pendingUpload.getFileName();
                            }
                            try {
                                DownloadController.streamFileToBrowser(filePath, response, name);
                            } catch (IOException ex) {
                                ex.printStackTrace();

                            }
                            return reactDoNothing();
                        }

                        if ("Validate".equals(fieldValue)) {
                            Map<String, String> params = new HashMap<>();
                            String json = "";
                            try {
                                File myObj = new File("c:\\Users\\test\\Downloads\\testvalidation.json");
                                Scanner myReader = new Scanner(myObj);
                                while (myReader.hasNextLine()) {
                                    String data = myReader.nextLine();
                                    json += data;
                                }
                                myReader.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                                return reactError(e.getMessage());
                            }
                             return json;
                        }


                        if ("Download Warnings".equals(op)) {
                            HttpSession session = request.getSession();
                            // todo - pending upload security for non admin users?
                            if (pendingUpload.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                                if (session.getAttribute(PendingUploadController.WARNINGSWORKBOOK) != null) {
                                    XSSFWorkbook wb = (XSSFWorkbook) session.getAttribute(PendingUploadController.WARNINGSWORKBOOK);
                                    response.setContentType("application/vnd.ms-excel");
                                    response.setHeader("Content-Disposition", "inline; filename=\"" + pendingUpload.getFileName() + "warnings.xlsx\"");
                                    try {
                                        ServletOutputStream outputStream = response.getOutputStream();
                                        wb.write(outputStream);
                                        outputStream.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                        }
                        if ("Delete".equals(op)) {
                            PendingUploadDAO.removeById(pendingUpload);
                            return DisplayService.getJson(loggedInUser,table,"Report",0);
                        }
                    } else if ("button".equals(fieldName)) {
                        if ("save".equals(fieldValue)) {
                            String lastTable = "";
                            int lastId = 0;
                            User user = null;
                            for (String fieldChanged : loggedInUser.getPendingChanges().keySet()) {
                                String[] fieldBits = fieldChanged.split("__");
                                table = fieldBits[0];
                                fieldChanged = fieldBits[2];
                                int recordId = Integer.parseInt(fieldBits[1]);
                                if (fieldBits.length > 3) {
                                    //TODO fill in for pending uploads
                                }

                                if (lastTable.length() == 0) {
                                    if (recordId > 0) {
                                        user = UserDAO.findById(recordId);
                                    } else {
                                        user = new User(0, LocalDateTime.now().plusYears(10), loggedInUser.getBusiness().getId(), null, null, null, null, null, loggedInUser.getUser().getEmail(), 0, 0, null, null);
                                    }
                                }
                                if (lastTable.length() > 0 && table != lastTable || recordId != lastId) {
                                    if (lastTable.equals("user")) {
                                        UserDAO.store(user);
                                    }
                                }
                                lastTable = table;
                                lastId = recordId;
                                if (table.equals("user")) {
                                    if (fieldName.equals("name")) {
                                        user.setName(fieldValue);
                                    }
                                    if (fieldName.equals("email")) {
                                        user.setEmail(fieldValue);
                                    }
                                    if (fieldName.equals("endDate")) {
                                        user.setEndDate(LocalDate.parse(fieldValue, formatter).atStartOfDay());
                                    }
                                    if (fieldName.equals("status")) {
                                        user.setStatus(fieldValue);
                                    }
                                    if (fieldName.equals("password")){
                                        final String salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                                        user.setSalt(salt);
                                        user.setPassword(AdminService.encrypt(fieldValue, salt));

                                    }


                                }


                            }
                            if (lastTable.length() > 0) {
                                if (lastTable.equals("user")) {
                                    UserDAO.store(user);
                                }
                            }
                        }
                        return DisplayService.getJson(loggedInUser, "Overview", "Report", 0);
                        //button pressed
                    } else {
                        loggedInUser.addPendingChange(fieldId, fieldValue);
                    }

                }
            }
            return reactDoNothing();


        } catch (NullPointerException npe) {
            npe.printStackTrace();
            return reactError("null pointer, json passed?");
        } catch (Exception e) {
            e.printStackTrace();
            reactError(e.getMessage());
        }
        return reactDoNothing();
    }

    public String reactDoNothing(){
        Map<String,Object>toReturn = new HashMap<>();
        toReturn.put("sessionid","");
        return DisplayService.reactVersion(toReturn,"OK");
    }


    public String reactError(String error) {
        Map<String,Object>toReturn = new HashMap<>();
        toReturn.put("sessionid","");
          return DisplayService.reactVersion(toReturn,"error:" + error);
    }

    /*
    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "files", required = false) MultipartFile[] uploadFiles
            , @RequestParam(value = "fieldid", required = false) String page
            , @RequestParam(value = "sessionid", required = false) String sessionId
    ) {

        try {
            LoggedInUser loggedInUser = null;

            if (loggedInUser == null && sessionId != null) {
                HttpSession httpSession = SessionListener.sessions.get(sessionId);
                if (httpSession != null) {
                    loggedInUser = (LoggedInUser) httpSession.getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                }
            }

            if (loggedInUser == null) {
                return jsonError("invalid sessionid");
            }
            String database = "";
            if (database != null && database.length() > 0) {
                Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getBusiness().getId());
                if (db == null) {
                    AdminService.createDatabase(database, loggedInUser, loggedInUser.getDatabaseServer());
                } else {
                    LoginService.switchDatabase(loggedInUser, database);
                }
            }
            MultipartFile uploadFile = null;
            if (uploadFiles.length > 0) {
                uploadFile = uploadFiles[0];
            }

            if (page.equals("Pending Uploads")) {
                String targetPath = SpreadsheetService.getFilesToImportDir() + "/tagged/" + System.currentTimeMillis() + uploadFile.getOriginalFilename();
                uploadFile.transferTo(new File(targetPath));
                // ok it's moved now make the pending upload record
                // todo - assign the database and team automatically!
                PendingUpload pendingUpload = new PendingUpload(0, loggedInUser.getUser().getBusinessId()
                        , LocalDateTime.now()
                        , null
                        , uploadFile.getOriginalFilename()
                        , targetPath
                        , loggedInUser.getUser().getId()
                        , -1
                        , loggedInUser.getDatabase().getId()
                        , null,
                        null);//the team can be deduced from the user.
                PendingUploadDAO.store(pendingUpload);
                return jsonAction("reshow");
            }
        } catch (Exception e) {
            return jsonError(e.getMessage());
        }


        return jsonError("not ready yet");
    }
*/


    public static ImportTemplateData loadMetaData(LoggedInUser loggedInUser, HttpServletRequest request) {
        try {
            //the metadata for each business can be loaded separately.   Maybe there should be metdata for databases....
            ImportTemplate importTemplate = null;
            if (loggedInUser!=null){
                importTemplate = ImportTemplateDAO.findForNameBeginningAndBusinessId(loggedInUser.getBusiness().getBusinessName() + " metadata.xlsx", loggedInUser.getUser().getBusinessId());
            }
            if (importTemplate == null) {
                // if there is no metadata, use default
                org.apache.poi.openxml4j.opc.OPCPackage opcPackage = OPCPackage.open(request.getSession().getServletContext().getResourceAsStream("/WEB-INF/Default Metadata.xlsx"));
                return ImportService.readTemplateFile(opcPackage);

            }
            return ImportService.getImportTemplateData(importTemplate, loggedInUser);
        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        }
        return null;


    }

    public static LoggedInUser isLoggedIn(String sessionId){
       LoggedInUser lu = reactConnections.get(sessionId);
       return lu;
    }


}