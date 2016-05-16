package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 29/06/15.
 *
 * Options against a report data region.
 */
public class UserRegionOptions extends StandardEntity{
    private final int userId;
    private final int reportId;
    private final String region;
    private int hideRows;
    private boolean sortable;
    // if set these will default to a total descending sort
    private int rowLimit;
    private int columnLimit;
    private String sortRow;
    private boolean sortRowAsc;
    private String sortColumn;
    private boolean sortColumnAsc;
    private int highlightDays;
    private String databaseName;

    UserRegionOptions(int id, int userId, int reportId, String region, int hideRows, boolean sortable
            , int rowLimit, int columnLimit, String sortRow, boolean sortRowAsc, String sortColumn, boolean sortColumnAsc, int highlightDays, String databaseName) {
        this.id = id;
        this.userId = userId;
        this.reportId = reportId;
        this.region = region;
        this.hideRows = hideRows;
        this.sortable = sortable;
        this.rowLimit = rowLimit;
        this.columnLimit = columnLimit;
        this.sortRow = sortRow;
        this.sortRowAsc = sortRowAsc;
        this.sortColumn = sortColumn;
        this.sortColumnAsc = sortColumnAsc;
        this.highlightDays = highlightDays;
        this.databaseName = databaseName;

    }

    // to read the format of options from the spreadsheet, code adapted from azquobook.
    // Maybe these things could be better represented by a key pair two column region

    public UserRegionOptions(int id, int userId, int reportId, String region, String spreadsheetSource) {
        spreadsheetSource = spreadsheetSource.toLowerCase();
        this.id = id;
        this.userId = userId;
        this.reportId = reportId;
        this.region = region;
        String SPREADSHEETHIDEROWS = "hiderows";
        hideRows = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDEROWS, spreadsheetSource));
        if (hideRows == 0) {
            String SPREADSHEETHIDEROWS2 = "hiderowvalues";
            hideRows = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDEROWS2, spreadsheetSource));
        }
        // todo : find out why this is so! (legacy logic)
        if (hideRows == 0) {
            hideRows = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second.
        }
        if (spreadsheetSource != null) {
            this.sortable = spreadsheetSource.contains("sortable"); // the get option thing is no good for just an "exists with no value" check, this is the same
            String ROWLIMIT = "maxrows";
            this.rowLimit = asNumber(getOptionFromSpreadsheetOptions(ROWLIMIT, spreadsheetSource));
            String COLUMNLIMIT = "maxcols";
            this.columnLimit = asNumber(getOptionFromSpreadsheetOptions(COLUMNLIMIT, spreadsheetSource));
            String HIGHLIGHT = "highlight";
            this.highlightDays = asNumber(getOptionFromSpreadsheetOptions(HIGHLIGHT, spreadsheetSource));
            String DATABASENAME = "database";
            this.databaseName = getOptionFromSpreadsheetOptions(DATABASENAME, spreadsheetSource);
        } else {
            this.sortable = false;
            this.rowLimit = 0;
            this.columnLimit = 0;
            this.highlightDays = 0;
            this.databaseName = null;
        }
        this.sortRow = null;
        this.sortRowAsc = false;
        this.sortColumn = null;
        this.sortColumnAsc = false;

        String sortColumn = getOptionFromSpreadsheetOptions("sortcolumn", spreadsheetSource);
        if (sortColumn != null) {
            this.sortColumnAsc = true;
            this.sortColumn = sortColumn;
           if (sortColumn.toLowerCase().endsWith(" desc")) {
                this.sortColumnAsc = false;
                this.sortColumn = sortColumn.substring(0, sortColumn.length() - 5);
            }
        }
    }


    private static int asNumber(String string) {
        try {
            return string != null ? Integer.parseInt(string) : 0;
        } catch (Exception ignored) {
        }
        return 0;
    }

    // should this be in here? Does it matter that much?

    private String getOptionFromSpreadsheetOptions(String optionName, String optionsForRegion) {
        if (optionsForRegion != null) {
            int foundPos = optionsForRegion.toLowerCase().indexOf(optionName.toLowerCase());
            if (foundPos != -1){
                if (optionsForRegion.length() > foundPos + optionName.length()) {
                    optionsForRegion = optionsForRegion.substring(foundPos + optionName.length());//allow for a space or '=' at the end of the option name
                    char operator = optionsForRegion.charAt(0);
                    if (operator == '>') {//interpret the '>' symbol as '-' to create an integer
                        optionsForRegion = "-" + optionsForRegion.substring(1);
                    } else {
                        //ignore '=' or a space
                        optionsForRegion = optionsForRegion.substring(1);
                    }
                    foundPos = optionsForRegion.indexOf(",");
                    if (foundPos > 0) {
                        optionsForRegion = optionsForRegion.substring(0, foundPos);
                    }
                    return optionsForRegion.trim();
                }
            }
        }
        // blank option means null, I'll ignore it.
        return null;
    }

    public int getUserId() {
        return userId;
    }

    public int getReportId() {
        return reportId;
    }

    public String getRegion() {
        return region;
    }

    public int getHideRows() {
        return hideRows;
    }

    public void setHideRows(int hideRows) {
        this.hideRows = hideRows;
    }

    public boolean getSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public int getRowLimit() {
        return rowLimit;
    }

    public void setRowLimit(int rowLimit) {
        this.rowLimit = rowLimit;
    }

    public int getColumnLimit() {
        return columnLimit;
    }

    public void setColumnLimit(int columnLimit) {
        this.columnLimit = columnLimit;
    }

    public String getSortRow() {
        return sortRow;
    }

    public void setSortRow(String sortRow) {
        this.sortRow = sortRow;
    }

    public boolean getSortRowAsc() {
        return sortRowAsc;
    }

    public void setSortRowAsc(boolean sortRowAsc) {
        this.sortRowAsc = sortRowAsc;
    }

    public String getSortColumn() {
        return sortColumn;
    }

    public void setSortColumn(String sortColumn) {
        this.sortColumn = sortColumn;
    }

    public boolean getSortColumnAsc() {
        return sortColumnAsc;
    }

    public void setSortColumnAsc(boolean sortColumnAsc) {
        this.sortColumnAsc = sortColumnAsc;
    }

    public int getHighlightDays() {
        return highlightDays;
    }

    public void setHighlightDays(int highlightDays) {
        this.highlightDays = highlightDays;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }


    @Override
    public String toString() {
        return "UserRegionOption{" +
                "id=" + id +
                ", userId=" + userId +
                ", reportId=" + reportId +
                ", region='" + region + '\'' +
                ", hideRows=" + hideRows +
                ", sortable=" + sortable +
                ", rowLimit=" + rowLimit +
                ", columnLimit=" + columnLimit +
                ", sortRow='" + sortRow + '\'' +
                ", sortRowAsc=" + sortRowAsc +
                ", sortColumn='" + sortColumn + '\'' +
                ", sortColumnAsc=" + sortColumnAsc +
                ", highlightDays=" + highlightDays +
                ", databaseName=" + databaseName +
                '}';
    }
}