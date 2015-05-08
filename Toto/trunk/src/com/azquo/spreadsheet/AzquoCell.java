package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

import java.util.List;

/**
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
    private final boolean locked;
    private final ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName;
    private final List<DataRegionHeading> rowHeadings;
    private final List<DataRegionHeading> columnHeadings;
    private final List<Name> contexts;
    private String stringValue;
    private double doubleValue;
    private boolean changed;
    private boolean highlighted;

    public AzquoCell(boolean locked, ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName, List<DataRegionHeading> rowHeadings
            , List<DataRegionHeading> columnHeadings, List<Name> contexts, String stringValue, double doubleValue, boolean highlighted) {
        this.locked = locked;
        this.listOfValuesOrNamesAndAttributeName = listOfValuesOrNamesAndAttributeName;
        this.rowHeadings = rowHeadings;
        this.columnHeadings = columnHeadings;
        this.contexts = contexts;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
        this.highlighted = highlighted;
    }

    public boolean isLocked() {
        return locked;
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

    public List<Name> getContexts() {
        return contexts;
    }

    public String getStringValue() {
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

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }
}
