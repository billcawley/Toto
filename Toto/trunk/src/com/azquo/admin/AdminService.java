package com.azquo.admin;

import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.*;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import sun.misc.BASE64Encoder;

import java.io.File;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Used for admin functions. Register, validate etc
 * pretty simple with calls to the vanilla admin dao classes
 * <p/>
 *
 * Note - most calls deal with security internally (isAdministrator) but not all, this really should be consistent
 */
public class AdminService {

    //private static final Logger logger = Logger.getLogger(AdminService.class);

    // after uncommenting to use it won't requite the activation email initially

    public static void registerBusiness(final String email
            , final String userName
            , final String password
            , final String businessName
            , final String address1
            , final String address2
            , final String address3
            , final String address4
            , final String postcode
            , final String telephone, final String website) throws Exception {
        // we need to check for existing businesses
        final String key = shaHash(System.currentTimeMillis() + "");
        final Business.BusinessDetails bd = new Business.BusinessDetails(address1, address2, address3, address4, postcode, telephone, website, key);
        final Business business = new Business(0, businessName, bd);
        final Business existing = BusinessDAO.findByName(businessName);
        if (existing != null) {
            throw new Exception(businessName + " already registered");
        }
        User existingUser = UserDAO.findByEmail(email);
        if (existingUser != null) {
            if (existingUser.getEndDate().isAfter(LocalDateTime.now())) { // active user
                throw new Exception(email + " already registered");
            } else {
                UserDAO.removeById(existingUser); // it will be created again
            }
        }
        BusinessDAO.store(business);
        final String salt = shaHash(System.currentTimeMillis() + "salt");
        final User user = new User(0, LocalDateTime.now().plusYears(30), business.getId(), email, userName, User.STATUS_ADMINISTRATOR, encrypt(password, salt), salt, "register business", 0,0);// Admin with
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

    private static String getSQLDatabaseName(final LoggedInUser loggedInUser, final String databaseName) {
        //TODO  Check name below is unique.
        return getBusinessPrefix(loggedInUser) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
    }

    private static String getBusinessPrefix(final LoggedInUser loggedInUser) {
        Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
        return b != null ? (b.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "") : null;
    }

    // ok in new report/database server split creating a database needs distinct bits

    public static void createDatabase(final String databaseName, String databaseType, final LoggedInUser loggedInUser, DatabaseServer databaseServer) throws Exception {
        if (databaseType == null) {
            databaseType = "";
        }
        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
            // force a developer prefix?
            Database existing = DatabaseDAO.findForNameAndBusinessId(databaseName, loggedInUser.getUser().getBusinessId());
            if (existing != null) {
                throw new Exception("That database already exists");
            }
            final String persistenceName = getSQLDatabaseName(loggedInUser, databaseName);
            final Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
            if (b == null) {
                throw new Exception("That business does not exist");
            }
            final Database database = new Database(0, b.getId(), loggedInUser.getUser().getId(), databaseName, persistenceName, databaseType, 0, 0, databaseServer.getId());
            DatabaseDAO.store(database);
            // will be over to the DB side
            RMIClient.getServerInterface(databaseServer.getIp()).createDatabase(database.getPersistenceName());
            loggedInUser.setDatabaseWithServer(databaseServer, database);
        } else {
            throw new Exception("Only administrators can create databases");
        }
    }

    public static void emptyDatabase(DatabaseServer databaseServer, Database database) throws Exception {
        RMIClient.getServerInterface(databaseServer.getIp()).emptyDatabase(database.getPersistenceName());
    }

    public static void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws Exception {
        RMIClient.getServerInterface(source.getServerIp()).copyDatabase(source, target, nameList, readLanguages);
    }


