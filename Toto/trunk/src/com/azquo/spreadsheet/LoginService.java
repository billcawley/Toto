package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.user.*;
import com.azquo.admin.onlinereport.OnlineReportDAO;
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
    private UserDAO userDao;
    @Autowired
    private LoginRecordDAO loginRecordDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private PermissionDAO permissionDao;
    @Autowired
    private DatabaseDAO databaseDao;
    @Autowired
    private AdminService adminService;

    private final HashMap<Integer, Integer> openDBCount = new HashMap<Integer, Integer>();

    // like the two above but for new object that does not reference the memory DB objects. Is the demo stuff still important??
    // very similar to top function, proxies through to a different one

    public LoggedInUser loginLoggedInUser(final String databaseName, final String userEmail, final String password, String spreadsheetName, boolean loggedIn) throws Exception {

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
            return loginLoggedInUser(databaseName, user);
        }
        return null;
    }

    private LoggedInUser loginLoggedInUser(final String databaseName, final User user) throws Exception {
        // ok user should be ok :)
        final Map<String, Database> okDatabases = foundDatabases(user);
        logger.info("ok databases size " + okDatabases.size() + " user " + user.getEmail());
        Database database;
        if (okDatabases.size() == 1) {
            logger.info("1 database, use that");
            database = okDatabases.values().iterator().next();
            /* do we need to add this back in via proxy?
            memoryDB = memoryDBManager.getAzquoMemoryDB(database);
            if (database.getName().equals("temp")) {
                memoryDB.zapDatabase();//to be on the safe side and avoid any persistance
            }*/
        } else {
            database = okDatabases.get(databaseName);
        }
        Permission permission = null;
        if (database != null) {
            permission = permissionDao.findByBusinessUserAndDatabase(user, database);
        }
        LoggedInUser loggedInUser = new LoggedInUser(user,database, permission != null ? permission.getReadList() : null, permission != null ? permission.getWriteList() : null);
        loginRecordDAO.store(new LoginRecord(0, user.getId(), database != null ? database.getId() : 0, new Date()));
        // I zapped something to do with anonymising here, don't know if it's still relevant
        return loggedInUser;
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

    public void switchDatabase(LoggedInUser loggedInUser, String newDBName) throws Exception {
        Database db = null;
        if (newDBName != null && newDBName.length() != 0) {
            db = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), newDBName);
            if (db == null) {
                throw new Exception(newDBName + " - no such database");
            }
        }
        switchDatabase(loggedInUser, db);
    }

    public void switchDatabase(LoggedInUser loggedInUser, Database db) throws Exception {
        loggedInUser.setDatabase(db);

        /* ok all this opencount etc stuff is going to have to go for the moment
        if (azquoMemoryDBConnection.getAzquoMemoryDB() != null) {
            Database oldDB = azquoMemoryDBConnection.getAzquoMemoryDB().getDatabase();
            if (newDb != null && newDb.getName().equals("temp")) {
            //don't switch to a temporary connection if you've been moved off it
                return;
            }
            if (newDb != null && oldDB.getName().equals(newDb.getName())) return;
            int databaseId = azquoMemoryDBConnection.getAzquoMemoryDB().getDatabase().getId();
            Integer openCount = openDBCount.get(databaseId);
            if (newDb == null)
                openCount = 1;//if we're deleting the database, then close the memory, regardless of whether others have it open.
            //todo - confirm where this is used! Seems dangerous . . .
            if (openCount != null && openCount == 1) {
                memoryDBManager.removeDatabase(azquoMemoryDBConnection.getAzquoMemoryDB().getDatabase());
                openDBCount.remove(databaseId);
                openDatabaseDAO.closeForDatabaseId(databaseId);
            } else if (openCount != null && openCount > 1) {
                openDBCount.put(databaseId, openCount - 1);
            }
        }
        if (newDb == null) {
            azquoMemoryDBConnection.setAzquoMemoryDB(null);
            return;
        }
        AzquoMemoryDB memoryDB = memoryDBManager.getAzquoMemoryDB(newDb);
        int databaseId = memoryDB.getDatabase().getId();
        Integer openCount = openDBCount.get(databaseId);
        if (openCount != null) {
            openDBCount.put(databaseId, openCount + 1);
        } else {
            openDBCount.put(databaseId, 1);
        }
        azquoMemoryDBConnection.setAzquoMemoryDB(memoryDB);*/
    }

}
