package com.azquo.memorydb.service;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.spreadsheet.AzquoCellResolver;
import com.azquo.spreadsheet.DataRegionHeading;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by edward on 14/10/16.
 * <p>
 * Functions that were in ValueService relating specifically to calculations or simple mathematical functions against values now go in here
 * Some code has been optimised according to rigorous testing.
 */
public class ValueCalculationService {

    private static final Logger logger = Logger.getLogger(ValueCalculationService.class);

    private static final String OPS = "&|<=>+-*/" + StringLiterals.MATHFUNCTION + StringLiterals.GREATEROREQUAL + StringLiterals.LESSOREQUAL;
    private static final String INDEPENDENTOF = "INDEPENDENT OF";
    private static final String USELEVEL = "USE LEVEL";
    private static final String DEPENDENTON = "DEPENDENT ON";

    private static final int EXPFUNCTION = 1;

    // Factored off to implement "lowest" calculation criteria (resolve for each permutation and sum) without duplicated code
    // fair few params, wonder if there's a way to avoid that?
    static double resolveCalc(AzquoMemoryDBConnection azquoMemoryDBConnection, String calcString, List<Name> formulaNames, List<Name> calcnames, List<String> calcs,
                              MutableBoolean locked, AzquoCellResolver.ValuesHook valuesHook,  AzquoCellResolver.ValuesHook scaleValuesHook, Set<Name>scaleHeadingNames, List<String> attributeNames
            , DataRegionHeading.FUNCTION function, Name exactName, Map<List<Name>, Set<Value>> nameComboValueCache, StringBuilder debugInfo) throws Exception {
        if (debugInfo != null) {
            debugInfo.append("\t");
            boolean first = true;
            for (Name n : calcnames) {
                if (first) {
                    debugInfo.append(n.getDefaultDisplayName());
                    first = false;
                } else {
                    debugInfo.append("\t").append(n.getDefaultDisplayName());
                }
            }
            debugInfo.append("\t\t");
        }
        double[] values = new double[20];//should be enough!!
        int valNo = 0;
        StringTokenizer st = new StringTokenizer(calcString, " ");
        // exposure * PD * LGD - exp(ECL)
        while (st.hasMoreTokens()) {
            String term = st.nextToken();
            if (OPS.contains(term)) { // operation
                valNo--;
                    /* EFC note : VERY IMPORTANT
                    *
                    * There's some nasty concurrency bug which, having investigated it, I think it something to do with the VM
                    * It would manifest in an array out of bounds exception
                    *
                    * here is the test code beneath "case *"
                    *
                    *                             try {
                                values[valNo - 1] *= values[valNo];
                            } catch (Exception e) {
                                tracker.append("--------- a very specific error!! val no = " + valNo);
                                tracker.append(" values size  " + values.length);
                                tracker.append("val no = " + valNo);
                                tracker.append(" values[0] ");
                                tracker.append(values[0]);
                                tracker.append(" values[1] ");
                                tracker.append(values[1]);
                                if (valNo >=0 && valNo < values.length){
                                    tracker.append(" values[valno] ");
                                    tracker.append(values[valNo]);
                                    tracker.append(" values[valNo - 1] " + values[valNo - 1]);
                                } else {
                                    tracker.append("valno out of bounds???");
                                }
                                throw e;
                            }

                    *
                    * which would show a clean nonsensical index out of bounds - val no being "legal" and still throwing the exception even though values and valno
                    * are local variables. Further to that the error won't be caught by the debugger, it will be there repeating on screen and in the logs,
                    * you set the break point and poof! it disappears. It might be worth checking if Java 9 has this problem . . .
                    *
                    * notably just having these lines fixes the problem, the Exception isn't hit
                    *
                    * tl;dr : DELETE THE FOLLOWING SIX LINES OF CODE AT YOUR PERIL
                    */
                try {
                    double value = values[valNo];
                } catch (Exception e) {
                    System.out.println("correcting nasty concurrency error!");
                    valNo = new Integer(valNo);
                }
                char charTerm = term.charAt(0);
                switch (charTerm) {
                    case StringLiterals.MATHFUNCTION:
                        if (values[valNo] == EXPFUNCTION) {
                            values[valNo - 1] = Math.exp(values[valNo - 1]);
                        }
                        break;
                    case '+':
                        values[valNo - 1] += values[valNo];
                        break;
                    case '|':
                    case '-':
                        values[valNo - 1] -= values[valNo];
                        break;
                    case '&':
                    case '*':
                        values[valNo - 1] *= values[valNo];
                        break;
                    case '/':
                        if (values[valNo] > 0) {
                            values[valNo - 1] /= values[valNo];
                        } else {
                            values[valNo - 1] = 0;
                        }
                        break;
                    case '>':
                        values[valNo - 1] = values[valNo - 1] > values[valNo] ? 1 : 0;
                        break;
                    case '<':
                        values[valNo - 1] = values[valNo - 1] < values[valNo] ? 1 : 0;
                        break;
                    case '=':
                        values[valNo - 1] = values[valNo - 1] == values[valNo] ? 1 : 0;
                        break;
                    case StringLiterals.GREATEROREQUAL:
                        values[valNo - 1] = values[valNo - 1] >= values[valNo] ? 1 : 0;
                        break;
                    case StringLiterals.LESSOREQUAL:
                        values[valNo - 1] = values[valNo - 1] <= values[valNo] ? 1 : 0;
                        break;
                }
            } else { // a value, not in the Azquo sense, a number or reference to a name
                if (StringLiterals.EXP.equalsIgnoreCase(term)) { // factor off to something more general later
                    values[valNo++] = EXPFUNCTION;
                } else if (NumberUtils.isNumber(term)) {
                    values[valNo++] = Double.parseDouble(term);
                } else {
                    // we assume it's a name id starting with NAMEMARKER
                    //int id = Integer.parseInt(term.substring(1));
                    // NOTE! As it stands a term can have one of these attributes, it won't use more than one
                    // so get the name and add it to the other names
                    Name name = NameQueryParser.getNameFromListAndMarker(term, formulaNames);
                    if (debugInfo != null) {
                        debugInfo.append(name.getDefaultDisplayName()).append(" ");
                    }
                    List<Name> seekList = new ArrayList<>(calcnames); // copy before modifying - may be scope for saving this later but in the mean tiem it was causing a problem
                    seekList.add(name);
                    boolean changed = false;
                    if (name.getAttribute(INDEPENDENTOF) != null) {// then this name formula term is saying it wants to exclude some names
                        if (debugInfo != null) {
                            debugInfo.append("Independent of ").append(name.getAttribute(INDEPENDENTOF)).append(" ");
                        }
                        Name independentOfSet = NameService.findByName(azquoMemoryDBConnection, name.getAttribute(INDEPENDENTOF));
                        Iterator<Name> seekListIterator = seekList.iterator();
                        while (seekListIterator.hasNext()) {
                            final Name test = seekListIterator.next();
                            if (!test.equals(name)
                                    && (independentOfSet.equals(test) || independentOfSet.findAllChildren().contains(test))) {
                                seekListIterator.remove();
                                changed = true;
                            }
                        }
                    }
                    // opposite of above I think - chance to factor?
                    if (name.getAttribute(DEPENDENTON) != null) {// then this name formula term is saying it wants to exclude some names
                        if (debugInfo != null) {
                            debugInfo.append("Dependent on ").append(name.getAttribute(DEPENDENTON)).append(" ");
                        }
                        Name dependentOnSet = NameService.findByName(azquoMemoryDBConnection, name.getAttribute(DEPENDENTON));
                        Iterator<Name> seekListIterator = seekList.iterator();
                        while (seekListIterator.hasNext()) {
                            final Name test = seekListIterator.next();
                            if (!test.equals(name)
                                    && !(dependentOnSet.equals(test) || dependentOnSet.findAllChildren().contains(test))) {  // as above but the the way around, if it's not the "dependent on" remove it from the list
                                seekListIterator.remove();
                                changed = true;
                            }
                        }
                        // important point - dependent on will force exact
                        function = DataRegionHeading.FUNCTION.ALLEXACT;
                    }
                    if (name.getAttribute(USELEVEL) != null) {// will only be used in context of lowest level (as in calc on lowest level then sum)
                        // what we're saying is check through the calc names at this lowest level and bump any up to the set specified by "USE LEVEL" if possible
                        if (debugInfo != null) {
                            debugInfo.append("Use level ").append(name.getAttribute(USELEVEL)).append(" ");
                        }
                        List<Name> newSeekList = new ArrayList<>(seekList.size()); // new one of same capacity, we'll be copying in changing as we go
                        final Collection<Name> useLevelNames = NameQueryParser.parseQuery(azquoMemoryDBConnection, name.getAttribute(USELEVEL));
                        for (Name currentName : seekList) { // so for each of the names I need to see if they are in any of them are in the children of the use level names and if so switch to that use level name
                            boolean found = false;
                            for (Name useLevelName : useLevelNames) {
                                if (useLevelName.equals(currentName) || useLevelName.findAllChildren().contains(currentName)) {
                                    found = true;
                                    newSeekList.add(useLevelName);
                                    changed = true;
                                    break;
                                }
                            }
                            if (!found) {
                                newSeekList.add(currentName); // leave it as it was
                            }
                        }
                        seekList = newSeekList;
                    }
                    if (debugInfo != null && changed) {
                        debugInfo.append(" - set = ");
                        boolean first = true;
                        for (Name n : seekList) {
                            if (first) {
                                debugInfo.append(n.getDefaultDisplayName());
                                first = false;
                            } else {
                                debugInfo.append(", ").append(n.getDefaultDisplayName());
                            }
                        }
                    }
                    //note - would there be recursion? Resolve order of formulae might be unreliable
                    double value = ValueService.findValueForNames(azquoMemoryDBConnection, seekList, calcs, locked, valuesHook, scaleValuesHook, scaleHeadingNames, attributeNames, function, exactName, nameComboValueCache, null);
                    if (debugInfo != null) {
                        debugInfo.append("\t").append(value).append("\t");
                    }
                    values[valNo++] = value;
                }
            }
        }
        if (debugInfo != null) {
            debugInfo.append("\n");
        }
        return values[0];
    }

