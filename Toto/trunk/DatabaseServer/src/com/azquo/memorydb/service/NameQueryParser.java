package com.azquo.memorydb.service;

import com.azquo.StringLiterals;
import com.azquo.dataimport.BatchImporter;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.spreadsheet.StringUtils;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracted from NameService by edward on 18/10/16.
 *
 * Parsing stuff in here, low level name functions in NameService
 *
 */
public class NameQueryParser {

    private static Runtime runtime = Runtime.getRuntime();

    private static final Logger logger = Logger.getLogger(NameQueryParser.class);

    // get names from a comma separated list. Well expressions describing names - only used for read and write permissions at the moment.
    public static List<Set<Name>> decodeString(AzquoMemoryDBConnection azquoMemoryDBConnection, String searchByNames, List<String> attributeNames) throws Exception {
        final List<Set<Name>> toReturn = new ArrayList<>();
        List<String> formulaStrings = new ArrayList<>();
        List<String> nameStrings = new ArrayList<>();
        List<String> attributeStrings = new ArrayList<>(); // attribute names is taken. Perhaps need to think about function parameter names
        searchByNames = StringUtils.prepareStatement(searchByNames, nameStrings, formulaStrings, attributeStrings);
        List<Name> referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
        // given that parse statement treats "," as an operator this should be ok.
        StringTokenizer st = new StringTokenizer(searchByNames, ",");
        while (st.hasMoreTokens()) {
            String nameName = st.nextToken().trim();
            final NameSetList nameSetList = interpretSetTerm(nameName, formulaStrings, referencedNames, attributeStrings);
            Set<Name> nameSet = nameSetList.set != null ? nameSetList.set : HashObjSets.newMutableSet(nameSetList.list); // just wrap if it's a list, should be fine. This object return type is for the query parser really
            toReturn.add(nameSet);
        }
        return toReturn;
    }

