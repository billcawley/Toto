package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.OpenDatabaseDAO;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.admin.user.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.jsonentities.DisplayValuesForProvenance;
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.CellForDisplay;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.ui.ModelMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.InetAddress;
import java.util.*;

// it seems that trying to configure the properties in spring is a problem
// this works but I'm a bit fed up of something that should be simple, go to a simple classpath read??
@Configuration
@PropertySource({"classpath:azquo.properties"})

public class SpreadsheetService {

    private static final Logger logger = Logger.getLogger(SpreadsheetService.class);

    @Autowired
    RMIClient rmiClient;

    @Autowired
    UserChoiceDAO userChoiceDAO;

    @Autowired
    UserRegionOptionsDAO userRegionOptionsDAO;

    @Autowired
    LoginService loginService;

    @Autowired
    AdminService adminService;

    @Autowired
    DatabaseDAO databaseDAO;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    LoginRecordDAO loginRecordDAO;

    @Autowired
    UploadRecordDAO uploadRecordDAO;

    @Autowired
    PermissionDAO permissionDAO;

    @Autowired
    OpenDatabaseDAO openDatabaseDAO;

    @Autowired
    UserDAO userDAO;

    @Autowired
    ServletContext servletContext;

    @Autowired
    private Environment env;

    private final String host;
    private String homeDir = null;

    // ints not boolean as I want to be able to tell if not set. Thread safety not such a concern, it's reading from a file, can't see how the state would be corrupted
    private int devMachine = -1;
    private int asposeLicense = -1;

    public static final String AZQUOHOME = "azquo.home";
    public static final String ASPOSELICENSE = "aspose.license";
    public static final String DEVMACHINE = "dev.machine";

    public String getHomeDir() {
        if (homeDir == null) {
            homeDir = env.getProperty(host + "." + AZQUOHOME);
            if (homeDir == null) {
                homeDir = env.getProperty(AZQUOHOME);
            }
        }
        return homeDir;
    }

    public boolean useAsposeLicense() {
        if (asposeLicense == -1) {
            if (env.getProperty(host + "." + ASPOSELICENSE) != null) {
                asposeLicense = (env.getProperty(host + "." + ASPOSELICENSE).equalsIgnoreCase("true") ? 1 : 0);
            } else {
                // if null default false
                asposeLicense = (env.getProperty(ASPOSELICENSE) != null && env.getProperty(ASPOSELICENSE).equalsIgnoreCase("true") ? 1 : 0);
            }
        }
        return asposeLicense == 1;
    }

    public boolean onADevMachine() {
        if (devMachine == -1) {
            if (env.getProperty(host + "." + DEVMACHINE) != null) {
                devMachine = (env.getProperty(host + "." + DEVMACHINE).equalsIgnoreCase("true") ? 1 : 0);
            } else {
                devMachine = (env.getProperty(DEVMACHINE) != null && env.getProperty(DEVMACHINE).equalsIgnoreCase("true") ? 1 : 0);
            }
        }
        return devMachine == 1;
    }

    public final int availableProcessors;
    public final int threadsToTry;

    public SpreadsheetService() {

        String current = null;
        try {
            current = new File( "." ).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Current dir:"+current);
        String thost = "";
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // may as well in case it goes wrong
        }
        System.out.println("host : " + thost);
        availableProcessors = Runtime.getRuntime().availableProcessors();
        System.out.println("Available processors : " + availableProcessors);
        host = thost;
        threadsToTry = availableProcessors < 4 ? availableProcessors : (availableProcessors / 2);

    }

    // What actually delivers the reports to the browser. Maybe change to an output writer? Save memory and increase speed.
    // also there's html in here, view stuff, need to get rid of that
    // the report/database server is going to force the view/model split

