package com.azquo.spreadsheet;

import com.azquo.ExternalConnector;
import com.azquo.StringLiterals;
import com.azquo.StringUtils;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.ReportSchedule;
import com.azquo.admin.onlinereport.ReportScheduleDAO;
import com.azquo.admin.user.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.transport.RegionOptions;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportExecutor;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.azquo.util.AzquoMailer;
import io.keikai.api.model.Sheet;
import io.keikai.model.CellRegion;
import io.keikai.model.SCell;
import io.keikai.model.SName;
import org.apache.commons.lang.math.NumberUtils;
import io.keikai.api.Exporter;
import io.keikai.api.Exporters;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import serilogj.Log;
import serilogj.LoggerConfiguration;
import serilogj.events.LogEventLevel;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static serilogj.sinks.coloredconsole.ColoredConsoleSinkConfigurator.*;
import static serilogj.sinks.rollingfile.RollingFileSinkConfigurator.*;
import static serilogj.sinks.seq.SeqSinkConfigurator.*;

/*
 * Copyright (C) 2016 Azquo Ltd.
 *
 * it seems that trying to configure the properties in spring is a problem
 * this works but I'm a bit fed up of something that should be simple, go to a simple classpath read??
 *
 * Holding miscellaneous functions at the mo, need to work out what goes where.
*/

public class SpreadsheetService {

//    private static final Logger logger = Logger.getLogger(SpreadsheetService.class);

    private static final Properties azquoProperties = new Properties();

    public static final String host;

