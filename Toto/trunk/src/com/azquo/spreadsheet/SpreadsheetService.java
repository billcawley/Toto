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


    public SpreadsheetService() {
        String thost = "";
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // may as well in case it goes wrong
        }
        System.out.print("Java Version : " + System.getProperty("java.version"));
        System.out.println("host : " + thost);
        host = thost;
        stringUtils = new StringUtils();
    }

    // What actually delivers the reports to the browser. Maybe change to an output writer? Save memory and increase speed.

    public String readExcel(LoggedInConnection loggedInConnection, OnlineReport onlineReport, String spreadsheetName, String message) throws Exception {

        String path = getHomeDir() + "/temp/";
        if (onlineReport.getId() == 1 && !loggedInConnection.getUser().isAdministrator()) {
            return showUserMenu(loggedInConnection);// user menu being what magento users typically see when logging in, a velocity page
            //onlineReport = onlineReportDAO.findById(-1);//user report list replaces admin sheet
        }

        AzquoBook azquoBook = new AzquoBook(valueService, adminService, nameService, importService, userChoiceDAO, this);
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
                    executeLoop(loggedInConnection,  onlineReport.getId(), nameLoop, 0);
                    return "";
                }
            }
            azquoBook.dataRegionPrefix = AzquoBook.azDataRegion;
            if (onlineReport.getId() == 1 || onlineReport.getId() == -1) {//this is the maintenance workbook
                azquoBook.dataRegionPrefix = AzquoBook.azInput;

            }
            spreadsheetName = azquoBook.printTabs(tabs, spreadsheetName);
            String error = azquoBook.convertSpreadsheetToHTML(loggedInConnection, onlineReport.getId(), spreadsheetName, worksheet);
            if (error.length() > 0) {
                message = error;
            }
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

    public void executeLoop(LoggedInConnection loggedInConnection, int reportId, List<SetNameChosen> nameLoop, int level) throws Exception {
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
    }

    // to put a referenced CSS inline for example
    // edd changing to read from web-inf

    StringBuilder readFile(String filename) {
        // First, copy the base css
        StringBuilder sb = new StringBuilder();
        BufferedReader in = null;
        try {

            in = new BufferedReader(new InputStreamReader(servletContext.getResourceAsStream("/WEB-INF/"+ filename)));
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

    public String saveAdminData(LoggedInConnection loggedInConnection) {
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
        if (newDBName.length()==0){
            loginService.switchDatabase(loggedInConnection,null);
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



    public String showUploadFile(LoggedInConnection loggedInConnection){

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
        if (searchNames==null)searchNames = "";
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

    public String outputHeadings(final List<List<DataRegionHeading>> headings, String language) {

        final StringBuilder sb = new StringBuilder();

        if (language == null || language.length() == 0) language = Name.DEFAULT_DISPLAY_NAME;
        for (int x = 0; x < headings.size(); x++) {
            List<DataRegionHeading> dataRegionHeadings = headings.get(x);
            if (x > 0) sb.append("\n");
            for (int y = 0; y < dataRegionHeadings.size(); y++) {
                if (y > 0) sb.append("\t");
                //NOW - LEAVE THE PRUNING OF NAMES TO EXCEL - MAYBE THE LIST WILL BE SORTED.
                DataRegionHeading rowName = dataRegionHeadings.get(y);
                if (rowName != null) {
                    Name name = rowName.getName();
                    if (name != null) {
                        String nameInLanguage = name.getAttribute(language);
                        if (nameInLanguage == null) {
                            nameInLanguage = name.getDefaultDisplayName();
                        }
                        sb.append(nameInLanguage);
                    } else {
                        String attribute = dataRegionHeadings.get(y).getAttribute();
                        if (attribute != null) sb.append(attribute);
                    }
                }
            }
        }
        return sb.toString();
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


    public String createNameListsFromExcelRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, List<List<List<DataRegionHeading>>> nameLists, final String excelRegionPasted, List<String> attributeNames) throws Exception {
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
                        try {
                            row.add(dataRegionHeadingsFromNames(nameService.parseQuery(azquoMemoryDBConnection, cellString, attributeNames), azquoMemoryDBConnection));
                        } catch (Exception e) {
                            return "error:" + e.getMessage();
                        }
                    }
                }
            }
            while (row.size() < maxColCount) row.add(null);
            nameLists.add(row);
        }
        return "";
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

    private boolean theseRowsInThisRegionAreBlank(LoggedInConnection loggedInConnection, String region, int rowInt, int count) {

        final List<List<ListOfValuesOrNamesAndAttributeName>> dataValueMap = loggedInConnection.getDataValueMap(region);

        if (dataValueMap != null) {
            for (int rowCount = 0; rowCount < count; rowCount++) {
                if (dataValueMap.get(rowInt + rowCount) != null) {
                    final List<ListOfValuesOrNamesAndAttributeName> rowContents = dataValueMap.get(rowInt + rowCount);
                    for (ListOfValuesOrNamesAndAttributeName oneCell : rowContents) {
                        // names should only be set if there is a value
                        if ((oneCell.getValues() != null && oneCell.getValues().size() > 0) || (oneCell.getNames() != null && oneCell.getNames().size() > 0)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
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

    public List<Integer> sortValues(int restrictCount, Map<Integer, Object> sortTotals, boolean isNumber, boolean sortRowsUp) {

        List<Integer> sortedValues = new ArrayList<Integer>();
        if (restrictCount != 0) {
            List<Map.Entry<Integer, Object>> list = new ArrayList<Map.Entry<Integer, Object>>(sortTotals.entrySet());
            if (isNumber) {

                // sort list based on
                if (sortRowsUp) {
                    Collections.sort(list, new Comparator<Map.Entry<Integer, Object>>() {
                        public int compare(Map.Entry<Integer, Object> o1, Map.Entry<Integer, Object> o2) {
                            Double d1 = (Double) o1.getValue();
                            Double d2 = (Double) o2.getValue();
                            return d1.compareTo(d2);
                        }
                    });
                } else {
                    Collections.sort(list, new Comparator<Map.Entry<Integer, Object>>() {
                        public int compare(Map.Entry<Integer, Object> o1, Map.Entry<Integer, Object> o2) {
                            Double d1 = (Double) o1.getValue();
                            Double d2 = (Double) o2.getValue();
                            return d2.compareTo(d1);
                        }
                    });

                }
            } else {
                if (sortRowsUp) {
                    Collections.sort(list, new Comparator<Map.Entry<Integer, Object>>() {
                        public int compare(Map.Entry<Integer, Object> o1, Map.Entry<Integer, Object> o2) {
                            String s1 = (String) o1.getValue();
                            String s2 = (String) o2.getValue();
                            return s1.compareTo(s2);
                        }
                    });
                } else {
                    Collections.sort(list, new Comparator<Map.Entry<Integer, Object>>() {
                        public int compare(Map.Entry<Integer, Object> o1, Map.Entry<Integer, Object> o2) {
                            String s1 = (String) o1.getValue();
                            String s2 = (String) o2.getValue();
                            return s2.compareTo(s1);
                        }
                    });

                }
            }
            for (Map.Entry<Integer, Object> aList : list) {
                sortedValues.add(aList.getKey());
            }
        } else {
            for (int i = 0; i < sortTotals.size(); i++) {
                sortedValues.add(i);
            }
        }
        return sortedValues;
    }

    public String setupRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        long start = System.currentTimeMillis();
        final List<List<List<DataRegionHeading>>> rowHeadingLists = new ArrayList<List<List<DataRegionHeading>>>();
        String error = createNameListsFromExcelRegion(loggedInConnection, rowHeadingLists, headingsSent, loggedInConnection.getLanguages());
        System.out.println("row heading setup took " + (System.currentTimeMillis() - start) + " millisecs");
        loggedInConnection.setRowHeadings(region, expandHeadings(rowHeadingLists));
        return error;
    }

    // THis returns the headings as they're meant to be seen in Excel I think
    // not this is called AFTER the data has been populated, hence how it can determine if rows are blank or not
    // broken into 2 functions to help use the new sheet display

    public String getRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent, final int filterCount) throws Exception {
        String language = stringUtils.getInstruction(headingsSent, "language");
        return outputHeadings(getRowHeadingsAsArray(loggedInConnection, region, filterCount), language);
    }

    public List<List<DataRegionHeading>> getRowHeadingsAsArray(final LoggedInConnection loggedInConnection, final String region, final int filterCount) throws Exception {
        // do we want to try removing blank rows?
        if (filterCount > 0) {
            //send back only those headings that have data - considered in batches of length filtercount.
            List<List<DataRegionHeading>> rowHeadingsWithData = new ArrayList<List<DataRegionHeading>>();
            List<List<DataRegionHeading>> allRowHeadings = loggedInConnection.getRowHeadings(region);
            int rowInt = 0;
            while (rowInt < allRowHeadings.size()) {
                if (!theseRowsInThisRegionAreBlank(loggedInConnection, region, rowInt, filterCount)) {
                    for (int rowCount = 0; rowCount < filterCount; rowCount++) {
                        rowHeadingsWithData.add(allRowHeadings.get(rowInt + rowCount));
                    }
                }
                rowInt += filterCount;
            }
            //note that the sort order has already been set.... there cannot be both a restrict count and a filter count
            return rowHeadingsWithData;
        } else if (loggedInConnection.getRestrictRowCount(region) != null && loggedInConnection.getRestrictRowCount(region) != 0) {//if not blanking then restrict by other criteria
            int restrictRowCount = loggedInConnection.getRestrictRowCount(region);
            List<Integer> sortedRows = loggedInConnection.getRowOrder(region);
            List<List<DataRegionHeading>> rowHeadingsWithData = new ArrayList<List<DataRegionHeading>>();
            List<List<DataRegionHeading>> allRowHeadings = loggedInConnection.getRowHeadings(region);
            if (restrictRowCount > allRowHeadings.size()) {
                restrictRowCount = allRowHeadings.size();
                loggedInConnection.setRestrictRowCount(region, restrictRowCount);
            }
            if (restrictRowCount > sortedRows.size()) {
                restrictRowCount = sortedRows.size();
                loggedInConnection.setRestrictRowCount(region, restrictRowCount);
            }

            for (int rowInt = 0; rowInt < restrictRowCount; rowInt++) {
                rowHeadingsWithData.add(allRowHeadings.get(sortedRows.get(rowInt)));
            }
            return rowHeadingsWithData;
        }
        return loggedInConnection.getRowHeadings(region);
    }

    /* ok so transposing happens here
    this is because the expand headings function is orientated for row headings and the column heading definitions are unsurprisingly set up for columns
     what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

     NOTE : this means the column heading are not stored according to the orientation used in the above function

      hence, to output them we have to transpose them again!

      should they be being transposed back again here after expanding? Or make expanding deal with this? Something to investigate

     */


    public String setupColumnHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        List<List<List<DataRegionHeading>>> columnHeadingLists = new ArrayList<List<List<DataRegionHeading>>>();
        String error = createNameListsFromExcelRegion(loggedInConnection, columnHeadingLists, headingsSent, loggedInConnection.getLanguages());
        loggedInConnection.setColumnHeadings(region, (expandHeadings(transpose2DList(columnHeadingLists))));
        return error;
    }

    // ok this seems rather similar ot the get RowHeadingsLogic - probabtl some factoring and perhaps better dealing with
    // transpose2DList. Todo!

    public String getColumnHeadings(final LoggedInConnection loggedInConnection, final String region, final String language) throws Exception {
        return outputHeadings(getColumnHeadingsAsArray(loggedInConnection, region), language);
    }

    public List<List<DataRegionHeading>> getColumnHeadingsAsArray(final LoggedInConnection loggedInConnection, final String region) throws Exception {
        if (loggedInConnection.getRestrictColCount(region) != null && loggedInConnection.getRestrictColCount(region) != 0) {
            int restrictColCount = loggedInConnection.getRestrictColCount(region);
            List<Integer> sortedCols = loggedInConnection.getColOrder(region);
            List<List<DataRegionHeading>> ColHeadingsWithData = new ArrayList<List<DataRegionHeading>>();
            List<List<DataRegionHeading>> allColHeadings = loggedInConnection.getColumnHeadings(region);
            if (restrictColCount > allColHeadings.size()) {
                restrictColCount = allColHeadings.size();
                loggedInConnection.setRestrictColCount(region, restrictColCount);
            }
            if (restrictColCount > sortedCols.size()) {
                restrictColCount = sortedCols.size();
                loggedInConnection.setRestrictColCount(region, restrictColCount);
            }

            for (int ColInt = 0; ColInt < restrictColCount; ColInt++) {
                ColHeadingsWithData.add(allColHeadings.get(sortedCols.get(ColInt)));
            }
            return transpose2DList(ColHeadingsWithData);
        }
        return transpose2DList(loggedInConnection.getColumnHeadings(region));
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

    private void formatLockMap(LoggedInConnection loggedInConnection, String region, List<List<Boolean>> lockMap) {
        StringBuilder sb = new StringBuilder();
        boolean firstRow = true;
        for (List<Boolean> row : lockMap) {
            if (firstRow) {
                firstRow = false;
            } else {
                sb.append("\n");
            }
            boolean firstCol = true;
            for (Boolean lock : row) {
                if (firstCol) {
                    firstCol = false;
                } else {
                    sb.append("\t");
                }
                if (lock) {
                    sb.append("LOCKED");
                }
            }
        }
        loggedInConnection.setLockMap(region, sb.toString());
    }

    // having built shownValueArray in getExcelDataForColumnsRowsAndContext need to format it. Whether such a function should go back in there is a question

    public final StringBuilder formatDataRegion(LoggedInConnection loggedInConnection, String region, List<List<String>> shownValueArray, int filterCount, int restrictRowCount, int restrictColCount) {

        int rowInt = 0;
        int blockRowCount = 0;
        int outputMarker = 0;
        boolean firstRow = true;
        List<Integer> sortedRows = loggedInConnection.getRowOrder(region);
        List<Integer> sortedCols = loggedInConnection.getColOrder(region);
        final StringBuilder sb = new StringBuilder();
        if (restrictRowCount == 0 || restrictRowCount > sortedRows.size()) {
            restrictRowCount = sortedRows.size();
        }
        if (restrictColCount == 0 || restrictColCount > sortedCols.size()) {
            restrictColCount = sortedCols.size();
        }
        for (int rowNo = 0; rowNo < restrictRowCount; rowNo++) {

            List<String> rowValuesShown = shownValueArray.get(sortedRows.get(rowNo));
            if (blockRowCount == 0) {
                outputMarker = sb.length();// in case we need to truncate it.
            }
            if (!firstRow) {
                sb.append("\n");
            }
            boolean newRow = true;
            for (int colNo = 0; colNo < restrictColCount; colNo++) {
                if (!newRow) {
                    sb.append("\t");
                }
                sb.append(rowValuesShown.get(sortedCols.get(colNo)));
                newRow = false;
            }
            rowInt++;
            firstRow = false;
            if (++blockRowCount == filterCount) {
                if (theseRowsInThisRegionAreBlank(loggedInConnection, region, rowInt - filterCount, filterCount)) {
                    sb.delete(outputMarker, sb.length());
                    // this should be the equivalent of the above delete
                }
                blockRowCount = 0;
            }
        }
        loggedInConnection.setSentDataMap(region, sb.toString());

        return sb;
    }

    public String getDataRegion(LoggedInConnection loggedInConnection, String context, String region, int filterCount, int maxRows, int maxCols) throws Exception {

        if (loggedInConnection.getRowHeadings(region) == null || loggedInConnection.getRowHeadings(region).size() == 0 || loggedInConnection.getColumnHeadings(region) == null || loggedInConnection.getColumnHeadings(region).size() == 0) {
            return "error: no headings passed";
        }


        loggedInConnection.getProvenance("in spreadsheet").setContext(context);

        final StringTokenizer st = new StringTokenizer(context, "\n");
        final List<Name> contextNames = new ArrayList<Name>();
        while (st.hasMoreTokens()) {
            final List<Name> thisContextNames = nameService.parseQuery(loggedInConnection, st.nextToken().trim());
            if (thisContextNames.size() > 1) {
                return "error: context names must be individual - use 'as' to put sets in context";
            }
            if (thisContextNames.size() > 0) {
                //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                contextNames.add(thisContextNames.get(0));
            }
        }
        return getExcelDataForColumnsRowsAndContext(loggedInConnection, contextNames, region, filterCount, maxRows, maxCols);
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
                    sortRowStrings.put(rowNo, cell.stringValue);
                    if (cell.stringValue.length() > 0 && !NumberUtils.isNumber(cell.stringValue)) {
                        rowNumbers = false;
                    }
                }
                if (restrictRowCount > 0 && (sortCol == 0 || sortCol == colNo + 1)) {
                    sortRowTotal += cell.doubleValue;
                }
                if (restrictColCount > 0 && (sortRow == 0 || sortRow == rowNo + 1)) {
                    sortColumnTotals.put(colNo, sortColumnTotals.get(colNo) + cell.doubleValue);
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
                for (int j = 0; j < filterCount; j++){
                    List<AzquoCell> rowToCheck = sortedCells.get((sortedRows.size() - 1) - j); // size - 1 for the last index
                    for (AzquoCell cellToCheck : rowToCheck){
                        if ((cellToCheck.listOfValuesOrNamesAndAttributeName.getNames() != null && !cellToCheck.listOfValuesOrNamesAndAttributeName.getNames().isEmpty())
                                || (cellToCheck.listOfValuesOrNamesAndAttributeName.getValues() != null && !cellToCheck.listOfValuesOrNamesAndAttributeName.getValues().isEmpty())){// there were values or names for the call
                            rowsBlank = false;
                            break;
                        }
                    }
                    if (!rowsBlank){
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

ok I'm going for that object type, outer list rows inner items on those rows, hope that's standard. Outside this function the sorting etc will happen.


    */
    public List<List<AzquoCell>> getAzquoCellsForColumnsRowsAndContext(AzquoMemoryDBConnection connection, final List<List<DataRegionHeading>> headingsForEachColumn
            , List<List<DataRegionHeading>> headingsForEachRow, final List<Name> contextNames, List<String> languages) throws Exception {
        List<List<AzquoCell>> toReturn = new ArrayList<List<AzquoCell>>();
        long track = System.currentTimeMillis();
        int totalRows = headingsForEachRow.size();
        int totalCols = headingsForEachColumn.size();
        int rowNo = 0;
        Map<Name, Integer> totalSetSize = new HashMap<Name, Integer>();// a cache to speed up cell calculation. Short hand of set sizes, we assume they won't change while creating this data.
        System.out.println("data region size = " + totalRows + " * " + totalCols);
        int maxRegionSize = 500000;
        if (totalRows * totalCols > maxRegionSize) {
            throw new Exception("error: data region too large - " + totalRows + " * " + totalCols + ", max cells " + maxRegionSize);
        }
        for (List<DataRegionHeading> rowHeadings : headingsForEachRow) {
            if (rowNo % 1000 == 0) System.out.print(".");
            List<AzquoCell> returnRow = new ArrayList<AzquoCell>();
            toReturn.add(returnRow);
            //todo, multithread on building this region? Since it's already added a different thread could modify the lists, that SHOULD work? Possible to have non udpated lists at the end? sort later!
            for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                // values I need to build the CellUI
                String stringValue = null;
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
        }
        valueService.printSumStats();
        valueService.printFindForNamesIncludeChildrenStats();
        logger.info("time to execute : " + (System.currentTimeMillis() - track));
        return toReturn;
    }

    // the guts of actually delivering the data for a region
    // displayObjectsForNewSheet is similar to shown value array but the latter is made of strings, I need more dynamic objects

    public String getExcelDataForColumnsRowsAndContext(final LoggedInConnection loggedInConnection, final List<Name> contextNames, final String region, int filterCount, int restrictRowCount, int restrictColCount) throws Exception {
        loggedInConnection.setContext(region, contextNames); // needed for provenance
        long track = System.currentTimeMillis();
        int totalRows = loggedInConnection.getRowHeadings(region).size();
        int totalCols = loggedInConnection.getColumnHeadings(region).size();
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
        Integer sortCol = findPosition(loggedInConnection.getColumnHeadings(region), loggedInConnection.getSortCol(region));
        Integer sortRow = findPosition(loggedInConnection.getRowHeadings(region), loggedInConnection.getSortRow(region));

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
        final List<List<ListOfValuesOrNamesAndAttributeName>> dataValuesMap
                = new ArrayList<List<ListOfValuesOrNamesAndAttributeName>>(totalRows); // rows, columns, lists of values
        loggedInConnection.setDataValueMap(region, dataValuesMap);
        final Map<Integer, Object> sortRowTotals = new HashMap<Integer, Object>();
        final Map<Integer, Object> sortRowStrings = new HashMap<Integer, Object>();
        final Map<Integer, Object> sortColumnTotals = new HashMap<Integer, Object>();
//        final Map<Integer, Object> sortColumnStrings = new HashMap<Integer, Object>();
        List<List<Set<DataRegionHeading>>> dataHeadingsMap = new ArrayList<List<Set<DataRegionHeading>>>(totalRows); // rows, columns, lists of names for each cell
        List<List<String>> shownValueArray = new ArrayList<List<String>>();
        List<List<Boolean>> lockArray = new ArrayList<List<Boolean>>();
        int rowNo = 0;
        Map<Name, Integer> totalSetSize = new HashMap<Name, Integer>();
        for (int colNo = 0; colNo < totalCols; colNo++) {
            sortColumnTotals.put(colNo, 0.00);
//            sortColumnStrings.put(colNo,"");
        }
        System.out.println("data region size = " + totalRows + " * " + totalCols);
        if (totalRows * totalCols > 500000) {
            throw new Exception("error: data region too large - " + totalRows + " * " + totalCols + ", max cells 500,000");
        }
        boolean rowNumbers = true;
//        boolean colNumbers = true;
        for (List<DataRegionHeading> rowHeadings : loggedInConnection.getRowHeadings(region)) { // make it like a document
            if (rowNo % 1000 == 0) System.out.print(".");
            ArrayList<ListOfValuesOrNamesAndAttributeName> thisRowValues
                    = new ArrayList<ListOfValuesOrNamesAndAttributeName>(totalCols);
            ArrayList<Set<DataRegionHeading>> thisRowHeadings = new ArrayList<Set<DataRegionHeading>>(totalCols);
            List<String> shownValues = new ArrayList<String>();
            List<Boolean> lockedCells = new ArrayList<Boolean>();
            dataValuesMap.add(thisRowValues);
            dataHeadingsMap.add(thisRowHeadings);
            shownValueArray.add(shownValues);
            lockArray.add(lockedCells);


            double sortRowTotal = 0.0;//note that, if there is a 'sortCol' then only that column is added to the total.
            int colNo = 0;
            sortRowStrings.put(rowNo, "");
            for (List<DataRegionHeading> columnHeadings : loggedInConnection.getColumnHeadings(region)) {
                final Set<DataRegionHeading> headingsForThisCell = new HashSet<DataRegionHeading>();
                //check that we do have both row and column headings.....
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
                        headingsForThisCell.addAll(dataRegionHeadingsFromNames(contextNames, loggedInConnection));
                    } else {
                        headingsForThisCell.clear();
                    }
                }
                // edd putting in peer check stuff here, should I not???
                MutableBoolean locked = new MutableBoolean(); // we use a mutable boolean as the functions that resolve the cell value may want to set it
                // why bother?   Maybe leave it as 'on demand' when a data region doesn't work
                // Map<String, String> result = nameService.isAValidNameSet(azquoMemoryDBConnection, namesForThisCell, new HashSet<Name>());
                // much simpler check - simply that the list is complete.
                boolean checked = true;
                for (DataRegionHeading heading : headingsForThisCell) {
                    if (heading.getName() == null && heading.getAttribute() == null) {
                        checked = false;
                    }
                    if (!heading.isWriteAllowed()) { // this replaces the isallowed check that was in the functions that resolved the cell values
                        locked.isTrue = true;
                    }
                }
                if (!checked) { // not a valid peer set? Show a blank locked cell
                    shownValues.add("");
                    lockedCells.add(true);
                    thisRowValues.add(null);
                } else {
                    // ok new logic here, we need to know if we're going to use attributes or values
                    boolean headingsHaveAttributes = headingsHaveAttributes(headingsForThisCell);
                    thisRowHeadings.add(headingsForThisCell);
                    double cellValue = 0;
                    ListOfValuesOrNamesAndAttributeName valuesOrNamesAndAttributeName;
                    String text = "";
                    if (!headingsHaveAttributes) { // we go the value route (the standard/old one), need the headings as names,
                        // TODO - peer additive check. If using peers and not additive, don't include children
                        List<Value> values = new ArrayList<Value>();
                        cellValue = valueService.findValueForNames(loggedInConnection, namesFromDataRegionHeadings(headingsForThisCell), locked, true, values, totalSetSize, loggedInConnection.getLanguages()); // true = pay attention to names additive flag
                        //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                        if (values.size() == 1 && !locked.isTrue) {

                            Value value = values.get(0);
                            text = value.getText();
                            if (text.contains("\n")) {
                                text = text.replaceAll("\n", "<br/>");//this is unsatisfactory, but a quick fix.
                            }
                            shownValues.add(text);
                        } else {
                            shownValues.add(cellValue + "");
                        }
                        valuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(values);
                    } else {  // now, new logic for attributes
                        List<Name> names = new ArrayList<Name>();
                        List<String> attributes = new ArrayList<String>();
                        valuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(names, attributes);
                        String attributeResult = valueService.findValueForHeadings(headingsForThisCell, locked, names, attributes);
                        if (NumberUtils.isNumber(attributeResult)) { // there should be a more efficient way I feel given that the result is typed internally
                            cellValue = Double.parseDouble(attributeResult);
                        }
                        attributeResult = attributeResult.replace("\n", "<br/>");//unsatisfactory....
                        shownValues.add(attributeResult);
                        text = attributeResult;
                    }
                    thisRowValues.add(valuesOrNamesAndAttributeName);
                    // ok these bits are for sorting. Could put a check on whether a number was actually the result but not so bothered
                    // code was a bit higher, have moved it below the chunk that detects if we're using values or not, see no harm in this.

                    if (sortCol == colNo + 1) {
                        sortRowStrings.put(rowNo, text);
                        if (text.length() > 0 && !NumberUtils.isNumber(text)) {
                            rowNumbers = false;
                        }
                    }
                    if (restrictRowCount > 0 && (sortCol == 0 || sortCol == colNo + 1)) {
                        sortRowTotal += cellValue;
                    }
                    if (restrictColCount > 0 && (sortRow == 0 || sortRow == rowNo + 1)) {
                        sortColumnTotals.put(colNo, (Double) sortColumnTotals.get(colNo) + cellValue);
                    }

                    if (locked.isTrue) {
                        lockedCells.add(true);
                    } else {
                        lockedCells.add(false);
                    }
                }
                colNo++;
            }
            sortRowTotals.put(rowNo++, sortRowTotal);

        }

        //sort and trim rows and cols
        List<Integer> sortedRows;
        if (rowNumbers) {
            sortedRows = sortValues(restrictRowCount, sortRowTotals, rowNumbers, sortRowsUp);
        } else {
            sortedRows = sortValues(restrictRowCount, sortRowStrings, rowNumbers, sortRowsUp);
        }
        loggedInConnection.setRestrictRowCount(region, restrictRowCount);
        loggedInConnection.setRowOrder(region, sortedRows);
        List<Integer> sortedCols = sortValues(restrictColCount, sortColumnTotals, rowNumbers, sortColsRight);
        loggedInConnection.setColOrder(region, sortedCols);
        loggedInConnection.setRestrictColCount(region, restrictColCount);
        final StringBuilder sb = formatDataRegion(loggedInConnection, region, shownValueArray, filterCount, restrictRowCount, restrictColCount);
        formatLockMap(loggedInConnection, region, lockArray);
        valueService.printSumStats();
        valueService.printFindForNamesIncludeChildrenStats();
        loggedInConnection.setDataHeadingsMap(region, dataHeadingsMap);
        logger.info("time to execute : " + (System.currentTimeMillis() - track));
        return sb.toString();
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

        final List<List<ListOfValuesOrNamesAndAttributeName>> dataValueMap = loggedInConnection.getDataValueMap(region);
        final List<Integer> rowOrder = loggedInConnection.getRowOrder(region);

        if (rowOrder != null) {
            if (rowInt >= rowOrder.size()) return 10000;
            rowInt = rowOrder.get(rowInt);
        }
        final List<Integer> colOrder = loggedInConnection.getColOrder(region);
        if (colOrder != null) {
            if (colInt >= colOrder.size()) return 10000;
            colInt = colOrder.get(colInt);
        }
        int age = 10000;
        if (dataValueMap == null) return age;
        if (rowInt >= dataValueMap.size()) return age;
        if (dataValueMap.get(rowInt) == null) return age;

        final List<ListOfValuesOrNamesAndAttributeName> rowValues = dataValueMap.get(rowInt);
        if (colInt >= rowValues.size()) {// a blank column
            return age;
        }
        final ListOfValuesOrNamesAndAttributeName valuesForCell = rowValues.get(colInt);
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
    public String formatDataRegionProvenanceForOutput(LoggedInConnection loggedInConnection, String region, int rowInt, int colInt, String jsonFunction) {
        final List<List<ListOfValuesOrNamesAndAttributeName>> dataValueMap = loggedInConnection.getDataValueMap(region);
        final List<Integer> rowOrder = loggedInConnection.getRowOrder(region);
        final List<Integer> colOrder = loggedInConnection.getColOrder(region);

        if (dataValueMap != null) {
            if (dataValueMap.get(rowInt) != null) {
                final List<ListOfValuesOrNamesAndAttributeName> rowValues = dataValueMap.get(rowOrder.get(rowInt));

                if (rowValues.get(colOrder.get(colInt)) != null) {
                    final ListOfValuesOrNamesAndAttributeName valuesForCell = rowValues.get(colOrder.get(colInt));
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

    public String saveData(LoggedInConnection loggedInConnection, String region, String editedData) throws Exception {
        String result = "";
        logger.info("------------------");
        logger.info(loggedInConnection.getLockMap(region));
        logger.info(loggedInConnection.getRowHeadings(region));
        logger.info(loggedInConnection.getColumnHeadings(region));
        logger.info(loggedInConnection.getSentDataMap(region));
        logger.info(loggedInConnection.getContext(region));
        // I'm not sure if these conditions are quite correct maybe check for getDataValueMap and getDataNamesMap instead of columns and rows etc?
        if (loggedInConnection.getLockMap(region) != null &&
                loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0
                && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0
                && loggedInConnection.getSentDataMap(region) != null && loggedInConnection.getContext(region) != null) {
            // oh-kay, need to compare against the sent data
            // going to parse the data here for the moment as parsing is controller stuff
            // I need to track column and Row
            int rowCounter = 0;
            final String[] originalReader = loggedInConnection.getSentDataMap(region).split("\n", -1);
            final String[] editedReader = editedData.split("\n", -1);
            final String[] lockLines = loggedInConnection.getLockMap(region).split("\n", -1);
            // rows, columns, value lists
            final List<List<ListOfValuesOrNamesAndAttributeName>> dataValuesMap = loggedInConnection.getDataValueMap(region);
            final List<List<Set<DataRegionHeading>>> dataHeadingsMap = loggedInConnection.getDataHeadingsMap(region);
            // TODO : deal with mismatched column and row counts
            int numberOfValuesModified = 0;
            List<Integer> sortedRows = loggedInConnection.getRowOrder(region);

            for (int rowNo = 0; rowNo < lockLines.length; rowNo++) {
                String lockLine = lockLines[rowNo];
                int columnCounter = 0;
                final List<ListOfValuesOrNamesAndAttributeName> rowValues = dataValuesMap.get(sortedRows.get(rowCounter));
                final List<Set<DataRegionHeading>> rowHeadings = dataHeadingsMap.get(rowCounter);
                final String[] originalValues = originalReader[rowNo].split("\t", -1);//NB Include trailing empty strings.
                final String[] editedValues = editedReader[rowNo].split("\t", -1);
                String[] locks = lockLine.split("\t", -1);
                for (int colNo = 0; colNo < locks.length; colNo++) {
                    String locked = locks[colNo];
                    //System.out.println("on " + columnCounter + ", " + rowCounter + " locked : " + locked);
                    // and here we get to the crux, the values do NOT match
                    // ok assign these two then deal with doubvle formatting stuff that can trip things up
                    String orig = originalValues[columnCounter].trim();
                    String edited = editedValues[columnCounter].trim();
                    if (orig.endsWith(".0")) {
                        orig = orig.substring(0, orig.length() - 2);
                    }
                    if (edited.endsWith(".0")) {
                        edited = edited.substring(0, edited.length() - 2);
                    }
                    if (!compareStringValues(orig, edited)) {
                        if (!locked.equalsIgnoreCase("locked")) { // it wasn't locked, good to go, check inside the different values bit to error if the excel tries something it should not
                            logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                            logger.info(orig + "|" + edited + "|");

                            final ListOfValuesOrNamesAndAttributeName valuesForCell = rowValues.get(columnCounter);
                            final Set<DataRegionHeading> headingsForCell = rowHeadings.get(columnCounter);
                            // one thing about these store functions to the value spreadsheet, they expect the provenance on the logged in connection to be appropriate
                            // right, switch here to deal with attribute based cell values

                            if (valuesForCell.getValues() != null) {
                                if (valuesForCell.getValues().size() == 1) {
                                    final Value theValue = valuesForCell.getValues().get(0);
                                    logger.info("trying to overwrite");
                                    if (edited.length() > 0) {
                                        //sometimes non-existant original values are stored as '0'
                                        valueService.overWriteExistingValue(loggedInConnection, theValue, edited);
                                        numberOfValuesModified++;
                                    } else {
                                        valueService.deleteValue(theValue);
                                    }
                                } else if (valuesForCell.getValues().isEmpty() && edited.length() > 0) {
                                    logger.info("storing new value here . . .");
                                    // this call to make the hash set seems rather unefficient
                                    valueService.storeValueWithProvenanceAndNames(loggedInConnection, edited, namesFromDataRegionHeadings(headingsForCell));
                                    numberOfValuesModified++;
                                }
                            } else {
                                if (valuesForCell.getNames().size() == 1 && valuesForCell.getAttributeNames().size() == 1) { // allows a simple store
                                    Name toChange = valuesForCell.getNames().get(0);
                                    String attribute = valuesForCell.getAttributeNames().get(0);
                                    logger.info("storing attribute value on " + toChange.getDefaultDisplayName() + " attribute " + attribute);
                                    toChange.setAttributeWillBePersisted(attribute, edited);
                                }
                            }
                        } else {
                            // TODO  WORK OUT WHAT TO BE DONE ON ERROR - what about calculated cells??
                            //result = "error:cannot edit locked cell " + columnCounter + ", " + rowCounter + " in region " + region;

                        }
                    }
                    columnCounter++;
                }
                rowCounter++;
            }
            result = numberOfValuesModified + " values modified";
            //putting in a 'persist' here for security.
            if (numberOfValuesModified > 0) {
                loggedInConnection.persist();
            }
        } else {
            result = " no sent data/rows/columns/context";
        }
        return result;
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