    private static AtomicLong resolveValuesNanoCallTime = new AtomicLong(0);
    private static AtomicLong resolveValuesNumberOfTimesCalled = new AtomicLong(0);
    // on a standard non-calc cell this will give the result. It can do sum, max min average etc
    // heavily used function - a fair bit of testing has gone on to increase speed and reduce garbage collection, please be careful before changing

    private static AtomicInteger resolveValuesForNamesIncludeChildrenCount = new AtomicInteger(0);

    static double resolveValues(final List<Value> values
            , AzquoCellResolver.ValuesHook valuesHook, AzquoCellResolver.ValuesHook scaleValuesHook, Set<Name>scaleHeadingNames, DataRegionHeading.FUNCTION function, MutableBoolean locked) {
        resolveValuesForNamesIncludeChildrenCount.incrementAndGet();
        //System.out.println("resolveValuesForNamesIncludeChildren");
        long start = System.nanoTime();

        double max = 0;
        double min = 0;
        Value maxVal = null;
        Value minVal = null;
        double sumValue = 0;
        // there might be numbers at the beginning then strings later, hence scan the lot first to decide if we're dealing with strings or numbers
        boolean stringMode = false;
        for (Value value : values) {
            if (value.getText() != null && !value.getText().equals("NULL") && value.getText().length() > 0) {
                if (!NumberUtils.isNumber(value.getText())) {
                    stringMode = true;
                    break;
                }
            }
        }
        boolean first = true;
        for (Value value : values) {
            if (value.getText() != null && !value.getText().equals("NULL") && value.getText().length() > 0) {
                if (!stringMode) {
                    double doubleValue;
                    if (scaleValuesHook!=null ){
                        double scale = scaleValue(value, valuesHook, scaleValuesHook, scaleHeadingNames);
                        doubleValue = scale * Double.parseDouble(value.getText());
                    }else{
                        doubleValue = Double.parseDouble(value.getText());
                    }
                    if (first) {
                        max = doubleValue;
                        min = doubleValue;
                    } else {
                        if (doubleValue < min) {
                            min = doubleValue;
                        }
                        if (doubleValue > max) {
                            max = doubleValue;
                        }
                    }
                    sumValue += doubleValue;
                } else {
                    if (first) {
                        maxVal = value;
                        minVal = value;
                    } else {
                        if (maxVal.getText().compareTo(value.getText()) < 0) {
                            maxVal = value;
                        }
                        if (minVal.getText().compareTo(value.getText()) > 0) {
                            minVal = value;
                        }
                    }
                }
                first = false;
            }
        }
        // ok the hack here is that it was just values. add all but this often involved copying into an empty list which is silly if the list is here and won't be used after,
        // hence most of the time use the ready made list unless there's already one there in which case we're part of calc and will need to add
        // I'm trying to minimise garbage
        if (valuesHook.values != null) {
            valuesHook.values.addAll(values);
        } else {
            valuesHook.values = values;
        }
        resolveValuesNanoCallTime.addAndGet(System.nanoTime() - start);
        resolveValuesNumberOfTimesCalled.incrementAndGet();
        if (values.size() > 1) {
            locked.isTrue = true;
        }
        if (function == DataRegionHeading.FUNCTION.COUNT) {
            return values.size();
        }
        if (function == DataRegionHeading.FUNCTION.AVERAGE) {
            if (values.size() == 0) {
                return 0; // avoid dividing by zero
            }
            return sumValue / values.size();
        }
        if (function == DataRegionHeading.FUNCTION.MAX) {
            if (maxVal != null) {
                values.clear();
                values.add(maxVal);
                return 0;
            }
            return max;
        }
        if (function == DataRegionHeading.FUNCTION.MIN) {
            if (minVal != null) {
                values.clear();
                values.add(minVal);
                return 0;
            }
            return min;
        }
        return sumValue; // default to sum, no function
    }