    static {
        //System.setProperty("org.apache.poi.util.POILogger", "org.apache.poi.util.SystemOutLogger");
        //System.setProperty("poi.log.level", POILogger.INFO + "");

        System.out.println("Setting JVM locale to UK");
        Locale.setDefault(Locale.UK);
        System.out.println("attempting properties load from classpath");
        try {
            azquoProperties.load(SpreadsheetService.class.getClassLoader().getResourceAsStream("azquo.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String current = null;
        try {
            current = new File(".").getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Current dir:" + current);
        String thost = "";
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // may as well in case it goes wrong
        }
        System.out.println("host : " + thost);
        System.out.println("Available processors : " + Runtime.getRuntime().availableProcessors());
        host = thost;
        Log.setLogger(new LoggerConfiguration()
                .writeTo(coloredConsole())
                .writeTo(rollingFile("test-{Date}.log"), LogEventLevel.Information)
//                .writeTo(seq("http://localhost:5341/"))
                .setMinimumLevel(LogEventLevel.Verbose)
                .createLogger());
    }

    static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

    // event id matches start and finish e.g. upload started/ended
    // synchronsed hmmm
    public static synchronized void monitorLog(String eventId, String business, String user, String type, String event, String identifier) {
        String dateString = format.format(new Date());
        try {
            if (!Files.exists(Paths.get(SpreadsheetService.getHomeDir() + "/azquoevents" + dateString + ".log"))){
                Files.write(Paths.get(SpreadsheetService.getHomeDir() + "/azquoevents" + dateString + ".log"),
                        ("TIMESTAMP\tEVENTID\tBUSINESS\tUSER\tTYPE\tEVENT\tIDENTIFIER\n")
                                .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.write(Paths.get(SpreadsheetService.getHomeDir() + "/azquoevents" + dateString + ".log"),
                    (System.currentTimeMillis() + "\t" + eventId + "\t" + business + "\t" + user + "\t" + type +  "\t" + event +  "\t" + identifier + "\n")
                            .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static final Map<String, String> properties = new ConcurrentHashMap<>();


    // ints not boolean as I want to be able to tell if not set. Thread safety not such a concern, it's reading from a file, can't see how the state would be corrupted
    private static int devMachine = -1;

    private static final String AZQUOHOME = "azquo.home";
    private static final String DEVMACHINE = "dev.machine";
    private static final String ALIAS = "alias";
    private static final String FILESTOIMPORTDIR = "filestoimportdir";
    private static final String DIRECTUPLOADDIR = "directuploaddir";
    // where to scan the XML, initially a path
    private static final String XMLSCANDIR = "xmlscandir";
    // where to put XML
    private static final String XMLDESTINATIONDIR = "xmldestinationdir";
    // option to override where the backups are put
    private static final String DBBACKUPDIR = "dbbackupdir";
    private static final String TRACKINGDB = "trackingdb";

    private static final String TRACKINGTABLE = "trackingtable";

    // for an external system to manage users. Should only really be used on a local network/VPN etc. Not on a public server like data.azquo.com
    private static final String MANAGEUSERSAPIKEY = "manageusersapikey";

    private static String getProperty(String key) {
        if (properties.get(key) == null) {
            if (azquoProperties.getProperty(host + "." + key) != null) {
                properties.put(key, azquoProperties.getProperty(host + "." + key));
            }
            if (properties.get(key) == null) {
                if (azquoProperties.getProperty(key) != null){
                    properties.put(key, azquoProperties.getProperty(key));
                }
            }
        }
        return properties.get(key);
    }

    public static String getHomeDir() {
        return getProperty(AZQUOHOME);
    }

    private static String alias = null;
    // keeps its own pattern
    public static String getAlias() {
        if (alias == null) {
            alias = azquoProperties.getProperty(host + "." + ALIAS);
            if (alias == null) {
                alias = host;
            }
        }
        return alias;
    }
    // keeps its own pattern
    public static boolean inProduction() {
        if (devMachine == -1) {
            if (azquoProperties.getProperty(host + "." + DEVMACHINE) != null) {
                devMachine = (azquoProperties.getProperty(host + "." + DEVMACHINE).equalsIgnoreCase("true") ? 1 : 0);
            } else {
                devMachine = (azquoProperties.getProperty(DEVMACHINE) != null && azquoProperties.getProperty(DEVMACHINE).equalsIgnoreCase("true") ? 1 : 0);
            }
        }
        return devMachine != 1;
    }

    public static String getFilesToImportDir() {
        return getProperty(FILESTOIMPORTDIR);
    }

    public static String getXMLScanDir() {
        return getProperty(XMLSCANDIR);
    }

    public static String getDirectUploadDir() {
        return getProperty(DIRECTUPLOADDIR);
    }

    // where we'll put generated XML for Ed Broking
    public static String getXMLDestinationDir() {
        return getProperty(XMLDESTINATIONDIR);
    }

    public static String getDBBackupDir() {
        return getProperty(DBBACKUPDIR);
    }

    public static String getTrackingDb() {
        return getProperty(TRACKINGDB);
    }

    public static String getTrackingTable() {
        return getProperty(TRACKINGTABLE);
    }

    public static String getManageusersapikey() {
        return getProperty(MANAGEUSERSAPIKEY);
    }

    private static final String LOGONPAGEOVERRIDE = "logonpageoverride";
    public static String getLogonPageOverride() {
        return getProperty(LOGONPAGEOVERRIDE);
    }

    private static final String LOGONPAGECOLOUR = "logonpagecolour";
    public static String getLogonPageColour() {
        return getProperty(LOGONPAGECOLOUR);
    }

    private static final String LOGONPAGEMESSAGE = "logonpagemessage";
    public static String getLogonPageMessage() {
        return getProperty(LOGONPAGEMESSAGE);
    }

    private static final String GROOVYDIR = "groovydir";
    public static String getGroovyDir() {
        return getProperty(GROOVYDIR);
    }

    public static void setUserChoice(LoggedInUser loggedInUser, String choiceName, String choiceValue) {
        int userId = loggedInUser.getUser().getId();
        if (choiceName.startsWith("az_")){
            choiceName = choiceName.substring(3);
        }
        choiceName = choiceName.replace(" ","");
        UserChoice userChoice = UserChoiceDAO.findForUserIdAndChoice(userId, choiceName);
        if (choiceValue != null && choiceValue.length() > 0) {
            if (choiceValue.contains(StringLiterals.languageIndicator)){
                List<String> results = CommonReportUtils.getDropdownListForQuery(loggedInUser,choiceValue);
                if (results!=null&& results.size()==1){//this list trips up where there is not an exact match, but some values for inexact match are found....
                    choiceValue = results.get(0);
                }
            }
             if (userChoice == null) {
                userChoice = new UserChoice(0, userId, choiceName, choiceValue.replace(StringLiterals.QUOTE+"",""), LocalDateTime.now());
                UserChoiceDAO.store(userChoice);
            } else {
                if (userChoice.getChoiceValue().equalsIgnoreCase(choiceValue)){
                   return;
                }
                userChoice.setChoiceValue(choiceValue);
                userChoice.setTime(LocalDateTime.now());
                UserChoiceDAO.store(userChoice);

            }
            //obtain a list of definitions that include the choice name in square brackets - to create new temporary sets
            List<String> dependentNames = CommonReportUtils.getDropdownListForQuery(loggedInUser, StringLiterals.DEFINITION+ StringLiterals.languageIndicator+"[" + choiceName);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            for (String dependentName:dependentNames){
                try {
                    String definition = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, dependentName, StringLiterals.DEFINITION);
                    //now we have both the target name, and the definition, create the set
                    CommonReportUtils.resolveQuery(loggedInUser, definition + " as " + StringLiterals.QUOTE + dependentName + StringLiterals.QUOTE,null);
                }catch (Exception e){
                    //cannot arrive here
                }
             }

        } else {
            if (userChoice != null) {
                UserChoiceDAO.removeById(userChoice);
            }
        }
    }

    // function that can be called by the front end to deliver the data and headings
    // this is sort of a proxy but the number of parameters changes a fair bit, I'll leave it for the mo

    public static CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(LoggedInUser loggedInUser, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , UserRegionOptions userRegionOptions, boolean quiet) throws Exception {
        return RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), loggedInUser.getUser().getEmail(), regionName, valueId, rowHeadingsSource, colHeadingsSource, contextSource,
                userRegionOptions.getRegionOptionsForTransport(), quiet);
    }

    public static CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(LoggedInUser loggedInUser, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , RegionOptions regionOptionsForTransport) throws Exception {
        return RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), loggedInUser.getUser().getEmail(), regionName, 0, rowHeadingsSource, colHeadingsSource, contextSource,
                regionOptionsForTransport, false);
    }

    // ok now this is going to ask the DB, it needs the selection criteria and original row and col for speed (so we don't need to get all the data and sort)
    public static ProvenanceDetailsForDisplay getProvenanceDetailsForDisplay(LoggedInUser loggedInUser, int reportId, String sheetName, String region, UserRegionOptions userRegionOptions, int rowInt, int colInt, int maxSize) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, sheetName, region);
        if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getData().size() > rowInt && cellsAndHeadingsForDisplay.getData().get(rowInt) != null// added range check as rowInt was the same as size - should it have been?
                && cellsAndHeadingsForDisplay.getData().size() > rowInt // stop array index problems
                && cellsAndHeadingsForDisplay.getData().get(rowInt).size() > colInt
                && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getProvenanceDetailsForDisplay(databaseAccessToken, loggedInUser.getUser().getEmail(), cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource(), userRegionOptions.getRegionOptionsForTransport()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol(), maxSize);
        }
        return new ProvenanceDetailsForDisplay("Audit not found", null, null); // maybe "not found"?
    }

