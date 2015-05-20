package com.azquo.spreadsheet;

import com.azquo.admin.database.Database;
import com.azquo.admin.user.User;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
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

    private static final Logger logger = Logger.getLogger(LoggedInUser.class);

    private final User user;
    private int reportId;
    // in here as AzquoBook batch processes these, hopefully can remove later
    private final Map<String, String> sortCol; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, String> sortRow;

    // I still need this for the locks in azquobook
    private final Map<String, CellsAndHeadingsForDisplay> sentCellsMaps; // returned display data for each region
    // need to hold the current one unlke with ZK which holds onto the user after the spreadsheet is created
    private AzquoBook azquoBook;
    private List<String> languages;

    private Database database;

    private String readPermissions;
    private String writePermissions;

    private static final String defaultRegion = "default-region";

    protected LoggedInUser(final User user, Database database, String readPermissions, String writePermissions) {
        this.user = user;
        reportId = 0;
        sortCol = new HashMap<String, String>();
        sortRow = new HashMap<String, String>();
        sentCellsMaps = new HashMap<String, CellsAndHeadingsForDisplay>();
        azquoBook = null;

        languages = new ArrayList<String>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        this.database = database;

        this.readPermissions = readPermissions;
        this.writePermissions = writePermissions;

    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
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

    public User getUser() {
        return user;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public String getReadPermissions() {
        return readPermissions;
    }

    public void setReadPermissions(String readPermissions) {
        this.readPermissions = readPermissions;
    }

    public String getWritePermissions() {
        return writePermissions;
    }

    public void setWritePermissions(String writePermissions) {
        this.writePermissions = writePermissions;
    }

    public DatabaseAccessToken getDataAccessToken(){
        return new DatabaseAccessToken(database.getMySQLName(), readPermissions,writePermissions,languages);
    }
}