    // for deduplicate, inspect search and definition. This is teh same as below and
    private static AtomicInteger parseQueryCount = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula) throws Exception {
        parseQueryCount.incrementAndGet();
        List<String> langs = new ArrayList<>();
        langs.add(Constants.DEFAULT_DISPLAY_NAME);
        return parseQuery(azquoMemoryDBConnection, setFormula, langs, new ArrayList<>());
    }

    private static AtomicInteger parseQuery2Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames) throws Exception {
        parseQuery2Count.incrementAndGet();
        return parseQuery(azquoMemoryDBConnection, setFormula, attributeNames, null);
    }

    /* todo : sort exceptions?
    todo - cache option in here
    now uses NameSetList to move connections of names around and only copy them as necessary. Has made the logic a little more complex
    in places but performance should be better and garbage generation reduced*/
    private static AtomicInteger parseQuery3Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, Collection<Name> toReturn) throws Exception {
        parseQuery3Count.incrementAndGet();
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
        //todo - find a better way of using 'parseQuery` for other operations
        if (setFormula.toLowerCase().startsWith("edit:")) {
            return NameEditFunctions.handleEdit(azquoMemoryDBConnection, setFormula.substring(5).trim(), languages);
        }

        setFormula = StringUtils.prepareStatement(setFormula, nameStrings, attributeStrings, formulaStrings);
        List<Name> referencedNames;
        try {
            referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, languages);
        } catch (Exception e) {
            if (setFormula.toLowerCase().equals("!00 children")) return new ArrayList<>();// what is this??
            throw e;
        }
        setFormula = setFormula.replace(StringLiterals.AS, StringLiterals.ASSYMBOL + "");
        setFormula = StringUtils.shuntingYardAlgorithm(setFormula);
        Pattern p = Pattern.compile("[\\+\\-\\*/" + StringLiterals.NAMEMARKER + StringLiterals.ASSYMBOL + "&]");//recognises + - * / NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line
        boolean resetDefs = false;
        logger.debug("Set formula after SYA " + setFormula);
        int pos = 0;
        int stackCount = 0;
        //int stringCount = 0;
        // now to act on the formulae which has been converted to Reverse Polish, hence stack based parsing and no brackets etc.
        // NOTE THAT THE SHUNTING YARD ALGORITHM HERE LEAVES FUNCTIONS AT THE START (e.g. Attributeset)
        while (pos < setFormula.length()) {
            if (setFormula.substring(pos).trim().toLowerCase().startsWith(StringLiterals.ATTRIBUTESET)) { // perhaps not elegant but we'll leave for the moment, can't really treat in query functions like operators
                pos = setFormula.indexOf(StringLiterals.ATTRIBUTESET) + StringLiterals.ATTRIBUTESET.length();
                //grab a couple of terms (only sets of names can go onto the stack
                try {
                    int commaPos = setFormula.indexOf(",", pos);
                    int spacePos = setFormula.indexOf(" ", commaPos + 2);
                    if (spacePos == -1) {
                        spacePos = setFormula.length();
                    }
                    String attName = getAttributeSetTerm(setFormula.substring(pos, commaPos), referencedNames, formulaStrings);
                    String attValue = getAttributeSetTerm(setFormula.substring(commaPos + 1, spacePos), referencedNames, formulaStrings);
                    nameStack.add(new NameSetList(attributeSet(azquoMemoryDBConnection, attName, attValue), null, true));
                    stackCount++;
                    pos = spacePos;
                    while (pos < setFormula.length() && setFormula.charAt(pos) == ' ') {
                        pos++;
                    }
                } catch (Exception e) {
                    throw new Exception(formulaCopy + " " + StringLiterals.ATTRIBUTESET + " not understood");
                }
            } else {
                Matcher m = p.matcher(setFormula.substring(pos + 2));
                // HANDLE SET INTERSECTIONS UNIONS AND EXCLUSIONS (* + - )
                char op = setFormula.charAt(pos);
                int nextTerm = setFormula.length() + 1;
                if (m.find()) {
                    nextTerm = m.start() + pos + 2;
                    // PROBLEM!   The name found may have been following 'from ' or 'to ' (e.g. dates contain '-' so need to be encapsulated in quotes)
                    // need to check for this....
                    while (nextTerm < setFormula.length() && (StringUtils.precededBy(setFormula, StringLiterals.AS, nextTerm) || StringUtils.precededBy(setFormula, StringLiterals.TO, nextTerm) || StringUtils.precededBy(setFormula, StringLiterals.FROM, nextTerm) || StringUtils.precededBy(setFormula, StringLiterals.AS, nextTerm))) {
                        int startPos = nextTerm + 1;
                        nextTerm = setFormula.length() + 1;
                        m = p.matcher(setFormula.substring(startPos));
                        if (m.find()) {
                            nextTerm = m.start() + startPos;
                        }
                    }
                }
                if (op == StringLiterals.NAMEMARKER) {
                    stackCount++;
                    // now returns a custom little object that hods a list a set and whether it's immutable
                    nameStack.add(interpretSetTerm(setFormula.substring(pos, nextTerm - 1), formulaStrings, referencedNames, attributeStrings));
                } else if (stackCount-- < 2) {
                    throw new Exception("not understood:  " + formulaCopy);
                } else if (op == '*') { // * meaning intersection here . . .
                    //assume that the second term implies 'level all'
                    //long start = System.currentTimeMillis();
//                System.out.println("starting * set sizes  nameStack(stackcount)" + nameStack.get(stackCount).getAsCollection().size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).getAsCollection().size());
                    NameSetList previousSet = nameStack.get(stackCount - 1);
                    // preserving ordering important - retainall on a mutable set, if available, might save a bit vs creating a new one
                    // for the moment create a new collection, list or set based on the type of "previous set"
                    Set<Name> setIntersectionSet = null;
                    List<Name> setIntersectionList = null;
                    if (previousSet.set != null) { // not ordered
                        setIntersectionSet = HashObjSets.newMutableSet();
                        Set<Name> previousSetSet = previousSet.set;
                        for (Name name : nameStack.get(stackCount).getAsCollection()) { // if the last one on the stack is a list or set it doens't matter I'm not doing a contains on it
                            if (previousSetSet.contains(name)) {
                                setIntersectionSet.add(name);
                            }
                            for (Name child : name.findAllChildren()) {
                                if (previousSetSet.contains(child)) {
                                    setIntersectionSet.add(child);
                                }
                            }
                        }
                    } else { // I need to use previous set as the outside loop for ordering
                        setIntersectionList = new ArrayList<>(); // keep it as a list
                        Set<Name> lastSet = nameStack.get(stackCount).set != null ? nameStack.get(stackCount).set : HashObjSets.newMutableSet(nameStack.get(stackCount).list); // wrap the last one in a set if it's not a set
                        for (Name name : previousSet.list) {
                            if (lastSet.contains(name)) {
                                setIntersectionList.add(name);
                            } else { // we've already checked the top members, check all children to see if it's in there also
                                for (Name intersectName : lastSet) {
                                    if (intersectName.findAllChildren().contains(name)) {
                                        setIntersectionList.add(name);
                                    }
                                }
                            }
                        }
                    }
                    nameStack.set(stackCount - 1, new NameSetList(setIntersectionSet, setIntersectionList, true)); // replace the previous NameSetList
                    //System.out.println("after new retainall " + (System.currentTimeMillis() - start) + "ms");
                    nameStack.remove(stackCount);
                } else if (op == '/') { // a possible performance hit here, not sure of other possible optimseations
                    // ok what's on the stack may be mutable but I'm going to have to make a copy - if I modify it the iterator on the loop below will break
                    Set<Name> parents = HashObjSets.newMutableSet(nameStack.get(stackCount).getAsCollection());
                    //long start = System.currentTimeMillis();
                    //long heapMarker = ((runtime.totalMemory() - runtime.freeMemory()) / mb);
                    //System.out.println("aft mutable init " + heapMarker);
                    //System.out.println("starting / set sizes  nameStack(stackcount)" + nameStack.get(stackCount).getAsCollection().size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).getAsCollection().size());
                    Collection<Name> lastName = nameStack.get(stackCount).getAsCollection();
                    // if filtering brand it means az_brand - this is for the pivot functionality, pivot filter and pivot header
                    if (lastName.size() == 1) {
                        Name setName = lastName.iterator().next();
                        lastName = setName.findAllChildren();
                        if (lastName.size() == 0 && setName.getDefaultDisplayName().startsWith("az_")) {
                            setName = NameService.findByName(azquoMemoryDBConnection, setName.getDefaultDisplayName().substring(3));
                            if (setName != null) {
                                lastName = setName.getChildren();
                            }
                        }
                    }
                    for (Name child : lastName) {
                        Name.findAllParents(child, parents); // new call to static function cuts garbage generation a lot
                    }
                    //System.out.println("find all parents in parse query part 1 " + (now - start) + " set sizes parents " + parents.size() + " heap increase = " + (((runtime.totalMemory() - runtime.freeMemory()) / mb) - heapMarker) + "MB");
                    //start = System.currentTimeMillis();
                    //nameStack.get(stackCount - 1).retainAll(parents); //can't do this any more, need to make a new one
                    NameSetList previousSet = nameStack.get(stackCount - 1);
                    // ok going to try to get a little clever here since it can be mutable
                    if (previousSet.mutable) {
                        if (previousSet.list != null) {
                            previousSet.list.retainAll(parents);
                        } else { // I keep assuming set won't be null. I guess we'll see
                            previousSet.set.retainAll(parents);
                        }
                    } else { // need to make a new one
                        Set<Name> setIntersectionSet = null;
                        List<Name> setIntersectionList = null;
                        if (previousSet.list != null) { // ordered
                            setIntersectionList = new ArrayList<>();
                            // we must use previous set on the outside
                            for (Name name : previousSet.list) {
                                if (parents.contains(name)) {
                                    setIntersectionList.add(name);
                                }
                            }
                        } else { // need to make a new set, unordered, check set sizes in an attempt to keep speed high
                            setIntersectionSet = HashObjSets.newMutableSet(); // testing shows no harm, should be a bit faster and better on memory
                            Set<Name> previousSetSet = previousSet.set;
                            if (previousSetSet.size() < parents.size()) { // since contains should be the same regardless of set size we want to iterate the smaller one to create the intersection
                                for (Name name : previousSetSet) {
                                    if (parents.contains(name)) {
                                        setIntersectionSet.add(name);
                                    }
                                }
                            } else {
                                for (Name name : parents) {
                                    if (previousSetSet.contains(name)) {
                                        setIntersectionSet.add(name);
                                    }
                                }
                            }

                        }
                        nameStack.set(stackCount - 1, new NameSetList(setIntersectionSet, setIntersectionList, true)); // replace the previous NameSetList
                    }
                    //System.out.println("after retainall " + (System.currentTimeMillis() - start));
                    nameStack.remove(stackCount);
                } else if (op == '-') {
                    // using immutable sets on the stack causes more code here but populating the stack should be MUCH faster
                    // ok I have the mutable option now
                    if (nameStack.get(stackCount - 1).mutable) {
                        nameStack.get(stackCount - 1).getAsCollection().removeAll(nameStack.get(stackCount).getAsCollection());
                    } else { // make a new one
                        Set<Name> currentSet = nameStack.get(stackCount).set != null ? nameStack.get(stackCount).set : HashObjSets.newMutableSet(nameStack.get(stackCount).list); // convert to a set if it's not. Faster contains.
                        NameSetList previousSet = nameStack.get(stackCount - 1);
                        // standard list or set check
                        Set<Name> resultAsSet = null;
                        List<Name> resultAsList = null;
                        // instantiate the correct type of collection and point result at it
                        Collection<Name> result = previousSet.set != null ? (resultAsSet = HashObjSets.newMutableSet()) : (resultAsList = new ArrayList<>()); // assignment expression, I hope clear.
                        // populate result with the difference
                        for (Name name : previousSet.getAsCollection()) {
                            if (!currentSet.contains(name)) { // only the ones not in the current set
                                result.add(name);
                            }
                        }
                        nameStack.set(stackCount - 1, new NameSetList(resultAsSet, resultAsList, true)); // replace the previous NameSetList
                    }
                    nameStack.remove(stackCount);
                } else if (op == '+') {
                    //nameStack.get(stackCount - 1).addAll(nameStack.get(stackCount));
                    if (nameStack.get(stackCount - 1).mutable) { // can use the old simple call :)
                        nameStack.get(stackCount - 1).getAsCollection().addAll(nameStack.get(stackCount).getAsCollection()); // simple - note lists won't detect duplicates but I guess they never did
                    } else { // need to make a new one preserving type for ordering
                        NameSetList previousSet = nameStack.get(stackCount - 1);
                        Set<Name> resultAsSet = null;
                        List<Name> resultAsList = null;
                        Collection<Name> result;
                        // instantiate the correct type of collection with the data and point result at it
                        result = previousSet.set != null ? (resultAsSet = HashObjSets.newMutableSet(previousSet.set)) : (resultAsList = new ArrayList<>(previousSet.list));
                        result.addAll(nameStack.get(stackCount).getAsCollection());
                        nameStack.set(stackCount - 1, new NameSetList(resultAsSet, resultAsList, true)); // replace the previous NameSetList
                    }
                    nameStack.remove(stackCount);
                } else if (op == StringLiterals.ASSYMBOL) {
                    resetDefs = true;
                    Name totalName = nameStack.get(stackCount).getAsCollection().iterator().next();// get(0) relies on list, this works on a collection
                /* ok here's the thing. We don't want this to be under the default display name, new logic jams the user email as the first "language"
                therefore if there's more than one language, we use the first one as the way to define this name.
                The point being that the result of "blah blah blah as 'Period Chosen'" will now mean different 'Period Chosen's for each user
                Need to watch out regarding creating user specific sets : when we get the name see if it's for this user, if so then just change it otherwise make a new one
                */
                    if (attributeNames.size() > 1) { // just checking we have have the user added to the list
                        String userEmail = attributeNames.get(0);
                        if (totalName.getAttribute(userEmail) == null) { // there is no specific set for this user yet, need to do something
                            Name userSpecificSet = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance()); // a basic copy of the set
                            //userSpecificSet.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, userEmail + totalName.getDefaultDisplayName()); // GOing to set the default display name as bits of the suystem really don't like it not being there
                            userSpecificSet.setAttributeWillBePersisted(userEmail, totalName.getDefaultDisplayName()); // set the name (usually default_display_name) but for the "user email" attribute
                            totalName.addChildWillBePersisted(userSpecificSet);
                            totalName = userSpecificSet; // switch the new one in, it will be used as normal
                        }
                    }
                    totalName.setChildrenWillBePersisted(nameStack.get(stackCount - 1).getAsCollection());
                    nameStack.remove(stackCount);
                    Set<Name> totalNameSet = HashObjSets.newMutableSet();
                    totalNameSet.add(totalName);
                    nameStack.set(stackCount - 1, new NameSetList(totalNameSet, null, true));
                }
                pos = nextTerm;
            }
        }

        if (azquoMemoryDBConnection.getReadPermissions().size() > 0) {
            for (Name possible : nameStack.get(0).getAsCollection()) {
                if (possible == null || isAllowed(possible, azquoMemoryDBConnection.getReadPermissions())) {
                    toReturn.add(possible);
                }
            }
        } else { // is the add all inefficient?
            toReturn.addAll(nameStack.get(0).getAsCollection());
        }
        long time = (System.currentTimeMillis() - track);
        long heapIncrease = ((runtime.totalMemory() - runtime.freeMemory()) / mb) - startUsed;
        if (heapIncrease > 50) {
            System.out.println("Parse query : " + formulaCopy + " heap increase : " + heapIncrease + "MB ###########");
        }
        if (time > 50) {
            System.out.println("Parse query : " + formulaCopy + " took : " + time + "ms");
        }
        if (resetDefs) {
            //currently recalculates ALL definitions regardless of whether they contain the changed set.  Could speed this by looking for expressions that contain the changed set name
            Collection<Name> defNames = azquoMemoryDBConnection.getAzquoMemoryDBIndex().namesForAttribute("DEFINITION");
            if (defNames != null) {
                for (Name defName : defNames) {
                    String definition = defName.getAttribute("DEFINITION");
                    if (definition != null) {
                        if (attributeNames.size() > 1) {
                            String userEmail = attributeNames.get(0);
                            if (defName.getAttribute(userEmail) == null) { // there is no specific set for this user yet, need to do something
                                List<String> localLanguages = new ArrayList<>();
                                localLanguages.add(userEmail);
                                Name userSpecificSet = NameService.findByName(azquoMemoryDBConnection, defName.getDefaultDisplayName(), localLanguages);
                                if (userSpecificSet == null) {
                                    userSpecificSet = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance()); // a basic copy of the set
                                    //userSpecificSet.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, userEmail + totalName.getDefaultDisplayName()); // GOing to set the default display name as bits of the suystem really don't like it not being there
                                    userSpecificSet.setAttributeWillBePersisted(userEmail, defName.getDefaultDisplayName()); // set the name (usually default_display_name) but for the "user email" attribute
                                    defName.addChildWillBePersisted(userSpecificSet);
                                }
                                defName = userSpecificSet; // switch the new one in, it will be used as normal
                            }
                        }
                        Collection<Name> defSet = parseQuery(azquoMemoryDBConnection, definition, attributeNames);
                        if (defSet != null) {
                            defName.setChildrenWillBePersisted(defSet);
                        }
                    }
                }
            }
        }
        return toReturn;
    }

    // Managed to convert to returning NameSetList, the key being using fast collection operations where possible depending on what has been passed

    private static AtomicInteger interpretSetTermCount = new AtomicInteger(0);

    private static NameSetList interpretSetTerm(String setTerm, List<String> strings, List<Name> referencedNames, List<String> attributeStrings) throws Exception {
        interpretSetTermCount.incrementAndGet();
        //System.out.println("interpret set term . . ." + setTerm);
        final String levelString = StringUtils.getInstruction(setTerm, StringLiterals.LEVEL);
        String fromString = StringUtils.getInstruction(setTerm, StringLiterals.FROM);
        String parentsString = StringUtils.getInstruction(setTerm, StringLiterals.PARENTS);
        String childrenString = StringUtils.getInstruction(setTerm, StringLiterals.CHILDREN);
        final String sorted = StringUtils.getInstruction(setTerm, StringLiterals.SORTED);
        String toString = StringUtils.getInstruction(setTerm, StringLiterals.TO);
        String countString = StringUtils.getInstruction(setTerm, StringLiterals.COUNT);
        final String countbackString = StringUtils.getInstruction(setTerm, StringLiterals.COUNTBACK);
        final String compareWithString = StringUtils.getInstruction(setTerm, StringLiterals.COMPAREWITH);
        String selectString = StringUtils.getInstruction(setTerm, StringLiterals.SELECT);

        int wherePos = setTerm.toLowerCase().indexOf(StringLiterals.WHERE.toLowerCase());
        String whereString = null;
        if (wherePos >= 0) {
            whereString = setTerm.substring(wherePos + 6);//the rest of the string???   maybe need 'group by' in future
        }
        if (levelString != null) {
            childrenString = "true";
        }
        NameSetList namesFound; // default for a single name?
        // used to be ; at the end of a name
        String nameString = setTerm;
        if (setTerm.indexOf(' ') > 0) {
            nameString = setTerm.substring(0, setTerm.indexOf(' ')).trim();
        }
        final Name name = getNameFromListAndMarker(nameString, referencedNames);
        if (name == null) {
            throw new Exception("error:  not understood: " + nameString);
        }
        if (childrenString == null && fromString == null && toString == null && countString == null) {
            List<Name> singleName = new ArrayList<>();
            singleName.add(name);
            namesFound = new NameSetList(null, singleName, true);// mutable single item list
        } else {
            namesFound = NameService.findChildrenAtLevel(name, levelString); // reassign names from the find children clause
            if (fromString == null) fromString = "";
            if (toString == null) toString = "";
            if (countString == null) countString = "";
            if (fromString.length() > 0 || toString.length() > 0 || countString.length() > 0) {
                if (namesFound.list != null) { // yeah I know some say this is not best practice but hey ho
                    namesFound = constrainNameListFromToCount(namesFound, fromString, toString, countString, countbackString, compareWithString, referencedNames);
                } else {
                    System.out.println("can't from/to/count a non-list, " + setTerm);
                }
            }
        }
        if (whereString != null) {
            // will only work if it's a list internally
            namesFound = filter(namesFound, whereString, strings, attributeStrings);
        }
        // could parents and select be more efficient?
        if (parentsString != null) {
            // make mutable if it isn't
            if (!namesFound.mutable) {
                namesFound = new NameSetList(namesFound);
            }
            Iterator<Name> withChildrenOnlyIterator = namesFound.getAsCollection().iterator();
            while (withChildrenOnlyIterator.hasNext()) {
                Name check = withChildrenOnlyIterator.next();
                if (!check.hasChildren()) {// use iterator remove for childless names
                    withChildrenOnlyIterator.remove();
                }
            }
        }
        if (selectString != null) {
            String toFind = strings.get(Integer.parseInt(selectString.substring(1, 3))).toLowerCase();
            // make mutable if not
            if (!namesFound.mutable) {
                namesFound = new NameSetList(namesFound);
            }
            Iterator<Name> selectedNamesIterator = namesFound.getAsCollection().iterator();
            while (selectedNamesIterator.hasNext()) {
                Name check = selectedNamesIterator.next();
                if (check == null || check.getDefaultDisplayName() == null
                        || !check.getDefaultDisplayName().toLowerCase().contains(toFind)) { // reversing logic from before to use iterator remove to get rid of non relevant names
                    selectedNamesIterator.remove(); //
                }
            }
        }
        if (sorted != null) { // I guess force list
            if (namesFound.list == null || !namesFound.mutable) { // then force to a mutable list, don't see that we have a choice
                namesFound = new NameSetList(null, new ArrayList<>(namesFound.getAsCollection()), true);
            }
            Collections.sort(namesFound.list, NameService.defaultLanguageCaseInsensitiveNameComparator);
        }
        return namesFound != null ? namesFound : new NameSetList(null, new ArrayList<>(), true); // empty one if it's null
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
            if (toAdd == null) {
                throw new Exception("error: cannot resolve reference to a name " + nameString);
            }
            referencedNames.add(toAdd);
        }
        return referencedNames;
    }

    // since we need different from the standard set ordering use a list, I see no real harm in that in these functions
    // note : in default language!
    private static AtomicInteger constrainNameListFromToCountCount = new AtomicInteger(0);

    private static NameSetList constrainNameListFromToCount(NameSetList nameSetList, String fromString, String toString, final String countString, final String countBackString, final String compareWithString, List<Name> referencedNames) throws Exception {
        if (nameSetList.list == null) {
            return nameSetList; // don't bother trying to constrain a non list
        }
        constrainNameListFromToCountCount.incrementAndGet();
        final ArrayList<Name> toReturn = new ArrayList<>();
        int to = -10000;
        int from = 1;
        int count = parseInt(countString, -1);
        int offset = parseInt(countBackString, 0);
        int compareWith = parseInt(compareWithString, 0);
        int space = 1; //spacing between 'compare with' fields
        //first look for integers and encoded names...

        if (toString.length() > 0 && count > 0) {
            if (!nameSetList.mutable) {
                nameSetList = new NameSetList(null, new ArrayList<>(nameSetList.list), true);// then make it mutable
            }
            //invert the list
            Collections.reverse(nameSetList.list);
            fromString = toString;
            toString = "";
        }

        if (fromString.length() > 0) {
            from = -1;
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (fromString.charAt(0) == StringLiterals.NAMEMARKER) {
                    Name fromName = getNameFromListAndMarker(fromString, referencedNames);
                    fromString = fromName.getDefaultDisplayName();
                }
            }
        }
        if (toString.length() > 0) {
            boolean fromEnd = false;
            if (toString.toLowerCase().endsWith("from end")) {
                fromEnd = true;
                toString = toString.substring(0, toString.length() - 9);
            }
            try {
                to = Integer.parseInt(toString);
                if (fromEnd) to = nameSetList.list.size() - to;
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (toString.charAt(0) == StringLiterals.NAMEMARKER) {
                    Name toName = getNameFromListAndMarker(toString, referencedNames);
                    toString = toName.getDefaultDisplayName();
                }
            }
        }
        int position = 1;
        boolean inSet = false;
        if (to != -1000 && to < 0) {
            to = nameSetList.list.size() + to;
        }
        int added = 0;
        for (int i = offset; i < nameSetList.list.size() + offset; i++) {
            if (position == from || (i < nameSetList.list.size() && nameSetList.list.get(i).getDefaultDisplayName().equals(fromString))) {
                inSet = true;
            }
            if (inSet) {
                toReturn.add(nameSetList.list.get(i - offset));
                if (compareWith != 0) {
                    toReturn.add(nameSetList.list.get(i - offset + compareWith));
                    for (int j = 0; j < space; j++) {
                        toReturn.add(null);
                    }
                }
                added++;
            }
            if (position == to || (i < nameSetList.list.size() && nameSetList.list.get(i).getDefaultDisplayName().equals(toString)) || added == count) {
                inSet = false;
            }
            position++;
        }
        while (added++ < count) {
            toReturn.add(null);
        }
        return new NameSetList(null, toReturn, true);
    }

    private static AtomicInteger filterCount = new AtomicInteger(0);

    // since what it's passed could be immutable need to return
    private static NameSetList filter(NameSetList nameSetList, String condition, List<String> strings, List<String> attributeNames) {
        filterCount.incrementAndGet();
        NameSetList toReturn = nameSetList.mutable ? nameSetList : new NameSetList(nameSetList); // make a new mutable NameSetList if the one passed wasn't mutable
        //NOT HANDLING 'OR' AT PRESENT
        int andPos = condition.toLowerCase().indexOf(" and ");
        // get the correct now mutable member collection to filter
        Collection<Name> namesToFilter = toReturn.getAsCollection();
        int lastPos = 0;
        while (lastPos < condition.length()) {
            if (andPos < 0) {
                andPos = condition.length();
            }
            String clause = condition.substring(lastPos, andPos).trim();
            Pattern p = Pattern.compile("[<=>]+");
            Matcher m = p.matcher(clause);

            if (m.find()) {
                String opfound = m.group();
                int pos = m.start();
                String clauseLhs = clause.substring(0, pos).trim();
                String clauseRhs = clause.substring(m.end()).trim();

                // note, given the new parser these clauses will either be literals or begin .
                // there may be code improvements that can be made knowing this

                if (clauseLhs.charAt(0) == StringLiterals.ATTRIBUTEMARKER) {// we need to replace it
                    clauseLhs = attributeNames.get(Integer.parseInt(clauseLhs.substring(1, 3)));
                }
                if (clauseRhs.charAt(0) == StringLiterals.ATTRIBUTEMARKER) {// we need to replace it
                    clauseRhs = attributeNames.get(Integer.parseInt(clauseRhs.substring(1, 3)));
                }

                String valRhs = "";
                boolean fixed = false;
                boolean isADate = false;
                if (clauseRhs.charAt(0) == '"') {
                    valRhs = strings.get(Integer.parseInt(clauseRhs.substring(1, 3)));// anything left in quotes is referenced in the strings list
                    fixed = true;
                    //assume here that date will be of the form yyyy-mm-dd
                    if (BatchImporter.isADate(valRhs) != null) {
                        isADate = true;
                    }
                }

                Set<Name> namesToRemove = HashObjSets.newMutableSet();
                for (Name name : namesToFilter) {
                    String valLhs = name.getAttribute(clauseLhs);

                    if (valLhs == null) {
                        valLhs = "";
                    }
                    if (isADate) {
                        valLhs = StringUtils.standardizeDate(valLhs);
                    }
                    if (!fixed) {
                        valRhs = name.getAttribute(clauseRhs);
                        if (valRhs == null) {
                            valRhs = "";
                        }
                    }
                    boolean OK = false;
                    int comp = valLhs.compareTo(valRhs);
                    for (int i = 0; i < opfound.length(); i++) {
                        char op = opfound.charAt(i);
                        switch (op) {
                            case '=':
                                if (comp == 0) OK = true;
                                break;
                            case '<':
                                if (comp < 0) OK = true;
                                break;
                            case '>':
                                if (comp > 0) OK = true;
                        }
                    }
                    if (!OK) {
                        namesToRemove.add(name);
                    }
                }
                // outside the loop, iterator shouldn't get shirty
                namesToFilter.removeAll(namesToRemove);
            }
            lastPos = andPos + 5;
            andPos = condition.toLowerCase().indexOf(" and ", lastPos);
        }
        return toReturn; // its appropriate member collection should have been modified via namesToFilter above, return it
    }

    // when parsing expressions we replace names with markers and jam them on a list. The expression is manipulated before being executed. On execution the referenced names need to be read from a list.

    private static AtomicInteger getNameFromListAndMarkerCount = new AtomicInteger(0);

    static Name getNameFromListAndMarker(String nameMarker, List<Name> nameList) throws Exception {
        getNameFromListAndMarkerCount.incrementAndGet();
        if (nameMarker.charAt(0) == StringLiterals.NAMEMARKER) {
            try {
                int nameNumber = Integer.parseInt(nameMarker.substring(1).trim());
                return nameList.get(nameNumber);
            } catch (Exception e) {
                throw new Exception("error: " + nameMarker + " is not a valid name");
            }
        } else {
            throw new Exception("error: " + nameMarker + " is not a valid name");
        }
    }

    // edd : I wonder a little about this but will leave it for the mo

    private static int parseInt(final String string, int existing) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return existing;
        }
    }

    private static String getAttributeSetTerm(String term, List<Name> referencedNames, List<String> strings) throws Exception {
        term = term.trim();
        if (term.startsWith("\"")) {
            return strings.get(Integer.parseInt(term.substring(1, 3))).toLowerCase();
        }
        if (term.startsWith(StringLiterals.NAMEMARKER + "")) {
            return getNameFromListAndMarker(term, referencedNames).getDefaultDisplayName();
        }
        return term;
    }

    // in parse query, we want to find any names with that attribute and value e.g. "BORNIN", "GUILDFORD"

    private static Set<Name> attributeSet(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeName, String attributeValue) {
        List<String> attributeNames = new ArrayList<>();
        attributeNames.add(attributeName);
        return azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(attributeNames, attributeValue, null);
    }

    public static void printFunctionCountStats() {
        System.out.println("######### NAME QUERY PARSER FUNCTION COUNTS");
        System.out.println("interpretSetTermCount\t\t\t\t\t\t\t\t" + interpretSetTermCount.get());
        System.out.println("constrainNameListFromToCountCount\t\t\t\t\t\t\t\t" + constrainNameListFromToCountCount.get());
        System.out.println("parseQueryCount\t\t\t\t\t\t\t\t" + parseQueryCount.get());
        System.out.println("parseQuery2Count\t\t\t\t\t\t\t\t" + parseQuery2Count.get());
        System.out.println("parseQuery3Count\t\t\t\t\t\t\t\t" + parseQuery3Count.get());
        System.out.println("getNameListFromStringListCount\t\t\t\t\t\t\t\t" + getNameListFromStringListCount.get());
        System.out.println("getNameFromListAndMarkerCount\t\t\t\t\t\t\t\t" + getNameFromListAndMarkerCount.get());
        System.out.println("filterCount\t\t\t\t\t\t\t\t" + filterCount.get());
    }

    public static void clearFunctionCountStats() {
        interpretSetTermCount.set(0);
        constrainNameListFromToCountCount.set(0);
        parseQueryCount.set(0);
        parseQuery2Count.set(0);
        parseQuery3Count.set(0);
        getNameListFromStringListCount.set(0);
        getNameFromListAndMarkerCount.set(0);
        filterCount.set(0);
    }

    public static boolean isAllowed(Name name, List<Set<Name>> names) {
        if (name == null || names == null || names.isEmpty()) { // empty the same as null
            return true;
        }
        for (Set<Name> listNames : names) {
            if (!listNames.isEmpty()) {
                if (NameService.inParentSet(name, listNames) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}