    // some code duplication with above, a way to factor?

    public static String getDebugForCell(LoggedInUser loggedInUser, int reportId, String sheetName, String region, UserRegionOptions userRegionOptions, int rowInt, int colInt) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, sheetName, region);
        if (cellsAndHeadingsForDisplay != null
                && cellsAndHeadingsForDisplay.getData().size() > rowInt // stop array index problems
                && cellsAndHeadingsForDisplay.getData().get(rowInt) != null
                && cellsAndHeadingsForDisplay.getData().get(rowInt).size() > colInt
                && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getDebugForCell(databaseAccessToken, loggedInUser.getUser().getEmail(), cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource(), userRegionOptions.getRegionOptionsForTransport()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol());
        }
        return "not found";
    }

    public static void databasePersist(LoggedInUser loggedInUser) throws Exception {
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        RMIClient.getServerInterface(databaseAccessToken.getServerIp()).persistDatabase(databaseAccessToken);
    }

    public static String saveData(LoggedInUser loggedInUser, int reportId, String reportName, String sheetName, String region) throws Exception {
        return saveData(loggedInUser, reportId, reportName, sheetName, region, true); // default to persist server side
    }

    private static void sendEmail(String emailInfo) {
        String[] emailParts = emailInfo.split("\\|"); // I *think* this will do the job, I changed from | which apparently on it's own in regexp is not treated as a string literal
        String emailAddress = emailParts[0];
        String emailSubject = emailParts[1];
        String emailText = emailParts[2];
        AzquoMailer.sendEMail(emailAddress, null, emailSubject, emailText, null, null);
    }

    public static String saveData(LoggedInUser loggedInUser, int reportId, String reportName, String sheetName, String region, boolean persist) throws Exception {
        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, sheetName, region);
        if (cellsAndHeadingsForDisplay != null) {
              // maybe go back to this later, currently it will be tripped up by a spreadsheet querying from more than one DB
            //if (!cellsAndHeadingsForDisplay.getOptions().noSave) {
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            final String result = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).saveData(databaseAccessToken, cellsAndHeadingsForDisplay, loggedInUser.getUser().getEmail(),loggedInUser.getUser().getName(), reportName, loggedInUser.getContext(), persist);
            if (result.startsWith("true")) { // then reset the cells and headings object to reflect the changed state
                for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()) {
                    for (CellForDisplay cell : row) {
                        if (cell.isChanged()) {
                            cell.setNewValuesToCurrentAfterSaving();
                        }
                    }
                }
            }
            return result;
            //}
        }
        return "no data passed for the region " + region + " and report " + reportName;
    }

    public static void unlockData(LoggedInUser loggedInUser) throws Exception {
        RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).unlockData(loggedInUser.getDataAccessToken());
    }

    public final static String FIRST_PLACEHOLDER = "||FIRST||";
    public final static String LAST_PLACEHOLDER = "||LAST||";

    // currently unused, will be by a report scheduler if we ever make one
    public static void setChoicesForReportParameters(Map<String, String> userChoices, String reportParameters) {
        /* right, we need to deal with setting the choices according to report parameters
        category = `Men's suits`, month = last
        currently allowing first or last, if first or last required as names add quotes
        I could rip off the parsing from string utils though it seems rather verbose, really what I want is tokenizing based on , but NOT in the quoted areas
        */

        if (reportParameters != null) { // report parameters are used for scheduled reports
            boolean inQuotes = false;
            int ruleStart = 0;
            int currentIndex = 0;
            for (char c : reportParameters.toCharArray()) {
                if (c == '`') { // can't use Name.Quote, it's on the DB server hmmmmm . . .
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) { // try to parse out the rule
                    String pair = reportParameters.substring(ruleStart, currentIndex).trim();
                    if (pair.indexOf("=") > 0) {
                        final String[] split = pair.split("=");
                        String value = split[1].trim();
                        if (value.equalsIgnoreCase("first")) {
                            value = FIRST_PLACEHOLDER;
                        } else if (value.startsWith("last")) {
                            value = LAST_PLACEHOLDER;
                        } else if (value.startsWith("`")) { // then zap the quotes
                            value = value.replace('`', ' ').trim();
                        }
                        userChoices.put(split[0].toLowerCase().trim(), value);
                    }
                    ruleStart = currentIndex + 1;
                }
                currentIndex++;
            }
        }
    }

    // adapted from some VB and description at http://grandzebu.net/informatique/codbar-en/ean13.htm
    // requires a 12 digits string and returns a string to use with EAN13.TTF
    // currently will return blank if the string can't be made, maybe it should exception

    public static String prepareEAN13Barcode(String source) {
        int checksum = 0;
        int first;
        if (source == null) {
            return "";
        }
        if (source.length() == 13) { // assume it's got the checksum already (how you'd read a barcode) and zap it
            source = source.substring(0, 12);
        } else if (source.length() != 12) { // handle source where the extra digit has been calculated?
            return "";
        }
        if (!NumberUtils.isDigits(source)) { // org.apache.commons.lang.math convenience call. It should be obvious what it's doing :)
            return "";
        }
/*        For i% = 12 To 1 Step -2
        checksum% = checksum% + Val(Mid$(chaine$, i%, 1))
        Next*/
// vb starts from index 1 on a string
        // odd indexed chars, we're counting from the RIGHT (the indexes do end up as odd in java as String indexes start at 0)
        for (int i = 11; i >= 0; i -= 2) {
            checksum += Integer.parseInt(source.substring(i, i + 1));
        }
        checksum *= 3; // odd indexed chars * 3
        //checksum% = checksum% * 3
        //add on the even indexed chars
        for (int i = 10; i >= 0; i -= 2) {
            checksum += Integer.parseInt(source.substring(i, i + 1));
        }
//        chaine$ = chaine$ & (10 - checksum% Mod 10) Mod 10
        source += (10 - checksum % 10); // I think equivalent?
//        'The first digit is taken just as it is, the second one come from table A
        StringBuilder toReturn = new StringBuilder();
        // first digit not coded
        toReturn.append(source.charAt(0));
        // second always table A
        toReturn.append((char) (65 + Integer.parseInt(source.substring(1, 2))));
        first = Integer.parseInt(source.substring(0, 1));
        // switch based on the first number for the next 5 digits. I don't really understand why but it's the rules
        for (int i = 2; i <= 6; i++) {
            boolean tableA = false;
            switch (i) {
                case 2:
                    if (first >= 0 && first <= 3) {
                        tableA = true;
                    }
                    break;
                case 3:
                    if (first == 0 || first == 4 || first == 7 || first == 8) {
                        tableA = true;
                    }
                    break;
                case 4:
                    if (first == 0 || first == 1 || first == 4 || first == 5 || first == 9) {
                        tableA = true;
                    }
                    break;
                case 5:
                    if (first == 0 || first == 2 || first == 5 || first == 6 || first == 7) {
                        tableA = true;
                    }
                    break;
                case 6:
                    if (first == 0 || first == 3 || first == 6 || first == 8 || first == 9) {
                        tableA = true;
                    }
                    break;
            }
            if (tableA) {
                toReturn.append((char) (65 + Integer.parseInt(source.substring(i, i + 1))));
            } else {
                toReturn.append((char) (75 + Integer.parseInt(source.substring(i, i + 1))));
            }
        }
        // add separator
        toReturn.append("*");
        // last 6 digits including the checksum on table c
        for (int i = 7; i <= 12; i++) {// the checksum was added, source is 13 long
            toReturn.append((char) (97 + Integer.parseInt(source.substring(i, i + 1))));

        }
        toReturn.append("+"); // end marker
        return toReturn.toString();
    }

    // should this be in here?

    public static void runScheduledReports() throws Exception {
        Map<String, List<File>> filesToSendForEachEmail = new HashMap<>();
        for (ReportSchedule reportSchedule : ReportScheduleDAO.findWhereDueBefore(LocalDateTime.now())) {
            /* ok we need to group reports by e-mail, as in multiple reports in one email, adapting the old code
            which had the "sendEMail" in this loop actually makes pretty good sense, I mean queue the reports up in a map
            in there then send at the end.
             */
            OnlineReport onlineReport = OnlineReportDAO.findById(reportSchedule.getReportId());
            Database database = DatabaseDAO.findById(reportSchedule.getDatabaseId());
            if (onlineReport != null && database != null) {
                List<User> users = UserDAO.findForBusinessId(database.getBusinessId());
                User user = null;
                for (User possible : users) {
                    if (user == null || possible.isAdministrator()) { // default to admin or the first we can find
                        user = possible;
                    }
                }
                // similar to normal loading
                assert user != null;
                Business b = BusinessDAO.findById(user.getBusinessId());
                if (b == null) {
                    throw new Exception("Business not found for user! Business id : " + user.getBusinessId());
                }
                String bookPath = getHomeDir() + ImportService.dbPath + b.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
//                final Book book = new support.importer.PatchedImporterImpl().imports(new File(bookPath), "Report name"); // the old temporary one, should be fixed now in the ZK libraries
                final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                // the first two make sense. Little funny about the second two but we need a reference to these
                book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                // ok what user? I think we'll call it an admin one.
                DatabaseServer databaseServer = DatabaseServerDAO.findById(database.getDatabaseServerId());
                // assuming no read permissions?
                // I should factor these few lines really
                LoggedInUser loggedInUser = new LoggedInUser(user, databaseServer, database, null, b);
                book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                book.getInternalBook().setAttribute(OnlineController.REPORT_ID, reportSchedule.getReportId());
                // added way after the initial code was written, effectively set choices.
                // today will be in the dateformat just below
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                String parameters = reportSchedule.getParameters();
                if (parameters != null && !parameters.isEmpty()) {
                    String[] choices = parameters.split(";");
                    for (String choice : choices) {
                        int equalPos = choice.indexOf("=");
                        if (equalPos > 0) {
                            String chosen = choice.substring(equalPos + 1);
                            choice = choice.substring(0, equalPos);
                            if (chosen.equalsIgnoreCase("today")){
                                chosen = df.format(new Date());
                            }
                            if (chosen.equalsIgnoreCase("yesterday")){
                                chosen = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now().minusDays(1));
                            }
                            setUserChoice(loggedInUser, choice, chosen);
                        }
                    }
                }
                if ("PDF".equals(reportSchedule.getType())) {
                    book.getInternalBook().setAttribute("novalidations", "true");
                }
                ReportRenderer.populateBook(book, 0);
                // execute ignores the email unless it should send
                if ("Execute".equalsIgnoreCase(reportSchedule.getType())){
                    System.out.println("execute from report scheduler");
                    ReportExecutor.runExecuteCommandForBook(book, StringLiterals.EXECUTE); // standard, there's the option to execute the contents of a different names
                    // so, can I have my PDF or XLS? Very similar to other the download code in the spreadsheet command controller
                } else if ("PDF".equals(reportSchedule.getType())) {
                    Exporter exporter = Exporters.getExporter("pdf");
                    File file = File.createTempFile(onlineReport.getReportName(), ".pdf");
                    exporter.export(book, file);
                    // queue don't send
                    for (String email : reportSchedule.getRecipients().split(",")) {
                        filesToSendForEachEmail.computeIfAbsent(email, t -> new ArrayList<>()).add(file);
                    }
                    // again copied and only modified slightly - todo, factor these?
                }else if ("XLS".equals(reportSchedule.getType())) {
                    Exporter exporter = Exporters.getExporter();
                    File file = File.createTempFile(onlineReport.getReportName(), ".xlsx");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        exporter.export(book, fos);
                    }
                    for (String email : reportSchedule.getRecipients().split(",")) {
                        filesToSendForEachEmail.computeIfAbsent(email, t -> new ArrayList<>()).add(file);
                    }
                }
                // adjust the schedule but wait a mo . . .
                switch (reportSchedule.getPeriod()) {
                    case "HOURLY":
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusHours(1));
                        break;
                    case "DAILY":
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusDays(1));
                        break;
                    case "WEEKLY":
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusWeeks(1));
                        break;
                    case "MONTHLY":
                        reportSchedule.setNextDue(reportSchedule.getNextDue().plusMonths(1));
                        break;
                }
                ReportScheduleDAO.store(reportSchedule);
            }
        }
        // now send
        for (Map.Entry<String, List<File>> emailFiles : filesToSendForEachEmail.entrySet()) {
            // might need to tweak subject, body and file names
            AzquoMailer.sendEMail(emailFiles.getKey(), emailFiles.getKey(), "Azquo Reports", "If you wish to interrogate the figures in this report, please login to Azquo", emailFiles.getValue(), null);
        }
    }

    // is this an aspose hangover? No I don't think so but whether we still need it is another matter. A bit like the barcodes maybe . . .

    public static Map<String, String> getImageList(LoggedInUser loggedInUser) throws Exception {
        Map<String, String> images = new HashMap<>();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        String imageList = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images");
        if (imageList != null) {
            String[] imageArray = imageList.split(",");
            for (String image : imageArray) {
                if (image.length() > 0 && image.indexOf(".") > 0) {
                    images.put(image.substring(0, image.indexOf(".")), image);
                }
            }
        }
        return images;
    }

    public static String getUniqueNameFromId(LoggedInUser loggedInUser, int nameId) throws RemoteException {
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getUniqueNameFromId(loggedInUser.getDataAccessToken(),nameId);
    }
    /* comment for the mo, might be useful later
    public static List<String> nameAutoComplete(LoggedInUser loggedInUser, String name) throws Exception {
        DatabaseAccessTken databaseAccessToken = loggedInUser.getDataAccessToken();
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).nameAutoComplete(databaseAccessToken, name, 100);
    }*/

    public static String findDeflects(LoggedInUser loggedInUser, Book book)throws Exception{
        for (SName sName:book.getInternalBook().getNames()){
            String nameSt = sName.getName().toLowerCase(Locale.ROOT);
            if (nameSt.startsWith("az_") && nameSt.endsWith("unknownaction")){
                String choice = nameSt.substring(0,nameSt.length()-13);
                SName choiceRegion = BookUtils.getNameByName(choice + "choice", book.getSheet(sName.getRefersToSheetName()));
                String choiceSet = BookUtils.getSnameCell(choiceRegion).getStringValue();
                UserChoice uc = UserChoiceDAO.findForUserIdAndChoice(loggedInUser.getUser().getId(),choice.substring(3));
                if (choiceSet!=null && uc !=null){
                    List<String > choices = CommonReportUtils.getDropdownListForQuery(loggedInUser,choiceSet);
                    if (!choices.contains(uc.getChoiceValue())){
                        SCell actionCell = BookUtils.getSnameCell(sName);
                        return actionCell.getStringValue();
                    }
                }
            }
        }
        return null;
    }

    public static String saveExternalData(Book book, LoggedInUser loggedInUser)throws Exception{
        String errors = "";
        int savedRows = 0;
        for (SName name:book.getInternalBook().getNames()){
            int nameRow = -1;
            int connectorRow =  -1;
            int sqlRow = -1;
            int saveRow = -1;
            int keyCol = -1;
            if (name.getName().toLowerCase(Locale.ROOT).startsWith(StringLiterals.AZIMPORTDATA)){
                List<List<String>> importdataspec = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, name);
                int cols = importdataspec.get(0).size();
                int rows = importdataspec.size();
                for (int rowNo = 0;rowNo < rows;rowNo++){
                    String heading = importdataspec.get(rowNo).get(0).toLowerCase(Locale.ROOT);
                    if (heading.equals("sheet/range name")) nameRow = rowNo;
                    if (heading.equals("connector")) connectorRow = rowNo;
                    if (heading.equals("sql")) sqlRow = rowNo;
                    if (heading.equals("save")) saveRow = rowNo;
                }
                if (nameRow < 0 || connectorRow < 0 || sqlRow < 0 || saveRow < 0){
                    return "";
                }
                for (int col=1;col<cols;col++) {
                    String rangeName = importdataspec.get(nameRow).get(col).toLowerCase(Locale.ROOT);
                    if (rangeName.length() == 0){
                        break;
                    }
                    String saveInstructions = importdataspec.get(saveRow).get(col);
                     if   (saveInstructions!=null && saveInstructions.length()>0) {
                        String keyName = StringUtils.findQuery("key",saveInstructions);
                         String updateQuery = StringUtils.findQuery("update", saveInstructions);
                         String insertKey = StringUtils.findQuery("insertkey", saveInstructions);
                         String deleteQuery = StringUtils.findQuery("delete", saveInstructions);
                         if (keyName==null){
                            throw new Exception("cannot find 'key=...;' in the save instructions");
                        }

                        String connectorName = importdataspec.get(connectorRow).get(col).toLowerCase(Locale.ROOT);
                        boolean found = false;
                        Sheet sheet = null;
                        int startRow = 0;
                        int startCol = 0;
                        int rowCount = 0;
                        int colCount = 0;
                        List<List<String>>data = null;
                        for (int i = 0; i < book.getNumberOfSheets(); i++) {
                            sheet = book.getSheetAt(i);
                            if (sheet.getSheetName().toLowerCase(Locale.ROOT).equals(rangeName)) {
                                found = true;
                                data = ImportService.rangeToList(sheet,null);
                                break;
                            }
                        }
                        if (!found) {
                            sheet = null;
                            SName sourceName = book.getInternalBook().getNameByName(rangeName);
                            if (sourceName != null) {
                                sheet = book.getSheet(sourceName.getRefersToSheetName());
                                data = ImportService.rangeToList(sheet,sourceName.getRefersToCellRegion());
                            }
                        }

                        if (data != null || data.size() > 0) {
                            if (rowCount == 0 || rowCount > data.size()) {
                                rowCount = data.size();
                            }
                            if (colCount == 0 || colCount > data.get(0).size()) {
                                colCount = data.get(0).size();
                            }

                            List<String> headingRow = new ArrayList<>();
                            for (int i=0; i< data.get(0).size();i++){
                                headingRow.add(data.get(0).get(i).substring(1,data.get(0).get(i).length()-1));
                            }
                            for (int col2=0;col2<headingRow.size();col2++){
                                if (headingRow.get(col2).equals(keyName)){
                                    keyCol = col2;
                                    break;
                                }

                            }
                            if (keyCol<0){
                                throw new Exception("cannot find " + keyName + " as a column heading");
                            }
                            List<List<String>>savedData = loggedInUser.getExternalData(rangeName);
                             Map<String,List<String>>originaldata = new HashMap<>();
                            for (int rowNo = 1;rowNo < savedData.size();rowNo++) {
                                List<String> originalLine = savedData.get(rowNo);
                                String key = originalLine.get(keyCol);
                                if (!key.equals("''")){
                                    originaldata.put(key, originalLine);
                                }
                            }

                            Set<String> keysFound = new HashSet<>();
                            for (int rowNo = 1; rowNo < rowCount; rowNo++) {
                                List<String> dataline = data.get(rowNo);
                                String keyVal = dataline.get(keyCol);
                                if (keyVal==null || keyVal.length() == 0 || keyVal.equals("''")){
                                    //insertline
                                    boolean hasData = false;
                                    for (int i=0;i< dataline.size();i++){
                                        String val = dataline.get(i);
                                        if (val!=null && val.length()> 0 && !val.equals("''")){
                                            hasData = true;
                                            break;
                                        }
                                    }
                                    if (hasData) {
                                        dataline.set(keyCol, insertKey);
                                        ExternalConnector.getData(loggedInUser, connectorName, updateQuery, createMap(headingRow, dataline), null);
                                        savedRows++;
                                    }
                                }else {
                                    keysFound.add(keyVal);

                                    List<String> originalLine = originaldata.get(keyVal);
                                    if (originalLine==null){
                                        throw new Exception("cannot find the key value "+ keyVal);
                                    }
                                    if (originalLine.size() != dataline.size()) {
                                        throw new Exception("column count does not match on save");
                                    }
                                    boolean diff = false;
                                    for (int colNo = 0; colNo < dataline.size(); colNo++) {
                                        if (!dataline.get(colNo).equals(originalLine.get(colNo).trim())) {
                                            diff = true;
                                            break;
                                        }
                                    }
                                    if (diff) {
                                        //update line
                                        try{
                                            ExternalConnector.getData(loggedInUser, connectorName, updateQuery, createMap(headingRow, dataline), keyName);
                                            savedRows++;
                                        }catch(Exception e){
                                            errors += "Key: " + keyVal + ":" + e.getMessage() + ";";
                                        }
                                    }
                                }
                            }
                            if (deleteQuery!=null && deleteQuery.equals("true")){
                                if(originaldata.keySet().removeAll(keysFound)){
                                    for (String toBeRemoved:originaldata.keySet()){
                                        //delete line - send only the delete value in the 'valueMap'
                                        Map<String,String> deleteMap = new HashMap<>();
                                        deleteMap.put(keyName, toBeRemoved);
                                        ExternalConnector.getData(loggedInUser,connectorName,updateQuery,deleteMap, keyName);
                                        savedRows++;

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return (savedRows + " rows saved;" + errors);
    }



    private static Map<String,String> createMap(List<String> headings, List<String> values){
        Map<String,String> toReturn = new HashMap<>();
        for (int fieldNo = 0;fieldNo <headings.size();fieldNo++){
            if (fieldNo < values.size()){
                toReturn.put(headings.get(fieldNo), values.get(fieldNo));
            }else{
                toReturn.put(headings.get(fieldNo), null);
            }
        }
        return toReturn;
    }

}