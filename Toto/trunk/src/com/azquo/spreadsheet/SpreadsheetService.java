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
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.MutableBoolean;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.view.AzquoBook;
import com.csvreader.CsvReader;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// it seems that trying to configure the properties in spring is a problem
// todo : see if upgrading spring to 4.2 makes this easier?
// todo : try to address proper use of protected and private given how I've shifter lots of classes around. This could apply to all sorts in the system.
@Configuration
@PropertySource({"classpath:azquo.properties"})

public class SpreadsheetService {

    private static final Logger logger = Logger.getLogger(SpreadsheetService.class);

    @Autowired
    NameService nameService;

    @Autowired
    UserChoiceDAO userChoiceDAO;

    @Autowired
    ValueService valueService;

    @Autowired
    LoginService loginService;

    @Autowired
    AdminService adminService;

    @Autowired
    ImportService importService;

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


    // best way to do this? A bean?
    public final StringUtils stringUtils;

    static class SetNameChosen {
        String setName;
        List<Name> choiceList;
        Name chosen;
    }

    static class UniqueName{
        Name name;
        String string;
    }

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
        stringUtils = new StringUtils();

        threadsToTry = availableProcessors < 4 ? availableProcessors : (availableProcessors / 2);

    }

    // What actually delivers the reports to the browser. Maybe change to an output writer? Save memory and increase speed.

    public String readExcel(LoggedInConnection loggedInConnection, OnlineReport onlineReport, String spreadsheetName, String message) throws Exception {
        String path = getHomeDir() + "/temp/";
        AzquoBook azquoBook = new AzquoBook(valueService, adminService, nameService, userChoiceDAO, this);
        StringBuilder worksheet = new StringBuilder();
        StringBuilder tabs = new StringBuilder();
        StringBuilder head = new StringBuilder();
        loggedInConnection.setAzquoBook(azquoBook);  // is this a heavy object to put against the session?
        VelocityContext velocityContext = new VelocityContext();
        if (spreadsheetName == null) {
            spreadsheetName = "";
        }
        if (onlineReport.getId() == 1 && spreadsheetName.equals("Upload")) {
            velocityContext.put("enctype", " enctype=\"multipart/form-data\" ");
        } else {
            velocityContext.put("enctype", "");
        }
        try {
            if (onlineReport.getId() < 2) {// we don't look in the DB directory
                azquoBook.loadBook(onlineReport.getFilename(), useAsposeLicense());
            } else {
                //note - the database specified in the report may not be the current database (as in applications such as Magento and reviews), but be 'temp'
                String filepath = ImportService.dbPath + onlineReport.getPathname() + "/onlinereports/" + onlineReport.getFilename();
                azquoBook.loadBook(getHomeDir() + filepath, useAsposeLicense());
                // excecuting parked for the mo while saving is, will need to think about reenabling
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
                            List<Name> nameList = nameService.parseQuery(loggedInConnection, executeItem);
                            if (nameList != null) {
                                SetNameChosen nextSetNameChosen = new SetNameChosen();
                                nextSetNameChosen.setName = executeItem.toLowerCase().replace("choice", "chosen");
                                nextSetNameChosen.choiceList = nameList;
                                nextSetNameChosen.chosen = null;
                                nameLoop.add(nextSetNameChosen);
                            }
                        }
                    }
                    executeLoop(loggedInConnection, onlineReport.getId(), nameLoop, 0);
                    return "";
                }*/
            }
            azquoBook.dataRegionPrefix = AzquoBook.azDataRegion;
            if (onlineReport.getId() == 1 || onlineReport.getId() == -1) {//this is the maintenance workbook
                azquoBook.dataRegionPrefix = AzquoBook.azInput;

            }
            spreadsheetName = azquoBook.printTabs(tabs, spreadsheetName);
            azquoBook.convertSpreadsheetToHTML(loggedInConnection, onlineReport.getId(), spreadsheetName, worksheet);
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
        azquoBook.fillVelocityOptionInfo(loggedInConnection, velocityContext);
        velocityContext.put("tabs", tabs.toString());
        velocityContext.put("topmessage", message);
        if (onlineReport.getId() == 1 && spreadsheetName.equalsIgnoreCase("reports")) {
            spreadsheetName = "";
        }
        velocityContext.put("spreadsheetname", spreadsheetName);
        velocityContext.put("topcell", azquoBook.getTopCell() + "");
        velocityContext.put("leftcell", azquoBook.getLeftCell() + "");
        velocityContext.put("maxheight", azquoBook.getMaxHeight() + "px");
        velocityContext.put("maxwidth", azquoBook.getMaxWidth() + "px");
        velocityContext.put("maxrow", azquoBook.getMaxRow() + "");
        velocityContext.put("maxcol", azquoBook.getMaxCol() + "");
        velocityContext.put("reportid", onlineReport.getId() + "");
        if (azquoBook.dataRegionPrefix.equals(AzquoBook.azDataRegion)) {

            velocityContext.put("menuitems", "[{\"position\":1,\"name\":\"Provenance\",\"enabled\":true,\"link\":\"showProvenance()\"},{\"position\":3,\"name\":\"Highlight changes\",\"enabled\":true,\"link\":\"showHighlight()\"}]");
        } else {
            velocityContext.put("menuitems", "[{\"position\":1,\"name\":\"Provenance\",\"enabled\":true,\"link\":\"showProvenance()\"}," +
                    "{\"position\":2,\"name\":\"Edit\",\"enabled\":true,\"link\":\"edit()\"}," +
                    "{\"position\":3,\"name\":\"Cut\",\"enabled\":true,\"link\":\"cut()\"}," +
                    "{\"position\":4,\"name\":\"Copy\",\"enabled\":true,\"link\":\"copy()\"}," +
                    "{\"position\":5,\"name\":\"Paste before\",\"enabled\":true,\"link\":\"paste(0)\"}," +
                    "{\"position\":6,\"name\":\"Paste after\",\"enabled\":true,\"link\":\"paste(1)\"}," +
                    "{\"position\":7,\"name\":\"Paste into\",\"enabled\":true,\"link\":\"paste(2)\"}," +
                    "{\"position\":8,\"name\":\"Delete\",\"enabled\":true,\"link\":\"deleteName()\"}]");
        }

        velocityContext.put("styles", head.toString());
        String ws = worksheet.toString();
        if (worksheet.indexOf("$azquodatabaselist") > 0) {
            ws = ws.replace("$azquodatabaselist", createDatabaseSelect(loggedInConnection));
        }
        if (ws.indexOf("$fileselect") > 0) {
            ws = ws.replace("$fileselect", "<input type=\"file\" name=\"uploadfile\">");
        }
        velocityContext.put("workbook", ws);

        velocityContext.put("charts", azquoBook.drawCharts(loggedInConnection, path).toString());
        return convertToVelocity(velocityContext, null, null, "onlineReport.vm");
    }
