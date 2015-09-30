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
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletContext;
import java.io.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// it seems that trying to configure the properties in spring is a problem - todo : see if upgrading spring to 4.2 makes this easier?
// todo : try to address proper use of protected and private given how I've shifter lots of classes around. This could apply to all sorts in the system.

public class DSSpreadsheetService {

    final Runtime runtime = Runtime.getRuntime();
    final int mb = 1024 * 1024;
    final int kb = 1024;


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

    static class UniqueName {
        Name name;
        String string;
    }

    // todo, clean this up when sessions are expired, maybe a last accessed time?
    private final Map<String, StringBuffer> sessionLogs;

    public DSSpreadsheetService() {
        String thost = "";
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
        if (log != null){
            return log.toString();
        }
        return "";
    }

/*    public void anonymise(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);
        List<Name> anonNames = nameService.findContainingName(azquoMemoryDBConnection, "", Name.ANON);


        for (Name set : anonNames) {
            String anonName = set.getAttribute(Name.ANON);
            if (set.getPeers().size() > 0) {
                Double low = 0.5;
                Double high = 0.5;
                try {
                    String[] limits = anonName.split(" ");
                    low = Double.parseDouble(limits[0]) / 100;
                    high = Double.parseDouble(limits[1]) / 100;

                } catch (Exception ignored) {
                }
                randomAdjust(set, low, high);
            } else {
                int count = 1;
                for (Name name : set.getChildren()) {
                    try {
                        name.setTemporaryAttribute(Constants.DEFAULT_DISPLAY_NAME, anonName.replace("[nn]", count + ""));
                        count++;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }*/

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
                        row.add(Collections.singletonList(new DataRegionHeading(sourceCell, true))); // we say that an attribuite heading defaults to writeable, it will defer to the name
                    } else {
                        DataRegionHeading.BASIC_RESOLVE_FUNCTION function = null;// that's sum practically speaking
                        // now allow functions
                        // I feel there should be a loop here
                        for (DataRegionHeading.BASIC_RESOLVE_FUNCTION basic_resolve_function : DataRegionHeading.BASIC_RESOLVE_FUNCTION.values()) {
                            if (sourceCell.startsWith(basic_resolve_function.name())) {
                                function = basic_resolve_function;
                                sourceCell = sourceCell.substring(sourceCell.indexOf("(") + 1, sourceCell.trim().length() - 1);// +1 - 1 to get rid of the brackets
                            }
                        }
                        String NAMECOUNT = "NAMECOUNT";
                        String TOTALNAMECOUNT = "TOTALNAMECOUNT";
                        // for these two I need the descriptions so I can make a useful cache key for counts
                        if (sourceCell.toUpperCase().startsWith(NAMECOUNT)) {
                            // should strip off the function
                            sourceCell = sourceCell.substring(sourceCell.indexOf("(", NAMECOUNT.length()) + 1); // chop off the beginning
                            sourceCell = sourceCell.substring(0, sourceCell.indexOf(")"));
                            // ok these could be heavy so let's cache them, feels a bit dodgy but we'll see how it goes.
                            Set<Name> nameCountSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(sourceCell);
                            if (nameCountSet == null){
                                nameCountSet = new HashSet<>(nameService.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames)); // put what would have caused multiple headings into namecount
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(sourceCell, nameCountSet);
                            }
                            List<DataRegionHeading> forNameCount = new ArrayList<>();
                            forNameCount.add(new DataRegionHeading(null, false, function, nameCountSet, sourceCell));
                            row.add(forNameCount);
                        } else if (sourceCell.toUpperCase().startsWith(TOTALNAMECOUNT)) {
                            // should strip off the function
                            sourceCell = sourceCell.substring(sourceCell.indexOf("(", NAMECOUNT.length()) + 1); // chop off the beginning
                            sourceCell = sourceCell.substring(0, sourceCell.indexOf(")"));
                            String selectionType = sourceCell.substring(0, sourceCell.indexOf(","));

                            // edd trying some caching

                            Set<Name> typeSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(selectionType);
                            if (typeSet == null){
                                typeSet = new HashSet<>(nameService.parseQuery(azquoMemoryDBConnection, selectionType, attributeNames));
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(sourceCell, typeSet);
                            }

                            if (typeSet.size() != 1) {
                                throw new Exception("selection types must be single cells = use 'as'");
                            }
                            String secondSet = sourceCell.substring(sourceCell.indexOf(",") + 1);

                            Set<Name> selectionSet = azquoMemoryDBConnection.getAzquoMemoryDB().getSetFromCache(secondSet);
                            if (selectionSet == null){
                                selectionSet = new HashSet<>(nameService.parseQuery(azquoMemoryDBConnection, secondSet, attributeNames));
                                azquoMemoryDBConnection.getAzquoMemoryDB().setSetInCache(secondSet, selectionSet);
                            }

                            List<DataRegionHeading> forNameCount = new ArrayList<>();
                            forNameCount.add(new DataRegionHeading(typeSet.iterator().next(), false, function, selectionSet, sourceCell));
                            row.add(forNameCount);
                        } else {
                            row.add(dataRegionHeadingsFromNames(nameService.parseQuery(azquoMemoryDBConnection, sourceCell, attributeNames), azquoMemoryDBConnection, function));
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

    given what we now know about arraylists can this be optimized? Very probably. TODO.
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

        /* second attempt, want it all in one function, benchmarking makes this slow, leave here commented for the moment
        // It would be nice to just return the list if there's onlly one of them but I can't do that I don't think, it would be one long row.
        // stop null pointers and calculate the size (what I'm after to use a single arraylist of the right size)
        int returnSize = 1;
        for (List<T> permutationDimension : listsToPermute) {
            if (permutationDimension == null) {
                permutationDimension = new ArrayList<>();
                permutationDimension.add(null);
            }
            returnSize *= permutationDimension.size();
        }
        List<List<T>> toReturn2 = new ArrayList<>(returnSize);
        boolean moreData = true;
        int[] indexes = new int[listsToPermute.size()]; // bunch of zeroes
        while (moreData){
            List<T> row = new ArrayList<>(listsToPermute.size());
            for (int i = 0; i < indexes.length; i++){
                row.add(listsToPermute.get(i).get(indexes[i]));
            }
            toReturn2.add(row);
                for (int i = indexes.length - 1; i >=0; i--){ // and go back increneting as they "roll over"
                    indexes[i]++;
                    if (indexes[i] >= listsToPermute.get(i).size()){ // it needs to roll over
                        indexes[i] = 0;
                        if (i == 0){ // we tried to roll over on the first list, should be all done!
                            moreData = false;  // stop the big loop.
                        }
                    } else { // don't carry on back
                        break;
                    }
                }
        }*/

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
        // ok the reason I'm doing this is to avoid unncessary array copying and resizing. If the size is 1 can just return that otherwise create an arraylist of the right size and copy in
        ArrayList<List<List<DataRegionHeading>>> permutedLists = new ArrayList<>(noOfHeadingDefinitionRows); // could be slightly less elemnts than this but it's unlikely to be a biggy.
        for (int headingDefinitionRowIndex = 0; headingDefinitionRowIndex < noOfHeadingDefinitionRows; headingDefinitionRowIndex++) {
            List<List<DataRegionHeading>> headingDefinitionRow = headingLists.get(headingDefinitionRowIndex);
            if (lastHeadingDefinitionCellIndex > 0 && !headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex)))
                starting = false;// Don't permute until you have something to permute!
            while (!starting && headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 // we're not on the last row
                    && headingLists.get(headingDefinitionRowIndex + 1).size() > 1 // the next row is not a single cell (will all rows be the same length?)
                    && headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex + 1)) // and the last cell is the only not null one
                    ) {
                if (headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex)== null){
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).add(null);
                }else{
                    headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                }
                headingDefinitionRowIndex++;
            }
            List<List<DataRegionHeading>> permuted = get2DPermutationOfLists(headingDefinitionRow);
            permutedLists.add(permuted);
        }
        if (permutedLists.size() == 1){ // it may often be
            return permutedLists.get(0);
        }
        // the point is I only want to make a new arraylist if there are lists to be combined and then I want it to be the right size. Some reports have had millions in here . . .
        int size = 0;
        for (List<List<DataRegionHeading>> permuted : permutedLists){
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

    private void nameAndParent(UniqueName uName) {
        Name name = uName.name;
        if (name.getParents() != null) {
            for (Name parent : name.getParents()) {
                //check that there are no duplicates with the same immediate parent
                boolean duplicate = false;
                for (Name sibling : parent.getChildren()) {
                    if (sibling != name && sibling.getDefaultDisplayName() != null
                            && sibling.getDefaultDisplayName().equals(name.getDefaultDisplayName())) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    uName.string = uName.string + "," + parent.getDefaultDisplayName();
                    uName.name = parent;//we may need this to go up the chain if the job is not yet done
                }
            }
        }
    }

    private List<String> findUniqueNames(List<UniqueName> pending) {
        List<String> output = new ArrayList<>();
        if (pending.size() == 1) {
            output.add(pending.get(0).string);
            return output;
        }
        //first add a unique parent onto each name
        pending.forEach(this::nameAndParent);
        //but maybe the parents are not unique names!
        boolean unique = false;
        int ucount = 10; //to prevent loops
        while (!unique && ucount-- > 0) {
            unique = true;
            for (UniqueName uName : pending) {
                int ncount = 0;
                for (UniqueName uName2 : pending) {
                    if (uName2.string.equals(uName.string)) ncount++;
                    if (ncount > 1) {
                        nameAndParent(uName2);
                    }
                }
                if (ncount > 1) {
                    nameAndParent(uName);
                    unique = false;
                    break;
                }
            }
        }
        for (UniqueName uName : pending) {
            output.add(uName.string);
        }
        return output;
    }

    private List<String> getIndividualNames(List<Name> sortedNames) {
        //this routine to output a list of names without duplicates by including parent names on duplicates
        List<String> output = new ArrayList<>();
        String lastName = null;
        List<UniqueName> pending = new ArrayList<>();
        for (Name name : sortedNames) {
            if (lastName != null) {
                if (name.getDefaultDisplayName() != null && !name.getDefaultDisplayName().equals(lastName)) {
                    output.addAll(findUniqueNames(pending));
                    pending = new ArrayList<>();
                    if (output.size() > 1500) break;
                }
            }
            lastName = name.getDefaultDisplayName();
            UniqueName uName = new UniqueName();
            uName.name = name;
            uName.string = lastName;
            pending.add(uName);
        }
        output.addAll(findUniqueNames(pending));
        return output;
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

        return getIndividualNames(nameService.parseQuery(getConnectionFromAccessToken(databaseAccessToken), query, languages));
    }

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

    // I guess headings when showing a jumble of values?
/*
    public LinkedHashSet<Name> getHeadings(Map<Set<Name>, Set<Value>> showValues) {
        LinkedHashSet<Name> headings = new LinkedHashSet<Name>();
        // this may not be optimal, can sort later . . .
        int count = 0;
        for (Set<Name> valNames : showValues.keySet()) {
            if (count++ == 2000) {
                break;
            }
            for (Name name : valNames) {
                if (!headings.contains(name.findATopParent())) {
                    headings.add(name.findATopParent());
                }
            }
        }
        return headings;
    }*/

    // return headings as strings for display, I'm going to put blanks in here if null.

    public List<List<String>> convertDataRegionHeadingsToStrings(List<List<DataRegionHeading>> source, List<String> languages) {
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
        azquoMemoryDBConnection.getUserLog().setLength(0); // clear I guess?
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
                for (DataRegionHeading dataRegionHeading : sourceCell.getColumnHeadings()){
                    if (dataRegionHeading == null || ".".equals(dataRegionHeading.getAttribute())){
                        ignored = true;
                    }
                }
                for (DataRegionHeading dataRegionHeading : sourceCell.getRowHeadings()){
                    if (dataRegionHeading == null || ".".equals(dataRegionHeading.getAttribute())){
                        ignored = true;
                    }
                }
                displayDataRow.add(new CellForDisplay(sourceCell.isLocked(), sourceCell.getStringValue(), sourceCell.getDoubleValue(), sourceCell.isHighlighted(), sourceCell.getUnsortedRow(), sourceCell.getUnsortedCol(), ignored));
            }
        }
        // this is single threaded as I assume not much data should be returned. Need to think about this.
        return new CellsAndHeadingsForDisplay(convertDataRegionHeadingsToStrings(getColumnHeadingsAsArray(data), databaseAccessToken.getLanguages())
                , convertDataRegionHeadingsToStrings(getRowHeadingsAsArray(data), databaseAccessToken.getLanguages()), displayData, rowHeadingsSource, colHeadingsSource, contextSource);
    }

    long threshold = 1000;// we log if a section takes longer

    private List<List<AzquoCell>> getDataRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, boolean sortRowAsc, String sortCol, boolean sortColAsc, List<String> languages, int highlightDays) throws Exception {
        long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        long newHeapMarker = oldHeapMarker;
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
        final List<List<List<DataRegionHeading>>> columnHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages);
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
            rowHeadings.add(new ArrayList<DataRegionHeading>());
            rowHeadings.get(0).add(new DataRegionHeading(null,false));
            //return new ArrayList<>();
        }
        final List<Name> contextNames = new ArrayList<>();
        for (List<String> contextItems : contextSource) { // context is flattened and it has support for carriage returned lists in a single cell. Rather arbitrary, remove at some point?
            for (String contextItem : contextItems) {
                final StringTokenizer st = new StringTokenizer(contextItem, "\n");
                while (st.hasMoreTokens()) {
                    final List<Name> thisContextNames = nameService.parseQuery(azquoMemoryDBCOnnection, st.nextToken().trim());
                    time = (System.currentTimeMillis() - track);
                    if (time > threshold) System.out.println("Context parsed in " + time + "ms");
                    track = System.currentTimeMillis();
                    if (thisContextNames.size() > 1) {
                        throw new Exception("error: context names must be individual - use 'as' to put sets in context");
                    }
                    if (thisContextNames.size() > 0) {
                        //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                        contextNames.add(thisContextNames.get(0));
                    }
                }
            }
        }
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
/*        System.out.println();
        System.out.println("Heap cost for headings/context : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();*/
        oldHeapMarker = newHeapMarker;
        // note, didn't se the context against the logged in connection, should I?
        // ok going to try to use the new function
        List<List<AzquoCell>> dataToShow = getAzquoCellsForRowsColumnsAndContext(azquoMemoryDBCOnnection, rowHeadings
                , columnHeadings, contextNames, languages);
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
/*        System.out.println();
        System.out.println("Heap cost for raw data : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();*/
        oldHeapMarker = newHeapMarker;
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data populated in " + time + "ms");
        if (time > 5000){ // a bit arbitrary
            valueService.printSumStats();
            valueService.printFindForNamesIncludeChildrenStats();
        }
        track = System.currentTimeMillis();
        dataToShow = sortAndFilterCells(dataToShow, rowHeadings, columnHeadings
                , filterCount, maxRows, maxCols, sortRow, sortRowAsc, sortCol, sortColAsc, highlightDays);
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
/*        System.out.println();
        System.out.println("Heap cost for sorting : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();*/
        oldHeapMarker = newHeapMarker;
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data sort/filter in " + time + "ms");
        System.out.println("region delivered in " + (System.currentTimeMillis() - start) + "ms");
        return dataToShow;
    }


    private List<Name> getContextNames(AzquoMemoryDBConnection azquoMemoryDBConnection, List<List<String>> contextSource) throws Exception {
        final List<Name> contextNames = new ArrayList<>();
        if (contextSource==null) return contextNames;
        for (List<String> contextItems : contextSource) { // context is flattened and it has support for carriage returned lists in a single cell
            for (String contextItem : contextItems) {
                final StringTokenizer st = new StringTokenizer(contextItem, "\n");
                while (st.hasMoreTokens()) {
                    final List<Name> thisContextNames = nameService.parseQuery(azquoMemoryDBConnection, st.nextToken().trim());
                    if (thisContextNames.size() > 1) {
                        throw new Exception("error: context names must be individual - use 'as' to put sets in context");
                    }
                    if (thisContextNames.size() > 0) {
                        //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                        contextNames.add(thisContextNames.get(0));
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
        final List<List<List<DataRegionHeading>>> columnHeadingLists = createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages);
        final List<List<DataRegionHeading>> columnHeadings = expandHeadings(transpose2DList(columnHeadingLists));
        if (columnHeadings.size() == 0 || rowHeadings.size() == 0) {
            return null;
        }
        final List<Name> contextNames = getContextNames(azquoMemoryDBCOnnection, contextSource);
        // now onto the bit to find the specific cell - the column headings were transposed then expanded so they're in the same format as the row headings
        // that is to say : the outside list's size is the number of columns or headings. So, do we have the row and col?
        if (unsortedRow < rowHeadings.size() && unsortedCol < columnHeadings.size()) {
            return getAzquoCellForHeadings(azquoMemoryDBCOnnection, rowHeadings.get(unsortedRow), columnHeadings.get(unsortedCol), contextNames, unsortedRow, unsortedCol, languages);
        }
        return null; // couldn't find it . . .
    }

    // for looking up a heading given a string. Used to find the col/row index to sort on

    private int findPosition(List<List<DataRegionHeading>> headings, String toFind) {
        if (toFind == null || toFind.length() == 0) {
            return -1;
        }
        int count = 0;
        for (List<DataRegionHeading> heading : headings) {
            DataRegionHeading dataRegionHeading = heading.get(heading.size() - 1);
            if (dataRegionHeading != null) {
                String toCompare;
                if (dataRegionHeading.getName() != null && dataRegionHeading.getName().getDefaultDisplayName() != null) {
                    toCompare = dataRegionHeading.getName().getDefaultDisplayName().replace(" ", "");
                } else {
                    toCompare = dataRegionHeading.getAttribute();
                }
                if (toCompare.equals(toFind)) {
                    return count;
                }
            }
            count++;
        }
        return -1;
    }

    // note, one could derive column and row headings from the source data's headings but passing them is easier if they are to hand which the should be
    // also deals with highlighting

    private List<List<AzquoCell>> sortAndFilterCells(List<List<AzquoCell>> sourceData, List<List<DataRegionHeading>> rowHeadings, List<List<DataRegionHeading>> columnHeadings
            , final int filterCount, int maxRows, int maxCols, String sortRowString, boolean sortRowAsc, String sortColString, boolean sortColAsc, int highlightDays) throws Exception {
        long track = System.currentTimeMillis();
        List<List<AzquoCell>> toReturn = sourceData;
        if (sourceData == null || sourceData.isEmpty()) {
            return sourceData;
        }
        Integer sortOnColIndex = findPosition(columnHeadings, sortColString);
        Integer sortOnRowIndex = findPosition(rowHeadings, sortRowString); // not used at the mo
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
            final Map<Integer, Double> sortRowTotals = new HashMap<>(sourceData.size());
            final Map<Integer, String> sortRowStrings = new HashMap<>(sourceData.size());
            final Map<Integer, Double> sortColumnTotals = new HashMap<>(totalCols);
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
            int blockRowCount = 0;
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
                if (++blockRowCount == filterCount) {
                    // we need the equivalent check of blank rows, checking the cell's list of names or values should do this
                    // go back from the end
                    boolean rowsBlank = true;
                    for (int j = 0; j < filterCount; j++) {
                        List<AzquoCell> rowToCheck = sortedCells.get((sortedCells.size() - 1) - j); // size - 1 for the last index
                        for (AzquoCell cellToCheck : rowToCheck) {
                          /*
                        if ((cellToCheck.getListOfValuesOrNamesAndAttributeName().getNames() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getNames().isEmpty())
                                || (cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty())) {// there were values or names for the call
                            */
                            //CHECKING VALUES ONLY
                            if (cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues() != null && !cellToCheck.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty()) {// there were values or names for the call
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
                            sortedCells.remove(sortedCells.size() - 1);
                        }
                    }
                    blockRowCount = 0;
                }

            }
            toReturn = sortedCells;
        }

        // it's at this point we actually have data that's going to be sent to a user in newRow so do the highlighting here I think
        if (highlightDays > 0) {
            for (List<AzquoCell> row : toReturn) {
                for (AzquoCell azquoCell : row) {
                    long age = 10000; // about 30 years old as default
                    ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
                    if (valuesForCell != null && (valuesForCell.getValues() != null || !valuesForCell.getValues().isEmpty())) {
                        for (Value value : valuesForCell.getValues()) {
                            if (value.getText().length() > 0) {
                                if (value.getProvenance() == null) {
                                    break;
                                }
                                LocalDateTime provdate = LocalDateTime.ofInstant(value.getProvenance().getTimeStamp().toInstant(), ZoneId.systemDefault());
                                // ah, decent Date APIs! Although the line above seems a bit verbose
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
                        if (counter.incrementAndGet() % progressBarStep == 0){
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
                if (counter.incrementAndGet() % progressBarStep == 0){
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

    private int totalNameSet(Name containsSet, Set<Name> selectionSet, Set<Name> alreadyTested, int track) {
//            System.out.println("totalNameSet track " + track + " contains set : " + containsSet.getDefaultDisplayName() + ", children size : " + containsSet.getChildren().size());
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
                if (!child.getChildren().isEmpty()) {
                    count += totalNameSet(child, selectionSet, alreadyTested, track);
                }
            }
        }
        return count;
    }

    private int getTotalNameCount(AzquoMemoryDBConnection connection, Set<DataRegionHeading> headings) {
        String cacheKey = "";
        for (DataRegionHeading heading : headings) {
            if (heading.getDescription() != null){
                cacheKey += heading.getDescription();
            } else if (heading.getName() != null){
                cacheKey += heading.getName().getDefaultDisplayName();
            }
        }
        Integer cached = connection.getAzquoMemoryDB().getCountFromCache(cacheKey); // I want hash code match not equals match (what hashmap would do)
        if (cached != null){
            return cached;
        }
        Name memberSet = null;
        Name containsSet = null;
        Set<Name> selectionSet = null;
        for (DataRegionHeading heading : headings) {
            if (heading.getName() != null) {
                if (heading.getNameCountSet() != null) {
                    selectionSet = heading.getNameCountSet();
                    containsSet = heading.getName();
                } else {
                    memberSet = heading.getName();
                }
            }
        }
        int toReturn = 0;
        Set<Name> alreadyTested = new HashSet<>();
        // Edd reinstating code here - about a month ago it stopped paying attention to member set
        if (containsSet != null && memberSet != null) {
            Set<Name> remainder = new HashSet<>(selectionSet);
            remainder.retainAll(memberSet.findAllChildren(false));
            toReturn = totalNameSet(containsSet, remainder, alreadyTested, 0);
        }
        connection.getAzquoMemoryDB().setCountInCache(cacheKey, toReturn);
        return toReturn;
    }

    // factored this off to enable getting a single cell, also useful to be called from the multi threading

    private AzquoCell getAzquoCellForHeadings(AzquoMemoryDBConnection connection, List<DataRegionHeading> rowHeadings, List<DataRegionHeading> columnHeadings
            , List<Name> contextNames, int rowNo, int colNo, List<String> languages) throws Exception {
        long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        long newHeapMarker = oldHeapMarker;

        String stringValue;
        double doubleValue = 0;
        Set<DataRegionHeading> headingsForThisCell = new HashSet<>(rowHeadings.size());
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
            stringValue = "";
        } else {
            // ok new logic here, we need to know if we're going to use attributes or values or namecount!
            DataRegionHeading.BASIC_RESOLVE_FUNCTION function = null;
            for (DataRegionHeading heading : headingsForThisCell) {
                if (heading.getFunction() != null) {
                    function = heading.getFunction();
                }
            }
            DataRegionHeading nameCountHeading = getHeadingWithNameCount(headingsForThisCell);
            if (nameCountHeading != null && headingsForThisCell.size() == 2) {//these functions only work if there's no context
                if (nameCountHeading.getName() != null) {
                    //System.out.println("going for total name set " + nameCountHeading.getNameCountSet().size() + " name we're using " + nameCountHeading.getName());
                    doubleValue = getTotalNameCount(connection, headingsForThisCell);
                } else {
                    Set<Name> nameCountSet = nameCountHeading.getNameCountSet();
                    doubleValue = 0.0;
                    for (DataRegionHeading dataRegionHeading : headingsForThisCell) {
                        if (dataRegionHeading != nameCountHeading && dataRegionHeading.getName() != null) { // should be fine
                            // we know this is a cached set internally, no need to create a new set - might be expensive
                            Collection<Name> nameCountSet2 = dataRegionHeading.getName().findAllChildren(false);
                            String cacheKey = nameCountHeading.getDescription() + dataRegionHeading.getName().getDefaultDisplayName();
                            Integer cached = connection.getAzquoMemoryDB().getCountFromCache(cacheKey);
                            if (cached != null){
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
            } else if (!headingsHaveAttributes(headingsForThisCell)) { // we go the value route (the standard/old one), need the headings as names,
                // TODO - peer additive check. If using peers and not additive, don't include children
                List<Value> values = new ArrayList<>();
                // now , get the function from the headings
                if (function != null) {
                    locked.isTrue = true;
                }
                if (headingsForThisCell.size() > 0) {
                    doubleValue = valueService.findValueForNames(connection, namesFromDataRegionHeadings(headingsForThisCell), locked, true, values, languages, function); // true = pay attention to names additive flag
                    //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                    if (values.size() == 1 && !locked.isTrue) {

                        Value value = values.get(0);
                        stringValue = value.getText();
                        if (stringValue.contains("\n")) {
                            stringValue = stringValue.replaceAll("\n", "<br/>");//this is unsatisfactory, but a quick fix.
                        }
                        // was isnumber test here to add a double to the
                    } else {
                        stringValue = doubleValue + "";
                    }
                } else {
                    stringValue = "";
                    doubleValue = 0;
                }
                listOfValuesOrNamesAndAttributeName = new ListOfValuesOrNamesAndAttributeName(values);
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
                String attributeResult = valueService.findValueForHeadings(rowAndColumnHeadingsForThisCell, locked, names, attributes);
                try {

                    doubleValue = Double.parseDouble(attributeResult);
                } catch (Exception e) {
                    //ignore
                }
                // ZK would sant this typed? Maybe just sort out later?
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
        StringBuffer errorTrack = new StringBuffer();// deliberately threadsafe, need to keep an eye on the report building . . .
        // tried multithreaded, abandoning big chunks
        // different style, just chuck every row in the queue
        int progressBarStep = (totalCols * totalRows) / 50 + 1;
        AtomicInteger counter = new AtomicInteger();
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        System.out.println();
        System.out.println("Heap cost to prepare data space : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();
        oldHeapMarker = newHeapMarker;
        if (totalRows < 1000){ // arbitrary cut off for the moment
            for (int row = 0; row < totalRows; row++) {
                List<DataRegionHeading> rowHeadings = headingsForEachRow.get(row);
                List<AzquoCell> returnRow = new ArrayList<>(headingsForEachColumn.size());
                for (int i = 0; i < headingsForEachColumn.size(); i++){
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
        oldHeapMarker = newHeapMarker;
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

    /* for anonymiseing data
    public void randomAdjust(Name name, double low, double high) {
        for (Value value : name.getValues()) {
            try {
                double orig = Double.parseDouble(value.getText());
                Double newValue = orig * ((1 + low) + (high - low) * Math.random());
                int newRound = (int) (newValue * 100);
                value.setText((((double) newRound) / 100) + "");
            } catch (Exception e) {

            }
        }

    }*/

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
        }
        return new ArrayList<>(); //just empty ok? null? Unsure
    }


    public TreeNode getDataList(Set<Name> names, int maxSize)throws Exception{
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

    public void importDataFromSpreadsheet(AzquoMemoryDBConnection azquoMemoryDBConnection, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user) throws Exception {


        //write the columnheadings and data to a temporary file, then import it
        String fileName = "temp_" + user;
        File temp = File.createTempFile(fileName + ".csv", "csv");
        String tempName = temp.getPath();
        temp.deleteOnExit();
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
                String val = cellForDisplay.getStringValue().length() > 0 ? cellForDisplay.getStringValue() : cellForDisplay.getDoubleValue() != 0 || cellForDisplay.isChanged() ? cellForDisplay.getDoubleValue() + "" : "";
                //for the moment we're passsing on cells that have not been entered as blanks which are ignored in the importer - this does not leave space for deleting values or attributes
                sb.append(val);
            }
            bw.write(sb.toString());
            bw.newLine();

        }
        bw.flush();
        bw.close();
        List<String> languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        importService.readPreparedFile(azquoMemoryDBConnection, tempName, "csv", languages);
    }

    // it's easiest just to send the CellsAndHeadingsForDisplay back to the back end and look for relevant changed cells
    public void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context) throws Exception {


        AzquoMemoryDBConnection azquoMemoryDBConnection = getConnectionFromAccessToken(databaseAccessToken);

        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches(); // may need to optimise later, clear the name counts also?

        azquoMemoryDBConnection.setProvenance(user, "in spreadsheet", reportName, context);
        if (cellsAndHeadingsForDisplay.getRowHeadings() == null && cellsAndHeadingsForDisplay.getData().size() > 0) {
            importDataFromSpreadsheet(azquoMemoryDBConnection, cellsAndHeadingsForDisplay, user);
            return;
        }


        int numberOfValuesModified = 0;
        List<Name> contextNames = getContextNames(azquoMemoryDBConnection, cellsAndHeadingsForDisplay.getContextSource());
        int rowCounter = 0;
        for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()) {
            int columnCounter = 0;
            for (CellForDisplay cell : row) {
                if (!cell.isLocked() && cell.isChanged()) {
                    //logger.info(orig + "|" + edited + "|"); // we no longet know the original value unless we jam it in AzquoCell
                    // note, if there are many cells being edited this becomes inefficient as headings are being repeatedly looked up
                     AzquoCell azquoCell = getSingleCellFromRegion(azquoMemoryDBConnection, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                            , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                            , cell.getUnsortedRow(), cell.getUnsortedCol(), databaseAccessToken.getLanguages());

                    if (azquoCell != null) {
                        numberOfValuesModified++;
                        // this save logic is the same as before but getting necessary info from the AzquoCell
                        logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                        final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
                        final Set<DataRegionHeading> headingsForCell = new HashSet<>(azquoCell.getColumnHeadings().size() + azquoCell.getRowHeadings().size());
                        headingsForCell.addAll(azquoCell.getColumnHeadings());
                        headingsForCell.addAll(azquoCell.getRowHeadings());
                        // one thing about these store functions to the value spreadsheet, they expect the provenance on the logged in connection to be appropriate
                        // right, switch here to deal with attribute based cell values

                        //first align text and numbers where appropriate
                        try {
                            if (cell.getDoubleValue() != 0.0) {
                                cell.setStringValue(cell.getDoubleValue() + "");
                            }
                        } catch (Exception ignored) {
                        }
                        if (cell.getStringValue().endsWith("%")) {
                            String percent = cell.getStringValue().substring(0, cell.getStringValue().length() - 1);
                            try {
                                double d = Double.parseDouble(percent) / 100;
                                cell.setStringValue(d + "");
                            } catch (Exception e) {
                                //do nothing
                            }
                        }
                        if (valuesForCell.getValues() != null) {
                            // this call to make the hash set seems rather unefficient
                            Set<Name> cellNames = namesFromDataRegionHeadings(headingsForCell);
                            cellNames.addAll(contextNames);
                            if (valuesForCell.getValues().size() == 1) {
                                final Value theValue = valuesForCell.getValues().get(0);
                                logger.info("trying to overwrite");
                                if (cell.getStringValue().length() > 0) {
                                    //sometimes non-existant original values are stored as '0'
                                    valueService.overWriteExistingValue(azquoMemoryDBConnection, theValue, cell.getStringValue());
                                    numberOfValuesModified++;
                                } else {
                                    theValue.delete();
                                }
                            } else if (valuesForCell.getValues().isEmpty() && cell.getStringValue().length() > 0) {
                                logger.info("storing new value here . . .");
                                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, cell.getStringValue(), cellNames);
                                numberOfValuesModified++;
                            }
                        } else {
                            if (valuesForCell.getNames().size() == 1 && valuesForCell.getAttributeNames().size() == 1) { // allows a simple store
                                Name toChange = valuesForCell.getNames().get(0);
                                String attribute = valuesForCell.getAttributeNames().get(0).substring(1);//remove the initial '.'
                                Name attSet = nameService.findByName(azquoMemoryDBConnection, attribute);
                                if (attSet != null && attSet.getChildren().size() > 0 && !azquoMemoryDBConnection.getAzquoMemoryDB().attributeExistsInDB(attribute)) {
                                    logger.info("storing " + toChange.getDefaultDisplayName() + " to children of  " + cell.getStringValue() + " within " + attribute);
                                    Name category = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell.getStringValue(), attSet, true);
                                    category.addChildWillBePersisted(toChange);
                                } else {
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
    }

    // Four little utility functions added by Edd, required now headings are not names

    public List<DataRegionHeading> dataRegionHeadingsFromNames(Collection<Name> names, AzquoMemoryDBConnection azquoMemoryDBConnection, DataRegionHeading.BASIC_RESOLVE_FUNCTION function) {
        List<DataRegionHeading> dataRegionHeadings = new ArrayList<>(names.size()); // names could be big, init the Collection with the right size
        if (azquoMemoryDBConnection.getWritePermissions() != null && !azquoMemoryDBConnection.getWritePermissions().isEmpty()){
            // then check permissions
            for (Name name : names) {
                // will the new write permissions cause an overhead?
                dataRegionHeadings.add(new DataRegionHeading(name, nameService.isAllowed(name, azquoMemoryDBConnection.getWritePermissions()), function, null, null));
            }
        } else { // don't bother checking permissions, write permissions to true
            for (Name name : names) {
                dataRegionHeadings.add(new DataRegionHeading(name, true, function, null, null));
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
        Set<String> names = new HashSet<>(); // attributes won't ever be big as they're manually defined, leave this to default (16 size table internally?)
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getAttribute() != null) {
                names.add(dataRegionHeading.getAttribute().substring(1)); // at the mo I assume attributes begin with .
            }
        }
        return names;
    }

    public boolean headingsHaveAttributes(Collection<DataRegionHeading> dataRegionHeadings) {
        for (DataRegionHeading dataRegionHeading : dataRegionHeadings) {
            if (dataRegionHeading.getAttribute() != null) {
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

}