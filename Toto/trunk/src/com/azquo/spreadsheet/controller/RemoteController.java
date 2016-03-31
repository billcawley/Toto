package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 29/03/16.
 * <p>
 * FOr API style access, I'm going to copy the online controller and modify
 */
@Controller
@RequestMapping("/Remote")
public class RemoteController {
    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;
    // note : I'm now thinking dao objects are acceptable in controllers if moving the call to the service would just be a proxy

    @Autowired
    private LoginService loginService;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private DatabaseServerDAO databaseServerDAO;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private UserRegionOptionsDAO userRegionOptionsDAO;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private ImportService importService;
    @Autowired
    private RMIClient rmiClient;

    /* Parameters are sent as Json . . . posted via https should be secure enough for the moment
         */
    private static class JsonParameters {
        public final String logon;
        public final String password;
        public final String database;
        public final String reportName;
        public final Map<String, String> choices;

        @JsonCreator
        public JsonParameters(@JsonProperty("logon") String logon
                ,@JsonProperty("password")  String password
                ,@JsonProperty("database")  String database
                ,@JsonProperty("reportName")  String reportName
                ,@JsonProperty("choices")  Map<String, String> choices) {
            this.logon = logon;
            this.password = password;
            this.database = database;
            this.reportName = reportName;
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

    private static class JsonReturn implements Serializable {
        public final String error;
        public final String database;
        public final String reportName;
        public final List<JsonRegion> regions;
        public JsonReturn(String error, String database, String reportName, List<JsonRegion> regions) {
            this.error = error;
            this.database = database;
            this.reportName = reportName;
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
    public String handleRequest(HttpServletRequest request
            , @RequestParam(value = "json", required = false) String json
    ) {
        try {
            JsonParameters jsonParameters = jacksonMapper.readValue(json, JsonParameters.class);
            Database db;
            try {
                LoggedInUser loggedInUser = loginService.loginLoggedInUser(request.getSession().getId(), jsonParameters.database, jsonParameters.logon, jsonParameters.password, false);
                if (loggedInUser == null) {
                    return "incorrect login details"; // probably need to add json
                }
                OnlineReport onlineReport = null;
                if (jsonParameters.reportName != null) {
                    //report id is assumed to be integer - sent from the website
                    onlineReport = onlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), jsonParameters.reportName);
                }
                if (onlineReport == null) {
                    return "incorrect report name"; // probably need to add json
                }
                loggedInUser.setReportId(onlineReport.getId());// that was below, whoops!
                if (onlineReport.getDatabaseId() > 0) {
                    db = databaseDAO.findById(onlineReport.getDatabaseId());
                    loginService.switchDatabase(loggedInUser, db);
                    onlineReport.setPathname(loggedInUser.getDatabase().getPersistenceName());
                } else {
                    db = loggedInUser.getDatabase();
                    onlineReport.setPathname(onlineReport.getDatabaseType());
                }
                if (db != null) {
                    onlineReport.setDatabase(db.getName());
                }
                if (jsonParameters.choices != null) {
                    for (String key : jsonParameters.choices.keySet()) {
                        spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), key, jsonParameters.choices.get(key));
                    }
                }
                // no region options at the moment

                try {
                    long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                    long newHeapMarker = oldHeapMarker;
                    String bookPath = spreadsheetService.getHomeDir() + ImportService.dbPath + onlineReport.getPathname() + "/onlinereports/" + onlineReport.getFilename();
                    final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                    book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                    book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                    // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                    book.getInternalBook().setAttribute(REPORT_ID, onlineReport.getId());
                    ZKAzquoBookUtils bookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO, rmiClient);
                    bookUtils.populateBook(book, 0);
                    newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                    System.out.println();
                    System.out.println("Heap cost to populate book : " + (newHeapMarker - oldHeapMarker) / mb);
                    System.out.println();
                    oldHeapMarker = newHeapMarker;
                    List<JsonRegion> jsonRegions = new ArrayList<>();
                    final List<SName> names = book.getInternalBook().getNames();
                    for (SName name : names){
                        final SSheet internalSheet = book.getSheet(name.getRefersToSheetName()).getInternalSheet();
                        final CellRegion refersToCellRegion = name.getRefersToCellRegion();
                        List<List<String>> data = new ArrayList<>();
                        for (int i = refersToCellRegion.getRow(); i <= refersToCellRegion.getLastRow(); i++){
                            List<String> row = new ArrayList<>();
                            for (int j = refersToCellRegion.getColumn(); j <= refersToCellRegion.getLastColumn(); j++){
                                final SCell cell = internalSheet.getCell(i, j);
                                if (cell.getType().equals(SCell.CellType.FORMULA)){
                                    System.out.println();
                                    if (cell.getFormulaResultType().equals(SCell.CellType.NUMBER)){
                                        row.add(cell.getNumberValue() + "");
                                    } else if (cell.getFormulaResultType().equals(SCell.CellType.ERROR)){
                                        row.add("FORMULA ERROR");
                                    } else {
                                        row.add(cell.getStringValue());
                                    }
                                } else if (cell.getType().equals(SCell.CellType.NUMBER)){
                                    row.add(cell.getNumberValue() + "");
                                } else {
                                    row.add(cell.getStringValue());
                                }
                            }
                            data.add(row);
                        }
                        jsonRegions.add(new JsonRegion(name.getName(), (refersToCellRegion.getLastRow() - refersToCellRegion.getRow()) + 1, (refersToCellRegion.getLastColumn() - refersToCellRegion.getColumn()) + 1, data));
                    }
                    return jacksonMapper.writeValueAsString(new JsonReturn("ok", loggedInUser.getDatabase().getName(), onlineReport.getReportName(), jsonRegions));
                } catch (Exception e) { // changed to overall exception handling
                    e.printStackTrace(); // Could be when importing the book, just log it
                    return e.getMessage(); // put it here to puck up instead of the report
                }
            } catch (Exception e) {
                logger.error("online controller error", e);
            }
            return "utf8page";
        } catch (NullPointerException npe) {
            return "null pointer, json passed?";
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }
}