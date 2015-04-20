package com.azquo.spreadsheet;

import com.azquo.admin.user.User;
import com.azquo.memorydb.*;
import com.azquo.spreadsheet.view.AzquoBook;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created with IntelliJ IDEA.AzquoMemoryDBContainer
 * User: cawley
 * Date: 31/10/13
 * Time: 19:25
 * To change this template use File | Settings | File Templates.
 * <p/>
 * A little more complex ins things like row headings. Used to just be a list but now it's maps (due to multiple regions on the excel sheet)
 * of lists of lists of names. Lists of lists due to mult level headings, e.g. London by container as two column headings above each other (the next one being london not by container)
 * Lockmaps and sent data maps are maps of the actual data sent to excel, this generally is read back by the csv reader
 *
 * Not thread safe really although it should be one per session. As in multiple tabs or fast refreshes could cause problems - that's something to look into.
 *
 * Since Excel is no longer the priority this class might be a bit different if rewritten - I'm going to be working on this, row and column heading for example may be removed.
 *
 *
 *
 *
 */
public final class LoggedInConnection extends AzquoMemoryDBConnection {

    public static final class NameOrValue{
        public Name name;
        public Set<Value> values;
    }

    public static final class JsTreeNode{
        public NameOrValue child;
        public Name parent;

        public JsTreeNode(NameOrValue child, Name parent){
            this.child = child;
            this.parent = parent;

        }
    }


    private static final Logger logger = Logger.getLogger(LoggedInConnection.class);

    private Date loginTime;
    private Date lastAccessed;
    private long timeOut;
    private String spreadsheetName;
    private int reportId;

    private final Map<String, List<List<DataRegionHeading>>> rowHeadings;
    private final Map<String, List<List<DataRegionHeading>>> columnHeadings;
    private final Map<String, List<Integer>> rowOrder;//for when top or bottom values need to be returned.
    private final Map<String, List<Integer>> colOrder; //as above
    private final Map<String, Integer> restrictRowCount; //as above
    private final Map<String, Integer> restrictColCount;
    private final Map<String, String> sortCol; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, String> sortRow; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, List<Name>> contexts;
    private final Map<String, String> lockMaps;
    private final Map<String, String> sentDataMaps;
    private final Map<String, List<List<ListOfValuesOrNamesAndAttributeName>>> sentDataValuesMaps; // As in a 2 d array (lists of lists) of lists of valuer Useful for when data is saved - now has to support the name attribute combo
    private final Map<String, List<List<Set<DataRegionHeading>>>> sentDataHeadingsMaps; // As in a 2 d array (lists of lists) of sets of names, identifying each cell. Necessary if saving new data in that cell. SHould the values map use sets also???
    private List<Set<Name>> namesToSearch;
    private Map<Set<Name>, Set<Value>> valuesFound;
    private AzquoBook azquoBook;
    private List<String>  languages;
    private Map<String, JsTreeNode> jsTreeIds;
    int lastJstreeId;

    private static final String defaultRegion = "default-region";

    protected LoggedInConnection(final AzquoMemoryDB azquoMemoryDB, final User user, final long timeOut, String spreadsheetName) {
        super(azquoMemoryDB, user);
        this.spreadsheetName = spreadsheetName;
        loginTime = new Date();
        lastAccessed = new Date();
        reportId = 0;
        rowHeadings = new HashMap<String, List<List<DataRegionHeading>>>();
        columnHeadings = new HashMap<String, List<List<DataRegionHeading>>>();
        rowOrder = new HashMap<String, List<Integer>>();
        colOrder = new HashMap<String, List<Integer>>();
        restrictRowCount = new HashMap<String, Integer>();
        restrictColCount = new HashMap<String, Integer>();
        sortCol = new HashMap<String, String>();
        sortRow = new HashMap<String, String>();
        contexts = new HashMap<String, List<Name>>();
        lockMaps = new HashMap<String, String>();
        sentDataMaps = new HashMap<String, String>();
        sentDataValuesMaps = new HashMap<String, List<List<ListOfValuesOrNamesAndAttributeName>>>();
        sentDataHeadingsMaps = new HashMap<String, List<List<Set<DataRegionHeading>>>>();
        namesToSearch = null;
        valuesFound = null;
        azquoBook = null;

        languages = new ArrayList<String>();
        languages.add(Name.DEFAULT_DISPLAY_NAME);
        jsTreeIds = new HashMap<String, JsTreeNode>();
        lastJstreeId = 0;

        if (timeOut > 0) {
            this.timeOut = timeOut;
        } else {
            this.timeOut = 1000 * 60 * 120;
        }

    }

