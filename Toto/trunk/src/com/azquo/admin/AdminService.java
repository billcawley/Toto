package com.azquo.admin;

import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.memorydb.core.MemoryDBManager;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.spreadsheet.LoggedInConnection;
import com.azquo.spreadsheet.LoginService;
import com.azquo.util.AzquoMailer;
import org.springframework.beans.factory.annotation.Autowired;
import sun.misc.BASE64Encoder;

import java.security.MessageDigest;
import java.time.LocalDateTime;
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
    MySQLDatabaseManager mySQLDatabaseManager;
    @Autowired
    private AzquoMailer azquoMailer;
    @Autowired
    private MemoryDBManager memoryDBManager;
    @Autowired
    private LoginService loginService;
    @Autowired
    private NameService nameService;
    @Autowired
    private ValueService valueService;
    @Autowired
    private OnlineReportDAO onlineReportDAO;

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

    public String getSQLDatabaseName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String databaseName) {
        //TODO  Check name below is unique.
        return getBusinessPrefix(azquoMemoryDBConnection) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
    }

    public String getBusinessPrefix(final AzquoMemoryDBConnection azquoMemoryDBConnection) {
        Business b = businessDAO.findById(azquoMemoryDBConnection.getBusinessId());
        return (b.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "");
    }

    public void emptyDatabase(String mysqlName) throws Exception {
        mySQLDatabaseManager.emptyDatabase(mysqlName);
    }


    public void dropDatabase(String mysqlName) throws Exception {
        mySQLDatabaseManager.dropDatabase(mysqlName);
    }

    public void createDatabase(final String databaseName, final LoggedInConnection loggedInConnection) throws Exception {
        if (loggedInConnection.getUser().isAdministrator()) {
            Database existing = databaseDAO.findForName(loggedInConnection.getBusinessId(), databaseName);
            if (existing != null) {
                throw new Exception("That database already exists");
            }
            final String mysqlName = getSQLDatabaseName(loggedInConnection, databaseName);
            final Business b = businessDAO.findById(loggedInConnection.getBusinessId());
            final Database database = new Database(0, LocalDateTime.now(), LocalDateTime.now().plusYears(10), b.getId(), databaseName, mysqlName, 0, 0);
            try {
                mySQLDatabaseManager.createNewDatabase(mysqlName);
            } catch (Exception e) {
                throw e;
            }
            databaseDAO.store(database);
            memoryDBManager.addNewToDBMap(database);
            if (loggedInConnection.getAzquoMemoryDB() == null) { // creating their first db I guess?
                loggedInConnection.setAzquoMemoryDB(memoryDBManager.getAzquoMemoryDB(database));
            }
        } else {
            throw new Exception("Only administrators can create databases");
        }
    }

    public void createUser(final String email
            , final String userName
            , final LocalDateTime endDate
            , final String status
            , final String password
            , final LoggedInConnection loggedInConnection) throws Exception {
        if (loggedInConnection.getUser().isAdministrator()) {
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, LocalDateTime.now(), endDate, loggedInConnection.getBusinessId(), email, userName, status, encrypt(password, salt), salt);
            userDao.store(user);
            return;
        } else {
            throw new Exception("error: you do not have permission to create a user");
        }
    }

    // now not dependant on selected database

    public void createUserPermission(final int userId, final int databaseId, final LocalDateTime startDate, final LocalDateTime endDate, final String readList, final String writeList, final LoggedInConnection loggedInConnection) throws Exception {
        User user = userDao.findById(userId);
        Database database = databaseDAO.findById(databaseId);
        if (loggedInConnection.getUser().isAdministrator()
                && user != null && user.getBusinessId() == loggedInConnection.getBusinessId()
                && database != null && database.getBusinessId() == loggedInConnection.getBusinessId()) {
            final Permission permission = new Permission(0, startDate, endDate, userId, databaseId, readList, writeList);
            permissionDAO.store(permission);
            return;
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

    public List<Database> getDatabaseListForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            return databaseDAO.findForBusinessId(loggedInConnection.getBusinessId());
        }
        return null;
    }

    public List<Database> getDatabaseListForBusiness(final Business business) {
        return databaseDAO.findForBusinessId(business.getId());
    }

    public List<User> getUserListForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator/**/()) {
            return userDao.findForBusinessId(loggedInConnection.getBusinessId());
        }
        return null;
    }


    public List<OnlineReport> getReportList(final LoggedInConnection loggedInConnection) {
        List<OnlineReport> reportList;
        if (loggedInConnection.getUser().isAdministrator()) {
            reportList = onlineReportDAO.findForBusinessId(loggedInConnection.getBusinessId());
        } else {
            String userStatus = loggedInConnection.getUser().getStatus();
            String[] status = userStatus.split(",");//user may have more than one status
            reportList = new ArrayList<OnlineReport>();
            for (int i = 0; i < status.length; i++) {
                reportList.addAll(onlineReportDAO.findForBusinessIdAndUserStatus(loggedInConnection.getBusinessId(), loggedInConnection.getUser().getStatus()));
            }
        }
        if (reportList != null) {
            for (OnlineReport onlineReport : reportList) {
                Database database = databaseDAO.findById(onlineReport.getDatabaseId());
                if (database != null) {//in case database has been deleted without deleting the report reference.....
                    onlineReport.setDatabase(database.getName());
                }
            }
        } else {
            OnlineReport notFound = new OnlineReport(0, 0, 0, "", "No reports found", "", "", "", "");
            reportList.add(notFound);
        }
        return reportList;
    }

    public List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            List<UploadRecord> uploadRecords = uploadRecordDAO.findForBusinessId(loggedInConnection.getBusinessId());
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

    public List<Permission> getPermissionList(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            return permissionDAO.findByBusinessId(loggedInConnection.getBusinessId());
        }
        return null;
    }

    public List<Permission.PermissionForDisplay> getDisplayPermissionList(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            List<Permission.PermissionForDisplay> permissions = new ArrayList<Permission.PermissionForDisplay>();
            for (Permission permission : permissionDAO.findByBusinessId(loggedInConnection.getBusinessId())){
                permissions.add(new Permission.PermissionForDisplay(permission, databaseDAO,userDao));
            }
            return permissions;
        }
        return null;
    }


    public Name copyName(AzquoMemoryDBConnection toDB, Name name, Name parent, List<String> languages, Collection<Name> allowed, Map<Name, Name> dictionary) throws Exception {
        Name name2 = dictionary.get(name);
        if (name2 != null) {
            return name2;
        }
        //consider ALL names as local.  Global names will be found from dictionary
        name2 = nameService.findOrCreateNameInParent(toDB, name.getDefaultDisplayName(), parent, true, languages);
        for (String attName : name.getAttributes().keySet()) {
            name2.setAttributeWillBePersisted(attName, name.getAttribute(attName));
        }
        LinkedHashMap<Name, Boolean> peers2 = new LinkedHashMap<Name, Boolean>();
        for (Name peer : name.getPeers().keySet()) {
            Name peer2 = copyName(toDB, peer, null, languages, null, dictionary);//assume that peers can be found globally
            peers2.put(peer2, name.getPeers().get(peer));
        }
        if (peers2.size() > 0) {
            name2.setPeersWillBePersisted(peers2);
        }
        for (Name child : name.getChildren()) {
            if (allowed == null || allowed.contains(child)) {
                copyName(toDB, child, name2, languages, allowed, dictionary);
            }
        }
        return name2;
    }

    public void copyDatabase(LoggedInConnection loggedInConnection, String database, String nameList) throws Exception {
        LoggedInConnection lic2 = loginService.login(database, loggedInConnection.getUser().getEmail(), "", 1, "", true);
        if (lic2 == null) {
            throw new Exception("cannot log in to " + database);
        }
        lic2.setNewProvenance("transfer from", database);
        //can't use 'nameService.decodeString as this may have multiple values in each list
        List<Set<Name>> namesToTransfer = nameService.decodeString(loggedInConnection, nameList, lic2.getLanguages());
        //find the data to transfer
        Map<Set<Name>, Set<Value>> showValues = valueService.getSearchValues(namesToTransfer);

        //extract the names from this data
        final Set<Name> namesFound = new HashSet<Name>();
        for (Set<Name> nameValues : showValues.keySet()) {
            for (Name name : nameValues) {
                namesFound.add(name);
            }
        }
        List<String> languages = new ArrayList<String>();
        languages.add(Name.DEFAULT_DISPLAY_NAME);
        //transfer each name and its parents.
        Map<Name, Name> dictionary = new HashMap<Name, Name>();
        for (Name name : namesFound) {
            Collection<Name> allowed = name.findAllParents();
            allowed.add(name);
            for (Name parent : name.findAllParents()) {
                if (parent.getParents() == null) {//we need to start from the top
                    //copyname copies all allowed children, and avoids endless loops.
                    copyName(lic2, parent, null, languages, allowed, dictionary);
                }
            }

        }
        for (Set<Name> nameValues : showValues.keySet()) {
            Set<Name> names2 = new HashSet<Name>();
            for (Name name : nameValues) {
                names2.add(dictionary.get(name));

            }
            valueService.storeValueWithProvenanceAndNames(lic2, valueService.addValues(showValues.get(nameValues)), names2);
        }
        lic2.persist();
    }

    public void storeReport(OnlineReport report){
        onlineReportDAO.store(report);
    }

    public void deleteUserById(int userId, LoggedInConnection loggedInConnection) {
        User user = userDao.findById(userId);
        if (user != null && loggedInConnection.getBusinessId() == user.getBusinessId()){
            userDao.removeById(user);
        }
    }

    public User getUserById(int userId, LoggedInConnection loggedInConnection) {
        User user = userDao.findById(userId);
        if (user != null && loggedInConnection.getBusinessId() == user.getBusinessId()){
            return user;
        }
        return null;
    }

    public void storeUser(User user){
        userDao.store(user);
    }

    public void deletePermissionById(int permissionId, LoggedInConnection loggedInConnection) {
        Permission permission = permissionDAO.findById(permissionId);
        User user = userDao.findById(permission.getUserId());
        if (permission != null && loggedInConnection.getBusinessId() == user.getBusinessId()){
            permissionDAO.removeById(permission);
        }
    }

    public Permission getPermissionById(int permissionId, LoggedInConnection loggedInConnection) {
        Permission permission = permissionDAO.findById(permissionId);
        if (permission != null){
            User user = userDao.findById(permission.getUserId());
            if (loggedInConnection.getBusinessId() == user.getBusinessId()){
                return permission;
            }
        }
        return null;
    }

    public void storePermission(Permission permission){
        permissionDAO.store(permission);
    }

    public Database getDatabaseById(int databaseId, LoggedInConnection loggedInConnection) {
        Database database = databaseDAO.findById(databaseId);
        if (database != null && loggedInConnection.getBusinessId() == database.getBusinessId()){
            return database;
        }
        return null;
    }

    // code adapted from spreadsheet service which it wilol be removed from
    public void removeDatabaseById(LoggedInConnection loggedInConnection,  int databaseId) throws Exception {
        Database db = databaseDAO.findById(databaseId);
        if (db != null && db.getBusinessId() == loggedInConnection.getBusinessId()) {
            List<OnlineReport> onlineReports = onlineReportDAO.findForDatabaseId(db.getId());
            for (OnlineReport onlineReport : onlineReports) {
                userChoiceDAO.deleteForReportId(onlineReport.getId());
            }
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
}
