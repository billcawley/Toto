package com.azquo.toto.service;

import com.azquo.toto.memorydb.TotoMemoryDB;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:25
 * To change this template use File | Settings | File Templates.
 *
 * ok here I'm pretty much making it up. I would use spring for session management but we can't use cookies here, it's requests from Excel
 * just make it work for the moment, worry about making it neater later. I've moved it to the service package as I want to protect calls to get the database object
 */
public class LoggedInConnection {

    private String connectionId;
    private TotoMemoryDB totoMemoryDB;
    private String userName;
    private Date loginTime;
    private Date lastAccessed;

    public LoggedInConnection(String connectionId, TotoMemoryDB totoMemoryDB, String userName){
        this.connectionId = connectionId;
        this.totoMemoryDB = totoMemoryDB;
        this.userName = userName;
        loginTime = new Date();
        lastAccessed = new Date();
    }

    public String getConnectionId() {
        return connectionId;
    }


    protected TotoMemoryDB getTotoMemoryDB() {
        return totoMemoryDB;
    }

    public String getUserName() {
        return userName;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }
}
