package com.azquo.spreadsheet;

import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.*;
import com.azquo.memorydb.service.MutableBoolean;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.view.CellForDisplay;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.FilterTriple;
import net.openhft.koloboke.collect.map.hash.HashIntDoubleMaps;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
/*
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created as a result of the report server/database server split
 *
 * todo : try to address proper use of protected and private given how I've shifted lots of classes around. This could apply to all sorts in the system.
 */

public class DSSpreadsheetService {

    private final Runtime runtime = Runtime.getRuntime();

    private final int COL_HEADINGS_NAME_QUERY_LIMIT = 500;

    private static final Logger logger = Logger.getLogger(DSSpreadsheetService.class);

    @Autowired
    NameService nameService;
    @Autowired
    ValueService valueService;
    @Autowired
    MemoryDBManager memoryDBManager;
    @Autowired
    DSImportService importService;

    // todo, clean this up when sessions are expired, maybe a last accessed time?
    private final Map<String, StringBuffer> sessionLogs;

    public DSSpreadsheetService() {
        String thost = ""; // Currently just to put in the log
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // Not exactly sure what it might be
        }
        System.out.println("host : " + thost);
        sessionLogs = new ConcurrentHashMap<>(); // may as well make concurrent to be safe.
    }

    // after some thinking trim this down to the basics. Would have just been a DB name for that server but need permissions too.
    // may cache in future to save DB/Permission lookups. Depends on how consolidated client/server calls can be made . . .

    public AzquoMemoryDBConnection getConnectionFromAccessToken(DatabaseAccessToken databaseAccessToken) throws Exception {
        // todo - address opendb count (do we care?) and exceptions
        StringBuffer sessionLog = sessionLogs.computeIfAbsent(databaseAccessToken.getUserSessionId(), t -> new StringBuffer()); // computeIfAbsent is such a wonderful thread safe call
        AzquoMemoryDB memoryDB = memoryDBManager.getAzquoMemoryDB(databaseAccessToken.getPersistenceName(), sessionLog);
        // we can't do the lookup for permissions out here as it requires the connection, hence pass things through
        return new AzquoMemoryDBConnection(memoryDB, databaseAccessToken, nameService, databaseAccessToken.getLanguages(), sessionLog);
    }

    public String getSessionLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        StringBuffer log = sessionLogs.get(databaseAccessToken.getUserSessionId());
        if (log != null) {
            return log.toString();
        }
        return "";
    }

    /*

    This function now takes a 2d array representing the excel region. It expects blank or null for empty cells (the old text paste didn't support this)
    more specifically : the outermost list is of rows, the second list is each cell in that row and the final list is a list not a heading
    because there could be multiple names/attributes in a cell if the cell has something like container;children. Interpretnames is what does this

    hence

    seaports;children   container;children

    for example returns a list of 1 as there's only 1 row passed, with a list of 2 as there's two cells in that row with lists of something like 90 and 3 names in the two cell list items

    we're not permuting, just saying "here is what that 2d excel region looks like in DataRegionHeadings"

    */

    private List<List<List<DataRegionHeading>>> createHeadingArraysFromSpreadsheetRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<List<String>> headingRegion, List<String> attributeNames) throws Exception {
        return createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, headingRegion, attributeNames, 0);
    }

    // now has the option to exception based on large sets being returned by parse query. Used currently on columns, if these name sets are more than a few hundred then that's clearly unworkable - you wouldn't want more than a few hundred columns
    private static final String HIERARCHY = "hierarchy";

    private List<List<List<DataRegionHeading>>> createHeadingArraysFromSpreadsheetRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<List<String>> headingRegion, List<String> attributeNames, int namesQueryLimit) throws Exception {
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
                        DataRegionHeading.FUNCTION function = null;// that's sum practically speaking
                        // now allow functions
                        for (DataRegionHeading.FUNCTION _function : DataRegionHeading.FUNCTION.values()) {
                            if (sourceCell.toUpperCase().startsWith(_function.name() + "(")) { // function name bracket no sapce
                                function = _function;
                                sourceCell = sourceCell.substring(sourceCell.indexOf("(") + 1, sourceCell.trim().length() - 1);// +1 - 1 to get rid of the brackets
                            }
                        }
                        /* The way name functions work is going to change to allow [ROWHEADING] and [COLUMNHEADING] which work with set operators, / * - + etc.
                           This means that in the case of name functions the heading can't cache sets, it needs to evaluate the formulae on each line and it means that each
                           heading needs to have its description populated even if it's a simple name. Caching of heading will be broken, this would slow down Damart (which will be broken anyway due to changing syntax)
                           for example but that's not such a concern right now. Later when evaluating cells it will look for a function in the row heading first then the column heading
                           and it will evaluate the first it finds taking into account [ROWHEADING] and [COLUMNHEADING], it can't evaluate both. And [ROWHEADING] means the first row heading
                           if there are more than one we may later allow [ROWHEADING1], [ROWHEADING2] etc. Thus a fair bit of code has been chopped out of here to be resolved later.
                           */
                        if (DataRegionHeading.isNameFunction(function)) { // then just set the description to be resolved later
                            List<DataRegionHeading> forFunction = new ArrayList<>();
                            forFunction.add(new DataRegionHeading(null, false, function, sourceCell, null)); // in this case the heading is just a placeholder for the formula to be evaluated later - that forumla being held in the description of the heading
                            row.add(forFunction);
                            // ok this is the kind of thing that would typically be in name service but it needs to set some formatting info (visually indentation) so
                            // it will be in here with limited parsing support e.g. `All customers` hierarchy 3 that is to say name, hierarchy, number
                        } else if (sourceCell.toLowerCase().contains(HIERARCHY)) { // kind of a special case, supports a name not an expression
                            String name = sourceCell.substring(0, sourceCell.toLowerCase().indexOf(HIERARCHY)).replace("`", "").trim();
                            int level = Integer.parseInt(sourceCell.substring(sourceCell.toLowerCase().indexOf(HIERARCHY) + HIERARCHY.length()).trim()); // fragile?
                            Collection<Name> names = nameService.parseQuery(azquoMemoryDBConnection, name, attributeNames); // should return one
                            if (!names.isEmpty()) {// it should be just one
                                List<DataRegionHeading> hierarchyList = new ArrayList<>();
                                List<DataRegionHeading> offsetHeadings = dataRegionHeadingsFromNames(names, azquoMemoryDBConnection, function, null, null); // I assume this will be only one!
                                resolveHierarchyForHeading(azquoMemoryDBConnection, offsetHeadings.get(0), hierarchyList, function, new ArrayList<>(), level);
                                if (namesQueryLimit > 0 && hierarchyList.size() > namesQueryLimit) {
                                    throw new Exception("While creating headings " + sourceCell + " resulted in " + hierarchyList.size() + " names, more than the specified limit of " + namesQueryLimit);
                                }
                                row.add(hierarchyList);
                            }
                        } else if (function== DataRegionHeading.FUNCTION.PERMUTE) {
                            String[] permutedNames = sourceCell.split(",");
                            List<Name> permuteNames = new ArrayList<>();
                            for (String permutedName:permutedNames) {
                                //find the set chosen
                                Name pName = nameService.findByName(azquoMemoryDBConnection, "az_" + permutedName.replace("`","").trim());
                                // if no set chosen, find the original set
                                if (pName==null|| pName.getChildren().size()==0){
                                    pName = nameService.findByName(azquoMemoryDBConnection, permutedName);

                                }
                                permuteNames.add(pName);
                            }
                            row.clear();
                            row.add(dataRegionHeadingsFromNames(permuteNames,azquoMemoryDBConnection,function,null,null));

                        }else{// most of the time it will be a vanilla query, there may be value functions that will be dealt with later
                            final Collection<Name> names = nameService.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames);
                            if (namesQueryLimit > 0 && names.size() > namesQueryLimit) {
                                throw new Exception("While creating headings " + sourceCell + " resulted in " + names.size() + " names, more than the specified limit of " + namesQueryLimit);
                            }
                            row.add(dataRegionHeadingsFromNames(names, azquoMemoryDBConnection, function, null, null));
                        }
                    }
                }
            }
        }
        return nameLists;
    }

    // recursive, the key is to add the offset to allow formatting of the hierarchy
    private void resolveHierarchyForHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, DataRegionHeading heading, List<DataRegionHeading> target
            , DataRegionHeading.FUNCTION function, List<DataRegionHeading> offsetHeadings, int levelLimit) {
        if (offsetHeadings.size() < levelLimit) {// then get the children
            List<DataRegionHeading> offsetHeadingsCopy = new ArrayList<>(offsetHeadings);
            offsetHeadingsCopy.add(heading);
            for (DataRegionHeading child : dataRegionHeadingsFromNames(heading.getName().getChildren(), azquoMemoryDBConnection, function, offsetHeadingsCopy, null)) {
                resolveHierarchyForHeading(azquoMemoryDBConnection, child, target, function, offsetHeadingsCopy, levelLimit);
            }
        }
        target.add(heading); // the "parent" is added after
    }

    /* ok we're passed a list of lists
    what is returned is a 2d array (also a list of lists) featuring every possible variation in order
    so if the initial lists passed were
    A,B
    1,2,3,4
    One, Two, Three

    The returned list will be the size of each passed list multiplied together (on that case 2*4*3 so 24)
    and each entry on that list will be the size of the number of passed lists, in this case 3
    so

    A, 1, ONE
    A, 1, TWO
    A, 1, THREE
    A, 2, ONE
    A, 2, TWO
    A, 2, THREE
    A, 3, ONE
    A, 3, TWO
    A, 3, THREE
    A, 4, ONE
    A, 4, TWO
    A, 4, THREE
    B, 1, ONE
    B, 1, TWO
    B, 1, THREE
    B, 2, ONE
    B, 2, TWO

    etc.

    Row/column reference below is based off the above example - in toReturn the index of the outside list is y and the the inside list x

    I tried optimising based on calculating the size of the ArrayList that would be required but it seemed to provide little additional benefit,
     plus I don't think this is a big performance bottleneck. Commented attempt at optimising will be in SVN if required.
    */

    private <T> List<List<T>> get2DPermutationOfLists(final List<List<T>> listsToPermute) {
        //this version does full permute
          List<List<T>> toReturn = null;
        for (List<T> permutationDimension : listsToPermute) {
            if (permutationDimension == null) {
                permutationDimension = new ArrayList<>();
                permutationDimension.add(null);
            }
            if (toReturn == null) { // first one, just assign the single column
                toReturn = new ArrayList<>(permutationDimension.size());
                for (T item : permutationDimension) {
                    List<T> createdRow = new ArrayList<>();
                    createdRow.add(item);
                    toReturn.add(createdRow);
                }
            } else {
                // this is better as a different function as internally it created a new 2d array which we can then assign back to this one
                toReturn = get2DArrayWithAddedPermutation(toReturn, permutationDimension);
            }
        }
        return toReturn;
    }

    List<List<DataRegionHeading>> sortCombos(List<DataRegionHeading> listToPermute,Collection<List<Name>> foundCombinations, int position, List<Map<Integer,Integer>> sortLists){
         Map<Integer,Integer> sortList = sortLists.get(position);
         Map<Integer,Collection<List<Name>>> sortMap = new TreeMap<>();
          for (List<Name> foundCombination:foundCombinations){
            int pos = sortList.get(foundCombination.get(position).getId());
            Collection<List<Name>> sortItem = sortMap.get(pos);
            if (sortItem == null){
                sortItem = new HashSet<List<Name>>();
                sortMap.put(pos, sortItem);
            }
            sortItem.add(foundCombination);
        }
        position++;
        List<List<DataRegionHeading>> toReturn = new ArrayList<>();
        for (int key : sortMap.keySet()){
            if (position == sortLists.size()){
                for (List<Name> entry : sortMap.get(key)){
                     List<DataRegionHeading> drhEntry = new ArrayList<>();
                    for (Name name:entry){
                        drhEntry.add(new DataRegionHeading(name,true,null,null, null));
                    }
                    toReturn.add(drhEntry);


                }
                //extract list and convert to dataregion heading
            }else{
                toReturn.addAll(sortCombos(listToPermute,sortMap.get(key),position,sortLists));

            }

        }
        if (toReturn.size() > 0){//maybe should be 1 - no need for totals if there's only one item
            //create a total line
            List<DataRegionHeading> drhEntry = new ArrayList<>();
            List<DataRegionHeading> lastEntry = toReturn.get(toReturn.size()-1);
            for (int i = 0; i < position - 1; i++) {
                drhEntry.add(lastEntry.get(i));
            }
            for (int i=position-1;i<listToPermute.size();i++){
                drhEntry.add(listToPermute.get(i));
            }
            toReturn.add(drhEntry);
         }


        return toReturn;

    }


    private List<List<DataRegionHeading>> findPermutedItems2(Collection<Name> sharedNames,   final List<DataRegionHeading> listToPermute ){


        List<Name> permuteNames = new ArrayList<>();
        for (DataRegionHeading drh:listToPermute){
            permuteNames.add(drh.getName());
            if (sharedNames==null){
                sharedNames = new HashSet<Name>(drh.getName().findAllChildren(false));
            }else{
                sharedNames.retainAll(drh.getName().findAllChildren(false));
            }
        }
        Collection<List<Name>> foundCombinations = new HashSet<>();
        for (Name name :sharedNames){
            List<Name> foundCombination = new ArrayList<>();
            for (Name pName:permuteNames){
                foundCombination.add(memberName(name, pName));
            }
            foundCombinations.add(foundCombination);

        }
        //now need to sort the list into the order in the individual permute sets. ... by stagess
        // create some sort lists;
        List<Map<Integer,Integer>> sortLists = new ArrayList<>();
        for (Name permuteName:permuteNames){
            Map<Integer,Integer>sortList = new HashedMap();
            int sortPos = 0;
            for (Name name:permuteName.getChildren()){
                sortList.put(name.getId(),sortPos++);
            }
            sortLists.add(sortList);
        }
        return sortCombos(listToPermute, foundCombinations,0,sortLists);

    }


    private Name memberName(Name element, Name topSet){
         for (Name parent:element.getParents()){
            if (parent.equals(topSet)){
                return element;
            }
            Name ancestor = memberName(parent, topSet);
             if (ancestor!=null){
                 return ancestor;
             }
        }
        return null;
    }


        private List<List<DataRegionHeading>> findPermutedItems(List<DataRegionHeading> currentList, Collection<Name> children, int position, final List<List<DataRegionHeading>> listsToPermute){
        List<DataRegionHeading> toPermute = listsToPermute.get(position);
        List<List<DataRegionHeading>> toReturn = new ArrayList<>();
        position++;
         for (DataRegionHeading drh:toPermute){

            if (drh.getName() != null && drh.getName().getChildren().size() > 0){
                int startSize = toReturn.size();
                for (Name child : drh.getName().getChildren()) {
                    Collection<Name> commonChildren = null;
                    if (children != null) {
                        commonChildren = new HashSet<>(children);
                    }
                    if (child.getChildren().size() > 0) {
                        if (commonChildren != null) {
                            commonChildren.retainAll(child.findAllChildren(false));
                        } else {
                            commonChildren = (Set) child.findAllChildren(false);
                        }
                    }
                    if (commonChildren == null || commonChildren.size() > 0) {
                        List<DataRegionHeading> newCurrent = new ArrayList<>(currentList);
                        DataRegionHeading childHeading = new DataRegionHeading(child, true, null, null, null, null);
                        newCurrent.add(childHeading);
                        if (position == listsToPermute.size()) {
                            toReturn.add(newCurrent);
                        } else {
                            toReturn.addAll(findPermutedItems(newCurrent, commonChildren, position, listsToPermute));
                        }
                    }
                }
                if (toReturn.size() > startSize  && position < listsToPermute.size()){
                    List<DataRegionHeading> newCurrent= new ArrayList<>(currentList);
                    newCurrent.add(drh);
                    while (++position < listsToPermute.size()){
                        newCurrent.add(null);
                    }
                    toReturn.add(newCurrent);

                }
            }else {
                List<DataRegionHeading> newCurrent= new ArrayList<>(currentList);
                newCurrent.add(drh);
                if (position==listsToPermute.size()){
                    toReturn.add(newCurrent);
                }else {
                    toReturn.addAll(findPermutedItems(newCurrent, children, position , listsToPermute));
                }
            }

        }
        return toReturn;
       }



    /*


    so say we already have
    a,1
    a,2
    a,3
    a,4
    b,1
    b,2
    b,3
    b,4

    for example

    and want to add the permutation ONE, TWO, THREE onto it.

    The returned list of lists will be the size of the list of lists passed * the size of teh passed new dimension
    and the nested lists in the returned list will be one bigger, featuring the new dimension

    if we looked at the above as a reference it would be 3 times as high and 1 column wider
     */


    private <T> List<List<T>> get2DArrayWithAddedPermutation(final List<List<T>> existing2DArray, List<T> permutationWeWantToAdd) {
        List<List<T>> toReturn = new ArrayList<>(existing2DArray.size() * permutationWeWantToAdd.size());
        for (List<T> existingRow : existing2DArray) {
            for (T elementWeWantToAdd : permutationWeWantToAdd) { // for each new element
                List<T> newRow = new ArrayList<>(existingRow); // copy the existing row
                newRow.add(elementWeWantToAdd);// add the extra element
                toReturn.add(newRow);
            }
        }
        return toReturn;
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

     */

    private List<List<DataRegionHeading>> expandHeadings(final List<List<List<DataRegionHeading>>> headingLists, Collection<Name> sharedNames) {
        final int noOfHeadingDefinitionRows = headingLists.size();
        if (noOfHeadingDefinitionRows == 0) {
            return new ArrayList<>();
        }
        final int lastHeadingDefinitionCellIndex = headingLists.get(0).size() - 1; // the headingLists will be square, that is to say all row lists the same length as prepared by createHeadingArraysFromSpreadsheetRegion
        // ok here's the logic, what's passed is a 2d array of lists, as created from createHeadingArraysFromSpreadsheetRegion
        // we would just run through the rows running a 2d permutation on each row BUT there's a rule that if there's
        // a row below blank except the right most one then add that right most one to the one above
        boolean starting = true;
        // ok the reason I'm doing this is to avoid unnecessary array copying and resizing. If the size is 1 can just return that otherwise create an ArrayList of the right size and copy in
        ArrayList<List<List<DataRegionHeading>>> permutedLists = new ArrayList<>(noOfHeadingDefinitionRows); // could be slightly less elements than this but it's unlikely to be a biggy.
        for (int headingDefinitionRowIndex = 0; headingDefinitionRowIndex < noOfHeadingDefinitionRows; headingDefinitionRowIndex++) { // not using a vanilla for loop as we want to skip forward to allow folding of rows with one cell on the right into the list above
            List<List<DataRegionHeading>> headingDefinitionRow = headingLists.get(headingDefinitionRowIndex);
            if (lastHeadingDefinitionCellIndex > 0 && !headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex)))
                starting = false;// Don't permute until you have something to permute!
            while (!starting && headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 // we're not on the last row
                    && headingLists.get(headingDefinitionRowIndex + 1).size() > 1 // the next row is not a single cell (will all rows be the same length?)
                    && headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex + 1)) // and the last cell is the only not null one
                    ) {
                // add the single last cell from the next row to the end of this row
                if (headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex) == null) { // note if it's completely empty headingDefinitionRowHasOnlyTheRightCellPopulated would return true, not sure if this logic is completely correct, left over from the old code, I think having nulls in the permuations is allowed
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).add(null);
                } else {
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                }
                headingDefinitionRowIndex++;
            }
            try {
                if (headingDefinitionRow.get(0).get(0).getFunction() == DataRegionHeading.FUNCTION.PERMUTE) {
                    List<List<DataRegionHeading>> permuted = findPermutedItems2(sharedNames, headingDefinitionRow.get(0));//assumes only one row of headings
                    permutedLists.add(permuted);
                }else{
                    List<List<DataRegionHeading>> permuted = get2DPermutationOfLists(headingDefinitionRow);
                    permutedLists.add(permuted);

                }
            }catch(Exception e){
                    List<List<DataRegionHeading>> permuted = get2DPermutationOfLists(headingDefinitionRow);
                    permutedLists.add(permuted);

            }
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

    private boolean headingDefinitionRowHasOnlyTheRightCellPopulated(List<List<DataRegionHeading>> headingLists) {
        int numberOfCellsInThisHeadingDefinition = headingLists.size();
        for (int cellIndex = 0; cellIndex < numberOfCellsInThisHeadingDefinition; cellIndex++) {
            if (headingLists.get(cellIndex) != null) {
                return cellIndex == numberOfCellsInThisHeadingDefinition - 1; // if the first to trip the not null is the last in the row then true!
            }
        }
        return true; // All null - treat as last cell populated - is this logical?
    }

    // Filter set being a multi selection list

    public void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = getConnectionFromAccessToken(databaseAccessToken);
        List<String> justUserNameLanguages = new ArrayList<>();
        justUserNameLanguages.add(userName);
        Name filterSets = nameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        Name set = nameService.findOrCreateNameInParent(connectionFromAccessToken, setName, filterSets, true, justUserNameLanguages);//must be a local name in 'Filter sets' and be for this user
        set.setChildrenWillBePersisted(Collections.emptyList()); // easiest way to clear them
        for (Integer childId : childrenIds) {
            Name childName = nameService.findById(connectionFromAccessToken, childId);
            if (childName != null) { // it really should not be!
                set.addChildWillBePersisted(childName); // and that should be it!
            }
        }
    }

    // This class and two functions are to make qualified listings on a drop down, adding parents to qualify where necessary.
    private static class UniqueName {
        final Name bottomName;
        Name topName; // often topName is name and the description will just be left as the basic name
        String description; // when the name becomes qualified the description will become name, parent, parent of parent etc. And top name will be the highest parent, held in case we need to qualify up another level.

        UniqueName(Name topName, String description) {
            bottomName = topName; // topName may be changed but this won't
            this.topName = topName;
            this.description = description;
        }
    }

    private boolean qualifyUniqueNameWithParent(UniqueName uName) {
        Name name = uName.topName;
        boolean changed = false;
        for (Name parent : name.getParents()) {
            // check that there are no names with the same name for this shared parent. Otherwise the description/name combo would be non unique.
            // If no viable parents were found then this function is out of luck so to speak
            boolean duplicate = false;
            for (Name sibling : parent.getChildren()) { // go through all siblings
                if (sibling != name && sibling.getDefaultDisplayName() != null
                        && sibling.getDefaultDisplayName().equals(name.getDefaultDisplayName())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                changed = true; // we actually did something
                // Edd added check for the parent name containing space and adding quotes
                // the key here is that whatever is in description can be used to look up a name. Used to be reverse order comma separated, now use new member of notation
                uName.description = parent.getDefaultDisplayName() + StringUtils.MEMBEROF + uName.description;
                uName.topName = parent;
                break; // no point continuing I guess, the unique name has a further unambiguous qualifier
            }
        }
        return changed;
    }

    // for the drop down, essentially given a collection of names for a query need to give a meaningful list qualifying names with parents where they are duplicates (I suppose high streets in different towns)
    // it was assumed that names were sorted, one can't guarantee this though preserving the order is important. EFC going to rewrite, won't require ordering, now this returns the unique nnames to enable "selected" for filter lists
    private List<UniqueName> getUniqueNames(Collection<Name> names) {
        List<UniqueName> toCheck = names.stream().map(name -> new UniqueName(name, name.getDefaultDisplayName())).collect(Collectors.toList()); // java 8 should be ok here, basically copy the names to unique names to check
        int triesLeft = 10; // just in case there's a chance of infinite loops
        boolean keepChecking = true;
        while (triesLeft >= 0 && keepChecking) {
            keepChecking = false; // set to false, only go around again if something below changes the list of unique names
            Set<String> descriptionDuplicateCheck = HashObjSets.newMutableSet();
            Set<String> duplicatedDescriptions = HashObjSets.newMutableSet();
            for (UniqueName uniqueName : toCheck) {
                if (descriptionDuplicateCheck.contains(uniqueName.description)) { // we have this description already
                    duplicatedDescriptions.add(uniqueName.description); // so add it to the list of descriptions to qualify further
                }
                descriptionDuplicateCheck.add(uniqueName.description);
            }
            if (!duplicatedDescriptions.isEmpty()) { // there are duplicates, try to sort it
                for (UniqueName uniqueName : toCheck) { // run through the list again
                    if (duplicatedDescriptions.contains(uniqueName.description)) { // ok this is one of the ones that needs sorting
                        if (qualifyUniqueNameWithParent(uniqueName)) { // try to sort the name
                            keepChecking = true; // something changed
                        }
                    }
                }
            }
            triesLeft--;
        }
        return toCheck;
    }

    private List<String> getUniqueNameStrings(Collection<UniqueName> names) {
        return names.stream().map(uniqueName -> uniqueName.description).collect(Collectors.toList()); // return the descriptions, that's what we're after, in many cases this may have been copied into unique names, not modified and copied back but that's fine
    }

    private List<FilterTriple> getFilterPairsFromUniqueNames(Collection<UniqueName> names, Name filterSet) {
        return names.stream().map(uniqueName -> new FilterTriple(uniqueName.bottomName.getId(), uniqueName.description, filterSet.getChildren().contains(uniqueName.bottomName)) ).collect(Collectors.toList()); // return the descriptions, that's what we're after, in many cases this may have been copied into unique names, not modified and copied back but that's fine
    }


    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices) - EFC, where is this used?
        int dotPos = query.indexOf(".");
        if (dotPos > 0) {//todo check that it's not part of a name
            Name possibleName = nameService.findByName(getConnectionFromAccessToken(databaseAccessToken), query.substring(0, dotPos));
            if (possibleName != null) {
                String result = possibleName.getAttribute(query.substring(dotPos + 1));
                List<String> toReturn = new ArrayList<>();
                toReturn.add(result);
                return toReturn;
            }
        }
        return getUniqueNameStrings(getUniqueNames(nameService.parseQuery(getConnectionFromAccessToken(databaseAccessToken), query, languages)));
    }

    public List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName, List<String> languages) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices) - EFC, where is this used?
        List<String> justUserNameLanguages = new ArrayList<>();
        justUserNameLanguages.add(userName);
        final AzquoMemoryDBConnection connectionFromAccessToken = getConnectionFromAccessToken(databaseAccessToken);
        Name filterSets = nameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        Name filterSet = nameService.findOrCreateNameInParent(connectionFromAccessToken, filterName, filterSets, true, justUserNameLanguages);//must be a local name in 'Filter sets' and be for this user
        int dotPos = query.indexOf(".");
        if (dotPos > 0) {//todo check that it's not part of a name
            Name possibleName = nameService.findByName(connectionFromAccessToken, query.substring(0, dotPos));
            if (possibleName != null) {
                String result = possibleName.getAttribute(query.substring(dotPos + 1));
                List<FilterTriple> toReturn = new ArrayList<>();
                toReturn.add(new FilterTriple(possibleName.getId(), result, filterSet.getChildren().contains(possibleName)));
                return toReturn;
            }
        }
        final Collection<Name> names = nameService.parseQuery(connectionFromAccessToken, query, languages);
        return getFilterPairsFromUniqueNames(getUniqueNames(nameService.parseQuery(getConnectionFromAccessToken(databaseAccessToken), query, languages)), filterSet);
    }

    // it doesn't return anything, for things like setting up "as" criteria
    public void resolveQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception {
        nameService.parseQuery(getConnectionFromAccessToken(databaseAccessToken), query, languages);
    }

    private List<Integer> sortDoubleValues(Map<Integer, Double> sortTotals, final boolean sortRowsUp) {
        final List<Integer> sortedValues = new ArrayList<>(sortTotals.size());
        List<Map.Entry<Integer, Double>> list = new ArrayList<>(sortTotals.entrySet());
        // sort list based on
        Collections.sort(list, (o1, o2) -> sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue()));
        for (Map.Entry<Integer, Double> aList : list) {
            sortedValues.add(aList.getKey());
        }
        return sortedValues;
    }

    // same thing for strings, I prefer stronger typing

    private List<Integer> sortStringValues(Map<Integer, String> sortTotals, final boolean sortRowsUp) {
        final List<Integer> sortedValues = new ArrayList<>(sortTotals.size());
        List<Map.Entry<Integer, String>> list = new ArrayList<>(sortTotals.entrySet());
        // sort list based on string now
        Collections.sort(list, (o1, o2) -> sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue()));

        for (Map.Entry<Integer, String> aList : list) {
            sortedValues.add(aList.getKey());
        }
        return sortedValues;
    }

    /* ok so transposing happens here
    this is because the expand headings function is orientated for row headings and the column heading definitions are unsurprisingly set up for columns
    what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

    NOTE : this means the column heading are not stored according to the orientation used in the above function hence, to output them we have to transpose them again!

    OK, having generified the function we should only need one function. The list could be anything, names, list of names, HashMaps whatever
    generics ensure that the return type will match the sent type now rather similar to the stacktrace example :)

    Variable names assume first list is of rows and the second is each row. down then across.
    So the size of the first list is the y size (number of rows) and the size of the nested list the xsize (number of columns)
    I'm going to model it that way round as when reading data from excel that's the default (we go line by line through each row, that's how the data is delivered), the rows is the outside list
    of course could reverse all descriptions and the function could still work

    */

    private <T> List<List<T>> transpose2DList(final List<List<T>> source2Dlist) {
        if (source2Dlist.size() == 0) {
            return new ArrayList<>();
        }
        final int oldXMax = source2Dlist.get(0).size(); // size of nested list, as described above (that is to say get the length of one row)
        final List<List<T>> flipped = new ArrayList<>(oldXMax);
        for (int newY = 0; newY < oldXMax; newY++) {
            List<T> newRow = new ArrayList<>(source2Dlist.size()); // make a new row
            for (List<T> oldRow : source2Dlist) { // and step down each of the old rows
                newRow.add(oldRow.get(newY));//so as we're moving across the new row we're moving down the old rows on a fixed column
                // the transposing is happening as a list which represents a row would typically be accessed by an x value but instead it's being accessed by an y value
                // in this loop the row being read from changes but the cell in that row does not
            }
            flipped.add(newRow);
        }
        return flipped;
    }

    // return headings as strings for display, I'm going to put blanks in here if null. Called after permuting/expanding

    private List<List<String>> convertDataRegionHeadingsToStrings(List<List<DataRegionHeading>> source, List<String> languagesSent) {
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
                int extraColsForThisRow = maxColOffsets.get(colIndex);
                for (int i = 0; i < extraColsForThisRow - (heading != null && heading.getOffsetHeadings() != null ? heading.getOffsetHeadings().size() : 0); i++) {
                    returnRow.add("");
                }
                colIndex++;
            }
        }
        return toReturn;
    }

    // should be called before each report request

    public void clearLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        getConnectionFromAccessToken(databaseAccessToken).clearUserLog();
    }

    // to try to force an exception to stop execution
    // todo - check interruption is not the thing here, probably not but check

    public void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        getConnectionFromAccessToken(databaseAccessToken).setStopInUserLog();
    }

    /* function that can be called by the front end to deliver the data and headings
    Region name as defined in the Excel. valueId if it's to be the default selected cell. Row and Column headings and context as parsed straight off the sheet (2d array of cells).
      Filtercount is to remove sets of blank rows, what size chunks we look for. Highlightdays means highlight data where the provenance is less than x days old.
     */

    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int filterCount, int maxRows, int maxCols
            , String sortRow, boolean sortRowAsc, String sortCol, boolean sortColAsc, int highlightDays) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        List<List<AzquoCell>> data = getDataRegion(azquoMemoryDBConnection, regionName, rowHeadingsSource, colHeadingsSource, contextSource
                , filterCount, maxRows, maxCols, sortRow, sortRowAsc, sortCol, sortColAsc, databaseAccessToken.getLanguages(), highlightDays, valueId);
        if (data.size() == 0) {
            return new CellsAndHeadingsForDisplay(colHeadingsSource, null, new ArrayList<>(), null, colHeadingsSource, null);
        }
        List<List<CellForDisplay>> displayData = new ArrayList<>(data.size());
        for (List<AzquoCell> sourceRow : data) {
            List<CellForDisplay> displayDataRow = new ArrayList<>(sourceRow.size());
            displayData.add(displayDataRow);
            for (AzquoCell sourceCell : sourceRow) {
                // I suppose a little overhead from this - if it's a big problem can store lists of ignored rows and cols above and use that
                // ignored just means space I think, as in allow the cell to be populated by a spreadsheet formula for example
                boolean ignored = false;
                //todo check this null heading business
                for (DataRegionHeading dataRegionHeading : sourceCell.getColumnHeadings()) {
                    if (dataRegionHeading == null || ".".equals(dataRegionHeading.getAttribute())) {
                        ignored = true;
                    }
                }
                for (DataRegionHeading dataRegionHeading : sourceCell.getRowHeadings()) {
                    if (dataRegionHeading == null || ".".equals(dataRegionHeading.getAttribute())) {
                        ignored = true;
                    }
                }
                if (sourceCell.isSelected()) {
                    System.out.println("selected cell");
                }
                displayDataRow.add(new CellForDisplay(sourceCell.isLocked(), sourceCell.getStringValue(), sourceCell.getDoubleValue(), sourceCell.isHighlighted(), sourceCell.getUnsortedRow(), sourceCell.getUnsortedCol(), ignored, sourceCell.isSelected()));
            }
        }
        //AzquoMemoryDB.printAllCountStats();
        //AzquoMemoryDB.clearAllCountStats();
        // this is single threaded as I assume not much data should be returned. Need to think about this.
        return new CellsAndHeadingsForDisplay(convertDataRegionHeadingsToStrings(getColumnHeadingsAsArray(data), databaseAccessToken.getLanguages())
                , convertDataRegionHeadingsToStrings(getRowHeadingsAsArray(data), databaseAccessToken.getLanguages()), displayData, rowHeadingsSource, colHeadingsSource, contextSource);
    }

    private List<List<AzquoCell>> getDataRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, boolean sortRowAsc, String sortCol, boolean sortColAsc, List<String> languages, int highlightDays, int valueId) throws Exception {
        azquoMemoryDBCOnnection.addToUserLog("Getting data for region : " + regionName);
        long track = System.currentTimeMillis();
        long start = track;
        long threshold = 1000;
        // the context is changing to data region headings to support name function permutations - unlike the column and row headings it has to be flat, a resultant one dimensional list from createHeadingArraysFromSpreadsheetRegion
        final List<DataRegionHeading> contextHeadings = getContextHeadings(azquoMemoryDBCOnnection,contextSource,languages);
        long time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Context parsed in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<List<DataRegionHeading>>> rowHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, rowHeadingsSource, languages);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        Collection<Name> sharedNames = getSharedNames(contextHeadings);//sharedNames only required for permutations
        final List<List<DataRegionHeading>> rowHeadings = expandHeadings(rowHeadingLists, sharedNames);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<List<DataRegionHeading>>> columnHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages, COL_HEADINGS_NAME_QUERY_LIMIT);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<DataRegionHeading>> columnHeadings = expandHeadings(transpose2DList(columnHeadingLists), sharedNames);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        if (columnHeadings.size() == 0) {
            throw new Exception("no headings passed");
        }
        if (rowHeadings.size() == 0) {
            //create a single row heading
            rowHeadings.add(new ArrayList<>());
            rowHeadings.get(0).add(new DataRegionHeading(null, false));
        }
          List<List<AzquoCell>> dataToShow = getAzquoCellsForRowsColumnsAndContext(azquoMemoryDBCOnnection, rowHeadings, columnHeadings, contextHeadings, languages, valueId);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data populated in " + time + "ms");
        if (time > 5000) { // a bit arbitrary
            valueService.printSumStats();
            valueService.printFindForNamesIncludeChildrenStats();
        }
        track = System.currentTimeMillis();
        dataToShow = sortAndFilterCells(dataToShow, rowHeadings, columnHeadings
                , filterCount, maxRows, maxCols, sortRow, sortRowAsc, sortCol, sortColAsc, highlightDays, languages);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data sort/filter in " + time + "ms");
        System.out.println("region delivered in " + (System.currentTimeMillis() - start) + "ms");
        return dataToShow;
    }

    private Collection<Name> getSharedNames(List<DataRegionHeading>headingList){
        Set<Name> shared = null;
        for (DataRegionHeading heading:headingList){
            if (heading.getName()!=null && heading.getName().getChildren().size() > 0){
                if (shared==null){
                    shared = new HashSet(heading.getName().findAllChildren(false));
                }else{
                    shared.retainAll(heading.getName().findAllChildren(false));

                }
            }
        }

        return shared;
    }

    // when doing things like saving/provenance the client needs to say "here's a region description and original position" to locate a cell server side

    private AzquoCell getSingleCellFromRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int unsortedRow, int unsortedCol, List<String> languages) throws Exception {
        // these 25 lines or so are used elsewhere, maybe normalise?
        final List<DataRegionHeading> contextHeadings = getContextHeadings(azquoMemoryDBCOnnection,contextSource,languages);
        Collection<Name> sharedNames = getSharedNames(contextHeadings);//sharedNames only required for permutations
        final List<List<List<DataRegionHeading>>> rowHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, rowHeadingsSource, languages);
        final List<List<DataRegionHeading>> rowHeadings = expandHeadings(rowHeadingLists, sharedNames);
        final List<List<List<DataRegionHeading>>> columnHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages, COL_HEADINGS_NAME_QUERY_LIMIT); // same as standard limit for col headings
        final List<List<DataRegionHeading>> columnHeadings = expandHeadings(transpose2DList(columnHeadingLists), sharedNames);
        if (columnHeadings.size() == 0 || rowHeadings.size() == 0) {
            return null;
        }
        // now onto the bit to find the specific cell - the column headings were transposed then expanded so they're in the same format as the row headings
        // that is to say : the outside list's size is the number of columns or headings. So, do we have the row and col?
        if (unsortedRow < rowHeadings.size() && unsortedCol < columnHeadings.size()) {
            return getAzquoCellForHeadings(azquoMemoryDBCOnnection, rowHeadings.get(unsortedRow), columnHeadings.get(unsortedCol), contextHeadings, unsortedRow, unsortedCol, languages, 0);
        }
        return null; // no headings match the row/col passed
    }

    // for looking up a heading given a string. Used to find the col/row index to sort on (as in I want to sort on "Colour", ok what's the column number?)

    private int findPosition(List<List<DataRegionHeading>> headings, String toFind, List<String> languages) {
        if (toFind == null || toFind.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(toFind);
        } catch (Exception e) {
            //so it isn't a number!
        }
        toFind = toFind.replace(" ", "").replace("`", "").toLowerCase();
        int count = 0;
        for (List<DataRegionHeading> heading : headings) {
            DataRegionHeading dataRegionHeading = heading.get(heading.size() - 1);
            if (dataRegionHeading != null) {
                if (dataRegionHeading.getName() != null) {
                    // ok now we need to deal with the languages! This function didn't before
                    for (String language : languages) {
                        String languageValue = dataRegionHeading.getName().getAttribute(language);
                        // just run through the relevant languages looking for this column
                        if (languageValue != null && languageValue.replace(" ", "").replace("`", "").toLowerCase().equals(toFind)) {
                            return count;
                        }
                    }
                } else {
                    if (dataRegionHeading.getAttribute().replace(" ", "").replace("`", "").toLowerCase().equals(toFind)) {
                        return count;
                    }
                }
            }
            count++;
        }
        return -1;
    }

    // note, one could derive column and row headings from the source data's headings but passing them is easier if they are to hand which the should be
    // also deals with highlighting

    private List<List<AzquoCell>> sortAndFilterCells(List<List<AzquoCell>> sourceData, List<List<DataRegionHeading>> rowHeadings, List<List<DataRegionHeading>> columnHeadings
            , final int filterCount, int maxRows, int maxCols, String sortRowString, boolean sortRowAsc, String sortColString, boolean sortColAsc, int highlightDays, List<String> languages) throws Exception {
        List<List<AzquoCell>> toReturn = sourceData;
        if (sourceData == null || sourceData.isEmpty()) {
            return sourceData;
        }
        Integer sortOnColIndex = findPosition(columnHeadings, sortColString, languages);
        Integer sortOnRowIndex = findPosition(rowHeadings, sortRowString, languages); // not used at the mo
        // new logic states that sorting on row or col totals only happens if a sort row or col hasn't been passed and there are more rows or cols than max rows or cols

        int totalRows = sourceData.size();
        int totalCols = sourceData.get(0).size();

        boolean sortOnColTotals = false;
        boolean sortOnRowTotals = false;
        if (maxRows != 0) {
            if (Math.abs(maxRows) < totalRows) {
                if (sortOnColIndex == -1) { // only cause total sorting if a column hasn't been passed
                    sortOnRowTotals = true;
                    sortColAsc = maxRows < 0;
                }
            } else {
                maxRows = 0; // zero it as it's a moot point
            }
        }
        if (maxCols != 0) {
            if (Math.abs(maxCols) < totalCols) {
                if (sortOnRowIndex == -1) { // only cause total sorting if a sorting row hasn't been passed
                    sortOnColTotals = true;
                    sortColAsc = maxCols < 0;
                }
            } else {
                maxCols = 0;
            }
        }
        maxRows = Math.abs(maxRows);
        maxCols = Math.abs(maxCols);

        if (sortOnColIndex != -1 || sortOnRowIndex != -1 || sortOnColTotals || sortOnRowTotals) { // then there's sorting to do
            // was a null check on sortRow and sortCol but it can't be null, it will be negative if none found
            final Map<Integer, Double> sortRowTotals = HashIntDoubleMaps.newMutableMap(sourceData.size());
            final Map<Integer, String> sortRowStrings = HashIntObjMaps.newMutableMap(sourceData.size());
            final Map<Integer, Double> sortColumnTotals = HashIntDoubleMaps.newMutableMap(totalCols);

            for (int colNo = 0; colNo < totalCols; colNo++) {
                sortColumnTotals.put(colNo, 0.00);
            }
            boolean rowNumbers = true;
            int rowNo = 0;
            // populate the maps that will be sorted
            for (List<AzquoCell> rowCells : sourceData) {
                double sortRowTotal = 0.0;//note that, if there is a 'sortCol' then only that column is added to the total. This row total or value is used when sorting by a column.
                int colNo = 0;
                sortRowStrings.put(rowNo, "");
                for (AzquoCell cell : rowCells) {
                    // ok these bits are for sorting. Could put a check on whether a number was actually the result but not so bothered
                    // info for an up/down string sort
                    if (sortOnColIndex == colNo) {// the point here is that sorting on a single column can be non numeric so we support that by jamming the string value in here
                        sortRowStrings.put(rowNo, cell.getStringValue());
                        if (cell.getStringValue().length() > 0 && !NumberUtils.isNumber(cell.getStringValue())) {
                            rowNumbers = false;
                        }
                    }
                    // info for an up/down number sort, possibly on total values of each row
                    if ((sortOnRowTotals && !headingsHaveAttributes(cell.getColumnHeadings())) || sortOnColIndex == colNo) { // while running through the cells either add the lot for rowtotals or just the column we care about
                        sortRowTotal += cell.getDoubleValue();
                    }
                    // info for a left/rigt number sort possibly on column totals (left/right string sort not supported)
                    if ((sortOnColTotals && !headingsHaveAttributes(cell.getRowHeadings())) || sortOnRowIndex == rowNo) {
                        sortColumnTotals.put(colNo, sortColumnTotals.get(colNo) + cell.getDoubleValue());
                    }
                    colNo++;
                }
                sortRowTotals.put(rowNo++, sortRowTotal);
            }
            List<Integer> sortedRows = null;
            if (sortOnColIndex != -1 || sortOnRowTotals) { // then we need to sort the rows
                if (rowNumbers) {
                    sortedRows = sortDoubleValues(sortRowTotals, sortColAsc);
                } else {
                    sortedRows = sortStringValues(sortRowStrings, sortColAsc);
                }
            }

            List<Integer> sortedCols = null;
            if (sortOnRowIndex != -1 || sortOnColTotals) { // then we need to sort the cols
                sortedCols = sortDoubleValues(sortColumnTotals, sortRowAsc);
            }
            // OK pasting and changing what was in format data region, it's only called by this
            // zero passed or set above means don't limit, this feels a little hacky but we need a less than condition on the for loop. Both for limiting and we need this type of loop as the index looks up on the sort
            if (maxRows == 0) {
                maxRows = totalRows;
            }
            if (maxCols == 0) {
                maxCols = totalCols;
            }
            List<List<AzquoCell>> sortedCells = new ArrayList<>(maxRows); // could be less due to blanked cells but I don't see a huge harm
            for (rowNo = 0; rowNo < maxRows; rowNo++) {
                List<AzquoCell> rowCells = sourceData.get(sortedRows != null ? sortedRows.get(rowNo) : rowNo); // if a sort happened use the row number according to it
                List<AzquoCell> newRow;
                if (sortedCols != null) {
                    newRow = new ArrayList<>(maxCols);
                    for (int colNo = 0; colNo < maxCols; colNo++) {
                        newRow.add(rowCells.get(sortedCols.get(colNo)));
                    }
                } else { // just jam in the row unchanged
                    newRow = rowCells;
                }
                sortedCells.add(newRow);
            }
            toReturn = sortedCells;
        }
        if (filterCount > 0) {
            int rowNo = 0;
            while (rowNo < toReturn.size()) {
                boolean rowsBlank = true;
                for (int j = 0; j < filterCount; j++) {
                    List<AzquoCell> rowToCheck = toReturn.get(rowNo + j); // size - 1 for the last index
                    for (AzquoCell cellToCheck : rowToCheck) {
                        //Values found = non blank row. I think filter will generally only be used in the context of values.
                        if (cellToCheck.getListOfValuesOrNamesAndAttributeName() != null && cellToCheck.getListOfValuesOrNamesAndAttributeName().getAttributeNames() == null
                                && cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty()) {
                            rowsBlank = false;
                            break;
                        }
                    }
                    if (!rowsBlank) {
                        break;
                    }
                }
                if (rowsBlank) {
                    for (int i = 0; i < filterCount; i++) {
                        toReturn.remove(rowNo);
                    }
                } else {
                    rowNo += filterCount;
                }
            }
        }

        // it's at this point we actually have data that's going to be sent to a user in newRow so do the highlighting here I think
        if (highlightDays > 0) {
            for (List<AzquoCell> row : toReturn) {
                for (AzquoCell azquoCell : row) {
                    long age = 10000; // about 30 years old as default
                    ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
                    if (valuesForCell != null && (valuesForCell.getValues() != null && !valuesForCell.getValues().isEmpty())) {
                        for (Value value : valuesForCell.getValues()) {
                            if (value.getText().length() > 0) {
                                if (value.getProvenance() == null) {
                                    break;
                                }
                                LocalDateTime provdate = LocalDateTime.ofInstant(value.getProvenance().getTimeStamp().toInstant(), ZoneId.systemDefault());
                                long cellAge = provdate.until(LocalDateTime.now(), ChronoUnit.DAYS);
                                if (cellAge < age) {
                                    age = cellAge;
                                }
                            }
                        }
                    }
                    if (highlightDays >= age) {
                        azquoCell.setHighlighted(true);
                    }
                }
            }
        }
        return toReturn;
    }

    /*
    Parameters

    The connection to the relevant DB
    Row and column headings (very possibly more than one heading for a given row or column if permutation is involved) and context names list
    Languages is attribute names but I think I'll call it languages as that's what it would practically be - used when looking up names for formulae

    So,it's going to return relevant data to the region. The values actually shown, (typed?) objects for ZKspreadsheet, locked or not, the headings are useful (though one could perhaps derive them)
    it seems that there should be a cell map or object and that's what this should return rather than having a bunch of multidimensional arrays
    ok I'm going for that object type (AzquoCell), outer list rows inner items on those rows, hope that's standard. Outside this function the sorting etc will happen.

Callable interface sorts the memory "happens before" using future gets which runnable did not guarantee I don't think (though it did work).
    */

    private class RowFiller implements Callable<List<AzquoCell>> {
        private final int row;
        private final List<List<DataRegionHeading>> headingsForEachColumn;
        private final List<List<DataRegionHeading>> headingsForEachRow;
        private final List<DataRegionHeading> contextHeadings;
        private final List<String> languages;
        private final int valueId;
        private final AzquoMemoryDBConnection connection;
        private final AtomicInteger counter;
        private final int progressBarStep;


        RowFiller(int row, List<List<DataRegionHeading>> headingsForEachColumn, List<List<DataRegionHeading>> headingsForEachRow
                , List<DataRegionHeading> contextHeadings, List<String> languages, int valueId, AzquoMemoryDBConnection connection, AtomicInteger counter, int progressBarStep) {
            this.row = row;
            this.headingsForEachColumn = headingsForEachColumn;
            this.headingsForEachRow = headingsForEachRow;
            this.contextHeadings = contextHeadings;
            this.languages = languages;
            this.valueId = valueId;
            this.connection = connection;
            this.counter = counter;
            this.progressBarStep = progressBarStep;
        }

        @Override
        public List<AzquoCell> call() throws Exception {
            //System.out.println("Filling " + startRow + " to " + endRow);
            List<DataRegionHeading> rowHeadings = headingsForEachRow.get(row);
            List<AzquoCell> returnRow = new ArrayList<>(headingsForEachColumn.size());
            int colNo = 0;
            for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                // values I need to build the CellUI
                returnRow.add(getAzquoCellForHeadings(connection, rowHeadings, columnHeadings, contextHeadings, row, colNo, languages, valueId));
                // for some reason this was before, it buggered up the ability to find the right column!
                colNo++;
                if (counter.incrementAndGet() % progressBarStep == 0) {
                    connection.addToUserLog("=", false);
                }
            }
            return returnRow;
        }
    }

    // More granular version of the above, less than 1000 rows, probably typical use.
    // On Damart for example we had 26*9 taking a while and it was reasonable to assume that rows were not even in terms of processing required
    private class CellFiller implements Callable<AzquoCell> {
        private final int row;
        private final int col;
        private final List<DataRegionHeading> headingsForColumn;
        private final List<DataRegionHeading> headingsForRow;
        private final List<DataRegionHeading> contextHeadings;
        private final List<String> languages;
        private final int valueId;
        private final AzquoMemoryDBConnection connection;
        private final AtomicInteger counter;
        private final int progressBarStep;

        CellFiller(int row, int col, List<DataRegionHeading> headingsForColumn, List<DataRegionHeading> headingsForRow,
                   List<DataRegionHeading> contextHeadings, List<String> languages, int valueId, AzquoMemoryDBConnection connection, AtomicInteger counter, int progressBarStep) {
            this.row = row;
            this.col = col;
            this.headingsForColumn = headingsForColumn;
            this.headingsForRow = headingsForRow;
            this.contextHeadings = contextHeadings;
            this.languages = languages;
            this.valueId = valueId;
            this.connection = connection;
            this.counter = counter;
            this.progressBarStep = progressBarStep;
        }

        // this should sort my memory concerns (I mean the AzquoCell being appropriately visible), call causing a memory barrier which runnable didn't.
        // Not 100% sure this error tracking is correct, leave it for the mo
        @Override
        public AzquoCell call() throws Exception {
            // connection.addToUserLog(".", false);
            final AzquoCell azquoCell = getAzquoCellForHeadings(connection, headingsForRow, headingsForColumn, contextHeadings, row, col, languages, valueId);
            if (counter.incrementAndGet() % progressBarStep == 0) {
                connection.addToUserLog("=", false);
            }
            return azquoCell;
        }
    }

    /* only called by itself and path intersection, we want to know the number of intersecting names between containsSet
    and all its children and selectionSet, recursive to deal with containsSet down to the bottom. alreadyTested is
    simply to stop checking names that have already been checked. Note already tested doesn't mean intersections are unique,
    the number on intersections is on alreadyTested's children, it's when there is an intersection there that a name is considered tested.

    Worth pointing out that findOverlap which was used on total name count is a total of unique names and hence can use a contains on findAllChildren which returns a set
     whereas this, on the other hand, requires the non unique intersection, a single name could contribute to the count more than once by being in more than one set.

    Note : if this is really hammered there would be a case for moving it inside name to directly access the array and hence avoid Iterator instantiation.

       */

    private int totalSetIntersectionCount(Name containsSet, Set<Name> selectionSet, Set<Name> alreadyTested, int track) {
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

    // hacky, I just need a way to pass the values without doing a redundant addAll
    public static class ValuesHook {
        public List<Value> values = null;
    }

    /* factored this off to enable getting a single cell, also useful to be called from the multi threading. Now deals with name functions which
    evaluate a name expression for each cell as opposed to value functions which work off names already resolved in the DataRegionHeadings
     */

    private AzquoCell getAzquoCellForHeadings(AzquoMemoryDBConnection connection, List<DataRegionHeading> rowHeadings, List<DataRegionHeading> columnHeadings
            , List<DataRegionHeading> contextHeadings, int rowNo, int colNo, List<String> languages, int valueId) throws Exception {
        boolean selected = false;
        String stringValue = "";
        double doubleValue = 0;
        MutableBoolean locked = new MutableBoolean(); // we use a mutable boolean as the functions that resolve the cell value may want to set it
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
            String ROWHEADING = "[ROWHEADING]";
            String ROWHEADINGLOWERCASE = "[rowheading]";
            if (!rowHeadings.isEmpty() && (cellQuery.contains(ROWHEADING) || cellQuery.contains(ROWHEADINGLOWERCASE))) {
                cellQuery = cellQuery.replace(ROWHEADING, rowHeadings.get(0).getName().getFullyQualifiedDefaultDisplayName()).replace(ROWHEADINGLOWERCASE, rowHeadings.get(0).getName().getFullyQualifiedDefaultDisplayName()); // we assume the row heading has a "legal" description. Probably a name identifier !1234
            }
            String COLUMNHEADING = "[COLUMNHEADING]";
            String COLUMNHEADINGLOWERCASE = "[columnheading]";
            if (!columnHeadings.isEmpty() && (cellQuery.contains(COLUMNHEADING) || cellQuery.contains(COLUMNHEADINGLOWERCASE))) {
                cellQuery = cellQuery.replace(COLUMNHEADING, columnHeadings.get(0).getName().getFullyQualifiedDefaultDisplayName()).replace(COLUMNHEADINGLOWERCASE, columnHeadings.get(0).getName().getFullyQualifiedDefaultDisplayName()); // and now the col headings
            }
            locked.isTrue = true; // they cant edit the results from complex functions
            if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) { // a straight set but with [ROWHEADING] as part of the criteria
                Set<Name> namesToCount = HashObjSets.newMutableSet(); // I think this will be faster for purpose
                nameService.parseQuery(connection, cellQuery, languages, namesToCount);
                doubleValue = namesToCount.size();
                stringValue = doubleValue + "";
            } else if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.PATHCOUNT) { // new syntax, before it was name, set now it's set, set. Sticking to very basic , split
                String[] twoSets = nameFunctionHeading.getDescription().split(","); // we assume this will give an array of two, I guess see if this is a problem
                Set<Name> leftSet = HashObjSets.newMutableSet();
                Set<Name> rightSet = HashObjSets.newMutableSet();
                nameService.parseQuery(connection, twoSets[0], languages, leftSet);
                nameService.parseQuery(connection, twoSets[1], languages, rightSet);
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
                final Collection<Name> set = nameService.parseQuery(connection, cellQuery, languages);
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
                final Collection<Name> set = nameService.parseQuery(connection, cellQuery, languages);
                doubleValue = 0;
                stringValue = set.isEmpty() ? "" : set.iterator().next().getDefaultDisplayName();
            } else if (nameFunctionHeading.getFunction() == DataRegionHeading.FUNCTION.LAST) {
                final Collection<Name> set = nameService.parseQuery(connection, cellQuery, languages);
                doubleValue = 0;
                stringValue = "";
                for (Name name : set) { //a bit of a lazy way of doing things but it should be fine, plus with only a collection interface not sure of how to get the last!
                    stringValue = name.getDefaultDisplayName();
                }
            }
        } else {// conventional type (sum) or value function
            Set<DataRegionHeading> headingsForThisCell = HashObjSets.newMutableSet(rowHeadings.size()); // I wonder a little how important having them as sets is . . .
            Set<DataRegionHeading> rowAndColumnHeadingsForThisCell = null;
            //check that we do have both row and column headings, otherwise blank them the cell will be blank (danger of e.g. a sum on the name "Product"!)
            for (DataRegionHeading heading : rowHeadings) {
                if (heading != null && (heading.getName() != null || (heading.getAttribute() != null && !heading.getAttribute().equals(".")))) {
                    headingsForThisCell.add(heading);
                }
            }
            int hCount = headingsForThisCell.size();
            boolean checked = true;
            if (hCount > 0) {
                for (DataRegionHeading heading : columnHeadings) {
                    if (heading != null && (heading.getName() != null || (heading.getAttribute() != null && !heading.getAttribute().equals(".")))) {
                        headingsForThisCell.add(heading);
                    }
                }
                rowAndColumnHeadingsForThisCell = new HashSet<>(headingsForThisCell);
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
            if (!checked) { // no valid row/col combo
                locked.isTrue = true;
            } else {
                // ok new logic here, we need to know if we're going to use attributes or values
                DataRegionHeading.FUNCTION function = null;
                Set<Name> valueFunctionSet = null;
                for (DataRegionHeading heading : headingsForThisCell) {
                    if (heading.getFunction() != null) { // should NOT be a name function, that should have been caught before
                        function = heading.getFunction();
                        valueFunctionSet = heading.getValueFunctionSet(); // value function e.g. value parent count can allow a name set to be defined
                        break; // can't combine functions I don't think
                    }
                }
                if (!headingsHaveAttributes(headingsForThisCell)) { // we go the value route (as iot was before allowing attributes), need the headings as names,
                    ValuesHook valuesHook = new ValuesHook();
                    // now , get the function from the headings
                    if (function != null) {
                        locked.isTrue = true;
                    }
                    if (headingsForThisCell.size() > 0) {
                        Value valueToTestFor = null;
                        if (valueId > 0) {
                            valueToTestFor = connection.getAzquoMemoryDB().getValueById(valueId);
                        }
                        doubleValue = valueService.findValueForNames(connection, namesFromDataRegionHeadings(headingsForThisCell), locked, true, valuesHook, languages, function); // true = pay attention to names additive flag - I want to get rid of this. We'll see.
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
                    listOfValuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(valuesHook.values);// can we zap the values from here? It might be a bit of a saving if there are loads of values per cell
                } else {  // attributes in the cells
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
                    String attributeResult = valueService.findValueForHeadings(rowAndColumnHeadingsForThisCell, locked);
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

    // todo : put the size check of each set and hence which way we run through the loop in here, should improve performance if required
    private int findOverlap(Collection<Name> set1, Collection<Name> set2) {
        int count = 0;
        for (Name name : set1) {
            if (set2.contains(name)) count++;
        }
        return count;
    }

    private List<List<AzquoCell>> getAzquoCellsForRowsColumnsAndContext(AzquoMemoryDBConnection connection, List<List<DataRegionHeading>> headingsForEachRow
            , final List<List<DataRegionHeading>> headingsForEachColumn
            , final List<DataRegionHeading> contextHeadings, List<String> languages, int valueId) throws Exception {
        long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        long newHeapMarker = oldHeapMarker;
        long track = System.currentTimeMillis();
        int totalRows = headingsForEachRow.size();
        int totalCols = headingsForEachColumn.size();
/*        for (int i = 0; i < totalRows; i++) {
            toReturn.add(null);// null the rows, basically adding spaces to the return list
        }*/
        List<List<AzquoCell>> toReturn = new ArrayList<>(totalRows); // make it the right size so multithreading changes the values but not the structure
        connection.addToUserLog("Size = " + totalRows + " * " + totalCols);
        int maxRegionSize = 2000000;//random!  set by WFC 29/6/15
        if (totalRows * totalCols > maxRegionSize) {
            throw new Exception("error: data region too large - " + totalRows + " * " + totalCols + ", max cells " + maxRegionSize);
        }
        ExecutorService executor = AzquoMemoryDB.mainThreadPool;
        int progressBarStep = (totalCols * totalRows) / 50 + 1;
        AtomicInteger counter = new AtomicInteger();
        // I was passing an ArrayList through to the tasks. This did seem to work but as I understand a Callable is what's required here, takes care of memory sync and exceptions
        if (totalRows < 1000) { // arbitrary cut off for the moment, Future per cell. I wonder if it should be lower than 1000?
            List<List<Future<AzquoCell>>> futureCellArray = new ArrayList<>();
            for (int row = 0; row < totalRows; row++) {
                List<DataRegionHeading> rowHeadings = headingsForEachRow.get(row);
                List<Future<AzquoCell>> futureRow = new ArrayList<>(headingsForEachColumn.size());
                /*for (int i = 0; i < headingsForEachColumn.size(); i++) {
                    returnRow.add(null);// yes a bit hacky, want to make the space that will be used by cellfiller
                }*/
                int colNo = 0;
                for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                    // inconsistent parameter ordering?
                    futureRow.add(executor.submit(new CellFiller(row, colNo, columnHeadings, rowHeadings, contextHeadings, languages, valueId, connection, counter, progressBarStep)));
                    colNo++;
                }
                futureCellArray.add(futureRow);
                //toReturn.set(row, returnRow);
            }
            // so the future cells have been racked up, let's try and get them
            for (List<Future<AzquoCell>> futureRow : futureCellArray) {
                List<AzquoCell> row = new ArrayList<>();
                for (Future<AzquoCell> futureCell : futureRow) {
                    row.add(futureCell.get());
                }
                toReturn.add(row);
            }
        } else { // Future per row
            List<Future<List<AzquoCell>>> futureRowArray = new ArrayList<>();
            for (int row = 0; row < totalRows; row++) {
                // row passed twice as
                futureRowArray.add(executor.submit(new RowFiller(row, headingsForEachColumn, headingsForEachRow, contextHeadings, languages, valueId, connection, counter, progressBarStep)));
            }
            for (Future<List<AzquoCell>> futureRow : futureRowArray) {
                toReturn.add(futureRow.get(1, TimeUnit.HOURS));
            }
        }
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        System.out.println();
        int mb = 1024 * 1024;
        System.out.println("Heap cost to make on multi thread : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();
        //oldHeapMarker = newHeapMarker;
        connection.addToUserLog(" time : " + (System.currentTimeMillis() - track) + "ms");
        return toReturn;
    }

    // new logic, derive the headings from the data, so after sorting the data one can get headings for each cell rather than pushing the sort over to the headings as well
    private List<List<DataRegionHeading>> getColumnHeadingsAsArray(List<List<AzquoCell>> cellArray) {
        List<List<DataRegionHeading>> toReturn = new ArrayList<>(cellArray.get(0).size());
        for (AzquoCell cell : cellArray.get(0)) {
            toReturn.add(cell.getColumnHeadings());
        }
        return transpose2DList(toReturn);
    }

    // new logic, derive the headings from the data, no need to resort to resorting etc.
    private List<List<DataRegionHeading>> getRowHeadingsAsArray(List<List<AzquoCell>> cellArray) {
        List<List<DataRegionHeading>> toReturn = new ArrayList<>(cellArray.size()); // might not use all of it but I'm a little confused as to why the row would be empty?
        for (List<AzquoCell> row : cellArray) {
            if (!row.isEmpty()) { // this check . . think for some unusual situations
                toReturn.add(row.get(0).getRowHeadings());
            }
        }
        return toReturn;
    }

    // used when comparing values. So ignore the currency symbol if the numbers are the same
    private String stripCurrency(String val) {
        //TODO we need to be able to detect other currencies
        if (val.length() > 1 && "$£".contains(val.substring(0, 1))) {
            return val.substring(1);
        }
        return val;
    }

    public boolean compareStringValues(final String val1, final String val2) {
        //tries to work out if numbers expressed with different numbers of decimal places, maybe including percentage signs and currency symbols are the same.
        if (val1.equals(val2)) return true;
        String val3 = val1;
        String val4 = val2;
        if (val1.endsWith("%") && val2.endsWith("%")) {
            val3 = val1.substring(0, val1.length() - 1);
            val4 = val2.substring(0, val2.length() - 1);
        }
        val3 = stripCurrency(val3);
        val4 = stripCurrency(val4);
        if (NumberUtils.isNumber(val3) && NumberUtils.isNumber(val4)) {
            Double n1 = Double.parseDouble(val3);
            Double n2 = Double.parseDouble(val4);
            if (n1 - n2 == 0) return true;
        }
        return false;
    }

    public List<TreeNode> getDataRegionProvenance(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        AzquoCell azquoCell = getSingleCellFromRegion(azquoMemoryDBConnection, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, databaseAccessToken.getLanguages());
        if (azquoCell != null) {
            final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
            //Set<Name> specialForProvenance = new HashSet<Name>();
            // todo, deal with name functions properly, will need to check through the DataRegionHeadings
            if (valuesForCell == null) {
                return nameCountProvenance(azquoCell);
            }
            if (valuesForCell.getValues() != null) {
                return nodify(valuesForCell.getValues(), maxSize);
            }
            if (azquoCell.getRowHeadings().get(0).getAttribute() != null || azquoCell.getColumnHeadings().get(0).getAttribute() != null) {
                if (azquoCell.getRowHeadings().get(0).getAttribute() != null) { // then col name, row attribute
                    return nodify(azquoCell.getColumnHeadings().get(0).getName(), azquoCell.getRowHeadings().get(0).getAttribute());
                } else { // the other way around
                    return nodify(azquoCell.getRowHeadings().get(0).getName(), azquoCell.getColumnHeadings().get(0).getAttribute());
                }
            }
        }
        return Collections.emptyList(); //just empty ok? null? Unsure
    }

    private List<TreeNode> nameCountProvenance(AzquoCell azquoCell) {
        String provString = "";
        Set<Name> cellNames = new HashSet<>();
        Name nameCountHeading = null;
        for (DataRegionHeading rowHeading : azquoCell.getRowHeadings()) {
            if (rowHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                provString += "namecount(" + rowHeading.getDescription();
                nameCountHeading = rowHeading.getName();
            }
            if (rowHeading.getName() != null) {
                cellNames.add(rowHeading.getName());
            }
        }
        for (DataRegionHeading colHeading : azquoCell.getColumnHeadings()) {
            if (colHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                provString += "namecount(" + colHeading.getDescription();
                nameCountHeading = colHeading.getName();
                break;
            }
            if (colHeading.getName() != null) {
                cellNames.add(colHeading.getName());
            }
        }
        List<TreeNode> toReturn = new ArrayList<>();
        if (nameCountHeading != null) {
            provString = "total" + provString;
        }
        Name cellName = cellNames.iterator().next();
        provString += " * " + cellName.getDefaultDisplayName() + ")";
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        Provenance p = cellName.getProvenance();
        TreeNode node = new TreeNode();
        node.setValue(azquoCell.getDoubleValue() + "");
        node.setName(provString);
        String source = df.format(p.getTimeStamp()) + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        node.setHeading(source + " " + method);
        toReturn.add(node);
        return toReturn;
    }

    // for inspect database I think - should be moved to the JStree service maybe?
    public TreeNode getDataList(Set<Name> names, int maxSize) throws Exception {
        List<Value> values = null;
        String heading = "";
        for (Name name : names) {
            if (values == null) {
//                values = new ArrayList<>(valueService.findValuesForNameIncludeAllChildren(name, true));
                values = new ArrayList<>(name.findValuesIncludingChildren(true));
            } else {
                values.retainAll(name.findValuesIncludingChildren(true));
            }
            if (heading.length() > 0) heading += ", ";
            heading += name.getDefaultDisplayName();
        }
        TreeNode toReturn = new TreeNode();
        toReturn.setHeading(heading);
        toReturn.setValue("");
        toReturn.setChildren(nodify(values, maxSize));
        valueService.addNodeValues(toReturn);
        return toReturn;
    }

    // As I understand this function is showing names attached to the values in this cell that are not in the requesting spread sheet's row/column/context
    // for provenance?
    private List<TreeNode> nodify(List<Value> values, int maxSize) {
        List<TreeNode> toReturn = new ArrayList<>();
        if (values != null && (values.size() > 1 || (values.size() > 0 && values.get(0) != null))) {
            valueService.sortValues(values);
            //simply sending out values is a mess - hence this ruse: extract the most persistent names as headings
            Date provdate = values.get(0).getProvenance().getTimeStamp();
            Set<Value> oneUpdate = new HashSet<>();
            Provenance p = null;
            for (Value value : values) {
                if (value.getProvenance().getTimeStamp() == provdate) {
                    oneUpdate.add(value);
                    p = value.getProvenance();
                } else {
                    toReturn.add(valueService.getTreeNode(oneUpdate, p, maxSize));
                    oneUpdate = new HashSet<>();
                    oneUpdate.add(value);
                    p = value.getProvenance();
                    provdate = value.getProvenance().getTimeStamp();
                }
            }
            toReturn.add(valueService.getTreeNode(oneUpdate, p, maxSize));
        }
        return toReturn;
    }

    // another not very helpfully named function, might be able to be rewritten after we zap Azquo Book (the Aspose based functionality)
    private List<TreeNode> nodify(Name name, String attribute) {
        attribute = attribute.substring(1).replace("`", "");
        List<TreeNode> toReturn = new ArrayList<>();
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        Provenance p = name.getProvenance();
        TreeNode node = new TreeNode();
        node.setValue(name.getAttribute(attribute));
        node.setName(name.getDefaultDisplayName() + "." + attribute);
        String source = df.format(p.getTimeStamp()) + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        node.setHeading(source + " " + method);
        toReturn.add(node);
        return toReturn;
    }

    // create a file to import from a populated region in the spreadsheet
    private void importDataFromSpreadsheet(AzquoMemoryDBConnection azquoMemoryDBConnection, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user) throws Exception {
        //write the column headings and data to a temporary file, then import it
        String fileName = "temp_" + user;
        File temp = File.createTempFile(fileName + ".csv", "csv");
        String tempName = temp.getPath();
        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
        StringBuffer sb = new StringBuffer();
        List<String> colHeadings = cellsAndHeadingsForDisplay.getColumnHeadings().get(0);
        boolean firstCol = true;
        for (String heading : colHeadings) {
            if (!firstCol) {
                sb.append("\t");
            } else {
                firstCol = false;
            }
            sb.append(heading);
        }
        bw.write(sb.toString());
        bw.newLine();
        List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
        for (List<CellForDisplay> row : data) {
            sb = new StringBuffer();
            firstCol = true;
            boolean blankLine = true;
            for (CellForDisplay cellForDisplay : row) {
                if (!firstCol) sb.append("\t");
                else firstCol = false;
                // use string if we have it,otherwise double if it's not 0 or explicitly changed (0 allowed if manually entered). Otherwise blank.
                if (cellForDisplay.getStringValue() != null) {
                    String val = cellForDisplay.getStringValue().length() > 0 ? cellForDisplay.getStringValue() : cellForDisplay.getDoubleValue() != 0 ? cellForDisplay.getDoubleValue() + "" : "";
                    //for the moment we're passsing on cells that have not been entered as blanks which are ignored in the importer - this does not leave space for deleting values or attributes
                    if (cellForDisplay.getStringValue().length() > 0) {
                        blankLine = false;
                    }
                    sb.append(val);
                }
            }
            if (!blankLine) {
                bw.write(sb.toString());
                bw.newLine();
            }
        }
        bw.flush();
        bw.close();
        importService.readPreparedFile(azquoMemoryDBConnection, tempName, "csv", Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME), true, true);
        temp.delete(); // see no harm in this here. Delete on exit has a problem with Tomcat being killed from the command line. Why is intelliJ shirty about this?
    }

    // it's easiest just to send the CellsAndHeadingsForDisplay back to the back end and look for relevant changed cells
    public void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user, "in spreadsheet", reportName, context);
        if (cellsAndHeadingsForDisplay.getRowHeadings() == null && cellsAndHeadingsForDisplay.getData().size() > 0) {
            importDataFromSpreadsheet(azquoMemoryDBConnection, cellsAndHeadingsForDisplay, user);
            azquoMemoryDBConnection.persist();
            return;
        }
        int numberOfValuesModified = 0;
        int rowCounter = 0;
        for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()) {
            int columnCounter = 0;
            for (CellForDisplay cell : row) {
                if (!cell.isLocked() && cell.isChanged()) {
                    //logger.info(orig + "|" + edited + "|"); // we no longer know the original value unless we jam it in AzquoCell
                    // note, if there are many cells being edited this becomes inefficient as headings are being repeatedly looked up
                    AzquoCell azquoCell = getSingleCellFromRegion(azquoMemoryDBConnection, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                            , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                            , cell.getUnsortedRow(), cell.getUnsortedCol(), databaseAccessToken.getLanguages());
                    if (azquoCell != null) {
                        numberOfValuesModified++;
                        // this save logic is the same as before but getting necessary info from the AzquoCell
                        logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                        final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
                        // one thing about these store functions to the value spreadsheet, they expect the provenance on the logged in connection to be appropriate
                        // first align text and numbers where appropriate
                        /* edd commenting 07/03/2016, this was stopping deleting a cell and I think it makes no sense looking at the ZK code that happens on editing, maybe a hangover from Aspose?
                        try {
                            if (cell.getDoubleValue() != 0.0) {
                                cell.setStringValue(cell.getDoubleValue() + "");
                            }
                        } catch (Exception ignored) {
                        }*/
                        if (cell.getStringValue() != null && cell.getStringValue().endsWith("%")) {
                            String percent = cell.getStringValue().substring(0, cell.getStringValue().length() - 1);
                            try {
                                double d = Double.parseDouble(percent) / 100;
                                cell.setStringValue(d + "");
                            } catch (Exception e) {
                                //do nothing
                            }
                        }
                        if (valuesForCell.getValues() != null) { // this assumes empty values rather than null if the populating code couldn't find any
                            if (valuesForCell.getValues().size() == 1) {
                                final Value theValue = valuesForCell.getValues().get(0);
                                logger.info("trying to overwrite");
                                if (cell.getStringValue() != null && cell.getStringValue().length() > 0) {
                                    //sometimes non-existent original values are stored as '0'
                                    valueService.overWriteExistingValue(azquoMemoryDBConnection, theValue, cell.getStringValue());
                                    numberOfValuesModified++;
                                } else {
                                    theValue.delete();
                                }
                            } else if (valuesForCell.getValues().isEmpty() && cell.getStringValue() != null && cell.getStringValue().length() > 0) {
                                logger.info("storing new value here . . .");
                                // need to get the names, this was outside
                                final Set<DataRegionHeading> headingsForCell = HashObjSets.newMutableSet(azquoCell.getColumnHeadings().size() + azquoCell.getRowHeadings().size());
                                headingsForCell.addAll(azquoCell.getColumnHeadings());
                                headingsForCell.addAll(azquoCell.getRowHeadings());
                                headingsForCell.addAll(azquoCell.getContexts());
                                Set<Name> cellNames = namesFromDataRegionHeadings(headingsForCell);
                                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, cell.getStringValue(), cellNames);
                                numberOfValuesModified++;
                            }
                            // warning on multiple values?
                        } else {
                            // added not null checks - can names or attributes be null here? Best check - todo
                            if (valuesForCell.getNames() != null && valuesForCell.getNames().size() == 1
                                    && valuesForCell.getAttributeNames() != null && valuesForCell.getAttributeNames().size() == 1) { // allows a simple attribute store
                                Name toChange = valuesForCell.getNames().get(0);
                                String attribute = valuesForCell.getAttributeNames().get(0).substring(1).replace(Name.QUOTE + "", "");//remove the initial '.' and any `
                                Name attSet = nameService.findByName(azquoMemoryDBConnection, attribute);
                                if (attSet != null && attSet.hasChildren() && !azquoMemoryDBConnection.getAzquoMemoryDB().attributeExistsInDB(attribute)) {
                                    /* right : when populating attribute based data findParentAttributes can be called internally in Name. DSSpreadsheetService is not aware of it but it means (in that case) the data
                                    returned is not in fact attributes but the name of an intermediate set in the hierarchy - suppose you want the category of a product the structure is
                                    all categories -> category -> product and .all categories is the column heading and the products are row headings then you'll get the category for the product as the cell value
                                     So attSet following that example is "All Categories", category is a (possibly) new name that's a child of all categories and then we need to add the product
                                     , named toChange at the moment to that category.
                                    */
                                    logger.info("storing " + toChange.getDefaultDisplayName() + " to children of  " + cell.getStringValue() + " within " + attribute);
                                    Name category = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell.getStringValue(), attSet, true);
                                    category.addChildWillBePersisted(toChange);
                                } else {// simple attribute set
                                    logger.info("storing attribute value on " + toChange.getDefaultDisplayName() + " attribute " + attribute);
                                    toChange.setAttributeWillBePersisted(attribute, cell.getStringValue());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (numberOfValuesModified > 0) {
            azquoMemoryDBConnection.persist();
        }
        // clear the caches after, if we do before then some will be recreated as part of saving.
        // Is this a bit overkill given that it should clear as it goes? I suppose there's the query and count caches, plus parents of the changed names
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
    }

    private List<DataRegionHeading> getContextHeadings(AzquoMemoryDBConnection azquoMemoryDBConnection, List<List<String>> contextSource, List<String> languages) throws Exception {
        final List<List<List<DataRegionHeading>>> contextArraysFromSpreadsheetRegion = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, contextSource, languages);
        final List<DataRegionHeading> contextHeadings = new ArrayList<>();
        for (List<List<DataRegionHeading>> list1 : contextArraysFromSpreadsheetRegion){
            for (List<DataRegionHeading> list2 : list1){
                if (list2 != null){ // seems it can be
                    contextHeadings.addAll(list2);
                }
            }
        }
        return contextHeadings;
    }

    private List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, AzquoMemoryDBConnection azquoMemoryDBConnection, DataRegionHeading.FUNCTION function, List<DataRegionHeading> offsetHeadings, Set<Name> valueFunctionSet) {
        List<DataRegionHeading> dataRegionHeadings = new ArrayList<>(names.size()); // names could be big, init the Collection with the right size
        if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()) {
            // then check permissions
            for (Name name : names) {
                // will the new write permissions cause an overhead?
                dataRegionHeadings.add(new DataRegionHeading(name, nameService.isAllowed(name, azquoMemoryDBConnection.getWritePermissions()), function, null, offsetHeadings, valueFunctionSet));
            }
        } else { // don't bother checking permissions, write permissions to true
            for (Name name : names) {
                dataRegionHeadings.add(new DataRegionHeading(name, true, function, null, offsetHeadings, valueFunctionSet));
            }
        }
        return dataRegionHeadings;
    }

    public Set<Name> namesFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        // ok some of the data region headings may be attribute, no real harm I don't think VS a whacking great set which would always be names
        Set<Name> names = new HashSet<>(dataRegionHeadings.size());
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getName() != null) {
                names.add(dataRegionHeading.getName());
            }
        }
        return names;
    }

    public Set<String> attributesFromDataRegionHeadings(Collection<DataRegionHeading> dataRegionHeadings) {
        Set<String> names = HashObjSets.newMutableSet(); // attributes won't ever be big as they're manually defined, leave this to default (16 size table internally?)
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getAttribute() != null) {
                names.add(dataRegionHeading.getAttribute().substring(1)); // at the mo I assume attributes begin with .
            }
        }
        return names;
    }

    private boolean headingsHaveAttributes(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading != null && dataRegionHeading.getAttribute() != null) {
                return true;
            }
        }
        return false;
    }
}