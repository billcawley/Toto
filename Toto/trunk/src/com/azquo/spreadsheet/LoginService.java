package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.user.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.memorydb.*;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Currently fairly simple login functions
 */




public class LoginService {

    private static final Logger logger = Logger.getLogger(LoginService.class);

    @Autowired
    private MemoryDBManager memoryDBManager;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private LoginRecordDAO loginRecordDAO;
    @Autowired
    private PermissionDAO permissionDao;
    @Autowired
    private DatabaseDAO databaseDao;
    @Autowired
    private AdminService adminService;
    @Autowired
    private NameService nameService;
    @Autowired
    private OpenDatabaseDAO openDatabaseDAO;
    @Autowired
    private ValueService valueService;

    @Autowired
    private AppDBConnectionMap connectionMap;
    @Autowired
    private BusinessDAO businessDAO;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private UploadRecordDAO uploadRecordDAO;



    private final HashMap<Integer, Integer> openDBCount = new HashMap<Integer, Integer>();




        public LoggedInConnection login(final String databaseName, final String userEmail, final String password, final int timeOutInMinutes, String spreadsheetName, boolean loggedIn) throws  Exception {

/*            System.out.println("database name " + databaseName);
            System.out.println("usermeail " + userEmail);
            System.out.println("password " + password);*/

            if (spreadsheetName == null) {
                spreadsheetName = "unknown";
            }

            User user;

            //for demo users, a new User id is made for each user.
            if (userEmail.startsWith("demo@user.com")) {
                user = userDao.findByEmail(userEmail);
                if (user == null) {
                    user = userDao.findByEmail("demo@user.com");
                    if (user != null) {
                        user.setEmail(userEmail);
                        user.setId(0);
                        userDao.store(user);
                    }
                }
            } else {
                user = userDao.findByEmail(userEmail);
            }
            //boolean temporary = false;
            if (user != null && (loggedIn || adminService.encrypt(password.trim(), user.getSalt()).equals(user.getPassword()))) {
                    return login(databaseName, user, timeOutInMinutes, spreadsheetName);

            }
            return null;
        }

    public LoggedInConnection login(final String databaseName, final User user, final int timeOutInMinutes, String spreadsheetName) throws  Exception{
                 // ok user should be ok :)
                final Map<String, Database> okDatabases = foundDatabases(user);
                logger.info("ok databases size " + okDatabases.size());
                AzquoMemoryDB memoryDB = null;
                Database database;
                if (okDatabases.size() == 1) {
                    logger.info("1 database, use that");
                    database = okDatabases.values().iterator().next();
                    memoryDB = memoryDBManager.getAzquoMemoryDB(database);
                    if (database.getName().equals("temp")){
                        memoryDB.zapDatabase();//to be on the safe side and avoid any persistance
                    }
                 } else {
                    database = okDatabases.get(databaseName);
                    if (database != null) {
                        memoryDB = memoryDBManager.getAzquoMemoryDB(database);
                    }
                }
                // could be a null memory db . . .
                /*
                LoggedInConnection lic = existingConnection(user);
                if (lic!=null){
                    return lic;
                }*/
                LoggedInConnection lic = new LoggedInConnection(memoryDB, user, timeOutInMinutes * 60 * 1000, spreadsheetName);
                int databaseId = 0;
                if (memoryDB != null && memoryDB.getDatabase() !=null){
                   databaseId = memoryDB.getDatabase().getId();
                   Integer openCount = openDBCount.get(databaseId);
                   if (openCount != null){
                       openDBCount.put(databaseId, openCount + 1);
                   }else{
                       openDBCount.put(databaseId, 1);
                   }
                }
                Permission permission = null;
                if (database != null){
                    permission = permissionDao.findByBusinessUserAndDatabase(lic.getUser(),database);
                }
                List<Set<Name>> names = new ArrayList<Set<Name>>();
                if (permission != null){
//                    String error = nameService.decodeString(lic,permission.getReadList(), names);
                    names = nameService.decodeString(lic,permission.getReadList(), lic.getLanguages());
                     //TODO HANDLE ERROR.  should not be any unless names have been changed since storing
                }
                lic.setReadPermissions(names);
                names = new ArrayList<Set<Name>>();
                if (permission != null){
//                    String error = nameService.decodeString(lic,permission.getWriteList(), names);
                    names = nameService.decodeString(lic,permission.getWriteList(), lic.getLanguages());
                    //TODO HANDLE ERROR.  should not be any unless names have been changed since storing
                }
                lic.setWritePermissions(names);
                if (database!=null && !database.getName().equals("temp")){
                    loginRecordDAO.store(new LoginRecord(0,user.getId(),databaseId, new Date()));
                }
                /*if (!user.getEmail().contains("@demo.") && !user.getEmail().contains("@user.")){
                    //azquoMailer.sendEMail(user.getEmail(),user.getName(),"Login to Azquo", "You have logged into Azquo.");
                }*/
                if (database != null && lic.getAzquoMemoryDB()!=null){
                    anonymise(lic);
                }


                return lic;
         }



