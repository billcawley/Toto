package com.azquo.admin;

import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.*;
import com.azquo.dataimport.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import org.apache.commons.io.FileUtils;
import org.springframework.ui.ModelMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

import static com.azquo.dataimport.ImportService.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Used for admin functions. Register, validate etc
 * pretty simple with calls to the vanilla admin dao classes
 * <p/>
 * <p>
 * Note - most calls deal with security internally (isAdministrator) but not all, this really should be consistent
 */
public class AdminService {

    //private static final Logger logger = Logger.getLogger(AdminService.class);

    // after uncommenting to use it won't requite the activation email initially

    public static Business registerBusiness(final String email
            , final String userName
            , final String password
            , final String businessName
            , final String address1
            , final String address2
            , final String address3
            , final String address4
            , final String postcode
            , final String telephone, final String website, String bannerColor, String logo) throws Exception {
        // we need to check for existing businesses
        final String key = shaHash(System.currentTimeMillis() + "");
        final Business.BusinessDetails bd = new Business.BusinessDetails(address1, address2, address3, address4, postcode, telephone, website, key);
        final Business business = new Business(0, businessName, bd, bannerColor, logo);
        final Business existing = BusinessDAO.findByName(businessName);
        if (existing != null) {
            throw new Exception(businessName + " already registered");
        }
        // remove the user existing check, on a new business any user is allowed
        BusinessDAO.store(business);
        final String salt = shaHash(System.currentTimeMillis() + "salt");
        final User user = new User(0, LocalDateTime.now().plusYears(30), business.getId(), email, userName, User.STATUS_ADMINISTRATOR, encrypt(password, salt), salt, "register business", 0, 0, "", null);// Admin with
        UserDAO.store(user);
        /*
        azquoMailer.sendEMail(user.getEmail()
                , user.getName()
                , "Azquo account activation for " + businessName
                , "<html>Dear " + user.getName() + "<br/><br/>Welcome to Azquo!<br/><br/>Your account key is : " + key + "</html>");
        // copy for us
        azquoMailer.sendEMail("info@azquo.com"
                , "Azquo Support"
                , "Azquo account activation for " + businessName
                , "<html>Dear " + user.getName() + "<br/><br/>Welcome to Azquo!<br/><br/>Your account key is : " + key + "</html>");*/
        return business;
    }
/*
this may now not work at all, perhaps delete?

    public String confirmKey(final String businessName, final String email, final String password, final String key, String spreadsheetName) throws Exception{
        final Business business = businessDAO.findByName(businessName);
        if (business != null && business.getBusinessDetails().validationKey.equals(key)) {
            business.setEndDate(LocalDateTime.now().plusYears(10));
            businessDAO.store(business);
            User user = userDao.findForBusinessId(business.getId()).get(0);
            user.setEndDate(LocalDateTime.now().plusYears(10));
            user.setStatus(User.STATUS_ADMINISTRATOR);
            userDao.store(user);
            LoggedInConnection loggedInConnection = loginService.login("unknown", email, password, 0, spreadsheetName, false);
            if (loggedInConnection == null) {
                return "error:no connection id";
            }
            return loggedInConnection.getConnectionId();
        }
        return "error:  incorrect key";
    }*/

    private static String getSQLDatabaseName(final DatabaseServer databaseServer, Business b, final String databaseName) throws Exception {
        StringBuilder candidate = new StringBuilder(getBusinessPrefix(b) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase());
        // this did just check against the database server. I'm now going to say persistence names should be unique. Should probably make the key unique. todo
        while (DatabaseDAO.findForPersistenceName(candidate.toString()) == null && RMIClient.getServerInterface(databaseServer.getIp()).databaseWithNameExists(candidate.toString())) {
            candidate.append("Z");
        }
        return candidate.toString();
    }

    private static String getBusinessPrefix(Business b) {
        return b != null ? (b.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "") : null;
    }

    // ok in new report/database server split creating a database needs distinct bits

