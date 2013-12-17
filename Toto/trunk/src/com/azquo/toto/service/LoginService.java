package com.azquo.toto.service;

import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Will be used to validate credentials and to track the sessions
 */
public class LoginService {

    @Autowired
    private TotoMemoryDB totoMemoryDB;
    @Autowired
    private TotoMemoryDB IMFMemoryDB;

    private final HashMap<String, LoggedInConnection> connections = new HashMap<String, LoggedInConnection>();


    public LoggedInConnection login(final String databaseName, final String user, final String password, int timeOutInMinutes){
        // TODO : detect duplicate logins?
        if (user.equalsIgnoreCase("bill") && password.equalsIgnoreCase("b1ll")){
            // just hacking it for the mo
            final LoggedInConnection lim = new LoggedInConnection(System.nanoTime() + "" , totoMemoryDB, user, timeOutInMinutes * 60 * 1000);
            connections.put(lim.getConnectionId(), lim);
            return lim;
        } else if (databaseName.equalsIgnoreCase("totoexport") && user.equalsIgnoreCase("edd") && password.equalsIgnoreCase("edd123")){
            // just hacking it for the mo
            final LoggedInConnection lim = new LoggedInConnection(System.nanoTime() + "" , IMFMemoryDB, user, timeOutInMinutes * 60 * 1000);
            connections.put(lim.getConnectionId(), lim);
            return lim;
        } else {
            return null;
        }

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
