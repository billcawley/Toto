package com.azquo.spreadsheet.controller;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.api.model.Validation;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 29/03/16.
 * <p>
 * For API style access, I'm going to copy the online controller and modify
 */
@Controller
@RequestMapping("/Remote")
public class RemoteController {
    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;
    // note : I'm now thinking dao objects are acceptable in controllers if moving the call to the service would just be a proxy
    /* Parameters are sent as Json . . . posted via https should be secure enough for the moment */
    private static class JsonParameters {
        public final String logon;
        public final String password;
        public final String database;
        public final String reportName;
        public final String regions;
        public final Map<String, String> choices;

        @JsonCreator
        public JsonParameters(@JsonProperty("logon") String logon
                , @JsonProperty("password") String password
                , @JsonProperty("database") String database
                , @JsonProperty("reportName") String reportName
                , @JsonProperty("regions") String regions
                , @JsonProperty("choices") Map<String, String> choices) {
            this.logon = logon;
            this.password = password;
            this.database = database;
            this.reportName = reportName;
            this.regions = regions;
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

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @RequestMapping
    @ResponseBody
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "json", required = false) String json
    ) {
        String result = "no action taken";
        try {
            System.out.println("json sent " + json);
            JsonParameters jsonParameters = jacksonMapper.readValue(json, JsonParameters.class);
            try {
                LoggedInUser loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), jsonParameters.database, jsonParameters.logon, jsonParameters.password, false);
                if (loggedInUser == null) {
                    return "incorrect login details"; // probably need to add json
                }

                if (jsonParameters.choices != null) {
                    for (String key : jsonParameters.choices.keySet()) {
                        //remove 'chosen' from key
                        SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), key.substring(0, key.length() - 6), jsonParameters.choices.get(key));
                    }
                }
                // database switching should be done by being logged in

                OnlineReport onlineReport = null;
                if (jsonParameters.reportName.length()==0){
                    onlineReport = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                    onlineReport.setDatabase(loggedInUser.getDatabase().getName());
                }else {
                    if (jsonParameters.reportName != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                        //report id is assumed to be integer - sent from the website
                        onlineReport = OnlineReportDAO.findForNameAndBusinessId(jsonParameters.reportName, loggedInUser.getUser().getBusinessId());
                        onlineReport.setDatabase(jsonParameters.database);
                    }
                }
                if (onlineReport == null) {
                    return "incorrect report name"; // probably need to add json
                }
                if (jsonParameters.choices != null) {
                    for (String key : jsonParameters.choices.keySet()) {
                        SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), key, jsonParameters.choices.get(key));
                    }
                }
                // no region options at the moment
                try {
                    long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                    String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + onlineReport.getFilenameForDisk();
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
                                    row.add(getCellValue(sheet,i,j));
                                }
                                data.add(row);
                            }
                            jsonRegions.add(new JsonRegion(name.getName(), (refersToCellRegion.getLastRow() - refersToCellRegion.getRow()) + 1, (refersToCellRegion.getLastColumn() - refersToCellRegion.getColumn()) + 1, data));
                        } else {
                            if (name.getName().toLowerCase().endsWith("chosen")) {
                                String value = getCellValue( sheet,refersToCellRegion.getRow(), refersToCellRegion.getColumn());
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
                                        options.add(getCellValue(listSheet,listRange.getRow() + row, listRange.getColumn()));

                                    }
                                }
                                jsonChoices.add(new JsonChoice(name.getName(), value, options));
                            }
                        }
                    }
                    result =  jacksonMapper.writeValueAsString(new JsonReturn("ok", loggedInUser.getDatabase().getName(), onlineReport.getReportName(), jsonChoices, jsonRegions));
             } catch (Exception e) { // changed to overall exception handling
                    e.printStackTrace(); // Could be when importing the book, just log it
                    result =e.getMessage(); // put it here to puck up instead of the report
                }
            } catch (Exception e) {
                logger.error("online controller error", e);
                result = e.getMessage();
            }
        } catch (NullPointerException npe) {
            result =  "null pointer, json passed?";
        } catch (Exception e) {
            e.printStackTrace();
            result  = e.getMessage();
        }
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "application/json");
        return result;

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
        return  returnString;
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