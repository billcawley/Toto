package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
import com.azquo.spreadsheet.*;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 16:49
 * Workhorse hammering away at the memory DB, things like findValueForNames are crucial.
 */
public final class ValueService {

    private static final String OPS = "+-*/";

    private static final Logger logger = Logger.getLogger(ValueService.class);

    private static AtomicInteger nameCompareCount = new AtomicInteger(0);

    private static Value createValue(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Provenance provenance, final String text) throws Exception {
        nameCompareCount.incrementAndGet();
        return new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), provenance, text);
    }

/*    private static Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new ConcurrentHashMap<>();

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

    public static Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        return timeTrack.get(azquoMemoryDBConnection);
    }*/

    // this is passed a string for the value, not sure if that is the best practice, need to think on it.

    private static AtomicInteger storeValueWithProvenanceAndNamesCount = new AtomicInteger(0);

    public static String storeValueWithProvenanceAndNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String valueString, final Set<Name> names) throws Exception {
        storeValueWithProvenanceAndNamesCount.incrementAndGet();
        // ok there's an issue of numbers with "," in them, in that case I should remove on the way in
        if (valueString.contains(",")) {
            String replaced = valueString.replace(",", "");
            if (NumberUtils.isNumber(replaced)) { // think that's fine
                // so without "," it IS a valid number, take commas out of valueString
                valueString = replaced;
            }
        }

        //long marker = System.currentTimeMillis();
        String toReturn = "";
        final List<Value> existingValues = findForNames(names);
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames2", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        boolean alreadyInDatabase = false;
        // there's new logic to add values to existing but this only applies to the same provenance and when importing (so same file)
        for (Value existingValue : existingValues) { // really should only be one
            if (existingValue.getProvenance().equals(azquoMemoryDBConnection.getProvenance()) && existingValue.getProvenance().getMethod().equals("imported")) {
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
            // won't be true for the same file as it will be caught above but from different files it could well happen
            if (DSSpreadsheetService.compareStringValues(existingValue.getText(), valueString)) {
                toReturn += "  that value already exists, skipping";
                alreadyInDatabase = true;
            } else {
                existingValue.delete();
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
            value.setNamesWillBePersisted(names);
        }
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames5", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        return toReturn;
    }

    // called when altering values in a spreadsheet
    private static AtomicInteger overWriteExistingValueCount = new AtomicInteger(0);

    public static boolean overWriteExistingValue(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Value existingValue, String newValueString) throws Exception {
        overWriteExistingValueCount.incrementAndGet();
        if (newValueString.contains(",")) {
            if (newValueString.contains(",")) {
                String replaced = newValueString.replace(",", "");
                if (NumberUtils.isNumber(replaced)) { // think that's fine
                    // so without "," it IS a valid number, take commas out of valueString
                    newValueString = replaced;
                }
            }
        }
        if (newValueString.equals(existingValue.getText())) {
            return true;
        }
        Value newValue = new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), newValueString);
        newValue.setNamesWillBePersisted(new HashSet<>(existingValue.getNames())); // a bit crappy but I'm trying to make it a list internally but interfaced by sets
        existingValue.delete();
        return true;
    }

    // doesn't work down the name children

    private static AtomicInteger findForNamesCount = new AtomicInteger(0);

    public static List<Value> findForNames(final Set<Name> names) {
        findForNamesCount.incrementAndGet();
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        final List<Value> values = new ArrayList<>();
        // first get the shortest value list
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names) {
            if (smallestNameSetSize == -1 || name.getValues().size() < smallestNameSetSize) {
                smallestNameSetSize = name.getValues().size();
                smallestName = name;
            }
        }
        assert smallestName != null; // make intellij happy
        for (Value value : smallestName.getValues()) {
            boolean theValueIsOk = true;
            for (Name name : names) {
                if (!name.equals(smallestName)) { // ignore the one we started with
                    if (!value.getNames().contains(name)) {
                        theValueIsOk = false;
                        break; // important, stop checking that that value contains he names we're interested in as, we didn't find one no point checking for the rest
                    }
                }
            }
            if (theValueIsOk) { // it was in all the names
                values.add(value);
            }
        }
        return values;
    }

    /* While the above is what would be used to check if data exists for a specific name combination (e.g. when inserting data) this will navigate down through the names
    this has been a major bottleneck, caching values against names helped a lot, also using similar logic to retainall but creating a new list seemed to double performance and with less garbage I hope!
    not to mention a list is better for iterator I think and .contains is NOT used in the one place this is called. Changing the return type to list.
    As mentioned in comments in the function a lot of effort went in to speeding up this function and reducing garbage, be very careful before changing it.
    */

    private static AtomicLong part1NanoCallTime1 = new AtomicLong(0);
    private static AtomicLong part2NanoCallTime1 = new AtomicLong(0);
    private static AtomicLong part3NanoCallTime1 = new AtomicLong(0);
    private static AtomicInteger numberOfTimesCalled1 = new AtomicInteger(0);

    // what if we could cache combos? E.g. a 5 comes in and we save

    private static AtomicInteger findForNamesIncludeChildrenCount = new AtomicInteger(0);

    private static List<Value> findForNamesIncludeChildren(final List<Name> names, Map<List<Name>, Set<Value>> nameComboValueCache) {

/*        StringBuilder log = new StringBuilder();
        for (Name name : names) {
            log.append("|");
            log.append(name.getDefaultDisplayName());
        }
        System.out.println(log.toString());*/

        findForNamesIncludeChildrenCount.incrementAndGet();
        long point = System.nanoTime();
        // ok here we're going to try and get clever by caching certain combinations of names
        final Set<Value> smallestValuesSet;
        Set[] setsToCheck;
        List<Value> toReturn = new ArrayList<>();// since the source is a set it will already be deduped - can use ArrayList to return
        if (nameComboValueCache != null && names.size() > 3) {
//            List<Name> allButTwo = names.subList(0, names.size() - 2);
            // testing is showing all but one being the fastest - this might make the
            List<Name> allButOne = names.subList(0, names.size() - 1);
            // note it may not actually be the smallest, we hope it is!
            // the collection wrap may cost, guess we'll see hjow it goes
            part1NanoCallTime1.addAndGet(System.nanoTime() - point);
//            smallestValuesSet = nameComboValueCache.computeIfAbsent(allButTwo, computed -> HashObjSets.newImmutableSet(findForNamesIncludeChildren(allButTwo, payAttentionToAdditive, null)));
            smallestValuesSet = nameComboValueCache.computeIfAbsent(allButOne, computed -> HashObjSets.newImmutableSet(findForNamesIncludeChildren(allButOne, null)));
            point = System.nanoTime(); //reset after the cache hit as that will be measured in the recursive call
/*            setsToCheck = new Set[2];
            setsToCheck[0] = names.get(names.size() - 2).findValuesIncludingChildren(payAttentionToAdditive);
            setsToCheck[1] = names.get(names.size() - 1).findValuesIncludingChildren(payAttentionToAdditive);*/
            setsToCheck = new Set[1];
            setsToCheck[0] = names.get(names.size() - 1).findValuesIncludingChildren();
        } else {
            // first get the shortest value list taking into account children
            int smallestNameSetSize = -1;
            Name smallestName = null;
            for (Name name : names) {
                int setSizeIncludingChildren = name.findValuesIncludingChildren().size();
                if (smallestNameSetSize == -1 || setSizeIncludingChildren < smallestNameSetSize) {
                    smallestNameSetSize = setSizeIncludingChildren;
                    if (smallestNameSetSize == 0) {//no values
                        return new ArrayList<>();
                    }
                    smallestName = name;
                }
            }
            part1NanoCallTime1.addAndGet(System.nanoTime() - point);
            point = System.nanoTime();
            assert smallestName != null; // make intellij happy
            smallestValuesSet = smallestName.findValuesIncludingChildren();
            part2NanoCallTime1.addAndGet(System.nanoTime() - point);
            point = System.nanoTime();
            // ok from testing a new list using contains against values seems to be the thing, double the speed at least I think!
            setsToCheck = new Set[names.size() - 1]; // I don't want to be creating iterators when checking. Iterator * millions = garbage (in the Java sense!). No problems losing typing, I just need the contains.
            int arrayIndex = 0;
            for (Name name : names) {
                if (name != smallestName) { // a little cheaper than making a new name set and knocking this one off I think
                    setsToCheck[arrayIndex] = name.findValuesIncludingChildren();
                    arrayIndex++;
                }
            }
        }
        // The core things we want to know about values e.g. sum, max, min, could be done in here but currently the values list, via ValuesHook, is still accesed and used.
        // When we zap it there may be a case for having this funciton return a double according to whether min max sum etc is required. Performance is pretty good at the moment though.
        boolean add; // declare out here, save reinitialising each time
        int index; // ditto that, should help
        for (Value value : smallestValuesSet) { // because this could be a whacking great loop! I mean many millions.
            // stopping the iterator in here and moving the declarations out of here made a MASSIVE difference! Be careful doing anything inside this loop,
            add = true;
            for (index = 0; index < setsToCheck.length; index++) {
                if (setsToCheck[index] != null && !setsToCheck[index].contains(value)) {//setsToCheck may not have the same dimensionality if there are duplicate headings and 'smallestName' is the duplicate heading
                    add = false;
                    break;
                }
            }
            if (add) {
                //new rule added by WFC March 2015:  if one element found is identical to the search set, use that one only.
                /* edd commenting 12th April, this cannot be in here
                boolean identical = true;
                for (Name test:names){
                    if (!value.getNames().contains(test)){
                        identical = false;
                        break;
                    }
                }
                if (identical){
                    toReturn.clear();
                    toReturn.add(value);
                    break;
                }*/
                toReturn.add(value);
            }
        }
        part3NanoCallTime1.addAndGet(System.nanoTime() - point);
        numberOfTimesCalled1.incrementAndGet();
        return toReturn;
    }

    public static void printFindForNamesIncludeChildrenStats() {
        if (numberOfTimesCalled1.get() > 0) {
            logger.info("calls to  FindForNamesIncludeChildrenStats : " + numberOfTimesCalled1.get());
            logger.info("part 1 average nano : " + (part1NanoCallTime1.get() / numberOfTimesCalled1.get()));
            logger.info("part 2 average nano : " + (part2NanoCallTime1.get() / numberOfTimesCalled1.get()));
            logger.info("part 3 average nano : " + (part3NanoCallTime1.get() / numberOfTimesCalled1.get()));
        }
    }

    private static long totalNanoCallTime = 0;
    private static long part1NanoCallTime = 0;
    private static long part2NanoCallTime = 0;
    private static int numberOfTimesCalled = 0;

    public static double findValueForNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<Name> names, final MutableBoolean locked
            , DSSpreadsheetService.ValuesHook valuesHook, List<String> attributeNames, DataRegionHeading.FUNCTION function, Map<List<Name>, Set<Value>> nameComboValueCache) throws Exception {
        return findValueForNames(azquoMemoryDBConnection, names, locked, valuesHook, attributeNames, function, nameComboValueCache, null);
    }

    // the function that populates each cell.
    private static AtomicInteger findValueForNamesCount = new AtomicInteger(0);

    public static double findValueForNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<Name> names, final MutableBoolean locked
            , DSSpreadsheetService.ValuesHook valuesHook, List<String> attributeNames, DataRegionHeading.FUNCTION function, Map<List<Name>, Set<Value>> nameComboValueCache, StringBuilder debugInfo) throws Exception {
        findValueForNamesCount.incrementAndGet();
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<>();
        Collection<Name> appliesToNames = null; // used if you want formulae from calc to be resolved at a lower level and the result summed rather than sum each term in the calc formula
        List<Name> formulaNames = new ArrayList<>();
        String calcString = null; // whether this is null replaces hasCalc
        // add all names to calcnames except the the one with CALCULATION
        // and here's a thing : if more than one name has CALCULATION then only the first will be used
        boolean lowest = false; // special applies to calc for each permutation of the calc names then sum
        for (Name name : names) {
            if (calcString == null) {// then try and find one - can only happen once
                String calc = name.getAttribute(Name.CALCULATION, false, null); // using extra parameters to stop parent checking for this attribute
                if (calc != null) {
                    if (debugInfo != null) {
                        debugInfo.append("\nCalculation from " + name.getDefaultDisplayName() + "\n");
                        debugInfo.append("\t" + calc + "\n");
                    }
                    if (name.getAttribute(Name.APPLIESTO) != null) {
                        if (debugInfo != null) {
                            debugInfo.append("\tApplies to " + name.getAttribute(Name.APPLIESTO) + "\n");
                        }
                        // will be being looked up by default display name for the moment
                        if (name.getAttribute(Name.APPLIESTO).trim().equalsIgnoreCase("lowest")) {
                            lowest = true;
                        } else {
                            appliesToNames = NameService.parseQuery(azquoMemoryDBConnection, name.getAttribute(Name.APPLIESTO));
                        }
                    }
                    // then get the result of it, this used to be stored in RPCALC
                    // it does extra things we won't use but the simple parser before SYA should be fine here
                    List<String> formulaStrings = new ArrayList<>();
                    List<String> nameStrings = new ArrayList<>();
                    List<String> attributeStrings = new ArrayList<>();
                    calc = StringUtils.prepareStatement(calc, nameStrings, formulaStrings, attributeStrings);
                    formulaNames = NameService.getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
                    calc = StringUtils.shuntingYardAlgorithm(calc);
                    //todo : make sure name lookups below use the new style of marker
                    if (!calc.startsWith("error")) { // there should be a better way to deal with errors
                        calcString = calc;
                    }
                }
                if (calcString == null) { // couldn't find and resolve a calculation (the vast majority of the time due to there being no attribute)
                    calcnames.add(name);
                }
            } else { // already have a calculation
                calcnames.add(name);
            }
        }
        // lowest then there's no point running outer loops etc. Note we assume this is a calc!
        if (lowest) {
            // prepare lists to permute - I can't just use the findAllChildrenSets as they're not level lowest
            List<List<Name>> toPermute = new ArrayList<>();
            for (Name calcName : calcnames) {
                Set<Name> permutationDimension = new HashSet<>(); // could move to koloboke? I need to use sets here to stop duplicates
                if (!calcName.hasChildren()) {
                    permutationDimension.add(calcName);
                } else {
                    for (Name name : calcName.findAllChildren()) {
                        if (!name.hasChildren()) {
                            permutationDimension.add(name);
                        }
                    }
                }
                toPermute.add(new ArrayList<>(permutationDimension)); // just wrap back to a lists,not that efficient but can use the existing permute function
            }
            // now I think I can just use an existing function!
            final List<List<Name>> permutationOfLists = DSSpreadsheetService.get2DPermutationOfLists(toPermute);
            double toReturn = 0;
            for (List<Name> lowLevelCalcNames : permutationOfLists) { // it's lowLevelCalcNames that we were after
                toReturn += resolveCalc(azquoMemoryDBConnection, calcString, formulaNames, lowLevelCalcNames, locked, valuesHook, attributeNames, function, nameComboValueCache, debugInfo);
            }
            return toReturn;
        } else {
            // ok we've populated calc names is applicable, now check if calc names interacts with applies to (applies to is looking for an intersection with calc names e,g, from the row or context NOT the names in the formulae)
            // this was just for the applied to names but to reduce code duplication we'll get the first calc name and jam it in here to make a single loop
            List<Name> outerLoopNames = new ArrayList<>();
            if (appliesToNames != null) { // then try and find the name
                Iterator<Name> calcNamesIterator = calcnames.iterator(); // for the remove function otherwise a loop might throw a wobbbler (exception)
                while (calcNamesIterator.hasNext()) { // go through the names and find the first that crosses over with the appliesTo set
                    Name calcName = calcNamesIterator.next();
                    for (Name appliesToName : appliesToNames) {
                        if (calcName.findAllChildren().contains(appliesToName)) { // we have a hit (one of the applies to list is found in the children of the formula names), add to the list that will replace this calc name
                            outerLoopNames.add(appliesToName);
                        }
                    }
                    if (!outerLoopNames.isEmpty()) {// we have a restricted list for this calc name, remove that calc name and stop looking
                        calcNamesIterator.remove();
                        break;
                    }
                }
            }
            // no reverse polish converted formula, just sum
            if (calcString == null) {
                // I'll not debug in here for the moment. We should know names and function by now
                return resolveValuesForNamesIncludeChildren(names, valuesHook, function, locked, nameComboValueCache);
            } else {
                if (outerLoopNames.isEmpty()) { // will be most of the time, put the first in the outer loop
                    outerLoopNames.add(calcnames.remove(0));// as mentioned above in the case of normal use take the first and move it to the outside loop. Yes it will just be added straight back on but otherwise we have code duplication below in an else or a function with many parameters passed
                }
                double toReturn = 0;
                for (Name appliesToName : outerLoopNames) { // in normal use just a single
                    calcnames.add(appliesToName);
                    toReturn += resolveCalc(azquoMemoryDBConnection, calcString, formulaNames, calcnames, locked, valuesHook, attributeNames, function, nameComboValueCache, debugInfo);
                    calcnames.remove(appliesToName);
                }
                locked.isTrue = true;
                return toReturn;
            }
        }
    }

    // factored off to implement "lowest" calculation criteria (resolve for each permutation and sum) without duplicated code
    // fair few params, wonder if there's a way to avoid that?
    private static double resolveCalc(AzquoMemoryDBConnection azquoMemoryDBConnection, String calcString, List<Name> formulaNames, List<Name> calcnames,
                                      MutableBoolean locked, DSSpreadsheetService.ValuesHook valuesHook, List<String> attributeNames
            , DataRegionHeading.FUNCTION function, Map<List<Name>, Set<Value>> nameComboValueCache, StringBuilder debugInfo) throws Exception {
        if (debugInfo != null) {
            debugInfo.append("\t");
            boolean first = true;
            for (Name n : calcnames) {
                if (first) {
                    debugInfo.append(n.getDefaultDisplayName());
                    first = false;
                } else {
                    debugInfo.append("\t" + n.getDefaultDisplayName());
                }
            }
            debugInfo.append("\t\t");
        }
        double[] values = new double[20];//should be enough!!
        int valNo = 0;
        StringTokenizer st = new StringTokenizer(calcString, " ");
        while (st.hasMoreTokens()) {
            String term = st.nextToken();
            if (OPS.contains(term)) { // operation
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
                    // NOTE! As it stands a term can have one of these attributes, it won't use more than one
                    // so get the name and add it to the other names
                    Name name = NameService.getNameFromListAndMarker(term, formulaNames);
                    if (debugInfo != null) {
                        debugInfo.append(name.getDefaultDisplayName() + " ");
                    }
                    List<Name> seekList = new ArrayList<>(calcnames); // copy before modifying - may be scope for saving this later but in the mean tiem it was causing a problem
                    seekList.add(name);
                    boolean changed = false;
                    if (name.getAttribute(Name.INDEPENDENTOF) != null) {// then this name formula term is saying it wants to exclude some names
                        if (debugInfo != null) {
                            debugInfo.append("Independent of " + name.getAttribute(Name.INDEPENDENTOF) + " ");
                        }
                        Name independentOfSet = NameService.findByName(azquoMemoryDBConnection, name.getAttribute(Name.INDEPENDENTOF));
                        Iterator<Name> seekListIterator = seekList.iterator();
                        while (seekListIterator.hasNext()) {
                            final Name test = seekListIterator.next();
                            if (!test.equals(name)
                                    && (independentOfSet.equals(test) || independentOfSet.findAllChildren().contains(test))) {
                                seekListIterator.remove();
                                changed = true;
                            }
                        }
                    }
                    // opposite of above I think - chance to factor?
                    if (name.getAttribute(Name.DEPENDENTON) != null) {// then this name formula term is saying it wants to exclude some names
                        if (debugInfo != null) {
                            debugInfo.append("Dependent on " + name.getAttribute(Name.DEPENDENTON) + " ");
                        }
                        Name dependentOnSet = NameService.findByName(azquoMemoryDBConnection, name.getAttribute(Name.DEPENDENTON));
                        Iterator<Name> seekListIterator = seekList.iterator();
                        while (seekListIterator.hasNext()) {
                            final Name test = seekListIterator.next();
                            if (!test.equals(name)
                                    && !(dependentOnSet.equals(test) || dependentOnSet.findAllChildren().contains(test))) {  // as above but the the way around, if it's not the "dependent on" remove it from the list
                                seekListIterator.remove();
                                changed = true;
                            }
                        }
                    }
                    if (name.getAttribute(Name.USELEVEL) != null) {// will only be used in context of lowest level (as in calc on lowest level then sum)
                        // what we're saying is check through the calc names at this lowest level and bump any up to the set specified by "USE LEVEL" if possible
                        if (debugInfo != null) {
                            debugInfo.append("Use level " + name.getAttribute(Name.USELEVEL) + " ");
                        }
                        List<Name> newSeekList = new ArrayList<>(seekList.size()); // new one of same capacity, we'll be copying in changing as we go
                        final Collection<Name> useLevelNames = NameService.parseQuery(azquoMemoryDBConnection, name.getAttribute(Name.USELEVEL));
                        for (Name currentName : seekList) { // so for each of the names I need to see if they are in any of them are in the children of the use level names and if so switch to that use level name
                            boolean found = false;
                            for (Name useLevelName : useLevelNames) {
                                if (useLevelName.findAllChildren().contains(currentName)) {
                                    found = true;
                                    newSeekList.add(useLevelName);
                                    changed = true;
                                    break;
                                }
                            }
                            if (!found) {
                                newSeekList.add(currentName); // leave it as it was
                            }
                        }
                        seekList = newSeekList;
                    }
                    if (debugInfo != null && changed) {
                        debugInfo.append(" - set = ");
                        boolean first = true;
                        for (Name n : seekList) {
                            if (first) {
                                debugInfo.append(n.getDefaultDisplayName());
                                first = false;
                            } else {
                                debugInfo.append(", " + n.getDefaultDisplayName());
                            }
                        }
                    }
                    //note - would there be recursion? Resolve order of formulae might be unreliable
                    double value = findValueForNames(azquoMemoryDBConnection, seekList, locked, valuesHook, attributeNames, function, nameComboValueCache);
                    if (debugInfo != null) {
                        debugInfo.append(" = " + value + "\t");
                    }
                    values[valNo++] = value;
                }
            }
        }
        if (debugInfo != null) {
            debugInfo.append("\n");
        }
        return values[0];
    }

    // Added by Edd, like above but uses an attribute (attributes?) and doesn't care about calc for the moment, hence should be much more simple
    // For the moment on the initial version don't use set intersection, just look at the headings as handed to the function - will it ever need to do this?

    private static AtomicInteger findValueForHeadingsCount = new AtomicInteger(0);

    public static String findValueForHeadings(final List<DataRegionHeading> headings, final MutableBoolean locked) throws Exception {
        findValueForHeadingsCount.incrementAndGet();
        List<Name> names = DSSpreadsheetService.namesFromDataRegionHeadings(headings);
        if (names.size() != 1) {
            locked.isTrue = true;
        }
        Set<String> attributes = DSSpreadsheetService.attributesFromDataRegionHeadings(headings);
        String stringResult = null;
        double numericResult = 0;
        int count = 0;
        String attValue = null;
        for (Name n : names) { // go through each name
            for (String attribute : attributes) { // and for each name the
                attValue = n.getAttribute(attribute.replace("`", "").toUpperCase());
                if (attValue != null) {
                    count++;
                    if (!locked.isTrue && n.getAttribute(attribute, false, new HashSet<>()) == null) { // the attribute is not against the name itself (it's from the parent or structure)
                        locked.isTrue = true;
                    }/* Edd note on comment, this was being added for saving but the only place this is called the names and attributes are added outside,
                     I don't see how these maps that were passed could not contain the required names and attributes.
                     else {
                        // this feels a little hacky but I need to record this for saving purposes later
                        // that is to say this is not required directly for the return value here
                        if (!namesForMap.contains(n)) {
                            namesForMap.add(n);
                        }
                        if (!attributesForMap.contains(attribute)) {
                            attributesForMap.add(attribute);
                        }
                    }*/
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

    // on a standard non-calc cell this will give the result
    // heavily used function - a fair bit of testing has gone on to increase speed and reduce garbage collection, please be careful before changing

    private static AtomicInteger resolveValuesForNamesIncludeChildrenCount = new AtomicInteger(0);

    private static double resolveValuesForNamesIncludeChildren(final List<Name> names
            , DSSpreadsheetService.ValuesHook valuesHook, DataRegionHeading.FUNCTION function, MutableBoolean locked, Map<List<Name>, Set<Value>> nameComboValueCache) {
        resolveValuesForNamesIncludeChildrenCount.incrementAndGet();
        //System.out.println("resolveValuesForNamesIncludeChildren");
        long start = System.nanoTime();

        List<Value> values = findForNamesIncludeChildren(names, nameComboValueCache);
        double max = 0;
        double min = 0;

        part1NanoCallTime += (System.nanoTime() - start);
        long point = System.nanoTime();
        double sumValue = 0;
        boolean first = true;
        for (Value value : values) {
            if (value.getText() != null && value.getText().length() > 0) {
                try {
                    double doubleValue = Double.parseDouble(value.getText());
                    if (first) {
                        max = doubleValue;
                        min = doubleValue;
                    } else {
                        if (doubleValue < min) {
                            min = doubleValue;
                        }
                        if (doubleValue > max) {
                            max = doubleValue;
                        }
                    }
                    sumValue += doubleValue;
                    first = false;
                } catch (Exception ignored) {
                }
            }
        }
        // ok the hack here is that it was just values. add all but this often involved copying into an empty list which is silly if the list is here and won't be used after,
        // hence most of the time use the ready made list unless there's already one there in which case we're part of calc and will need to add
        // I'm trying to minimise garbage
        if (valuesHook.values != null) {
            valuesHook.values.addAll(values);
        } else {
            valuesHook.values = values;
        }
        part2NanoCallTime += (System.nanoTime() - point);
        totalNanoCallTime += (System.nanoTime() - start);
        numberOfTimesCalled++;
        if (values.size() > 1) {
            locked.isTrue = true;
        }
        if (function == DataRegionHeading.FUNCTION.COUNT) {
            return values.size();
        }
        if (function == DataRegionHeading.FUNCTION.AVERAGE) {
            if (values.size() == 0) {
                return 0; // avoid dividing by zero
            }
            return sumValue / values.size();
        }
        if (function == DataRegionHeading.FUNCTION.MAX) {
            return max;
        }
        if (function == DataRegionHeading.FUNCTION.MIN) {
            return min;
        }
        return sumValue; // default to sum, no function
    }

    public static void printSumStats() {
        if (numberOfTimesCalled > 0) {
            logger.info("calls to  resolveValuesForNamesIncludeChildren : " + numberOfTimesCalled);
            logger.info("part 1 average nano : " + (part1NanoCallTime / numberOfTimesCalled));
            logger.info("part 2 average nano : " + (part2NanoCallTime / numberOfTimesCalled));
            logger.info("total average nano : " + (totalNanoCallTime / numberOfTimesCalled));
        }
    }

    // find the most used name by a set of values, used by printBatch to derive headings

    private static AtomicInteger getMostUsedNameCount = new AtomicInteger(0);

    private static Name getMostUsedName(Set<DummyValue> values, Name topParent) {
        getMostUsedNameCount.incrementAndGet();
        Map<Name, Integer> nameCount = new HashMap<>();
        for (DummyValue value : values) {
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

    // by time. For database inspect.
    private static AtomicInteger sortValuesCount = new AtomicInteger(0);

    public static void sortValues(List<Value> values) {
        sortValuesCount.incrementAndGet();
        Collections.sort(values, (o1, o2) ->
        {
            // check this is the right way around later
            if (o1.getProvenance().getTimeStamp() == null) {
                return 1;
            }
            if (o2.getProvenance().getTimeStamp() == null) {
                return -1;
            }
            return (o2.getProvenance().getTimeStamp())
                    .compareTo(o1.getProvenance().getTimeStamp());
        });
    }

    // printbatch was creating a value, no good, use this instead
    private static class DummyValue {

        private final int id;

        private final String valueText;
        private final Collection<Name> names;

        DummyValue(int id, String valueText, Collection<Name> names) {
            this.id = id;
            this.valueText = valueText;
            this.names = names;
        }

        String getValueText() {
            return valueText;
        }

        public Collection<Name> getNames() {
            return names;
        }

        public int getId() {
            return id;
        }
    }

    /* nodify the values. It finds the name which represents the most values and displays
    them under them then the name that best represents the rest etc etc until all values have been displayed
    For inspecting databases
      */

    private static AtomicInteger getTreeNodesFromValuesCount = new AtomicInteger(0);

    private static List<TreeNode> getTreeNodesFromValues(Set<Value> values, int maxSize) {
        getTreeNodesFromValuesCount.incrementAndGet();
        Set<DummyValue> convertedToDummy = new HashSet<>(values.size());
        for (Value value : values) {
            convertedToDummy.add(new DummyValue(value.getId(), value.getText(), value.getNames()));
        }
        return getTreeNodesFromDummyValues(convertedToDummy, maxSize);
    }

    private static AtomicInteger getTreeNodesFromValues2Count = new AtomicInteger(0);

    private static List<TreeNode> getTreeNodesFromDummyValues(Set<DummyValue> values, int maxSize) {
        getTreeNodesFromValues2Count.incrementAndGet();
        //int debugCount = 0;
        boolean headingNeeded = false;
        double dValue = 0.0;
        List<TreeNode> nodeList = new ArrayList<>();
        int count = 0;
        for (DummyValue value : values) {
            if (value.getNames().size() > 1) {
                headingNeeded = true;
                break;
            }
            count++;
            if (count > maxSize) {
                nodeList.add(new TreeNode((values.size() - maxSize) + " more...", "", 0, 0));
                break;
            }
            String nameFound = null;
            for (Name name : value.getNames()) {
                nameFound = name.getDefaultDisplayName(); // so it's always going to be the last name??
            }
            String val = value.getValueText();
            double d = 0;
            try {
                d = Double.parseDouble(val);
                if (d != 0) {
                    val = roundValue(d);
                }
            } catch (Exception ignored) {
            }
            nodeList.add(new TreeNode(nameFound, val, d, value.getId()));
        }
        if (headingNeeded) {
            Name topParent = null;
            while (values.size() > 0) {
                count++;
                Name heading = getMostUsedName(values, topParent);
                topParent = heading.findATopParent();
                Set<DummyValue> extract = new HashSet<>();
                Set<DummyValue> slimExtract = new HashSet<>();
                for (DummyValue value : values) {
                    if (value.getNames().contains(heading)) {
                        extract.add(value);
                        try {
                            dValue += Double.parseDouble(value.getValueText());
                        } catch (Exception e) {
                            //ignore
                        }
                        //creating a new 'value' with one less name for recursion
                        try {
                            Set<Name> slimNames = new HashSet<>(value.getNames());
                            slimNames.remove(heading);
                            DummyValue slimValue = new DummyValue(value.getId(), value.getValueText(), slimNames);
                            slimExtract.add(slimValue);
                        } catch (Exception e) {
                            // exception from value constructor, should not happen
                            e.printStackTrace();
                        }
                        //debugCount = slimValue.getNames().size();
                    }
                }
                values.removeAll(extract);
                nodeList.add(new TreeNode("", heading.getDefaultDisplayName(), roundValue(dValue), dValue, getTreeNodesFromDummyValues(slimExtract, maxSize)));
                dValue = 0;
            }
        }
        return nodeList;

    }

    private static AtomicInteger roundValueCount = new AtomicInteger(0);

    private static String roundValue(double dValue) {
        roundValueCount.incrementAndGet();
        Locale locale = Locale.getDefault();
        NumberFormat nf = NumberFormat.getInstance(locale);
        // is other formatting required?
        return nf.format(dValue);
    }

    // jam this outside to stop instantiation in each function call
    private static DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");

    private static AtomicInteger getTreeNodeCount = new AtomicInteger(0);

    public static TreeNode getTreeNode(Set<Value> values, Provenance p, int maxSize) {
        getTreeNodeCount.incrementAndGet();
        String source = (p.getTimeStamp() != null ? df.format(p.getTimeStamp()) : "date unknown") + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        TreeNode toReturn = new TreeNode(source, method, null, 0, getTreeNodesFromValues(values, maxSize));
        addNodeValues(toReturn);
        return toReturn;
    }

    private static AtomicInteger addNodeValuesCount = new AtomicInteger(0);

    public static void addNodeValues(TreeNode t) {
        addNodeValuesCount.incrementAndGet();
        double d = 0;
        for (TreeNode child : t.getChildren()) {
            d += child.getDvalue();
        }
        t.setValue(roundValue(d));
        t.setDvalue(d);
    }

    // these 4 are related to database copying, an admin function, It's more complex than a simple copy due to the ability to copy at a level
    // EFC note : I'm re-enabling this code but I'm not going to look too closely until it's being used again

    private static AtomicInteger sumNameCount = new AtomicInteger(0);

    private static Name sumName(Name name, List<Set<Name>> searchNames) {
        sumNameCount.incrementAndGet();
        for (Set<Name> searchName : searchNames) {
            Name maybeParent = NameService.inParentSet(name, searchName);
            if (maybeParent != null) {
                return maybeParent;
            }
        }
        return name;
    }

    private static AtomicInteger findForSearchNamesIncludeChildrenCount = new AtomicInteger(0);

    // for searches, the Names are a List of sets rather than a set, and the result need not be ordered
    // todo - get rid of part1NanoCallTime1 etc or use different variables! They are usef in the main value resolving function
    private static Set<Value> findForSearchNamesIncludeChildren(final List<Set<Name>> names) {
        findForSearchNamesIncludeChildrenCount.incrementAndGet();
        long start = System.nanoTime();

        final Set<Value> values = new HashSet<>();
        // assume that the first set of names is the most restrictive
        Set<Name> smallestNames = names.get(0);
        part1NanoCallTime1.addAndGet(System.nanoTime() - start);
        long point = System.nanoTime();

        final Set<Value> valueSet = new HashSet<>();
        for (Name name : smallestNames) {
            valueSet.addAll(name.findValuesIncludingChildren());
        }
        // this seems a fairly crude implementation, list all values for the name sets then check that that values list is in all the name sets
        part2NanoCallTime1.addAndGet(System.nanoTime() - point);
        point = System.nanoTime();
        for (Value value : valueSet) {
            boolean theValueIsOk = true;
            for (Set<Name> nameSet : names) {
                if (!nameSet.equals(smallestNames)) { // ignore the one we started with
                    boolean foundInChildList = false;
                    for (Name valueNames : value.getNames()) {
                        if (NameService.inParentSet(valueNames, nameSet) != null) {
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
        part3NanoCallTime1.addAndGet(System.nanoTime() - point);
        numberOfTimesCalled1.incrementAndGet();
        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    private static AtomicInteger getSearchValuesCount = new AtomicInteger(0);

    static Map<Set<Name>, Set<Value>> getSearchValues(final List<Set<Name>> searchNames) throws Exception {
        getSearchValuesCount.incrementAndGet();
        if (searchNames == null) return null;
        Set<Value> values = findForSearchNamesIncludeChildren(searchNames);
        //The names on the values have been moved 'up' the tree to the name that was searched
        // e.g. if the search was 'England' and the name was 'London' then 'London' has been replaced with 'England'
        // so there may be duplicates in an unordered search - hence the consolidation below.
        final Map<Set<Name>, Set<Value>> showValues = new HashMap<>();
        for (Value value : values) {
            Set<Name> sumNames = new HashSet<>();
            for (Name name : value.getNames()) {
                sumNames.add(sumName(name, searchNames));
            }
            Set<Value> alreadyThere = showValues.get(sumNames);
            if (alreadyThere != null) {
                alreadyThere.add(value);
            } else {
                Set<Value> newValues = new HashSet<>();
                newValues.add(value);
                showValues.put(sumNames, newValues);
            }
        }
        return showValues;
    }

    private static AtomicInteger addValuesCount = new AtomicInteger(0);

    static String addValues(Set<Value> values) {
        addValuesCount.incrementAndGet();
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

    public static void printFunctionCountStats() {
        System.out.println("######### VALUE SERVICE FUNCTION COUNTS");
        System.out.println("nameCompareCount\t\t\t\t\t\t\t\t" + nameCompareCount.get());
        System.out.println("storeValueWithProvenanceAndNamesCount\t\t\t\t\t\t\t\t" + storeValueWithProvenanceAndNamesCount.get());
        System.out.println("overWriteExistingValueCount\t\t\t\t\t\t\t\t" + overWriteExistingValueCount.get());
        System.out.println("findForNamesCount\t\t\t\t\t\t\t\t" + findForNamesCount.get());
        System.out.println("findForNamesIncludeChildrenCount\t\t\t\t\t\t\t\t" + findForNamesIncludeChildrenCount.get());
        System.out.println("findValueForNamesCount\t\t\t\t\t\t\t\t" + findValueForNamesCount.get());
        System.out.println("findValueForHeadingsCount\t\t\t\t\t\t\t\t" + findValueForHeadingsCount.get());
        System.out.println("resolveValuesForNamesIncludeChildrenCount\t\t\t\t\t\t\t\t" + resolveValuesForNamesIncludeChildrenCount.get());
        System.out.println("getMostUsedNameCount\t\t\t\t\t\t\t\t" + getMostUsedNameCount.get());
        System.out.println("sortValuesCount\t\t\t\t\t\t\t\t" + sortValuesCount.get());
        System.out.println("getTreeNodesFromValuesCount\t\t\t\t\t\t\t\t" + getTreeNodesFromValuesCount.get());
        System.out.println("getTreeNodesFromValues2Count\t\t\t\t\t\t\t\t" + getTreeNodesFromValues2Count.get());
        System.out.println("roundValueCount\t\t\t\t\t\t\t\t" + roundValueCount.get());
        System.out.println("getTreeNodeCount\t\t\t\t\t\t\t\t" + getTreeNodeCount.get());
        System.out.println("addNodeValuesCount\t\t\t\t\t\t\t\t" + addNodeValuesCount.get());
        System.out.println("sumNameCount\t\t\t\t\t\t\t\t" + sumNameCount.get());
        System.out.println("findForSearchNamesIncludeChildrenCount\t\t\t\t\t\t\t\t" + findForSearchNamesIncludeChildrenCount.get());
        System.out.println("getSearchValuesCount\t\t\t\t\t\t\t\t" + getSearchValuesCount.get());
        System.out.println("addValuesCount\t\t\t\t\t\t\t\t" + addValuesCount.get());
    }

    public static void clearFunctionCountStats() {
        nameCompareCount.set(0);
        storeValueWithProvenanceAndNamesCount.set(0);
        overWriteExistingValueCount.set(0);
        findForNamesCount.set(0);
        findForNamesIncludeChildrenCount.set(0);
        findValueForNamesCount.set(0);
        findValueForHeadingsCount.set(0);
        resolveValuesForNamesIncludeChildrenCount.set(0);
        getMostUsedNameCount.set(0);
        sortValuesCount.set(0);
        getTreeNodesFromValuesCount.set(0);
        getTreeNodesFromValues2Count.set(0);
        roundValueCount.set(0);
        getTreeNodeCount.set(0);
        addNodeValuesCount.set(0);
        sumNameCount.set(0);
        findForSearchNamesIncludeChildrenCount.set(0);
        getSearchValuesCount.set(0);
        addValuesCount.set(0);
    }
}