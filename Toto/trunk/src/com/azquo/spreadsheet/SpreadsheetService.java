package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.ReportSchedule;
import com.azquo.admin.onlinereport.ReportScheduleDAO;
import com.azquo.admin.user.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.CellForDisplay;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import com.azquo.util.AzquoMailer;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.ui.ModelMap;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
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
    DatabaseServerDAO databaseServerDAO;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    LoginRecordDAO loginRecordDAO;

    @Autowired
    ReportScheduleDAO reportScheduleDAO;

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

    // to nload the old AzquoBook. Will be phased out so I'll leave the HTML in here.
    public void readExcel(ModelMap model, LoggedInUser loggedInUser, OnlineReport onlineReport, String spreadsheetName) throws Exception {
        String message;
        String path = getHomeDir() + "/temp/";
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, userRegionOptionsDAO, this, rmiClient);
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
                // executing parked commented as the execute loop code makes no sense
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
        List<Map<String, String>> reports = new ArrayList<>();
        for (String dbName:databases.keySet()) {
            Database database = databases.get(dbName);
            List<OnlineReport> onlineReports = onlineReportDAO.findForDatabaseIdAndUserStatus(database.getId(), loggedInUser.getUser().getStatus(),database.getDatabaseType());
            //TODO  set a database for each report - also check permissions
            model.addAttribute("database", database);
            String reportCategory = "";

            for (OnlineReport onlineReport : onlineReports) {
                Map<String, String> vReport = new HashMap<>();
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


    // function that can be called by the front end to deliver the data and headings
    // this is sort of a proxy but the number of parameters changes a fair bit, I'll leave it for the mo

    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , UserRegionOptions userRegionOptions) throws Exception {
        return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).getCellsAndHeadingsForDisplay(databaseAccessToken, regionName, rowHeadingsSource, colHeadingsSource, contextSource,
                userRegionOptions.getHideRows(), userRegionOptions.getRowLimit(), userRegionOptions.getColumnLimit(), userRegionOptions.getSortRow()
                , userRegionOptions.getSortRowAsc(), userRegionOptions.getSortColumn(), userRegionOptions.getSortColumnAsc(), userRegionOptions.getHighlightDays());
    }

    // ok now this is going to ask the DB, it needs the selection criteria and original row and col for speed (so we don't need to get all the data and sort)
    public List<TreeNode> getTreeNode(LoggedInUser loggedInUser, String region, int rowInt, int colInt, int maxSize) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(region);
        if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getData().get(rowInt) != null
                && cellsAndHeadingsForDisplay.getData().size() > rowInt // stop array index problems
                && cellsAndHeadingsForDisplay.getData().get(rowInt).size() > colInt
                && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).formatDataRegionProvenanceForOutput(databaseAccessToken, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol(), maxSize);
        }
        return new ArrayList<>(); // maybe "not found"?
    }


    public void saveData(LoggedInUser loggedInUser, String region, String reportName) throws Exception {
        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(region);
        if (cellsAndHeadingsForDisplay != null){
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            rmiClient.getServerInterface(databaseAccessToken.getServerIp()).saveData(databaseAccessToken, cellsAndHeadingsForDisplay, loggedInUser.getUser().getName(), reportName, loggedInUser.getContext());
        }
    }

    public String setChoices(LoggedInUser loggedInUser, String provline){
         int inSpreadPos = provline.indexOf("in spreadsheet");
        if (inSpreadPos < 0) return null;
        int withPos = provline.indexOf(" with ", inSpreadPos);
        if (withPos < 0) return null;
        String reportName = provline.substring(inSpreadPos + 14, withPos).trim();
        String paramString = provline.substring(withPos + 6);
        int equalsPos = paramString.indexOf(" = ");
        while (equalsPos > 0){
            int endParam = paramString.indexOf(";");
            if (endParam < 0) endParam = paramString.length();
            String paramName = paramString.substring(0, equalsPos).trim();
            String paramValue = paramString.substring(equalsPos + 3, endParam).trim();
            setUserChoice(loggedInUser.getUser().getId(), paramName, paramValue);
            paramString = paramString.substring(endParam);
            if (paramString.length() > 0) paramString = paramString.substring(1);//remove the semicolon
            equalsPos = paramString.indexOf(" = ");
        }
        return reportName;
    }

    // adapted from some VB and description at http://grandzebu.net/informatique/codbar-en/ean13.htm
    // requires a 12 digits string and returns a string to use with EAN13.TTF
    // currently will return blank if the string can't be made, maybe it should exception

    public static String prepareEAN13Barcode(String source){
        int checksum = 0;
        int first;
        if (source == null){
            return "";
        }
        if (source.length() == 13){ // assume it's got the checksum already (how you'd read a barcode) and zap it
            source = source.substring(0,12);
        } else if (source.length() != 12){ // handle source where the extra digit has been calculated?
            return "";
        }
        if (!NumberUtils.isDigits(source)){ // org.apache.commons.lang.math convenience call. It should be obvious what it's doing :)
            return "";
        }
/*        For i% = 12 To 1 Step -2
        checksum% = checksum% + Val(Mid$(chaine$, i%, 1))
        Next*/
// vb starts from index 1 on a string
        // odd indexed chars, we're counting from the RIGHT (the indexes do end up as odd in java as String indexes start at 0)
        for (int i = 11; i >= 0; i -= 2){
            checksum += Integer.parseInt(source.substring(i, i + 1));
        }
        checksum *= 3; // odd indexed chars * 3
        //checksum% = checksum% * 3
        //add on the even indexed chars
        for (int i = 10; i >= 0; i -= 2){
            checksum += Integer.parseInt(source.substring(i,i + 1));
        }
//        chaine$ = chaine$ & (10 - checksum% Mod 10) Mod 10
        source += (10 - checksum%10); // I think equivalent?
//        'The first digit is taken just as it is, the second one come from table A
        StringBuilder toReturn = new StringBuilder();
        // first digit not coded
        toReturn.append(source.charAt(0));
        // second always table A
        toReturn.append((char) (65 + Integer.parseInt(source.substring(1,2))));
        first = Integer.parseInt(source.substring(0,1));
        // switch based on the first number for the next 5 digits. I don't really understand why but it's the rules
        for (int i = 2; i <= 6; i++){
            boolean tableA = false;
            switch (i){
                case 2:
                    if (first >= 0 && first <= 3){
                        tableA = true;
                    }
                    break;
                case 3:
                    if (first == 0 || first == 4 || first == 7 || first == 8){
                        tableA = true;
                    }
                    break;
                case 4:
                    if (first == 0 || first == 1 || first == 4 || first == 5 || first == 9){
                        tableA = true;
                    }
                    break;
                case 5:
                    if (first == 0 || first == 2 || first == 5 || first == 6 || first == 7){
                        tableA = true;
                    }
                    break;
                case 6:
                    if (first == 0 || first == 3 || first == 6 || first == 8 || first == 9){
                        tableA = true;
                    }
                    break;
            }
            if (tableA){
                toReturn.append((char) (65 + Integer.parseInt(source.substring(i,i + 1))));
            } else {
                toReturn.append((char) (75 + Integer.parseInt(source.substring(i,i + 1))));
            }
        }
        // add separator
        toReturn.append("*");
        // last 6 digits including the checksum on table c
        for (int i = 7; i <= 12; i++) {// the checksum was added, source is 13 long
            toReturn.append((char) (97 + Integer.parseInt(source.substring(i,i + 1))));

        }
        toReturn.append("+"); // end marker
        return toReturn.toString();
    }

    // should this be in here?

    public void runScheduledReports() throws Exception {
        for (ReportSchedule reportSchedule : reportScheduleDAO.findWhereDueBefore(LocalDateTime.now())){
            OnlineReport onlineReport = onlineReportDAO.findById(reportSchedule.getReportId());
            Database database = databaseDAO.findById(reportSchedule.getDatabaseId());
            if (onlineReport != null && database != null){
                onlineReport.setPathname(database.getMySQLName());
                List<User> users = userDAO.findForBusinessId(database.getBusinessId());
                User user = null;
                for (User possible : users){
                    if (user == null || possible.isAdministrator()){ // default to admin or the first we can find
                        user = possible;
                    }
                }
                // similar to normal loading
                String bookPath = getHomeDir() + ImportService.dbPath + onlineReport.getPathname() + "/onlinereports/" + onlineReport.getFilename();
//                final Book book = new support.importer.PatchedImporterImpl().imports(new File(bookPath), "Report name"); // the old temporary one, should be fixed now in the ZK libraries
                final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                // the first two make sense. Little funny about the second two but we need a reference to these
                book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                // ok what user? I think we'll call it an admin one.
                DatabaseServer databaseServer = databaseServerDAO.findById(database.getDatabaseServerId());
                // assuming no read permissions?
                LoggedInUser loggedInUser = new LoggedInUser("", user,databaseServer,database, null, null);
                book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                book.getInternalBook().setAttribute(OnlineController.REPORT_ID, reportSchedule.getReportId());
                ZKAzquoBookUtils bookUtils = new ZKAzquoBookUtils(this, userChoiceDAO, userRegionOptionsDAO, rmiClient);
                bookUtils.populateBook(book);
                AzquoMailer azquoMailer = new AzquoMailer();
                // so, can I have my PDF or XLS? Very similar to other the download code in the spreadsheet command controller
                if ("PDF".equals(reportSchedule.getType())){
                    Exporter exporter = Exporters.getExporter("pdf");
                    File file = File.createTempFile(onlineReport.getReportName(), ".pdf");
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(file);
                        exporter.export(book, file);
                    } finally {
                        if (fos != null) {
                            fos.close();
                        }
                    }
                    // now send?
                    azquoMailer.sendEMail(reportSchedule.getRecipients(), null, onlineReport.getReportName(), "Attached", file);
                }
                // again copied and only modified slightly - todo, factor these?
                if ("XLS".equals(reportSchedule.getType())){
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
                    azquoMailer.sendEMail(reportSchedule.getRecipients(), null, onlineReport.getReportName(), "Attached", file);
                }
                // adjust the schedule but wait a mo . . .
                switch (reportSchedule.getPeriod()){
                    case "HOURLY" :
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusHours(1));
                        break;
                    case "DAILY" :
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusDays(1));
                        break;
                    case "WEEKLY" :
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusWeeks(1));
                        break;
                    case "MONTHLY" :
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusMonths(1));
                        break;
                }
                reportScheduleDAO.store(reportSchedule);
            }
        }
    }
}