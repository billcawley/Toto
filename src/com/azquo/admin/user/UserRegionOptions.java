package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import com.azquo.spreadsheet.transport.RegionOptions;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 29/06/15.
 * <p>
 * Options against a report data region.
 *
 * todo - sort field/function ordering
 */
public class UserRegionOptions extends StandardEntity {
    private final int userId;
    private final int reportId;
    private final String region;
    private int hideRows;
    private int hideRowValues;
    private int hideCols;
    private boolean sortable;
    // if set these will default to a total descending sort
    private int rowLimit;
    private int columnLimit;
    private String sortRow;
    private boolean sortRowAsc;
    private String sortColumn;
    private boolean sortColumnAsc;
    private int highlightDays;
    private boolean noSave;
    private String databaseName;
    private boolean userLocked;
    private int permuteTotalCount;

    private String rowLanguage;
    private String columnLanguage;
    private boolean ignoreHeadingErrors;
    private boolean preSave;
    private boolean dynamicUpdate;

    private boolean csvDownload;
    // rendered means formulae etc resolved
    private boolean csvRenderedDownload;

    UserRegionOptions(int id, int userId, int reportId, String region, int hideRows, int hideRowValues, int hideCols, boolean sortable
            , int rowLimit, int columnLimit, String sortRow, boolean sortRowAsc, String sortColumn
            , boolean sortColumnAsc, int highlightDays, boolean noSave, String databaseName, String rowLanguage, String columnLanguage, boolean userLocked, int permuteTotalCount, boolean ignoreHeadingErrors, boolean preSave, boolean dynamicUpdate, boolean csvDownload, boolean csvRenderedDownload) {
        this.id = id;
        this.userId = userId;
        this.reportId = reportId;
        this.region = region;
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
        this.noSave = noSave;
        this.databaseName = databaseName;
        this.rowLanguage = rowLanguage;
        this.columnLanguage = columnLanguage;
        this.permuteTotalCount = permuteTotalCount;
        this.userLocked = userLocked;
        this.ignoreHeadingErrors = ignoreHeadingErrors;
        this.preSave = preSave;
        this.dynamicUpdate = dynamicUpdate;
        this.csvDownload = csvDownload;
        this.csvRenderedDownload = csvRenderedDownload;
    }

    // to read the format of options from the spreadsheet, code adapted from azquobook.
    // Maybe these things could be better represented by a key pair two column region

