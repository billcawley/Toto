package com.azquo.spreadsheet;

import com.azquo.MultidimensionalListUtils;
import com.azquo.StringLiterals;
import com.azquo.ThreadPools;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 * <p>
 * Functions to resolve lists of DataRegionHeadings according to instructions in reports. A bit longer than I'd like but an improvement.
 */
class DataRegionHeadingService {
    /* This function now takes a 2d array representing the excel region. It expects blank or null for empty cells (the old text paste didn't support this)
    more specifically : the outermost list is of rows, the second list is each cell in that row and the final list is a list not a heading
    because there could be multiple names/attributes in a cell if the cell has something like container;children. Interpretnames is what does this. Hence
    seaports;children   container;children
    for example returns a list of 1 as there's only 1 row passed, with a list of 2 as there's two cells in that row with lists of something like 90 and 3 names in the two cell list items
    we're not permuting, just saying "here is what that 2d excel region looks like in DataRegionHeadings"
    */

    static List<List<List<DataRegionHeading>>> createHeadingArraysFromSpreadsheetRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<List<String>> headingRegion, List<String> attributeNames, DataRegionHeading.SUFFIX defaultSuffix, boolean ignoreHeadingErrors) throws Exception {
        return createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, headingRegion, attributeNames, 0, defaultSuffix, ignoreHeadingErrors);
    }


    // now has the option to exception based on large sets being returned by parse query. Used currently on columns, if these name sets are more than a few hundred then that's clearly unworkable - you wouldn't want more than a few hundred columns
    static List<List<List<DataRegionHeading>>> createHeadingArraysFromSpreadsheetRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<List<String>> headingRegion, List<String> attributeNames, int namesQueryLimit, DataRegionHeading.SUFFIX defaultSuffix, boolean ignoreHeadingErrors) throws Exception {
        List<List<List<DataRegionHeading>>> nameLists = new ArrayList<>(headingRegion.size());
        for (List<String> sourceRow : headingRegion) { // we're stepping through the cells that describe headings
            // ok here's the thing, before it was just names here, now it could be other things, attribute names formulae etc.
            List<List<DataRegionHeading>> row = new ArrayList<>(sourceRow.size());
            nameLists.add(row);
            for (String sourceCell : sourceRow) {
                sourceCell = NameQueryParser.replaceAttributes(azquoMemoryDBConnection,sourceCell.trim());
                //ignore 'editable' at this stage
                if (sourceCell.toLowerCase().endsWith(StringLiterals.EDITABLE)){
                    sourceCell = sourceCell.substring(0,sourceCell.length()-StringLiterals.EDITABLE.length()).trim();
                }
                if (sourceCell.length() == 0) {
                    row.add(null);
                } else {
                    if (sourceCell.endsWith("↕") || sourceCell.endsWith("↑") || sourceCell.endsWith("↓")) {
                        sourceCell = sourceCell.substring(0, sourceCell.length() - 2);
                    }
                    // ok dictionary is slightly awkward as a function as it can work with a single name *or* an attribute
                    // also it will not expand - it will be one row or column
                    if (sourceCell.toUpperCase().startsWith(DataRegionHeading.FUNCTION.DICTIONARY.name())) {
                        sourceCell = sourceCell.substring(sourceCell.indexOf("(") + 1, sourceCell.trim().length() - 1);
                        // todo - this function parameter parsing needs to be factored and be aware of commas in names
                        String[] split = sourceCell.split(",");
                        if (split.length == 3){
                            List<DataRegionHeading> single = new ArrayList<>();
                            String nameOrAttribute = split[0];
                            final Collection<Name> dictionarySet = NameQueryParser.parseQuery(azquoMemoryDBConnection, split[1], attributeNames, false);
                            String dictionaryAttribute = split[2];
                            if (nameOrAttribute.startsWith(".")){ // attribute
//                                DataRegionHeading(Name name, boolean writeAllowed, FUNCTION function, DataRegionHeading.SUFFIX suffix, String description, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter, String calculation) {
                                single.add(new DataRegionHeading(nameOrAttribute, false, DataRegionHeading.FUNCTION.DICTIONARY, null, dictionaryAttribute,null,dictionarySet, 0));// we say that an attribute heading defaults to writable, it will defer to the name
                            } else {
                                Name name = NameService.findByName(azquoMemoryDBConnection, sourceCell, attributeNames);
                                if (name != null){
                                    single.add(new DataRegionHeading(nameOrAttribute, false, DataRegionHeading.FUNCTION.DICTIONARY, null, dictionaryAttribute,null,dictionarySet, 0));// we say that an attribute heading defaults to writable, it will defer to the name
                                }
                            }
                            row.add(single);
                        }
                    } else if (sourceCell.startsWith(".")) { //attribute
                        // currently only one attribute per cell, I suppose it could be many in future (available attributes for a name, a list maybe?)
                        // do NOT use singleton list as nice as the code looks! The list may be modified later . . .
                        List<DataRegionHeading> single = new ArrayList<>();
                        Collection<Name> attributeSet = null;
                        try {
                            attributeSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, sourceCell.substring(1));
                        } catch (Exception e) {
                            //not a recognisable set
                        }
                        single.add(new DataRegionHeading(sourceCell, true, attributeSet));// we say that an attribute heading defaults to writable, it will defer to the name
                        row.add(single);
                    } else {
                        DataRegionHeading.FUNCTION function = null;// that's the value or sum of values
                        // now allow functions
                        for (DataRegionHeading.FUNCTION _function : DataRegionHeading.FUNCTION.values()) {
                            if (sourceCell.toUpperCase().startsWith(_function.name() + "(")) { // function name bracket no sapce
                                function = _function;
                                sourceCell = sourceCell.substring(sourceCell.indexOf("(") + 1, sourceCell.trim().length() - 1);// +1 - 1 to get rid of the function name and brackets
                            }
                        }
                        DataRegionHeading.SUFFIX suffix = null;// assign similar to functions
                        for (DataRegionHeading.SUFFIX _suffix : DataRegionHeading.SUFFIX.values()) {
                            if (sourceCell.toUpperCase().endsWith(_suffix.name())) {
                                suffix = _suffix;
                                sourceCell = sourceCell.substring(0, sourceCell.length() - suffix.name().length()).trim();
                            }
                        }
                        if (suffix == null && defaultSuffix != null) {
                            suffix = defaultSuffix;
                        }
                        /* The way expression functions work is going to change to allow [ROWHEADING] and [COLUMNHEADING] which work with set operators, / * - + etc.
                           This means that in the case of expression functions the heading can't cache sets, it needs to evaluate the formulae on each line and it means that each
                           heading needs to have its description populated even if it's a simple name. Caching of heading will be broken, this would slow down Damart (which will be broken anyway due to changing syntax)
                           for example but that's not such a concern right now. Later when evaluating cells it will look for a function in the row heading first then the column heading
                           and it will evaluate the first it finds taking into account [ROWHEADING] and [COLUMNHEADING], it can't evaluate both. And [ROWHEADING] means the first row heading
                           if there are more than one we may later allow [ROWHEADING1], [ROWHEADING2] etc.
                           */
                        if (DataRegionHeading.isExpressionFunction(function)) { // then just set the description to be resolved later
                            List<DataRegionHeading> forFunction = new ArrayList<>();
                            forFunction.add(new DataRegionHeading(null, false, function, suffix, sourceCell, null)); // in this case the heading is just a placeholder for the formula to be evaluated later - that forumla being held in the description of the heading
                            row.add(forFunction);
                            // ok this is the kind of thing that would typically be in name service where queries are parsed but it needs to set some formatting info (visually indentation) so
                            // it will be in here with limited parsing support e.g. `All customers` hierarchy 3 that is to say name, hierarchy, number
                        } else if (sourceCell.toLowerCase().contains(StringLiterals.HIERARCHY)) { // kind of a special case, supports a name not an expression
                            String name = sourceCell.substring(0, sourceCell.toLowerCase().indexOf(StringLiterals.HIERARCHY)).replace("`", "").trim();
                            int level = Integer.parseInt(sourceCell.substring(sourceCell.toLowerCase().indexOf(StringLiterals.HIERARCHY) + StringLiterals.HIERARCHY.length()).trim()); // fragile?
                            Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, name, attributeNames, true); // should return one
                            if (!names.isEmpty()) {// it should be just one
                                List<DataRegionHeading> hierarchyList = new ArrayList<>();
                                List<DataRegionHeading> offsetHeadings = dataRegionHeadingsFromNames(names, function, suffix, null, null, 0); // I assume this will be only one!
                                resolveHierarchyForHeading(offsetHeadings.get(0), hierarchyList, function, suffix, new ArrayList<>(), level);
                                if (namesQueryLimit > 0 && hierarchyList.size() > namesQueryLimit) {
                                    throw new Exception("While creating headings " + sourceCell + " resulted in " + hierarchyList.size() + " names, more than the specified limit of " + namesQueryLimit);
                                }
                                row.add(hierarchyList);
                            }
                        } else if (function == DataRegionHeading.FUNCTION.PERMUTE) {
                            String[] permutedNames = sourceCell.split(",");
                            List<DataRegionHeading> headings = new ArrayList<>();
                            for (String permutedName : permutedNames) {
                                // todo - what if you have `thingamy whatsit sorted`?
                                boolean sorted = permutedName.contains(" sorted");
                                if (sorted) {
                                    permutedName = permutedName.substring(0, permutedName.indexOf(" sorted")).trim();
                                }
                                // EFC noting the hack, looking for a name created by pivot selections to permute as the first possibility.Need to see how this is set to confirm if it can be improved. todo
                                Name pName = null;
                                // now need try catch - might get an error if other users made the name but this one didn't thus the lookup is ambiguous
                                try{
                                    pName = NameService.findByName(azquoMemoryDBConnection, "az_" + permutedName.replace("`", "").trim(), attributeNames);
                                    // if no set chosen, find the original set
                                    if (pName == null || pName.getChildren().size() == 0) {
                                        // in new logic keep the attribute names in the search - otherwise inconsistent with above, will break on temporary names populated wth "as"
                                        // I don't think I need to zap the quotes
                                        pName = NameService.findByName(azquoMemoryDBConnection, permutedName, attributeNames);
                                        if (pName == null) {
                                            Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, permutedName, attributeNames, false);
                                            if (names != null && names.size() == 1) {
                                                pName = names.iterator().next();
                                            }
                                        }
                                    }
                                } catch (Exception e){
                                        System.out.println("permute lookup error " + e.getMessage());
                                     //pName = NameService.findByName(azquoMemoryDBConnection, permutedName);
                                }
                                // ok now we are using sorted heare we can't use dataRegionHeadingsFromNames, this is similar to the check internally
                                // I'm going to jam sorted in the description, hopefully won't be a problem

/*                                if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()) {
                                    // will the new write permissions cause an overhead?
                                    headings.add(new DataRegionHeading(pName, NameQueryParser.isAllowed(pName, azquoMemoryDBConnection.getWritePermissions()), function, suffix, sorted ? "sorted" : null, null, null, 0));
                                } else { // don't bother checking permissions, write permissions to true
 */
                                headings.add(new DataRegionHeading(pName, true, function, suffix, sorted ? "sorted" : null, null, null, 0));
                                //                             }
                            }
                            row.clear();
                            row.add(headings);
                            // just adding this back in now! Second parameter needs to go in valueFunctionSet
                        } else if (function == DataRegionHeading.FUNCTION.VALUEPARENTCOUNT) {
                            // todo - this function parameter parsing needs to be factored and be aware of commas in names
                            String firstSet = sourceCell.substring(0, sourceCell.indexOf(",")).trim();
                            String secondSet = sourceCell.substring(sourceCell.indexOf(",") + 1).trim();
                            // maybe these two could have a "true" on returnReadOnlyCollection . . . todo
                            final Collection<Name> mainSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, firstSet, attributeNames, false);
                            final Collection<Name> selectionSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, secondSet, attributeNames, false);
                            row.add(dataRegionHeadingsFromNames(mainSet, function, suffix, null, selectionSet, 0));
                        } else if (function == DataRegionHeading.FUNCTION.PERCENTILE || function == DataRegionHeading.FUNCTION.PERCENTILENZ) {
                            // todo - this function parameter parsing needs to be factored and be aware of commas in names
                            String firstSet = sourceCell.substring(0, sourceCell.indexOf(",")).trim();
                            String percentileParam = sourceCell.substring(sourceCell.indexOf(",") + 1).trim();
                            boolean divideBy100 = false;
                            if (percentileParam.contains("%")) {
                                percentileParam = percentileParam.replace("%", "").trim();
                                divideBy100 = true;
                            }
                            double percentileDouble = 0;
                            try {
                                percentileDouble = Double.parseDouble(percentileParam);
                                if (divideBy100) {
                                    percentileDouble /= 100;
                                }
                            } catch (NumberFormatException ignored) { // maybe add an error later?
                            }
                            // maybe could have a "true" on returnReadOnlyCollection . . . todo
                            final Collection<Name> mainSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, firstSet, attributeNames, false);
                            row.add(dataRegionHeadingsFromNames(mainSet, function, suffix, null, null, percentileDouble));
                        } else if (DataRegionHeading.isBestMatchFunction(function)) {
                            // todo - this function parameter parsing needs to be factored and be aware of commas in names
                            int commaPos = sourceCell.indexOf(",");
                            if (commaPos < 0){
                                throw new Exception("best match functions need two parameters - a set name, and a name to match");
                            }
                            String firstSet = sourceCell.substring(0, sourceCell.indexOf(",")).trim();
                            String description = sourceCell.substring(sourceCell.indexOf(",") + 1).trim();
                            // maybe these two could have a "true" on returnReadOnlyCollection . . . todo
                            final Collection<Name> mainSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, firstSet, attributeNames, false);//returns the single name to find
                            List<DataRegionHeading> dataRegionHeadings = new ArrayList<>();
                            dataRegionHeadings.add(new DataRegionHeading((Name) null, true, function, suffix, description, null, mainSet, 0));
                            row.add(dataRegionHeadings);
                            //row.add(dataRegionHeadingsFromNames(null, function, suffix, null, mainSet, 0,description));
                        } else {// most of the time it will be a vanilla query, there may be value functions that will be dealt with later
                            Collection<Name> names;
                            try {
                                // ok due to optimiseation for the jewel hut this is the first place I'm going to tell the query parser that a read only returned collection is fine
                                // this should provide a decent speed increase since inside the query parser there was an addAll jamming things up
                                boolean sorted = false;
                                if (sourceCell.endsWith(" sorted")){
                                    sorted = true;
                                    // parseQuery needs the 'sorted' instruction for sorting name sets.   Irrelevent if sorting data
                                    //sourceCell = sourceCell.substring(0,sourceCell.length() - 7).trim();
                                }
                                if (sourceCell.startsWith("(") && sourceCell.endsWith(")")){
                                    List<DataRegionHeading> single = new ArrayList<>();
                                    sourceCell = sourceCell.substring(1,sourceCell.length()-1);


                                    single.add(new DataRegionHeading(null, false, null, null, sorted ? "sorted" : null, null, null, 0, sourceCell));
                                    row.add(single);
                                 }else {

                                    names = NameQueryParser.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames, true);
                                    if (namesQueryLimit > 0 && names.size() > namesQueryLimit) {
                                        throw new Exception("While creating headings " + sourceCell + " resulted in " + names.size() + " names, more than the specified limit of " + namesQueryLimit);
                                    }
                                    row.add(dataRegionHeadingsFromNames(names, function, suffix, null, null, 0));
                                }
                            } catch (Exception e) {
                                if (ignoreHeadingErrors) { // the ignore is only for vanilla queries, functions probably should error regardless
                                    List<DataRegionHeading> single = new ArrayList<>();
                                    single.add(new DataRegionHeading("[unknown]", false, null, null,sourceCell,null,null,0));// we say that an attribute heading defaults to writable, it will defer to the name
                                    row.add(single);
                                } else {
                                    throw e;
                                }
                            }
                        }
                    }
                }
            }
        }
        return nameLists;
    }

    // recursive, the key is to add the offset to allow formatting of the hierarchy
    private static void resolveHierarchyForHeading(DataRegionHeading heading, List<DataRegionHeading> target
            , DataRegionHeading.FUNCTION function, DataRegionHeading.SUFFIX suffix, List<DataRegionHeading> offsetHeadings, int levelLimit) {
        if (offsetHeadings.size() < levelLimit) {// then get the children
            List<DataRegionHeading> offsetHeadingsCopy = new ArrayList<>(offsetHeadings);
            offsetHeadingsCopy.add(heading);
            for (DataRegionHeading child : dataRegionHeadingsFromNames(heading.getName().getChildren(), function, suffix, offsetHeadingsCopy, null, 0)) {
                resolveHierarchyForHeading(child, target, function, suffix, offsetHeadingsCopy, levelLimit);
            }
        }
        target.add(heading); // the "parent" is added after
    }

    /* Created by WFC to sort pivot table permuted headings, commented/modified a little by EFC
    List to permute is the names from the permute function, found combinations is from the permute function, sort lists is the "natural ordering" of each of the lists of headings we het from the children
    in listToPermute. Hence the outside list size of listToPermute and sortLists should be the same
    A comparator might provide a more succinct sort (we'd need to pass sortLists still), would need to address how the totals were added after. Something to consider.
    */

    private static List<List<DataRegionHeading>> sortCombos(List<DataRegionHeading> listToPermute, Set<List<Name>> foundCombinations, int position, final List<Map<Name, Integer>> sortLists, boolean noPermuteTotals) {
        Map<Name, Integer> sortList = sortLists.get(position);
        // a tree map is ordered by keys, means putting by sort position will result in a correctly ordered iterator
        Map<Integer, Set<List<Name>>> sortMap = new TreeMap<>();
        // go through combinations putting them in order according to the name in "position" so if it's the first then all the names on the left will be grouped
        for (List<Name> foundCombination : foundCombinations) {
            int sortPosition = sortList.get(foundCombination.get(position));
            Collection<List<Name>> sortItem = sortMap.computeIfAbsent(sortPosition, integer -> new HashSet<>());
            sortItem.add(foundCombination);
        }
        position++;
        List<List<DataRegionHeading>> toReturn = new ArrayList<>();
        for (Set<List<Name>> names : sortMap.values()) { // so now we run through the groups of sorted names
            if (position == sortLists.size()) { // we're on the last one
                // according to the logic of the function I'd be very surprised if this ever had more than one combo at this point as it should be
                // sorted on the last and there are no duplicate foundCombinations
                for (List<Name> entry : names) {
                    List<DataRegionHeading> drhEntry = new ArrayList<>();
                    // now for that combo build the headings
                    for (Name name : entry) {
                        drhEntry.add(new DataRegionHeading(name, true, null, null, null, null));
                    }
                    toReturn.add(drhEntry);
                }
                // if it's not the last grab this clump of combos and run them through this function again to sort on the next level down
            } else {
                toReturn.addAll(sortCombos(listToPermute, names, position, sortLists, noPermuteTotals));
            }
        }
        // now add totals - remember this is recursive - this will happen leaving each level
        if (toReturn.size() > 0 && !noPermuteTotals) {//maybe should be 1 - no need for totals if there's only one item
            // I think this means add the last but one and then populate the rest with the top sets? it would make sense for totals
            List<DataRegionHeading> drhEntry = new ArrayList<>();
            List<DataRegionHeading> lastEntry = toReturn.get(toReturn.size() - 1);
            for (int i = 0; i < position - 1; i++) {
                drhEntry.add(lastEntry.get(i));
            }
            for (int i = position - 1; i < listToPermute.size(); i++) {
                drhEntry.add(listToPermute.get(i));
            }
            toReturn.add(drhEntry);
        }
        return toReturn;
    }

    /* ok, so, practically speaking the shared names if applicable are from context (probably the findAllChildren from one name)
    and the listToPermute is the contents of the permute function e.g. permute(`Product category`, `Product subcategory`) in Nisbets*/

    private static List<List<DataRegionHeading>> findPermutedItems(final Collection<Name> sharedNames, final List<DataRegionHeading> listToPermute, boolean noPermuteTotals) throws Exception {
        NumberFormat nf = NumberFormat.getInstance();
        long startTime = System.currentTimeMillis();
        List<Collection<Name>> sharedNamesSets = new ArrayList<>();
        List<Name> permuteNames = new ArrayList<>();
        List<Name> sharedNamesList = new ArrayList<>(); // an arraylist is fine it's only going to be iterated later
        // assemble the sets I want to find a cross section of
        Set<Name> permuteNamesThatNeedSorting = new HashSet<>(); //  a bit hacky. After extracting the permute names from the data region headings we need to check if any were flagged as sorted, jam their indexes in here
        for (DataRegionHeading drh : listToPermute) {
            if (drh.getName() != null) {
                permuteNames.add(drh.getName());
                sharedNamesSets.add(drh.getName().findAllChildren());
                if ("sorted".equalsIgnoreCase(drh.getDescription())) { // hacky, string literal and the description against a heading is not as clear as it could be!
                    permuteNamesThatNeedSorting.add(drh.getName());
                }
            }
        }
        if (sharedNames != null) {
            sharedNamesSets.add(sharedNames);
        }
        if (!sharedNamesSets.isEmpty()) {
            // similar logic to getting values for a name. todo - can we factor? - it would be a function that takes 3 sets and finds the intersection, not rocket surgery . . . well except the issue of return types and single sets? IN this case there will always be more than one set
            Collection<Name> smallestNameSet = null;
            for (Collection<Name> nameSet : sharedNamesSets) {
                if (smallestNameSet == null || nameSet.size() < smallestNameSet.size()) {
                    smallestNameSet = nameSet;
                    if (smallestNameSet.size() == 0) {// empty set, shared will bee empty just return empty now
                        return new ArrayList<>();
                    }
                }
            }
            Collection[] setsToCheck = new Collection[sharedNamesSets.size() - 1];
            int arrayIndex = 0;
            for (Collection<Name> sharedNameSet : sharedNamesSets) {
                if (sharedNameSet != smallestNameSet) {
                    setsToCheck[arrayIndex] = sharedNameSet;
                    arrayIndex++;
                }
            }
            boolean add;
            int index;
            for (Name name : smallestNameSet) {
                add = true;
                for (index = 0; index < setsToCheck.length; index++) {
                    if (!setsToCheck[index].contains(name)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    sharedNamesList.add(name);
                }
            }
        }
        // so now we have all the names common to the shared and the lists to permute, an overall set of shared names for the permutation, this sharedNamesList will be smaller the more permute criteria there are
        System.out.println("time for building shared " + nf.format(System.currentTimeMillis() - startTime));
        startTime = System.currentTimeMillis();
        //this is the part that takes a long time...
        Set<List<Name>> foundCombinations;
        int comboSize = permuteNames.size();
        int size = sharedNamesList.size();
        // essentially what we're going to do now is go through every name in the shared pool and for each of them run memberName against the headings.
        // member name against the headings is saying "which of this heading's direct children is this name related to?". We know that anything in shared WILL have the names in listToPermute in its parents
        // of course there will be much duplication, hence use a set, foundCombinations
        if (size > 100_000) {// arbitrary cut off for multi threading, this multi threading helps, no question
            System.out.println("multi threading the combo resolution");
            foundCombinations = Collections.newSetFromMap(new ConcurrentHashMap<>()); // has to be concurrent for multi threading!
            ExecutorService executor = ThreadPools.getMainThreadPool();
            final int divider = 50; // a bit arbitrary perhaps
            List<Future<Void>> tasks = new ArrayList<>(50);
            for (int i = 0; i < divider; i++) {
                tasks.add(executor.submit(new ComboBuilder(comboSize, permuteNames, (i * size / divider), ((i + 1) * size / divider), foundCombinations, sharedNamesList)));
            }
            // then make sure all are executed and square up the memory - Future.get means a happens before for the multi threaded stuff
            for (Future<Void> task : tasks) {
                task.get();
            }
        } else { // just single thread
            foundCombinations = HashObjSets.newMutableSet();
            for (Name name : sharedNamesList) {
                List<Name> foundCombination = new ArrayList<>(comboSize);
                for (Name pName : permuteNames) {
                    foundCombination.add(Name.memberName(name, pName));
                }
                foundCombinations.add(foundCombination);
            }
        }
        System.out.println("time for getting combinations " + nf.format((System.currentTimeMillis() - startTime)));
        startTime = System.currentTimeMillis();
        // this is running through each of the name's children (the categories we're permuting) assigning a "natural" position
        // it's created out here as sortCombos is recursive hence we don't want to recreate it each time
        List<Map<Name, Integer>> sortLists = new ArrayList<>();
        for (Name permuteName : permuteNames) {
            Map<Name, Integer> sortList = new HashMap<>();
            Collection<Name> children = permuteName.getChildren();
            if (permuteNamesThatNeedSorting.contains(permuteName)) { // then new code to sort. Override the "natural" sorting if it exists
                List<Name> sorted = new ArrayList<>(children);
                NameService.sortCaseInsensitive(sorted);
                children = sorted;
            }
            int sortPos = 0;
            for (Name name : children) {
                sortList.put(name, sortPos++);
            }
            sortLists.add(sortList);
        }
        List<List<DataRegionHeading>> permuted = sortCombos(listToPermute, foundCombinations, 0, sortLists, noPermuteTotals);
        System.out.println("Headings sorted in " + nf.format((System.currentTimeMillis() - startTime)));
        return permuted;
    }

    /*

    Edd pasted stuff, might be ditched

    This is called after the names are loaded by createHeadingArraysFromSpreadsheetRegion. in the case of columns it is transposed first

    The dynamic name calls e.g. Seaports; children; have their lists populated but they have not been expanded out into the 2d set itself

    So in the case of row headings for the export example it's passed a list or 1 (the outermost list of row headings defined)

    and this 1 has a list of 2, the two cells defined in row headings as seaports; children     container;children

    We want to return a list<list> being row, cells in that row, the expansion of each of the rows. The source in my example being ony one row with 2 cells in it
    those two cells holding lists of 96 and 4 hence we get 384 back, each with 2 cells.

    That logic just described is the bulk of the function I think. Permutation is handed off to get2DPermutationOfLists, then those permuted lists are simply stacked together.

    Note! the permute FUNCTION is a different from standard permute in that it takes a list of names combined with the context and then only shows permutations that actually have data

    This now needs to support a more advanced structure. So, given the definitions on the left it should now look like the first permutation rather the second as it was (pasted from WFC spec)

    Definition                                  Results we want                             How it was rendering

    Set1	Item1	    Item 2                  S11	    Item 1	    Item 2                  S11	    Item1	    Item 2
            Set 2	    Item 3                  	    S21	        Item 3                  S12	    Item1	    Item 2
                        Item4                   	    	        Item 4                  S13	    Item1	    Item 2
                                                	    S22 	    Item 3                  	    S22 	    Item 3
            Set 5	    Item 5                  	    	        Item 4                  	    	        Item 4
                        Item6                   	    S23	        Item 3                  	    S23	        Item 3
    Item 7	Item 98	    Item 9                  	    	        Item 4                  	    	        Item 4
                                                	    S51	        Item5                   	    S51	        Item5
                                                	    	        Item 6                  	    	        Item 6
                                                	    S52	        Item 5                  	    S52	        Item 5
                                                	    	        Item 6                  	    	        Item 6
                                                S12	    Item 1	    Item 2                  Item 7	Item 98	    Item 9
                                                	    S21	        Item 3
                                                	    	        Item 4
                                                	    S22 	    Item 3
                                                	    	        Item 4
                                                	    S23	        Item 3
                                                	    	        Item 4
                                                	    S51	        Item5
                                                	    	        Item 6
                                                	    S52	        Item 5
                                                	    	        Item 6
                                                S13	    Item 1	    Item 2
                                                	    S21	        Item 3
                                                	    	        Item 4
                                                	    S22 	    Item 3
                                                	    	        Item 4
                                                	    S23	        Item 3
                                                	    	        Item 4
                                                	    S51	        Item5
                                                	    	        Item 6
                                                	    S52	        Item 5
                                                	    	        Item 6
                                                Item 7	Item 98	    Item 9

    The old code supported this kind of logic on the last column as a bit of a hack but now we want structure to permute correctly.
    One can NOT just compress each column and then permute the lot, the structure will be lost. We'll probably need a new object
    that holds the headings represented as a tree structure that can be resolved. The input is still the same - a 2d List of lists
    representing the cells that are the heading definitions. A list per cell since it may be a Name query rather than a reference
    to a name.

    Note : should the example from WFC include spaces? I think perhaps it should. Deal with spaces in a bit.

    The Tree object will have a list or names and a list of Tree objects I think

     */

    /* commenting for the mo . . .

    private static class HeadingTreeNode {

        public final List<DataRegionHeading> headings;
        public final List<HeadingTreeNode> childNodes;

        public HeadingTreeNode(List<DataRegionHeading> headings, List<HeadingTreeNode> childNodes) {
            this.headings = headings;
            this.childNodes = childNodes;
        }
    }

    static List<List<DataRegionHeading>> newExpandHeadings(final List<List<List<DataRegionHeading>>> headingLists, Collection<Name> sharedNames) throws Exception {
        final int noOfHeadingDefinitionRows = headingLists.size();
        if (noOfHeadingDefinitionRows == 0) {
            return new ArrayList<>();
        }
        List<List<DataRegionHeading>> output = new ArrayList<>();
        // first job needs to be packaging up the headings
        List<HeadingTreeNode> headingTreeNodesFromHeadings = getHeadingTreeNodesFromHeadings(headingLists);
        // and now resolve them!
        for (HeadingTreeNode headingTreeNode : headingTreeNodesFromHeadings) {
            expandTreeNode(headingTreeNode, output);
        }
        // todo - what about permute and hasTitles?
        return output;
    }

    // ok the following two functions might be able to be combined. Not worth it for performance. Would it be clearer?
    // Probably not as the combined function would need to take into account the expanded sets. Leaving for the mo.
    public static void expandTreeNode(HeadingTreeNode headingTreeNode, List<List<DataRegionHeading>> headingsSoFar) {
        boolean first = true;
        List<DataRegionHeading> startingRow = headingsSoFar.get(headingsSoFar.size() - 1);
        List<DataRegionHeading> startingRowCopy = new ArrayList<>(startingRow); // copy as it will be modified
        for (DataRegionHeading dataRegionHeading : headingTreeNode.headings) {
            if (first) {
                startingRow.add(dataRegionHeading);
            } else {
                List<DataRegionHeading> newRow = new ArrayList<>(startingRowCopy);
                newRow.add(dataRegionHeading);
                headingsSoFar.add(newRow);
            }
            for (HeadingTreeNode childNode : headingTreeNode.childNodes) {
                expandTreeNode(childNode, headingsSoFar);
            }
            first = false;
        }
    }

    // todo - totally blank lines?
    // package headings according to new tree criteria, this function will be recursive
    public static List<HeadingTreeNode> getHeadingTreeNodesFromHeadings(final List<List<List<DataRegionHeading>>> headingLists) {
        List<HeadingTreeNode> toReturn = new ArrayList<>();
        if (headingLists == null || headingLists.isEmpty()) {
            return toReturn; // null maybe? Not sure there's much advantage to it. There won't be many of these objects.
        }
        List<List<List<DataRegionHeading>>> subList = new ArrayList<>();
        List<DataRegionHeading> currentHeading = null;
        for (List<List<DataRegionHeading>> headingListsRow : headingLists) {
            if (headingListsRow.get(0) != null && !headingListsRow.get(0).isEmpty()) { // then there is something on this line
                if (currentHeading != null) { // so it's not the first
                    toReturn.add(new HeadingTreeNode(currentHeading, getHeadingTreeNodesFromHeadings(subList)));
                    subList = new ArrayList<>();
                }
                currentHeading = headingListsRow.get(0);
            }
            if (headingListsRow.size() > 1) { // can't add to a sub list if there's nothing further to add
                subList.add(headingListsRow.subList(1, headingListsRow.size()));
            }
        }
        if (currentHeading != null) { // catch the last one
            toReturn.add(new HeadingTreeNode(currentHeading, getHeadingTreeNodesFromHeadings(subList)));
        }
        return toReturn;
    }

    */

    static List<List<DataRegionHeading>> permuteHeadings(List<DataRegionHeading> mainHeading, List<List<List<DataRegionHeading>>> subHeadings, Collection<Name> sharedNames, boolean noPermuteTotals) throws Exception {
        List<List<DataRegionHeading>> toReturn = new ArrayList<>();
        List<List<DataRegionHeading>> expandedSubheadings = expandHeadings(subHeadings, sharedNames, noPermuteTotals);
        if (mainHeading != null && mainHeading.size() > 0 && mainHeading.get(0).getFunction() == DataRegionHeading.FUNCTION.PERMUTE) {
            List<List<DataRegionHeading>> permuted = findPermutedItems(sharedNames, mainHeading, noPermuteTotals);
            for (List<DataRegionHeading> permuteLine : permuted) {
                for (List<DataRegionHeading> expandedSubheading : expandedSubheadings) {
                    List<DataRegionHeading> newHeadings = new ArrayList<>();
                    newHeadings.addAll(permuteLine);
                    newHeadings.addAll(expandedSubheading);
                    toReturn.add(newHeadings);
                }
            }
        } else {
            if (mainHeading == null) {
                //pity this is the same code as the lines below - could not work out how to combine them.
                for (List<DataRegionHeading> expandedSubheading : expandedSubheadings) {
                    List<DataRegionHeading> newHeadings = new ArrayList<>();
                    newHeadings.add(null);
                    newHeadings.addAll(expandedSubheading);
                    toReturn.add(newHeadings);
                }
            } else {
                for (DataRegionHeading mainElement : mainHeading) {
                    for (List<DataRegionHeading> expandedSubheading : expandedSubheadings) {
                        List<DataRegionHeading> newHeadings = new ArrayList<>();
                        //if the last element is null, then all headings are null
                        if (expandedSubheading.get(expandedSubheading.size() - 1) == null) {
                            newHeadings.add(null);
                        } else {
                            newHeadings.add(mainElement);
                        }
                        newHeadings.addAll(expandedSubheading);
                        toReturn.add(newHeadings);
                    }
                }
            }
        }
        return toReturn;
    }

    // last 2 params only for permute - a concern?
    static List<List<DataRegionHeading>> expandHeadings(final List<List<List<DataRegionHeading>>> headingLists, Collection<Name> sharedNames, boolean noPermuteTotals) throws Exception {
        List<List<DataRegionHeading>> toReturn = new ArrayList<>();
        if (headingLists.size()==0) return toReturn;
        if (headingLists.get(0).size() > 1) {
            Iterator<List<List<DataRegionHeading>>> it = headingLists.iterator();
            List<List<List<DataRegionHeading>>> subHeadings = null;
            List<DataRegionHeading> mainHeading = null;
            while (it.hasNext()) {
                List<List<DataRegionHeading>> headingRow = it.next();
                if (subHeadings == null || headingRow.get(0) != null) {
                    if (subHeadings != null) {
                        toReturn.addAll(permuteHeadings(mainHeading, subHeadings, sharedNames, noPermuteTotals));
                    }
                    mainHeading = headingRow.get(0);
                    subHeadings = new ArrayList<>();
                }
                List<List<DataRegionHeading>> subHeadingRow = new ArrayList<>();
                Iterator<List<DataRegionHeading>> heading = headingRow.iterator();
                heading.next();
                while (heading.hasNext()) {
                    subHeadingRow.add(heading.next());

                }
                subHeadings.add(subHeadingRow);
            }
            toReturn.addAll(permuteHeadings(mainHeading, subHeadings, sharedNames, noPermuteTotals));
        } else {
            Iterator<List<List<DataRegionHeading>>> headingRowIterator = headingLists.iterator();
            List<DataRegionHeading> nextHeading = null;
            List<List<DataRegionHeading>> headingList = headingRowIterator.next();
            if (headingList!=null&& headingList.size()> 0 ){
                nextHeading = headingList.get(0);
            }
            if (nextHeading != null && nextHeading.size() > 0 && nextHeading.get(0).getFunction() == DataRegionHeading.FUNCTION.PERMUTE) { // if the first one is permute we assume the lot are
                toReturn.addAll(findPermutedItems(sharedNames, nextHeading, noPermuteTotals));//assumes only one row of headings, it's a list of the permute names
            } else {        //last column - simply expand, filling spaces where available.
                List<DataRegionHeading> heading = null;
                boolean workToDo = true;
                while (headingRowIterator.hasNext() || workToDo) {
                    heading = nextHeading;
                    if (headingRowIterator.hasNext()) {
                        nextHeading = headingRowIterator.next().get(0);
                    } else {
                        nextHeading = null;
                        workToDo = false;
                    }
                    if (heading != null) {
                        int count = 0;
                        for (DataRegionHeading headingElement : heading) {
                            List<DataRegionHeading> newRow = new ArrayList<>();
                            newRow.add(headingElement);
                            toReturn.add(newRow);
                            if (count++ > 0 && nextHeading == null) {
                                if (headingRowIterator.hasNext()) {
                                    nextHeading = headingRowIterator.next().get(0);
                                } else {
                                    workToDo = false;
                                }
                            }
                        }
                    } else {
                        List<DataRegionHeading> newRow = new ArrayList<>();
                        newRow.add(null);
                        toReturn.add(newRow);
                    }
                }
            }
        }
        return toReturn;
    }

    // return headings as strings for display, I'm going to put blanks in here if null. Called after permuting/expanding
    static List<List<String>> convertDataRegionHeadingsToStrings(List<List<DataRegionHeading>> source, String user) {
        // first I need to check max offsets for each column - need to check on whether the 2d arrays are the same orientation for row or column headings or not todo
        List<Integer> maxColOffsets = new ArrayList<>();
        for (List<DataRegionHeading> row : source) {
            if (maxColOffsets.isEmpty()) {
                for (DataRegionHeading ignored : row) {
                    maxColOffsets.add(0);
                }
            }
            int index = 0;
            for (DataRegionHeading heading : row) {
                if (heading != null && heading.getOffsetHeadings() != null && maxColOffsets.get(index) < heading.getOffsetHeadings().size()) {
                    maxColOffsets.set(index, heading.getOffsetHeadings().size());
                }
                index++;
            }
        }
        int extraColsFromOffsets = 0; // need to know this before making each row
        for (int offset : maxColOffsets) {
            extraColsFromOffsets += offset;
        }
        // note, custom languages - the wrong way around!
        List<String> languages = new ArrayList<>();
        languages.add(StringLiterals.DEFAULT_DISPLAY_NAME);//for displaying headings always look for DEFAULT_DISPLAY_NAME first - otherwise may look up the chain for local names
        languages.add(user);
        List<List<String>> toReturn = new ArrayList<>(source.size());
        for (List<DataRegionHeading> row : source) {
            List<String> returnRow = new ArrayList<>(row.size() + extraColsFromOffsets);
            toReturn.add(returnRow);
            int colIndex = 0;
            for (DataRegionHeading heading : row) {
                String cellValue = null;
                if (heading != null) {
                    if (heading.getOffsetHeadings() != null) {
                        for (DataRegionHeading offsetHeading : heading.getOffsetHeadings()) {
                            String offsetString = "";
                            for (String language : languages) {
                                if (offsetHeading.getName().getAttribute(language) != null) {
                                    offsetString = offsetHeading.getName().getAttribute(language);
                                    break;
                                }
                                // used to check default display name here but that's redundant, it's set above
                            }
                            returnRow.add(offsetString);
                        }
                    }
                    Name name = heading.getName();
                    if (name != null) {
                        for (String language : languages) {
                            if (name.getAttribute(language) != null) {
                                cellValue = name.getAttribute(language);
                                break;
                            }
                            // used to check default display name here but that's redundant, it's set above
                        }
                    } else {
                        cellValue = heading.getAttribute();
                    }
                }
                returnRow.add(cellValue != null ? cellValue : "");
                // can index out of bounde here with duff inputs better error? todo
                try {
                    int extraColsForThisRow = maxColOffsets.get(colIndex);
                    for (int i = 0; i < extraColsForThisRow - (heading != null && heading.getOffsetHeadings() != null ? heading.getOffsetHeadings().size() : 0); i++) {
                        returnRow.add("");
                    }
                }catch(Exception e){
                    //spurious heading with function PERMUTE
                }
                colIndex++;
            }
        }
        return toReturn;
    }

    // new logic, derive the headings from the data, so after sorting the data one can get headings for each cell rather than pushing the sort over to the headings as well
    static List<List<DataRegionHeading>> getColumnHeadingsAsArray(List<List<AzquoCell>> cellArray) {
        List<List<DataRegionHeading>> toReturn = new ArrayList<>(cellArray.get(0).size());
        for (AzquoCell cell : cellArray.get(0)) {
            toReturn.add(cell.getColumnHeadings());
        }
        return MultidimensionalListUtils.transpose2DList(toReturn);
    }

    // new logic, derive the headings from the data, no need to resort to resorting etc.
    static List<List<DataRegionHeading>> getRowHeadingsAsArray(List<List<AzquoCell>> cellArray) {
        List<List<DataRegionHeading>> toReturn = new ArrayList<>(cellArray.size()); // might not use all of it but I'm a little confused as to why the row would be empty?
        for (List<AzquoCell> row : cellArray) {
            if (!row.isEmpty()) { // this check . . think for some unusual situations
                toReturn.add(row.get(0).getRowHeadings());
            }
        }
        return toReturn;
    }

    static List<DataRegionHeading> getContextHeadings(AzquoMemoryDBConnection azquoMemoryDBConnection, List<List<String>> contextSource, List<String> languages) throws Exception {
        final List<List<List<DataRegionHeading>>> contextArraysFromSpreadsheetRegion = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, contextSource, languages, null, false); // no default suffix, this is where we might find it. DOn't surpress errors onh context!
        final List<DataRegionHeading> contextHeadings = new ArrayList<>();
        for (List<List<DataRegionHeading>> list1 : contextArraysFromSpreadsheetRegion) {
            for (List<DataRegionHeading> list2 : list1) {
                if (list2 != null) { // seems it can be
                    contextHeadings.addAll(list2);
                }
            }
        }
        return contextHeadings;
    }
    private static List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, DataRegionHeading.FUNCTION function, DataRegionHeading.SUFFIX suffix, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter) {
        return dataRegionHeadingsFromNames(names, function, suffix,offsetHeadings, valueFunctionSet, doubleParameter, null);
    }




        private static List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, DataRegionHeading.FUNCTION function, DataRegionHeading.SUFFIX suffix, List<DataRegionHeading> offsetHeadings, Collection<Name> valueFunctionSet, double doubleParameter, String description) {
        List<DataRegionHeading> dataRegionHeadings = new ArrayList<>(names.size()); // names could be big, init the Collection with the right size
      /*
        if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()) {
            // then check permissions
            for (Name name : names) {
                // will the new write permissions cause an overhead?
                dataRegionHeadings.add(new DataRegionHeading(name, NameQueryParser.isAllowed(name, azquoMemoryDBConnection.getWritePermissions()), function, suffix, null, offsetHeadings, valueFunctionSet, doubleParameter));
            }
        } else { // don't bother checking permissions, write permissions to true
        */
        for (Name name : names) {
            dataRegionHeadings.add(new DataRegionHeading(name, true, function, suffix, description, offsetHeadings, valueFunctionSet, doubleParameter));
        }
        //}
        //System.out.println("time for dataRegionHeadingsFromNames " + (System.currentTimeMillis() - startTime));
        return dataRegionHeadings;
    }

    static List<Name> namesFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        // ok some of the data region headings may be attribute, no real harm I don't think VS a whacking great set which would always be names
        List<Name> names = new ArrayList<>(dataRegionHeadings.size()); // switching back to list, now I consider I'm not sure if sets help much here and I want ordering
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading != null && dataRegionHeading.getName() != null) {
                names.add(dataRegionHeading.getName());
            }
        }
        return names;
    }

    static List<String> calcsFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        // ok some of the data region headings may be attribute, no real harm I don't think VS a whacking great set which would always be names
        List<String> calcs = new ArrayList<>(dataRegionHeadings.size()); // switching back to list, now I consider I'm not sure if sets help much here and I want ordering
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading != null && dataRegionHeading.getCalculation() != null) {
                calcs.add(dataRegionHeading.getCalculation());
            }
        }
        return calcs;
    }
    static Set<String> attributesFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        Set<String> names = HashObjSets.newMutableSet(); // attributes won't ever be big as they're manually defined, leave this to default (16 size table internally?)
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getAttribute() != null) {
                names.add(dataRegionHeading.getAttribute().substring(1)); // at the mo I assume attributes begin with .
            }
        }
        return names;
    }

    static boolean headingsHaveAttributes(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading != null && dataRegionHeading.getAttribute() != null) {
                return true;
            }
        }
        return false;
    }
}