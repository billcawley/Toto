package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

import java.util.List;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 03/02/15.
 * <p/>
 * Headers of Rows and Columns and Context fields used to be Names, plain and simple.
 * <p/>
 * Now they can be attributes also (and may be other things too), thus this class which will now need to be
 * used to represent these headers and context.
 * <p/>
 * See no reason not to make this simple and immutable. Bottom line is that this can be either an attribute or a name
 *
 */
public class DataRegionHeading {
    public enum FUNCTION {COUNT, AVERAGE, MAX, MIN, NAMECOUNT, PATHCOUNT, VALUEPARENTCOUNT}

    private final Name name;
    private final String attribute;
    private final boolean writeAllowed;
    private final FUNCTION function;
    // either the name (normally) or the function as written in the case of name count path count etc.
    private final String description;
    private final List<DataRegionHeading> offsetHeadings; // used when formatting hierarchy
    private final Set<Name> valueFunctionSet;

    public DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, String description, Set<Name> valueFunctionSet) {
        this(name, writeAllowed,function,description, null, valueFunctionSet);
    }

    public DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, String description, List<DataRegionHeading> offsetHeadings, Set<Name> valueFunctionSet) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
        this.function = function;
        this.description = description;
        this.offsetHeadings = offsetHeadings;
        this.valueFunctionSet = valueFunctionSet;
    }

    // no functions with attributes for the moment
    public DataRegionHeading(String attribute, boolean writeAllowed) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = null;
        this.description = null;
        this.offsetHeadings = null;
        this.valueFunctionSet = null;
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

    public FUNCTION getFunction() {
        return function;
    }

    @Override
    public String toString() {
        return "DataRegionHeading{" +
                "name=" + name +
                ", attribute='" + attribute + '\'' +
                ", writeAllowed=" + writeAllowed +
                ", function=" + function +
                '}';
    }

    public String getDescription() {
        return description;
    }

    public List<DataRegionHeading> getOffsetHeadings() {
        return offsetHeadings;
    }

    public Set<Name> getValueFunctionSet() {
        return valueFunctionSet;
    }

    public boolean isNameFunction(){
        return isNameFunction(function);
    }
    // useful to be called outside if an instance
    public static boolean isNameFunction(FUNCTION function){
        return function != null && (function == FUNCTION.NAMECOUNT || function == FUNCTION.PATHCOUNT);
    }
}