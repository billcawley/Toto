package com.azquo.memorydb.service;

import com.azquo.StringLiterals;
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
 * <p>
 * Parsing stuff in here, low level name functions in NameService, filter functions in NameFilterFunctions and Name stack
 * operators are now dealt with in NameStackOperators.
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
            NameSetList  nameSetList = null;
            nameSetList = interpretSetTerm(nameSetList, nameName, formulaStrings, referencedNames, attributeStrings);
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
        return parseQuery(azquoMemoryDBConnection, setFormula, langs, null, false);
    }

    private static AtomicInteger parseQuery2Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, boolean returnReadOnlyCollection) throws Exception {
        parseQuery2Count.incrementAndGet();
        return parseQuery(azquoMemoryDBConnection, setFormula, attributeNames, null, returnReadOnlyCollection);
    }

    /* todo : sort exceptions?
    todo - cache option in here
    now uses NameSetList to move connections of names around and only copy them as necessary. Has made the logic a little more complex
    in places but performance should be better and garbage generation reduced
    todo - check logic regarding toReturn makes sense.
    */
    private static AtomicInteger parseQuery3Count = new AtomicInteger(0);

    public static Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, Collection<Name> toReturn, boolean returnReadOnlyCollection) throws Exception {
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
        } else {
            // if a colleciton to return was passed we can't return a read only colleciton, we'll be adding to what was passed
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
        boolean sorted = false;
        if (setFormula.toLowerCase().endsWith(" "+StringLiterals.SORTED)){
            sorted = true;
            setFormula = setFormula.substring(0,setFormula.length() - StringLiterals.SORTED.length() - 1);
        }

        setFormula = StringUtils.prepareStatement(setFormula, nameStrings, attributeStrings, formulaStrings);
        List<Name> referencedNames;
        try {
            referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, languages);
        } catch (Exception e) {
            if (setFormula.toLowerCase().equals("!00 children")) return new ArrayList<>();
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
            throw e;
        }
        setFormula = setFormula.replace(StringLiterals.AS, StringLiterals.ASSYMBOL + "");
        setFormula = StringUtils.shuntingYardAlgorithm(setFormula);
        Pattern p = Pattern.compile("[\\+\\-\\*/" + StringLiterals.NAMEMARKER + StringLiterals.ASSYMBOL + "&]");//recognises + - * / NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line
        boolean resetDefs = false;
        logger.debug("Set formula after SYA " + setFormula);
        int pos = 0;
        // could we get rid of stack count and just use the ArrayList's size?
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
                if (op == StringLiterals.NAMEMARKER) { // then a straight name children from to etc. Resolve in interpretSetTerm
                    stackCount++;
                    // now returns a custom little object that hods a list a set and whether it's immutable
                    nameStack.add(interpretSetTerm(null,setFormula.substring(pos, nextTerm - 1), formulaStrings, referencedNames, attributeStrings));
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
                } else if (op == StringLiterals.ASSYMBOL) {
                    resetDefs = true;
                    NameStackOperators.assignSetAsName(azquoMemoryDBConnection, attributeNames, nameStack, stackCount);
                }
                if (op!=StringLiterals.NAMEMARKER && nextTerm > setFormula.length() && pos < nextTerm - 3){
                    //there's still more stuff to understand!  Having created a set, we may now wish to operate on that set
                    int childrenPos = setFormula.substring(pos).indexOf("children");
                    if (childrenPos > 0) {

                        nameStack.set(0, interpretSetTerm(nameStack.get(0), setFormula.substring(pos + 1, pos + childrenPos), formulaStrings, referencedNames, attributeStrings));
                        nameStack.set(0, interpretSetTerm(nameStack.get(0), setFormula.substring(childrenPos + pos), formulaStrings, referencedNames, attributeStrings));
                    }else{
                        nameStack.set(0, interpretSetTerm(nameStack.get(0), setFormula.substring(pos + 1), formulaStrings, referencedNames, attributeStrings));
                    }
                }
                pos = nextTerm;
            }
        }
        if (sorted){
            if (nameStack.get(0) == null || nameStack.get(0).list == null || !nameStack.get(0).mutable) { // then force to a mutable list, don't see that we have a choice
                nameStack.set(0,new NameSetList(null, new ArrayList<>(nameStack.get(0).getAsCollection()), true));
            }
            nameStack.get(0).list.sort(NameService.defaultLanguageCaseInsensitiveNameComparator);

        }

        long heapIncreaseBeforeCopyToArray = ((runtime.totalMemory() - runtime.freeMemory()) / mb) - startUsed;

        if (azquoMemoryDBConnection.getReadPermissions().size() > 0) {
            for (Name possible : nameStack.get(0).getAsCollection()) {
                if (possible == null || isAllowed(possible, azquoMemoryDBConnection.getReadPermissions())) {
                    toReturn.add(possible);
                }
            }
        } else { // add all can be inefficient as it does a .toArray but the big saving here can be if I can get a hint that the collection won't be modified, then I can just return what's on the namestack
            // note the first entry on the name stack might actually be mutable anyway and hence we could just return it IF a toReturn collection wasn't passed
            if (returnReadOnlyCollection){
                toReturn = nameStack.get(0).getAsCollection();
            } else {
                toReturn.addAll(nameStack.get(0).getAsCollection());
            }
        }
        long time = (System.currentTimeMillis() - track);
        long heapIncrease = ((runtime.totalMemory() - runtime.freeMemory()) / mb) - startUsed;
        if (heapIncrease > 50) {
            System.out.println("Parse query : " + formulaCopy + " heap increase : " + heapIncrease + "MB. Before copying to array : " + heapIncreaseBeforeCopyToArray + " ###########");
        }
        if (time > 50) {
            System.out.println("Parse query : " + formulaCopy + " took : " + time + "ms");
        }
        // check if this is necessary? Refactor?
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
                        Collection<Name> defSet = parseQuery(azquoMemoryDBConnection, definition, attributeNames, true); // can be read only
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

    private static NameSetList interpretSetTerm(NameSetList namesFound, String setTerm, List<String> strings, List<Name> referencedNames, List<String> attributeStrings) throws Exception {
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
        if (namesFound==null || namesFound.list.size() == 1) {
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
            }else{
                name = namesFound.list.get(0);
            }
            if (childrenString == null && fromString == null && toString == null && countString == null) {
                List<Name> singleName = new ArrayList<>();
                singleName.add(name);
                namesFound = new NameSetList(null, singleName, true);// mutable single item list
            } else {
                namesFound = NameService.findChildrenAtLevel(name, levelString); // reassign names from the find children clause
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
                namesFound = NameFilterFunctions.constrainNameListFromToCount(namesFound, fromString, toString, countString, offsetString, compareWithString, referencedNames);
            } else {
                System.out.println("can't from/to/count a non-list, " + setTerm);
            }
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

    // edd : I wonder a little about this but will leave it for the mo

    static int parseInt(final String string, int existing) {
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