    public static void printSumStats() {
        if (resolveValuesNumberOfTimesCalled.get() > 0) {
            logger.info("calls to resolveValuesForNamesIncludeChildren : " + resolveValuesNumberOfTimesCalled.get());
            logger.info("total average nano : " + (resolveValuesNanoCallTime.get() / resolveValuesNumberOfTimesCalled.get()));
        }
    }

    private static double scaleValue(Value value, AzquoCellResolver.ValuesHook valuesHook, AzquoCellResolver.ValuesHook scaleValuesHook, Set<Name>scaleHeadingNames) {
        double scaleDouble = 0;
        Set<Name> valueParents = new HashSet<>();
        Set<Name> allValueParents = new HashSet<>();
        for (Name name : value.getNames()) {
            valueParents.addAll(name.getParents());
            valueParents.add(name);
        }
        //throw in the headings as well - these should be found regardless

        valueParents.addAll(scaleHeadingNames);
         int newValuesSize = valuesHook.values.size();

        double scale = findScale(scaleValuesHook, valueParents, valuesHook);
        if (newValuesSize == valuesHook.values.size()) {
            for (Name name : value.getNames()) {
                valueParents.addAll(name.getParents());

                allValueParents.addAll(name.findAllParents());
                valueParents.add(name);
            }
            allValueParents.addAll(scaleHeadingNames);
            scale = findScale(scaleValuesHook, allValueParents, valuesHook);
        }
        return scale;

    }
    private static double findScale(AzquoCellResolver.ValuesHook scaleValuesHook, Set<Name> parents, AzquoCellResolver.ValuesHook valuesHook){

        double scale = 0;
        for (Value scaleValue:scaleValuesHook.values){
            boolean scaleFound = true;
            for(Name scaleName:scaleValue.getNames()){
                if (!parents.contains(scaleName)){
                    scaleFound = false;
                    break;
                }
            }
            if (scaleFound){
                scale += Double.parseDouble(scaleValue.getText());
                valuesHook.values.add(scaleValue);
            }
        }
        return scale;

    }

}