    public void readExcel(ModelMap model, LoggedInUser loggedInUser, OnlineReport onlineReport, String spreadsheetName) throws Exception {
        String message;
        String path = getHomeDir() + "/temp/";
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, userRegionOptionsDAO, this);
        StringBuilder worksheet = new StringBuilder();
        StringBuilder tabs = new StringBuilder();
        StringBuilder head = new StringBuilder();
        loggedInUser.setAzquoBook(azquoBook);  // is this a heavy object to put against the session?
        if (spreadsheetName == null) {
            spreadsheetName = "";
        }
        if (onlineReport.getId() == 1 && spreadsheetName.equals("Upload")) {
            model.addAttribute("enctype", " enctype=\"multipart/form-data\" ");
        } else {
            model.addAttribute("enctype", "");
        }
        try {
            if (onlineReport.getId() < 2) {// we don't look in the DB directory
                azquoBook.loadBook(onlineReport.getFilename(), useAsposeLicense());
            } else {
                //note - the database specified in the report may not be the current database (as in applications such as Magento and reviews), but be 'temp'
                String filepath = ImportService.dbPath + onlineReport.getPathname() + "/onlinereports/" + onlineReport.getFilename();
                azquoBook.loadBook(getHomeDir() + filepath, useAsposeLicense());
                // excecuting parked commented as the executeloop code makes no sense
                /*
                String executeSetName = azquoBook.getRangeValue("az_ExecuteSet");
                List<SetNameChosen> nameLoop = new ArrayList<SetNameChosen>();
                String executeSet = null;
                if (executeSetName != null && executeSetName.length() > 0) {
                    executeSet = azquoBook.getRangeValue(executeSetName);
                }
                if (executeSet != null) {
                    String[] executeItems = executeSet.split(",");
                    for (String executeItem : executeItems) {

                        if (executeItem.length() > 0 && executeItem.toLowerCase().endsWith("choice")) {
                            List<Name> nameList = nameService.parseQuery(loggedInUser, executeItem);
                            if (nameList != null) {
                                SetNameChosen nextSetNameChosen = new SetNameChosen();
                                nextSetNameChosen.setName = executeItem.toLowerCase().replace("choice", "chosen");
                                nextSetNameChosen.choiceList = nameList;
                                nextSetNameChosen.chosen = null;
                                nameLoop.add(nextSetNameChosen);
                            }
                        }
                    }
                    executeLoop(loggedInUser, onlineReport.getId(), nameLoop, 0);
                    return "";
                }*/
            }
            azquoBook.dataRegionPrefix = AzquoBook.azDataRegion;
            spreadsheetName = azquoBook.printTabs(tabs, spreadsheetName);
            message = azquoBook.convertSpreadsheetToHTML(loggedInUser, onlineReport.getId(), spreadsheetName, worksheet);
        } catch (Exception e) {
            e.printStackTrace();
            throw (e);
        }
        head.append("<style>\n");
        head.append(azquoBook.printAllStyles());
        head.append(readFile("css/excelStyle.css"));
        head.append("</style>\n");
        //velocityContext.put("script",readFile("online.js").toString());
        //velocityContext.put("topmenu",createTopMenu(loggedInConnection).toString());
        azquoBook.fillVelocityOptionInfo(loggedInUser, model,onlineReport.getId());
        
        model.addAttribute("tabs", tabs.toString());
        model.addAttribute("topmessage", message);
        if (onlineReport.getId() == 1 && spreadsheetName.equalsIgnoreCase("reports")) {
            spreadsheetName = "";
        }
        model.addAttribute("spreadsheetname", spreadsheetName);
        model.addAttribute("topcell", azquoBook.getTopCell() + "");
        model.addAttribute("leftcell", azquoBook.getLeftCell() + "");
        model.addAttribute("maxheight", azquoBook.getMaxHeight() + "px");
        model.addAttribute("maxwidth", azquoBook.getMaxWidth() + "px");
        model.addAttribute("maxrow", azquoBook.getMaxRow() + "");
        model.addAttribute("maxcol", azquoBook.getMaxCol() + "");
        model.addAttribute("reportid", onlineReport.getId() + "");
        if (azquoBook.dataRegionPrefix.equals(AzquoBook.azDataRegion)) {

            model.addAttribute("menuitems", "[{\"position\":1,\"name\":\"Provenance\",\"enabled\":true,\"link\":\"showProvenance()\"},{\"position\":3,\"name\":\"Highlight changes\",\"enabled\":true,\"link\":\"showHighlight()\"}]");
        } else {
            model.addAttribute("menuitems", "[{\"position\":1,\"name\":\"Provenance\",\"enabled\":true,\"link\":\"showProvenance()\"}," +
                    "{\"position\":2,\"name\":\"Edit\",\"enabled\":true,\"link\":\"edit()\"}," +
                    "{\"position\":3,\"name\":\"Cut\",\"enabled\":true,\"link\":\"cut()\"}," +
                    "{\"position\":4,\"name\":\"Copy\",\"enabled\":true,\"link\":\"copy()\"}," +
                    "{\"position\":5,\"name\":\"Paste before\",\"enabled\":true,\"link\":\"paste(0)\"}," +
                    "{\"position\":6,\"name\":\"Paste after\",\"enabled\":true,\"link\":\"paste(1)\"}," +
                    "{\"position\":7,\"name\":\"Paste into\",\"enabled\":true,\"link\":\"paste(2)\"}," +
                    "{\"position\":8,\"name\":\"Delete\",\"enabled\":true,\"link\":\"deleteName()\"}]");
        }

