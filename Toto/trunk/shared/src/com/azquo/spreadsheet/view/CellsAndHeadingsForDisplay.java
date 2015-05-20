package com.azquo.spreadsheet.view;

import java.io.Serializable;
import java.util.List;

/**
 * Created by cawley on 11/05/15.
 *
 * I don't see much reason for this class to be mutable though the data cells can be changed
 */
public class CellsAndHeadingsForDisplay implements Serializable {

    private final List<List<String>> columnHeadings;
    private final List<List<String>> rowHeadings;
    private final List<List<CellForDisplay>> data;

    public CellsAndHeadingsForDisplay(List<List<String>> columnHeadings, List<List<String>> rowHeadings, List<List<CellForDisplay>> data) {
        this.columnHeadings = columnHeadings;
        this.rowHeadings = rowHeadings;
        this.data = data;
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
}
