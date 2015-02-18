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
public class DataRegionHeading {

    private final Name name;
    private final String attribute;
    private boolean writeAllowed;

    public DataRegionHeading(Name name, boolean writeAllowed) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
    }

    public DataRegionHeading(String attribute, boolean writeAllowed) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
    }

    public Name getName() {
        return name;
    }

    public String getAttribute() {
        return attribute;
    }

    public boolean isWriteAllowed() {
        return writeAllowed;
    }
}
