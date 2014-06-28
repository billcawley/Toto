package com.azquo.service;

import com.azquo.adminentities.User;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import com.azquo.memorydb.Value;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:25
 * To change this template use File | Settings | File Templates.
 * <p/>
 * I would use spring for session management but we can't use cookies here, it's requests from Excel. There may be a way around this.
 * I've moved it to the service package as I want to protect calls to get the database object
 * Has become more complex but still fairly simple, useful session stuff is more basic things like the connection id and database and timeout
 * A little more complex ins things like row headings. Used to just be a list but now it's maps (due to multiple regions on the excel sheet)
 * of lists of lists of names. Lists of lists due to mult level headings, e.g. London by container as two column headings above each other (the next one being london not by container)
 * Lockmaps and sent data maps are maps of the actual data sent to excel, this generally is read back by the csv reader
 */
public final class LoggedInConnection {

    private static final Logger logger = Logger.getLogger(LoggedInConnection.class);

    private final String connectionId;
    // ok I am gonna allow DB switching
    private AzquoMemoryDB azquoMemoryDB;
    private final User user;
    private Date loginTime;
    private Date lastAccessed;
    private long timeOut;
    private String language;
    private String spreadsheetName;
    private boolean loose;  // this flag is used to say whether names can be searched in other languages than the current default

    private final Map<String, List<List<Name>>> rowHeadings;
    private final Map<String, List<List<Name>>> columnHeadings;
    private final Map<String, List<Name>> rowHeadingSupplements;//this will allow product classes to be included in the returned row headings, not used to define data.
    private final Map<String, List<Integer>> rowOrder;//for when top or bottom values need to be returned.
    private final Map<String, Integer> restrictCount; //as above
    private final Map<String, Integer> sortCol; //when a region is to be sorted on a particular column
    private final Map<String, List<Name>> contexts;
    private final Map<String, String> lockMaps;
    private final Map<String, String> sentDataMaps;
    private final Map<String, List<List<List<Value>>>> sentDataValuesMaps; // As in a 2 d array (lists of lists) of lists of valuer Useful for when data is saved
    private final Map<String, List<List<Set<Name>>>> sentDataNamesMaps; // As in a 2 d array (lists of lists) of sets of names, identifying each cell. Necessary if saving new data in that cell. SHould the values map use sets also???
    private List<Set<Name>>  readPermissions;
    private List<Set<Name>> writePermissions;
    private AzquoBook azquoBook;

    private static final String defaultRegion = "default-region";

    public LoggedInConnection(final String connectionId, final AzquoMemoryDB azquoMemoryDB, final User user, final long timeOut, String spreadsheetName) {
        this.connectionId = connectionId;
        this.azquoMemoryDB = azquoMemoryDB;
        this.spreadsheetName = spreadsheetName;
        this.user = user;
        loginTime = new Date();
        lastAccessed = new Date();
        language = Name.DEFAULT_DISPLAY_NAME;
        loose = false;
        rowHeadings = new HashMap<String, List<List<Name>>>();
        columnHeadings = new HashMap<String, List<List<Name>>>();
        rowHeadingSupplements = new HashMap<String, List<Name>>();
        rowOrder = new HashMap<String, List<Integer>>();
        restrictCount = new HashMap<String, Integer>();
        sortCol = new HashMap<String, Integer>();
        contexts = new HashMap<String, List<Name>>();
        lockMaps = new HashMap<String, String>();
        sentDataMaps = new HashMap<String, String>();
        sentDataValuesMaps = new HashMap<String, List<List<List<Value>>>>();
        sentDataNamesMaps = new HashMap<String, List<List<Set<Name>>>>();
        readPermissions =  new ArrayList<Set<Name>>();
        writePermissions = new ArrayList<Set<Name>>();
        azquoBook = null;

        if (timeOut > 0) {
            this.timeOut = timeOut;
        } else {
            this.timeOut = 1000 * 60 * 120;
        }

    }

    public String getConnectionId() {
        return connectionId;
    }

