package com.azquo.memorydb.service;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringUtils;
import com.azquo.memorydb.core.StandardName;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * <p>
 * Should now deal with the more basic functions for fine and store names. Parsing stuff has been moved out of here to String Utils and the NameQueryParser.
 *
 */
public final class NameService {

    private static final Logger logger = Logger.getLogger(NameService.class);

    private static AtomicInteger nameCompareCount = new AtomicInteger(0);

    static final Comparator<Name> defaultLanguageCaseInsensitiveNameComparator = (n1, n2) -> {
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

    private static AtomicInteger sortCaseInsensitiveCount = new AtomicInteger(0);

    public static void sortCaseInsensitive(List<Name> namesList) {
        namesList.sort(defaultLanguageCaseInsensitiveNameComparator);
    }

    /* renaming this function to match the memory DB one - it's a straight proxy through except for trying for all
    attributes if it can't find any names for the specified attribute and sorting the result (hence loading the set in an arraylist)
     */

    private static AtomicInteger findContainingNameCount = new AtomicInteger(0);

    static public ArrayList<Name> getNamesWithAttributeContaining(final AzquoMemoryDBConnection azquoMemoryDBConnection, String attribute, String searchString) {
        findContainingNameCount.incrementAndGet();
        int languagePos = searchString.indexOf(StringLiterals.languageIndicator);
        if (languagePos > 0) {
            int namePos = searchString.indexOf(StringLiterals.QUOTE);
            if (namePos < 0 || namePos > languagePos) {
                //change the language
                attribute =searchString.substring(0, languagePos);
                searchString = searchString.substring(languagePos + 2);
            }
        }
        // new condition
        ArrayList<Name> namesList = new ArrayList<>(azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttribute(attribute, searchString));
        if (namesList.size() == 0){
            namesList =  new ArrayList<>(azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesWithAttributeContaining(attribute, searchString));
        }
        if (namesList.size() == 0 && attribute.length() > 0) {
            namesList = getNamesWithAttributeContaining(azquoMemoryDBConnection, "", searchString);//try all the attributes
        }
        namesList.sort(defaultLanguageCaseInsensitiveNameComparator);
        return namesList;
    }

    private static AtomicInteger findByIdCount = new AtomicInteger(0);

    static public Name findById(final AzquoMemoryDBConnection azquoMemoryDBConnection, int id) {
        findByIdCount.incrementAndGet();
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(id);
    }

    private static AtomicInteger getNameByAttributeCount = new AtomicInteger(0);

    private static Name getNameByAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeValue, Name parent, final List<String> attributeNames) throws Exception {
        getNameByAttributeCount.incrementAndGet();
        // attribute value null? Can it happen?
        if (attributeValue.length() > 0 && attributeValue.charAt(0) == StringLiterals.NAMEMARKER) {
            throw new Exception("getNameByAttribute should no longer have name marker passed to it!");
        }
        return azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(attributeNames, attributeValue.replace(StringLiterals.QUOTE, ' ').trim(), parent);
    }

    private static AtomicInteger findByNameCount = new AtomicInteger(0);

