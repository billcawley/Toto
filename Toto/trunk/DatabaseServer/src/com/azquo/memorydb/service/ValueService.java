package com.azquo.memorydb.service;

import com.azquo.MultidimensionalListUtils;
import com.azquo.StringLiterals;
import com.azquo.StringUtils;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
import com.azquo.spreadsheet.*;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;

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
 * Workhorse hammering away at the memory DB, things like findValueForNames are crucial. Some code has been optimised according to rigorous testing.
 */
public final class ValueService {

    private static final String APPLIESTO = "APPLIES TO";

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
    // used to return a string that was never used! Now a boolean indicating if data was changed

    private static AtomicInteger storeValueWithProvenanceAndNamesCount = new AtomicInteger(0);

    public static boolean storeValueWithProvenanceAndNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String valueString, final Set<Name> names) throws Exception {
        return storeValueWithProvenanceAndNames(azquoMemoryDBConnection, valueString, names, false);
    }


    public static boolean storeValueWithProvenanceAndNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String valueString, final Set<Name> names, boolean override) throws Exception {
        boolean dataChanged = false;
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
        final List<Value> existingValues = findForNames(names);
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames2", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        boolean alreadyInDatabase = false;
        // there's new logic to add values to existing but this only applies to the same provenance and when importing (so same file)
        for (Value existingValue : existingValues) { // really should only be one
            if (existingValue.getProvenance().equals(azquoMemoryDBConnection.getProvenance()) && existingValue.getProvenance().getMethod().equals("imported")) {
                // add values from same data import
                try {
                    Double existingDouble = Double.parseDouble(existingValue.getText());
                    Double newValue = Double.parseDouble(valueString);
                    if (override) {
                        if (newValue.equals(existingDouble)) return false;
                    } else {
                        valueString = (existingDouble + newValue) + "";
                    }
                    existingValue.setText(valueString);
                    dataChanged = true;
                } catch (Exception e) {
                    // use the latest value
                }
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames2", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
            // won't be true for the same file as it will be caught above but from different files it could well happen
            if (StringUtils.compareStringValues(existingValue.getText(), valueString)) {
                alreadyInDatabase = true;
            } else {
                existingValue.delete();
                // provenance table : person, time, method, name
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames3", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
        }
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames4", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        if (!alreadyInDatabase) {
            dataChanged = true;
            // create
            Value value = createValue(azquoMemoryDBConnection, azquoMemoryDBConnection.getProvenance(), valueString);
            // and link to names
            value.setNamesWillBePersisted(names);
        }
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames5", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        return dataChanged;
    }

    // called when altering values in a spreadsheet
    // should somehow be factored with function above?
    private static AtomicInteger overWriteExistingValueCount = new AtomicInteger(0);

    public static boolean overWriteExistingValue(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Value existingValue, String newValueString) throws Exception {
        overWriteExistingValueCount.incrementAndGet();
        if (newValueString.contains(",")) {
            String replaced = newValueString.replace(",", "");
            if (NumberUtils.isNumber(replaced)) { // think that's fine
                // so without "," it IS a valid number, take commas out of valueString
                newValueString = replaced;
            }
        }
        if (StringUtils.compareStringValues(existingValue.getText(), newValueString)) { // converted to use compare string values rather than simple replace
            return true;
        }
        Value newValue = new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), newValueString);
        newValue.setNamesWillBePersisted(new HashSet<>(existingValue.getNames())); // a bit crappy but I'm trying to make it a list internally but interfaced by sets
        existingValue.delete();
        return true;
    }

    // doesn't work down the name children - generally this is used when storing

    private static AtomicInteger findForNamesCount = new AtomicInteger(0);

    public static List<Value> findForNames(final Collection<Name> names) {
        findForNamesCount.incrementAndGet();
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

    // aside from a recursive call this is used in one place and is crucial to
    // how to get rid of exactName that WFC added???
    private static AtomicInteger findForNamesIncludeChildrenCount = new AtomicInteger(0);

    private static List<Value> findForNamesIncludeChildren(final List<Name> names, Map<List<Name>, Set<Value>> nameComboValueCache, Name exactName) {

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
        // this chunk in the if/else is to get the smallest value set and sets to check, can it be factored?
        // key to nameComboValueCache is to cache the values against all but one of the names (this assumes the names have been sent in a useful order!) And then use that on subsequent calls
        if (nameComboValueCache != null && names.size() > 3) {
//            List<Name> allButTwo = names.subList(0, names.size() - 2);
            // testing is showing all but one being the fastest - this might make the
            List<Name> allButOne = names.subList(0, names.size() - 1);
            // note it may not actually be the smallest, we hope it is!
            // the collection wrap may cost, guess we'll see how it goes
            part1NanoCallTime1.addAndGet(System.nanoTime() - point);
//            smallestValuesSet = nameComboValueCache.computeIfAbsent(allButTwo, computed -> HashObjSets.newImmutableSet(findForNamesIncludeChildren(allButTwo, payAttentionToAdditive, null)));
            smallestValuesSet = nameComboValueCache.computeIfAbsent(allButOne, computed -> HashObjSets.newImmutableSet(findForNamesIncludeChildren(allButOne, null, exactName)));
            point = System.nanoTime(); //reset after the cache hit as that will be measured in the recursive call
/*            setsToCheck = new Set[2];
            setsToCheck[0] = names.get(names.size() - 2).findValuesIncludingChildren(payAttentionToAdditive);
            setsToCheck[1] = names.get(names.size() - 1).findValuesIncludingChildren(payAttentionToAdditive);*/
            setsToCheck = new Set[1];
            // Edd note - I'm not particularly happy about this
            if (exactName != null && names.get(names.size() - 1) == exactName) {
                setsToCheck[0] = HashObjSets.newImmutableSet(names.get(names.size() - 1).getValues());
            } else {
                setsToCheck[0] = names.get(names.size() - 1).findValuesIncludingChildren();
            }
        } else {
            // first get the shortest value list taking into account children
            int smallestNameSetSize = -1;
            Name smallestName = null;
            for (Name name : names) {
                int setSizeIncludingChildren = 0;

                if (name == exactName) {
                    setSizeIncludingChildren = name.getValues().size();
                } else {
                    setSizeIncludingChildren = name.findValuesIncludingChildren().size();
                }

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

            if (smallestName == exactName) {
                smallestValuesSet = HashObjSets.newImmutableSet(smallestName.getValues());
            } else {
                smallestValuesSet = smallestName.findValuesIncludingChildren();
            }
            part2NanoCallTime1.addAndGet(System.nanoTime() - point);
            point = System.nanoTime();
            // ok from testing a new list using contains against values seems to be the thing, double the speed at least I think!
            setsToCheck = new Set[names.size() - 1]; // I don't want to be creating iterators when checking. Iterator * millions = garbage (in the Java sense!). No problems losing typing, I just need the contains.
            int arrayIndex = 0;
            for (Name name : names) {
                // note if smallest name is in there twice (duplicate names) then setsToCheck will hav e mull elements at the end, I check for this later in the big loop, should probably zap that. Or get rid of the smallest names before?
                if (name != smallestName) { // a little cheaper than making a new name set and knocking this one off I think
                    if (name == exactName) {
                        setsToCheck[arrayIndex] = HashObjSets.newImmutableSet(name.getValues());
                    } else {
                        setsToCheck[arrayIndex] = name.findValuesIncludingChildren();
                    }
                    arrayIndex++;
                }
            }
        }
        // The core things we want to know about values e.g. sum, max, min, could be done in here but currently the values list, via ValuesHook, is still accesed and used.
        // When we zap it there may be a case for having this funciton return a double according to whether min max sum etc is required. Performance is pretty good at the moment though.
        boolean add; // declare out here, save reinitialising each time
        int index; // ditto that, should help
        for (Value value : smallestValuesSet) { // because this could be a whacking great loop! I mean many millions.
            // stopping the iterator in here and moving the declarations out of here made a MASSIVE difference!
            /*

            BE CAREFUL DOING ANYTHING INSIDE THIS LOOP

             */
            add = true;
            for (index = 0; index < setsToCheck.length; index++) {
                if (setsToCheck[index] != null && !setsToCheck[index].contains(value)) {//setsToCheck may not have the same dimensionality if there are duplicate headings and 'smallestName' is the duplicate heading
                    add = false;
                    break;
                }
            }
            if (add) {
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

    // the function that populates each cell with a numeric value that could be a value, a calculation etc.
    private static AtomicInteger findValueForNamesCount = new AtomicInteger(0);

    public static double findValueForNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<Name> names, final List<String> calcs, final MutableBoolean locked
            , AzquoCellResolver.ValuesHook valuesHook, List<String> attributeNames, DataRegionHeading functionHeading, Map<List<Name>, Set<Value>> nameComboValueCache, StringBuilder debugInfo) throws Exception {
        findValueForNamesCount.incrementAndGet();
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<>();
        Collection<Name> appliesToNames = null; // used if you want formulae from calc to be resolved at a lower level and the result summed rather than sum each term in the calc formula
        List<Name> formulaNames = new ArrayList<>();
        List<String> formulaStrings = new ArrayList<>();
        List<String> nameStrings = new ArrayList<>();
        List<String> attributeStrings = new ArrayList<>();
        List<String> remainingCalcs = new ArrayList<>();
        String calcString = null; // whether this is null replaces hasCalc
        // add all names to calcnames except the the one with CALCULATION
        // and here's a thing : if more than one name has CALCULATION then only the first will be used
        boolean lowest = false; // special applies to calc for each permutation of the calc names then sum

        if (calcs != null && calcs.size() > 0) {
            for (String calc : calcs) {
                if (calcString == null) {
                    calc = StringUtils.prepareStatement(calc, nameStrings, formulaStrings, attributeStrings);
                    if (debugInfo != null) {
                        debugInfo.append("\nCalculation from heading\n");
                        debugInfo.append("\t").append(calc).append("\n");
                    }
                    formulaNames = NameQueryParser.getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
                    calc = StringUtils.fixNumberFunction(calc, StringLiterals.EXP);
                    calc = StringUtils.shuntingYardAlgorithm(calc);
                    if (!calc.startsWith("error")) { // there should be a better way to deal with errors
                        calcString = calc;
                    }
                } else
                    remainingCalcs.add(calc);

            }
        }
        for (Name name : names) {
            if (calcString == null) {// then try and find one - can only happen once
                String calc = name.getAttribute(StringLiterals.CALCULATION, false, null); // using extra parameters to stop parent checking for this attribute
                if (calc != null) {
                    if (debugInfo != null) {
                        debugInfo.append("\nCalculation from ").append(name.getDefaultDisplayName()).append("\n");
                        debugInfo.append("\t").append(calc).append("\n");
                    }
                    if (name.getAttribute(APPLIESTO) != null) {
                        if (debugInfo != null) {
                            debugInfo.append("\tApplies to ").append(name.getAttribute(APPLIESTO)).append("\n");
                        }
                        // will be being looked up by default display name for the moment
                        if (name.getAttribute(APPLIESTO).trim().equalsIgnoreCase("lowest")) {
                            lowest = true;
                        } else {
                            appliesToNames = NameQueryParser.parseQuery(azquoMemoryDBConnection, name.getAttribute(APPLIESTO));
                        }
                    }
                    // then get the result of it, this used to be stored in RPCALC
                    // it does extra things we won't use but the simple parser before SYA should be fine here
                    calc = StringUtils.prepareStatement(calc, nameStrings, formulaStrings, attributeStrings);
                    formulaNames = NameQueryParser.getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
                    calc = StringUtils.fixNumberFunction(calc, StringLiterals.EXP);
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
            final List<List<Name>> permutationOfLists = MultidimensionalListUtils.get2DPermutationOfLists(toPermute);
            double toReturn = 0;
            for (List<Name> lowLevelCalcNames : permutationOfLists) { // it's lowLevelCalcNames that we were after
                if (valuesHook.calcValues == null) {
                    valuesHook.calcValues = new ArrayList<>();
                }
                double result = ValueCalculationService.resolveCalc(azquoMemoryDBConnection, calcString, formulaNames, lowLevelCalcNames, remainingCalcs, locked, valuesHook, attributeNames, functionHeading, nameComboValueCache, debugInfo);
                valuesHook.calcValues.add(result);
                toReturn += result;
            }
            return toReturn;
        } else {
            /* new logic - leave the calc names alone, this is about deciding, if we have appliesToNames, how many of these names will go into the outer loop
            any calc names which cross over with applied names will trim it, any which don't won't. So make outer loops the same as applied names and trim where applicable
             */

            Collection<Name> outerLoopNames = new ArrayList<>();
            if (appliesToNames != null) { // then try and find the name
                outerLoopNames = appliesToNames;
                //System.out.println("Outer loop size : " + appliesToNames.size());
                for (Name calcName : calcnames) {
                    // I think the contains is on the second collection which means this way around hsould be fater than the other way around
                    if (!Collections.disjoint(outerLoopNames, calcName.findAllChildren())) { // nagative on disjoint, there were elements in common
                        outerLoopNames.retainAll(calcName.findAllChildren());
                        //System.out.println("Trim " + calcName.getDefaultDisplayName() + " : outer loop size : " + outerLoopNames.size());
                    }
                }
            }
            // no reverse polish converted formula, just sum
            if (calcString == null) {
                // I'll not debug in here for the moment. We should know names and function by now
                if (functionHeading != null && functionHeading.getFunction() == DataRegionHeading.FUNCTION.ALLEXACT) { // match only the values that correspond to the names exactly
                    final List<Value> forNames = findForNames(names);
                    // need to check we don't have values with extra names
                    forNames.removeIf(value -> value.getNames().size() > names.size()); // new syntax! Dunno about efficiency but this will be very rarely used
                    return ValueCalculationService.resolveValues(forNames, valuesHook, functionHeading, locked);
                } else {
                    Name exactName = null;
                    if (functionHeading != null && functionHeading.getFunction() == DataRegionHeading.FUNCTION.EXACT) {
                        exactName = functionHeading.getName();
                    }

                    return ValueCalculationService.resolveValues(findForNamesIncludeChildren(names, nameComboValueCache, exactName), valuesHook, functionHeading, locked);
                }
            } else {
                if (outerLoopNames.isEmpty()) { // will be most of the time, put the first in the outer loop
                    outerLoopNames.add(calcnames.remove(0));// as mentioned above in the case of normal use take the first and move it to the outside loop. Yes it will just be added straight back on but otherwise we have code duplication below in an else or a function with many parameters passed
                }
                double toReturn = 0;
                for (Name appliesToName : outerLoopNames) { // in normal use just a single
                    calcnames.add(appliesToName);
                    if (valuesHook.calcValues == null) {
                        valuesHook.calcValues = new ArrayList<>();
                    }
                    double result = ValueCalculationService.resolveCalc(azquoMemoryDBConnection, calcString, formulaNames, calcnames, remainingCalcs, locked, valuesHook, attributeNames, functionHeading, nameComboValueCache, debugInfo);
                    valuesHook.calcValues.add(result);
                    toReturn += result;
                    calcnames.remove(appliesToName);
                }
                locked.isTrue = true;
                return toReturn;
            }
        }
    }


    public static void printFunctionCountStats() {
        System.out.println("######### VALUE SERVICE FUNCTION COUNTS");
        System.out.println("nameCompareCount\t\t\t\t\t\t\t\t" + nameCompareCount.get());
        System.out.println("storeValueWithProvenanceAndNamesCount\t\t\t\t\t\t\t\t" + storeValueWithProvenanceAndNamesCount.get());
        System.out.println("overWriteExistingValueCount\t\t\t\t\t\t\t\t" + overWriteExistingValueCount.get());
        System.out.println("findForNamesCount\t\t\t\t\t\t\t\t" + findForNamesCount.get());
        System.out.println("findForNamesIncludeChildrenCount\t\t\t\t\t\t\t\t" + findForNamesIncludeChildrenCount.get());
        System.out.println("findValueForNamesCount\t\t\t\t\t\t\t\t" + findValueForNamesCount.get());
    }

    public static void clearFunctionCountStats() {
        nameCompareCount.set(0);
        storeValueWithProvenanceAndNamesCount.set(0);
        overWriteExistingValueCount.set(0);
        findForNamesCount.set(0);
        findForNamesIncludeChildrenCount.set(0);
        findValueForNamesCount.set(0);
    }
}