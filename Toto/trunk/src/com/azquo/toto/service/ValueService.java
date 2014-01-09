package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import com.azquo.toto.memorydb.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 16:49
 * Workhorse hammering away at the memory DB.

 */
public final class ValueService {

    public final static String VALUE = "VALUE";

    @Autowired
    private NameService nameService;

    // set the names in delete info and unlink - best I can come up with at the moment
    public void deleteValue(final Value value) throws Exception {
        String names = "";
        for (Name n : value.getNames()){
            names += ", `" + n.getDefaultDisplayName() + "`";
        }
        if (names.length() > 0){
            names = names.substring(2);
        }
        value.setDeletedInfoWillBePersisted(names);
        value.setNamesWillBePersisted(new HashSet<Name>());
    }

    public Value createValue(final LoggedInConnection loggedInConnection, final Provenance provenance, final double doubleValue, final String text) throws Exception {
//        return totoMemoryDB.createValue(provenanceId,doubleValue,text);
        return new Value(loggedInConnection.getTotoMemoryDB(),provenance,doubleValue,text,null);
    }

    public void linkValueToNames(final Value value, final Set<Name> names) throws Exception {
        value.setNamesWillBePersisted(names);
    }

    // this is passed a string for the value, not sure if that is the best practice, need to think on it.