    public UserRegionOptions(int id, int userId, int reportId, String region, String spreadsheetSource) {
        this.id = id;
        this.userId = userId;
        this.reportId = reportId;
        this.region = region;
        String SPREADSHEETHIDEROWS = "hiderows";
        String SPREADSHEETHIDECOLS = "hidecols";
        String SPREADSHEETHIDEROWS2 = "hiderowvalues";
        hideRowValues = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDEROWS2, spreadsheetSource));
        if (hideRowValues == 0) {
            hideRows = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDEROWS, spreadsheetSource));
        } else {
            hideRows = hideRowValues;
        }

        hideCols = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDECOLS, spreadsheetSource));
        if (spreadsheetSource != null) {
            spreadsheetSource = spreadsheetSource.toLowerCase();
            this.sortable = spreadsheetSource.contains("sortable"); // the get option thing is no good for just an "exists with no value" check, this is the same
            String ROWLIMIT = "maxrows";
            this.rowLimit = asNumber(getOptionFromSpreadsheetOptions(ROWLIMIT, spreadsheetSource));
            String COLUMNLIMIT = "maxcols";
            this.columnLimit = asNumber(getOptionFromSpreadsheetOptions(COLUMNLIMIT, spreadsheetSource));
            String HIGHLIGHT = "highlight";
            this.highlightDays = asNumber(getOptionFromSpreadsheetOptions(HIGHLIGHT, spreadsheetSource));
            this.noSave = spreadsheetSource.contains("nosave");
            String DATABASENAME = "database";
            this.databaseName = getOptionFromSpreadsheetOptions(DATABASENAME, spreadsheetSource);
            this.rowLanguage = getOptionFromSpreadsheetOptions("row language", spreadsheetSource);
            this.columnLanguage = getOptionFromSpreadsheetOptions("column language", spreadsheetSource);
            this.userLocked = spreadsheetSource.toLowerCase().contains("userlocked"); // the get option thing is no good for just an "exists with no value" check, this is the same
            String PERMUTETOTALCOUNT = "permutetotalcount";
            if (spreadsheetSource.toLowerCase().contains("nopermutetotals")){
                permuteTotalCount = 0;
            }else{
                if (spreadsheetSource.toLowerCase().contains(PERMUTETOTALCOUNT)){
                    permuteTotalCount = asNumber(getOptionFromSpreadsheetOptions(PERMUTETOTALCOUNT, spreadsheetSource));
                }else{
                    permuteTotalCount = 100;
                }
            }
            this.ignoreHeadingErrors = spreadsheetSource.contains("ignoreheadingerrors");
            this.preSave = spreadsheetSource.contains("presave");
            this.dynamicUpdate = spreadsheetSource.toLowerCase().contains("dynamicupdate");
            this.csvDownload = spreadsheetSource.toLowerCase().contains("csvdownload");
            this.csvRenderedDownload = spreadsheetSource.toLowerCase().contains("csvrendereddownload");
        } else {
            this.sortable = false;
            this.rowLimit = 0;
            this.columnLimit = 0;
            this.highlightDays = 0;
            this.noSave = false;
            this.databaseName = null;
            this.userLocked = false;
            this.ignoreHeadingErrors = false;
            this.preSave = false;
            this.dynamicUpdate = false;
            this.csvDownload = false;
            this.csvRenderedDownload = false;
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
            if (foundPos != -1) {
                if (optionsForRegion.length() > foundPos + optionName.length()) {
                    optionsForRegion = optionsForRegion.substring(foundPos + optionName.length());//allow for a space or '=' at the end of the option name
                    char operator = optionsForRegion.charAt(0);
                    if (operator == '>') {//interpret the '>' symbol as '-' to create an integer
                        optionsForRegion = "-" + optionsForRegion.substring(1);
                    } else {
                        //ignore '=' or a space
                        optionsForRegion = optionsForRegion.substring(1);
                    }
                    foundPos = optionsForRegion.indexOf(";");
                    if (foundPos < 0){
                        foundPos = optionsForRegion.indexOf(",");
                    }
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

    public int getHideRowValues() {
        return hideRowValues;
    }

    public void setHideRowValues(int hideRowValues) {
        this.hideRowValues = hideRowValues;
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

    // As mentioned in RegionOptions,
    public RegionOptions getRegionOptionsForTransport() {
        return new RegionOptions(hideRows, hideRowValues, hideCols, sortable, rowLimit, columnLimit, sortRow, sortRowAsc, sortColumn, sortColumnAsc, highlightDays, rowLanguage, columnLanguage, noSave, databaseName, userLocked, permuteTotalCount, ignoreHeadingErrors, preSave, dynamicUpdate, csvDownload);
    }

    public String getRowLanguage() {
        return rowLanguage;
    }

    public void setRowLanguage(String rowLanguage) {
        this.rowLanguage = rowLanguage;
    }

    public String getColumnLanguage() {
        return columnLanguage;
    }

    public void setColumnLanguage(String columnLanguage) {
        this.columnLanguage = columnLanguage;
    }

    public boolean getNoSave() {
        return noSave;
    }

    public void setNoSave(boolean noSave) {
        this.noSave = noSave;
    }

    public boolean getUserLocked() {
        return userLocked;
    }

    public void setUserLocked(boolean userLocked) {
        this.userLocked = userLocked;
    }

    public int getPermuteTotalCount() {
        return permuteTotalCount;
    }

    public boolean getIgnoreHeadingErrors() {
        return ignoreHeadingErrors;
    }

    public void setIgnoreHeadingErrors(boolean ignoreHeadingErrors) {
        this.ignoreHeadingErrors = ignoreHeadingErrors;
    }

    public boolean getPreSave() {
        return preSave;
    }

    public boolean getCsvDownload() {
        return csvDownload;
    }

    public boolean getCsvRenderedDownload() {
        return csvRenderedDownload;
    }

    @Override
    public String toString() {
        return "UserRegionOptions{" +
                "userId=" + userId +
                ", reportId=" + reportId +
                ", region='" + region + '\'' +
                ", hideRows=" + hideRows +
                ", hideRowValues=" + hideRowValues +
                ", hideCols=" + hideCols +
                ", sortable=" + sortable +
                ", rowLimit=" + rowLimit +
                ", columnLimit=" + columnLimit +
                ", sortRow='" + sortRow + '\'' +
                ", sortRowAsc=" + sortRowAsc +
                ", sortColumn='" + sortColumn + '\'' +
                ", sortColumnAsc=" + sortColumnAsc +
                ", highlightDays=" + highlightDays +
                ", noSave=" + noSave +
                ", databaseName='" + databaseName + '\'' +
                ", userLocked=" + userLocked +
                ", rowLanguage='" + rowLanguage + '\'' +
                ", columnLanguage='" + columnLanguage + '\'' +
                ", ignoreHeadingErrors=" + ignoreHeadingErrors +
                '}';
    }
}