package com.azquo.memorydb.service;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.StringUtils;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracted from NameService by edward on 18/10/16.
 * <p>
 * Parsing stuff in here, low level name functions in NameService, filter functions in NameFilterFunctions and Name stack
 * operators are now dealt with in NameStackOperators.
 */
public class NameQueryParser {

    private static Runtime runtime = Runtime.getRuntime();

    private static final Logger logger = Logger.getLogger(NameQueryParser.class);

    // for deduplicate, inspect search and definition.
    private static AtomicInteger parseQueryCount = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula) throws Exception {
        parseQueryCount.incrementAndGet();
        return parseQuery(azquoMemoryDBConnection, setFormula, StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, null, false);
    }

    private static AtomicInteger parseQuery2Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, boolean returnReadOnlyCollection) throws Exception {
        parseQuery2Count.incrementAndGet();
        return parseQuery(azquoMemoryDBConnection, setFormula, attributeNames, returnReadOnlyCollection, null);
    }

    private static AtomicInteger parseQuery3Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, boolean returnReadOnlyCollection, List<List<String>> contextSource) throws Exception {
        parseQuery3Count.incrementAndGet();
        return parseQuery(azquoMemoryDBConnection, setFormula, attributeNames, null, returnReadOnlyCollection, contextSource);
    }

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, Collection<Name> toReturn, boolean returnReadOnlyCollection) throws Exception {
        return parseQuery(azquoMemoryDBConnection, setFormula, attributeNames, toReturn, returnReadOnlyCollection, null);
    }

    /* todo : sort exceptions?
    todo - cache option in here
    now uses NameSetList to move connections of names around and only copy them as necessary. Has made the logic a little more complex
    in places but performance should be better and garbage reduced
    todo - check logic regarding toReturn makes sense.
    */
    private static AtomicInteger parseQuery4Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, Collection<Name> toReturn, boolean returnReadOnlyCollection, List<List<String>> contextSource) throws Exception {
        parseQuery4Count.incrementAndGet();
        long track = System.currentTimeMillis();
        String formulaCopy = setFormula;
        int mb = 1024 * 1024;
        long startUsed = (runtime.totalMemory() - runtime.freeMemory()) / mb;

        /*
         * This routine now amended to allow for union (+) and intersection (*) of sets.
         *
         * This entails first sorting out the names in quotes (which may contain the reserved characters),
         * starting from the end (there may be "name","parent" in the list)
         *
         * These will be replaced by !<id>   e.g. !1234
         * */
        if (toReturn == null) {
            toReturn = new ArrayList<>(); // default to this collection type
        } else {
            // if a collection to return was passed we can't return a read only collection, we'll be adding to what was passed
            returnReadOnlyCollection = false;
        }
        if (setFormula.length() == 0) {
            return toReturn;
        }
        List<String> languages = new ArrayList<>(attributeNames);
        int languagePos = setFormula.indexOf(StringLiterals.languageIndicator);
        if (languagePos > 0) {
            int namePos = setFormula.indexOf(StringLiterals.QUOTE);
            if (namePos < 0 || namePos > languagePos) {
                //change the language
                languages.clear();
                languages.add(setFormula.substring(0, languagePos));
                setFormula = setFormula.substring(languagePos + 2);
            }
        }
        //todo - find a better way of using 'parseQuery` for other operations
        if (setFormula.toLowerCase().startsWith("edit:")) {
            return NameEditFunctions.handleEdit(azquoMemoryDBConnection, setFormula.substring(5).trim(), languages);
        }
        // filterby is a special case, I'm only allowing one of them. e.g. Claim children * `bb12345` filterby "(`line count` > 1)"
        String filterByCriteria = null;
        if (setFormula.toLowerCase().contains(StringLiterals.FILTERBY)) {
            int filterByIndex = setFormula.toLowerCase().indexOf(StringLiterals.FILTERBY);
            int firstQuote = setFormula.indexOf("\"", filterByIndex);
            int secondQuote = setFormula.indexOf("\"", firstQuote + 1);
            if (firstQuote < 0 || secondQuote < 0) {
                throw new Exception("filterby must be followed by criteria in quotes" + setFormula);
            }
            if (setFormula.toLowerCase().indexOf(StringLiterals.FILTERBY, firstQuote) != -1) {
                throw new Exception("cannot have more than one filterby " + setFormula);
            }
            if (contextSource == null) {
                throw new Exception("cannot use filterby without context source - query must be matched to a region " + setFormula);
            }

            filterByCriteria = setFormula.substring(firstQuote + 1, secondQuote);
            setFormula = setFormula.substring(0, firstQuote) + setFormula.substring(secondQuote + 1); // zap the criteria - it will mess up the parsing below
        }

        List<NameSetList> nameStack = new ArrayList<>(); // now use the container object, means we only create new collections at the last minute as required
        List<String> formulaStrings = new ArrayList<>();
        List<String> nameStrings = new ArrayList<>();
        List<String> attributeStrings = new ArrayList<>(); // attribute names is taken. Perhaps need to think about function parameter names
        Name possibleName = NameService.findByName(azquoMemoryDBConnection, setFormula, languages);
        if (possibleName != null) {
            toReturn.add(possibleName);
            long time = (System.currentTimeMillis() - track);
            long heapIncrease = ((runtime.totalMemory() - runtime.freeMemory()) / mb) - startUsed;
            if (heapIncrease > 50) {
                System.out.println("Parse query : " + formulaCopy + " heap increase : " + heapIncrease + "MB ###########");
            }
            if (time > 50) {
                System.out.println("Parse query : " + formulaCopy + " took : " + time + "ms");
            }
            return toReturn;
        }
        boolean sorted = false;
        setFormula = replaceAttributes(azquoMemoryDBConnection, setFormula);//replaces quoted attributes as constants
        setFormula = StringUtils.prepareStatement(setFormula, nameStrings, attributeStrings, formulaStrings);
        if (!setFormula.contains(" + ") && setFormula.toLowerCase().endsWith(" " + StringLiterals.SORTED)) {
            sorted = true;
            setFormula = setFormula.substring(0, setFormula.length() - StringLiterals.SORTED.length() - 1);
        }
        List<Name> referencedNames;
        try {
            referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, languages);
        } catch (Exception e) {
            // EFC commenting 13/01/2020, this really should never have been in here, need to test to make sure this doesn't break stuff
            //if (setFormula.toLowerCase().equals(StringLiterals.NAMEMARKER + "00 children")) return new ArrayList<>();
               /* sometimes excel formulae generate dependent sets that do not exist (e.g. `2012 Transactions`
            in that case it is better to return a null rather than an exception as temporary names may still be set incorrectly
            if (setFormula.contains(StringLiterals.AS)) {
                //clear the 'as' set and exit gracefully
                Name targetName = NameService.findNameAndAttribute(azquoMemoryDBConnection, nameStrings.get(nameStrings.size() - 1), attributeNames);
                if (targetName != null) {
                    targetName.setChildrenWillBePersisted(new ArrayList<>());
                    return new ArrayList<>();
                }
            }*/
            throw new Exception("could not parse " + formulaCopy + "(error: " + e.getMessage() + ")");
        }
        setFormula = setFormula.replace(StringLiterals.ASGLOBAL, StringLiterals.ASGLOBALSYMBOL + "").replace(StringLiterals.FILTERBY, StringLiterals.FILTERBYSYMBOL + "");
        setFormula = setFormula.replace(StringLiterals.AS, StringLiterals.ASSYMBOL + "").replace(StringLiterals.CONTAINS, StringLiterals.CONTAINSSYMBOL + "");

        setFormula = StringUtils.shuntingYardAlgorithm(setFormula);
        Pattern p = Pattern.compile("[\\+\\-\\*/" + StringLiterals.NAMEMARKER + StringLiterals.ASSYMBOL + StringLiterals.ASGLOBALSYMBOL + StringLiterals.CONTAINSSYMBOL + StringLiterals.FILTERBYSYMBOL + "]");//recognises + - * / NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line
        String resetDefs = null;
        boolean global = false;
        logger.debug("Set formula after SYA " + setFormula);
        int pos = 0;
        // could we get rid of stack count and just use the ArrayList's size?
        int stackCount = 0;
        //int stringCount = 0;
        // now to act on the formulae which has been converted to Reverse Polish, hence stack based parsing and no brackets etc.
        // NOTE THAT THE SHUNTING YARD ALGORITHM HERE LEAVES FUNCTIONS AT THE START (e.g. Attributeset)
        // now to act on the formulae which has been converted to Reverse Polish, hence stack based parsing and no brackets etc.
        while (pos < setFormula.length()) {
            Matcher m = p.matcher(setFormula.substring(pos + 2));
            // HANDLE SET INTERSECTIONS UNIONS AND EXCLUSIONS (* + - )
            char op = setFormula.charAt(pos);
            int nextTerm = setFormula.length() + 1;
            if (m.find()) {
                nextTerm = m.start() + pos + 2;
                // PROBLEM!   The name found may have been following 'from ' or 'to ' (e.g. dates contain '-' so need to be encapsulated in quotes)
                // need to check for this....
                while (nextTerm < setFormula.length() && (StringUtils.precededBy(setFormula, StringLiterals.ASGLOBAL, nextTerm)
                        || StringUtils.precededBy(setFormula, StringLiterals.AS, nextTerm)
                        || StringUtils.precededBy(setFormula, StringLiterals.TO, nextTerm)
                        || StringUtils.precededBy(setFormula, StringLiterals.FROM, nextTerm))) {
                    int startPos = nextTerm + 1;
                    nextTerm = setFormula.length() + 1;
                    m = p.matcher(setFormula.substring(startPos));
                    if (m.find()) {
                        nextTerm = m.start() + startPos;
                    }
                }
            }
            if (op == StringLiterals.NAMEMARKER) { // then a straight name children from to etc. Resolve in interpretSetTerm
                stackCount++;
                // now returns a custom little object that hods a list a set and whether it's immutable
                nameStack.add(interpretSetTerm(null, setFormula.substring(pos, nextTerm - 1), formulaStrings, referencedNames, attributeStrings, azquoMemoryDBConnection, attributeNames, setFormula));
            } else if (op == StringLiterals.FILTERBYSYMBOL) { // filter by is unique - it's not an operator which takes two name sets, it simply applies a condition to the set before sp the stack can be one for this operator
                NameStackOperators.filterBy(nameStack, filterByCriteria, azquoMemoryDBConnection, contextSource, languages);
            } else if (stackCount-- < 2) {
                throw new Exception("not understood:  " + formulaCopy);
            } else if (op == '*') { // * meaning intersection here . . .
                NameStackOperators.setIntersection(nameStack, stackCount);
            } else if (op == '/') {
                NameStackOperators.childParentsSetIntersection(azquoMemoryDBConnection, nameStack, stackCount);
            } else if (op == '-') {
                NameStackOperators.removeFromSet(nameStack, stackCount);
            } else if (op == '+') {
                NameStackOperators.addSets(nameStack, stackCount);
            } else if (op == StringLiterals.CONTAINSSYMBOL) {
                //swap the last two elements on the stack, then use op = '-'
                NameSetList topList = nameStack.get(stackCount);
                nameStack.set(stackCount, nameStack.get(stackCount - 1));
                nameStack.set(stackCount - 1, topList);
                NameStackOperators.removeFromSet(nameStack, stackCount);
            } else if (op == StringLiterals.ASSYMBOL) {
                Name totalName = nameStack.get(stackCount).getAsCollection().iterator().next();// get(0) relies on list, this works on a collection
                if (totalName.getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME) != null) {
                    resetDefs = totalName.getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME).toLowerCase();
                }
                NameStackOperators.assignSetAsName(azquoMemoryDBConnection, attributeNames, nameStack, stackCount, false);
            } else if (op == StringLiterals.ASGLOBALSYMBOL) {
                Name totalName = nameStack.get(stackCount).getAsCollection().iterator().next();// get(0) relies on list, this works on a collection
                if (totalName.getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME) != null) {
                    resetDefs = totalName.getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME).toLowerCase();
                }
                NameStackOperators.assignSetAsName(azquoMemoryDBConnection, attributeNames, nameStack, stackCount, true);
                global = true;
            }
            if (op != StringLiterals.NAMEMARKER && nextTerm > setFormula.length() && pos < nextTerm - 3) {
                //there's still more stuff to understand!  Having created a set, we may now wish to operate on that set
                int childrenPos = setFormula.substring(pos).indexOf("children");
                if (childrenPos > 0) {

                    nameStack.set(0, interpretSetTerm(nameStack.get(0), setFormula.substring(pos + 1, pos + childrenPos), formulaStrings, referencedNames, attributeStrings, azquoMemoryDBConnection, attributeNames, setFormula));
                    nameStack.set(0, interpretSetTerm(nameStack.get(0), setFormula.substring(childrenPos + pos), formulaStrings, referencedNames, attributeStrings, azquoMemoryDBConnection, attributeNames, setFormula));
                } else {
                    nameStack.set(0, interpretSetTerm(nameStack.get(0), setFormula.substring(pos + 1), formulaStrings, referencedNames, attributeStrings, azquoMemoryDBConnection, attributeNames, setFormula));
                }
            }
            pos = nextTerm;
        }
        if (sorted) {
            if (nameStack.get(0) == null || nameStack.get(0).list == null || !nameStack.get(0).mutable) { // then force to a mutable list, don't see that we have a choice
                nameStack.set(0, new NameSetList(null, new ArrayList<>(nameStack.get(0).getAsCollection()), true));
            }
            nameStack.get(0).list.sort(NameService.defaultLanguageCaseInsensitiveNameComparator);

        }

        long heapIncreaseBeforeCopyToArray = ((runtime.totalMemory() - runtime.freeMemory()) / mb) - startUsed;
