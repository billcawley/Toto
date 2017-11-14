package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.ExcelService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zkoss.util.media.AMedia;
import org.zkoss.zss.api.*;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.api.model.Validation;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;
import org.zkoss.zul.Filedownload;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static class JsonParameters {
        public final String op;
        public final String sessionid;
        public final String logon;
        public final String password;
        public final String database;
        public final String reportName;
        public final String sheetName;
        public final String region;
        public final String regionrow;
        public final String regioncol;
        public final String options;
        public final String choice;
        public final String chosen;
        public final Map<String, String> choices;

        @JsonCreator
        public JsonParameters(@JsonProperty("op") String op
                , @JsonProperty ("sessionid") String sessionid
                , @JsonProperty ("logon") String logon
                , @JsonProperty("password") String password
                , @JsonProperty("database") String database
                , @JsonProperty("reportName") String reportName
                , @JsonProperty("sheetName") String sheetName
                , @JsonProperty("region") String region
                , @JsonProperty("regionrow") String regionrow
                , @JsonProperty("regioncol") String regioncol
                , @JsonProperty("options") String options
                , @JsonProperty("choice") String choice
                , @JsonProperty("chosen") String chosen
                , @JsonProperty("choices") Map<String, String> choices) {
            this.op = op;
            this.sessionid = sessionid;
            this.logon = logon;
            this.password = password;
            this.database = database;
            this.reportName = reportName;
            this.sheetName = sheetName;
            this.region = region;
            this.regionrow = regionrow;
            this.regioncol = regioncol;
            this.options = options;
            this.choice = choice;
            this.chosen = chosen;
            this.choices = choices;
        }
    }

    private static class JsonRegion implements Serializable {
        public final String name;
        public final int rows;
        public final int columns;
        public final List<List<String>> data;

        private JsonRegion(String name, int rows, int columns, List<List<String>> data) {
            this.name = name;
            this.rows = rows;
            this.columns = columns;
            this.data = data;
        }
    }

    private static class JsonChoice implements Serializable {
        public final String name;
        public final String value;
        public final List<String> options;

        private JsonChoice(String name, String value, List<String> options) {
            this.name = name;
            this.value = value;
            this.options = options;
        }
    }


    private static class JsonReturn implements Serializable {
        public final String error;
        public final String database;
        public final String reportName;
        public final List<JsonChoice> choices;
        public final List<JsonRegion> regions;

        public JsonReturn(String error, String database, String reportName, List<JsonChoice> choices, List<JsonRegion> regions) {
            this.error = error;
            this.database = database;
            this.reportName = reportName;
            this.choices = choices;
            this.regions = regions;

        }
    }



    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String BOOK_PATH = "BOOK_PATH";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";


    @RequestMapping
    @ResponseBody
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "json", required = false) String jsonString
    ) {


        String result = "no action taken";
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "application/json");
        final ObjectMapper jacksonMapper = new ObjectMapper();

        try {
            jsonString = jsonString.replace("\\\"","\"");
            System.out.println("json sent " + jsonString);

            JsonParameters json = jacksonMapper.readValue(jsonString, JsonParameters.class);
            try {
                LoggedInUser loggedInUser = null;
                if (json.sessionid!=null){
                    loggedInUser = excelConnections.get(json.sessionid);
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);

                }
                if (loggedInUser == null) {
                    loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), json.database, json.logon, json.password, false);
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
                        if (json.op.equals("logon")) {
                            List<OnlineReport> reports = AdminService.getReportList(loggedInUser);
                            List<JsonReturn> reportList = new ArrayList<>();
                            for (OnlineReport report:reports){
                                reportList.add(new JsonReturn(null,report.getDatabase(),report.getReportName(), null,null));
                            }
                            String returnString = "{\"error\":\"\",\"sessionid\":\"" + request.getSession().getId() + "\",\"reports\":" + jacksonMapper.writeValueAsString(reportList) + "}";
                            return returnString;
                        }
                    }
                }
                if (json.database!=null && json.database.length() > 0){
                    LoginService.switchDatabase(loggedInUser,json.database);
                }
                if (json.reportName!=null && json.reportName.length() > 0){
                    loggedInUser.setOnlineReport(OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(),json.reportName));
                }
                if (json.op.equals("admin")){
                    //ManageDatabasesController.handleRequest(request);
                      response.sendRedirect("/api/ManageDatabases");
                      return "";

                 }
                if (json.op.equals("audit")) {
                    UserRegionOptions userRegionOptions = ExcelService.getUserRegionOptions(loggedInUser,json.options,loggedInUser.getOnlineReport().getId(),json.region);
                    jacksonMapper.registerModule(new JavaTimeModule());
                    //jacksonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


                    //jacksonMapper.registerModule(new JavaTimeModule());
                    final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, loggedInUser.getOnlineReport().getId(), json.sheetName,
                            json.region, userRegionOptions, Integer.parseInt(json.regionrow), Integer.parseInt(json.regioncol), 1000);
                    if (provenanceDetailsForDisplay.getAuditForDisplayList() != null && !provenanceDetailsForDisplay.getAuditForDisplayList().isEmpty()) {
                         return jacksonMapper.writeValueAsString(provenanceDetailsForDisplay);
                    }
                        //buildContextMenuProvenanceDownload(provenanceDetailsForDisplay, reportId);
                }

                if (json.choices != null) {
                    for (String key : json.choices.keySet()) {
                        //remove 'chosen' from key
                        SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), key.substring(0, key.length() - 6), json.choices.get(key));
                    }
                }
                // database switching should be done by being logged in

                OnlineReport onlineReport = null;
                         if (json.reportName==null || json.reportName.length() == 0) {
                    onlineReport = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                    if (onlineReport!=null){
                        onlineReport.setDatabase(loggedInUser.getDatabase().getName());
                    }
                } else {
                    if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                        //report id is assumed to be integer - sent from the website
                        onlineReport = OnlineReportDAO.findForNameAndBusinessId(json.reportName, loggedInUser.getUser().getBusinessId());
                        onlineReport.setDatabase(json.database);
                    }
                }
                if (onlineReport == null) {
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader("Content-type", "application/json");
                    return jsonError("incorrect report name"); // probably need to add json
                }
                if (json.choices != null) {
                    for (String key : json.choices.keySet()) {
                        SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), key, json.choices.get(key));
                    }
                }
                // no region options at the moment
                try {
                    long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                    String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
                    final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                    book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                    book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                    // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                    book.getInternalBook().setAttribute(REPORT_ID, onlineReport.getId());
                    ReportRenderer.populateBook(book, 0);
                    long newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                    System.out.println();
                    System.out.println("Heap cost to populate book : " + (newHeapMarker - oldHeapMarker) / mb);
                    System.out.println();

                    // WFC DOWNLOAD TEST


                    Exporter exporter = Exporters.getExporter();
                    File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(file);
                        exporter.export(book, fos);
                    } finally {
                        if (fos != null) {
                            fos.close();
                        }
                    }
                    int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // Set up mime type
                    try {
                        DownloadController.streamFileToBrowser(file.toPath(), response, onlineReport.getReportName()+ ".xlsx");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return jsonError("OK");



                    /* WFC commented out this code (which is for an API to return relevant spreadsheet info) in order to test new Excel download functionality..


                    List<JsonRegion> jsonRegions = new ArrayList<>();
                    List<JsonChoice> jsonChoices = new ArrayList<>();
                    final List<SName> names = book.getInternalBook().getNames();
                    for (SName name : names) {
                        final Sheet sheet = book.getSheet(name.getRefersToSheetName());
                        final CellRegion refersToCellRegion = name.getRefersToCellRegion();
                        if (name.getName().toLowerCase().startsWith("az_web")) {
                            List<List<String>> data = new ArrayList<>();
                            for (int i = refersToCellRegion.getRow(); i <= refersToCellRegion.getLastRow(); i++) {
                                List<String> row = new ArrayList<>();
                                for (int j = refersToCellRegion.getColumn(); j <= refersToCellRegion.getLastColumn(); j++) {
                                    row.add(getCellValue(sheet, i, j));
                                }
                                data.add(row);
                            }
                            jsonRegions.add(new JsonRegion(name.getName(), (refersToCellRegion.getLastRow() - refersToCellRegion.getRow()) + 1, (refersToCellRegion.getLastColumn() - refersToCellRegion.getColumn()) + 1, data));
                        } else {
                            if (name.getName().toLowerCase().endsWith("chosen")) {
                                String value = getCellValue(sheet, refersToCellRegion.getRow(), refersToCellRegion.getColumn());
                                List<String> options = new ArrayList<>();
                                Range range = Ranges.range(book.getSheet(name.getRefersToSheetName()), refersToCellRegion.getRow(), refersToCellRegion.getColumn(), refersToCellRegion.getRow(), refersToCellRegion.getColumn());
                                List<Validation> validations = range.getValidations();
                                for (Validation option : validations) {
                                    String listRangeString = option.getFormula1();
                                    //should be a quick way to get the range but I can't find it so...
                                    int endSheetName = listRangeString.indexOf("!");
                                    Sheet listSheet = book.getSheet(listRangeString.substring(1, endSheetName));
                                    String localListRange = listRangeString.substring(endSheetName + 1);
                                    Range listRange = Ranges.range(listSheet, localListRange);
                                    for (int row = 0; row < listRange.getRowCount(); row++) {
                                        options.add(getCellValue(listSheet, listRange.getRow() + row, listRange.getColumn()));

                                    }
                                }
                                jsonChoices.add(new JsonChoice(name.getName(), value, options));
                            }
                        }
                    }
                    result = jacksonMapper.writeValueAsString(new JsonReturn("ok", loggedInUser.getDatabase().getName(), onlineReport.getReportName(), jsonChoices, jsonRegions));
                    */
                } catch (Exception e) { // changed to overall exception handling
                    e.printStackTrace(); // Could be when importing the book, just log it
                    result = e.getMessage(); // put it here to puck up instead of the report
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

    static String getCellValue(Sheet sheet, int r, int c) {
        //this is a copy of the same function in ReportUtilities - but unavailable in this class
        String returnString = null;
        Range range = Ranges.range(sheet, r, c);
        CellData cellData = range.getCellData();
        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
        //if (colCount++ > 0) bw.write('\t');
        if (cellData != null) {
            String stringValue = "";
            try {
                stringValue = cellData.getFormatText();// I assume means formatted text
                if (r > 0 && dataFormat.toLowerCase().contains("mm-") && (stringValue.length() == 8 || stringValue.length() == 6)) {//fix a ZK bug
                    stringValue = stringValue.replace(" ", "-");//crude replacement of spaces in dates with dashes
                }
            } catch (Exception ignored) {
            }
            if (!dataFormat.toLowerCase().contains("m")) {//check that it is not a date or a time
                //if it's a number, remove all formatting
                try {
                    double d = cellData.getDoubleValue();
                    String newStringValue = d + "";
                    if (!newStringValue.contains("E")) {
                        if (newStringValue.endsWith(".0")) {
                            stringValue = newStringValue.substring(0, newStringValue.length() - 2);
                        } else {
                            stringValue = newStringValue;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (stringValue.contains("\"\"") && stringValue.startsWith("\"") && stringValue.endsWith("\"")) {
                //remove spuriouse quote marks
                stringValue = stringValue.substring(1, stringValue.length() - 1).replace("\"\"", "\"");
            }
            returnString = stringValue;
        }
        return returnString;
    }


    private boolean inSet(String toTest, String[] set) {
        for (String element : set) {
            if (element.trim().equalsIgnoreCase(toTest)) {
                return true;
            }
        }
        return false;
    }
}