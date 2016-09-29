package com.azquo.memorydb.core;

import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by edward on 28/09/16.
 *
 * Factoring off the name indexes. Queries to the index can be public, changes should be package private.
 */
public class AzquoMemoryDBIndex {

    private final Map<String, Map<String, List<Name>>> nameByAttributeMap; // a map of maps of lists of names. Fun! Moved back to lists to save memory, the lists are unlikely to be big
    private final AzquoMemoryDB azquoMemoryDB;


    AzquoMemoryDBIndex(AzquoMemoryDB azquoMemoryDB) {
        this.azquoMemoryDB = azquoMemoryDB;
        nameByAttributeMap = new ConcurrentHashMap<>();
    }

    private static AtomicInteger getAttributesCount = new AtomicInteger(0);

    public List<String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return Collections.unmodifiableList(new ArrayList<>(nameByAttributeMap.keySet()));
    }

    // same as above but then zap any not in the parent

    private static AtomicInteger getNamesForAttributeAndParentCount = new AtomicInteger(0);

    private Set<Name> getNamesForAttributeAndParent(final String attributeName, final String attributeValue, Name parent) {
        getNamesForAttributeAndParentCount.incrementAndGet();
        Set<Name> possibles = getNamesForAttribute(attributeName, attributeValue);
        for (Name possible : possibles) {
            if (possible.getParents().contains(parent)) { //trying for immediate parent first
                Set<Name> found = HashObjSets.newMutableSet();
                found.add(possible);
                return found; // and return straight away
            }
        }
        Iterator<Name> iterator = possibles.iterator();
        while (iterator.hasNext()) {
            Name possible = iterator.next();
            if (!possible.findAllParents().contains(parent)) {//logic changed by WFC 30/06/15 to allow sets import to search within a general set (e.g. 'date') rather than need an immediate parent (e.g. 'All dates')
                iterator.remove();
            }
        }
        return possibles; // so this could be more than one if there were multiple in a big parent set (presumably at different levels)
    }

    // fundamental low level function to get a set of names from the attribute indexes. Forces case insensitivity.
    // Is wrapping in a hashSet such a big deal? Using koloboke immutable should be a little more efficient

    private static AtomicInteger getNamesForAttributeCount = new AtomicInteger(0);

    private Set<Name> getNamesForAttribute(final String attributeName, final String attributeValue) {
        getNamesForAttributeCount.incrementAndGet();
        Map<String, List<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map != null) { // that attribute is there
            List<Name> names = map.get(attributeValue.toLowerCase().trim());
            if (names != null) { // were there any entries for that value?
                return HashObjSets.newMutableSet(names); // I've seen this modified outside, I guess no harm in that
            }
        }
        return Collections.emptySet(); // moving away from nulls
    }

    // for checking confidential, will save considerable time

    private static AtomicInteger attributeExistsInDBCount = new AtomicInteger(0);

    public boolean attributeExistsInDB(final String attributeName) {
        attributeExistsInDBCount.incrementAndGet();
        return nameByAttributeMap.get(attributeName.toUpperCase().trim()) != null;
    }

    // work through a list of possible names for a given attribute in order that the attribute names are listed. Parent optional

    private static AtomicInteger getNamesForAttributeNamesAndParentCount = new AtomicInteger(0);

    public Set<Name> getNamesForAttributeNamesAndParent(final List<String> attributeNames, final String attributeValue, Name parent) {
        getNamesForAttributeNamesAndParentCount.incrementAndGet();
        if (parent != null) {
            for (String attributeName : attributeNames) {
                Set<Name> names = getNamesForAttributeAndParent(attributeName, attributeValue, parent);
                if (!names.isEmpty()) {
                    return names;
                }
            }
        } else {
            if (attributeNames != null) {
                for (String attributeName : attributeNames) {
                    Set<Name> names = getNamesForAttribute(attributeName, attributeValue);
                    if (!names.isEmpty()) {
                        return names;
                    }
                }
            }
        }
        return Collections.emptySet();
    }

