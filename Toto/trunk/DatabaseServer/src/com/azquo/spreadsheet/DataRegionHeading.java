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
    public enum FUNCTION {COUNT, AVERAGE, MAX, MIN, VALUEPARENTCOUNT, SET, FIRST, LAST, NAMECOUNT, PATHCOUNT, PERMUTE}
    /*
    COUNT               Value function      The number of values rather than the sum
    AVERAGE             Value function      The average value
    MAX, MIN            Value functions     Max and min values found
    VALUEPARENTCOUNT    Value/Name function The number of names in a given set that are parents of the values found by the other cell definition features (e.g customers buying a product) (customer set
    SET                 Name function       Comma separated list of elements of the set
    FIRST, LAST         Name function       The first or last elements of the set
    NAMECOUNT           Name function       The number of elements of the set
    PATHCOUNT           Name function       The number of paths between the sets (e.g. the number of mailings sent to a specified group of customers)
    PERMUTE             Heading function    The system will find all the combinations of the immediate children of the list to be permuted, selected on the basis of sharing common descendants

     */
    public enum SUFFIX {UNLOCKED, LOCKED, SPLIT}
    /*
    Additional criteria. Existing locking stands but a new rule is to lock by default on a composite value
     */
    private final Name name;
    private final String attribute;
    private final boolean writeAllowed;
    private final FUNCTION function;
    private final SUFFIX suffix;
    // either the name (normally) or the function as written in the case of name count path count etc.
    private final String description;
    private final List<DataRegionHeading> offsetHeadings; // used when formatting hierarchy
    private final Set<Name> valueFunctionSet;

    DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String description, Set<Name> valueFunctionSet) {
        this(name, writeAllowed,function,suffix, description, null, valueFunctionSet);
    }

    DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String description, List<DataRegionHeading> offsetHeadings, Set<Name> valueFunctionSet) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
        this.function = function;
        this.suffix = suffix;
        this.description = description;
        this.offsetHeadings = offsetHeadings;
        this.valueFunctionSet = valueFunctionSet;
     }

    // no functions with attributes for the moment
    DataRegionHeading(String attribute, boolean writeAllowed) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = null;
        this.suffix = null;
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

    boolean isWriteAllowed() {
        return writeAllowed;
    }

    public FUNCTION getFunction() {
        return function;
    }

    public SUFFIX getSuffix() {
        return suffix;
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

    String getDescription() {
        return description;
    }

    List<DataRegionHeading> getOffsetHeadings() {
        return offsetHeadings;
    }

    Set<Name> getValueFunctionSet() {
        return valueFunctionSet;
    }

    boolean isNameFunction(){
        return isNameFunction(function);
    }
    // useful to be called outside if an instance
    static boolean isNameFunction(FUNCTION function){
        return function != null && (function == FUNCTION.NAMECOUNT || function == FUNCTION.PATHCOUNT || function == FUNCTION.SET || function == FUNCTION.FIRST || function == FUNCTION.LAST);
    }
}