package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 03/02/15.
 * <p/>
 * Headings of Rows and Columns and Context fields used to be Names, plain and simple.
 * <p/>
 * Now they can be attributes also (and may be other things too), thus this class which will now need to be
 * used to represent these headings and context.
 * <p/>
 * See no reason not to make this simple and immutable. Bottom line is that this can be either an attribute or a name
 *
 */
public class DataRegionHeading {
    // todo - average min and max use calculation?
    public enum FUNCTION {COUNT, AVERAGE, MAX, MIN, VALUEPARENTCOUNT, VALUESET, PERCENTILE, PERCENTILENZ, STDEVA, SET, FIRST, LAST, NAMECOUNT, PATHCOUNT, PERMUTE, EXACT, ALLEXACT, AUDITDATE, AUDITCHANGEDBY, BESTMATCH, BESTNAMEMATCH, BESTVALUEMATCH, BESTNAMEVALUEMATCH}
    /*
    COUNT               Value function      The number of values rather than the sum
    AVERAGE             Value function      The average value
    MAX, MIN            Value functions     Max and min values found
    VALUEPARENTCOUNT    Value/Name function The number of names in a given set that are parents of the values found by the other cell definition features (e.g customers buying a product) (customer set
    VALUESET            Value function      a replacement for using 'as' <temporaryset>  - creates a set used for that heading only
    PERCENTILE          Value function      <set, percentileInt>  gives the value in the set at that percentile
    PERCENTILENZ        Value function       as above ignoring zero figures
    STDEVA              Value function      Standard deviation
    SET                 Name function       Comma separated list of elements of the set
    FIRST, LAST         Name function       The first or last elements of the set
    NAMECOUNT           Name function       The number of elements of the set
    PATHCOUNT           Name function       The number of paths between the sets (e.g. the number of mailings sent to a specified group of customers)
    PERMUTE             Heading function    The system will find all the combinations of the immediate children of the list to be permuted, selected on the basis of sharing common descendants
    EXACT               Value function      exact meaning get only values that match exactly the name passed. Generally would only be one value
    ALLEXACT            Value function      as above for all names in cell
    AUDITDATE           Audit function      the provenance date of the latest value in the cell
    AUDITCHANGEDBY      Audit function      the name of the person who last changed the value
    BESTMATCH           Value function      the nearest value of the cll to the value given (e.g the latest date of change BESTMATCH(Change dates, 2018-01-01) for i
     */
    enum SUFFIX {UNLOCKED, LOCKED, SPLIT}
    /*
    Additional criteria. Existing locking stands but a new rule is to lock by default on a composite value
     */
    private final Name name;
    private final String attribute;
    private final boolean writeAllowed;
    private final FUNCTION function;
    private final SUFFIX suffix;
    // either the name (normally) or the function as written in the case of name count path count etc. Useful for debugging and for storing queries that cna only be resolved later e.g. with [ROWHEADING]
    private final String description;
    private final List<DataRegionHeading> offsetHeadings; // used when formatting hierarchy
    private final Collection<Name> valueFunctionSet; // just used for valueparentcount
    private final double doubleParameter; // initially used for percentile, could be others. I think this needs to be rearranged at some point but for the moment make percentile work.
    private final Collection<Name> attributeSet;
    private final String calculation; //the description having passed through the preparation routines

    DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String description, Set<Name> valueFunctionSet) {
        this(name, writeAllowed,function,suffix, description, null, valueFunctionSet, 0, null);
    }

    DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String description, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter) {
        this(name, writeAllowed,function,suffix, description, offsetHeadings, valueFunctionSet, doubleParameter, null);
    }


    DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String description, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter, String calculation) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
        this.function = function;
        this.suffix = suffix;
        this.description = description;
        this.offsetHeadings = offsetHeadings;
        this.valueFunctionSet = valueFunctionSet;
        this.doubleParameter = doubleParameter;
        this.attributeSet = null;
        this.calculation = calculation;
     }

    // no functions with attributes for the moment
    DataRegionHeading(String attribute, boolean writeAllowed, Collection<Name> attributeSet) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = null;
        this.suffix = null;
        this.description = null;
        this.offsetHeadings = null;
        this.valueFunctionSet = null;
        this.doubleParameter = 0;
        this.attributeSet = attributeSet;
        this.calculation = null;

    }

    public Name getName() {
        return name;
    }

    public String getAttribute() {
        return attribute;
    }

    public Collection<Name> getAttributeSet() {return attributeSet; }

    boolean isWriteAllowed() {
        return writeAllowed;
    }

    public FUNCTION getFunction() {
        return function;
    }

    SUFFIX getSuffix() {
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

    String getDebugInfo() {
        String nameName = "";
        if (name != null){
            String nameString = name.getDefaultDisplayName();
            if (nameString==null && name.getParents()!=null){//temporary set
                nameString = name.getParents().iterator().next().getDefaultDisplayName();
            }
            nameName =  "Name : " + nameString;
        }
        return nameName + (attribute != null ? " Attribute : " + attribute : "")
                + (function != null ? " Function : " + function : "");
    }

    public String getDescription() {
        return description;
    }

    List<DataRegionHeading> getOffsetHeadings() {
        return offsetHeadings;
    }

    Collection<Name> getValueFunctionSet() {
        return valueFunctionSet;
    }

    public boolean isExpressionFunction(){
        return isExpressionFunction(function);
    }
    // useful to be called outside if an instance
    static boolean isExpressionFunction(FUNCTION function){
        return function != null && (function == FUNCTION.NAMECOUNT
                || function == FUNCTION.PATHCOUNT
                || function == FUNCTION.SET
                || function == FUNCTION.FIRST
                || function == FUNCTION.LAST
                || function == FUNCTION.VALUESET
                || function == FUNCTION.BESTNAMEMATCH);
    }

    static boolean isBestMatchFunction(FUNCTION function){
        return function != null && (function == FUNCTION.BESTMATCH
                || function == FUNCTION.BESTVALUEMATCH
                || function == FUNCTION.BESTNAMEMATCH
                || function == FUNCTION.BESTNAMEVALUEMATCH);
    }

    double getDoubleParameter() {
        return doubleParameter;
    }

    public String getCalculation() { return calculation; }


}