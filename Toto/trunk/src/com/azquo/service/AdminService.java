package com.azquo.service;

import com.azquo.admindao.*;
import com.azquo.adminentities.*;
import com.azquo.memorydb.MemoryDBManager;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Value;
import com.azquo.util.AzquoMailer;
import org.springframework.beans.factory.annotation.Autowired;
import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Used for admin functions. Register, validate etc
 * pretty simple with calls to the vanilla admin dao classes
 */
public class AdminService {

    //private static final Logger logger = Logger.getLogger(AdminService.class);

    @Autowired
    private BusinessDAO businessDao;
    @Autowired
    private DatabaseDAO databaseDao;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private PermissionDAO permissionDao;
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


    public String registerBusiness(final String email
            , final String userName
            , final String password
            , final String businessName
            , final String address1
            , final String address2
            , final String address3
            , final String address4
            , final String postcode
            , final String telephone) {
        // we need to check for existing businesses
        final String key = shaHash(System.currentTimeMillis() + "");
        final Business.BusinessDetails bd = new Business.BusinessDetails(address1, address2, address3, address4, postcode, telephone, "website???", key);
        final Business business = new Business(0, new Date(), new Date(), businessName, 0, bd);
        final Business existing = businessDao.findByName(businessName);
        if (existing != null) { // ok new criteria, overwrite if the business was not already key validated
            if (existing.getEndDate().after(new Date())) {
                return "error: " + businessName + " already registered";
            } else {
                businessDao.removeById(existing); // it will be created again
            }
        }
        User existingUser = userDao.findByEmail(email);
        if (existingUser != null) {
            if (existingUser.getEndDate().after(new Date())) { // active user
                return "error: " + email + " already registered";
            } else {
                userDao.removeById(existingUser); // it will be created again
            }
        }
        businessDao.store(business);
        final String salt = shaHash(System.currentTimeMillis() + "salt");
        final User user = new User(0, new Date(), new Date(), business.getId(), email, userName, "administrator", encrypt(password, salt), salt);
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
        return "true";
    }

    public String confirmKey(final String businessName, final String email, final String password, final String key, String spreadsheetName) throws Exception{
        final Business business = businessDao.findByName(businessName);
        if (business != null && business.getBusinessDetails().validationKey.equals(key)) {
            business.setEndDate(new Date(130, 1, 1));
            businessDao.store(business);
            User user = userDao.findForBusinessId(business.getId()).get(0);
            user.setEndDate(new Date(130, 1, 1));
            user.setStatus(User.STATUS_ADMINISTRATOR);
            userDao.store(user);
            LoggedInConnection loggedInConnection = loginService.login("unknown", email, password, 0, spreadsheetName, false);
            if (loggedInConnection == null) {
                return "error:no connection id";
            }
            return loggedInConnection.getConnectionId();
        }
        return "error:  incorrect key";
    }

