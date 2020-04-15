package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracted from DSImportService on by EFC on 12/09/16.
 * <p>
 * This is the class that actually processes each line in a file that's being imported. Designed to be called from a thread pool.
 * The basic file parsing is single threaded but since this can start while later lines are being read I don't think this is a problem.
 * That is to say on a large file the threads will start to stack up fairly quickly
 * Adapted to Callable from Runnable - I like the assurances this gives for memory synchronisation
 */
public class BatchImporter implements Callable<Void> {

    private final AzquoMemoryDBConnection azquoMemoryDBConnection;
    private final List<LineDataWithLineNumber> dataToLoad;
    private final Map<String, Name> namesFoundCache;
    private final List<String> attributeNames;
    private final Map<Integer, List<String>> linesRejected;
    private final AtomicInteger noLinesRejected;
    private final boolean clearData;
    private final CompositeIndexResolver compositeIndexResolver;
    private static final int CHECKTRUE = 1;
    private static final int CHECKFALSE = 0;
    private static final int CHECKMAYBE = 2;
    private static final String PROVISIONAL = "***";

    BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection
            , List<LineDataWithLineNumber> dataToLoad
            , Map<String, Name> namesFoundCache
            , List<String> attributeNames
            , Map<Integer, List<String>> linesRejected
            , AtomicInteger noLinesRejected
            , boolean clearData
            , CompositeIndexResolver compositeIndexResolver) {
        this.azquoMemoryDBConnection = azquoMemoryDBConnection;
        this.dataToLoad = dataToLoad;
        this.namesFoundCache = namesFoundCache;
        this.attributeNames = attributeNames;
        this.linesRejected = linesRejected;
        this.noLinesRejected = noLinesRejected;
        this.clearData = clearData;
        this.compositeIndexResolver = compositeIndexResolver;
    }

    private static final char CONSTANTMARKER = '!';

    @Override
    public Void call() {
        Long time = System.currentTimeMillis();
        for (LineDataWithLineNumber lineDataWithLineNumber : dataToLoad) {
            List<ImportCellWithHeading> lineToLoad = lineDataWithLineNumber.getLineData();
            int lineNumber = lineDataWithLineNumber.getLineNumber();
            try {
                ImportCellWithHeading first = lineToLoad.get(0);
                /*
                There's a thought that this should be a whole line check rather than the first column.

                Skip any line that has a blank in the first column unless the first column had no heading or it's composite
                happy for the check to remain in here - more stuff for the multi threaded bit
                blank attribute allowed as it's not structural, a blank attribute won't break anything
                */
                if (first.getLineValue().length() > 0 || first.getImmutableImportHeading().heading == null
                        || first.getImmutableImportHeading().compositionPattern != null || first.getImmutableImportHeading().attribute != null) {
                    // new better representation of line rejection logic. It's an exception caught below
                    // note setResolved() is doing the only/existing validation and will exception accordingly
                    try {
                        // first pass over the cells, check ignored and set resolved where we can
                        for (ImportCellWithHeading cell : lineToLoad) {
                            // simple ignore list check
                            if (cell.getImmutableImportHeading().ignoreList != null) {
                                for (String ignoreItem : cell.getImmutableImportHeading().ignoreList) {
                                    if (cell.getLineValue().toLowerCase().contains(ignoreItem)) {
                                        throw new LineRejectionException("ignored");
                                    }
                                }
                            }
                            // override more simple than composition, just set it regardless
                            if (cell.getImmutableImportHeading().override != null) {
                                cell.setLineValue(cell.getImmutableImportHeading().override, azquoMemoryDBConnection, attributeNames);
                                /* We try to resolve if there's a composition pattern or lookup *and* no value in the cell.
                                So no composition and no lookup config and we flag as needs resolving as false OR If there's a value in the cell we don't override it
                                with a composite/lookup value
                                 */
                            } else if (!cell.getLineValue().isEmpty() || (cell.getImmutableImportHeading().lookupParentIndex < 0 && cell.getImmutableImportHeading().compositionPattern == null)) {
                                cell.setLineValueResolved(azquoMemoryDBConnection, attributeNames);
                            }
                        }
                        // composite is interdependent but so are name relations so one function needs to deal with both
                        resolveCellInterdependence(lineToLoad, lineNumber);
                        try {
                            interpretLine(lineToLoad, lineNumber, clearData);
                        } catch (Exception e) {
                            azquoMemoryDBConnection.addToUserLogNoException(e.getMessage(), true);
                            e.printStackTrace();
                            throw e;
                        }
                        Long now = System.currentTimeMillis();
                        if (now - time > 10) { // 10ms a bit arbitrary
                            System.out.println("line no " + lineNumber + " time = " + (now - time) + "ms");
                        }
                        time = now;
                    } catch (LineRejectionException lre) {
                        if (!"ignored".equalsIgnoreCase(lre.getReason())) {
                            noLinesRejected.incrementAndGet();
                            if (linesRejected.size() < 1000) {
                                linesRejected.computeIfAbsent(lineNumber,
                                        t -> new ArrayList<>()).add(lre.getReason());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (linesRejected.size() < 1000) {
                    linesRejected.computeIfAbsent(lineNumber
                            , t -> new ArrayList<>()).add(e.getMessage());
                }
                noLinesRejected.incrementAndGet();
            }
        }
        azquoMemoryDBConnection.addToUserLogNoException("..Batch finishing : " + DecimalFormat.getInstance().format(dataToLoad.size()) + " imported.", true);
        return null;
    }


    // composite and name dependencies between cells
    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values
    // name resolution has necessarily been mixed in, composites may use names for attributes

    private void resolveCellInterdependence(List<ImportCellWithHeading> cells, int importLine) throws Exception {
        boolean adjusted = true;
        int timesLineIsModified = 0;
        int loopLimit = 10;
        // an initial check on value needs resolving will have been done by this point
        // first pass on name resolution, cells with line values resolved may be able to have names resolved also if required
        tryToResolveNames(cells);
        // loops in case there are multiple levels of dependencies.
        // note : a circular reference could cause an infinite loop - hence timesLineIsModified limit
        while (adjusted && timesLineIsModified < loopLimit) {
            adjusted = false;
            for (ImportCellWithHeading cell : cells) {
                if (!cell.lineValueResolved()) {
                    if (cell.getImmutableImportHeading().lookupParentIndex < 0) {
                        String compositionPattern = cell.getImmutableImportHeading().compositionPattern;
                        if (compositionPattern == null) {
                            compositionPattern = cell.getLineValue();
                        }
                        compositionPattern = compositionPattern.replace("LINENO", importLine + "");
                        if (compositionPattern.toLowerCase().startsWith("if(")) {
                            if (resolveIf(cell, compositionPattern, cells)) {
                                adjusted = true;
                            }
                        } else {
                            if (resolveComposition(cell, compositionPattern, cells)) {
                                adjusted = true;
                            }
                        }
                    } else {
                        // lookups are pretty involved but here the notable thing is that they can reoslve both line values and names if its dependencies are met
                        ImportCellWithHeading parentCell = cells.get(cell.getImmutableImportHeading().lookupParentIndex);
                        if (parentCell.getLineNames() != null) {
                            if (checkLookup(azquoMemoryDBConnection, cell, parentCell.getLineNames().iterator().next(), cells, compositeIndexResolver)){
                                adjusted = true;
                            }
                        }
                    }
                }
            }
            if (adjusted) {
                tryToResolveNames(cells);
            }

            timesLineIsModified++;
            if (timesLineIsModified == loopLimit) {
                throw new Exception("Circular references in headings!");
            }
        }
    }

    /* we have to resolve names according to dependencies so that
    A. Work in progress line values are not resolved - this would create junk in the database and
    B.  Where there are dependencies between cells we watch for local parents and so resolve things in the correct order - more details on this below
    Note that currently lookup resolving, which is not in here, can resolve names also and it will take priority as it resolves the value and names
    at the same time so this function can't get there first. It can though deal with the children of lookups after they've been sorted
    */
    private void tryToResolveNames(List<ImportCellWithHeading> cells) throws Exception {
        int timesLineIsModified = 0;
        int loopLimit = 10;
        boolean resolveAgain = true;
        // the logic is similar to the composite one above. Scan the line trying to resolve and if anything changes try again as dependencies may have been resolved
        // when nothing new can be resolved stop
        while (resolveAgain) {
            resolveAgain = false;
            for (ImportCellWithHeading cell : cells) {
                if (cell.lineValueResolved()) { // can only resolve names if line values resolved
                    // first try to sort the line names (if not done already)
                    if (!cell.lineNamesResolved()) {
                        /* dictionary map first, should be simple. The names in the map were resolved in the heading reader
                        The dictionary substitutes free style text with categories from a lookup set.

                        Each lookup (e.g   '123 Auto Accident not relating to speed') is given a lookup phrase (e.g.   car + accident - speed)
                        in which if each positive term is found, and no negative term is found, the phrase is replaced by the category.
                        The terms may consist of lists (' car + accident - speed, fast).
                        There may also be a set of 'synonyms' which consists of similar words (e.g.  car : vehicle, auto, motor) to help the definition

                        Also see note on MutableImportHeading dictionaryMap
                        */
                        if (cell.getImmutableImportHeading().dictionaryMap != null) {
                            if (cell.getLineValue().length() > 0) { // I suppose it could be empty
                                boolean hasResult = false;
                                for (Name category : cell.getImmutableImportHeading().dictionaryMap.keySet()) {
                                    boolean found = true;
                                    List<ImmutableImportHeading.DictionaryTerm> dictionaryTerms = cell.getImmutableImportHeading().dictionaryMap.get(category);
                                    for (ImmutableImportHeading.DictionaryTerm dictionaryTerm : dictionaryTerms) {
                                        found = false; //the phrase now has to pass every one of the tests.  If it does so then the category is found.
                                        for (String item : dictionaryTerm.items) {
                                            if (dictionaryTerm.exclude) {
                                                if (BatchImporter.containsSynonym(cell.getImmutableImportHeading().synonyms, item.toLowerCase().trim(), cell.getLineValue().toLowerCase())) {
                                                    break;
                                                }
                                            } else {
                                                if (BatchImporter.containsSynonym(cell.getImmutableImportHeading().synonyms, item.toLowerCase().trim(), cell.getLineValue().toLowerCase())) {
                                                    found = true;
                                                }
                                            }
                                        }
                                        if (!found) break;
                                    }
                                    if (found) {
                                        cell.addToLineNames(category);
                                        hasResult = true;
                                        break;
                                    }
                                }
                                if (!hasResult) {
                                    if (!cell.getImmutableImportHeading().blankZeroes) {
                                        Name parent = cell.getImmutableImportHeading().parentNames.iterator().next();
                                        cell.addToLineNames(BatchImporter.findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, "Uncategorised " + parent.getDefaultDisplayName(), parent, false, StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST));
                                    }
                                }
                            }
                            cell.setLineNamesResolved(); // deal with its children later
                            resolveAgain = true;
                        } else
        /*
             It would be nice to just resolve the names straight away but there's a priority - we need to take into account the relations between the cells.
             The real issue here is local parents as in cells that are children of other cells local. Under these circumstances the names must be resolved
             by the parent cell first so that they are created  correctly.

             Imagine 3 headings. Town, street, whether it's pedestrianized. Pedestrianized parent of street. Town parent of street local.
             If we resolve "Pedestrianized parent of street" first both pedestrianized (ok) and street (NOT ok!) will have their line names resolved
             whereas resolving "Town parent of street local" first means that the street should be correct by the time we resolve "Pedestrianized parent of street".
             essentially sort local names first.

             Practically this means we don't at this pint resolve any names if they have a local parent, that's done below.
             Note that there can be locals in locals, this code takes that into account as successive passes will resolve down the chain.
              */

                            if (cell.getImmutableImportHeading().localParentIndex == -1) { // no local parents across the cells, means we can do some resolution here
                                // resolution that will take into account the parents as assigned by the heading
                                // but NOT, notably, any parents this cell may have from another cell in the same line
                                if (!cell.getLineValue().isEmpty()) {
                                    // in simple terms if a line cell value refers to a name it can now refer to a set of names
                                    // to make a set parent of more than one thing e.g. parent of set a, set b, set c
                                    // nothing in the heading has changed except the split char but we need to detect it here
                                    // split before checking for quotes etc. IF THE SPLIT CHAR IS IN QUOTES WE DON'T CURRENTLY SUPPORT THAT!
                                    List<String> localLanguages = setLocalLanguage(cell.getImmutableImportHeading().attribute, attributeNames);
                                    String[] split;
                                    if (cell.getImmutableImportHeading().splitChar == null) {
                                        split = new String[]{cell.getLineValue()};
                                    } else {
                                        split = cell.getLineValue().split(cell.getImmutableImportHeading().splitChar);
                                    }
                                    for (String nameToAdd : split) {
                                        if (cell.getImmutableImportHeading().optional) {
                                            //don't create a new name
                                            try {
                                                // worth noting that optional assumes one parent
                                                // addToLineNames will simply ignore null
                                                cell.addToLineNames(NameService.findByName(azquoMemoryDBConnection, cell.getImmutableImportHeading().parentNames.iterator().next().getDefaultDisplayName() + "->" + nameToAdd, localLanguages));
                                            } catch (Exception ignored) {
                                            }
                                        } else {
                                            Set<Name> parents = cell.getImmutableImportHeading().parentNames;
                                            if (parents.size() == 0) { // if null parents then local means nothing here though it will matter to the children (in other cells) of the names we're making now
                                                cell.addToLineNames(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, nameToAdd.trim(), null, false, localLanguages));
                                            } else {
                                                for (Name parent : parents) {
                                                    cell.addToLineNames(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, nameToAdd.trim(), parent, cell.getImmutableImportHeading().isLocal, localLanguages));
                                                }
                                            }
                                        }
                                    }
                                }
                                cell.setLineNamesResolved();
                                resolveAgain = true;
                            }
                    }
                }
            }
            // Note the first for loop closed and another opened. This is to preserve existing logic where all line names are as resolved as possible first before dealing with the parent/child relations between cells
            // if we don't do the two loops then the else "it is already resolved" conditions below might not get hit as much as they should be. Also the sortExclusive check will
            // reference another column which should have line names resolved by now if possible
            for (ImportCellWithHeading cell : cells) {
                // try and resolve any children this cell may have? If their line name is resolved (may not be if local parents)
                // note - lineNamesChildrenResolved is only set to false when the indexForChild is -1, when there could be a requirement to resolve the children
                if (cell.lineNamesResolved() && cell.getLineNames() != null && !cell.lineNamesChildrenResolved()) {// line names could be resolved and null due to optional or unsuccessful dictionary lookup for example
                    ImportCellWithHeading childCell = cells.get(cell.getImmutableImportHeading().indexForChild);
                    if (childCell.lineValueResolved()) { // that child cell has it's line vale resolved
                        if (!childCell.lineNamesResolved()) { // it doesn't have the names resolved yet
                            // keep existing errors
                            if (childCell.getLineValue().isEmpty()) {
                                throw new Exception("blank value for " + childCell.getImmutableImportHeading().heading + " (child of " + cell.getLineValue() + " " + cell.getImmutableImportHeading().heading + ")");
                            }
                            // probably the most simple check - that the child cell has no local parents *or* that this cell is local
                            if (childCell.getImmutableImportHeading().localParentIndex == -1 || cell.getImmutableImportHeading().isLocal) {
                                // can we factor the split code with code above? Not a biggy
                                String[] childNames;
                                if (childCell.getImmutableImportHeading().splitChar == null) {
                                    childNames = new String[]{childCell.getLineValue()};
                                } else {
                                    childNames = childCell.getLineValue().split(childCell.getImmutableImportHeading().splitChar);
                                }
                                // this pays no attention to optional - it only makes sense in context of child of a heading
                                for (String childName : childNames) {
                                    for (Name thisCellsName : cell.getLineNames()) {
                                        childCell.addToLineNames(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childName, thisCellsName
                                                , cell.getImmutableImportHeading().isLocal, setLocalLanguage(childCell.getImmutableImportHeading().attribute, attributeNames)));
                                    }
                                }
                                // sort the child cell's parent headings
                                for (Name parent : childCell.getImmutableImportHeading().parentNames) {
                                    for (Name child : childCell.getLineNames()) {
                                        parent.addChildWillBePersisted(child, azquoMemoryDBConnection);
                                    }
                                }
                                cell.setLineNamesChildrenResolved(); // if possible. So mark as resolved.
                                childCell.setLineNamesResolved();
                                resolveAgain = true; // cell names were set as resolved, go around again
                            }
                        } else {// it is already resolved,  add to the parents of the line names already there
                            if (childCell.getLineNames() != null) { // resolved may still be null, optional for example
                                for (Name parent : cell.getLineNames()) {
                                    for (Name childCellName : childCell.getLineNames()) {
                                        parent.addChildWillBePersisted(childCellName, azquoMemoryDBConnection);
                                    }
                                }
                            }
                            // I do not think this warrants resolving again. Although the action was required nothing will have been dependant on the parents I don't think
                            cell.setLineNamesChildrenResolved(); // if the children were null nothing changed but we tried, mark it as done
                        }
                    }
                    if (cell.lineNamesChildrenResolved() && childCell.getLineNames() != null) { // as mentioned above it could be marked as resolved with null line names
                        // but, if not null, sort exclusives
                        sortExclusive(cell, cells);
                    }
                }
                // end cell loop
            }
            timesLineIsModified++;
            if (timesLineIsModified == loopLimit) {
                throw new Exception("Circular references in headings when resolving names!");
            }
            // end main wile loop
        }
    }

    /*
    Exclusive practically means, if necessary, zapping some parents of the children of this column that are not this column.

    Let us assume cellWithHeading's heading is "Category" (a heading which may have no Name though its cells will)
    which is "child of" "All Categories" (a Name in the database) and "parent of" "Product", another heading. Cells in the "Product" column have Names, childCell.getLineNames().
    We might say that the cell in "Category" is "Shirts" and the cell in "Product" is "White Poplin Shirt". By putting exclusive in the "Category" column we're saying
    : get rid of any parents "White Poplin Shirt" has that are in "All Categories" that are NOT "Shirts". I'll use this examples to comment below.

    If exclusive has a value then whatever it specifies replaces the set defined by "child of" ("All Categories in this example") to remove from so :
    get rid of any parents "White Poplin Shirt" has that are in (or are!) "Name Specified By Exclusive" that are NOT "Shirts"

    Note : "Name Specified By Exclusive" used to be a straight lookup in the "else" below but now it refers to another column's name.
    If you wanted the old functionality you'd need to make a column with a default value to do it
     */
    private void sortExclusive(ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells) throws Exception {
        ImportCellWithHeading childCell = cells.get(cellWithHeading.getImmutableImportHeading().indexForChild);
        // note! Exclusive can't work if this cellWithHeading column is multiple names
        if (cellWithHeading.getLineNames().size() == 1 && cellWithHeading.getImmutableImportHeading().exclusiveIndex != HeadingReader.NOTEXCLUSIVE) {
            //the 'parent' is the current cell name, not its parent, it is the parent that will stay on the child regardless
            Name parent = cellWithHeading.getLineNames().iterator().next();
            // if blank exclusive means check for parents to zap in the name this column is child of
            Name exclusiveName = null;
            if (cellWithHeading.getImmutableImportHeading().exclusiveIndex == HeadingReader.EXCLUSIVETOCHILDOF) {
                // blank exclusive clause, use "child of" clause - currently this only looks at the first name in that list to be exclusive of, more than one makes little sense
                exclusiveName = cellWithHeading.getImmutableImportHeading().parentNames.iterator().next();
            } else { // exclusive has a value, not null or blank, is referring to a name in another cell
                // what if the names have not been resolved yet? They ony could not be if local which I think would NOT be the exclusive set
                Set<Name> exclusiveNameSet = cells.get(cellWithHeading.getImmutableImportHeading().exclusiveIndex).getLineNames();
                if (exclusiveNameSet != null) {
                    exclusiveName = exclusiveNameSet.iterator().next();
                }
            }
            if (exclusiveName != null) {
                /* To follow the example above run through the parents of "White Poplin Shirt".
                Firstly check that the parent is "Shirts", if it is we of course leave it there and note to not re-add (needsAdding = false).
                If the parent is NOT "Shirts" we check to see if it's the exclusive set itself ("White Poplin Shirt" was directly in "All Categories")
                or if the parent is any of the children of the exclusive set in this case maybe "Swimwear". If so zap that parent from the child.

                Given that we now have multiple names on a line we run through the child ones checking as necessary.
                */
                for (Name childCellName : childCell.getLineNames()) {
                    boolean needsAdding = true; // defaults to true
                    for (Name childCellParent : childCellName.getParents()) {
                        if (childCellParent == parent) {
                            needsAdding = false;
                        } else if (childCellParent == exclusiveName || exclusiveName.getChildren().contains(childCellParent)) {
                            childCellParent.removeFromChildrenWillBePersisted(childCellName, azquoMemoryDBConnection);
                        }
                    }
                    // should have cleaned up parents we don't want. Add the parent we do want if it wasn't there already
                    if (needsAdding) {
                        parent.addChildWillBePersisted(childCellName, azquoMemoryDBConnection);
                    }
                }
            }
        }
    }

    public static boolean containsSynonym(Map<String, List<String>> synonymList, String term, String value) {
        if (value.contains(term)) {
            return true;
        }
        if (synonymList != null) {
            List<String> synonyms = synonymList.get(term);
            if (synonyms != null) {
                for (String synonym : synonyms) {
                    if (value.contains(synonym.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // comma as opposed to ? : as that syntax is what is used in Excel
    // IF(C2=”Yes”,1,2) says IF(C2 = Yes, then return a 1, otherwise return a 2) but the terms are Azquo Composites. References to cells.
    // note this is a function that's resolved before otehr composite stuff
    private boolean resolveIf(ImportCellWithHeading cell, String compositionPattern, List<ImportCellWithHeading> cells) throws Exception {
        int commaPos = compositionPattern.indexOf(",");
        if (commaPos < 0)
            return false;
        int secondComma = compositionPattern.indexOf(",", commaPos + 1);
        if (secondComma < 0)
            return false;
        String condition = compositionPattern.substring(3, commaPos).trim();
        String trueTerm = compositionPattern.substring(commaPos + 1, secondComma).trim();
        String falseTerm = compositionPattern.substring(secondComma + 1, compositionPattern.length() - 1);
        String conditionTerm = findEquals(condition);
        if (conditionTerm == null)
            return false;
        int conditionPos = condition.indexOf(conditionTerm);
        String leftTerm = getCompositeValue(cell, condition.substring(0, conditionPos).trim(), cells);
        if (leftTerm == null) return false;
        String rightTerm = getCompositeValue(cell, condition.substring(conditionPos + conditionTerm.length()).trim(), cells);
        if (rightTerm == null) return false;
        // string compare only currently, could probably detect numbers and adjust accordingly
        if ((conditionTerm.contains("=") && leftTerm.equals(rightTerm)) || (conditionTerm.contains("<") && leftTerm.compareTo(rightTerm) < 0) || (conditionTerm.contains(">") && leftTerm.compareTo(rightTerm) > 0)) {
            return resolveComposition(cell, trueTerm, cells);
        }
        return resolveComposition(cell, falseTerm, cells);
    }

    private static String findEquals(String term) {
        if (term.contains(">=")) return ">=";
        if (term.contains("<=")) return "<=";
        if (term.contains("=")) return "=";
        if (term.contains(">")) return ">";
        if (term.contains("<")) return "<";
        return null;
    }

    private boolean resolveComposition(ImportCellWithHeading cell, String compositionPattern, List<ImportCellWithHeading> cells) throws Exception {
        String value = getCompositeValue(cell, compositionPattern, cells);
        if (value == null) {
            return false;
        }
        cell.setLineValue(value, azquoMemoryDBConnection, attributeNames);
        return true;
    }

    // Now supports basic excel like string operations, and at the end can do numeric evaluation if necessary
    // really we're just resolving anything in quotes ` with a special case for attribute
    private String getCompositeValue(ImportCellWithHeading cell, String compositionPattern, List<ImportCellWithHeading> cells) throws Exception {
        int headingMarker = compositionPattern.indexOf("`");
        while (headingMarker >= 0) {
            boolean doubleQuotes = false;
            if (headingMarker < compositionPattern.length() && compositionPattern.charAt(headingMarker + 1) == '`') {
                doubleQuotes = true;
                headingMarker++;
            }
            int headingEnd = compositionPattern.indexOf("`", headingMarker + 1);
            if (headingEnd > 0) {
                // note : this means functions mid left right etc are in the quotes `left (4, col name)`
                String nameAttribute = null;
                String function = null;
                int funcInt = 0;
                int funcInt2 = 0;
                String expression = compositionPattern.substring(headingMarker + 1, headingEnd);
                // can now call an attribute of another cell if it's a name.
                // More powerful and intuitive than the previous defaulting to the default display name which would sometimes replace the cell value
                if (compositionPattern.length() > (headingEnd + 1) && compositionPattern.charAt(headingEnd + 1) == '.' && compositionPattern.charAt(headingEnd + 2) == '`') {
                    int start = headingEnd + 3;
                    headingEnd = compositionPattern.indexOf("`", start);
                    nameAttribute = compositionPattern.substring(start, headingEnd);
                } else { // either parse simple functions or do name attribute, can't do both
                    // fairly standard replace name of column with column value but with string manipulation left right mid
                    // checking for things like right(A Column Name, 5). Mid has two numbers.
                    // note that len(another column name) is supported too so you can reference the length of other cells, stringTerm does this
                    if (expression.contains("(")) {
                        int bracketPos = expression.indexOf("(");
                        function = expression.substring(0, bracketPos);
                        int commaPos = expression.indexOf(",", bracketPos + 1);
                        int secondComma;
                        if (commaPos > 0) {
                            secondComma = expression.indexOf(",", commaPos + 1);
                            String countString;
                            try {
                                if (secondComma < 0) {
                                    countString = expression.substring(commaPos + 1, expression.length() - 1);
                                    funcInt = stringTerm(countString.toLowerCase().trim(), cells);
                                } else {
                                    countString = expression.substring(commaPos + 1, secondComma);
                                    funcInt = stringTerm(countString.toLowerCase().trim(), cells);
                                    countString = expression.substring(secondComma + 1, expression.length() - 1);
                                    funcInt2 = stringTerm(countString.toLowerCase().trim(), cells);
                                }
                            } catch (Exception ignore) {
                            }
                            expression = expression.substring(bracketPos + 1, commaPos);
                            //only testing for default display name at present - need to work out what might happen if name contained '.'
                            int dotPos = expression.toUpperCase().indexOf("." + StringLiterals.DEFAULT_DISPLAY_NAME);
                            if (dotPos > 0) {
                                nameAttribute = StringLiterals.DEFAULT_DISPLAY_NAME;
                                expression = expression.substring(0, dotPos);
                            }
                        }
                    }
                }
                // if there was a function its name and parameters have been extracted and expression should now be a column name
                ImportCellWithHeading compCell;
                expression = expression.trim();
                int colIndex = compositeIndexResolver.getColumnIndexForHeading(expression);
                if (colIndex != -1) {
                    compCell = cells.get(colIndex);
                } else {
                    throw new Exception("Unable to find column : " + expression + " in composition pattern " + cell.getImmutableImportHeading().compositionPattern + " in heading " + cell.getImmutableImportHeading().heading);
                }
                // skip until the referenced cell has been resolved - the loop outside checking for dependencies will send us back here
                if (compCell != null && compCell.lineValueResolved()) {
                    String sourceVal;
                    // we have a name attribute and it is a column with a name, we resolve the name if necessary and get the attribute
                    if (nameAttribute != null && compCell.getImmutableImportHeading().lineNameRequired) {
                        if (compCell.getLineNames() == null) {
                            return null; // this will stop ifs and composite resolving for the moment. If other cells are changed it the function will be called on the cell again
                        }
                        sourceVal = compCell.getLineNames().iterator().next().getAttribute(nameAttribute);
                    } else { // normal
                        sourceVal = compCell.getLineValue();
                    }
                    // Could be null if a duff attribute was specified, whether this should exception is an interesting one
                    if (sourceVal != null) {
                        // we have the value, check if there was a function to do to it
                        // the two ints need to be as they are used in excel
                        if (function != null && (funcInt > 0 || funcInt2 > 0) && sourceVal.length() > funcInt) {
                            if (function.equalsIgnoreCase("left")) {
                                sourceVal = sourceVal.substring(0, funcInt);
                            }
                            if (function.equalsIgnoreCase("right")) {
                                sourceVal = sourceVal.substring(sourceVal.length() - funcInt);
                            }
                            if (function.equalsIgnoreCase("mid")) {
                                //the second parameter of mid is the number of characters, not the end character
                                // if the length goes off the end ignore it
                                if (((funcInt - 1) + funcInt2) >= sourceVal.length()) {
                                    sourceVal = sourceVal.substring(funcInt - 1);
                                } else {
                                    sourceVal = sourceVal.substring(funcInt - 1, (funcInt - 1) + funcInt2);
                                }
                            }
                            if (function.equalsIgnoreCase("standardise")) {
                                sourceVal = sourceVal.replaceAll("[^0-9a-zA-Z]", "").toLowerCase();
                                if (funcInt < sourceVal.length()) {
                                    sourceVal = sourceVal.substring(0, funcInt);
                                }
                            }
                        }
                        // now replace and move the marker to the next possible place
                        compositionPattern = compositionPattern.replace(compositionPattern.substring(headingMarker, headingEnd + 1), sourceVal);
                        headingMarker = headingMarker + sourceVal.length() - 1;//is increased before two lines below
                        if (doubleQuotes) headingMarker++;
                    } else {
                        // as mentioned above returning the nulls doesn't mean the cell is written off. The loop will try again if other cells are
                        // line value was null. Probably due to referencing a null attribute
                        return null;
                    }
                } else { // couldn't find the cell or the required cell is not resolved yet
                    return null;
                }
            }
            // try to find the start of the next column referenced
            headingMarker = compositionPattern.indexOf("`", ++headingMarker);
        }
        // not quite sure where this would have come from but we need to zap it before checking calc
        if (compositionPattern.startsWith("\"") && compositionPattern.endsWith("\"")) {
            compositionPattern = compositionPattern.substring(1, compositionPattern.length() - 1);
        }
        // after all the column/string function/attribute is done see if there's a numeric option
        if (compositionPattern.toLowerCase().startsWith("calc")) {
            // chop calc then percentage hack, x% should be (x/100) or x*0.01 which should work
            compositionPattern = compositionPattern.substring(5).replace("%", "*0.01");
            try {
                Expression e = new ExpressionBuilder(compositionPattern).build();
                // As WFC pointed out one could perhaps precompile the polish notation so it's not resolved on every cell of the column but I'm not bothered at the moment
                compositionPattern = roundoff(e.evaluate()); // roundoff probably still required
            } catch (Exception ignored) { // following the previous convention we'll just fail silently
            }
        }
        return compositionPattern;
    }

    // enables referencing the length of other column, useful for left mid right functions above
    private int stringTerm(String string, List<ImportCellWithHeading> cells) {
        int lengthPos = string.indexOf("len(");
        while (lengthPos >= 0) {
            int endBrackets = string.indexOf(")", lengthPos);
            if (endBrackets < 0) return 0;
            String term = string.substring(lengthPos + 4, endBrackets);
            int colIndex = compositeIndexResolver.getColumnIndexForHeading(term);
            if (colIndex >= 0) {
                String compString = cells.get(colIndex).getLineValue();
                if (compString == null || compString.length() == 0) {
                    return 0;
                }
                string = string.substring(0, lengthPos) + compString.length() + string.substring(endBrackets + 1);

            } else {
                return 0;
            }
            lengthPos = string.indexOf("len(");

        }
        int minusPos = string.indexOf("-");
        int minus = 0;
        if (minusPos > 0) {
            minus = Integer.parseInt(string.substring(minusPos + 1).trim());
            string = string.substring(0, minusPos).trim();
        }
        int toReturn = Integer.parseInt(string);
        return toReturn - minus;
    }

    // maybe should go in StringUtils. No urgency at the mo
    private static String roundoff(double d) {
        String toReturn = d + "";
        int dPos = toReturn.indexOf(".");
        if (dPos < 0) return toReturn;
        if (toReturn.length() > dPos) {
            int newLen = toReturn.length();
            while (newLen > dPos) {
                if (toReturn.charAt(newLen - 1) != '0') {
                    break;
                }
                newLen--;
            }
            if (newLen < toReturn.length()) {
                if (newLen == dPos + 1) {
                    return toReturn.substring(0, dPos);
                }
                toReturn = toReturn.substring(0, newLen);

            }
        }
        String test = toReturn.substring(0, dPos) + toReturn.substring(dPos + 1);
        int pos9 = test.indexOf("999999999999");
        if (pos9 > 0) {
            char roundUp = (char) (test.charAt(pos9 - 1) + 1);
            test = test.substring(0, pos9 - 1) + roundUp;
        } else {
            int pos0 = test.indexOf("000000000000");
            if (pos0 < 0) {
                return toReturn;
            }
            if (pos0 == 0) {
                test = "0";
            } else {
                test = test.substring(0, pos0);
            }
        }
        if (test.length() > dPos) {
            return test.substring(0, dPos) + "." + test.substring(dPos);
        } else {
            return (test + "0000000000").substring(0, dPos);
        }
    }

     /*

     The key here is that there's a value in a cell and we want to use lookups such as those in the split contract definitions
     to assign the correct name. There's a set of names that might each contain something like

    and('Inception' < "2018-08-01", 'state' in {"DE","MD", "VA"}, 'comm_percent' > 25)
    and('Inception' < "2018-08-01", 'state' in {"GA","NC", "SC"}, 'comm_percent' > 25)
    and('Inception' < "2018-08-01", 'state' = "FL", 'comm_percent' > 25)
    and('Inception' < "2018-08-01", 'state' in {"AL","MS", "LA"}, 'comm_percent' > 25)
    and('Inception' < "2018-08-01", 'state' ="TX", 'comm_percent' > 25)
    and('Inception' < "2018-11-01", 'state' in {"DE","MD", "VA"}, 'comm_percent' > 25)

    Essentially the name for the cell will be derived from other cells in the line according to criteria

     example to illustrate

     lookup 'Policy Reference' in 'Contract Reference' using and('inception' >= `binder contract inception`, 'inception' <= `binder contract expiry`)

     lookupParentIndex = pointer to the heading 'Contract Reference' which will have a name in each cell e.g. BB301212112
     lookupString = and('inception' >= `binder contract inception`, 'inception' <= `binder contract expiry`)

     this will search the children of the set (in this example BB301212112) for elements which satisfy the condition.
     in the condition, '' means headings in the file `` means attributes of the child name so in this case 'inception'
     is a heading in the file and `binder contract inception` and `binder contract expiry` are attributes of children of BB301212112

     there is a backup of best guess when it's above lookupFrom but not below lookupTo, applicable when provisional, note below

     after a name is set here line names is done (though parents and exclusive may need to be sorted)


     As an alternative source of the lookup string lookupString can be an attribute on the child name
     e.g  lookupString = `specification`

     in this case the condition will be taken from the attribute, so will differ for each element tested.  This condition is recognised by the absence of relation operators <=>{}

     so the children of BB301212112 to follow that example would each have their specification. This is used when classifying split contracts - each contract may have a different spec

    EFC note 04/03/20 - now I understand this I'm concerned that too much complexity is going on in the Java. I think this could be done in straight Excel
    with input and output regions but it needs testing.

    WFC - after discussion, the reason why this is done in Java is because the tests are sequential.  In Excel this would mean that the formula for
    deciding a category would need to be in a single cell, instead of maybe a dozen or more here.  This is far easier to maintain in Excel

     */

    private boolean checkLookup(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportCellWithHeading cell, Name parentSet, List<ImportCellWithHeading> lineToLoad, CompositeIndexResolver compositeIndexResolver) throws Exception {
        Pattern p = Pattern.compile("[<=~>{]");
        String condition = cell.getImmutableImportHeading().lookupString;
        Matcher m = p.matcher(condition);
        String conditionAttribute = null;
        if (!m.find()) {
            conditionAttribute = condition;
        }
        boolean provisional = false;
        Map<Name, String> nearestList = new HashMap<>();
        for (Name toTest : parentSet.getChildren()) {
            if (conditionAttribute != null) {
                condition = toTest.getAttribute(conditionAttribute);
            }
            int checkResult = checkCondition(lineToLoad, condition, compositeIndexResolver, toTest, nearestList, provisional);
            if (checkResult == CHECKTRUE) {
                int indexForChild = cell.getImmutableImportHeading().indexForChild;
                int indexForParent = cell.getImmutableImportHeading().exclusiveIndex;
                if (provisional && indexForParent >= 0 && indexForChild >= 0) {//only set the value if there is not already a value in the database
                    Set<Name> childNames = lineToLoad.get(indexForChild).getLineNames();
                    if (childNames == null || childNames.size() == 0 || lineToLoad.get(indexForParent).getLineValue() == null) {
                        return false; //not enough info yet to decide whether to fill in the value
                    }
                    Name childName = childNames.iterator().next();
                    String existingName = childName.getAttribute(lineToLoad.get(indexForParent).getLineValue());
                    if (existingName != null && existingName.length() > 0) {
                        cell.setLineValue(existingName, azquoMemoryDBConnection, attributeNames);
                        cell.addToLineNames(NameService.findByName(azquoMemoryDBConnection, existingName));
                        cell.setLineNamesResolved();
                        return true;
                    }
                }
                //if provisional but can't find existing then just add to line names as normal
                cell.addToLineNames(toTest);
                cell.setLineValue(toTest.getDefaultDisplayName(), azquoMemoryDBConnection, attributeNames);
                cell.setLineNamesResolved(); // deal with its children later
                return true;
            }
            if (checkResult == CHECKMAYBE) {
                //new behaviour.  If the lookup is provisional, then it will never fill in on insufficient information

                if (cell.getImmutableImportHeading().provisional) return false;
                provisional = true;//applies to the next condition only
            } else {
                provisional = false;
            }
        }

        if (nearestList.size() > 0) {
            Name nameFound = null;
            String highestFound = "";
            for (Name toTest : nearestList.keySet()) {
                if (highestFound.compareTo(nearestList.get(toTest)) < 0) {
                    highestFound = nearestList.get(toTest);
                    nameFound = toTest;
                }
            }
            if (nameFound != null) {
                cell.addToLineNames(nameFound);
                cell.setLineValue(nameFound.getDefaultDisplayName(), azquoMemoryDBConnection, attributeNames);
                cell.setLineNamesResolved(); // deal with its children later
                return true;
            }
        }
        return false;
    }

    /* see the comment above checkLookup, this actually checks the condition of the lookup against a child of the parent name

    this is an example from BB304140

    inception and date reference a column. The date and states are string literals and there's the in {} syntax

    */
    // and('Inception' < "2018-08-01", 'state' in {"DE","MD", "VA"})

    private int checkCondition(List<ImportCellWithHeading> lineToLoad, String condition, CompositeIndexResolver compositeIndexResolver, Name nameToTest, final Map<Name, String> nearestList, boolean provisional) {
        //returns CHECKTRUE, CHECKFALSE, CHECKMAYBE
        int found = CHECKFALSE;
        boolean maybe = false;
        if (condition.toLowerCase().equals("all"))
            return CHECKTRUE;// presumably the condition would be from the name attribute in this case - pointless to put it in a heading
        List<String> constants = new ArrayList<>();
        List<List<String>> sets = new ArrayList<>();
        Pattern p = Pattern.compile("\"[^\"]*\"");
        Matcher m = p.matcher(condition);
        StringBuffer newCondition = new StringBuffer();
        int lastPos = 0;
        int count = 0;
        // four parsing while loops, three quote types ", ', ` and the brackets for "in" which are parsed separately
        // the first does string constants "
        while (m.find()) {
            constants.add(condition.substring(m.start() + 1, m.end() - 1));
            newCondition.append(condition, lastPos, m.start()).append(CONSTANTMARKER).append(("" + (count++ + 100)).substring(1));
            lastPos = m.end();
        }
        condition = newCondition.toString() + condition.substring(lastPos);
        newCondition = new StringBuffer();
        lastPos = 0;
        p = Pattern.compile("'[^']*'");
        m = p.matcher(condition);
        // the second loop does composites '
        while (m.find()) {
            String conditionValue = null;
            int fieldNo = compositeIndexResolver.getColumnIndexForHeading(condition.substring(m.start() + 1, m.end() - 1));
            if (fieldNo >= 0) {
                ImportCellWithHeading cell = lineToLoad.get(fieldNo);
                // If it's not resolved no problem, this will be called again if further adjustments are made later
                if (cell.lineValueResolved()) {
                    conditionValue = cell.getLineValue();
                }
                if (conditionValue == null) {
                    return CHECKFALSE;
                }
            }
            constants.add(conditionValue);//note that a null here means that the field does not exist, so the result may be 'maybe'
            newCondition.append(condition.substring(lastPos, m.start()) + CONSTANTMARKER + ("" + (count++ + 100)).substring(1));
            lastPos = m.end();
        }
        condition = newCondition.toString() + condition.substring(lastPos);
        newCondition = new StringBuffer();
        lastPos = 0;
        p = Pattern.compile("`[^`]*`");
        m = p.matcher(condition);
        // the third loop does attributes `
        while (m.find()) {
            String attribute = nameToTest.getAttribute(condition.substring(m.start() + 1, m.end() - 1));
            if (attribute == null) {
                return CHECKFALSE;
            }
            constants.add(attribute);
            newCondition.append(condition.substring(lastPos, m.start()) + CONSTANTMARKER + ("" + (count++ + 100)).substring(1));
            lastPos = m.end();
        }
        condition = newCondition.toString() + condition.substring(lastPos);
        newCondition = new StringBuffer();
        lastPos = 0;
        p = Pattern.compile("\\{[^\\}]*\\}");
        m = p.matcher(condition);
        count = 0;
        // fourth loop does the brackets
        while (m.find()) {
            sets.add(Arrays.asList(condition.substring(m.start() + 1, m.end() - 1).split(",")));
            newCondition.append(condition.substring(lastPos, m.start()) + CONSTANTMARKER + ("" + (count++ + 100)).substring(1));
            lastPos = m.end();

        }
        condition = newCondition.toString() + condition.substring(lastPos);

        List<String> conditions;
        // like Excel syntax, apply multiple conditions
        if (condition.startsWith("and(") && condition.endsWith(")")) {
            conditions = Arrays.asList(condition.substring(4, condition.length() - 1).split(","));
        } else {
            conditions = new ArrayList<>();
            conditions.add(condition);
        }
        for (String element : conditions) {
            found = CHECKFALSE;
            //'state' in {"DE","MD", "VA"})
            int inPos = element.indexOf(" in ");
            if (inPos > 0) {
                String fieldSt = element.substring(0, inPos).trim();
                if (fieldSt.charAt(0) != CONSTANTMARKER) {
                    return CHECKFALSE;
                }
                String fieldFound = constants.get(Integer.parseInt(fieldSt.substring(1)));
                if (fieldFound == null) {
                    maybe = true;
                    found = 1;
                    break;
                } else {
                    List<String> setFound = sets.get(Integer.parseInt(element.substring(inPos + 5)));
                    for (String testField : setFound) {
                        if (testField.trim().startsWith(CONSTANTMARKER + "")) {
                            testField = constants.get(Integer.parseInt(testField.trim().substring(1)));
                        }
                        if (testField.equalsIgnoreCase(fieldFound)) {
                            found = 1;
                            break;
                        }
                    }
                }
            } else {
                p = Pattern.compile("[<=~>]+");
                m = p.matcher(element);
                if (!m.find()) return CHECKFALSE;
                String LHS = interpretTerm(constants, element.substring(0, m.start()).trim());
                String RHS = interpretTerm(constants, element.substring(m.end()).trim());
                if (LHS == null || RHS == null) {
                    maybe = true;
                    found = 1;
                } else {
                    if (RHS.endsWith(PROVISIONAL)) {
                        //some explanation needed.
                        /*categorisation of risk searches for three counties in florida.  Areas in Florida outside the three counties are treated differently
                         * but, if there is no county information, but the state is known to be Florida, the risk is provisionally treated as if in the three counties
                         *
                         * EFC 04/03/20 note - this is a hack for BB304140 which I'm not sure is required any more, I'd quite like to zap it
                         * */
                        if (!provisional) {
                            found = CHECKFALSE;
                            break;
                        }
                        RHS = RHS.substring(0, RHS.indexOf(PROVISIONAL));
                    }
                    String op = m.group();
                    for (int i = 0; i < op.length(); i++) {
                        switch (op.charAt(i)) {
                            case '<':
                                try {
                                    if (Double.parseDouble(LHS) < Double.parseDouble(RHS)) {
                                        found = CHECKTRUE;
                                    }
                                } catch (Exception e) {
                                    if (LHS.toLowerCase().compareTo(RHS.toLowerCase()) < 0) {
                                        found = CHECKTRUE;
                                    }
                                }
                                break;
                            case '=':
                                try {
                                    if (Double.parseDouble(LHS) == Double.parseDouble(RHS)) {
                                        found = CHECKTRUE;
                                    }
                                } catch (Exception e) {
                                    if (LHS.equalsIgnoreCase(RHS)) {
                                        found = CHECKTRUE;
                                    }
                                }
                                break;
                            case '>':
                                try {
                                    if (Double.parseDouble(LHS) > Double.parseDouble(RHS)) {
                                        found = CHECKTRUE;
                                    }
                                } catch (Exception e) {
                                    if (LHS.toLowerCase().compareTo(RHS.toLowerCase()) > 0) {
                                        found = CHECKTRUE;
                                    }
                                }
                                break;
                            case '~':
                                if (LHS.toLowerCase().compareTo(RHS.toLowerCase()) >= 0) {
                                    nearestList.put(nameToTest, RHS.toLowerCase());
                                }
                        }
                    }
                }
            }
            if (found == CHECKFALSE) return CHECKFALSE;
        }
        //'CHECKMAYBE' means that there is insufficient information to be sure
        if (maybe) return CHECKMAYBE;
        return found;
    }

    private static String interpretTerm(List<String> constants, String term) {
        term = term.replace(Character.toString((char) 160), "").trim();
        if (term.charAt(0) == CONSTANTMARKER) {
            return constants.get(Integer.parseInt(term.substring(1)));
        }
        return term;
    }

    private void interpretLine(List<ImportCellWithHeading> cells, int importLine, boolean clearData) throws Exception {
        long tooLong = 2; // now ms
        long time = System.currentTimeMillis();
        for (ImportCellWithHeading cell : cells) {
            boolean peersOk = true;
            // now do the peers
            final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
            // The heading reader has now improved to hand over one set of peer names and indexes, this class need not know if they came from context or not
            // Fairly simple, add the names we already have then look up the ones from the lines based on the peerIndexes
            if (!cell.getImmutableImportHeading().peerNames.isEmpty() || !cell.getImmutableImportHeading().peerIndexes.isEmpty()) {
                namesForValue.addAll(cell.getImmutableImportHeading().peerNames);// start with the ones we have to hand
                for (int peerCellIndex : cell.getImmutableImportHeading().peerIndexes) {
                    ImportCellWithHeading peerCell = cells.get(peerCellIndex);
                    if (peerCell.getLineNames() != null) {
                        namesForValue.addAll(peerCell.getLineNames());
                    } else {// fail - I plan to have resolved all possible line names by this point!
                        peersOk = false;
                        break;
                    }
                }
            }
            if (!peersOk) {
                // was CopyOnWriteArrayList but that made no sense - a single line won't be hit by multiple threads, just this one
                noLinesRejected.incrementAndGet();
                if (linesRejected.size() < 1000) {
                    linesRejected.computeIfAbsent(importLine, t -> new ArrayList<>()).add(":Missing peers for" + cell.getImmutableImportHeading().heading);
                }
            } else if (!namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value - the latter likely if the cell was used to create names rather than store values
                // now we have the set of names for that name with peers get the value from that headingNo it's a heading for
                String value = cell.getLineValue();
                if (!(cell.getImmutableImportHeading().blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    // finally store our value and names for it - only increment the value count if something actually changed in the DB
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue, cell.getImmutableImportHeading().replace);
                } else if (clearData) { // only kicks in if the cell is blank
                    // EFC extracted out of value service, cleaner out here
                    final List<Value> existingValues = ValueService.findForNames(namesForValue);
                    if (existingValues.size() == 1) {
                        existingValues.get(0).delete();
                    }
                }
                // now attribute, not allowing attributes and peers to mix
            } else if (cell.getImmutableImportHeading().indexForAttribute >= 0 && cell.getImmutableImportHeading().attribute != null && cell.getLineValue().length() > 0) {
                String attribute = cell.getImmutableImportHeading().attribute;
                if (cell.getImmutableImportHeading().attributeColumn >= 0) {//attribute *name* refers to the value in another column - so find it
                    attribute = cells.get(cell.getImmutableImportHeading().attributeColumn).getLineValue();
                }
                ImportCellWithHeading identityCell = cells.get(cell.getImmutableImportHeading().indexForAttribute); // get our cell which will have names we want to set the attributes on
                if (identityCell.getLineNames() == null) {
                    noLinesRejected.incrementAndGet();
                    if (linesRejected.size() < 1000) {
                        linesRejected.computeIfAbsent(importLine, t -> new ArrayList<>()).add("No name for attribute " + cell.getImmutableImportHeading().attribute + " of " + cell.getImmutableImportHeading().heading);
                    }
                    break;
                } else {
                    // EFC 24/07/19 before the worst that could happen is an attribute overwrote itself but a hack has been put in AzquoMemoryDBIndex.setAttributeForNameInAttributeNameMap
                    // where the index adapts based on a separated list e.g. for an attribute it has a||b||c which then means that when searching for a name with that attribute either a or b or c will find it
                    // that results in a situation where a seemingly harmless find a name by attribute a with the value b and set a as b can in fact cause a problem
                    if (!cell.equals(identityCell)) {//IF THIS IS THE IDENTITY CELL THE SYSTEM MIGHT OVERRIDE ALTERNATIVE ATTRIBUTES (a||b||c)
                        for (Name name : identityCell.getLineNames()) {
                            // provisional means if there's a value there already don't change it
                            if (!cell.getImmutableImportHeading().provisional || name.getAttribute(attribute) == null) {
                                name.setAttributeWillBePersisted(attribute, cell.getLineValue(), azquoMemoryDBConnection);
                            }
                            // EFC note - need to check on definition - it means something like "the last three months"
                            if (attribute.toLowerCase().equals("definition")) {
                                //work it out now!
                                name.setChildrenWillBePersisted(NameQueryParser.parseQuery(azquoMemoryDBConnection, cell.getLineValue()), azquoMemoryDBConnection);
                            }
                        }
                    }
                }
            }
            long now = System.currentTimeMillis();
            if (now - time > tooLong) {
                System.out.println(cell.getImmutableImportHeading().heading + " took " + (now - time) + "ms");
            }
            time = System.currentTimeMillis();
        }
    }

    private static boolean isZero(String text) {
        try {
            return Double.parseDouble(text.replace(",", "")) == 0.0;
        } catch (Exception e) {
            return true;
        }
    }

    // The cache is purely a performance thing though it's used for a little logging later (total number of names inserted)

    static Name findOrCreateNameStructureWithCache(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent - note it will fail if the name was changed e.g. parents removed by exclusive but that's not a problem
        String np = name + ",";
        if (parent != null) {
            np += parent.getId();
        }
        np += attributeNames.get(0);
        Name found = namesFoundCache.get(np);
        if (found != null) {
            return found;
        }
        found = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, name, parent, local, attributeNames);
        if (found == null) {
            System.out.println("found null in findOrCreateNameStructureWithCache");
            System.out.println("name = " + name);
            System.out.println("parent = " + parent);
            System.out.println("local = " + local);
            System.out.println("attributenames = " + attributeNames);
        } else {
            namesFoundCache.put(np, found);
        }
        return found;
    }

    private static List<String> setLocalLanguage(String localLanguage, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<>();
        if (localLanguage != null) {
            String[] localLangs = localLanguage.split(",");
            for (String localLang : localLangs) {
                languages.add(localLang.trim());
            }
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;
    }
}