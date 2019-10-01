package com.azquo.spreadsheet;

import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 16/04/15.
 * <p/>
 * all we may need to know about a cell as seen on the screen by a user.
 * <p/>
 * One point here is holding the headings against each cell but if not the headings then indexes would need to be saved
 * the reference will be 8 bytes to an object that exists anyway (in headings) vs 4 bytes for an int, I'm going to leave it for the mo
 *
 * Now we're moving to a client/server model I can't push this object through to the client,
 *
 */
public class AzquoCell {
    private boolean locked;
    private final ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName;
    private final List<DataRegionHeading> rowHeadings;
    private final List<DataRegionHeading> columnHeadings;
    private final List<DataRegionHeading> contexts;
    // where this cell was before sorting, can be passed on through to the front end, makes finding a specifric cell later easier
    private final int unsortedRow;
    private final int unsortedCol;
    private final String stringValue;
    private final double doubleValue;
    private boolean highlighted;
    // after drilldown or provenance an opened spreadsheet might have a cell selected
    private final boolean selected;

    AzquoCell(boolean locked, ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName, List<DataRegionHeading> rowHeadings
            , List<DataRegionHeading> columnHeadings, List<DataRegionHeading> contexts, int unsortedRow, int unsortedCol, String stringValue, double doubleValue, boolean highlighted, boolean selected) {
        this.locked = locked;
        this.listOfValuesOrNamesAndAttributeName = listOfValuesOrNamesAndAttributeName;
        this.rowHeadings = rowHeadings;
        this.columnHeadings = columnHeadings;
        this.contexts = contexts;
        this.unsortedRow = unsortedRow;
        this.unsortedCol = unsortedCol;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
        this.highlighted = highlighted;
        this.selected = selected;
    }

    boolean isLocked() {
        return locked;
    }

    void setAsLocked() {
        this.locked = true;
    }

    public ListOfValuesOrNamesAndAttributeName getListOfValuesOrNamesAndAttributeName() {
        return listOfValuesOrNamesAndAttributeName;
    }

    public List<DataRegionHeading> getRowHeadings() {
        return rowHeadings;
    }

    public List<DataRegionHeading> getColumnHeadings() {
        return columnHeadings;
    }

    public List<DataRegionHeading> getContexts() {
        return contexts;
    }

    public String getStringValue() {
        return stringValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    boolean isHighlighted() {
        return highlighted;
    }

    void setAsHighlighted() {
        this.highlighted = true;
    }

    int getUnsortedRow() {
        return unsortedRow;
    }

    int getUnsortedCol() {
        return unsortedCol;
    }

    boolean isSelected() {
        return selected;
    }
}