    public String getSQLDatabaseName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String databaseName) {
        Business b = businessDao.findById(azquoMemoryDBConnection.getBusinessId());
        //TODO  Check name below is unique.
        return getBusinessPrefix(azquoMemoryDBConnection) + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();


    }

    public String getBusinessPrefix(final AzquoMemoryDBConnection azquoMemoryDBConnection){
        Business b = businessDao.findById(azquoMemoryDBConnection.getBusinessId());
        return (b.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "");

    }

    public String dropDatabase(String mysqlName) throws Exception{
        mySQLDatabaseManager.dropDatabase(mysqlName);
        return "";
    }

    public void createDatabase(final String databaseName, final LoggedInConnection loggedInConnection) throws Exception {
        if (loggedInConnection.getUser().isAdministrator()) {
             Database existing = databaseDao.findForName(loggedInConnection.getBusinessId(),databaseName);
            if (existing != null){
                throw new Exception("error: That database already exists");
            }
            final String mysqlName = getSQLDatabaseName(loggedInConnection, databaseName);
            final Business b = businessDao.findById(loggedInConnection.getBusinessId());
            final Database database = new Database(0, new Date(), new Date(130, 1, 1), b.getId(), databaseName, mysqlName, 0, 0);
            try{
               mySQLDatabaseManager.createNewDatabase(mysqlName);
            }catch (Exception e){
                throw e;
            }
            databaseDao.store(database);
            memoryDBManager.addNewToDBMap(database);
            if (loggedInConnection.getAzquoMemoryDB() == null) { // creating their first db I guess?
                loggedInConnection.setAzquoMemoryDB(memoryDBManager.getAzquoMemoryDB(database));
            }

        }else{
            throw new Exception("error: Only administrators can create databases");
        }
    }

    public void createUser(final String email
            , final String userName
            , final String status
            , final String password
            , final LoggedInConnection loggedInConnection) throws Exception {
        if (loggedInConnection.getUser().isAdministrator()) {
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, new Date(), new Date(130, 1, 1), loggedInConnection.getBusinessId(), email, userName, status, encrypt(password, salt), salt);
            userDao.store(user);
            return;
        }else{
            throw new Exception("error: you do not have permission to create a user");
        }
     }

    public void createUserPermission(final String email, final String readList, final String writeList, final LoggedInConnection loggedInConnection) throws Exception {
        if (loggedInConnection.getUser().isAdministrator() && loggedInConnection.getAzquoMemoryDB() != null) { // actually have a DB selected
            final Permission permission = new Permission(0, new Date(), new Date(130, 1, 1), userDao.findByEmail(email).getId(), loggedInConnection.getAzquoMemoryDB().getDatabase().getId(), readList, writeList);
            permissionDao.store(permission);
            return;
        }else{
            throw new Exception("error: you do not have permission to perform this action");
        }
    }

    //variation on a function I've used before

    /*                String salt = PasswordUtils.shaHash(System.currentTimeMillis() + "salt");
    v.setPasswordSalt(salt);
    v.setPassword(PasswordUtils.encrypt(password, salt)); // new better encryption . . .*/


    public String encrypt(final String password, String salt) {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "fail . . .";
    }

    public String shaHash(final String toHash) {   // for making a password salt
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(toHash.getBytes());
            Formatter formatter = new Formatter();
            for (byte b : hash) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "fail . . .";
    }


    public Business getBusinessById(int id) {
        return businessDao.findById(id);
    }

    public List<Database> getDatabaseListForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            return databaseDao.findForBusinessId(loggedInConnection.getBusinessId());
        }
        return null;
    }

    public List<Database> getDatabaseListForBusiness(final Business business) {
            return databaseDao.findForBusinessId(business.getId());
    }

    public List<User> getUserListForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator/**/()) {
            return userDao.findForBusinessId(loggedInConnection.getBusinessId());
        }
        return null;
    }


    public String saveOnlineReportList(final LoggedInConnection loggedInConnection, List<OnlineReport> reportsSent){

        for (OnlineReport reportSent:reportsSent){
            //when saving a report from an Excel data sheet, the system does not know the ID, so the report can be identified by name
            //when saving from the admin workbook, the name may be changed, so check for duplicate names.
            final int EXISTINGREPORT = -1;
            reportSent.setBusinessId(loggedInConnection.getBusinessId());
            if (reportSent.getDatabaseId() == 0){
                Database d = databaseDao.findForName(loggedInConnection.getBusinessId(), reportSent.getDatabase());
                if (d != null){
                    reportSent.setDatabaseId(d.getId());
                }else{
                    return "error: database not recognised " + reportSent.getDatabase();
                }

            }
            OnlineReport existingReport = onlineReportDAO.findForDatabaseIdAndName(reportSent.getDatabaseId(), reportSent.getReportName());
            if (reportSent.getUserStatus() == null){
                reportSent.setUserStatus("");
            }
            if (reportSent.getId() == EXISTINGREPORT){
                if (existingReport == null){
                    reportSent.setId(0);
                }else{
                    reportSent.setId(existingReport.getId());
                }
            }
            if (existingReport != null && existingReport.getId() != reportSent.getId()){
                return "error:  there already exists a report named " + reportSent.getReportName() + " in the database " + databaseDao.findById(reportSent.getDatabaseId()).getName();

            }

            if (reportSent.getId() > 0){
                existingReport = onlineReportDAO.findById(reportSent.getId());
                if (existingReport != null){
                    if (reportSent.getReportName().length() == 0){
                        onlineReportDAO.removeById(reportSent);

                    }else{
                        existingReport.setDatabaseId(reportSent.getDatabaseId());
                        existingReport.setReportName(reportSent.getReportName());
                        if (reportSent.getUserStatus() != null && reportSent.getUserStatus().length() > 0){
                            existingReport.setUserStatus(reportSent.getUserStatus());
                        }
                        if (reportSent.getFilename() != null && reportSent.getFilename().length() > 0){
                            existingReport.setFilename(reportSent.getFilename());
                        }
                        onlineReportDAO.store(existingReport);
                    }
                }
                else{
                    return "error: the report id " + reportSent.getId() + " does not exist";
                }
            }else{
                onlineReportDAO.store(reportSent);
            }
        }
        return "";
    }


    public List<OnlineReport> getReportList(final LoggedInConnection loggedInConnection) {
        List<OnlineReport> reportList;
        if (loggedInConnection.getUser().isAdministrator()) {
            reportList = onlineReportDAO.findForBusinessId(loggedInConnection.getBusinessId());
        }else{
            String userStatus = loggedInConnection.getUser().getStatus();
            String[]status = userStatus.split(",");//user may have more than one status
            reportList = new ArrayList<OnlineReport>();
            for (int i=0;i<status.length; i++) {
                reportList.addAll(onlineReportDAO.findForBusinessIdAndUserStatus(loggedInConnection.getBusinessId(), loggedInConnection.getUser().getStatus()));
            }
        }
        if (reportList != null){
             for (OnlineReport onlineReport:reportList) {
                 Database database = databaseDao.findById(onlineReport.getDatabaseId());
                 if (database!=null) {//in case database has been deleted without deleting the report reference.....
                     onlineReport.setDatabase(database.getName());
                 }
            }
        }else{
            OnlineReport notFound = new OnlineReport(0,0,0,"","No reports found","","","","");
            reportList.add(notFound);
        }
        return reportList;
    }



    public String setUserListForBusiness(final LoggedInConnection loggedInConnection, List<User> userList) {
        if (loggedInConnection.getUser().isAdministrator()) {
            for (User user : userList) {
                User existingUser = userDao.findByEmail(user.getEmail());
                if (existingUser != null && existingUser.getBusinessId() != loggedInConnection.getBusinessId()) {
                    return "error: cannot modify/create users for a different business! (" + existingUser.getEmail() + ")";
                }
                if (user.getEndDate().getYear() > 129){
                    user.setEndDate(new Date(130,1,1));
                }
                if (user.getId() > 0) {
                    existingUser = userDao.findById(user.getId());
                    if (existingUser == null) {
                        return "error: passed used with an Id I can't find";
                    } else {
                        if (user.getStartDate() != null) {
                            existingUser.setStartDate(user.getStartDate());
                        }
                        if (user.getEndDate() != null) {
                            existingUser.setEndDate(user.getEndDate());
                        }
                        if (user.getBusinessId() > 0) {
                            existingUser.setBusinessId(user.getBusinessId());
                        }
                        if (user.getEmail() != null) {
                            existingUser.setEmail(user.getEmail());
                        }
                        if (user.getName() != null) {
                            existingUser.setName(user.getName());
                        }
                        if (user.getStatus() != null) {
                            existingUser.setStatus(user.getStatus());
                        }
                        if (user.getPassword() != null && user.getPassword().length() > 0) {
                            final String salt = shaHash(System.currentTimeMillis() + "salt");
                            existingUser.setSalt(salt);
                            existingUser.setPassword(encrypt(user.getPassword(), salt));
                        }
                        userDao.store(existingUser);
                    }
                } else { // a new one. Possibly need checks on required fields but for the moment just sort the password and store.
                    if (user.getPassword() != null) {
                        final String salt = shaHash(System.currentTimeMillis() + "salt");
                        final User newUser = new User(0, new Date(), new Date(), loggedInConnection.getBusinessId(), user.getEmail(), user.getName(), user.getStatus(), encrypt(user.getPassword(), salt), salt);
                        userDao.store(newUser);
                    }
                 }
            }
        }
        return "";
    }

    public List<UploadRecord.UploadRecordForDisplay> getUploadRecordsForDisplayForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            List<UploadRecord> uploadRecords = uploadRecordDAO.findForBusinessId(loggedInConnection.getBusinessId());
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplay = new ArrayList<UploadRecord.UploadRecordForDisplay>();
            for (UploadRecord uploadRecord : uploadRecords) {
                String dbName = "";
                if (uploadRecord.getDatabaseId()> 0){
                    dbName = databaseDao.findById(uploadRecord.getDatabaseId()).getName();
                }
                String userName = "";
                if (uploadRecord.getUserId() > 0) {
                    userName = userDao.findById(uploadRecord.getUserId()).getName();
                }
                uploadRecordsForDisplay.add(new UploadRecord.UploadRecordForDisplay(uploadRecord, businessDao.findById(uploadRecord.getBusinessId()).getBusinessName(), dbName, userName));
            }
            return uploadRecordsForDisplay;
        }
        return null;
    }

    public List<Permission> getPermissionList(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()) {
            List<Permission> permissionsList = permissionDao.findByBusinessId(loggedInConnection.getBusinessId());
            for (Permission p : permissionsList) {
                p.setEmail(userDao.findById(p.getUserId()).getEmail());
                p.setDatabase(databaseDao.findById(p.getDatabaseId()).getName());
            }
            return permissionsList;
        }
        return null;
    }

    // there is a constraint on here,only allow relevant user or database ids! Otherwise could cause all sort of trouble
    public String setPermissionListForBusiness(LoggedInConnection loggedInConnection, List<Permission> permissionList) throws Exception{
        permissionDao.deleteForBusinessId(loggedInConnection.getBusinessId());
        for (Permission permission : permissionList) {
            if (permission.getEndDate().getYear() > 129){
                permission.setEndDate(new Date(130,1,1));
            }


            Database d = databaseDao.findForName(loggedInConnection.getBusinessId(), permission.getDatabase());
            if (d == null || d.getBusinessId() != loggedInConnection.getBusinessId()) {
                return "error: database name " + permission.getDatabase() + " is invalid";
            }
            permission.setDatabaseId(d.getId());
            User u = userDao.findByEmail(permission.getEmail());
            if (u == null || u.getBusinessId() != loggedInConnection.getBusinessId()) {
                return "error: user email " + permission.getEmail() + " is invalid";
            }
            try{
                nameService.decodeString(loggedInConnection,permission.getReadList(), loggedInConnection.getLanguages());
                nameService.decodeString(loggedInConnection, permission.getWriteList(), loggedInConnection.getLanguages());

            } catch (Exception e){
                return "error:" + e.getMessage();
            }
            permission.setUserId(u.getId());
            permissionDao.store(permission);
        }
        return "";
    }


    public Name copyName(AzquoMemoryDBConnection toDB, Name name, Name parent, List<String> languages, Collection<Name> allowed, Map<Name,Name> dictionary) throws Exception{
        Name name2 =dictionary.get(name);
        if (name2!=null ) return name2;

        //consider ALL names as local.  Global names will be found from dictionary
        name2 = nameService.findOrCreateNameInParent(toDB, name.getDefaultDisplayName(), parent, true, languages);
        for (String attName : name.getAttributes().keySet()) {
            name2.setAttributeWillBePersisted(attName, name.getAttribute(attName));
        }
        LinkedHashMap<Name, Boolean> peers2 = new LinkedHashMap<Name, Boolean>();
        for (Name peer:name.getPeers().keySet()){
            Name peer2 = copyName(toDB,peer, null, languages, null, dictionary);//assume that peers can be found globally
            peers2.put(peer2, name.getPeers().get(peer));

        }
        if (peers2.size() > 0){
            name2.setPeersWillBePersisted(peers2);
        }
        for (Name child:name.getChildren()){
            if (allowed==null || allowed.contains(child)){
                copyName(toDB,child, name2, languages, allowed, dictionary);
            }
        }
        return name2;



    }





    public String copyDatabase(LoggedInConnection loggedInConnection, String database, String nameList) throws Exception{

        LoggedInConnection lic2 = loginService.login(database, loggedInConnection.getUser().getEmail(),"",1,"",true);
        if (lic2 == null){
            return "error:  cannot log in to " + database;
        }
        lic2.setNewProvenance("transfer from", database);
        //can't use 'nameService.decodeString as this may have multiple values in each list
        List<Set<Name>> namesToTransfer = nameService.decodeString(loggedInConnection,nameList, lic2.getLanguages());
        //find the data to transfer
        Map<Set<Name>, Set<Value>> showValues = valueService.getSearchValues(namesToTransfer);

        //extract the names from this data
        final Set<Name> namesFound = new HashSet<Name>();
        for (Set<Name> nameValues:showValues.keySet()){
            for (Name name:nameValues){
                namesFound.add(name);
            }
        }
        List<String> languages = new ArrayList<String>();
        languages.add(Name.DEFAULT_DISPLAY_NAME);
        //transfer each name and its parents.
        Map<Name, Name> dictionary = new HashMap<Name, Name>();
        for (Name name:namesFound){
            Collection<Name> allowed = name.findAllParents();
            allowed.add(name);
            for (Name parent:name.findAllParents()){
                if (parent.getParents()==null){//we need to start from the top
                    //copyname copies all allowed children, and avoids endless loops.
                    copyName(lic2,parent,null, languages, allowed, dictionary);
                 }
             }

        }
        for (Set<Name> nameValues:showValues.keySet()){
            Set<Name> names2 = new HashSet<Name>();
            for (Name name:nameValues){
                names2.add(dictionary.get(name));

            }
            valueService.storeValueWithProvenanceAndNames(lic2,valueService.addValues(showValues.get(nameValues)), names2);
        }
        lic2.persist();

        return "";
    }
}
