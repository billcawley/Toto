package com.azquo.service;

import com.azquo.memorydb.Name;

/**
 * Created by cawley on 03/02/15.
 *
 * Headers of Rows and Columns and Context fields used to be Names, plain and simple.
 *
 * Now they can be attributes also (and may be other things too), thus this class which will now need to be
 * used to represent these headers and context.
 *
 * See no reason not to make this simple and immutable. Bottom line is that this can be either an attribute or a name
 */
public class DataRegionHeader {

    private final Name name;
    private final String attribute;

    public DataRegionHeader(Name name) {
        this.name = name;
        this.attribute = null;
    }

    public DataRegionHeader(String attribute) {
        this.name = null;
        this.attribute = attribute;
    }

    public Name getName() {
        return name;
    }

    public String getAttribute() {
        return attribute;
    }
}
