package com.azquo.memorydb.core;

import com.azquo.StringLiterals;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from AzquoMemoryDB by edward on 28/09/16.
 * <p>
 * This class is responsible for indexing of the AzquoMemoryDB. Currently names by attribute.
 * Queries to the index can be public, changes should be package private.
 * <p>
 * Thread safety is deferred to standard Java classes, should be fine.
 * <p>
 * Doesn't know which memory database it's held against, I see no reason for it to currently.
 * <p>
 * I'm using intern when adding strings to objects, it should be used wherever that string is going to hang around.
 */
public class AzquoMemoryDBIndex {

    // immutable in a superficial sense?

    private final Map<String, Map<String, Collection<Name>>> nameByAttributeMap; // a map of maps of Collections of names. Collections so I can do the classic switch from CopyOnWriteArray to a concurrent set when it gets too big. Otherwise COpyOnWriteArray can really jam things up when it gets big.

    AzquoMemoryDBIndex() {
        nameByAttributeMap = new ConcurrentHashMap<>();
    }

    private static AtomicInteger getAttributesCount = new AtomicInteger(0);

    // was unmodifiable, since it's a copy I see no reason. Also its only use is serialised.
    public List<String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return new ArrayList<>(nameByAttributeMap.keySet());
    }

    // fundamental low level function to get a set of names from the attribute indexes. Forces case insensitivity.
    // now used outside this class but I see no harm in that
    // Is wrapping in a hashSet such a big deal?

    private static AtomicInteger getNamesForAttributeCount = new AtomicInteger(0);

    public Set<Name> getNamesForAttribute(final String attributeName, final String attributeValue) {
        getNamesForAttributeCount.incrementAndGet();
        Map<String, Collection<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map != null) { // that attribute is there
            Collection<Name> names = map.get(attributeValue.toLowerCase().trim());
            if (names != null) { // were there any entries for that value?
                return HashObjSets.newMutableSet(names); // I've seen this modified outside, I guess no harm in that
            }
        }
        return Collections.emptySet(); // moving away from nulls - this will complain outside if it is modified though!
    }

    // same as above but then zap any not in the parent - if one cant find a direct parent then allow indirect parents which might mean multiple names

    private static AtomicInteger getNamesForAttributeAndParentCount = new AtomicInteger(0);

    private Set<Name> getNamesForAttributeAndParent(final String attributeName, final String attributeValue, Name parent) {
        getNamesForAttributeAndParentCount.incrementAndGet();
        Set<Name> possibles = getNamesForAttribute(attributeName, attributeValue);
        for (Name possible : possibles) {
            if (possible.getParents().contains(parent)) { //trying for immediate parent first
                Set<Name> found = HashObjSets.newMutableSet();// leave as mutable for the moment, the NameSetList may change it
                found.add(possible);
                return found; // and return straight away
            }
        }
        return Collections.emptySet();
        //WFC removed this - caused problems in listing duplicate emails in customer list where succeeding customers were children of originals.
        // get rid of any that are not in the parent - removeIf has the same logic as the iterator that was there before.
        //possibles.removeIf(possible -> !possible.findAllParents().contains(parent));
        //return possibles; // so this could be more than one if there were multiple in a big parent set (presumably at different levels)
    }

    // only used by the namesFromAttributeFunction. Todo - confirm the usage of this. I'd like to zap it, WFC says it's not currently being used but wants to keep it
    private static AtomicInteger getValuesForAttributeCount = new AtomicInteger(0);

    public Set<String> getValuesForAttribute(final String attributeName) {
        getValuesForAttributeCount.incrementAndGet();
        Map<String, Collection<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map != null) { // that attribute is there
            return HashObjSets.newMutableSet(map.keySet());
        }
        return Collections.emptySet(); // moving away from nulls - this will complain outside if it is modified though!
    }

    private static AtomicInteger attributeExistsInDBCount = new AtomicInteger(0);

    public boolean attributeExistsInDB(final String attributeName) {
        attributeExistsInDBCount.incrementAndGet();
        return nameByAttributeMap.containsKey(attributeName.toUpperCase().trim());
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

    // the root is a command in the UI tree edit findduplicates
    // is this still used? Check with WFC todo

    private static AtomicInteger getNameByAttributeCount = new AtomicInteger(0);

    public List<Name> findDuplicateNames(String attributeName, Set<String> exceptions) {
        List<Name> found = new ArrayList<>();
        Map<String, Collection<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map == null) return null;
        int dupCount = 0;
        int testCount = 0;
        for (Collection<Name> names : map.values()) {
            if (testCount++ % 50000 == 0) {
                System.out.println("testing for duplicates - count " + testCount + " dupes found " + dupCount);
            }
            if (names.size() > 1) {
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
/*            System.out.println("Attribute name : " + attributeNames);
            System.out.println("Attribute value : " + attributeValue);
            for (Name possible : possibles){
                System.out.println("id : " + possible.getId() + " atts : " + possible.getAttributes());
            }*/
            throw new Exception("Found more than one name for " + attributeValue);
        }
        return null;
    }

    private static AtomicInteger getNamesWithAttributeContainingCount = new AtomicInteger(0);

    public Set<Name> getNamesWithAttributeContaining(final String attributeName, String attributeValue, int limit) {
        getNamesWithAttributeContainingCount.incrementAndGet();
        boolean endsWith = true;
        boolean startsWith = true;
        if (attributeValue.endsWith("*")) {
            endsWith = false;
            attributeValue = attributeValue.substring(0, attributeValue.length() - 1);
        }
        if (attributeValue.startsWith("*")) {
            startsWith = false;
            attributeValue = attributeValue.substring(1);
        }
        return getNamesByAttributeValueWildcards(attributeName, attributeValue, startsWith, endsWith, limit);
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
                return getNamesByAttributeValueWildcards(attributeName,attributeValueSearch,startsWith,endsWith,StringLiterals.FINDLIMIT);
    }


        private Set<Name> getNamesByAttributeValueWildcards(final String attributeName, final String attributeValueSearch, final boolean startsWith, final boolean endsWith, int limit) {
        getNamesByAttributeValueWildcardsCount.incrementAndGet();
        final Set<Name> names = HashObjSets.newMutableSet();
        if (attributeName.length() == 0) { // so just search for *any* attribute containing the thing
            for (String attName : nameByAttributeMap.keySet()) {
                if (attName.length() > 0) {//not sure how a blank attribute name was created!
                    names.addAll(getNamesByAttributeValueWildcards(attName, attributeValueSearch, startsWith, endsWith,limit));
                }
                // and when attribute name is blank we don't return for all attribute names, just the first that contains this
                if (names.size() > 0) {
                    return names;
                }
            }
            return names;
        }
        final String uctAttributeName = attributeName.toUpperCase().trim();
        final String lctAttributeValueSearch = attributeValueSearch.toLowerCase().trim();
        if (nameByAttributeMap.get(uctAttributeName) == null) {// we don't have that attribute at all
            return names;
        }
        limit--;
        for (String attributeValue : nameByAttributeMap.get(uctAttributeName).keySet()) {
            if (startsWith && endsWith) { // the way the flags have been setup the logic is correct though it may look wrong * at the end sets up starts with, * at the beginning sets up ends with. Hence both contains.
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
            if (names.size()>limit){
                return names;
            }
        }

        return names;
    }

    private static AtomicInteger findTopNamesCount = new AtomicInteger(0);

    public List<Name> findTopNames(String language) {
        findTopNamesCount.incrementAndGet();
        Map<String, Collection<Name>> thisMap = nameByAttributeMap.get(language);
        final List<Name> toReturn = new ArrayList<>();
        for (Collection<Name> names : thisMap.values()) {
            for (Name name : names) {
                if (!name.hasParents()) { // top parent full stop
                    toReturn.add(name);
                } else { // little hazy on the logic here but I think the point is to say that if all the parents of the name are NOT in the language specified then that's a top name for this language.
                    // Kind of makes sense but where is this used? I can only see when attribute is passed to JSTree. Maybe clarify with WFC. todo
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

    // only used when looking up for "DEFINITION", inline?

    public Collection<Name> namesForAttribute(String attribute) {
        Map<String, Collection<Name>> namesForThisAttribute = nameByAttributeMap.get(attribute);
        if (namesForThisAttribute == null) return null;
        Collection<Name> toReturn = new HashSet<>();
/*        for (String key : namesForThisAttribute.keySet()) {
            toReturn.addAll(namesForThisAttribute.get(key));
        }*/
        for (Collection<Name> names : namesForThisAttribute.values()) {
            toReturn.addAll(names);
        }
        return toReturn;
    }

    // Sets indexes for names, this needs to be thread safe to support multi threaded name linking.
    // Thread safe by API use not anything clever on my part.

    private static AtomicInteger setAttributeForNameInAttributeNameMapCount = new AtomicInteger(0);

    void setAttributeForNameInAttributeNameMap(String attributeName, String attributeValue, Name name) {
        if (attributeName.length() == 0) {
            return;//there should never be a zero length attribute name
        }
        setAttributeForNameInAttributeNameMapCount.incrementAndGet();
        // upper and lower seems a bit arbitrary, I need a way of making it case insensitive.
        // these interns have been tested as helping memory usage.
        String ucAttributeName = attributeName.toUpperCase().trim().intern();
        // todo - an if on the split to save garbage? also should this be allowed at all! Hadn't really considered it. Used on importing so really the code should be there not in there
        // EFC 04/03/20. I really don't like this split but it is being used in lookups ExDwell||Exhome||Exwind under .LineOfBusiness for example so then it can be looked up but numerous of those
        //
        String[] attValues = attributeValue.split(StringLiterals.NEXTATTRIBUTE);
        for (String attValue : attValues) {
            setAttributeForNameInAttributeMap(attValue,ucAttributeName,name);
        }
        // so if it was something||anotherthing make sure that is in there too, it may be referenced
        if (attValues.length > 1){
            setAttributeForNameInAttributeMap(attributeValue, ucAttributeName, name);
        }
    }

    private void setAttributeForNameInAttributeMap(String attValue, String ucAttributeName, Name name){
        String lcAttributeValue = attValue.toLowerCase().trim().intern();
        if (lcAttributeValue.indexOf(StringLiterals.QUOTE) >= 0 && !ucAttributeName.equals(StringLiterals.CALCULATION)) {
            lcAttributeValue = lcAttributeValue.replace(StringLiterals.QUOTE, '\'').intern();
        }
        // moved to computeIfAbsent, saved a fair few lines of code
        Map<String, Collection<Name>> namesForThisAttribute = nameByAttributeMap.computeIfAbsent(ucAttributeName, s -> new ConcurrentHashMap<>());
        // Generally these lists will be single and not modified often so I think copy on write array should do the high read speed thread safe trick!
        // cost on writes but thread safe reads, might take a little more memory than the ol ArrayList, hopefully not a big prob
        // ok, these got bigger than expected, big bottleneck. Using compute should be able to safely switch over at 512 entries
        //Collection<Name> names = namesForThisAttribute.computeIfAbsent(lcAttributeValue, s -> new CopyOnWriteArrayList<>());
        // modification of the sets or list is in compute but I still need the collections themselves to be thread safe as I need to safely call an iterator on them
        // EFC 13/02/20 - added in singleton
        namesForThisAttribute.compute(lcAttributeValue, (k, v) -> {
            // singleton should bring some speed and memory saving
            // could other pointers be saved?
            if (v == null) {
                v = name; // name now implements collections
            } else {
                if (v.size() == 1) {
                    v = new CopyOnWriteArrayList<>(v); // switch the single name as collection to copy on write array
                }
                if (v.size() == 512) { // switch the copy on write array to a set. Not sure how much this is used, this would mean many names with the same attribute and value
                    Collection<Name> asSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
                    asSet.addAll(v);
                    v = asSet;
                }
                v.add(name); // this WAS outside but in theory this might mean two different collections escaping to each have something added, hence an index entry gets missed. Unlikely but no harm in it being in here
            }
            return v;
        });

    }

    // Again use the compute, keeps things safe

    private static AtomicInteger removeAttributeFromNameInAttributeNameMapCount = new AtomicInteger(0);

    void removeAttributeFromNameInAttributeNameMap(final String attributeName, final String attributeValue, final Name name) {
        removeAttributeFromNameInAttributeNameMapCount.incrementAndGet();
        String ucAttributeName = attributeName.toUpperCase().trim();
        String[] attValues = attributeValue.split(StringLiterals.NEXTATTRIBUTE);
        for (String attValue : attValues) {
            String lcAttributeValue = attValue.toLowerCase().trim();
            final Map<String, Collection<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
            if (namesForThisAttribute != null) {// the map we care about
                // putting this modification in compute to be sure it won't get tripped up finding a collection that was switched in the mean time by the 512 setAttribute switch above
                namesForThisAttribute.compute(lcAttributeValue, (k, v) -> {
                    if (v != null) {
                        if (v == name) {// it is the name, null it
                            return null; // zap the list completely, I think that works?
                        } else {
                            v.remove(name);
                        }
                    }
                    return v;
                });
            }
        }
    }

    private static void printFunctionCountStats() {
        System.out.println("######### AZQUO MEMORY DB INDEX FUNCTION COUNTS");
        System.out.println("getAttributesCount\t\t\t\t" + getAttributesCount.get());
        System.out.println("getNamesForAttributeCount\t\t\t\t" + getNamesForAttributeCount.get());
        System.out.println("getValuesForAttributeCount\t\t\t\t" + getValuesForAttributeCount.get());
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
        getValuesForAttributeCount.set(0);
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

    public static class TypedPair<F,S> {
        private final F first;
        private final S second;

        public TypedPair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }

        // these two are hacky
        @Override
        public int hashCode() {
            return (first.toString() + " " + second.toString()).hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof TypedPair){
                TypedPair tp = (TypedPair)o;
                return tp.first.equals(first) && tp.second.equals(second);
            }
            return super.equals(o);
        }
    }


    void printIndexStats() {
        NumberFormat nf = NumberFormat.getInstance();

        // temptation to use the Map.Entry here is apparently wrong as the entry object is mutable and may be changed when iterator.next is called
        List<TypedPair<String, Map<String, Collection<Name>>>> topAttributes = new ArrayList<>();
        for (Map.Entry<String, Map<String, Collection<Name>>> stringMapEntry : nameByAttributeMap.entrySet()) {
            topAttributes.add(new TypedPair<>(stringMapEntry.getKey(), stringMapEntry.getValue()));
        }
        topAttributes.sort((o1, o2) -> (Integer.compare(o2.getSecond().size(), o1.getSecond().size())));
        System.out.println("Attribute Name                          Number of values");
        for (TypedPair<String, Map<String, Collection<Name>>> attEntry : topAttributes) {
            System.out.print(attEntry.getFirst());
            if (attEntry.getFirst().length() < 40) {
                for (int i = 0; i < 40 - attEntry.getFirst().length(); i++) {
                    System.out.print(" ");
                }
            }
            System.out.println(nf.format(attEntry.getSecond().size()));
        }
        // now try to work out how many entries for each attribute have
        Map<Integer, AtomicInteger> counts = new HashMap<>();
        for (Map<String, Collection<Name>> index : nameByAttributeMap.values()) {
            for (Collection<Name> namesForAttValue : index.values()) {
                int size = namesForAttValue.size();
                if (size > 512) {
                    size = 512;
                }
                counts.computeIfAbsent(size, i -> new AtomicInteger()).incrementAndGet(); // essentially count the number of indexes of any given size
            }
        }
        List<Integer> sizes = new ArrayList<>(counts.keySet());
        Collections.sort(sizes);
        System.out.println("index size          number of indexes with that size");
        for (Integer size : sizes) {
            String ssize = nf.format(size);
            if (size == 512) {
                ssize = ">=512";
            }
            System.out.print(ssize);
            if (ssize.length() < 20) {
                for (int i = 0; i < 20 - ssize.length(); i++) {
                    System.out.print(" ");
                }
            }
            System.out.println(counts.get(size));
        }
    }
}