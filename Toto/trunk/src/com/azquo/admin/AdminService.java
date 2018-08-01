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
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
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
import java.util.stream.Collectors;

import static com.azquo.dataimport.ImportService.dbPath;
import static com.azquo.dataimport.ImportService.onlineReportsDir;

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
 * <p>
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
        final Business business = new Business(0, businessName, bd, null,null);
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
        final User user = new User(0, LocalDateTime.now().plusYears(30), business.getId(), email, userName, User.STATUS_ADMINISTRATOR, encrypt(password, salt), salt, "register business", 0, 0);// Admin with
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

    public static String getSQLDatabaseName(final DatabaseServer databaseServer, Business b, final String databaseName) throws Exception{
        StringBuilder candidate = new StringBuilder(getBusinessPrefix(b) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase());
        while (RMIClient.getServerInterface(databaseServer.getIp()).databaseWithNameExists(candidate.toString())){
            candidate.append("Z");
        }
        return candidate.toString();
    }

    private static String getBusinessPrefix(Business b) {
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
            final Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
            if (b == null) {
                throw new Exception("That business does not exist");
            }
            final String persistenceName = getSQLDatabaseName(databaseServer, b, databaseName);
            final Database database = new Database(0, b.getId(), loggedInUser.getUser().getId(), databaseName, persistenceName, databaseType, 0, 0, databaseServer.getId(), null);
            DatabaseDAO.store(database);
            // will be over to the DB side
            RMIClient.getServerInterface(databaseServer.getIp()).createDatabase(database.getPersistenceName());
            loggedInUser.setDatabaseWithServer(databaseServer, database);
        } else {
            throw new Exception("Only administrators can create databases");
        }
    }

    // used by magento, always the currently logged in db
    public static void emptyDatabase(LoggedInUser loggedInUser) throws Exception {
        DatabaseServer databaseServer = DatabaseServerDAO.findById(loggedInUser.getDatabase().getDatabaseServerId());
        RMIClient.getServerInterface(databaseServer.getIp()).emptyDatabase(loggedInUser.getDatabase().getPersistenceName());
        checkDBSetupFile(loggedInUser, loggedInUser.getDatabase());
    }

    public static void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, String user) throws Exception {
        RMIClient.getServerInterface(source.getServerIp()).copyDatabase(source, target, nameList, user);
    }

    public static void copyDatabase(LoggedInUser loggedInUser, Database source, String newName) throws Exception {
        final Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
        if (b == null) {
            throw new Exception("That business does not exist");
        }
        final String persistenceName = getSQLDatabaseName(DatabaseServerDAO.findById(source.getDatabaseServerId()), b, newName);
        final Database database = new Database(0, source.getBusinessId(), loggedInUser.getUser().getId(), newName, persistenceName, source.getDatabaseType(), source.getNameCount(), source.getValueCount(), source.getDatabaseServerId(), null);
        DatabaseDAO.store(database);
        DatabaseServer server = DatabaseServerDAO.findById(database.getDatabaseServerId());
        RMIClient.getServerInterface(server.getIp()).copyDatabase(source.getPersistenceName(), database.getPersistenceName());
    }

    // added for the business copy function - copy a database to another business. Currently same db server, might change that later. I pass the new user as it's the one attached to the enw business
    public static Database copyDatabase(Database source, User newUser) throws Exception {
        Business b = BusinessDAO.findById(newUser.getBusinessId());
        final String persistenceName = getSQLDatabaseName(DatabaseServerDAO.findById(source.getDatabaseServerId()), b, source.getName()); // we want the persistence name based off old db name and server but with the new business name
        final Database database = new Database(0, newUser.getBusinessId(), newUser.getId(), source.getName(), persistenceName, source.getDatabaseType(), source.getNameCount(), source.getValueCount(), source.getDatabaseServerId(), null);
        DatabaseDAO.store(database);
        DatabaseServer server = DatabaseServerDAO.findById(database.getDatabaseServerId());
        RMIClient.getServerInterface(server.getIp()).copyDatabase(source.getPersistenceName(), database.getPersistenceName());
        return database;
    }

    // added for the business copy function - copy a report to another business
    public static OnlineReport copyReport(LoggedInUser loggedInUser, OnlineReport source, User newUser) throws Exception {
        Business b = BusinessDAO.findById(newUser.getBusinessId());
        String businessDirectory = (b.getBusinessName() + "                    ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
           OnlineReport newReport = new OnlineReport(0, LocalDateTime.now(), b.getId(), newUser.getId(), source.getDatabase(), source.getReportName(), source.getFilename(), "", null);
        OnlineReportDAO.store(newReport); // store before or.getFilenameForDisk() or the id will be wrong!
        // new java 7 call, much better!
        Files.copy(Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + source.getFilenameForDisk())
                , Paths.get(SpreadsheetService.getHomeDir() + dbPath + businessDirectory + onlineReportsDir + newReport.getFilenameForDisk()));
        // do the links after
        return newReport;
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
    public static List<Database> getDatabaseListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        if (loggedInUser.getUser().isDeveloper()) {
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
        if (!loggedInUser.getUser().isAdministrator()&&!loggedInUser.getUser().isDeveloper()) {
            int reportId = loggedInUser.getUser().getReportId();
            int dbId = loggedInUser.getUser().getDatabaseId();
            if (reportId > 0 && dbId > 0) {
                OnlineReport or = OnlineReportDAO.findById(reportId);
                if (or!=null) {
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
            } else if (loggedInUser.getUser().isDeveloper() && database.getUserId() == loggedInUser.getUser().getId()) { // develope constrained to their own reports
                List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(database.getId());
                for (OnlineReport report : reports) {
                    report.setDatabase(database.getName());
                }
                reportList.addAll(reports);
            }
        }
          // was setting the database name for each report, this will be irrelevant
        if (reportList.size() == 0) {

            OnlineReport notFound = new OnlineReport(0, LocalDateTime.now(), 0, loggedInUser.getUser().getId(), "", "No reports found", "", "",null);
            reportList.add(notFound);
        } else {
            for (OnlineReport or : reportList){
                if (or.getUserId() != 0 && or.getUserId() != loggedInUser.getUser().getId()){
                    User otherUser = UserDAO.findById(or.getUserId());
                    or.setReportName(or.getReportName() + " uploaded by " + (otherUser != null ? otherUser.getName() : "?")); // I'm assuming the report isn't saved after - just for
                }
            }
        }
        reportList.sort((o1, o2) -> {
            // adding isempty here as empty is the same as null for our sorting purposes
            String o1Explanation = o1.getExplanation();
            if (o1Explanation==null|| o1Explanation.isEmpty()) o1Explanation = "zzz";
            String o2Explanation = o2.getExplanation();
            if (o2Explanation==null || o2Explanation.isEmpty()) o2Explanation = "zzz";
            return (o1.getDatabase() + o1Explanation).compareTo(o2.getDatabase()+o2Explanation);
        });
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

    public static List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusiness(final LoggedInUser loggedInUser, String fileSearch) {
        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
            List<UploadRecord> uploadRecords = UploadRecordDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()); // limited to 10k for the mo
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<>();
            int count = 0;
            Set<Integer> databaseIdsWithSetupsAlready = new HashSet<>();
            for (UploadRecord uploadRecord : uploadRecords) {
                if (fileSearch == null || fileSearch.equals("") || (uploadRecord.getFileName().toLowerCase().contains(fileSearch.toLowerCase()))){
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
                        boolean setup = false;
                        if ("setup".equals(uploadRecord.getFileType()) && !databaseIdsWithSetupsAlready.contains(uploadRecord.getDatabaseId())){
                            databaseIdsWithSetupsAlready.add(uploadRecord.getDatabaseId());
                            setup = true;
                        }
                        uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, BusinessDAO.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName, downloadable, setup));
                        if (count > 100){
                            break;
                        }
                    }
                }
            }
            return uploadRecordsForDisplay;
        }
        return null;
    }

    public static void deleteUserById(int userId) {
        User user = UserDAO.findById(userId);
        UserDAO.removeById(user);
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

    public static void removeReportById(LoggedInUser loggedInUser, int reportId)  {
        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
        if (onlineReport != null && ((loggedInUser.getUser().isAdministrator() && onlineReport.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && onlineReport.getBusinessId() == loggedInUser.getUser().getId()))) {
            Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + onlineReportsDir + onlineReport.getFilenameForDisk());
            if (Files.exists(fullPath) || Files.isDirectory(fullPath)){
                try {
                    Files.deleteIfExists(fullPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            OnlineReportDAO.removeById(onlineReport);
            // and the schedules
            List<ReportSchedule> reportSchedules = ReportScheduleDAO.findForReportId(reportId);
            for (ReportSchedule reportSchedule : reportSchedules){
                ReportScheduleDAO.removeById(reportSchedule);
            }
        }
    }

    // code adapted from spreadsheet service which it wilol be removed from
    public static void removeDatabaseById(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getBusinessId() == loggedInUser.getUser().getId()))) {
            // note, used to delete choices for the reports for this DB, won't do this now as
            // before unlinking get the reports to see if they need zapping
            final List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(db.getId());
            DatabaseReportLinkDAO.unLinkDatabase(databaseId);
            for (OnlineReport or : reports) {
                if (DatabaseReportLinkDAO.getDatabaseIdsForReportId(or.getId()).isEmpty()) { // then this report no longer has any databases
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
            Database oldDb = loggedInUser.getDatabase();
            LoginService.switchDatabase(loggedInUser, db);
            checkDBSetupFile(loggedInUser,db);
            updateNameAndValueCounts(loggedInUser, db);
            LoginService.switchDatabase(loggedInUser, oldDb);
        }
    }

    public static void updateNameAndValueCounts(LoggedInUser loggedInUser, Database database) throws Exception {
        database.setNameCount(AdminService.getNameCount(loggedInUser, database));
        database.setValueCount(AdminService.getValueCount(loggedInUser, database));
        DatabaseDAO.store(database);
    }

    private static void checkDBSetupFile(LoggedInUser loggedInUser, Database db) throws Exception{
//        String nearlyfullPath = SpreadsheetService.getHomeDir() +  ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.databaseSetupSheetsDir + ;
        // todo - new apis . . .
        Path setupFile = null;
        Path dir = Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + ImportService.databaseSetupSheetsDir);
        if (Files.isDirectory(dir)){
            for (Path path : Files.list(dir).collect(Collectors.toList())){
                if (path != null &&  path.getFileName() != null && db != null && path.getFileName().toString().startsWith("Setup" + db.getName()) // note the toString here!
                        && (setupFile == null || Files.getLastModifiedTime(path).toMillis() > Files.getLastModifiedTime(setupFile).toMillis())){
                    setupFile = path;
                }
            }
        }
        System.out.println("setup file : " + setupFile);

//        setupFile.getFileName().toString();
        if (setupFile != null && setupFile.getFileName() != null){
            ImportService.importTheFile(loggedInUser,
                    setupFile.getFileName() + ""
                    , setupFile.toString(), false);
        }
    }

    public static void checkDatabaseById(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getBusinessId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).checkDatabase(db.getPersistenceName());
        }
    }

    public static void unloadDatabase(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = DatabaseDAO.findById(databaseId);
        if (db != null && ((loggedInUser.getUser().isAdministrator() && db.getBusinessId() == loggedInUser.getUser().getBusinessId())
                || (loggedInUser.getUser().isDeveloper() && db.getBusinessId() == loggedInUser.getUser().getId()))) {
            RMIClient.getServerInterface(DatabaseServerDAO.findById(db.getDatabaseServerId()).getIp()).unloadDatabase(db.getPersistenceName());
        }
    }

    public static void deleteUploadRecord(LoggedInUser loggedInUser, int uploadRecordId) throws Exception {
        UploadRecord ur = UploadRecordDAO.findById(uploadRecordId);
        if (ur != null && ur.getBusinessId() == loggedInUser.getUser().getBusinessId()){
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

    public static void setBanner(ModelMap model, LoggedInUser loggedInUser){
        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
        String bannerColor = business.getBannerColor();
        if (bannerColor==null || bannerColor.length()==0) bannerColor = "#F58030";
        String logo = business.getLogo();
        if (logo==null || logo.length()==0) logo = "logo_alt.png";
        model.addAttribute("bannerColor", bannerColor);
        model.addAttribute("logo", logo);

    }

}