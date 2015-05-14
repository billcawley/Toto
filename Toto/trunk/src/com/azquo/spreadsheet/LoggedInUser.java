package com.azquo.spreadsheet;

import com.azquo.admin.database.Database;
import com.azquo.admin.user.User;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by cawley on 12/05/15.
 *
 * On the new client/server model the old LoggedInConnection will not do. We want an object representing a logged in user against the session
 * which holds no database classes. It will have a fair bit of stuff that was in logged in conneciton but no DB classes
 *
 */
public class LoggedInUser {

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


    private static final Logger logger = Logger.getLogger(LoggedInUser.class);

    private Date loginTime;
    private Date lastAccessed;
    private long timeOut;
    private int reportId;
    // in here as AzquoBook batch processes these, hopefully can remove later
    private final Map<String, String> sortCol; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, String> sortRow;
    // I still need this for the locks
    private final Map<String, CellsAndHeadingsForDisplay> sentCellsMaps; // returned display data for each region
    private List<Set<Name>> namesToSearch;
    // need to hold the current one unlke with ZK which holds onto the user after the spreadsheet is created
    private AzquoBook azquoBook;
    private List<String> languages;
    private Map<String, JsTreeNode> jsTreeIds;
    int lastJstreeId;

    private static final String defaultRegion = "default-region";

    protected LoggedInUser(final User user, final long timeOut, String spreadsheetName, Database database) {
        loginTime = new Date();
        lastAccessed = new Date();
        reportId = 0;
        sortCol = new HashMap<String, String>();
        sortRow = new HashMap<String, String>();
        sentCellsMaps = new HashMap<String, CellsAndHeadingsForDisplay>();
        namesToSearch = null;
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


    public CellsAndHeadingsForDisplay getSentCells(final String region) {
        if (region == null || region.isEmpty()) {
            return sentCellsMaps.get(defaultRegion);
        } else {
            return sentCellsMaps.get(region.toLowerCase());
        }
    }

    public void setSentCells(final String region, final CellsAndHeadingsForDisplay sentCells) {
        if (region == null || region.isEmpty()) {
            this.sentCellsMaps.put(defaultRegion, sentCells);
        } else {
            this.sentCellsMaps.put(region, sentCells);
        }
    }

    public List<Set<Name>> getNamesToSearch() {
        return this.namesToSearch;
    }

    public void setNamesToSearch(List<Set<Name>> names) {
        this.namesToSearch = names;
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
