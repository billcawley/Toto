package com.azquo.memorydb.service;

import com.azquo.memorydb.Constants;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.memorydb.core.Name;
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
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * <p>
 * Ok, outside of the memorydb package this may be the the most fundamental class.
 * <p>
 * I've tried to move most of the string parsing to StringUtils.
 * <p>
 * While this has been tidied a fair bit it could do with more I think, arranging of logic, checking of function/variable names etc.
 * <p>
 * I mentioned the string parsing above - it can lead to code that's doing something fairly simple but looks complex if we're not careful.
 */
public final class NameService {

    @Autowired
    ValueService valueService;//used only in formatting children for output
    @Autowired
    DSSpreadsheetService dsSpreadsheetService;//used only in formatting children for output

    private StringUtils stringUtils = new StringUtils(); // just make it quickly like this for the mo

    private static final Logger logger = Logger.getLogger(NameService.class);

    public static final String LEVEL = "level";
    public static final String PARENTS = "parents";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String COUNT = "count";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String ATTRIBUTESET = "attributeset";
    public static final String SELECT = "select";
    //public static final String LOWEST = "lowest";
    //public static final String ALL = "all";
    public static final char NAMEMARKER = '!';
    public static final char ATTRIBUTEMARKER = '|';
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";
    public static final String COUNTBACK = "countback";
    public static final String COMPAREWITH = "comparewith";
    public static final String AS = "as";
    public static final char ASSYMBOL = '@';
    public static final String WHERE = "where";
    private static final String languageIndicator = "<-";

    // hopefully thread safe??
    private static AtomicInteger nameCompareCount = new AtomicInteger(0);

    private final Comparator<Name> defaultLanguageCaseInsensitiveNameComparator = (n1, n2) -> {
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

    // get names from a comma separated list. Well expressions describing names - only used for read and write permissions at the moment.

    private static AtomicInteger decodeStringCount = new AtomicInteger(0);

    public final List<Set<Name>> decodeString(AzquoMemoryDBConnection azquoMemoryDBConnection, String searchByNames, List<String> attributeNames) throws Exception {
        decodeStringCount.incrementAndGet();
        final List<Set<Name>> toReturn = new ArrayList<>();
        List<String> formulaStrings = new ArrayList<>();
        List<String> nameStrings = new ArrayList<>();
        List<String> attributeStrings = new ArrayList<>(); // attribute names is taken. Perhaps need to think about function parameter names
        searchByNames = stringUtils.prepareStatement(searchByNames, nameStrings, formulaStrings, attributeStrings);
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

    // we replace the names with markers for parsing. Then we need to resolve them later, here is where the exception will be thrown. Should be NameNotFoundException?

    private static AtomicInteger getNameListFromStringListCount = new AtomicInteger(0);

    List<Name> getNameListFromStringList(List<String> nameStrings, AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> attributeNames) throws Exception {
        getNameListFromStringListCount.incrementAndGet();
        List<Name> referencedNames = new ArrayList<>(nameStrings.size());
        for (String nameString : nameStrings) {
            Name toAdd = findNameAndAttribute(azquoMemoryDBConnection, nameString, attributeNames);
            if (toAdd == null) {
                throw new Exception("error: cannot resolve reference to a name " + nameString);
            }
            referencedNames.add(toAdd);
        }
        return referencedNames;
    }

    /* renaming this function to match the memory DB one - it's a straight proxy through except for trying for all
    attributes if it can't find any names for the specified attribute and sorting the result (hence loading the set in an arraylist)
     */

    private static AtomicInteger findContainingNameCount = new AtomicInteger(0);

    public ArrayList<Name> getNamesWithAttributeContaining(final AzquoMemoryDBConnection azquoMemoryDBConnection, String attribute, final String searchString) {
        findContainingNameCount.incrementAndGet();
        ArrayList<Name> namesList = new ArrayList<>(azquoMemoryDBConnection.getAzquoMemoryDB().getNamesWithAttributeContaining(attribute, searchString));
        if (namesList.size() == 0 && attribute.length() > 0) {
            namesList = getNamesWithAttributeContaining(azquoMemoryDBConnection, "", searchString);//try all the attributes
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

    private Name getNameByAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeValue, Name parent, final List<String> attributeNames) throws Exception {
        getNameByAttributeCount.incrementAndGet();
        // attribute value null? Can it happen?
        if (attributeValue.length() > 0 && attributeValue.charAt(0) == NAMEMARKER) {
            throw new Exception("error: getNameByAttribute should no longer have name marker passed to it!");
        }
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, attributeValue.replace(Name.QUOTE, ' ').trim(), parent);
    }

    private static AtomicInteger findByNameCount = new AtomicInteger(0);

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) throws Exception {
        findByNameCount.incrementAndGet();
        return findByName(azquoMemoryDBConnection, name, Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME));
    }