    public static void createUser(final String email
            , final String userName
            , final LocalDateTime endDate
            , final String status
            , final String password
            , final LoggedInUser loggedInUser
    , int databaseId
    , int userId) throws Exception {
        if (loggedInUser.getUser().isAdministrator()) {
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, endDate, loggedInUser.getUser().getBusinessId(), email, userName, status, encrypt(password, salt), salt, loggedInUser.getUser().getEmail(), databaseId, userId);
            UserDAO.store(user);
        } else {
            throw new Exception("error: you do not have permission to create a user");
        }
    }

    public static String encrypt(final String password, String salt) throws Exception {
        // WARNING! DO NOT MODIFY THE reference to "scapbadopbebedop"  bit in the code below or existing passwords will stop working! This is the extra bit on the salt or the number of hash cycles! stay at 3296
        salt += "scapbadopbebedop";
        byte[] passBytes = password.getBytes();
        byte[] saltBytes = salt.getBytes();
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
        return (new BASE64Encoder()).encode(passBytes);
    }

    public static String shaHash(final String toHash) throws Exception {   // for making a password salt
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(toHash.getBytes());
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
    public static List<Database> getDatabaseListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        if (loggedInUser.getUser().isDeveloper()){
            return DatabaseDAO.findForUserId(loggedInUser.getUser().getId());
        }
        return null;
    }

    public static List<User> getUserListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return UserDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        if (loggedInUser.getUser().isMaster()) {
            return UserDAO.findForBusinessIdAndCreatedBy(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getEmail());
        }
        return null;
    }

    public static List<OnlineReport> getReportList(final LoggedInUser loggedInUser) {
        List<OnlineReport> reportList = new ArrayList<>();
        List<Database> databases = DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        for (Database database : databases) {
            if (loggedInUser.getUser().isAdministrator()){// admin gets all
                List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(database.getId());
                for (OnlineReport report : reports) {
                    report.setDatabase(database.getName());
                }
                reportList.addAll(reports);
            } else if (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()){ // develope constrained to their own reports
                List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(database.getId());
                for (OnlineReport report : reports) {
                    report.setDatabase(database.getName());
                }
                reportList.addAll(reports);
            }
        }
        // was setting the database name for each report, this will be irrelevant
        if (reportList.size() == 0) {
            OnlineReport notFound = new OnlineReport(0, LocalDateTime.now(), 0,0, "", "No reports found", "", "");
            reportList.add(notFound);
        }
        return reportList;
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

    public static List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
            List<UploadRecord> uploadRecords = UploadRecordDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<>();
            for (UploadRecord uploadRecord : uploadRecords) {
                if (loggedInUser.getUser().isAdministrator() || uploadRecord.getUserId() == loggedInUser.getUser().getId()){ // admin is all, developer is just theirs
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
                    if (uploadRecord.getTempPath() != null && !uploadRecord.getTempPath().isEmpty()){
                        File test = new File(uploadRecord.getTempPath());
                        if (test.exists() && test.isFile()){
                            downloadable = true;
                        }
                    }
                    uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, BusinessDAO.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName, downloadable));
                }
            }
            return uploadRecordsForDisplay;
        }
        return null;
    }

    public static void deleteUserById(int userId, LoggedInUser loggedInUser) {
        User user = UserDAO.findById(userId);
    }

    public static User getUserById(int userId, LoggedInUser loggedInUser) {
        User user = UserDAO.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
            return user;
        }
        return null;
    }

    public static Database getDatabaseById(int databaseId, LoggedInUser loggedInUser) {
        Database database = DatabaseDAO.findById(databaseId);
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getBusinessId() == loggedInUser.getUser().getId()))) {
            return database;
        }
        return null;
    }

    public static OnlineReport getReportById(int reportId, LoggedInUser loggedInUser) {
        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
        if (onlineReport != null && ((loggedInUser.getUser().isAdministrator() && onlineReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && onlineReport.getBusinessId() == loggedInUser.getUser().getId()))) {
            return onlineReport;
        }
        return null;
    }

    public static void removeReportById(LoggedInUser loggedInUser, int reportId) {
        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
        if (onlineReport != null && ((loggedInUser.getUser().isAdministrator() && onlineReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && onlineReport.getBusinessId() == loggedInUser.getUser().getId()))) {
            String fullPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + onlineReport.getFilenameForDisk();
            File file = new File(fullPath);
            if (file.exists()) {
                file.delete();
            }
            OnlineReportDAO.removeById(onlineReport);
        }
    }

    // code adapted from spreadsheet service which it wilol be removed from
    public static void removeDatabaseById(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getBusinessId() == loggedInUser.getUser().getId()))) {
            // note, used to delete choices for the reports for this DB, won't do this now as
            LoginRecordDAO.removeForDatabaseId(db.getId());
            // before unlinking get the reports to see if they need zapping
            final List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(db.getId());
            DatabaseReportLinkDAO.unLinkDatabase(databaseId);
            for (OnlineReport or : reports){
                if (DatabaseReportLinkDAO.getDatabaseIdsForReportId(or.getId()).isEmpty()){ // then this report no longer has any databases
                    removeReportById(loggedInUser, or.getId());
                }
            }
            OpenDatabaseDAO.removeForDatabaseId(db.getId());
            UploadRecordDAO.removeForDatabaseId(db.getId());
            DatabaseDAO.removeById(db);
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).dropDatabase(db.getPersistenceName());
        }
    }

    public static void emptyDatabaseById(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getBusinessId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).emptyDatabase(db.getPersistenceName());
        }
    }

    public static void unloadDatabase(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getBusinessId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).unloadDatabase(db.getPersistenceName());
        }
    }

    // a little inconsistent but it will be called using a database object, no point looking up again
    public static boolean isDatabaseLoaded(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getBusinessId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).isDatabaseLoaded(database.getPersistenceName());
        }
        return false;
    }

    public static int getNameCount(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getBusinessId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getNameCount(database.getPersistenceName());
        }
        return 0;
    }

    public static int getValueCount(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && ((loggedInUser.getUser().isAdministrator() && database.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && database.getBusinessId() == loggedInUser.getUser().getId()))) {
            return RMIClient.getServerInterface(DatabaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getValueCount(database.getPersistenceName());
        }
        return 0;
    }
}
