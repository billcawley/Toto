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
import org.springframework.beans.factory.annotation.Autowired;
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
 */
public class AdminService {

    //private static final Logger logger = Logger.getLogger(AdminService.class);

    @Autowired
    private BusinessDAO businessDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private DatabaseServerDAO databaseServerDAO;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private LoginRecordDAO loginRecordDAO;
    @Autowired
    private OpenDatabaseDAO openDatabaseDAO;
    @Autowired
    private PermissionDAO permissionDAO;
    @Autowired
    private UploadRecordDAO uploadRecordDAO;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private DatabaseReportLinkDAO databaseReportLinkDAO;
    @Autowired
    private ReportScheduleDAO reportScheduleDAO;
    @Autowired
    private RMIClient rmiClient;
    @Autowired
    private SpreadsheetService spreadsheetService;

    // after uncommenting to use it won't requite the activation email initially

    public void registerBusiness(final String email
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
        final Business existing = businessDAO.findByName(businessName);
        if (existing != null) {
            throw new Exception(businessName + " already registered");
        }
        User existingUser = userDao.findByEmail(email);
        if (existingUser != null) {
            if (existingUser.getEndDate().isAfter(LocalDateTime.now())) { // active user
                throw new Exception(email + " already registered");
            } else {
                userDao.removeById(existingUser); // it will be created again
            }
        }
        businessDAO.store(business);
        final String salt = shaHash(System.currentTimeMillis() + "salt");
        final User user = new User(0, LocalDateTime.now().plusYears(30), business.getId(), email, userName, User.STATUS_ADMINISTRATOR, encrypt(password, salt), salt, "register business");
        userDao.store(user);
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

    private String getSQLDatabaseName(final LoggedInUser loggedInUser, final String databaseName) {
        //TODO  Check name below is unique.
        return getBusinessPrefix(loggedInUser) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
    }

    private String getBusinessPrefix(final LoggedInUser loggedInUser) {
        Business b = businessDAO.findById(loggedInUser.getUser().getBusinessId());
        return b != null ? (b.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "") : null;
    }

    // ok in new report/database server split creating a database needs distinct bits

    public void createDatabase(final String databaseName, String databaseType, final LoggedInUser loggedInUser, DatabaseServer databaseServer) throws Exception {
        if (databaseType == null) {
            databaseType = "";
        }
        if (loggedInUser.getUser().isAdministrator()) {
            Database existing = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), databaseName);
            if (existing != null) {
                throw new Exception("That database already exists");
            }
            final String persistenceName = getSQLDatabaseName(loggedInUser, databaseName);
            final Business b = businessDAO.findById(loggedInUser.getUser().getBusinessId());
            if (b == null) {
                throw new Exception("That business does not exist");
            }
            final Database database = new Database(0, b.getId(), databaseName, persistenceName, databaseType, 0, 0, databaseServer.getId());
            databaseDAO.store(database);
            // will be over to the DB side
            rmiClient.getServerInterface(databaseServer.getIp()).createDatabase(database.getPersistenceName());
            loggedInUser.setDatabaseWithServer(databaseServer, database);
        } else {
            throw new Exception("Only administrators can create databases");
        }
    }

    public void emptyDatabase(DatabaseServer databaseServer, Database database) throws Exception {
        rmiClient.getServerInterface(databaseServer.getIp()).emptyDatabase(database.getPersistenceName());
    }

    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws Exception {
        rmiClient.getServerInterface(source.getServerIp()).copyDatabase(source, target, nameList, readLanguages);
    }


    public void createUser(final String email
            , final String userName
            , final LocalDateTime endDate
            , final String status
            , final String password
            , final LoggedInUser loggedInUser) throws Exception {
        if (loggedInUser.getUser().isAdministrator()) {
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, endDate, loggedInUser.getUser().getBusinessId(), email, userName, status, encrypt(password, salt), salt, loggedInUser.getUser().getEmail());
            userDao.store(user);
        } else {
            throw new Exception("error: you do not have permission to create a user");
        }
    }

    // now not dependant on selected database

    public void createUserPermission(final int reportId, final int userId, final int databaseId, final String readList, final String writeList, final LoggedInUser loggedInUser) throws Exception {
        User user = userDao.findById(userId);
        Database database = databaseDAO.findById(databaseId);
        if (loggedInUser.getUser().isAdministrator()
                && user != null && user.getBusinessId() == loggedInUser.getUser().getBusinessId()
                && database != null && database.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            final Permission permission = new Permission(0, reportId, userId, databaseId, readList, writeList);
            permissionDAO.store(permission);
        } else {
            throw new Exception("error: you do not have permission to perform this action");
        }
    }

    public String encrypt(final String password, String salt) throws Exception {
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

    public String shaHash(final String toHash) throws Exception {   // for making a password salt
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(toHash.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    public Business getBusinessById(int id) {
        return businessDAO.findById(id);
    }

    public List<Database> getDatabaseListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return databaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        return null;
    }

    public List<User> getUserListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return userDao.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        if (loggedInUser.getUser().isMaster()) {
            return userDao.findForBusinessIdAndCreatedBy(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getEmail());
        }
        return null;
    }

    public List<OnlineReport> getReportListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return onlineReportDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        return null;
    }


    public List<OnlineReport> getReportList(final LoggedInUser loggedInUser) {
        List<OnlineReport> reportList = new ArrayList<>();
        List<Database> databases = databaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        for (Database database : databases) {
            List<OnlineReport> reports = onlineReportDAO.findForDatabaseId(database.getId());
            for (OnlineReport report : reports) {
                report.setDatabase(database.getName());
            }
            reportList.addAll(reports);
        }
        // was setting the database name for each report, this will be irrelevant
        if (reportList.size() == 0) {
            OnlineReport notFound = new OnlineReport(0, LocalDateTime.now(), 0, "", "", "", "No reports found", "", "", OnlineReport.AZQUO_BOOK, true);
            reportList.add(notFound);
        }
        return reportList;
    }

    public List<ReportSchedule> getReportScheduleList(final LoggedInUser loggedInUser) {
        List<ReportSchedule> toReturn = new ArrayList<>();
        List<Database> databases = databaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        for (Database database : databases) {
            List<ReportSchedule> reportSchedules = reportScheduleDAO.findForDatabaseId(database.getId());
            toReturn.addAll(reportSchedules);

        }
        return toReturn;
    }

    public List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            List<UploadRecord> uploadRecords = uploadRecordDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<>();
            for (UploadRecord uploadRecord : uploadRecords) {
                String dbName = "";
                if (uploadRecord.getDatabaseId() > 0) {
                    Database database = databaseDAO.findById(uploadRecord.getDatabaseId());
                    if (database != null) {
                        dbName = database.getName();
                    }
                }
                String userName = "";
                if (uploadRecord.getUserId() > 0) {
                    User user = userDao.findById(uploadRecord.getUserId());
                    if (user != null) {
                        userName = user.getName();
                    }
                }
                uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, businessDAO.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName));
            }
            return uploadRecordsForDisplay;
        }
        return null;
    }

    public List<Permission.PermissionForDisplay> getDisplayPermissionList(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            List<Permission.PermissionForDisplay> permissions = new ArrayList<>();
            for (Permission permission : permissionDAO.findByBusinessId(loggedInUser.getUser().getBusinessId())) {
                OnlineReport onlineReport = onlineReportDAO.findById(permission.getReportId());
                if (onlineReport != null && onlineReport.getActive()) { // really an inactive report should be zapped, here for safety
                    permissions.add(new Permission.PermissionForDisplay(permission, databaseDAO, userDao));
                }
            }
            return permissions;
        }
        return null;
    }

    public void deleteUserById(int userId, LoggedInUser loggedInUser) {
        User user = userDao.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
            userDao.removeById(user);
            final List<Permission> forUserId = permissionDAO.findForUserId(userId);
            for (Permission permission : forUserId) {
                permissionDAO.removeById(permission);
            }
        }
    }

    public User getUserById(int userId, LoggedInUser loggedInUser) {
        User user = userDao.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
            return user;
        }
        return null;
    }

    public void deletePermissionById(int permissionId, LoggedInUser loggedInUser) {
        Permission permission = permissionDAO.findById(permissionId);
        User user = userDao.findById(permission.getUserId());
        if (loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
            permissionDAO.removeById(permission);
        }
    }

    public Permission getPermissionById(int permissionId, LoggedInUser loggedInUser) {
        Permission permission = permissionDAO.findById(permissionId);
        if (permission != null) {
            User user = userDao.findById(permission.getUserId());
            if (loggedInUser.getUser().getBusinessId() == user.getBusinessId()) {
                return permission;
            }
        }
        return null;
    }

    public Database getDatabaseById(int databaseId, LoggedInUser loggedInUser) {
        Database database = databaseDAO.findById(databaseId);
        if (database != null && loggedInUser.getUser().getBusinessId() == database.getBusinessId()) {
            return database;
        }
        return null;
    }

    public OnlineReport getReportById(int reportId, LoggedInUser loggedInUser) {
        OnlineReport onlineReport = onlineReportDAO.findById(reportId);
        if (onlineReport != null && loggedInUser.getUser().getBusinessId() == onlineReport.getBusinessId()) {
            return onlineReport;
        }
        return null;
    }

    public void removeReportById(LoggedInUser loggedInUser, int reportId) {
        OnlineReport onlineReport = onlineReportDAO.findById(reportId);
        if (onlineReport != null && onlineReport.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            String fullPath = spreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + onlineReport.getFilename();
            File file = new File(fullPath);
            if (file.exists()) {
                file.delete();
            }
            onlineReportDAO.removeById(onlineReport);
        }
    }

    // code adapted from spreadsheet service which it wilol be removed from
    public void removeDatabaseById(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            // note, used to delete choices for the reports for this DB, won't do this now as
            loginRecordDAO.removeForDatabaseId(db.getId());
            databaseReportLinkDAO.unLinkDatabase(databaseId);
            openDatabaseDAO.removeForDatabaseId(db.getId());
            permissionDAO.removeForDatabaseId(db.getId());
            uploadRecordDAO.removeForDatabaseId(db.getId());
            databaseDAO.removeById(db);
            rmiClient.getServerInterface(databaseServerDAO.findById(db.getDatabaseServerId()).getIp()).dropDatabase(db.getPersistenceName());
        }
    }

    public void emptyDatabaseById(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            rmiClient.getServerInterface(databaseServerDAO.findById(db.getDatabaseServerId()).getIp()).emptyDatabase(db.getPersistenceName());
        }
    }

    public void unloadDatabase(LoggedInUser loggedInUser, int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            rmiClient.getServerInterface(databaseServerDAO.findById(db.getDatabaseServerId()).getIp()).unloadDatabase(db.getPersistenceName());
        }
    }

    // a little inconsistent but it will be called using a database object, no point looking up again
    public boolean isDatabaseLoaded(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && database.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            return rmiClient.getServerInterface(databaseServerDAO.findById(database.getDatabaseServerId()).getIp()).isDatabaseLoaded(database.getPersistenceName());
        }
        return false;
    }

    public int getNameCount(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && database.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            return rmiClient.getServerInterface(databaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getNameCount(database.getPersistenceName());
        }
        return 0;
    }

    public int getValueCount(LoggedInUser loggedInUser, Database database) throws Exception {
        if (database != null && database.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            return rmiClient.getServerInterface(databaseServerDAO.findById(database.getDatabaseServerId()).getIp()).getValueCount(database.getPersistenceName());
        }
        return 0;
    }
}
