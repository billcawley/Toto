package com.azquo.service;

import com.azquo.admindao.*;
import com.azquo.adminentities.*;
import com.azquo.memorydb.Name;
import com.azquo.view.AzquoBook;
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

@Configuration
@PropertySource({"classpath:azquo.properties"})

public class OnlineService {


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

    class SetNameChosen {
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


    public OnlineService() {
        String thost = "";
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // may as well in case it goes wrong
        }
        System.out.println("host : " + thost);
        host = thost;
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
                    executeLoop(loggedInConnection, spreadsheetName, onlineReport.getId(), nameLoop, 0);
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
        head.append(readFile("excelStyle.css"));
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
        velocityContext.put("connectionid", loggedInConnection.getConnectionId() + "");
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


    /*
        private StringBuffer createTopMenu(LoggedInConnection loggedInConnection){
            StringBuffer sb = new StringBuffer();
            sb.append("<a class=\"menubutton\" href=\"#\" onclick=\"openTopMenu();\"><img src=\"/images/menu.png\"></a>");
            sb.append("<div id=\"topmenubox\" class=\"topmenubox\">\n");
            sb.append("<ul  class=\"topmenu\">\n");
            sb.append("<li><a href=\"#\" onclick=\"downloadAsXLS();\">Download as XLS</a></li>\n");
            if (loggedInConnection.getAzquoBook().dataRegionPrefix.equals(AzquoBook.azDataRegion)) {
                //sb.append("<li><input type=\"checkbox\" id=\"withMacros\" value=\"\">with macros</li>\n");
                sb.append("<li><a href=\"#\" onclick=\"downloadAsPDF();\">Download as PDF</a></li>\n");
                sb.append("<li><a href=\"#\" onclick=\"inspectDatabase();\">Inspect database</a></li>\n");
                //sb.append(menuItem("Draw chart", "drawChart()", " id=\"drawChart\""));
            }
              sb.append("</ul></div>");
            sb.append("<a class=\"savedata\" href=\"#\" onclick=\"saveData()\" id=\"saveData\" style=\"display:none;\">Save data</a>");
            return sb;


        }

    private StringBuffer menuItem(String name, String link, String itemClass) {
        return menuItem(name, link, itemClass, "");

    }


    private StringBuffer menuItem(String name, String link, String itemClass, String submenu) {
        StringBuffer sb = new StringBuffer();
        sb.append("<li");
        if (itemClass.length() > 0) sb.append(itemClass);
        sb.append("><a href=\"#\" onclick=\"" + link + "\">" + name + "</a>" + submenu + "</li>\n");
        return sb;
    }*/


    public void executeLoop(LoggedInConnection loggedInConnection, String spreadsheetName, int reportId, List<SetNameChosen> nameLoop, int level) throws Exception {
        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        for (Name chosen : nameLoop.get(level).choiceList) {
            setUserChoice(loggedInConnection.getUser().getId(), reportId, nameLoop.get(level).setName, chosen.getDefaultDisplayName());
            level++;
            if (level == nameLoop.size()) {
                azquoBook.executeSheet(loggedInConnection, spreadsheetName, reportId);
            } else {
                executeLoop(loggedInConnection, spreadsheetName, reportId, nameLoop, level + 1);
            }
        }
    }

    // to put a referenced CSS inline for example

    StringBuilder readFile(String filename) {
        // First, copy the base css
        StringBuilder sb = new StringBuilder();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(filename)));
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
            if (chosen.length() == 0) {
                sb.append("<option value=\"\">No database chosen</option>");
            }
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

    public String switchDatabase(LoggedInConnection loggedInConnection, String newDBName) throws Exception {
        Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), newDBName);
        if (db == null) {
            return newDBName + " - no such database";
        }
        loginService.switchDatabase(loggedInConnection, db);
        return "";
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
        if (database.length() > 0) {
            switchDatabase(loggedInConnection, database);
        }
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

    // I think on logging into Magento reports for example

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
            vReport.put("link", "/api/Online/?opcode=loadsheet&connectionid=" + loggedInConnection.getConnectionId() + "&reportid=" + onlineReport.getId());
            reports.add(vReport);
        }
        return convertToVelocity(context, "reports", reports, "azquoReports.vm");
    }

    public String showNameDetails(LoggedInConnection loggedInConnection, String database, int nameId, String parents) throws Exception {
        if (database != null && database.length() > 0) {
            Database newDB = databaseDAO.findForName(loggedInConnection.getBusinessId(), database);
            if (newDB == null) {
                return "no database chosen";
            }
            loginService.switchDatabase(loggedInConnection, newDB);
        }
        VelocityContext context = new VelocityContext();
        context.put("connectionid", loggedInConnection.getConnectionId() + "");
        context.put("parents", parents);
        context.put("rootid", nameId + "");
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
}