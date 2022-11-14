package com.azquo.spreadsheet.transport;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * 
 * Created by cawley on 11/05/15.
 *
 * I don't see much reason for this class to be mutable though the data cells can be changed
 *
 * I've added the source, the criteria sent to the DB to create the data
 *
 * Also going to add a lock report, a place to put it for the mo
 */
public class CellsAndHeadingsForDisplay implements Serializable {

    private final String region; // convenient
    private final List<List<String>> columnHeadings;
    private List<List<String>> rowHeadings;
    // ok, on loading the report renderer on the report server data regions can overwrite each other or create data to be saved based on formulae. Typically used when executing.
    // these two flag rows or columns where a zero result should be saved if there's currently no value for that cell in the DB. Normally 0 is the same as nothing so I don't bother saving.
    private final Set<Integer> zeroSavedColumnIndexes;
    private final Set<Integer> zeroSavedRowIndexes;
    private List<List<CellForDisplay>> data;
    private final List<List<String>> rowHeadingsSource;
    private final List<List<String>> colHeadingsSource;
    private final List<List<String>> contextSource;
    private final long timeStamp;
    private RegionOptions options;
    // I believe locks are more a question of user editing than he cell being e.g. made from the result of a server side function
    private final String lockResult;

    public CellsAndHeadingsForDisplay(String region, List<List<String>> columnHeadings, List<List<String>> rowHeadings, Set<Integer> zeroSavedColumnIndexes, Set<Integer> zeroSavedRowIndexes, List<List<CellForDisplay>> data
            , List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, long timeStamp, RegionOptions options, String lockResult) {
        this.region = region;
        this.columnHeadings = columnHeadings;
        this.rowHeadings = rowHeadings;
        this.zeroSavedColumnIndexes = zeroSavedColumnIndexes;
        this.zeroSavedRowIndexes = zeroSavedRowIndexes;
        this.data = data;
        this.rowHeadingsSource = rowHeadingsSource;
        this.colHeadingsSource = colHeadingsSource;
        this.contextSource = contextSource;
        this.timeStamp = timeStamp;
        this.options = options;
        this.lockResult = lockResult;
    }

    public String getRegion() {
        return region;
    }

    public List<List<String>> getColumnHeadings() {
        return columnHeadings;
    }

    public List<List<String>> getRowHeadings() {
        return rowHeadings;
    }

    public void setRowHeadings(List<List<String>> rowHeadings){ this.rowHeadings = rowHeadings;
    }
    public void  setRowHeading(int rowNo, int colNo, String value) {
         rowHeadings.get(rowNo).set(colNo,value);
    }

    public Set<Integer> getZeroSavedColumnIndexes() {
        return zeroSavedColumnIndexes;
    }

    public Set<Integer> getZeroSavedRowIndexes() {
        return zeroSavedRowIndexes;
    }

    public List<List<CellForDisplay>> getData() {
        return data;
    }

    public void setData(List<List<CellForDisplay>> data){this.data = data;}

    public List<List<String>> getRowHeadingsSource() {
        return rowHeadingsSource;
    }

    public List<List<String>> getColHeadingsSource() {
        return colHeadingsSource;
    }

    public List<List<String>> getContextSource() {
        return contextSource;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public RegionOptions getOptions() {
        return options;
    }

    public void setOptions(RegionOptions regionOptions){ this.options = regionOptions; }

    public String getLockResult() {
        return lockResult;
    }

    // intellij generated. I want to compare if the data matches . . .
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CellsAndHeadingsForDisplay that = (CellsAndHeadingsForDisplay) o;
        return region.equals(that.region) &&
                data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(region, data);
    }
}