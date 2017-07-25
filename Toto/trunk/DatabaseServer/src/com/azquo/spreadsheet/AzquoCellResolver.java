package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.NameUtils;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.MutableBoolean;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.ValueService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;


import java.util.*;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 * <p>
 * Functions specifically to resolve individual cells, nothing to do with sorting filtering etc.
 */
public class AzquoCellResolver {
    // hacky, I just need a way to pass the values without doing a redundant addAll
    public static class ValuesHook {
        public List<Value> values = null;
        public List<Double> calcValues = null; // memory overhead?
    }

    static AzquoCell getAzquoCellForHeadings(AzquoMemoryDBConnection connection, List<DataRegionHeading> rowHeadings, List<DataRegionHeading> columnHeadings
            , List<DataRegionHeading> contextHeadings, int rowNo, int colNo, List<String> languages, int valueId, Map<List<Name>, Set<Value>> nameComboValueCache) throws Exception {
        return getAzquoCellForHeadings(connection, rowHeadings, columnHeadings, contextHeadings, rowNo, colNo, languages, valueId, nameComboValueCache, null);
    }

    // Now deals with name functions which evaluate a name expression for each cell as opposed to value functions which work off names already resolved in the DataRegionHeadings
    static AzquoCell getAzquoCellForHeadings(AzquoMemoryDBConnection connection, List<DataRegionHeading> rowHeadings, List<DataRegionHeading> columnHeadings
            , List<DataRegionHeading> contextHeadings, int rowNo, int colNo, List<String> languages, int valueId, Map<List<Name>, Set<Value>> nameComboValueCache, StringBuilder debugInfo) throws Exception {
        if (debugInfo != null) {
            debugInfo.append("Row Headings\n\n");
            for (DataRegionHeading rowHeading : rowHeadings) {
                if (rowHeading != null) {
                    debugInfo.append("\t" + rowHeading.getDebugInfo() + "\n");
                }
            }
            debugInfo.append("\nColumn Headings\n\n");
            for (DataRegionHeading columnHeading : columnHeadings) {
                if (columnHeading != null) {
                    debugInfo.append("\t" + columnHeading.getDebugInfo() + "\n");
                }
            }
            debugInfo.append("\nContext\n\n");
            for (DataRegionHeading context : contextHeadings) {
                if (context != null) {
                    debugInfo.append("\t" + context.getDebugInfo() + "\n");
                }
            }
        }
        boolean selected = false;
        String stringValue = "";
        double doubleValue = 0;
        MutableBoolean locked = new MutableBoolean(); // we use a mutable boolean as the functions that resolve the cell value may want to set it
        boolean hasData = false;
        for (DataRegionHeading heading : rowHeadings) {
            if (heading != null && (heading.getName() != null || heading.getAttribute() != null || heading.getFunction() != null)) {
                hasData = true;
                break;
            }
        }
        if (hasData) {
            hasData = false;
            for (DataRegionHeading heading : columnHeadings) {
                if (heading != null && (heading.getName() != null || heading.getAttribute() != null || heading.getFunction() != null)) {
                    hasData = true;
                    break;
                }
            }
        }
        if (!hasData) {
            return new AzquoCell(locked.isTrue, null, rowHeadings, columnHeadings, contextHeadings, rowNo, colNo, "", doubleValue, false, false);
        }
        ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName = null;
        // ok under new logic the complex functions will work very differently evaluating a query for each cell rather than gathering headings as below. Hence a big if here
        DataRegionHeading nameFunctionHeading = null;
        for (DataRegionHeading columnHeading : columnHeadings) { // try column headings first
            if (columnHeading != null && columnHeading.isNameFunction()) {
                nameFunctionHeading = columnHeading;
                break;
            }
        }
        if (nameFunctionHeading == null) { // then check the row headings
            for (DataRegionHeading rowHeading : rowHeadings) {
                if (rowHeading != null && rowHeading.isNameFunction()) {
                    nameFunctionHeading = rowHeading;
                    break;
                }
            }
        }
        if (nameFunctionHeading == null) { // finally context for permuted name functions
            for (DataRegionHeading contextHeading : contextHeadings) {
                if (contextHeading != null && contextHeading.isNameFunction()) {
                    nameFunctionHeading = contextHeading;
                    break;
                }
            }
        }
        // todo re-implement caching here if there are performance problems - I did use findOverlap before here but I don't think is applicable now the name query is much more flexible. Caching fragments of the query would be the thing
        if (nameFunctionHeading != null) {
            String cellQuery = nameFunctionHeading.getDescription();
            //testing here for [rowheading], [rowheading2] etc...
            String ROWHEADING = "[ROWHEADING";
            if (!rowHeadings.isEmpty()) {
                if (!cellQuery.contains(ROWHEADING)) {
                    ROWHEADING = ROWHEADING.toLowerCase();
                }
                if (cellQuery.contains(ROWHEADING)) {
                    String filler = "";
                    for (int colNo1 = 0; colNo1 < rowHeadings.size(); colNo1++) {
                        String fillerAll = ROWHEADING + filler + "]";
                        if (cellQuery.contains(fillerAll)) {
                            if (rowHeadings.get(colNo1).getName() == null) {
                                cellQuery = "";
                            } else {
                                cellQuery = cellQuery.replace(fillerAll, NameUtils.getFullyQualifiedDefaultDisplayName(rowHeadings.get(colNo1).getName())); // we assume the row heading has a "legal" description. Probably a name identifier !1234
                            }
                        }
                        filler = (colNo1 + 2) + "";
                    }
                }
            }
            String COLUMNHEADING = "[COLUMNHEADING]";
            String COLUMNHEADINGLOWERCASE = "[columnheading]";
            if (!columnHeadings.isEmpty() && (cellQuery.contains(COLUMNHEADING) || cellQuery.contains(COLUMNHEADINGLOWERCASE))) {
                cellQuery = cellQuery.replace(COLUMNHEADING, NameUtils.getFullyQualifiedDefaultDisplayName(columnHeadings.get(0).getName()))
                        .replace(COLUMNHEADINGLOWERCASE, NameUtils.getFullyQualifiedDefaultDisplayName(columnHeadings.get(0).getName())); // and now the col headings
            }
            if (debugInfo != null) {
                debugInfo.append("\nFunction\n\n");
                debugInfo.append("\t" + nameFunctionHeading.getFunction() + "\n");
                debugInfo.append("\nQuery\n\n");
                debugInfo.append("\t" + cellQuery + "\n");
            }
            locked.isTrue = true; // they cant edit the results from complex functions
            if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) { // a straight set but with [ROWHEADING] as part of the criteria
                Set<Name> namesToCount = HashObjSets.newMutableSet(); // I think this will be faster for purpose
                NameQueryParser.parseQuery(connection, cellQuery, languages, namesToCount, false);
                doubleValue = namesToCount.size();
                stringValue = doubleValue + "";
            } else if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.PATHCOUNT) { // new syntax, before it was name, set now it's set, set. Sticking to very basic , split
                //todo - this parsing needs to happen in the DateRegionHeadingService and needs to be robust to commas in names!
                String[] twoSets = nameFunctionHeading.getDescription().split(","); // we assume this will give an array of two, I guess see if this is a problem
                Set<Name> leftSet = HashObjSets.newMutableSet();
                Set<Name> rightSet = HashObjSets.newMutableSet();
                NameQueryParser.parseQuery(connection, twoSets[0], languages, leftSet, false);
                NameQueryParser.parseQuery(connection, twoSets[1], languages, rightSet, false);
                // ok I have the two sets, I got rid of total name count (which featured caching), I'm going to do the nuts and bolts here, need to think a little
                Set<Name> alreadyTested = HashObjSets.newMutableSet();
                // ok this should be like the inside of totalSetIntersectionCount but dealing with left set as the first parameter not a name.
                // Notable that the left set is expanded out to try intersecting with the right set which is "as is", this needs testing
                int count = 0;
                for (Name child : leftSet) {
                    if (rightSet.contains(child)) {
                        count++;
                    }
                }
                if (count == 0) { // I think we only go ahead if there was no intersection at the top level - need to discuss with WFC
                    for (Name child : leftSet) {
                        if (child.hasChildren()) {
                            count += totalSetIntersectionCount(child, rightSet, alreadyTested, 0);
                        }
                    }
                }
                doubleValue = count;
                stringValue = count + "";
            } else if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.SET) {
                final Collection<Name> set = NameQueryParser.parseQuery(connection, cellQuery, languages, true);
                doubleValue = 0;
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Name name : set) {
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(name.getDefaultDisplayName()); // make use the languages? Maybe later.
                    first = false;
                }
                stringValue = sb.toString();
            } else if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.FIRST) { // we may have to pass a hint about ordering to the query parser, let's see how it goes without it
                final Collection<Name> set = NameQueryParser.parseQuery(connection, cellQuery, languages, true);
                doubleValue = 0;
                stringValue = set.isEmpty() ? "" : set.iterator().next().getDefaultDisplayName();
            } else if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.LAST) {
                final Collection<Name> set = NameQueryParser.parseQuery(connection, cellQuery, languages, true);
                doubleValue = 0;
                stringValue = "";
                for (Name name : set) { //a bit of a lazy way of doing things but it should be fine, plus with only a collection interface not sure of how to get the last!
                    stringValue = name.getDefaultDisplayName();
                }
            }
        } else {// conventional type (sum) or value function
            // changing these collections to lists
            List<DataRegionHeading> headingsForThisCell = new ArrayList<>(rowHeadings.size() + columnHeadings.size() + contextHeadings.size()); // moving to lists, don't see a problem (!)
            List<DataRegionHeading> rowAndColumnHeadingsForThisCell = null;
            //check that we do have both row and column headings, otherwise blank them the cell will be blank (danger of e.g. a sum on the name "Product"!)
            for (DataRegionHeading heading : rowHeadings) {
                if (heading != null && (heading.getName() != null || !isDot(heading))) {
                    headingsForThisCell.add(heading);
                }
            }
            int hCount = headingsForThisCell.size();
            boolean checked = true;
            if (hCount > 0) {
                for (DataRegionHeading heading : columnHeadings) {
                    if (heading != null && (heading.getName() != null || !isDot(heading))) {
                        headingsForThisCell.add(heading);
                    }
                }
                rowAndColumnHeadingsForThisCell = new ArrayList<>(headingsForThisCell);
                if (isDot(rowHeadings.get(rowHeadings.size() - 1)) || isDot(columnHeadings.get(columnHeadings.size() - 1))) {
                    locked.isTrue = true;
                }
                if (headingsForThisCell.size() > hCount) {
                    headingsForThisCell.addAll(contextHeadings);
                } else {
                    headingsForThisCell.clear();
                    checked = false;
                }
            }
            for (DataRegionHeading heading : headingsForThisCell) {
                if (heading.getName() == null && heading.getAttribute() == null) { // a redundant check? todo : confirm
                    checked = false;
                }
                if (!heading.isWriteAllowed()) { // this replaces the isallowed check that was in the functions that resolved the cell values
                    locked.isTrue = true;
                }
            }
            Collections.reverse(headingsForThisCell); // here is the easiest place to do this, it was added in row, column, context order, we want to reverse this to improve caching on the names in a bit

            if (!checked) { // no valid row/col combo
                locked.isTrue = true;
            } else {
                // ok new logic here, we need to know if we're going to use attributes or values
                DataRegionHeading functionHeading = null;
                DataRegionHeading.FUNCTION function = null;
                Collection<Name> valueFunctionSet = null;
                double functionDoubleParameter = 0;
                for (DataRegionHeading heading : headingsForThisCell) {
                    if (heading.getFunction() != null) { // should NOT be a name function, that should have been caught before
                        functionHeading = heading;
                        function = functionHeading.getFunction();
                        if (function == DataRegionHeading.FUNCTION.PERCENTILE || function == DataRegionHeading.FUNCTION.PERCENTILENZ) { // hacky, any way around that?
                            functionDoubleParameter = heading.getDoubleParameter();
                        }
                        valueFunctionSet = heading.getValueFunctionSet(); // value function e.g. value parent count can allow a name set to be defined
                        if (debugInfo != null) {
                            debugInfo.append("\nFunction\n\n");
                            debugInfo.append("\t" + function + "\n"); // was nameFunctionHeading.getFunction(), think that was wrong
                        }
                        break; // can't combine functions I don't think
                    }
                }
                if (!DataRegionHeadingService.headingsHaveAttributes(headingsForThisCell)) { // we go the value route (as it was before allowing attributes), need the headings as names,
                    ValuesHook valuesHook = new ValuesHook(); // todo, can we use this only when necessary?
                    // now , get the function from the headings
                    if (function != null && !function.equals(DataRegionHeading.FUNCTION.ALLEXACT)) {
                        locked.isTrue = true;
                    }
                    if (headingsForThisCell.size() > 0) {
                        Value valueToTestFor = null;
                        if (valueId > 0) {
                            valueToTestFor = connection.getAzquoMemoryDB().getValueById(valueId);
                        }
                        if (function == null || !function.equals(DataRegionHeading.FUNCTION.ALLEXACT)) {
                            for (DataRegionHeading lockCheck : headingsForThisCell) {
                                //'unlocked' on any cell allows entry
                                if (lockCheck.getSuffix() == DataRegionHeading.SUFFIX.UNLOCKED) {
                                    locked.isTrue = false;
                                    break;
                                } else if (lockCheck.getSuffix() == DataRegionHeading.SUFFIX.LOCKED) {
                                    locked.isTrue = true;
                                } else if (lockCheck.getName() != null && lockCheck.getName().hasChildren() && // a name with children so default locked UNLESS defined as unlocked or split
                                        lockCheck.getSuffix() != DataRegionHeading.SUFFIX.UNLOCKED && lockCheck.getSuffix() != DataRegionHeading.SUFFIX.SPLIT) {
                                    locked.isTrue = true;
                                }
                            }
                        }
                        doubleValue = ValueService.findValueForNames(connection, DataRegionHeadingService.namesFromDataRegionHeadings(headingsForThisCell), locked, valuesHook, languages, functionHeading, nameComboValueCache, debugInfo);
                        if (function == DataRegionHeading.FUNCTION.VALUEPARENTCOUNT && valueFunctionSet != null) { // then value parent count, we're going to override the double value just set
                            // now, find all the parents and cross them with the valueParentCountHeading set
                            Set<Name> allValueParents = HashObjSets.newMutableSet();
                            for (Value v : valuesHook.values) {
                                for (Name n : v.getNames()) {
                                    allValueParents.add(n); // add the name
                                    allValueParents.addAll(n.findAllParents()); // and all it's parents
                                }
                            }
                            // now find the overlap between the value parents and the set in the heading
                            if (valueFunctionSet.size() < allValueParents.size()) {
                                doubleValue = findOverlap(valueFunctionSet, allValueParents);
                            } else {
                                doubleValue = findOverlap(allValueParents, valueFunctionSet);
                            }
                        }
                        if (function == DataRegionHeading.FUNCTION.PERCENTILE || function == DataRegionHeading.FUNCTION.PERCENTILENZ) {
                            /*
Calculation is

Sort values into order,  work out between which two the percentile sits, and value is on a straight line between the two

e.g.   values are 1,2,3,6,7    Percentile 0.4   is between 2 (25%) and 3 (50%)   value = 2 + (3-2) * (0.4 - 0.35) / (0.5 - 0.25) = 2.2

lands on a number return that

between 2 and 3 so 1 in the amount between, times that by the difference between the percentile required and the percentile of the lower one  and divide by the gap percentage

But can use a library?
                             */
                            double[] forPercentile; // switching to list as if we use Percentile Non Zero we don'#t know how long it will be
                            int count = 0;
                            if (valuesHook.calcValues != null) { // then override with calc values
                                forPercentile = new double[valuesHook.calcValues.size()];
                                for (double d : valuesHook.calcValues) {
                                    if (function == DataRegionHeading.FUNCTION.PERCENTILE || d != 0) {
                                        forPercentile[count] = d;
                                        count++;
                                    }
                                }
                            } else { // normal
                                forPercentile = new double[valuesHook.values.size()];
                                for (Value v : valuesHook.values) {
                                    double d = 0;
                                    try {
                                        d = Double.parseDouble(v.getText());
                                    } catch (NumberFormatException ignored) {
                                    }
                                    if (function == DataRegionHeading.FUNCTION.PERCENTILE || d != 0) {
                                        forPercentile[count] = d;
                                        count++;
                                    }
                                }
                            }
                            // whole array wasn't used. FOr all I know this might be fine but best to trim it to size
                            if (count != forPercentile.length){
                                double[] forPercentileAdjusted = new double[count];
                                System.arraycopy(forPercentile, 0, forPercentileAdjusted, 0, count);
                                forPercentile = forPercentileAdjusted;
                            }


                            Arrays.sort(forPercentile);
                            // just in case
                            if (forPercentile.length == 0){
                                doubleValue = 0;
                            } else {
                                // java doesn't like end case 0, deal with it here
                                if (functionDoubleParameter == 0) {
                                    doubleValue = forPercentile[0];
                                } else {
                                    Percentile p = new Percentile().withEstimationType(Percentile.EstimationType.R_7); // Excel type!
                                    p.setData(forPercentile);
                                    doubleValue = p.evaluate(functionDoubleParameter * 100); // I think this function expects out of 100. We'll see . . .
                                }
                            }
                        }
                        /* commenting for the mo
                        if (function == DataRegionHeading.FUNCTION.NPV) {
                            // looked at logic online and adapted to this - should work
                            double r1 = functionDoubleParameter + 1;
                            double trate = r1;
                            for (Value v : valuesHook.values) {
                                // currently not a number treated as 0, easy to make it skipped
                                double d = 0;
                                try {
                                    d = Double.parseDouble(v.getText());
                                } catch (NumberFormatException ignored) {
                                }
                                doubleValue += d / trate;
                                trate *= r1;
                            }
                        }*/
                        if (function == DataRegionHeading.FUNCTION.STDEVA) {
                            double[] forSD;
                            if (valuesHook.calcValues != null) { // then override with calc values
                                forSD = new double[valuesHook.calcValues.size()];
                                int count = 0;
                                for (double d : valuesHook.calcValues) {
                                    System.out.println("Standard deviation calculated value : " + d);
                                    forSD[count] = d;
                                    count++;
                                }
                            } else { // normal
                                forSD = new double[valuesHook.values.size()];
                                int count = 0;
                                for (Value v : valuesHook.values) {
                                    double d = 0;
                                    try {
                                        d = Double.parseDouble(v.getText());
                                    } catch (NumberFormatException ignored) {
                                    }
                                    forSD[count] = d;
                                    count++;
                                }
                            }
                            StandardDeviation sd = new StandardDeviation();
                            doubleValue = sd.evaluate(forSD);
                        }
                        //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                        if (valuesHook.values.size() == 1 && !locked.isTrue) {
                            Value value = valuesHook.values.get(0);
                            selected = valueToTestFor == value; // I think this is the right logic - is the value the one drilled down from?
                            stringValue = value.getText();
                            if (stringValue.contains("\n")) {
                                stringValue = stringValue.replaceAll("\n", "<br/>");//this is unsatisfactory, but a quick fix.
                            }
                            // was isnumber test here to add a double to the
                        } else if (valuesHook.values.size() > 0) {
                            stringValue = doubleValue + "";
                        }
                    } else {
                        stringValue = "";
                        doubleValue = 0;
                    }
                    listOfValuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(valuesHook.values);// we need this for locking among other things
                } else {  // attributes in the cells - currently no debug on this, it should be fairly obvious
                    List<Name> names = new ArrayList<>();
                    List<String> attributes = new ArrayList<>();
                    for (DataRegionHeading heading : headingsForThisCell) {
                        if (heading.getName() != null) {
                            names.add(heading.getName());
                        }
                        if (heading.getAttribute() != null) {
                            attributes.add(heading.getAttribute());
                        }
                    }
                    listOfValuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(names, attributes);
                    String attributeResult = findValueForHeadings(rowAndColumnHeadingsForThisCell, locked);
                    if (locked.isTrue) { // check there' wasn't unlocked, this overrides the rule in findValueForHeadings
                        for (DataRegionHeading lockCheck : headingsForThisCell) {
                            if (lockCheck.getSuffix() == DataRegionHeading.SUFFIX.UNLOCKED) {
                                locked.isTrue = false;
                                break;
                            }
                        }
                    }
                    try {
                        doubleValue = Double.parseDouble(attributeResult);
                    } catch (Exception e) {
                        //ignore
                    }
                    // ZK would want this typed as in number or string? Maybe just sort out later?
                    if (attributeResult != null) {
                        attributeResult = attributeResult.replace("\n", "<br/>");//unsatisfactory....
                        stringValue = attributeResult;
                    } else {
                        stringValue = "";
                    }
                }
            }
        }
                /* something to note : in the old model there was a map of headings used for each cell. I could add headingsForThisCell to the cell which would be a unique set for each cell
                 but instead I'll just add the headings and row and context, I think it would be less memory. 3 object references vs a set*/
        return new AzquoCell(locked.isTrue, listOfValuesOrNamesAndAttributeName, rowHeadings, columnHeadings, contextHeadings, rowNo, colNo, stringValue, doubleValue, false, selected);
    }

    // Simple attribute summing (assuming attributes are numeric), doesn't use set intersection or name children or anything like that
    // For the moment on the initial version don't use set intersection, just look at the headings as handed to the function - will it ever need to do this?

    private static String findValueForHeadings(final List<DataRegionHeading> headings, final MutableBoolean locked) throws Exception {
        List<Name> names = DataRegionHeadingService.namesFromDataRegionHeadings(headings);
        if (names.size() != 1) {
            locked.isTrue = true;
        }
        Collection<Name> attributeSet = null;
        for (DataRegionHeading heading : headings) {
            if (heading.getAttributeSet() != null) {
                attributeSet = heading.getAttributeSet();
                break;
            }
        }
        Set<String> attributes = DataRegionHeadingService.attributesFromDataRegionHeadings(headings);
        String stringResult = null;
        double numericResult = 0;
        int count = 0;
        String attValue = null;
        //code for multiple attributes by EFC  - WFC doesn't understand it!
        for (Name n : names) { // go through each name
            for (String attribute : attributes) {
                if (attributeSet != null) {
                    for (Name possibleParent : attributeSet) {
                        if (possibleParent.getChildren().contains(n)) {
                            attValue = possibleParent.getDefaultDisplayName();
                            break;
                        }
                    }
                }
                if (attValue == null) {
                    attValue = n.getAttribute(attribute.replace("`", "").toUpperCase());
                }
                if (attValue != null) {
                    count++;
                    if (!locked.isTrue && n.getAttribute(attribute, false, new HashSet<>()) == null) { // the attribute is not against the name itself (it's from the parent or structure)
                        locked.isTrue = true;
                    }
                    // basic sum across the names/attributes (not going into children)
                    // comma separated for non numeric. If mixed it will default to the string
                    try {
                        numericResult += Double.parseDouble(attValue);
                    } catch (Exception e) {
                        if (stringResult == null) {
                            stringResult = attValue;
                        } else {
                            stringResult += (", " + attValue);
                        }
                    }
                }
            }
        }
        if (count <= 1) return attValue;//don't allow long numbers to be converted to standard form.
        return (stringResult != null) ? stringResult : numericResult + "";
    }

    /*
    Used for PATHCOUNT
    only called by itself and path intersection, we want to know the number of intersecting names between containsSet
    and all its children and selectionSet, recursive to deal with containsSet down to the bottom. alreadyTested is
    simply to stop checking names that have already been checked. Note already tested doesn't mean intersections are unique,
    the number on intersections is on alreadyTested's children, it's when there is an intersection there that a name is considered tested.

    Worth pointing out that findOverlap which was used on total name count is a total of unique names and hence can use a contains on findAllChildren which returns a set
     whereas this, on the other hand, requires the non unique intersection, a single name could contribute to the count more than once by being in more than one set.

    Note : if this is really hammered there would be a case for moving it inside name to directly access the array and hence avoid Iterator instantiation.

       */

    private static int totalSetIntersectionCount(Name containsSet, Set<Name> selectionSet, Set<Name> alreadyTested, int track) {
//            System.out.println("totalSetIntersectionCount track " + track + " contains set : " + containsSet.getDefaultDisplayName() + ", children size : " + containsSet.getChildren().size());
        track++;
        if (alreadyTested.contains(containsSet)) return 0;
        alreadyTested.add(containsSet);
        int count = 0;
        for (Name child : containsSet.getChildren()) {
            if (selectionSet.contains(child)) {
                count++;
            }
        }
        if (count > 0) {
            return count;
        } else {
            for (Name child : containsSet.getChildren()) {
                if (child.hasChildren()) {
                    count += totalSetIntersectionCount(child, selectionSet, alreadyTested, track);
                }
            }
        }
        return count;
    }

    // todo : put the size check of each set and hence which way we run through the loop in here, should improve performance if required
    // for valueparentcount
    private static int findOverlap(Collection<Name> set1, Collection<Name> set2) {
        int count = 0;
        for (Name name : set1) {
            if (set2.contains(name)) count++;
        }
        return count;
    }

    static boolean isDot(DataRegionHeading dataRegionHeading) {
        return dataRegionHeading != null && dataRegionHeading.getAttribute() != null && dataRegionHeading.getAttribute().equals(".");
    }
}