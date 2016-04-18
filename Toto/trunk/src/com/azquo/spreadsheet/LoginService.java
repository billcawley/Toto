package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
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
    private BusinessDAO businessDAO;
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

    // todo - what to do about database here, it's not ideal and based on the old model

    private LoggedInUser loginLoggedInUser(final String sessionId, final String databaseName, final User user) throws Exception {
        Database database = null;
        // ok user should be ok :)
        Permission permission = null;
        if (databaseName != null && databaseName.length() > 0){ // trying to set a db, how often?
            if (user.isAdministrator()){
                final List<Database> forBusinessId = databaseDAO.findForBusinessId(user.getBusinessId());
                for (Database database1 : forBusinessId){
                    if (database1.getName().equalsIgnoreCase(databaseName)){
                        database = database1;
                        break;
                    }
                }
            } else { // try and do it by permission - should we allow this at all for non admin users? todo - is the logic here correct, I need both the db and permissions, right now it feels like a double look up
                final List<Database> forBusinessId = databaseDAO.findForUserIdViaPermission(user.getId());
                for (Database database1 : forBusinessId){
                    if (database1.getName().equalsIgnoreCase(databaseName)){
                        database = database1;
                        permission = permissionDao.findByBusinessUserAndDatabase(user, database);
                        break;
                    }
                }
            }
        }
        DatabaseServer databaseServer = null;
        if (database != null){
            databaseServer = databaseServerDao.findById(database.getDatabaseServerId());
        }

        Business b = businessDAO.findById(user.getBusinessId());
        if (b == null){
            throw new Exception("Business not found for user! Business id : " + user.getBusinessId());
        }
        String businessDirectory = (b.getBusinessName() + "               ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
        LoggedInUser loggedInUser = new LoggedInUser(sessionId, user,databaseServer,database, permission != null ? permission.getReadList() : null, permission != null ? permission.getWriteList() : null, null, businessDirectory);
        if (loggedInUser.getUser().getId() != 25){ // stop recording Nic's logins which are also used by the monitoring software!
            loginRecordDAO.store(new LoginRecord(0, user.getId(), database != null ? database.getId() : 0, new Date()));
        }
        // I zapped something to do with anonymising here, don't know if it's still relevant
        return loggedInUser;
    }

    // basic business match check on these functions

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
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()){
            DatabaseServer databaseServer = databaseServerDao.findById(db.getDatabaseServerId());
            loggedInUser.setDatabaseWithServer(databaseServer, db);
        }
    }
}