    public String storeValueWithProvenanceAndNames(final LoggedInConnection loggedInConnection, final String valueString, final Provenance provenance, final Set<Name> names) throws Exception {
        String toReturn = "";
        final Set<Name> validNames = new HashSet<Name>();
        final Map<String, String> nameCheckResult = nameService.isAValidNameSet(loggedInConnection, names, validNames);
        final String error = nameCheckResult.get(NameService.ERROR);
        final String warning = nameCheckResult.get(NameService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        final List<Value> existingValues = findForNames(validNames);
        boolean alreadyInDatabase = false;
        for (Value existingValue : existingValues){ // really should only be one
            if (existingValue.getText().equals(valueString)){
                toReturn += "  that value already exists, skipping";
                alreadyInDatabase = true;
            } else {
                deleteValue(existingValue);
                // provenance table : person, time, method, name
                toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
            }
        }
        if(!alreadyInDatabase){
            Value value = createValue(loggedInConnection, provenance, 0,valueString);
            toReturn += "  stored";
            // and link to names
            linkValueToNames(value, validNames);
        }
        return toReturn;
    }

    public boolean overWriteExistingValue(final LoggedInConnection loggedInConnection, String region, final Value existingValue, final String newValueString) throws Exception {
        StringBuilder rowsString = new StringBuilder();
        for (List<Name> rowNames : loggedInConnection.getRowHeadings(region))
        {
            for (Name name : rowNames){
                rowsString.append(name.getDefaultDisplayName());
                rowsString.append(",");
            }
        }
        StringBuilder columnsString = new StringBuilder();
        for (List<Name> colName : loggedInConnection.getColumnHeadings(region))
        {
            for (Name name:colName){
                columnsString.append(name.getDefaultDisplayName());
                columnsString.append(",");
            }
        }

        StringBuilder contextString = new StringBuilder();
        for (Name name : loggedInConnection.getContext(region))
        {
            contextString.append(name.getDefaultDisplayName());
            contextString.append(",");
        }

        Provenance provenance = new Provenance(loggedInConnection.getTotoMemoryDB(), loggedInConnection.getUser().getName(), new java.util.Date(),"edit data", "excel spraedsheet name here??",rowsString.toString(), columnsString.toString(), contextString.toString());
        Value newValue = new Value(loggedInConnection.getTotoMemoryDB(), provenance, 0, newValueString, null);
        newValue.setNamesWillBePersisted(existingValue.getNames());
        deleteValue(existingValue);
        return true;
    }

    public boolean storeNewValueFromEdit(final LoggedInConnection loggedInConnection, String region, final Set<Name>names, final String newValueString) throws Exception {
        StringBuilder rowsString = new StringBuilder();
        for (List<Name> rowNames : loggedInConnection.getRowHeadings(region))
        {
            for (Name name:rowNames){
                rowsString.append(name.getDefaultDisplayName());
                rowsString.append(",");
            }
        }
        StringBuilder columnsString = new StringBuilder();
        for (List<Name> colNames : loggedInConnection.getColumnHeadings(region))
        {
            for (Name name:colNames){
                columnsString.append(name.getDefaultDisplayName());
                columnsString.append(",");
            }
        }

        StringBuilder contextString = new StringBuilder();
        for (Name name : loggedInConnection.getContext(region))
        {
            contextString.append(name.getDefaultDisplayName());
            contextString.append(",");
        }

        Provenance provenance = new Provenance(loggedInConnection.getTotoMemoryDB(), loggedInConnection.getUser().getName(), new java.util.Date(),"edit data", "excel spreadsheet name here??",rowsString.toString(), columnsString.toString(), contextString.toString());
        storeValueWithProvenanceAndNames(loggedInConnection,newValueString,provenance, names);
        return true;
    }

    public List<Value> findForNames(final Set<Name> names){
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        //long track = System.nanoTime();
        final List<Value> values = new ArrayList<Value>();
        // first get the shortest value list
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names){
            if (smallestNameSetSize == -1 || name.getValues().size() < smallestNameSetSize){
                smallestNameSetSize = name.getValues().size();
                smallestName = name;
            }
        }

        //System.out.println("track a   : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();
        // changing to sets for speed (hopefully!)
        //int count = 0;


        assert smallestName != null; // make intellij happy :P
        for (Value value : smallestName.getValues()){
            boolean theValueIsOk = true;
            for (Name name : names){
                if (!name.equals(smallestName)){ // ignore the one we started with
                    if (!value.getNames().contains(name)){
//                        count++;
                        theValueIsOk = false;
                        break; // important, stop checking that that value contains he names we're interested in as, we didn't find one no point checking for the rest
                    }
                }
            }
            if (theValueIsOk){ // it was in all the names :)
                values.add(value);
            }
        }

        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    // while the above is what would be used to check if data exists for a specific label combination (e.g. when inserting data) this will navigate down through the labels
    // I'm going to try for similar logic but using the lists of children for each label rather than just the label if that makes sense
    // I wonder if it should be a list or set returned?

    // this is slow relatively speaking


    long part1NanoCallTime1 = 0;
    long part2NanoCallTime1 = 0;
    long part3NanoCallTime1 = 0;
    int numberOfTimesCalled1 = 0;


    public List<Value> findForNamesIncludeChildren(final Set<Name> names, boolean payAttentionToAdditive){
        long start = System.nanoTime();

        final List<Value> values = new ArrayList<Value>();
        // first get the shortest value list taking into account children
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names){
            int setSizeIncludingChildren = name.getValues().size();
            for (Name child : name.findAllChildren(payAttentionToAdditive)){
                setSizeIncludingChildren += child.getValues().size();
            }
            if (smallestNameSetSize == -1 || setSizeIncludingChildren < smallestNameSetSize){
                smallestNameSetSize = setSizeIncludingChildren;
                smallestName = name;
            }
        }

        part1NanoCallTime1 += (System.nanoTime() - start);
        long point =System.nanoTime();
        assert smallestName != null; // make intellij happy :P
        final List<Value> valueList = findValuesForNameIncludeAllChildren(smallestName, payAttentionToAdditive);
        part2NanoCallTime1 += (System.nanoTime() - point);
        point =System.nanoTime();
        for (Value value : valueList){
            boolean theValueIsOk = true;
            for (Name name : names){
                if (!name.equals(smallestName)){ // ignore the one we started with
                    if (!value.getNames().contains(name)){ // top name not in there check children also
                        boolean foundInChildList = false;
                        for (Name child : name.findAllChildren(payAttentionToAdditive)){
                            if (value.getNames().contains(child)){
                                foundInChildList = true;
                                break;
                            }
                        }
                        if (!foundInChildList){
//                        count++;
                            theValueIsOk = false;
                            break;
                        }
                    }
                }
            }
            if (theValueIsOk){ // it was in all the names :)
                values.add(value);
            }
        }
        part3NanoCallTime1 += (System.nanoTime() - point);
        numberOfTimesCalled1++;
        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    public void printFindForNamesIncludeChildrenStats(){
        System.out.println("calls to  FindForNamesIncludeChildrenStats : " + numberOfTimesCalled1);
        System.out.println("part 1 average nano : " + (part1NanoCallTime1/numberOfTimesCalled1));
        System.out.println("part 2 average nano : " + (part2NanoCallTime1/numberOfTimesCalled1));
        System.out.println("part 3 average nano : " + (part3NanoCallTime1/numberOfTimesCalled1));
    }


    long totalNanoCallTime = 0;
    long part1NanoCallTime = 0;
    long part2NanoCallTime = 0;
    int numberOfTimesCalled = 0;


    public double findValueForNames(LoggedInConnection loggedInConnection, Set<Name> names, boolean[] locklist, boolean payAttentionToAdditive){
        //there are faster methods of discovering whether a calculation applies - maybe have a set of calced names for reference.
        List<Name> calcnames = new ArrayList<Name>();
        String calcString = "";
        boolean hasCalc = false;
        for (Name name:names){
            if (!hasCalc){
                calcString = name.getAttribute("RPCALC");
                if (calcString != null){
                    hasCalc = true;
                }else{
                    calcnames.add(name);
                }
            }else{
                calcnames.add(name);
            }
        }
        if (!hasCalc){
            return findSumForNamesIncludeChildren(names, locklist, payAttentionToAdditive);

        }else{
            double[] values = new double[20];//should be enough!!
            int valNo = 0;
            int pos = 0;
            while (pos < calcString.length()){
                int spacepos = (calcString + " ").indexOf(" ", pos);
                String term = calcString.substring(pos, spacepos);
                pos = spacepos + 1;
                double calcedVal = 0.0;
                if (term.length()== 1){ // operation
                    valNo--;
                    char charTerm = term.charAt(0);
                    if (charTerm == '+'){
                        values[valNo-1] += values[valNo];
                    }else{
                        if (charTerm=='-'){
                            values[valNo-1] -= values[valNo];
                        }else{
                            if (charTerm=='*'){
                                values[valNo-1] *= values[valNo];
                            }else{
                                 if (values[valNo] == 0){
                                     values[valNo-1] = 0;
                                 }else{
                                     values[valNo-1] /= values[valNo];
                                 }
                            }
                        }
                    }
                 }else{
                    try{
                        calcedVal = Double.parseDouble(term);
                        values[valNo++]= calcedVal;
                    }catch(Exception e){
                      int id = Integer.parseInt(term.substring(1));
                       Name name = nameService.findById(loggedInConnection,id);
                       calcnames.add(name);
                       //note - recursion in case of more than one formula, but the order of the formulae is undefined if the formulae are in different peer groups
                       values[valNo++] =findSumForNamesIncludeChildren(new HashSet<Name>(calcnames), locklist, payAttentionToAdditive);
                       calcnames.remove(calcnames.size()-1);
                    }

                }
                pos = spacepos + 1;
            }
            return values[0];
        }
    }

    public double findSumForNamesIncludeChildren(Set<Name> names, boolean[]locklist, boolean payAttentionToAdditive){
        //System.out.println("findSumForNamesIncludeChildren");
        long start = System.nanoTime();

        List<Value> values = findForNamesIncludeChildren(names, payAttentionToAdditive);
        part1NanoCallTime += (System.nanoTime() - start);
        long point = System.nanoTime();
        double sumValue = 0;
        for (Value value : values){
            if (value.getText() != null && value.getText().length() > 0){
                try{
/*                    if (names.contains(nameService.findByName(loggedInConnection, "www.bakerross.co.uk"))){
                        System.out.print("adding " + value.getText());
                        for (Name n : value.getNames()){
                            System.out.print(" " + n.getName());
                        }
                        System.out.println();
                    }                        */
                    sumValue += Double.parseDouble(value.getText());
                } catch (Exception ignored){
                }
            } else {
                sumValue += value.getDoubleValue();
            }
        }
        if (values.size()> 1){
           locklist[0] = true;
        }
        part2NanoCallTime += (System.nanoTime() - point);
        totalNanoCallTime += (System.nanoTime() - start);
        numberOfTimesCalled++;
        return sumValue;
    }

    public void printSumStats(){
        System.out.println("calls to  findSumForNamesIncludeChildren : " + numberOfTimesCalled);
        System.out.println("part 1 average nano : " + (part1NanoCallTime/numberOfTimesCalled));
        System.out.println("part 2 average nano : " + (part2NanoCallTime/numberOfTimesCalled));
        System.out.println("total average nano : " + (totalNanoCallTime/numberOfTimesCalled));
    }

    public List<Value> findValuesForNameIncludeAllChildren(Name name, boolean payAttentionToAdditive){
        List<Value> toReturn = new ArrayList<Value>();
        toReturn.addAll(name.getValues());
        for (Name child : name.findAllChildren(payAttentionToAdditive)){
            toReturn.addAll(child.getValues());
        }
        return toReturn;
    }


    public List<List<List<Name>>> transposeHeadingLists(List<List<List<Name>>> headingLists) {
        List<List<List<Name>>> flipped = new ArrayList<List<List<Name>>>();
        final int N = headingLists.get(0).size();
        for (int i = 0; i < N; i++) {
            List<List<Name>> col = new ArrayList<List<Name>>();
            for (List<List<Name>> row : headingLists) {
                col.add(row.get(i));
            }
            flipped.add(col);
        }
        return flipped;
    }

    public List<List<Name>> transposeHeadings(List<List<Name>> headings) {
        List<List<Name>> flipped = new ArrayList<List<Name>>();
        final int N = headings.get(0).size();
        for (int i = 0; i < N; i++) {
            List<Name> col = new ArrayList<Name>();
            for (List<Name> row : headings) {
                col.add(row.get(i));
            }
            flipped.add(col);
        }
        return flipped;
    }

    public String outputHeadings(List<List<Name>> headings) {
        final StringBuilder sb = new StringBuilder();
        List<Name> lastxNames = null;
        for (int x = 0; x < headings.size();x++) {
            List<Name> xNames = headings.get(x);
            if (x > 0) sb.append("\n");
            for (int y = 0; y < xNames.size(); y++) {
                if (y > 0) sb.append("\t");
                //don't show repeating names in the headings - leave blank.
                if ((x==0 || !lastxNames.get(y).equals(xNames.get(y))) && (y==0 || !xNames.get(y-1).equals(xNames.get(y)))){
                   sb.append(xNames.get(y).getDefaultDisplayName());
                }
            }
            lastxNames = xNames;
        }
        return sb.toString();
    }

    public List<Name> interpretItem(String item){
     //todo  - item should be a string, but potentially include ;children; level x; from a; to b; from n; to n;
        return null;
    }

    public List<List<List<Name>>> interpretHeadings(LoggedInConnection loggedInConnection, String headingsSent) throws Exception{

        int maxx = 1;
        int y = 0;
        int pos = 0;
        int lineend = (headingsSent + "\n").indexOf("\n", pos);
        while (lineend > 0){
            int x = 1;
            int tabPos = headingsSent.indexOf("\t", pos);
            while (tabPos > 0 && tabPos < lineend){
                if (++x > maxx) maxx = x;
                pos = tabPos + 1;
                tabPos = headingsSent.indexOf("\t", pos);
            }
            y++;
            pos = lineend + 1;
            lineend = (headingsSent + "\n").indexOf("\n", pos);
        }
        pos = 0;
        List<List<List<Name>>> headingNames = new ArrayList<List<List<Name>>>(); //note that each cell at this point may contain a list (e.g. xxx;elements)
        lineend =  (headingsSent + "\n").indexOf("\n", pos);
        while (lineend > 0){
            List<List<Name>> lineNames = new ArrayList<List<Name>>();
            int x = 0;
            int tabPos = headingsSent.indexOf("\t", pos);
            while (tabPos > 0 && tabPos < lineend){
               x++;
               String item = headingsSent.substring(pos, tabPos);
               lineNames.add(nameService.interpretName(loggedInConnection,item));
               pos = tabPos + 1;
               tabPos = headingsSent.indexOf("\t", pos);
            }
            String item = headingsSent.substring(pos, lineend);
            lineNames.add(nameService.interpretName(loggedInConnection,item));

            while (++x < maxx) lineNames.add(null);
            y++;
            headingNames.add(lineNames);
            pos = lineend + 1;
            lineend = (headingsSent + "\n").indexOf("\n", pos);
        }
        return headingNames;
    }

    private boolean blankCol(List<List<List<Name>>> headingLists, int i){
        int N = headingLists.size();
        if (N==1) return false;
        for (int j= 0; j < N-1; j++){
            if (headingLists.get(j).get(i) != null) return false;

        }
        return true;
    }

    private List<List<Name>> permuteRowList(List<List<Name>> collist){

        //this will return only up to three levels.  I tried a recursive routine, but the arrays, though created correctly (see below) did not return correctly
        List<List<Name>> output = new ArrayList<List<Name>>();
        int n = collist.size();
        for (Name name:collist.get(0)){
             if (n==1){
                 List<Name> nameList = new ArrayList<Name>();
                 nameList.add(name);
                 output.add(nameList);
             }else{
                 for (Name name2 : collist.get(1)){
                     if (n==2){
                         List<Name> nameList = new ArrayList<Name>();
                         nameList.add(name);
                         nameList.add(name2);
                         output.add(nameList);
                     }else{
                         for (Name name3 : collist.get(2)){
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


    public List<List<Name>> expandHeadings(List<List<List<Name>>> headingLists){
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
        final int rowCount = headingLists.size()-1;
        final int N = headingLists.get(0).size();
        for (int i = 0; i < N; i++) {
            List<List<Name>> col = new ArrayList<List<Name>>();

            for (List<List<Name>> row : headingLists) {
                col.add(row.get(i));
            }
            while (i < N && blankCol(headingLists, i)){
                col.get(rowCount).addAll(headingLists.get(rowCount).get(++i));
            }
            List<List<Name>> permuted = permuteRowList(col);
            output.addAll(permuted);
        }
        return output;
     }


    public String getRowHeadings(LoggedInConnection loggedInConnection, String region, String headingsSent)throws Exception{
        List<List<List<Name>>> rowHeadingLists = transposeHeadingLists(interpretHeadings(loggedInConnection, headingsSent));
        loggedInConnection.setRowHeadings(region, expandHeadings(rowHeadingLists));
        return outputHeadings(loggedInConnection.getRowHeadings(region));
    }

    public String getColumnHeadings(LoggedInConnection loggedInConnection, String region, String headingsSent)throws Exception{
        List<List<List<Name>>> columnHeadingLists = (interpretHeadings(loggedInConnection,headingsSent));
        loggedInConnection.setColumnHeadings(region, expandHeadings(columnHeadingLists));
        return outputHeadings(transposeHeadings(loggedInConnection.getColumnHeadings(region)));
    }


    public String getExcelDataForNamesSearch(Set<Name> searchNames) throws Exception {
        final StringBuilder sb = new StringBuilder();
        List<Value> values =findForNamesIncludeChildren(searchNames, false);
        Set<String> headings = new LinkedHashSet<String>();
        // this may not be optimal, can sort later . . .
        int count = 0;
        for (Value value : values){
            if (count++ == 2000){
                break;
            }
            for(Name name : value.getNames()){
                if (!headings.contains(name.findTopParent().getDefaultDisplayName())){
                    headings.add(name.findTopParent().getDefaultDisplayName());
                }
            }
        }
        sb.append(" ");
        for (String heading : headings){
            sb.append("\t").append(heading);
        }
        sb.append("\n");
        count = 0;
        for (Value value : values){
            if (count++ == 2000){
                break;
            }
            sb.append(value.getText());
            String[] names = new String[headings.size()];
            int i = 0;
            for(String heading : headings){
                for(Name name : value.getNames()){
                    if (name.findTopParent().getDefaultDisplayName().equals(heading)){
                        names[i] = name.getDefaultDisplayName();
                    }
                }
                i++;
            }
            for (String name : names){
                if  (name!=null){
                    sb.append("\t").append(name);
                }else{
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }


    public String getExcelDataForColumnsRowsAndContext(LoggedInConnection loggedInConnection, List<Name> contextNames, String region) throws Exception {
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
                Map<String, String> result = nameService.isAValidNameSet(loggedInConnection, namesForThisCell, new HashSet<Name>());
                if (result.get(NameService.ERROR) != null) { // not a valid peer set? must say something useful to the user!
                    return result.get(NameService.ERROR);
                }

                List<Value> values = new ArrayList<Value>();
                thisRowValues.add(values);
                thisRowNames.add(namesForThisCell);
                boolean[] locklist = new boolean[1]; // needs an array so that the function can set it
                locklist[0] = false;
                // TODO - peer additive check. If using peers and not additive, don't include children
                 sb.append(findValueForNames(loggedInConnection, namesForThisCell, locklist, true)); // true = pay attention to names additive flag
                if (locklist[0]) {
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