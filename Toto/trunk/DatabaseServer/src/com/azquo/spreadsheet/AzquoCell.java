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
 * todo : not that important but consider the unused functions
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
    private String stringValue;
    private double doubleValue;
    private boolean changed;
    private boolean highlighted;
    // after drilldown or provenance an opened spreadsheet might have a cell selected
    private boolean selected;

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

    public void setLocked(boolean locked) {
        this.locked = locked;
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

    List<DataRegionHeading> getContexts() {
        return contexts;
    }

    String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
        changed = true;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(double doubleValue) {
        this.doubleValue = doubleValue;
        changed = true;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    boolean isHighlighted() {
        return highlighted;
    }

    void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
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

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
