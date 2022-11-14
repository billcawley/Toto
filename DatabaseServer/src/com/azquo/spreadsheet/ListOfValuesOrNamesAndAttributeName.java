package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;

import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * Created by cawley on 16/04/15.
 *
 * for the map of values for a region. Used to be just a list of values for each cell but now given attributes it could
 * be a lit of names and and attributes - typically will be just one attribute and name
 * a little similar to name or value I suppose though this needs attributes specified
 */
public class ListOfValuesOrNamesAndAttributeName {
    private final List<Value> values;
    private final List<Name> names;
    private final List<String> attributeNames;

    ListOfValuesOrNamesAndAttributeName(List<Name> names, List<String> attributeNames) {
        this.names = names;
        this.attributeNames = attributeNames;
        this.values = null;
    }

    ListOfValuesOrNamesAndAttributeName(List<Value> values) {
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

    List<String> getAttributeNames() {
        return attributeNames;
    }
}