/*    public Name getNameByDefaultDisplayName(final String attributeValue) {
        return getNameByAttribute( Arrays.asList(Name.DEFAULT_DISPLAY_NAME), attributeValue, null);
    }

    public Name getNameByDefaultDisplayName(final String attributeValue, final Name parent) {
        return getNameByAttribute( Arrays.asList(Name.DEFAULT_DISPLAY_NAME), attributeValue, parent);
    }*/

    private static AtomicInteger getNameByAttributeCount = new AtomicInteger(0);

    public List<Name> findDuplicateNames(String attributeName, Set<String> exceptions) {
        List<Name> found = new ArrayList<>();
        Map<String, List<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map == null) return null;
        int dupCount = 0;
        int testCount = 0;
        for (String string : map.keySet()) {
            if (testCount++ % 50000 == 0)
                System.out.println("testing for duplicates - count " + testCount + " dups found " + dupCount);
            if (map.get(string).size() > 1) {
                List<Name> names = map.get(string);
                boolean nameadded = false;
                for (Name name : names) {
                    for (String attribute : name.getAttributeKeys()) {
                        if (name.getAttributes().size() == 1 || (!attribute.equals(attributeName) && !exceptions.contains(attribute))) {
                            String attValue = name.getAttribute(attribute);
                            for (Name name2 : names) {
                                if (name2.getId() == name.getId()) break;
                                List<String> attKeys2 = name2.getAttributeKeys();
                                //note checking here only on the attribute values of the name itself (not parent names)
                                if (attKeys2.contains(attribute) && name2.getAttribute(attribute).equals(attValue)) {
                                    if (!nameadded) {
                                        found.add(name);
                                        nameadded = true;
                                    }
                                    found.add(name2);
                                    dupCount++;

                                }
                            }
                        }

                    }

                }
                if (dupCount > 100) {
                    break;
                }
            }
        }
        return found;
    }

    private static AtomicInteger getNameByAttribute2Count = new AtomicInteger(0);

    public Name getNameByAttribute(final List<String> attributeNames, final String attributeValue, final Name parent) throws Exception {
        getNameByAttribute2Count.incrementAndGet();
        Set<Name> possibles = getNamesForAttributeNamesAndParent(attributeNames, attributeValue, parent);
        // all well and good but now which one to return?
        if (possibles.size() == 1) { // simple
            return possibles.iterator().next();
        } else if (possibles.size() > 1) { // more than one . . . try and replicate logic that was there before
            // what happens here is a bit arbitrary! Perhaps standardise
            if (parent == null) { // no parent criteria
                for (Name possible : possibles) {
                    if (!possible.hasParents()) { // we chuck back the first top level one. Not sure this is the best logic, more than one possible with no top levels means return null
                        return possible;
                    }
                }
            } else { // if there were more than one found taking into account parent criteria simply return the first
                return possibles.iterator().next();
            }
            throw new Exception("Found more than one name for " + attributeValue);
        }
        return null;
    }

    private static AtomicInteger getNamesWithAttributeContainingCount = new AtomicInteger(0);

    public Set<Name> getNamesWithAttributeContaining(final String attributeName, final String attributeValue) {
        getNamesWithAttributeContainingCount.incrementAndGet();
        return getNamesByAttributeValueWildcards(attributeName, attributeValue, true, true);
    }

    private static AtomicInteger getNamesWithAttributeStartingCount = new AtomicInteger(0);

    public Set<Name> getNamesWithAttributeStarting(final String attributeName, final String attributeValue) {
        getNamesWithAttributeContainingCount.incrementAndGet();
        if (attributeValue.charAt(0) == '*') {
            return getNamesByAttributeValueWildcards(attributeName, attributeValue.substring(1), true, true);
        }
        return getNamesByAttributeValueWildcards(attributeName, attributeValue, true, false);
    }

    // get names containing an attribute using wildcards, start end both

    private static AtomicInteger getNamesByAttributeValueWildcardsCount = new AtomicInteger(0);

    private Set<Name> getNamesByAttributeValueWildcards(final String attributeName, final String attributeValueSearch, final boolean startsWith, final boolean endsWith) {
        getNamesByAttributeValueWildcardsCount.incrementAndGet();
        final Set<Name> names = HashObjSets.newMutableSet();
        if (attributeName.length() == 0) { // odd that it might be
            for (String attName : nameByAttributeMap.keySet()) {
                names.addAll(getNamesByAttributeValueWildcards(attName, attributeValueSearch, startsWith, endsWith)); // and when attribute name is blank we don't return for all attribute names, just the first that contains this
                if (names.size() > 0) {
                    return names;
                }
            }
            return names;
        }
        final String uctAttributeName = attributeName.toUpperCase().trim();
        final String lctAttributeValueSearch = attributeValueSearch.toLowerCase().trim();
        if (nameByAttributeMap.get(uctAttributeName) == null) {
            return names;
        }
        for (String attributeValue : nameByAttributeMap.get(uctAttributeName).keySet()) {
            if (startsWith && endsWith) {
                if (attributeValue.contains(lctAttributeValueSearch)) {
                    names.addAll(nameByAttributeMap.get(uctAttributeName).get(attributeValue));
                }
            } else if (startsWith) {
                if (attributeValue.startsWith(lctAttributeValueSearch)) {
                    names.addAll(nameByAttributeMap.get(uctAttributeName).get(attributeValue));
                }
            } else if (endsWith) {
                if (attributeValue.endsWith(lctAttributeValueSearch)) {
                    names.addAll(nameByAttributeMap.get(uctAttributeName).get(attributeValue));
                }
            }
        }
        return names;
    }

    private static AtomicInteger findTopNamesCount = new AtomicInteger(0);

    public List<Name> findTopNames(String language) {
        findTopNamesCount.incrementAndGet();
        Map<String, List<Name>> thisMap = nameByAttributeMap.get(language);
        final List<Name> toReturn = new ArrayList<>();
        for (List<Name> names : thisMap.values()) {
            for (Name name : names) {
                if (!name.hasParents()) { // top parent full stop
                    toReturn.add(name);
                } else { // little hazy on the logic here but I think the point is to say that if all the parents of the name are NOT in the language specified then that's a top name for this language.
                    // Kind of makes sense but where is this used?
                    boolean include = true;
                    for (Name parent : name.getParents()) {
                        if (parent.getAttribute(language) != null) {
                            include = false;
                            break;
                        }
                    }
                    if (include) {
                        toReturn.add(name);
                    }
                }
            }
        }
        return toReturn;
    }

    // ok I'd have liked this to be part of add name to db but the name won't have been initialised, add name to db is called in the name constructor
    // before the attributes have been initialised

    private static AtomicInteger addNameToAttributeNameMapCount = new AtomicInteger(0);

    void addNameToAttributeNameMap(final Name newName) throws Exception {
        addNameToAttributeNameMapCount.incrementAndGet();
        newName.checkDatabaseMatches(azquoMemoryDB);
        // skip the map to save the memory
        int i = 0;
        List<String> attributeValues = newName.getAttributeValues();
        for (String attributeName : newName.getAttributeKeys()) {
            setAttributeForNameInAttributeNameMap(attributeName, attributeValues.get(i), newName);
            i++;
        }
    }

    // only used when looking up for "DEFINITION", inline?

    public Collection<Name> namesForAttribute(String attribute) {
        Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(attribute);
        if (namesForThisAttribute == null) return null;
        Collection<Name> toReturn = new HashSet<>();
        for (String key : namesForThisAttribute.keySet()) {
            toReturn.addAll(namesForThisAttribute.get(key));
        }
        return toReturn;
    }

    // Sets indexes for names, this needs to be thread safe to support multi threaded name linking.

    private static AtomicInteger setAttributeForNameInAttributeNameMapCount = new AtomicInteger(0);

    void setAttributeForNameInAttributeNameMap(String attributeName, String attributeValue, Name name) {
        setAttributeForNameInAttributeNameMapCount.incrementAndGet();
        // upper and lower seems a bit arbitrary, I need a way of making it case insensitive.
        // these interns have been tested as helping memory usage.
        String lcAttributeValue = attributeValue.toLowerCase().trim().intern();
        String ucAttributeName = attributeName.toUpperCase().trim().intern();
        if (lcAttributeValue.indexOf(Name.QUOTE) >= 0 && !ucAttributeName.equals(Name.CALCULATION)) {
            lcAttributeValue = lcAttributeValue.replace(Name.QUOTE, '\'').intern();
        }
        // The way to use putIfAbsent correctly according to a stack overflow example
        Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
        if (namesForThisAttribute == null) {
            final Map<String, List<Name>> newNamesForThisAttribute = new ConcurrentHashMap<>();
            namesForThisAttribute = nameByAttributeMap.putIfAbsent(ucAttributeName, newNamesForThisAttribute);// in ConcurrentHashMap this is atomic, thanks Doug!
            if (namesForThisAttribute == null) {// the new one went in, use it, otherwise use the one that "sneaked" in there in the mean time :)
                namesForThisAttribute = newNamesForThisAttribute;
            }
        }
        // same pattern but for the lists. Generally these lists will be single and not modified often so I think copy on write array should do the high read speed thread safe trick!
        List<Name> names = namesForThisAttribute.get(lcAttributeValue);
        if (names == null) {
            final List<Name> newNames = new CopyOnWriteArrayList<>();// cost on writes but thread safe reads, might take a little more memory than the ol arraylist, hopefully not a big prob
            names = namesForThisAttribute.putIfAbsent(lcAttributeValue, newNames);
            if (names == null) {
                names = newNames;
            }
        }
        // ok, got names
        names.add(name); // thread safe, internally locked but of course just for this particular attribute and value heh.
        // Could maybe get a little speed by adding a special case for the first name (as in singleton)
    }

    // I think this is just much more simple re thread safety in that if we can't find the map and list we just don't do anything and the final remove should be safe according to CopyOnWriteArray

    private static AtomicInteger removeAttributeFromNameInAttributeNameMapCount = new AtomicInteger(0);

    void removeAttributeFromNameInAttributeNameMap(final String attributeName, final String attributeValue, final Name name) throws Exception {
        removeAttributeFromNameInAttributeNameMapCount.incrementAndGet();
        String ucAttributeName = attributeName.toUpperCase().trim();
        String lcAttributeValue = attributeValue.toLowerCase().trim();
        name.checkDatabaseMatches(azquoMemoryDB);
        final Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
        if (namesForThisAttribute != null) {// the map we care about
            final List<Name> namesForThatAttributeAndAttributeValue = namesForThisAttribute.get(lcAttributeValue);
            if (namesForThatAttributeAndAttributeValue != null) {
                namesForThatAttributeAndAttributeValue.remove(name); // if it's there which it should be zap it from the list . . .
            }
        }
    }

    private static void printFunctionCountStats() {
        System.out.println("######### AZQUO MEMORY DB INDEX FUNCTION COUNTS");
        System.out.println("getAttributesCount\t\t\t\t" + getAttributesCount.get());
        System.out.println("getNamesForAttributeCount\t\t\t\t" + getNamesForAttributeCount.get());
        System.out.println("attributeExistsInDBCount\t\t\t\t" + attributeExistsInDBCount.get());
        System.out.println("getNamesForAttributeAndParentCount\t\t\t\t" + getNamesForAttributeAndParentCount.get());
        System.out.println("getNamesForAttributeNamesAndParentCount\t\t\t\t" + getNamesForAttributeNamesAndParentCount.get());
        System.out.println("getNameByAttributeCount\t\t\t\t" + getNameByAttributeCount.get());
        System.out.println("getNameByAttribute2Count\t\t\t\t" + getNameByAttribute2Count.get());
        System.out.println("getNamesWithAttributeContainingCount\t\t\t\t" + getNamesWithAttributeContainingCount.get());
        System.out.println("getNamesWithAttributeStartingCount\t\t\t\t" + getNamesWithAttributeStartingCount.get());
        System.out.println("getNamesByAttributeValueWildcardsCount\t\t\t\t" + getNamesByAttributeValueWildcardsCount.get());
        System.out.println("findTopNamesCount\t\t\t\t" + findTopNamesCount.get());
        System.out.println("setAttributeForNameInAttributeNameMapCount\t\t\t\t" + setAttributeForNameInAttributeNameMapCount.get());
        System.out.println("removeAttributeFromNameInAttributeNameMapCount\t\t\t\t" + removeAttributeFromNameInAttributeNameMapCount.get());
    }

    private static void clearFunctionCountStats() {
        getAttributesCount.set(0);
        getNamesForAttributeCount.set(0);
        attributeExistsInDBCount.set(0);
        getNamesForAttributeAndParentCount.set(0);
        getNamesForAttributeNamesAndParentCount.set(0);
        getNameByAttributeCount.set(0);
        getNameByAttribute2Count.set(0);
        getNamesWithAttributeContainingCount.set(0);
        getNamesWithAttributeStartingCount.set(0);
        getNamesByAttributeValueWildcardsCount.set(0);
        findTopNamesCount.set(0);
//        clearSetAndCountCacheForNameCount.set(0);
//        clearSetAndCountCacheForStringCount.set(0);
        setAttributeForNameInAttributeNameMapCount.set(0);
        removeAttributeFromNameInAttributeNameMapCount.set(0);
    }
}