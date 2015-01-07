package com.azquo.service;

import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import com.azquo.memorydb.Value;
import com.csvreader.CsvReader;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.StringReader;
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

    private StringUtils stringUtils = new StringUtils();

    // set the names in delete info and unlink - best I can come up with at the moment
    // unlike Name I don't think we're actually going to delete it - though whether the current name behavior is correct is another thing
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

    private class MBoolean{
        boolean isTrue;

        MBoolean(){
            isTrue = false;
        }
    }

    public Value createValue(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Provenance provenance, final String text) throws Exception {
        return new Value(azquoMemoryDBConnection.getAzquoMemoryDB(), provenance, text, null);
    }

    Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new HashMap<AzquoMemoryDBConnection, Map<String, Long>>();

    private void addToTimesForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection, String trackName, long toAdd){
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

    public Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection){
        return timeTrack.get(azquoMemoryDBConnection);
    }


    // this is passed a string for the value, not sure if that is the best practice, need to think on it.

    public String storeValueWithProvenanceAndNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String valueString, final Set<Name> names) throws Exception {
        //long marker = System.currentTimeMillis();
        String toReturn = "";

        final Set<Name> validNames = new HashSet<Name>();

        final Map<String, String> nameCheckResult = nameService.isAValidNameSet(azquoMemoryDBConnection, names, validNames);
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames1", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        final String error = nameCheckResult.get(NameService.ERROR);
        final String warning = nameCheckResult.get(NameService.WARNING);
        if (error != null) {
            return error;
        } else if (warning != null) {
            toReturn += warning;
        }

        final List<Value> existingValues = findForNames(names);
        //addToTimesForConnection(azquoMemoryDBConnection, "storeValueWithProvenanceAndNames2", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        boolean alreadyInDatabase = false;
        for (Value existingValue : existingValues) { // really should only be one
            if (existingValue.getProvenance().equals(azquoMemoryDBConnection.getProvenance()) && existingValue.getProvenance().getMethod().equals("import")) {
                //new behaviour - add values from same import.
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

            if (compareStringValues(existingValue.getText(),valueString)) {
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

    public List<Value> findForNamesIncludeChildren(final Set<Name> names, boolean payAttentionToAdditive) {
        return findForNamesIncludeChildren(names, payAttentionToAdditive, null);
    }

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
            if (setSizeCache != null){
                setSizeIncludingChildren = setSizeCache.get(name);
            }
            if (setSizeIncludingChildren == null) {
                setSizeIncludingChildren = name.getValues().size();
                for (Name child : name.findAllChildren(payAttentionToAdditive)) {
                    setSizeIncludingChildren += child.getValues().size();
                }
                if (setSizeCache != null){
                    setSizeCache.put(name,setSizeIncludingChildren);
                }
            }

            if (smallestNameSetSize == -1 || setSizeIncludingChildren < smallestNameSetSize) {
                smallestNameSetSize = setSizeIncludingChildren;
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
                        Set <Name> copy = new HashSet<Name>(value.getNames());
                         copy.retainAll(name.findAllChildren(payAttentionToAdditive));
                         if (copy.size()==0){
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

    /* RPCALC will have been set by the shunting yard algorithm earlier
    names referenced as id to stop operators in names
    RPcalc will be a list of values and operations e.g. 5*(2+3) converted to 5.0 2.1 + 3.0 * !76 +
    There may be name references in there, by !nameid
    */

    private Set<Name> trimNames(Name name, Set<Name> nameSet) {
        //this is for weeding out peers when an element of the calc has less peers
        int required = name.getPeers().size();
        Set<Name> applicableNames = new HashSet<Name>();
        for (Name peer : name.getPeers().keySet()) {
            for (Name listName : nameSet) {
                if (listName.findATopParent() == peer.findATopParent()) {
                    applicableNames.add(listName);
                    if (--required == 0) {
                        return applicableNames;
                    }

                }
            }
        }
        return applicableNames;

    }

    public double findValueForNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, final Set<Name> names, final MBoolean locked, final boolean payAttentionToAdditive, List<Value> valuesFound, Map<Name,Integer> totalSetSize) {
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<Name>();

        String calcString = "";
        boolean hasCalc = false;
        // add all names to calcnames except the the one with RPCALC
        // and here's a thing : if more than one name has RPcalc then only the first will be used
        for (Name name : names) {
            if (!hasCalc) {
                calcString = name.getAttribute(Name.RPCALC);
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
            locked.isTrue = false;
            for (Name oneName : names) {
                if ((oneName.getPeers().size() == 0 && oneName.getChildren().size() > 0) || !nameService.isAllowed(oneName, azquoMemoryDBConnection.getWritePermissions()))
                    locked.isTrue = true;
            }
            return findSumForNamesIncludeChildren(names, payAttentionToAdditive, valuesFound, totalSetSize);
        } else {
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
                        int id = Integer.parseInt(term.substring(1));
                        // so get the name and add it to the other names
                        Name name = nameService.findById(azquoMemoryDBConnection, id);
                        Set<Name> seekSet = new HashSet(calcnames);
                        if (name.getPeers().size() == 0 || name.getPeers().size() == calcnames.size()) {
                            seekSet.add(name);
                        } else {
                            seekSet = trimNames(name, seekSet);
                            seekSet.add(name);
                        }

                        // and put the result in
                        //note - recursion in case of more than one formula, but the order of the formulae is undefined if the formulae are in different peer groups
                        values[valNo++] = findValueForNames(azquoMemoryDBConnection, seekSet, locked, payAttentionToAdditive, valuesFound, totalSetSize);


                    }
                }
            }
            locked.isTrue = true;
            return values[0];
        }
    }

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
        // TODO : concurrent errror here !!!
        /*

        java.util.ConcurrentModificationException
        at java.util.HashMap$HashIterator.nextEntry(HashMap.java:926)
        at java.util.HashMap$KeyIterator.next(HashMap.java:960)
        at com.azquo.service.ValueService.findValuesForNameIncludeAllChildren(ValueService.java:460)
        at com.azquo.service.ValueService.findForNamesIncludeChildren(ValueService.java:228)

         */
        for (Name child : name.findAllChildren(payAttentionToAdditive)) {
            toReturn.addAll(child.getValues());
        }
        return toReturn;
    }

    public String outputHeadings(final List<List<Name>> headings, String language, List<String> rowHeadingSupplements) {

        final StringBuilder sb = new StringBuilder();

        if (language == null || language.length() == 0) language = Name.DEFAULT_DISPLAY_NAME;
        for (int x = 0; x < headings.size(); x++) {
            List<Name> xNames = headings.get(x);
            if (x > 0) sb.append("\n");
            Name lastName = null;
            for (int y = 0; y < xNames.size(); y++) {
                if (y > 0) sb.append("\t");
                //NOW - LEAVE THE PRUNING OF NAMES TO EXCEL - MAYBE THE LIST WILL BE SORTED.
                //don't show repeating names in the headings - leave blank.
                //if ((x == 0 || !lastxNames.get(y).equals(xNames.get(y))) && (y == 0 || !xNames.get(y - 1).equals(xNames.get(y)))) {
                lastName = xNames.get(y);
                if (lastName != null) {
                    String nameInLanguage = lastName.getAttribute(language);
                    if (nameInLanguage == null) {
                        nameInLanguage = lastName.getDefaultDisplayName();
                    }
                    sb.append(nameInLanguage);
                }
            }
            if (rowHeadingSupplements != null) {
                for (String supplement : rowHeadingSupplements) {
                    String attribute = lastName.getAttribute(supplement);
                    if (attribute != null) {
                        sb.append("\t" + attribute);
                    } else {
                        sb.append("\t");
                    }
                }
            }
            // lastxNames = xNames;
        }
        return sb.toString();
    }

/*    public List<Name> interpretItem(String item) {
        //todo  - item should be a string, but potentially include ;children; level x; from a; to b; from n; to n;
        return null;
    }*/


    /*

    Ok, select a region of names in excel and paste and this function will build a multidimentional array of name objects from that paste

    more specifically : the outermost list is of rows, the second list is each cell in that row and the final list is a list not a name
    because there could be multiple names in a cell if the cell has something like container;children. Interpretnames is what does this

hence

seaports;children   container;children

 for example returns a list of 1 as there's only 1 row passed, with a list of 2 as there's two cells in that row with lists of something like 90 and 3 names in the two cell list items

 we're not permuting, just saying "here is what that 2d excel region looks like in names"

     */



public String createNameListsFromExcelRegion(final AzquoMemoryDBConnection azquoMemoryDBConnection, List<List<List<Name>>> nameLists, List<String> supplementNames, final String excelRegionPasted, List<String> attributeNames) throws Exception {
        //logger.info("excel region pasted : " + excelRegionPasted);
        int maxColCount = 1;
        CsvReader pastedDataReader = new CsvReader(new StringReader(excelRegionPasted), '\t');
        while (pastedDataReader.readRecord()) {
            if (pastedDataReader.getColumnCount() > maxColCount) {
                maxColCount = pastedDataReader.getColumnCount();
            }
        }
        pastedDataReader = new CsvReader(new StringReader(excelRegionPasted), '\t'); // reset the CSV reader
        pastedDataReader.setUseTextQualifier(false);
        while (pastedDataReader.readRecord()) {
            List<List<Name>> row = new ArrayList<List<Name>>();
            for (int column = 0; column < pastedDataReader.getColumnCount(); column++) {
                String cellString = pastedDataReader.get(column);
                if (cellString.length() == 0) {
                    row.add(null);
                } else {
                    if (cellString.toLowerCase().contains(";with ")) {
                        int withPos = cellString.toLowerCase().indexOf(";with ");
                        String withList = cellString.substring(withPos + 6);
                        cellString = cellString.substring(0, withPos);
                        String[] sNames = withList.split(",");
                        for (String sName:sNames){
                            supplementNames.add(sName.trim());
                        }
                    }
                    try{

                        List<Name> nameList =  nameService.interpretName(azquoMemoryDBConnection, cellString, attributeNames);
                        row.add(nameList);

                    } catch (Exception e){
                        return "error:" + e.getMessage();
                    }
                }
            }
            while (row.size() < maxColCount) row.add(null);
            nameLists.add(row);
        }
        return "";
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

    */

    public <T> List<List<T>> get2DPermutationOfLists(final List<List<T>> listsToPermute) {

        List<List<T>> toReturn = null;
        for (List<T> permutationDimension : listsToPermute) {
            if (toReturn == null) { // first one, just assign the single column
                toReturn = new ArrayList<List<T>>();
                for (T item : permutationDimension) {
                    List<T> createdRow = new ArrayList<T>();
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


    public <T> List<List<T>> get2DArrayWithAddedPermutation(final List<List<T>> existing2DArray, List<T> permutationWeWantToAdd) {
        List<List<T>> toReturn = new ArrayList<List<T>>();
        for (List<T> existingRow : existing2DArray) {
            for (T elementWeWantToAdd : permutationWeWantToAdd) { // for each new element
                List<T> newRow = new ArrayList<T>(existingRow); // copy the existing row
                newRow.add(elementWeWantToAdd);// add the extra element
                toReturn.add(newRow);
            }
        }
        return toReturn;
    }

    /*

    This is called after the names are loaded by createNameListsFromExcelRegion. in the case of columns it is transposed first

    The dynamic namecalls e.g. Seaports; children; have their lists populated but they have not been expanded out into the 2d set itself

    So in the case of row headings for the export example it's passed a list or 1 (the outermost list of row headings derined)

    and this 1 has a list of 2, the two cells defined in row headings as seaports; children     container;children

      We want to return a list<list> being row, cells in that row, the expansion of each of the rows. The source in my example being ony one row with 2 cells in it
      those two cells holding lists of 96 and 4 hence we get 384 back, each with 2 cells.

    RIGHT!

    I know what the function that used to be called blank col is for. Heading definitions are a region
    if this region is more than on column wide then rows which have only one cell populated and that cell is the right most cell
    then that cell is added to the cell above it, it becomes part of that permutation.


     */


    public List<List<Name>> expandHeadings(final List<List<List<Name>>> headingLists) {

        List<List<Name>> output = new ArrayList<List<Name>>();
        final int noOfHeadingDefinitionRows = headingLists.size();
        if (noOfHeadingDefinitionRows == 0){
            return output;
        }
        final int lastHeadingDefinitionCellIndex = headingLists.get(0).size() - 1; // the headingLists will be square, that is to say all row lists the same length as prepared by createNameListsFromExcelRegion


        for (int headingDefinitionRowIndex = 0; headingDefinitionRowIndex < noOfHeadingDefinitionRows; headingDefinitionRowIndex++) {
            List<List<Name>> headingDefinitionRow = headingLists.get(headingDefinitionRowIndex);
            // ok we have one of the heading definition rows
            while (headingDefinitionRowIndex < noOfHeadingDefinitionRows - 1 // we're not on the last row
                    && headingLists.get(headingDefinitionRowIndex + 1).size() > 1 // the next row is not a single cell (will all rows be the same length?)
                    && headingDefinitionRowHasOnlyTheRightCellPopulated(headingLists.get(headingDefinitionRowIndex + 1))) { // and the last cell is the only not null one
                headingDefinitionRow.get(lastHeadingDefinitionCellIndex).addAll(headingLists.get(headingDefinitionRowIndex + 1).get(lastHeadingDefinitionCellIndex));
                headingDefinitionRowIndex++;
            }
            List<List<Name>> permuted = get2DPermutationOfLists(headingDefinitionRow);
            output.addAll(permuted);
        }
        return output;

    }

    // what we're saying is it's only got one cell in the heading definition filled and it's the last one.

    private boolean headingDefinitionRowHasOnlyTheRightCellPopulated(List<List<Name>> headingLists) {
        int numberOfCellsInThisHeadingDefinition = headingLists.size();
        for (int cellIndex = 0; cellIndex < numberOfCellsInThisHeadingDefinition; cellIndex++) {
            if (headingLists.get(cellIndex) != null) {
                return cellIndex == numberOfCellsInThisHeadingDefinition - 1; // if the first to trip the not null is the last in the row then true!
            }

        }
        return false; // it was ALL null, error time?
    }


    /*
    createNameListsFromExcelRegion is fairly simple, given seaports;children    container;children returns a list of names of seaports and a list of container criteria held in a list
     which is in turn held in a larger list as seaports;children    container;children is one row which expands out to many, there could be further rows underneath (I mean rows in excel to define headings)
     notable that this means that headings for rows or columns are sent in the same format, a region from excel

     Rowheadingslists outside is actual rows, one in is columns,final one is for the cell
                  */


    private boolean blankRows(LoggedInConnection loggedInConnection, String region, int rowInt, int count) {

        final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);

        if (dataValueMap != null) {
            for (int rowCount = 0; rowCount < count; rowCount++) {
                if (dataValueMap.get(rowInt + rowCount) != null) {
                    final List<List<Value>> rowValues = dataValueMap.get(rowInt + rowCount);
                    for (List<Value> oneCell : rowValues) {
                        if (oneCell.size() > 0) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }


    public List<Integer> sortRows(int restrictCount, Map<Integer, Double> sortTotals) {

        List<Integer> sortedRows = new ArrayList<Integer>();
        if (restrictCount != 0) {
            List list = new LinkedList(sortTotals.entrySet());

            // sort list based on comparator
            Collections.sort(list, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return ((Comparable) ((Map.Entry) (o1)).getValue())
                            .compareTo(((Map.Entry) (o2)).getValue());
                }
            });

            for (Iterator it = list.iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                sortedRows.add((Integer) entry.getKey());
            }

        } else {
            for (int i = 0; i < sortTotals.size(); i++) {
                sortedRows.add(i);
            }
        }
        return sortedRows;

    }

    public String getFullRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        String language = stringUtils.getInstruction(headingsSent, "language");
        final List<List<List<Name>>> rowHeadingLists = new ArrayList<List<List<Name>>>();
        List<String> supplementNames = new ArrayList<String>();

        String error = createNameListsFromExcelRegion(loggedInConnection, rowHeadingLists, supplementNames, headingsSent, loggedInConnection.getLanguages());
        //if (error.length() > 0) {
       //     return error;
       // }
        loggedInConnection.setRowHeadings(region, expandHeadings(rowHeadingLists));
        loggedInConnection.setRowHeadingSupplements(region, supplementNames);
        return outputHeadings(loggedInConnection.getRowHeadings(region), language, loggedInConnection.getRowHeadingSupplements(region));


    }

    public String getRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent, final int filterCount) throws Exception {
        // rows, columns, cells (which can have many names (e.g. xxx;elements), I mean rows and columns and cells of a region saying what the headings should be, not the headings themselves!
        // "here is what that 2d heading definition excel region looks like in names"
        String language = stringUtils.getInstruction(headingsSent, "language");


        if (filterCount > 0) {
            //send back only those headings that have data - considered in batches of length filtercount.
            List<List<Name>> rowHeadingsWithData = new ArrayList<List<Name>>();
            List<List<Name>> allRowHeadings = loggedInConnection.getRowHeadings(region);
            int rowInt = 0;
            while (rowInt < allRowHeadings.size()) {
                if (!blankRows(loggedInConnection, region, rowInt, filterCount)) {
                    for (int rowCount = 0; rowCount < filterCount; rowCount++) {
                        rowHeadingsWithData.add(allRowHeadings.get(rowInt + rowCount));
                    }
                }
                rowInt += filterCount;
            }
            //note that the sort order has already been set.... there cannot be both a restrict count and a filter count
            return outputHeadings(rowHeadingsWithData, language, loggedInConnection.getRowHeadingSupplements(region));
        } else if (loggedInConnection.getRestrictCount(region) != null && loggedInConnection.getRestrictCount(region) != 0) {
            int restrictCount = loggedInConnection.getRestrictCount(region);
            List<Integer> sortedRows = loggedInConnection.getRowOrder(region);
            List<List<Name>> rowHeadingsWithData = new ArrayList<List<Name>>();
            List<List<Name>> allRowHeadings = loggedInConnection.getRowHeadings(region);
            if (restrictCount > allRowHeadings.size()) {
                restrictCount = allRowHeadings.size();
                loggedInConnection.setRestrictCount(region, restrictCount);
            }
            if (restrictCount > sortedRows.size()) {
                restrictCount = sortedRows.size();
                loggedInConnection.setRestrictCount(region, restrictCount);
            }

            for (int rowInt = 0; rowInt < restrictCount; rowInt++) {
                rowHeadingsWithData.add(allRowHeadings.get(sortedRows.get(rowInt)));
            }
            return outputHeadings(rowHeadingsWithData, language, loggedInConnection.getRowHeadingSupplements(region));
        } else if (filterCount == -1) {//Online reports with no filtercount or sorting
            return outputHeadings(loggedInConnection.getRowHeadings(region), language, loggedInConnection.getRowHeadingSupplements(region));


        } else {
            return getFullRowHeadings(loggedInConnection, region, headingsSent);
        }
    }

    /* ok so transposing happens here
    this is because the expand headings function is orientated for for headings and the column heading definitions are unsurprisingly set up for columns
     what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

     NOTE : this means the column heading are not stored according to the orientation used in the above function

      hence, to output them we have to transpose them again!

     */


    public String getColumnHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        List<List<List<Name>>> columnHeadingLists = new ArrayList<List<List<Name>>>();
        List<String> supplementNames = new ArrayList<String>();//not used for column headings, but needed for the interpretation routine
        // rows, columns, cells (which can have many names (e.g. xxx;elements), I mean rows and columns and cells of a region saying what the headings should be, not the headings themselves!
        // "here is what that 2d heading definition excel region looks like in names"
        String error = createNameListsFromExcelRegion(loggedInConnection, columnHeadingLists, supplementNames, headingsSent, loggedInConnection.getLanguages());
        if (error.length() > 0) {
            return error;
        }
        loggedInConnection.setColumnHeadings(region, (expandHeadings(transpose2DList(columnHeadingLists))));
        String language = stringUtils.getInstruction(headingsSent, "language");
        return outputHeadings(transpose2DList(loggedInConnection.getColumnHeadings(region)), language, null);
    }

    /*

    OK, having generified the function we should only need one function. The list could be anything, names, list of names, hashmaps whatever
    generics ensure that the return type will match the sent type
    now rather similar to the stacktrace example :)

    Variable names assume first list is of rows and the second is each row. down then across.
    So the size of the first list is the ysize (number of rows) and the size of the nested list the xsize (number of columns)
    I'm going to model it that way round as when reading data from excel that's the default (we go line by line through each row, that's how the data is delivered), the rows is the outside list
    of course could reverse all descriptions and teh function could still work

    */

    public <T> List<List<T>> transpose2DList(final List<List<T>> source2Dlist) {
        final List<List<T>> flipped = new ArrayList<List<T>>();
        if (source2Dlist.size() == 0) {
            return flipped;
        }
        final int oldXMax = source2Dlist.get(0).size(); // size of nested list, as described above (that is to say get the length of one row)
        for (int newY = 0; newY < oldXMax; newY++) {
            List<T> newRow = new ArrayList<T>(); // make a new row
            for (List<T> oldRow : source2Dlist) { // and step down each of the old rows
                newRow.add(oldRow.get(newY));//so as we're moving across the new row we're moving down the old rows on a fixed column
                // the transposing is happening as a list which represents a row would typically be accessed by an x value but instead it's being accessed by an y value
                // in this loop the row being read from changes but the cell in that row does not
            }
            flipped.add(newRow);
        }
        return flipped;
    }

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
        if (searchNames==null) return null;
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
            if (alreadyThere != null){
                alreadyThere.add(value);
            }else{
                Set<Value> newValues = new HashSet<Value>();
                newValues.add(value);
                showValues.put(sumNames, newValues);
            }
          }
        return showValues;
    }

    public String addValues(Set<Value>values){

        String stringVal = null;
        Double doubleVal = 0.0;
        boolean percentage = false;
        for (Value value:values) {
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

    public LinkedHashSet<Name> getHeadings(Map <Set<Name>, Set<Value>> showValues){
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

    }


    public String getExcelDataForNamesSearch(final List<Set<Name>> searchNames) throws Exception {
        final StringBuilder sb = new StringBuilder();
        Map<Set<Name>, Set<Value>> showValues = getSearchValues(searchNames);
        sb.append(" ");
        LinkedHashSet<Name> headings = getHeadings(showValues);
        for (Name heading : headings) {
            sb.append("\t").append(heading.getDefaultDisplayName());
        }
        sb.append("\n");
        int count = 0;
        for (Set<Name> valNames : showValues.keySet()) {
            if (count++ == 2000) {
                break;
            }
            sb.append(addValues(showValues.get(valNames)));
            String[] names = new String[headings.size()];
            int i = 0;
            for (Name heading : headings) {
                for (Name name : valNames) {
                    if (name.findATopParent() ==heading) {
                        names[i] = name.getDefaultDisplayName();
                    }
                }
                i++;
            }
            for (String name : names) {
                if (name != null) {
                    sb.append("\t").append(name);
                } else {
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private boolean isInPeers(Name name, Map<Name, Boolean> peers) {
        for (Name peer : peers.keySet()) {
            if (peer == name) return true;

        }
        for (Name parent : name.getParents()) {
            if (isInPeers(parent, peers)) {
                return true;
            }
        }
        return false;
    }

    // todo edd understand

    private void createCellNameList(final Set<Name> namesForThisCell, final List<Name> rowName, final List<Name> columnName, final List<Name> contextNames) {
        namesForThisCell.addAll(contextNames);
        namesForThisCell.addAll(columnName);
        namesForThisCell.addAll(rowName);
        //now check that all names are needed
        /*  THERE MAY BE INTERSECTIONS OF SETS IN CONTEXT WITH OTHER SETS,

        Map<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>();
        for (Name peerCell : columnName) {
            if (peerCell != null && peerCell.getPeers().size() > 0) {
                peers = peerCell.getPeers();
            }
        }
        if (peers.size() == 0) {
            for (Name peerCell : rowName) {
                if (peerCell != null && peerCell.getPeers().size() > 0) {
                    peers = peerCell.getPeers();
                }
            }
        }
        if (peers.size() > 0 && peers.size() + 1 != namesForThisCell.size()) {
            // we may discard some names
            List<Name> surplusNames = new ArrayList<Name>();
            for (Name name : namesForThisCell) {
                if (name.getPeers().size() == 0 && !isInPeers(name, peers)) {
                    surplusNames.add(name);
                }
            }
            namesForThisCell.removeAll(surplusNames);
        }
        */
    }


    private void formatLockMap(LoggedInConnection loggedInConnection, String region, List<List<Boolean>> lockMap) {
        StringBuffer sb = new StringBuffer();
        boolean firstRow = true;
        for (List<Boolean> row : lockMap) {
            if (firstRow) {
                firstRow = false;
            } else {
                sb.append("\n");
            }
            boolean firstCol = true;
            for (Boolean lock : row) {
                if (firstCol) {
                    firstCol = false;
                } else {
                    sb.append("\t");
                }
                if (lock) {
                    sb.append("LOCKED");
                }
            }
        }
        loggedInConnection.setLockMap(region, sb.toString());
    }

    public final StringBuilder formatDataRegion(LoggedInConnection loggedInConnection, String region, List<List<String>> shownValueArray, int filterCount, int restrictCount, Map<Integer, Double> sortTotals) {

        int rowInt = 0;
        int blockRowCount = 0;
        int outputMarker = 0;
        boolean firstRow = true;
        if (restrictCount == 0) {
            restrictCount = sortTotals.size();
        }
        final StringBuilder sb = new StringBuilder();
        List<Integer> sortedRows = loggedInConnection.getRowOrder(region);
        if (restrictCount > sortedRows.size()) {
            restrictCount = sortedRows.size();
        }
        for (int rowNo = 0; rowNo < restrictCount; rowNo++) {

            List<String> rowValuesShown = shownValueArray.get(sortedRows.get(rowNo));
            if (blockRowCount == 0) {
                outputMarker = sb.length();// in case we need to truncate it.
            }
            if (!firstRow) {
                sb.append("\n");
            }
            boolean newRow = true;
            for (String colValue : rowValuesShown) {
                if (!newRow) {
                    sb.append("\t");
                }
                sb.append(colValue);
                newRow = false;

            }

            rowInt++;
            firstRow = false;
            if (++blockRowCount == filterCount) {
                if (blankRows(loggedInConnection, region, rowInt - filterCount, filterCount)) {
                    sb.delete(outputMarker, sb.length());
                }
                blockRowCount = 0;
            }
        }
        loggedInConnection.setSentDataMap(region, sb.toString());

        return sb;
    }

    public String getDataRegion(LoggedInConnection loggedInConnection, String context, String region, int filterCount, int maxRows) throws Exception {

        if (loggedInConnection.getRowHeadings(region) == null || loggedInConnection.getRowHeadings(region).size() == 0 || loggedInConnection.getColumnHeadings(region) == null || loggedInConnection.getColumnHeadings(region).size() == 0) {
            return "no headings passed";
        }


        loggedInConnection.getProvenance("in spreadsheet").setContext(context);

        final StringTokenizer st = new StringTokenizer(context, "\n");
        final List<Name> contextNames = new ArrayList<Name>();
        while (st.hasMoreTokens()) {
            final List<Name> thisContextNames = nameService.interpretName(loggedInConnection,st.nextToken().trim());
            if (thisContextNames.size() > 1){
                return "error: context names must be individual - use 'as' to put sets in context";
            }
            if (thisContextNames.size() > 0) {
                 //Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim(), loggedInConnection.getLanguages());
                contextNames.add(thisContextNames.get(0));
            }
        }
        return getExcelDataForColumnsRowsAndContext(loggedInConnection, contextNames, region, filterCount, maxRows);
    }


    public String getExcelDataForColumnsRowsAndContext(final LoggedInConnection loggedInConnection, final List<Name> contextNames, final String region, int filterCount, int restrictCount) throws Exception {
        loggedInConnection.setContext(region, contextNames); // needed for provenance
        long track = System.currentTimeMillis();
        Integer sortCol = loggedInConnection.getSortCol(region);
        if (sortCol == null){
            sortCol = -1;
        }
        if (sortCol >=0 && restrictCount == 0){
            restrictCount = loggedInConnection.getRowHeadings(region).size();//this is a signal to sort the rows
        }

        final List<List<List<Value>>> dataValuesMap = new ArrayList<List<List<Value>>>(loggedInConnection.getRowHeadings(region).size()); // rows, columns, lists of values
        loggedInConnection.setDataValueMap(region, dataValuesMap);
        final Map<Integer, Double> sortTotals = new HashMap<Integer, Double>();
        List<List<Set<Name>>> dataNamesMap = new ArrayList<List<Set<Name>>>(loggedInConnection.getRowHeadings(region).size()); // rows, columns, lists of names for each cell
        List<List<String>> shownValueArray = new ArrayList<List<String>>();
        List<List<Boolean>> lockArray = new ArrayList<List<Boolean>>();
        int rowNo = 0;
        Map<Name,Integer> totalSetSize = new HashMap<Name, Integer>();
        for (List<Name> rowName : loggedInConnection.getRowHeadings(region)) { // make it like a document
            ArrayList<List<Value>> thisRowValues = new ArrayList<List<Value>>(loggedInConnection.getColumnHeadings(region).size());
            ArrayList<Set<Name>> thisRowNames = new ArrayList<Set<Name>>(loggedInConnection.getColumnHeadings(region).size());
            List<String> shownValues = new ArrayList<String>();
            List<Boolean> lockedCells = new ArrayList<Boolean>();
            dataValuesMap.add(thisRowValues);
            dataNamesMap.add(thisRowNames);
            shownValueArray.add(shownValues);
            lockArray.add(lockedCells);


            double sortTotal = 0.0;//note that, if there is a 'sortCol' then only that column is added to the total.
            boolean hasValues = false;
            int colNo = 0;
            for (List<Name> columnName : loggedInConnection.getColumnHeadings(region)) {
                final Set<Name> namesForThisCell = new HashSet<Name>();
                createCellNameList(namesForThisCell, rowName, columnName, contextNames);
                // edd putting in peer check stuff here, should I not???
                MBoolean locked = new MBoolean(); // we can pass a mutable boolean in and have the function set it
                // why bother?   Maybe leave it as 'on demand' when a data region doesn't work
                // Map<String, String> result = nameService.isAValidNameSet(azquoMemoryDBConnection, namesForThisCell, new HashSet<Name>());
                // much simpler check - simply that the list is complete.
                boolean checked = true;
                for (Name name : namesForThisCell) {
                    if (name == null) checked = false;
                }
                if (!checked) { // not a valid peer set? Show a blank locked cell
                    shownValues.add("");
                    lockedCells.add(true);
                    thisRowValues.add(null);
                } else {

                    List<Value> values = new ArrayList<Value>();
                    thisRowValues.add(values);
                    thisRowNames.add(namesForThisCell);
                    // TODO - peer additive check. If using peers and not additive, don't include children
                    double cellValue = findValueForNames(loggedInConnection, namesForThisCell, locked, true, values, totalSetSize); // true = pay attention to names additive flag
                    //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                    if (sortCol==-1 || sortCol == colNo) {
                        if (restrictCount < 0) {
                            sortTotal += cellValue;
                        } else {
                            sortTotal -= cellValue;
                        }
                    }
                    if (values.size() > 0) {
                        hasValues = true;
                    }
                    if (values.size() == 1 && !locked.isTrue) {
                        for (Value value : values) {
                            shownValues.add(value.getText());
                            if (sortCol == colNo && !NumberUtils.isNumber(value.getText())){
                                //make up a suitable double to get some kind of order! sort on 8 characters
                                String padded = value.getText() + "        ";
                                for (int i = 0;i< 8; i++){
                                    sortTotal = sortTotal * 64 + padded.charAt(i) - 32;
                                }
                            }
                        }
                    } else {
                        shownValues.add(cellValue + "");
                    }
                    if (locked.isTrue) {
                        lockedCells.add(true);
                    } else {
                        lockedCells.add(false);
                    }
                }
                colNo++;
            }
            sortTotals.put(rowNo++, sortTotal);

        }
        loggedInConnection.setRowOrder(region, sortRows(restrictCount, sortTotals));
        loggedInConnection.setRestrictCount(region, restrictCount);
        final StringBuilder sb = formatDataRegion(loggedInConnection, region, shownValueArray, filterCount, restrictCount, sortTotals);
        formatLockMap(loggedInConnection, region, lockArray);

        printSumStats();
        printFindForNamesIncludeChildrenStats();
        loggedInConnection.setDataNamesMap(region, dataNamesMap);
        logger.info("time to execute : " + (System.currentTimeMillis() - track));
        return sb.toString();
    }


    private String jsonElement(String elementName, String elementValue) {
        return "\"" + elementName + "\":\"" + elementValue.replace("\"", "\\") + "\"";
    }

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

    }

    private String stripCurrency(String val){
        //TODO we need to be able to detect other currencies

        if (val.length() > 1 && "$£".contains(val.substring(0,1))){
            return val.substring(1);

        }
        return val;
    }

    private boolean compareStringValues(final String val1, final String val2){
        //tries to work out if numbers expressed with different numbers of decimal places, maybe including percentage signs and currency symbols are the same.
        if (val1.equals(val2)) return true;
        String val3 = val1;
        String val4 = val2;
        if (val1.endsWith("%") && val2.endsWith("%")){
            val3 = val1.substring(0, val1.length() - 1);
            val4 = val2.substring(0, val2.length() - 1);
         }
        val3 = stripCurrency(val3);
        val4 = stripCurrency(val4);
        if (NumberUtils.isNumber(val3) && NumberUtils.isNumber(val4)){
            Double n1 = Double.parseDouble(val3);
            Double n2 = Double.parseDouble(val4);
            if (n1 - n2 == 0) return true;
        }
        return false;
    }


    public int getAge(LoggedInConnection loggedInConnection, String region, int rowInt, int colInt){
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();

        final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);
        final List<Integer> rowOrder = loggedInConnection.getRowOrder(region);
        if (rowOrder != null){
            rowInt = rowOrder.get(rowInt);
        }

        int age = 10000;
        if (dataValueMap == null) return age;
        if (rowInt >= dataValueMap.size()) return age;
        if (dataValueMap.get(rowInt) == null) return age;

        final List<List<Value>> rowValues = dataValueMap.get(rowInt);
        if (colInt >=rowValues.size()){// a blank column
            return age;
        }
        final List<Value> valuesForCell = rowValues.get(colInt);
        if (valuesForCell==null || valuesForCell.size() == 0){
             return 0;
        }
        if (valuesForCell.size()==1){
            for (Value value:valuesForCell){
                if (value == null){//cell has been changed
                    return 0;
                }
            }
        }
        for (Value value : valuesForCell) {
            if (value.getText().length() > 0) {
                Date provdate = value.getProvenance().getTimeStamp();

                final long dateSubtract = today.getTime() - provdate.getTime();
                final long time = 1000 * 60 * 60 * 24;

                final int cellAge = (int) (dateSubtract / time);
                if (cellAge < age) {
                    age = cellAge;
                }
            }
        }
        return age;
    }


    public String formatDataRegionProvenanceForOutput(LoggedInConnection loggedInConnection, String region, int rowInt, int colInt, String jsonFunction) {
        final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);
        final List<Integer> rowOrder = loggedInConnection.getRowOrder(region);


        if (dataValueMap != null) {
            if (dataValueMap.get(rowInt) != null) {
                final List<List<Value>> rowValues = dataValueMap.get(rowOrder.get(rowInt));

                if (rowValues.get(colInt) != null) {
                    final List<Value> valuesForCell = rowValues.get(colInt);
                    final Set<Name> originalCellNames = new HashSet<Name>();
                    //Need to find the difference between this value and the visible value.  First find the visible names on the cell
                    originalCellNames.addAll(loggedInConnection.getContext(region));
                    originalCellNames.addAll(loggedInConnection.getRowHeadings(region).get(rowOrder.get(rowInt)));
                    originalCellNames.addAll(loggedInConnection.getColumnHeadings(region).get(colInt));
                    //Set<Name> specialForProvenance = new HashSet<Name>();


                    return formatCellProvenanceForOutput(loggedInConnection, originalCellNames, valuesForCell, jsonFunction);
                } else {
                    return ""; //return "error: col out of range : " + colInt;
                }
            } else {
                return ""; //"error: row out of range : " + rowInt;
            }
        } else {
            return ""; //"error: data has not been sent for that row/col/region";
        }
    }

    private Name getMostUsedName(Set<Value> values, Name topParent) {
        Map<Name, Integer> nameCount = new HashMap<Name, Integer>();
        for (Value value : values) {
            for (Name name : value.getNames()) {
                if (topParent == null || name.findATopParent()==topParent) {
                    Integer origCount = nameCount.get(name);
                    if (origCount == null) {
                        nameCount.put(name, 1);
                    } else {
                        nameCount.put(name, origCount + 1);
                    }
                }
            }
        }
        if (nameCount.size() == 0){
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

        Collections.sort(values, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Value) (o1)).getProvenance().getTimeStamp())
                        .compareTo(((Value) (o2)).getProvenance().getTimeStamp());
            }
        });

    }


    private Set<Name> listDiff(Set<Name> list1, Set<Name> list2) {
        Set<Name> diff = new HashSet<Name>();
        diff.addAll(list1);
        diff.removeAll(list2);
        return diff;
    }


    private StringBuffer printBatch(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values) {
        StringBuffer sb = new StringBuffer();
        int debugCount = 0;
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
            debugCount = value.getNames().size();
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
                        debugCount = slimValue.getNames().size();
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


    private String jsonValue(String val1, String val2, boolean comma) {
        String result = "\"" + val1 + "\":\"" + val2 + "\"";
        if (!comma) {
            return result;

        }
        return "," + result;
    }


    private StringBuffer printExtract(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values, Provenance p) {
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        StringBuffer sb = new StringBuffer();
        sb.append(jsonValue("heading", "<b>" +  df.format(p.getTimeStamp()) + "</b> by <b>" + p.getUser() + "</b><br/>Method:" + p.getMethod() + " " + p.getName(), false));
        sb.append(",\"items\":[");
        sb.append(printBatch(azquoMemoryDBConnection, values));
        sb.append("]");
        return sb;
    }


    // As I understand this function is showing names attached to the values in this cell that are not in the requesting spread sheet's row/column/context
    // not exactly sure why
    // this might make it a bit more difficult to jackson but we should aim to do it really

    public String formatCellProvenanceForOutput(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Name> origNames, List<Value> values, String jsonFunction) {

        StringBuffer output = new StringBuffer();
        output.append(jsonFunction + "({\"provenance\":[{");
        int count = 0;
        if (values.size() > 1 || (values.size() > 0 && values.get(0) != null)) {
            sortValues(values);

            //simply sending out values is a mess - hence this ruse: extract the most persistent names as headings
            Date provdate = values.get(0).getProvenance().getTimeStamp();
            Set<Value> oneUpdate = new HashSet<Value>();
            Provenance p = null;
            boolean firstHeading = true;
            for (Value value : values) {
                if (value.getProvenance().getTimeStamp() == provdate) {
                    oneUpdate.add(value);
                    p = value.getProvenance();
                } else {
                    if (firstHeading){
                        firstHeading= false;
                    }else{
                        output.append("},{");
                    }
                    output.append(printExtract(azquoMemoryDBConnection, oneUpdate, p));
                    oneUpdate = new HashSet<Value>();
                    provdate = value.getProvenance().getTimeStamp();
                }
            }
            if (!firstHeading){
                 output.append(",");
            }
            output.append(printExtract(azquoMemoryDBConnection, oneUpdate, p));
        }
        output.append("}]})");
        return output.toString();


    }


    public String formatProvenanceForOutput(Provenance provenance, String jsonFunction) {

        String output;
        if (provenance == null) {
            output = "{provenance:[{\"who\":\"no provenance\"}]}";
        } else {
            //String user = provenance.getUser();
            output = "{\"provenance\":[{\"who\":\"" + provenance.getUser() + "\",\"when\":\"" + provenance.getTimeStamp() + "\",\"how\":\"" + provenance.getMethod() + "\",\"where\":\"" + provenance.getName() + "\",\"value\":\"\",\"context\":\"" + provenance.getContext().replace("\n", ",") + "\"}]}";
        }
        if (jsonFunction != null && jsonFunction.length() > 0) {
            return jsonFunction + "(" + output + ")";
        } else {
            return output;
        }
    }


    public String saveData(LoggedInConnection loggedInConnection, String region, String editedData) throws Exception {
        String result = "";
        logger.info("------------------");
        logger.info(loggedInConnection.getLockMap(region));
        logger.info(loggedInConnection.getRowHeadings(region));
        logger.info(loggedInConnection.getColumnHeadings(region));
        logger.info(loggedInConnection.getSentDataMap(region));
        logger.info(loggedInConnection.getContext(region));
        // I'm not sure if these conditions are quite correct maybe check for getDataValueMap and getDataNamesMap instead of columns and rows etc?
        if (loggedInConnection.getLockMap(region) != null &&
                loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0
                && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0
                && loggedInConnection.getSentDataMap(region) != null && loggedInConnection.getContext(region) != null) {
            // oh-kay, need to compare against the sent data
            // going to parse the data here for the moment as parsing is controller stuff
            // I need to track column and Row
            int rowCounter = 0;
            final String[] originalReader = loggedInConnection.getSentDataMap(region).split("\n",-1);
            final String[] editedReader = editedData.split("\n",-1);
            final String[] lockLines = loggedInConnection.getLockMap(region).split("\n",-1);
              // rows, columns, value lists
            final List<List<List<Value>>> dataValuesMap = loggedInConnection.getDataValueMap(region);
            final List<List<Set<Name>>> dataNamesMap = loggedInConnection.getDataNamesMap(region);
            // TODO : deal with mismatched column and row counts
            int numberOfValuesModified = 0;
            List<Integer> sortedRows = loggedInConnection.getRowOrder(region);

            for (int rowNo = 0; rowNo < lockLines.length; rowNo++) {
                String lockLine = lockLines[rowNo];
                int columnCounter = 0;
                final List<List<Value>> rowValues = dataValuesMap.get(sortedRows.get(rowCounter));
                final List<Set<Name>> rowNames = dataNamesMap.get(rowCounter);
                final String[] originalValues = originalReader[rowNo].split("\t",-1);//NB Include trailing empty strings.
                final String[] editedValues = editedReader[rowNo].split("\t",-1);
                String[] locks = lockLine.split("\t", -1);
                for (int colNo = 0; colNo < locks.length; colNo++) {
                    String locked = locks[colNo];
                    //System.out.println("on " + columnCounter + ", " + rowCounter + " locked : " + locked);
                    // and here we get to the crux, the values do NOT match
                    // ok assign these two then deal with doubvle formatting stuff that can trip things up
                    String orig = originalValues[columnCounter].trim();
                    String edited = editedValues[columnCounter].trim();
                    if (orig.endsWith(".0")) {
                        orig = orig.substring(0, orig.length() - 2);
                    }
                    if (edited.endsWith(".0")) {
                        edited = edited.substring(0, edited.length() - 2);
                    }
                    if (!compareStringValues(orig,edited)) {
                        if (!locked.equalsIgnoreCase("locked")) { // it wasn't locked, good to go, check inside the different values bit to error if the excel tries something it should not
                            logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                            logger.info(orig + "|" + edited + "|");

                            final List<Value> valuesForCell = rowValues.get(columnCounter);
                            final Set<Name> namesForCell = rowNames.get(columnCounter);
                            // one thing about these store functions to the value service, they expect the provenance on the logged in connection to be appropriate
                            if (valuesForCell.size() == 1) {
                                final Value theValue = valuesForCell.get(0);
                                logger.info("trying to overwrite");
                                if (edited.length() > 0) {
                                    //sometimes non-existant original values are stored as '0'
                                    overWriteExistingValue(loggedInConnection, theValue, edited);
                                    numberOfValuesModified++;
                                }else
                                    deleteValue(theValue);
                            } else if (valuesForCell.isEmpty() && edited.length()> 0) {
                                logger.info("storing new value here . . .");
                                storeValueWithProvenanceAndNames(loggedInConnection, edited, namesForCell);
                                numberOfValuesModified++;
                            }
                        } else {
                            // TODO  WORK OUT WHAT TO BE DONE ON ERROR - what about calculated cells??
                            //result = "error:cannot edit locked cell " + columnCounter + ", " + rowCounter + " in region " + region;

                        }
                    }
                    columnCounter++;
                }
                rowCounter++;
            }
            result = numberOfValuesModified + " values modified";
            //putting in a 'persist' here for security.
            if (numberOfValuesModified > 0) {
                loggedInConnection.persist();
            }
        } else {
            result = " no sent data/rows/columns/context";
        }
        return result;

    }

}