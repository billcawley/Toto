package com.azquo.memorydb.service;

import com.azquo.memorydb.Constants;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.spreadsheet.StringUtils;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * <p>
 * Ok, outside of the memorydb package this may be the the most fundamental class.
 * Edd trying to understand it properly and trying to get string parsing out of it but not sure how easy that will be
 * <p>
 * I don't feel that tere are obviously better places for the funcitons right now except that
 * todo : can the string parsing and json generation be moved out of here?
 */
public final class NameService {
    public static final String MEMBEROF = "->";

    @Autowired
    ValueService valueService;//used only in formating children for output
    @Autowired
    DSSpreadsheetService dsSpreadsheetService;//used only in formating children for output

    public StringUtils stringUtils = new StringUtils(); // just make it quickly like this for the mo
    //    private static final ObjectMapper jacksonMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(NameService.class);

    public static final String LEVEL = "level";
    public static final String PARENTS = "parents";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String COUNT = "count";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String SELECT = "select";
    //public static final String LOWEST = "lowest";
    //public static final String ALL = "all";
    public static final char NAMEMARKER = '!';
    public static final char ATTRIBUTEMARKER = '|';
    public static final String CREATE = "create";
    public static final String PEERS = "peers";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";
    public static final String COUNTBACK = "countback";
    public static final String COMPAREWITH = "comparewith";
    public static final String AS = "as";
    public static final char ASSYMBOL = '@';
    public static final String WHERE = "where";

    // hopefully thread safe??
    private static AtomicInteger nameCompareCount = new AtomicInteger(0);

    public final Comparator<Name> defaultLanguageCaseInsensitiveNameComparator = (n1, n2) -> {
        nameCompareCount.incrementAndGet();
        // null checks to keep intellij happy, probably not a bad thing
        boolean n1Null = n1 == null || n1.getDefaultDisplayName() == null;
        boolean n2Null = n2 == null || n2.getDefaultDisplayName() == null;
        if (n1Null && n2Null) {
            return 0;
        }
        // is that the right way round? Not sure
        if (n1Null) {
            return -1;
        }
        if (n2Null) {
            return 1;
        }
        return n1.getDefaultDisplayName().toUpperCase().compareTo(n2.getDefaultDisplayName().toUpperCase()); // think that will give us a case insensitive sort!
    };

    // get names from a comma separated list. Well expressions describing names.

    private static AtomicInteger decodeStringCount = new AtomicInteger(0);

    public final List<Set<Name>> decodeString(AzquoMemoryDBConnection azquoMemoryDBConnection, String searchByNames, List<String> attributeNames) throws Exception {
        decodeStringCount.incrementAndGet();
        final List<Set<Name>> toReturn = new ArrayList<>();
        List<String> formulaStrings = new ArrayList<>();
        List<String> nameStrings = new ArrayList<>();
        List<String> attributeStrings = new ArrayList<>(); // attribute names is taken. Perhaps need to think about function parameter names
        searchByNames = stringUtils.parseStatement(searchByNames, nameStrings, formulaStrings, attributeStrings);
        List<Name> referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
        // given that parse statement treats "," as an operator this should be ok.
        StringTokenizer st = new StringTokenizer(searchByNames, ",");
        while (st.hasMoreTokens()) {
            String nameName = st.nextToken().trim();
            Set<Name> nameSet = interpretSetTerm(nameName, formulaStrings, referencedNames, attributeStrings); // no need to wrap it
            toReturn.add(nameSet);
        }
        return toReturn;
    }

    // we replace the names with markers for parsing. Then we need to resolve them later, here is where the exception will be thrown. Should be NameNotFoundException?

    private static AtomicInteger getNameListFromStringListCount = new AtomicInteger(0);

    public List<Name> getNameListFromStringList(List<String> nameStrings, AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> attributeNames) throws Exception {
        getNameListFromStringListCount.incrementAndGet();
        List<Name> referencedNames = new ArrayList<>(nameStrings.size());
        for (String nameString : nameStrings) {
            Name toAdd = findByName(azquoMemoryDBConnection, nameString, attributeNames);
            if (toAdd == null) {
                throw new Exception("error: cannot resolve reference to a name " + nameString);
            }
            referencedNames.add(toAdd);
        }
        return referencedNames;
    }

/*    public ArrayList<Name> findContainingName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) {
        // go for the default for the moment
        return findContainingName(azquoMemoryDBConnection, name, Constants.DEFAULT_DISPLAY_NAME);
     }*/

    // the parameter is called name as the get attribute function will look up and derive attributes it can't find from parent/combinations

    private static AtomicInteger findContainingNameCount = new AtomicInteger(0);