/*
        if (azquoMemoryDBConnection.getReadPermissions().size() > 0) {
            for (Name possible : nameStack.get(0).getAsCollection()) {
                if (possible == null || isAllowed(possible, azquoMemoryDBConnection.getReadPermissions())) {
                    toReturn.add(possible);
                }
            }
        } else { // add all can be inefficient as it does a .toArray but the big saving here can be if I can get a hint that the collection won't be modified, then I can just return what's on the namestack

            // note the first entry on the name stack might actually be mutable anyway and hence we could just return it IF a toReturn collection wasn't passed
  */
        if (returnReadOnlyCollection) {
            toReturn = nameStack.get(0).getAsCollection();
        } else {
            toReturn.addAll(nameStack.get(0).getAsCollection());
        }
        //   }
        long time = (System.currentTimeMillis() - track);
        long heapIncrease = ((runtime.totalMemory() - runtime.freeMemory()) / mb) - startUsed;
        if (heapIncrease > 50) {
            System.out.println("Parse query : " + formulaCopy + " heap increase : " + heapIncrease + "MB. Before copying to array : " + heapIncreaseBeforeCopyToArray + " ########### size : " + toReturn.size());
        }
        if (time > 50) {
            System.out.println("Parse query : " + formulaCopy + " took : " + time + "ms size : " + toReturn.size());
        }
        // check if this is necessary? Refactor?
        if (resetDefs != null) {
            //currently recalculates ALL definitions regardless of whether they contain the changed set.  Could speed this by looking for expressions that contain the changed set name
            Collection<Name> defNames = azquoMemoryDBConnection.getAzquoMemoryDBIndex().namesForAttribute(StringLiterals.DEFINITION);
            if (defNames != null) {
                for (Name defName : defNames) {
                    String definition = defName.getAttribute(StringLiterals.DEFINITION);
                    if (definition != null && definition.toLowerCase().contains(resetDefs)) {
                        if (!global && attributeNames.size() > 1) {
                            String userEmail = attributeNames.get(0);
                            if (defName.getAttribute(userEmail) == null) { // there is no specific set for this user yet, need to do something
                                List<String> localLanguages = new ArrayList<>();
                                localLanguages.add(userEmail);
                                Name userSpecificSet = NameService.findByName(azquoMemoryDBConnection, defName.getDefaultDisplayName(), localLanguages);
                                if (userSpecificSet == null) {
                                    azquoMemoryDBConnection.setProvenance(userEmail, "set assigned", "", "query");
                                    userSpecificSet = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance()); // a basic copy of the set
                                    //userSpecificSet.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, userEmail + totalName.getDefaultDisplayName()); // GOing to set the default display name as bits of the suystem really don't like it not being there
                                    userSpecificSet.setAttributeWillBePersisted(userEmail, defName.getDefaultDisplayName(), azquoMemoryDBConnection); // set the name (usually default_display_name) but for the "user email" attribute
                                    defName.addChildWillBePersisted(userSpecificSet, azquoMemoryDBConnection);
                                }
                                defName = userSpecificSet; // switch the new one in, it will be used as normal
                            }
                        }
                        Collection<Name> defSet = parseQuery(azquoMemoryDBConnection, definition, attributeNames, true); // can be read only
                        if (defSet != null) {
                            defName.setChildrenWillBePersisted(defSet, azquoMemoryDBConnection);
                        }
                    }
                }
            }
        }
        return toReturn;
    }

    // Managed to convert to returning NameSetList, the key being using fast collection operations where possible depending on what has been passed
    // not entirely happy with this being able to be passed a name set list. It to allow more complex things such as calling children on a previously interpreted set term. todo - make more elegant?
    // needs AzquoMemoryDBConnection for its indexes for the attribute set criteria. Boring but can't see a way around that.
    private static AtomicInteger interpretSetTermCount = new AtomicInteger(0);

    private static NameSetList interpretSetTerm(NameSetList namesFound, String setTerm, List<String> strings, List<Name> referencedNames, List<String> attributeStrings, AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> languages, String setFormula) throws Exception {
        interpretSetTermCount.incrementAndGet();
        //System.out.println("interpret set term . . ." + setTerm);
        final String levelString = StringUtils.getInstruction(setTerm, StringLiterals.LEVEL);
        String fromString = StringUtils.getInstruction(setTerm, StringLiterals.FROM);
        String offsetString = StringUtils.getInstruction(setTerm, StringLiterals.OFFSET);
        String backstepString = StringUtils.getInstruction(setTerm, StringLiterals.BACKSTEP);
        String parentsString = StringUtils.getInstruction(setTerm, StringLiterals.PARENTS);
        String childrenString = StringUtils.getInstruction(setTerm, StringLiterals.CHILDREN);
        final String sorted = StringUtils.getInstruction(setTerm, StringLiterals.SORTED);
        String toString = StringUtils.getInstruction(setTerm, StringLiterals.TO);
        String countString = StringUtils.getInstruction(setTerm, StringLiterals.COUNT);
        String selectString = StringUtils.getInstruction(setTerm, StringLiterals.SELECT);
        // now attribute set goes in here
        final String attributeSetString = StringUtils.getInstruction(setTerm, StringLiterals.ATTRIBUTESET);

        int wherePos = setTerm.toLowerCase().indexOf(StringLiterals.WHERE.toLowerCase());
        String whereString = null;
        if (wherePos >= 0) {
            if ((wherePos + 6) < setTerm.length()){ // stop possible index out of bounds issue
                whereString = setTerm.substring(wherePos + 6);//the rest of the string???   maybe need 'group by' in future
            } else {
                System.out.println("Dodgy where criteria : " + setTerm);
            }
        }
        if (levelString != null) {
            childrenString = "true";
        }
        if (namesFound == null || namesFound.list.size() == 1) {
            Name name;

            if (namesFound == null) {
                String nameString = setTerm;
                if (setTerm.indexOf(' ') > 0) {
                    nameString = setTerm.substring(0, setTerm.indexOf(' ')).trim();
                }
                name = getNameFromListAndMarker(nameString, referencedNames);
                if (name == null) {
                    throw new Exception(" not understood: " + nameString);
                }
            } else {
                name = namesFound.list.get(0);
            }
            if (childrenString == null && fromString == null && toString == null && countString == null) {
                List<Name> singleName = new ArrayList<>();
                singleName.add(name);
                namesFound = new NameSetList(null, singleName, true);// mutable single item list
            } else {
                namesFound = NameService.findChildrenAtLevel(name, levelString); // reassign names from the find children clause
                if (languages.size() > 1) {//need to check for a list of temporary names
                    List<Name> replacementNames = new ArrayList<>();
                    for (Name child : namesFound.getAsCollection()) {
                        if (child != null) {
                            if (child.getChildren().size() > 0 && child.getChildren().iterator().next().getDefaultDisplayName() == null) {//we have a temporary name
                                Name localChild = NameService.findByName(azquoMemoryDBConnection, child.getDefaultDisplayName(), languages);
                                if (localChild != null) {
                                    replacementNames.add(localChild);
                                }
                            } else {
                                break;
                            }
                        }
                    }
                    if (replacementNames.size() > 0) {//assuming ALL names are temporary currently
                        namesFound = new NameSetList(null, replacementNames, true);

                    }
                }
            }
        }
        if (whereString != null) {
            // will only work if it's a list internally
            namesFound = NameFilterFunctions.filter(namesFound, whereString, strings, attributeStrings);
        }
        // could parents and select be more efficient?
        if (parentsString != null) {
            // make mutable if it isn't
            if (!namesFound.mutable) {
                namesFound = new NameSetList(namesFound);
            }
            // removeif should have little performance hit
            namesFound.getAsCollection().removeIf(check -> !check.hasChildren());
        }
        if (selectString != null) {
            String toFind = strings.get(Integer.parseInt(selectString.substring(1, 3))).toLowerCase();
            // make mutable if not
            if (!namesFound.mutable) {
                namesFound = new NameSetList(namesFound);
            }
            // reversing logic from before to use iterator remove to get rid of non relevant names
            // switching to removeif . . .this null checking botheres me a little - should either the name or it's DDN be null?
            namesFound.getAsCollection().removeIf(check -> check == null || check.getDefaultDisplayName() == null
                    || !check.getDefaultDisplayName().toLowerCase().contains(toFind));
        }
        // I believe this is the correct place to put this, after resolving the set but before sorting/ordering etc. This will of course likely totally transform the set
        // todo - attributeSetString can be blank so this will index out of bounds, sort that
        if (attributeSetString != null) {
            String resolvedString = strings.get(Integer.parseInt(attributeSetString.substring(1, 3))).toLowerCase();
            namesFound = attributeSet(azquoMemoryDBConnection, resolvedString, namesFound);
        }

        if (sorted != null) { // I guess force list
            if (namesFound.list == null || !namesFound.mutable) { // then force to a mutable list, don't see that we have a choice
                namesFound = new NameSetList(null, new ArrayList<>(namesFound.getAsCollection()), true);
            }
            namesFound.list.sort(NameService.defaultLanguageCaseInsensitiveNameComparator);
        }
        // do from/to/count at the end. It was above for no good reason I can see
        if (fromString == null) fromString = "";
        if (offsetString == null) offsetString = "";
        if (backstepString != null) offsetString = "-" + backstepString;
        if (toString == null) toString = "";
        if (countString == null) countString = "";
        if (fromString.length() > 0 || toString.length() > 0 || countString.length() > 0) {
            if (namesFound.list != null) { // yeah I know some say this is not best practice but hey ho
                namesFound = NameFilterFunctions.constrainNameListFromToCount(namesFound, fromString, toString, countString, offsetString, referencedNames);
            } else {
                System.out.println("can't from/to/count a non-list, " + setFormula);
            }
        }
        return namesFound;
    }

    // we replace the names with markers for parsing. Then we need to resolve them later, here is where the exception will be thrown. Should be NameNotFoundException?

    private static AtomicInteger getNameListFromStringListCount = new AtomicInteger(0);

    static List<Name> getNameListFromStringList(List<String> nameStrings, AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> attributeNames) throws Exception {
        getNameListFromStringListCount.incrementAndGet();
        List<Name> referencedNames = new ArrayList<>(nameStrings.size());
        for (String nameString : nameStrings) {
            Name toAdd = NameService.findNameAndAttribute(azquoMemoryDBConnection, nameString, attributeNames);
            // a hack for pivot filters, should this be here?
            if (toAdd == null && nameString.startsWith("az_")) {
                //to handle pivot filters...
                toAdd = NameService.findNameAndAttribute(azquoMemoryDBConnection, nameString.substring(3), attributeNames);
            }
            // typically used with as, a bunch of sets may be created as part of a report but we then sap them when the report has finished loading
            nameString = nameString.replace(StringLiterals.QUOTE + "", "");
            if (toAdd == null && nameString.toUpperCase().endsWith("(TEMPORARY)")) { // hacky? Factor the literal?
                Name temporaryNames = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, StringLiterals.TEMPORARYNAMES, null, false); // make if it's not there, I guess no harm in it hanging around
                // create the temporary name to be used later in an "as" no doubt
                toAdd = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, nameString, temporaryNames, true);
            }
            if (toAdd == null) {
                throw new Exception("Cannot resolve reference to a name " + nameString);
            }
            referencedNames.add(toAdd);
        }
        return referencedNames;
    }

    // note : in default language!

    // when parsing expressions we replace names with markers and jam them on a list. The expression is manipulated before being executed. On execution the referenced names need to be read from a list.

    private static AtomicInteger getNameFromListAndMarkerCount = new AtomicInteger(0);

    static Name getNameFromListAndMarker(String nameMarker, List<Name> nameList) throws Exception {
        getNameFromListAndMarkerCount.incrementAndGet();
        if (nameMarker.charAt(0) == StringLiterals.NAMEMARKER) {
            try {
                int nameNumber = Integer.parseInt(nameMarker.substring(1).trim());
                return nameList.get(nameNumber);
            } catch (Exception e) {
                throw new Exception(nameMarker + " is not a valid name");
            }
        } else {
            throw new Exception(nameMarker + " is not a valid name");
        }
    }

    public static String replaceAttributes(AzquoMemoryDBConnection azquoMemoryDBConnection, String statement) throws Exception {
        StringBuilder modifiedStatement = new StringBuilder();
        Pattern p = Pattern.compile("" + StringLiterals.QUOTE + "[^" + StringLiterals.QUOTE + "]*" + StringLiterals.QUOTE + "\\." + StringLiterals.QUOTE + "[^" + StringLiterals.QUOTE + "]*" + StringLiterals.QUOTE); //`name`.`attribute`
        Matcher matcher = p.matcher(statement);
        int lastEnd = 0;
        while (matcher.find()) {
            if (modifiedStatement.length() == 0) {
                modifiedStatement.append(statement, 0, matcher.start());
            } else {
                modifiedStatement.append(statement, lastEnd, matcher.start());
            }
            lastEnd = matcher.end();
            String attribute = statement.substring(matcher.start(), matcher.end());
            int nameEnd = statement.indexOf(StringLiterals.QUOTE, matcher.start() + 1);
            String nameString = statement.substring(matcher.start() + 1, nameEnd);
            String attributeString = statement.substring(nameEnd + 3, matcher.end() - 1);
            Name name = NameService.findByName(azquoMemoryDBConnection, nameString);
            if (name != null) {
                String attVal = name.getAttribute(attributeString);
                if (attVal != null) {
                    attribute = attVal;//replaces with a name (an element of a string
                }
            }
            modifiedStatement.append(attribute);
        }
        modifiedStatement.append(statement.substring(lastEnd));

        return modifiedStatement.toString();
    }
    // edd : I wonder a little about this but will leave it for the mo

    static int parseInt(final String string, int existing) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return existing;
        }
    }

    // used to simply match on a string literal, now matches a given attribute across the database to a name set.
    // the initial use was the attribute unsubscribed which had a date in there and crossing that with a name query on dates
    private static NameSetList attributeSet(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeName, NameSetList toConvert) {
        String setAttribute = attributeName;
        int dotPos = attributeName.indexOf(".");
        if (dotPos > 0) {
            setAttribute = attributeName.substring(dotPos + 1);
            attributeName = attributeName.substring(0, dotPos);
        }
        Set<Name> result = HashObjSets.newMutableSet();
        for (Name source : toConvert.getAsCollection()) {
            // some collection wrapping in here, could be made more efficient if necessary
            /*if the source has the attribute that it's looking for from the set, then find all others using that attribute
            e.g.   If you are looking for sales witha promotion code and the source is a list of promotions, each with a code, then first try the promotion code
            ...but remember to remove the source set from the result.

             */
            if (source.getAttribute(setAttribute) != null) {
                result.addAll(azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttribute(attributeName, source.getAttribute(setAttribute)));
            }
            result.addAll(azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttribute(attributeName, source.getDefaultDisplayName()));
        }
        result.removeAll(toConvert.getAsCollection());
        return new NameSetList(result, null, true);
    }

    public static void printFunctionCountStats() {
        System.out.println("######### NAME QUERY PARSER FUNCTION COUNTS");
        System.out.println("interpretSetTermCount\t\t\t\t\t\t\t\t" + interpretSetTermCount.get());
        System.out.println("parseQueryCount\t\t\t\t\t\t\t\t" + parseQueryCount.get());
        System.out.println("parseQuery2Count\t\t\t\t\t\t\t\t" + parseQuery2Count.get());
        System.out.println("parseQuery3Count\t\t\t\t\t\t\t\t" + parseQuery3Count.get());
        System.out.println("getNameListFromStringListCount\t\t\t\t\t\t\t\t" + getNameListFromStringListCount.get());
        System.out.println("getNameFromListAndMarkerCount\t\t\t\t\t\t\t\t" + getNameFromListAndMarkerCount.get());
    }

    public static void clearFunctionCountStats() {
        interpretSetTermCount.set(0);
        parseQueryCount.set(0);
        parseQuery2Count.set(0);
        parseQuery3Count.set(0);
        getNameListFromStringListCount.set(0);
        getNameFromListAndMarkerCount.set(0);
    }
}