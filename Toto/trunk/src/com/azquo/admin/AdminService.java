package com.azquo.admin;

import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import org.springframework.beans.factory.annotation.Autowired;
import sun.misc.BASE64Encoder;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Used for admin functions. Register, validate etc
 * pretty simple with calls to the vanilla admin dao classes
 * <p/>
 * some functions stopped being used when the admin was moved from excel to azquo book. WHen it's moved back then they may be used again
 */
public class AdminService {

    //private static final Logger logger = Logger.getLogger(AdminService.class);

    @Autowired
    private BusinessDAO businessDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
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
    private RMIClient rmiClient;

    // from the old excel sheet (I mean register in Excel, not a browser!) keeping for the mo as it may be useful in the new admin or for Azquo.com

/*    public void registerBusiness(final String email
            , final String userName
            , final String password
            , final String businessName
            , final String address1
            , final String address2
            , final String address3
            , final String address4
            , final String postcode
            , final String telephone) throws Exception {
        // we need to check for existing businesses
        final String key = shaHash(System.currentTimeMillis() + "");
        final Business.BusinessDetails bd = new Business.BusinessDetails(address1, address2, address3, address4, postcode, telephone, "website???", key);
        final Business business = new Business(0, LocalDateTime.now(), LocalDateTime.now(), businessName, 0, bd);
        final Business existing = businessDAO.findByName(businessName);
        if (existing != null) { // ok new criteria, overwrite if the business was not already key validated
            if (existing.getEndDate().isAfter(LocalDateTime.now())) {
                throw new Exception(businessName + " already registered");
            } else {
                businessDAO.removeById(existing); // it will be created again
            }
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
        final User user = new User(0, LocalDateTime.now(), LocalDateTime.now(), business.getId(), email, userName, "administrator", encrypt(password, salt), salt);
        userDao.store(user);
        azquoMailer.sendEMail(user.getEmail()
                , user.getName()
                , "Azquo account activation for " + businessName
                , "<html>Dear " + user.getName() + "<br/><br/>Welcome to Azquo!<br/><br/>Your account key is : " + key + "</html>");
        // copy for us
        azquoMailer.sendEMail("info@azquo.com"
                , "Azquo Support"
                , "Azquo account activation for " + businessName
                , "<html>Dear " + user.getName() + "<br/><br/>Welcome to Azquo!<br/><br/>Your account key is : " + key + "</html>");
    }

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

    public String getSQLDatabaseName(final LoggedInUser loggedInUser, final String databaseName) {
        //TODO  Check name below is unique.
        return getBusinessPrefix(loggedInUser) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
    }

    public String getBusinessPrefix(final LoggedInUser loggedInUser) {
        Business b = businessDAO.findById(loggedInUser.getUser().getBusinessId());
        return (b.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "");
    }

    // ok in new report/datavase server split creating a database needs distinct bits

    public void createDatabase(final String databaseName, final String databaseType, final LoggedInUser loggedInUser) throws Exception {
        if (loggedInUser.getUser().isAdministrator()) {
            Database existing = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), databaseName);
            if (existing != null) {
                throw new Exception("That database already exists");
            }
            final String mysqlName = getSQLDatabaseName(loggedInUser, databaseName);
            final Business b = businessDAO.findById(loggedInUser.getUser().getBusinessId());
            final Database database = new Database(0, LocalDateTime.now(), LocalDateTime.now().plusYears(10), b.getId(), databaseName, mysqlName, databaseType, 0, 0);
            databaseDAO.store(database);
            // will be over to the DB side
            createDatabase(database.getMySQLName());
            loggedInUser.setDatabase(database);
        } else {
            throw new Exception("Only administrators can create databases");
        }
    }

    // will be for the database side
    public void emptyDatabase(String mysqlName) throws Exception {
        rmiClient.getServerInterface().emptyDatabase(mysqlName);
    }

    public void dropDatabase(String mysqlName) throws Exception {
        rmiClient.getServerInterface().dropDatabase(mysqlName);
    }

    private void createDatabase(final String mysqlName) throws Exception {
        rmiClient.getServerInterface().createDatabase(mysqlName);
    }

    public void createUser(final String email
            , final String userName
            , final LocalDateTime endDate
            , final String status
            , final String password
            , final LoggedInUser loggedInUser) throws Exception {
        if (loggedInUser.getUser().isAdministrator()) {
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, LocalDateTime.now(), endDate, loggedInUser.getUser().getBusinessId(), email, userName, status, encrypt(password, salt), salt);
            userDao.store(user);
        } else {
            throw new Exception("error: you do not have permission to create a user");
        }
    }

    // now not dependant on selected database

    public void createUserPermission(final int userId, final int databaseId, final LocalDateTime startDate, final LocalDateTime endDate, final String readList, final String writeList, final LoggedInUser loggedInUser) throws Exception {
        User user = userDao.findById(userId);
        Database database = databaseDAO.findById(databaseId);
        if (loggedInUser.getUser().isAdministrator()
                && user != null && user.getBusinessId() == loggedInUser.getUser().getBusinessId()
                && database != null && database.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            final Permission permission = new Permission(0, startDate, endDate, userId, databaseId, readList, writeList);
            permissionDAO.store(permission);
        } else {
            throw new Exception("error: you do not have permission to perform this action");
        }
    }

    //variation on a function I've used before

    /*                String salt = PasswordUtils.shaHash(System.currentTimeMillis() + "salt");
    v.setPasswordSalt(salt);
    v.setPassword(PasswordUtils.encrypt(password, salt)); // new better encryption . . .*/