    public ArrayList<Name> findContainingName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, String attribute) {
        findContainingNameCount.incrementAndGet();
        ArrayList<Name> namesList = new ArrayList<>(azquoMemoryDBConnection.getAzquoMemoryDB().getNamesWithAttributeContaining(attribute, name));
        if (namesList.size() == 0 && attribute.length() > 0) {
            namesList = findContainingName(azquoMemoryDBConnection, name, "");//try all the attributes
        }
        Collections.sort(namesList, defaultLanguageCaseInsensitiveNameComparator);
        return namesList;
    }

    private static AtomicInteger findByIdCount = new AtomicInteger(0);

    public Name findById(final AzquoMemoryDBConnection azquoMemoryDBConnection, int id) {
        findByIdCount.incrementAndGet();
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(id);
    }

    private static AtomicInteger getNameByAttributeCount = new AtomicInteger(0);

    public Name getNameByAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeValue, Name parent, final List<String> attributeNames) throws Exception {
        getNameByAttributeCount.incrementAndGet();
        if (attributeValue.charAt(0) == NAMEMARKER) {
            throw new Exception("error: getNameByAttribute should no longer have name marker passed to it!");
        }
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, attributeValue.replace(Name.QUOTE, ' ').trim(), parent);
    }

    private static AtomicInteger findByNameCount = new AtomicInteger(0);

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) throws Exception {
        findByNameCount.incrementAndGet();
        // aha, not null passed here now, jam in a default display name I think
        List<String> attNames = new ArrayList<>();
        attNames.add(Constants.DEFAULT_DISPLAY_NAME);
        return findByName(azquoMemoryDBConnection, name, attNames);
    }

    private static AtomicInteger findByName2Count = new AtomicInteger(0);

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final List<String> attributeNames) throws Exception {
        findByName2Count.incrementAndGet();
     /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.
        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.
        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London
        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

        */

        // language effectively being the attribute name
        // so london, ontario, canada
        // parent name would be canada
        if (name == null || name.length() == 0) return null;
        String parentName = stringUtils.findParentFromList(name);
        String remainder = name;
        Set<Name> possibleParents = null;
        // keep chopping away at the string until we find the closest parent we can
        // the point of all of this is to be able to ask for a name with the nearest parent but we can't just try and get it from the string directly e.g. get me WHsmiths on High street
        // we need to look from the top to distinguish high street in different towns
        while (parentName != null) {
            if (remainder.contains(MEMBEROF)) {
                remainder = remainder.substring(name.indexOf(MEMBEROF) + 2);
            } else {
                remainder = remainder.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length()));
            }
            if (possibleParents == null) {
                possibleParents = azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(attributeNames, parentName.replace(Name.QUOTE, ' ').trim(), null);
            } else {
                Set<Name> nextParents = new HashSet<>();
                for (Name parent : possibleParents) {
                    Name foundParent = getNameByAttribute(azquoMemoryDBConnection, parentName, parent, attributeNames);
                    if (foundParent != null) {
                        nextParents.add(foundParent);
                    }
                    possibleParents = nextParents;
                }
            }
            if (possibleParents == null || possibleParents.size() == 0) { // parent was null, since we're just trying to find that stops us right here
                return null;
            }
            // so chop off the last name, lastindex of moves backwards from the index
            // the reason for this is to deal with quotes, we could have said simply the substring take off the parent name length but we don't know about quotes or spaces after the comma
            // remainder is the rest of the string, could be london, ontario - Canada was taken off
            parentName = stringUtils.findParentFromList(remainder);
        }
        if (possibleParents == null) {
            return getNameByAttribute(azquoMemoryDBConnection, remainder, null, attributeNames);

        } else {
            for (Name parent : possibleParents) {
                Name found = getNameByAttribute(azquoMemoryDBConnection, remainder.replace(Name.QUOTE, ' ').trim(), parent, attributeNames);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static AtomicInteger clearChildrenCount = new AtomicInteger(0);

    public void clearChildren(Name name) throws Exception {
        clearChildrenCount.incrementAndGet();
        for (Name child : name.getChildren()) {
            name.removeFromChildrenWillBePersisted(child);
        }

    }

    private static AtomicInteger findTopNamesCount = new AtomicInteger(0);

    public List<Name> findTopNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String language) {
        findTopNamesCount.incrementAndGet();
        if (language.equals(Constants.DEFAULT_DISPLAY_NAME)) {
            return azquoMemoryDBConnection.getAzquoMemoryDB().findTopNames();
        } else {
            return azquoMemoryDBConnection.getAzquoMemoryDB().findTopNames(language);
        }
    }


    private static AtomicInteger findOrCreateNameStructureCount = new AtomicInteger(0);

    public Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, Name topParent, boolean local) throws Exception {
        findOrCreateNameStructureCount.incrementAndGet();
        return findOrCreateNameStructure(azquoMemoryDBConnection, name, topParent, local, null);
    }

    private static AtomicInteger findOrCreateNameStructure2Count = new AtomicInteger(0);

    public Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, Name topParent, boolean local, List<String> attributeNames) throws Exception {
        findOrCreateNameStructure2Count.incrementAndGet();
        /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.

        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.

        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London

        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

         */
        /* NEW BEHAVIOUR APRIL 2015
           If, on the first pass at finding a parent, more than one parent is found, it will see if it can find the whole string in any of the parent sets.
         */


        String parentName = stringUtils.findParentFromList(name);
        String remainder = name;
        if (parentName == null) {
            return findOrCreateNameInParent(azquoMemoryDBConnection, name, topParent, local, attributeNames);
        }

       /*
        ok teh key here is to step through the parent -> child list as defined in the name string creating teh hierarchy as you go along
        the top parent is the context in which names should be searched for and created if not existing, the parent name and parent is the direct parent we may have just created
        so what unique is saying is : ok we have the parent we want to add a name to : the question is do we search under that parent to find or create or under the top parent?
        More specifically : if it is unique check for the name anywhere under the top parent to find it and then move it if necessary, if not unique then it could, for example, be another name called London
        I think maybe the names of variables could be clearer here!, maybe look into on second pass
        */
        Name parent = topParent;
        while (parentName != null) {
            if (remainder.contains(MEMBEROF) && !remainder.endsWith(MEMBEROF)) {
                remainder = remainder.substring(remainder.indexOf(MEMBEROF) + 2);
            } else {
                remainder = remainder.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length() - 1));
            }
            //if two commas in succession occur, ignore the blank parent
            if (parentName.length() > 0) {
                parent = findOrCreateNameInParent(azquoMemoryDBConnection, parentName, parent, local, attributeNames);
            }
            parentName = stringUtils.findParentFromList(remainder);
        }
        return findOrCreateNameInParent(azquoMemoryDBConnection, remainder, parent, local, attributeNames);
    }

    private static AtomicInteger includeInSetCount = new AtomicInteger(0);

    public void includeInSet(Name name, Name set) throws Exception {
        includeInSetCount.incrementAndGet();
        set.addChildWillBePersisted(name);//ok add as asked
        Collection<Name> setParents = set.findAllParents();
        for (Name parent : name.getParents()) { // now check the direct parents and see that none are in the parents of the set we just put it in.
            // e.g the name was Ludlow in in places. We decided to add Ludlow to Shropshire which is all well and good.
            // Among Shropshire's parents is places so remove Ludlow from Places as it's now in places via Shropshire.
            if (setParents.contains(parent)) {
                parent.removeFromChildrenWillBePersisted(name);// following my above example, take Ludlow out of places
                break;
            }
        }
    }

    private static AtomicInteger findOrCreateNameInParentCount = new AtomicInteger(0);

    public Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name newParent, boolean local) throws Exception {
        findOrCreateNameInParentCount.incrementAndGet();
        return findOrCreateNameInParent(azquoMemoryDBConnection, name, newParent, local, null);
    }

    // um I think that should be concurrent
    Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new ConcurrentHashMap<>();

    private long addToTimesForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection, String trackName, long marker) {
        long now = System.currentTimeMillis();
        long toAdd = marker - now;
        long current = 0;
        if (timeTrack.get(azquoMemoryDBConnection) != null) {
            if (timeTrack.get(azquoMemoryDBConnection).get(trackName) != null) {
                current = timeTrack.get(azquoMemoryDBConnection).get(trackName);
            }
        } else {
            timeTrack.put(azquoMemoryDBConnection, new ConcurrentHashMap<>());
        }
        timeTrack.get(azquoMemoryDBConnection).put(trackName, current + toAdd);
        return now;
    }

    public Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        return timeTrack.get(azquoMemoryDBConnection);
    }

    private static final boolean profile = false;

    // todo - permissions here or at a higher level?

    private static AtomicInteger findOrCreateNameInParent2Count = new AtomicInteger(0);

    public Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name parent, boolean local, List<String> attributeNames) throws Exception {
        findOrCreateNameInParent2Count.incrementAndGet();
        long marker = System.currentTimeMillis();
        if (name.length() == 0) {
            return null;
        }
     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an dataimport),and insert a structure into it*/
        if (attributeNames == null) {
            attributeNames = new ArrayList<>();
            attributeNames.add(Constants.DEFAULT_DISPLAY_NAME);
        }

        String storeName = name.replace(Name.QUOTE, ' ').trim();
        Name existing;

        if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent1", marker);
        if (parent != null) { // ok try to find it in that parent
            //try for an existing name already with the same parent
            if (local) {// ok looking only below that parent or just in it's whole set or top parent.
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, parent);
            } else {
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent2", marker);
            // find an existing name with no parents. (note that if there are multiple such names, then the return will be null)
            // if we can't find the name in parent  then it's acceptable to find one with no parents or children todo - think about this!
            if (existing == null) {
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
                if (existing != null && (existing.hasParents() || existing.hasChildren())) {
                    existing = null;
                }
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent3", marker);
        } else { // no parent passed go for a vanilla lookup
            existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent4", marker);
        }
        if (existing != null) {
            // direct parents may be moved up the hierarchy (e.g. if existing parent is 'Europe' and new parent is 'London', which is in 'Europe' then
            // remove 'Europe' from the direct parent list.
            //NEW CONDITION ADDED - we are parent = child, but not bothering to put into the set.  This may need discussion - are the parent and child really the same?
            // I think I was just avoiding a circular reference
            if (parent != null && existing != parent && !existing.findAllParents().contains(parent)) {
                //only check if the new parent is not already in the parent hierarchy.
                includeInSet(existing, parent);
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent5", marker);
            return existing;
        } else {
            logger.debug("New name: " + storeName + ", " + (parent != null ? "," + parent.getDefaultDisplayName() : ""));
            // todo - we should not be getting the provenance from the connection
            Provenance provenance = azquoMemoryDBConnection.getProvenance();
            Name newName = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), provenance, true); // default additive to true
            // was != which would probably have worked but safer with !.equals
            if (!attributeNames.get(0).equals(Constants.DEFAULT_DISPLAY_NAME)) { // we set the leading attribute name, I guess the secondary ones should not be set they are for searches
                newName.setAttributeWillBePersisted(attributeNames.get(0), storeName);
            }
            newName.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, storeName); // and set the default regardless
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent6", marker);
            if (parent != null) {
                // and add the new name to the parent of course :)
                parent.addChildWillBePersisted(newName);
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent7", marker);
            return newName;
        }
    }

    public static final int LOWEST_LEVEL_INT = 100;
    public static final int ALL_LEVEL_INT = 101;

    // needs to be a list to preserve order when adding. Or could use a linked set, don't see much advantage

    private static AtomicInteger findChildrenAtLevelCount = new AtomicInteger(0);

    public Collection<Name> findChildrenAtLevel(final Name name, final String levelString) throws Exception {
        findChildrenAtLevelCount.incrementAndGet();
        // level 100 means get me the lowest
        // level 101 means 'ALL' (including the top level
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this
        int level = 1;
        if (levelString != null) {
            try {
                level = Integer.parseInt(levelString);
            } catch (NumberFormatException nfe) {
                //carry on regardless!
            }
        }
        if (level == 1) { // then no need to get clever, just return the children
            return name.getChildren(); // we do NOT want to wrap such an collection, it could be massive!
        }
        if (level == ALL_LEVEL_INT) {
            System.out.println("ALL_LEVEL_INT called on " + name.getDefaultDisplayName() + ", annoying!");
            // new logic! All names is the name find all children plus itself. A bit annoying to make a copy but there we go
            Set<Name> toReturn = HashObjSets.newUpdatableSet(name.findAllChildren(false));
            toReturn.add(name); // a whole new set just to add this, grrr
            return toReturn;
        }
        Collection<Name> namesFound = HashObjSets.newMutableSet();
        if (name.hasOrderedChildren()){
            boolean ordered = true;
            for (Name check : name.getChildren()){
                if (!check.hasOrderedChildren()){ // then these children are unordered, I'm going to say the whole lot doens't need to be ordered
                    ordered = false;
                    break;
                }
            }
            if (ordered){ // I'll grudgingly use a list . . .
                namesFound = new ArrayList<>();
            }
        }
        addNames(name, namesFound, 0, level);
        return namesFound;
    }

    private static AtomicInteger addNamesCount = new AtomicInteger(0);

    public void addNames(final Name name, Collection<Name> namesFound, final int currentLevel, final int level) throws Exception {
        addNamesCount.incrementAndGet();
        // I think this logic is obsolete now I have the add all below
/*        if (currentLevel == level) {
            namesFound.add(name);
            return;
        }*/
        if (!name.hasChildren()) {
            if (level == LOWEST_LEVEL_INT) {
                namesFound.add(name);
            }
        } else {
            if (currentLevel == (level - 1)){ // then we want the next one down, just add it all . . .
                namesFound.addAll(name.getChildren());
            } else {
                for (Name child : name.getChildren()) {
                    addNames(child, namesFound, currentLevel + 1, level);
                }
            }
        }
    }

    // edd : I wonder a little about this but will leave it for the mo

    private int parseInt(final String string, int existing) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return existing;
        }
    }

    // when parsing expressions we replace names with markers and jam them on a list. The expression is manipulated before being executed. On execution the referenced names need to be read from a list.

    private static AtomicInteger getNameFromListAndMarkerCount = new AtomicInteger(0);

    public Name getNameFromListAndMarker(String nameMarker, List<Name> nameList) throws Exception {
        getNameFromListAndMarkerCount.incrementAndGet();
        if (nameMarker.charAt(0) == NAMEMARKER) {
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

    // since we need different from the standard set ordering use a list, I see no real harm in that in these functions
    // note : in default language!

    private static AtomicInteger findChildrenFromToCount = new AtomicInteger(0);

    public List<Name> findChildrenFromToCount(final List<Name> names, String fromString, String toString, final String countString, final String countbackString, final String compareWithString, List<Name> referencedNames) throws Exception {
        findChildrenFromToCount.incrementAndGet();
        final ArrayList<Name> toReturn = new ArrayList<>();
        int to = -10000;
        int from = 1;
        int count = parseInt(countString, -1);
        int offset = parseInt(countbackString, 0);
        int compareWith = parseInt(compareWithString, 0);
        int space = 1; //spacing between 'compare with' fields
        //first look for integers and encoded names...

        if (fromString.length() > 0) {
            from = -1;
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (fromString.charAt(0) == NAMEMARKER) {
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
                if (fromEnd) to = names.size() - to;
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (toString.charAt(0) == NAMEMARKER) {
                    Name toName = getNameFromListAndMarker(toString, referencedNames);
                    toString = toName.getDefaultDisplayName();
                }
            }

        }
        int position = 1;
        boolean inSet = false;
        if (to != -1000 && to < 0) {
            to = names.size() + to;
        }


        int added = 0;

        for (int i = offset; i < names.size() + offset; i++) {

            if (position == from || (i < names.size() && names.get(i).getDefaultDisplayName().equals(fromString)))
                inSet = true;
            if (inSet) {
                toReturn.add(names.get(i - offset));
                if (compareWith != 0) {
                    toReturn.add(names.get(i - offset + compareWith));
                    for (int j = 0; j < space; j++) {
                        toReturn.add(null);
                    }
                }
                added++;
            }
            if (position == to || (i < names.size() && names.get(i).getDefaultDisplayName().equals(toString)) || added == count)
                inSet = false;
            position++;
        }
        while (added++ < count) {
            toReturn.add(null);
        }
        return toReturn;
    }

    // to find a set of names, a few bits that were part of the original set of functions
    // add the default display name since no attributes were specified.
    private static AtomicInteger parseQueryCount = new AtomicInteger(0);

    public final Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula) throws Exception {
        parseQueryCount.incrementAndGet();
        List<String> langs = new ArrayList<>();
        langs.add(Constants.DEFAULT_DISPLAY_NAME);
        return parseQuery(azquoMemoryDBConnection, setFormula, langs, new ArrayList<>());
    }

    private static AtomicInteger parseQuery2Count = new AtomicInteger(0);

    public final Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames) throws Exception {
        parseQuery2Count.incrementAndGet();
        return parseQuery(azquoMemoryDBConnection, setFormula, attributeNames, null);
    }

    // todo : sort exceptions? Move to another class? Also should the namestack be more generic
    // todo - cache option in here
    Runtime runtime = Runtime.getRuntime();
    int mb = 1024 * 1024;

    private static AtomicInteger parseQuery3Count = new AtomicInteger(0);

    public final Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, Collection<Name> toReturn) throws Exception {
        parseQuery3Count.incrementAndGet();
        long track = System.currentTimeMillis();
        String formulaCopy = setFormula;
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
        List<Collection<Name>> nameStack = new ArrayList<>(); // make this more generic, the key is in the name marker bits, might use different collections depending on operator!
        List<String> formulaStrings = new ArrayList<>();
        List<String> nameStrings = new ArrayList<>();
        List<String> attributeStrings = new ArrayList<>(); // attribute names is taken. Perhaps need to think about function parameter names
        Name possibleName = findByName(azquoMemoryDBConnection, setFormula);
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
        if (setFormula.startsWith("deduplicate")) {
            return deduplicate(azquoMemoryDBConnection, setFormula.substring(12));
        }
        if (setFormula.startsWith("zap ")) {
            Collection<Name> names = parseQuery(azquoMemoryDBConnection, setFormula.substring(4), attributeNames); // defaulting to list here
            if (names != null) {
                for (Name name : names) name.delete();
                return toReturn;
            }
        }
        setFormula = stringUtils.parseStatement(setFormula, nameStrings, attributeStrings, formulaStrings);
        List<Name> referencedNames;
        try {
            referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
        } catch (Exception e) {
            if (setFormula.toLowerCase().equals("!00 children")) return new ArrayList<>();// what is this??
            throw e;
        }
        setFormula = setFormula.replace(AS, ASSYMBOL + "");
        setFormula = stringUtils.shuntingYardAlgorithm(setFormula);
        Pattern p = Pattern.compile("[\\+\\-\\*/" + NAMEMARKER + NameService.ASSYMBOL + "&]");//recognises + - * / NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line

        logger.debug("Set formula after SYA " + setFormula);
        int pos = 0;
        int stackCount = 0;
        //int stringCount = 0;
        while (pos < setFormula.length()) {
            Matcher m = p.matcher(setFormula.substring(pos + 2));
            // HANDLE SET INTERSECTIONS UNIONS AND EXCLUSIONS (* + - )
            char op = setFormula.charAt(pos);
            int nextTerm = setFormula.length() + 1;
            if (m.find()) {
                nextTerm = m.start() + pos + 2;
                //PROBLEM!   The name found may have been following 'from ' or 'to ' (e.g. dates contain '-' so need to be encapsulated in quotes)
                //  need to check for this....
                while (nextTerm < setFormula.length() && (stringUtils.precededBy(setFormula, AS, nextTerm) || stringUtils.precededBy(setFormula, TO, nextTerm) || stringUtils.precededBy(setFormula, FROM, nextTerm) || stringUtils.precededBy(setFormula, AS, nextTerm))) {
                    int startPos = nextTerm + 1;
                    nextTerm = setFormula.length() + 1;
                    m = p.matcher(setFormula.substring(startPos));
                    if (m.find()) {
                        nextTerm = m.start() + startPos;
                    }
                }
            }
            if (op == NAMEMARKER) {
                stackCount++;
                // new logic - we trust interpret set term to return an ordered set if it thinks it necessary.
                // ok there's a good change the set returned is immutable, need to take this into account!
                nameStack.add(interpretSetTerm(setFormula.substring(pos, nextTerm - 1), formulaStrings, referencedNames, attributeStrings));
            } else if (stackCount-- < 2) {
                throw new Exception("not understood:  " + setFormula);
            } else if (op == '*') { // * meaning intersection here . . .
                //assume that the second term implies 'level all'
                long start = System.currentTimeMillis();
                System.out.println("starting * set sizes  nameStack(stackcount)" + nameStack.get(stackCount).size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).size());
                Collection<Name> previousSet = nameStack.get(stackCount - 1);
                // testing shows no harm, should be a bit faster and better on memory.
                // Should I be testing sizes for the most efficient iterator? With the extra name it's a bit different in terms of logic
                Set<Name> setIntersection = HashObjSets.newMutableSet();
                for (Name name : nameStack.get(stackCount)) {
                    if (previousSet.contains(name)) {
                        setIntersection.add(name);
                    }
                    for (Name child : name.findAllChildren(false)) {
                        if (previousSet.contains(child)) {
                            setIntersection.add(child);
                        }
                    }
                }
                nameStack.set(stackCount - 1, setIntersection); // replace the previous set
                System.out.println("after new retainall " + (System.currentTimeMillis() - start) + "ms");
                nameStack.remove(stackCount);
            } else if (op == '/') { // this can be slow : todo - speed up? Is it the retainall? Should I be using sets?
                //new HashSet<>(nameStack.get(stackCount));
                // do we have to make a new set?
                //System.out.println("pre mutable init " + ((runtime.totalMemory() - runtime.freeMemory()) / mb));
                Set<Name> parents = HashObjSets.newMutableSet(nameStack.get(stackCount)); // ok since what's on namestack is now immutable I guess we need to copy this
                long start = System.currentTimeMillis();
                long heapMarker = ((runtime.totalMemory() - runtime.freeMemory()) / mb);
                //System.out.println("aft mutable init " + heapMarker);
                System.out.println("starting / set sizes  nameStack(stackcount)" + nameStack.get(stackCount).size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).size());
                for (Name child : nameStack.get(stackCount)) {
                    Name.findAllParents(child, parents); // new call to static funciton should cut garbage generation a lot
                }
                long now = System.currentTimeMillis();
                System.out.println("find all parents in parse query part 1 " + (now - start) + " set sizes parents " + parents.size() + " heap increase = " + (((runtime.totalMemory() - runtime.freeMemory()) / mb) - heapMarker) + "MB");
                start = now;
                //nameStack.get(stackCount - 1).retainAll(parents); //can't do this any more, need to make a new one
                Collection<Name> previousSet = nameStack.get(stackCount - 1);
                Set<Name> setIntersection = HashObjSets.newMutableSet(); // testing shows no harm, should be a bit faster and better on memory
                if (previousSet.size() < parents.size()){ // since contains should be the same regardless of set size we want to iterate the smaller one to create the intersection
                    for (Name name : previousSet) {
                        if (parents.contains(name)) {
                            setIntersection.add(name);
                        }
                    }
                } else {
                    for (Name name : parents) {
                        if (previousSet.contains(name)) {
                            setIntersection.add(name);
                        }
                    }
                }
                System.out.println("after retainall " + (System.currentTimeMillis() - start));
                nameStack.set(stackCount - 1, setIntersection); // replace the previous set
                nameStack.remove(stackCount);
            } else if (op == '-') {
                // using immutable sets on the stack causes more code here but populating the stack should be MUCH faster
                //nameStack.get(stackCount - 1).removeAll(nameStack.get(stackCount));
                Collection<Name> currentSet = nameStack.get(stackCount);
                Collection<Name> previousSet = nameStack.get(stackCount - 1);
                Set<Name> result = HashObjSets.newMutableSet();
                for (Name name : previousSet) {
                    if (!currentSet.contains(name)) { // only the ones not in the current set
                        result.add(name);
                    }
                }
                nameStack.set(stackCount - 1, result); // replace the previous set
                nameStack.remove(stackCount);
            } else if (op == '+') {
                //nameStack.get(stackCount - 1).addAll(nameStack.get(stackCount));
                Set<Name> result = HashObjSets.newMutableSet(nameStack.get(stackCount - 1));
                result.addAll(nameStack.get(stackCount));
                nameStack.set(stackCount - 1, result); // replace the previous set
                nameStack.remove(stackCount);
            } else if (op == ASSYMBOL) {
                Name totalName = nameStack.get(stackCount).iterator().next(); // alternative if we're using a linked hash set
                totalName.setChildrenWillBePersisted(nameStack.get(stackCount - 1));
                nameStack.remove(stackCount);
                // can't do that any more
                //nameStack.get(stackCount - 1).clear();
                //nameStack.get(stackCount - 1).add(totalName);
                // should be the same
                Set<Name> totalNameSet = HashObjSets.newMutableSet();
                totalNameSet.add(totalName);
                nameStack.set(stackCount - 1, totalNameSet);
            }
            pos = nextTerm;
        }

        boolean hasPermissions = false;
        if (azquoMemoryDBConnection.getReadPermissions().size() > 0) {
            hasPermissions = true;
        }
        if (hasPermissions || azquoMemoryDBConnection.getAzquoMemoryDB().attributeExistsInDB("CONFIDENTIAL")) { // then we need to check permissions etc
            for (Name possible : nameStack.get(0)) {
                if (possible == null || (possible.getAttribute("CONFIDENTIAL") == null && (!hasPermissions || isAllowed(possible, azquoMemoryDBConnection.getReadPermissions())))) {
                    toReturn.add(possible);
                }
            }
        } else { // just make a copy as list and return. This colleciton copying all over the pace bothers me a bit.
            toReturn.addAll(nameStack.get(0));
        }
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

    private static AtomicInteger attributeListCount = new AtomicInteger(0);

    public List<String> attributeList(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        attributeListCount.incrementAndGet();
        return azquoMemoryDBConnection.getAzquoMemoryDB().getAttributes();
    }

    private static AtomicInteger findAllParentsCount = new AtomicInteger(0);

