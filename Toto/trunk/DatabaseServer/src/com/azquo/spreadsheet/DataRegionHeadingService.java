package com.azquo.spreadsheet;

import com.azquo.StringLiterals;
import com.azquo.ThreadPools;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
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
                if (sourceCell == null || sourceCell.length() == 0) {
                    row.add(null);
                } else {
                    // was just a name expression, now we allow an attribute also. May be more in future.
                    if (sourceCell.startsWith(".")) { //
                        // currently only one attribute per cell, I suppose it could be many in future (available attributes for a name, a list maybe?)
                        // do NOT use singleton list as nice as the code looks! The list may be modified later . . .
                        List<DataRegionHeading> single = new ArrayList<>();
                        single.add(new DataRegionHeading(sourceCell, true));// we say that an attribute heading defaults to writable, it will defer to the name
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
                        /* The way name functions work is going to change to allow [ROWHEADING] and [COLUMNHEADING] which work with set operators, / * - + etc.
                           This means that in the case of name functions the heading can't cache sets, it needs to evaluate the formulae on each line and it means that each
                           heading needs to have its description populated even if it's a simple name. Caching of heading will be broken, this would slow down Damart (which will be broken anyway due to changing syntax)
                           for example but that's not such a concern right now. Later when evaluating cells it will look for a function in the row heading first then the column heading
                           and it will evaluate the first it finds taking into account [ROWHEADING] and [COLUMNHEADING], it can't evaluate both. And [ROWHEADING] means the first row heading
                           if there are more than one we may later allow [ROWHEADING1], [ROWHEADING2] etc.
                           */
                        if (DataRegionHeading.isNameFunction(function)) { // then just set the description to be resolved later
                            List<DataRegionHeading> forFunction = new ArrayList<>();
                            forFunction.add(new DataRegionHeading(null, false, function, suffix, sourceCell, null)); // in this case the heading is just a placeholder for the formula to be evaluated later - that forumla being held in the description of the heading
                            row.add(forFunction);
                            // ok this is the kind of thing that would typically be in name service where queries are parsed but it needs to set some formatting info (visually indentation) so
                            // it will be in here with limited parsing support e.g. `All customers` hierarchy 3 that is to say name, hierarchy, number
                        } else if (sourceCell.toLowerCase().contains(StringLiterals.HIERARCHY)) { // kind of a special case, supports a name not an expression
                            String name = sourceCell.substring(0, sourceCell.toLowerCase().indexOf(StringLiterals.HIERARCHY)).replace("`", "").trim();
                            int level = Integer.parseInt(sourceCell.substring(sourceCell.toLowerCase().indexOf(StringLiterals.HIERARCHY) + StringLiterals.HIERARCHY.length()).trim()); // fragile?
                            Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, name, attributeNames); // should return one
                            if (!names.isEmpty()) {// it should be just one
                                List<DataRegionHeading> hierarchyList = new ArrayList<>();
                                List<DataRegionHeading> offsetHeadings = dataRegionHeadingsFromNames(names, azquoMemoryDBConnection, function, suffix, null, null); // I assume this will be only one!
                                resolveHierarchyForHeading(azquoMemoryDBConnection, offsetHeadings.get(0), hierarchyList, function, suffix, new ArrayList<>(), level);
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
                                Name pName = NameService.findByName(azquoMemoryDBConnection, "az_" + permutedName.replace("`", "").trim(), attributeNames);
                                // if no set chosen, find the original set
                                if (pName == null || pName.getChildren().size() == 0) {
                                    pName = NameService.findByName(azquoMemoryDBConnection, permutedName);
                                }
                                // ok now we are using sorted heare we can't use dataRegionHeadingsFromNames, this is similar to the check internally
                                // I'm going to jam sorted in the description, hopefully won't be a problem
                                if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()) {
                                    // will the new write permissions cause an overhead?
                                    headings.add(new DataRegionHeading(pName, NameQueryParser.isAllowed(pName, azquoMemoryDBConnection.getWritePermissions()), function, suffix, sorted ? "sorted" : null, null, null));
                                } else { // don't bother checking permissions, write permissions to true
                                    headings.add(new DataRegionHeading(pName, true, function, suffix, sorted ? "sorted" : null, null, null));
                                }
                            }
                            row.clear();
                            row.add(headings);
                        } else {// most of the time it will be a vanilla query, there may be value functions that will be dealt with later
                            Collection<Name> names;
                            try {
                                names = NameQueryParser.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames);
                            } catch (Exception e) {
                                if (ignoreHeadingErrors) { // the ignore is only for vanilla queries, functions probably should error regardless
                                    names = new ArrayList<>();
                                } else {
                                    throw e;
                                }
                            }
                            if (namesQueryLimit > 0 && names.size() > namesQueryLimit) {
                                throw new Exception("While creating headings " + sourceCell + " resulted in " + names.size() + " names, more than the specified limit of " + namesQueryLimit);
                            }
                            row.add(dataRegionHeadingsFromNames(names, azquoMemoryDBConnection, function, suffix, null, null));

                        }
                    }
                }
            }
        }
        return nameLists;
    }

    // recursive, the key is to add the offset to allow formatting of the hierarchy
    private static void resolveHierarchyForHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, DataRegionHeading heading, List<DataRegionHeading> target
            , DataRegionHeading.FUNCTION function, DataRegionHeading.SUFFIX suffix, List<DataRegionHeading> offsetHeadings, int levelLimit) {
        if (offsetHeadings.size() < levelLimit) {// then get the children
            List<DataRegionHeading> offsetHeadingsCopy = new ArrayList<>(offsetHeadings);
            offsetHeadingsCopy.add(heading);
            for (DataRegionHeading child : dataRegionHeadingsFromNames(heading.getName().getChildren(), azquoMemoryDBConnection, function, suffix, offsetHeadingsCopy, null)) {
                resolveHierarchyForHeading(azquoMemoryDBConnection, child, target, function, suffix, offsetHeadingsCopy, levelLimit);
            }
        }
        target.add(heading); // the "parent" is added after
    }

    /* Created by WFC to sort pivot table permuted headings, commented/modified a little by EFC
    List to permute is the names from the permute function, found combinations is from the permute function, sort lists is the "natural ordering" of each of the lists of headings we het from the children
    in listToPermute. Hence the outside list size of listToPermute and sortLists should be the same
    A comparator might provide a more succinct sort (we'd need to pass sortLists still), would need to address how the totals were added after. Something to consider.
    */

    private static List<List<DataRegionHeading>> sortCombos(List<DataRegionHeading> listToPermute, Set<List<Name>> foundCombinations, int position, final List<Map<Name, Integer>> sortLists) {
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
        for (int key : sortMap.keySet()) { // so now we run through the groups of sorted names
            if (position == sortLists.size()) { // we're on the last one
                // according to the logic of the function I'd be very surprised if this ever had more than one combo at this point as it should be
                // sorted on the last and there are no duplicate foundCombinations
                for (List<Name> entry : sortMap.get(key)) {
                    List<DataRegionHeading> drhEntry = new ArrayList<>();
                    // now for that combo build the headings
                    for (Name name : entry) {
                        drhEntry.add(new DataRegionHeading(name, true, null, null, null, null));
                    }
                    toReturn.add(drhEntry);
                }
                // if it's not the last grab this clump of combos and run them through this function again to sort on the next level down
            } else {
                toReturn.addAll(sortCombos(listToPermute, sortMap.get(key), position, sortLists));
            }
        }
        // now add totals - remember this is recursive - this will happen leaving each level
        if (toReturn.size() > 0) {//maybe should be 1 - no need for totals if there's only one item
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

    private static List<List<DataRegionHeading>> findPermutedItems(final Collection<Name> sharedNames, final List<List<DataRegionHeading>> headingRow) throws Exception {
        NumberFormat nf = NumberFormat.getInstance();
        List<DataRegionHeading> listToPermute = headingRow.get(0);
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
            assert smallestNameSet != null;
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
        List<List<DataRegionHeading>> permuted = sortCombos(listToPermute, foundCombinations, 0, sortLists);
        for (int i = 1; i < headingRow.size(); i++) {
            permuted = MultidimensionalListUtils.get2DArrayWithAddedPermutation(permuted, headingRow.get(i));
        }
        System.out.println("Headings sorted in " + nf.format((System.currentTimeMillis() - startTime)));
        return permuted;
    }

    /*
    This is called after the names are loaded by createHeadingArraysFromSpreadsheetRegion. in the case of columns it is transposed first

    The dynamic name calls e.g. Seaports; children; have their lists populated but they have not been expanded out into the 2d set itself

    So in the case of row headings for the export example it's passed a list or 1 (the outermost list of row headings defined)

    and this 1 has a list of 2, the two cells defined in row headings as seaports; children     container;children

    We want to return a list<list> being row, cells in that row, the expansion of each of the rows. The source in my example being ony one row with 2 cells in it
    those two cells holding lists of 96 and 4 hence we get 384 back, each with 2 cells.

    Heading definitions are a region if this region is more than on column wide then rows which have only one cell populated
    and that cell is the right most cell then that cell is added to the cell above it, it becomes part of that permutation.
    I renamed the function headingDefinitionRowHasOnlyTheRightCellPopulated to help make this clear.

    That logic just described is the bulk of the function I think. Permutation is handed off to get2DPermutationOfLists, then those permuted lists are simply stacked together.

    Note! the permute FUNCTION is a different from standard permute in that it takes a list of names combined with the context and then only shows permutations that actually have data

     */

    static List<List<DataRegionHeading>> expandHeadings(final List<List<List<DataRegionHeading>>> headingLists, Collection<Name> sharedNames) throws Exception {
        final int noOfHeadingDefinitionRows = headingLists.size();
        if (noOfHeadingDefinitionRows == 0) {
            return new ArrayList<>();
        }
        final int lastHeadingDefinitionCellIndex = headingLists.get(0).size() - 1; // the headingLists will be square, that is to say all row lists the same length as prepared by createHeadingArraysFromSpreadsheetRegion
        // ok here's the logic, what's passed is a 2d array of lists, as created from createHeadingArraysFromSpreadsheetRegion
        // we would just run through the rows running a 2d permutation on each row BUT there's a rule that if there's
        // a row below blank except the right most one then add that right most to the one above
        boolean starting = true;
        // ok the reason I'm doing this is to avoid unnecessary array copying and resizing. If the size is 1 can just return that otherwise create an ArrayList of the right size and copy in
        ArrayList<List<List<DataRegionHeading>>> permutedLists = new ArrayList<>(noOfHeadingDefinitionRows); // could be slightly less elements than this but it's unlikely to be a biggy.
        List<List<DataRegionHeading>> lastHeadingRow = null;
        for (int headingDefinitionRowIndex = 0; headingDefinitionRowIndex < noOfHeadingDefinitionRows; headingDefinitionRowIndex++) { // not using a vanilla for loop as we want to skip forward to allow folding of rows with one cell on the right into the list above
            List<List<DataRegionHeading>> headingDefinitionRow = headingLists.get(headingDefinitionRowIndex);
            if (lastHeadingRow != null) {
                //copy down titles
                for (int i = 0; i < headingDefinitionRow.size(); i++) {
                    if (headingDefinitionRow.get(i) == null && lastHeadingRow.get(i) != null) {
                        headingDefinitionRow.set(i, lastHeadingRow.get(i));
                    }
                }
            }

            if (headingDefinitionRow.size() == 0) {
                headingDefinitionRow.add(null);
            }
            if (headingDefinitionRow.get(lastHeadingDefinitionCellIndex) == null) {
                headingDefinitionRow.set(lastHeadingDefinitionCellIndex, new ArrayList<>());
                headingDefinitionRow.get(lastHeadingDefinitionCellIndex).add(null);
            }
            int startCount = headingDefinitionRow.get(lastHeadingDefinitionCellIndex).size() - 1;
            if (lastHeadingDefinitionCellIndex > 0 && !headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex)))
                starting = false;// Don't permute until you have something to permute!
            while (!starting && headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 // we're not on the last row
                    && headingLists.get(headingDefinitionRowIndex + 1).size() > 1 // the next row is not a single cell (will all rows be the same length?)
                    && headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex + 1)) // and the last cell is the only not null one
                    ) {
                // add the single last cell from the next row to the end of this row
                if (headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex) == null) { // note if it's completely empty headingDefinitionRowHasOnlyTheRightCellPopulated would return true, not sure if this logic is completely correct, left over from the old code, I think having nulls in the permuations is allowed
                    if (startCount-- <= 0) {
                        headingDefinitionRow.get(lastHeadingDefinitionCellIndex).add(null);
                    }
                } else {
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                    startCount = headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex).size() - 1;
                }
                headingDefinitionRowIndex++;
            }
            if (headingDefinitionRow.get(0) != null && headingDefinitionRow.get(0).size() > 0 && headingDefinitionRow.get(0).get(0) != null && headingDefinitionRow.get(0).get(0).getFunction() == DataRegionHeading.FUNCTION.PERMUTE) { // if the first one is permute we assume the lot are
                List<List<DataRegionHeading>> permuted = findPermutedItems(sharedNames, headingDefinitionRow);//assumes only one row of headings, it's a list of the permute names
                permutedLists.add(permuted);
            } else {
                List<List<DataRegionHeading>> permuted = MultidimensionalListUtils.get2DPermutationOfLists(headingDefinitionRow);
                permutedLists.add(permuted);
                if (lastHeadingDefinitionCellIndex == 0) {
                    int spaceNeeded = permuted.size() - 1;
                    while (spaceNeeded-- > 0 && headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 && headingLists.get(headingDefinitionRowIndex + 1).get(0) == null) {
                        headingDefinitionRowIndex++;
                    }
                }

            }
            lastHeadingRow = headingDefinitionRow;
        }
        if (permutedLists.size() == 1) { // it was just one row to permute, return it as is rather than combining the permuted results together which might result in a bit of garbage due to array copying
            return permutedLists.get(0);
        }
        // the point is I only want to make a new ArrayList if there are lists to be combined and then I want it to be the right size. Some reports have had millions in here . . .
        int size = 0;
        for (List<List<DataRegionHeading>> permuted : permutedLists) {
            size += permuted.size();
        }
        List<List<DataRegionHeading>> output = new ArrayList<>(size);
        permutedLists.forEach(output::addAll);
        return output;
    }

    // what we're saying is it's only got one cell in the heading definition filled and it's the last one.
    private static boolean headingDefinitionRowHasOnlyTheRightCellPopulated(List<List<DataRegionHeading>> headingLists) {
        int numberOfCellsInThisHeadingDefinition = headingLists.size();
        for (int cellIndex = 0; cellIndex < numberOfCellsInThisHeadingDefinition; cellIndex++) {
            if (headingLists.get(cellIndex) != null) {
                return cellIndex == numberOfCellsInThisHeadingDefinition - 1; // if the first to trip the not null is the last in the row then true!
            }
        }
        return true; // All null - treat as last cell populated - is this logical?
    }

    // return headings as strings for display, I'm going to put blanks in here if null. Called after permuting/expanding
    static List<List<String>> convertDataRegionHeadingsToStrings(List<List<DataRegionHeading>> source, List<String> languagesSent) {
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

        List<String> languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);//for displaying headings always look for DEFAULT_DISPLAY_NAME first - otherwise may look up the chain for local names
        languages.addAll(languagesSent);
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
                int extraColsForThisRow = maxColOffsets.get(colIndex);
                for (int i = 0; i < extraColsForThisRow - (heading != null && heading.getOffsetHeadings() != null ? heading.getOffsetHeadings().size() : 0); i++) {
                    returnRow.add("");
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

    private static List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, AzquoMemoryDBConnection azquoMemoryDBConnection, DataRegionHeading.FUNCTION function, DataRegionHeading.SUFFIX suffix, List<DataRegionHeading> offsetHeadings, Set<Name> valueFunctionSet) {
        List<DataRegionHeading> dataRegionHeadings = new ArrayList<>(names.size()); // names could be big, init the Collection with the right size
        if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()) {
            // then check permissions
            for (Name name : names) {
                // will the new write permissions cause an overhead?
                dataRegionHeadings.add(new DataRegionHeading(name, NameQueryParser.isAllowed(name, azquoMemoryDBConnection.getWritePermissions()), function, suffix, null, offsetHeadings, valueFunctionSet));
            }
        } else { // don't bother checking permissions, write permissions to true
            for (Name name : names) {
                dataRegionHeadings.add(new DataRegionHeading(name, true, function, suffix, null, offsetHeadings, valueFunctionSet));
            }
        }
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