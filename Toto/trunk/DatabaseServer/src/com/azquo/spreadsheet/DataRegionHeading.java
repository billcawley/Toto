package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd.
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
    public enum FUNCTION {COUNT//               Value function      The number of values rather than the sum
        , AVERAGE//                             Value function      The average value
        , MAX//                                 Value functions     Max and min values found
        , MIN
        , VALUEPARENTCOUNT//                    Value/Name function The number of names in a given set that are parents of the values found by the other cell definition features (e.g customers buying a product) (customer set
        , VALUESET//                            Value function      a replacement for using 'as' <temporaryset>  - creates a set used for that heading only
        , PERCENTILE//                          Value function      <set, percentileInt>  gives the value in the set at that percentile
        , PERCENTILENZ//                        Value function       as above ignoring zero figures
        , STDEVA//                              Value function      Standard deviation
        , SET//                                 Name function       Comma separated list of elements of the set
        , FIRST//                               Name function       The first or last elements of the set
        , LAST
        , NAMECOUNT//                           Name function       The number of elements of the set
        , NAMELIST //                           Name function       The names of the elements above as a comma-separated list
        , EXISTS //                             Name function       returns 1 if the set has elements
        , PATHCOUNT//                           Name function       The number of paths between the sets (e.g. the number of mailings sent to a specified group of customers)
        , PERMUTE//                             Heading function    The system will find all the combinations of the immediate children of the list to be permuted, selected on the basis of sharing common descendants
        , EXACT//                               Value function      exact meaning get only values that match exactly the name passed. Generally would only be one value
        , ALLEXACT//                            Value function      as above for all names in cell
        , AUDITDATE//                           Audit function      the provenance date of the latest value in the cell
        , AUDITCHANGEDBY//                      Audit function      the name of the person who last changed the value
        , BESTMATCH//                           Value function      the nearest value of the cell to the value given (e.g the latest date of change BESTMATCH(Change dates, 2018-01-01) for i
        , BESTVALUEMATCH//                      Value function      BESTVALUEMATCH(<set expression>,<value>) is equivalent to an Excel LOOKUP with the last parameter set to ‘true’.   It selects from the values found for a cell to find the value attached to the element of the set that best matches the value given.   As a typical example, if the price of a product changes sporadically, and the change dates are recorded in the set ‘Product price change dates’, the price that is in force at a particular day – e.g. 01/01/2018 will be: Bestvaluematch(`Product price change dates` children,2018-01-01)
        , BESTNAMEMATCH//                       Value/Name function BESTNAMEMATCH(<set expression>,<value>) is similar to the above, but finds the name in the set which is most close to the value given.  In the above example it would show the last date on which any product changed price.
        , BESTNAMEVALUEMATCH//                  Value/Name function BESTNAMEVALUEMATCH(<set expression>,<value>) is similar to ‘bestvaluematch’ in finding the values for the particular context, but then shows the name attached to the value found.  In the example above it would show the last date on which a particular product changed price.
        , DICTIONARY//                          Name function       Replacing the dictionary on importing. Passed a set of names and an attribute to look for in those names which will be used to define dictionary criteria, see comments in the code itself for syntax
        , SCALE//                               Value function      uses the 'scale' terms to create a lookup list, which is then applied to the individual values in the rest of the cell names to create a multiplier (e.g. for FX)
        , DEFAULT//                             Value function      <set, defaultValue>  if nothing found use the string default value
    }

    enum SUFFIX {UNLOCKED, LOCKED, SPLIT}
    /*
    Additional criteria. Existing locking stands but a new rule is to lock by default on a composite value
     */
    private Name name;
    private String attribute;
    private final boolean writeAllowed;
    private FUNCTION function;
    private final SUFFIX suffix;
    // either the name (normally) or the function as written in the case of name count path count etc.
    // Useful for debugging and for storing queries that can only be resolved later e.g. with [ROWHEADING]
    // also used simply as described - when a function has a string parameter or parameters
    private String stringParameter;
    private final List<DataRegionHeading> offsetHeadings; // used when formatting hierarchy
    private Collection<Name> valueFunctionSet; // just used for valueparentcount and valueset
    private Collection<Name> valueFunctionChildren; // just used for valueparentcount and valueset
    private final double doubleParameter; // initially used for percentile, could be others. I think this needs to be rearranged at some point but for the moment make percentile work.
    private final Collection<Name> attributeSet;
    private final String calculation; //will later be used as a calculation - coming in from the heading rather than the name, see value service 334

    DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String stringParameter, Set<Name> valueFunctionSet) {
        this(name, writeAllowed,function,suffix, stringParameter, null, valueFunctionSet, 0, null);
    }

    public DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String stringParameter, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter) {
        this(name, writeAllowed,function,suffix, stringParameter, offsetHeadings, valueFunctionSet, doubleParameter, null);
    }

    // todo - maybe factor the constructors a little :)
    public DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String stringParameter, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter, String calculation) {
        this.name = name;
        this.attribute = null;
        this.writeAllowed = writeAllowed;
        this.function = function;
        this.suffix = suffix;
        this.stringParameter = stringParameter;
        this.offsetHeadings = offsetHeadings;
        this.valueFunctionSet = valueFunctionSet;
        this.doubleParameter = doubleParameter;
        this.attributeSet = null;
        this.calculation = calculation;
        Collection<Name> valueFunctionChildren = new HashSet<>();
        if (valueFunctionSet!=null) {
            for (Name n : valueFunctionSet) {
                valueFunctionChildren.addAll(n.findAllChildren());
            }
        }
        this.valueFunctionChildren = valueFunctionChildren;
     }

    DataRegionHeading(String attribute, boolean writeAllowed, Collection<Name> attributeSet) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = null;
        this.suffix = null;
        this.stringParameter = null;
        this.offsetHeadings = null;
        this.valueFunctionSet = null;
        this.doubleParameter = 0;
        this.attributeSet = attributeSet;
        this.calculation = null;
    }

    // new thing - function with an attribute, initially just dictionary
    DataRegionHeading(String attribute, boolean writeAllowed, FUNCTION function, SUFFIX suffix, String stringParameter, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter) {
        this.name = null;
        this.attribute = attribute;
        this.writeAllowed = writeAllowed;
        this.function = function;
        this.suffix = suffix;
        this.stringParameter = stringParameter;
        this.offsetHeadings = offsetHeadings;
        this.valueFunctionSet = valueFunctionSet;
        this.doubleParameter = doubleParameter;
        this.attributeSet = null;
        this.calculation = null;
    }

    public Name getName() {
        return name;
    }

    public void amendPermutedHeading(Collection<Name> names){
        name = null;
        if (names !=null){
            stringParameter ="-";
            function = FUNCTION.VALUESET;
            valueFunctionSet = names;

        }else{
            attribute = ".";
        }
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

    public String getStringParameter() {
        return stringParameter;
    }

    List<DataRegionHeading> getOffsetHeadings() {
        return offsetHeadings;
    }

    Collection<Name> getValueFunctionSet() {
        return valueFunctionSet;
    }
    Collection<Name> getValueFunctionChildren() {
        return valueFunctionChildren;
    }

    public boolean isExpressionFunction(){
        return isExpressionFunction(function);
    }
    // useful to be called outside if an instance
    static boolean isExpressionFunction(FUNCTION function){
        return (function == FUNCTION.NAMECOUNT
                || function == FUNCTION.NAMELIST
                || function == FUNCTION.PATHCOUNT
                || function == FUNCTION.SET
                || function == FUNCTION.FIRST
                || function == FUNCTION.LAST
                || function == FUNCTION.VALUESET
                || function == FUNCTION.BESTNAMEMATCH);
    }

    static boolean isBestMatchFunction(FUNCTION function){
        return (function == FUNCTION.BESTMATCH
                || function == FUNCTION.BESTVALUEMATCH
                || function == FUNCTION.BESTNAMEMATCH
                || function == FUNCTION.BESTNAMEVALUEMATCH);
    }

    double getDoubleParameter() {
        return doubleParameter;
    }

    String getCalculation() { return calculation; }

}