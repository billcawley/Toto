package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

import java.util.Collections;
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

    public enum BASIC_RESOLVE_FUNCTION {COUNT, AVERAGE, MAX, MIN}

    private final Name name;
    private final String attribute;
    private final boolean writeAllowed;
    private final BASIC_RESOLVE_FUNCTION function;
    private final Set<Name> nameCountSet;
    // easiest way to add this without disrupting - if we add more may need to just have a set and a flag for what it is for
    private final Set<Name> valueParentCountSet;
    // an identifier,
    private final String description;
    private final List<DataRegionHeading> offsetHeadings; // used when formatting hierarchy

    public DataRegionHeading(Name name, boolean writeAllowed, BASIC_RESOLVE_FUNCTION function, Set<Name> nameCountSet, Set<Name> valueParentCountSet, String description) {
        this(name, writeAllowed,function,nameCountSet,valueParentCountSet,description, null);
    }

    public DataRegionHeading(Name name, boolean writeAllowed, BASIC_RESOLVE_FUNCTION function, Set<Name> nameCountSet, Set<Name> valueParentCountSet, String description, List<DataRegionHeading> offsetHeadings) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
        this.function = function;
        this.nameCountSet =  nameCountSet != null ? Collections.unmodifiableSet(nameCountSet) : null;
        this.valueParentCountSet =  valueParentCountSet != null ? Collections.unmodifiableSet(valueParentCountSet) : null;
        this.description = description;
        this.offsetHeadings = offsetHeadings;
    }

    // no functions with attributes for the moment
    public DataRegionHeading(String attribute, boolean writeAllowed) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = null;
        this.nameCountSet = null;
        this.valueParentCountSet = null;
        this.description = null;
        this.offsetHeadings = null;
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

    public Set<Name> getNameCountSet() {
        return nameCountSet;
    }

    public Set<Name> getValueParentCountSet() {
        return valueParentCountSet;
    }

    public BASIC_RESOLVE_FUNCTION getFunction() {
        return function;
    }

    @Override
    public String toString() {
        return "DataRegionHeading{" +
                "name=" + name +
                ", attribute='" + attribute + '\'' +
                ", writeAllowed=" + writeAllowed +
                ", function=" + function +
                ", nameCountSetSize=" + (nameCountSet != null ? nameCountSet.size() : "") +
                '}';
    }

    public String getDescription() {
        return description;
    }

    public List<DataRegionHeading> getOffsetHeadings() {
        return offsetHeadings;
    }
}