    public Date getLoginTime() {
        return loginTime;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public int getReportId() { return  reportId; }

    public void setReportId(int reportId) { this.reportId = reportId; }

    public long getTimeOut() {
        return timeOut;
    }

    public void setLastAccessed(final Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public List<List<DataRegionHeading>> getRowHeadings(final String region) {
        if (region == null || region.isEmpty()) {
            return rowHeadings.get(defaultRegion);
        } else {
            return rowHeadings.get(region);
        }
    }

    public void setRowHeadings(final String region, final List<List<DataRegionHeading>> rowHeadings) {
        if (region == null || region.isEmpty()) {
            this.rowHeadings.put(defaultRegion, rowHeadings);
        } else {
            this.rowHeadings.put(region, rowHeadings);
        }
    }

    public List<List<DataRegionHeading>> getColumnHeadings(final String region) {
        if (region == null || region.isEmpty()) {
            return columnHeadings.get(defaultRegion);
        } else {
            return columnHeadings.get(region);
        }
    }

    public void setColumnHeadings(final String region, final List<List<DataRegionHeading>> columnHeadings) {
        if (region == null || region.isEmpty()) {
            this.columnHeadings.put(defaultRegion, columnHeadings);
        } else {
            this.columnHeadings.put(region, columnHeadings);
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

    public List<Integer> getColOrder(final String region) {
        if (region == null || region.isEmpty()) {
            return colOrder.get(defaultRegion);
        } else {
            return colOrder.get(region);
        }
    }

    public void setColOrder(final String region, final List<Integer> colOrder){
        if (region == null || region.isEmpty()) {
            this.colOrder.put(defaultRegion, colOrder);
        } else {
            this.colOrder.put(region, colOrder);
        }

    }


    public Integer getRestrictRowCount(final String region) {
        if (region == null || region.isEmpty()) {
            return restrictRowCount.get(defaultRegion);
        } else {
            return restrictRowCount.get(region);
        }
    }


    public void setRestrictRowCount(final String region, final int restrictRowCount){
        if (region == null || region.isEmpty()) {
            this.restrictRowCount.put(defaultRegion, restrictRowCount);
        } else {
            this.restrictRowCount.put(region, restrictRowCount);
        }

    }



    public Integer getRestrictColCount(final String region) {
        if (region == null || region.isEmpty()) {
            return restrictColCount.get(defaultRegion);
        } else {
            return restrictColCount.get(region);
        }
    }

    public void setRestrictColCount(final String region, final int restrictColCount){
        if (region == null || region.isEmpty()) {
            this.restrictColCount.put(defaultRegion, restrictColCount);
        } else {
            this.restrictColCount.put(region, restrictColCount);
        }

    }


    public String getSortCol(final String region) {
        if (region == null || region.isEmpty()) {
            return sortCol.get(defaultRegion);
        } else {
            return sortCol.get(region);
        }
    }

    public void setSortCol(final String region, final String sortCol){
        if (region == null || region.isEmpty()) {
            this.sortCol.put(defaultRegion, sortCol);
        } else {
            this.sortCol.put(region, sortCol);
        }

    }
    public void clearSortCols(){
        this.sortCol.clear();

    }

    public String getSortRow(final String region) {
        if (region == null || region.isEmpty()) {
            return sortRow.get(defaultRegion);
        } else {
            return sortRow.get(region);
        }
    }

    public void setSortRow(final String region, final String sortRow){
        if (region == null || region.isEmpty()) {
            this.sortRow.put(defaultRegion, sortRow);
        } else {
            this.sortRow.put(region, sortRow);
        }

    }
    public void clearSortRows(){
        this.sortRow.clear();

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

    public List<List<ListOfValuesOrNamesAndAttributeName>> getDataValueMap(final String region) {
        if (region == null || region.isEmpty()) {
            return sentDataValuesMaps.get(defaultRegion);
        } else {
            return sentDataValuesMaps.get(region.toLowerCase());
        }
    }

    public void setDataValueMap(final String region, final List<List<ListOfValuesOrNamesAndAttributeName>> sentDataValuesMap) {
        if (region == null || region.isEmpty()) {
            this.sentDataValuesMaps.put(defaultRegion, sentDataValuesMap);
        } else {
            this.sentDataValuesMaps.put(region, sentDataValuesMap);
        }
    }

    public List<List<Set<DataRegionHeading>>> getDataHeadingsMap(final String region) {
        if (region == null || region.isEmpty()) {
            return sentDataHeadingsMaps.get(defaultRegion);
        } else {
            return sentDataHeadingsMaps.get(region);
        }
    }

    public void setDataHeadingsMap(final String region, final List<List<Set<DataRegionHeading>>> sentDataHeadingsMap) {
        if (region == null || region.isEmpty()) {
            this.sentDataHeadingsMaps.put(defaultRegion, sentDataHeadingsMap);
        } else {
            this.sentDataHeadingsMaps.put(region, sentDataHeadingsMap);
        }
    }

    // very basic, needs to be improved

    public Provenance getProvenance(String where) {
        if (provenance == null) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), user.getName(), new Date(), where, spreadsheetName, "");
            } catch (Exception e) {
            }
        }
        return provenance;
    }

    public List<Set<Name>> getNamesToSearch(){
        return this.namesToSearch;
    }

    public void setNamesToSearch(List<Set<Name>> names){
        this.namesToSearch = names;
    }

    public Map<Set<Name>, Set<Value>> getValuesFound(){
        return this.valuesFound;
    }

    public void setValuesFound(Map<Set<Name>,Set<Value>> valuesFound){ this.valuesFound = valuesFound; }

    public AzquoBook getAzquoBook() { return this.azquoBook; }

    public void setAzquoBook(AzquoBook azquoBook){    this.azquoBook = azquoBook;  }


    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public Map<String,JsTreeNode> getJsTreeIds(){
        return jsTreeIds;
    }


    public int getLastJstreeId(){
        return lastJstreeId;
    }


    public void setLastJstreeId(int lastJstreeId){
        this.lastJstreeId = lastJstreeId;
    }
}