    public static Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) throws Exception {
        findByNameCount.incrementAndGet();
        return findByName(azquoMemoryDBConnection, name, StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST);
    }

    private static AtomicInteger findByName2Count = new AtomicInteger(0);

    private static Name findParentAttributesName(Name child, String attributeName, Set<Name> checked) {
        attributeName = attributeName.trim(); // EFC removed .toUppercase, pointless as only used for equalsIgnoreCase
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

    static Name findNameAndAttribute(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String qualifiedName, final List<String> attributeNames) throws Exception {
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

    public static Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, String qualifiedName, List<String> attributeNames) throws Exception {
        findByName2Count.incrementAndGet();
        // This routine accepts multiple 'memberof (->) symbols.  It also checks for the 'language indicator' (<-)
        // note if qualifiedName is null this will NPE - not sure if this is a problem
        int langPos = qualifiedName.indexOf(StringLiterals.languageIndicator);
        if (langPos > 0) {
            int quotePos = qualifiedName.indexOf(StringLiterals.QUOTE);
            if (quotePos < 0 || quotePos > langPos) {
                attributeNames = Collections.singletonList(qualifiedName.substring(0,langPos));
                qualifiedName = qualifiedName.substring(langPos + 2);
            }
        }

        if (qualifiedName.length() == 0) return null;
        List<String> parents = StringUtils.parseNameQualifiedWithParents(qualifiedName); // should never return an empty array given the check just now on qualified name
        String name = parents.remove(parents.size() - 1); // get the name off the end of the list now should just be parents in top to bottom order
        Set<Name> possibleParents = null;
        for (String parent : parents) {
            if (possibleParents == null) { // will happen on the first one
                // most of the time would only be one but the name on the right (top for this expression) might not be a top name in the DB hence there could be multiple
                possibleParents = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(attributeNames, parent, null);
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

    private static AtomicInteger findTopNamesCount = new AtomicInteger(0);

    public static List<Name> findTopNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, String language) {
        findTopNamesCount.incrementAndGet();
        if (language.equals(StringLiterals.DEFAULT_DISPLAY_NAME)) {
            return azquoMemoryDBConnection.getAzquoMemoryDB().findTopNames();
        } else {
            return azquoMemoryDBConnection.getAzquoMemoryDBIndex().findTopNames(language);
        }
    }

    private static AtomicInteger findOrCreateNameStructureCount = new AtomicInteger(0);

    public static Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, Name topParent, boolean local) throws Exception {
        findOrCreateNameStructureCount.incrementAndGet();
        return findOrCreateNameStructure(azquoMemoryDBConnection, name, topParent, local, null);
    }

    private static AtomicInteger findOrCreateNameStructure2Count = new AtomicInteger(0);

    public static Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String qualifiedName, Name topParent, boolean local, List<String> attributeNames) throws Exception {
        findOrCreateNameStructure2Count.incrementAndGet();
        // see findByName for structure string style
        // note a null/empty qualifiedName will end up returning topParent

        List<String> nameAndParents = StringUtils.parseNameQualifiedWithParents(qualifiedName); // should never return an empty array given the check just now on qualified name
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

    private static void includeInSet(Name name, Name set, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        includeInSetCount.incrementAndGet();
        set.addChildWillBePersisted(name, azquoMemoryDBConnection);//ok add as asked
        /*  REMOVING THIS CONDITION - CAUSED PROBLEMS IN SETTING UP `Order Entities->Shipping` then `Order Entities->Invoice Total->Shipping` in Magento
        Collection<Name> setParents = set.findAllParents();
        for (Name parent : name.getParents()) { // now check the direct parents and see that none are in the parents of the set we just put it in.
            // e.g the name was Ludlow in in places. We decided to add Ludlow to Shropshire which is all well and good.
            // Among Shropshire's parents is places so remove Ludlow from Places as it's now in places via Shropshire.
            if (setParents.contains(parent)) {
                parent.removeFromChildrenWillBePersisted(name);// following my above example, take Ludlow out of places
                break;
            }
        }
        */
    }

    private static AtomicInteger findOrCreateNameInParentCount = new AtomicInteger(0);

    public static Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name newParent, boolean local) throws Exception {
        findOrCreateNameInParentCount.incrementAndGet();
        return findOrCreateNameInParent(azquoMemoryDBConnection, name, newParent, local, null);
    }

    private static Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new ConcurrentHashMap<>();

    private static long addToTimesForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection, String trackName, long marker) {
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

    public static Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        return timeTrack.get(azquoMemoryDBConnection);
    }

    private static final boolean profile = false;

    // todo - permissions here or at a higher level?
    // aside from the odd logic to get existing I think this is fairly clear
    private static AtomicInteger findOrCreateNameInParent2Count = new AtomicInteger(0);

    public static Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name parent, boolean local, List<String> attributeNames) throws Exception {
        findOrCreateNameInParent2Count.incrementAndGet();
        long marker = System.currentTimeMillis();
        if (name == null || name.length() == 0) {
            throw new Exception("Name to be created is blank or null!");
        }
     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an dataimport),and insert a structure into it*/
        if (attributeNames == null) {
            attributeNames = StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
        }

        String storeName = name.replace(StringLiterals.QUOTE, ' ').trim();
        Name existing;

        if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent1", marker);
        if (parent != null) { // ok try to find it in that parent
            //try for an existing name already with the same parent
            if (local) {// ok looking only below that parent or just in it's whole set or top parent.
                existing = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(attributeNames, storeName, parent);
                if (existing == null) { // couldn't find local - try for one which has no parents or children, that's allowable for local (to be moved)
                    try {
                        existing = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(attributeNames, storeName, null);
                    } catch (Exception ignored) { // ignore the Found more than one name exception
                    }
                    if (existing != null && (existing.hasParents() || existing.hasChildren())) {
                        existing = null;
                    }
                }
                if (profile)
                    marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent2", marker);
            } else {// so we ignore parent if not local, we'll grab what we can to move it into the right parent set
                try {
                    existing = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(attributeNames, storeName, parent);
                    if (existing!=null)return existing;
                    // to clarify the logic here - if we have a name but in no set then grab it as it will be put in the set later. Todo - clean logic?
                    existing = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(attributeNames, storeName, null);
                } catch (Exception ignored) { // ignore the Found more than one name exception
                    existing = null;
                }
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent3", marker);
        } else { // no parent passed go for a vanilla lookup
            try {
                existing = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(attributeNames, storeName, null);
            } catch (Exception ignored) { // ignore the Found more than one name exception
                existing = null;
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent4", marker);
        }
        if (existing != null) {
            // direct parents may be moved up the hierarchy (e.g. if existing parent is 'Europe' and new parent is 'London', which is in 'Europe' then
            // remove 'Europe' from the direct parent list.
            //NEW CONDITION ADDED - we are parent = child, but not bothering to put into the set.  This may need discussion - are the parent and child really the same?
            // I think I was just avoiding a circular reference
            if (parent != null && existing != parent && !existing.findAllParents().contains(parent)) {
                //only check if the new parent is not already in the parent hierarchy.
                includeInSet(existing, parent, azquoMemoryDBConnection);
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent5", marker);
            return existing;
        } else {
              logger.debug("New name: " + storeName + ", " + (parent != null ? "," + parent.getDefaultDisplayName() : ""));
            // I think provenance from connection is correct, we should be looking to make a useful provenance when making the connection from the data access token
            Name newName = new StandardName(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance());
            if (!attributeNames.get(0).equals(StringLiterals.DEFAULT_DISPLAY_NAME)) { // we set the leading attribute name, I guess the secondary ones should not be set they are for searches
                newName.setAttributeWillBePersisted(attributeNames.get(0), storeName, azquoMemoryDBConnection);
            }
            newName.setAttributeWillBePersisted(StringLiterals.DEFAULT_DISPLAY_NAME, storeName, azquoMemoryDBConnection); // and set the default regardless
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent6", marker);
            if (parent != null) {
                // and add the new name to the parent of course :)
                parent.addChildWillBePersisted(newName, azquoMemoryDBConnection);
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent7", marker);
            return newName;
        }
    }

    public static final int LOWEST_LEVEL_INT = 100;
    private static final int ALL_LEVEL_INT = 101;

    // a loose type of return as we might be pulling sets or lists from names - todo - custom object

    private static AtomicInteger findChildrenAtLevelCount = new AtomicInteger(0);

    static NameSetList findChildrenAtLevel(final Name name, final String levelString) {
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
        if (level < 0) {
            return findParentsAtLevel(name, -level);
        }

        if (level == 1) { // then no need to get clever, just return the children
            if (name.hasChildrenAsSet()) {
                 return new NameSetList(name.getChildrenAsSet(), null, false);
            } else {
                if (name.getAttribute(StringLiterals.DISPLAYROWS)!=null){
                    int lineNo = 0;
                    List<Name> children = new ArrayList<>();
                    Iterator<Name> nameIt = name.getChildren().iterator();
                    String[] displayRows = name.getAttribute(StringLiterals.DISPLAYROWS).split(",");
                    for (String displayRow:displayRows){
                        int dRow = Integer.parseInt(displayRow);
                        while (dRow > ++lineNo) {
                            children.add(null);
                        }
                        children.add(nameIt.next());
                        if (!nameIt.hasNext()){
                            break;
                        }
                    }
                    while (nameIt.hasNext()){
                        children.add(nameIt.next());
                    }
                    return new NameSetList(null, children, true);
                }
                return new NameSetList(null, name.getChildrenAsList(), false);
            }
        }
        if (level == ALL_LEVEL_INT) {
            System.out.println("ALL_LEVEL_INT called on " + name.getDefaultDisplayName() + ", annoying!");
            // new logic! All names is the name find all children plus itself. A bit annoying to make a copy but there we go
            Set<Name> toReturn = HashObjSets.newMutableSet(name.findAllChildren());
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
        name.addChildrenToCollection(ordered ? namesFoundOrderedSet : namesFoundSet, 0, level);
        return new NameSetList(ordered ? null : namesFoundSet, ordered ? new ArrayList<>(namesFoundOrderedSet) : null, true); // it will be mutable either way
    }

    private static NameSetList findParentsAtLevel(final Name name, int level) {
        findChildrenAtLevelCount.incrementAndGet();
        if (level == 1) { // then no need to get clever, just return the parents
            return new NameSetList(null, name.getParents(), false);
        }
        // parents are not ordered like children, use a Set
        Set<Name> namesFoundSet = HashObjSets.newMutableSet();
        // has been moved to name to directly access contents of name hopefully increasing speed and saving on garbage generation
        name.addParentNamesToCollection(namesFoundSet, 0, level);
        return new NameSetList(namesFoundSet, null, true); // it will be mutable either way
    }

    private static AtomicInteger attributeListCount = new AtomicInteger(0);

    public static List<String> attributeList(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        attributeListCount.incrementAndGet();
        List<String> attributes = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getAttributes();
        Collections.sort(attributes);
        return attributes;
    }

    private static AtomicInteger getDefaultLanguagesListCount = new AtomicInteger(0);

    public static List<String> getDefaultLanguagesList(String user) {
        getDefaultLanguagesListCount.incrementAndGet();
        List<String> languages = new ArrayList<>();
        languages.add(user);
        languages.add(StringLiterals.DEFAULT_DISPLAY_NAME);
        return languages;
    }

    public static void printFunctionCountStats() {
        System.out.println("######### NAME SERVICE FUNCTION COUNTS");
        System.out.println("nameCompareCount\t\t\t\t\t\t\t\t" + nameCompareCount.get());
        System.out.println("sortCaseInsensitiveCount\t\t\t\t\t\t\t\t" + sortCaseInsensitiveCount.get());
        System.out.println("findContainingNameCount\t\t\t\t\t\t\t\t" + findContainingNameCount.get());
        System.out.println("findByIdCount\t\t\t\t\t\t\t\t" + findByIdCount.get());
        System.out.println("getNameByAttributeCount\t\t\t\t\t\t\t\t" + getNameByAttributeCount.get());
        System.out.println("findByNameCount\t\t\t\t\t\t\t\t" + findByNameCount.get());
        System.out.println("findByName2Count\t\t\t\t\t\t\t\t" + findByName2Count.get());
        System.out.println("findTopNamesCount\t\t\t\t\t\t\t\t" + findTopNamesCount.get());
        System.out.println("findOrCreateNameStructureCount\t\t\t\t\t\t\t\t" + findOrCreateNameStructureCount.get());
        System.out.println("findOrCreateNameStructure2Count\t\t\t\t\t\t\t\t" + findOrCreateNameStructure2Count.get());
        System.out.println("includeInSetCount\t\t\t\t\t\t\t\t" + includeInSetCount.get());
        System.out.println("findOrCreateNameInParentCount\t\t\t\t\t\t\t\t" + findOrCreateNameInParentCount.get());
        System.out.println("findOrCreateNameInParent2Count\t\t\t\t\t\t\t\t" + findOrCreateNameInParent2Count.get());
        System.out.println("findChildrenAtLevelCount\t\t\t\t\t\t\t\t" + findChildrenAtLevelCount.get());
        System.out.println("attributeListCount\t\t\t\t\t\t\t\t" + attributeListCount.get());
    }

    public static void clearFunctionCountStats() {
        nameCompareCount.set(0);
        sortCaseInsensitiveCount.set(0);
        findContainingNameCount.set(0);
        findByIdCount.set(0);
        getNameByAttributeCount.set(0);
        findByNameCount.set(0);
        findByName2Count.set(0);
        findTopNamesCount.set(0);
        findOrCreateNameStructureCount.set(0);
        findOrCreateNameStructure2Count.set(0);
        includeInSetCount.set(0);
        findOrCreateNameInParentCount.set(0);
        findOrCreateNameInParent2Count.set(0);
        findChildrenAtLevelCount.set(0);
        attributeListCount.set(0);
    }
}