    public static Database createDatabase(final String databaseName, final LoggedInUser loggedInUser, DatabaseServer databaseServer) throws Exception {
        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
            // force a developer prefix?
            Database existing = DatabaseDAO.findForNameAndBusinessId(databaseName, loggedInUser.getUser().getBusinessId());
            if (existing != null) {
                throw new Exception("That database already exists");
            }
            final Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
            if (b == null) {
                throw new Exception("That business does not exist");
            }
            final String persistenceName = getSQLDatabaseName(databaseServer, b, databaseName);
            final Database database = new Database(0, b.getId(), loggedInUser.getUser().getId(), databaseName, persistenceName, 0, 0, databaseServer.getId(), null, null, false, -1);
            DatabaseDAO.store(database);
            // will be over to the DB side
            RMIClient.getServerInterface(databaseServer.getIp()).createDatabase(database.getPersistenceName());
            loggedInUser.setDatabaseWithServer(databaseServer, database);
            return database;
        } else {
            throw new Exception("Only administrators can create databases");
        }
    }


    // used by magento, always the currently logged in db
    public static void emptyDatabase(LoggedInUser loggedInUser) throws Exception {
        DatabaseServer databaseServer = DatabaseServerDAO.findById(loggedInUser.getDatabase().getDatabaseServerId());
        RMIClient.getServerInterface(databaseServer.getIp()).emptyDatabase(loggedInUser.getDatabase().getPersistenceName());
    }

    public static void createUser(final String email
            , final String userName
            , final LocalDateTime endDate
            , final String status
            , final String password
            , final LoggedInUser loggedInUser
            , int databaseId
            , int userId
            , String selections
            , String team
    ) throws Exception {
        if (loggedInUser.getUser().isAdministrator()) {
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, endDate, loggedInUser.getUser().getBusinessId(), email, userName, status, encrypt(password, salt), salt, loggedInUser.getUser().getEmail(), databaseId, userId, selections, team);
            UserDAO.store(user);
        } else {
            throw new Exception("You do not have permission to create a user");
        }
    }

    public static String encrypt(final String password, String salt) throws Exception {
        // WARNING! DO NOT MODIFY THE reference to "scapbadopbebedop"  bit in the code below or existing passwords will stop working! This is the extra bit on the salt or the number of hash cycles! stay at 3296
        salt += "scapbadopbebedop";
        byte[] passBytes = password.getBytes(Charset.forName("UTF-8"));
        byte[] saltBytes = salt.getBytes(Charset.forName("UTF-8"));
        int hashCycles = 3296;
        MessageDigest md = MessageDigest.getInstance("SHA");
        for (int i = 0; i < hashCycles; i++) {
            // add the salt to the pass and encrypt, then add the salt to the result and encrypt. Repeat as necessary . . .
            byte[] combined = new byte[saltBytes.length + passBytes.length];
            for (int j = 0; j < combined.length; j++) {
                combined[j] = j < saltBytes.length ? saltBytes[j] : passBytes[j - saltBytes.length];
            }
            md.update(combined);
            passBytes = md.digest();
        }
        return Base64.getEncoder().encodeToString(passBytes);
    }

    public static String shaHash(final String toHash) throws Exception {   // for making a password salt
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(toHash.getBytes(Charset.forName("UTF-8")));
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    public static Business getBusinessById(int id) {
        return BusinessDAO.findById(id);
    }

    // return empty lists? Not sure . . .
    public static List<Database> getDatabaseListForBusinessWithBasicSecurity(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        if (loggedInUser.getUser().isDeveloper()) {
            return DatabaseDAO.findForUserId(loggedInUser.getUser().getId());
        }
        return null;
    }

    public static List<User> getUserListForBusinessWithBasicSecurity(final LoggedInUser loggedInUser) {
        List<User> toReturn = null;
        if (loggedInUser.getUser().isAdministrator()) {
            toReturn = UserDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        if (loggedInUser.getUser().isMaster()) {
            toReturn = UserDAO.findForBusinessIdAndCreatedBy(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getEmail());
        }
        if (toReturn != null){
            for (User u : toReturn){
                UserActivity ua = UserActivityDAO.findMostRecentForUserAndBusinessId(loggedInUser.getUser().getBusinessId(), u.getEmail());
                if (ua != null){
                    u.setRecentActivity(ua.getTimeStamp().toString());
                }
            }
        }
        return toReturn;
    }

    public static List<OnlineReport> getReportList(final LoggedInUser loggedInUser, boolean webFormat) {
        List<OnlineReport> reportList = new ArrayList<>();
        if (!loggedInUser.getUser().isAdministrator() && !loggedInUser.getUser().isDeveloper()) {
            int reportId = loggedInUser.getUser().getReportId();
            int dbId = loggedInUser.getUser().getDatabaseId();
            if (reportId > 0 && dbId > 0) {
                OnlineReport or = OnlineReportDAO.findById(reportId);
                if (or != null) {
                    or.setDatabase(DatabaseDAO.findById(dbId).getName());
                    reportList.add(or);
                    return reportList;
                }
            }
        }

        List<Database> databases = DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        for (Database database : databases) {
            if (loggedInUser.getUser().isAdministrator()) {// admin gets all
                List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(database.getId());
                for (OnlineReport report : reports) {
                    report.setDatabase(database.getName());
                }
                reportList.addAll(reports);
            } else if (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()) { // developer constrained to their own database reports
                List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(database.getId());
                for (OnlineReport report : reports) {
                    report.setDatabase(database.getName());
                }
                reportList.addAll(reports);
            }
        }
        if (loggedInUser.getUser().isAdministrator()) {
            List<OnlineReport> reports = OnlineReportDAO.findForBusinessIdWithNoDatabase(loggedInUser.getUser().getBusinessId());
            for (OnlineReport report : reports) {
                report.setDatabase("No database");
            }
            reportList.addAll(reports);
        }
        // was setting the database name for each report, this will be irrelevant
        if (reportList.size() == 0) {
            OnlineReport notFound = new OnlineReport(0, LocalDateTime.now(), 0, loggedInUser.getUser().getId(), "", "No reports found", "", "", "");
            reportList.add(notFound);
        } else {
            for (OnlineReport or : reportList) {
                User otherUser = UserDAO.findById(or.getUserId());
                or.setReportName(or.getReportName() + " uploaded by " + (otherUser != null ? otherUser.getName() : "?")); // I'm assuming the report isn't saved after - just for
            }
        }
        reportList.sort(Comparator.comparing(o -> (o.getDatabase() + getVal(o.getCategory()) + getVal(o.getExplanation()))));
        if (webFormat){ // for the web interface, don't do it for the plugin for example
            // for formatting purposes, displaying the report list with useful categories
            // - notably this could cause problems if one of these were saved but I'm not that bothered for the moment
            String c = null;
            for (OnlineReport or : reportList){
                if (or.getCategory() == null || or.getCategory().equals(c)){
                    or.setCategory("");
                } else {
                    c = or.getCategory();
                }
            }
        }

        return reportList;
    }

    private static String getVal(String value){
        if (value==null || value.isEmpty()) return "zzz";
        return value;
    }
    public static List<ReportSchedule> getReportScheduleList(final LoggedInUser loggedInUser) {
        List<ReportSchedule> toReturn = new ArrayList<>();
        List<Database> databases = DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        for (Database database : databases) {
            List<ReportSchedule> reportSchedules = ReportScheduleDAO.findForDatabaseId(database.getId());
            toReturn.addAll(reportSchedules);
        }
        return toReturn;
    }

    public static List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusinessWithBasicSecurity(final LoggedInUser loggedInUser, String fileSearch, boolean withAutos) {
        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
            List<UploadRecord> uploadRecords = UploadRecordDAO.findForBusinessId(loggedInUser.getUser().getBusinessId(), withAutos); // limited to 10k for the mo
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<>();
            int count = 0;
            for (UploadRecord uploadRecord : uploadRecords) {
                if (fileSearch == null || fileSearch.equals("") || (uploadRecord.getFileName().toLowerCase().contains(fileSearch.toLowerCase()))) {
                    if (loggedInUser.getUser().isAdministrator() || uploadRecord.getUserId() == loggedInUser.getUser().getId()) { // admin is all, developer is just theirs
                        count++;
                        String dbName = "";
                        if (uploadRecord.getDatabaseId() > 0) {
                            Database database = DatabaseDAO.findById(uploadRecord.getDatabaseId());
                            if (database != null) {
                                dbName = database.getName();
                            }
                        }
                        String userName = "";
                        if (uploadRecord.getUserId() > 0) {
                            User user = UserDAO.findById(uploadRecord.getUserId());
                            if (user != null) {
                                userName = user.getName();
                            }
                        }
                        boolean downloadable = false;
                        if (uploadRecord.getTempPath() != null && !uploadRecord.getTempPath().isEmpty()) {
                            File test = new File(uploadRecord.getTempPath());
                            if (test.exists() && test.isFile()) {
                                downloadable = true;
                            }
                        }
                        uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, BusinessDAO.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName, downloadable));
                        if (count > 5000) {
                            break;
                        }
                    }
                }
            }
            String fileName = null;
            for (UploadRecord.UploadRecordForDisplay uploadRecordForDisplay:uploadRecordsForDisplay){
                if (fileName==null){
                    fileName = uploadRecordForDisplay.getFileName();
                }else{
                    if (!uploadRecordForDisplay.getFileName().equals(fileName)) {
                        //if there is more than one report name reduce the list to the latest uploads only
                        uploadRecordsForDisplay.sort((o1, o2) -> ((o2.getFileName() + o2.getTextOrderedDate()).compareTo((o1.getFileName() + o1.getTextOrderedDate()))));
                        List<UploadRecord.UploadRecordForDisplay> newList = new ArrayList<>();
                        fileName = null;
                        int fileCount = 0;
                        UploadRecord.UploadRecordForDisplay lastURFD = null;
                        for (UploadRecord.UploadRecordForDisplay uRFD : uploadRecordsForDisplay) {
                            if (!uRFD.getFileName().equals(fileName)) {
                                fileName = uRFD.getFileName();
                                if (fileCount > 0){
                                    lastURFD.setCount(fileCount);
                                }
                                fileCount = 1;
                                newList.add(uRFD);
                                lastURFD = uRFD;
                            }else{
                                fileCount++;
                            }
                        }
                        if (fileCount > 0){
                            lastURFD.setCount(fileCount);
                        }
                        uploadRecordsForDisplay = newList;
                        uploadRecordsForDisplay.sort((o1, o2) -> (o2.getDate().compareTo(o1.getDate())));
                        break;
                    }
                }
            }



            return uploadRecordsForDisplay;
        }
        return null;
    }

    public static List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForUserWithBasicSecurity(LoggedInUser loggedInUser) {
        List<UploadRecord> forDatabaseIdWithFileType = UploadRecordDAO.findForDatabaseIdWithFileType(loggedInUser.getDatabase().getId());// limited to 10k for the mo
        // ducplication, factor later, todo
        List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<>();
        for (UploadRecord uploadRecord : forDatabaseIdWithFileType){
            String dbName = "";
            if (uploadRecord.getDatabaseId() > 0) {
                Database database = DatabaseDAO.findById(uploadRecord.getDatabaseId());
                if (database != null) {
                    dbName = database.getName();
                }
            }
            String userName = "";
            if (uploadRecord.getUserId() > 0) {
                User user = UserDAO.findById(uploadRecord.getUserId());
                if (user != null) {
                    userName = user.getName();
                }
            }
            boolean downloadable = false;
            if (uploadRecord.getTempPath() != null && !uploadRecord.getTempPath().isEmpty()) {
                File test = new File(uploadRecord.getTempPath());
                if (test.exists() && test.isFile()) {
                    downloadable = true;
                }
            }
            uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, BusinessDAO.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName, downloadable));
        }
        return uploadRecordsForDisplay;
    }

    // only short but perhaps logic could be cleaned up a little
    public static List<PendingUpload.PendingUploadForDisplay> getPendingUploadsForDisplayForBusinessWithBasicSecurity(final LoggedInUser loggedInUser, String fileSearch, boolean allteams, boolean uploaded) {
        List<PendingUpload> pendingUploads;
        if (fileSearch != null && fileSearch.length() > 0) {
            pendingUploads = PendingUploadDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        } else {
            if (uploaded) {
                pendingUploads = PendingUploadDAO.findForBusinessIdAndProcessed(loggedInUser.getUser().getBusinessId());
            } else {
                if (loggedInUser.getUser().getTeam() != null && loggedInUser.getUser().getTeam().length() > 0 && !allteams) {
                    pendingUploads = PendingUploadDAO.findForBusinessIdAndTeamNotProcessed(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getTeam());
                } else {
                    pendingUploads = PendingUploadDAO.findForBusinessIdNotProcessed(loggedInUser.getUser().getBusinessId());
                }
            }
        }

        // new logic - since all users can access this I'll now constrain the list if the user isn't admin
        if (!loggedInUser.getUser().isAdministrator()){
            List<Integer> okDatabaseIds = new ArrayList<>();
            for (LoggedInUser.ReportIdDatabaseId securityPair : loggedInUser.getReportIdDatabaseIdPermissions().values()){
                okDatabaseIds.add(securityPair.getDatabaseId());
            }
            pendingUploads.removeIf(next -> !okDatabaseIds.contains(next.getDatabaseId()));
        }

        List<PendingUpload.PendingUploadForDisplay> pendingUploadForDisplays = new ArrayList<>();
        int count = 0;
        for (PendingUpload pendingUpload : pendingUploads) {
            if (fileSearch == null || fileSearch.equals("") || (pendingUpload.getFileName().toLowerCase().contains(fileSearch.toLowerCase()))) {
                count++;
                pendingUploadForDisplays.add(new PendingUpload.PendingUploadForDisplay(pendingUpload));
                if (count > 100) {
                    break;
                }
            }
        }
        return pendingUploadForDisplays;
    }

    public static void deleteUserById(int userId, LoggedInUser loggedInUser) {
        User user = UserDAO.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
            UserDAO.removeById(user);
        }
    }

    public static boolean deleteUserByLogin(String login, Business b) {
        User user = UserDAO.findByEmailAndBusinessId(login, b.getId());
        if (user != null) {
            UserDAO.removeById(user);
            return true;
        }
        return false;
    }

    public static User getUserById(int userId, LoggedInUser loggedInUser) {
        User user = UserDAO.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
            return user;
        }
        return null;
    }

    public static Database getDatabaseByIdWithBasicSecurityCheck(int databaseId, LoggedInUser loggedInUser) {
        Database database = DatabaseDAO.findById(databaseId);
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()))) {
            return database;
        }
        return null;
    }

    public static OnlineReport getReportByIdWithBasicSecurityCheck(int reportId, LoggedInUser loggedInUser) {
        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
        if (onlineReport != null && ((loggedInUser.getUser().isAdministrator() && onlineReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && onlineReport.getUserId() == loggedInUser.getUser().getId()))) {
            return onlineReport;
        }
        return null;
    }

    public static void removeReportByIdWithBasicSecurity(LoggedInUser loggedInUser, int reportId) {
        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
        if (onlineReport != null && ((loggedInUser.getUser().isAdministrator() && onlineReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && onlineReport.getUserId() == loggedInUser.getUser().getId()))) {
            Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + onlineReportsDir + onlineReport.getFilenameForDisk());
            if (Files.exists(fullPath) || Files.isDirectory(fullPath)) {
                try {
                    Files.deleteIfExists(fullPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            OnlineReportDAO.removeById(onlineReport);
            // and the schedules
            List<ReportSchedule> reportSchedules = ReportScheduleDAO.findForReportId(reportId);
            for (ReportSchedule reportSchedule : reportSchedules) {
                ReportScheduleDAO.removeById(reportSchedule);
            }
        }
    }

    public static void removeImportTemplateByIdWithBasicSecurity(LoggedInUser loggedInUser, int templateId) {
        ImportTemplate importTemplate = ImportTemplateDAO.findById(templateId);
        if (importTemplate != null && (loggedInUser.getUser().isAdministrator() && importTemplate.getBusinessId() == loggedInUser.getUser().getBusinessId())) {
            Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + importTemplate.getFilenameForDisk());
            if (Files.exists(fullPath) || Files.isDirectory(fullPath)) {
                try {
                    Files.deleteIfExists(fullPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            ImportTemplateDAO.removeById(importTemplate);
        }
    }

    // code adapted from spreadsheet
    public static void removeDatabaseByIdWithBasicSecurity(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getUserId() == loggedInUser.getUser().getId()))) {
            // note, used to delete choices for the reports for this DB, won't do this now as
            // before unlinking get the reports to see if they need zapping
            final List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(db.getId());
            DatabaseReportLinkDAO.unLinkDatabase(databaseId);
            for (OnlineReport or : reports) {
                if (DatabaseReportLinkDAO.getDatabaseIdsForReportId(or.getId()).isEmpty() && UserDAO.findForReportId(or.getId()).isEmpty()) { // then this report no longer has any databases
                    removeReportByIdWithBasicSecurity(loggedInUser, or.getId());
                }
            }
            // if there is a template associated with this database and no others then zap it
            ImportTemplate importTemplate = db.getImportTemplateId() != -1 ? ImportTemplateDAO.findById(db.getImportTemplateId()) : null;
            if (importTemplate != null) {
                if (DatabaseDAO.findForImportTemplateId(importTemplate.getId()).size() == 1) {
                    ImportTemplateDAO.removeById(importTemplate);
                    Files.deleteIfExists(Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + importTemplate.getFilenameForDisk()));
                }
            }

            OpenDatabaseDAO.removeForDatabaseId(db.getId());
            UploadRecordDAO.removeForDatabaseId(db.getId());
            // zap the backups also
            String dbBackupsDirectory = DBCron.getDBBackupsDirectory(db);
            FileUtils.deleteDirectory(new File(dbBackupsDirectory));
            // I'm not going to delete the files for the moment
            PendingUploadDAO.removeForDatabaseId(db.getId());
            DatabaseDAO.removeById(db);
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).dropDatabase(db.getPersistenceName());
        }
    }

    public static void emptyDatabaseByIdWithBasicSecurity(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getUserId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).emptyDatabase(db.getPersistenceName());
            Database oldDb = loggedInUser.getDatabase();
            LoginService.switchDatabase(loggedInUser, db);
            updateNameAndValueCounts(loggedInUser, db);
            LoginService.switchDatabase(loggedInUser, oldDb);
        }
    }

    //now also does the last audit
    public static void updateNameAndValueCounts(LoggedInUser loggedInUser, Database database) throws Exception {
        // security can cause a problem here hence the ifs
        int nameCount = AdminService.getNameCountWithBasicSecurity(loggedInUser, database);
        if (nameCount >= 0){
            database.setNameCount(nameCount);
        }
        int valueCount = AdminService.getValueCountWithBasicSecurity(loggedInUser, database);
        if (valueCount >= 0){
            database.setValueCount(valueCount);
        }
        String recentProvenance = getMostRecentProvenance(loggedInUser, database);
        if (recentProvenance != null){
            database.setLastProvenance(recentProvenance);
        }
        DatabaseDAO.store(database);
    }

    public static void checkDatabaseByIdWithBasicSecurity(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getUserId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).checkDatabase(db.getPersistenceName());
        }
    }

    public static void unloadDatabaseWithBasicSecurity(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getUserId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).unloadDatabase(db.getPersistenceName());
        }
    }

    public static void deleteUploadRecord(LoggedInUser loggedInUser, int uploadRecordId) throws Exception {
        UploadRecord ur = UploadRecordDAO.findById(uploadRecordId);
        if (ur != null && ((loggedInUser.getUser().isAdministrator() && ur.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && ur.getUserId() == loggedInUser.getUser().getId()))) {
            if (ur.getTempPath() != null && !ur.getTempPath().isEmpty()) {
                Path path = Paths.get(ur.getTempPath());
                if (!Files.isDirectory(path) && Files.exists(path)) {
                    Files.delete(path);
                }
            }
            UploadRecordDAO.removeById(ur);
        }
    }

    // a little inconsistent but it will be called using a database object, no point looking up again
    public static boolean isDatabaseLoaded(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).isDatabaseLoaded(database.getPersistenceName());
        }
        return false;
    }

    private static int getNameCountWithBasicSecurity(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getNameCount(database.getPersistenceName());
        }
        return -1;
    }

    private static int getValueCountWithBasicSecurity(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getValueCount(database.getPersistenceName());
        }
        return -1;
    }

    private static String getMostRecentProvenance(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getMostRecentProvenance(database.getPersistenceName());
        }
        return null;
    }

    public static void setBanner(ModelMap model, LoggedInUser loggedInUser) {
        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
        String bannerColor = business.getBannerColor();
        if (bannerColor == null || bannerColor.length() == 0) bannerColor = "#F58030";
        String logo = business.getLogo();
        if (logo == null || logo.length() == 0) logo = "logo_alt.png";
        model.addAttribute("bannerColor", bannerColor);
        model.addAttribute("logo", logo);

    }

    public static void toggleAutoBackupWithBasicSecurity(LoggedInUser loggedInUser, int databaseId) {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getUserId() == loggedInUser.getUser().getId()))) { // business ID is user ID?? woah! todo
            db.setAutoBackup(!db.getAutoBackup());
            DatabaseDAO.store(db);
        }
    }

}