package com.azquo.spreadsheet;

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
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.azquo.util.AzquoMailer;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;

import java.io.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;

/*
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * it seems that trying to configure the properties in spring is a problem
 * this works but I'm a bit fed up of something that should be simple, go to a simple classpath read??
 *
 * Holding miscellaneous functions at the mo, need to work out what goes where.
*/

public class SpreadsheetService {

//    private static final Logger logger = Logger.getLogger(SpreadsheetService.class);

    private static final Properties azquoProperties = new Properties();

    private static final String host;

    static {
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
    }

    private static String homeDir = null;

    // ints not boolean as I want to be able to tell if not set. Thread safety not such a concern, it's reading from a file, can't see how the state would be corrupted
    private static int devMachine = -1;

    private static final String AZQUOHOME = "azquo.home";
    private static final String DEVMACHINE = "dev.machine";

    public static String getHomeDir() {
        if (homeDir == null) {
            homeDir = azquoProperties.getProperty(host + "." + AZQUOHOME);
            if (homeDir == null) {
                homeDir = azquoProperties.getProperty(AZQUOHOME);
            }
        }
        return homeDir;
    }

    public static boolean onADevMachine() {
        if (devMachine == -1) {
            if (azquoProperties.getProperty(host + "." + DEVMACHINE) != null) {
                devMachine = (azquoProperties.getProperty(host + "." + DEVMACHINE).equalsIgnoreCase("true") ? 1 : 0);
            } else {
                devMachine = (azquoProperties.getProperty(DEVMACHINE) != null && azquoProperties.getProperty(DEVMACHINE).equalsIgnoreCase("true") ? 1 : 0);
            }
        }
        return devMachine == 1;
    }

    public static void setUserChoice(int userId, String choiceName, String choiceValue) {
        UserChoice userChoice = UserChoiceDAO.findForUserIdAndChoice(userId, choiceName);
        if (choiceValue != null && choiceValue.length() > 0) {
            if (userChoice == null) {
                userChoice = new UserChoice(0, userId, choiceName, choiceValue, new Date());
                UserChoiceDAO.store(userChoice);
            } else {
                if (!choiceValue.equals(userChoice.getChoiceValue())) {
                    userChoice.setChoiceValue(choiceValue);
                    userChoice.setTime(new Date());
                    UserChoiceDAO.store(userChoice);
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

    public static CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , UserRegionOptions userRegionOptions, boolean quiet) throws Exception {
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getCellsAndHeadingsForDisplay(databaseAccessToken, regionName, valueId, rowHeadingsSource, colHeadingsSource, contextSource,
                userRegionOptions.getRegionOptionsForTransport(), quiet);
    }

    // ok now this is going to ask the DB, it needs the selection criteria and original row and col for speed (so we don't need to get all the data and sort)
    public static ProvenanceDetailsForDisplay getProvenanceDetailsForDisplay(LoggedInUser loggedInUser, int reportId, String region, int rowInt, int colInt, int maxSize) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
        if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getData().get(rowInt) != null
                && cellsAndHeadingsForDisplay.getData().size() > rowInt // stop array index problems
                && cellsAndHeadingsForDisplay.getData().get(rowInt).size() > colInt
                && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getProvenanceDetailsForDisplay(databaseAccessToken, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol(), maxSize);
        }
        return new ProvenanceDetailsForDisplay(null, null); // maybe "not found"?
    }

    // some code duplication with above, a way to factor?

    public static String getDebugForCell(LoggedInUser loggedInUser, int reportId, String region, int rowInt, int colInt) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
        if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getData().get(rowInt) != null
                && cellsAndHeadingsForDisplay.getData().size() > rowInt // stop array index problems
                && cellsAndHeadingsForDisplay.getData().get(rowInt).size() > colInt
                && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getDebugForCell(databaseAccessToken, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol());
        }
        return "not found";
    }

    public static void databasePersist(LoggedInUser loggedInUser) throws Exception {
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        RMIClient.getServerInterface(databaseAccessToken.getServerIp()).persistDatabase(databaseAccessToken);
    }

    public static String saveData(LoggedInUser loggedInUser, String region, int reportId, String reportName) throws Exception {
        return saveData(loggedInUser, region, reportId, reportName, true); // default to persist server side
    }

    public static String saveData(LoggedInUser loggedInUser, String region, int reportId, String reportName, boolean persist) throws Exception {
        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
        if (cellsAndHeadingsForDisplay != null) {
            // maybe go back to this later, currently it will be tripped up by a spreadsheet querying from more than one DB
            //if (!cellsAndHeadingsForDisplay.getOptions().noSave) {
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            final String result = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).saveData(databaseAccessToken, cellsAndHeadingsForDisplay, loggedInUser.getUser().getName(), reportName, loggedInUser.getContext(), persist);
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
        return "no data passed for that region " + region + " and report " + reportName;
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
                Business b = BusinessDAO.findById(user.getBusinessId());
                if (b == null) {
                    throw new Exception("Business not found for user! Business id : " + user.getBusinessId());
                }
                String businessDirectory = (b.getBusinessName() + "                    ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
                String bookPath = getHomeDir() + ImportService.dbPath + businessDirectory + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
//                final Book book = new support.importer.PatchedImporterImpl().imports(new File(bookPath), "Report name"); // the old temporary one, should be fixed now in the ZK libraries
                final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                // the first two make sense. Little funny about the second two but we need a reference to these
                book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                // ok what user? I think we'll call it an admin one.
                DatabaseServer databaseServer = DatabaseServerDAO.findById(database.getDatabaseServerId());
                // assuming no read permissions?
                // I should factor these few lines really
                LoggedInUser loggedInUser = new LoggedInUser("", user, databaseServer, database, null, businessDirectory);
                book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                book.getInternalBook().setAttribute(OnlineController.REPORT_ID, reportSchedule.getReportId());
                ReportRenderer.populateBook(book, 0);
                // so, can I have my PDF or XLS? Very similar to other the download code in the spreadsheet command controller
                if ("PDF".equals(reportSchedule.getType())) {
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
                    // queue don't send
                    for (String email : reportSchedule.getRecipients().split(",")) {
                        filesToSendForEachEmail.computeIfAbsent(email, t -> new ArrayList<>()).add(file);
                    }
                }
                // again copied and only modified slightly - todo, factor these?
                if ("XLS".equals(reportSchedule.getType())) {
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
        for (String email : filesToSendForEachEmail.keySet()) {
            // might need to tweak subject, body and file names
            AzquoMailer.sendEMail(email, email, "Azquo Reports", "Attached", filesToSendForEachEmail.get(email), null);
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

    public static List<String> nameAutoComplete(LoggedInUser loggedInUser, String name) throws Exception {
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).nameAutoComplete(databaseAccessToken, name, 100);
    }
}