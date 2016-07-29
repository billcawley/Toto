package com.azquo.memorydb.core;

import com.azquo.memorydb.Constants;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.StringUtils;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Azquo object, names now have attributes and what was the name (as in the text) is now simply an attribute of the name
 * defined currently in a static below. Names can have parent and child relationships with multiple other names. Sets of names.
 * <p>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on. The class should be thread safe allowing concurrent reads but not concurrent writes.
 * Changes to state need not be immediately visible though in some cases code needs to be structured for suitable atomicity (e.g. attributes).
 * <p>
 * Thread safety is important, essentially modifying data will be synchronized, reads won't be and won't be completely up to date but should return consistent data.
 * <p>
 * This object was weighing in at over 2k average in an example magento db the goal is to bring it to sub 500 bytes.
 * This means in some cases variables being null until used and using arrays instead of sets switching for performance
 * when the list gets too big. I got it to about 850 bytes (magento example DB). Will park for the mo,
 * further change would probably involve changes to attributes, I mean the name of attributes not being held here for example.
 * <p>
 * Attributes case insensitive . . .not entirely happy about this.
 * <p>
 * Was comparable but this resulted in a code warning I've moved the comparator to NameService
 * <p>
 * Note : as well as heap overhead there's garbage collection overhead, watch for avoidable throw away objects.
 * <p>
 * Are we acquiring multiple locks in any places? Is it worth testing the cost of synchronizing access to bits of name? Depending on cost it could mean the code is simplified . . .
 * <p>
 * Also I'm using Double Checked Locking. With volatile DCL is correct according to the JMM,
 * Given that synchronization is not inherently expensive it might be worth considering how much places where I'm using DCL might actually be contended much.
 */
public final class Name extends AzquoMemoryDBEntity {

    public static final String CALCULATION = "CALCULATION";
    public static final String APPLIESTO = "APPLIES TO";
    public static final String INDEPENDENTOF = "INDEPENDENT OF";
    public static final String DEPENDENTON = "DEPENDENT ON";
    private static final String LOCAL = "LOCAL";
    private static final String ATTRIBUTEDIVIDER = "↑"; // it will go attribute name, attribute vale, attribute name, attribute vale

    public static final char QUOTE = '`';

    private static final int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets. A bit arbitrary, might be worth testing (speed vs memory usage)

    // just a cache while the names by id map is being populated. I experimented with various ideas e.g. a parent cache also, this seemed the best compromise

    private byte[] childrenCache = null;

//    private static final Logger logger = Logger.getLogger(Name.class);

    private Provenance provenance; // should be volatile? Don't care about being completely up to date but could a prtially constructed object get in here?

    /* Going to try for attributes as two arrays as this should save a lot of space vs a LinkedHashMap.
    That is to say one makes a new one of these when updating attributes and then switch it in. Immutable, hence atomic
    (but not necessarily immediately visible!) switch of two arrays if an object reference is out of date it will at least be two consistent arrays.

    I have no problem with out of date versions as long as object contents remain consistent.

    Gets to wrap with as list, don't want them altering the length anyway, this does have a bit of an overhead vs the old model but I don't think this will be a biggy.
    Also I know that aslist on two of the gets doesn't make immutable, I consider this class "trusted". If bothered can use unmodifiableList.

    Ok using arrays here has saved about 5% on a database's memory usage. They stay unless there's a big unfixable problem.

    I've put the get in here, stops worry about an object switch in the middle of a get (if it were referenceing the keys and values externally it could go out of sync).

    All fields final, means the constructor should complete before the object reference is generated and the object is published. Immutable as mentioned above.
    */
    private static final class NameAttributes {
        private final String[] attributeKeys;
        private final String[] attributeValues;

        NameAttributes(List<String> attributeKeys, List<String> attributeValues) throws Exception {
            if (attributeKeys.size() != attributeValues.size()) {
                throw new Exception("Keys and values for attributes must match!");
            }
            this.attributeKeys = new String[attributeKeys.size()];
            attributeKeys.toArray(this.attributeKeys);
            this.attributeValues = new String[attributeValues.size()];
            attributeValues.toArray(this.attributeValues);
        }

        // faster init - note this is kind of dangerous in that the arrays could be externally modified, used by the new fast loader
        NameAttributes(String[] attributeKeys, String[] attributeValues) throws Exception {
            this.attributeKeys = attributeKeys;
            this.attributeValues = attributeValues;
        }

        NameAttributes() { // blank default. Fine.
            attributeKeys = new String[0];
            attributeValues = new String[0];
        }

        List<String> getAttributeKeys() {
            return Arrays.asList(attributeKeys);
        }

        List<String> getAttributeValues() {
            return Arrays.asList(attributeValues);
        }

        public String getAttribute(String attributeName) {
            for (int i = 0; i < attributeKeys.length; i++) {
                if (attributeKeys[i].equals(attributeName)) {
                    return attributeValues[i];
                }
            }
            return null;
        }

        Map<String, String> getAsMap() {
            Map<String, String> attributesAsMap = new HashMap<>(attributeKeys.length);
            int count = 0;
            for (String key : attributeKeys) { // hmm, can still access and foreach on the internal array. Np I suppose!
                attributesAsMap.put(key, attributeValues[count]);
                count++;
            }
            return attributesAsMap;
        }
    }

    private NameAttributes nameAttributes; // since NameAttributes is immutable there's no need for this to be volatile I don't think. Could test performance, I suppose volatile is slightly preferable? Happens before?

    /* memory db structure bits. There may be better ways to do this but we'll leave it here for the mo
     Values, parent are for quick lookup, must be modified appropriately
     to be clear, these are not used when persisting, they are derived from the name sets in values and the children below
     Sets are expensive in terms of memory, will use arrays instead unless they get big (above threshold 512 at the mo)
     make a new array and switch on changing to make atomic and always wrap them unmodifiable on get
     Based on recommendations here https://en.wikipedia.org/wiki/Double-checked_locking I switched the sets to volatile
     to protect against half instantiated object references, it seems (Damart test) to have had no performance effect.
     Arrays volatile too seems ok (there may be an effect importing).
     */
    private volatile Set<Value> valuesAsSet;
    private volatile Value[] values;
    private volatile Set<Name> parentsAsSet;
    private volatile Name[] parents;
    private volatile Set<Name> childrenAsSet;
    private volatile Name[] children;

    // For the code to make new names (not when loading).

    private static AtomicInteger newNameCount = new AtomicInteger(0);

    public Name(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance) throws Exception {
        super(azquoMemoryDB, 0);
        newNameCount.incrementAndGet();
        valuesAsSet = null;
        values = new Value[0]; // Turning these 3 lists to arrays, memory a priority
        parentsAsSet = null;
        parents = new Name[0];
        childrenAsSet = null;
        children = new Name[0];
        nameAttributes = new NameAttributes(); // attributes will nearly always be written over, this is just a placeholder
        getAzquoMemoryDB().addNameToDb(this);
        newNameCount.incrementAndGet();
        setProvenanceWillBePersisted(provenance);
    }

    // For loading, it should only be used by the NameDAO. Can I reshuffle and make it non public? Todo.

