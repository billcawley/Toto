package com.azquo.service;

import com.azquo.adminentities.OnlineReport;
import com.azquo.jsonrequestentities.ValueJsonRequest;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import com.azquo.memorydb.Value;
import com.csvreader.CsvReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.StringReader;
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

    public Value createValue(final LoggedInConnection loggedInConnection, final Provenance provenance, final String text) throws Exception {
        return new Value(loggedInConnection.getAzquoMemoryDB(), provenance, text, null);
    }

    // this is passed a string for the value, not sure if that is the best practice, need to think on it.

    public String storeValueWithProvenanceAndNames(final LoggedInConnection loggedInConnection, String valueString, final Set<Name> names) throws Exception {
        String toReturn = "";
        final Set<Name> validNames = new HashSet<Name>();
        final Map<String, String> nameCheckResult = nameService.isAValidNameSet(names, validNames);
        final String error = nameCheckResult.get(NameService.ERROR);
        final String warning = nameCheckResult.get(NameService.WARNING);
        if (error != null) {
            return error;
        } else if (warning != null) {
            toReturn += warning;
        }
        final List<Value> existingValues = findForNames(validNames);
        boolean alreadyInDatabase = false;
        for (Value existingValue : existingValues) { // really should only be one
            if (existingValue.getProvenance().equals(loggedInConnection.getProvenance()) && existingValue.getProvenance().getMethod().equals("import")){
                //new behaviour - add values from same import.
                try{
                    Double existingDouble = Double.parseDouble(existingValue.getText());
                    Double newValue = Double.parseDouble(valueString);
                    valueString = (existingDouble + newValue) + "";
                    existingValue.setText(valueString);
                }catch (Exception e){
                    // use the latest value
                }
            }
            if (existingValue.getText().equals(valueString)) {
                toReturn += "  that value already exists, skipping";
                alreadyInDatabase = true;
            } else {
                deleteValue(existingValue);
                // provenance table : person, time, method, name
                toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
            }
        }
        if (!alreadyInDatabase) {
            // create
            Value value = createValue(loggedInConnection, loggedInConnection.getProvenance(), valueString);
            toReturn += "  stored";
            // and link to names
            value.setNamesWillBePersisted(validNames);
        }
        return toReturn;
    }


    public boolean overWriteExistingValue(final LoggedInConnection loggedInConnection, final Value existingValue, final String newValueString) throws Exception {
        Value newValue = new Value(loggedInConnection.getAzquoMemoryDB(), loggedInConnection.getProvenance(), newValueString, null);
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
        long start = System.nanoTime();

        final List<Value> values = new ArrayList<Value>();
        // first get the shortest value list taking into account children
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names) {
            int setSizeIncludingChildren = name.getValues().size();
            for (Name child : name.findAllChildren(payAttentionToAdditive)) {
                setSizeIncludingChildren += child.getValues().size();
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
                        boolean foundInChildList = false;
                        for (Name child : name.findAllChildren(payAttentionToAdditive)) {
                            if (value.getNames().contains(child)) {
                                foundInChildList = true;
                                break;
                            }
                        }
                        if (!foundInChildList) {
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
        for (Name name:smallestNames){
            valueSet.addAll(findValuesForNameIncludeAllChildren(name, payAttentionToAdditive));
        }
        part2NanoCallTime1 += (System.nanoTime() - point);
        point = System.nanoTime();
        for (Value value : valueSet) {
            boolean theValueIsOk = true;
            for (Set<Name> nameSet : names) {
                if (!nameSet.equals(smallestNames)) { // ignore the one we started with
                    boolean foundInChildList = false;
                    for(Name valueNames:value.getNames()){
                        if (nameService.inParentSet(valueNames,nameSet) !=null){
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
        if (numberOfTimesCalled1 > 0){
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

    public double findValueForNames(final LoggedInConnection loggedInConnection, final Set<Name> names, final MutableBoolean locked, final boolean payAttentionToAdditive, List<Value> valuesFound) {
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<Name>();
        String calcString = "";
        boolean hasCalc = false;
        // add all names to calcnames except the the one with RPCALC
        // and here's a thing : if more than one name has RPcalc then only the first will be used
        for (Name name : names) {
            if (!hasCalc) {
                calcString = name.getAttribute("RPCALC");
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
            locked.setValue(false);
            for (Name oneName: names){
                    if ((oneName.getPeers().size()==0 && oneName.getChildren().size() > 0) || !nameService.isAllowed(oneName, loggedInConnection.getWritePermissions())) locked.setValue(true);
            }
            return findSumForNamesIncludeChildren(names, payAttentionToAdditive, valuesFound);
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
                        Name name = nameService.findById(loggedInConnection, id);
                        calcnames.add(name);
                        // and put the result in
                        //note - recursion in case of more than one formula, but the order of the formulae is undefined if the formulae are in different peer groups
                        values[valNo++] = findValueForNames(loggedInConnection, new HashSet<Name>(calcnames), locked, payAttentionToAdditive, valuesFound);
                        calcnames.remove(calcnames.size() - 1);
                    }
                }
            }
            locked.setValue(true);
            return values[0];
        }
    }

    public double findSumForNamesIncludeChildren(final Set<Name> names, final boolean payAttentionToAdditive, List<Value> valuesFound) {
        //System.out.println("findSumForNamesIncludeChildren");
        long start = System.nanoTime();

        List<Value> values = findForNamesIncludeChildren(names, payAttentionToAdditive);
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
        if (numberOfTimesCalled > 0){
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

    public String outputHeadings(final List<List<Name>> headings, String language, List<Name> rowHeadingSupplements) {

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
                if (lastName != null){
                    String nameInLanguage =lastName.getAttribute(language);
                    if (nameInLanguage==null) {
                        nameInLanguage = lastName.getDefaultDisplayName();
                    }
                    sb.append(nameInLanguage);
                }
            }
            if (rowHeadingSupplements != null){
                for (Name structureName:rowHeadingSupplements){
                    sb.append("\t" + getStructureName(lastName, structureName));
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



    private String getStructureName(Name element, Name className){
        //works out to which set in the class the element belongs (e.g. if topparent = "car", element = "mondeo' and class = "manufacturer", this should find 'Ford cars' and return 'Ford'
        String byName = "by " + className.getDefaultDisplayName();
        for (Name parent:element.getParents()){
            for (Name grandParent:parent.getParents()){
                String grandParentName = grandParent.getDefaultDisplayName();
                if (grandParentName.endsWith(byName)){
                    String plural = grandParentName.substring(0, grandParentName.length() - byName.length()).trim();
                    String parentName = parent.getDefaultDisplayName();
                    if (parentName.length() > plural.length()){
                        return parentName.substring(0,parentName.length() - plural.length()).trim();
                    }

                }
            }
        }

        return "";
    }

    public String createNameListsFromExcelRegion(final LoggedInConnection loggedInConnection, List<List<List<Name>>> nameLists, List<Name> supplementNames, final String excelRegionPasted) throws Exception {
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
                    List<Name> nameList = new ArrayList<Name>();
                    if (cellString.toLowerCase().contains(";with ")){
                        int withPos = cellString.toLowerCase().indexOf(";with ");
                        String withList = cellString.substring(withPos + 6);
                        cellString = cellString.substring(0,withPos);
                        List<Set<Name>> sNames = new ArrayList<Set<Name>>();
                        String error = nameService.decodeString(loggedInConnection,withList, sNames);
                        if (error.length() > 0){
                            return error;
                        }
                        for (Set<Name> sName:sNames){
                            supplementNames.addAll(sName); // sName should be a set of one element
                        }
                    }
                    String error = nameService.interpretName(loggedInConnection, nameList, cellString);
                    if (error.length() > 0) {
                        return error;
                    }
                    row.add(nameList);
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



    private boolean blankRows(LoggedInConnection loggedInConnection, String region, int rowInt, int count){

        final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);

        if (dataValueMap != null) {
            for (int rowCount = 0; rowCount < count; rowCount ++){
                if (dataValueMap.get(rowInt + rowCount) != null) {
                    final List<List<Value>> rowValues = dataValueMap.get(rowInt + rowCount);
                    for (List<Value> oneCell:rowValues){
                        if (oneCell.size() > 0){
                            return  false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static Map sortByComparator(Map unsortMap) {

        List list = new LinkedList(unsortMap.entrySet());

        // sort list based on comparator
        Collections.sort(list, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Map.Entry) (o1)).getValue())
                        .compareTo(((Map.Entry) (o2)).getValue());
            }
        });

        // put sorted list into map again
        //LinkedHashMap make sure order in which keys were inserted
        Map sortedMap = new LinkedHashMap();
        for (Iterator it = list.iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    public List<Integer> sortRows(int restrictCount, Map<Integer, Double> rowTotals){

        List<Integer>sortedRows = new ArrayList<Integer>();
        if (restrictCount != 0){
            List list = new LinkedList(rowTotals.entrySet());

            // sort list based on comparator
            Collections.sort(list, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return ((Comparable) ((Map.Entry) (o1)).getValue())
                            .compareTo(((Map.Entry) (o2)).getValue());
                }
            });

            for (Iterator it = list.iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                sortedRows.add((Integer)entry.getKey());
            }

        }else{
            for (int i = 0;i < rowTotals.size(); i++){
                sortedRows.add(i);
            }
        }
        return sortedRows;

    }
   public String getFullRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent)throws Exception{
       String language = nameService.getInstruction(headingsSent, "language");
       final List<List<List<Name>>> rowHeadingLists = new ArrayList<List<List<Name>>>();
       List <Name> supplementNames = new ArrayList<Name>();
       loggedInConnection.getProvenance().setRowHeadings(headingsSent);
       String error = createNameListsFromExcelRegion(loggedInConnection, rowHeadingLists, supplementNames, headingsSent);
       if (error.length() > 0) {
           return error;
       }
       loggedInConnection.setRowHeadings(region, expandHeadings(rowHeadingLists));
       loggedInConnection.setRowHeadingSupplements(region,supplementNames);
       return outputHeadings(loggedInConnection.getRowHeadings(region), language, loggedInConnection.getRowHeadingSupplements(region));


   }

    public String getRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent, final int filterCount) throws Exception {
        // rows, columns, cells (which can have many names (e.g. xxx;elements), I mean rows and columns and cells of a region saying what the headings should be, not the headings themselves!
        // "here is what that 2d heading definition excel region looks like in names"
        String language = nameService.getInstruction(headingsSent, "language");


        if (filterCount > 0){
            //send back only those headings that have data - considered in batches of length filtercount.
            List<List<Name>> rowHeadingsWithData = new ArrayList<List<Name>>();
            List<List<Name>> allRowHeadings = loggedInConnection.getRowHeadings(region);
            int rowInt = 0;
            while (rowInt < allRowHeadings.size()){
                if ( !blankRows(loggedInConnection,region, rowInt, filterCount)){
                    for (int rowCount = 0; rowCount< filterCount; rowCount++){
                        rowHeadingsWithData.add(allRowHeadings.get(rowInt + rowCount));
                    }
                }
                rowInt += filterCount;
            }
            //note that the sort order has already been set.... there cannot be both a restrict count and a filter count
            return outputHeadings(rowHeadingsWithData, language, loggedInConnection.getRowHeadingSupplements(region));
        }else if (loggedInConnection.getRestrictCount(region)!=null && loggedInConnection.getRestrictCount(region)!= 0) {
            int restrictCount = loggedInConnection.getRestrictCount(region);
            List<Integer> sortedRows = loggedInConnection.getRowOrder(region);
            List<List<Name>> rowHeadingsWithData = new ArrayList<List<Name>>();
            List<List<Name>> allRowHeadings = loggedInConnection.getRowHeadings(region);
            if (restrictCount > allRowHeadings.size()) {
                restrictCount = allRowHeadings.size();
                loggedInConnection.setRestrictCount(region, restrictCount);
            }
            for (int rowInt = 0; rowInt < restrictCount; rowInt++) {
                rowHeadingsWithData.add(allRowHeadings.get(sortedRows.get(rowInt)));
            }
            return outputHeadings(rowHeadingsWithData, language, loggedInConnection.getRowHeadingSupplements(region));
        }else if (filterCount==-1){//Online reports with no filtercount or sorting
            return outputHeadings(loggedInConnection.getRowHeadings(region), language, loggedInConnection.getRowHeadingSupplements(region));


        }else{
            return getFullRowHeadings(loggedInConnection,region,headingsSent);
        }
    }

    /* ok so transposing happens here
    this is because the expand headings function is orientated for for headings and the column heading definitions are unsurprisingly set up for columns
     what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

     NOTE : this means the column heading are not stored according to the orientation used in the above function

      hence, to output them we have to transpose them again!

     */


    public String getColumnHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        loggedInConnection.getProvenance().setColumnHeadings(headingsSent);
        List<List<List<Name>>> columnHeadingLists = new ArrayList<List<List<Name>>>();
        List<Name> supplementNames = new ArrayList<Name>();//not used for column headings, but needed for the interpretation routine
        // rows, columns, cells (which can have many names (e.g. xxx;elements), I mean rows and columns and cells of a region saying what the headings should be, not the headings themselves!
        // "here is what that 2d heading definition excel region looks like in names"
        String error = createNameListsFromExcelRegion(loggedInConnection, columnHeadingLists, supplementNames, headingsSent);
        if (error.length() > 0) {
            return error;
        }
        loggedInConnection.setColumnHeadings(region, (expandHeadings(transpose2DList(columnHeadingLists))));
        String language = nameService.getInstruction(headingsSent, "language");
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

    private Name sumName(Name name, List<Set<Name>> searchNames){
        for (Set<Name> searchName: searchNames){
            Name maybeParent = nameService.inParentSet(name, searchName);
            if (maybeParent != null){
                return maybeParent;
            }
        }
        return name;


    }


    public Map<Set<Name>, String> getSearchValues(final List<Set<Name>>searchNames) throws Exception {
        Set<Value> values = findForSearchNamesIncludeChildren(searchNames, false);
        //The names on the values have been moved 'up' the tree to the name that was searched
        // e.g. if the search was 'England' and the name was 'London' then 'London' has been replaced with 'England'
        // so there may be duplicates in an unordered search - hence the consolidation below.
        final Map<Set<Name>, String> showStrings = new HashMap<Set<Name>,String>();
        for (Value value:values){
            Set<Name> sumNames = new HashSet<Name>();
            for (Name name:value.getNames()){
                sumNames.add(sumName(name, searchNames));
            }
            String alreadyThere = showStrings.get(sumNames);
            if (alreadyThere != null){
                //handle percentages, but not currency prefixes currently

                if (NumberUtils.isNumber(alreadyThere) && NumberUtils.isNumber(value.getText())){
                    showStrings.put(sumNames,(Double.parseDouble(alreadyThere) + Double.parseDouble(value.getText()))+"");
                }else if (alreadyThere.endsWith("%") && value.getText().endsWith("%")){
                    showStrings.put(sumNames, Double.parseDouble(alreadyThere.substring(0,alreadyThere.length() - 1)) + Double.parseDouble(value.getText().substring(0,value.getText().length()-1)) + "%");
                }else{
                    showStrings.put(sumNames, alreadyThere + "+" + value.getText());
                }
            }else{
                showStrings.put(sumNames,value.getText());
            }
            for (Set<Name> nameSet:showStrings.keySet()){
                String val = showStrings.get(nameSet);
                if (NumberUtils.isNumber(val) && val.endsWith(".0")){
                    showStrings.put(nameSet,val.substring(0, val.length() - 2));
                }
            }
        }
        return showStrings;
    }

    public String getExcelDataForNamesSearch(final List<Set<Name>> searchNames) throws Exception {
        final StringBuilder sb = new StringBuilder();
        Map<Set<Name>, String> showValues = getSearchValues(searchNames);
         Set<String> headings = new LinkedHashSet<String>();
        // this may not be optimal, can sort later . . .
        int count = 0;
        for (Set<Name> valNames :showValues.keySet()) {
            if (count++ == 2000) {
                break;
            }
            for (Name name : valNames) {
                if (!headings.contains(name.findTopParent().getDefaultDisplayName())) {
                    headings.add(name.findTopParent().getDefaultDisplayName());
                }
            }
        }
        sb.append(" ");
        for (String heading : headings) {
            sb.append("\t").append(heading);
        }
        sb.append("\n");
        count = 0;
        for (Set<Name> valNames: showValues.keySet()) {
            if (count++ == 2000) {
                break;
            }
            sb.append(showValues.get(valNames));
            String[] names = new String[headings.size()];
            int i = 0;
            for (String heading : headings) {
                for (Name name : valNames) {
                    if (name.findTopParent().getDefaultDisplayName().equals(heading)) {
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
        if (peers.size() > 0 && peers.size() != namesForThisCell.size()) {
            // we must discard some names
            List<Name> surplusNames = new ArrayList<Name>();
            for (Name name : namesForThisCell) {
                if (name.getPeers().size() == 0 && !isInPeers(name, peers)) {
                    surplusNames.add(name);
                }
            }
            namesForThisCell.removeAll(surplusNames);
        }
    }


    public final StringBuilder formatDataRegion(LoggedInConnection loggedInConnection, String region, List<List<String>> shownValueArray, List<List<Boolean>> lockArray, int filterCount, int restrictCount, Map<Integer,Double>rowTotals){

        int rowInt = 0;
        int blockRowCount = 0;
        int outputMarker = 0;
        boolean firstRow = true;
        if (restrictCount==0) {
            restrictCount = rowTotals.size();
        }
        final StringBuilder sb = new StringBuilder();
        final StringBuilder lockMapsb = new StringBuilder();
        List<Integer> sortedRows = loggedInConnection.getRowOrder(region);
        if (restrictCount > sortedRows.size()){
            restrictCount = sortedRows.size();
        }
        for (int rowNo =0;rowNo < restrictCount;rowNo++){

            List<String> rowValuesShown = shownValueArray.get(sortedRows.get(rowNo));
            List<Boolean> locks = lockArray.get(sortedRows.get(rowNo));
            if (blockRowCount == 0){
                outputMarker = sb.length();// in case we need to truncate it.
            }
            if (!firstRow){
                lockMapsb.append("\n");
                sb.append("\n");
            }
            boolean newRow = true;
            Iterator it = locks.iterator();
            for (String colValue:rowValuesShown){
                boolean locked = (Boolean) it.next();
                if (!newRow){
                    lockMapsb.append("\t");
                    sb.append("\t");
                }
                sb.append(colValue);
                if (locked){
                    lockMapsb.append("LOCKED");
                }else{
                    lockMapsb.append("");
                }
                newRow = false;

            }

            rowInt++;
            firstRow = false;
            if (++blockRowCount==filterCount){
                if (blankRows(loggedInConnection, region, rowInt - filterCount, filterCount)){
                    sb.delete(outputMarker, sb.length());
                }
                blockRowCount = 0;
            }
        }
        loggedInConnection.setLockMap(region, lockMapsb.toString());
        loggedInConnection.setSentDataMap(region, sb.toString());

        return sb;
    }
 public String getDataRegion(LoggedInConnection loggedInConnection, String context, String region, int filterCount, int maxRows) throws Exception{

     if (loggedInConnection.getRowHeadings(region) == null || loggedInConnection.getRowHeadings(region).size() == 0 || loggedInConnection.getColumnHeadings(region) == null || loggedInConnection.getColumnHeadings(region).size() == 0){
         return "no headings passed";
     }



     loggedInConnection.getProvenance().setContext(context);

     final StringTokenizer st = new StringTokenizer(context, "\n");
     final List<Name> contextNames = new ArrayList<Name>();
     while (st.hasMoreTokens()) {
         final Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim());
         if (contextName == null) {
             return "error:I can't find a name for the context : " + context;
         }
         contextNames.add(contextName);
     }
     return getExcelDataForColumnsRowsAndContext(loggedInConnection, contextNames, region, filterCount, maxRows);
 }




    public String getExcelDataForColumnsRowsAndContext(final LoggedInConnection loggedInConnection, final List<Name> contextNames, final String region, int filterCount, int restrictCount) throws Exception {
        loggedInConnection.setContext(region, contextNames); // needed for provenance
        long track = System.currentTimeMillis();

        final List<List<List<Value>>> dataValuesMap = new ArrayList<List<List<Value>>>(loggedInConnection.getRowHeadings(region).size()); // rows, columns, lists of values
        loggedInConnection.setDataValueMap(region, dataValuesMap);
        final Map<Integer,Double> rowTotals = new HashMap<Integer,Double>();
        List<List<Set<Name>>> dataNamesMap = new ArrayList<List<Set<Name>>>(loggedInConnection.getRowHeadings(region).size()); // rows, columns, lists of names for each cell
         List<List<String>> shownValueArray = new ArrayList<List<String>>();
        List<List<Boolean>> lockArray = new ArrayList<List<Boolean>>();
        int rowNo = 0;
        for (List<Name> rowName : loggedInConnection.getRowHeadings(region)) { // make it like a document
              ArrayList<List<Value>> thisRowValues = new ArrayList<List<Value>>(loggedInConnection.getColumnHeadings(region).size());
            ArrayList<Set<Name>> thisRowNames = new ArrayList<Set<Name>>(loggedInConnection.getColumnHeadings(region).size());
            List<String> shownValues = new ArrayList<String>();
            List<Boolean> lockedCells = new ArrayList<Boolean>();
            dataValuesMap.add(thisRowValues);
            dataNamesMap.add(thisRowNames);
            shownValueArray.add(shownValues);
            lockArray.add(lockedCells);

            double rowTotal = 0.0;
            for (List<Name> columnName : loggedInConnection.getColumnHeadings(region)) {
                final Set<Name> namesForThisCell = new HashSet<Name>();
                createCellNameList(namesForThisCell, rowName, columnName, contextNames);
                // edd putting in peer check stuff here, should I not???
                 MutableBoolean locked = new MutableBoolean(false); // we can pass a mutable boolean in and have the function set it
                 Map<String, String> result = nameService.isAValidNameSet(namesForThisCell, new HashSet<Name>());
                if (result.get(NameService.ERROR) != null) { // not a valid peer set? Show a blank locked cell
                    shownValues.add("");
                    lockedCells.add(true);
                }else{

                    List<Value> values = new ArrayList<Value>();
                    thisRowValues.add(values);
                    thisRowNames.add(namesForThisCell);
                    // TODO - peer additive check. If using peers and not additive, don't include children
                    double cellValue = findValueForNames(loggedInConnection, namesForThisCell, locked, true, values); // true = pay attention to names additive flag
                    //if there's only one value, treat it as text (it may be text, or may include £,$,%)
                    if (restrictCount < 0){
                        rowTotal += cellValue;
                    }else{
                        rowTotal -= cellValue;
                    }
                    if (values.size() == 1){
                        for (Value value:values){
                            shownValues.add(value.getText());
                        }
                    }else{
                        shownValues.add(cellValue + "");
                    }
                    if (locked.isTrue()){
                        lockedCells.add(true);
                    }else{
                        lockedCells.add(false);
                    }
                }
            }
            rowTotals.put(rowNo++, rowTotal);

        }
        loggedInConnection.setRowOrder(region,sortRows(restrictCount, rowTotals));
        loggedInConnection.setRestrictCount(region,restrictCount);
        final StringBuilder sb =  formatDataRegion(loggedInConnection,region, shownValueArray, lockArray, filterCount, restrictCount, rowTotals);

        printSumStats();
        printFindForNamesIncludeChildrenStats();
        loggedInConnection.setDataNamesMap(region, dataNamesMap);
        logger.info("time to execute : " + (System.currentTimeMillis() - track));
        return sb.toString();
    }


    private String jsonElement(String elementName, String elementValue){
        return "\"" + elementName + "\":\"" + elementValue.replace("\"","\\") + "\"";
    }



}