    public Map<String, Database> foundDatabases(User user) {
        final List<Permission> userAcceses = permissionDao.findForUserId(user.getId());
        final Map<String, Database> okDatabases = new HashMap<String, Database>();
        if (user.isAdministrator() || user.getEmail().startsWith("demo@user")) { // automatically has all dbs regardless of permission
            for (Database database : databaseDao.findForBusinessId(user.getBusinessId())) {
                if (database.getEndDate().isAfter(LocalDateTime.now())) {
                    okDatabases.put(database.getName(), database);
                }
            }
        } else {
            for (Permission permission : userAcceses) {
                if (permission.getEndDate().isAfter(LocalDateTime.now())) {
                    Database database = databaseDao.findById(permission.getDatabaseId());
                    if (database.getEndDate().isAfter(LocalDateTime.now())) {
                        okDatabases.put(database.getName(), database);
                    }
                }
            }
        }
        return okDatabases;
    }



    public void anonymise(LoggedInConnection loggedInConnection){
        List<Name> anonNames = nameService.findContainingName(loggedInConnection,"",Name.ANON);

        for (Name set:anonNames){
            String anonName = set.getAttribute(Name.ANON);
            if (set.getPeers().size() > 0 ){
                Double low = 0.5;
                Double high = 0.5;
                try{
                    String[] limits = anonName.split(" ");
                    low = Double.parseDouble(limits[0]) / 100;
                    high = Double.parseDouble(limits[1])/ 100;

                } catch (Exception ignored){
                }
                valueService.randomAdjust(set, low, high);
            }else {
                int count = 1;
                for (Name name : set.getChildren()) {
                    try {
                        name.setTemporaryAttribute(Name.DEFAULT_DISPLAY_NAME, anonName.replace("[nn]", count + ""));
                        count++;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }


/*
    public void zapConnectionsTimedOut() {
        for (Iterator<Map.Entry<String, LoggedInConnection>> it = connections.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LoggedInConnection> entry = it.next();
            LoggedInConnection lic = entry.getValue();
            if ((System.currentTimeMillis() - lic.getLastAccessed().getTime()) > lic.getTimeOut()) {
                // connection timed out
                if (lic.getAzquoMemoryDB() != null) {
                     int databaseId = 0;
                    if (lic.getAzquoMemoryDB().getDatabase() !=null) {
                        databaseId = lic.getAzquoMemoryDB().getDatabase().getId();
                      }
                    it.remove();
                    if (databaseId > 0) {
                        Integer openCount = openDBCount.get(databaseId);
                        if (openCount == 1) {
                            memoryDBManager.removeDatabase(lic.getAzquoMemoryDB().getDatabase());
                            openDBCount.remove(databaseId);
                            openDatabaseDAO.closeForDatabaseId(databaseId);
                        } else {
                            openDBCount.put(databaseId, openCount - 1);
                        }
                    }
                }
            }
        }
    }*/

    public void switchDatabase(LoggedInConnection loggedInConnection, Database newDb)throws Exception{
        if (loggedInConnection.getAzquoMemoryDB()!= null){
            Database oldDB = loggedInConnection.getAzquoMemoryDB().getDatabase();
            if (newDb!= null && newDb.getName().equals("temp")){

                //don't switch to a temporary connection if you've been moved off it
                return;
            }
            if (newDb!= null && oldDB.getName().equals(newDb.getName())) return;
            int databaseId = loggedInConnection.getAzquoMemoryDB().getDatabase().getId();
            Integer openCount = openDBCount.get(databaseId);
            if (newDb == null) openCount = 1;//if we're deleting the database, then close the memory, regardless of whether others have it open.
            if (openCount != null && openCount == 1) {
                memoryDBManager.removeDatabase(loggedInConnection.getAzquoMemoryDB().getDatabase());
                openDBCount.remove(databaseId);
                openDatabaseDAO.closeForDatabaseId(databaseId);
            } else if (openCount != null && openCount > 1){
                openDBCount.put(databaseId, openCount - 1);
            }
        }
        if (newDb==null){
            loggedInConnection.setAzquoMemoryDB(null);
            return;
        }
        AzquoMemoryDB memoryDB = memoryDBManager.getAzquoMemoryDB(newDb);
        int databaseId = memoryDB.getDatabase().getId();
        Integer openCount = openDBCount.get(databaseId);
        if (openCount != null){
            openDBCount.put(databaseId, openCount + 1);
        }else{
            openDBCount.put(databaseId, 1);
        }
        loggedInConnection.setAzquoMemoryDB(memoryDB);


    }

/*    private LoggedInConnection existingConnection(final User user){
        if (!user.getEmail().equals("tempuser")){ // edd : not really happy about these sring literlas, fix later . . .
            for (String lic:connections.keySet()){
                LoggedInConnection loggedInConnection = connections.get(lic);
                if (loggedInConnection.getUser().getId()==user.getId()){
                    return loggedInConnection;
                }
            }
        }
        return null;
    }*/



/*    public LoggedInConnection getConnectionFromJsonRequest(final StandardJsonRequest standardJsonRequest, String callerIP) throws Exception{
        String user = standardJsonRequest.user;
        if (user != null && user.equals("demo@user.com")){
            user = user + callerIP;
        }
        if (standardJsonRequest.user != null && standardJsonRequest.user.length() > 0 &&
                standardJsonRequest.password != null && standardJsonRequest.password.length() > 0) {
            return login(standardJsonRequest.database == null ? "" : standardJsonRequest.database, standardJsonRequest.user, standardJsonRequest.password, 60, standardJsonRequest.spreadsheetName, false);
        } else if (standardJsonRequest.connectionId != null && standardJsonRequest.connectionId.length() > 0) {
            return getConnection(standardJsonRequest.connectionId);
        }
        return null;
    }
*/
    public void createAzquoMaster() throws  Exception{

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat tf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        AzquoMemoryDBConnection masterDBConnection = connectionMap.getConnection("Demo_master");//TODO  Convert to Azquo_master
        Name allBusinesses = nameService.findOrCreateNameInParent(masterDBConnection, "All businesses", null, false);
        Name allDatabases = nameService.findOrCreateNameInParent(masterDBConnection,"All databases", null,false);
        Name allUsers = nameService.findOrCreateNameInParent(masterDBConnection,"All users", null,false);
        Name allStatuses = nameService.findOrCreateNameInParent(masterDBConnection,"All statuses", null, false);
        Name allPermissions = nameService.findOrCreateNameInParent(masterDBConnection, "All permissions", null, false);
        Name allReports = nameService.findOrCreateNameInParent(masterDBConnection,"All reports", null, false);
        Name allUserChoices = nameService.findOrCreateNameInParent(masterDBConnection, "All user choices",null, false);
        Name allUploads = nameService.findOrCreateNameInParent(masterDBConnection,"All uploads", null, false);
        Name allLogins = nameService.findOrCreateNameInParent(masterDBConnection,"All logins", null, false);
        Name allOpenDatabases = nameService.findOrCreateNameInParent(masterDBConnection,"All databases opened", null, false);
        List<Business> businesses = businessDAO.findAll();
        for (Business b:businesses) {
            Name bName = nameService.findOrCreateNameInParent(masterDBConnection, b.getBusinessName(), allBusinesses, false);
            Business.BusinessDetails bd = b.getBusinessDetails();
            bName.setAttributeWillBePersisted("address1", bd.address1);
            bName.setAttributeWillBePersisted("address2", bd.address2);
            bName.setAttributeWillBePersisted("address3", bd.address3);
            bName.setAttributeWillBePersisted("address4", bd.address4);
            bName.setAttributeWillBePersisted("website", bd.website);
            bName.setAttributeWillBePersisted("postcode", bd.postcode);
            bName.setAttributeWillBePersisted("telephone", bd.telephone);
            List<Database> databases = databaseDao.findForBusinessId(b.getId());
            Map<Integer, Name> dbMap = new HashMap<Integer, Name>();
            for (Database db : databases) {
                Name dbName = nameService.findOrCreateNameInParent(masterDBConnection, db.getName(), bName, true);
                allDatabases.addChildWillBePersisted(dbName);
                dbName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, db.getMySQLName());
                dbName.setAttributeWillBePersisted("Start date", df.format(db.getStartDate()));
                dbName.setAttributeWillBePersisted("End date", df.format(db.getEndDate()));
                dbMap.put(db.getId(), dbName);
                List<OpenDatabase> openDatabases = openDatabaseDAO.findForDatabaseId(db.getId());
                for (OpenDatabase od:openDatabases){
                    Name odName = nameService.findOrCreateNameInParent(masterDBConnection,db.getName()+":"+tf.format(od.getOpen()),allOpenDatabases, true);
                    dbName.addChildWillBePersisted(odName);
                    odName.setAttributeWillBePersisted("close",tf.format(od.getClose()));
                    odName.setAttributeWillBePersisted("open",tf.format(od.getOpen()));

                }

            }
            List<User> users = userDao.findForBusinessId(b.getId());
            Map<Integer,Name> userMap = new HashMap<Integer, Name>();
            //Map<String, Name> statusMap = new HashMap<String, Name>();
            for (User u : users) {
                Name uName = nameService.findOrCreateNameInParent(masterDBConnection, u.getEmail(), bName, true);
                userMap.put(u.getId(), uName);
                allUsers.addChildWillBePersisted(uName);
                uName.setAttributeWillBePersisted("name", u.getName());
                Name sName = nameService.findOrCreateNameInParent(masterDBConnection, u.getStatus(), allStatuses, true);
                sName.addChildWillBePersisted(uName);
                uName.setAttributeWillBePersisted("password", u.getPassword());
                uName.setAttributeWillBePersisted("salt", u.getSalt());
                uName.setAttributeWillBePersisted("Start date", df.format(u.getStartDate()));
                uName.setAttributeWillBePersisted("End date", df.format(u.getEndDate()));
                String userStatus = u.getStatus();
                if (userStatus != null && userStatus.length() > 0) {
                    String[] userStatuses = userStatus.split(",");
                    for (String us : userStatuses) {
                        String businessUs = b.getBusinessName() + ":" + us;
                        Name usName = nameService.findOrCreateNameInParent(masterDBConnection, businessUs, bName, true);
                        allStatuses.addChildWillBePersisted(usName);
                        usName.addChildWillBePersisted(uName);

                    }
                }
                List<Permission> permissions = permissionDao.findForUserId(u.getId());

                for (Permission p : permissions) {
                    Name pName = nameService.findOrCreateNameInParent(masterDBConnection, dbMap.get(p.getDatabaseId()).getDefaultDisplayName() + ":" + u.getEmail(), allPermissions, true);
                    uName.addChildWillBePersisted(pName);
                    if (p.getReadList() != null && p.getReadList().length() > 0) {
                        pName.setAttributeWillBePersisted("read list", p.getReadList());
                    }
                    if (p.getWriteList() != null && p.getWriteList().length() > 0) {
                        pName.setAttributeWillBePersisted("write list", p.getWriteList());
                    }
                    pName.setAttributeWillBePersisted("Start date", df.format(p.getStartDate()));
                    pName.setAttributeWillBePersisted("End date", df.format(p.getEndDate()));

                }
                //Map<Integer,Name>olrMap = new HashMap<Integer, Name>();
                List<OnlineReport> onlineReports = onlineReportDAO.findForBusinessId(b.getId());
                for (OnlineReport olr : onlineReports) {
                    Name olrParent = bName;
                    if (olr.getDatabaseId() > 0) {
                        //olrParent = dbMap.get(olrParent); // what was there, I assume it means the below?
                        olrParent = dbMap.get(olrParent.getId());
                    }

                    Name olrName = nameService.findOrCreateNameInParent(masterDBConnection, olr.getReportName(), olrParent, true);
                    //olrMap.put(olr.getId(), olrName);
                    olrName.setAttributeWillBePersisted("filename", olr.getFilename());
                    olrName.setAttributeWillBePersisted("explanation", olr.getExplanation());
                    allReports.addChildWillBePersisted(olrName);
                    String ustatus = olr.getUserStatus();
                    if (ustatus != null && ustatus.length() > 0) {
                        String[] ustatuses = ustatus.split(",");
                        for (String us : ustatuses) {
                            String businessUs = b.getBusinessName() + ":" + us;
                            Name usName = nameService.findOrCreateNameInParent(masterDBConnection, businessUs, bName, true);
                            allStatuses.addChildWillBePersisted(usName);
                            usName.addChildWillBePersisted(olrName);

                        }
                    }
                    List<UserChoice> userChoices = userChoiceDAO.findForUserIdAndReportId(u.getId(), olr.getId());
                    for (UserChoice uc:userChoices){
                         Name ucName = nameService.findOrCreateNameInParent(masterDBConnection, u.getEmail() + ":" + olrName.getDefaultDisplayName(), allUserChoices, true);
                         uName.addChildWillBePersisted(ucName);
                         ucName.setAttributeWillBePersisted(uc.getChoiceName(), uc.getChoiceValue());
                         ucName.setAttributeWillBePersisted("update time", tf.format(uc.getTime()));
                    }
                    List<LoginRecord> loginRecords = loginRecordDAO.findForUserId(u.getId());
                    for (LoginRecord lr:loginRecords){
                        Name dbName = dbMap.get(lr.getDatabaseId());
                        if (dbName!=null){
                            Name lrName = nameService.findOrCreateNameInParent(masterDBConnection,u.getEmail()+":"+tf.format(lr.getTime()),allLogins, true);
                            dbName.addChildWillBePersisted(lrName);
                            lrName.setAttributeWillBePersisted("time", tf.format(lr.getTime()));
                        }
                    }

                }
            }
            List<UploadRecord> uploadRecords = uploadRecordDAO.findForBusinessId(b.getId());
            for (UploadRecord ur:uploadRecords){
                Name dbName = dbMap.get(ur.getDatabaseId());
                Name uName = userMap.get(ur.getUserId());
                if (dbName != null && uName!=null){
                    Name urName = nameService.findOrCreateNameInParent(masterDBConnection,uName.getDefaultDisplayName() + ":" + tf.format(ur.getDate()), allUploads,true);
                    urName.setAttributeWillBePersisted("file name", ur.getFileName());
                    urName.setAttributeWillBePersisted("time", tf.format(ur.getDate()));
                }
            }

         }


    }

}