        model.addAttribute("styles", head.toString());
        String ws = worksheet.toString();
        if (worksheet.indexOf("$azquodatabaselist") > 0) {
            ws = ws.replace("$azquodatabaselist", createDatabaseSelect(loggedInUser));
        }
        if (ws.indexOf("$fileselect") > 0) {
            ws = ws.replace("$fileselect", "<input type=\"file\" name=\"uploadfile\">");
        }
        model.addAttribute("workbook", ws);
        model.addAttribute("charts", azquoBook.drawCharts(loggedInUser, path).toString());
    }

/*    public void executeLoop(LoggedInConnection loggedInConnection, int reportId, List<SetNameChosen> nameLoop, int level) throws Exception {
        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        for (Name chosen : nameLoop.get(level).choiceList) {
            setUserChoice(loggedInConnection.getUser().getId(), reportId, nameLoop.get(level).setName, chosen.getDefaultDisplayName());
            level++;
            if (level == nameLoop.size()) {
                azquoBook.executeSheet(loggedInConnection);
            } else {
                executeLoop(loggedInConnection, reportId, nameLoop, level + 1);
            }
        }
    }*/

    // to put a referenced CSS inline for example
    // edd changing to read from web-inf

    StringBuilder readFile(String filename) {
        // First, copy the base css
        StringBuilder sb = new StringBuilder();
        BufferedReader in = null;
        try {

            in = new BufferedReader(new InputStreamReader(servletContext.getResourceAsStream("/WEB-INF/" + filename)));
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Reading standard css", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    //noinspection ThrowFromFinallyBlock
                    throw new IllegalStateException("Reading standard css", e);
                }
            }
        }
        return sb;
    }

    public void setUserChoice(int userId, String choiceName, String choiceValue) {
        UserChoice userChoice = userChoiceDAO.findForUserIdAndChoice(userId, choiceName);
        if (choiceValue != null && choiceValue.length() > 0) {
            if (userChoice == null) {
                userChoice = new UserChoice(0, userId, choiceName, choiceValue, new Date());
                userChoiceDAO.store(userChoice);
            } else {
                if (!choiceValue.equals(userChoice.getChoiceValue())) {
                    userChoice.setChoiceValue(choiceValue);
                    userChoice.setTime(new Date());
                    userChoiceDAO.store(userChoice);
                }
            }
        } else {
            if (userChoice != null) {
                userChoiceDAO.removeById(userChoice);
            }
        }
    }

    public void saveBook(HttpServletResponse response, LoggedInUser loggedInUser, String fileName) throws Exception {
        loggedInUser.getAzquoBook().saveBook(response, fileName);
    }

    public void saveBookasPDF(HttpServletResponse response, LoggedInUser loggedInUser, String fileName) throws Exception {
        loggedInUser.getAzquoBook().saveBookAsPDF(response, fileName);
    }

    public void saveBookActive(HttpServletResponse response, LoggedInUser loggedInUser, String fileName) throws Exception {
        loggedInUser.getAzquoBook().saveBookActive(response, fileName, env.getProperty("azquo.home") + "/onlinereports/Admin/Azquoblank.xls");
    }

    public String getProvenance(LoggedInUser loggedInUser, int row, int col, String jsonFunction) throws Exception {
        return loggedInUser.getAzquoBook().getProvenance(loggedInUser, row, col, jsonFunction);
    }

    public StringBuilder createDatabaseSelect(LoggedInUser loggedInUser) {
        StringBuilder sb = new StringBuilder();
        String chosen = "";
        Map<String, Database> foundDatabases = loginService.foundDatabases(loggedInUser.getUser());
        if (foundDatabases.size() > 1) {
            if (loggedInUser.getDatabase() != null) chosen = loggedInUser.getDatabase().getName();
            sb.append("<select class=\"databaseselect\" name=\"database\" id=\"databasechosen\" value=\"").append(chosen).append("\">\n");
            sb.append("<option value=\"\">No database chosen</option>");
            for (String dbName : foundDatabases.keySet()) {
                sb.append("<option value =\"").append(dbName).append("\"");
                if (dbName.equals(chosen)) sb.append(" selected");
                sb.append(">").append(dbName).append("</option>\n");
            }
            sb.append("</select>");
        } else {
            if (foundDatabases.size() == 1) {
                for (String dbName : foundDatabases.keySet()) {
                    sb.append(dbName);
                }
            } else {
                sb.append("No database available");
            }
        }
        return sb;
    }

    // on logging into Magento reports for example

    public void showUserMenu(ModelMap model, LoggedInUser loggedInUser) {
        Map<String, Database> databases = loginService.foundDatabases(loggedInUser.getUser());
         model.addAttribute("welcome", "Welcome to Azquo!");
        List<Map<String, String>> reports = new ArrayList<Map<String, String>>();
        for (String dbName:databases.keySet()) {
            Database database = databases.get(dbName);
            List<OnlineReport> onlineReports = onlineReportDAO.findForDatabaseIdAndUserStatus(database.getId(), loggedInUser.getUser().getStatus(),database.getDatabaseType());
            //TODO  set a database for each report - also check permissions
            model.addAttribute("database", database);
            String reportCategory = "";

            for (OnlineReport onlineReport : onlineReports) {
                Map<String, String> vReport = new HashMap<String, String>();
                if (!onlineReport.getReportCategory().equals(reportCategory)) {
                    vReport.put("category", onlineReport.getReportCategory());
                } else {
                    vReport.put("category", "");
                }
                reportCategory = onlineReport.getReportCategory();
                vReport.put("name", onlineReport.getReportName());
                vReport.put("explanation", onlineReport.getExplanation());
                vReport.put("link", "/api/Online/?opcode=loadsheet&reportid=" + onlineReport.getId());
                reports.add(vReport);
            }
        }
        model.addAttribute("reports", reports);
    }

    public void showNameDetails(ModelMap model, LoggedInUser loggedInUser, String database, String rootId, String parents, String searchNames) throws Exception {
        model.addAttribute("message","");
        if (database != null && database.length() > 0) {
            Database newDB = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
            if (newDB == null) {
                model.addAttribute("message","no database chosen");
            }
            loginService.switchDatabase(loggedInUser, newDB);
        }
        if (searchNames == null) searchNames = "";
        model.addAttribute("parents", parents);
        model.addAttribute("rootid", rootId);
        model.addAttribute("searchnames", searchNames);
    }

    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception{
        return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).getDropDownListForQuery(databaseAccessToken, query, languages);
    }

    // function that can be called by the front end to deliver the data and headings

    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , UserRegionOptions userRegionOptions) throws Exception {
        return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).getCellsAndHeadingsForDisplay(databaseAccessToken, rowHeadingsSource, colHeadingsSource, contextSource,
                userRegionOptions.getHideRows(), userRegionOptions.getRowLimit(), userRegionOptions.getColumnLimit(), userRegionOptions.getSortRow()
                , userRegionOptions.getSortRowAsc(), userRegionOptions.getSortColumn(), userRegionOptions.getSortColumnAsc(), userRegionOptions.getHighlightDays());
    }

    public String processJSTreeRequest(DatabaseAccessToken dataAccessToken, String json, String jsTreeId, String topNode, String op, String parent, boolean parents, String itemsChosen, String position, String backupSearchTerm) throws Exception{
        return rmiClient.getServerInterface(dataAccessToken.getServerIp()).processJSTreeRequest(dataAccessToken, json, jsTreeId, topNode, op, parent, parents, itemsChosen, position, backupSearchTerm);
    }

    // ok now this is going to ask the DB, it needs the selection criteria and original row and col for speed (so we don't need to get all the data and sort)
    public List<DisplayValuesForProvenance> getDisplayValuesForProvenance(LoggedInUser loggedInUser, String region, int rowInt, int colInt) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(region);
        if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getData().get(rowInt) != null && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).formatDataRegionProvenanceForOutput(databaseAccessToken, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol());
        }
        return new ArrayList<DisplayValuesForProvenance>(); // maybe "not found"?
    }

    public void saveData(LoggedInUser loggedInUser, String region, String reportName) throws Exception {
        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(region);
        if (cellsAndHeadingsForDisplay != null){
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            rmiClient.getServerInterface(databaseAccessToken.getServerIp()).saveData(databaseAccessToken, cellsAndHeadingsForDisplay, loggedInUser.getUser().getName(), reportName, loggedInUser.getContext());
        }
    }

}