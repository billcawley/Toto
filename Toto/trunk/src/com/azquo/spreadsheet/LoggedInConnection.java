package com.azquo.spreadsheet;

import com.azquo.admin.user.User;
import com.azquo.memorydb.*;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
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
 * <p/>
 * Not thread safe really although it should be one per session. As in multiple tabs or fast refreshes could cause problems - that's something to look into.
 * <p/>
 * Since Excel is no longer the priority this class might be a bit different if rewritten - I'm going to be working on this, row and column heading for example may be removed.
 */
public final class LoggedInConnection extends AzquoMemoryDBConnection {

    public static final class NameOrValue {
        public Name name;
        public Set<Value> values;
    }

    public static final class JsTreeNode {
        public NameOrValue child;
        public Name parent;

        public JsTreeNode(NameOrValue child, Name parent) {
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

    private final Map<String, String> sortCol; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, String> sortRow; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, List<Name>> contexts;
    private final Map<String, List<List<AzquoCell>>> sentCellsMaps; // As in a 2 d array (lists of lists) of the sent cells, should replace a number of older maps
    private List<Set<Name>> namesToSearch;
    private Map<Set<Name>, Set<Value>> valuesFound;
    private AzquoBook azquoBook;
    private List<String> languages;
    private Map<String, JsTreeNode> jsTreeIds;
    int lastJstreeId;

    private static final String defaultRegion = "default-region";

    protected LoggedInConnection(final AzquoMemoryDB azquoMemoryDB, final User user, final long timeOut, String spreadsheetName) {
        super(azquoMemoryDB, user);
        this.spreadsheetName = spreadsheetName;
        loginTime = new Date();
        lastAccessed = new Date();
        reportId = 0;
        sortCol = new HashMap<String, String>();
        sortRow = new HashMap<String, String>();
        contexts = new HashMap<String, List<Name>>();
        sentCellsMaps = new HashMap<String, List<List<AzquoCell>>>();
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

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void setLastAccessed(final Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }


    public String getSortCol(final String region) {
        if (region == null || region.isEmpty()) {
            return sortCol.get(defaultRegion);
        } else {
            return sortCol.get(region);
        }
    }

    public void setSortCol(final String region, final String sortCol) {
        if (region == null || region.isEmpty()) {
            this.sortCol.put(defaultRegion, sortCol);
        } else {
            this.sortCol.put(region, sortCol);
        }

    }

    public void clearSortCols() {
        this.sortCol.clear();

    }

    public String getSortRow(final String region) {
        if (region == null || region.isEmpty()) {
            return sortRow.get(defaultRegion);
        } else {
            return sortRow.get(region);
        }
    }

    public void setSortRow(final String region, final String sortRow) {
        if (region == null || region.isEmpty()) {
            this.sortRow.put(defaultRegion, sortRow);
        } else {
            this.sortRow.put(region, sortRow);
        }

    }

    public void clearSortRows() {
        this.sortRow.clear();

    }


    public List<List<AzquoCell>> getSentCells(final String region) {
        if (region == null || region.isEmpty()) {
            return sentCellsMaps.get(defaultRegion);
        } else {
            return sentCellsMaps.get(region.toLowerCase());
        }
    }

    public void setSentCells(final String region, final List<List<AzquoCell>> sentCells) {
        if (region == null || region.isEmpty()) {
            this.sentCellsMaps.put(defaultRegion, sentCells);
        } else {
            this.sentCellsMaps.put(region, sentCells);
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

    public List<Set<Name>> getNamesToSearch() {
        return this.namesToSearch;
    }

    public void setNamesToSearch(List<Set<Name>> names) {
        this.namesToSearch = names;
    }

    public Map<Set<Name>, Set<Value>> getValuesFound() {
        return this.valuesFound;
    }

    public void setValuesFound(Map<Set<Name>, Set<Value>> valuesFound) {
        this.valuesFound = valuesFound;
    }

    public AzquoBook getAzquoBook() {
        return this.azquoBook;
    }

    public void setAzquoBook(AzquoBook azquoBook) {
        this.azquoBook = azquoBook;
    }


    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public Map<String, JsTreeNode> getJsTreeIds() {
        return jsTreeIds;
    }


    public int getLastJstreeId() {
        return lastJstreeId;
    }


    public void setLastJstreeId(int lastJstreeId) {
        this.lastJstreeId = lastJstreeId;
    }
}

