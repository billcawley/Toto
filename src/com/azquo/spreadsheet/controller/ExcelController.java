package com.azquo.spreadsheet.controller;

import com.azquo.SessionListener;
import com.azquo.StringLiterals;
import com.azquo.admin.AdminService;
import com.azquo.admin.StorybookService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.*;
import com.azquo.spreadsheet.transport.json.CellsAndHeadingsForExcel;
import com.azquo.spreadsheet.transport.json.ExcelJsonRequest;
import com.azquo.spreadsheet.transport.json.ExcelRegionModification;
import com.azquo.spreadsheet.zk.ChoicesService;
import com.azquo.spreadsheet.zk.ReportExecutor;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.ss.usermodel.WorkbookFactory;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by edward on 29/03/16.
 * <p>
 * For API style access, I'm going to copy the online controller and modify
 */
@Controller
@RequestMapping("/Excel")
public class ExcelController {

    @Autowired
    ServletContext servletContext;

    // I would liked to have zapped this but the audit seems to open a new window with a new session. Hence can't use standard servlet session tracking
    public static final Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it
    // ok we need support for a user being in more than one business
    public static final Map<String, List<LoggedInUser>> excelMultiUserConnections = new ConcurrentHashMap<>();

    /* Parameters are sent as Json . . . posted via https should be secure enough for the moment */

    public static class DatabaseReport implements Serializable {
        public String sheetName;
        public String author;
        public String untaggedReportName;
        public String database;
        public String category;

        public DatabaseReport(String sheetName, String author, String untaggedReportName, String database, String category) {
            this.sheetName = sheetName;
            this.author = author;
            this.untaggedReportName = untaggedReportName;
            this.database = database;
            this.category = category;
        }

        public String getSheetName() {
            return this.sheetName;
        }

        public String getAuthor() {
            return this.author;
        }

        public String getUntaggedReportName() {
            return this.untaggedReportName;
        }

        public String getDatabase() {
            return this.database;
        }

        public String getCategory() {
            return category;
        }
    }

    public static class UserForm implements Serializable {
        public String name;
        public String database;

        public UserForm(String name, String database) {
            this.name = name;
            this.database = database;
        }

        public String getName() {
            return name;
        }

        public String getDatabase() {
            return database;
        }
    }


    public class Base64Return {
        public List<String> slices;

        public Base64Return() {
            slices = new ArrayList<>();
        }
    }

    public static class LoginInfo {
        public String sessionId;
        public List<String> userIds;
        public List<String> userDescriptions;

        public LoginInfo(String sessionId, List<String> userIds, List<String> userDescriptions) {
            this.sessionId = sessionId;
            this.userIds = userIds;
            this.userDescriptions = userDescriptions;
        }
    }

    public static class FormSpec {
        public List<String> formFields;
        public Map<String, List<String>> formChoices;
        public Map<String, String> formDefaults;

        public FormSpec(List<String> formFields, Map<String, List<String>> formChoices, Map<String, String> formDefaults) {
            this.formFields = formFields;
            this.formChoices = formChoices;
            this.formDefaults = formDefaults;
        }
    }

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";

