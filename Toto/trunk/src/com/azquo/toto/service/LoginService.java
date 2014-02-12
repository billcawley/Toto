package com.azquo.toto.service;

import com.azquo.toto.admindao.PermissionDAO;
import com.azquo.toto.admindao.DatabaseDAO;
import com.azquo.toto.admindao.UserDAO;
import com.azquo.toto.adminentities.Permission;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.adminentities.User;
import com.azquo.toto.jsonrequestentities.StandardJsonRequest;
import com.azquo.toto.memorydb.MemoryDBManager;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Currently fairly simple login functions
 */
public class LoginService {

    @Autowired
    private MemoryDBManager memoryDBManager;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private PermissionDAO permissionDao;
    @Autowired
    private DatabaseDAO databaseDao;
    @Autowired
    private AdminService adminService;

    private final HashMap<String, LoggedInConnection> connections = new HashMap<String, LoggedInConnection>();


    public LoggedInConnection login(final String databaseName, final String userEmail, final String password, final int timeOutInMinutes) {

        // right, need an actual login process here!

    /*                String salt = PasswordUtils.shaHash(System.currentTimeMillis() + "salt");
    v.setPasswordSalt(salt);
    v.setPassword(PasswordUtils.encrypt(password, salt)); // new better encryption . . .*/

        User user = userDao.findByEmail(userEmail);
        if (user != null) {
            if (adminService.encrypt(password, user.getSalt()).equals(user.getPassword())) {
                // ok user should be ok :)
                final List<Permission> userAcceses = permissionDao.findForUserId(user.getId());
                final Map<String, Database> okDatabases = new HashMap<String, Database>();
                if (user.isAdministrator()) { // automatically has all dbs regardless of permission
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
                System.out.println("ok databases size " + okDatabases.size());
                TotoMemoryDB memoryDB = null;
                if (okDatabases.size() == 1) {
                    System.out.println("1 database, use that");
                    memoryDB = memoryDBManager.getTotoMemoryDB(okDatabases.values().iterator().next());
                } else {
                    Database database = okDatabases.get(databaseName);
                    if (database != null) {
                        memoryDB = memoryDBManager.getTotoMemoryDB(database);
                    }
                }
                // could be a null memory db . . .
                //TODO : ask tomcat for a session id . . .
                final LoggedInConnection lic = new LoggedInConnection(System.nanoTime() + "", memoryDB, user, timeOutInMinutes * 60 * 1000);
                connections.put(lic.getConnectionId(), lic);
                return lic;
            } // else would be wrong password
        } // else would be email not found
        return null;
    }

    public LoggedInConnection getConnection(final String connectionId) {

        final LoggedInConnection lic = connections.get(connectionId);
        if (lic != null) {
            System.out.println("last accessed : " + lic.getLastAccessed() + " timeout " + lic.getTimeOut());
            if ((System.currentTimeMillis() - lic.getLastAccessed().getTime()) > lic.getTimeOut()) {
                // connection timed out
                connections.remove(lic.getConnectionId());
                return null;
            }
            lic.setLastAccessed(new Date());
        }
        return lic;

    }

    public LoggedInConnection getConnectionFromJsonRequest(final StandardJsonRequest standardJsonRequest) {
        if (standardJsonRequest.user != null && standardJsonRequest.user.length() > 0 &&
                standardJsonRequest.password != null && standardJsonRequest.password.length() > 0) {
            return login(standardJsonRequest.database == null ? "" : standardJsonRequest.database, standardJsonRequest.user, standardJsonRequest.password, 60);
        } else if (standardJsonRequest.connectionId != null && standardJsonRequest.connectionId.length() > 0) {
            return getConnection(standardJsonRequest.connectionId);
        }
        return null;
    }

}
