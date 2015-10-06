package com.azquo.memorydb.core;

import com.azquo.memorydb.Constants;
import com.azquo.memorydb.dao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Azquo object, names now have attributes and what was the name (as in the text) is now simply an attribute of the name
 * defined currently in a static below. Names can have parent and child relationships with multiple other names. Sets of names.
 * <p>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on
 * <p>
 * <p>
 * Of course we care about thread safety even if crude but we have another concern now : this object was weighing in at over 2k average in an example magento db
 * the goal is to bring it to sub 500 bytes. THis means in some cases variables being null until used and using arrays instead of sets switching for performance
 * when the list gets too big.
 * <p>
 * Update on memory : got it to about 850 bytes (magento example DB). Will park for the mo, further change would probably involve changes to attributes,
 * I mean the name of attributes not being held here for example
 * <p>
 * attributes case insensitive . . .not entirely happy about this
 * <p>
 * was comparable but this resulted in a code warning I've moved the comparator to NameService
 * <p>
 * Note : there's memory overhead but also garbage collection overhead, watch for avoidable throw away objects.
 */
public final class Name extends AzquoMemoryDBEntity {

    public static final String CALCULATION = "CALCULATION";
    public static final String LOCAL = "LOCAL";
    private static final String ATTRIBUTEDIVIDER = "¬"; // it will go attribute name, attribute vale, attribute name, attribute vale

    public static final char QUOTE = '`';

    private static final int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets

    // name needs this as it links to itself hence have to load all names THEN parse the json, other objects do not hence it's in here not the memory db entity
    // as mentioned just a cache while the names by id map is being populated

    // if we're going to speed up loading e.g. with blobs then this json cache can change to a loading cache that will have a json cache among others
    // this way there will be no extra overhead during normal running, sill one null reference
    // note : for full speed we'd have values in here too, has implications for how value is initialised

    private LoadCache loadCache;

    private static final class LoadCache {
        public final String jsonCache;
        public final byte[] childrenCache;

        public LoadCache(String jsonCache, byte[] childrenCache) {
            this.jsonCache = jsonCache;
            this.childrenCache = childrenCache;
        }
    }


//    private static final Logger logger = Logger.getLogger(Name.class);

    private Provenance provenance;
    private boolean additive;

    /* going to try for attributes as two arrays as this should save a lot of space vs a linked hash map
    that is to say one makes a new one of these when updating attributes and then switch it in. Hence atomic (but not necessarily visible!) switch of two arrays
    if an object reference is out of date it will at least be two consistent arrays

    I have no problem with out of date versions of nameAttributes as the JVM makes things visible to other threads as long as object contents remain consistent.

    This was using arraylists, I want to switch to arrays to save memory

    Gets to wrap with as list, don't want them altering the length anyway, this does have a bit of an overhead vs the old model but I don't think this will be a biggy. I guess check memory saving?
    Also I know that aslist doens't make immutable, I consider this class "trusted". If bothered can use unmodifiableList.

    ok using arrays here has saved about 5% on a database's memory usage. They stay unless there's a big unfixable problem.

    and put the get in here, stops worry about an object switch in the middle of a get

    */
    private static final class NameAttributes {
        private final String[] attributeKeys;
        private final String[] attributeValues;

        public NameAttributes(List<String> attributeKeys, List<String> attributeValues) throws Exception {
            if (attributeKeys.size() != attributeValues.size()) {
                throw new Exception("Keys and values for attributes must match!");
            }
            this.attributeKeys = new String[attributeKeys.size()];
            attributeKeys.toArray(this.attributeKeys);
            this.attributeValues = new String[attributeValues.size()];
            attributeValues.toArray(this.attributeValues);
        }

        // faster init - note this is kind of dangerous in that the arrays could be externally modified, used by the new fast loader
        public NameAttributes(String[] attributeKeys, String[] attributeValues) throws Exception {
            this.attributeKeys = attributeKeys;
            this.attributeValues = attributeValues;
        }

        public NameAttributes() { // blank default. Fine.
            attributeKeys = new String[0];
            attributeValues = new String[0];
        }

        public List<String> getAttributeKeys() {
            return Arrays.asList(attributeKeys);
        }

        public List<String> getAttributeValues() {
            return Arrays.asList(attributeValues);
        }

        public String getAttribute(String attributeName) {
            //attributeName = attributeName.toUpperCase(); // interesting I zapped this, why?
            int index = getAttributeKeys().indexOf(attributeName);
            if (index != -1) {
                return attributeValues[index];
            }
            return null;
        }

        public Map<String, String> getAsMap() {
            Map<String, String> attributesAsMap = new HashMap<>(attributeKeys.length);
            int count = 0;
            for (String key : attributeKeys) { // hmm, can still access and foreach on the internal array. Np I suppose!
                attributesAsMap.put(key, attributeValues[count]);
                count++;
            }
            return attributesAsMap;
        }

    }

    private NameAttributes nameAttributes;

    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo
    // these (values, parent) are for quick lookup, must be modified appropriately
    // to be clear, these are not used when persisting, they are derived from the name sets in values and the two below
    // Sets are expensive in terms of memory, will use arrays instead unless they get big (above threshold 512 at the mo) make a new array and switch on changing to make atomic and always wrap them unmodifiable on get
    // I'm not going to make these volatile as the ony time it really matters is on writes which are synchronized and I understand this deals with memory barriers
    private Set<Value> valuesAsSet;
    private Value[] values;
    private Set<Name> parentsAsSet;
    private Name[] parents;
    // we want thread safe != null test on changes but this should be done by synchronized
    private Set<Name> childrenAsSet;
    private Name[] children;

    // for the code to make new names

    private static AtomicInteger newName = new AtomicInteger(0);

    public Name(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, boolean additive) throws Exception {
        this(azquoMemoryDB, 0, null);
        newName.incrementAndGet();
        setProvenanceWillBePersisted(provenance);
        setAdditiveWillBePersisted(additive);
    }

    // protected, should only be called by azquo memory db
    // Lists/Sets are not set here as we need to wait then load from the json cache
    // use arrays internally?