    @ResponseBody
    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "userid", required = false) String userId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "form", required = false) String form
            , @RequestParam(value = "formsubmit", required = false) String formsubmit
            , @RequestParam(value = "reportname", required = false) String reportName
            , @RequestParam(value = "sheetname", required = false) String sheetName
            , @RequestParam(value = "logon", required = false) String logon
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "region", required = false) String region
            , @RequestParam(value = "options", required = false) String options
            , @RequestParam(value = "regionrow", required = false) String regionrow
            , @RequestParam(value = "regioncol", required = false) String regioncol
            , @RequestParam(value = "choice", required = false) String choice
            , @RequestParam(value = "chosen", required = false) String chosen
            , @RequestParam(value = "context", required = false) String context
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "filename", required = false) String fileName
            , @RequestParam(value = "template", required = false) String template
    ) {
        String result = "no action taken";
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "application/json");
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (op != null) {
            op = op.toLowerCase();
        } else {
            op = "";
        }
        // hacky, fix later todo
        if (reportName == null) {
            reportName = "";
        } else {
            reportName = reportName.trim();
            if (reportName.contains(" uploaded by")) {
                reportName = reportName.substring(0, reportName.indexOf(" uploaded by"));
            }
        }

        try {
            if (op.equals("download64") && fileName != null){ // if no sessionId this means an xlsx file from WEB-INF, public anyway so no security but we still need to base 64 encode it
                // basic hack protection though I'm not sure whether anyone could get anything that worrying
                //System.out.println("download64:" + fileName);
                byte[] encodedBytes = null;
                while (fileName.contains("\\") || fileName.contains("/")){
                    fileName = fileName.replace("\\","");
                    fileName = fileName.replace("/","");
                }
                // similar to below but less going on
                if (sessionId!=null){
                    LoggedInUser loggedInUser = null;
                    //System.out.println("template download1:" + fileName);
                    if (excelMultiUserConnections.get(sessionId) != null){
                        for (LoggedInUser check : excelMultiUserConnections.get(sessionId)) {
                            if (check.getUser().getId() == Integer.parseInt(userId)) {
                                loggedInUser = check;
                            }
                        }
                    }
                    if (loggedInUser==null){
                        loggedInUser = excelConnections.get(sessionId);
                    }
                    //TODO should there be any security encoding??
                    if (sessionId.equals("testing")|| loggedInUser!= null) {
                        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                            fileName = fileName.replace("_", " ");
                            reportName = fileName;
                            if (fileName.contains(" with ")) {
                                reportName = fileName.substring(0, fileName.indexOf(" with ")).replace("`", "");
                                String withContext = fileName.substring(fileName.indexOf(" with ") + 6);
                                ChoicesService.setChoices(loggedInUser, withContext);
                            }
                            OnlineReport onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                            if (onlineReport!=null){
                                File file = ExcelService.createReport(loggedInUser, onlineReport, false);
                                encodedBytes = Base64.getEncoder().encode(IOUtils.toByteArray(Files.newInputStream(file.toPath())));
                            }
                        }else {
                            ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(fileName, loggedInUser.getUser().getBusinessId());
                            String directory = "risk";
                            if (!sessionId.equals("testing")) {
                                directory = loggedInUser.getBusinessDirectory();
                            }
                            String filePath = SpreadsheetService.getHomeDir() + ImportService.dbPath + directory + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk();
                            //System.out.println("template download:" + filePath);
                            File file = new File(filePath);
                            encodedBytes = Base64.getEncoder().encode(IOUtils.toByteArray(Files.newInputStream(file.toPath())));
                        }
                    }else{
                        return null;
                    }
                }else{
                    String filePath = "/WEB-INF/" + fileName;
                    encodedBytes = Base64.getEncoder().encode(IOUtils.toByteArray(servletContext.getResourceAsStream(filePath)));

                }
                // similar to below but less going on
                String string64 = new String(encodedBytes);
                int sliceSize = 8000;
                Base64Return base64Return = new Base64Return();
                int startPos = 0;
                while (startPos < encodedBytes.length) {
                    int thisSlice = sliceSize;
                    if (startPos + sliceSize > encodedBytes.length) {
                        thisSlice = encodedBytes.length - startPos;
                    }
                    base64Return.slices.add(string64.substring(startPos, startPos + thisSlice));
                    startPos += sliceSize;
                }
                return jacksonMapper.writeValueAsString(base64Return);
            }


            LoggedInUser loggedInUser = null;
            // todo - try to tidy up the login ogic if possible. Hacked to support users that cross businesses
            if (op.equals("logon") || logon!=null) {
                 if (NumberUtils.isNumber(userId) && sessionId != null){ // then try to select from a multi usr session
                    if (excelMultiUserConnections.get(sessionId) != null){
                        for (LoggedInUser check : excelMultiUserConnections.get(sessionId)) {
                            if (check.getUser().getId() == Integer.parseInt(userId)) {
                                loggedInUser = check;
                            }
                        }
                    }
                } else {
                    List<LoggedInUser> loggedInUsers = LoginService.loginLoggedInUser(database, logon, URLDecoder.decode(password, "UTF-8"));
                    if (!loggedInUsers.isEmpty()) {
                        if (loggedInUsers.size() > 1) { // new criteria, need to pick the user! Replicate website functionality, keep functionality the same for single user
                            //session.setAttribute(LOGGED_IN_USERS_SESSION, loggedInUsers);
                            List<String> userIds = new ArrayList<>();
                            List<String> userDescriptions = new ArrayList<>();
                            for (LoggedInUser l : loggedInUsers) {
                                userIds.add(l.getUser().getId() + "");
                                userDescriptions.add(l.getUser().getBusinessName() + " - " + l.getUser().getStatus());
                            }
                            excelMultiUserConnections.put(request.getSession().getId(), loggedInUsers);
                            return jacksonMapper.writeValueAsString(new LoginInfo(request.getSession().getId(), userIds, userDescriptions));
                        }
                        loggedInUser = loggedInUsers.get(0);
                    }
                }
                if (loggedInUser == null) {
                    System.out.println("login attempt by " + logon + " with incorrect details");
                    return jsonError("incorrect login details");
                } else {
                    //find existing if already logged in. And clean up any duplicates if they're there
                    boolean newUser = true;
                    // ok we might be logging on passing a session id if in the case of multi user selection - already logged in really but have to select the user
                    // . Hence override the request session if session Id passed here
                    String currentSession = sessionId != null ? sessionId : request.getSession().getId();
                    for (String existingSessionId : excelConnections.keySet()) {
                        LoggedInUser existingUser = excelConnections.get(existingSessionId);
                        if (existingUser.getUser().getId() == loggedInUser.getUser().getId()) {
                            excelConnections.put(currentSession, existingUser);
                            if (!existingSessionId.equals(currentSession)) {
                                excelConnections.remove(existingSessionId);
                            }
                            loggedInUser = existingUser;
                            newUser = false;
                        }
                    }
                    if (newUser) {
                        excelConnections.put(currentSession + "", loggedInUser);
                    }
                    if (!loggedInUser.getUser().isAdministrator() && !loggedInUser.getUser().isDeveloper() && loggedInUser.getUser().getReportId() != 0) {// then we need to load in the permissions
                        // typically loading in the permissions would be done in online report controller. I'm going to paste relevant code here, it might be factored later
                        OnlineReport or = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath +
                                loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + or.getFilenameForDisk();
                        Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                        // I think I need those two
                        book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                        book.getInternalBook().setAttribute(REPORT_ID, or.getId());
                        ReportRenderer.populateBook(book, 0);
                    }

                    LoginInfo li = new LoginInfo(currentSession, null, null);
                    //System.out.println("login response : " + jacksonMapper.writeValueAsString(li));
                    return jacksonMapper.writeValueAsString(li);
                }
            }

            // slight logic shift - logon goes first then session id check, can't see a problem. Moved due to multi user functionality
            if (sessionId != null) {
                loggedInUser = excelConnections.get(sessionId);
            }

            // logic added by EFC 15/04/21. For Atos we're going to try to do some json graphs. This will be in the browser so try the logged in user in the normal way also
            if (loggedInUser == null && sessionId != null){
                HttpSession httpSession = SessionListener.sessions.get(sessionId);
                if (httpSession != null){
                    loggedInUser = (LoggedInUser) httpSession.getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                }
            }

            if (loggedInUser == null) {
                return "invalid sessionid";
            }

            if (database != null && database.length() > 0) {
                LoginService.switchDatabase(loggedInUser, database);
            }

            if (reportName.length() > 0) {
                reportName = java.net.URLDecoder.decode(reportName, "UTF-8");
                loggedInUser.setOnlineReport(OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName.trim()));
            }

            if ("storybook".equals(op)){
                return StorybookService.getJson(loggedInUser);
            }


            if (op.equals("multiuserstatus")) {
                if (excelMultiUserConnections.get(sessionId) != null){
                    return "Logged in under : " + loggedInUser.getUser().getBusinessName() + ". Switch business.";
                }
                return "";
            }

            if (op.equals("checkmultiuser")) {
                if (excelMultiUserConnections.get(sessionId) != null){
                    List<String> userIds = new ArrayList<>();
                    List<String> userDescriptions = new ArrayList<>();
                    for (LoggedInUser l : excelMultiUserConnections.get(sessionId) ) {
                        userIds.add(l.getUser().getId() + "");
                        userDescriptions.add(l.getUser().getBusinessName() + " - " + l.getUser().getStatus());
                    }
                    return jacksonMapper.writeValueAsString(new LoginInfo(sessionId, userIds, userDescriptions));
                }
                return jacksonMapper.writeValueAsString(new LoginInfo(sessionId, null, null));
            }

            if (op.equals("audit")) {
//                UserRegionOptions userRegionOptions = ExcelService.getUserRegionOptions(loggedInUser, "", loggedInUser.getOnlineReport().getId(), region); // what it was, no good!
                UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), 0, region, options); // fudging report id
                jacksonMapper.registerModule(new JavaTimeModule());
                jacksonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                //jacksonMapper.registerModule(new JavaTimeModule());
                final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, loggedInUser.getOnlineReport().getId(), sheetName,
                        region, userRegionOptions, Integer.parseInt(regionrow), Integer.parseInt(regioncol), 1000);
                if (provenanceDetailsForDisplay.getAuditForDisplayList() != null && !provenanceDetailsForDisplay.getAuditForDisplayList().isEmpty()) {
                    String toReturn = jacksonMapper.writeValueAsString(provenanceDetailsForDisplay);
                    //System.out.println(toReturn);
                    return toReturn;
                }
                return (jsonError("no details"));
                //buildContextMenuProvenanceDownload(provenanceDetailsForDisplay, reportId);
            }

            if (op.equals("getchoices")) {
                // I'm going to surpress errors for the moment until I can work out how to display them in the Excel TS
                try {
                    List<FilterTriple> filterOptions = CommonReportUtils.getFilterListForQuery(loggedInUser, choice, chosen);//choice is the name of the range, chosen= the name of the choice cell (not its value as previously stated. I don't think anyway!)
                    System.out.println("filter options size " + filterOptions.size());
                    return jacksonMapper.writeValueAsString(filterOptions);
                } catch (Exception e) {
                    e.printStackTrace();
                    return jacksonMapper.writeValueAsString(new ArrayList<FilterTriple>());
                }
            }

            if (op.equals("setchoices")) {
                System.out.println("chosen for multi : " + chosen);
                List<Integer> childIds = jacksonMapper.readValue(chosen, jacksonMapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), choice, loggedInUser.getUser().getEmail(), childIds);
                return jsonError("done");
            }

            if (op.equals("createfilterset")) {
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSetWithQuery(loggedInUser.getDataAccessToken(), choice, loggedInUser.getUser().getEmail(), chosen);
                return jsonError("done");
            }

            // so this may be required to set the value in the multi cell e.g. [all] or [all but] etc. Occasionally this is used in formulae, perhaps it should not be
            if (op.equals("getmulticellcontents")) {
                String check = ChoicesService.multiList(loggedInUser, chosen, choice);
                System.out.println("getmulticellcontents check : " + check);
                return check;
            }

            if (op.equals("userchoices")) {
                return jacksonMapper.writeValueAsString(CommonReportUtils.getUserChoicesMap(loggedInUser));
            }

            if (op.equals("setchoice")) {
                SpreadsheetService.setUserChoice(loggedInUser, choice, chosen);
                return jsonError("done");
            }

            if (op.equals("getcount")) {
                int choices = CommonReportUtils.getNameQueryCount(loggedInUser, choice);
                return choices + "";
            }

            if (op.equals("sethighlight")) {
                int hDays = 0;
                if (chosen.equals("1 hour")) chosen = "2 days"; //horrible!
                if (chosen.toLowerCase().contains(" day")) {
                    hDays = Integer.parseInt(chosen.substring(0, chosen.toLowerCase().indexOf(" day")));
                }
                if (loggedInUser.getOnlineReport() == null || region == null || region.length() == 0) {
                    return "not enough details to highlight";
                }
                UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), loggedInUser.getOnlineReport().getId(), region);
                if (userRegionOptions == null) {
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), loggedInUser.getOnlineReport().getId(), region, "highlight=" + hDays + "\n");
                    UserRegionOptionsDAO.store(userRegionOptions);
                } else {
                    if (userRegionOptions.getHighlightDays() != hDays) {
                        userRegionOptions.setHighlightDays(hDays);
                        UserRegionOptionsDAO.store(userRegionOptions);
                    }
                }
                return jsonError("done");
            }
            if (op.equals("resolvequery")) {
                return CommonReportUtils.resolveQuery(loggedInUser, chosen, null);
            }
            if (op.equals("getdropdownlistforquery")) {
                return jacksonMapper.writeValueAsString(CommonReportUtils.getDropdownListForQuery(loggedInUser, choice));
            }

            // database switching should be done by being logged in
            String downloadName = "";
            boolean base64 = false;
            if (op.equals("download64")) {
                op = "download";
                base64 = true;
            }

            // EFC todo - what's going on in here? Seems more than just download . . .
            if (op.equals("download")) {
                try {
                    File file = null;
                    OnlineReport onlineReport = null;
                    if ((reportName.length() == 0) && (database == null || database.length() == 0)) {
                        //get initial menu
                        List<OnlineReport> reports = AdminService.getReportList(loggedInUser, false);
                        if (reports.size() == 1 && !reports.get(0).getReportName().equals("No reports found")) {
                            onlineReport = reports.get(0);
                            downloadName = onlineReport.getReportName();
                        } else {
                            //list the reports
                            file = ExcelService.listReports(request, reports);
                            downloadName = "Available reports";
                        }
                    } else {
                        if (reportName.length() == 0) {
                            onlineReport = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                            if (onlineReport != null) {
                                onlineReport.setDatabase(loggedInUser.getDatabase().getName());
                                loggedInUser.getUser().setReportId(onlineReport.getId());
                            }
                        } else {
                            if (database == null || database.length() == 0) {
                                database = loggedInUser.getDatabase().getName();
                            }
                            if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                                onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                                if (onlineReport!=null) {
                                    onlineReport.setDatabase(database);
                                    loggedInUser.getUser().setReportId(onlineReport.getId());
                                }
                            } else {
                                Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                                onlineReport = OnlineReportDAO.findForDatabaseIdAndName(db.getId(), reportName);
                                boolean allowed = false;
                                for (String key : loggedInUser.getReportIdDatabaseIdPermissions().keySet()) {
                                    LoggedInUser.ReportDatabase permission = loggedInUser.getPermission(key);
                                    if (permission.getReport().getId() == onlineReport.getId() && permission.getDatabase().getId() == db.getId()) {
                                        allowed = true;
                                        onlineReport.setDatabase(db.getName());
                                        break;
                                    }
                                }
                                if (!allowed) {
                                    onlineReport = null;
                                }
                            }

                        }
                        if (onlineReport != null) {
                            downloadName = onlineReport.getReportName();
                        }
                    }
                    if (downloadName.length() == 0) {
                        response.setHeader("Access-Control-Allow-Origin", "*");
                        response.setHeader("Content-type", "application/json");
                        return jsonError("incorrect report name"); // probably need to add json
                    }
                    boolean isTemplate = false;
                    if (base64 || (template != null && template.equals("true"))) {
                        isTemplate = true;
                        downloadName += " Template";
                    }
                    if (file == null) {
                        file = ExcelService.createReport(loggedInUser, onlineReport, isTemplate);
                    }
                    String suffix = ".xlsx";
                    int dotPos = file.getPath().lastIndexOf(".");
                    if (dotPos > 0 && file.getPath().length() - dotPos < 6) {
                        suffix = file.getPath().substring(dotPos);
                    }
                    if (base64) {
//                        byte[] bytes = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
                        // will this mess up the file?? who knows!
                        OPCPackage opcPackage = OPCPackage.open(file.getAbsolutePath());
//                        Workbook book = new XSSFWorkbook(opcPackage);
                        // same API call?
                        Workbook book = WorkbookFactory.create(opcPackage);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        // adding the try catch here so we always close the OPC package which I think causes a problem under windows
                        try {
                            for (int i = 0; i < book.getNumberOfNames(); i++) {
                                Name nameAt = book.getNameAt(i);
                                try {
                                    if (!nameAt.getSheetName().isEmpty()) {
                                        if (nameAt.getSheetIndex() == -1) {
                                            int lookup = book.getSheetIndex(nameAt.getSheetName());
                                            if (lookup != -1) {
                                                nameAt.setSheetIndex(lookup);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) { // maybe do something with it later but for the moment don't. Just want it to fix what names it can
//                                    ignored.printStackTrace();
                                }
                            }
                            book.write(baos);
                        } catch (Exception ignored) {
//                            ignored.printStackTrace();
                        }
                        // don't close, it will write!!!
                        // and I have to revert or it won't give the file handler back. Under windows anyway. So just doing nothing not ok either.
                        opcPackage.revert();
                        byte[] encodedBytes = Base64.getEncoder().encode(baos.toByteArray());
                        baos.close();
                        String string64 = new String(encodedBytes);
                        int sliceSize = 8000;

                        Base64Return base64Return = new Base64Return();
                        int startPos = 0;
                        while (startPos < encodedBytes.length) {
                            int thisSlice = sliceSize;
                            if (startPos + sliceSize > encodedBytes.length) {
                                thisSlice = encodedBytes.length - startPos;
                            }
                            base64Return.slices.add(string64.substring(startPos, startPos + thisSlice));
                            startPos += sliceSize;
                        }
                        return jacksonMapper.writeValueAsString(base64Return);
                    }
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh.mm.ss");
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // Set up mime type
                    try {
                        DownloadController.streamFileToBrowser(file.toPath(), response, downloadName + " " + df.format(new Date()) + suffix);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return jsonError("OK");
                } catch (Exception e) { // changed to overall exception handling
                    e.printStackTrace(); // Could be when importing the book, just log it
                    result = e.getMessage(); // put it here to puck up instead of the report
                }
            }
            if (op.equals("allowedreports")) {
                List<DatabaseReport> databaseReports = new ArrayList<>();
                List<OnlineReport> allowedReports = AdminService.getReportList(loggedInUser, false);

                if (allowedReports.size() == 1) {
                    //OnlineReport or = allowedReports.get(0);
                    //String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath +
                    //        loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + or.getFilenameForDisk();
                    //Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                    //book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                    //book.getInternalBook().setAttribute(REPORT_ID, or.getId());
                    //StringLiterals.populateBook(book, 0);
                    Map<String, LoggedInUser.ReportIdDatabaseId> reports = loggedInUser.getReportIdDatabaseIdPermissions();
                    if (!reports.isEmpty()){
                        for (LoggedInUser.ReportIdDatabaseId reportIdDatabaseId : reports.values()) {
                            OnlineReport or = OnlineReportDAO.findById(reportIdDatabaseId.getReportId());
                            Database db = DatabaseDAO.findById(reportIdDatabaseId.getDatabaseId());
                            databaseReports.add(new DatabaseReport(or.getFilename(), or.getAuthor(), or.getUntaggedReportName(), db.getName(), or.getCategory()));
                            databaseReports.sort(Comparator.comparing(o -> (o.getCategory() + o.getSheetName())));
                        }
                    } else { // a developer or admin who happens to have one report
                        OnlineReport or = allowedReports.get(0);
                        databaseReports.add(new DatabaseReport(or.getFilename(), or.getAuthor(), or.getUntaggedReportName(), or.getDatabase(), or.getCategory()));
                    }
                } else {
                    // admin
                    for (OnlineReport or : allowedReports) {
                        databaseReports.add(new DatabaseReport(or.getFilename(), or.getAuthor(), or.getUntaggedReportName(), or.getDatabase(), or.getCategory()));
                    }
                }

                return jacksonMapper.writeValueAsString(databaseReports);
            }
            boolean adminDev = loggedInUser.getUser().isDeveloper() || loggedInUser.getUser().isAdministrator();
            List<Database> databases;
            if (adminDev) {
                databases = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            } else {
                databases = new ArrayList<>();
                Map<String, LoggedInUser.ReportIdDatabaseId> reports = loggedInUser.getReportIdDatabaseIdPermissions();
                for (LoggedInUser.ReportIdDatabaseId reportIdDatabaseId : reports.values()) {
                    databases.add(DatabaseDAO.findById(reportIdDatabaseId.getDatabaseId()));
                }
            }
            if (op.equals("allowedforms")) {
                List<UserForm> userForms = new ArrayList<>();
//                List<OnlineReport> allowedReports = AdminService.getReportList(loggedInUser);
                if (databases != null) {
                    for (Database d : databases) {
                        if (d.getImportTemplateId() != -1) {
                            ImportTemplate importTemplate = ImportTemplateDAO.findById(d.getImportTemplateId());
                            if (importTemplate != null) { // a duff id could mean it is
                                ImportTemplateData importTemplateData = ImportService.getImportTemplateData(importTemplate, loggedInUser);
                                for (String sheet : importTemplateData.getSheets().keySet()) {
                                    if (sheet.toLowerCase().startsWith("form") && (adminDev || (loggedInUser.getFormPermissions().contains(sheet.substring(4).toLowerCase())))) {
//                                    List<List<String>> cellData = importTemplateData.getSheets().get(sheet);
                                        userForms.add(new UserForm(sheet.substring(4), d.getName()));
                                    }
                                }
                            }
                        }
                    }
                }
                return jacksonMapper.writeValueAsString(userForms);
            }
            int autoCompleteThreshold = 270;
            if (form != null && !form.isEmpty() && database != null && !database.isEmpty()) {
                List<String> formFields = new ArrayList<>();
                // so we'll want dropdowns
                Map<String, List<String>> choices = new HashMap<>();
                Map<String, String> defaultValues = new HashMap<>();
                if (databases != null) {
                    for (Database d : databases) {
                        if (d.getImportTemplateId() != -1 && d.getName().equalsIgnoreCase(database)) {
                            // autocomplete going in here for the mo. May as well use these parameters
                            if (choice != null && chosen != null && !choice.isEmpty()) {
                                List<String> toReturn = new ArrayList<>();
                                try {
                                    List<String> dropdownListForQuery = CommonReportUtils.getDropdownListForQuery(loggedInUser, choice, null, chosen);
                                    for (String value : dropdownListForQuery) {
                                        toReturn.add(value);
                                        if (toReturn.size() > autoCompleteThreshold) { // arbitrary, need to test
                                            break;
                                        }
                                    }
                                } catch (Exception e) { // maybe do something more clever later . . .
                                    e.printStackTrace();
                                }
                                System.out.println(jacksonMapper.writeValueAsString(toReturn));
                                return jacksonMapper.writeValueAsString(toReturn);
                            }

                            ImportTemplate importTemplate = ImportTemplateDAO.findById(loggedInUser.getDatabase().getImportTemplateId());
                            ImportTemplateData importTemplateData = ImportService.getImportTemplateData(importTemplate, loggedInUser);
                            for (String sheet : importTemplateData.getSheets().keySet()) {
                                if (sheet.toLowerCase().startsWith("form") && sheet.substring(4).equalsIgnoreCase(form)
                                        && (adminDev || (loggedInUser.getFormPermissions().contains(sheet.substring(4).toLowerCase())))) {
                                    // ok are we submitting data from the form or wanting the fields?
                                    formFields = importTemplateData.getSheets().get(sheet).get(0); // top row is the field names
                                    for (int i = formFields.size() - 1; i >= 0; i--) {
                                        if (formFields.get(i).trim().isEmpty()) {
                                            formFields.remove(i);
                                        } else {
                                            break;
                                        }
                                    }
                                    // need to get to the parameter bits, maybe could reuse code but I'm not sure
                                    boolean hitBlank = false;
                                    String postProcessor = null;
                                    for (int row = 1; row < importTemplateData.getSheets().get(sheet).size(); row++) {
                                        if (importTemplateData.getSheets().get(sheet).get(row).isEmpty()) {
                                            hitBlank = true;
                                        } else if (hitBlank && importTemplateData.getSheets().get(sheet).get(row).size() >= 2) {
                                            String name = importTemplateData.getSheets().get(sheet).get(row).get(0);
                                            String query = importTemplateData.getSheets().get(sheet).get(row).get(1);
                                            List<String> dropdownListForQuery;
                                            try {
                                                // hacky. Many more hacks and I'll need to rethink this
                                                if (name.equalsIgnoreCase(ImportService.POSTPROCESSOR)) {
                                                    postProcessor = query;
                                                } else if (query.equalsIgnoreCase("textarea")) { // I feel this is a bit hacky, maybe
                                                    choices.put(name, Collections.singletonList("textarea"));
                                                } else {
                                                    dropdownListForQuery = CommonReportUtils.getDropdownListForQuery(loggedInUser, query, null, null);
                                                    if (dropdownListForQuery.size() > autoCompleteThreshold) {
                                                        List<String> autoAndChoice = new ArrayList<>();
                                                        autoAndChoice.add("auto");
                                                        autoAndChoice.add(query);
                                                        choices.put(name, autoAndChoice);
                                                    } else {
                                                        choices.put(name, dropdownListForQuery);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace(); // might want user feedback later
                                            }
                                        } else { // check for language date in the bit above parameters
                                            int col = 0;
                                            for (String cellValue : importTemplateData.getSheets().get(sheet).get(row)) {
                                                if (cellValue.toLowerCase().contains("language date")) {
                                                    // in theory it might index out of bounds but that would be quite odd. We've found language date - flag it up as a single choice
                                                    choices.put(importTemplateData.getSheets().get(sheet).get(0).get(col), Collections.singletonList("date"));
                                                }
                                                if (cellValue.toLowerCase().contains("default ")) {
                                                    String defaultValue;
                                                    int defaultIndex = cellValue.toLowerCase().indexOf("default ") + "default ".length();
                                                    if (cellValue.indexOf(";", defaultIndex) > 0) {
                                                        defaultValue = cellValue.substring(defaultIndex, cellValue.indexOf(";", defaultIndex));
                                                    } else {
                                                        defaultValue = cellValue.substring(defaultIndex).trim();
                                                    }
                                                    defaultValues.put(importTemplateData.getSheets().get(sheet).get(0).get(col), defaultValue);
                                                }
                                                col++;
                                            }
                                        }
                                    }

                                    if ("true".equalsIgnoreCase(formsubmit)) {
                                        Path target = Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + "formsubmit" + form + ".tsv"); // timestamp to stop file overwriting
                                        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                                            // new logic - not going to use the import template for importing - will copy the top bits off the import template
                                            int row = 1;
                                            while (importTemplateData.getSheets().get(sheet).size() > row) {
                                                if (importTemplateData.getSheets().get(sheet).get(row).isEmpty()) {
                                                    break;
                                                }
                                                for (String cell : importTemplateData.getSheets().get(sheet).get(row)) {
                                                    bufferedWriter.write(cell + "\t");
                                                }
                                                bufferedWriter.newLine();
                                                row++;
                                            }
                                            // now put in our one line of data
                                            bufferedWriter.newLine();
                                            for (String name : formFields) {
                                                bufferedWriter.write(request.getParameter(name) != null ? request.getParameter(name) : "" + "\t");
                                            }
                                            bufferedWriter.newLine();
                                        }
                                        final Map<String, String> fileNameParams = new HashMap<>();
                                        ImportService.addFileNameParametersToMap(target.getFileName().toString(), fileNameParams);
                                        UploadedFile uploadedFile = new UploadedFile(target.toAbsolutePath().toString(), Collections.singletonList(target.getFileName().toString()), fileNameParams, false, false);
                                        List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile, null, null);
                                        UploadedFile uploadedFile1 = uploadedFiles.get(0);
                                        if (uploadedFile1.getError() != null && !uploadedFile1.getError().isEmpty()) {
                                            return uploadedFile1.getError();
                                        } else if (postProcessor != null) { // deal with execute. More specifically execute needs to be used by the claims header thing
                                            // set user choices to submitted fields
                                            for (String name : formFields) {
                                                if (request.getParameter(name) != null) {
                                                    System.out.println(name + " : " + request.getParameter(name));
                                                    SpreadsheetService.setUserChoice(loggedInUser, name, request.getParameter(name));
                                                }
                                            }
                                            // should we bother to report on the post processing result?
                                            uploadedFile.setPostProcessingResult(ReportExecutor.runExecute(loggedInUser, postProcessor, null, uploadedFile.getProvenanceId(), false).toString());
                                        }
                                        return "ok";
                                    }
                                }
                            }
                        }
                        if (!formFields.isEmpty()) {
                            break;
                        }
                    }
                }
                String toReturn = jacksonMapper.writeValueAsString(new FormSpec(formFields, choices, defaultValues));
                System.out.println(toReturn);
                return toReturn;
            }

            if (op.equals("loadregion")) {
                // since this expects a certain type of json format returned then we need to wrap the error in one of those objects
                if (json.length() < 10_000){
                    System.out.println("json : " + json);
                }
                ExcelJsonRequest excelJsonRequest = jacksonMapper.readValue(json, ExcelJsonRequest.class);
                try {
                    long time = System.currentTimeMillis();
                    if (excelJsonRequest.query != null) {
                        for (List<String> strings : excelJsonRequest.query) {
                            for (String string : strings) {
                                if (string != null && string.length() > 0) {
                                    string = CommonReportUtils.replaceUserChoicesInQuery(loggedInUser, string);
                                    CommonReportUtils.getDropdownListForQuery(loggedInUser, string);
                                }
                            }
                        }
                    }
                    // ok this will have to be moved
                    //ExcelJsonRequest excelJsonRequest = jacksonMapper.readValue(json.replace("\\\"", "\""), ExcelJsonRequest.class);
                    // maybe it shouldn't be replaced!
                    String optionsSource = excelJsonRequest.optionsSource != null ? excelJsonRequest.optionsSource : "";

                    UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), loggedInUser.getUser().getReportId(), excelJsonRequest.region, optionsSource);
                    // UserRegionOptions from MySQL will have limited fields filled
                    UserRegionOptions userRegionOptions2 = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), loggedInUser.getUser().getReportId(), excelJsonRequest.region);
                    // only these five fields are taken from the table
                    if (userRegionOptions2 != null) {
                        if (userRegionOptions.getSortColumn() == null) {
                            userRegionOptions.setSortColumn(userRegionOptions2.getSortColumn());
                            userRegionOptions.setSortColumnAsc(userRegionOptions2.getSortColumnAsc());
                        }
                        if (userRegionOptions.getSortRow() == null) {
                            userRegionOptions.setSortRow(userRegionOptions2.getSortRow());
                            userRegionOptions.setSortRowAsc(userRegionOptions2.getSortRowAsc());
                        }
                        userRegionOptions.setHighlightDays(userRegionOptions2.getHighlightDays());
                    }
                    if (excelJsonRequest.rowHeadings == null || excelJsonRequest.rowHeadings.isEmpty()) { // no row headings is an import region - assign an empty sent cells. Todo - could this be factored?
                        List<List<String>> colHeadings = excelJsonRequest.columnHeadings;
                        // ok change from the logic used in ZK. In ZK we had to prepare a blank set of data cells to be modified
                        // as the user changed them but it's difficult to prepare them here as we don't know the data region size and luckily it's not necessary
                        // as the data is sent in a block from Excel. It might have changed size in the mean time (as in someone changed the data region size and now the headings don't match) but I'm nto that bothered by this for the mo
                        // put an empty data set here as the reference is final, fill it out below with the data sent size from the user
                        // note the col headings source is going in here as is without processing as in the case of ad-hoc it is not dynamic (i.e. an Azquo query), it's import file column headings, parsed into an array in Excel
                        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(excelJsonRequest.region, colHeadings, null, null, null, new ArrayList<>(), null, null, null, 0, userRegionOptions.getRegionOptionsForTransport(), null);
                        loggedInUser.setSentCells(loggedInUser.getUser().getReportId(), excelJsonRequest.sheetName, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                        return "Empty space set to ad hoc data : " + excelJsonRequest.region;
                    } else {
                        // todo : find out how, in particular after a list of states, the arrays can not be square, that is to say that the bottom one has an extra blank
                        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser,
                                excelJsonRequest.region, 0,
                                replaceUserChoicesInHeadings(loggedInUser, excelJsonRequest.rowHeadings),
                                replaceUserChoicesInHeadings(loggedInUser, excelJsonRequest.columnHeadings),
                                replaceUserChoicesInHeadings(loggedInUser, excelJsonRequest.context),
                                userRegionOptions, true);
                        RegionOptions holdOptions = cellsAndHeadingsForDisplay.getOptions();//don't want to send these to Excel
                        cellsAndHeadingsForDisplay.setOptions(null);
                        System.out.println("NPE checking : " + loggedInUser.getOnlineReport());
                        System.out.println("NPE checking : " + excelJsonRequest);
                        // Edd allowing null online report for the new range test. Don't think it will cause harm
                        loggedInUser.setSentCells(loggedInUser.getOnlineReport() != null ? loggedInUser.getOnlineReport().getId() : 0, excelJsonRequest.sheetName, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                        result = jacksonMapper.writeValueAsString(new CellsAndHeadingsForExcel(excelJsonRequest, cellsAndHeadingsForDisplay));
                        cellsAndHeadingsForDisplay.setOptions(holdOptions);
                        System.out.println("About to return result which is " + result.length() + " long in " + (System.currentTimeMillis() - time));
                        return result;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    List<List<String>> errorHeading = new ArrayList<>();
                    List<String> error = new ArrayList<>();
                    error.add("error");
                    errorHeading.add(error);
                    List<List<CellForDisplay>> data = new ArrayList<>();
                    List<CellForDisplay> row = new ArrayList<>();
                    // I had a comment about the error needing more but I think this is fine for the moment
                    row.add(new CellForDisplay(
                            true, "Error : " + e.getMessage(), 0, false, 0, 0, false, false, "", 0)
                    );
                    data.add(row);
                    errorHeading.add(error);
                    return jacksonMapper.writeValueAsString(new CellsAndHeadingsForExcel(null, new CellsAndHeadingsForDisplay(
                            excelJsonRequest.region,
                            errorHeading,
                            errorHeading,
                            new HashSet<>(),
                            new HashSet<>(),
                            data,
                            excelJsonRequest.rowHeadings,
                            excelJsonRequest.columnHeadings,
                            excelJsonRequest.context,
                            System.currentTimeMillis(),
                            null,
                            ""
                    )));
                }
            }
            /* the COM plugin worked by sending back all the regions - I'm going to try a lighter approach where just the modified cells are sent - this will be a JSON list of

                interface RegionModification {
                    sheet: string;
                    region: string;
                    cellModifications : CellModification[];
                }

                interface CellModification {
                    Row: number;
                    col: number;
                    newValue: string;
                }


             */

            if (op.equals("savemodifications")) {
                System.out.println("json " + json);
                List<ExcelRegionModification> excelRegionModifications = jacksonMapper.readValue(json, jacksonMapper.getTypeFactory().constructCollectionType(List.class, ExcelRegionModification.class));
                // todo - set the context which is a choice list really, see ChoicesService.resolveAndSetChoiceOptions
                //loggedInUser.setContext(context);
                for (ExcelRegionModification excelRegionModification : excelRegionModifications) {
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(loggedInUser.getUser().getReportId(), excelRegionModification.sheet, excelRegionModification.region);
                    List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                    for (ExcelRegionModification.CellModification cellModification : excelRegionModification.cellModifications) {
                        if (cellModification.row < data.size()) {
                            List<CellForDisplay> row = data.get(cellModification.row);
                            if (cellModification.col < row.size()) {
                                CellForDisplay cell = row.get(cellModification.col);
                                if (!isEqual(cell.getStringValue(), cellModification.newValue)) {
                                    cell.setNewStringValue(cellModification.newValue);
                                }
                            }
                        } // exception on an else? I think the server side stuff will give give some helpful feedback in the result
                    }
                    int reportId = loggedInUser.getUser().getReportId();
                    reportName = OnlineReportDAO.findById(reportId).getReportName();
                    loggedInUser.setContext(context);
                    if (result.equals("no action taken")) { // hacky, fix later
                        result = SpreadsheetService.saveData(loggedInUser, reportId, reportName, excelRegionModification.sheet, excelRegionModification.region, false);
                    } else {
                        result += (", " + SpreadsheetService.saveData(loggedInUser, reportId, reportName, excelRegionModification.sheet, excelRegionModification.region, false));
                    }
                    // so here's the followon execute
                    OnlineReport or = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                    String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath +
                            loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + or.getFilenameForDisk();
                    Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                    // I think I need those two
                    book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                    book.getInternalBook().setAttribute(REPORT_ID, or.getId());
                    ReportRenderer.populateBook(book, 0);
                    // ok this crashes due to no book path but I think I'll allow no book path itnernally as
                    ReportExecutor.runExecuteCommandForBook(book, StringLiterals.FOLLOWON); // that SHOULD do it. It will fail gracefully in the vast majority of times there is no followon
                }
                return result;
            }
            if (op.equals("importwizard")){
                return ImportWizard.handleRequest(loggedInUser,request);

            }
            if (op.equals("reportwizard")){
                return ReportWizard.handleRequest(request,loggedInUser);

            }
            if (op.equals("searchdatabase")){
                return DatabaseSearchController.respondTo(request, loggedInUser);
            }
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            result = "null pointer, json passed?";
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return jsonError(result);
    }



    private static List<List<String>> replaceUserChoicesInHeadings(LoggedInUser loggedInUser, List<List<String>> headings) {
        List<List<String>> toReturn = new ArrayList<>();
        for (List<String> row : headings) {
            List<String> newRow = new ArrayList<>();
            for (String heading : row) {
                newRow.add(CommonReportUtils.replaceUserChoicesInQuery(loggedInUser, heading));

            }
            toReturn.add(newRow);
        }
        return toReturn;
    }

    private boolean isEqual(String s1, String s2) {
        if (s1.equals(s2)) return true;
        try {
            double d1 = Double.parseDouble(s1);
            double d2 = Double.parseDouble(s2);
            double diff = (d1 - d2);
            if (diff < .0000000001 && diff > -.0000000001) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public String jsonError(String error) {
        return "{\"error\":\"" + error + "\"}";
    }

    // when not multipart - this is a bit annoying, hopefully can find a way around it later
    @ResponseBody
    @RequestMapping
    public String handleRequest(HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "userid", required = false) String userId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "form", required = false) String form
            , @RequestParam(value = "formsubmit", required = false) String formsubmit
            , @RequestParam(value = "reportname", required = false) String reportName
            , @RequestParam(value = "sheetname", required = false) String sheetName
            , @RequestParam(value = "logon", required = false) String logon
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "region", required = false) String region
            , @RequestParam(value = "options", required = false) String options
            , @RequestParam(value = "regionrow", required = false) String regionrow
            , @RequestParam(value = "regioncol", required = false) String regioncol
            , @RequestParam(value = "choice", required = false) String choice
            , @RequestParam(value = "chosen", required = false) String chosen
            , @RequestParam(value = "context", required = false) String context
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "filename", required = false) String fileName
    ) {
        return handleRequest(request, response, sessionId, userId,  op, database, form, formsubmit, reportName, sheetName, logon, password, region, options, regionrow, regioncol, choice, chosen, context, json, fileName, "true");
    }
}