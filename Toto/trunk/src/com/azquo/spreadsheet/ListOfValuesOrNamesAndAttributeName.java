package com.azquo.spreadsheet;

import com.azquo.memorydb.Name;
import com.azquo.memorydb.Value;

import java.util.List;

/**
 * Created by cawley on 16/04/15.
 */
public class ListOfValuesOrNamesAndAttributeName {
    // for the map of values for a region. Used to be just a list of values for each cell but now given attributes it could be a lit of names and and attributes - typically will be just one attribute and name
    // a little similar to name or value I suppose though this needs attributes specified
    private final List<Value> values;
    private final List<Name> names;
    private final List<String> attributeNames;

    public ListOfValuesOrNamesAndAttributeName(List<Name> names, List<String> attributeNames) {
        this.names = names;
        this.attributeNames = attributeNames;
        this.values = null;
    }

    public ListOfValuesOrNamesAndAttributeName(List<Value> values) {
        this.values = values;
        this.names = null;
        this.attributeNames = null;
    }

    public List<Value> getValues() {
        return values;
    }

    public List<Name> getNames() {
        return names;
    }

    public List<String> getAttributeNames() {
        return attributeNames;
    }
}
