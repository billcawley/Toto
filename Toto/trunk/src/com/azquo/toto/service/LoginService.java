package com.azquo.toto.service;

import com.azquo.toto.admindao.AccessDAO;
import com.azquo.toto.admindao.DatabaseDAO;
import com.azquo.toto.admindao.UserDAO;
import com.azquo.toto.adminentities.Access;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.adminentities.User;
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
 * Will be used to validate credentials and to track the sessions
 */
public class LoginService {

    @Autowired
    private MemoryDBManager memoryDBManager;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private AccessDAO accessDao;
    @Autowired
    private DatabaseDAO databaseDao;

    private final HashMap<String, LoggedInConnection> connections = new HashMap<String, LoggedInConnection>();


    public LoggedInConnection login(final String databaseName, final String userEmail, final String password, int timeOutInMinutes){

        // right, need an actual login process here!

    /*                String salt = PasswordUtils.shaHash(System.currentTimeMillis() + "salt");
    v.setPasswordSalt(salt);
    v.setPassword(PasswordUtils.encrypt(password, salt)); // new better encryption . . .*/

        User user = userDao.findByEmail(userEmail);

        if (user != null){
            if (AdminService.encrypt(password, user.getSalt()).equals(user.getPassword())){
                // ok user should be ok :)
                List<Access> userAccess = accessDao.findForUserId(user.getId());
                Map<String, Database> okDatabases = new HashMap<String, Database>();

                for (Access access : userAccess){
                    if (access.getActive()){
                        Database database = databaseDao.findById(access.getDatabaseId());
                        if (database.getActive()){
                            okDatabases.put(database.getName(), database);
                        }
                    }
                }

                if (okDatabases.size() == 1){
                    final LoggedInConnection lic = new LoggedInConnection(System.nanoTime() + "" , memoryDBManager.getTotoMemoryDB(okDatabases.values().iterator().next()), user, timeOutInMinutes * 60 * 1000);
                    connections.put(lic.getConnectionId(), lic);
                    return lic;
                } else {
                    Database database = okDatabases.get(databaseName);
                    if (database != null){
                        final LoggedInConnection lic = new LoggedInConnection(System.nanoTime() + "" , memoryDBManager.getTotoMemoryDB(okDatabases.values().iterator().next()), user, timeOutInMinutes * 60 * 1000);
                        connections.put(lic.getConnectionId(), lic);
                        return lic;
                    }
                }

            } else {
                // say wrong password??
            }

        } else {
            // tell them email not found???
        }
        return null;
    }

    public LoggedInConnection getConnection(String connectionId){

        final LoggedInConnection lic = connections.get(connectionId);
        if (lic != null){
            System.out.println("last accessed : " + lic.getLastAccessed() + " timeout " + lic.getTimeOut());
            if ((System.currentTimeMillis() - lic.getLastAccessed().getTime()) > lic.getTimeOut()){
                // connection timed out
                connections.remove(lic.getConnectionId());
                return null;
            }
            lic.setLastAccessed(new Date());
        }
        return lic;

    }

}
