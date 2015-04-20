package com.azquo.spreadsheet;

import com.azquo.memorydb.Name;

import java.util.List;

/**
 * Created by cawley on 16/04/15.
 *
 * all we may need to know about a cell as seen on the screen by a user.
 *
 * One point here is holding the headings against each cell but if not the headings then indexes would need to be saved
 * the reference will be 8 bytes to an object that exists anyway (in headings) vs 4 bytes for an int, I'm going to leave it for the mo
 * It was a set here, not sure whether to use it ro not
 *
 */
public class AzquoCell {
    public final boolean locked;
    public final ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName;
    public final List<DataRegionHeading> rowHeadings;
    public final List<DataRegionHeading> columnHeadings;
    public final List<Name> contexts;
    public final String stringValue;
    public final double doubleValue;

    public AzquoCell(boolean locked, ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName, List<DataRegionHeading> rowHeadings, List<DataRegionHeading> columnHeadings, List<Name> contexts, String stringValue, double doubleValue) {
        this.locked = locked;
        this.listOfValuesOrNamesAndAttributeName = listOfValuesOrNamesAndAttributeName;
        this.rowHeadings = rowHeadings;
        this.columnHeadings = columnHeadings;
        this.contexts = contexts;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
    }
}
