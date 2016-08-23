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
import com.azquo.memorydb.TreeNode;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.view.CellForDisplay;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import com.azquo.util.AzquoMailer;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.ui.ModelMap;
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
 * Class split now database/report server
 *
 * it seems that trying to configure the properties in spring is a problem
 * this works but I'm a bit fed up of something that should be simple, go to a simple classpath read??
*/

public class SpreadsheetService {

    private static final Logger logger = Logger.getLogger(SpreadsheetService.class);

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

    // on logging into Magento reports for example

    public static void showUserMenu(ModelMap model, LoggedInUser loggedInUser) {
        model.addAttribute("welcome", "Welcome to Azquo!");
        List<Map<String, String>> reports = new ArrayList<>();


        final List<Permission> forUserId = PermissionDAO.findForUserId(loggedInUser.getUser().getId());// new model this should be all we need . . .
//            model.addAttribute("database", database);
        String reportCategory = "";
            for (Permission permission : forUserId) {
                final OnlineReport onlineReport = OnlineReportDAO.findById(permission.getReportId());
                if (onlineReport != null){
                    Map<String, String> vReport = new HashMap<>();
                    if (onlineReport.getReportCategory() != null && !onlineReport.getReportCategory().equals(reportCategory)) {
                        vReport.put("category", onlineReport.getReportCategory());
                    } else {
                        vReport.put("category", "");
                    }
                    reportCategory = onlineReport.getReportCategory();
                    vReport.put("name", onlineReport.getReportName());
                    vReport.put("explanation", onlineReport.getExplanation());
                    vReport.put("link", "/api/Online/?opcode=loadsheet&permissionid=" + permission.getId()); // todo - address how this will be used!
                    reports.add(vReport);
                }
            }
        model.addAttribute("reports", reports);
    }


    // function that can be called by the front end to deliver the data and headings
    // this is sort of a proxy but the number of parameters changes a fair bit, I'll leave it for the mo

    public static CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , UserRegionOptions userRegionOptions) throws Exception {
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getCellsAndHeadingsForDisplay(databaseAccessToken, regionName, valueId, rowHeadingsSource, colHeadingsSource, contextSource,
                userRegionOptions.getRegionOptionsForTransport());
    }

    // ok now this is going to ask the DB, it needs the selection criteria and original row and col for speed (so we don't need to get all the data and sort)
    public static List<TreeNode> getTreeNode(LoggedInUser loggedInUser, int reportId, String region, int rowInt, int colInt, int maxSize) throws Exception {
        final CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
        if (cellsAndHeadingsForDisplay != null && cellsAndHeadingsForDisplay.getData().get(rowInt) != null
                && cellsAndHeadingsForDisplay.getData().size() > rowInt // stop array index problems
                && cellsAndHeadingsForDisplay.getData().get(rowInt).size() > colInt
                && cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt) != null) {
            final CellForDisplay cellForDisplay = cellsAndHeadingsForDisplay.getData().get(rowInt).get(colInt);
            DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
            return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).formatDataRegionProvenanceForOutput(databaseAccessToken, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                    , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                    , cellForDisplay.getUnsortedRow(), cellForDisplay.getUnsortedCol(), maxSize);
        }
        return new ArrayList<>(); // maybe "not found"?
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
        return saveData(loggedInUser,region,reportId,reportName,true); // default to persist server side
    }

    public static String saveData(LoggedInUser loggedInUser, String region, int reportId, String reportName, boolean persist) throws Exception {
        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
        if (cellsAndHeadingsForDisplay != null) {
            if (!cellsAndHeadingsForDisplay.getOptions().noSave) {
                DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
                final String result = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).saveData(databaseAccessToken, cellsAndHeadingsForDisplay, region, loggedInUser.getUser().getName(), reportName, loggedInUser.getContext(), persist);
                if (result.equals("true")){ // then reset the cells and headings object to reflect the changed state
                    for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()){
                        for (CellForDisplay cell : row){
                            if (cell.isChanged()){
                                cell.setNewValuesToCurrentAfterSaving();
                            }
                        }
                    }
                }
                return result;
            }
        }
        return "no data passed for that region and report " + region + ", " + reportName;
    }

    public static String setChoices(LoggedInUser loggedInUser, String provline) {
        int inSpreadPos = provline.toLowerCase().indexOf("in spreadsheet");
        if (inSpreadPos < 0) return null;
        int withPos = provline.indexOf(" with ", inSpreadPos);
        if (withPos < 0) withPos = provline.indexOf(".");//no parameters
        if (withPos < 0) return null;
        String reportName = provline.substring(inSpreadPos + 14, withPos).trim().replace("`", "");//strip any spurious `
        String paramString = provline.substring(withPos + 6);
        int equalsPos = paramString.indexOf(" = ");
        while (equalsPos > 0) {
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
                onlineReport.setPathname(database.getPersistenceName());
                List<User> users = UserDAO.findForBusinessId(database.getBusinessId());
                User user = null;
                for (User possible : users) {
                    if (user == null || possible.isAdministrator()) { // default to admin or the first we can find
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
                DatabaseServer databaseServer = DatabaseServerDAO.findById(database.getDatabaseServerId());
                // assuming no read permissions?
                // I should factor these few lines really
                Business b = BusinessDAO.findById(user.getBusinessId());
                if (b == null){
                    throw new Exception("Business not found for user! Business id : " + user.getBusinessId());
                }
                String businessDirectory = (b.getBusinessName() + "               ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
                LoggedInUser loggedInUser = new LoggedInUser("", user, databaseServer, database, null, null, null, businessDirectory);
                book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                book.getInternalBook().setAttribute(OnlineController.REPORT_ID, reportSchedule.getReportId());
                ZKAzquoBookUtils bookUtils = new ZKAzquoBookUtils();
                bookUtils.populateBook(book, 0);
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
        AzquoMailer azquoMailer = new AzquoMailer();
        for (String email : filesToSendForEachEmail.keySet()) {
            // might need to tweak subject, body and file names
            azquoMailer.sendEMail(email, email, "Azquo Reports", "Attached", filesToSendForEachEmail.get(email), null);
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