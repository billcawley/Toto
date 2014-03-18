package com.azquo.service;

import com.azquo.admindao.LoginRecordDAO;
import com.azquo.admindao.PermissionDAO;
import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.UserDAO;
import com.azquo.adminentities.LoginRecord;
import com.azquo.adminentities.Permission;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.User;
import com.azquo.jsonrequestentities.StandardJsonRequest;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.MemoryDBManager;
import com.azquo.memorydb.Name;
import com.azquo.util.AzquoMailer;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

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

    private final HashMap<String, LoggedInConnection> connections = new HashMap<String, LoggedInConnection>();


    public LoggedInConnection login(final String databaseName, final String userEmail, final String password, final int timeOutInMinutes, String spreadsheetName, boolean loggedIn) throws  Exception{

        if (spreadsheetName == null) {
            spreadsheetName = "unknown";
        }

        User user = userDao.findByEmail(userEmail);
        if (user != null) {
            if (loggedIn || adminService.encrypt(password, user.getSalt()).equals(user.getPassword())) {
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
                final LoggedInConnection lic = new LoggedInConnection(System.nanoTime() + "", memoryDB, user, timeOutInMinutes * 60 * 1000, spreadsheetName);
                int databaseId  = 0;
                if (memoryDB != null){
                    databaseId = memoryDB.getDatabase().getId();
                }
                Permission permission = null;
                if (database != null){
                    permission = permissionDao.findByBusinessUserAndDatabase(lic.getUser(),database);
                }
                List<Set<Name>> names = new ArrayList<Set<Name>>();
                if (permission != null){
                     String error = nameService.decodeString(lic,permission.getReadList(), names);
                     //TODO HANDLE ERROR.  should not be any unless names have been changed since storing
                }
                lic.setReadPermissions(names);
                names = new ArrayList<Set<Name>>();
                if (permission != null){
                    String error = nameService.decodeString(lic,permission.getWriteList(), names);
                    //TODO HANDLE ERROR.  should not be any unless names have been changed since storing
                }
                lic.setWritePermissions(names);

                loginRecordDAO.store(new LoginRecord(0,user.getId(),databaseId, new Date()));
                if (!user.getEmail().contains("@demo.") && !user.getEmail().contains("@user.")){
                    azquoMailer.sendEMail(user.getEmail(),user.getName(),"Login to Azquo", "You have logged into Azquo.");
                }
                connections.put(lic.getConnectionId(), lic);
                return lic;
            } // else would be wrong password
        } // else would be email not found
        return null;
    }

    public LoggedInConnection getConnection(final String connectionId) {

        final LoggedInConnection lic = connections.get(connectionId);
        if (lic != null) {
            logger.info("last accessed : " + lic.getLastAccessed() + " timeout " + lic.getTimeOut());
            if ((System.currentTimeMillis() - lic.getLastAccessed().getTime()) > lic.getTimeOut()) {
                // connection timed out
                connections.remove(lic.getConnectionId());
                return null;
            }
            lic.setLastAccessed(new Date());
        }
        return lic;

    }

    public LoggedInConnection getConnectionFromJsonRequest(final StandardJsonRequest standardJsonRequest) throws Exception{
        if (standardJsonRequest.user != null && standardJsonRequest.user.length() > 0 &&
                standardJsonRequest.password != null && standardJsonRequest.password.length() > 0) {
            return login(standardJsonRequest.database == null ? "" : standardJsonRequest.database, standardJsonRequest.user, standardJsonRequest.password, 60, standardJsonRequest.spreadsheetName, false);
        } else if (standardJsonRequest.connectionId != null && standardJsonRequest.connectionId.length() > 0) {
            return getConnection(standardJsonRequest.connectionId);
        }
        return null;
    }

}
