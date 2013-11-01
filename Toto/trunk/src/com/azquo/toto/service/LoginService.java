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
    private HashMap<String, LoggedInConnection> connections = new HashMap<String, LoggedInConnection>();


    public LoggedInConnection login(String databaseName, String user, String password){
        // TODO : detect duplicate logins?
        if (databaseName.equalsIgnoreCase("tototest") && user.equalsIgnoreCase("bill") && password.equalsIgnoreCase("thew1password")){
            // just hacking it for the mo
            LoggedInConnection lim = new LoggedInConnection(System.nanoTime() + "" , totoMemoryDB, user);
            connections.put(lim.getConnectionId(), lim);
            return lim;
        } else {
            return null;
        }

    }

    public LoggedInConnection getConnection(String connectionId){

        LoggedInConnection lic = connections.get(connectionId);
        // TODO : timeout here
        lic.setLastAccessed(new Date());
        return lic;

    }

}
