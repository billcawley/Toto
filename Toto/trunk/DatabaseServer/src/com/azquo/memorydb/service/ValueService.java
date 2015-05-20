package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
import com.azquo.spreadsheet.*;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 16:49
 * Workhorse hammering away at the memory DB.
 */
public final class ValueService {

    private static final Logger logger = Logger.getLogger(ValueService.class);

    @Autowired
    private NameService nameService;

    @Autowired
    private DSSpreadsheetService dsSpreadsheetService;

    private StringUtils stringUtils = new StringUtils();

    // set the names in delete info and unlink - best I can come up with at the moment
    // unlike Name I don't think we're actually going to delete it - though whether the current name behavior is correct is another thing
    // todo : make delete mean delete, rollback without names rollback is pointless
    public void deleteValue(final Value value) throws Exception {
        String names = "";
        for (Name n : value.getNames()) {
            names += ", `" + n.getDefaultDisplayName() + "`";
        }
        if (names.length() > 0) {
            names = names.substring(2);
        }
        value.setDeletedInfoWillBePersisted(names);
        value.setNamesWillBePersisted(new HashSet<Name>());
    }

    // one line function, much point??

    public Value createValue(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Provenance provenance, final String text) throws Exception {
        return new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), provenance, text, null);
    }

    Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new HashMap<AzquoMemoryDBConnection, Map<String, Long>>();

    private void addToTimesForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection, String trackName, long toAdd) {
        long current = 0;
        if (timeTrack.get(azquoMemoryDBConnection) != null) {
            if (timeTrack.get(azquoMemoryDBConnection).get(trackName) != null) {
                current = timeTrack.get(azquoMemoryDBConnection).get(trackName);
            }
        } else {
            timeTrack.put(azquoMemoryDBConnection, new HashMap<String, Long>());
        }
        timeTrack.get(azquoMemoryDBConnection).put(trackName, current + toAdd);
    }

    public Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        return timeTrack.get(azquoMemoryDBConnection);
    }


    // this is passed a string for the value, not sure if that is the best practice, need to think on it.

    public String storeValueWithProvenanceAndNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String valueString, final Set<Name> names) throws Exception {
        //long marker = System.currentTimeMillis();
        String toReturn = "";

        final Set<Name> validNames = new HashSet<Name>();

        //removed valid name set check - caveat saver! WFC.
        validNames.addAll(names);

        final List<Value> existingValues = findForNames(names);
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames2", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        boolean alreadyInDatabase = false;
        for (Value existingValue : existingValues) { // really should only be one
            if (existingValue.getProvenance().equals(azquoMemoryDBConnection.getProvenance()) && existingValue.getProvenance().getMethod().equals("import")) {
                //new behaviour - add values from same dataimport.
                try {
                    Double existingDouble = Double.parseDouble(existingValue.getText());
                    Double newValue = Double.parseDouble(valueString);
                    valueString = (existingDouble + newValue) + "";
                    existingValue.setText(valueString);
                } catch (Exception e) {
                    // use the latest value
                }
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames2", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();

            if (dsSpreadsheetService.compareStringValues(existingValue.getText(), valueString)) {
                toReturn += "  that value already exists, skipping";
                alreadyInDatabase = true;
            } else {
                deleteValue(existingValue);
                // provenance table : person, time, method, name
                toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames3", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
        }
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames4", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        if (!alreadyInDatabase) {
            // create
            Value value = createValue(azquoMemoryDBConnection, azquoMemoryDBConnection.getProvenance(), valueString);
            toReturn += "  stored";
            // and link to names
            value.setNamesWillBePersisted(validNames);
        }
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames5", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        return toReturn;
    }


    public boolean overWriteExistingValue(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Value existingValue, final String newValueString) throws Exception {
        Value newValue = new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), newValueString, null);
        newValue.setNamesWillBePersisted(existingValue.getNames());
        deleteValue(existingValue);
        return true;
    }

    // doesn't work down the name children

    public List<Value> findForNames(final Set<Name> names) {
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        //long track = System.nanoTime();
        final List<Value> values = new ArrayList<Value>();
        // first get the shortest value list
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names) {
            if (smallestNameSetSize == -1 || name.getValues().size() < smallestNameSetSize) {
                smallestNameSetSize = name.getValues().size();
                smallestName = name;
            }
        }

        //System.out.println("track a   : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();
        // changing to sets for speed (hopefully!)
        //int count = 0;

        assert smallestName != null; // make intellij happy :P
        for (Value value : smallestName.getValues()) {
            boolean theValueIsOk = true;
            for (Name name : names) {
                if (!name.equals(smallestName)) { // ignore the one we started with
                    if (!value.getNames().contains(name)) {
//                        count++;
                        theValueIsOk = false;
                        break; // important, stop checking that that value contains he names we're interested in as, we didn't find one no point checking for the rest
                    }
                }
            }
            if (theValueIsOk) { // it was in all the names :)
                values.add(value);
            }
        }

        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    // while the above is what would be used to check if data exists for a specific name combination (e.g. when inserting data) this will navigate down through the names
    // I'm going to try for similar logic but using the lists of children for each name rather than just the name if that makes sense
    // I wonder if it should be a list or set returned?

    // this is slow relatively speaking


    long part1NanoCallTime1 = 0;
    long part2NanoCallTime1 = 0;
    long part3NanoCallTime1 = 0;
    int numberOfTimesCalled1 = 0;

    // setsize cache is a temporary cache of set sizes. Passed through as typically it would only be used for the scope of an operation
    // it is requires as we enumerate though the set a lot. A question : should a set record something like "numvaluesincludingchildren"
    // a possible TODO

    public List<Value> findForNamesIncludeChildren(final Set<Name> names, boolean payAttentionToAdditive, Map<Name, Integer> setSizeCache) {
        long start = System.nanoTime();

        final List<Value> values = new ArrayList<Value>();
        // first get the shortest value list taking into account children
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names) {
            Integer setSizeIncludingChildren = null;
            if (setSizeCache != null) {
                setSizeIncludingChildren = setSizeCache.get(name);
            }
            if (setSizeIncludingChildren == null) {
                setSizeIncludingChildren = name.getValues().size();
                for (Name child : name.findAllChildren(payAttentionToAdditive)) {
                    setSizeIncludingChildren += child.getValues().size();
                }
                if (setSizeCache != null) {
                    setSizeCache.put(name, setSizeIncludingChildren);
                }
            }

            if (smallestNameSetSize == -1 || setSizeIncludingChildren < smallestNameSetSize) {
                smallestNameSetSize = setSizeIncludingChildren;
                if (smallestNameSetSize == 0) {//no values
                    return values;
                }
                smallestName = name;
            }
        }

        part1NanoCallTime1 += (System.nanoTime() - start);
        long point = System.nanoTime();
        assert smallestName != null; // make intellij happy :P
        final List<Value> valueList = findValuesForNameIncludeAllChildren(smallestName, payAttentionToAdditive);
        part2NanoCallTime1 += (System.nanoTime() - point);
        point = System.nanoTime();
        for (Value value : valueList) {
            boolean theValueIsOk = true;
            for (Name name : names) {
                if (!name.equals(smallestName)) { // ignore the one we started with
                    if (!value.getNames().contains(name)) { // top name not in there check children also
                        Set<Name> copy = new HashSet<Name>(value.getNames());
                        copy.retainAll(name.findAllChildren(payAttentionToAdditive));
                        if (copy.size() == 0) {
                            //                        count++;
                            theValueIsOk = false;
                            break;
                        }
                    }
                }
            }
            if (theValueIsOk) { // it was in all the names :)
                values.add(value);
            }
        }
        part3NanoCallTime1 += (System.nanoTime() - point);
        numberOfTimesCalled1++;
        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    // for searches, the Names are a List of sets rather than a set, and the result need not be ordered
    public Set<Value> findForSearchNamesIncludeChildren(final List<Set<Name>> names, boolean payAttentionToAdditive) {
        long start = System.nanoTime();

        final Set<Value> values = new HashSet<Value>();
        // assume that the first set of names is the most restrictive
        Set<Name> smallestNames = names.get(0);
        part1NanoCallTime1 += (System.nanoTime() - start);
        long point = System.nanoTime();

        final Set<Value> valueSet = new HashSet<Value>();
        for (Name name : smallestNames) {
            valueSet.addAll(findValuesForNameIncludeAllChildren(name, payAttentionToAdditive));
        }
        // this seems a fairly crude implementation, list all values for the name sets then check that that values list is in all the name sets
        part2NanoCallTime1 += (System.nanoTime() - point);
        point = System.nanoTime();
        for (Value value : valueSet) {
            boolean theValueIsOk = true;
            for (Set<Name> nameSet : names) {
                if (!nameSet.equals(smallestNames)) { // ignore the one we started with
                    boolean foundInChildList = false;
                    for (Name valueNames : value.getNames()) {
                        if (nameService.inParentSet(valueNames, nameSet) != null) {
                            foundInChildList = true;
                            break;
                        }
                    }
                    if (!foundInChildList) {
                        theValueIsOk = false;
                        break;
                    }
                }
            }
            if (theValueIsOk) { // it was in all the names :)
                values.add(value);
            }
        }
        part3NanoCallTime1 += (System.nanoTime() - point);
        numberOfTimesCalled1++;
        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    public void printFindForNamesIncludeChildrenStats() {
        if (numberOfTimesCalled1 > 0) {
            logger.info("calls to  FindForNamesIncludeChildrenStats : " + numberOfTimesCalled1);
            logger.info("part 1 average nano : " + (part1NanoCallTime1 / numberOfTimesCalled1));
            logger.info("part 2 average nano : " + (part2NanoCallTime1 / numberOfTimesCalled1));
            logger.info("part 3 average nano : " + (part3NanoCallTime1 / numberOfTimesCalled1));
        }
    }

    long totalNanoCallTime = 0;
    long part1NanoCallTime = 0;
    long part2NanoCallTime = 0;
    int numberOfTimesCalled = 0;

    // the function that populates each cell.
    public double findValueForNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Set<Name> names, final MutableBoolean locked, final boolean payAttentionToAdditive, List<Value> valuesFound, Map<Name, Integer> totalSetSize, List<String> attributeNames) throws Exception {
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<Name>();
        String calcString = null;
        List<Name> formulaNames = new ArrayList<Name>();
        boolean hasCalc = false;
        // add all names to calcnames except the the one with CALCULATION
        // and here's a thing : if more than one name has CALCULATION then only the first will be used
        // todo : I think this logic could be cleaned up a little - the way hascalc is set seems wrong but initial attempts to simplify were also wrong
        for (Name name : names) {
            if (!hasCalc) {// then try and find one
                String calc = name.getAttribute(Name.CALCULATION, false, new HashSet<Name>());
                if (calc != null) {
                    // then get the result of it, this used to be stored in RPCALC
                    // it does extra things we won't use but the simple parser before SYA should be fine here
                    List<String> formulaStrings = new ArrayList<String>();
                    List<String> nameStrings = new ArrayList<String>();
                    List<String> attributeStrings = new ArrayList<String>();

                    calc = stringUtils.parseStatement(calc, nameStrings, formulaStrings, attributeStrings);
                    formulaNames = nameService.getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
                    calc = stringUtils.shuntingYardAlgorithm(calc);
                    //todo : make sure name lookups below use the new style of marker
                    if (!calc.startsWith("error")) { // there should be a better way to deal with errors
                        calcString = calc;
                        hasCalc = true;
                    }
                }
                if (calcString != null) {
                    hasCalc = true;
                } else {
                    calcnames.add(name);
                }
            } else {
                calcnames.add(name);
            }
        }

        // no reverse polish converted formula, just sum
        if (!hasCalc) {
            for (Name oneName : names) { // inexpensive check based on peers which we don't use that much now
                if (oneName.getPeers().size() == 0 && oneName.getChildren().size() > 0) {
                    locked.isTrue = true;
                    break;
                }
            }
            return findSumForNamesIncludeChildren(names, payAttentionToAdditive, valuesFound, totalSetSize);
        } else {
            // this is where the work done by the shunting yard algorithm is used
            // ok I think I know why an array was used, to easily reference the entry before
            double[] values = new double[20];//should be enough!!
            int valNo = 0;
            StringTokenizer st = new StringTokenizer(calcString, " ");
            while (st.hasMoreTokens()) {
                String term = st.nextToken();
                if (term.length() == 1) { // operation
                    valNo--;
                    char charTerm = term.charAt(0);
                    if (charTerm == '+') {
                        values[valNo - 1] += values[valNo];
                    } else if (charTerm == '-') {
                        values[valNo - 1] -= values[valNo];
                    } else if (charTerm == '*') {
                        values[valNo - 1] *= values[valNo];
                    } else if (values[valNo] == 0) {
                        values[valNo - 1] = 0;
                    } else {
                        values[valNo - 1] /= values[valNo];
                    }
                } else { // a value, not in the Azquo sense, a number or reference to a name
                    if (NumberUtils.isNumber(term)) {
                        values[valNo++] = Double.parseDouble(term);
                    } else {
                        // we assume it's a name id starting with NAMEMARKER
                        //int id = Integer.parseInt(term.substring(1));
                        // so get the name and add it to the other names
                        Name name = nameService.getNameFromListAndMarker(term, formulaNames);
                        Set<Name> seekSet = new HashSet<Name>(calcnames);
                        if (name.getPeers().size() == 0 || name.getPeers().size() == calcnames.size()) {
                            seekSet.add(name);
                        } else {
                            seekSet = nameService.trimNames(name, seekSet);
                            seekSet.add(name);
                        }
                        // and put the result in
                        //note - recursion in case of more than one formula, but the order of the formulae is undefined if the formulae are in different peer groups
                        values[valNo++] = findValueForNames(azquoMemoryDBConnection, seekSet, locked, payAttentionToAdditive, valuesFound, totalSetSize, attributeNames);
                    }
                }
            }
            locked.isTrue = true;
            return values[0];
        }
    }

    // Added by Edd, like above but uses an attribute (attributes?) and doens't care about calc for the moment, hence should be much more simple
    // For the moment on the initial version don't use set intersection, just look at the headings as handed to the function

    public String findValueForHeadings(final Set<DataRegionHeading> headings, final MutableBoolean locked, List<Name> namesForMap, List<String> attributesForMap) throws Exception {
        Set<Name> names = dsSpreadsheetService.namesFromDataRegionHeadings(headings);
        if (names.size() != 1) {
            locked.isTrue = true;
        }
        Set<String> attributes = dsSpreadsheetService.attributesFromDataRegionHeadings(headings);
        String stringResult = null;
        double numericResult = 0;
        // was on set intersection . . .
        int count = 0;
        String attValue = null;
        for (Name n : names) {
            for (String attribute : attributes) {
                attValue = n.getAttribute(attribute.replace("`", ""));
                if (attValue != null) {
                    count++;
                    if (!locked.isTrue && n.getAttribute(attribute, false, new HashSet<Name>()) == null) { // tha attribute is not against the name itself (it's form the parent or structure)
                        locked.isTrue = true;
                    } else {
                        // this feels a little hacky but I need to record this for saving purposes later
                        // that is to say this is note required directly for the return value here
                        if (!namesForMap.contains(n)) {
                            namesForMap.add(n);
                        }
                        if (!attributesForMap.contains(attribute)) {
                            attributesForMap.add(attribute);
                        }
                    }
                    // basic sum across the names/attributes (not going into children)
                    // comma separated for non numeric. If mixed it will default to the string
                    if (NumberUtils.isNumber(attValue)) {
                        numericResult += Double.parseDouble(attValue);
                    } else {
                        if (stringResult == null) {
                            stringResult = attValue;
                        } else {
                            stringResult += (", " + attValue);
                        }
                    }
                }
            }
        }
        if (count == 1) return attValue;//don't allow long numbers to be converted to standard form.
        return (stringResult != null) ? stringResult : numericResult + "";
    }

    // on a standard non-calc cell this will give the result

    public double findSumForNamesIncludeChildren(final Set<Name> names, final boolean payAttentionToAdditive, List<Value> valuesFound, Map<Name, Integer> totalSetSize) {
        //System.out.println("findSumForNamesIncludeChildren");
        long start = System.nanoTime();

        List<Value> values = findForNamesIncludeChildren(names, payAttentionToAdditive, totalSetSize);
        part1NanoCallTime += (System.nanoTime() - start);
        long point = System.nanoTime();
        double sumValue = 0;
        for (Value value : values) {
            if (value.getText() != null && value.getText().length() > 0) {
                try {
                    sumValue += Double.parseDouble(value.getText());
                } catch (Exception ignored) {
                }
            }
        }
        if (valuesFound != null) {
            valuesFound.addAll(values);
        }

        part2NanoCallTime += (System.nanoTime() - point);
        totalNanoCallTime += (System.nanoTime() - start);
        numberOfTimesCalled++;
        return sumValue;
    }

    public void printSumStats() {
        if (numberOfTimesCalled > 0) {
            logger.info("calls to  findSumForNamesIncludeChildren : " + numberOfTimesCalled);
            logger.info("part 1 average nano : " + (part1NanoCallTime / numberOfTimesCalled));
            logger.info("part 2 average nano : " + (part2NanoCallTime / numberOfTimesCalled));
            logger.info("total average nano : " + (totalNanoCallTime / numberOfTimesCalled));
        }
    }

    public List<Value> findValuesForNameIncludeAllChildren(final Name name, boolean payAttentionToAdditive) {
        List<Value> toReturn = new ArrayList<Value>();
        toReturn.addAll(name.getValues());
        for (Name child : name.findAllChildren(payAttentionToAdditive)) {
            toReturn.addAll(child.getValues());
        }
        return toReturn;
    }

    // edd not completely clear about these three functions, todo

    private Name sumName(Name name, List<Set<Name>> searchNames) {
        for (Set<Name> searchName : searchNames) {
            Name maybeParent = nameService.inParentSet(name, searchName);
            if (maybeParent != null) {
                return maybeParent;
            }
        }
        return name;
    }

    public Map<Set<Name>, Set<Value>> getSearchValues(final List<Set<Name>> searchNames) throws Exception {
        if (searchNames == null) return null;
        Set<Value> values = findForSearchNamesIncludeChildren(searchNames, false);
        //The names on the values have been moved 'up' the tree to the name that was searched
        // e.g. if the search was 'England' and the name was 'London' then 'London' has been replaced with 'England'
        // so there may be duplicates in an unordered search - hence the consolidation below.
        final Map<Set<Name>, Set<Value>> showValues = new HashMap<Set<Name>, Set<Value>>();
        for (Value value : values) {
            Set<Name> sumNames = new HashSet<Name>();
            for (Name name : value.getNames()) {
                sumNames.add(sumName(name, searchNames));
            }
            Set<Value> alreadyThere = showValues.get(sumNames);
            if (alreadyThere != null) {
                alreadyThere.add(value);
            } else {
                Set<Value> newValues = new HashSet<Value>();
                newValues.add(value);
                showValues.put(sumNames, newValues);
            }
        }
        return showValues;
    }

    public String addValues(Set<Value> values) {
        String stringVal = null;
        Double doubleVal = 0.0;
        boolean percentage = false;
        for (Value value : values) {
            String thisVal = value.getText();
            Double thisNum = 0.0;
            if (NumberUtils.isNumber(thisVal)) {
                thisNum = Double.parseDouble(thisVal);
            } else {
                if (thisVal.endsWith("%") && NumberUtils.isNumber(thisVal.substring(0, thisVal.length() - 1))) {
                    thisNum = Double.parseDouble(thisVal.substring(0, thisVal.length() - 1)) / 100;
                    percentage = true;
                }
            }
            doubleVal += thisNum;
            if (stringVal == null) {
                stringVal = thisVal;
            }
        }
        if (doubleVal != 0.0) {
            if (percentage) doubleVal *= 100;
            stringVal = doubleVal + "";
            if (stringVal.endsWith(".0")) {
                stringVal = stringVal.substring(0, stringVal.length() - 2);
            }
            if (percentage) stringVal += "%";
        }
        return stringVal;
    }

    // find the most used name by a set of values, used by printBatch to derive headings

    private Name getMostUsedName(Set<Value> values, Name topParent) {
        Map<Name, Integer> nameCount = new HashMap<Name, Integer>();
        for (Value value : values) {
            for (Name name : value.getNames()) {
                if (topParent == null || name.findATopParent() == topParent) {
                    Integer origCount = nameCount.get(name);
                    if (origCount == null) {
                        nameCount.put(name, 1);
                    } else {
                        nameCount.put(name, origCount + 1);
                    }
                }
            }
        }
        if (nameCount.size() == 0) {
            return getMostUsedName(values, null);
        }
        int maxCount = 0;
        Name maxName = null;
        for (Name name : nameCount.keySet()) {
            int count = nameCount.get(name);
            if (count > maxCount) {
                maxCount = count;
                maxName = name;
            }
        }
        return maxName;
    }


    public void sortValues(List<Value> values) {
        Collections.sort(values, new Comparator<Value>() {
            public int compare(Value o1, Value o2) {
                return (o1.getProvenance().getTimeStamp())
                        .compareTo(o2.getProvenance().getTimeStamp());
            }
        });
    }

    // pring a bunch of values in json. It seems to find the name which represents the most values and displays
    // them under them then the name that best represents the rest etc etc until all values have been displayed

    private StringBuffer printBatch(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values) {
        StringBuffer sb = new StringBuffer();
        //int debugCount = 0;
        boolean headingNeeded = false;
        boolean firstName = true;
        for (Value value : values) {
            if (value.getNames().size() > 1) {
                headingNeeded = true;
                break;
            }
            String nameFound = null;
            for (Name name : value.getNames()) {
                nameFound = name.getDefaultDisplayName();
            }
            if (firstName) {
                firstName = false;
            } else {
                sb.append(",");
            }
            sb.append("{");
            //debugCount = value.getNames().size();
            sb.append(jsonValue("value", value.getText(), false));
            sb.append(jsonValue("name", nameFound, true));
            sb.append("}");

        }

        if (headingNeeded) {
            boolean firstHeading = true;
            Name topParent = null;
            while (values.size() > 0) {
                Name heading = getMostUsedName(values, topParent);
                topParent = heading.findATopParent();
                Set<Value> extract = new HashSet<Value>();
                Set<Value> slimExtract = new HashSet<Value>();
                for (Value value : values) {
                    if (value.getNames().contains(heading)) {
                        extract.add(value);
                        //creating a new 'value' with one less name for recursion
                        Value slimValue = null;
                        try {
                            slimValue = new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), null, value.getText(), null);
                        } catch (Exception e) {
                            //no reason for exceptions, so ignore.
                        }
                        Set<Name> slimNames = new HashSet<Name>();
                        for (Name name : value.getNames()) {
                            slimNames.add(name);
                        }
                        slimNames.remove(heading);
                        slimValue.setNames(slimNames);
                        slimExtract.add(slimValue);
                        //debugCount = slimValue.getNames().size();
                    }


                }
                values.removeAll(extract);
                if (firstHeading) {
                    firstHeading = false;
                } else {
                    sb.append(",");
                }
                sb.append("{");
                sb.append(jsonValue("heading", heading.getDefaultDisplayName(), false));
                sb.append(",\"items\":[" + printBatch(azquoMemoryDBConnection, slimExtract).toString() + "]");
                sb.append("}");
            }
        }
        return sb;

    }

    // again, should we be using jackson??

    private String jsonValue(String val1, String val2, boolean comma) {
        String result = "\"" + val1 + "\":\"" + val2.replace("\"", "\\\"") + "\"";
        if (!comma) {
            return result;

        }
        return "," + result;
    }

    public StringBuffer printExtract(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values, Provenance p) {
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        StringBuffer sb = new StringBuffer();
        sb.append(jsonValue("heading", "<b>" + df.format(p.getTimeStamp()) + "</b> by <b>" + p.getUser() + "</b><br/>Method:" + p.getMethod() + " " + p.getName(), false));
        sb.append(",\"items\":[");
        sb.append(printBatch(azquoMemoryDBConnection, values));
        sb.append("]");
        return sb;
    }
}