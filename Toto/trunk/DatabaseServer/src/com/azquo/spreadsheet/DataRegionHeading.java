package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

/**
 * Created by cawley on 03/02/15.
 * <p/>
 * Headers of Rows and Columns and Context fields used to be Names, plain and simple.
 * <p/>
 * Now they can be attributes also (and may be other things too), thus this class which will now need to be
 * used to represent these headers and context.
 * <p/>
 * See no reason not to make this simple and immutable. Bottom line is that this can be either an attribute or a name
 *
 * OK, in preparation for splitting the the UI/Rendering I need to get name out of here
 */
public class DataRegionHeading {

    public enum BASIC_RESOLVE_FUNCTION {COUNT, AVERAGE, MAX, MIN};

    private final Name name;
    private final String attribute;
    private final boolean writeAllowed;
    private final BASIC_RESOLVE_FUNCTION function;

    public DataRegionHeading(Name name, boolean writeAllowed, BASIC_RESOLVE_FUNCTION function) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
        this.function = function;
    }

    // no functions with attributes for the moment
    public DataRegionHeading(String attribute, boolean writeAllowed) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = null;
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

    public BASIC_RESOLVE_FUNCTION getFunction() {
        return function;
    }
}
