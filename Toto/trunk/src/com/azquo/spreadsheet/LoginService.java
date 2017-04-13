package com.azquo.spreadsheet;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
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

    public static LoggedInUser loginLoggedInUser(final String sessionId, final String databaseName, final String userEmail, final String password, boolean loggedIn) throws Exception {
        return loginLoggedInUser(sessionId, databaseName, userEmail, password, null, loggedIn);
    }


     public static LoggedInUser loginLoggedInUser(final String sessionId, String databaseName, final String userEmail, final String password, final String reportName, boolean loggedIn) throws Exception {
        User user;

        //for demo users, a new User id is made for each user.
        if (userEmail.startsWith("demo@user.com")) {
            user = UserDAO.findByEmail(userEmail);
            if (user == null) {
                user = UserDAO.findByEmail("demo@user.com");
                if (user != null) {
                    user.setEmail(userEmail);
                    user.setId(0);
                    UserDAO.store(user);
                }
            }
        } else {
            user = UserDAO.findByEmail(userEmail);
        }
         if (reportName !=null && reportName.length() > 0 && user!=null){
             if (reportName.equals("unknown")){
                 List<Database>dbs = DatabaseDAO.findForUserId(user.getId());

                 if (dbs.size()> 0){
                     for (Database db:dbs){
                          databaseName += db.getName() + ",";
                     }
                 }
             }else {
                 OnlineReport onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, user.getBusinessId());
                 if (onlineReport != null) {
                     List<Integer> dblist = DatabaseReportLinkDAO.getDatabaseIdsForReportId(onlineReport.getId());
                     if (dblist.size() > 0) {
                         for (Integer dbNo:dblist){
                              databaseName += DatabaseDAO.findById(dbNo).getName() + ",";
                         }
                     }
                 }
             }
          }

         //boolean temporary = false;
        if (user != null && (loggedIn || AdminService.encrypt(password.trim(), user.getSalt()).equals(user.getPassword()))) {
            return loginLoggedInUser(sessionId, databaseName, user);
        }
        return null;
    }

    // todo - what to do about database here, it's not ideal and based on the old model. Also inline the function?

    private static LoggedInUser loginLoggedInUser(final String sessionId, String databaseNames, final User user) throws Exception {
        Database database = null;
        String databaseName = "";
        // ok user should be ok :)
        if (databaseNames!=null && databaseNames.length() > 0) {
            databaseName = databaseNames.substring(0,databaseNames.indexOf(","));
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
        }

        Business b = BusinessDAO.findById(user.getBusinessId());
        if (b == null) {
            throw new Exception("Business not found for user! Business id : " + user.getBusinessId());
        }
        String businessDirectory = (b.getBusinessName() + "                    ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
        LoggedInUser loggedInUser = new LoggedInUser(sessionId, user, databaseServer, database, null, null, null, businessDirectory);// null the read/write list for the mo
        loggedInUser.setDbNames(databaseNames);
        if (loggedInUser.getUser().getId() != 25) { // stop recording Nic's logins which are also used by the monitoring software!
            LoginRecordDAO.store(new LoginRecord(0, user.getId(), database != null ? database.getId() : 0, new Date()));
        }
        // I zapped something to do with anonymising here, don't know if it's still relevant
        return loggedInUser;
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

    public static void switchDatabase(LoggedInUser loggedInUser, Database db) throws Exception {
        if (db != null && db.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
            DatabaseServer databaseServer = DatabaseServerDAO.findById(db.getDatabaseServerId());
            loggedInUser.setDatabaseWithServer(databaseServer, db);
        }
    }


}