    private static AtomicInteger findByName2Count = new AtomicInteger(0);

    private Name findParentAttributesName(Name child, String attributeName, Set<Name> checked) {
        attributeName = attributeName.trim().toUpperCase();
        for (Name parent : child.getParents()) {
            if (!checked.contains(parent)) {
                checked.add(parent);
                if (parent.getDefaultDisplayName() != null && parent.getDefaultDisplayName().equalsIgnoreCase(attributeName)) {
                    return child;
                }
                Name attribute = findParentAttributesName(parent, attributeName, checked);
                if (attribute != null) {
                    return attribute;
                }
            }
        }
        return null;
    }

    private Name findNameAndAttribute(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String qualifiedName, final List<String> attributeNames) throws Exception {
        int attPos = qualifiedName.lastIndexOf("`.`");
        if (attPos > 0) {
            Name parentFound = findNameAndAttribute(azquoMemoryDBConnection, qualifiedName.substring(0, attPos + 1), attributeNames);
            if (parentFound == null) return null;
            String attribute = qualifiedName.substring(attPos + 2).replace("`", "");
            Name attName = findParentAttributesName(parentFound, attribute, HashObjSets.newMutableSet());
            if (attName == null) {//see if we can find a name from the string value
                String attVal = parentFound.getAttribute(attribute);
                if (attVal != null) {
                    return findByName(azquoMemoryDBConnection, attVal);
                }
            }
            return attName;
        } else {
            return findByName(azquoMemoryDBConnection, qualifiedName, attributeNames);
        }
    }

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, String qualifiedName, final List<String> attributeNames) throws Exception {
        findByName2Count.incrementAndGet();
        // This routine accepts multiple 'memberof (->) symbols.  It also checks for the 'language indicator' (<-)
        // note if qualifiedName is null this will NPE - not sure if this is a problem
        int langPos = qualifiedName.indexOf(languageIndicator);
        if (langPos > 0) {
            int quotePos = qualifiedName.indexOf(Name.QUOTE);
            if (quotePos < 0 || quotePos > langPos) {
                attributeNames.clear();
                attributeNames.add(qualifiedName.substring(0, langPos));
                qualifiedName = qualifiedName.substring(langPos + 2);
            }
        }

        if (qualifiedName.length() == 0) return null;
        List<String> parents = stringUtils.parseNameQualifiedWithParents(qualifiedName); // should never return an empty array given the check just now on qualified name
        String name = parents.remove(parents.size() - 1); // get the name off the end of the list now should just be parents in top to bottom order
        Set<Name> possibleParents = null;
        for (String parent : parents) {
            if (possibleParents == null) { // will happen on the first one
                // most of the time would only be one but the name on the right (top for this expression) might not be a top name in the DB hence there could be multiple
                possibleParents = azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(attributeNames, parent, null);
            } else {
                Set<Name> nextPossibleParents = HashObjSets.newMutableSet();
                for (Name possibleParent : possibleParents) {
                    Name foundParent = getNameByAttribute(azquoMemoryDBConnection, parent, possibleParent, attributeNames);
                    if (foundParent != null) {
                        nextPossibleParents.add(foundParent);
                    }
                }
                possibleParents = nextPossibleParents;
            }
            if (possibleParents == null || possibleParents.size() == 0) { // unable to find this level of parents - stop now
                return null;
            }
        }
        if (possibleParents == null) {
            return getNameByAttribute(azquoMemoryDBConnection, name, null, attributeNames);
        } else {
            for (Name parent : possibleParents) {
                Name found = getNameByAttribute(azquoMemoryDBConnection, name, parent, attributeNames);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // used by sets import - should this be a function against name?

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

    public Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String qualifiedName, Name topParent, boolean local, List<String> attributeNames) throws Exception {
        findOrCreateNameStructure2Count.incrementAndGet();
        // see findByName for structure string style
        // note a null/empty qualifiedName will end up returning topParent

        List<String> nameAndParents = stringUtils.parseNameQualifiedWithParents(qualifiedName); // should never return an empty array given the check just now on qualified name
        if (nameAndParents.size() == 1) { // no structure just the name, pass on through
            return findOrCreateNameInParent(azquoMemoryDBConnection, qualifiedName, topParent, local, attributeNames);
        }
       /*
        ok the key here is to step through the parent -> child list as defined in the name string creating the hierarchy as you go along
        the top parent is used for the first one - inside findOrCreateNameInParent local will make the distinction internally to allow names
         with the same name in different places or to move the name from where it was. Moving the parsing to parseNameQualifiedWithParents has mad this a lot simpler
        hopefully it's all still sound.
        */
        Name nameOrParent = topParent;
        // given the separated parsing this is much more simple, creating down the chain but starting with the very top parent. Could have left the
        for (String nameParentString : nameAndParents) {
            nameOrParent = findOrCreateNameInParent(azquoMemoryDBConnection, nameParentString, nameOrParent, local, attributeNames);
        }
        return nameOrParent; // the last one created is what we want
    }

    private static AtomicInteger includeInSetCount = new AtomicInteger(0);

    private void includeInSet(Name name, Name set) throws Exception {
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

    private Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new ConcurrentHashMap<>();

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
    // aside from the odd logic to get existing I think this is fairly clear
    private static AtomicInteger findOrCreateNameInParent2Count = new AtomicInteger(0);

    public Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name parent, boolean local, List<String> attributeNames) throws Exception {
        findOrCreateNameInParent2Count.incrementAndGet();
        long marker = System.currentTimeMillis();
        if (name == null || name.length() == 0) {
            return null;
            // dammit can't throw this just yet, caused a problem for callum, to invstigate
//            throw new Exception("Name to be created is blank or null!");
        }
     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an dataimport),and insert a structure into it*/
        if (attributeNames == null) {
            attributeNames = Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME);
        }

        String storeName = name.replace(Name.QUOTE, ' ').trim();
        Name existing;

        if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent1", marker);
        if (parent != null) { // ok try to find it in that parent
            //try for an existing name already with the same parent
            if (local) {// ok looking only below that parent or just in it's whole set or top parent.
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, parent);
                if (existing == null) { // couldn't find local - try for one which has no parents or children, that's allowable for local (to be moved)
                    existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
                    if (existing != null && (existing.hasParents() || existing.hasChildren())) {
                        existing = null;
                    }
                }
                if (profile)
                    marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent2", marker);
            } else {// so we ignore parent if not local, we'll grab what we can to move it into the right parent set
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
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
            // I think provenance from connection is correct, we should be looking to make a useful provenance when making the connection from the data access token
            Name newName = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), true); // default additive to true
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

    // in order to avoid unnecessary collection copying (we could be talking millions of names) I made this little container to move a collection that could be a list or set and possibly mutable

    private static class NameSetList {
        public final Set<Name> set;
        public final List<Name> list;
        final boolean mutable;

        NameSetList(Set<Name> set, List<Name> list, boolean mutable) {
            this.set = set;
            this.list = list;
            this.mutable = mutable;
        }

        // make from an existing (probably immutable) one
        NameSetList(NameSetList nameSetList) {
            set = nameSetList.set != null ? HashObjSets.newMutableSet(nameSetList.set) : null;
            list = nameSetList.list != null ? new ArrayList<>(nameSetList.list) : null;
            mutable = true;
        }

        Collection<Name> getAsCollection() {
            return set != null ? set : list != null ? list : null;
        }
    }

    public static final int LOWEST_LEVEL_INT = 100;
    private static final int ALL_LEVEL_INT = 101;

    // a loose type of return as we might be pulling sets or lists from names - todo - custom object

    private static AtomicInteger findChildrenAtLevelCount = new AtomicInteger(0);

    private NameSetList findChildrenAtLevel(final Name name, final String levelString) throws Exception {
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
        if (level < 0){
            return findParentsAtLevel(name,-level);
        }

        if (level == 1) { // then no need to get clever, just return the children
            if (name.hasChildrenAsSet()) {
                return new NameSetList(name.getChildrenAsSet(), null, false);
            } else {
                return new NameSetList(null, name.getChildrenAsList(), false);
            }
        }
        if (level == ALL_LEVEL_INT) {
            System.out.println("ALL_LEVEL_INT called on " + name.getDefaultDisplayName() + ", annoying!");
            // new logic! All names is the name find all children plus itself. A bit annoying to make a copy but there we go
            Set<Name> toReturn = HashObjSets.newMutableSet(name.findAllChildren(false));
            toReturn.add(name); // a whole new set just to add this, grrr
            return new NameSetList(toReturn, null, true); // at least we're now flagging this up : you can modify this set
        }
        /* To explain the nasty hack here with the linked hash set : it was just list but we don't want duplicates. So use LinkedHashSet
            which is still ordered but unfortunately is not sortable, need a list for that, hence why it's re-wrapped in an array list below.
           This is probably not efficient but I'm assuming a non all or level one ordered is fairly rare
          */
        Set<Name> namesFoundOrderedSet = new LinkedHashSet<>();
        Set<Name> namesFoundSet = HashObjSets.newMutableSet();
        boolean ordered = false;
        if (!name.hasChildrenAsSet()) {
            ordered = true;
            for (Name check : name.getChildren()) {
                if (check.hasChildrenAsSet()) { // then these children are unordered, I'm going to say the whole lot doesn't need to be ordered
                    ordered = false;
                    break;
                }
            }
        }
        // has been moved to name to directly access contents of name hopefully increasing speed and saving on garbage generation
        Name.addNames(name, ordered ? namesFoundOrderedSet : namesFoundSet, 0, level);
        return new NameSetList(ordered ? null : namesFoundSet, ordered ? new ArrayList<>(namesFoundOrderedSet) : null, true); // it will be mutable either way
    }

    private NameSetList findParentsAtLevel(final Name name, int level) throws Exception {
        findChildrenAtLevelCount.incrementAndGet();
        if (level == 1) { // then no need to get clever, just return the parents
            if (name.hasParentsAsSet()) {
                return new NameSetList(name.getParentsAsSet(), null, false);
            } else {
                return new NameSetList(null, name.getParentsAsList(), false);
            }
        }
        // parents are not ordered like children, use a Set
        Set<Name> namesFoundSet = HashObjSets.newMutableSet();
        // has been moved to name to directly access contents of name hopefully increasing speed and saving on garbage generation
        Name.addParentNames(name, namesFoundSet, 0, level);
        return new NameSetList(namesFoundSet, null, true); // it will be mutable either way
    }

    private static AtomicInteger addNamesCount = new AtomicInteger(0);

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

    Name getNameFromListAndMarker(String nameMarker, List<Name> nameList) throws Exception {
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

    private NameSetList constrainNameListFromToCount(NameSetList nameSetList, String fromString, String toString, final String countString, final String countBackString, final String compareWithString, List<Name> referencedNames) throws Exception {
        if (nameSetList.list == null) {
            return nameSetList; // don't bother trying to constrain a non list
        }
        findChildrenFromToCount.incrementAndGet();
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
                if (fromEnd) to = nameSetList.list.size() - to;
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

    // for deduplicate, inspect search and definition. This is teh same as below and
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

    private Runtime runtime = Runtime.getRuntime();

    /* todo : sort exceptions? Move to another class?
    todo - cache option in here
    now uses NameSetList to move connections of names around and only copy them as necessary. Has made the logic a little more complex
    in places but performance should be better and garbage generation reduced*/
    private static AtomicInteger parseQuery3Count = new AtomicInteger(0);

    public final Collection<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames, Collection<Name> toReturn) throws Exception {
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
        int languagePos = setFormula.indexOf(languageIndicator);
        if (languagePos > 0) {
            int namePos = setFormula.indexOf(Name.QUOTE);
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
        Name possibleName = findByName(azquoMemoryDBConnection, setFormula, languages);
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
            return handleEdit(azquoMemoryDBConnection, setFormula.substring(5).trim(), languages);
        }
        setFormula = stringUtils.prepareStatement(setFormula, nameStrings, attributeStrings, formulaStrings);
        List<Name> referencedNames;
        try {
            referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, languages);
        } catch (Exception e) {
            if (setFormula.toLowerCase().equals("!00 children")) return new ArrayList<>();// what is this??
            throw e;
        }
        setFormula = setFormula.replace(AS, ASSYMBOL + "");
        setFormula = stringUtils.shuntingYardAlgorithm(setFormula);
        Pattern p = Pattern.compile("[\\+\\-\\*/" + NAMEMARKER + NameService.ASSYMBOL + "&]");//recognises + - * / NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line
        boolean resetDefs = false;
        logger.debug("Set formula after SYA " + setFormula);
        int pos = 0;
        int stackCount = 0;
        //int stringCount = 0;
        // now to act on the formulae which has been converted to Reverse Polish, hence stack based parsing and no brackets etc.
        // NOTE THAT THE SHUNTING YARD ALGORITHM HERE LEAVES FUNCTIONS AT THE START (e.g. Attributeset)
        int attSetStack = 0;//not ideal...
        while (pos < setFormula.length()) {
            if (setFormula.substring(pos).trim().toLowerCase().startsWith(NameService.ATTRIBUTESET)) {
                pos = setFormula.indexOf(NameService.ATTRIBUTESET) + NameService.ATTRIBUTESET.length();
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
                    throw new Exception(formulaCopy + " " + NameService.ATTRIBUTESET + " not understood");
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
                    // now returns a custom little object that hods a list a set and whether it's immutable
                    nameStack.add(interpretSetTerm(setFormula.substring(pos, nextTerm - 1), formulaStrings, referencedNames, attributeStrings));
                } else if (stackCount-- < 2) {
                    throw new Exception("not understood:  " + formulaCopy);
                } else if (op == '*') { // * meaning intersection here . . .
                    //assume that the second term implies 'level all'
                    long start = System.currentTimeMillis();
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
                            for (Name child : name.findAllChildren(false)) {
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
                                    if (intersectName.findAllChildren(false).contains(name)) {
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
                    long start = System.currentTimeMillis();
                    long heapMarker = ((runtime.totalMemory() - runtime.freeMemory()) / mb);
                    //System.out.println("aft mutable init " + heapMarker);
                    //System.out.println("starting / set sizes  nameStack(stackcount)" + nameStack.get(stackCount).getAsCollection().size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).getAsCollection().size());
                    for (Name child : nameStack.get(stackCount).getAsCollection()) {
                        Name.findAllParents(child, parents); // new call to static function cuts garbage generation a lot
                    }
                    //System.out.println("find all parents in parse query part 1 " + (now - start) + " set sizes parents " + parents.size() + " heap increase = " + (((runtime.totalMemory() - runtime.freeMemory()) / mb) - heapMarker) + "MB");
                    start = System.currentTimeMillis();
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
                } else if (op == ASSYMBOL) {
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
                            Name userSpecificSet = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), totalName.getAdditive()); // a basic copy of the set
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
            Collection<Name> defNames = azquoMemoryDBConnection.getAzquoMemoryDB().namesForAttribute("DEFINITION");
            if (defNames != null) {
                for (Name defName : defNames) {
                    String definition = defName.getAttribute("DEFINITION");
                    if (definition != null) {
                        if (attributeNames.size() > 1) {
                            String userEmail = attributeNames.get(0);
                            if (defName.getAttribute(userEmail) == null) { // there is no specific set for this user yet, need to do something
                                List<String> localLanguages = new ArrayList<>();
                                localLanguages.add(userEmail);
                                Name userSpecificSet = findByName(azquoMemoryDBConnection, defName.getDefaultDisplayName(), localLanguages);
                                if (userSpecificSet == null) {
                                    userSpecificSet = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), defName.getAdditive()); // a basic copy of the set
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

    private String getAttributeSetTerm(String term, List<Name> referencedNames, List<String>strings) throws Exception{
        term = term.trim();
        if (term.startsWith("\"")){
            return strings.get(Integer.parseInt(term.substring(1, 3))).toLowerCase();

        }
        if (term.startsWith(NameService.NAMEMARKER + "")){
            return getNameFromListAndMarker(term, referencedNames).getDefaultDisplayName();
        }
        return term;

    }

    private List<Name> handleEdit(AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> languages) throws Exception {
        List<Name> toReturn = new ArrayList<>();
        if (setFormula.startsWith("deduplicate")) {
            return deduplicate(azquoMemoryDBConnection, setFormula.substring(12));
        }
        if (setFormula.startsWith("findduplicates")) {
            return findDuplicateNames(azquoMemoryDBConnection, setFormula);
        }
        if (setFormula.startsWith("zap ")) {
            Collection<Name> names = parseQuery(azquoMemoryDBConnection, setFormula.substring(4), languages); // defaulting to list here
            if (names != null) {
                for (Name name : names) name.delete();
                return toReturn;
            }
        }
        throw new Exception(setFormula + " not understood");
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

    // Edd note - I'm not completely clear on the deduplicate utility functions but they are not core functionality, more to do with importing (should they be in there?)
    // so happy to just check for code warnings and not understand 100%

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

    private List<Name> findDuplicateNames(AzquoMemoryDBConnection azquoMemoryDBConnection, String instructions) {
        Set<String> attributeExceptions = new HashSet<>();
        if (instructions.toLowerCase().contains("except ")) {
            String exceptionList = instructions.toUpperCase().substring(instructions.toUpperCase().indexOf("EXCEPT ") + 7).trim();
            String[] eList = exceptionList.split(",");
            for (String exception : eList) {
                attributeExceptions.add(exception.trim());
            }
        }
       /*input syntax 'findduplicates`   probably need to add 'exception' list of cases where duplicates are expected (e.g.   Swimshop product categories)*/
        return azquoMemoryDBConnection.getAzquoMemoryDB().findDuplicateNames(Constants.DEFAULT_DISPLAY_NAME, attributeExceptions);
    }

    private List<Name> deduplicate(AzquoMemoryDBConnection azquoMemoryDBConnection, String formula) throws Exception {
        /*The syntax of the query is 'deduplicate <Set<Name>> to <Name>   Any duplicate names within the source set will be renamed and put in the destination name*/
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
                nameMap.putIfAbsent(nameString, new HashSet<>());
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

    Name inParentSet(Name name, Collection<Name> maybeParents) {
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
        for (Set<Name> listNames : names) {
            if (!listNames.isEmpty()) {
                if (inParentSet(name, listNames) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AtomicInteger filterCount = new AtomicInteger(0);

    // since what it's passed could be immutable need to return
    private NameSetList filter(NameSetList nameSetList, String condition, List<String> strings, List<String> attributeNames) {
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
                for (Name name : namesToFilter) {
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
                // outside the loop, iterator shouldn't get shirty
                namesToFilter.removeAll(namesToRemove);
            }
            lastPos = andPos + 5;
            andPos = condition.toLowerCase().indexOf(" and ", lastPos);
        }
        return toReturn; // its appropriate member collection should have been modified via namesToFilter above, return it
    }

    // Managed to convert to returning NameSetList, the key being using fast collection operations where possible depending on what has been passed

    private static AtomicInteger interpretSetTermCount = new AtomicInteger(0);

    private NameSetList interpretSetTerm(String setTerm, List<String> strings, List<Name> referencedNames, List<String> attributeStrings) throws Exception {
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
        String selectString = stringUtils.getInstruction(setTerm, SELECT);

        int wherePos = setTerm.toLowerCase().indexOf(WHERE.toLowerCase());
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
            namesFound = findChildrenAtLevel(name, levelString); // reassign names from the find children clause
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
            Collections.sort(namesFound.list, defaultLanguageCaseInsensitiveNameComparator);
        }
        return namesFound != null ? namesFound : new NameSetList(null, new ArrayList<>(), true); // empty one if it's null
    }

    public int countAttributes(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeName, String attributeValue){
        List<String> attributeNames = new ArrayList<>();
        attributeNames.add(attributeName);
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(attributeNames, attributeValue, null).size();

    }

    public Set<Name> attributeSet(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeName, String attributeValue){
        List<String> attributeNames = new ArrayList<>();
        attributeNames.add(attributeName);
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(attributeNames, attributeValue, null);

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
        System.out.println("constrainNameListFromToCount\t\t\t\t\t\t\t\t" + findChildrenFromToCount.get());
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