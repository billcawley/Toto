package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.spreadsheet.AzquoCellResolver;
import com.azquo.spreadsheet.DSSpreadsheetService;
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

    private static final String OPS = "+-*/";
    private static final String INDEPENDENTOF = "INDEPENDENT OF";
    private static final String USELEVEL = "USE LEVEL";
    private static final String DEPENDENTON = "DEPENDENT ON";

    // Factored off to implement "lowest" calculation criteria (resolve for each permutation and sum) without duplicated code
    // fair few params, wonder if there's a way to avoid that?
    static double resolveCalc(AzquoMemoryDBConnection azquoMemoryDBConnection, String calcString, List<Name> formulaNames, List<Name> calcnames,
                              MutableBoolean locked, AzquoCellResolver.ValuesHook valuesHook, List<String> attributeNames
            , DataRegionHeading.FUNCTION function, Map<List<Name>, Set<Value>> nameComboValueCache, StringBuilder debugInfo) throws Exception {
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
        while (st.hasMoreTokens()) {
            String term = st.nextToken();
            if (OPS.contains(term)) { // operation
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
                                if (useLevelName.findAllChildren().contains(currentName)) {
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
                    double value = ValueService.findValueForNames(azquoMemoryDBConnection, seekList, locked, valuesHook, attributeNames, function, nameComboValueCache, null);
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
            , AzquoCellResolver.ValuesHook valuesHook, DataRegionHeading.FUNCTION function, MutableBoolean locked) {
        resolveValuesForNamesIncludeChildrenCount.incrementAndGet();
        //System.out.println("resolveValuesForNamesIncludeChildren");
        long start = System.nanoTime();

        double max = 0;
        double min = 0;
        double sumValue = 0;
        boolean first = true;
        for (Value value : values) {
            if (value.getText() != null && value.getText().length() > 0) {
                try {
                    // whole lotta parsing here, could be vaoided if we stored double values. Something to consider.
                    double doubleValue = Double.parseDouble(value.getText());
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
                    first = false;
                } catch (Exception ignored) {
                }
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
            return max;
        }
        if (function == DataRegionHeading.FUNCTION.MIN) {
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
}