    public String encrypt(final String password, String salt) throws Exception {
        // WARNING! DO NOT MODIFY THE reference to "scapbadopbebedop"  bit in the code below or it will stop working! This is the extra bit on the salt or the number of hash cycles! stay at 3296
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

    public List<Database> getDatabaseListForBusiness(final Business business) {
        return databaseDAO.findForBusinessId(business.getId());
    }

    public List<User> getUserListForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return userDao.findForBusinessId(loggedInUser.getUser().getBusinessId());
        }
        return null;
    }


    public List<OnlineReport> getReportList(final LoggedInUser loggedInUser) {
        List<OnlineReport> reportList;
        if (loggedInUser.getUser().isAdministrator()) {
            reportList = onlineReportDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
        } else {
            // there was a look here based on splitting te user status by , but it made no sense, this happens inside this function
            reportList = onlineReportDAO.findForBusinessIdAndUserStatus(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getStatus());
        }
        if (reportList != null) {
            for (OnlineReport onlineReport : reportList) {
                Database database = databaseDAO.findById(onlineReport.getDatabaseId());
                if (database != null) {//in case database has been deleted without deleting the report reference.....
                    onlineReport.setDatabase(database.getName());
                }
            }
        } else {
            OnlineReport notFound = new OnlineReport(0, LocalDateTime.now(), 0, 0, "", "", "No reports found", "", "", "", "", OnlineReport.AZQUO_BOOK, true); // default to old for the moment
            reportList.add(notFound);
        }
        return reportList;
    }

    public List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusiness(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            List<UploadRecord> uploadRecords = uploadRecordDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<UploadRecord.UploadRecordForDisplay>();
            for (UploadRecord uploadRecord : uploadRecords) {
                String dbName = "";
                if (uploadRecord.getDatabaseId() > 0) {
                    dbName = databaseDAO.findById(uploadRecord.getDatabaseId()).getName();
                }
                String userName = "";
                if (uploadRecord.getUserId() > 0) {
                    userName = userDao.findById(uploadRecord.getUserId()).getName();
                }
                uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, businessDAO.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName));
            }
            return uploadRecordsForDisplay;
        }
        return null;
    }

    public List<Permission> getPermissionList(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            return permissionDAO.findByBusinessId(loggedInUser.getUser().getBusinessId());
        }
        return null;
    }

    public List<Permission.PermissionForDisplay> getDisplayPermissionList(final LoggedInUser loggedInUser) {
        if (loggedInUser.getUser().isAdministrator()) {
            List<Permission.PermissionForDisplay> permissions = new ArrayList<Permission.PermissionForDisplay>();
            for (Permission permission : permissionDAO.findByBusinessId(loggedInUser.getUser().getBusinessId())){
                permissions.add(new Permission.PermissionForDisplay(permission, databaseDAO,userDao));
            }
            return permissions;
        }
        return null;
    }

    // will be purely DB side

    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws Exception {
        //todo proxy through
    }

    public void storeReport(OnlineReport report){
        onlineReportDAO.store(report);
    }

    public void deleteUserById(int userId, LoggedInUser loggedInUser) {
        User user = userDao.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()){
            userDao.removeById(user);
        }
    }

    public User getUserById(int userId, LoggedInUser loggedInUser) {
        User user = userDao.findById(userId);
        if (user != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()){
            return user;
        }
        return null;
    }

    public void storeUser(User user){
        userDao.store(user);
    }

    public void deletePermissionById(int permissionId, LoggedInUser loggedInUser) {
        Permission permission = permissionDAO.findById(permissionId);
        User user = userDao.findById(permission.getUserId());
        if (permission != null && loggedInUser.getUser().getBusinessId() == user.getBusinessId()){
            permissionDAO.removeById(permission);
        }
    }

    public Permission getPermissionById(int permissionId, LoggedInUser loggedInUser) {
        Permission permission = permissionDAO.findById(permissionId);
        if (permission != null){
            User user = userDao.findById(permission.getUserId());
            if (loggedInUser.getUser().getBusinessId() == user.getBusinessId()){
                return permission;
            }
        }
        return null;
    }

    public void storePermission(Permission permission){
        permissionDAO.store(permission);
    }

    public Database getDatabaseById(int databaseId, LoggedInUser loggedInUser) {
        Database database = databaseDAO.findById(databaseId);
        if (database != null && loggedInUser.getUser().getBusinessId() == database.getBusinessId()){
            return database;
        }
        return null;
    }

    // code adapted from spreadsheet service which it wilol be removed from
    public void removeDatabaseById(LoggedInUser loggedInUser,  int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            // note, used to delete choices for the reports for this DB, won't do this now as
            loginRecordDAO.removeForDatabaseId(db.getId());
            onlineReportDAO.removeForDatabaseId(db.getId());
            openDatabaseDAO.removeForDatabaseId(db.getId());
            permissionDAO.removeForDatabaseId(db.getId());
            uploadRecordDAO.removeForDatabaseId(db.getId());
            String mySQLName = db.getMySQLName();
            databaseDAO.removeById(db);
            dropDatabase(mySQLName);
        }
    }

    public void emptyDatabaseById(LoggedInUser loggedInUser,  int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            String mySQLName = db.getMySQLName();
            emptyDatabase(mySQLName);
        }
    }

    public void unloadDatabase(LoggedInUser loggedInUser,  int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            rmiClient.getServerInterface().unloadDatabase(db.getMySQLName());
        }
    }

    // a little inconsistent but it will be called using a database object, no point looking up again
    public boolean isDatabaseLoaded(LoggedInUser loggedInUser,  Database database) throws Exception {
        if (database != null && database.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            return rmiClient.getServerInterface().isDatabaseLoaded(database.getMySQLName());
        }
        return false;
    }

}
