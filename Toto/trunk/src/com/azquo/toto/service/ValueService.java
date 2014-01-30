package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Value;
import com.csvreader.CsvReader;
import org.apache.commons.lang.mutable.MutableBoolean;
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

    public Value createValue(final LoggedInConnection loggedInConnection, final Provenance provenance, final String text) throws Exception {
        return new Value(loggedInConnection.getTotoMemoryDB(), provenance, text, null);
    }

    // this is passed a string for the value, not sure if that is the best practice, need to think on it.

    public String storeValueWithProvenanceAndNames(final LoggedInConnection loggedInConnection, final String valueString, final Set<Name> names) throws Exception {
        String toReturn = "";
        final Set<Name> validNames = new HashSet<Name>();
        final Map<String, String> nameCheckResult = nameService.isAValidNameSet(names, validNames);
        final String error = nameCheckResult.get(NameService.ERROR);
        final String warning = nameCheckResult.get(NameService.ERROR);
        if (error != null) {
            return error;
        } else if (warning != null) {
            toReturn += warning;
        }
        final List<Value> existingValues = findForNames(validNames);
        boolean alreadyInDatabase = false;
        for (Value existingValue : existingValues) { // really should only be one
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
        Value newValue = new Value(loggedInConnection.getTotoMemoryDB(), loggedInConnection.getProvenance(), newValueString, null);
        newValue.setNamesWillBePersisted(existingValue.getNames());
        deleteValue(existingValue);
        return true;
    }

    public boolean storeNewValueFromEdit(final LoggedInConnection loggedInConnection, final Set<Name> names, final String newValueString) throws Exception {
        storeValueWithProvenanceAndNames(loggedInConnection, newValueString, names);
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

    public void printFindForNamesIncludeChildrenStats() {
        System.out.println("calls to  FindForNamesIncludeChildrenStats : " + numberOfTimesCalled1);
        System.out.println("part 1 average nano : " + (part1NanoCallTime1 / numberOfTimesCalled1));
        System.out.println("part 2 average nano : " + (part2NanoCallTime1 / numberOfTimesCalled1));
        System.out.println("part 3 average nano : " + (part3NanoCallTime1 / numberOfTimesCalled1));
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

    public double findValueForNames(final LoggedInConnection loggedInConnection, final Set<Name> names, final MutableBoolean locked, final boolean payAttentionToAdditive, List<Value>valuesFound) {
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<Name>();
        String calcString = "";
        boolean hasCalc = false;
        // add all names to calcnames except the the one with RPCALC
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
              return findSumForNamesIncludeChildren(names, locked, payAttentionToAdditive, valuesFound);
        } else {
            // ok I think I know why an array was used, to easily reference the entry before
            double[] values = new double[20];//should be enough!!
            int valNo = 0;
            StringTokenizer st = new StringTokenizer(calcString, " ");
            while (st.hasMoreTokens()) {
                String term = st.nextToken();
                double calcedVal = 0.0;
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
                    try {
                        // try to parse the value, if it parses then add it to wherever we are in the values array and increment the pointer.
                        calcedVal = Double.parseDouble(term);
                        values[valNo++] = calcedVal;
                    } catch (Exception e) {
                        // we assume it's a name id
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
            return values[0];
        }
    }

      public double findSumForNamesIncludeChildren(final Set<Name> names, final MutableBoolean locked, final boolean payAttentionToAdditive, List<Value> valuesFound) {
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
        if (values.size() > 1) {
            locked.setValue(true);
        }
        if (valuesFound != null){
            valuesFound.addAll(values);
        }

            part2NanoCallTime += (System.nanoTime() - point);
        totalNanoCallTime += (System.nanoTime() - start);
        numberOfTimesCalled++;
        return sumValue;
    }

    public void printSumStats() {
        System.out.println("calls to  findSumForNamesIncludeChildren : " + numberOfTimesCalled);
        System.out.println("part 1 average nano : " + (part1NanoCallTime / numberOfTimesCalled));
        System.out.println("part 2 average nano : " + (part2NanoCallTime / numberOfTimesCalled));
        System.out.println("total average nano : " + (totalNanoCallTime / numberOfTimesCalled));
    }

    public List<Value> findValuesForNameIncludeAllChildren(final Name name, boolean payAttentionToAdditive) {
        List<Value> toReturn = new ArrayList<Value>();
        toReturn.addAll(name.getValues());
        for (Name child : name.findAllChildren(payAttentionToAdditive)) {
            toReturn.addAll(child.getValues());
        }
        return toReturn;
    }

    public String outputHeadings(final List<List<Name>> headings) {
        final StringBuilder sb = new StringBuilder();
        List<Name> lastxNames = null;
        for (int x = 0; x < headings.size(); x++) {
            List<Name> xNames = headings.get(x);
            if (x > 0) sb.append("\n");
            for (int y = 0; y < xNames.size(); y++) {
                if (y > 0) sb.append("\t");
                //don't show repeating names in the headings - leave blank.
                if ((x == 0 || !lastxNames.get(y).equals(xNames.get(y))) && (y == 0 || !xNames.get(y - 1).equals(xNames.get(y)))) {
                    sb.append(xNames.get(y).getDefaultDisplayName());
                }
            }
            lastxNames = xNames;
        }
        return sb.toString();
    }

    public List<Name> interpretItem(String item) {
        //todo  - item should be a string, but potentially include ;children; level x; from a; to b; from n; to n;
        return null;
    }

    /*

    Ok, select a region of names in excel and paste and this function will build a multidimentional array of name objects from that paste

    more specifically : the outermost list is of rows, the second list is each cell in that row and the final list is a list not a name
    because there could be multiple names in a cell if the cell has something like container;children. Interpretnames is what does this

hence

seaports;children   container;children

 for example returns a list of 1 as there's only 1 row passed, with a list of 2 as there's two cells in that row with lists of something like 90 and 3 names in the two cell list items

     */


    public List<List<List<Name>>> createNameListsFromExcelRegion(final LoggedInConnection loggedInConnection, final String excelRegionPasted) throws Exception {
        int maxColCount = 1;
        CsvReader pastedDataReader = new CsvReader(new StringReader(excelRegionPasted), '\t');
        while (pastedDataReader.readRecord()){
            if (pastedDataReader.getColumnCount() > maxColCount){
                maxColCount = pastedDataReader.getColumnCount();
            }
        }
        final List<List<List<Name>>> nameLists = new ArrayList<List<List<Name>>>(); //note that each cell at this point may contain a list (e.g. xxx;elements)
        pastedDataReader = new CsvReader(new StringReader(excelRegionPasted), '\t'); // reset the CSV reader
        while (pastedDataReader.readRecord()) {
            List<List<Name>> row = new ArrayList<List<Name>>();
            for (int column = 0; column < pastedDataReader.getColumnCount(); column++){
                String cellString = pastedDataReader.get(column);
                row.add(nameService.interpretName(loggedInConnection, cellString));
            }
            while (row.size() < maxColCount) row.add(null);
            nameLists.add(row);
        }
        return nameLists;
    }

    private boolean blankCol(List<List<List<Name>>> headingLists, int i) {
        int N = headingLists.size();
        if (N == 1) return false;
        for (int j = 0; j < N - 1; j++) {
            if (headingLists.get(j).get(i) != null) return false;

        }
        return true;
    }

    // Called by expand headings, need to get my head round this
    // TODO edd understand

    private List<List<Name>> permuteRowList(final List<List<Name>> collist) {

        //this will return only up to three levels.  I tried a recursive routine, but the arrays, though created correctly (see below) did not return correctly
        List<List<Name>> output = new ArrayList<List<Name>>();
        int n = collist.size();
        for (Name name : collist.get(0)) {
            if (n == 1) {
                List<Name> nameList = new ArrayList<Name>();
                nameList.add(name);
                output.add(nameList);
            } else {
                for (Name name2 : collist.get(1)) {
                    if (n == 2) {
                        List<Name> nameList = new ArrayList<Name>();
                        nameList.add(name);
                        nameList.add(name2);
                        output.add(nameList);
                    } else {
                        for (Name name3 : collist.get(2)) {
                            List<Name> nameList = new ArrayList<Name>();
                            nameList.add(name);
                            nameList.add(name2);
                            nameList.add(name3);
                            output.add(nameList);
                        }
                    }
                }
            }
        }
        return output;
    }

        /*tried a recursive routine here, but became muddled in parameter passing - it created the correct list, then lost it in passing the parameters
        for (Name name:collist.get(cellNo)){
            input.add(name);
            if (++cellNo == collist.size()){
                output.add(input);

            }else{
                output = permuteRow(input, output, collist, cellNo);
            }
            input.remove(input.size()-1);
            cellNo--;
        }
     return output;

}
*/

    // TODO again edd understand


    public List<List<Name>> expandHeadings(final List<List<List<Name>>> headingLists) {
          /*
          e.g.                      null    1,2,3,         null     4,5
                                     a        b     c,d,e   f        g   h
                          this should expand to
                                    null   1  1  1  1  1  2  2  2   2  2  3  3   3   3   3  4  4   5   5
                                     a     b  c  d  e  f  b  c  d   e  f  b  c   d   e   f  g  h   g   h

                                     the rows are permuted as far as the next item on the same line
           */

        //Note that this routine transposes the list while expanding!
        List<List<Name>> output = new ArrayList<List<Name>>();
        final int rowCount = headingLists.size() - 1;
        final int N = headingLists.get(0).size();
        for (int i = 0; i < N; i++) {
            List<List<Name>> col = new ArrayList<List<Name>>();

            for (List<List<Name>> row : headingLists) {
                col.add(row.get(i));
            }
            while (i < N && blankCol(headingLists, i)) {
                col.get(rowCount).addAll(headingLists.get(rowCount).get(++i));
            }
            List<List<Name>> permuted = permuteRowList(col);
            output.addAll(permuted);
        }
        return output;
    }

    /*todo edd try to understand
    notable that these two are the same except one transposes before setting to the logged in connection and one does after
    one is transposing lists the other is not, based on whether the transposing happens before or after expand headings
                  */

    public String getRowHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        List<List<List<Name>>> rowHeadingLists = transpose2DList(createNameListsFromExcelRegion(loggedInConnection, headingsSent));
        loggedInConnection.setRowHeadings(region, expandHeadings(rowHeadingLists));
        return outputHeadings(loggedInConnection.getRowHeadings(region));
    }

    public String getColumnHeadings(final LoggedInConnection loggedInConnection, final String region, final String headingsSent) throws Exception {
        List<List<List<Name>>> columnHeadingLists = createNameListsFromExcelRegion(loggedInConnection, headingsSent);
        loggedInConnection.setColumnHeadings(region, expandHeadings(columnHeadingLists));
        return outputHeadings(transpose2DList(loggedInConnection.getColumnHeadings(region)));
    }

    /*

    OK, having generified the function we should only need one function. The list could be anything, names, list of names, hashmaps whatever
    generics ensure that the return type will match the sent type
    now rather similar to the stacktrace example :)

    Variable names assume first list is of columns and the second is each column. Right then down.
    So the size of the first list is the xsize (number of columns/row width) and the size of the nested list the ysize (number of rows/column height)

    */

    public <T> List<List<T>> transpose2DList(final List<List<T>> source2Dlist) {
        final List<List<T>> flipped = new ArrayList<List<T>>();
        final int oldYMax = source2Dlist.get(0).size(); // size of nested list, as described above
        for (int newX = 0; newX < oldYMax; newX++) {
            List<T> newColumn = new ArrayList<T>(); // make a new column
            for (List<T> oldColumn : source2Dlist) { // and step across each of the old columns
                newColumn.add(oldColumn.get(newX));//so as we're moving down the new column we're moving right across the old ones
                // the transposing is happening as a list which represents a column would typically be accessed by a y value but instead it's being accessed by an x value
            }
            flipped.add(newColumn);
        }
        return flipped;
    }


    public String getExcelDataForNamesSearch(final Set<Name> searchNames) throws Exception {
        final StringBuilder sb = new StringBuilder();
        List<Value> values = findForNamesIncludeChildren(searchNames, false);
        Set<String> headings = new LinkedHashSet<String>();
        // this may not be optimal, can sort later . . .
        int count = 0;
        for (Value value : values) {
            if (count++ == 2000) {
                break;
            }
            for (Name name : value.getNames()) {
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
        for (Value value : values) {
            if (count++ == 2000) {
                break;
            }
            sb.append(value.getText());
            String[] names = new String[headings.size()];
            int i = 0;
            for (String heading : headings) {
                for (Name name : value.getNames()) {
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


    public String getExcelDataForColumnsRowsAndContext(final LoggedInConnection loggedInConnection, final List<Name> contextNames, final String region) throws Exception {
        loggedInConnection.setContext(region, contextNames); // needed for provenance
        long track = System.currentTimeMillis();
        final StringBuilder sb = new StringBuilder();
        final StringBuilder lockMapsb = new StringBuilder();

        List<List<List<Value>>> dataValuesMap = new ArrayList<List<List<Value>>>(loggedInConnection.getRowHeadings(region).size()); // rows, columns, lists of values
        List<List<Set<Name>>> dataNamesMap = new ArrayList<List<Set<Name>>>(loggedInConnection.getRowHeadings(region).size()); // rows, columns, lists of names for each cell

        for (List<Name> rowName : loggedInConnection.getRowHeadings(region)) { // make it like a document
            ArrayList<List<Value>> thisRowValues = new ArrayList<List<Value>>(loggedInConnection.getColumnHeadings(region).size());
            ArrayList<Set<Name>> thisRowNames = new ArrayList<Set<Name>>(loggedInConnection.getColumnHeadings(region).size());
            dataValuesMap.add(thisRowValues);
            dataNamesMap.add(thisRowNames);
            int count = 1;
            for (List<Name> columnName : loggedInConnection.getColumnHeadings(region)) {
                final Set<Name> namesForThisCell = new HashSet<Name>();
                namesForThisCell.addAll(contextNames);
                namesForThisCell.addAll(columnName);
                namesForThisCell.addAll(rowName);

                // edd putting in peer check stuff here, should I not???
                Map<String, String> result = nameService.isAValidNameSet(namesForThisCell, new HashSet<Name>());
                if (result.get(NameService.ERROR) != null) { // not a valid peer set? must say something useful to the user!
                    return result.get(NameService.ERROR);
                }

                List<Value> values = new ArrayList<Value>();
                thisRowValues.add(values);
                thisRowNames.add(namesForThisCell);
                MutableBoolean locked = new MutableBoolean(false); // we can pass a mutable boolean in and have the function set it
                // TODO - peer additive check. If using peers and not additive, don't include children
                sb.append(findValueForNames(loggedInConnection, namesForThisCell, locked, true, values)); // true = pay attention to names additive flag
                if (locked.isTrue()) {
                    lockMapsb.append("LOCKED");
                }
                // if it's 1 then saving is easy, overwrite the old value. If not then since it's valid peer set I guess we add the new value?
                if (count < loggedInConnection.getColumnHeadings(region).size()) {
                    sb.append("\t");
                    lockMapsb.append("\t");
                } else {
                    sb.append("\r");
                    lockMapsb.append("\r");
                }
                count++;
            }
        }
        printSumStats();
        printFindForNamesIncludeChildrenStats();
        System.out.println("time to execute : " + (System.currentTimeMillis() - track));
        loggedInConnection.setLockMap(region, lockMapsb.toString());
        loggedInConnection.setSentDataMap(region, sb.toString());
        loggedInConnection.setDataValueMap(region, dataValuesMap);
        loggedInConnection.setDataNamesMap(region, dataNamesMap);
        return sb.toString();
    }
}