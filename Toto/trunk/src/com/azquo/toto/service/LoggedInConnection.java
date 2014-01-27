package com.azquo.toto.service;

import com.azquo.toto.adminentities.User;
import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import com.azquo.toto.memorydb.Value;

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
    // ok I am gonna allow DB switching
    private TotoMemoryDB totoMemoryDB;
    private final User user;
    private Date loginTime;
    private Date lastAccessed;
    private long timeOut;
    private String language;

    private final Map<String, List<List<Name>>> rowHeadings;
    private final Map<String, List<List<Name>>> columnHeadings;
    private final Map<String, List<Name>> contexts;
    private final Map<String, String> lockMaps;
    private final Map<String, String> sentDataMaps;
    private final Map<String, List<List<List<Value>>>> sentDataValuesMaps; // As in a 2 d array (lists of lists) of lists of valuer Useful for when data is saved
    private final Map<String, List<List<Set<Name>>>> sentDataNamesMaps; // As in a 2 d array (lists of lists) of sets of names, identifying each cell. Necessary if saving new data in that cell. SHould the values map use sets also???

    private static final String defaultRegion = "default-region";

    public LoggedInConnection(final String connectionId, final TotoMemoryDB totoMemoryDB, final User user, final long timeOut){
        this.connectionId = connectionId;
        this.totoMemoryDB = totoMemoryDB;
        this.user = user;
        loginTime = new Date();
        lastAccessed = new Date();
        language = Name.DEFAULT_DISPLAY_NAME;
        rowHeadings = new HashMap<String, List<List<Name>>>();
        columnHeadings = new HashMap<String, List<List<Name>>>();
        contexts = new HashMap<String, List<Name>>();
        lockMaps = new HashMap<String, String>();
        sentDataMaps = new HashMap<String, String>();
        sentDataValuesMaps = new HashMap<String, List<List<List<Value>>>>();
        sentDataNamesMaps = new HashMap<String, List<List<Set<Name>>>>();
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

    protected void setTotoMemoryDB(final TotoMemoryDB totoMemoryDB) {
        this.totoMemoryDB = totoMemoryDB;
    }

    public User getUser() {
        return user;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public String getLanguage() {return language; }

    public long getTimeOut() {
        return timeOut;
    }

    public void setLastAccessed(final Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public void setLanguage(final String language)  {this.language = language; }

    public List<List<Name>> getRowHeadings(final String region) {
        if (region == null || region.isEmpty()){
            return rowHeadings.get(defaultRegion);
        } else {
            return rowHeadings.get(region);
        }
    }

    public void setRowHeadings(final String region,  final List<List<Name>> rowHeadings) {
        if (region == null || region.isEmpty()){
            this.rowHeadings.put(defaultRegion, rowHeadings);
        } else {
            this.rowHeadings.put(region, rowHeadings);
        }
    }

    public List<List<Name>> getColumnHeadings(final String region) {
        if (region == null || region.isEmpty()){
            return columnHeadings.get(defaultRegion);
        } else {
            return columnHeadings.get(region);
        }
    }

    public void setColumnHeadings(final String region,  final List<List<Name>> columnHeadings) {
        if (region == null || region.isEmpty()){
            this.columnHeadings.put(defaultRegion, columnHeadings);
        } else {
            this.columnHeadings.put(region, columnHeadings);
        }
    }

    public List<Name> getContext(final String region) {
        if (region == null || region.isEmpty()){
            return contexts.get(defaultRegion);
        } else {
            return contexts.get(region);
        }
    }

    public void setContext(final String region,  final List<Name> contexts) {
        if (region == null || region.isEmpty()){
            this.contexts.put(defaultRegion, contexts);
        } else {
            this.contexts.put(region, contexts);
        }
    }

    public String getLockMap(final String region) {
        if (region == null || region.isEmpty()){
            return lockMaps.get(defaultRegion);
        } else {
            return lockMaps.get(region);
        }
    }

    public void setLockMap(final String region,  final String lockMap) {
        if (region == null || region.isEmpty()){
            this.lockMaps.put(defaultRegion, lockMap);
        } else {
            this.lockMaps.put(region, lockMap);
        }
    }

    public String getSentDataMap(final String region) {
        if (region == null || region.isEmpty()){
            return sentDataMaps.get(defaultRegion);
        } else {
            return sentDataMaps.get(region);
        }
    }

    public void setSentDataMap(final String region,  final String sentDataMap) {
        if (region == null || region.isEmpty()){
            this.sentDataMaps.put(defaultRegion, sentDataMap);
        } else {
            this.sentDataMaps.put(region, sentDataMap);
        }
    }

    public List<List<List<Value>>> getDataValueMap(final String region) {
        if (region == null || region.isEmpty()){
            return sentDataValuesMaps.get(defaultRegion);
        } else {
            return sentDataValuesMaps.get(region);
        }
    }

    public void setDataValueMap(final String region,  final List<List<List<Value>>> sentDataValuesMap) {
        if (region == null || region.isEmpty()){
            this.sentDataValuesMaps.put(defaultRegion, sentDataValuesMap);
        } else {
            this.sentDataValuesMaps.put(region, sentDataValuesMap);
        }
    }

    public List<List<Set<Name>>> getDataNamesMap(final String region) {
        if (region == null || region.isEmpty()){
            return sentDataNamesMaps.get(defaultRegion);
        } else {
            return sentDataNamesMaps.get(region);
        }
    }

    public void setDataNamesMap(final String region,  final List<List<Set<Name>>> sentDataNamesMap) {
        if (region == null || region.isEmpty()){
            this.sentDataNamesMaps.put(defaultRegion, sentDataNamesMap);
        } else {
            this.sentDataNamesMaps.put(region, sentDataNamesMap);
        }
    }

    // very basic, needs to be improved

    private Provenance provenance = null;


    public Provenance getProvenance() {
        if (provenance == null){
            try{
                provenance = new Provenance(getTotoMemoryDB(),user.getEmail(), new Date(),"method", "spreadsheet name?", "row heading", "column headings", "context");
            } catch (Exception e){
            }
        }
        return provenance;
    }

    public void setNewProvenance(String provenanceMethod, String provenanceName){
       try{
           provenance = new Provenance(getTotoMemoryDB(),user.getEmail(), new Date(),provenanceMethod, provenanceName, "", "", "");
       }catch (Exception e){

       }
    }
}