    private static AtomicInteger newName2 = new AtomicInteger(0);

    protected Name(final AzquoMemoryDB azquoMemoryDB, int id, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        newName2.incrementAndGet();
        loadCache = new LoadCache(jsonFromDB, null);
        additive = true; // by default
        valuesAsSet = null;
        values = new Value[0]; // Turning these 3 lists to arrays, memory a priority
        parentsAsSet = null;
        parents = new Name[0];
        childrenAsSet = null;
        children = new Name[0];
        nameAttributes = new NameAttributes(); // attributes will nearly always be written over, this is just a placeholder
        getAzquoMemoryDB().addNameToDb(this);
    }

    // as above but using new fast loading - todo - work out if this bing public (as it needs to be for the DAO) is a problem

    private static AtomicInteger newName3 = new AtomicInteger(0);

    public Name(final AzquoMemoryDB azquoMemoryDB, int id, int provenanceId, boolean additive, String attributes, byte[] chidrenCache, int noParents, int noValues) throws Exception {
        super(azquoMemoryDB, id);
        newName3.incrementAndGet();
        loadCache = new LoadCache(null, chidrenCache); // link these after
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId); // see no reason not to do this here now
        this.additive = additive;
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
        values = new Value[noValues <= ARRAYTHRESHOLD ? noValues : 0]; // prepare the array, as long as this is taken into account later should be fine
        parentsAsSet = noParents > ARRAYTHRESHOLD ? Collections.newSetFromMap(new ConcurrentHashMap<>(noParents)) : null;
        parents = new Name[noParents <= ARRAYTHRESHOLD ? noParents : 0];
        childrenAsSet = null;// dealt with properly later
        children = new Name[0];
        getAzquoMemoryDB().addNameToDb(this);
    }

    // for convenience but be careful where it is used . . .
    private static AtomicInteger getDefaultDisplayNameCounter = new AtomicInteger(0);

    public String getDefaultDisplayName() {
        getDefaultDisplayNameCounter.incrementAndGet();
        return nameAttributes.getAttribute(Constants.DEFAULT_DISPLAY_NAME);
    }

    // provenance immutable. If it were not then would need to clone

    public Provenance getProvenance() {
        return provenance;
    }

    public boolean getAdditive() {
        return additive;
    }

    public synchronized void setAdditiveWillBePersisted(final boolean additive) throws Exception {
        if (this.additive != additive) {
            this.additive = additive;
            setNeedsPersisting();
        }
    }

    public synchronized void setProvenanceWillBePersisted(final Provenance provenance) throws Exception {
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
                ", additive=" + additive +
                ", nameAttributes=" + nameAttributes +
                '}';
    }

    // we assume sets are built on concurrent hash map and lists are not modifiable
    // even if based on concurrent hash map we don't want anything external modifying it, make the set unmodifiable

    private static AtomicInteger getValuesCounter = new AtomicInteger(0);
    public Collection<Value> getValues() {
        getValuesCounter.incrementAndGet();
        return valuesAsSet != null ? Collections.unmodifiableCollection(valuesAsSet) : Collections.unmodifiableCollection(Arrays.asList(values));
    }

    // added by WFC I guess, need to check on this - Edd
    // this could be non thread safe.

    private static AtomicInteger transferValuesCounter = new AtomicInteger(0);
    public void transferValues(Name from) throws Exception {
        transferValuesCounter.incrementAndGet();
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
    protected void addToValues(final Value value) throws Exception {
        addToValues(value, false);
    }

    private static AtomicInteger addToValuesCounter = new AtomicInteger(0);
    protected void addToValues(final Value value, boolean noDuplicationCheck) throws Exception {
        addToValuesCounter.incrementAndGet();
        checkDatabaseMatches(value);
        // may make this more claver as in clearing only if there's a change but not now
        valuesIncludingChildrenCache = null;
        valuesIncludingChildrenPayAttentionToAdditiveCache = null;
        if (valuesAsSet != null) {
            valuesAsSet.add(value);// Backed by concurrent hash map should be thread safe
        } else {
            synchronized (this) { // syncing changes on this is fine, don't want to on values itself as it's about to be changed - synchronizing on a non final field is asking for trouble
                // what if it simultaneously hit the array threshold at the same time? Double check it!
                if (valuesAsSet != null) {
                    valuesAsSet.add(value);
                    return;
                }
                // it's this contains expense that means we should stop using arraylists over a certain size.
                // If loading skip the duplication check, we assume data integrity, put the aslist in the conditional so we won't instantiate the object unnecessarily, this function is hammered on loading
                if (noDuplicationCheck || !Arrays.asList(values).contains(value)) {
                    // OK, here it may get interesting, what about size???
                    if (values.length >= ARRAYTHRESHOLD) { // we need to convert. copy the array to a new concurrent hashset then set it
                        valuesAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
                        valuesAsSet.addAll(Arrays.asList(values)); // add the existing ones
                        valuesAsSet.add(value);
                        //values = new ArrayList<Value>(); // to save memory, leaving commented as the saving probably isn't that much and I'm a little worried about concurrency.
                        // todo - profile how many of these are left over in, for example, Damart.
                    } else { // ok we have to switch a new one in, need to think of the best way with the new array model
                        // this is synchronized, I should be able to be simple about this and be safe still
                        // new code to deal with arrays assigned to the correct size on loading
                        if (values.length != 0 && values[values.length - 1] == null){ // during loading
                            for (int i = 0; i < values.length; i++){ // being synchronised this should be all ok
                                if (values[i] == null){
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

    private static AtomicInteger removeFromValuesCounter = new AtomicInteger(0);

    protected void removeFromValues(final Value value) throws Exception {
        removeFromValuesCounter.incrementAndGet();
        if (valuesAsSet != null) {
            if (valuesAsSet.remove(value)){
                valuesIncludingChildrenCache = null;
                valuesIncludingChildrenPayAttentionToAdditiveCache = null;
            }
        } else {
            synchronized (this) { // just sync on this object to protect the lists
                List<Value> valuesList = Arrays.asList(values);
                if (valuesList.contains(value)) {
                    // ok and a manual copy, again since synchronized I can't see a massive problem here.
                    Value[] newValuesArray = new Value[values.length - 1];
                    int newArrayPosition = 0;// gotta have a separate index on the new array, they will go out of sync
                    for (Value value1 : values) { // do one copy skipping the element we want removed
                        if (!value1.equals(value)) { // if it's not the one we want to return then copy
                            newValuesArray[newArrayPosition] = value1;
                            newArrayPosition++;
                        }
                    }
                    values = newValuesArray;
                    valuesIncludingChildrenCache = null;
                    valuesIncludingChildrenPayAttentionToAdditiveCache = null;
                }
            }
        }
    }

    private static AtomicInteger getParentsCounter = new AtomicInteger(0);

    public Collection<Name> getParents() {
        getParentsCounter.incrementAndGet();
        return parentsAsSet != null ? Collections.unmodifiableCollection(parentsAsSet) : Collections.unmodifiableCollection(Arrays.asList(parents));
    }

    // note these two should be called in synchronized blocks if acting on things like parents, children etc
    // doesn't check contains, there is logic after the contains when adding which can't go in here (as in are we going to switch to set?)
    private static AtomicInteger nameArrayAppendCounter = new AtomicInteger(0);

    private Name[] nameArrayAppend(Name[] source, Name toAppend) {
        nameArrayAppendCounter.incrementAndGet();
        Name[] newArray = new Name[source.length + 1];
        System.arraycopy(source, 0, newArray, 0, source.length); // intellij simplified it to this, should be fine
        newArray[source.length] = toAppend;
        return newArray;
    }

    // I realise some of this stuff is probably very like the internal workings of ArrayList! Important here to save space with vanilla arrays I'm rolling my own.

    private static AtomicInteger nameArrayAppendCounter2 = new AtomicInteger(0);
    private Name[] nameArrayAppend(Name[] source, Name toAppend, int position) {
        nameArrayAppendCounter2.incrementAndGet();
        if (position >= source.length) {
            return nameArrayAppend(source, toAppend);
        }
        Name[] newArray = new Name[source.length + 1];
        for (int i = 0; i < source.length; i++) { // do one copy skipping the element we want removed
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

    private static AtomicInteger nameArrayRemoveIfExists = new AtomicInteger(0);
    private Name[] nameArrayRemoveIfExists(Name[] source, Name toRemove) {
        nameArrayRemoveIfExists.incrementAndGet();
        List<Name> sourceList = Arrays.asList(source);
        if (sourceList.contains(toRemove)) {
            return nameArrayRemove(source, toRemove);
        } else {
            return source;
        }
    }

    // note, assumes it is in there! Otherwise will be an exception

    private static AtomicInteger nameArrayRemoveCounter = new AtomicInteger(0);
    private Name[] nameArrayRemove(Name[] source, Name toRemove) {
        nameArrayRemoveCounter.incrementAndGet();
        Name[] newArray = new Name[source.length - 1];
        int newArrayPosition = 0;// gotta have a separate index on the new array, they will go out of sync
        for (Name name : source) { // do one copy skipping the element we want removed
            if (!name.equals(toRemove)) { // if it's not the one we want to return then copy
                newArray[newArrayPosition] = name;
                newArrayPosition++;
            }
        }
        return newArray;
    }

/*    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Name) {
            return getId() == ((Name)obj).getId();
        }
        return false;
    }*/

    private void addToParents(final Name name) throws Exception {
        addToParents(name, false);
    }
    // don't allow external classes to set the parents I mean by function or otherwise, Name can manage this based on set children
    // synchronize on parents? It's not final is the thing
    // this is absolutely hammered on loading, saving bits of logic or bytes of garbage generated is worth it!

    private static AtomicInteger addToParentsCounter = new AtomicInteger(0);
    private void addToParents(final Name name, boolean noDuplicationCheck) throws Exception {
        addToParentsCounter.incrementAndGet();
        if (parentsAsSet != null) {
            parentsAsSet.add(name);
        } else {
            synchronized (this) {
                // what if it simultaneously hit the array threshold at the same time? Double check it!
                if (parentsAsSet != null) {
                    parentsAsSet.add(name);
                    return;
                }
                // it's this contains expense that means we should stop using arraylists over a certain size.
                // If loading skip the duplication check, we assume data integrity, put the aslist in the conditional so we won't instantiate the object unnecessarily, this function is hammered on loading
                if (noDuplicationCheck || !Arrays.asList(parents).contains(name)) {
                    if (parents.length >= ARRAYTHRESHOLD) {
                        parentsAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
                        parentsAsSet.addAll(Arrays.asList(parents));
                        parentsAsSet.add(name);
                        // parents  new arraylist?
                    } else {
                        if (parents.length != 0 && parents[parents.length - 1] == null){ // then we're loading for the first time
                            for (int i = 0; i < parents.length; i++){ // being synchronised this should be all ok
                                if (parents[i] == null){
                                    parents[i] = name;
                                    break;
                                }
                            }
                        } else { // normal modification
                            parents = nameArrayAppend(parents, name);
                        }
                    }
                }
            }
        }
    }

    private static AtomicInteger removeFromParentsCounter = new AtomicInteger(0);
    private void removeFromParents(final Name name) throws Exception {
        removeFromParentsCounter.incrementAndGet();
        if (parentsAsSet != null) {
            parentsAsSet.remove(name);
        } else {
            synchronized (this) { // just sync on this object to protect the lists
                parents = nameArrayRemoveIfExists(parents, name);
            }
        }
    }

    // returns a collection, I think this is just iterated over to check stuff
    // todo - check use of this and then whether we use sets internally or not
    // these two functions moved here from the spreadsheet

    private static AtomicInteger findAllParentsCounter = new AtomicInteger(0);
    public Collection<Name> findAllParents() {
        findAllParentsCounter.incrementAndGet();
        final Set<Name> allParents = new HashSet<>();
        findAllParents(this, allParents);
        return allParents;
    }

    // option for collect call but I don't really get it
    private static AtomicInteger findAllParentsCounter2 = new AtomicInteger(0);
    private void findAllParents(Name name, final Set<Name> allParents) {
        findAllParentsCounter2.incrementAndGet();
        for (Name parent : name.getParents()) {
            if (!allParents.contains(parent)) {
                allParents.add(parent);
                findAllParents(parent, allParents);
            }
        }
    }

    // we are now allowing a name to be in more than one top parent, hence the name change

    private static AtomicInteger findATopParentCounter = new AtomicInteger(0);
    public Name findATopParent() {
        findATopParentCounter.incrementAndGet();
        if (getParents().size() > 0) {
            Name parent = getParents().iterator().next();
            while (parent != null) {
                if (parent.getParents().size() > 0 && parent.getAttribute(LOCAL) == null) {
                    parent = parent.getParents().iterator().next();
                } else {
                    return parent; // it has no parents, must be top
                }
            }
        }
        //WFC amendment - used to be 'null' below
        return this;
    }

    // same logic as find all parents but returns a set, should be correct
    // also we have the option to use additive or not

    // leaving as sets for the mo. Arrays would be cheaper on the memory I suppose, conversion can be expensive though
    private Set<Name> findAllChildrenCache = null;// is this ever used?? COuld save a few bytes
    private Set<Name> findAllChildrenPayAttentionToAdditiveCache = null;

    private static AtomicInteger finaAllChildrenCounter = new AtomicInteger(0);
    private void findAllChildren(Name name, boolean payAttentionToAdditive, final Set<Name> allChildren) {
        finaAllChildrenCounter.incrementAndGet();
        if (payAttentionToAdditive && !name.additive) return;
        for (Name child : name.getChildren()) {
            if (allChildren.add(child)) {
                findAllChildren(child, payAttentionToAdditive, allChildren);
            }
        }
    }
    private static AtomicInteger finaAllChildrenCounter1 = new AtomicInteger(0);
    public Collection<Name> findAllChildren(boolean payAttentionToAdditive) {
        finaAllChildrenCounter1.incrementAndGet();
        if (payAttentionToAdditive) {
            // I think this a worthwhile variable in case the cache is zapped in the mean time, it's about thread safety
            Set<Name> localReference = findAllChildrenPayAttentionToAdditiveCache;
            if (localReference == null) {
                // ok I don't want to be building two at a time, hence I want to synchronize this bit,
                synchronized (this) { // ideally I wouldn't synchronize on this, it would be on findAllChildrenPayAttentionToAdditiveCache but it's not final and I don't want it to be for the moment
                    localReference = findAllChildrenPayAttentionToAdditiveCache; // check again after synchronized, it may have been sorted in the mean time
                    if (localReference == null) {
                        localReference = HashObjSets.newUpdatableSet();
                        findAllChildren(this, true, localReference);
                        if (localReference.isEmpty()) {
                            findAllChildrenPayAttentionToAdditiveCache = Collections.emptySet(); // stop memory overhead, ooh I feel all clever!
                        } else {
                            findAllChildrenPayAttentionToAdditiveCache = localReference;
                        }
                    }/* else {
                        System.out.println("skipped a simultaneous findAllChildren cache populate, good stuff");
                    }*/
                }
            }
            return Collections.unmodifiableCollection(localReference);
        } else {
            Set<Name> localReference = findAllChildrenCache;
            if (localReference == null) {
                // ok I don't want to be building two at a time, hence I want to synchronize this bit
                synchronized (this) { // ideally I wouldn't synchronize on this, it would be on findAllChildrenCache but it's not final and I don't want it to be for the moment
                    localReference = findAllChildrenCache; // check again after synchronized, it may have been sorted in the mean time
                    if (localReference == null) {
                        /*
                        TODO - this seems, in some circumstances, to go slow. It's when it has over 20 million entries but this of itself is not the problem, I've tested higher than that with the names
                        Related to contains hits? Or initial size? NEvertheless barring this occasional problem the meory overhead of the caches being this saves gigibytes, I'm leaving it in for the moment
                        with a view to fixing it.
                         */
                        //long track = System.currentTimeMillis();
                        localReference = HashObjSets.newMutableSet();
                        findAllChildren(this, false, localReference);
                        //System.out.println("Time to make find all children false cache : " + (System.currentTimeMillis() - track) + " result size " + localReference.size());
                        if (localReference.isEmpty()) {
                            findAllChildrenCache = Collections.emptySet(); // stop memory overhead, ooh I feel all clever!
                        } else {
                            findAllChildrenCache = localReference;
                        }
                    }/* else {
                        System.out.println("skipped a simultaneous findAllChildren cache populate, good stuff");
                    }*/
                }
            }
            return Collections.unmodifiableCollection(localReference);
        }
    }

    private Set<Value> valuesIncludingChildrenCache = null; // I had a value set size cache but then I thought why not store the values? Could add some overhead to build but after might be very useful
    // plus I can check some alternative set intersection stuff
    private Set<Value> valuesIncludingChildrenPayAttentionToAdditiveCache = null;
    // Tie its invalidation to the find all children invalidation

    private static AtomicInteger findValuesIncludingChildrenCounter = new AtomicInteger(0);
    public Set<Value> findValuesIncludingChildren(boolean payAttentionToAdditive) {
        findValuesIncludingChildrenCounter.incrementAndGet();
        if (payAttentionToAdditive) {
            Set<Value> localReference = valuesIncludingChildrenPayAttentionToAdditiveCache; // in case the cache is nulled before we try to return it
            if (localReference == null) {
                // ok I don't want to be building two at a time, hence I want to synchronize this bit,
                synchronized (this) {
                    localReference = valuesIncludingChildrenPayAttentionToAdditiveCache; // check again after synchronized, it may have been sorted in the mean time
                    if (localReference == null) {
//                        long track = System.currentTimeMillis();
                        localReference = HashObjSets.newUpdatableSet(getValues());
                        for (Name child : findAllChildren(true)) {
                            localReference.addAll(child.getValues());
                        }
                        if (localReference.isEmpty()) {
                            valuesIncludingChildrenPayAttentionToAdditiveCache = Collections.emptySet(); // stop memory overhead, ooh I feel all clever!
                        } else {
                            valuesIncludingChildrenPayAttentionToAdditiveCache = localReference;
                        }
                    }
                }
            }
            return Collections.unmodifiableSet(localReference);
        } else {
            Set<Value> localReference = valuesIncludingChildrenCache; // in case the cache is nulled before we try to return it
            if (localReference == null) {
                // ok I don't want to be building two at a time, hence I want to synchronize this bit,
                synchronized (this) {
                    localReference = valuesIncludingChildrenCache; // check again after synchronized, it may have been sorted in the mean time
                    if (localReference == null) {
                        localReference = HashObjSets.newUpdatableSet(getValues());
                        for (Name child : findAllChildren(false)) {
                            localReference.addAll(child.getValues());
                        }
                        if (localReference.isEmpty()) {
                            valuesIncludingChildrenCache = Collections.emptySet(); // stop memory overhead, ooh I feel all clever!
                        } else {
                            valuesIncludingChildrenCache = localReference;
                        }
                    }
                }
            }
            return Collections.unmodifiableSet(localReference);
        }
    }

    // synchronized? Not sure if it matters, dn't need immediate visibility and the cache read should (!) be thread safe.
    // The read uses synchronized to stop creating the cache more than necessary rather than to be totally up to date
    private static AtomicInteger clearChildrenCachesCounter = new AtomicInteger(0);
    public void clearChildrenCaches() {
        clearChildrenCachesCounter.incrementAndGet();
        findAllChildrenCache = null;
        findAllChildrenPayAttentionToAdditiveCache = null;
        valuesIncludingChildrenCache = null;
        valuesIncludingChildrenPayAttentionToAdditiveCache = null;
    }

    private static AtomicInteger getChildrenCounter = new AtomicInteger(0);
    public Collection<Name> getChildren() {
        getChildrenCounter.incrementAndGet();
        return childrenAsSet != null ? Collections.unmodifiableCollection(childrenAsSet) : Collections.unmodifiableCollection(Arrays.asList(children));
    }

    // might seem inefficient but the adds and removes deal with parents and things. Might reconsider code if used more heavily
    // each add/remove is safe but for predictability best to synchronize the lot here I think
    private static AtomicInteger setChildrenCounter = new AtomicInteger(0);
    public synchronized void setChildrenWillBePersisted(Collection<Name> children) throws Exception {
        setChildrenCounter.incrementAndGet();
        Collection<Name> existingChildren = getChildren();
        // like an equals but the standard equals might trip up on different collection types
        // at the moment the passed should always be a hashset so run contains all against that I think
        // notably ignores ordering!
        if (children.size() != existingChildren.size() || !children.containsAll(existingChildren)) { // this could provide a major speed increase where this function is "recklessly" called (e.g. in the "as" bit in parseQuery in NameService)
            for (Name oldChild : this.getChildren()) {
                removeFromChildrenWillBePersisted(oldChild, false); // no cache clear, will do it in a mo!
            }
            for (Name child : children) {
                addChildWillBePersisted(child, 0, false); // no cache clear, will do it in a mo!
            }
            clearChildrenCaches();
            getAzquoMemoryDB().clearSetAndCountCacheForName(this);
        }
    }

    private static AtomicInteger addChildWillBePersistedCounter = new AtomicInteger(0);
    public void addChildWillBePersisted(Name child) throws Exception {
        addChildWillBePersistedCounter.incrementAndGet();
        addChildWillBePersisted(child, 0, true);
    }

    private static AtomicInteger addChildWillBePersistedCounter1 = new AtomicInteger(0);
    public void addChildWillBePersisted(Name child, int position) throws Exception {
        addChildWillBePersistedCounter1.incrementAndGet();
        addChildWillBePersisted(child, position, true);
    }

    // with position, will just add if none passed note : this sees position as starting at 1!
    // note : modified to do nothing if the name is in the set. No changing of position.

    private static AtomicInteger addChildWillBePersistedCounter2 = new AtomicInteger(0);
    private void addChildWillBePersisted(Name child, int position, boolean clearCache) throws Exception {
        addChildWillBePersistedCounter2.incrementAndGet();
        checkDatabaseMatches(child);
        if (child.equals(this)) return;//don't put child into itself
        /*removing this check - it takes far too long - maybe should make it optional
        if (findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        */
        // NOTE!! for the set version we're now just using a set backed by a concurrent hash map NOT liked hashset, for large child sets ordering will be ignored!
        // while childrenasaset is thread safe I think I'm going to need to synchronize the lot to make make state more consistent
        boolean changed = false; // only do the after stuff if something changed
        synchronized (this) {
            if (childrenAsSet != null) {
                changed = childrenAsSet.add(child);
            } else {
                List<Name> childrenList = Arrays.asList(children);
                if (!childrenList.contains(child)) {
                    changed = true;
                    // like parents, hope the logic is sound
                    if (childrenList.size() >= ARRAYTHRESHOLD) { // we need to convert. copy the array to a new hashset then set it
                        childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
                        childrenAsSet.addAll(childrenList);
                        childrenAsSet.add(child);
                        // children new arraylist?;
                    } else {
                        if (position != 0) {
                            children = nameArrayAppend(children, child, position);
                        } else {
                            // unlike with parents and values we don't want to look for an empty initialised array here, children can be dealt with more cleanly in the linking
                            children = nameArrayAppend(children, child);
                        }
                    }
                }
            }
            if (changed) { // new logic, only do these things if something was changed
                child.addToParents(this);//synchronized internally with this also so will not deadlock
                setNeedsPersisting();
                //and check that there are not indirect connections which should be deleted (e.g. if London exists in UK and Europe, and we are now
                //specifying that UK is in Europe, we must remove the direct link from London to Europe
                /*
                for (Name descendant : child.findAllChildren(false)) {
                    if (getChildren().contains(descendant)) {
                        removeFromChildrenWillBePersisted(descendant);
                    }
                }*/
            }
        }
        if (changed && clearCache) {
            clearChildrenCaches();
            getAzquoMemoryDB().clearSetAndCountCacheForName(this);
        }
    }

    private static AtomicInteger removeFromChildrenWillBePersistedCounter = new AtomicInteger(0);
    public void removeFromChildrenWillBePersisted(Name name) throws Exception {
        removeFromChildrenWillBePersistedCounter.incrementAndGet();
        removeFromChildrenWillBePersisted(name, true);
    }

    private static AtomicInteger removeFromChildrenWillBePersistedCounter1 = new AtomicInteger(0);
    private void removeFromChildrenWillBePersisted(Name name, boolean clearCache) throws Exception {
        removeFromChildrenWillBePersistedCounter1.incrementAndGet();
        checkDatabaseMatches(name);// even if not needed throw the exception!
        // maybe could narrow this a little?
        synchronized (this) {
            if (getChildren().contains(name)) { // then something to remove
                name.removeFromParents(this);
                /*THIS CONDITION NO LONGER APPLIES

                //don't allow names that have previously had parents to fall out of topparent set
                if (name.getParents().size() == 0) {
                    this.findATopParent().addChildWillBePersisted(name);//revert to top parent (Note that, if 'this' is top parent, then it will be re-instated!
                }
                */

                if (childrenAsSet != null) {
                    childrenAsSet.remove(name);
                } else {
                    children = nameArrayRemove(children, name); // note this will fail if it turns out children does not contain the name. SHould be ok.
                }
                setNeedsPersisting();
            }
        }
        if (clearCache) {
            getAzquoMemoryDB().clearSetAndCountCacheForName(this);
            clearChildrenCaches();
        }
    }

    private static AtomicInteger getAttributesCounter = new AtomicInteger(0);
    public Map<String, String> getAttributes() {
        getAttributesCounter.incrementAndGet();
        return Collections.unmodifiableMap(nameAttributes.getAsMap());
    }

    // to be used in loading - could cause corruption so protected
    protected List<String> getAttributeKeys() {
        return nameAttributes.getAttributeKeys();
    }

    protected List<String> getAttributeValues() {
        return nameAttributes.getAttributeValues();
    }

    // todo - be sure this makes sense given new name attributes
    // I think plain old synchronized here is safe enough if not that fast

    private static AtomicInteger setAttributeWillBePersistedCounter = new AtomicInteger(0);
    public synchronized void setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {
        setAttributeWillBePersistedCounter.incrementAndGet();
        // make safe for new way of persisting attributes
        if (attributeName.contains("¬")) {
            attributeName = attributeName.replace("¬", "");
        }
        if (attributeValue.contains("¬")) {
            attributeValue = attributeValue.replace("¬", "");
        }
        attributeName = attributeName.trim().toUpperCase(); // I think this is the only point at which attributes are created thus if it's uppercased here we should not need to check anywhere else
        // important, manage persistence, allowed name rules, db look ups
        // only care about ones in this set
        // code adapted from map based code to lists, may need rewriting
        // again assume nameAttributes reference only set in code synchronized on this block
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

    private static AtomicInteger removeAttributeWillBePersistedCounter = new AtomicInteger(0);
    public synchronized void removeAttributeWillBePersisted(String attributeName) throws Exception {
        removeAttributeWillBePersistedCounter.incrementAndGet();
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

    // convenience
    private static AtomicInteger clearAttributesCounter = new AtomicInteger(0);
    public synchronized void clearAttributes() throws Exception {
        clearAttributesCounter.incrementAndGet();
        for (String attribute : nameAttributes.attributeKeys) { // nameAttributes will be reassigned by the function but that should be ok, hang onto the key set as it was at the beginning
            removeAttributeWillBePersisted(attribute);
        }
    }

    private static AtomicInteger findParentAttributesCounter = new AtomicInteger(0);
    private String findParentAttributes(Name child, String attributeName, Set<Name> checked) {
        findParentAttributesCounter.incrementAndGet();
        attributeName = attributeName.trim().toUpperCase();
        for (Name parent : child.getParents()) {
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
        return null;
    }

    // default to parent check

    private static AtomicInteger getAttributeCounter = new AtomicInteger(0);
    public String getAttribute(String attributeName) {
        getAttributeCounter.incrementAndGet();
        return getAttribute(attributeName, true, new HashSet<>());
    }


    private static AtomicInteger getAttributeCounter1 = new AtomicInteger(0);
    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked) {
        getAttributeCounter1.incrementAndGet();
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

    // for Jackson mapping, trying to attach to actual fields would be dangerous in terms of allowing unsafe access
    private static class JsonTransport {
        public int provenanceId;
        public boolean additive;
        public Map<String, String> attributes;
        // I'm changing this to a list - the transport should preserve order in case that's required but it shouldn't be responsible for
        // detecting duplicates (shouldn't be a set). If it actually did eliminate duplicates we've got bigger problems.
        public List<Integer> childrenIds;

        @JsonCreator
        public JsonTransport(@JsonProperty("provenanceId") int provenanceId
                , @JsonProperty("additive") boolean additive
                , @JsonProperty("attributes") Map<String, String> attributes
                , @JsonProperty("childrenIds") List<Integer> childrenIds) {
            this.provenanceId = provenanceId;
            this.additive = additive;
            this.attributes = attributes;
            this.childrenIds = childrenIds;
        }
    }

    @Override
    protected String getPersistTable() {
        return StandardDAO.PersistedTable.name.name();
    }

    private static AtomicInteger getAsJsonCounter = new AtomicInteger(0);
    @Override
    public String getAsJson() {
        getAsJsonCounter.incrementAndGet();
        // Going against my previous comment we're vonverting to lists - transport should be "dumb" I think. Plus overhead.
        Collection<Name> children = getChildren();
        List<Integer> childrenIds = new ArrayList<>(children.size());
        for (Name child : getChildren()) {
            childrenIds.add(child.getId());
        }
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(), additive, getAttributes(), childrenIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // in here is a bit more efficient I think but should it be in the DAO?

    private static AtomicInteger getAttributesForFastStoreCounter = new AtomicInteger(0);
    public String getAttributesForFastStore() {
        getAttributesForFastStoreCounter.incrementAndGet();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < nameAttributes.attributeKeys.length; i++) {
            if (i != 0) {
                stringBuilder.append(ATTRIBUTEDIVIDER);
            }
            stringBuilder.append(nameAttributes.attributeKeys[i]);
            stringBuilder.append(ATTRIBUTEDIVIDER);
            stringBuilder.append(nameAttributes.attributeValues[i]);
        }
        return stringBuilder.toString();
    }

    // maybe move out of here?

    private static AtomicInteger entitiesIdsAsBytesCounter = new AtomicInteger(0);
    private byte[] entitiesIdsAsBytes(Collection<? extends AzquoMemoryDBEntity> entities) {
        entitiesIdsAsBytesCounter.incrementAndGet();
        ByteBuffer buffer = ByteBuffer.allocate(entities.size() * 4);
        for (AzquoMemoryDBEntity entity : entities) {
            buffer.putInt(entity.getId());
        }
        return buffer.array();
    }

    public byte[] getChildrenIdsAsBytes() {
        return entitiesIdsAsBytes(getChildren());
    }

    // protected to only be used by the database loading, can't be called in the constructor as name by id maps may not be populated
    // changing synchronized to only relevant portions
    // note : this function is absolutely hammered while linking so optimiseations here are helpful

    private static AtomicInteger linkCounter = new AtomicInteger(0);
    protected void link() throws Exception {
        linkCounter.incrementAndGet();
        if (getAzquoMemoryDB().getNeedsLoading() && loadCache.jsonCache != null) { // during loading and using old json
            try {
                /* Must make sure I set initial collection sizes when loading, resizing is not cheap generally.
                I had children outside here a reference to use to set the parents but I now see no reason not to use getChildren outside . . . the DB should not be being modified while loading surely?
                could have a reference to the array and set right after loading if paranoid
                need to think clearly about what needs synchronization. Given how it is used need it be synchronized at all? Does it matter that much to performance?
                I think the principle is that anything that modifies the state of the object is synchronized. Not a bad one.
                */
                synchronized (this) {
                    JsonTransport transport = jacksonMapper.readValue(loadCache.jsonCache, JsonTransport.class);
                    loadCache = null;// free the memory
                    this.provenance = getAzquoMemoryDB().getProvenanceById(transport.provenanceId);
                    this.additive = transport.additive;
                    //this.attributes = transport.attributes;
                    List<String> attributeKeys = new ArrayList<>();
                    List<String> attributeValues = new ArrayList<>();
                    for (String key : transport.attributes.keySet()) {
                        // interning is clearly saving memory and it seems with little performance overhead
                        attributeKeys.add(key.toUpperCase().intern());
                        attributeValues.add(transport.attributes.get(key).intern());
                    }
                    nameAttributes = new NameAttributes(attributeKeys, attributeValues);

                    // I had a function here I passed a collection of names to to set the children, I'm putting that inline now
                    // no need for the intermediate collection - notable if dealing with millions

                    if (transport.childrenIds.size() > ARRAYTHRESHOLD) {
                        // set the size! Could be quite significant for loading times.
                        this.childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(transport.childrenIds.size())); // NOTE! now we're not using linked, position and ordering will be ignored for large sets of children!!
                        // hesitant to use collect call, seems a way to make another collection. I want to avoid this.
                        for (Integer childId : transport.childrenIds) {
                            this.childrenAsSet.add(getAzquoMemoryDB().getNameById(childId));
                        }
                    } else {
                        Name[] newChildren = new Name[transport.childrenIds.size()];
                        int index = 0;
                        for (Integer childId : transport.childrenIds) {
                            newChildren[index] = getAzquoMemoryDB().getNameById(childId);
                            index++;
                        }
                        this.children = newChildren;
                    }
                }
                // need to sort out the parents - I deliberately excluded this from synchronisation or one could theoretically hit a deadlock
                // addToParents can be synchronized on the child.
                for (Name newChild : getChildren()) { // used to use a list set in the synchronized block. Not quite sure why now . . .
                    newChild.addToParents(this, true);
                }
            } catch (IOException e) {
                System.out.println("jsoncache = " + loadCache.jsonCache);
                e.printStackTrace();
            }
        }
        if (getAzquoMemoryDB().getNeedsLoading() && loadCache != null) { // the new fast load, I have 3 caches to deal with
                /* Synchronize to set up the 3 lists, hopefully not too difficult*/
            synchronized (this) {
                // it's things like this that I believe will be way faster and lighter ongarbage than the old json method
                //int noValues = loadCache.valuesCache.length / 4;
                ////int noParents = loadCache.parentsCache.length / 4;
                int noChildren = loadCache.childrenCache.length / 4;
                ByteBuffer byteBuffer = ByteBuffer.wrap(loadCache.childrenCache);
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
                loadCache = null;// free the memory
            }                // should parents be stored as ids? Or maybe the size would help, could init the map or array effectively
            // need to sort out the parents - I deliberately excluded this from synchronisation or one could theoretically hit a deadlock
            // addToParents can be synchronized on the child.
            for (Name newChild : getChildren()) { // used to use a list set in the synchronized block. Not quite sure why now . . .
                newChild.addToParents(this);
            }

        }
    }

    // first of its kind. Try to be comprehensive
    // want to make it synchronized but I'm calling synchronized functions on other objects. Hmmmmmmmm.
    private static AtomicInteger deleteCounter = new AtomicInteger(0);
    public void delete() throws Exception {
        deleteCounter.incrementAndGet();
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

    public static void printFunctionCountStats(){
        System.out.println("######### NAME FUNCTION COUNTS");
        System.out.println("newName\t\t\t\t" + newName.get());
        System.out.println("newName2\t\t\t\t" + newName2.get());
        System.out.println("newName3\t\t\t\t" + newName3.get());
        System.out.println("getDefaultDisplayNameCounter\t\t\t\t" + getDefaultDisplayNameCounter.get());
        System.out.println("getValuesCounter\t\t\t\t" + getValuesCounter.get());
        System.out.println("transferValuesCounter\t\t\t\t" + transferValuesCounter.get());
        System.out.println("addToValuesCounter\t\t\t\t" + addToValuesCounter.get());
        System.out.println("removeFromValuesCounter\t\t\t\t" + removeFromValuesCounter.get());
        System.out.println("getParentsCounter\t\t\t\t" + getParentsCounter.get());
        System.out.println("nameArrayAppendCounter\t\t\t\t" + nameArrayAppendCounter.get());
        System.out.println("nameArrayAppendCounter2\t\t\t\t" + nameArrayAppendCounter2.get());
        System.out.println("nameArrayRemoveIfExists\t\t\t\t" + nameArrayRemoveIfExists.get());
        System.out.println("nameArrayRemoveCounter\t\t\t\t" + nameArrayRemoveCounter.get());
        System.out.println("addToParentsCounter\t\t\t\t" + addToParentsCounter.get());
        System.out.println("removeFromParentsCounter\t\t\t\t" + removeFromParentsCounter.get());
        System.out.println("findAllParentsCounter\t\t\t\t" + findAllParentsCounter.get());
        System.out.println("findAllParentsCounter2\t\t\t\t" + findAllParentsCounter2.get());
        System.out.println("findATopParentCounter\t\t\t\t" + findATopParentCounter.get());
        System.out.println("finaAllChildrenCounter\t\t\t\t" + finaAllChildrenCounter.get());
        System.out.println("finaAllChildrenCounter1\t\t\t\t" + finaAllChildrenCounter1.get());
        System.out.println("findValuesIncludingChildrenCounter\t\t\t\t" + findValuesIncludingChildrenCounter.get());
        System.out.println("clearChildrenCachesCounter\t\t\t\t" + clearChildrenCachesCounter.get());
        System.out.println("getChildrenCounter\t\t\t\t" + getChildrenCounter.get());
        System.out.println("setChildrenCounter\t\t\t\t" + setChildrenCounter.get());
        System.out.println("addChildWillBePersistedCounter\t\t\t\t" + addChildWillBePersistedCounter.get());
        System.out.println("addChildWillBePersistedCounter1\t\t\t\t" + addChildWillBePersistedCounter1.get());
        System.out.println("addChildWillBePersistedCounter2\t\t\t\t" + addChildWillBePersistedCounter2.get());
        System.out.println("removeFromChildrenWillBePersistedCounter\t\t\t\t" + removeFromChildrenWillBePersistedCounter.get());
        System.out.println("removeFromChildrenWillBePersistedCounter1\t\t\t\t" + removeFromChildrenWillBePersistedCounter1.get());
        System.out.println("getAttributesCounter\t\t\t\t" + getAttributesCounter.get());
        System.out.println("setAttributeWillBePersistedCounter\t\t\t\t" + setAttributeWillBePersistedCounter.get());
        System.out.println("removeAttributeWillBePersistedCounter\t\t\t\t" + removeAttributeWillBePersistedCounter.get());
        System.out.println("clearAttributesCounter\t\t\t\t" + clearAttributesCounter.get());
        System.out.println("findParentAttributesCounter\t\t\t\t" + findParentAttributesCounter.get());
        System.out.println("getAttributeCounter\t\t\t\t" + getAttributeCounter.get());
        System.out.println("getAttributeCounter1\t\t\t\t" + getAttributeCounter1.get());
        System.out.println("getAsJsonCounter\t\t\t\t" + getAsJsonCounter.get());
        System.out.println("getAttributesForFastStoreCounter\t\t\t\t" + getAttributesForFastStoreCounter.get());
        System.out.println("entitiesIdsAsBytesCounter\t\t\t\t" + entitiesIdsAsBytesCounter.get());
        System.out.println("linkCounter\t\t\t\t" + linkCounter.get());
        System.out.println("deleteCounter\t\t\t\t" + deleteCounter.get());
    }
    public static void clearFunctionCountStats() {
        newName.set(0);
        newName2.set(0);
        newName3.set(0);
        getDefaultDisplayNameCounter.set(0);
        getValuesCounter.set(0);
        transferValuesCounter.set(0);
        addToValuesCounter.set(0);
        removeFromValuesCounter.set(0);
        getParentsCounter.set(0);
        nameArrayAppendCounter.set(0);
        nameArrayAppendCounter2.set(0);
        nameArrayRemoveIfExists.set(0);
        nameArrayRemoveCounter.set(0);
        addToParentsCounter.set(0);
        removeFromParentsCounter.set(0);
        findAllParentsCounter.set(0);
        findAllParentsCounter2.set(0);
        findATopParentCounter.set(0);
        finaAllChildrenCounter.set(0);
        finaAllChildrenCounter1.set(0);
        findValuesIncludingChildrenCounter.set(0);
        clearChildrenCachesCounter.set(0);
        getChildrenCounter.set(0);
        setChildrenCounter.set(0);
        addChildWillBePersistedCounter.set(0);
        addChildWillBePersistedCounter1.set(0);
        addChildWillBePersistedCounter2.set(0);
        removeFromChildrenWillBePersistedCounter.set(0);
        removeFromChildrenWillBePersistedCounter1.set(0);
        getAttributesCounter.set(0);
        setAttributeWillBePersistedCounter.set(0);
        removeAttributeWillBePersistedCounter.set(0);
        clearAttributesCounter.set(0);
        findParentAttributesCounter.set(0);
        getAttributeCounter.set(0);
        getAttributeCounter1.set(0);
        getAsJsonCounter.set(0);
        getAttributesForFastStoreCounter.set(0);
        entitiesIdsAsBytesCounter.set(0);
        linkCounter.set(0);
        deleteCounter.set(0);
    }
}