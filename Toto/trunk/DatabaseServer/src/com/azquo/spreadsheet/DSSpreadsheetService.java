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
import net.openhft.koloboke.collect.map.hash.HashIntDoubleMaps;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletContext;
import java.io.*;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// it seems that trying to configure the properties in spring is a problem - todo : see if upgrading spring to 4.2 makes this easier?
// todo : try to address proper use of protected and private given how I've shifted lots of classes around. This could apply to all sorts in the system.

public class DSSpreadsheetService {

    final Runtime runtime = Runtime.getRuntime();
    final int mb = 1024 * 1024;

    final int COL_HEADINGS_NAME_QUERY_LIMIT = 500;


    private static final Logger logger = Logger.getLogger(DSSpreadsheetService.class);

    @Autowired
    NameService nameService;

    @Autowired
    ValueService valueService;
    @Autowired
    MemoryDBManager memoryDBManager;

    @Autowired
    ServletContext servletContext;

    @Autowired
    DSImportService importService;

    public final StringUtils stringUtils;

    // todo, clean this up when sessions are expired, maybe a last accessed time?
    private final Map<String, StringBuffer> sessionLogs;

    public DSSpreadsheetService() {
        String thost = ""; // Currently just to put in the log
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // may as well in case it goes wrong
        }
        System.out.println("host : " + thost);
        stringUtils = new StringUtils();
        sessionLogs = new ConcurrentHashMap<>(); // may as well make concurrent to be safe.
    }

    // after some thinking trim this down to the basics. Would have just been a DB name for that server but need permissions too.
    // may cache in future to save DB/Permission lookups. Depends on how consolidated client/server calls can be made . . .

    public AzquoMemoryDBConnection getConnectionFromAccessToken(DatabaseAccessToken databaseAccessToken) throws Exception {
        // todo - address opendb count (do we care?) and exceptions
        StringBuffer sessionLog = sessionLogs.get(databaseAccessToken.getUserSessionId());
        if (sessionLog == null) {
            final StringBuffer newSessionLog = new StringBuffer();
            sessionLog = sessionLogs.putIfAbsent(databaseAccessToken.getUserSessionId(), newSessionLog);// in ConcurrentHashMap this is atomic, thanks Doug!
            if (sessionLog == null) {// the new one went in, use it, otherwise use the one that "sneaked" in there in the mean time :)
                sessionLog = newSessionLog;
            }
        }
        AzquoMemoryDB memoryDB = memoryDBManager.getAzquoMemoryDB(databaseAccessToken.getDatabaseMySQLName(), sessionLog);
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

 we're not permuting, just saying "here is what that 2d excel region looks like in names"

     */

    private List<List<List<DataRegionHeading>>> createHeadingArraysFromSpreadsheetRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<List<String>> headingRegion, List<String> attributeNames) throws Exception {
        return createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, headingRegion, attributeNames, 0);
    }

    // now has the option to exception based on large sets being returned by parse query. Used currently on columns, if these name sets are more than a few hundred then that's clearly unworkable.

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
                        row.add(Collections.singletonList(new DataRegionHeading(sourceCell, true))); // we say that an attribute heading defaults to writable, it will defer to the name
                    } else {
                        DataRegionHeading.BASIC_RESOLVE_FUNCTION function = null;// that's sum practically speaking
                        // now allow functions
                        for (DataRegionHeading.BASIC_RESOLVE_FUNCTION basic_resolve_function : DataRegionHeading.BASIC_RESOLVE_FUNCTION.values()) {
                            if (sourceCell.toUpperCase().startsWith(basic_resolve_function.name())) {
                                function = basic_resolve_function;
                                sourceCell = sourceCell.substring(sourceCell.indexOf("(") + 1, sourceCell.trim().length() - 1);// +1 - 1 to get rid of the brackets
                            }
                        }
                        String NAMECOUNT = "NAMECOUNT";
                        String TOTALNAMECOUNT = "TOTALNAMECOUNT";
                        String VALUEPARENTCOUNT = "VALUEPARENTCOUNT";
                        // for these two I need the descriptions so I can make a useful cache key for counts
                        if (sourceCell.toUpperCase().startsWith(NAMECOUNT)) {
                            // should strip off the function
                            sourceCell = sourceCell.substring(sourceCell.indexOf("(", NAMECOUNT.length()) + 1); // chop off the beginning
                            sourceCell = sourceCell.substring(0, sourceCell.indexOf(")"));
                            // ok these could be heavy so let's cache them, feels a bit dodgy but we'll see how it goes.
                            Set<Name> nameCountSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(sourceCell);
                            if (nameCountSet == null) {
                                nameCountSet = HashObjSets.newMutableSet();
                                nameService.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames, nameCountSet); // namecount set will be populated internally - the last parameter passed
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(sourceCell, nameCountSet);
                            }
                            List<DataRegionHeading> forNameCount = new ArrayList<>();
                            forNameCount.add(new DataRegionHeading(null, false, function, nameCountSet, null, sourceCell));
                            row.add(forNameCount);
                        } else if (sourceCell.toUpperCase().startsWith(TOTALNAMECOUNT)) {
                            // should strip off the function
                            sourceCell = sourceCell.substring(sourceCell.indexOf("(", NAMECOUNT.length()) + 1); // chop off the beginning
                            sourceCell = sourceCell.substring(0, sourceCell.indexOf(")"));
                            String selectionType = sourceCell.substring(0, sourceCell.indexOf(","));
                            // edd trying some caching
                            // this type set is an odd one - we on;y use the first one. Which would mean it's eithe a vanilla name or an as set in which case caching may not be a thing. TODO
                            Set<Name> typeSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(selectionType);
                            if (typeSet == null) {
                                typeSet = HashObjSets.newMutableSet();
                                nameService.parseQuery(azquoMemoryDBConnection, selectionType, attributeNames, typeSet); // namecount set will be populated internally - the last parameter passed
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(sourceCell, typeSet);
                            }
                            if (typeSet.size() != 1) {
                                throw new Exception("selection types must be single cells = use 'as'");
                            }
                            String secondSet = sourceCell.substring(sourceCell.indexOf(",") + 1);
                            Set<Name> selectionSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(secondSet);
                            if (selectionSet == null) {
                                selectionSet = HashObjSets.newMutableSet();
                                nameService.parseQuery(azquoMemoryDBConnection, secondSet, attributeNames, selectionSet); // namecount set will be populated internally - the last parameter passed
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(secondSet, selectionSet);
                            }
                            List<DataRegionHeading> forNameCount = new ArrayList<>();
                            forNameCount.add(new DataRegionHeading(typeSet.iterator().next(), false, function, selectionSet, null, sourceCell));
                            row.add(forNameCount);
                        } else if (sourceCell.toUpperCase().startsWith(VALUEPARENTCOUNT)) { // similar to above in terms of gathering info - could be a normaliseation later for the moment make it work
                            // should strip off the function
                            sourceCell = sourceCell.substring(sourceCell.indexOf("(", VALUEPARENTCOUNT.length()) + 1); // chop off the beginning
                            sourceCell = sourceCell.substring(0, sourceCell.indexOf(")"));
                            String selectionType = sourceCell.substring(0, sourceCell.indexOf(","));
                            // edd trying some caching
                            Set<Name> typeSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(selectionType);
                            if (typeSet == null) {
                                typeSet = HashObjSets.newMutableSet();
                                nameService.parseQuery(azquoMemoryDBConnection, selectionType, attributeNames, typeSet);
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(sourceCell, typeSet);
                            }
                            if (typeSet.size() != 1) {
                                throw new Exception("selection types must be single cells = use 'as'");
                            }
                            String secondSet = sourceCell.substring(sourceCell.indexOf(",") + 1);
                            Set<Name> selectionSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(secondSet);
                            if (selectionSet == null) {
                                selectionSet = HashObjSets.newMutableSet();
                                nameService.parseQuery(azquoMemoryDBConnection, secondSet, attributeNames, selectionSet); // namecount set will be populated internally - the last parameter passed
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(secondSet, selectionSet);
                            }
                            List<DataRegionHeading> forNameCount = new ArrayList<>();
                            // same as above but put the set in a different place for detection later
                            forNameCount.add(new DataRegionHeading(typeSet.iterator().next(), false, function, null, selectionSet, sourceCell));
                            row.add(forNameCount);
                        } else { // most of the time it will be a vanilla query
                            final Collection<Name> names = nameService.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames);
                            if (namesQueryLimit > 0 && names.size() > namesQueryLimit) {
                                throw new Exception("While creating headings " + sourceCell + " resulted in " + names.size() + " names, more than the specified limit of " + namesQueryLimit);
                            }
                            row.add(dataRegionHeadingsFromNames(names, azquoMemoryDBConnection, function));
                        }
                    }
                }
            }
        }
        return nameLists;
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

    I tried optimising based on calculating the size of the arraylist that would be required but it seemed to provide little additional benefit,
     plus I don't think this is a big performance bottleneck. Commented attempt at optimising will be in SVN if required.
    */

    private <T> List<List<T>> get2DPermutationOfLists(final List<List<T>> listsToPermute) {
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

    /* so say we already have
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
    and the nested lists in teh returned list will be one bigger, featuring the new dimension

    if we looked at the above as a reference it would be 3 times as high and 1 column wider
     */


    private <T> List<List<T>> get2DArrayWithAddedPermutation(final List<List<T>> existing2DArray, List<T> permutationWeWantToAdd) {
        List<List<T>> toReturn = new ArrayList<>(existing2DArray.size() * permutationWeWantToAdd.size());// think that's right
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

    The dynamic namecalls e.g. Seaports; children; have their lists populated but they have not been expanded out into the 2d set itself

    So in the case of row headings for the export example it's passed a list or 1 (the outermost list of row headings defined)

    and this 1 has a list of 2, the two cells defined in row headings as seaports; children     container;children

      We want to return a list<list> being row, cells in that row, the expansion of each of the rows. The source in my example being ony one row with 2 cells in it
      those two cells holding lists of 96 and 4 hence we get 384 back, each with 2 cells.

    RIGHT!

    I know what the function that used to be called blank col is for. Heading definitions are a region
    if this region is more than on column wide then rows which have only one cell populated and that cell is the right most cell
    then that cell is added to the cell above it, it becomes part of that permutation.

    That logic just described is the bulk of the function I think. Permutation is handed off to get2DPermutationOfLists, then those permuted lists are simply stacked together.

     */

    private List<List<DataRegionHeading>> expandHeadings(final List<List<List<DataRegionHeading>>> headingLists) {
        final int noOfHeadingDefinitionRows = headingLists.size();
        if (noOfHeadingDefinitionRows == 0) {
            return new ArrayList<>();
        }
        final int lastHeadingDefinitionCellIndex = headingLists.get(0).size() - 1; // the headingLists will be square, that is to say all row lists the same length as prepared by createHeadingArraysFromSpreadsheetRegion
        // ok here's the logic, what's passed is a 2d array of lists, as created from createHeadingArraysFromSpreadsheetRegion
        // we would just run through the rows running a 2d permutation on each row BUT there's a rule that if there's
        // a row below blank except the right most one then add that right most one to the one above
        boolean starting = true;
        // ok the reason I'm doing this is to avoid unnecessary array copying and resizing. If the size is 1 can just return that otherwise create an arraylist of the right size and copy in
        ArrayList<List<List<DataRegionHeading>>> permutedLists = new ArrayList<>(noOfHeadingDefinitionRows); // could be slightly less elements than this but it's unlikely to be a biggy.
        for (int headingDefinitionRowIndex = 0; headingDefinitionRowIndex < noOfHeadingDefinitionRows; headingDefinitionRowIndex++) {
            List<List<DataRegionHeading>> headingDefinitionRow = headingLists.get(headingDefinitionRowIndex);
            if (lastHeadingDefinitionCellIndex > 0 && !headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex)))
                starting = false;// Don't permute until you have something to permute!
            while (!starting && headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 // we're not on the last row
                    && headingLists.get(headingDefinitionRowIndex + 1).size() > 1 // the next row is not a single cell (will all rows be the same length?)
                    && headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex + 1)) // and the last cell is the only not null one
                    ) {
                if (headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex) == null) {
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).add(null);
                } else {
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                }
                headingDefinitionRowIndex++;
            }
            List<List<DataRegionHeading>> permuted = get2DPermutationOfLists(headingDefinitionRow);
            permutedLists.add(permuted);
        }
        if (permutedLists.size() == 1) { // it may often be
            return permutedLists.get(0);
        }
        // the point is I only want to make a new arraylist if there are lists to be combined and then I want it to be the right size. Some reports have had millions in here . . .
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
        return true; // All null - treat as last cell populated
    }

    // to help making names on a dropdown.
    static class UniqueName {
        Name topName; // often topName is name and the description will just be left as the basic name
        String description; // when the name becomes qualified the description will become name, parent, parent of parent etc. And top name will be the highest parent, held in case we need to qualify up another level.

        public UniqueName(Name topName, String description) {
            this.topName = topName;
            this.description = description;
        }
    }

    private boolean qualifyUniqueNameWithParent(UniqueName uName) {
        Name name = uName.topName;
        boolean changed = false;
        for (Name parent : name.getParents()) {
            //check that there are no names with the same name for this shared parent. Otherwise the description/name combo would be non unique.
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
                // the key here is that whatever is in description can be used to look up a name. Used to be reverse orcer comma separated, now use new member of notation
                uName.description =  parent.getDefaultDisplayName() + StringUtils.MEMBEROF + uName.description;
                uName.topName = parent;
                break; // no point continuing I guess, the unique name has a further unambiguous qualifier
            }
        }
        return changed;
    }

    // for the drop down, essentially given a collection of names for a query need to give a meaningful list qualifying names with parents where they are duplicates (I suppose high streets in different towns)
    // it was assumed that names were sorted, one can't guarantee this though preserving the order is important. EFC going to rewrite, won't require ordering
    private List<String> getUniqueNameStrings(Collection<Name> names) {
        List<UniqueName> toCheck = names.stream().map(name -> new UniqueName(name, name.getDefaultDisplayName())).collect(Collectors.toList()); // java 8 should be ok here, basically copy the names to unique names to check
        int triesLeft = 10; // just in case
        boolean keepChecking = true;
        while (triesLeft >= 0 && keepChecking){
            keepChecking = false; // set to false, only go around again if something below changes the list of unique names
            Set<String> descriptionDuplicateCheck = HashObjSets.newMutableSet();
            Set<String> duplicatedDescriptions = HashObjSets.newMutableSet();
            for (UniqueName uniqueName : toCheck){
                if (descriptionDuplicateCheck.contains(uniqueName.description)){ // we have this description already
                    duplicatedDescriptions.add(uniqueName.description); // so add it to the list of descriptions to qualify further
                }
                descriptionDuplicateCheck.add(uniqueName.description);
            }
            if (!duplicatedDescriptions.isEmpty()){ // there are duplicates, try to sort it
                for (UniqueName uniqueName : toCheck){ // run through the list again
                    if (duplicatedDescriptions.contains(uniqueName.description)){ // ok this is one of the ones that needs sorting
                        if (qualifyUniqueNameWithParent(uniqueName)){ // try to sort the name
                            keepChecking = true; // something changed
                        }
                    }
                }
            }
            triesLeft--;
        }
        return toCheck.stream().map(uniqueName -> uniqueName.description).collect(Collectors.toList()); // return the descriptions, that's what we're after, in many cases this may have been copied into unique names, not modified and copied back but that fine
    }

    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices)
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
        return getUniqueNameStrings(nameService.parseQuery(getConnectionFromAccessToken(databaseAccessToken), query, languages));
    }

    // it doens't return anything for things like setting up "as" criteria
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
    this is because the expand headings function is orientated for for headings and the column heading definitions are unsurprisingly set up for columns
     what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

     NOTE : this means the column heading are not stored according to the orientation used in the above function

      hence, to output them we have to transpose them again!

    OK, having generified the function we should only need one function. The list could be anything, names, list of names, hashmaps whatever
    generics ensure that the return type will match the sent type
    now rather similar to the stacktrace example :)

    Variable names assume first list is of rows and the second is each row. down then across.
    So the size of the first list is the ysize (number of rows) and the size of the nested list the xsize (number of columns)
    I'm going to model it that way round as when reading data from excel that's the default (we go line by line through each row, that's how the data is delivered), the rows is the outside list
    of course could reverse all descriptions and teh function could still work

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

    // return headings as strings for display, I'm going to put blanks in here if null.

    public List<List<String>> convertDataRegionHeadingsToStrings(List<List<DataRegionHeading>> source, List<String> languagesSent) {
        List<String> languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);//for displaying headings always look for DEFAULT_DISPLAY_NAME first - otherwise may look up the chain for local names
        languages.addAll(languagesSent);

        List<List<String>> toReturn = new ArrayList<>(source.size());
        for (List<DataRegionHeading> row : source) {
            List<String> returnRow = new ArrayList<>(row.size());
            toReturn.add(returnRow);
            for (DataRegionHeading heading : row) {
                String cellValue = null;
                if (heading != null) {
                    Name name = heading.getName();
                    if (name != null) {
                        for (String language : languages) {
                            if (name.getAttribute(language) != null) {
                                cellValue = name.getAttribute(language);
                                break;
                            }
                        }
                        if (cellValue == null) {
                            cellValue = name.getDefaultDisplayName();
                        }
                    } else {
                        cellValue = heading.getAttribute();
                    }
                }
                returnRow.add(cellValue != null ? cellValue : "");
            }
        }
        return toReturn;
    }

    // should be called before each report request

    public void clearLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.clearUserLog(); // clear I guess?
    }

    // to try to force an exception to stop execution

    public void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setStopInUserLog();
    }

    // function that can be called by the front end to deliver the data and headings

    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int filterCount, int maxRows, int maxCols
            , String sortRow, boolean sortRowAsc, String sortCol, boolean sortColAsc, int highlightDays) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        List<List<AzquoCell>> data = getDataRegion(azquoMemoryDBConnection, regionName, rowHeadingsSource, colHeadingsSource, contextSource, filterCount, maxRows, maxCols, sortRow, sortRowAsc, sortCol, sortColAsc, databaseAccessToken.getLanguages(), highlightDays);
        if (data.size() == 0) {
            return new CellsAndHeadingsForDisplay(colHeadingsSource, null, new ArrayList<>(), null, colHeadingsSource, null);
        }
        List<List<CellForDisplay>> displayData = new ArrayList<>(data.size());
        for (List<AzquoCell> sourceRow : data) {
            List<CellForDisplay> displayDataRow = new ArrayList<>(sourceRow.size());
            displayData.add(displayDataRow);
            for (AzquoCell sourceCell : sourceRow) {
                // I suppose a little overhead from this - if it's a big problem can store lists of ignored rows and cols above and use that
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
                displayDataRow.add(new CellForDisplay(sourceCell.isLocked(), sourceCell.getStringValue(), sourceCell.getDoubleValue(), sourceCell.isHighlighted(), sourceCell.getUnsortedRow(), sourceCell.getUnsortedCol(), ignored));
            }
        }
        //AzquoMemoryDB.printAllCountStats();
        //AzquoMemoryDB.clearAllCountStats();
        // this is single threaded as I assume not much data should be returned. Need to think about this.
        return new CellsAndHeadingsForDisplay(convertDataRegionHeadingsToStrings(getColumnHeadingsAsArray(data), databaseAccessToken.getLanguages())
                , convertDataRegionHeadingsToStrings(getRowHeadingsAsArray(data), databaseAccessToken.getLanguages()), displayData, rowHeadingsSource, colHeadingsSource, contextSource);
    }

    long threshold = 1000;// we log if a section takes longer

    private List<List<AzquoCell>> getDataRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, boolean sortRowAsc, String sortCol, boolean sortColAsc, List<String> languages, int highlightDays) throws Exception {
        azquoMemoryDBCOnnection.addToUserLog("Getting data for region : " + regionName);
        long track = System.currentTimeMillis();
        long start = track;
        final List<List<List<DataRegionHeading>>> rowHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, rowHeadingsSource, languages);
        long time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<DataRegionHeading>> rowHeadings = expandHeadings(rowHeadingLists);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<List<DataRegionHeading>>> columnHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages, COL_HEADINGS_NAME_QUERY_LIMIT);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<DataRegionHeading>> columnHeadings = expandHeadings(transpose2DList(columnHeadingLists));
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
            //return new ArrayList<>();
        }
        final List<Name> contextNames = new ArrayList<>();
        for (List<String> contextItems : contextSource) { // context is flattened and it has support for carriage returned lists in a single cell. Rather arbitrary, remove at some point? Also should be using get context names?
            for (String contextItem : contextItems) {
                final StringTokenizer st = new StringTokenizer(contextItem, "\n");
                while (st.hasMoreTokens()) {
                    final Collection<Name> thisContextNames = nameService.parseQuery(azquoMemoryDBCOnnection, st.nextToken().trim(), languages);
                    time = (System.currentTimeMillis() - track);
                    if (time > threshold) System.out.println("Context parsed in " + time + "ms");
                    track = System.currentTimeMillis();
                    if (thisContextNames.size() > 1) {
                        throw new Exception("error: context names must be individual - use 'as' to put sets in context");
                    }
                    if (thisContextNames.size() > 0) {
                        //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                        contextNames.add(thisContextNames.iterator().next());
                    }
                }
            }
        }
        // note, didn't se the context against the logged in connection, should I?
        // ok going to try to use the new function
        List<List<AzquoCell>> dataToShow = getAzquoCellsForRowsColumnsAndContext(azquoMemoryDBCOnnection, rowHeadings
                , columnHeadings, contextNames, languages);
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

    private List<Name> getContextNames(AzquoMemoryDBConnection azquoMemoryDBConnection, List<List<String>> contextSource, List<String> languages) throws Exception {
        final List<Name> contextNames = new ArrayList<>();
        if (contextSource == null) return contextNames;
        for (List<String> contextItems : contextSource) { // context is flattened and it has support for carriage returned lists in a single cell
            for (String contextItem : contextItems) {
                final StringTokenizer st = new StringTokenizer(contextItem, "\n");
                while (st.hasMoreTokens()) {
                    final Collection<Name> thisContextNames = nameService.parseQuery(azquoMemoryDBConnection, st.nextToken().trim(), languages);
                    if (thisContextNames.size() > 1) {
                        throw new Exception("error: context names must be individual - use 'as' to put sets in context");
                    }
                    if (thisContextNames.size() > 0) {
                        //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                        contextNames.add(thisContextNames.iterator().next());
                    }
                }
            }
        }
        return contextNames;
    }

    // when doing things like saving/provenance the client needs to say "here's a region description and original position" to locate a cell server side

    private AzquoCell getSingleCellFromRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int unsortedRow, int unsortedCol, List<String> languages) throws Exception {
        // these 25 lines or so are used elsewhere, maybe normalise?
        final List<List<List<DataRegionHeading>>> rowHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, rowHeadingsSource, languages);
        final List<List<DataRegionHeading>> rowHeadings = expandHeadings(rowHeadingLists);
        final List<List<List<DataRegionHeading>>> columnHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages, COL_HEADINGS_NAME_QUERY_LIMIT); // same as standard limit for col headings
        final List<List<DataRegionHeading>> columnHeadings = expandHeadings(transpose2DList(columnHeadingLists));
        if (columnHeadings.size() == 0 || rowHeadings.size() == 0) {
            return null;
        }
        final List<Name> contextNames = getContextNames(azquoMemoryDBCOnnection, contextSource, languages);
        // now onto the bit to find the specific cell - the column headings were transposed then expanded so they're in the same format as the row headings
        // that is to say : the outside list's size is the number of columns or headings. So, do we have the row and col?
        if (unsortedRow < rowHeadings.size() && unsortedCol < columnHeadings.size()) {
            return getAzquoCellForHeadings(azquoMemoryDBCOnnection, rowHeadings.get(unsortedRow), columnHeadings.get(unsortedCol), contextNames, unsortedRow, unsortedCol, languages);
        }
        return null; // couldn't find it . . .
    }

    // for looking up a heading given a string. Used to find the col/row index to sort on

    private int findPosition(List<List<DataRegionHeading>> headings, String toFind, List<String> languages) {
         if (toFind == null || toFind.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(toFind);

        }catch(Exception e){
            //so it isn't a number!
        }
        toFind = toFind.replace(" ","").replace("`","").toLowerCase();
        int count = 0;
        for (List<DataRegionHeading> heading : headings) {
            DataRegionHeading dataRegionHeading = heading.get(heading.size() - 1);
            if (dataRegionHeading != null) {
                String toCompare;
                if (dataRegionHeading.getName() != null) {
                    // ok now we need to deal with the languages! This function didn't before
                    for (String language : languages){
                        String languageValue = dataRegionHeading.getName().getAttribute(language);
                        // just run through the relevant languages looking for this column
                        if (languageValue != null &&  languageValue.replace(" ","").toLowerCase().equals(toFind)) {
                            return count;
                        }
                    }
                } else {
                    if (dataRegionHeading.getAttribute().replace(" ","").toLowerCase().equals(toFind)) {
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
        long track = System.currentTimeMillis();
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


        if (sortOnColIndex != -1 || sortOnRowIndex != -1 || sortOnColTotals || sortOnRowTotals) { // then there's no sorting to do!
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
            /* ok here's a thing . . . I think this code that used to chop didn't take into account row sorting. Should be pretty easy to just do here I think
            to be clear what this is doing is checking for chunks of blank rows and remoiving them from the results - worth noting that this doesn't compensate for the max,
            one could end up with less than the max when there was more data available as the max was loaded then chopped. On the other hand typical max ordering would mean that
            by this point it would all be blank rows anyway */

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
                          /*
                        if ((cellToCheck.getListOfValuesOrNamesAndAttributeName().getNames() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getNames().isEmpty())
                                || (cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty())) {// there were values or names for the call
                            */
                        //CHECKING VALUES ONLY
                        if (cellToCheck.getListOfValuesOrNamesAndAttributeName() != null && cellToCheck.getListOfValuesOrNamesAndAttributeName().getAttributeNames() == null && cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty()) {// there were values or names for the call
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

    */

    public class RowFiller implements Runnable {
        private final int startRow;
        private final int endRow;
        private final List<List<AzquoCell>> targetArray;
        private final List<List<DataRegionHeading>> headingsForEachColumn;
        private final List<List<DataRegionHeading>> headingsForEachRow;
        private final List<Name> contextNames;
        private final List<String> languages;
        private final AzquoMemoryDBConnection connection;
        private final StringBuffer errorTrack;
        private final AtomicInteger counter;
        private final int progressBarStep;

        public RowFiller(int startRow, int endRow, List<List<AzquoCell>> targetArray, List<List<DataRegionHeading>> headingsForEachColumn, List<List<DataRegionHeading>> headingsForEachRow
                , List<Name> contextNames, List<String> languages, AzquoMemoryDBConnection connection, StringBuffer errorTrack, AtomicInteger counter, int progressBarStep) {
            this.startRow = startRow;
            this.endRow = endRow;
            this.targetArray = targetArray;
            this.headingsForEachColumn = headingsForEachColumn;
            this.headingsForEachRow = headingsForEachRow;
            this.contextNames = contextNames;
            this.languages = languages;
            this.connection = connection;
            this.errorTrack = errorTrack;
            this.counter = counter;
            this.progressBarStep = progressBarStep;
        }

        @Override
        public void run() {
            try {
                //System.out.println("Filling " + startRow + " to " + endRow);
                for (int rowNo = startRow; rowNo <= endRow; rowNo++) {
                    List<DataRegionHeading> rowHeadings = headingsForEachRow.get(rowNo);
                    List<AzquoCell> returnRow = new ArrayList<>(headingsForEachColumn.size());
                    int colNo = 0;
                    for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                        // values I need to build the CellUI
                        returnRow.add(getAzquoCellForHeadings(connection, rowHeadings, columnHeadings, contextNames, rowNo, colNo, languages));
                        // for some reason this was before, it buggered up the ability to find the right column!
                        colNo++;
                        if (counter.incrementAndGet() % progressBarStep == 0) {
                            connection.addToUserLog("=", false);
                        }
                    }
                    targetArray.set(rowNo, returnRow);
                }
            } catch (Exception e) {
                errorTrack.append("RowFiller : ").append(e.getMessage()).append("\n");
                for (StackTraceElement ste : e.getStackTrace()) {
                    errorTrack.append("RowFiller : ").append(ste).append("\n");
                }
            }
        }
    }

    // for less than 1000 rows, probably typical use. On damart for example we had 26*9 taking a while and it was reasonable to assume that rows were not even in terms of processing required
    public class CellFiller implements Runnable {
        private final int row;
        private final int col;
        private final List<AzquoCell> targetRow;
        private final List<DataRegionHeading> headingsForColumn;
        private final List<DataRegionHeading> headingsForRow;
        private final List<Name> contextNames;
        private final List<String> languages;
        private final AzquoMemoryDBConnection connection;
        private final StringBuffer errorTrack;
        private final AtomicInteger counter;
        private final int progressBarStep;

        public CellFiller(int row, int col, List<AzquoCell> targetRow, List<DataRegionHeading> headingsForColumn, List<DataRegionHeading> headingsForRow,
                          List<Name> contextNames, List<String> languages, AzquoMemoryDBConnection connection, StringBuffer errorTrack, AtomicInteger counter, int progressBarStep) {
            this.row = row;
            this.col = col;
            this.targetRow = targetRow;
            this.headingsForColumn = headingsForColumn;
            this.headingsForRow = headingsForRow;
            this.contextNames = contextNames;
            this.languages = languages;
            this.connection = connection;
            this.errorTrack = errorTrack;
            this.counter = counter;
            this.progressBarStep = progressBarStep;
        }

        @Override
        public void run() {
            try {
                // connection.addToUserLog(".", false);
                // for some reason this was before, it buggered up the ability to find the right column!
                targetRow.set(col, getAzquoCellForHeadings(connection, headingsForRow, headingsForColumn, contextNames, row, col, languages));
                if (counter.incrementAndGet() % progressBarStep == 0) {
                    connection.addToUserLog("=", false);
                }
            } catch (Exception e) {
                errorTrack.append("CellFiller : ").append(e.getMessage()).append("\n");
                for (StackTraceElement ste : e.getStackTrace()) {
                    errorTrack.append("CellFiller : ").append(ste).append("\n");
                }
            }
        }
    }

    /* only called by itself and total name count, we want to know the number of intersecting names between containsSet
    and all its children and selectionSet, recursive to deal with containsSet down to the bottom. alreadyTested is
    simply to stop checking names that have already been checked.

    Worth pointing out that findOverlap for Name Count is a total of unique names and hence can use a contains on findAllChildren which returns a set
     whereas this, on the other hand, requires the non unique intersection, a single name could contribute to the count more than once by bing in more than one set.

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

    /* the total name count function is the intersection count of its first parameter (a name) and all it's children and the second parameter which is a set
     the key being that that second parameter set is modified by being intersected with the row heading and all its children
     so number of intersections between the first parameter and all its children and (the second parameter set intersected with the row name and all its children)
     */

    private int getTotalNameCount(AzquoMemoryDBConnection connection, Set<DataRegionHeading> headings) {
        String cacheKey = "";
        for (DataRegionHeading heading : headings) {
            if (heading.getDescription() != null) {
                cacheKey += heading.getDescription();
            } else if (heading.getName() != null) {
                cacheKey += heading.getName().getDefaultDisplayName();
            }
        }
        Integer cached = connection.getAzquoMemoryDB().getCountFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        Name memberSet = null;
        Name containsSet = null;
        Set<Name> selectionSet = null;
        for (DataRegionHeading heading : headings) {
            if (heading.getName() != null) {
                if (heading.getNameCountSet() != null) { // the heading with the function, typically a column heading I think
                    containsSet = heading.getName(); // the first parameter of the function in the report  - a single name but it could well be defined in an "as"
                    selectionSet = heading.getNameCountSet(); // the second parameter of the function in the report, a set
                } else { // a vanilla name heading, typically a row heading
                    memberSet = heading.getName();
                }
            }
        }
        int toReturn = 0;
        Set<Name> alreadyTested = HashObjSets.newMutableSet();
        // todo - alternative to retainall again? Creation of new set?
        if (containsSet != null && memberSet != null) {
            Set<Name> remainder = HashObjSets.newMutableSet(selectionSet); // so take the second function parameter set
            remainder.retainAll(memberSet.findAllChildren(false)); // intersect with the row heading name and all it's children
            toReturn = totalSetIntersectionCount(containsSet, remainder, alreadyTested, 0); // and get the set intersection of that and the first parameter and all its children
        }
        connection.getAzquoMemoryDB().setCountInCache(cacheKey, toReturn);
        return toReturn;
    }

    // hacky, I just need a way to pass the values without doing a redundant addAll
    public static class ValuesHook {
        public List<Value> values = null;
    }

    // factored this off to enable getting a single cell, also useful to be called from the multi threading

    private AzquoCell getAzquoCellForHeadings(AzquoMemoryDBConnection connection, List<DataRegionHeading> rowHeadings, List<DataRegionHeading> columnHeadings
            , List<Name> contextNames, int rowNo, int colNo, List<String> languages) throws Exception {
        String stringValue = "";
        double doubleValue = 0;
        Set<DataRegionHeading> headingsForThisCell = HashObjSets.newMutableSet(rowHeadings.size()); // I wonder a little how important having them as sets is . . .
        Set<DataRegionHeading> rowAndColumnHeadingsForThisCell = null;
        ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName = null;
        //check that we do have both row and column headings, otherwise blank them the cell will be blank (danger of e.g. a sum on the name "Product"!)
        for (DataRegionHeading heading : rowHeadings) {
            if (heading != null && (heading.getName() != null || (heading.getAttribute() != null && !heading.getAttribute().equals(".")) || heading.getNameCountSet() != null)) {
                headingsForThisCell.add(heading);
            }
        }
        int hCount = headingsForThisCell.size();
        boolean checked = true;
        if (hCount > 0) {
            for (DataRegionHeading heading : columnHeadings) {
                if (heading != null && (heading.getName() != null || (heading.getAttribute() != null && !heading.getAttribute().equals(".")) || heading.getNameCountSet() != null)) {
                    headingsForThisCell.add(heading);
                }
            }
            rowAndColumnHeadingsForThisCell = new HashSet<>(headingsForThisCell);
            if (headingsForThisCell.size() > hCount) {
                headingsForThisCell.addAll(dataRegionHeadingsFromNames(contextNames, connection, null)); // no functions (including namecount) for context
            } else {
                headingsForThisCell.clear();
                checked = false;
            }
        }
        MutableBoolean locked = new MutableBoolean(); // we use a mutable boolean as the functions that resolve the cell value may want to set it
        for (DataRegionHeading heading : headingsForThisCell) {
            if (heading.getName() == null && heading.getAttribute() == null && heading.getNameCountSet() == null) {
                checked = false;
            }
            if (!heading.isWriteAllowed()) { // this replaces the isallowed check that was in the functions that resolved the cell values
                locked.isTrue = true;
            }
        }
        if (!checked) { // not a valid peer set? Lock and no values set (blank)
            locked.isTrue = true;
        } else {
            // ok new logic here, we need to know if we're going to use attributes or values or namecount!
            DataRegionHeading.BASIC_RESOLVE_FUNCTION function = null;
            for (DataRegionHeading heading : headingsForThisCell) {
                if (heading.getFunction() != null) {
                    function = heading.getFunction();
                }
            }
            DataRegionHeading nameCountHeading = getHeadingWithNameCount(headingsForThisCell);
            DataRegionHeading valueParentCountHeading = getHeadingWithValueParentCount(headingsForThisCell);
            if (nameCountHeading != null && headingsForThisCell.size() == 2) {//these functions only work if there's no context
                if (nameCountHeading.getName() != null) {
                    //System.out.println("going for total name set " + nameCountHeading.getNameCountSet().size() + " name we're using " + nameCountHeading.getName());
                    doubleValue = getTotalNameCount(connection, headingsForThisCell);
                } else {// without the name set that means name count - practically speaking after dealing with the caching this is really about calling findOverlap with the set and all the children of the row
                    Set<Name> nameCountSet = nameCountHeading.getNameCountSet();
                    doubleValue = 0.0;
                    for (DataRegionHeading dataRegionHeading : headingsForThisCell) {
                        if (dataRegionHeading != nameCountHeading && dataRegionHeading.getName() != null) { // should be the non function heading, the row heading with a name
                            // we know this is a cached set internally, no need to create a new set - might be expensive
                            Collection<Name> nameCountSet2 = dataRegionHeading.getName().findAllChildren(false);
                            String cacheKey = nameCountHeading.getDescription() + dataRegionHeading.getName().getDefaultDisplayName();
                            Integer cached = connection.getAzquoMemoryDB().getCountFromCache(cacheKey);
                            if (cached != null) {
                                doubleValue = cached;
                            } else {
                                int valueAsInt;
                                if (nameCountSet.size() < nameCountSet2.size()) {
                                    valueAsInt = findOverlap(nameCountSet, nameCountSet2);
                                } else {
                                    valueAsInt = findOverlap(nameCountSet2, nameCountSet);
                                }
                                connection.getAzquoMemoryDB().setCountInCache(cacheKey, valueAsInt);
                                doubleValue = valueAsInt;
                            }
                        }
                    }
                }
                stringValue = doubleValue + "";
                // again this block copy pasted from above to make valueparent count work,
            } else if (valueParentCountHeading != null && valueParentCountHeading.getName() != null) {//this will work with context
                ValuesHook valuesHook = new ValuesHook();
                valueService.findValueForNames(connection, namesFromDataRegionHeadings(headingsForThisCell), locked, true, valuesHook, languages, function);// so resolve as we usually would to get the values
                // now, find all the parents and cross them with the valueParentCountHeading set
                Set<Name> allValueParents = HashObjSets.newMutableSet();
                for (Value v : valuesHook.values){
                    for (Name n : v.getNames()){
                        allValueParents.add(n); // add the name
                        allValueParents.addAll(n.findAllParents()); // and all it's parents
                    }
                }
                // now find the overlap between the value parents and the set in the heading
                if (valueParentCountHeading.getValueParentCountSet().size() < allValueParents.size()) {
                    doubleValue = findOverlap(valueParentCountHeading.getValueParentCountSet(), allValueParents);
                } else {
                    doubleValue = findOverlap(allValueParents, valueParentCountHeading.getValueParentCountSet());
                }
                stringValue = doubleValue + "";
            } else if (!headingsHaveAttributes(headingsForThisCell)) { // we go the value route (the standard/old one), need the headings as names,
                ValuesHook valuesHook = new ValuesHook();
                // now , get the function from the headings
                if (function != null) {
                    locked.isTrue = true;
                }
                if (headingsForThisCell.size() > 0) {
                    doubleValue = valueService.findValueForNames(connection, namesFromDataRegionHeadings(headingsForThisCell), locked, true, valuesHook, languages, function); // true = pay attention to names additive flag
                    //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                    if (valuesHook.values.size() == 1 && !locked.isTrue) {

                        Value value = valuesHook.values.get(0);
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
            } else {  // now, new logic for attributes
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
                // ZK would want this typed? Maybe just sort out later? EFC later : what does this mean?
                if (attributeResult != null) {
                    attributeResult = attributeResult.replace("\n", "<br/>");//unsatisfactory....
                    stringValue = attributeResult;
                } else {
                    stringValue = "";
                }
            }
        }
                /* something to note : in the old model there was a map of headings used for each cell. I could add headingsForThisCell to the cell which would be a unique set for each cell
                 but instead I'll just add the headings and row and context, I think it would be less memory. 3 object references vs a set*/
        return new AzquoCell(locked.isTrue, listOfValuesOrNamesAndAttributeName, rowHeadings, columnHeadings, contextNames, rowNo, colNo, stringValue, doubleValue, false);
    }

    // we were doing retainAll().size() but this is less expensive
    // todo : put the size check of each set and hence which way we run through the loop in here
    private int findOverlap(Collection<Name> set1, Collection<Name> set2) {
        int count = 0;
        for (Name name : set1) {
            if (set2.contains(name)) count++;
        }
        return count;
    }

    private List<List<AzquoCell>> getAzquoCellsForRowsColumnsAndContext(AzquoMemoryDBConnection connection, List<List<DataRegionHeading>> headingsForEachRow
            , final List<List<DataRegionHeading>> headingsForEachColumn
            , final List<Name> contextNames, List<String> languages) throws Exception {
        long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        long newHeapMarker = oldHeapMarker;
        long track = System.currentTimeMillis();
        int totalRows = headingsForEachRow.size();
        int totalCols = headingsForEachColumn.size();
        List<List<AzquoCell>> toReturn = new ArrayList<>(totalRows); // make it the right size so multithreading changes the values but not the structure
        for (int i = 0; i < totalRows; i++) {
            toReturn.add(null);// null the rows, basically adding spaces to the return list
        }
        connection.addToUserLog("Size = " + totalRows + " * " + totalCols);
        int maxRegionSize = 2000000;//random!  set by WFC 29/6/15
        if (totalRows * totalCols > maxRegionSize) {
            throw new Exception("error: data region too large - " + totalRows + " * " + totalCols + ", max cells " + maxRegionSize);
        }
        int threads = connection.getAzquoMemoryDB().getReportFillerThreads();
        connection.addToUserLog("Populating using " + threads + " thread(s)");
        ExecutorService executor = Executors.newFixedThreadPool(threads); // picking 10 based on an example I saw . . .
        StringBuffer errorTrack = new StringBuffer();// deliberately thread safe, need to keep an eye on the report building . . .
        // tried multi-threaded, abandoning big chunks
        // different style, just chuck every row in the queue
        int progressBarStep = (totalCols * totalRows) / 50 + 1;
        AtomicInteger counter = new AtomicInteger();
        if (totalRows < 1000) { // arbitrary cut off for the moment
            for (int row = 0; row < totalRows; row++) {
                List<DataRegionHeading> rowHeadings = headingsForEachRow.get(row);
                List<AzquoCell> returnRow = new ArrayList<>(headingsForEachColumn.size());
                for (int i = 0; i < headingsForEachColumn.size(); i++) {
                    returnRow.add(null);// yes a bit hacky, want to make the space that will be used by cellfiller
                }
                int colNo = 0;
                for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                    // inconsistent parameter ordering
                    executor.execute(new CellFiller(row, colNo, returnRow, columnHeadings, rowHeadings, contextNames, languages, connection, errorTrack, counter, progressBarStep));
                    colNo++;
                }
                toReturn.set(row, returnRow);
            }
        } else {
            for (int row = 0; row < totalRows; row++) {
                // row passed twice as
                executor.execute(new RowFiller(row, row, toReturn, headingsForEachColumn, headingsForEachRow, contextNames, languages, connection, errorTrack, counter, progressBarStep));
            }
        }
        if (errorTrack.length() > 0) {
            throw new Exception(errorTrack.toString());
        }
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            throw new Exception("Data region took longer than an hour to load");
        }
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        System.out.println();
        System.out.println("Heap cost to make on multi thread : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();
        //oldHeapMarker = newHeapMarker;
        if (errorTrack.length() > 0) {
            throw new Exception(errorTrack.toString());
        }
        connection.addToUserLog(" time : " + (System.currentTimeMillis() - track) + "ms");
        return toReturn;
    }

    // new logic, derive the headings from the data, no need to resort to resorting etc.
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

    // todo, when cell contents are from attributes??
    public List<TreeNode> getDataRegionProvenance(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        AzquoCell azquoCell = getSingleCellFromRegion(azquoMemoryDBConnection, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, databaseAccessToken.getLanguages());
        if (azquoCell != null) {
            final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
            //Set<Name> specialForProvenance = new HashSet<Name>();
            if (valuesForCell.getValues() != null) {
                return nodify(valuesForCell.getValues(), maxSize);
            }
            if (azquoCell.getRowHeadings().get(0).getAttribute()!=null || azquoCell.getColumnHeadings().get(0).getAttribute()!=null){
                Name cellName = null;
                String attribute = null;
                if (azquoCell.getRowHeadings().get(0).getAttribute()!=null){
                    cellName = azquoCell.getColumnHeadings().get(0).getName();
                    attribute = azquoCell.getRowHeadings().get(0).getAttribute();
                }else{
                    cellName = azquoCell.getRowHeadings().get(0).getName();
                    attribute = azquoCell.getColumnHeadings().get(0).getAttribute();

                }
                return nodify(cellName,attribute);
            }
        }
        return new ArrayList<>(); //just empty ok? null? Unsure
    }

    // for inspect database I think
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
    // not exactly sure why
    public List<TreeNode> nodify(List<Value> values, int maxSize) {
        List<TreeNode> toReturn = new ArrayList<>();
        if (values.size() > 1 || (values.size() > 0 && values.get(0) != null)) {
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


    public List<TreeNode> nodify(Name name, String attribute) {

        attribute = attribute.substring(1).replace("`","");
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



    public void importDataFromSpreadsheet(AzquoMemoryDBConnection azquoMemoryDBConnection, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user) throws Exception {
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
            for (CellForDisplay cellForDisplay : row) {
                if (!firstCol) sb.append("\t");
                else firstCol = false;
                // use string if we have it,otherwise double if it's not 0 or explicitly changed (0 allowed if manually entered). Otherwise blank.
                if (cellForDisplay.getStringValue() != null) {
                    String val = cellForDisplay.getStringValue().length() > 0 ? cellForDisplay.getStringValue() : cellForDisplay.getDoubleValue() != 0 || cellForDisplay.isChanged() ? cellForDisplay.getDoubleValue() + "" : "";
                    //for the moment we're passsing on cells that have not been entered as blanks which are ignored in the importer - this does not leave space for deleting values or attributes
                    sb.append(val);
                }
            }
            bw.write(sb.toString());
            bw.newLine();

        }
        bw.flush();
        bw.close();
        importService.readPreparedFile(azquoMemoryDBConnection, tempName, "csv", Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME), true, true);
        temp.delete(); // see no harm in this here. Delete on exit has a problem with Tomcat being killed.
    }

    // it's easiest just to send the CellsAndHeadingsForDisplay back to the back end and look for relevant changed cells
    public void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user, "in spreadsheet", reportName, context);
        if (cellsAndHeadingsForDisplay.getRowHeadings() == null && cellsAndHeadingsForDisplay.getData().size() > 0) {
            importDataFromSpreadsheet(azquoMemoryDBConnection, cellsAndHeadingsForDisplay, user);
            return;
        }
        int numberOfValuesModified = 0;
        List<Name> contextNames = getContextNames(azquoMemoryDBConnection, cellsAndHeadingsForDisplay.getContextSource(), databaseAccessToken.getLanguages());
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
                        try {
                            if (cell.getDoubleValue() != 0.0) {
                                cell.setStringValue(cell.getDoubleValue() + "");
                            }
                        } catch (Exception ignored) {
                        }
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
                                Set<Name> cellNames = namesFromDataRegionHeadings(headingsForCell);
                                cellNames.addAll(contextNames);
                                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, cell.getStringValue(), cellNames);
                                numberOfValuesModified++;
                            }
                            // warning on multiple values?
                        } else {
                            if (valuesForCell.getNames().size() == 1 && valuesForCell.getAttributeNames().size() == 1) { // allows a simple attribute store
                                Name toChange = valuesForCell.getNames().get(0);
                                String attribute = valuesForCell.getAttributeNames().get(0).substring(1);//remove the initial '.'
                                Name attSet = nameService.findByName(azquoMemoryDBConnection, attribute);
                                if (attSet != null && attSet.hasChildren() && !azquoMemoryDBConnection.getAzquoMemoryDB().attributeExistsInDB(attribute)) {
                                    /* right : when populating attribute based data findParentttributes can be called internally in Name. DSSpreadsheetService is not aware of it but it means (in that case) the data
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

    // Five little utility functions added by Edd, required now headings are not names

    public List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, AzquoMemoryDBConnection azquoMemoryDBConnection, DataRegionHeading.BASIC_RESOLVE_FUNCTION function) {
        List<DataRegionHeading> dataRegionHeadings = new ArrayList<>(names.size()); // names could be big, init the Collection with the right size
        if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()) {
            // then check permissions
            for (Name name : names) {
                // will the new write permissions cause an overhead?
                dataRegionHeadings.add(new DataRegionHeading(name, nameService.isAllowed(name, azquoMemoryDBConnection.getWritePermissions()), function, null, null, null));
            }
        } else { // don't bother checking permissions, write permissions to true
            for (Name name : names) {
                dataRegionHeadings.add(new DataRegionHeading(name, true, function, null, null, null));
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

    public boolean headingsHaveAttributes(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading != null && dataRegionHeading.getAttribute() != null) {
                return true;
            }
        }
        return false;
    }

    public DataRegionHeading getHeadingWithNameCount(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getNameCountSet() != null) {
                return dataRegionHeading;
            }
        }
        return null;
    }

    public DataRegionHeading getHeadingWithValueParentCount(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getValueParentCountSet() != null) {
                return dataRegionHeading;
            }
        }
        return null;
    }
}