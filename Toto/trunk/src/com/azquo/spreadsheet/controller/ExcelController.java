package com.azquo.spreadsheet.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.*;
import com.azquo.spreadsheet.transport.json.CellsAndHeadingsForExcel;
import com.azquo.spreadsheet.transport.json.ExcelJsonRequest;
import com.azquo.spreadsheet.zk.ChoicesService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zeroturnaround.zip.ZipUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    public static final Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it

    // note : I'm now thinking dao objects are acceptable in controllers if moving the call to the service would just be a proxy
    /* Parameters are sent as Json . . . posted via https should be secure enough for the moment */

    public static class DatabaseReport implements Serializable {
        public String sheetName;
        public String author;
        public String untaggedReportName;
        public String database;

        public DatabaseReport(String sheetName, String author, String untaggedReportName, String database) {
            this.sheetName = sheetName;
            this.author = author;
            this.untaggedReportName = untaggedReportName;
            this.database = database;
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
    }


    public class Base64Return {
        public List<String> slices;

        public Base64Return() {
            slices = new ArrayList<>();
        }
    }

    public static class LoginInfo {
        public String sessionId;
        public String userType;

        public LoginInfo(String sessionId, String userType) {
            this.sessionId = sessionId;
            this.userType = userType;
        }
    }

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String BOOK_PATH = "BOOK_PATH";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";

    @ResponseBody
    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "database", required = false) String database
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
            , @RequestParam(value = "json", required = false) String json
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

        try {
            LoggedInUser loggedInUser = null;
            if (sessionId != null) {
                loggedInUser = excelConnections.get(sessionId);
                if (loggedInUser == null) {
                    return "invalid sessionid";
                } else {
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                }
            }

            if (loggedInUser == null) {
                loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), database, logon, java.net.URLDecoder.decode(password, "UTF-8"), false);
                if (loggedInUser == null) {
                    System.out.println("login attempt by " + logon + " password " + password);
                    return jsonError("incorrect login details");
                } else {
                    //find existing if already logged in, and remember the current report, database, server
                    boolean newUser = true;
                    for (String existingSessionId : excelConnections.keySet()) {
                        LoggedInUser existingUser = excelConnections.get(existingSessionId);
                        if (existingUser.getUser().getId() == loggedInUser.getUser().getId()) {
                            excelConnections.put(request.getSession().getId(), existingUser);
                            if (!existingSessionId.equals(request.getSession().getId())) {
                                excelConnections.remove(existingSessionId);
                            }
                            loggedInUser = existingUser;
                            newUser = false;
                        }
                    }
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                    if (newUser) {
                        excelConnections.put(request.getSession().getId() + "", loggedInUser);
                    }
                    if (op.equals("logon")) {
                        LoginInfo li = new LoginInfo(request.getSession().getId(), loggedInUser.getUser().getStatus());
                        return jacksonMapper.writeValueAsString(li);
                    }
                }
            }

            if (database != null && database.length() > 0) {
                LoginService.switchDatabase(loggedInUser, database);
            }

            if (reportName != null && reportName.length() > 0) {
                reportName = java.net.URLDecoder.decode(reportName, "UTF-8");
                loggedInUser.setOnlineReport(OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName.trim()));
            }

            if (op.equals("admin")) {
                //ManageDatabasesController.handleRequest(request);
                response.sendRedirect("/api/ManageDatabases");
                return "";
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
                    System.out.println(toReturn);
                    return toReturn;
                }
                return (jsonError("no details"));
                //buildContextMenuProvenanceDownload(provenanceDetailsForDisplay, reportId);
            }

            if (op.equals("getchoices")) {
                // I'm going to surpress errors for the moment until I can work out how to display them in the Excel TS
                try {
                    List<FilterTriple> filterOptions = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                            .getFilterListForQuery(loggedInUser.getDataAccessToken(), choice, chosen, loggedInUser.getUser().getEmail());//choice is the name of the range, chosen= the name of the choice cell (not its value as previously stated. I don't think anyway!)
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
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), choice, loggedInUser.getUser().getEmail(), chosen);
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
                SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choice, chosen);
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
                return CommonReportUtils.resolveQuery(loggedInUser, chosen);
            }
            if (op.equals("getdropdownlistforquery")) {
                System.out.println("choice query : " + choice);
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
                    if ((reportName == null || reportName.length() == 0) && (database == null || database.length() == 0)) {
                        //get initial menu
                        List<OnlineReport> reports = AdminService.getReportList(loggedInUser);
                        if (reports.size() == 1 && !reports.get(0).getReportName().equals("No reports found")) {
                            onlineReport = reports.get(0);
                            downloadName = onlineReport.getReportName();
                        } else {
                            //list the reports
                            file = ExcelService.listReports(request, reports);
                            downloadName = "Available reports";
                        }
                    } else {
                        reportName = reportName.trim();
                        if (reportName.contains(" uploaded by")) {
                            reportName = reportName.substring(0, reportName.indexOf(" uploaded by"));
                        }
                        if (reportName == null || reportName.length() == 0) {
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
                                //report id is assumed to be integer - sent from the website
                                onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                                onlineReport.setDatabase(database);
                                loggedInUser.getUser().setReportId(onlineReport.getId());
                            } else {
                                Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                                onlineReport = OnlineReportDAO.findForDatabaseIdAndName(db.getId(), reportName);
                                boolean allowed = false;
                                for (String key : loggedInUser.getPermissionsFromReport().keySet()) {
                                    TypedPair<OnlineReport, Database> dbreport = loggedInUser.getPermissionsFromReport().get(key);
                                    if (dbreport.getFirst().getId() == onlineReport.getId() && dbreport.getSecond().getId() == db.getId()) {
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
                        // ok new thing - we want the xlsx file to auto open a plugin pane, there's a function to add this in
                        String convertedFile = createAutoOpenIfRequired(file.getAbsolutePath());
                        byte[] bytes = Files.readAllBytes(Paths.get(convertedFile));
                        byte[] encodedBytes = Base64.getEncoder().encode(bytes);
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
                if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                    List<OnlineReport> reports = AdminService.getReportList(loggedInUser);
                    for (OnlineReport or : reports) {
                        databaseReports.add(new DatabaseReport(or.getDatabase() + " :   " + or.getUntaggedReportName(), or.getAuthor(), or.getReportName(), or.getDatabase()));
                    }

                } else {
                    Map<String, TypedPair<OnlineReport, Database>> permitted = loggedInUser.getPermissionsFromReport();
                    for (String st : permitted.keySet()) {
                        TypedPair<OnlineReport, Database> tp = permitted.get(st);
                        databaseReports.add(new DatabaseReport(st, tp.getFirst().getAuthor(), tp.getFirst().getUntaggedReportName(), tp.getSecond().getName()));
                    }
                }
                return jacksonMapper.writeValueAsString(databaseReports);
            }
            if (op.equals("loadregion")) {
                // since this expects a certain type of json format returned then we need to wrap the error in one of those objects
                System.out.println("json : " + json);
                ExcelJsonRequest excelJsonRequest = jacksonMapper.readValue(json, ExcelJsonRequest.class);
                try {
                    long time = System.currentTimeMillis();
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
                        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, excelJsonRequest.region, 0, excelJsonRequest.rowHeadings, excelJsonRequest.columnHeadings,
                                excelJsonRequest.context, userRegionOptions, true);
                        RegionOptions holdOptions = cellsAndHeadingsForDisplay.getOptions();//don't want to send these to Excel
                        cellsAndHeadingsForDisplay.setOptions(null);
                        loggedInUser.setSentCells(loggedInUser.getOnlineReport().getId(), excelJsonRequest.sheetName, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                        result = jacksonMapper.writeValueAsString(new CellsAndHeadingsForExcel(cellsAndHeadingsForDisplay));
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
                            true, "Error : " + e.getMessage(), 0, false,0,0,false, false, "", 0)
                    );
                    data.add(row);
                    errorHeading.add(error);
                    return jacksonMapper.writeValueAsString(new CellsAndHeadingsForExcel(new CellsAndHeadingsForDisplay(
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
            if (op.equals("saveregion")) {
                ExcelJsonRequest excelJsonRequest = jacksonMapper.readValue(json, ExcelJsonRequest.class);
                loggedInUser.setContext(excelJsonRequest.userContext);
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(loggedInUser.getUser().getReportId(), excelJsonRequest.sheetName, excelJsonRequest.region);
                List<List<String>> data = excelJsonRequest.data;
                List<List<CellForDisplay>> oldData = cellsAndHeadingsForDisplay.getData();

                if (data.size() < oldData.size() || (data != null && oldData != null && data.size() > 0 && oldData.size() > 0 && data.get(0).size() != oldData.get(0).size()))
                    return "error: data region " + excelJsonRequest.region + " on " + excelJsonRequest.sheetName + " has changed size.";
                Iterator<List<String>> rowIt = data.iterator();
                for (List<CellForDisplay> oldRow : oldData) {//for the moment, ignore any lines below old data - assume to be blank.....
                    List<String> row = rowIt.next();
                    Iterator cellIt = oldRow.iterator();
                    for (String cell : row) {
                        CellForDisplay oldCell = (CellForDisplay) cellIt.next();
                        if (!isEqual(oldCell.getStringValue(), cell)) {
                            oldCell.setNewStringValue(cell);
                            oldCell.setChanged();
                        }
                    }//no persisting
                }
                int reportId = loggedInUser.getUser().getReportId();
                reportName = OnlineReportDAO.findById(reportId).getReportName();
                result = SpreadsheetService.saveData(loggedInUser, reportId, reportName, excelJsonRequest.sheetName, excelJsonRequest.region, false);
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

    public String createAutoOpenIfRequired(String sourceFile) throws IOException {
        File check = new File(sourceFile.substring(0, sourceFile.lastIndexOf(".")) + "autoopen" + sourceFile.substring(sourceFile.lastIndexOf(".")));
        // need to do this at the moment as I'm not sure how we deal with reports being updated . . .
        // question is - if there is an autoopen version has the original been updated in the mean time? Some kind of timestamp against the original file
        if (check.exists()) {
            check.delete();
        }

        // this needs to be somewhere better. Todo.
        String patchFilesSource = SpreadsheetService.getPatchFilesSource();
        if (patchFilesSource == null || patchFilesSource.isEmpty()){
            return "no patch files";
        }
        //String zipFile = "/home/edward/Downloads/xlsxbreakdown/tomodify.xlsx";
        FileUtils.copyFile(new File(sourceFile), new File(sourceFile + ".bak")); // worth making a backup as the .explode below will zap the original it seems
        ZipUtil.explode(new File(sourceFile));
                /* right, in here I need to hack the XML files
                Based off a diff between files with the bits we need and files which don't :
                1. [Content_Types].xml] - add

                <Override PartName="/xl/webextensions/taskpanes.xml" ContentType="application/vnd.ms-office.webextensiontaskpanes+xml"/>
                <Override PartName="/xl/webextensions/webextension1.xml" ContentType="application/vnd.ms-office.webextension+xml"/>

                in the root <Types> element, presumably order is not important so jam it before the </Types>?

                .rels has added a relationship reference, need to add something like
                <Relationship Id="rId2" Type="http://schemas.microsoft.com/office/2011/relationships/webextensiontaskpanes" Target="xl/webextensions/taskpanes.xml"/>
                bur presumably not interfere with other relationships

                Three files added to /xl/webextensions
                taskpanes.xml
                webextension1.xml
                _rels/taskpanes.xml.rels

                The first two represent what the link below says needs to be added. I guess adding the third won't hurt though I have no idea if it is necessary.

                https://docs.microsoft.com/en-us/office/dev/add-ins/develop/automatically-open-a-task-pane-with-a-document
                 */

        File xmlFileToPatch = new File(sourceFile + "/[Content_Types].xml");
        String xml = FileUtils.readFileToString(xmlFileToPatch);
        xml = xml.substring(0, xml.indexOf("</Types>"))
                + "<Override PartName=\"/xl/webextensions/taskpanes.xml\" ContentType=\"application/vnd.ms-office.webextensiontaskpanes+xml\"/>"
                + "<Override PartName=\"/xl/webextensions/webextension1.xml\" ContentType=\"application/vnd.ms-office.webextension+xml\"/>"
                + xml.substring(xml.indexOf("</Types>"));
        FileUtils.deleteQuietly(xmlFileToPatch);
        FileUtils.write(xmlFileToPatch, xml);

        // now patch the .rels file

        File relsFileToPatch = new File(sourceFile + "/_rels/.rels");
        String rels = FileUtils.readFileToString(relsFileToPatch);
                /* ok find the point at which existing relationships stop and add
                 <Relationship Id="rId2" Type="http://schemas.microsoft.com/office/2011/relationships/webextensiontaskpanes" Target="xl/webextensions/taskpanes.xml"/>
                with a non taken id
                */
        int i;
        for (i = 1; i < 20; i++) { // arbitrary stop at 20, I'm looking for the first spare
            if (!rels.contains("Relationship Id=\"rId" + i + "\"")) {
                break;
            }
        }
        // jam it right before </Relationships>

        rels = rels.substring(0, rels.indexOf("</Relationships>"))
                + "<Relationship Id=\"rId" + i + "\" Type=\"http://schemas.microsoft.com/office/2011/relationships/webextensiontaskpanes\" Target=\"xl/webextensions/taskpanes.xml\"/>"
                + rels.substring(rels.indexOf("</Relationships>"));
        FileUtils.deleteQuietly(relsFileToPatch);
        FileUtils.write(relsFileToPatch, rels);

        // now copy in the patch files . . .
        FileUtils.copyDirectory(new File(patchFilesSource), new File(sourceFile + "/xl/webextensions/"));
        ZipUtil.pack(new File(sourceFile), new File(sourceFile.substring(0, sourceFile.lastIndexOf(".")) + "autoopen" + sourceFile.substring(sourceFile.lastIndexOf("."))));
        FileUtils.deleteDirectory(new File(sourceFile));
        //restore the backup
        FileUtils.copyFile(new File(sourceFile + ".bak"), new File(sourceFile));
        FileUtils.deleteQuietly(new File(sourceFile + ".bak"));
        return sourceFile.substring(0, sourceFile.lastIndexOf(".")) + "autoopen" + sourceFile.substring(sourceFile.lastIndexOf("."));

    }

    private boolean isEqual(String s1, String s2) {
        if (s1.equals(s2)) return true;
        try {
            double d1 = Double.parseDouble(s1);
            double d2 = Double.parseDouble(s2);
            double diff = (d1 - d2);
            if (diff < .0000000001 && diff > -.0000000001) return true;
        } catch (Exception e) {

        }
        return false;
    }


    private String jsonError(String error) {
        return "{\"error\":\"" + error + "\"}";
    }

    // when not multipart - this is a bit annoying, hopefully can find a way around it later
    @ResponseBody
    @RequestMapping
    public String handleRequest(HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "sessionid", required = false) String sessionId
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "database", required = false) String database
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
            , @RequestParam(value = "json", required = false) String json

    ) {
        return handleRequest(request, response, sessionId, op, database, reportName, sheetName, logon, password, region, options, regionrow, regioncol, choice, chosen, json, "true");
    }

}