package com.azquo.spreadsheet.view;

import java.io.Serializable;

/**
 * Created by edward on 15/08/16.
 *
 * Factoring to make this part of UserRegionOptions was awkward, more simple to just have a simple immutable class to pass the options to the database server.
 */
public class RegionOptions implements Serializable {
    public final int hideRows;
    public final boolean sortable;
    public final int rowLimit;
    public final int columnLimit;
    public final String sortRow;
    public final boolean sortRowAsc;
    public final String sortColumn;
    public final boolean sortColumnAsc;
    public final int highlightDays;
    public final String rowLanguage;
    public final String columnLanguage;
    public final boolean noSave;
    public final String database;

    public RegionOptions(int hideRows, boolean sortable, int rowLimit, int columnLimit, String sortRow, boolean sortRowAsc, String sortColumn, boolean sortColumnAsc, int highlightDays, String rowLanguage, String columnLanguage, boolean noSave, String database) {
        this.hideRows = hideRows;
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
    }
}
