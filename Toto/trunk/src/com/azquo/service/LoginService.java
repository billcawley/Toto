package com.azquo.service;

import com.azquo.admindao.*;
import com.azquo.adminentities.*;
import com.azquo.jsonrequestentities.StandardJsonRequest;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.MemoryDBManager;
import com.azquo.memorydb.Name;
import com.azquo.util.AzquoMailer;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import sun.rmi.runtime.Log;

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
    private AzquoMailer azquoMailer;
    @Autowired
    private NameService nameService;
    @Autowired
    private OpenDatabaseDAO openDatabaseDAO;
    @Autowired
    private ValueService valueService;

    private final HashMap<String, LoggedInConnection> connections = new HashMap<String, LoggedInConnection>();
    private final HashMap<Integer, Integer> openDBCount = new HashMap<Integer, Integer>();




        public LoggedInConnection login(final String databaseName, final String userEmail, final String password, final int timeOutInMinutes, String spreadsheetName, boolean loggedIn) throws  Exception{

        if (spreadsheetName == null) {
            spreadsheetName = "unknown";
        }

        User user=null;

        //for demo users, a new User id is made for each user.
        if (userEmail.startsWith("demo@user.com")){
            user = userDao.findByEmail(userEmail);
            if (user== null) {
                user = userDao.findByEmail("demo@user.com");
                if (user != null) {
                    user.setEmail(userEmail);
                    user.setId(0);
                    userDao.store(user);
                }
            }
        }else{
            user = userDao.findByEmail(userEmail);
        }
        //boolean temporary = false;
        if (user != null) {
            if (loggedIn || adminService.encrypt(password.trim(), user.getSalt()).equals(user.getPassword())) {
                // ok user should be ok :)
                final Map<String, Database> okDatabases = foundDatabases(user);
                logger.info("ok databases size " + okDatabases.size());
                AzquoMemoryDB memoryDB = null;
                Database database;
                if (okDatabases.size() == 1) {
                    logger.info("1 database, use that");
                    database = okDatabases.values().iterator().next();
                    memoryDB = memoryDBManager.getAzquoMemoryDB(database);
                } else {
                    database = okDatabases.get(databaseName);
                    if (database != null) {
                        memoryDB = memoryDBManager.getAzquoMemoryDB(database);
                    }
                }
                // could be a null memory db . . .
                //TODO : ask tomcat for a session id . . .
                LoggedInConnection lic = existingConnection(user);
                if (lic!=null){
                    return lic;
                }
                lic = new LoggedInConnection(System.nanoTime() + "", memoryDB, user, timeOutInMinutes * 60 * 1000, spreadsheetName);
                int databaseId = 0;
                if (memoryDB != null && !memoryDB.getDatabase().getName().equals("temp")){
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
                if (!user.getEmail().contains("@demo.") && !user.getEmail().contains("@user.")){
                    //azquoMailer.sendEMail(user.getEmail(),user.getName(),"Login to Azquo", "You have logged into Azquo.");
                }
                connections.put(lic.getConnectionId(), lic);
                if (database != null && lic.getAzquoMemoryDB()!=null){
                    anonymise(lic);
                }


                return lic;
            } // else would be wrong password
        } // else would be email not found
        return null;
    }



    public Map<String, Database> foundDatabases(User user) {
        final List<Permission> userAcceses = permissionDao.findForUserId(user.getId());
        final Map<String, Database> okDatabases = new HashMap<String, Database>();
        if (user.isAdministrator() || user.getEmail().startsWith("demo@user")) { // automatically has all dbs regardless of permission
            for (Database database : databaseDao.findForBusinessId(user.getBusinessId())) {
                if (database.getEndDate().after(new Date())) {
                    okDatabases.put(database.getName(), database);
                }
            }
        } else {
            for (Permission permission : userAcceses) {
                if (permission.getEndDate().after(new Date())) {
                    Database database = databaseDao.findById(permission.getDatabaseId());
                    if (database.getEndDate().after(new Date())) {
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

                }catch(Exception e){

                }
                valueService.randomAdjust(set, low, high);

            }else {
                int count = 1;
                for (Name name : set.getChildren()) {
                    try {
                        name.setTemporaryAttribute(Name.DEFAULT_DISPLAY_NAME, anonName.replace("[nn]", count + ""));
                        count++;
                    } catch (Exception e) {

                    }
                }
            }
        }
    }


    public void zapConnectionsTimedOut() {
        for (Iterator<Map.Entry<String, LoggedInConnection>> it = connections.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LoggedInConnection> entry = it.next();
            LoggedInConnection lic = entry.getValue();
            if ((System.currentTimeMillis() - lic.getLastAccessed().getTime()) > lic.getTimeOut()) {
                // connection timed out
                if (lic.getAzquoMemoryDB() != null) {
                    int databaseId = lic.getAzquoMemoryDB().getDatabase().getId();
                    String dbName = lic.getAzquoMemoryDB().getDatabase().getName();
                    it.remove();
                    if (!dbName.equals("temp")) {
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
    }

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

    private LoggedInConnection existingConnection(final User user){
        if (!user.getEmail().equals("tempuser")){ // edd : not really happy about these sring literlas, fix later . . .
            for (String lic:connections.keySet()){
                LoggedInConnection loggedInConnection = connections.get(lic);
                if (loggedInConnection.getUser().getId()==user.getId()){
                    return loggedInConnection;
                }
            }
        }
        return null;
    }



    public LoggedInConnection getConnection(final String connectionId) {

        zapConnectionsTimedOut();
        final LoggedInConnection lic = connections.get(connectionId);
        if (lic != null) {
            logger.info("last accessed : " + lic.getLastAccessed() + " timeout " + lic.getTimeOut());
            lic.setLastAccessed(new Date());
        }
        return lic;

    }

    public LoggedInConnection getConnectionFromJsonRequest(final StandardJsonRequest standardJsonRequest, String callerIP) throws Exception{
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

}
