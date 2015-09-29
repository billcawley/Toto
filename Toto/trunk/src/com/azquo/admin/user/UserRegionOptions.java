package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;

/**
 * Created by cawley on 29/06/15.
 */
public class UserRegionOptions extends StandardEntity{
    final int userId;
    final int reportId;
    final String region;
    int hideRows;
    boolean sortable;
    // if set these will default to a total descending sort
    int rowLimit;
    int columnLimit;
    String sortRow;
    boolean sortRowAsc;
    String sortColumn;
    boolean sortColumnAsc;
    int highlightDays;

    public UserRegionOptions(int id, int userId, int reportId, String region, int hideRows, boolean sortable
            , int rowLimit, int columnLimit, String sortRow, boolean sortRowAsc, String sortColumn, boolean sortColumnAsc, int highlightDays) {
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
    }

    // to read the format of options from the spreadsheet, code adapted from azquobook.
    // Maybe these things could be better represented by a key pair two column region

    public static String SPREADSHEETHIDEROWS = "hiderows";
    public static String SPREADSHEETHIDEROWS2 = "hiderowvalues";
    public static String SORTABLE = "sortable";
    public static String ROWLIMIT = "maxrows";
    public static String COLUMNLIMIT = "maxcols";
    public static String HIGHLIGHT = "highlight";

    public UserRegionOptions(int id, int userId, int reportId, String region, String spreadsheetSource) {
        this.id = id;
        this.userId = userId;
        this.reportId = reportId;
        this.region = region;


        hideRows = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDEROWS, spreadsheetSource));
        if (hideRows == 0){
            hideRows = asNumber(getOptionFromSpreadsheetOptions(SPREADSHEETHIDEROWS2, spreadsheetSource));
        }
        // todo : find out why this is so! (legacy logic)
        if (hideRows == 0){
            hideRows = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second.
        }
        if (spreadsheetSource!=null) {
            this.sortable = spreadsheetSource.contains("sortable"); // the get option thing is no good for just an "exists with no value" check, this is the same
            this.rowLimit = asNumber(getOptionFromSpreadsheetOptions(ROWLIMIT, spreadsheetSource));
            this.columnLimit = asNumber(getOptionFromSpreadsheetOptions(COLUMNLIMIT, spreadsheetSource));
            this.highlightDays = asNumber(getOptionFromSpreadsheetOptions(HIGHLIGHT, spreadsheetSource));
        }else{
            this.sortable = false;
            this.rowLimit = 0;
            this.columnLimit = 0;
            this.highlightDays = 0;
        }
        // currently sort columns and rows are not supported in this way
        this.sortRow = null;
        this.sortRowAsc = false;
        this.sortColumn = null;
        this.sortColumnAsc = false;
     }


    private static int asNumber(String string) {
        try {
            return string != null ? Integer.parseInt(string) : 0;
        } catch (Exception ignored) {
        }
        return 0;
    }

    // should this be in here? Does it matter that much?

    public String getOptionFromSpreadsheetOptions(String optionName, String optionsForRegion) {
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
                '}';
    }
}
