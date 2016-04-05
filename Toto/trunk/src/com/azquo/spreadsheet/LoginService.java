package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.admin.user.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
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
    private DatabaseServerDAO databaseServerDao;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private PermissionDAO permissionDao;
    @Autowired
    private DatabaseDAO databaseDao;
    @Autowired
    private AdminService adminService;

    public LoggedInUser loginLoggedInUser(final String sessionId, final String databaseName, final String userEmail, final String password, boolean loggedIn) throws Exception {
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
            return loginLoggedInUser(sessionId, databaseName, user);
        }
        return null;
    }

    private LoggedInUser loginLoggedInUser(final String sessionId, final String databaseName, final User user) throws Exception {
        // ok user should be ok :)
        final Map<String, Database> okDatabases = foundDatabases(user);
        logger.info("ok databases size " + okDatabases.size() + " user " + user.getEmail());
        Database database;
        if (okDatabases.size() == 1) {
            logger.info("1 database, use that");
            database = okDatabases.values().iterator().next();
        } else {
            database = okDatabases.get(databaseName);
        }
        Permission permission = null;
        if (database != null) {
            permission = permissionDao.findByBusinessUserAndDatabase(user, database);
        }
        DatabaseServer databaseServer = null;
        if (database != null){
            databaseServer = databaseServerDao.findById(database.getDatabaseServerId());
        }
        LoggedInUser loggedInUser = new LoggedInUser(sessionId, user,databaseServer,database, permission != null ? permission.getReadList() : null, permission != null ? permission.getWriteList() : null, null);
        if (loggedInUser.getUser().getId() != 25){ // stop recording Nic's logins which are also used by the monitoring software!
            loginRecordDAO.store(new LoginRecord(0, user.getId(), database != null ? database.getId() : 0, new Date()));
        }
        // I zapped something to do with anonymising here, don't know if it's still relevant
        return loggedInUser;
    }

    // todo - switch to using a list not a map?

    public Map<String, Database> foundDatabases(User user) {
        final List<Permission> userAcceses = permissionDao.findForUserId(user.getId());
        final Map<String, Database> okDatabases = new HashMap<>();
        if (user.isAdministrator() || user.getEmail().startsWith("demo@user")) { // automatically has all dbs regardless of permission
            for (Database database : databaseDao.findForBusinessId(user.getBusinessId())) {
                    okDatabases.put(database.getName(), database);
            }
        } else {
            for (Permission permission : userAcceses) {
                if (permission.getEndDate().isAfter(LocalDateTime.now())) {
                    Database database = databaseDao.findById(permission.getDatabaseId());
                    if (database != null){
                        okDatabases.put(database.getName(), database);
                    }
                }
            }
        }
        return okDatabases;
    }

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

    // we used to record open counts, this will need to be dealt with server side

    public void switchDatabase(LoggedInUser loggedInUser, Database db) throws Exception {
        DatabaseServer databaseServer = null;
        if (db != null){
            databaseServer = databaseServerDao.findById(db.getDatabaseServerId());
        }
        loggedInUser.setDatabaseWithServer(databaseServer, db);
    }
}