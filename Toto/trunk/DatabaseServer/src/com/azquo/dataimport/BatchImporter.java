package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracted from DSImportService on by edward on 12/09/16.
 * <p>
 * This is the class that actually processes each line in a file that's being imported. Designed to be called from a thread pool.
 * The basic file parsing is single threaded but since this can start while later lines are being read I don't think this is a problem.
 * That is to say on a large file the threads will start to stack up fairly quickly
 * Adapted to Callable from Runnable - I like the assurances this gives for memory synchronisation
 * <p>
 */
public class BatchImporter implements Callable<Void> {

    private final AzquoMemoryDBConnection azquoMemoryDBConnection;
    // just to give a little feedback on the number imported
    private final AtomicInteger valuesModifiedCounter;
    private int lineNo;
    private final List<List<ImportCellWithHeading>> dataToLoad;
    private final Map<String, Name> namesFoundCache;
    private final List<String> attributeNames;
    private final Set<Integer> linesRejected;

    BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valuesModifiedCounter, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo, Set<Integer> linesRejected) {
        this.azquoMemoryDBConnection = azquoMemoryDBConnection;
        this.valuesModifiedCounter = valuesModifiedCounter;
        this.dataToLoad = dataToLoad;
        this.namesFoundCache = namesFoundCache;
        this.attributeNames = attributeNames;
        this.lineNo = lineNo;
        this.linesRejected = linesRejected;
    }

    @Override
    public Void call() throws Exception {
        try {
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
            /* skip any line that has a blank in the first column unless the first column had no header
               of course if the first column has no header and then the second has data but not on this line then it would get loaded
               happy for the check to remain in here - more stuff for the multi threaded bit */
                ImportCellWithHeading first = lineToLoad.get(0);
                if (first.getLineValue().length() > 0 || first.getImmutableImportHeading().heading == null || first.getImmutableImportHeading().compositionPattern != null) {
                    //check dates before resolving composite values
                    for (ImportCellWithHeading importCellWithHeading : lineToLoad) {
                        // this basic value checking was outside, I see no reason it shouldn't be in here
                        if (importCellWithHeading.getImmutableImportHeading().attribute != null && importCellWithHeading.getImmutableImportHeading().isDate) {
                    /*
                    interpret the date and change to standard form
                    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    */
                            LocalDate date = isADate(importCellWithHeading.getLineValue());
                            if (date != null) {
                                importCellWithHeading.setLineValue(dateTimeFormatter.format(date));
                            }
                        }
                    }
                    resolveCompositeValues(lineToLoad, lineNo);
                    if (checkOnlyAndExisting(azquoMemoryDBConnection, lineToLoad, attributeNames)) {
                        try {
                            // valueTracker simply the number of values imported
                            valuesModifiedCounter.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, lineNo, linesRejected));
                        } catch (Exception e) {
                            azquoMemoryDBConnection.addToUserLogNoException(e.getMessage(), true);
                            throw e;
                        }
                        Long now = System.currentTimeMillis();
                        if (now - time > 10) { // 10ms a bit arbitrary
                            System.out.println("line no " + lineNo + " time = " + (now - time) + "ms");
                        }
                        time = now;
                    } else if (linesRejected.size() < 1_000) {
                        linesRejected.add(lineNo);
                    }
                }
                lineNo++;
            }
            azquoMemoryDBConnection.addToUserLogNoException("Batch finishing : " + DecimalFormat.getInstance().format(lineNo) + " imported.", true);
            azquoMemoryDBConnection.addToUserLogNoException("Values Imported/Modified : " + DecimalFormat.getInstance().format(valuesModifiedCounter), true);
            return null;
        } catch (Exception e) { // stacktrace first
            e.printStackTrace();
            throw e;
        }
    }

    // Checking only and existing means "should we import the line at all" based on these criteria

    private static boolean checkOnlyAndExisting(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, List<String> languages) {
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().only != null) {
                //`only' can have wildcards  '*xxx*'
                String only = cell.getImmutableImportHeading().only.toLowerCase();
                String lineValue = cell.getLineValue().toLowerCase();
                if (only.startsWith("*")) {
                    if (only.endsWith("*")) {
                        if (!lineValue.contains(only.substring(1, only.length() - 1))) {
                            return false;
                        }
                    } else if (!lineValue.startsWith(only.substring(1))) {
                        return false;
                    }
                } else if (only.endsWith("*")) {
                    if (!lineValue.startsWith(only.substring(0, only.length() - 1))) {
                        return false;
                    }
                } else {
                    if (!lineValue.equals(only)) {
                        return false;
                    }
                }
            }
            // we could be deriving the name from composite so check existing here
            if (cell.getImmutableImportHeading().existing) {
                if (cell.getImmutableImportHeading().attribute != null && cell.getImmutableImportHeading().attribute.length() > 0) {
                    languages = Collections.singletonList(cell.getImmutableImportHeading().attribute);
                }
                if (languages == null) { // same logic as used when creating the line names, not sure of this
                    languages = Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME);
                }
                // note I'm not going to check parentNames are not empty here, if someone put existing without specifying child of then I think it's fair to say the line isn't valid
                for (Name parent : cell.getImmutableImportHeading().parentNames) { // try to find any names from anywhere
                    if (!azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(languages, cell.getLineValue(), parent).isEmpty()) { // NOT empty, we found one!
                        return true; // no point carrying on
                    }
                }
                return false; // none found
            }
        }
        return true;
    }

    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values
    // Now supports basic excel like string operations, left right and mid, also simple single operator calculation on the results.

    private static void resolveCompositeValues(List<ImportCellWithHeading> cells, int lineNo) {
        boolean adjusted = true;
        //loops in case there are multiple levels of dependencies. The compositionPattern stays the same but on each pass the result may be different.
        while (adjusted) {
            adjusted = false;
            for (ImportCellWithHeading cell : cells) {
                if (cell.getImmutableImportHeading().compositionPattern != null) {
                    String result = cell.getImmutableImportHeading().compositionPattern;
                    // do line number first, I see no reason not to. Important for pivot.
                    String LINENO = "LINENO";
                    result = result.replace(LINENO, lineNo + "");
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            // fairly standard replace name of column with column value but with string manipulation left right mid
                            String expression = result.substring(headingMarker + 1, headingEnd);
                            String function = null;
                            int funcInt = 0;
                            int funcInt2 = 0;
                            // checking for things like right(A Column Name, 5). Mid has two numbers.
                            if (expression.contains("(")) {
                                int bracketpos = expression.indexOf("(");
                                function = expression.substring(0, bracketpos);
                                int commaPos = expression.indexOf(",", bracketpos + 1);
                                int secondComma;
                                if (commaPos > 0) {
                                    secondComma = expression.indexOf(",", commaPos + 1);
                                    String countString;
                                    try {
                                        if (secondComma < 0) {
                                            countString = expression.substring(commaPos + 1, expression.length() - 1);
                                            funcInt = Integer.parseInt(countString.trim());
                                        } else {
                                            countString = expression.substring(commaPos + 1, secondComma);
                                            funcInt = Integer.parseInt(countString.trim());
                                            countString = expression.substring(secondComma + 1, expression.length() - 1);
                                            funcInt2 = Integer.parseInt(countString);
                                        }
                                    } catch (Exception ignore) {
                                    }
                                    expression = expression.substring(bracketpos + 1, commaPos);
                                }
                            }
                            // if there was a function its name and parameters have been extracted and expression should now be a column name (trim?)
                            // so resolve the column name and run the function if there was one
                            ImportCellWithHeading compCell = findCellWithHeadingForComposite(expression, cells);
                            if (compCell != null) {
                                String sourceVal = compCell.getLineValue();
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
                                        sourceVal = sourceVal.substring(funcInt - 1, (funcInt - 1) + funcInt2);
                                    }
                                }
                                result = result.replace(result.substring(headingMarker, headingEnd + 1), sourceVal);
                            }
                        }
                        // try to find the start of the next column referenced
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    // single operator calculation after resolving the column names. 1*4.5, 76+345 etc. trim?
                    if (result.toLowerCase().startsWith("calc")) {
                        result = result.substring(5);
                        // IntelliJ said escaping  was redundant I shall assume it's correct.
                        Pattern p = Pattern.compile("[+\\-*/]");
                        Matcher m = p.matcher(result);
                        if (m.find()) {
                            double dresult = 0.0;
                            try {
                                double first = Double.parseDouble(result.substring(0, m.start()));
                                double second = Double.parseDouble(result.substring(m.end()));
                                char c = m.group().charAt(0);
                                switch (c) {
                                    case '+':
                                        dresult = first + second;
                                        break;
                                    case '-':
                                        dresult = first - second;
                                        break;
                                    case '*':
                                        dresult = first * second;
                                        break;
                                    case '/':
                                        dresult = first / second;
                                        break;

                                }
                            } catch (Exception ignored) {
                            }
                            result = dresult + "";
                        }
                    }
                    if (!result.equals(cell.getLineValue())) {
                        cell.setLineValue(result);
                        adjusted = true; // if composition did result in the line value being changed we should run the loop again in case dependencies mean the results will change again
                    }
                }
            }
        }
    }

    // more simple than the column index look up - we just want a a match, don't care about attributes etc.
    private static ImportCellWithHeading findCellWithHeadingForComposite(String nameToFind, List<ImportCellWithHeading> importCellWithHeadings) {
        for (ImportCellWithHeading importCellWithHeading : importCellWithHeadings) {
            ImmutableImportHeading heading = importCellWithHeading.getImmutableImportHeading();
            if (heading.heading != null && heading.heading.equalsIgnoreCase(nameToFind)) {
                return importCellWithHeading;
            }
        }
        return null;
    }

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private static int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo, Set<Integer> linesRejected) throws Exception {
        int valueCount = 0;
        // initial pass to deal with defaults, dates and local parents
        // set defaults before dealing with local parent/child
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.getImmutableImportHeading().defaultValue != null && importCellWithHeading.getLineValue().trim().length() == 0) {
                importCellWithHeading.setLineValue(importCellWithHeading.getImmutableImportHeading().defaultValue);
            }
        }
        for (ImportCellWithHeading importCellWithHeading : cells) {
             /* 3 headings. Town, street, whether it's pedestrianized. Pedestrianized parent of street. Town parent of street local.
             the key here is that the resolveLineNameParentsAndChildForCell has to resolve line Name for both of them - if it's called on "Pedestrianized parent of street" first
             both pedestrianized (ok) and street (NOT ok!) will have their line names resolved
             whereas resolving "Town parent of street local" first means that the street should be correct by the time we resolve "Pedestrianized parent of street".
             essentially sort all local names */
            if (importCellWithHeading.getImmutableImportHeading().lineNameRequired && importCellWithHeading.getImmutableImportHeading().isLocal) {
                // local and it is a parent of another heading (has child heading), inside this function it will use the child heading set up
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        // now sort non local names
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().lineNameRequired && !cell.getImmutableImportHeading().isLocal) {
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, cell, cells, attributeNames, lineNo);
            }
        }

        long tooLong = 2; // now ms
        long time = System.currentTimeMillis();
        // now do the peers
        for (ImportCellWithHeading cell : cells) {
            boolean peersOk = true;
            final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
            // The heading reader has now improved to hand over one set of peer names and indexes, this class need not know if they came from context or not
            // Fairly simple, add the names we already have then look up the ones from the lines based on the peerIndexes
            if (!cell.getImmutableImportHeading().peerNames.isEmpty() || !cell.getImmutableImportHeading().peerIndexes.isEmpty()) {
                namesForValue.addAll(cell.getImmutableImportHeading().peerNames);// start with the ones we have to hand
                for (int peerCellIndex : cell.getImmutableImportHeading().peerIndexes) {
                    ImportCellWithHeading peerCell = cells.get(peerCellIndex);
                    if (peerCell.getLineName() != null) {
                        namesForValue.add(peerCell.getLineName());
                    } else {// fail - I plan to have resolved all possible line names by this point!
                        peersOk = false;
                        break;
                    }
                }
            }
            if (!peersOk) {
                linesRejected.add(lineNo); // new logic to mark unstored values in the lines rejected
            } else if (!namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                String value = cell.getLineValue();
                if (!(cell.getImmutableImportHeading().blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    // finally store our value and names for it - only increnemt the value count if something actually changed in the DB
                    if (ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue)) {
                        valueCount++;
                    }
                }
            }
            // ok that's the peer/value stuff done I think, now onto attributes
            if (cell.getImmutableImportHeading().indexForAttribute >= 0 && cell.getImmutableImportHeading().attribute != null
                    && cell.getLineValue().length() > 0) {
                // handle attribute was here, we no longer require creating the line name so it can in lined be cut down a lot
                ImportCellWithHeading identityCell = cells.get(cell.getImmutableImportHeading().indexForAttribute); // get our source cell
                if (identityCell.getLineName() == null){
                    linesRejected.add(lineNo); // well just mark that the line was no good, should be ok for the moment
                } else {
                    identityCell.getLineName().setAttributeWillBePersisted(cell.getImmutableImportHeading().attribute, cell.getLineValue().replace("\\\\t", "\t").replace("\\\\n", "\n"));
                }
            }
            long now = System.currentTimeMillis();
            if (now - time > tooLong) {
                System.out.println(cell.getImmutableImportHeading().heading + " took " + (now - time) + "ms");
            }
            time = System.currentTimeMillis();
        }
        return valueCount;
    }

    // todo accessed outside, move them out?

    private static LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public static LocalDate isADate(String maybeDate) {
        String dateToTest = maybeDate.replace("/", "-").replace(" ", "-");
        LocalDate date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, ukdf4);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 11 ? dateToTest.substring(0, 11) : dateToTest, ukdf3);
        if (date != null) return date;
        return tryDate(dateToTest.length() > 8 ? dateToTest.substring(0, 8) : dateToTest, ukdf2);
    }

    private static boolean isZero(String text) {
        try {
            double d = Double.parseDouble(text);
            return d == 0.0;
        } catch (Exception e) {
            return true;
        }
    }

    // namesFound is a cache. Then the heading we care about then the list of all headings.
    // This used to be called handle parent and deal only with parents and children but it also resolved line names. Should be called for local first then non local
    // it tests to see if the current line name is null or not as it may have been set by a call to resolveLineNamesParentsChildrenRemove on a different cell setting the child name
    private static void resolveLineNameParentsAndChildForCell(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache,
                                                              ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int lineNo) throws Exception {
        if (cellWithHeading.getLineValue().length() == 0) { // so nothing to do
            return;
        }
        /* ok this is important - I was adjusting cellWithHeading.getLineValue() to add quotes,
        this was not clever as it's referenced in other places (goes back to the idea that ideally this would be immutable)
        So make a local reference to add quotes to
         */
        String cellWithHeadingLineValue = cellWithHeading.getLineValue();
        if (cellWithHeadingLineValue.contains(",") && !cellWithHeadingLineValue.contains(StringLiterals.QUOTE + "")) {//beware of treating commas in cells as set delimiters....
            cellWithHeadingLineValue = StringLiterals.QUOTE + cellWithHeadingLineValue + StringLiterals.QUOTE;
        }
        if (cellWithHeading.getSplitNames() == null) { // then create it, this will take care of the parents ("child of") while creating
            cellWithHeading.setSplitNames(new HashSet<>());
            if (cellWithHeading.getImmutableImportHeading().splitChar == null) {
                if (cellWithHeading.getLineName() == null) {
                    cellWithHeading.setLineName(includeInParents(azquoMemoryDBConnection, namesFoundCache, cellWithHeadingLineValue
                            , cellWithHeading.getImmutableImportHeading().parentNames, cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(cellWithHeading.getImmutableImportHeading(), attributeNames)));
                } else {
                    for (Name parent : cellWithHeading.getImmutableImportHeading().parentNames) {
                        parent.addChildWillBePersisted(cellWithHeading.getLineName());

                    }
                }
                cellWithHeading.getSplitNames().add(cellWithHeading.getLineName());
            } else {
                //sometimes there is a list of parents here (e.g. company industry segments   Retail Grocery/Wholesale Grocery/Newsagent) where we want to insert the child into all sets
                String[] splitStrings = cellWithHeadingLineValue.split(cellWithHeading.getImmutableImportHeading().splitChar);
                for (String splitString : splitStrings) {
                    cellWithHeading.getSplitNames().add(includeInParents(azquoMemoryDBConnection, namesFoundCache, splitString.trim()
                            , cellWithHeading.getImmutableImportHeading().parentNames, cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(cellWithHeading.getImmutableImportHeading(), attributeNames)));
                }
            }
        } else { // it existed (created below as a child name), sort parents if necessary
            for (Name child : cellWithHeading.getSplitNames()) {
                for (Name parent : cellWithHeading.getImmutableImportHeading().parentNames) { // apparently there can be multiple child ofs, put the name for the line in the appropriate sets, pretty vanilla based off the parents set up
                    parent.addChildWillBePersisted(child);
                }
            }
        }
        // ok that's "child of" (as in for names) done
        // now for "parent of", the child of this line
        if (cellWithHeading.getImmutableImportHeading().indexForChild != -1) {
            ImportCellWithHeading childCell = cells.get(cellWithHeading.getImmutableImportHeading().indexForChild);
            if (childCell.getLineValue().length() == 0) {
                throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.getLineValue() + " " + cellWithHeading.getImmutableImportHeading().heading);
            }
            // ok got the child cell, need to find the child cell name to add it to this cell's children
            boolean needsAdding = true; // defaults to true
            if (childCell.getLineName() == null) {
                childCell.setLineName(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childCell.getLineValue(), cellWithHeading.getLineName()
                        , cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(childCell.getImmutableImportHeading(), attributeNames)));
            }
            for (Name parent : cellWithHeading.getSplitNames()) {
                //the 'parent' above is the current cell, not its parent
                // check exclusive to remove the child from some other parents if necessary - this replaces the old "remove from" functionality
                /*
                Exclusive merits explanation. Let us assume cellWithHeading's heading is "Category" (a heading which may have no Name though its cells will)
                which is "child of" "All Categories" (a Name in the database) and "parent of" "Product", another heading. Cells in the "Product" column have Names, childCell.getLineName().
                We might say that the cell in "Category" is "Shirts" and the cell in "Product" is "White Poplin Shirt". By putting exclusive in the "Category" column we're saying
                : get rid of any parents "White Poplin Shirt" has that are in "All Categories" that are NOT "Shirts". I'll use this examples to comment below.
                If exclusive has a value then whatever it specifies replaces the set defined by "child of" ("All Categories in this example") to remove from so :
                get rid of any parents "White Poplin Shirt" has that are in (or are!) "Name Specified By Exclusive" that are NOT "Shirts"
                 */
                // Exclusive is being dealt with as a single name, when defined explicitly it is and if we're deriving it from "child of" we just grab the first.
                Name exclusiveName = null;
                if ("".equals(cellWithHeading.getImmutableImportHeading().exclusive)) {
                    // blank exclusive clause, use "child of" clause - currently this only looks at the first name to be exclusive of, more than one makes little sense
                    // (check all the way down. all children, necessary due due to composite option name1->name2->name3->etc
                    exclusiveName = cellWithHeading.getImmutableImportHeading().parentNames.iterator().next();
                } else if (cellWithHeading.getImmutableImportHeading().exclusive != null) { // exclusive is referring to a higher name
                    exclusiveName = NameService.findByName(azquoMemoryDBConnection, cellWithHeading.getImmutableImportHeading().exclusive);
                }
                if (exclusiveName != null) {
                    /* To follow the example above run through the parents of "White Poplin Shirt".
                    Firstly check that the parent is "Shirts", if it is we of course leave it there and note to not re-add (needsAdding = false).
                      If the parent is NOT "Shirts" we check to see if it's the exclusive set itself ("White Poplin Shirt" was directly in "All Categories")
                      or if the parent is any of the children of the exclusive set in this case maybe "Swimwear". Since we don't know what the existing category
                      structure was check all the way down (findAllChildren() not just getChildren()), "White Poplin Shirt" could have ended up in
                      "All Categories-Swimwear->Mens" for example. Notable that nested name syntax (with "->") is allowed in the cells and
                      might well have been built using the composite functionality above so it's possible another azquo upload could have jammed "White Poplin Shirt"
                      somewhere under "All Categories" many levels below. */
                    for (Name childCellParent : childCell.getLineName().getParents()) {
                        if (childCellParent == cellWithHeading.getLineName()) {
                            needsAdding = false;
                        } else if (childCellParent == exclusiveName || exclusiveName.getChildren().contains(childCellParent)) {
                            childCellParent.removeFromChildrenWillBePersisted(childCell.getLineName());
                        }
                    }
                }
                // having hopefully sorted a new name or exclusive add the child
                if (needsAdding) {
                    parent.addChildWillBePersisted(childCell.getLineName());
                }
            }
        }
    }

    // The cache is purely a performance thing though it's used for a little logging later (total number of names inserted)

    private static Name findOrCreateNameStructureWithCache(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
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
        namesFoundCache.put(np, found);
        return found;
    }

    // to make a batch call to the above if there are a list of parents a name should have

    private static Name includeInParents(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Set<Name> parents, boolean local, List<String> attributeNames) throws Exception {
        Name child = null;
        if (parents == null || parents.size() == 0) {
            child = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, name, null, local, attributeNames);
        } else {
            for (Name parent : parents) {
                child = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, name, parent, local, attributeNames);
            }
        }
        return child;
    }

    private static List<String> setLocalLanguage(ImmutableImportHeading heading, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<>();
        if (heading.attribute != null) {
            languages.add(heading.attribute);
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;
    }
}