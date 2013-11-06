package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.TotoMemoryDB;

import java.util.*;

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
public final class LoggedInConnection {

    private final String connectionId;
    private final TotoMemoryDB totoMemoryDB;
    private final String userName;
    private Date loginTime;
    private Date lastAccessed;
    private long timeOut;

    private final Map<String, List<Name>> rowHeadings;
    private final Map<String, List<Name>> columnHeadings;

    private static final String defaultRegion = "default-region";

    public LoggedInConnection(final String connectionId, final TotoMemoryDB totoMemoryDB, final String userName, long timeOut){
        this.connectionId = connectionId;
        this.totoMemoryDB = totoMemoryDB;
        this.userName = userName;
        loginTime = new Date();
        lastAccessed = new Date();
        rowHeadings = new HashMap<String, List<Name>>();
        columnHeadings = new HashMap<String, List<Name>>();
        if (timeOut > 0){
            this.timeOut = timeOut;
        } else {
            this.timeOut = 1000 *60 * 120;
        }
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

    public long getTimeOut() {
        return timeOut;
    }

    public void setLastAccessed(final Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public List<Name> getRowHeadings(final String region) {
        if (region == null){
            return rowHeadings.get(defaultRegion);
        } else {
            return rowHeadings.get(region);
        }
    }

    public void setRowHeadings(final String region,  final List<Name> rowHeadings) {
        if (region == null){
            this.rowHeadings.put(defaultRegion, rowHeadings);
        } else {
            this.rowHeadings.put(region, rowHeadings);
        }
    }

    public List<Name> getColumnHeadings(final String region) {
        if (region == null){
            return columnHeadings.get(defaultRegion);
        } else {
            return columnHeadings.get(region);
        }
    }

    public void setColumnHeadings(final String region,  final List<Name> columnHeadings) {
        if (region == null){
            this.columnHeadings.put(defaultRegion, columnHeadings);
        } else {
            this.columnHeadings.put(region, columnHeadings);
        }
    }

}