    private static AtomicInteger newName3Count = new AtomicInteger(0);

    public Name(final AzquoMemoryDB azquoMemoryDB, int id, int provenanceId, String attributes, byte[] chidrenCache, int noParents, int noValues) throws Exception {
        super(azquoMemoryDB, id);
        newName3Count.incrementAndGet();
        this.childrenCache = chidrenCache;
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId); // see no reason not to do this here now
        //this.attributes = transport.attributes;
        String[] attsArray = attributes.split(ATTRIBUTEDIVIDER);
        String[] attributeKeys = new String[attsArray.length / 2];
        String[] attributeValues = new String[attsArray.length / 2];
        for (int i = 0; i < attributeKeys.length; i++) {
            attributeKeys[i] = attsArray[i * 2];
            attributeValues[i] = attsArray[(i * 2) + 1];
        }
        nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        valuesAsSet = noValues > ARRAYTHRESHOLD ? Collections.newSetFromMap(new ConcurrentHashMap<>(noValues)) : null; // should help loading - prepare the space
        // could values be nulled if the set is being used? The saving would be minor I think.
        values = new Value[noValues <= ARRAYTHRESHOLD ? noValues : 0]; // prepare the array, as long as this is taken into account later should be fine
        parentsAsSet = noParents > ARRAYTHRESHOLD ? Collections.newSetFromMap(new ConcurrentHashMap<>(noParents)) : null;
        parents = new Name[noParents <= ARRAYTHRESHOLD ? noParents : 0];
        childrenAsSet = null;// dealt with properly later
        children = new Name[0];
        getAzquoMemoryDB().addNameToDb(this);
    }

    private static AtomicInteger getDefaultDisplayNameCount = new AtomicInteger(0);

    // for convenience but be careful where it is used . . .
    public String getDefaultDisplayName() {
        getDefaultDisplayNameCount.incrementAndGet();
        return nameAttributes.getAttribute(Constants.DEFAULT_DISPLAY_NAME);
    }

    private static AtomicInteger getFullyQualifiedDefaultDisplayNameCount = new AtomicInteger(0);

    public String getFullyQualifiedDefaultDisplayName() {
        if (getParents().isEmpty()){
            return QUOTE + getDefaultDisplayName() + QUOTE;
        }
        Collection<Name> parents = getParents();
        String qualified = getDefaultDisplayName();
        while (!parents.isEmpty()){
            Name parent = parents.iterator().next();
            qualified = parent.getDefaultDisplayName() + StringUtils.MEMBEROF + qualified;
            parents = parent.getParents();
        }
        return QUOTE + qualified + QUOTE;
    }

    // provenance immutable. If it were not then would need to clone

    public Provenance getProvenance() {
        return provenance;
    }

    private synchronized void setProvenanceWillBePersisted(final Provenance provenance) throws Exception {
        if (this.provenance == null || !this.provenance.equals(provenance)) {
            this.provenance = provenance;
            setNeedsPersisting();
        }
    }

    @Override
    public String toString() {
        return "Name{" +
                "id=" + getId() +
                ", default display name=" + getDefaultDisplayName() +
                ", provenance=" + provenance +
                ", nameAttributes=" + nameAttributes +
                '}';
    }

    /* We assume sets are built on concurrent hash map and lists are not modifiable
    even if based on concurrent hash map we don't want anything external modifying it, make the set unmodifiable.
    Now sets are volatile it shouldn't be possible for a half initialised set to be in here not null. Not that I ever saw evidence for this.
    Check that the array has elements before wrapping, want to avoid unnecessary garbage - hence Collections.emptyList() rather than unnecessary wrapping.
    */

    private static AtomicInteger getValuesCount = new AtomicInteger(0);

    public Collection<Value> getValues() {
        getValuesCount.incrementAndGet();
        return valuesAsSet != null ? Collections.unmodifiableSet(valuesAsSet) : values.length > 0 ? Collections.unmodifiableList(Arrays.asList(values)) : Collections.emptyList();
    }

    public boolean hasValues() {
        return valuesAsSet != null || values.length > 0;
    }

    // added by WFC I guess, need to check on this - Edd
    // this may not be thread safe . . .

    private static AtomicInteger transferValuesCount = new AtomicInteger(0);

    public void transferValues(Name from) throws Exception {
        transferValuesCount.incrementAndGet();
        if (from.valuesAsSet == null) return;
        if (valuesAsSet == null) {
            valuesAsSet = new HashSet<>(from.valuesAsSet);
        } else {
            for (Value v : from.getValues()) {
                addToValues(v);
            }
        }
        from.valuesAsSet = null;
    }

    // these two are becoming protected so they can be set by Value.
    // Value will be the reference point for the value name link, the ones here are for fast lookup, no need for persistence
    // following parent pattern re switching to sets etc
    void addToValues(final Value value) throws Exception {
        addToValues(value, false);
    }

    // no duplication is while loading, to reduce work
    private static AtomicInteger addToValuesCount = new AtomicInteger(0);

    void addToValues(final Value value, boolean noDuplicationCheck) throws Exception {
        addToValuesCount.incrementAndGet();
        checkDatabaseMatches(value);
        // may make this more clever as in clearing only if there's a change but not now
        valuesIncludingChildrenCache = null;
        // without volatile supposedly this (and getValues) could be accessing a not yet instantiated set. I am sceptical.
        if (valuesAsSet != null) {
            valuesAsSet.add(value);// Backed by concurrent hash map should be thread safe
        } else {
            synchronized (this) { // syncing changes on this is fine, don't want to on values itself as it's about to be changed - synchronizing on a non final field is asking for trouble
                // The ol' double checked locking
                if (valuesAsSet != null) {
                    valuesAsSet.add(value);
                    return;
                }
                // it's this contains expense that means we should stop using ArrayList over a certain size.
                // If loading skip the duplication check, we assume data integrity, asList an attempt to reduce garbage, I think it's object is lighter than an Arraylist. It won't be hit during loading but will during importing,
                if (noDuplicationCheck || !Arrays.asList(values).contains(value)) {
                    if (values.length >= ARRAYTHRESHOLD) { // we need to convert. copy the array to a new concurrent hash set then set it
                        /* Note! I've read it would be possible in theory for a half baked reference to the set to escape if the reference wasn't volatile.
                        That is to say a reference escaping before the constructor completed.
                        Not entirely sure about this but I've made it volatile and also will now add a local reference to the set to populate it before switching in.
                        volatile should ensure these changes are reflected when valuesAsSet is accessed.*/
                        Set<Value> localValuesAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
                        localValuesAsSet.addAll(Arrays.asList(values)); // add the existing ones
                        localValuesAsSet.add(value);
                        valuesAsSet = localValuesAsSet; // and now switch it in - since we have used a local variable this should fulfil textbook double checked locking
                        //values = new ArrayList<Value>(); // to save memory, leaving commented as the saving probably isn't that much and I'm a little worried about concurrency.
                        // todo - profile how many of these are left over in a large database?
                    } else { // ok we have to switch a new one in, need to think of the best way with the new array model
                        // this is synchronized, I should be able to be simple about this and be safe still
                        // new code to deal with arrays assigned to the correct size on loading
                        if (values.length != 0 && values[values.length - 1] == null) { // during loading - should noduplication really be used since that's what it is? todo
                            for (int i = 0; i < values.length; i++) { // being synchronised this should be all ok
                                if (values[i] == null) {
                                    values[i] = value;
                                    break;
                                }
                            }
                        } else { // normal modification
                            Value[] newValuesArray = new Value[values.length + 1];
                            System.arraycopy(values, 0, newValuesArray, 0, values.length); // intellij simplified it to this, should be fine
                            newValuesArray[values.length] = value;
                            values = newValuesArray;
                        }
                    }
                }
            }
        }
    }

    private static AtomicInteger removeFromValuesCount = new AtomicInteger(0);

    void removeFromValues(final Value value) throws Exception {
        removeFromValuesCount.incrementAndGet();
        synchronized (this) { // just sync on this object to protect the lists
            // double check - I'd forgotten to here! Regardless of whether values as set is volatile or not it could have been set in the mean time by addToValues above
            if (valuesAsSet != null) {
                if (valuesAsSet.remove(value)) {
                    valuesIncludingChildrenCache = null;
                }
                //return;
            }
            List<Value> valuesList = Arrays.asList(values);
            if (valuesList.contains(value)) {
                // ok and a manual copy, again since synchronized I can't see a massive problem here.
                Value[] newValuesArray = new Value[values.length - 1];
                int newArrayPosition = 0;// gotta have a separate index on the new array, they will go out of sync
                for (Value value1 : values) { // do one copy skipping the element we want removed
                    // WFC found null pointers in the values list - code amended to remove them.
                    //if (value1!=null && !value1.equals(value)) { // if it's not the one we want to return then copy
                        newValuesArray[newArrayPosition] = value1;
                        newArrayPosition++;
                   // }
                }
                //if (newArrayPosition==values.length - 1){
                    values = newValuesArray;
               // }else{
              //      values = Arrays.copyOf(newValuesArray,newArrayPosition);
              //  }
                valuesIncludingChildrenCache = null;
            }
        }
    }

    private static AtomicInteger getParentsCount = new AtomicInteger(0);

    public Collection<Name> getParents() {
        getParentsCount.incrementAndGet();
        return parentsAsSet != null ? Collections.unmodifiableSet(parentsAsSet) : parents.length > 0 ? Collections.unmodifiableList(Arrays.asList(parents)) : Collections.emptyList();
    }

    public boolean hasParents() {
        return parentsAsSet != null || parents.length > 0;
    }

    public boolean hasParentsAsSet() {
        return parentsAsSet != null;
    }

    private static AtomicInteger getParentsAsSetCount = new AtomicInteger(0);

    // if used incorrectly means NPE, I don't mind about this for the mo. We're allowing interpretSetTerm low level access to the list and sets to stop unnecessary collection copying in the query parser
    public Set<Name> getParentsAsSet() {
        getChildrenAsSetCount.incrementAndGet();
        return Collections.unmodifiableSet(parentsAsSet);
    }

    private static AtomicInteger getParentsAsListCount = new AtomicInteger(0);

    public List<Name> getParentsAsList() {
        getParentsAsListCount.incrementAndGet();
        return parents.length > 0 ? Collections.unmodifiableList(Arrays.asList(parents)) : Collections.emptyList();
    }

    // note these two should be called in synchronized blocks if acting on things like parents, children etc
    // doesn't check contains, there is logic after the contains when adding which can't go in here (as in are we going to switch to set?)
    private static AtomicInteger nameArrayAppendCount = new AtomicInteger(0);

    private Name[] nameArrayAppend(Name[] source, Name toAppend) {
        nameArrayAppendCount.incrementAndGet();
        Name[] newArray = new Name[source.length + 1];
        System.arraycopy(source, 0, newArray, 0, source.length); // intellij simplified it to this, should be fine
        newArray[source.length] = toAppend;
        return newArray;
    }

    // I realise some of this stuff is probably very like the internal workings of ArrayList! Important here to save space with vanilla arrays I'm rolling my own.

    private static AtomicInteger nameArrayAppend2Count = new AtomicInteger(0);

    private Name[] nameArrayAppend(Name[] source, Name toAppend, int position) {
        nameArrayAppend2Count.incrementAndGet();
        if (position >= source.length) {
            return nameArrayAppend(source, toAppend);
        }
        Name[] newArray = new Name[source.length + 1];
        for (int i = 0; i < source.length; i++) {
            if (i <= position) {
                newArray[i] = source[i];
                if (i == position) {
                    newArray[i + 1] = toAppend;
                }
            } else {
                newArray[i + 1] = source[i];
            }
        }
        return newArray;
    }

    // can check contains

    private static AtomicInteger nameArrayRemoveIfExistsCount = new AtomicInteger(0);

    private Name[] nameArrayRemoveIfExists(Name[] source, Name toRemove) {
        nameArrayRemoveIfExistsCount.incrementAndGet();
        List<Name> sourceList = Arrays.asList(source);
        if (sourceList.contains(toRemove)) {
            return nameArrayRemove(source, toRemove);
        } else {
            return source;
        }
    }

    // note, assumes it is in there! Otherwise will be an exception

    private static AtomicInteger nameArrayRemoveCount = new AtomicInteger(0);

    private Name[] nameArrayRemove(Name[] source, Name toRemove) {
        nameArrayRemoveCount.incrementAndGet();
        Name[] newArray = new Name[source.length - 1];
        int newArrayPosition = 0;// gotta have a separate index on the new array, they will go out of sync
        for (Name name : source) { // do one copy skipping the element we want removed
            if (name != toRemove) { // if it's not the one we want to return then copy
                newArray[newArrayPosition] = name;
                newArrayPosition++;
            }
        }
        return newArray;
    }

    private void addToParents(final Name name) throws Exception {
        addToParents(name, false);
    }
    /* don't allow external classes to set the parents I mean by function or otherwise, Name can manage this based on set children
    this is absolutely hammered on loading, saving bits of logic or bytes of garbage generated is worth it!
    for notes about the pattern here see addToValues */

    private static AtomicInteger addToParentsCount = new AtomicInteger(0);

    private void addToParents(final Name name, boolean noDuplicationCheck) throws Exception {
        addToParentsCount.incrementAndGet();
        if (parentsAsSet != null) {
            parentsAsSet.add(name);
        } else {
            synchronized (this) {
                if (parentsAsSet != null) {
                    parentsAsSet.add(name);
                    return;
                }
                if (noDuplicationCheck || !Arrays.asList(parents).contains(name)) {
                    if (parents.length >= ARRAYTHRESHOLD) {
                        parentsAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
                        parentsAsSet.addAll(Arrays.asList(parents));
                        parentsAsSet.add(name);
                    } else {
                        if (parents.length != 0 && parents[parents.length - 1] == null) {
                            for (int i = 0; i < parents.length; i++) {
                                if (parents[i] == null) {
                                    parents[i] = name;
                                    break;
                                }
                            }
                        } else {
                            parents = nameArrayAppend(parents, name);
                        }
                    }
                }
            }
        }
    }

    private static AtomicInteger removeFromParentsCount = new AtomicInteger(0);

    private void removeFromParents(final Name name) throws Exception {
        removeFromParentsCount.incrementAndGet();
        if (parentsAsSet != null) {
            parentsAsSet.remove(name);
        } else {
            synchronized (this) { // just sync on this object to protect the lists
                if (parentsAsSet != null) { // double check it, I'd forgotten to before! - reads being slightly out of date is fine, modification stuff isn't
                    parentsAsSet.remove(name);
                    return;
                }
                parents = nameArrayRemoveIfExists(parents, name);
            }
        }
    }

    /* returns a collection, I think this is just iterated over to check stuff but a set internally as it should not have duplicates. These two functions moved
    here from the spreadsheet as I want direct access to the parents array, saving a fair amount of garbage if it's hammered (array vs ArrayList.iterator())*/

    private static AtomicInteger findAllParentsCount = new AtomicInteger(0);

    public Collection<Name> findAllParents() {
        findAllParentsCount.incrementAndGet();
        final Set<Name> allParents = HashObjSets.newMutableSet(); // should be ok to use these now
        findAllParents(this, allParents);
        return allParents;
    }

    private static AtomicInteger findAllParents2Count = new AtomicInteger(0);

    // note : this was using getParents, since it was really being hammered this was no good due to the garbage generation of that function, hence the change
    // public and stateless, in here as it accesses things I don't want accessed outside
    public static void findAllParents(Name name, final Set<Name> allParents) {
        findAllParents2Count.incrementAndGet();

        if (name.parentsAsSet != null) {
            for (Name parent : name.parentsAsSet) { // we'll allow this kind of access in here
                if (allParents.add(parent)) {
                    findAllParents(parent, allParents);
                }
            }
        } else if (name.parents.length > 0) {
            for (int i = 0; i < name.parents.length; i++) {
                if (name.parents[i]==null){
                    System.out.println("DATABASE CORRUPTION " + name.getDefaultDisplayName() + " id " + name.getId() + " has a null parent");
                }else {
                    if (allParents.add(name.parents[i])) { // the function was moved in here to access this array directly. Externally it would need to be wrapped in an unmodifiable List. Means garbage!
                        findAllParents(name.parents[i], allParents);
                    }
                }
            }
        }
    }

    // as above being made static in here due to performance
    private static AtomicInteger memberNameCount = new AtomicInteger(0);

    public static Name memberName(Name element, Name topSet) {
        if (element.parentsAsSet != null) {
            for (Name parent : element.parentsAsSet) {
                if (parent.equals(topSet)) {
                    return element;
                }
                Name ancestor = memberName(parent, topSet);
                if (ancestor != null) {
                    return ancestor;
                }
            }
        } else if (element.parents.length > 0) {
            for (int i = 0; i < element.parents.length; i++) {
                if (element.parents[i].equals(topSet)) {
                    return element;
                }
                Name ancestor = memberName(element.parents[i], topSet);
                if (ancestor != null) {
                    return ancestor;
                }
            }
        }
        return null;
    }


    private static AtomicInteger addNamesCount = new AtomicInteger(0);

    // was in name service, added in here as a static function to give it direct access to the private arrays. Again an effort to reduce garbage.
    public static void addNames(final Name name, Collection<Name> namesFound, final int currentLevel, final int level) throws Exception {
        addNamesCount.incrementAndGet();
        if (!name.hasChildren()) {
            if (level == NameService.LOWEST_LEVEL_INT) {
                namesFound.add(name);
            }
        } else {
            if (currentLevel == (level - 1)) { // then we want the next one down, just add it all . . .
                if (name.childrenAsSet != null) {
                    namesFound.addAll(name.childrenAsSet);
                } else if (name.children.length > 0) {
                    for (int i = 0; i < name.children.length; i++) { // intellij wants to use the collections implementation, I think this is slightly more efficient
                        namesFound.add(name.children[i]);
                    }
                }
            } else {
                for (Name child : name.getChildren()) {// Accessing children as above might save a bit of garbage but I think the big wins have been done
                    addNames(child, namesFound, currentLevel + 1, level);
                }
            }
        }
    }

    // to support negative levels on children clause, level is seen as parent level, same as above but moving in the opposite direction
    public static void addParentNames(final Name name, Collection<Name> namesFound, final int currentLevel, final int level) throws Exception {
        addNamesCount.incrementAndGet();
        if (!name.hasParents()) {
            if (level == NameService.LOWEST_LEVEL_INT) { // misnomer but same logic
                namesFound.add(name);
            }
        } else {
            if (currentLevel == (level - 1)) { // then we want the next one up, just add it all . . .
                if (name.parentsAsSet != null) {
                    namesFound.addAll(name.parentsAsSet);
                } else if (name.parents.length > 0) {
                    for (int i = 0; i < name.parents.length; i++) { // intellij wants to use the collections implementation, I think this is slightly more efficient
                        namesFound.add(name.parents[i]);
                    }
                }
            } else {
                for (Name child : name.getParents()) {
                    addParentNames(child, namesFound, currentLevel + 1, level);
                }
            }
        }
    }

    // we are now allowing a name to be in more than one top parent, hence the name change

    private static AtomicInteger findATopParentCount = new AtomicInteger(0);

    public Name findATopParent() {
        findATopParentCount.incrementAndGet();
        if (hasParents()) {
            Name parent = getParents().iterator().next();
            while (parent != null) { // can this ever be null? I'd say it shouldn't be. Logic works fine though.
                if (parent.hasParents() && parent.getAttribute(LOCAL) == null) {
                    parent = parent.getParents().iterator().next();
                } else {
                    return parent; // it has no parents, must be top
                }
            }
        }
        return this;
    }

    // we are now allowing a name to be in more than one top parent, hence the name change

    private static AtomicInteger findTopParentsCount = new AtomicInteger(0);

    public List<Name> findTopParents() {
        findTopParentsCount.incrementAndGet();
        List<Name> toReturn = new ArrayList<>();
        if (hasParents()) {
            for (Name parent : findAllParents()) {
                if (!parent.hasParents()) {
                    toReturn.add(parent);
                }
            }
            return toReturn;
        }
        return Collections.singletonList(this); // no parents then this is a top parent
    }

    // same logic as find all parents but returns a set, should be correct
    // Koloboke makes the sets as light as can be expected, volatile to comply with double-checked locking pattern https://en.wikipedia.org/wiki/Double-checked_locking
    private volatile Set<Name> findAllChildrenCache = null;

    private static AtomicInteger finaAllChildrenCount = new AtomicInteger(0);

    private void findAllChildren(Name name, final Set<Name> allChildren) {
        finaAllChildrenCount.incrementAndGet();
        // similar to optimisation for get all parents, this potentially could cause a concurrency problem if the array were shrunk by another thread, I don't think this a concern in the context it's used
        // and we'll know if we look at the log. If this happened a local reference to the array should sort it, save the pointer garbage for the mo
        if (name.childrenAsSet != null) {
            for (Name child : name.childrenAsSet) { // as mentioned above I'll allow this kind of access in here
                if (allChildren.add(child)) {
                    findAllChildren(child, allChildren);
                }
            }
        } else if (name.children.length > 0) {
            for (int i = 0; i < name.children.length; i++) {
                if (allChildren.add(name.children[i])) {
                    findAllChildren(name.children[i], allChildren);
                }
            }
        }
    }

    private static AtomicInteger finaAllChildren2Count = new AtomicInteger(0);

    public Collection<Name> findAllChildren() {
        finaAllChildren2Count.incrementAndGet();
            /* local reference useful for my logic anyway but also fulfil double-checked locking.
            having findAllChildrenCache volatile in addition to this variable should mean things are predictable.
             */
            Set<Name> localReference = findAllChildrenCache;
            if (localReference == null) {
                // ok I don't want to be building two at a time, hence I want to synchronize this bit,
                synchronized (this) { // ideally I wouldn't synchronize on this, it would be on findAllChildrenCache but it's not final and I don't want it to be for the moment
                    localReference = findAllChildrenCache; // check again after synchronized, it may have been sorted in the mean time
                    if (localReference == null) {
                        localReference = HashObjSets.newUpdatableSet();
                        findAllChildren(this, localReference);
                        if (localReference.isEmpty()) {
                            findAllChildrenCache = Collections.emptySet(); // stop memory overhead, ooh I feel all clever!
                        } else {
                            findAllChildrenCache = localReference;
                        }
                    }
                }
            }
            return Collections.unmodifiableSet(localReference);
    }

    /* as above but for values, proved to provide a decent speed increase
    plus I can check some alternative set intersection stuff
    Tie its invalidation to the find all children invalidation
    See comments in findAllChildren for cache pattern notes
    */
    private volatile Set<Value> valuesIncludingChildrenCache = null;

    private static AtomicInteger findValuesIncludingChildrenCount = new AtomicInteger(0);

    public Set<Value> findValuesIncludingChildren() {
        findValuesIncludingChildrenCount.incrementAndGet();
            Set<Value> localReference = valuesIncludingChildrenCache;
            if (localReference == null) {
                synchronized (this) {
                    localReference = valuesIncludingChildrenCache;
                    if (localReference == null) {
                        localReference = HashObjSets.newUpdatableSet(getValues());
                        int i;
                        for (Name child : findAllChildren()) {
                            if (child.valuesAsSet != null) {
                                localReference.addAll(child.valuesAsSet);
                            } else if (child.values.length > 0) {
                                for (i = 0; i < child.values.length; i++) {
                                    localReference.add(child.values[i]);
                                }
                            }
                        }
                        if (localReference.isEmpty()) {
                            valuesIncludingChildrenCache = Collections.emptySet();
                        } else {
                            valuesIncludingChildrenCache = localReference;
                        }
                    }
                }
            }
            return Collections.unmodifiableSet(localReference);
    }

    // synchronized? Not sure if it matters, don't need immediate visibility and the cache read should (!) be thread safe.
    // The read uses synchronized to stop creating the cache more than necessary rather than to be totally up to date
    private static AtomicInteger clearChildrenCachesCount = new AtomicInteger(0);

    void clearChildrenCaches() {
        clearChildrenCachesCount.incrementAndGet();
        findAllChildrenCache = null;
        valuesIncludingChildrenCache = null;
    }

    private static AtomicInteger getChildrenCount = new AtomicInteger(0);

    public Collection<Name> getChildren() {
        getChildrenCount.incrementAndGet();
        return childrenAsSet != null ? Collections.unmodifiableSet(childrenAsSet) : children.length > 0 ? Collections.unmodifiableList(Arrays.asList(children)) : Collections.emptyList();
    }

    private static AtomicInteger getChildrenAsSetCount = new AtomicInteger(0);

    // if used incorrectly means NPE, I don't mind about this for the mo. We're allowing interpretSetTerm low level access to the list and sets to stop unnecessary collection copying in the query parser
    public Set<Name> getChildrenAsSet() {
        getChildrenAsSetCount.incrementAndGet();
        return Collections.unmodifiableSet(childrenAsSet);
    }

    private static AtomicInteger getChildrenAsListCount = new AtomicInteger(0);

    public List<Name> getChildrenAsList() {
        getChildrenAsListCount.incrementAndGet();
        return children.length > 0 ? Collections.unmodifiableList(Arrays.asList(children)) : Collections.emptyList();
    }

    public boolean hasChildren() {
        return childrenAsSet != null || children.length > 0;
    }

    public boolean hasChildrenAsSet() {
        return childrenAsSet != null;
    }

    // might seem inefficient but the adds and removes deal with parents and things. Might reconsider code if used more heavily
    // each add/remove is thread safe but should not allow two to run concurrently
    private static AtomicInteger setChildrenCount = new AtomicInteger(0);

    public synchronized void setChildrenWillBePersisted(Collection<Name> children) throws Exception {
        setChildrenCount.incrementAndGet();
        Collection<Name> existingChildren = getChildren();
        /* like an equals but the standard equals might trip up on different collection types
        notable that contains all will ignore ordering! If large it will be a HashSet anyway
        this could provide a major speed increase where this function is "recklessly" called (e.g. in the "as" bit in parseQuery in NameService),
        don't keep reassigning "as" and clearing the caches when the collection is the same */
        if (children.size() != existingChildren.size() || !children.containsAll(existingChildren)) {
            for (Name oldChild : this.getChildren()) {
                removeFromChildrenWillBePersisted(oldChild, false); // no cache clear, will do it in a mo!
            }
            for (Name child : children) {
                if (child != null) {
                    addChildWillBePersisted(child, 0, false); // no cache clear, will do it in a mo!
                }
            }
            clearChildrenCaches();
            //getAzquoMemoryDB().clearSetAndCountCacheForName(this);
        }
    }

    private static AtomicInteger addChildWillBePersistedCount = new AtomicInteger(0);

    public void addChildWillBePersisted(Name child) throws Exception {
        addChildWillBePersistedCount.incrementAndGet();
        addChildWillBePersisted(child, 0, true);
    }

    // not being used anywhere . . . order might still be there from insert order but adding in a specific position is not
    private static AtomicInteger addChildWillBePersisted2Count = new AtomicInteger(0);

    public void addChildWillBePersisted(Name child, int position) throws Exception {
        addChildWillBePersisted2Count.incrementAndGet();
        addChildWillBePersisted(child, position, true);
    }

    // with position, will just add if none passed note : this sees position as starting at 1!
    // note : modified to do nothing if the name is in the set. No changing of position.

    private static AtomicInteger addChildWillBePersisted3Count = new AtomicInteger(0);

    private void addChildWillBePersisted(Name child, int position, boolean clearCache) throws Exception {
        addChildWillBePersisted3Count.incrementAndGet();
        checkDatabaseMatches(child);
        if (child.equals(this)) return;//don't put child into itself
        /*removing this check - it takes far too long - maybe should make it optional
        if (findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        */
        // NOTE! for the set version we're now just using a set backed by a concurrent hash map, for large child sets ordering will be ignored.
        // While childrenAsSet is thread safe I think I'm going to need to synchronize the lot to make make state more consistent, at least with itself
        boolean changed = false; // only do the after stuff if something changed
        synchronized (this) {
            if (childrenAsSet != null) {
                changed = childrenAsSet.add(child);
            } else {
                List<Name> childrenList = Arrays.asList(children);
                if (!childrenList.contains(child)) {
                    changed = true;
                    // like parents, hope the logic is sound
                    if (childrenList.size() >= ARRAYTHRESHOLD) {
                        childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
                        childrenAsSet.addAll(childrenList);
                        childrenAsSet.add(child);
                        // children new arraylist?;
                    } else {
                        if (position != 0) {
                            children = nameArrayAppend(children, child, position);
                        } else {
                            // unlike with parents and values we don't want to look for an empty initialised array here,
                            // children can be dealt with more cleanly in the linking (as in we'll make the array to size in one shot there after the names have been set in the maps in AzquoMemoryDB)
                            children = nameArrayAppend(children, child);
                        }
                    }
                }
            }
            if (changed) { // new logic, only do these things if something was changed
                child.addToParents(this);//synchronized internally with this also so will not deadlock
                setNeedsPersisting();
                //and check that there are not indirect connections which should be deleted (e.g. if London exists in UK and Europe, and we are now
                //specifying that UK is in Europe, we must remove the direct link from London to Europe - this was commented due to speed I guess? todo clarify and uncomment or delete
                /*
                for (Name descendant : child.findAllChildren(false)) {
                    if (getChildren().contains(descendant)) {
                        removeFromChildrenWillBePersisted(descendant);
                    }
                }*/
            }
        }
        // cache clearing stuff can happen outside the synchronized block
        if (changed && clearCache) {
            clearChildrenCaches();
            //getAzquoMemoryDB().clearSetAndCountCacheForName(this);
        }
    }

    private static AtomicInteger removeFromChildrenWillBePersistedCount = new AtomicInteger(0);

    public void removeFromChildrenWillBePersisted(Name name) throws Exception {
        removeFromChildrenWillBePersistedCount.incrementAndGet();
        removeFromChildrenWillBePersisted(name, true);
    }

    private static AtomicInteger removeFromChildrenWillBePersisted2Count = new AtomicInteger(0);

    private void removeFromChildrenWillBePersisted(Name name, boolean clearCache) throws Exception {
        removeFromChildrenWillBePersisted2Count.incrementAndGet();
        checkDatabaseMatches(name);// even if not needed throw the exception!
        // maybe could narrow this a little?
        synchronized (this) {
            if (getChildren().contains(name)) { // then something to remove
                name.removeFromParents(this);
                if (childrenAsSet != null) {
                    childrenAsSet.remove(name);
                } else {
                    children = nameArrayRemove(children, name); // note this will fail if it turns out children does not contain the name. Should be ok.
                }
                setNeedsPersisting();
            }
        }
        if (clearCache) {
            clearChildrenCaches();
            //getAzquoMemoryDB().clearSetAndCountCacheForName(this);
        }
    }

    // notably, since the map is created on the fly and not canonical I could just return it. A moot point I think.
    private static AtomicInteger getAttributesCount = new AtomicInteger(0);

    public Map<String, String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return Collections.unmodifiableMap(nameAttributes.getAsMap());
    }

    // to be used in loading - could cause corruption so protected. In fact this if for indexing in AzquoMemoryDB, using getAttributes() above would be much better but that means garbage, need a solution to that TODO
    List<String> getAttributeKeys() {
        return nameAttributes.getAttributeKeys();
    }

    List<String> getAttributeValues() {
        return nameAttributes.getAttributeValues();
    }

    // I think plain old synchronized here is safe enough if not that fast
    // Hammered on importing but not loading, hence why I've not been as careful on garbage as I might be

    private static AtomicInteger setAttributeWillBePersistedCount = new AtomicInteger(0);

    public synchronized void setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {
        setAttributeWillBePersistedCount.incrementAndGet();
        // make safe for new way of persisting attributes
        if (attributeName.contains(ATTRIBUTEDIVIDER)) {
            attributeName = attributeName.replace(ATTRIBUTEDIVIDER, "");
        }
        if (attributeValue != null && attributeValue.contains(ATTRIBUTEDIVIDER)) {
            attributeValue = attributeValue.replace(ATTRIBUTEDIVIDER, "");
        }
        attributeName = attributeName.trim().toUpperCase(); // I think this is the only point at which attributes are created thus if it's uppercased here we should not need to check anywhere else
        /* important, manage persistence, allowed name rules, db look ups only care about ones in this set
         code adapted from map based code to lists assume nameAttributes reference only set in code synchronized in these three functions and constructors*/
        List<String> attributeKeys = new ArrayList<>(nameAttributes.getAttributeKeys());
        List<String> attributeValues = new ArrayList<>(nameAttributes.getAttributeValues());

        int index = attributeKeys.indexOf(attributeName);
        String existing = null;
        if (index != -1) {
            // we want an index out of bounds to be thrown here if they don't match
            existing = attributeValues.get(index);
        }
        if (attributeValue == null || attributeValue.length() == 0) {
            // delete it
            if (existing != null) {
                attributeKeys.remove(index);
                attributeValues.remove(index);
                //attributes.remove(attributeName);
                getAzquoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
                nameAttributes = new NameAttributes(attributeKeys, attributeValues);
                setNeedsPersisting();
            }
            return;
        }
        if (existing != null && existing.equals(attributeValue)) {
            return;
        }
        if (existing != null) {
            // just update the values
            attributeValues.remove(index);
            attributeValues.add(index, attributeValue);
            getAzquoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
        } else {
            // a new one
            attributeKeys.add(attributeName);
            attributeValues.add(attributeValue);
        }
        nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        // now deal with the DB maps!
        // ok here I did say addNameToAttributeNameMap but that is inefficient, it uses every attribute, we've only changed one
        getAzquoMemoryDB().setAttributeForNameInAttributeNameMap(attributeName, attributeValue, this);
        setNeedsPersisting();
    }

    private static AtomicInteger removeAttributeWillBePersistedCount = new AtomicInteger(0);

    private synchronized void removeAttributeWillBePersisted(String attributeName) throws Exception {
        removeAttributeWillBePersistedCount.incrementAndGet();
        attributeName = attributeName.trim().toUpperCase();
        int index = nameAttributes.getAttributeKeys().indexOf(attributeName);
        if (index != -1) {
            List<String> attributeKeys = new ArrayList<>(nameAttributes.getAttributeKeys());
            List<String> attributeValues = new ArrayList<>(nameAttributes.getAttributeValues());
            getAzquoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, attributeValues.get(index), this);
            attributeKeys.remove(index);
            attributeValues.remove(index);
            nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        }
        setNeedsPersisting();
    }

    // convenience - plain clearing of this object won't change the indexes in the memory db. Hence remove on each one.
    private static AtomicInteger clearAttributesCount = new AtomicInteger(0);

    public synchronized void clearAttributes() throws Exception {
        clearAttributesCount.incrementAndGet();
        for (String attribute : nameAttributes.attributeKeys) { // nameAttributes will be reassigned by the function but that should be ok, hang onto the key set as it was at the beginning
            removeAttributeWillBePersisted(attribute);
        }
    }

    // criteria for fall back attributes added by WFC, not entirely sure I'd have done this but anyway
    private static AtomicInteger findParentAttributesCount = new AtomicInteger(0);

    private String findParentAttributes(Name child, String attributeName, Set<Name> checked) {
        findParentAttributesCount.incrementAndGet();
        attributeName = attributeName.trim().toUpperCase();
        for (Name parent : child.getParents()) {
            if (parent==null){
                System.out.println("null parent on " + child.getDefaultDisplayName());
            }else {
                if (!checked.contains(parent)) {
                    checked.add(parent);
                    if (parent.getDefaultDisplayName() != null && parent.getDefaultDisplayName().equalsIgnoreCase(attributeName)) {
                        return child.getDefaultDisplayName();
                    }
                    String attribute = parent.getAttribute(attributeName, true, checked);
                    if (attribute != null) {
                        return attribute;
                    }
                }
            }
        }
        return null;
    }

    // default to parent check

    private static AtomicInteger getAttributeCount = new AtomicInteger(0);

    public String getAttribute(String attributeName) {
        getAttributeCount.incrementAndGet();
        return getAttribute(attributeName, true, HashObjSets.newMutableSet());
    }


    private static AtomicInteger getAttribute2Count = new AtomicInteger(0);

    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked) {
        attributeName = attributeName.toUpperCase(); // edd adding (back?) in, need to do this since all attributes are uppercase internally - check there are not redundant uppercases in other places TODO
        getAttribute2Count.incrementAndGet();
        String attribute = nameAttributes.getAttribute(attributeName);
        if (attribute != null) return attribute;
        //look up the chain for any parent with the attribute
        if (parentCheck) {
            return findParentAttributes(this, attributeName, checked);
        }
        return null;
    }

