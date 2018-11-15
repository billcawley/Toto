package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.user.*;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Currently fairly simple login functions
 */

public class LoginService {

    private static final Logger logger = Logger.getLogger(LoginService.class);

    public static LoggedInUser loginLoggedInUser(final String sessionId, String databaseName, final String userEmail, final String password, boolean loggedIn) throws Exception {
        User user = UserDAO.findByEmail(userEmail);
        if (user != null && (loggedIn || AdminService.encrypt(password.trim(), user.getSalt()).equals(user.getPassword()))) {
            Database database = null;
            if (databaseName == null){
                databaseName = "";
            }
            // new logic run regardless of whether we were passed a db as we want to default if there's only one DB (this was lost and it knackered some magento uploads)
            if (user.isAdministrator()) {
                final List<Database> forBusinessId = DatabaseDAO.findForBusinessId(user.getBusinessId());
                if (forBusinessId.size() == 1) {
                    database = forBusinessId.get(0);
                } else {
                    for (Database database1 : forBusinessId) {
                        if (database1.getName().equalsIgnoreCase(databaseName)) {
                            database = database1;
                            break;
                        }
                    }
                }
            } else if (user.isDeveloper()) {// a bit wordy? can perhaps be factored later
                final List<Database> forBusinessId = DatabaseDAO.findForBusinessId(user.getBusinessId());
                if (forBusinessId.size() == 1 && forBusinessId.get(0).getUserId() == user.getId()) {
                    database = forBusinessId.get(0);
                } else {
                    for (Database database1 : forBusinessId) {
                        if (database1.getName().equalsIgnoreCase(databaseName) && database1.getUserId() == user.getId()) {
                            database = database1;
                            break;
                        }
                    }
                }
            }
            if (database == null) { // now we have a DB against the user we could perhaps remove the defaults to a single one above? Perhaps different for admin or developer
                database = DatabaseDAO.findById(user.getDatabaseId());
            }

            DatabaseServer databaseServer = null;
            if (database != null) {
                databaseServer = DatabaseServerDAO.findById(database.getDatabaseServerId());
            } else {
                databaseServer = DatabaseServerDAO.findAll().get(0); // bit of a hack, currently we don't deal with selecting different servers
            }

            Business b = BusinessDAO.findById(user.getBusinessId());
            if (b == null) {
                throw new Exception("Business not found for user! Business id : " + user.getBusinessId());
            }
            return new LoggedInUser(sessionId, user, databaseServer, database, null, b.getBusinessDirectory());// null the read/write list for the mo
        }
        return null;
    }


    // basic business match check on these functions
    // todo address insecurity on these two, problem is that some non admin or developer user stuff is ad hoc based off reports. Hence if I try and do security here it won't work . . .

    public static void switchDatabase(LoggedInUser loggedInUser, String newDBName) throws Exception {
        Database db = null;
        if (newDBName != null && newDBName.length() != 0) {
            db = DatabaseDAO.findForNameAndBusinessId(newDBName, loggedInUser.getUser().getBusinessId());
            if (db == null) {
                throw new Exception(newDBName + " - no such database");
            }
        }
        switchDatabase(loggedInUser, db);
    }

    // we used to record open counts, this will need to be dealt with server side

    public static void switchDatabase(LoggedInUser loggedInUser, Database db) {
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            DatabaseServer databaseServer = DatabaseServerDAO.findById(db.getDatabaseServerId());
            loggedInUser.setDatabaseWithServer(databaseServer, db);
        }
    }
}