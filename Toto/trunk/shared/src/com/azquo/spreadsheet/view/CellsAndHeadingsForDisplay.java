package com.azquo.spreadsheet.view;

import java.io.Serializable;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * 
 * Created by cawley on 11/05/15.
 *
 * I don't see much reason for this class to be mutable though the data cells can be changed
 *
 * I've added the source, the criteria sent to the DB to create the data
 */
public class CellsAndHeadingsForDisplay implements Serializable {

    private final List<List<String>> columnHeadings;
    private final List<List<String>> rowHeadings;
    private final List<List<CellForDisplay>> data;
    private final List<List<String>> rowHeadingsSource;
    private final List<List<String>> colHeadingsSource;
    private final List<List<String>> contextSource;

    public CellsAndHeadingsForDisplay(List<List<String>> columnHeadings, List<List<String>> rowHeadings, List<List<CellForDisplay>> data
            , List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource) {
        this.columnHeadings = columnHeadings;
        this.rowHeadings = rowHeadings;
        this.data = data;
        this.rowHeadingsSource = rowHeadingsSource;
        this.colHeadingsSource = colHeadingsSource;
        this.contextSource = contextSource;
    }

    public List<List<String>> getColumnHeadings() {
        return columnHeadings;
    }

    public List<List<String>> getRowHeadings() {
        return rowHeadings;
    }

    public List<List<CellForDisplay>> getData() {
        return data;
    }

    public List<List<String>> getRowHeadingsSource() {
        return rowHeadingsSource;
    }

    public List<List<String>> getColHeadingsSource() {
        return colHeadingsSource;
    }

    public List<List<String>> getContextSource() {
        return contextSource;
    }
}
