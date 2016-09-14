package com.azquo.dataimport;

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
 *
 * Created by EFC to try to improve speed through multi threading.
 * The basic file parsing is single threaded but since this can start while later lines are being read I don't think this is a problem.
 * That is to say on a large file the threads will start to stack up fairly quickly
 * Adapted to Callable from Runnable - I like the assurances this gives for memory synchronisation
 */
public class BatchImporter implements Callable<Void> {

    private final AzquoMemoryDBConnection azquoMemoryDBConnection;
    private final AtomicInteger valueTracker;
    private int lineNo;
    private final List<List<ImportCellWithHeading>> dataToLoad;
    private final Map<String, Name> namesFoundCache;
    private final List<String> attributeNames;

    BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valueTracker, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) {
        this.azquoMemoryDBConnection = azquoMemoryDBConnection;
        this.valueTracker = valueTracker;
        this.dataToLoad = dataToLoad;
        this.namesFoundCache = namesFoundCache;
        this.attributeNames = attributeNames;
        this.lineNo = lineNo;
    }

    @Override
    public Void call() throws Exception {
        long trigger = 10;
        Long time = System.currentTimeMillis();
        for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
            /* skip any line that has a blank in the first column unless the first column had no header
               of course if the first column has no header and then the second has data but not on this line then it would get loaded
               happy for the check to remain in here - more stuff for the multi threaded bit */
            ImportCellWithHeading first = lineToLoad.get(0);
            if (first.getLineValue().length() > 0 || first.getImmutableImportHeading().heading == null || first.getImmutableImportHeading().compositionPattern != null) {
                if (getCompositeValuesCheckOnlyAndExisting(azquoMemoryDBConnection, lineToLoad, lineNo, attributeNames)) {
                    try {
                        // valueTracker simply the number of values imported
                        valueTracker.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, lineNo));
                    } catch (Exception e) {
                        azquoMemoryDBConnection.addToUserLogNoException(e.getMessage(), true);
                        throw e;
                    }
                    Long now = System.currentTimeMillis();
                    if (now - time > trigger) {
                        System.out.println("line no " + lineNo + " time = " + (now - time) + "ms");
                    }
                    time = now;
                }
            }
            lineNo++;
        }
        azquoMemoryDBConnection.addToUserLogNoException("Batch finishing : " + DecimalFormat.getInstance().format(lineNo) + " imported.", true);
        azquoMemoryDBConnection.addToUserLogNoException("Values Imported : " + DecimalFormat.getInstance().format(valueTracker), true);
        return null;
    }

    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values
    // now seems to support basic excel like string operations, left right and mid. Checking only and existing means "should we import the line at all" bases on these criteria

    private static boolean getCompositeValuesCheckOnlyAndExisting(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, int lineNo, List<String> languages) {
        int adjusted = 2;
        //loops in case there are multiple levels of dependencies
        while (adjusted > 1) {
            adjusted = 0;
            for (ImportCellWithHeading cell : cells) {
                if (cell.getImmutableImportHeading().compositionPattern != null) {
                    String result = cell.getImmutableImportHeading().compositionPattern;
                    // do line number first, I see no reason not to
                    String LINENO = "LINENO";
                    result = result.replace(LINENO, lineNo + "");
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            String expression = result.substring(headingMarker + 1, headingEnd);
                            String function = null;
                            int funcInt = 0;
                            int funcInt2 = 0;
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
                            ImportCellWithHeading compCell = findCellWithHeading(expression, cells);
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
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    if (result.toLowerCase().startsWith("calc")) {
                        result = result.substring(5);
                        Pattern p = Pattern.compile("[\\+\\-\\*\\/]");
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
                        adjusted++;
                    }
                }
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
                    // note I'm not going to check parentNames are not empty here, if someone put existing wihthout specifying child of then I think it's fair to say the line isn't valid
                    for (Name parent : cell.getImmutableImportHeading().parentNames) { // try to find any names from anywhere
                        if (!azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(languages, cell.getLineValue(), parent).isEmpty()) { // NOT empty, we found one!
                            return true; // no point carrying on
                        }
                    }
                    return false; // none found
                }
            }
        }
        return true;
    }

    /* Used to find component cells for composite values
    The extra logic aside simply from heading matching is the identifier flag (multiple attributes mean many headings with the same name)
    Or attribute being null (thus we don't care about identifier)
    */

    private static ImportCellWithHeading findCellWithHeading(String nameToFind, List<ImportCellWithHeading> importCellWithHeadings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        ImportCellWithHeading toReturn = null;
        for (ImportCellWithHeading importCellWithHeading : importCellWithHeadings) {
            ImmutableImportHeading heading = importCellWithHeading.getImmutableImportHeading();
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind))
                    && (heading.isAttributeSubject || heading.attribute == null || heading.isDate)) {
                if (heading.isAttributeSubject) {
                    return importCellWithHeading;
                }
                // ah I see the logic here. Identifier means it's the one to use, if not then there must be only one - if more than one are found then it's too ambiguous to work with.
                if (toReturn == null) {
                    toReturn = importCellWithHeading; // our possibility but don't return yet, need to check if there's more than one match
                } else {
                    return null;// found more than one possibility, return null now
                }
            }
        }
        return toReturn;
    }

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private static int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) throws Exception {
        int valueCount = 0;
        // initial pass to deal with defaults, dates and local parents
        //set defaults before dealing with local parent/child
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.getImmutableImportHeading().defaultValue != null && importCellWithHeading.getLineValue().length() == 0) {
                importCellWithHeading.setLineValue(importCellWithHeading.getImmutableImportHeading().defaultValue);
            }
        }
        for (ImportCellWithHeading importCellWithHeading : cells) {
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
            /* ok the gist seems to be that there's peers as defined in a context item in which case it's looking in context items and peers
            peers as defined in the context will look for other columns and in the context (those in the context having been pre prepared)
             */
            boolean peersOk = true;
            final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
            if (!cell.getImmutableImportHeading().contextPeersFromContext.isEmpty() || !cell.getImmutableImportHeading().contextPeerCellIndexes.isEmpty()) { // new criteria,this means there are context peers to deal with
                namesForValue.addAll(cell.getImmutableImportHeading().contextPeersFromContext);// start with the ones we have to hand, including the main name
                for (int peerCellIndex : cell.getImmutableImportHeading().contextPeerCellIndexes) {
                    // Clarified now - normally contextPeerCellIndexes refers to the line name but if it's "this" then it's the heading name. Inconsistent.
                    if (peerCellIndex == -1) {
                        namesForValue.add(cell.getImmutableImportHeading().name);
                    } else {
                        ImportCellWithHeading peerCell = cells.get(peerCellIndex);
                        if (peerCell.getLineName() != null) {
                            namesForValue.add(peerCell.getLineName());
                        } else {// fail - I plan to have resolved all possible line names by this point!
                            peersOk = false;
                            break;
                        }
                    }
                }
                // can't have more than one peers defined so if not from context check standard peers - peers from context is as it says, from context but not defined in there!
            } else if (!cell.getImmutableImportHeading().peerCellIndexes.isEmpty() || !cell.getImmutableImportHeading().peersFromContext.isEmpty()) {
                namesForValue.add(cell.getImmutableImportHeading().name); // the one at the top of this headingNo, the name with peers.
                // the old logic added the context peers straight in so I see no problem doing this here - this is what might be called inherited peers, from a col to the left.
                // On the col where context peers are defined normal peers should not be defined or used
                namesForValue.addAll(cell.getImmutableImportHeading().peersFromContext);
                // Ok I had to stick to indexes to get the cells
                for (Integer peerCellIndex : cell.getImmutableImportHeading().peerCellIndexes) { // go looking for non context peers
                    ImportCellWithHeading peerCell = cells.get(peerCellIndex); // get the cell
                    if (peerCell.getLineName() != null) {// under new logic the line name would have been created if possible so if it's not there fail
                        namesForValue.add(peerCell.getLineName());
                    } else {
                        peersOk = false;
                        break; // no point continuing gathering the names
                    }
                }
            }
            if (peersOk && !namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                String value = cell.getLineValue();
                if (!(cell.getImmutableImportHeading().blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    valueCount++;
                    // finally store our value and names for it
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                }
            }
            // ok that's the peer/value stuff done I think, now onto attributes
            if (cell.getImmutableImportHeading().indexForAttribute >= 0 && cell.getImmutableImportHeading().attribute != null
                    && cell.getLineValue().length() > 0) {
                // handle attribute was here, we no longer require creating the line name so it can in lined be cut down a lot
                ImportCellWithHeading identityCell = cells.get(cell.getImmutableImportHeading().indexForAttribute); // get our source cell
                if (identityCell.getLineName() != null) {
                    identityCell.getLineName().setAttributeWillBePersisted(cell.getImmutableImportHeading().attribute, cell.getLineValue().replace("\\\\t","\t").replace("\\\\n","\n"));
                }
                // else an error? If the line name couldn't be made in resolveLineNamesParentsChildren above there's nothing to be done about it
            }
            long now = System.currentTimeMillis();
            if (now - time > tooLong) {
                System.out.println(cell.getImmutableImportHeading().heading + " took " + (now - time) + "ms");
            }
            time = System.currentTimeMillis();
        }
        return valueCount;
    }

    // todo accessed outside, move them out

    private static LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static LocalDate isADate(String maybeDate) {
        LocalDate date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf4);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf3);
        if (date != null) return date;
        return tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
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
        So make a local reference to add quotes to or whatever
         */
        String cellWithHeadingLineValue = cellWithHeading.getLineValue();
        if (cellWithHeadingLineValue.contains(",") && !cellWithHeadingLineValue.contains(Name.QUOTE + "")) {//beware of treating commas in cells as set delimiters....
            cellWithHeadingLineValue = Name.QUOTE + cellWithHeadingLineValue + Name.QUOTE;
        }
        if (cellWithHeading.getLineName() == null) { // then create it, this will take care of the parents ("child of") while creating
            cellWithHeading.setLineName(includeInParents(azquoMemoryDBConnection, namesFoundCache, cellWithHeadingLineValue
                    , cellWithHeading.getImmutableImportHeading().parentNames, cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(cellWithHeading.getImmutableImportHeading(), attributeNames)));
        } else { // it existed (created below as a child name), sort parents if necessary
            for (Name parent : cellWithHeading.getImmutableImportHeading().parentNames) { // apparently there can be multiple child ofs, put the name for the line in the appropriate sets, pretty vanilla based off the parents set up
                parent.addChildWillBePersisted(cellWithHeading.getLineName());
            }
        }
        // ok that's "child of" (as in for names) done
        // now for "parent of", the, child of this line
        if (cellWithHeading.getImmutableImportHeading().indexForChild != -1) {
            ImportCellWithHeading childCell = cells.get(cellWithHeading.getImmutableImportHeading().indexForChild);
            if (childCell.getLineValue().length() == 0) {
                throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.getLineValue() + " " + cellWithHeading.getImmutableImportHeading().heading);
            }

            // ok got the child cell, need to find the child cell name to add it to this cell's children
            // I think here's it's trying to add to the cells name
            if (childCell.getLineName() == null) {
                childCell.setLineName(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childCell.getLineValue(), cellWithHeading.getLineName()
                        , cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(childCell.getImmutableImportHeading(), attributeNames)));
            } else { // check exclusive logic, only if the child cell line name exists then remove the child from parents if necessary - this replaces the old "remove from" funcitonality
                // the exclusiveSetToCheckAgainst means that if the child we're about to sort has a parent in this set we need to get rid of it before re adding the child to the new location
                Collection<Name> exclusiveSetToCheckAgainst = null;
                if ("".equals(cellWithHeading.getImmutableImportHeading().exclusive) && cellWithHeading.getImmutableImportHeading().parentNames.size() == 1) {
                    // blank exclusive clause, use child of if there's one (check all the way down. all children, necessary due due to composite option name1->name2->name3->etc
                    exclusiveSetToCheckAgainst = cellWithHeading.getImmutableImportHeading().parentNames.iterator().next().findAllChildren();
                } else if (cellWithHeading.getImmutableImportHeading().exclusive != null) { // exclusive is referring to a higher name
                    Name specifiedExclusiveSet = NameService.findByName(azquoMemoryDBConnection, cellWithHeading.getImmutableImportHeading().exclusive);
                    if (specifiedExclusiveSet != null) {
                        specifiedExclusiveSet.removeFromChildrenWillBePersisted(childCell.getLineName()); // if it's directly against the top it won't be caught by the set below, don't want to add to the set I'd have to make a new, potentially large, set
                        exclusiveSetToCheckAgainst = specifiedExclusiveSet.findAllChildren();
                    }
                }
                if (exclusiveSetToCheckAgainst != null) {
                    // essentially if we're saying that this heading is a category e.g. swimwear and we're about to add another name (a swimsuit one assumes) then go through other categories removing the swimsuit from them if it is in there
                    for (Name nameToRemoveFrom : childCell.getLineName().getParents()) {
                        if (exclusiveSetToCheckAgainst.contains(nameToRemoveFrom) && nameToRemoveFrom != cellWithHeading.getLineName()) { // the existing parent is one to be zapped by exclusive criteria and it's not the one we're about to add
                            nameToRemoveFrom.removeFromChildrenWillBePersisted(childCell.getLineName());
                        }
                    }
                }
            }
            cellWithHeading.getLineName().addChildWillBePersisted(childCell.getLineName());
        }
    }

    // I think the cache is purely a performance thing though it's used for a little logging later (total number of names inserted)

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