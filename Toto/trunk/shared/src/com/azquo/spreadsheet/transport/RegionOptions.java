package com.azquo.spreadsheet.transport;

import java.io.Serializable;

/**
 * Created by edward on 15/08/16.
 *
 * Factoring to make this part of UserRegionOptions was awkward, more simple to just have a simple immutable class to pass the options to the database server.
 */
public class RegionOptions implements Serializable {
    public final int hideRows;
    public final int hideRowValues;
    public final int hideCols;
    public final boolean sortable;// unused - remove?
    public final int rowLimit;
    public final int columnLimit;
    public final String sortRow;
    public final boolean sortRowAsc;
    public final String sortColumn;
    public final boolean sortColumnAsc;
    public final int highlightDays;
    public final String rowLanguage;
    public final String columnLanguage;
    public final boolean noSave;// unused - remove?
    public final String database;
    public final boolean lockRequest;
    public final int permuteTotalCount;
    public final boolean ignoreHeadingErrors;
    public final boolean preSave;
    public final boolean dynamicUpdate;

    public final static int LATEST = 1000;
    public final static int ONEHOUR = 1001;


    public RegionOptions(int hideRows, int hideRowValues,int hideCols, boolean sortable, int rowLimit, int columnLimit, String sortRow, boolean sortRowAsc, String sortColumn, boolean sortColumnAsc
            , int highlightDays, String rowLanguage, String columnLanguage, boolean noSave, String database, boolean lockRequest, int permuteTotalCount, boolean ignoreHeadingErrors, boolean preSave, boolean dynamicUpdate) {
        this.hideRows = hideRows;
        this.hideRowValues = hideRowValues;
        this.hideCols = hideCols;
        this.sortable = sortable;
        this.rowLimit = rowLimit;
        this.columnLimit = columnLimit;
        this.sortRow = sortRow;
        this.sortRowAsc = sortRowAsc;
        this.sortColumn = sortColumn;
        this.sortColumnAsc = sortColumnAsc;
        this.highlightDays = highlightDays;
        this.rowLanguage = rowLanguage;
        this.columnLanguage = columnLanguage;
        this.noSave = noSave;
        this.database = database;
        this.lockRequest = lockRequest;
        this.permuteTotalCount = permuteTotalCount;
        this.ignoreHeadingErrors = ignoreHeadingErrors;
        this.preSave = preSave;
        this.dynamicUpdate = dynamicUpdate;
    }
}
