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
 * Note 04/01/19 - could be easier to understand. Moving dictionary to reports will help.
 * <p>
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
                    //check dates before resolving composite values
                    for (ImportCellWithHeading importCellWithHeading : lineToLoad) {
                        // this basic value checking was outside, I see no reason it shouldn't be in here
                        // attempt to standardise date formats
                        checkDate(importCellWithHeading);
                    }
                    String rejectionReason = null;
                    // simple ignore list check
                    for (ImportCellWithHeading cell : lineToLoad) {
                        if (cell.getImmutableImportHeading().ignoreList != null) {
                            for (String ignoreItem : cell.getImmutableImportHeading().ignoreList) {
                                if (cell.getLineValue().toLowerCase().contains(ignoreItem)) {
                                    rejectionReason = "ignored";
                                }
                            }
                        }
                    }
                    // composite might do things that affect only and existing hence do it before
                    if (rejectionReason == null) {
                        resolveCompositeValues(azquoMemoryDBConnection, namesFoundCache, attributeNames, lineToLoad, lineNumber, compositeIndexResolver);
                        rejectionReason = checkOnlyAndExisting(azquoMemoryDBConnection, lineToLoad, attributeNames);
                    }
                    if (rejectionReason == null) {
                        try {
                            // dictionary stuff, need to remove when it's confirmed working in reports, parkd currently 24/07/2019
                            resolveIntoCategories(azquoMemoryDBConnection, namesFoundCache, lineToLoad);
                            interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, lineNumber, linesRejected, clearData);
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
                    } else if (!"ignored".equalsIgnoreCase(rejectionReason)) {
                        noLinesRejected.incrementAndGet();
                        if (linesRejected.size() < 1000) {
                            linesRejected.computeIfAbsent(lineNumber,
                                    t -> new ArrayList<>()).add(rejectionReason);
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
        // don't have values modified any more - need to replace this or just zap it?
//        azquoMemoryDBConnection.addToUserLogNoException("..Values Imported/Modified : " + DecimalFormat.getInstance().format(valuesModifiedCounter), true);
        return null;
    }

    /*
    interpret the date and change to standard form
    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
    HeadingReader defines DATELANG and USDATELANG
    */
    private static void checkDate(ImportCellWithHeading importCellWithHeading) {
        if (importCellWithHeading.getImmutableImportHeading().attribute != null && importCellWithHeading.getImmutableImportHeading().dateForm > 0) {
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

    // Checking only and existing means "should we import the line at all" based on these criteria

    private static String checkOnlyAndExisting(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, List<String> languages) {
        //returns the error
        for (ImportCellWithHeading cell : cells) {
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
            // this assumes composite has been run if required
            // note that the code assumes there can only be one "existing" per line, it will exit this function on the first one.
            if (cell.getImmutableImportHeading().existing) {
                boolean cellOk = false;
                if (cell.getImmutableImportHeading().attribute != null && cell.getImmutableImportHeading().attribute.length() > 0) {
                    languages = new ArrayList<>();
                    String newLanguages = cell.getImmutableImportHeading().attribute;
                    languages.addAll(Arrays.asList(newLanguages.split(",")));
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

    private static void resolveCompositeValues(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, List<String> attributeNames, List<ImportCellWithHeading> cells, int importLine, CompositeIndexResolver compositeIndexResolver) throws Exception {
        boolean adjusted = true;
        int timesLineIsModified = 0;
        // first pass, sort overrides and flag what might need resolving
        for (ImportCellWithHeading cell : cells) {
            // we try to resolve if there's a composition pattern *and* no value in the cell. If there's a value in the cell we don't override it with a composite value
            if (cell.getImmutableImportHeading().compositionPattern == null || (cell.getLineValue() != null && !cell.getLineValue().isEmpty())) {
                cell.needsResolving = false;
            }
            if (cell.getImmutableImportHeading().override != null) {
                cell.setLineValue(cell.getImmutableImportHeading().override);
                cell.needsResolving = false;
                if (cell.getImmutableImportHeading().lineNameRequired) {
                    Name compName = includeInParents(azquoMemoryDBConnection, namesFoundCache, cell.getLineValue().trim()
                            , cell.getImmutableImportHeading().parentNames, cell.getImmutableImportHeading().isLocal, StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST);
                    cell.addToLineNames(compName);
                }
            }
        }
        int loopLimit = 10;
        // loops in case there are multiple levels of dependencies. The compositionPattern stays the same but on each pass the result may be different.
        // note : a circular reference could cause an infinite loop - hence timesLineIsModified limit
        while (adjusted && timesLineIsModified < loopLimit) {
            adjusted = false;
            for (ImportCellWithHeading cell : cells) {
                if (cell.needsResolving) {
                    String compositionPattern = cell.getImmutableImportHeading().compositionPattern;
                    compositionPattern = compositionPattern.replace("LINENO", importLine + "");
                    if (compositionPattern.toLowerCase().startsWith("if(")) {
                        if (resolveIf(azquoMemoryDBConnection, cell, compositionPattern, cells, compositeIndexResolver, namesFoundCache, attributeNames)) {
                            adjusted = true;
                        }
                    } else {
                        if (resolveComposition(azquoMemoryDBConnection, cell, compositionPattern, cells, compositeIndexResolver, namesFoundCache, attributeNames)) {
                            adjusted = true;
                        }
                    }
                }
            }
            timesLineIsModified++;
            if (timesLineIsModified == loopLimit) {
                throw new Exception("Circular composite references in headings!");
            }
        }
    }

    private static int stringTerm(String string, List<ImportCellWithHeading> cells, CompositeIndexResolver compositeIndexResolver) {
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

    // this routine uses cell.lineValue to store interim results...
    // comma as opposed to ? : as that syntax is what is used in Excel

    private static boolean resolveIf(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportCellWithHeading cell, String compositionPattern, List<ImportCellWithHeading> cells, CompositeIndexResolver compositeIndexResolver, Map<String, Name> namesFoundCache, List<String> attributeNames) throws Exception {
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
        if (!resolveComposition(azquoMemoryDBConnection, cell, condition.substring(0, conditionPos).trim(), cells, compositeIndexResolver, namesFoundCache, attributeNames))
            return false;
        // as mentioned above - composition jams the result into line value so need to get it out of there
        String leftTerm = cell.getLineValue();
        if (!resolveComposition(azquoMemoryDBConnection, cell, condition.substring(conditionPos + conditionTerm.length()).trim(), cells, compositeIndexResolver, namesFoundCache, attributeNames))
            return false;
        String rightTerm = cell.getLineValue();
        // string compare only currently, could probably detect numbers and adjust accordingly
        if ((conditionTerm.contains("=") && leftTerm.equals(rightTerm)) || (conditionTerm.contains("<") && leftTerm.compareTo(rightTerm) < 0) || (conditionTerm.contains(">") && leftTerm.compareTo(rightTerm) > 0)) {
            return resolveComposition(azquoMemoryDBConnection, cell, trueTerm, cells, compositeIndexResolver, namesFoundCache, attributeNames);
        }
        return resolveComposition(azquoMemoryDBConnection, cell, falseTerm, cells, compositeIndexResolver, namesFoundCache, attributeNames);
    }

    private static String findEquals(String term) {
        if (term.contains(">=")) return ">=";
        if (term.contains("<=")) return "<=";
        if (term.contains("=")) return "=";
        if (term.contains(">")) return ">";
        if (term.contains("<")) return "<";
        return null;
    }

    private static boolean resolveComposition(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportCellWithHeading cell, String compositionPattern, List<ImportCellWithHeading> cells, CompositeIndexResolver compositeIndexResolver, Map<String, Name> namesFoundCache, List<String> attributeNames) throws Exception {
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
                                    funcInt = stringTerm(countString.toLowerCase().trim(), cells, compositeIndexResolver);
                                } else {
                                    countString = expression.substring(commaPos + 1, secondComma);
                                    funcInt = stringTerm(countString.toLowerCase().trim(), cells, compositeIndexResolver);
                                    countString = expression.substring(secondComma + 1, expression.length() - 1);
                                    funcInt2 = stringTerm(countString.toLowerCase().trim(), cells, compositeIndexResolver);
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
                if (compCell != null && compCell.getLineValue() != null && !compCell.needsResolving) {
                    String sourceVal;
                    // we have a name attribute and it is a column with a name, we resolve the name if necessary and get the attribute
                    if (nameAttribute != null && compCell.getImmutableImportHeading().lineNameRequired) {
                        if (compCell.getLineNames() == null && compCell.getLineValue().length() > 0) {
                            Name compName = includeInParents(azquoMemoryDBConnection, namesFoundCache, compCell.getLineValue().trim()
                                    , compCell.getImmutableImportHeading().parentNames, compCell.getImmutableImportHeading().isLocal, setLocalLanguage(compCell.getImmutableImportHeading().attribute, attributeNames));
                            compCell.addToLineNames(compName);
                        }
                        if (compCell.getLineNames() == null) {
                            return false;
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
                            if (function.equalsIgnoreCase("standardise")){
                                sourceVal = sourceVal.replaceAll("[^0-9a-zA-Z]", "").toLowerCase().substring(0,funcInt);
                            }
                        }
                        // now replace and move the marker to the next possible place
                        compositionPattern = compositionPattern.replace(compositionPattern.substring(headingMarker, headingEnd + 1), sourceVal);
                        headingMarker = headingMarker + sourceVal.length() - 1;//is increased before two lines below
                        if (doubleQuotes) headingMarker++;
                    } else {
                        // can't get the value . . .
                        return false;
                    }
                } else { // couldn't find the cell or the required cell is already resolved, the latter resulting in false is the "no more work to do" signal
                    return false;
                }
            }
            // try to find the start of the next column referenced
            headingMarker = compositionPattern.indexOf("`", ++headingMarker);
        }
        // todo - investigate third party libraries to evaluate expressions
        // after all the column/string function/attribute still is done there may yet be some basic numeric stuff to do
        // single operator calculation after resolving the column names. 1*4.5, 76+345 etc. trim?
        if (compositionPattern.toLowerCase().startsWith("calc")) {
            compositionPattern = compositionPattern.substring(5);
            // IntelliJ said escaping  was redundant I shall assume it's correct.
            Pattern p = Pattern.compile("[+\\-*/]");
            Matcher m = p.matcher(compositionPattern);
            if (m.find()) {
                double dresult = 0.0;
                try {
                    double first = Double.parseDouble(compositionPattern.substring(0, m.start()));
                    double second = Double.parseDouble(compositionPattern.substring(m.end()));
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
                compositionPattern = dresult + "";
            }
        }
        // this is being done in here as well as later as it may affect dependencies when resolving composite, can't wait until later
        if (cell.getImmutableImportHeading().removeSpaces) {
            compositionPattern = compositionPattern.replace(" ", "");
        }
        if (compositionPattern.startsWith("\"") && compositionPattern.endsWith("\"")) {
            compositionPattern = compositionPattern.substring(1, compositionPattern.length() - 1);
        }
        cell.setLineValue(compositionPattern);
        cell.needsResolving = false;
        checkLookup(azquoMemoryDBConnection, cell);
        checkDate(cell);
        return true; // if composition did result in the line value being changed we should run the loop again in case dependencies mean the results will change again
    }


    /*

Thd dictionary substitutes free style text with categories from a lookup set.

Each lookup (e.g   '123 Auto Accident not relating to speed') is given a lookup phrase (e.g.   car + accident - speed)
 in which if each positive term is found, and no negative term is found, the phrase is replaced by the category.
  The terms may consist of lists (' car + accident - speed, fast).
  There may also be a set of 'synonyms' which consists of similar words (e.g.  car : vehicle, auto, motor) to help the definition

    Also see note on MutableImportHeading dictionaryMap

    *** Following discussion 20/12/2018 this will be moved to reporting
    22/02/19 - added to the report side, will be deleted from here when the reports use the new code
    24/07/19 - still not ready to be removed
     */

    private static void resolveIntoCategories(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, List<ImportCellWithHeading> cells) throws Exception {
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().dictionaryMap != null) {
                String value = cell.getLineValue();
                if (value != null && value.length() > 0) {
                    boolean hasResult = false;
                    for (Name category : cell.getImmutableImportHeading().dictionaryMap.keySet()) {
                        boolean found = true;
                        List<ImmutableImportHeading.DictionaryTerm> dictionaryTerms = cell.getImmutableImportHeading().dictionaryMap.get(category);
                        for (ImmutableImportHeading.DictionaryTerm dictionaryTerm : dictionaryTerms) {
                            found = false; //the phrase now has to pass every one of the tests.  If it does so then the category is found.
                            for (String item : dictionaryTerm.items) {
                                if (dictionaryTerm.exclude) {
                                    if (containsSynonym(cell.getImmutableImportHeading().synonyms, item.toLowerCase().trim(), value.toLowerCase())) {
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

     /*categorise numeric values, see HeadingReader.java
     lookup assumes that the value in the cell is two values comma separated. The name of a set and the value to look for in its children.
     Typically this value is made by a composition though it doesn't have to be
     from/to are different attributes to check against in the set
     lookup used for finding a contract year off a date . . .
     apparently can't remove. I was up to here in checking - July 2019*/

    private static void checkLookup(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportCellWithHeading cell) throws Exception {
        /* example to illustrate
        lookup from `binder contract inception` to `binder contract expiry`
        so

        lookupFrom `binder contract inception`
        lookupTo `binder contract expiry`

        notably these two are attribute names - it seems a little odd that the attribute names are fixed but the set can change, we're finding these for each of the children of setName

        The line value in this case is composition `left(Bordereau Contract Year,9)`,`inception`

        the setName there will be a BB123456 or something similar, inception is a date

    `   so we get the children of the contract year and run through them checking the from and to attributes and the first
        time we find the value sitting between them we have our match and set the line name to be that name and the value to default display name
        there is a backup of best guess when it's above lookupFrom but not below lookupTo, applicable when provisional, note below

        Also note lookupTo can be null in which case it just needs greater then lookupFrom to match
         */
        if (cell.getLineValue() != null && cell.getLineValue().length() > 0 && cell.getImmutableImportHeading().lookupFrom != null && cell.getLineNames() == null) {
            int commaPos = cell.getLineValue().indexOf(",");
            if (commaPos < 0) {
                throw new Exception(cell.getImmutableImportHeading().heading + " has no comma in the lookup value");
            }
            String setName = cell.getLineValue().substring(0, commaPos).trim();
            String valueToTest = cell.getLineValue().substring(commaPos + 1).trim();
            Name toTestParent = NameService.findByName(azquoMemoryDBConnection, setName);

            if (toTestParent == null) {
                throw new Exception((cell.getImmutableImportHeading().heading + " no such name: " + setName));
            }
            boolean found = false;
            String bestFrom = "";
            double bestFromNo = 0;
            Name bestGuess = null;
            for (Name toTest : toTestParent.getChildren()) {
                String lowLimit = toTest.getAttribute(cell.getImmutableImportHeading().lookupFrom);
                if (lowLimit != null) {
                    try {
                        Double d = Double.parseDouble(lowLimit);
                        Double d2 = Double.parseDouble(valueToTest);
                        if (d2 >= d) {
                            if (d > bestFromNo) {
                                bestFromNo = d;
                                bestGuess = toTest;
                            }
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
                                found = true;
                            }
                        }
                    } catch (Exception e) {
                        //compare strings
                        if (lowLimit.compareTo(valueToTest) <= 0) {
                            if (lowLimit.compareTo(bestFrom) > 0) {
                                bestFrom = lowLimit;
                                bestGuess = toTest;
                            }
                            if (cell.getImmutableImportHeading().lookupTo != null) {
                                String highLimit = toTest.getAttribute(cell.getImmutableImportHeading().lookupTo);
                                if (highLimit != null && highLimit.compareTo(valueToTest) >= 0) {
                                    newCellNameValue(cell, toTest);
                                    found = true;
                                    break;
                                }
                            } else {
                                found = true;
                            }
                        }
                    }
                }
            }
            if (found) {
                newCellNameValue(cell, bestGuess);
            } else {
                // provisional means we only store if something does not exist already. Under these circumstances loosen the matching criteria and allow best guess
                if (cell.getImmutableImportHeading().provisional && bestGuess != null) {
                    newCellNameValue(cell, bestGuess);
                } else {
                    throw new Exception("lookup for " + cell.getImmutableImportHeading().heading + " on " + setName + " and " + valueToTest + " not found");
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

    private static void newCellNameValue(ImportCellWithHeading cell, Name name) {
        cell.addToLineNames(name);
        cell.setLineValue(name.getDefaultDisplayName());
        cell.needsResolving = false;
    }

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private static void interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int importLine, Map<Integer, List<String>> linesRejected, boolean clearData) throws Exception {
        // initial pass to deal with spaces that might need removing and local parents
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.getImmutableImportHeading().removeSpaces) {
                importCellWithHeading.setLineValue(importCellWithHeading.getLineValue().replace(" ", ""));
            }
        }
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().lineNameRequired) {
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, cell, cells, attributeNames, 0);
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
                // was CopyOnWriteArrayList but that made no sense - a single line won't be hit by multiple threads, just this one
                linesRejected.computeIfAbsent(importLine, t -> new ArrayList<>()).add(":Missing peers for" + cell.getImmutableImportHeading().heading);
            } else if (!namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a heading for
                String value = cell.getLineValue();
                if (!(cell.getImmutableImportHeading().blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    // finally store our value and names for it - only increment the value count if something actually changed in the DB
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue, cell.getImmutableImportHeading().replace,cell.getImmutableImportHeading().replace);
                } else if (clearData) { // only kicks in if the cell is blank
                    // EFC extracted out of value service, cleaner out here
                    final List<Value> existingValues = ValueService.findForNames(namesForValue);
                    if (existingValues.size() == 1) {
                        existingValues.get(0).delete();
                    }
                }
                // now attribute, not allowing attributes and peers to mix
            } else if (cell.getImmutableImportHeading().indexForAttribute >= 0 && cell.getImmutableImportHeading().attribute != null
                    && cell.getLineValue().length() > 0) {
                String attribute = cell.getImmutableImportHeading().attribute;
                if (cell.getImmutableImportHeading().attributeColumn >= 0) {//attribute name refers to the value in another column - so find it
                    attribute = cells.get(cell.getImmutableImportHeading().attributeColumn).getLineValue();
                }
                // handle attribute was here, we no longer require creating the line name so it can in lined be cut down a lot
                ImportCellWithHeading identityCell = cells.get(cell.getImmutableImportHeading().indexForAttribute); // get our source cell
                if (identityCell.getLineNames() == null) {
                    linesRejected.computeIfAbsent(importLine, t -> new ArrayList<>()).add("No name for attribute " + cell.getImmutableImportHeading().attribute + " of " + cell.getImmutableImportHeading().heading);
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
                            // EFC note - need to check on definition
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


    /* namesFound is a cache. Then the heading we care about then the list of all headings.
     * */

    private static void resolveLineNameParentsAndChildForCell(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache,
                                                              ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int recursionLevel) throws Exception {
        /*
             Imagine 3 headings. Town, street, whether it's pedestrianized. Pedestrianized parent of street. Town parent of street local.
             the key here is that the resolveLineNameParentsAndChildForCell has to resolve line Name for both of them - if it's called on "Pedestrianized parent of street" first
             both pedestrianized (ok) and street (NOT ok!) will have their line names resolved
             whereas resolving "Town parent of street local" first means that the street should be correct by the time we resolve "Pedestrianized parent of street".
             essentially sort local names need to be sorted first.

             The point is that the name is attached to a call, it is only resolved once, local gets priority, resolve it first

             EFC note August 2018 after modifying WFC code : the old method was just to run through cells that have isLocal first but that could be tripped up
             by a local in a local which, while not recommended, is supported. The key here is that locals should be resolved in order starting at the top.

             localParentIndexes enables this, recurse up to the top and go down meaning this cell will be safe to resolve if there are local names involved.

              */
        recursionLevel++;
        for (int localParentIndex : cellWithHeading.getImmutableImportHeading().localParentIndexes) {
            ImportCellWithHeading parentHeading = cells.get(localParentIndex);
            if (parentHeading.getLineNames() == null || parentHeading.getLineNames().size() == 0) { // so it has not been resolved yet!
                if (recursionLevel == 8) { // arbitrary but not unreasonable
                    throw new Exception("recursion loop on heading " + cellWithHeading.getImmutableImportHeading().heading);
                }
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, parentHeading, cells, attributeNames, recursionLevel);
            }
        }
        // in simple terms if a line cell value refers to a name it can now refer to a set of names
        // to make a set parent of more than one thing e.g. parent of set a, set b, set c
        // nothing in the heading has changed except the split char but we need to detect it here
        // split before checking for quotes etc. IF THE SPLIT CHAR IS IN QUOTES WE DON'T CURRENTLY SUPPORT THAT!
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
                    parent.addChildWillBePersisted(child, azquoMemoryDBConnection);
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
                            if (!cellWithHeading.getImmutableImportHeading().provisional || !alreadyCategorised(parent, childCellName)) {//checking whether the child is already in the set under another
                                parent.addChildWillBePersisted(childCellName, azquoMemoryDBConnection);
                            }
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
                                    if (cellWithHeading.getImmutableImportHeading().provisional) {
                                        needsAdding = false;
                                        break;
                                    }
                                    childCellParent.removeFromChildrenWillBePersisted(childCellName, azquoMemoryDBConnection);
                                }
                            }
                            // having hopefully sorted a new name or exclusive add the child
                            if (needsAdding) {
                                parent.addChildWillBePersisted(childCellName, azquoMemoryDBConnection);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean alreadyCategorised(Name parent, Name child) {
        //this routine is for the specific case where a categorisation is only to be done if the child is not already categorised
        //when importing Ed Broking premium data the premiums need to be categorised, but the information available is such that the categorisation is sometimes false
        //so the categorisation must not override other categorisations

        for (Name grandparent : parent.getParents()) {
            if (grandparent.getDefaultDisplayName() != null && child.getAttribute(grandparent.getDefaultDisplayName()) != null) {
                return true;
            }
        }
        return false;
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