/*    private void findAllParents(Name name, final Set<Name> allParents) {
        findAllParentsCount.incrementAndGet();
            for (Name parent : name.getParents()) {
                if (allParents.add(parent)) {
                    findAllParents(parent, allParents);
                }
            }
    }*/

    private static AtomicInteger dedupeOneCount = new AtomicInteger(0);

    private void dedupeOne(Name name, Set<Name> possibles, Name rubbishBin) throws Exception {
        dedupeOneCount.incrementAndGet();
        for (Name child2 : possibles) {
            if (child2.getId() != name.getId()) {
                Set<Name> existingChildren = new HashSet<>(child2.getChildren());
                for (Name grandchild : existingChildren) {
                    name.addChildWillBePersisted(grandchild);
                    child2.removeFromChildrenWillBePersisted(grandchild);
                }
                Set<Name> existingParents = new HashSet<>(child2.getParents());
                for (Name parentInLaw : existingParents) {
                    parentInLaw.addChildWillBePersisted(name);
                    parentInLaw.removeFromChildrenWillBePersisted(child2);
                }
                name.transferValues(child2);
                child2.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, "duplicate-" + child2.getDefaultDisplayName());

                rubbishBin.addChildWillBePersisted(child2);

            }
        }

    }

    private static AtomicInteger deduplicateCount = new AtomicInteger(0);

    private List<Name> deduplicate(AzquoMemoryDBConnection azquoMemoryDBConnection, String formula) throws Exception {
        deduplicateCount.incrementAndGet();
        List<Name> toReturn = new ArrayList<>();
        int toPos = formula.indexOf(" to ");
        if (toPos < 0) return toReturn;
        String baseSet = formula.substring(0, toPos);
        String binSet = formula.substring(toPos + 4);
        Name rubbishBin = findOrCreateNameInParent(azquoMemoryDBConnection, binSet, null, false);
        Collection<Name> names = parseQuery(azquoMemoryDBConnection, baseSet);
        if (names.size() == 0) return toReturn;
        if (names.size() > 1) {
            Map<String, Set<Name>> nameMap = new HashMap<>();
            for (Name name : names) {
                String nameString = name.getDefaultDisplayName();
                if (nameMap.get(nameString) == null) {
                    nameMap.put(nameString, new HashSet<>());
                }
                nameMap.get(nameString).add(name);
            }
            for (String nameString : nameMap.keySet()) {
                if (nameMap.get(nameString).size() > 1) {
                    Set<Name> dups = nameMap.get(nameString);
                    dedupeOne(dups.iterator().next(), dups, rubbishBin);
                }
            }
            toReturn.add(rubbishBin);
            return toReturn;
        }
        Name name = names.iterator().next();
        List<String> languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        for (Name child : name.findAllChildren(false)) {
            if (!rubbishBin.getChildren().contains(child)) {
                Set<Name> possibles = azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(languages, child.getDefaultDisplayName(), name);
                if (possibles.size() > 1) {
                    dedupeOne(child, possibles, rubbishBin);
                }
            }
        }
        toReturn.add(rubbishBin);
        azquoMemoryDBConnection.persist();
        return toReturn;
    }

    private static AtomicInteger inParentSetCount = new AtomicInteger(0);

    public Name inParentSet(Name name, Collection<Name> maybeParents) {
        inParentSetCount.incrementAndGet();
        if (maybeParents.contains(name)) {
            return name;
        }
        for (Name parent : name.getParents()) {
            Name maybeParent = inParentSet(parent, maybeParents);
            if (maybeParent != null) {
                return maybeParent;
            }
        }
        return null;
    }

    private static AtomicInteger isAllowedCount = new AtomicInteger(0);

    public boolean isAllowed(Name name, List<Set<Name>> names) {
        isAllowedCount.incrementAndGet();
        if (name == null || names == null || names.isEmpty()) { // empty the same as null
            return true;
        }
         /*
         * check if name is in one relevant set from names.  If so then OK
         * If not, then depends if the name is confidential.
          *
          * */
        for (Set<Name> listNames : names) {
            if (!listNames.isEmpty()) {
                //Name listName = listNames.iterator().next();//all names in each list have the same topparent, so don't try further (just get the first)
                if (inParentSet(name, listNames) != null) {
                    return true;
                }
            }
        }
        String confidential = name.getAttribute("CONFIDENTIAL");
        return confidential == null || !confidential.equalsIgnoreCase("true");
    }

    private static AtomicInteger filterCount = new AtomicInteger(0);

    // since what it's passed could be immutable need to return
    // not sure of the scope for optimiseation
    private Collection<Name> filter(Collection<Name> names, String condition, List<String> strings, List<String> attributeNames) {
        filterCount.incrementAndGet();
        //NOT HANDLING 'OR' AT PRESENT
        int andPos = condition.toLowerCase().indexOf(" and ");
        Collection<Name> namesToReturn;
        // again feels hacky :P, I'm looking to preserve ordering, can't build the list in order as removing isn't in order
        // so need new collections, hopefully this won't be called much on big sets.
        if (names instanceof List){
            namesToReturn = new ArrayList<>(names);
        } else {
            namesToReturn = HashObjSets.newMutableSet(names); // need a mutable one
        }
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

                if (clauseLhs.charAt(0) == ATTRIBUTEMARKER) {// we need to replace it
                    clauseLhs = attributeNames.get(Integer.parseInt(clauseLhs.substring(1, 3)));
                }
                if (clauseRhs.charAt(0) == ATTRIBUTEMARKER) {// we need to replace it
                    clauseRhs = attributeNames.get(Integer.parseInt(clauseRhs.substring(1, 3)));
                }
                String valRhs = "";
                boolean fixed = false;
                if (clauseRhs.charAt(0) == '"') {
                    valRhs = strings.get(Integer.parseInt(clauseRhs.substring(1, 3)));// anything left in quotes is referenced in the strings list
                    fixed = true;
                }

                Set<Name> namesToRemove = HashObjSets.newMutableSet();
                for (Name name : namesToReturn) {
                    String valLhs = name.getAttribute(clauseLhs);
                    if (valLhs == null) {
                        valLhs = "";
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
                namesToReturn.removeAll(namesToRemove);
            }
            lastPos = andPos + 5;
            andPos = condition.toLowerCase().indexOf(" and ", lastPos);
        }
        return namesToReturn;
    }

    // Ok I'm now making this a set with the caveat that it may be a linked hash set if there's ordering. Also we assume that returned sets won't be modified.
    // Some use of instanceof depending on wwhether we need ordering. Feels a bit hacky, might be able to clean up the logic

    private static AtomicInteger interpretSetTermCount = new AtomicInteger(0);

    private Set<Name> interpretSetTerm(String setTerm, List<String> strings, List<Name> referencedNames, List<String> attributeStrings) throws Exception {
        interpretSetTermCount.incrementAndGet();
        //System.out.println("interpret set term . . ." + setTerm);
        final String levelString = stringUtils.getInstruction(setTerm, LEVEL);
        String fromString = stringUtils.getInstruction(setTerm, FROM);
        String parentsString = stringUtils.getInstruction(setTerm, PARENTS);
        String childrenString = stringUtils.getInstruction(setTerm, CHILDREN);
        final String sorted = stringUtils.getInstruction(setTerm, SORTED);
        String toString = stringUtils.getInstruction(setTerm, TO);
        String countString = stringUtils.getInstruction(setTerm, COUNT);
        final String countbackString = stringUtils.getInstruction(setTerm, COUNTBACK);
        final String compareWithString = stringUtils.getInstruction(setTerm, COMPAREWITH);
        //String totalledAsString = stringUtils.getInstruction(setTerm, AS);
        String selectString = stringUtils.getInstruction(setTerm, SELECT);
        // removed totalled as

        //final String associatedString = stringUtils.getInstruction(setTerm, ASSOCIATED);
        int wherePos = setTerm.toLowerCase().indexOf(WHERE.toLowerCase());
        String whereString = null;
        if (wherePos >= 0) {
            whereString = setTerm.substring(wherePos + 6);//the rest of the string???   maybe need 'group by' in future
        }
        if (levelString != null) {
            childrenString = "true";
        }
        Collection<Name> namesFound = new ArrayList<>(); // default for a single name?
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
            namesFound.add(name); // one in the arraylist
        } else {
            namesFound = findChildrenAtLevel(name, levelString); // reassign names from the find children clause
            if (fromString == null) fromString = "";
            if (toString == null) toString = "";
            if (countString == null) countString = "";
            // SECOND  Sort if necessary

            //THIRD  trim that down to the subset defined by from, to, count
            if (fromString.length() > 0 || toString.length() > 0 || countString.length() > 0) {
                if (namesFound instanceof List){ // yeah I know some say this is not best practice but hey ho
                    namesFound = findChildrenFromToCount((List<Name>)namesFound, fromString, toString, countString, countbackString, compareWithString, referencedNames);
                } else {
                    System.out.println("can't from/to/count a non-list, " + setTerm);
                }
            }
        }
        if (whereString != null) {
            namesFound = filter(namesFound, whereString, strings, attributeStrings);
        }
        // could parents and select be more efficient?
        if (parentsString != null) {
            //remove the childless names
            Collection<Name> withChildren;
            // ergh again :P
            if (namesFound instanceof List){
                withChildren = new ArrayList<>();
            } else {
                withChildren = HashObjSets.newMutableSet();
            }
            for (Name possibleName : namesFound) {
                if (possibleName.hasChildren()) {
                    withChildren.add(possibleName);
                }
            }
            namesFound = withChildren;
        }
        if (selectString != null) {
            String toFind = strings.get(Integer.parseInt(selectString.substring(1, 3))).toLowerCase();
            Collection<Name> selectedNames;
            if (namesFound instanceof List){
                selectedNames = new ArrayList<>();
            } else {
                selectedNames = HashObjSets.newMutableSet();
            }
            for (Name sname : namesFound) {
                if (sname != null && sname.getDefaultDisplayName() != null // is this checking to make intellij happy that important, maybe I want an NPE?
                        && sname.getDefaultDisplayName().toLowerCase().contains(toFind)) {
                    selectedNames.add(sname);
                }
            }
            namesFound = selectedNames;
        }
        if (sorted != null) {
            if (!(namesFound instanceof List)){ // it's a set, need to wrap, hope it's not a big set
                namesFound = new ArrayList<>(namesFound);
            }
            Collections.sort((List<Name>)namesFound, defaultLanguageCaseInsensitiveNameComparator);
        }
        if (namesFound instanceof List) { // it's a list but I want a set,
            return new LinkedHashSet<>(namesFound); // want to avoid this under most circumstances
        }
        if (namesFound instanceof Set) { // what I hope it will be mopst of the time
            return (Set<Name>) namesFound;
        }
        System.out.println("unexpected collection type in interpter set term : " + setTerm);
        return null;
    }


    public static void printFunctionCountStats() {
        System.out.println("######### NAME SERVICE FUNCTION COUNTS");

        System.out.println("nameCompareCount\t\t\t\t\t\t\t\t" + nameCompareCount.get());
        System.out.println("decodeStringCount\t\t\t\t\t\t\t\t" + decodeStringCount.get());
        System.out.println("getNameListFromStringListCount\t\t\t\t\t\t\t\t" + getNameListFromStringListCount.get());
        System.out.println("findContainingNameCount\t\t\t\t\t\t\t\t" + findContainingNameCount.get());
        System.out.println("findByIdCount\t\t\t\t\t\t\t\t" + findByIdCount.get());
        System.out.println("getNameByAttributeCount\t\t\t\t\t\t\t\t" + getNameByAttributeCount.get());
        System.out.println("findByNameCount\t\t\t\t\t\t\t\t" + findByNameCount.get());
        System.out.println("findByName2Count\t\t\t\t\t\t\t\t" + findByName2Count.get());
        System.out.println("clearChildrenCount\t\t\t\t\t\t\t\t" + clearChildrenCount.get());
        System.out.println("findTopNamesCount\t\t\t\t\t\t\t\t" + findTopNamesCount.get());
        System.out.println("findOrCreateNameStructureCount\t\t\t\t\t\t\t\t" + findOrCreateNameStructureCount.get());
        System.out.println("findOrCreateNameStructure2Count\t\t\t\t\t\t\t\t" + findOrCreateNameStructure2Count.get());
        System.out.println("includeInSetCount\t\t\t\t\t\t\t\t" + includeInSetCount.get());
        System.out.println("findOrCreateNameInParentCount\t\t\t\t\t\t\t\t" + findOrCreateNameInParentCount.get());
        System.out.println("findOrCreateNameInParent2Count\t\t\t\t\t\t\t\t" + findOrCreateNameInParent2Count.get());
        System.out.println("findChildrenAtLevelCount\t\t\t\t\t\t\t\t" + findChildrenAtLevelCount.get());
        System.out.println("addNamesCount\t\t\t\t\t\t\t\t" + addNamesCount.get());
        System.out.println("getNameFromListAndMarkerCount\t\t\t\t\t\t\t\t" + getNameFromListAndMarkerCount.get());
        System.out.println("findChildrenFromToCount\t\t\t\t\t\t\t\t" + findChildrenFromToCount.get());
        System.out.println("parseQueryCount\t\t\t\t\t\t\t\t" + parseQueryCount.get());
        System.out.println("parseQuery2Count\t\t\t\t\t\t\t\t" + parseQuery2Count.get());
        System.out.println("parseQuery3Count\t\t\t\t\t\t\t\t" + parseQuery3Count.get());
        System.out.println("attributeListCount\t\t\t\t\t\t\t\t" + attributeListCount.get());
        System.out.println("findAllParentsCount\t\t\t\t\t\t\t\t" + findAllParentsCount.get());
        System.out.println("dedupeOneCount\t\t\t\t\t\t\t\t" + dedupeOneCount.get());
        System.out.println("deduplicateCount\t\t\t\t\t\t\t\t" + deduplicateCount.get());
        System.out.println("inParentSetCount\t\t\t\t\t\t\t\t" + inParentSetCount.get());
        System.out.println("isAllowedCount\t\t\t\t\t\t\t\t" + isAllowedCount.get());
        System.out.println("filterCount\t\t\t\t\t\t\t\t" + filterCount.get());
        System.out.println("interpretSetTermCount\t\t\t\t\t\t\t\t" + interpretSetTermCount.get());
    }

    public static void clearFunctionCountStats() {
        nameCompareCount.set(0);
        decodeStringCount.set(0);
        getNameListFromStringListCount.set(0);
        findContainingNameCount.set(0);
        findByIdCount.set(0);
        getNameByAttributeCount.set(0);
        findByNameCount.set(0);
        findByName2Count.set(0);
        clearChildrenCount.set(0);
        findTopNamesCount.set(0);
        findOrCreateNameStructureCount.set(0);
        findOrCreateNameStructure2Count.set(0);
        includeInSetCount.set(0);
        findOrCreateNameInParentCount.set(0);
        findOrCreateNameInParent2Count.set(0);
        findChildrenAtLevelCount.set(0);
        addNamesCount.set(0);
        getNameFromListAndMarkerCount.set(0);
        findChildrenFromToCount.set(0);
        parseQueryCount.set(0);
        parseQuery2Count.set(0);
        parseQuery3Count.set(0);
        attributeListCount.set(0);
        findAllParentsCount.set(0);
        dedupeOneCount.set(0);
        deduplicateCount.set(0);
        inParentSetCount.set(0);
        isAllowedCount.set(0);
        filterCount.set(0);
        interpretSetTermCount.set(0);
    }
}