//executing parked while saving is
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

    public void setUserChoice(int userId, int reportId, String choiceName, String choiceValue) {
        if (choiceName.equalsIgnoreCase(AzquoBook.OPTIONPREFIX + "clear overrides")) {
            userChoiceDAO.deleteOverridesForUserAndReportId(userId, reportId);
            return;
        }
        UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(userId, reportId, choiceName);
        if (choiceValue != null && choiceValue.length() > 0) {
            if (userChoice == null) {
                userChoice = new UserChoice(0, userId, reportId, choiceName, choiceValue, new Date());
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
                userChoiceDAO.deleteForReportId(userChoice.getId());
            }
        }
    }

    public void saveBook(HttpServletResponse response, LoggedInConnection loggedInConnection, String fileName) throws Exception {
        loggedInConnection.getAzquoBook().saveBook(response, fileName);
    }

    public void saveBookasPDF(HttpServletResponse response, LoggedInConnection loggedInConnection, String fileName) throws Exception {
        loggedInConnection.getAzquoBook().saveBookAsPDF(response, fileName);
    }

    public void saveBookActive(HttpServletResponse response, LoggedInConnection loggedInConnection, String fileName) throws Exception {
        loggedInConnection.getAzquoBook().saveBookActive(response, fileName, env.getProperty("azquo.home") + "/onlinereports/Admin/Azquoblank.xls");
    }

    public String changeValue(LoggedInConnection loggedInConnection, int row, int col, String value) {
        return loggedInConnection.getAzquoBook().changeValue(row, col, value, loggedInConnection);
    }

    public String getProvenance(LoggedInConnection loggedInConnection, int row, int col, String jsonFunction) {
        return loggedInConnection.getAzquoBook().getProvenance(loggedInConnection, row, col, jsonFunction);
    }

    // one can administrate by editing data pretty much as it appears in MySQL tables, hence the headings (column names) need to be escaped unless someone plays silly buggers
    // of course if they have admin rights (where this is used) they could probably do plenty of damage to the DB anyway

    private String convertNameToSQL(String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char ch = fieldName.charAt(i);
            if (ch >= 'a') {
                sb.append(ch);
            } else {
                sb.append(("_" + ch).toLowerCase());
            }
        }
        return sb.toString();
    }

    public String saveAdminData(LoggedInConnection loggedInConnection) throws Exception {
        String result = "";
        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        String tableName = azquoBook.getAdminTableName();
        StringBuilder data = azquoBook.getAdminData();
        if (data == null) {
            result = "error: no data to save";
        } else {
            StringTokenizer st = new StringTokenizer(data.toString(), "\n");
            String headingsList = st.nextToken();
            String[] headings = headingsList.split("\t");
            while (st.hasMoreTokens()) {
                String dataLine = st.nextToken();
                final Map<String, Object> parameters = new HashMap<String, Object>();
                StringTokenizer st2 = new StringTokenizer(dataLine, "\t");
                String idVal = st2.nextToken();
                int id = 0;
                if (idVal.length() > 0) {
                    try {
                        id = Integer.parseInt(idVal);
                    } catch (Exception ignored) {
                    }
                }
                for (int i = 1; i < headings.length; i++) {
                    String value = "";
                    if (st2.hasMoreTokens()) {
                        value = st2.nextToken();
                    }
                    String heading = convertNameToSQL(headings[i]);
                    if (heading.contains("date")) {
                        parameters.put(heading, interpretDate(value));
                    } else {
                        parameters.put(convertNameToSQL(headings[i]), value);
                    }

                }
                if (tableName.equalsIgnoreCase("online_report")) {
                    onlineReportDAO.update(id, parameters);
                } else if (tableName.equals("permission")) {
                    String dbName = (String) parameters.get("database");
                    if (dbName != null && dbName.length() > 0) {
                        Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), dbName);
                        if (db != null) {
                            parameters.put("database_id", db.getId());
                            String email = (String) parameters.get("email");
                            if (email != null && email.length() > 0) {
                                User user = userDAO.findByEmail(email);
                                if (user != null) {
                                    parameters.put("user_id", user.getId());
                                    parameters.remove("email");
                                    parameters.remove("database");
                                    permissionDAO.update(id, parameters);

                                }
                            }
                        }
                    }
                } else if (tableName.equals("user")) {
                    userDAO.update(id, loggedInConnection.getBusinessId(), parameters);
                }
            }
        }
        return result;
    }

    // parking normal saving for the moment
    public void saveData(LoggedInConnection loggedInConnection) throws Exception {
        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        if (azquoBook.dataRegionPrefix.equals(AzquoBook.azInput)) {
            saveAdminData(loggedInConnection);
        } else {
            azquoBook.saveData(loggedInConnection);
        }
    }

    private StringBuilder createDatabaseSelect(LoggedInConnection loggedInConnection) {
        StringBuilder sb = new StringBuilder();
        String chosen = "";
        Map<String, Database> foundDatabases = loginService.foundDatabases(loggedInConnection.getUser());
        if (foundDatabases.size() > 1) {
            if (loggedInConnection.getAzquoMemoryDB() != null) chosen = loggedInConnection.getLocalCurrentDBName();
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

    public void switchDatabase(LoggedInConnection loggedInConnection, String newDBName) throws Exception {
        if (newDBName.length() == 0) {
            loginService.switchDatabase(loggedInConnection, null);
            return;
        }
        Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), newDBName);
        if (db == null) {
            throw new Exception(newDBName + " - no such database");
        }
        loginService.switchDatabase(loggedInConnection, db);

    }

    // basic db wide functions, create delete db etc

    public void followInstructionsAt(LoggedInConnection loggedInConnection, int rowNo, int colNo, String database, MultipartFile file) throws Exception {
        //this routine is called when a button on the maintenance spreadsheet is pressed
        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        String result = azquoBook.getCellContent(rowNo, colNo);
        String op = "";
        String newdatabase = "";
        String nameList = "";
        if (result.startsWith("$button;name=") && result.indexOf("op=") > 0) {
            String link = result.substring(result.indexOf("op=") + 3);
            String paramName = "op";
            while (paramName.length() > 0) {
                String paramValue = link.substring(0, (link + "&").indexOf("&"));
                if (paramValue.length() < link.length()) {
                    link = link.substring(paramValue.length() + 1);
                } else {
                    link = "";
                }
                if (paramName.equals("op")) {
                    op = paramValue;
                } else if (paramName.equals("database")) {
                    database = paramValue;
                } else if (paramName.equals("newdatabase")) {
                    newdatabase = paramValue;
                } else if (paramName.equals("namelist")) {
                    nameList = paramValue;
                }
                paramName = "";
                if (link.indexOf("=") > 0) {
                    paramName = link.substring(0, link.indexOf("="));
                    link = link.substring(paramName.length() + 1);
                }
            }
        }
        switchDatabase(loggedInConnection, database);
        if (op.equalsIgnoreCase("newdatabase")) {
            if (newdatabase.length() > 0) {
                adminService.createDatabase(newdatabase, loggedInConnection);
            }
        }
        if (op.equalsIgnoreCase("copydatabase")) {
            adminService.copyDatabase(loggedInConnection, database, nameList);
        }
        if (op.equals("delete")) {
            loginService.switchDatabase(loggedInConnection, null);
            Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), database);
            if (db != null) {
                List<OnlineReport> onlineReports = onlineReportDAO.findForDatabaseId(db.getId());
                for (OnlineReport onlineReport : onlineReports) {
                    userChoiceDAO.deleteForReportId(onlineReport.getId());
                }
                loginRecordDAO.removeForDatabaseId(db.getId());
                onlineReportDAO.removeForDatabaseId(db.getId());
                openDatabaseDAO.removeForDatabaseId(db.getId());
                permissionDAO.removeForDatabaseId(db.getId());
                uploadRecordDAO.removeForDatabaseId(db.getId());
                String mySQLName = db.getMySQLName();
                databaseDAO.removeById(db);
                adminService.dropDatabase(mySQLName);
                // we were returning some of the results from this function, it seems not any more?
            }
        }
        if (op.equalsIgnoreCase("upload")) {
            InputStream uploadFile = file.getInputStream();
            String fileName = file.getOriginalFilename();
            importService.importTheFile(loggedInConnection, fileName, uploadFile, "", true, loggedInConnection.getLanguages());
        }
    }

    public static Date interpretDate(String dateString) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("dd/MM/yy");
        SimpleDateFormat dateFormat3 = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat dateFormat4 = new SimpleDateFormat("dd-MM-yyyy");
        Date dateFound = null;
        if (dateString.length() > 5) {
            if (dateString.substring(2, 3).equals("/")) {
                if (dateString.length() > 8) {
                    try {
                        dateFound = dateFormat3.parse(dateString);
                    } catch (Exception ignored) {
                    }

                } else {
                    try {
                        dateFound = dateFormat2.parse(dateString);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                if (dateString.substring(2, 3).equals("-")) {
                    try {
                        dateFound = dateFormat4.parse(dateString);
                    } catch (Exception ignored) {
                    }
                } else {
                    try {
                        dateFound = simpleDateFormat.parse(dateString);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return dateFound;
    }


    public String showUploadFile(LoggedInConnection loggedInConnection) {
        VelocityContext context = new VelocityContext();
        context.put("azquodatabaselist", createDatabaseSelect(loggedInConnection));
        return convertToVelocity(context, "upload", null, "upload.vm");
    }


    // on logging into Magento reports for example

    public String showUserMenu(LoggedInConnection loggedInConnection) {
        List<OnlineReport> onlineReports = onlineReportDAO.findForBusinessIdAndUserStatus(loggedInConnection.getBusinessId(), loggedInConnection.getUser().getStatus());
        VelocityContext context = new VelocityContext();
        context.put("welcome", "Welcome to Azquo!");
        if (loggedInConnection.getCurrentDatabase() != null) {
            context.put("database", loggedInConnection.getAzquoMemoryDB().getDatabase().getName());
        }
        Set<Map<String, String>> reports = new HashSet<Map<String, String>>();
        for (OnlineReport onlineReport : onlineReports) {
            Map<String, String> vReport = new HashMap<String, String>();
            vReport.put("name", onlineReport.getReportName());
            vReport.put("explanation", onlineReport.getExplanation());
            vReport.put("link", "/api/Online/?opcode=loadsheet&reportid=" + onlineReport.getId());
            reports.add(vReport);
        }
        return convertToVelocity(context, "reports", reports, "azquoReports.vm");
    }

    public String showNameDetails(LoggedInConnection loggedInConnection, String database, int nameId, String parents, String searchNames) throws Exception {
        if (database != null && database.length() > 0) {
            Database newDB = databaseDAO.findForName(loggedInConnection.getBusinessId(), database);
            if (newDB == null) {
                return "no database chosen";
            }
            loginService.switchDatabase(loggedInConnection, newDB);
        }
        if (searchNames == null) searchNames = "";
        VelocityContext context = new VelocityContext();
        context.put("parents", parents);
        context.put("rootid", nameId + "");
        context.put("searchnames", searchNames);
        return convertToVelocity(context, null, null, "jstree.vm");
    }

    // do we need to be creating a new velocity engine every time? Not sure if necessary.
    // The amount of string pushing around here bugs me slightly, azquo book probably makes a big old chunk of HTML
    // todo, maybe use velocity in the spring way? http://wiki.apache.org/velocity/VelocityAndSpringStepByStep

    private String convertToVelocity(VelocityContext context, String itemName, Set<Map<String, String>> items, String velocityTemplate) {
        VelocityEngine ve = new VelocityEngine();
        Properties properties = new Properties();
        Template t;
        if (velocityTemplate == null) {
            velocityTemplate = "email.vm";
        }
        if ((velocityTemplate.startsWith("http://") || velocityTemplate.startsWith("https://")) && velocityTemplate.indexOf("/", 8) != -1) {
            properties.put("resource.loader", "url");
            properties.put("url.resource.loader.class", "org.apache.velocity.runtime.resource.loader.URLResourceLoader");
            properties.put("url.resource.loader.root", velocityTemplate.substring(0, velocityTemplate.lastIndexOf("/") + 1));
            ve.init(properties);
            t = ve.getTemplate(velocityTemplate.substring(velocityTemplate.lastIndexOf("/") + 1));
        } else {
            properties.setProperty("resource.loader", "webapp");
            properties.setProperty("webapp.resource.loader.class", "org.apache.velocity.tools.view.WebappResourceLoader");
            properties.setProperty("webapp.resource.loader.path", "/WEB-INF/velocity/");
            ve.setApplicationAttribute("javax.servlet.ServletContext", servletContext);
            ve.init(properties);
            t = ve.getTemplate(velocityTemplate);
        }
        /*  create a context and add data */
        context.put(itemName, items);
         /* now render the template into a StringWriter */
        StringWriter writer = new StringWriter();
        t.merge(context, writer);
        /* show the World */
        return writer.toString();
    }

    /*

    Ok, select a region of names in excel and paste and this function will build a multidimentional array of heading objects from that paste

    more specifically : the outermost list is of rows, the second list is each cell in that row and the final list is a list not a heading
    because there could be multiple names/attributes in a cell if the cell has something like container;children. Interpretnames is what does this

hence

seaports;children   container;children

 for example returns a list of 1 as there's only 1 row passed, with a list of 2 as there's two cells in that row with lists of something like 90 and 3 names in the two cell list items

 we're not permuting, just saying "here is what that 2d excel region looks like in names"

     */


    public List<List<List<DataRegionHeading>>> createNameListsFromExcelRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String excelRegionPasted, List<String> attributeNames) throws Exception {
        List<List<List<DataRegionHeading>>> nameLists = new ArrayList<List<List<DataRegionHeading>>>();
        //logger.info("excel region pasted : " + excelRegionPasted);
        int maxColCount = 1;
        CsvReader pastedDataReader = new CsvReader(new StringReader(excelRegionPasted), '\t');
        while (pastedDataReader.readRecord()) {
            if (pastedDataReader.getColumnCount() > maxColCount) {
                maxColCount = pastedDataReader.getColumnCount();
            }
        }
        pastedDataReader = new CsvReader(new StringReader(excelRegionPasted), '\t'); // reset the CSV reader
        pastedDataReader.setUseTextQualifier(false);
        while (pastedDataReader.readRecord()) { // we're stepping through the cells that describe headings

            // ok here's the thing, before it was just names here, now it could be other things, attribute names formulae etc.
            List<List<DataRegionHeading>> row = new ArrayList<List<DataRegionHeading>>();
            for (int column = 0; column < pastedDataReader.getColumnCount(); column++) {
                String cellString = pastedDataReader.get(column);
                if (cellString.length() == 0) {
                    row.add(null);
                } else {
                    // was just a name expression, now we allow an attribute also. May be more in future.
                    if (cellString.startsWith(".")) {
                        // currently only one attribute per cell, I suppose it could be many in future (available attributes for a name, a list maybe?)
                        row.add(Arrays.asList(new DataRegionHeading(cellString, true))); // we say that an attribuite heading defaults to writeable, it will defer to the name
                    } else {
                        row.add(dataRegionHeadingsFromNames(nameService.parseQuery(azquoMemoryDBConnection, cellString, attributeNames), azquoMemoryDBConnection));
                    }
                }
            }
            while (row.size() < maxColCount) row.add(null);
            nameLists.add(row);
        }
        return nameLists;
    }

    /* ok we're passed a list of lists
    what is returned is a 2d array (also a list of lists) featuring every possible variation in order
    so if the initial lists passed were
    A,B
    1,2,3,4
    One, Two, Three

    The returned list will be the size of each passed list multiplied together (on that case 2*4*3 so 24)
    and each entry on that list will be the size of the number of passed lists, in this case 3
    so

    A, 1, ONE
    A, 1, TWO
    A, 1, THREE
    A, 2, ONE
    A, 2, TWO
    A, 2, THREE
    A, 3, ONE
    A, 3, TWO
    A, 3, THREE
    A, 4, ONE
    A, 4, TWO
    A, 4, THREE
    B, 1, ONE
    B, 1, TWO
    B, 1, THREE
    B, 2, ONE
    B, 2, TWO

    etc.

    Row/column reference below is based off the above example - in toReturn the index of the outside list is y and the the inside list x

    */

    public <T> List<List<T>> get2DPermutationOfLists(final List<List<T>> listsToPermute) {

        List<List<T>> toReturn = null;

        for (List<T> permutationDimension : listsToPermute) {
            if (permutationDimension == null) {
                permutationDimension = new ArrayList<T>();
                permutationDimension.add(null);

            }
            if (toReturn == null) { // first one, just assign the single column
                toReturn = new ArrayList<List<T>>();
                for (T item : permutationDimension) {
                    List<T> createdRow = new ArrayList<T>();
                    createdRow.add(item);
                    toReturn.add(createdRow);
                }
            } else {
                // this is better as a different function as internally it created a new 2d array which we can then assign back to this one
                toReturn = get2DArrayWithAddedPermutation(toReturn, permutationDimension);
            }
        }
        return toReturn;
    }

    /* so say we already have
    a,1
    a,2
    a,3
    a,4
    b,1
    b,2
    b,3
    b,4

    for example

    and want to add the permutation ONE, TWO, THREE onto it.

    The returned list of lists will be the size of the list of lists passed * the size of teh passed new dimension
    and the nested lists in teh returned list will be one bigger, featuring the new dimension

    if we looked at the above as a reference it would be 3 times as high and 1 column wider
     */


    public <T> List<List<T>> get2DArrayWithAddedPermutation(final List<List<T>> existing2DArray, List<T> permutationWeWantToAdd) {
        List<List<T>> toReturn = new ArrayList<List<T>>();
        for (List<T> existingRow : existing2DArray) {
            for (T elementWeWantToAdd : permutationWeWantToAdd) { // for each new element
                List<T> newRow = new ArrayList<T>(existingRow); // copy the existing row
                newRow.add(elementWeWantToAdd);// add the extra element
                toReturn.add(newRow);
            }
        }
        return toReturn;
    }

    /*

    This is called after the names are loaded by createNameListsFromExcelRegion. in the case of columns it is transposed first

    The dynamic namecalls e.g. Seaports; children; have their lists populated but they have not been expanded out into the 2d set itself

    So in the case of row headings for the export example it's passed a list or 1 (the outermost list of row headings defined)

    and this 1 has a list of 2, the two cells defined in row headings as seaports; children     container;children

      We want to return a list<list> being row, cells in that row, the expansion of each of the rows. The source in my example being ony one row with 2 cells in it
      those two cells holding lists of 96 and 4 hence we get 384 back, each with 2 cells.

    RIGHT!

    I know what the function that used to be called blank col is for. Heading definitions are a region
    if this region is more than on column wide then rows which have only one cell populated and that cell is the right most cell
    then that cell is added to the cell above it, it becomes part of that permutation.


     */


    public List<List<DataRegionHeading>> expandHeadings(final List<List<List<DataRegionHeading>>> headingLists) {

        List<List<DataRegionHeading>> output = new ArrayList<List<DataRegionHeading>>();
        final int noOfHeadingDefinitionRows = headingLists.size();
        if (noOfHeadingDefinitionRows == 0) {
            return output;
        }
        final int lastHeadingDefinitionCellIndex = headingLists.get(0).size() - 1; // the headingLists will be square, that is to say all row lists the same length as prepared by createNameListsFromExcelRegion

        // ok here's the logic, what's passed is a 2d array of lists, as created from createNameListsFromExcelRegion
        // we would just run through the rows running a 2d permutation on each row BUT there's a rule that if there's
        // a row below blank except the right most one then add that right most one to the one above
        for (int headingDefinitionRowIndex = 0; headingDefinitionRowIndex < noOfHeadingDefinitionRows; headingDefinitionRowIndex++) {
            List<List<DataRegionHeading>> headingDefinitionRow = headingLists.get(headingDefinitionRowIndex);
            // ok we have one of the heading definition rows
            while (headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 // we're not on the last row
                    && headingLists.get(headingDefinitionRowIndex + 1).size() > 1 // the next row is not a single cell (will all rows be the same length?)
                    && headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex + 1))) { // and the last cell is the only not null one
                headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                headingDefinitionRowIndex++;
            }
            List<List<DataRegionHeading>> permuted = get2DPermutationOfLists(headingDefinitionRow);
            output.addAll(permuted);
        }
        return output;

    }

    // what we're saying is it's only got one cell in the heading definition filled and it's the last one.

    private boolean headingDefinitionRowHasOnlyTheRightCellPopulated(List<List<DataRegionHeading>> headingLists) {
        int numberOfCellsInThisHeadingDefinition = headingLists.size();
        for (int cellIndex = 0; cellIndex < numberOfCellsInThisHeadingDefinition; cellIndex++) {
            if (headingLists.get(cellIndex) != null) {
                return cellIndex == numberOfCellsInThisHeadingDefinition - 1; // if the first to trip the not null is the last in the row then true!
            }

        }
        return false; // it was ALL null, error time?
    }

    private void nameAndParent(UniqueName uName){
        Name name = uName.name;
        if (name.getParents()!=null) {
            Iterator it = name.getParents().iterator();
            while (it.hasNext()) {
                Name parent = (Name) it.next();
                //check that there are no duplicates with the same immediate parent
                boolean duplicate = false;
                for (Name sibling:parent.getChildren()) {
                    if (sibling!=name && sibling.getDefaultDisplayName().equals(name.getDefaultDisplayName())){
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate){
                    uName.string = uName.string + "," + parent.getDefaultDisplayName();
                    uName.name = parent;//we may need this to go up the chain if the job is not yet done
                }
            }
        }
     }


    private List<String> findUniqueNames(List<UniqueName> pending){
        List<String> output = new ArrayList<String>();
        if (pending.size()==1){
              output.add(pending.get(0).string);
            return output;
        }
        //first add a unique parent onto each name
        for (UniqueName uName:pending){
            nameAndParent(uName);
        }
        //but maybe the parents are not unique names!
        boolean unique = false;
        int ucount = 10; //to prevent loops
        while (!unique && ucount-- > 0) {
            unique = true;
            for (UniqueName uName : pending) {
                String testName = uName.string;
                int ncount = 0;
                for (UniqueName uName2 :pending){
                    if (uName2.string.equals(uName.string)) ncount++;
                    if (ncount > 1){
                        nameAndParent(uName2);
                    }
                }
                if (ncount > 1){
                    nameAndParent(uName);
                    unique = false;
                    break;
                }
            }
        }
        for (UniqueName uName:pending){
            output.add(uName.string);
        }
        return output;



}

    public List<String> getIndividualNames(List<Name> sortedNames){
        //this routine to output a list of names without duplicates by including parent names on duplicates
        List<String> output = new ArrayList<String>();
        String lastName = null;
        List<UniqueName> pending = new ArrayList<UniqueName>();
        for (Name name:sortedNames) {
            if (lastName != null) {
                if (!name.getDefaultDisplayName().equals(lastName)) {
                    output.addAll(findUniqueNames(pending));
                    pending = new ArrayList<UniqueName>();
                }
            }
            lastName = name.getDefaultDisplayName();
            UniqueName uName = new UniqueName();
            uName.name = name;
            uName.string = lastName;
            pending.add(uName);

            }
            output.addAll(findUniqueNames(pending));
            return output;
        }


    // todo make sense of the bloody restrictcount parameter

    public List<Integer> sortDoubleValues(int restrictCount, Map<Integer, Double> sortTotals, final boolean sortRowsUp) {

        final List<Integer> sortedValues = new ArrayList<Integer>();
        if (restrictCount != 0) {
            List<Map.Entry<Integer, Double>> list = new ArrayList<Map.Entry<Integer, Double>>(sortTotals.entrySet());
            // sort list based on
            Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
                public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                    return sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue());
                }
            });
            for (Map.Entry<Integer, Double> aList : list) {
                sortedValues.add(aList.getKey());
            }
        } else {
            for (int i = 0; i < sortTotals.size(); i++) {
                sortedValues.add(i);
            }
        }
        return sortedValues;
    }

    // same thing for strings, I prefer stronger typing

    public List<Integer> sortStringValues(int restrictCount, Map<Integer, String> sortTotals, final boolean sortRowsUp) {
        final List<Integer> sortedValues = new ArrayList<Integer>();
        if (restrictCount != 0) {
            List<Map.Entry<Integer, String>> list = new ArrayList<Map.Entry<Integer, String>>(sortTotals.entrySet());
            // sort list based on string now
            Collections.sort(list, new Comparator<Map.Entry<Integer, String>>() {
                public int compare(Map.Entry<Integer, String> o1, Map.Entry<Integer, String> o2) {
                    return sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue());
                }
            });

            for (Map.Entry<Integer, String> aList : list) {
                sortedValues.add(aList.getKey());
            }
        } else {
            for (int i = 0; i < sortTotals.size(); i++) {
                sortedValues.add(i);
            }
        }
        return sortedValues;
    }

    /* ok so transposing happens here
    this is because the expand headings function is orientated for for headings and the column heading definitions are unsurprisingly set up for columns
     what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

     NOTE : this means the column heading are not stored according to the orientation used in the above function

      hence, to output them we have to transpose them again!

    OK, having generified the function we should only need one function. The list could be anything, names, list of names, hashmaps whatever
    generics ensure that the return type will match the sent type
    now rather similar to the stacktrace example :)

    Variable names assume first list is of rows and the second is each row. down then across.
    So the size of the first list is the ysize (number of rows) and the size of the nested list the xsize (number of columns)
    I'm going to model it that way round as when reading data from excel that's the default (we go line by line through each row, that's how the data is delivered), the rows is the outside list
    of course could reverse all descriptions and teh function could still work

    */

    public <T> List<List<T>> transpose2DList(final List<List<T>> source2Dlist) {
        final List<List<T>> flipped = new ArrayList<List<T>>();
        if (source2Dlist.size() == 0) {
            return flipped;
        }
        final int oldXMax = source2Dlist.get(0).size(); // size of nested list, as described above (that is to say get the length of one row)
        for (int newY = 0; newY < oldXMax; newY++) {
            List<T> newRow = new ArrayList<T>(); // make a new row
            for (List<T> oldRow : source2Dlist) { // and step down each of the old rows
                newRow.add(oldRow.get(newY));//so as we're moving across the new row we're moving down the old rows on a fixed column
                // the transposing is happening as a list which represents a row would typically be accessed by an x value but instead it's being accessed by an y value
                // in this loop the row being read from changes but the cell in that row does not
            }
            flipped.add(newRow);
        }
        return flipped;
    }

    // I guess headings when showing a jumble of values?

    public LinkedHashSet<Name> getHeadings(Map<Set<Name>, Set<Value>> showValues) {
        LinkedHashSet<Name> headings = new LinkedHashSet<Name>();
        // this may not be optimal, can sort later . . .
        int count = 0;
        for (Set<Name> valNames : showValues.keySet()) {
            if (count++ == 2000) {
                break;
            }
            for (Name name : valNames) {
                if (!headings.contains(name.findATopParent())) {
                    headings.add(name.findATopParent());
                }
            }
        }
        return headings;

    }

    // vanilla jackson might not be good enough but this is too much manual json writing I think

    public String getJsonDataforOneName(LoggedInConnection loggedInConnection, final Name name, Map<String, LoggedInConnection.JsTreeNode> lookup) throws Exception {
        final StringBuilder sb = new StringBuilder();
        Set<Name> names = new HashSet<Name>();
        names.add(name);
        List<Set<Name>> searchNames = new ArrayList<Set<Name>>();
        searchNames.add(names);
        Map<Set<Name>, Set<Value>> showValues = valueService.getSearchValues(searchNames);
        if (showValues == null) {
            return "";
        }
        sb.append(", \"children\":[");
        int lastId = loggedInConnection.getLastJstreeId();
        int count = 0;
        for (Set<Name> valNames : showValues.keySet()) {
            Set<Value> values = showValues.get(valNames);
            if (count++ > 0) {
                sb.append(",");
            }

            loggedInConnection.setLastJstreeId(++lastId);
            LoggedInConnection.NameOrValue nameOrValue = new LoggedInConnection.NameOrValue();
            nameOrValue.values = values;
            nameOrValue.name = null;
            LoggedInConnection.JsTreeNode newNode = new LoggedInConnection.JsTreeNode(nameOrValue, name);
            lookup.put(lastId + "", newNode);
            if (count > 100) {
                sb.append("{\"id\":" + lastId + ",\"text\":\"" + (showValues.size() - 100) + " more....\"}");
                break;
            }
            sb.append("{\"id\":" + lastId + ",\"text\":\"" + valueService.addValues(values) + " ");
            for (Name valName : valNames) {
                if (valName.getId() != name.getId()) {
                    sb.append(valName.getDefaultDisplayName().replace("\"", "\\\"") + " ");
                }
            }
            sb.append("\"");
            sb.append("}");

        }
        sb.append("]");
        return sb.toString();
    }

    // still a little funny about whether a logged in conneciton should be passed

    public List<List<AzquoCell>> getDataRegion(LoggedInConnection loggedInConnection, List<List<DataRegionHeading>> rowHeadings
            , List<List<DataRegionHeading>> columnHeadings, String context
            , int filterCount, int maxRows, int maxCols, String sortRow, String sortCol) throws Exception {
        if (columnHeadings == null || columnHeadings.size() == 0 || rowHeadings == null || rowHeadings.size() == 0) {
            throw new Exception("no headings passed");
        }
        final StringTokenizer st = new StringTokenizer(context, "\n");
        final List<Name> contextNames = new ArrayList<Name>();
        while (st.hasMoreTokens()) {
            final List<Name> thisContextNames = nameService.parseQuery(loggedInConnection, st.nextToken().trim());
            if (thisContextNames.size() > 1) {
                throw new Exception("context names must be individual - use 'as' to put sets in context");
            }
            if (thisContextNames.size() > 0) {
                //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                contextNames.add(thisContextNames.get(0));
            }
        }
        // note, didn't se the context against the logged in connection, should I?
        // ok going to try to use the new function
        List<List<AzquoCell>> dataToShow = getAzquoCellsForRowsColumnsAndContext(loggedInConnection, rowHeadings
                , columnHeadings, contextNames, loggedInConnection.getLanguages());
        dataToShow = sortAndFilterCells(dataToShow, rowHeadings, columnHeadings
                , filterCount, maxRows, maxCols, sortRow, sortCol);
        return dataToShow;
        //return getExcelDataForColumnsRowsAndContext(loggedInConnection, contextNames, region, filterCount, maxRows, maxCols);
    }

    // for looking up a heading given a string. Seems used for looking up teh right col or row to sort on

    private int findPosition(List<List<DataRegionHeading>> headings, String toFind) {
        boolean desc = false;
        if (toFind == null || toFind.length() == 0) {
            return 0;
        }
        if (toFind.endsWith("-desc")) {
            toFind = toFind.replace("-desc", "");
            desc = true;
        }
        int count = 1;
        for (List<DataRegionHeading> heading : headings) {
            DataRegionHeading dataRegionHeading = heading.get(heading.size() - 1);
            if (dataRegionHeading != null) {
                String toCompare;
                if (dataRegionHeading.getName() != null) {
                    toCompare = dataRegionHeading.getName().getDefaultDisplayName().replace(" ", "");
                } else {
                    toCompare = dataRegionHeading.getAttribute();
                }
                if (toCompare.equals(toFind)) {
                    if (desc) {
                        return -count;
                    }
                    return count;
                }
            }
            count++;
        }
        return 0;
    }

    // plan with refactoring is for this to do some of what was in the old get excel data function. Taking the full region and imposing useful user limits
    // note, one could derive column and row headings from the source data's headings but passing them is easier if they are to hand which the should be

    public List<List<AzquoCell>> sortAndFilterCells(List<List<AzquoCell>> sourceData, List<List<DataRegionHeading>> rowHeadings, List<List<DataRegionHeading>> columnHeadings
            , int filterCount, int restrictRowCount, int restrictColCount, String sortRowString, String sortColString) throws Exception {
        long track = System.currentTimeMillis();
        if (sourceData == null || sourceData.isEmpty()) {
            return sourceData;
        }
        int totalRows = sourceData.size();
        int totalCols = sourceData.get(0).size();
        if (restrictRowCount < 0) {
            //decide whether to sort the rows
            if (totalRows + restrictRowCount < 0) {
                restrictRowCount = 0;
            } else {
                restrictRowCount = -restrictRowCount;
            }
        }
        if (restrictColCount < 0) {
            //decide whether to sort the Cols
            if (totalCols + restrictColCount < 0) {
                restrictColCount = 0;
            } else {
                restrictColCount = -restrictColCount;
            }
        }
        if (restrictRowCount > totalRows) restrictRowCount = totalRows;
        if (restrictColCount > totalCols) restrictColCount = totalCols;
        Integer sortCol = findPosition(columnHeadings, sortColString);
        Integer sortRow = findPosition(rowHeadings, sortRowString);

        boolean sortRowsUp = false;
        boolean sortColsRight = false;
        if (sortCol == null) {
            sortCol = 0;
        } else {
            if (sortCol > 0) {
                sortRowsUp = true;
            } else {
                sortCol = -sortCol;
            }
        }
        if (sortRow == null) {
            sortRow = 0;
        } else {
            if (sortRow > 0) {
                sortColsRight = true;
            } else {
                sortRow = -sortRow;
            }
        }
        if (sortCol > 0 && restrictRowCount == 0) {
            restrictRowCount = totalRows;//this is a signal to sort the rows
        }
        if (sortRow > 0 && restrictColCount == 0) {
            restrictColCount = totalCols;//this is a signal to sort the cols
        }
        final Map<Integer, Double> sortRowTotals = new HashMap<Integer, Double>();
        final Map<Integer, String> sortRowStrings = new HashMap<Integer, String>();
        final Map<Integer, Double> sortColumnTotals = new HashMap<Integer, Double>();
        int rowNo = 0;
        for (int colNo = 0; colNo < totalCols; colNo++) {
            sortColumnTotals.put(colNo, 0.00);
//            sortColumnStrings.put(colNo,"");
        }
        boolean rowNumbers = true;
        for (List<AzquoCell> rowCells : sourceData) {
            double sortRowTotal = 0.0;//note that, if there is a 'sortCol' then only that column is added to the total.
            int colNo = 0;
            sortRowStrings.put(rowNo, "");
            for (AzquoCell cell : rowCells) {
                // ok these bits are for sorting. Could put a check on whether a number was actually the result but not so bothered
                if (sortCol == colNo + 1) {
                    sortRowStrings.put(rowNo, cell.getStringValue());
                    if (cell.getStringValue().length() > 0 && !NumberUtils.isNumber(cell.getStringValue())) {
                        rowNumbers = false;
                    }
                }
                if (restrictRowCount > 0 && (sortCol == 0 || sortCol == colNo + 1)) {
                    sortRowTotal += cell.getDoubleValue();
                }
                if (restrictColCount > 0 && (sortRow == 0 || sortRow == rowNo + 1)) {
                    sortColumnTotals.put(colNo, sortColumnTotals.get(colNo) + cell.getDoubleValue());
                }
                colNo++;
            }
            sortRowTotals.put(rowNo++, sortRowTotal);

        }

        //sort and trim rows and cols
        List<Integer> sortedRows;
        if (rowNumbers) {
            sortedRows = sortDoubleValues(restrictRowCount, sortRowTotals, sortRowsUp);
        } else {
            sortedRows = sortStringValues(restrictRowCount, sortRowStrings, sortRowsUp);
        }

        // ok we've got the sort info, here the old code set a fair amount of stuff in logged in connection, I need to work out where it's used.
        // the restrict row count was set, it's used in getrowheadingsasarray
        //loggedInConnection.setRestrictRowCount(region, restrictRowCount);
        // used in ormatdataregion, formatdataregionprovenanceforoutput, getage(??), getrowheadingsasarray, savedata
        //loggedInConnection.setRowOrder(region, sortedRows);
        List<Integer> sortedCols = sortDoubleValues(restrictColCount, sortColumnTotals, sortColsRight);
        // I assume these two are the same as above
        //loggedInConnection.setColOrder(region, sortedCols);
        //loggedInConnection.setRestrictColCount(region, restrictColCount);

        // OK pasting and changing what was in format data region, it's only called by this

        int rowInt = 0;
        int blockRowCount = 0;
        if (restrictRowCount == 0 || restrictRowCount > sortedRows.size()) {
            restrictRowCount = sortedRows.size();
        }
        if (restrictColCount == 0 || restrictColCount > sortedCols.size()) {
            restrictColCount = sortedCols.size();
        }
        List<List<AzquoCell>> sortedCells = new ArrayList<List<AzquoCell>>();
        for (rowNo = 0; rowNo < restrictRowCount; rowNo++) {
            List<AzquoCell> rowCells = sourceData.get(sortedRows.get(rowNo));

            List<AzquoCell> newRow = new ArrayList<AzquoCell>();
            sortedCells.add(newRow);
            // this may often be a straight copy (if no col sorting) but not too bothered about the possible speed increase
            for (int colNo = 0; colNo < restrictColCount; colNo++) {
                newRow.add(rowCells.get(sortedCols.get(colNo)));
            }
            rowInt++;

            // ok here's a thing . . . I think this code that used to cchop didn't take into account row sorting. Should be pretty easy to just do here I think
            if (++blockRowCount == filterCount) {
                // we need the equivalent check of blank rows, checking the cell's list of names or values should do this
                // go back from the beginning
                boolean rowsBlank = true;
                for (int j = 0; j < filterCount; j++) {
                    List<AzquoCell> rowToCheck = sortedCells.get((sortedRows.size() - 1) - j); // size - 1 for the last index
                    for (AzquoCell cellToCheck : rowToCheck) {
                        if ((cellToCheck.getListOfValuesOrNamesAndAttributeName().getNames() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getNames().isEmpty())
                                || (cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty())) {// there were values or names for the call
                            rowsBlank = false;
                            break;
                        }
                    }
                    if (!rowsBlank) {
                        break;
                    }
                }
                if (rowsBlank) {
                    for (int i = 0; i < filterCount; i++) {
                        sortedCells.remove(sortedCells.size() - 1);
                    }
                }
                blockRowCount = 0;
            }
        }

        //formatLockMap(loggedInConnection, region, lockArray);
        valueService.printSumStats();
        valueService.printFindForNamesIncludeChildrenStats();
        //loggedInConnection.setDataHeadingsMap(region, dataHeadingsMap);
        logger.info("time to execute : " + (System.currentTimeMillis() - track));
        return sortedCells;
    }


    /* I need to make more sense of getExcelDataForColumnsRowsAndContext, going to start here
     this just gets the data, no sorting or anything like that
     and nothing stateful, no interacting with logged connections or anything like that though it needs a connection

     Given the flexibility on headings being names or attributes and the result of a cell being a number from a value or sum of values or formula or attributes I need to think about data in and out

     Parameters

    The connection to the relevant DB
    Row and column headings (very possibly more than one heading for a given row or column if permutation is involved) and context names list
    Languages is attribute names but I think I'll call it languages as that's what it would practically be - used when looking up names for formulae

    So,it's going to return relevant data to the region. The values actually shown, (typed?) objects for ZKspreadsheet, locked or not, the headings are useful (though one could perhaps derive them)
it seems that there should be a cell map or object and that's what this should return rather than having a bunch of multidimensional arrays

ok I'm going for that object type (AzquoCell), outer list rows inner items on those rows, hope that's standard. Outside this function the sorting etc will happen.

I think that this is an ideal candidate for multithreading to speed things up


    */

    boolean tryMultiThreaded = true;

    public class RowFiller implements Runnable {

        private final int startRow;
        private final int endRow;
        private final List<List<AzquoCell>> targetArray;
        private final List<List<DataRegionHeading>> headingsForEachColumn;
        private final List<List<DataRegionHeading>> headingsForEachRow;
        private final List<Name> contextNames;
        private final List<String> languages;
        private final AzquoMemoryDBConnection connection;
        private final Map<Name, Integer> totalSetSize;
        private final StringBuffer errorTrack;

        public RowFiller(int startRow, int endRow, List<List<AzquoCell>> targetArray, List<List<DataRegionHeading>> headingsForEachColumn, List<List<DataRegionHeading>> headingsForEachRow, List<Name> contextNames, List<String> languages, AzquoMemoryDBConnection connection, Map<Name, Integer> totalSetSize, StringBuffer errorTrack) {
            this.startRow = startRow;
            this.endRow = endRow;
            this.targetArray = targetArray;
            this.headingsForEachColumn = headingsForEachColumn;
            this.headingsForEachRow = headingsForEachRow;
            this.contextNames = contextNames;
            this.languages = languages;
            this.connection = connection;
            this.totalSetSize = totalSetSize;
            this.errorTrack = errorTrack;
        }

        @Override
        public void run() {
            try {
                //System.out.println("Filling " + startRow + " to " + endRow);
                for (int rowNo = startRow; rowNo <= endRow; rowNo++) {
                    List<DataRegionHeading> rowHeadings = headingsForEachRow.get(rowNo);
                    if (rowNo % 1000 == 0) System.out.print(".");
                    List<AzquoCell> returnRow = new ArrayList<AzquoCell>();
                    //todo, multithread on building this region? Since it's already added a different thread could modify the lists, that SHOULD work? Possible to have non udpated lists at the end? sort later!
                    for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                        // values I need to build the CellUI
                        String stringValue;
                        double doubleValue = 0;
                        Set<DataRegionHeading> headingsForThisCell = new HashSet<DataRegionHeading>();
                        ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName = null;

                        //check that we do have both row and column headings, oterhiwse blank them the cell will be blank (danger of e.g. a sum on the name "Product"!)
                        for (DataRegionHeading heading : rowHeadings) {
                            if (heading != null) {
                                headingsForThisCell.add(heading);
                            }
                        }
                        int hCount = headingsForThisCell.size();
                        if (hCount > 0) {
                            for (DataRegionHeading heading : columnHeadings) {
                                if (heading != null) {
                                    headingsForThisCell.add(heading);
                                }
                            }
                            if (headingsForThisCell.size() > hCount) {
                                headingsForThisCell.addAll(dataRegionHeadingsFromNames(contextNames, connection));
                            } else {
                                headingsForThisCell.clear();
                            }
                        }
                        MutableBoolean locked = new MutableBoolean(); // we use a mutable boolean as the functions that resolve the cell value may want to set it
                        boolean checked = true;
                        for (DataRegionHeading heading : headingsForThisCell) {
                            if (heading.getName() == null && heading.getAttribute() == null) {
                                checked = false;
                            }
                            if (!heading.isWriteAllowed()) { // this replaces the isallowed check that was in the functions that resolved the cell values
                                locked.isTrue = true;
                            }
                        }
                        if (!checked) { // not a valid peer set? Lock and no values set (blank)
                            locked.isTrue = true;
                            stringValue = "";
                        } else {
                            // ok new logic here, we need to know if we're going to use attributes or values
                            boolean headingsHaveAttributes = headingsHaveAttributes(headingsForThisCell);
                            if (!headingsHaveAttributes) { // we go the value route (the standard/old one), need the headings as names,
                                // TODO - peer additive check. If using peers and not additive, don't include children
                                List<Value> values = new ArrayList<Value>();
                                doubleValue = valueService.findValueForNames(connection, namesFromDataRegionHeadings(headingsForThisCell), locked, true, values, totalSetSize, languages); // true = pay attention to names additive flag
                                //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                                if (values.size() == 1 && !locked.isTrue) {

                                    Value value = values.get(0);
                                    stringValue = value.getText();
                                    if (stringValue.contains("\n")) {
                                        stringValue = stringValue.replaceAll("\n", "<br/>");//this is unsatisfactory, but a quick fix.
                                    }
                                    // was isnumber test here to add a double to the
                                } else {
                                    stringValue = doubleValue + "";
                                }
                                listOfValuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(values);
                            } else {  // now, new logic for attributes
                                List<Name> names = new ArrayList<Name>();
                                List<String> attributes = new ArrayList<String>();
                                listOfValuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(names, attributes);
                                String attributeResult = valueService.findValueForHeadings(headingsForThisCell, locked, names, attributes);
                                if (NumberUtils.isNumber(attributeResult)) { // there should be a more efficient way I feel given that the result is typed internally
                                    doubleValue = Double.parseDouble(attributeResult);
                                    // ZK would sant this typed? Maybe just sort out later?
                                }
                                attributeResult = attributeResult.replace("\n", "<br/>");//unsatisfactory....
                                stringValue = attributeResult;
                            }
                        }
                /* something to note : in the old model there was a map of headings used for each cell. I could add headingsForThisCell to the cell which would be a unique set for each cell
                 but instead I'll just add the headings and row and context, this should enable us to do what we need later (saving) and I think it would be less memory. 3 object references vs a set*/
                        AzquoCell azquoCell = new AzquoCell(locked.isTrue, listOfValuesOrNamesAndAttributeName, rowHeadings, columnHeadings, contextNames, stringValue, doubleValue);
                        returnRow.add(azquoCell);
                    }
                    targetArray.set(rowNo, returnRow);
                }
            } catch (Exception e) {
                errorTrack.append(e.getMessage()).append("\n");
                errorTrack.append(e.getStackTrace()[0]);
            }
        }
    }


    public List<List<AzquoCell>> getAzquoCellsForRowsColumnsAndContext(AzquoMemoryDBConnection connection, List<List<DataRegionHeading>> headingsForEachRow
            , final List<List<DataRegionHeading>> headingsForEachColumn
            , final List<Name> contextNames, List<String> languages) throws Exception {
        //tryMultiThreaded = !tryMultiThreaded;
        long track = System.currentTimeMillis();
        int totalRows = headingsForEachRow.size();
        int totalCols = headingsForEachColumn.size();
        List<List<AzquoCell>> toReturn = new ArrayList<List<AzquoCell>>(totalRows); // make it the right size so multithreading changes the values but not the structure
        for (int i = 0; i < totalRows; i++) {
            toReturn.add(null);// null the rows, basically adding spaces to the return list
        }
        Map<Name, Integer> totalSetSize = new ConcurrentHashMap<Name, Integer>();// a cache to speed up cell calculation. Short hand of set sizes, we assume they won't change while creating this data.
        System.out.println("data region size = " + totalRows + " * " + totalCols);
        int maxRegionSize = 500000;
        if (totalRows * totalCols > maxRegionSize) {
            throw new Exception("error: data region too large - " + totalRows + " * " + totalCols + ", max cells " + maxRegionSize);
        }
        int threads = threadsToTry;
        if (!tryMultiThreaded) {
            threads = 1;
        }
        System.out.println("populating using " + threads + " threas(s)");
        ExecutorService executor = Executors.newFixedThreadPool(threads); // picking 10 based on an example I saw . . .
        StringBuffer errorTrack = new StringBuffer();// deliberately threadsafe, need to keep an eye on the report building . . .
        // tried multithreaded, going for big chunks
        int chunk = totalRows / threads;
        int startRow = 0;
        int endRow = startRow + chunk - 1; // - 1 as endrow is inclusive
        for (int i = 1; i <= threads; i++) {
            if (endRow >= totalRows || i == threads) { // the last one
                endRow = totalRows - 1;
            }
            executor.execute(new RowFiller(startRow, endRow, toReturn, headingsForEachColumn, headingsForEachRow, contextNames, languages, connection, totalSetSize, errorTrack));
            startRow += chunk;
            endRow += chunk;
        }


        if (errorTrack.length() > 0) {
            throw new Exception(errorTrack.toString());
        }
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            throw new Exception("Data region took longer than an hour to load");
        }
        if (errorTrack.length() > 0) {
            throw new Exception(errorTrack.toString());
        }
        //valueService.printSumStats();
        //valueService.printFindForNamesIncludeChildrenStats();
        logger.info("time to execute : " + (System.currentTimeMillis() - track));
        return toReturn;
    }

    // new logic, derive the headings from the data, no need to resort to resorting etc.
    public List<List<DataRegionHeading>> getColumnHeadingsAsArray(List<List<AzquoCell>> cellArray) {
        List<List<DataRegionHeading>> toReturn = new ArrayList<List<DataRegionHeading>>();
        for (AzquoCell cell : cellArray.get(0)) {
            toReturn.add(cell.getColumnHeadings());
        }
        return transpose2DList(toReturn);
    }

    // new logic, derive the headings from the data, no need to resort to resorting etc.
    public List<List<DataRegionHeading>> getRowHeadingsAsArray(List<List<AzquoCell>> cellArray) {
        List<List<DataRegionHeading>> toReturn = new ArrayList<List<DataRegionHeading>>();
        for (List<AzquoCell> cell : cellArray) {
            toReturn.add(cell.get(0).getRowHeadings());
        }
        return toReturn;
    }


    // for anonymiseing data

    public void randomAdjust(Name name, double low, double high) {
        for (Value value : name.getValues()) {
            try {
                double orig = Double.parseDouble(value.getText());
                Double newValue = orig * ((1 + low) + (high - low) * Math.random());
                int newRound = (int) (newValue * 100);
                value.setText((((double) newRound) / 100) + "");
            } catch (Exception e) {

            }
        }

    }

    // used when comparing values. So ignore the currency symbol if the numbers are the same

    private String stripCurrency(String val) {
        //TODO we need to be able to detect other currencies

        if (val.length() > 1 && "$£".contains(val.substring(0, 1))) {
            return val.substring(1);

        }
        return val;
    }

    public boolean compareStringValues(final String val1, final String val2) {
        //tries to work out if numbers expressed with different numbers of decimal places, maybe including percentage signs and currency symbols are the same.
        if (val1.equals(val2)) return true;
        String val3 = val1;
        String val4 = val2;
        if (val1.endsWith("%") && val2.endsWith("%")) {
            val3 = val1.substring(0, val1.length() - 1);
            val4 = val2.substring(0, val2.length() - 1);
        }
        val3 = stripCurrency(val3);
        val4 = stripCurrency(val4);
        if (NumberUtils.isNumber(val3) && NumberUtils.isNumber(val4)) {
            Double n1 = Double.parseDouble(val3);
            Double n2 = Double.parseDouble(val4);
            if (n1 - n2 == 0) return true;
        }
        return false;
    }

    // todo : does this need to deal with name/attribute combos?
    // for highlighting cells based on provenance time, seems quite the effort

    public int getAge(LoggedInConnection loggedInConnection, String region, int rowInt, int colInt) {
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();

        final List<List<AzquoCell>> sentCells = loggedInConnection.getSentCells(region);
        int age = 10000;
        if (sentCells == null) return age;
        if (rowInt >= sentCells.size()) return age;
        if (sentCells.get(rowInt) == null) return age;

        final List<AzquoCell> rowValues = sentCells.get(rowInt);
        if (colInt >= rowValues.size()) {// a blank column
            return age;
        }
        final ListOfValuesOrNamesAndAttributeName valuesForCell = rowValues.get(colInt).getListOfValuesOrNamesAndAttributeName();
        if (valuesForCell.getValues() == null || valuesForCell.getValues().size() == 0) {
            return 0;
        }
        if (valuesForCell.getValues().size() == 1) {
            for (Value value : valuesForCell.getValues()) {
                if (value == null) {//cell has been changed
                    return 0;
                }
            }
        }
        for (Value value : valuesForCell.getValues()) {
            if (value.getText().length() > 0) {
                if (value.getProvenance() == null) {
                    return 0;
                }
                Date provdate = value.getProvenance().getTimeStamp();

                final long dateSubtract = today.getTime() - provdate.getTime();
                final long time = 1000 * 60 * 60 * 24;

                final int cellAge = (int) (dateSubtract / time);
                if (cellAge < age) {
                    age = cellAge;
                }
            }
        }
        return age;
    }

    // todo, when cell contents are from attributes??
    // this function seemed to be opverly complex before taking into account ordering and things. All we care about is matching to what was sent. What was sent is what should be in the sent cells
    public String formatDataRegionProvenanceForOutput(LoggedInConnection loggedInConnection, String region, int rowInt, int colInt, String jsonFunction) {
        final List<List<AzquoCell>> sentCells = loggedInConnection.getSentCells(region);
        if (sentCells != null) {
            if (sentCells.get(rowInt) != null) {
                final List<AzquoCell> rowValues = sentCells.get(rowInt);
                if (rowValues.get(colInt) != null) {
                    final ListOfValuesOrNamesAndAttributeName valuesForCell = rowValues.get(colInt).getListOfValuesOrNamesAndAttributeName();
                    //Set<Name> specialForProvenance = new HashSet<Name>();
                    if (valuesForCell.getValues() != null) {
                        return formatCellProvenanceForOutput(loggedInConnection, valuesForCell.getValues(), jsonFunction);
                    }
                    return "";
                } else {
                    return ""; //return "error: col out of range : " + colInt;
                }
            } else {
                return ""; //"error: row out of range : " + rowInt;
            }
        } else {
            return ""; //"error: data has not been sent for that row/col/region";
        }
    }

    // As I understand this function is showing names attached to the values in this cell that are not in the requesting spread sheet's row/column/context
    // not exactly sure why
    // this might make it a bit more difficult to jackson but we should aim to do it really

    public String formatCellProvenanceForOutput(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values, String jsonFunction) {

        StringBuilder output = new StringBuilder();
        output.append(jsonFunction + "({\"provenance\":[{");
        int count = 0;
        if (values.size() > 1 || (values.size() > 0 && values.get(0) != null)) {
            valueService.sortValues(values);
            //simply sending out values is a mess - hence this ruse: extract the most persistent names as headings
            Date provdate = values.get(0).getProvenance().getTimeStamp();
            Set<Value> oneUpdate = new HashSet<Value>();
            Provenance p = null;
            boolean firstHeading = true;
            for (Value value : values) {
                if (value.getProvenance().getTimeStamp() == provdate) {
                    oneUpdate.add(value);
                    p = value.getProvenance();
                } else {
                    if (firstHeading) {
                        firstHeading = false;
                    } else {
                        output.append("},{");
                    }
                    output.append(valueService.printExtract(azquoMemoryDBConnection, oneUpdate, p));
                    oneUpdate = new HashSet<Value>();
                    provdate = value.getProvenance().getTimeStamp();
                }
            }
            if (!firstHeading) {
                output.append(",");
            }
            output.append(valueService.printExtract(azquoMemoryDBConnection, oneUpdate, p));
        }
        output.append("}]})");
        return output.toString();
    }

    public String formatProvenanceForOutput(Provenance provenance, String jsonFunction) {
        String output;
        if (provenance == null) {
            output = "{provenance:[{\"who\":\"no provenance\"}]}";
        } else {
            //String user = provenance.getUser();
            output = "{\"provenance\":[{\"who\":\"" + provenance.getUser() + "\",\"when\":\"" + provenance.getTimeStamp() + "\",\"how\":\"" + provenance.getMethod() + "\",\"where\":\"" + provenance.getName() + "\",\"value\":\"\",\"context\":\"" + provenance.getContext().replace("\n", ",") + "\"}]}";
        }
        if (jsonFunction != null && jsonFunction.length() > 0) {
            return jsonFunction + "(" + output + ")";
        } else {
            return output;
        }
    }

    // todo - make work after other code has been rearranged

    public void saveData(LoggedInConnection loggedInConnection, String region) throws Exception {
        int numberOfValuesModified = 0;
        // reenabling here using azquocells which should now have a flag for change. This should simplfy the code below
        int rowCounter = 0;
        if (loggedInConnection.getSentCells(region) != null){
            for (List<AzquoCell> row : loggedInConnection.getSentCells(region)){
                int columnCounter = 0;
                for (AzquoCell cell : row){
                    if (!cell.isLocked() && cell.isChanged()){ // this should be allw e need to know!
                        numberOfValuesModified++;
                        // this save logic is the same as before but getting necessary info from the AzquoCell
                        logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                        //logger.info(orig + "|" + edited + "|"); // we no longet know the original value unless we jam it in AzquoCell

                        final ListOfValuesOrNamesAndAttributeName valuesForCell = cell.getListOfValuesOrNamesAndAttributeName();
                        final Set<DataRegionHeading> headingsForCell = new HashSet<DataRegionHeading>();
                        headingsForCell.addAll(cell.getColumnHeadings());
                        headingsForCell.addAll(cell.getRowHeadings());
                        // one thing about these store functions to the value spreadsheet, they expect the provenance on the logged in connection to be appropriate
                        // right, switch here to deal with attribute based cell values

                        if (valuesForCell.getValues() != null) {
                            if (valuesForCell.getValues().size() == 1) {
                                final Value theValue = valuesForCell.getValues().get(0);
                                logger.info("trying to overwrite");
                                if (cell.getStringValue().length() > 0) {
                                    //sometimes non-existant original values are stored as '0'
                                    valueService.overWriteExistingValue(loggedInConnection, theValue, cell.getStringValue());
                                    numberOfValuesModified++;
                                } else {
                                    valueService.deleteValue(theValue);
                                }
                            } else if (valuesForCell.getValues().isEmpty() && cell.getStringValue().length() > 0) {
                                logger.info("storing new value here . . .");
                                // this call to make the hash set seems rather unefficient
                                valueService.storeValueWithProvenanceAndNames(loggedInConnection, cell.getStringValue(), namesFromDataRegionHeadings(headingsForCell));
                                numberOfValuesModified++;
                            }
                        } else {
                            if (valuesForCell.getNames().size() == 1 && valuesForCell.getAttributeNames().size() == 1) { // allows a simple store
                                Name toChange = valuesForCell.getNames().get(0);
                                String attribute = valuesForCell.getAttributeNames().get(0);
                                logger.info("storing attribute value on " + toChange.getDefaultDisplayName() + " attribute " + attribute);
                                toChange.setAttributeWillBePersisted(attribute, cell.getStringValue());
                            }
                        }

                    }
                }
            }
        }
        if (numberOfValuesModified > 0){
            loggedInConnection.persist();
        }
    }

    // Four little utility functions added by Edd, required now headings are not names

    public List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, AzquoMemoryDBConnection azquoMemoryDBConnection) {
        List<DataRegionHeading> dataRegionHeadings = new ArrayList<DataRegionHeading>();
        for (Name name : names) {
            // will the new write permissions cause an overhead?
            dataRegionHeadings.add(new DataRegionHeading(name, nameService.isAllowed(name, azquoMemoryDBConnection.getWritePermissions())));
        }
        return dataRegionHeadings;
    }

    public Set<Name> namesFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        Set<Name> names = new HashSet<Name>();
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getName() != null) {
                names.add(dataRegionHeading.getName());
            }
        }
        return names;
    }

    public Set<String> attributesFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        Set<String> names = new HashSet<String>();
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getAttribute() != null) {
                names.add(dataRegionHeading.getAttribute().substring(1)); // at the mo I assume attributes begin with .
            }
        }
        return names;
    }

    public boolean headingsHaveAttributes(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getAttribute() != null) {
                return true;
            }
        }
        return false;
    }


}