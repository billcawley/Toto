package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;

import java.text.DecimalFormat;
import java.time.LocalDate;
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
    private int importLine;
    private final List<List<ImportCellWithHeading>> dataToLoad;
    private final Map<String, Name> namesFoundCache;
    private final List<String> attributeNames;
    private final Set<String> linesRejected;

    BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valuesModifiedCounter, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFoundCache, List<String> attributeNames, int importLine, Set<String> linesRejected) {
        this.azquoMemoryDBConnection = azquoMemoryDBConnection;
        this.valuesModifiedCounter = valuesModifiedCounter;
        this.dataToLoad = dataToLoad;
        this.namesFoundCache = namesFoundCache;
        this.attributeNames = attributeNames;
        this.importLine = importLine;
        this.linesRejected = linesRejected;
    }

    @Override
    public Void call() {
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
            /*
            There's a thought that this should be a whole line check rather than the first column

            skip any line that has a blank in the first column unless the first column had no header or it's composite
            happy for the check to remain in here - more stuff for the multi threaded bit
            blank attribute allowed as it's not structural, a blank attribute won't break anything
            */
                try {
                    ImportCellWithHeading first = lineToLoad.get(0);
                    if (first.getLineValue().length() > 0 || first.getImmutableImportHeading().heading == null || first.getImmutableImportHeading().compositionPattern != null || first.getImmutableImportHeading().attribute != null) {
                        //check dates before resolving composite values
                        for (ImportCellWithHeading importCellWithHeading : lineToLoad) {
                            // this basic value checking was outside, I see no reason it shouldn't be in here
                            // attempt to standardise date formats
                            if (importCellWithHeading.getImmutableImportHeading().attribute != null && importCellWithHeading.getImmutableImportHeading().dateForm > 0) {
                            /*
                            interpret the date and change to standard form
                            todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                            */
                                LocalDate date;
                                if (importCellWithHeading.getImmutableImportHeading().dateForm == StringLiterals.UKDATE) {
                                    date = DateUtils.isADate(importCellWithHeading.getLineValue());
                                } else {
                                    date = DateUtils.isUSDate(importCellWithHeading.getLineValue());
                                }
                                if (date != null) {
                                    importCellWithHeading.setLineValue(DateUtils.dateTimeFormatter.format(date));
                                }
                            }
                        }
                        // default values might now be used by composite
                        resolveDefaultValues(lineToLoad);
                        // composite might do things that affect only and existing hence do it before
                        resolveCompositeValues(azquoMemoryDBConnection, namesFoundCache, attributeNames, lineToLoad, importLine);
                        String rejectionReason = checkOnlyAndExisting(azquoMemoryDBConnection, lineToLoad, attributeNames);
                        if (rejectionReason == null) {
                            try {
                                resolveCategories(azquoMemoryDBConnection, namesFoundCache, lineToLoad);
                                // valueTracker simply the number of values imported
                                valuesModifiedCounter.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, importLine, linesRejected));
                            } catch (Exception e) {
                                azquoMemoryDBConnection.addToUserLogNoException(e.getMessage(), true);
                                e.printStackTrace();
                                throw e;
                            }
                            Long now = System.currentTimeMillis();
                            if (now - time > 10) { // 10ms a bit arbitrary
                                System.out.println("line no " + importLine + " time = " + (now - time) + "ms");
                            }

                            time = now;
                        } else if (linesRejected.size() < 100) {
                            linesRejected.add(importLine + ": " + rejectionReason);
                        }
                    }

                } catch (Exception e) {
                    if (linesRejected.size() < 100) {
                        linesRejected.add(importLine + ": " + e.getMessage() + "\n");
                    }
                }
                importLine++;
            }
            azquoMemoryDBConnection.addToUserLogNoException("Batch finishing : " + DecimalFormat.getInstance().format(importLine) + " imported.", true);
            azquoMemoryDBConnection.addToUserLogNoException("Values Imported/Modified : " + DecimalFormat.getInstance().format(valuesModifiedCounter), true);
            return null;
    }

    // set defaults, factored now as new logic means composites might use defaults
    private static void resolveDefaultValues(List<ImportCellWithHeading> lineToLoad) {
        // set defaults before dealing with local parent/child
        for (ImportCellWithHeading importCellWithHeading : lineToLoad) {
            if (importCellWithHeading.getImmutableImportHeading().defaultValue != null && (importCellWithHeading.getImmutableImportHeading().override != null || importCellWithHeading.getLineValue().trim().length() == 0)) {
                String defaultValue = importCellWithHeading.getImmutableImportHeading().defaultValue;
                if (importCellWithHeading.getImmutableImportHeading().lineNameRequired) {
                    for (ImportCellWithHeading cell : lineToLoad) {
                        // If one of the other cells is referring to this as its attribute e.g. Customer.Address1 and this cell is Customer and blank then set this value to whatever is in Customer.Address1 and set the language to Address1
                        // of course this logic only is used where default is used so it's a question of whether there's a better option than default if the cell is empty
                        // So keep the line imported if there's data missing I guess
                        if (cell != importCellWithHeading && cell.getImmutableImportHeading().indexForAttribute == lineToLoad.indexOf(importCellWithHeading) && cell.getLineValue().length() > 0) {
                            defaultValue = cell.getLineValue();
                            break;
                        }
                    }
                }
                importCellWithHeading.setLineValue(defaultValue);
                importCellWithHeading.setResolved(true);
            }
        }
    }

    // Checking only and existing means "should we import the line at all" based on these criteria

    private static String checkOnlyAndExisting(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, List<String> languages) {
        //returns the error
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().ignoreList != null) {
                for (String ignoreItem : cell.getImmutableImportHeading().ignoreList) {
                    if (cell.getLineValue().toLowerCase().contains(ignoreItem)) {
                        return "ignored";
                    }
                }
            }
            if (cell.getImmutableImportHeading().only != null) {
                //`only' can have wildcards  '*xxx*'
                String only = cell.getImmutableImportHeading().only.toLowerCase();
                String lineValue = cell.getLineValue().toLowerCase();
                if (only.startsWith("*")) {
                    if (only.endsWith("*")) {
                        if (!lineValue.contains(only.substring(1, only.length() - 1))) {
                            return "not in '*only*'";
                        }
                    } else if (!lineValue.endsWith(only.substring(1))) {
                        return "not in `*only'";
                    }
                } else if (only.endsWith("*")) {
                    if (!lineValue.startsWith(only.substring(0, only.length() - 1))) {
                        return "not in 'only*'";
                    }
                } else {
                    if (!lineValue.equals(only)) {
                        return "not in 'only'";
                    }
                }
            }
            // we could be deriving the name from composite so check existing here
            // note that the code assumes there can only be one "existing" per line, it will exit this function on the first one.
            if (cell.getImmutableImportHeading().existing) {
                boolean cellOk = false;
                if (cell.getImmutableImportHeading().attribute != null && cell.getImmutableImportHeading().attribute.length() > 0) {
                    languages = Collections.singletonList(cell.getImmutableImportHeading().attribute);
                }
                if (languages == null) { // same logic as used when creating the line names, not sure of this
                    languages = Collections.singletonList(StringLiterals.DEFAULT_DISPLAY_NAME);
                }
                // note I'm not going to check parentNames are not empty here, if someone put existing without specifying child of then I think it's fair to say the line isn't valid
                for (Name parent : cell.getImmutableImportHeading().parentNames) { // try to find any names from anywhere
                    if (!azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(languages, cell.getLineValue(), parent).isEmpty()) { // NOT empty, we found one!
                        cellOk = true;
                        break; // no point continuing, we found one
                    }
                }
                if (!cellOk) {
                    return cell.getLineValue() + " not existing"; // none found break the line
                }
            }
        }
        return null;
    }

    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values
    // Now supports basic excel like string operations, left right and mid, also simple single operator calculation on the results.
    // Calcs simple for the moment - if required could integrate the shunting yard algorithm

    private static void resolveCompositeValues(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, List<String> attributeNames, List<ImportCellWithHeading> cells, int importLine) throws Exception {
        boolean adjusted = true;
        //loops in case there are multiple levels of dependencies. The compositionPattern stays the same but on each pass the result may be different.
        // note : a circular reference could cause an infinite loop - hence the counter
        int counter = 0;
        while (adjusted && counter < 10) {
            adjusted = false;
            for (ImportCellWithHeading cell : cells) {
                if (!cell.getResolved() && cell.getImmutableImportHeading().compositionPattern != null && (cell.getLineValue() == null || cell.getLineValue().length() == 0)) {
                    String result = cell.getImmutableImportHeading().compositionPattern;
                    // do line number first, I see no reason not to. Important for pivot.
                    String LINENO = "LINENO";
                    result = result.replace(LINENO, importLine + "");
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        boolean doublequotes = false;
                        if (headingMarker < result.length() && result.charAt(headingMarker + 1) == '`') {
                            doublequotes = true;
                            headingMarker++;
                        }
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
                            // used to lookup column name each time but now it's been replaced with the index, required due to Ed Broking heading renaming that can happen earlier
                            // this is probably a bit faster too
                            ImportCellWithHeading compCell = null;
                            try {
                                compCell = cells.get(Integer.parseInt(expression));
                            } catch (Exception ignored) {

                            }
                            if (compCell != null && compCell.getLineValue() != null && resolved(compCell)) {
                                String sourceVal = null;
                                if (compCell.getImmutableImportHeading().lineNameRequired) {
                                    if (compCell.getLineNames() == null && compCell.getLineValue().length() > 0) {
                                        Name compName = includeInParents(azquoMemoryDBConnection, namesFoundCache, compCell.getLineValue().trim()
                                                , compCell.getImmutableImportHeading().parentNames, compCell.getImmutableImportHeading().isLocal, setLocalLanguage(compCell.getImmutableImportHeading().attribute, attributeNames));
                                        compCell.addToLineNames(compName);
                                        if (compName.getDefaultDisplayName().equals(compCell.getLineValue())){
                                            compCell.setLineValue(compName.getDefaultDisplayName());
                                        }

                                    }
                                    if (compCell.getLineNames() != null) {
                                        sourceVal = compCell.getLineNames().iterator().next().getDefaultDisplayName();
                                    }
                                } else {
                                    sourceVal = compCell.getLineValue();
                                }
                                // the two ints need to be as they are used in excel
                                if (sourceVal != null) {
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
                                    headingMarker = headingMarker + sourceVal.length() - 1;//is increaed before two lines below
                                    if (doublequotes) headingMarker++;
                                } else {
                                    result = "";
                                    headingMarker = headingEnd;
                                }
                            } else {
                                headingMarker = headingEnd;
                                result = "";
                            }

                        }
                        // try to find the start of the next column referenced
                        headingMarker = result.indexOf("`", ++headingMarker);
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
                    if (cell.getImmutableImportHeading().removeSpaces) {
                        result = result.replace(" ", "");
                    }
                    if (result.length() > 0 && !result.equals(cell.getLineValue())) {
                        cell.setLineValue(result);
                        cell.setResolved(true);
                        checkLookup(azquoMemoryDBConnection, cell);
                        adjusted = true; // if composition did result in the line value being changed we should run the loop again in case dependencies mean the results will change again
                    }
                }
            }
            counter++;
        }
        if (counter == 10) {
            throw new Exception("circular composite references in headers!");
        }
    }

    private static boolean resolved(ImportCellWithHeading cell) {
        if (cell.getImmutableImportHeading().compositionPattern != null || cell.getImmutableImportHeading().lookupFrom != null) {
            return cell.getResolved();
        }

        return true;
    }

    private static void resolveCategories(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, List<ImportCellWithHeading> cells) throws Exception {
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().dictionaryMap != null) {
                String value = cell.getLineValue();
                if (value != null && value.length() > 0) {
                    boolean hasResult = false;
                    for (Name category : cell.getImmutableImportHeading().dictionaryMap.keySet()) {
                        boolean found = true;
                        List<DictionaryTerm> dictionaryTerms = cell.getImmutableImportHeading().dictionaryMap.get(category);
                        for (DictionaryTerm dictionaryTerm : dictionaryTerms) {
                            found = false; //the phrase now has to pass every one of the tests.  If it does so then the category is found.
                            for (String item : dictionaryTerm.items) {
                                if (dictionaryTerm.exclude) {
                                    if (containsSynonym(cell.getImmutableImportHeading().synonyms, item.toLowerCase().trim(), value.toLowerCase())) {
                                        found = false;
                                        break;
                                    }
                                } else {
                                    if (containsSynonym(cell.getImmutableImportHeading().synonyms, item.toLowerCase().trim(), value.toLowerCase())) {
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
                            List<String> languages = Collections.singletonList(StringLiterals.DEFAULT_DISPLAY_NAME);
                            cell.addToLineNames(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, "Uncategorised " + parent.getDefaultDisplayName(), parent, false, languages));
                        } else {
                            cell.setLineValue("");
                        }
                    }
                }
            }
        }
    }

    // categorise numeric values, see HeadingReader

    private static void checkLookup(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportCellWithHeading cell) throws Exception {
        if (cell.getLineValue() != null && cell.getLineValue().length() > 0 && cell.getImmutableImportHeading().lookupFrom != null && cell.getLineNames() == null) {
            int commaPos = cell.getLineValue().indexOf(",");
            if (commaPos < 0) {
                throw new Exception(cell.getImmutableImportHeading().heading + " has no comma in the lookup value");
            }
            String setName = cell.getLineValue().substring(0, commaPos).trim();
            String valueToTest = cell.getLineValue().substring(commaPos + 1).trim();
            Name toTestParent = NameService.findByName(azquoMemoryDBConnection, setName);

            if (toTestParent == null) {
                throw new Exception((cell.getImmutableImportHeading().heading + " no such set: " + setName));
            }
            boolean found = false;
            for (Name toTest : toTestParent.getChildren()) {
                String lowLimit = toTest.getAttribute(cell.getImmutableImportHeading().lookupFrom);
                if (lowLimit != null) {
                    try {

                        Double d = Double.parseDouble(lowLimit);
                        Double d2 = Double.parseDouble(valueToTest);
                        if (d2 >= d) {
                            if (cell.getImmutableImportHeading().lookupTo != null) {
                                String highlimit = toTest.getAttribute(cell.getImmutableImportHeading().lookupTo);
                                if (highlimit != null) {
                                    d = Double.parseDouble(highlimit);
                                    if (d2 <= d) {
                                        found = true;
                                        newCellNameValue(cell, toTest);
                                        break;
                                    }
                                }
                            } else {
                                newCellNameValue(cell, toTest);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        //compare strings
                        if (lowLimit.compareTo(valueToTest) <= 0) {
                            if (cell.getImmutableImportHeading().lookupTo != null) {
                                String highLimit = toTest.getAttribute(cell.getImmutableImportHeading().lookupTo);
                                if (highLimit != null && highLimit.compareTo(valueToTest) >= 0) {
                                    newCellNameValue(cell, toTest);
                                    found = true;
                                    break;
                                }
                            } else {
                                newCellNameValue(cell, toTest);
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!found) {
                throw new Exception("lookup for " + cell.getImmutableImportHeading().heading + " on " + setName + " and " + valueToTest);
            }
        }
    }

    private static boolean containsSynonym(Map<String, List<String>> synonymList, String term, String value) {
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

    private static void newCellNameValue(ImportCellWithHeading cell, Name name) {
        cell.addToLineNames(name);
        cell.setLineValue(name.getDefaultDisplayName());
        cell.setResolved(true);
    }

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private static int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int importLine, Set<String> linesRejected) throws Exception {
        int valueCount = 0;
        // initial pass to deal spaces that might need removing and local parents
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.getImmutableImportHeading().removeSpaces) {
                importCellWithHeading.setLineValue(importCellWithHeading.getLineValue().replace(" ", ""));
            }
        }
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().lineNameRequired) {
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, cell, cells, attributeNames, importLine, 0);
            }
        }

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
                linesRejected.add(importLine + ":Missing peers for" + cell.getImmutableImportHeading().heading); // new logic to mark unstored values in the lines rejected
            } else if (!namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                String value = cell.getLineValue();
                if (!(cell.getImmutableImportHeading().blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    // finally store our value and names for it - only increment the value count if something actually changed in the DB
                    if (ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue, cell.getImmutableImportHeading().replace)) {
                        valueCount++;
                    }
                } else if (cell.getImmutableImportHeading().clearData) { // only kicks in if the cell is blank
                    // EFC extracted out of value service, cleaner out here
                    final List<Value> existingValues = ValueService.findForNames(namesForValue);
                    if (existingValues.size() == 1) {
                        existingValues.get(0).delete();
                    }

                }
            } else { // now attribute, not allowing attributes and peers to mix
                if (cell.getImmutableImportHeading().indexForAttribute >= 0 && cell.getImmutableImportHeading().attribute != null
                        && cell.getLineValue().length() > 0) {
                    String attribute = cell.getImmutableImportHeading().attribute;
                    if (cell.getImmutableImportHeading().attributeColumn >= 0) {//attribute name refers to the value in another column - so find it
                        attribute = cells.get(cell.getImmutableImportHeading().attributeColumn).getLineValue();
                    }
                    // handle attribute was here, we no longer require creating the line name so it can in lined be cut down a lot
                    ImportCellWithHeading identityCell = cells.get(cell.getImmutableImportHeading().indexForAttribute); // get our source cell
                    if (identityCell.getLineNames() == null) {
                        linesRejected.add(importLine + " No name for attribute " + cell.getImmutableImportHeading().attribute + " of " + cell.getImmutableImportHeading().heading);
                        break;
                    } else {
                        for (Name name : identityCell.getLineNames()) {
                            name.setAttributeWillBePersisted(attribute, cell.getLineValue());
                            // EFC note - need to check on definition
                            if (attribute.toLowerCase().equals("definition")) {
                                //work it out now!
                                name.setChildrenWillBePersisted(NameQueryParser.parseQuery(azquoMemoryDBConnection, cell.getLineValue()));
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
        return valueCount;
    }

    private static boolean isZero(String text) {
        try {
            return Double.parseDouble(text.replace(",", "")) == 0.0;
        } catch (Exception e) {
            return true;
        }
    }


    /* namesFound is a cache. Then the heading we care about then the list of all headings.
     * */

    private static void resolveLineNameParentsAndChildForCell(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache,
                                                              ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int importLine, int recursionLevel) throws Exception {
        /*
             Imagine 3 headings. Town, street, whether it's pedestrianized. Pedestrianized parent of street. Town parent of street local.
             the key here is that the resolveLineNameParentsAndChildForCell has to resolve line Name for both of them - if it's called on "Pedestrianized parent of street" first
             both pedestrianized (ok) and street (NOT ok!) will have their line names resolved
             whereas resolving "Town parent of street local" first means that the street should be correct by the time we resolve "Pedestrianized parent of street".
             essentially sort local names need to be sorted first.

             EFC note August 2018 after modifying WFC code : the old method was just to run through cells that have isLocal first but that could be tripped up
             by a local in a local which, while note recommended, is supported. The key here is that locals should be resolved in order starting at the top.

             localParentIndexes enables this, recurse up to the top and go down meaning this cell will be safe to resolve if there are local names involved.

              */
        recursionLevel++;
        for (int localParentIndex : cellWithHeading.getImmutableImportHeading().localParentIndexes) {
            ImportCellWithHeading parentHeading = cells.get(localParentIndex);
            if (parentHeading.getLineNames() == null || parentHeading.getLineNames().size() == 0) { // so it has not been resolved yet!
                if (recursionLevel == 8) { // arbitrary but not unreasonable
                    throw new Exception("recursion loop on heading " + cellWithHeading.getImmutableImportHeading().heading);
                }
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, parentHeading, cells, attributeNames, importLine, recursionLevel);
            }
        }
        // in simple terms if a line cell value refers to a name it can now refer to a set of names
        // to make a set parent of more than one thing e.g. parent of set a, set b, set c
        // nothing in the heading has changed except the split char but we need to detect it here
        // split before checking for quotes etc. IF THE SPLIT CHAR IS IN QUOTES WE DON'T CURRENTLY SUPPORT THAT! e.g. ,
        String[] nameNames;
        if (cellWithHeading.getImmutableImportHeading().splitChar == null) {
            nameNames = new String[]{cellWithHeading.getLineValue()};
        } else {
            nameNames = cellWithHeading.getLineValue().split(cellWithHeading.getImmutableImportHeading().splitChar);
        }

        if (cellWithHeading.getLineNames() == null) { // then create it, this will take care of the parents ("child of") while creating
            //sometimes there is a list of parents here (e.g. company industry segments   Retail Grocery/Wholesale Grocery/Newsagent) where we want to insert the child into all sets
            for (String nameName : nameNames) {
                if (nameName.trim().length() > 0) {
                    cellWithHeading.addToLineNames(includeInParents(azquoMemoryDBConnection, namesFoundCache, nameName.trim()
                            , cellWithHeading.getImmutableImportHeading().parentNames, cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(cellWithHeading.getImmutableImportHeading().attribute, attributeNames)));
                }
            }
        } else { // it existed (created below as child name(s))
            for (Name child : cellWithHeading.getLineNames()) {
                for (Name parent : cellWithHeading.getImmutableImportHeading().parentNames) { // apparently there can be multiple child ofs, put the name for the line in the appropriate sets, pretty vanilla based off the parents set up
                    parent.addChildWillBePersisted(child);
                }
            }
        }
        // ok that's "child of" (as in for names) done
        // now for "parent of", the child of this line
        if (cellWithHeading.getImmutableImportHeading().indexForChild != -1 && cellWithHeading.getLineValue().length() > 0) {
            ImportCellWithHeading childCell = cells.get(cellWithHeading.getImmutableImportHeading().indexForChild);
            if (childCell.getLineValue().length() == 0) {
                throw new Exception("blank value for " + childCell.getImmutableImportHeading().heading + " (child of " + cellWithHeading.getLineValue() + " " + cellWithHeading.getImmutableImportHeading().heading + ")");
            }
            // ok got the child cell, need to find the child cell name to add it to this cell's children
            if (childCell.getLineNames() == null) {
                // child cell needs to support
                String[] childNames;
                if (childCell.getImmutableImportHeading().splitChar == null) {
                    childNames = new String[]{childCell.getLineValue()};
                } else {
                    childNames = childCell.getLineValue().split(childCell.getImmutableImportHeading().splitChar);
                }
                for (String childName : childNames) {
                    // can cellWithHeading.getLineNames() actually be null by this point? Maybe if the line was blank
                    if (cellWithHeading.getLineNames() != null) {
                        for (Name thisCellsName : cellWithHeading.getLineNames()) {
                            childCell.addToLineNames(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childName, thisCellsName
                                    , cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(childCell.getImmutableImportHeading().attribute, attributeNames)));
                        }
                    }
                }
            } else { // a simple include in sets if line names exists. Exclusive after is for taking stuff out if required
                // in theory this column could be multiple parents and the column "parent of" refers to could be multiple children, permute over the combinations
                if (cellWithHeading.getLineNames() != null) { // it can be null, not sure if it should be?? But it can be, stop NPE
                    for (Name parent : cellWithHeading.getLineNames()) {
                        for (Name childCellName : childCell.getLineNames()) {
                            parent.addChildWillBePersisted(childCellName);
                        }
                    }
                }
            }
            // note! Exclusive can't work if THIS column is multiple names
            if (cellWithHeading.getLineNames() != null) {
                if (cellWithHeading.getLineNames().size() == 1 && cellWithHeading.getImmutableImportHeading().exclusive != null) {
                    Name parent = cellWithHeading.getLineNames().iterator().next();
                    //the 'parent' above is the current cell name, not its parent
                    // check exclusive to remove the child from some other parents if necessary - this replaces the old "remove from" functionality
                /*
                Exclusive merits explanation. Let us assume cellWithHeading's heading is "Category" (a heading which may have no Name though its cells will)
                which is "child of" "All Categories" (a Name in the database) and "parent of" "Product", another heading. Cells in the "Product" column have Names, childCell.getLineNames().
                We might say that the cell in "Category" is "Shirts" and the cell in "Product" is "White Poplin Shirt". By putting exclusive in the "Category" column we're saying
                : get rid of any parents "White Poplin Shirt" has that are in "All Categories" that are NOT "Shirts". I'll use this examples to comment below.
                If exclusive has a value then whatever it specifies replaces the set defined by "child of" ("All Categories in this example") to remove from so :
                get rid of any parents "White Poplin Shirt" has that are in (or are!) "Name Specified By Exclusive" that are NOT "Shirts"
                 */
                    // Exclusive is being dealt with as a single name, when defined explicitly it is and if we're deriving it from "child of" we just grab the first.
                    Name exclusiveName;
                    if ("".equals(cellWithHeading.getImmutableImportHeading().exclusive)) {
                        // blank exclusive clause, use "child of" clause - currently this only looks at the first name to be exclusive of, more than one makes little sense
                        // (check all the way down. all children, necessary due due to composite option name1->name2->name3->etc
                        exclusiveName = cellWithHeading.getImmutableImportHeading().parentNames.iterator().next();
                    } else { // exclusive has a value, not null or blank, is referring to a higher name
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
                        // given that we now have multiple names on a line we run through the child ones checking as necessary
                        for (Name childCellName : childCell.getLineNames()) {
                            boolean needsAdding = true; // defaults to true
                            for (Name childCellParent : childCellName.getParents()) {
                                if (childCellParent == parent) {
                                    needsAdding = false;
                                } else if (childCellParent == exclusiveName || exclusiveName.getChildren().contains(childCellParent)) {
                                    childCellParent.removeFromChildrenWillBePersisted(childCellName);
                                }
                            }
                            // having hopefully sorted a new name or exclusive add the child
                            if (needsAdding) {
                                parent.addChildWillBePersisted(childCellName);
                            }
                        }
                    }
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