    public AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
    }//was 'protected' but need to know if a database is loaded

    public String getCurrentDBName() {
        return azquoMemoryDB.getDatabase().getName();
    }



    protected void setAzquoMemoryDB(final AzquoMemoryDB azquoMemoryDB) {
        this.azquoMemoryDB = azquoMemoryDB;
    }

    public User getUser() {
        return user;
    }

    public int getBusinessId(){
        return user.getBusinessId();
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public String getLanguage() {
        return language;
    }

    public boolean getLoose() {
        return loose;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void setLastAccessed(final Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public void setLoose(final boolean loose) {
        this.loose = loose;
    }

    public List<List<Name>> getRowHeadings(final String region) {
        if (region == null || region.isEmpty()) {
            return rowHeadings.get(defaultRegion);
        } else {
            return rowHeadings.get(region);
        }
    }

    public void setRowHeadings(final String region, final List<List<Name>> rowHeadings) {
        if (region == null || region.isEmpty()) {
            this.rowHeadings.put(defaultRegion, rowHeadings);
        } else {
            this.rowHeadings.put(region, rowHeadings);
        }
    }

    public List<List<Name>> getColumnHeadings(final String region) {
        if (region == null || region.isEmpty()) {
            return columnHeadings.get(defaultRegion);
        } else {
            return columnHeadings.get(region);
        }
    }

    public void setColumnHeadings(final String region, final List<List<Name>> columnHeadings) {
        if (region == null || region.isEmpty()) {
            this.columnHeadings.put(defaultRegion, columnHeadings);
        } else {
            this.columnHeadings.put(region, columnHeadings);
        }
    }

    public List<Name> getRowHeadingSupplements(final String region) {
        if (region == null || region.isEmpty()) {
            return rowHeadingSupplements.get(defaultRegion);
        } else {
            return rowHeadingSupplements.get(region);
        }
    }

    public void setRowHeadingSupplements(final String region, final List<Name> rowHeadingSupplements) {
        if (region == null || region.isEmpty()) {
            this.rowHeadingSupplements.put(defaultRegion, rowHeadingSupplements);
        } else {
            this.rowHeadingSupplements.put(region, rowHeadingSupplements);
        }

    }

    public List<Integer> getRowOrder(final String region) {
        if (region == null || region.isEmpty()) {
            return rowOrder.get(defaultRegion);
        } else {
            return rowOrder.get(region);
        }
    }

      public void setRowOrder(final String region, final List<Integer> rowOrder){
          if (region == null || region.isEmpty()) {
              this.rowOrder.put(defaultRegion, rowOrder);
          } else {
              this.rowOrder.put(region, rowOrder);
          }

      }

    public Integer getRestrictCount(final String region) {
        if (region == null || region.isEmpty()) {
            return restrictCount.get(defaultRegion);
        } else {
            return restrictCount.get(region);
        }
    }

    public void setRestrictCount(final String region, final int restrictCount){
        if (region == null || region.isEmpty()) {
            this.restrictCount.put(defaultRegion, restrictCount);
        } else {
            this.restrictCount.put(region, restrictCount);
        }

    }

    public Integer getSortCol(final String region) {
        if (region == null || region.isEmpty()) {
            return sortCol.get(defaultRegion);
        } else {
            return sortCol.get(region);
        }
    }

    public void setSortCol(final String region, final int sortCol){
        if (region == null || region.isEmpty()) {
            this.sortCol.put(defaultRegion, sortCol);
        } else {
            this.sortCol.put(region, sortCol);
        }

    }


    public List<Name> getContext(final String region) {
        if (region == null || region.isEmpty()) {
            return contexts.get(defaultRegion);
        } else {
            return contexts.get(region);
        }
    }

    public void setContext(final String region, final List<Name> contexts) {
        if (region == null || region.isEmpty()) {
            this.contexts.put(defaultRegion, contexts);
        } else {
            this.contexts.put(region, contexts);
        }
    }

    public String getLockMap(final String region) {
        if (region == null || region.isEmpty()) {
            return lockMaps.get(defaultRegion);
        } else {
            return lockMaps.get(region);
        }
    }

    public void setLockMap(final String region, final String lockMap) {
        if (region == null || region.isEmpty()) {
            this.lockMaps.put(defaultRegion, lockMap);
        } else {
            this.lockMaps.put(region, lockMap);
        }
    }

    public String getSentDataMap(final String region) {
        if (region == null || region.isEmpty()) {
            return sentDataMaps.get(defaultRegion);
        } else {
            return sentDataMaps.get(region);
        }
    }

    public void setSentDataMap(final String region, final String sentDataMap) {
        if (region == null || region.isEmpty()) {
            this.sentDataMaps.put(defaultRegion, sentDataMap);
        } else {
            this.sentDataMaps.put(region, sentDataMap);
        }
    }

    public List<List<List<Value>>> getDataValueMap(final String region) {
        if (region == null || region.isEmpty()) {
            return sentDataValuesMaps.get(defaultRegion);
        } else {
            return sentDataValuesMaps.get(region);
        }
    }

    public void setDataValueMap(final String region, final List<List<List<Value>>> sentDataValuesMap) {
        if (region == null || region.isEmpty()) {
            this.sentDataValuesMaps.put(defaultRegion, sentDataValuesMap);
        } else {
            this.sentDataValuesMaps.put(region, sentDataValuesMap);
        }
    }

    public List<List<Set<Name>>> getDataNamesMap(final String region) {
        if (region == null || region.isEmpty()) {
            return sentDataNamesMaps.get(defaultRegion);
        } else {
            return sentDataNamesMaps.get(region);
        }
    }

    public void setDataNamesMap(final String region, final List<List<Set<Name>>> sentDataNamesMap) {
        if (region == null || region.isEmpty()) {
            this.sentDataNamesMaps.put(defaultRegion, sentDataNamesMap);
        } else {
            this.sentDataNamesMaps.put(region, sentDataNamesMap);
        }
    }

    // very basic, needs to be improved

    private Provenance provenance = null;


    public Provenance getProvenance() {
        if (provenance == null) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), user.getName(), new Date(), "in spreadsheet", spreadsheetName, "row heading", "column headings", "context");
            } catch (Exception e) {
            }
        }
        return provenance;
    }

    public void setNewProvenance(String provenanceMethod, String provenanceName){
        setNewProvenance(provenanceMethod, provenanceName,"","","");
    }

    public void setNewProvenance(String provenanceMethod, String provenanceName, String rowHeadings, String columnHeadings, String context) {
        try {
            provenance = new Provenance(getAzquoMemoryDB(), user.getName(), new Date(), provenanceMethod, provenanceName, rowHeadings, columnHeadings, context);
        } catch (Exception e) {
            logger.error("can't set a new provenance", e);
        }
    }

    public List<Set<Name>> getReadPermissions(){
        return this.readPermissions;
    }

    public void setReadPermissions(List<Set<Name>> names){
        this.readPermissions = names;
    }

    public List<Set<Name>> getWritePermissions(){
        return this.writePermissions;
    }

    public void setWritePermissions(List<Set<Name>> names){
        this.writePermissions = names;
    }

    public AzquoBook getAzquoBook() { return this.azquoBook; }

    public void setAzquoBook(AzquoBook azquoBook){    this.azquoBook = azquoBook;  }



}

