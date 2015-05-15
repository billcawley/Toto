package com.azquo.spreadsheet;

import com.azquo.admin.user.User;
import com.azquo.memorydb.*;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created with IntelliJ IDEA.AzquoMemoryDBContainer
 * User: cawley
 * Date: 31/10/13
 * Time: 19:25
 * To change this template use File | Settings | File Templates.
 * <p/>
 * Originally created to manage a conneciton to Excel. Requirements have drastically changed and for the new Client/Server model it will not extend the database connection directly.
 */
public final class LoggedInConnection extends AzquoMemoryDBConnection {


    private static final Logger logger = Logger.getLogger(LoggedInConnection.class);

    private String spreadsheetName;
    private int reportId;

    private final Map<String, String> sortCol; //when a region is to be sorted on a particular column.  Column numbers start with 1, and are negative for descending
    private final Map<String, String> sortRow;
    // I still need this for the locks
    private final Map<String, CellsAndHeadingsForDisplay> sentCellsMaps; // returned display data for each region
    // namestosearch was here, I zapped it
    private AzquoBook azquoBook;
    private List<String> languages;
    int lastJstreeId;

    private static final String defaultRegion = "default-region";

    protected LoggedInConnection(final AzquoMemoryDB azquoMemoryDB, final User user, String spreadsheetName) {
        super(azquoMemoryDB, user);
        this.spreadsheetName = spreadsheetName;
        reportId = 0;
        sortCol = new HashMap<String, String>();
        sortRow = new HashMap<String, String>();
        sentCellsMaps = new HashMap<String, CellsAndHeadingsForDisplay>();
        azquoBook = null;
        languages = new ArrayList<String>();
        languages.add(Name.DEFAULT_DISPLAY_NAME);
        lastJstreeId = 0;

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

    public int getLastJstreeId() {
        return lastJstreeId;
    }


    public void setLastJstreeId(int lastJstreeId) {
        this.lastJstreeId = lastJstreeId;
    }
}

