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
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

// it seems that trying to configure the properties in spring is a problem
// todo : see if upgrading spring to 4.2 makes this easier?
// todo : try to address proper use of protected and private given how I've shifter lots of classes around. This could apply to all sorts in the system.
@Configuration
@PropertySource({"classpath:azquo.properties"})

public class SpreadsheetService {

    private static final Logger logger = Logger.getLogger(SpreadsheetService.class);

    @Autowired
    UserChoiceDAO userChoiceDAO;

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
        threadsToTry = availableProcessors < 4 ? availableProcessors : (availableProcessors / 2);

    }

    // What actually delivers the reports to the browser. Maybe change to an output writer? Save memory and increase speed.
    // also there's html in here, view stuff, need to get rid of that
    // the report/database server is going to force the view/model split

    public String readExcel(LoggedInUser loggedInUser, OnlineReport onlineReport, String spreadsheetName, String message) throws Exception {
        String path = getHomeDir() + "/temp/";
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, this, importService);
        StringBuilder worksheet = new StringBuilder();
        StringBuilder tabs = new StringBuilder();
        StringBuilder head = new StringBuilder();
        loggedInUser.setAzquoBook(azquoBook);  // is this a heavy object to put against the session?
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
        azquoBook.fillVelocityOptionInfo(loggedInUser, velocityContext);
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
            ws = ws.replace("$azquodatabaselist", createDatabaseSelect(loggedInUser));
        }
        if (ws.indexOf("$fileselect") > 0) {
            ws = ws.replace("$fileselect", "<input type=\"file\" name=\"uploadfile\">");
        }
        velocityContext.put("workbook", ws);

        velocityContext.put("charts", azquoBook.drawCharts(loggedInUser, path).toString());
        return convertToVelocity(velocityContext, null, null, "onlineReport.vm");
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

    public void saveBook(HttpServletResponse response, LoggedInUser loggedInUser, String fileName) throws Exception {
        loggedInUser.getAzquoBook().saveBook(response, fileName);
    }

    public void saveBookasPDF(HttpServletResponse response, LoggedInUser loggedInUser, String fileName) throws Exception {
        loggedInUser.getAzquoBook().saveBookAsPDF(response, fileName);
    }

    public void saveBookActive(HttpServletResponse response, LoggedInUser loggedInUser, String fileName) throws Exception {
        loggedInUser.getAzquoBook().saveBookActive(response, fileName, env.getProperty("azquo.home") + "/onlinereports/Admin/Azquoblank.xls");
    }

    public String changeValue(LoggedInUser loggedInUser, int row, int col, String value) {
        return loggedInUser.getAzquoBook().changeValue(row, col, value, loggedInUser);
    }

    public String getJsonList(DatabaseAccessToken databaseAccessToken, String listName, String listChoice, String entered, String jsonFunction) throws  Exception{
        // todo - proxy via RMI
        return null;
    }

    public String getProvenance(LoggedInUser loggedInUser, int row, int col, String jsonFunction) {
        return loggedInUser.getAzquoBook().getProvenance(loggedInUser, row, col, jsonFunction);
    }

    private StringBuilder createDatabaseSelect(LoggedInUser loggedInUser) {
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

    public String showUploadFile(LoggedInUser loggedInUser) {
        VelocityContext context = new VelocityContext();
        context.put("azquodatabaselist", createDatabaseSelect(loggedInUser));
        return convertToVelocity(context, "upload", null, "upload.vm");
    }

    // on logging into Magento reports for example

    public String showUserMenu(LoggedInUser loggedInUser) {
        List<OnlineReport> onlineReports = onlineReportDAO.findForBusinessIdAndUserStatus(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getStatus());
        VelocityContext context = new VelocityContext();
        context.put("welcome", "Welcome to Azquo!");
        if (loggedInUser.getDatabase() != null) {
            context.put("database", loggedInUser.getDatabase().getName());
        }
        List<Map<String, String>> reports = new ArrayList<Map<String, String>>();
        String reportCategory = "";

        for (OnlineReport onlineReport : onlineReports) {
            Map<String, String> vReport = new HashMap<String, String>();
            if (!onlineReport.getReportCategory().equals(reportCategory)){
                vReport.put("category", onlineReport.getReportCategory());
            }else{
                vReport.put("category","");
            }
            reportCategory = onlineReport.getReportCategory();
            vReport.put("name", onlineReport.getReportName());
            vReport.put("explanation", onlineReport.getExplanation());
            vReport.put("link", "/api/Online/?opcode=loadsheet&reportid=" + onlineReport.getId());
            reports.add(vReport);
        }
        return convertToVelocity(context, "reports", reports, "azquoReports.vm");
    }

    public String showNameDetails(LoggedInUser loggedInUser, String database, String rootId, String parents, String searchNames) throws Exception {
        if (database != null && database.length() > 0) {
            Database newDB = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
            if (newDB == null) {
                return "no database chosen";
            }
            loginService.switchDatabase(loggedInUser, newDB);
        }
        if (searchNames == null) searchNames = "";
        VelocityContext context = new VelocityContext();
        context.put("parents", parents);
        context.put("rootid", rootId);
        context.put("searchnames", searchNames);
        return convertToVelocity(context, null, null, "jstree.vm");
    }

    // do we need to be creating a new velocity engine every time? Not sure if necessary.
    // The amount of string pushing around here bugs me slightly, azquo book probably makes a big old chunk of HTML
    // todo, maybe use velocity in the spring way? http://wiki.apache.org/velocity/VelocityAndSpringStepByStep

    private String convertToVelocity(VelocityContext context, String itemName, List<Map<String, String>> items, String velocityTemplate) {
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



    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception{
        return null;// todo - proxy on through
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

    // function that can be called by the front end to deliver the data and headings
    // todo - proxy on through

    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, String sortCol) throws Exception {
        return null;
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

    // todo, when cell contents are from attributes??
    // this function seemed to be opverly complex before taking into account ordering and things. All we care about is matching to what was sent. What was sent is what should be in the sent cells
    public String formatDataRegionProvenanceForOutput(LoggedInUser loggedInUser, String region, int rowInt, int colInt, String jsonFunction) {
        return "todo : provenance on a data cell";
/*        final List<List<AzquoCell>> sentCells = loggedInConnection.getSentCells(region);
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
        }*/
    }

    // todo, make work again at some point
/*
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
    }*/

    // todo : make work again after code split
    public void saveData(LoggedInUser loggedInUser, String region) throws Exception {
/*        int numberOfValuesModified = 0;
        int rowCounter = 0;
        if (loggedInConnection.getSentCells(region) != null){
            for (List<CellForDisplay> row : loggedInConnection.getSentCells(region)){
                int columnCounter = 0;
                for (CellForDisplay cell : row){
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
        }*/
    }

}