/*    public boolean hasInParentTree(final Name testParent) {
        for (Name parent : getParents()) {
            if (testParent == parent || parent.hasInParentTree(testParent)) {
                return true;
            }
        }
        return false;
    }*/

    @Override
    final protected void entitySpecificSetAsPersisted() {
        getAzquoMemoryDB().removeNameNeedsPersisting(this);
    }

    @Override
    final protected void entitySpecificSetNeedsPersisting() {
        getAzquoMemoryDB().setNameNeedsPersisting(this);
    }

    // in here is a bit more efficient I think but should it be in the DAO?

    private static AtomicInteger getAttributesForFastStoreCount = new AtomicInteger(0);

    public String getAttributesForFastStore() {
        getAttributesForFastStoreCount.incrementAndGet();
        StringBuilder stringBuilder = new StringBuilder();
        NameAttributes localNameAttributes = nameAttributes; // grab a reference just to be safe
        for (int i = 0; i < localNameAttributes.attributeKeys.length; i++) {
            if (i != 0) {
                stringBuilder.append(ATTRIBUTEDIVIDER);
            }
            stringBuilder.append(localNameAttributes.attributeKeys[i]);
            stringBuilder.append(ATTRIBUTEDIVIDER);
            stringBuilder.append(localNameAttributes.attributeValues[i]);
        }
        return stringBuilder.toString();
    }

    public byte[] getChildrenIdsAsBytes() {
        return getChildrenIdsAsBytes(0);
    }

    // not really bothered about factoring at the moment - it would involve using the getChildren and getNames functions which needlesly create garbage (this function will be hammered when persisting, would rather not make the garbage)

    private static AtomicInteger getChildrenIdsAsBytesCount = new AtomicInteger(0);

    private byte[] getChildrenIdsAsBytes(int tries) {
        getChildrenIdsAsBytesCount.incrementAndGet();
        try {
            if (childrenAsSet != null) {
                // theoretical possibility of size changing between allocate and creating the byte array
                // my initial method to sort this will be to catch the exception and try again. TODO
                ByteBuffer buffer = ByteBuffer.allocate(childrenAsSet.size() * 4);
                for (Name name : childrenAsSet) {
                    buffer.putInt(name.getId());
                }
                return buffer.array();
            } else {
                ByteBuffer buffer = ByteBuffer.allocate(children.length * 4);
                for (Name name : children) {
                    buffer.putInt(name.getId());
                }
                return buffer.array();
            }
        } catch (BufferOverflowException e){
            if (tries < 5){ // a bit arbitrary
                tries++;
                System.out.println("retrying after buffer overflow in getChildrenIdsAsBytes on " + getDefaultDisplayName() + " try number " + tries);
                return getChildrenIdsAsBytes(tries);
            } else {
                throw  e;
            }
        }
    }

    /* protected to only be used by the database loading, can't be called in the constructor as name by id maps may not be populated
    changing synchronized to only relevant portions
    note : this function is absolutely hammered while linking (surprise!) so optimiseations here are helpful */

    private static AtomicInteger linkCount = new AtomicInteger(0);

    void link() throws Exception {
        linkCount.incrementAndGet();
        if (getAzquoMemoryDB().getNeedsLoading() && childrenCache != null) { // if called and these conditions are not true then there's a problem
            synchronized (this) {
                // it's things like this that I believe will be way faster and lighter ongarbage than the old json method
                int noChildren = childrenCache.length / 4;
                ByteBuffer byteBuffer = ByteBuffer.wrap(childrenCache);
                if (noChildren > ARRAYTHRESHOLD) {
                    this.childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(noChildren));
                    for (int i = 0; i < noChildren; i++) {
                        this.childrenAsSet.add(getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4)));
                    }
                } else {
                    Name[] newChildren = new Name[noChildren];
                    for (int i = 0; i < noChildren; i++) {
                        newChildren[i] = getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4));
                    }
                    this.children = newChildren;
                }
                childrenCache = null;// free the memory
            }
        } else {
            throw new Exception("Trying to link with no children cache or after loading?"); // children cache could be empty but should not be null!
        }
        /* need to sort out the parents - I deliberately excluded this from synchronisation or one could theoretically hit a deadlock.
         addToParents can be synchronized on the child. Changing from get children here as I want to avoid the array wrapping where I can (make less garbage)*/
        if (childrenAsSet != null) {
            for (Name newChild : childrenAsSet) {
                newChild.addToParents(this, true);
            }
        } else {
            for (int i = 0; i < children.length; i++) { // directly hitting the array could cause a problem if it were reassigned while this happened but here it should be fine. Again intellij wants an API call but I'm sceptical since this is hammered
                children[i].addToParents(this, true);
            }
        }
    }

    // first of its kind. Try to be comprehensive
    // want to make it synchronized but I'm calling synchronized functions on other objects. Hmmmmmmmm.
    private static AtomicInteger deleteCount = new AtomicInteger(0);

    public void delete() throws Exception {
        deleteCount.incrementAndGet();
        Collection<Value> values;
        Collection<Name> parents;

        // ok we want a thread safe snapshot really of things that are synchronized on other objects
        synchronized (this) {
            values = getValues();
            parents = getParents();
            // the basics are done here in the synchronized block
            getAzquoMemoryDB().removeNameFromDb(this);
            needsDeleting = true;
            setNeedsPersisting();
            // remove children - this is using the same lock so do it in here
            for (Name child : getChildren()) {
                removeFromChildrenWillBePersisted(child);
            }
            for (String attribute : getAttributeKeys()) {
                setAttributeWillBePersisted(attribute, null); // simple way to clear it from the indexes
            }
        }
        // then the actions on other objects - we now delete such values
        for (Value v : values) {
            v.delete();
        }
        // remove from parents
        for (Name parent : parents) {
            parent.removeFromChildrenWillBePersisted(this);
        }
    }

    static void printFunctionCountStats() {
        System.out.println("######### NAME FUNCTION COUNTS");
        System.out.println("newNameCount\t\t\t\t" + newNameCount.get());
        System.out.println("newNameCount3\t\t\t\t" + newName3Count.get());
        System.out.println("getDefaultDisplayNameCount\t\t\t\t" + getDefaultDisplayNameCount.get());
        System.out.println("getFullyQualifiedDefaultDisplayNameCount\t\t\t\t" + getFullyQualifiedDefaultDisplayNameCount.get());
        System.out.println("getValuesCount\t\t\t\t" + getValuesCount.get());
        System.out.println("transferValuesCount\t\t\t\t" + transferValuesCount.get());
        System.out.println("addToValuesCount\t\t\t\t" + addToValuesCount.get());
        System.out.println("removeFromValuesCount\t\t\t\t" + removeFromValuesCount.get());
        System.out.println("getParentsCount\t\t\t\t" + getParentsCount.get());
        System.out.println("nameArrayAppendCount\t\t\t\t" + nameArrayAppendCount.get());
        System.out.println("nameArrayAppend2Count\t\t\t\t" + nameArrayAppend2Count.get());
        System.out.println("nameArrayRemoveIfExistsCount\t\t\t\t" + nameArrayRemoveIfExistsCount.get());
        System.out.println("nameArrayRemoveCount\t\t\t\t" + nameArrayRemoveCount.get());
        System.out.println("addToParentsCount\t\t\t\t" + addToParentsCount.get());
        System.out.println("removeFromParentsCount\t\t\t\t" + removeFromParentsCount.get());
        System.out.println("findAllParentsCount\t\t\t\t" + findAllParentsCount.get());
        System.out.println("findAllParents2Count\t\t\t\t" + findAllParents2Count.get());
        System.out.println("memberNameCount\t\t\t\t" + memberNameCount.get());
        System.out.println("addNamesCount\t\t\t\t" + addNamesCount.get());
        System.out.println("findATopParentCount\t\t\t\t" + findATopParentCount.get());
        System.out.println("findTopParentsCount\t\t\t\t" + findTopParentsCount.get());
        System.out.println("finaAllChildrenCount\t\t\t\t" + finaAllChildrenCount.get());
        System.out.println("finaAllChildren2Count\t\t\t\t" + finaAllChildren2Count.get());
        System.out.println("findValuesIncludingChildrenCount\t\t\t\t" + findValuesIncludingChildrenCount.get());
        System.out.println("clearChildrenCachesCount\t\t\t\t" + clearChildrenCachesCount.get());
        System.out.println("getChildrenCount\t\t\t\t" + getChildrenCount.get());
        System.out.println("setChildrenCount\t\t\t\t" + setChildrenCount.get());
        System.out.println("addChildWillBePersistedCount\t\t\t\t" + addChildWillBePersistedCount.get());
        System.out.println("addChildWillBePersisted2Count\t\t\t\t" + addChildWillBePersisted2Count.get());
        System.out.println("addChildWillBePersisted3Count\t\t\t\t" + addChildWillBePersisted3Count.get());
        System.out.println("removeFromChildrenWillBePersistedCount\t\t\t\t" + removeFromChildrenWillBePersistedCount.get());
        System.out.println("removeFromChildrenWillBePersisted2Count\t\t\t\t" + removeFromChildrenWillBePersisted2Count.get());
        System.out.println("getAttributesCount\t\t\t\t" + getAttributesCount.get());
        System.out.println("setAttributeWillBePersistedCount\t\t\t\t" + setAttributeWillBePersistedCount.get());
        System.out.println("removeAttributeWillBePersistedCount\t\t\t\t" + removeAttributeWillBePersistedCount.get());
        System.out.println("clearAttributesCount\t\t\t\t" + clearAttributesCount.get());
        System.out.println("findParentAttributesCount\t\t\t\t" + findParentAttributesCount.get());
        System.out.println("getAttributeCount\t\t\t\t" + getAttributeCount.get());
        System.out.println("getAttribute2Count\t\t\t\t" + getAttribute2Count.get());
        System.out.println("getAttributesForFastStoreCount\t\t\t\t" + getAttributesForFastStoreCount.get());
        System.out.println("getChildrenIdsAsBytesCount\t\t\t\t" + getChildrenIdsAsBytesCount.get());
        System.out.println("getChildrenAsSet\t\t\t\t" + getChildrenAsSetCount.get());
        System.out.println("getChildrenAsList\t\t\t\t" + getChildrenAsListCount.get());
        System.out.println("getParentsAsSet\t\t\t\t" + getParentsAsSetCount.get());
        System.out.println("getParentsAsList\t\t\t\t" + getParentsAsListCount.get());
        System.out.println("linkCount\t\t\t\t" + linkCount.get());
        System.out.println("deleteCount\t\t\t\t" + deleteCount.get());
    }

    static void clearFunctionCountStats() {
        newNameCount.set(0);
        newName3Count.set(0);
        getDefaultDisplayNameCount.set(0);
        getValuesCount.set(0);
        transferValuesCount.set(0);
        addToValuesCount.set(0);
        removeFromValuesCount.set(0);
        getParentsCount.set(0);
        nameArrayAppendCount.set(0);
        nameArrayAppend2Count.set(0);
        nameArrayRemoveIfExistsCount.set(0);
        nameArrayRemoveCount.set(0);
        addToParentsCount.set(0);
        removeFromParentsCount.set(0);
        findAllParentsCount.set(0);
        findAllParents2Count.set(0);
        memberNameCount.set(0);
        addNamesCount.set(0);
        findATopParentCount.set(0);
        findTopParentsCount.set(0);
        finaAllChildrenCount.set(0);
        finaAllChildren2Count.set(0);
        findValuesIncludingChildrenCount.set(0);
        clearChildrenCachesCount.set(0);
        getChildrenCount.set(0);
        setChildrenCount.set(0);
        addChildWillBePersistedCount.set(0);
        addChildWillBePersisted2Count.set(0);
        addChildWillBePersisted3Count.set(0);
        removeFromChildrenWillBePersistedCount.set(0);
        removeFromChildrenWillBePersisted2Count.set(0);
        getAttributesCount.set(0);
        setAttributeWillBePersistedCount.set(0);
        removeAttributeWillBePersistedCount.set(0);
        clearAttributesCount.set(0);
        findParentAttributesCount.set(0);
        getAttributeCount.set(0);
        getAttribute2Count.set(0);
        getAttributesForFastStoreCount.set(0);
        getChildrenIdsAsBytesCount.set(0);
        getChildrenAsSetCount.set(0);
        getChildrenAsListCount.set(0);
        getParentsAsSetCount.set(0);
        getParentsAsListCount.set(0);
        linkCount.set(0);
        deleteCount.set(0);
    }
}