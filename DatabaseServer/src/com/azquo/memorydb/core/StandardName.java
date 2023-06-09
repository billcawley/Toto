package com.azquo.memorydb.core;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.service.NameService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import com.google.common.collect.Lists;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * This class represents a Name, a fundamental Azquo object. Names now have attributes and what was the name (as in the text) is now simply an attribute of the name
 * defined currently in a static below. Names can have parent and child relationships with multiple other names. Sets of names.
 * <p>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on. The class should be thread safe allowing concurrent reads if not concurrent writes.
 * Changes to state need not be immediately visible though in some cases code needs to be structured for suitable atomicity (e.g. attributes).
 * <p>
 * Thread safety is important, essentially modifying data will be synchronized, reads won't be and won't be completely up to date but should return consistent data.
 * <p>
 * This object was weighing in at over 2k average in an example magento db the goal is to bring it to sub 500 bytes.
 * This means in some cases variables being null until used and using arrays instead of sets switching for performance
 * when the list gets too big. I got it to about 850 bytes (magento example DB). Will park for the mo,
 * further change would probably involve changes to attributes, I mean the name of attributes not being held here for example.
 * <p>
 * Attributes case insensitive according to WFC spec.
 * <p>
 * Was comparable but this resulted in a code warning I've moved the comparator to NameService
 * <p>
 * Note : as well as heap overhead there's garbage collection overhead, watch for avoidable throw away objects.
 * <p>
 * Are we acquiring multiple locks in any places? Is it worth testing the cost of synchronizing access to bits of name? Depending on cost it could mean the code is simplified . . .
 * <p>
 * Also I'm using Double Checked Locking. With volatile DCL is correct according to the JMM,
 * Given that synchronization is not inherently expensive it might be worth considering how much places where I'm using DCL might actually be contended much.
 * <p>
 * I've extracted NameAttributes and a few static functions but there's still a fair amount of code in here. Values and children switching between arrays and sets might be a concern.
 */
public final class StandardName extends Name {

    public static final int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets. A bit arbitrary, might be worth testing (speed vs memory usage)

//    private static final Logger logger = Logger.getLogger(Name.class);

    private Provenance provenance; // should be volatile? Don't care about being completely up to date but could a partially constructed object get in here?

    private volatile NameAttributes nameAttributes; // since NameAttributes is immutable there's no need for this to be volatile I don't think. Could test performance, I suppose volatile is slightly preferable? Happens before?

    /* memory db structure bits. There may be better ways to do this but we'll leave it here for the mo
     Values, parent are for quick lookup, must be modified appropriately
     to be clear, these are not used when persisting, they are derived from the name sets in values and the children below
     Sets are expensive in terms of memory, will use arrays instead unless they get big (above threshold 512 at the mo)
     make a new array and switch on changing to make atomic and always wrap them unmodifiable on get
     Based on recommendations here https://en.wikipedia.org/wiki/Double-checked_locking I switched the sets to volatile
     to protect against half instantiated object references, it seems (Damart test) to have had no performance effect.
     Arrays volatile too seems ok (there may be an effect importing).

     Since parents are very rarely above the array threshold I've left them as array only

     Currently all private but this forces some code into this class which ideally would be outside - a trade off that I'm not entirely happy about.
     */
    private volatile Set<Value> valuesAsSet;
    private volatile Value[] values;
    private volatile Name[] parents;
    private volatile Set<Name> childrenAsSet;
    private volatile Name[] children;

    // For the code to make new names (not when loading).

    private static final AtomicInteger newNameCount = new AtomicInteger(0);

    public StandardName(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance) throws Exception {
        super(azquoMemoryDB, 0);
        newNameCount.incrementAndGet();
        valuesAsSet = null;
        values = new Value[0]; // Turning these 3 lists to arrays, memory a priority
        parents = new Name[0];
        childrenAsSet = null;
        children = new Name[0];
        nameAttributes = new NameAttributes(); // attributes will nearly always be written over, this is just a placeholder
        getAzquoMemoryDB().addNameToDb(this);
        newNameCount.incrementAndGet();
        this.provenance = provenance;
    }

    private static final AtomicInteger newName3Count = new AtomicInteger(0);

    // For loading, it should only be used by the NameDAO, I can't really restrict it and make it non public without rearranging the package I don't think.
    // yes I am exposing "this". Seems ok so far. Interning the attributes should help memory usage.

    public StandardName(final AzquoMemoryDB azquoMemoryDB, int id, int provenanceId, String attributes, int noParents, int noValues) throws Exception {
        this(azquoMemoryDB, id, provenanceId,null, attributes, noParents, noValues, false);
    }

    // package private - BackupTransport can force the ID
    StandardName(final AzquoMemoryDB azquoMemoryDB, int id, int provenanceId, NameAttributes nameAttributes, String attributes, int noParents, int noValues, boolean forceIdForbackup) throws Exception {
        super(azquoMemoryDB, id, forceIdForbackup);
        newName3Count.incrementAndGet();
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId); // see no reason not to do this here now
        if (this.provenance == null){
            System.out.println("Provenance null on backup restore!, id is " + provenanceId + ", making blank provenance");
            this.provenance = new Provenance(azquoMemoryDB, "-","-","-","-");
        }
        //this.attributes = transport.attributes;
        // can pass nameAttributes as an optimiseation when making a temporary copy
        if (nameAttributes != null){
            this.nameAttributes = nameAttributes;
            // perhaps breaks the optimiseationa  little but hey ho
            for (String key : nameAttributes.getAttributeKeys()){
                azquoMemoryDB.getIndex().setAttributeForNameInAttributeNameMap(key, nameAttributes.getAttribute(key), this); // used to be done after, can't se a reason not to do this here
            }
        } else {
            String[] attsArray = attributes.split(StringLiterals.ATTRIBUTEDIVIDER);
            String[] attributeKeys = new String[attsArray.length / 2];
            String[] attributeValues = new String[attsArray.length / 2];
            for (int i = 0; i < attributeKeys.length; i++) {
                attributeKeys[i] = attsArray[i * 2].intern();
                attributeValues[i] = attsArray[(i * 2) + 1].intern();
                azquoMemoryDB.getIndex().setAttributeForNameInAttributeNameMap(attributeKeys[i], attributeValues[i], this); // used to be done after, can't se a reason not to do this here
            }
            this.nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        }
        /* OK I realise that this criteria of greater than or equal to ARRAYTHRESHOLD assigning a set and less than an array is slightly inconsistent with addToValues
        that is to say one could get to an array of 512, save and reload and then that 512 would be a set. I do this because the value loading is a bit of a hack
        and if I copy the criteria then addToValues will crash on the edge case of 512. I don't really see a problem with this under typical operation. It might, I suppose,
        be an issue in a database with many names with 512 values but the rest of the time it's a moot point. DOcumenting here in case anyone sees the incinsistency and tries to "fix" it.
        */
        valuesAsSet = noValues >= ARRAYTHRESHOLD ? Collections.newSetFromMap(new ConcurrentHashMap<>(noValues)) : null; // should help loading - prepare the space
        // could values be nulled if the set is being used? The saving would be minor I think.
        values = new Value[noValues < ARRAYTHRESHOLD ? noValues : 0]; // prepare the array, as long as this is taken into account later should be fine
        parents = new Name[noParents]; // no parents as set! as mentioned parents will very rarely big big and certainly not in the same way children and values are.
        childrenAsSet = null;// dealt with properly later
        children = new Name[0];
        getAzquoMemoryDB().addNameToDb(this);
    }

    private static final AtomicInteger getDefaultDisplayNameCount = new AtomicInteger(0);

    // for convenience but be careful where it is used . . .
    public String getDefaultDisplayName() {
        getDefaultDisplayNameCount.incrementAndGet();
        return nameAttributes.getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME);
    }

    // provenance immutable. If it were not then would need to clone

    public Provenance getProvenance() {
        return provenance;
    }

    void setProvenanceWillBePersisted(final Provenance provenance) {
        synchronized (this){
            if (this.provenance == null || !this.provenance.equals(provenance)) {
                this.provenance = provenance;
                setNeedsPersisting();
            }
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

    private static final AtomicInteger getValuesCount = new AtomicInteger(0);

    public Collection<Value> getValues() {
        getValuesCount.incrementAndGet();
        return valuesAsSet != null ? Collections.unmodifiableSet(valuesAsSet) : values.length > 0 ? Collections.unmodifiableList(Arrays.asList(values)) : Collections.emptyList();
    }

    public int getValueCount() {
        return getValues().size();
    }

    public boolean hasValues() {
        return valuesAsSet != null || values.length > 0;
    }

    // replacing public addToValues and RemoveFromValues. Name should handle this internally.
    // It will do this by checking the value that has been passed to it and seeing if it needs to take action
    // Not putting up with public add and remove from values as before
    void checkValue(final Value value, boolean backupRestore) throws Exception {
        if (value.hasName(this)) {
            addToValues(value, backupRestore);
        } else {
            removeFromValues(value);
        }
    }

    // these two functions used when data in persistence seems to not match (number of values or parents seems wrong)

    synchronized void valueArrayCheck() {
        if (valuesAsSet == null) {
            ArrayList<Value> newList = new ArrayList<>();
            for (Value v : values) {
                if (v != null) {
                    newList.add(v);
                }
            }
            values = newList.toArray(new Value[newList.size()]);
        }
    }

    // only called when loading - if called the database might not be usable
    synchronized void parentArrayCheck() {
        ArrayList<Name> newList = new ArrayList<>();
        for (Name n : parents) {
            if (n != null) {
                newList.add(n);
            }
        }
        parents = newList.toArray(new Name[newList.size()]);
    }

    // should only be called on loading, potential to break things. lock this down. todo
    /*synchronized void normaliseParents(Name source) {
        parents = source.parents;
    }*/

    // no duplication is while loading, to reduce work
    private static final AtomicInteger addToValuesCount = new AtomicInteger(0);

    void addToValues(final Value value) throws Exception {
        addToValues(value, false);
    }

    private void addToValues(final Value value, boolean backupRestore) throws Exception {
        addToValuesCount.incrementAndGet();
        boolean databaseIsLoading = getAzquoMemoryDB().getNeedsLoading() || backupRestore;
        checkDatabaseMatches(value);
        // may make this more clever as in clearing only if there's a change but not now
        valuesIncludingChildrenCache = null;
        // without volatile supposedly this (and getValues) could be accessing a not yet instantiated set. I am sceptical.
        if (valuesAsSet != null) {
            valuesAsSet.add(value);// Backed by concurrent hash map, will be thread safe
        } else {
            synchronized (this) { // syncing changes on this is fine, don't want to on values itself as it's about to be changed - synchronizing on a non final field is asking for trouble
                // The ol' double checked locking
                if (valuesAsSet != null) {
                    valuesAsSet.add(value);
                    return;
                }
                // it's this contains expense that means we should stop using ArrayList over a certain size.
                // If loading skip the duplication check, we assume data integrity, asList an attempt to reduce garbage, I think it's object is lighter than an ArrayList. It won't be hit during loading but will during importing,
                if (databaseIsLoading || !Arrays.asList(values).contains(value)) {
                    if (values.length >= ARRAYTHRESHOLD) { // we need to convert. copy the array to a new concurrent hash set then set it
                        /* Note! I've read it would be possible in theory for a half baked reference to the set to escape if the reference wasn't volatile.
                        That is to say a reference escaping before the constructor completed.
                        Not entirely sure about this but I've made it volatile and also will now add a local reference to the set to populate it before switching in.
                        volatile should ensure these changes are reflected when valuesAsSet is accessed.*/
                        Set<Value> localValuesAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
                        localValuesAsSet.addAll(Arrays.asList(values)); // add the existing ones
                        localValuesAsSet.add(value);
                        valuesAsSet = localValuesAsSet; // and now switch it in - since we have used a local variable this should fulfil textbook double checked locking
                        // I could clear the old values array but this might trip up the getValues since it's not synchronized. Won't be an issue when the DB is loaded from persistence
                    } else { // ok we have to switch a new array in
                        // this is synchronized, I should be able to be simple about this and be safe still
                        // new code to deal with arrays assigned to the correct size on loading
                        // don't trust no_values, compensate and log
                        if (values.length != 0 && values[values.length - 1] == null) {
                            if (!databaseIsLoading) {
                                System.out.println("empty space in values after the database has finished loading - no_values wrong on name id " + getId() + " " + getDefaultDisplayName());
                                getAzquoMemoryDB().forceNameNeedsPersisting(this);
                            }
                            // If there's a mismatch and noValues is too small it just won't be added to the list. But if noValues isn't correct things have already gone wrong
                            for (int i = 0; i < values.length; i++) { // being synchronised this should be all ok
                                if (values[i] == null) {
                                    values[i] = value;
                                    break;
                                }
                            }
                        } else { // normal modification
                            if (databaseIsLoading) {
                                System.out.println("while loading ran out of values space - no_values wrong on name id " + getId() + " " + getDefaultDisplayName());
                                getAzquoMemoryDB().forceNameNeedsPersisting(this);
                            }
                            Value[] newValuesArray = new Value[values.length + 1];
                            System.arraycopy(values, 0, newValuesArray, 0, values.length); // intellij simplified it to this, should be fine
                            newValuesArray[values.length] = value;
                            values = newValuesArray;
                        }
                    }
                }
            }
        }
        setNeedsPersisting(); // will be ignored on loading. Best to put in here to be safe. Could maybe check it's actually necessary above if performance an issue.
    }

    private static final AtomicInteger removeFromValuesCount = new AtomicInteger(0);

    void removeFromValues(final Value value) {
        removeFromValuesCount.incrementAndGet();
        if (valuesAsSet != null) {
            if (valuesAsSet.remove(value)) {
                valuesIncludingChildrenCache = null;
            }
        } else {
            synchronized (this) { // just sync on this object to protect the lists
                // double check - I'd forgotten to here! Regardless of whether values as set is volatile or not it could have been set in the mean time by addToValues above
                if (valuesAsSet != null) {
                    if (valuesAsSet.remove(value)) {
                        valuesIncludingChildrenCache = null;
                    }
                    return;
                }
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
                }
            }
        }
        setNeedsPersisting(); // will be ignored on loading. Best to put in here to be safe.
    }

    // returns list now as that's what it is!
    private static final AtomicInteger getParentsCount = new AtomicInteger(0);

    public List<Name> getParents() {
        getParentsCount.incrementAndGet();
        return parents.length > 0 ? Collections.unmodifiableList(Arrays.asList(parents)) : Collections.emptyList();
    }

    public boolean hasParents() {
        return parents.length > 0;
    }

    void addToParents(final Name name) {
        addToParents(name, false);
    }
    /* don't allow external classes to set the parents I mean by function or otherwise, Name can manage this based on set children
    this is absolutely hammered on loading, saving bits of logic or bytes of garbage generated is worth it!
    for notes about the pattern here see addToValues. I'm going to set needs persisting on parent modifications regardless
    so no_parents in the db is kept correct. Could check to see if the parents actually changed before setNeedsPersisting
    but due to where these functions are called internally I think there would be little gain, these two functions only tend to be
     called after it is confirmed that changed to children actually happened*/

    private static final AtomicInteger addToParentsCount = new AtomicInteger(0);

    // don't trust no_parents, revert to old style but add logging
    void addToParents(final Name name, boolean databaseIsLoading) {
        addToParentsCount.incrementAndGet();
        synchronized (this) {
            if (databaseIsLoading || !Arrays.asList(parents).contains(name)) {
                if (parents.length != 0 && parents[parents.length - 1] == null) {
                    if (!databaseIsLoading) {
                        System.out.println("empty space in parents after the database has finished loading - no_parents wrong on name id " + getId() + " " + getDefaultDisplayName());
                        getAzquoMemoryDB().forceNameNeedsPersisting(this);
                    }
                    for (int i = 0; i < parents.length; i++) {
                        if (parents[i] == null) {
                            parents[i] = name;
                            break;
                        }
                    }
                } else {
                    if (databaseIsLoading) {
                        System.out.println("while loading ran out of parents space - no_parents wrong on name id " + getId() + " " + getDefaultDisplayName());
                        getAzquoMemoryDB().forceNameNeedsPersisting(this);
                    }
                    parents = NameUtils.nameArrayAppend(parents, name);
                }
                // won't of course be used if we're loading . . .
                setNeedsPersisting();
            }
        }
    }

    private static final AtomicInteger removeFromParentsCount = new AtomicInteger(0);

    void removeFromParents(final Name name) {
        removeFromParentsCount.incrementAndGet();
         // todo - synchronise on something else? this was part of a JB deadlock, the other hal
        synchronized (this) {
            parents = NameUtils.nameArrayRemoveIfExists(parents, name);
        }
        setNeedsPersisting();
    }

    /* returns a collection, I think this is just iterated over to check stuff but a set internally as it should not have duplicates. These two functions moved
    here from the spreadsheet as I want direct access to the parents array, saving a fair amount of garbage if it's hammered (array vs ArrayList.iterator())*/

    private static final AtomicInteger findAllParentsCount = new AtomicInteger(0);

    public Collection<Name> findAllParents() {
        findAllParentsCount.incrementAndGet();
        final Set<Name> allParents = HashObjSets.newMutableSet(); // should be ok to use these now - maybe use updateable map?
        findAllParents(allParents, 1);
        return allParents;
    }

    private static final AtomicInteger findAllParents2Count = new AtomicInteger(0);

    // note : this was using getParents, since it was really being hammered this was no good due to the garbage generation of that function, hence the change
    // in here as it accesses things I don't want accessed outside


    public void findAllParents(final Set<Name> allParents, int level) {
        findAllParents2Count.incrementAndGet();
        if (level++> 20){
            //to stop the stack from overflowing
            return;
        }

        Name[] parentsRefCopy = parents; // ok in theory the parents could get modified in the for loop, this wouldn't be helpful so I'll keep a copy of the reference to use in case name.parents gets switched out
        for (Name parent : parentsRefCopy) { // should be the same as a for int i; i < parentsRefCopy.length; i++

            if (parent == null) { // having fixed a bug this should be rare now. Typical cause of a null parent would be no_parents in MySQL being incorrect and too big
                System.out.println("DATABASE CORRUPTION " + getDefaultDisplayName() + " id " + getId() + " has a null parent");
            } else {
                if (allParents.add(parent)) { // the function was moved in here to access this array directly. Externally it would need to be wrapped in an unmodifiable List. Means garbage!
                    parent.findAllParents(allParents, level);
                }
            }
        }
    }

    // as above in here directly accessing fields due to performance
    // used when permuting - for element we want to find a parent it has (possibly up the chain) in top set. So if we had a shop and we did member name with that and "All Counties" we'd hope to find the county the shop was in
    private static final AtomicInteger memberNameCount = new AtomicInteger(0);

    public Name memberName(Name topSet) {
        Name[] refCopy = parents;
        if (refCopy.length > 0) {
            for (int i = 0; i < refCopy.length; i++) {
                if (refCopy[i].equals(topSet)) {
                    return this;
                }
                Name ancestor = refCopy[i].memberName(topSet);
                if (ancestor != null) {
                    return ancestor;
                }
            }
        }
        return null;
    }


    private static final AtomicInteger addNamesCount = new AtomicInteger(0);

    // was in name service, added in here as a static function to give it direct access to the private arrays. Again an effort to reduce garbage.
    // Used by find all names, this is hammered, efficiency is important
    public void addChildrenToCollection(Collection<Name> namesFound, final int currentLevel, final int level) {
        addNamesCount.incrementAndGet();
        if (!hasChildren()) {
            if (level == NameService.LOWEST_LEVEL_INT) {
                namesFound.add(this);
            }
        } else {
            if (currentLevel == (level - 1)) { // then we want the next one down, just add it all . . .
                if (childrenAsSet != null) {
                    namesFound.addAll(childrenAsSet);
                } else if (children.length > 0) {
                    //IntelliJ recommends Collections.addAll(namesFound, name.children); instead. I think it's not quite as efficient
                    Name[] refCopy = children; // be defensive unless I can find documentation that says the for loop can't get tripped up
                    for (int i = 0; i < refCopy.length; i++) {
                        namesFound.add(refCopy[i]);
                    }
                }
            } else {
                for (Name child : getChildren()) {// Accessing children as above might save a bit of garbage but I think the big wins have been done
                    child.addChildrenToCollection(namesFound, currentLevel + 1, level);
                }
            }
        }
    }

    // to support negative levels on children clause, level is seen as parent level, same as above but moving in the opposite direction
    // more simple as parents are just an array, no parentsAsSet currently
    // Used by find parents at level, this is hammered, efficiency is important
    public void addParentNamesToCollection(Collection<Name> namesFound, final int currentLevel, final int level) {
        addNamesCount.incrementAndGet();
        if (!hasParents()) {
            if (level == NameService.LOWEST_LEVEL_INT) { // misnomer but same logic
                namesFound.add(this);
            }
        } else {
            Name[] refCopy = parents; // be defensive unless I can find documentation that says the for loop can't get tripped up
            if (currentLevel == (level - 1)) { // then we want the next one up, just add it all . . .
                if (refCopy.length > 0) {
                    //noinspection ManualArrayToCollectionCopy, surpressing as I believe this is a little more efficient in terms of not instantiating an Iterator
                    for (int i = 0; i < refCopy.length; i++) {
                        namesFound.add(refCopy[i]);
                    }
                }
            } else {
                for (Name parent : refCopy) {
                    parent.addParentNamesToCollection(namesFound, currentLevel + 1, level);
                }
            }
        }
    }

    public void addValuesToCollection(Collection<Value> values) {
        if (valuesAsSet != null) {
            values.addAll(valuesAsSet);
        } else if (this.values.length > 0) {
            Value[] refCopy = this.values; // in case values is swapped out while adding
            // Intellij wants to change this I think it's a bit more efficient as it is, might look into this
            for (Value v : refCopy) {
                values.add(v);
            }
        }
    }

    // we are now allowing a name to be in more than one top parent, hence the name change

    private static final AtomicInteger findATopParentCount = new AtomicInteger(0);

    public Name findATopParent() {
        findATopParentCount.incrementAndGet();
        // I suppose concurrent access when knocking off a parent could cause problems . . . I'm not so bothered at the moment
        if (hasParents()) {
            Name parent = parents[0];
            // todo check with WFC - are we using local??? also zap this null check
            while (parent != null) {
                if (parent.hasParents() && parent.getAttribute(StringLiterals.LOCAL) == null) {
                    parent = parent.getParents().iterator().next();
                } else {
                    return parent; // it has no parents, must be top
                }
            }
        }
        return this;
    }

    // same logic as find all parents but returns a set, should be correct
    // Koloboke makes the sets as light as can be expected, volatile to comply with double-checked locking pattern https://en.wikipedia.org/wiki/Double-checked_locking
    private volatile Set<Name> findAllChildrenCache = null;

    private static final AtomicInteger finaAllChildrenCount = new AtomicInteger(0);

    void findAllChildren(final Set<Name> allChildren, int level) {
        if (level > 100){
            System.out.println("Find all children stopping at level 100 " + this.getDefaultDisplayName());
            return;
        }
        finaAllChildrenCount.incrementAndGet();
        // similar to optimisation for get all parents
        // and we'll know if we look at the log. If this happened a local reference to the array should sort it, save the pointer garbage for the mo
        if (childrenAsSet != null) {
            for (Name child : childrenAsSet) { // as mentioned above I'll allow this kind of access in here
                if (allChildren.add(child)) {
                    child.findAllChildren(allChildren, level + 1);
                }
            }
        } else if (children.length > 0) {
            Name[] childrenRefCopy = children; // in case it gets switched out half way through
            for (Name child : childrenRefCopy) {
                if (allChildren.add(child)) {
                    child.findAllChildren(allChildren, level + 1);
                }
            }
        }
    }

    private static final AtomicInteger finaAllChildren2Count = new AtomicInteger(0);

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
                    findAllChildren(localReference, 0);
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


    private static final AtomicInteger findValuesIncludingChildrenCount = new AtomicInteger(0);

    public Set<Value> findValuesIncludingChildren() {
        findValuesIncludingChildrenCount.incrementAndGet();
        Set<Value> localReference = valuesIncludingChildrenCache;
        if (localReference == null) {
            synchronized (this) {
                localReference = valuesIncludingChildrenCache;
                if (localReference == null) {
                    localReference = HashObjSets.newUpdatableSet(getValues());
                    for (Name child : findAllChildren()) {
                        child.addValuesToCollection(localReference);
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
    // todo this is all well and good, are we clearing the clearing the parent's caches? Should we?? Some thought required!
    private static final AtomicInteger clearChildrenCachesCount = new AtomicInteger(0);

    void clearChildrenCaches() {
        clearChildrenCachesCount.incrementAndGet();
        findAllChildrenCache = null;
        valuesIncludingChildrenCache = null;
    }

    private static final AtomicInteger getChildrenCount = new AtomicInteger(0);

    public Collection<Name> getChildren() {
        getChildrenCount.incrementAndGet();
        return childrenAsSet != null ? Collections.unmodifiableSet(childrenAsSet) : children.length > 0 ? Collections.unmodifiableList(Arrays.asList(children)) : Collections.emptyList();
    }

    private static final AtomicInteger getChildrenAsSetCount = new AtomicInteger(0);

    // if used incorrectly means NPE, I don't mind about this for the mo. We're allowing interpretSetTerm low level access to the list and sets to stop unnecessary collection copying in the query parser
    public Set<Name> getChildrenAsSet() {
        getChildrenAsSetCount.incrementAndGet();
        return Collections.unmodifiableSet(childrenAsSet);
    }

    private static final AtomicInteger getChildrenAsListCount = new AtomicInteger(0);

    public List<Name> getChildrenAsList() {
        getChildrenAsListCount.incrementAndGet();
        return children.length > 0 ? Collections.unmodifiableList(Arrays.asList(children)) : Collections.emptyList();
    }

    public boolean hasChildren() {
        return childrenAsSet != null || children.length > 0;
    }

    // todo - new implementations might mean that a false to true race condition may result in a null array reference. Since it won't go set->array then trying for the array first would be the thing to do
    public boolean hasChildrenAsSet() {
        return childrenAsSet != null;
    }

    // might seem inefficient but the adds and removes deal with parents and things. Might reconsider code if used more heavily
    // each add/remove is thread safe but should not allow two to run concurrently
    private static final AtomicInteger setChildrenCount = new AtomicInteger(0);

    // pass conneciton not provenance - we want the conneciton to know if the provenance was used or not
    public synchronized void setChildrenWillBePersisted(Collection<Name> newChildren, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        setChildrenCount.incrementAndGet();
        Collection<Name> existingChildren = getChildren();
        /* like an equals but the standard equals might trip up on different collection types
        notable that contains all will ignore ordering! If large it will be a HashSet anyway (note - it WAS the wrong way around,
         call contains on existing, NOT children, children (the passed parameter) could be a massive List!)
        this could provide a major speed increase where this function is "recklessly" called (e.g. in the "as" bit in parseQuery in NameService),
        don't keep reassigning "as" and clearing the caches when the collection is the same */
        if (newChildren.size() != existingChildren.size() || !existingChildren.containsAll(newChildren)) {
            for (Name oldChild : this.getChildren()) {
                removeFromChildrenWillBePersistedNoCacheClear(oldChild);
            }
            // there was a child null check here, not keen on that
            for (Name child : newChildren) {
                addChildWillBePersisted(child, false); // todo, get rid of the boolean
            }
            clearChildrenCaches();
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
            //getAzquoMemoryDB().clearSetAndCountCacheForName(this);
        }
    }

    private static final AtomicInteger addChildWillBePersistedCount = new AtomicInteger(0);

    public void addChildWillBePersisted(Name child, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        addChildWillBePersistedCount.incrementAndGet();
        if (addChildWillBePersisted(child, true)){
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
        }
    }

    private static final AtomicInteger addChildWillBePersisted3Count = new AtomicInteger(0);

    private boolean addChildWillBePersisted(Name child, boolean clearCache) throws Exception {
        addChildWillBePersisted3Count.incrementAndGet();
        checkDatabaseMatches(child);
        if (child.equals(this)) return false;//don't put child into itself
        /*removing this check - it takes far too long - maybe should make it optional
        if (findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        */
        // circular references are becoming a problem on JB so a compromise - check for a close circular reference. Hopefully won't cause a big performance hit
        if (getParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }

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
                        // unlike with parents and values we don't want to look for an empty initialised array here,
                        // children can be dealt with more cleanly in the linking (as in we'll make the array to size in one shot there after the names have been set in the maps in AzquoMemoryDB)
                        children = NameUtils.nameArrayAppend(children, child);
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
        return changed;
    }

    private static final AtomicInteger removeFromChildrenWillBePersistedCount = new AtomicInteger(0);

    public void removeFromChildrenWillBePersisted(Name name, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        removeFromChildrenWillBePersistedCount.incrementAndGet();
        if (removeFromChildrenWillBePersistedNoCacheClear(name)){
            clearChildrenCaches();
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
        }
        //getAzquoMemoryDB().clearSetAndCountCacheForName(this);
    }

    private static final AtomicInteger removeFromChildrenWillBePersisted2Count = new AtomicInteger(0);

    private boolean removeFromChildrenWillBePersistedNoCacheClear(Name name) throws Exception {
        removeFromChildrenWillBePersisted2Count.incrementAndGet();
        checkDatabaseMatches(name);// even if not needed throw the exception!
        // maybe could narrow this a little?
        synchronized (this) {
            if (getChildren().contains(name)) { // then something to remove
                name.removeFromParents(this);
                if (childrenAsSet != null) {
                    childrenAsSet.remove(name);
                } else {
                    children = NameUtils.nameArrayRemove(children, name); // note this will fail if it turns out children does not contain the name. Should be ok.
                }
                setNeedsPersisting();
                return true;
            }
        }
        return false;
    }

    // notably, since the map is created on the fly and not canonical I could just return it. A moot point I think.
    private static final AtomicInteger getAttributesCount = new AtomicInteger(0);

    public Map<String, String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return Collections.unmodifiableMap(nameAttributes.getAsMap());
    }

    // external access hence make unmodifiable. Currently just for a util function in azquomemorydb
    List<String> getAttributeKeys() {
        return Collections.unmodifiableList(nameAttributes.getAttributeKeys());
    }

    // I think plain old synchronized here is safe enough if not that fast
    // Hammered on importing but not loading, hence why I've not been as careful on garbage as I might be

    private static final AtomicInteger setAttributeWillBePersistedCount = new AtomicInteger(0);

    public synchronized void setAttributeWillBePersisted(String attributeName, String attributeValue, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        setAttributeWillBePersistedCount.incrementAndGet();
        // make safe for new way of persisting attributes
        if (attributeName.contains(StringLiterals.ATTRIBUTEDIVIDER)) {
            attributeName = attributeName.replace(StringLiterals.ATTRIBUTEDIVIDER, "");
        }
        if (attributeValue != null && attributeValue.contains(StringLiterals.ATTRIBUTEDIVIDER)) {
            attributeValue = attributeValue.replace(StringLiterals.ATTRIBUTEDIVIDER, "");
        }
        attributeName = attributeName.trim().toUpperCase(); // I think this is the only point at which attributes are created thus if it's uppercased here we should not need to check anywhere else
        /* code adapted from map based code to lists assume nameAttributes reference only set in code synchronized in these three functions and constructors*/
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
                getAzquoMemoryDB().getIndex().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
                nameAttributes = new NameAttributes(attributeKeys, attributeValues);
                setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
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
            getAzquoMemoryDB().getIndex().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
        } else {
            // a new one
            attributeKeys.add(attributeName);
            attributeValues.add(attributeValue);
        }
        nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        // now deal with the DB maps!
        getAzquoMemoryDB().getIndex().setAttributeForNameInAttributeNameMap(attributeName, attributeValue, this);
        setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
        setNeedsPersisting();
    }

    private static final AtomicInteger removeAttributeWillBePersistedCount = new AtomicInteger(0);

    private synchronized void removeAttributeWillBePersisted(String attributeName, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        removeAttributeWillBePersistedCount.incrementAndGet();
        attributeName = attributeName.trim().toUpperCase();
        int index = nameAttributes.getAttributeKeys().indexOf(attributeName);
        if (index != -1) {
            List<String> attributeKeys = new ArrayList<>(nameAttributes.getAttributeKeys());
            List<String> attributeValues = new ArrayList<>(nameAttributes.getAttributeValues());
            getAzquoMemoryDB().getIndex().removeAttributeFromNameInAttributeNameMap(attributeName, attributeValues.get(index), this);
            attributeKeys.remove(index);
            attributeValues.remove(index);
            nameAttributes = new NameAttributes(attributeKeys, attributeValues);
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
            setNeedsPersisting();
        }
    }

    // convenience - plain clearing of this object won't change the indexes in the memory db. Hence remove on each one.
    private static final AtomicInteger clearAttributesCount = new AtomicInteger(0);

    public synchronized void clearAttributes(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        clearAttributesCount.incrementAndGet();
        for (String attribute : nameAttributes.getAttributeKeys()) { // nameAttributes will be reassigned by the function but that should be ok, hang onto the key set as it was at the beginning
            removeAttributeWillBePersisted(attribute, azquoMemoryDBConnection);
        }
    }

    // criteria for fall back attributes added by WFC, not entirely sure I'd have done this but anyway
    private static final AtomicInteger findParentAttributesCount = new AtomicInteger(0);

    // todo - make more generic?
    private static String findParentAttributes(StandardName child, String attributeName, Set<Name> checked, Name origName, int level) {
        findParentAttributesCount.incrementAndGet();
        Name[] refCopy = child.parents;
        if (refCopy==null) {
            return null;
        }
        for (Name parent : refCopy) {
            if (!checked.contains(parent)) {
                checked.add(parent);
                // ok, check for the parent actually matching the display name, here we need to do a hack supporting the member of
                if (attributeName.contains(StringLiterals.MEMBEROF)) { // hacky doing this here but the alternative is bodging an AzquoMemoryDBConnection for NameService. Lesser of two evils.
                    String checkName = attributeName.substring(attributeName.indexOf(StringLiterals.MEMBEROF) + StringLiterals.MEMBEROF.length());
                    String parentName = attributeName.substring(0, attributeName.indexOf(StringLiterals.MEMBEROF));
                    if (parent.getDefaultDisplayName() != null && parent.getDefaultDisplayName().equalsIgnoreCase(checkName)) { // ok a candidate!
                        // now check the parents to see if it's correct
                        for (Name parentParent : parent.getParents()) { // yes parent parent a bit hacky, we're looking to qualify the parent. getParents not idea for garbage but I assume this will NOT be called that often!
                            if (parentParent.getDefaultDisplayName().equalsIgnoreCase(parentName)) {
                                if (level > 1) {
                                    //check that there is not a direct connection.
                                    // EFC comment - this is the attribute as names thing so if we ask for Azquo.TOWN and azquo is in Ludlow which is in Town then we return Ludlow.
                                    // I understand this logic but it's a classic thing that might not be documented and perhaps the code needs checking for perfoamnce issues
                                    Set<Name> directConnection = new HashSet<>(origName.getParents());
                                    directConnection.retainAll(parent.getChildren());
                                    if (directConnection.size() > 0) {
                                        return directConnection.iterator().next().getDefaultDisplayName();
                                    }
                                }
                                return child.getDefaultDisplayName();
                            }
                        }
                    }
                } else { // normal
                    if (parent.getDefaultDisplayName() != null && parent.getDefaultDisplayName().equalsIgnoreCase(attributeName)) {
                        if (level > 1) {
                            //check that there is not a direct connection.
                            Set<Name> directConnection = new HashSet<>(origName.getParents());
                            directConnection.retainAll(parent.getChildren());
                            if (directConnection.size() > 0) {
                                return directConnection.iterator().next().getDefaultDisplayName();
                            }
                        }
                        return child.getDefaultDisplayName();
                    }
                }
                String attribute = parent.getAttribute(attributeName, true, checked, origName, level + 1); // no parent check first time
                if (attribute != null) {
                    return attribute;
                }
            }
        }
        return null;
    }

    // default to parent check

    private static final AtomicInteger getAttributeCount = new AtomicInteger(0);

    public String getAttribute(String attributeName) {
        getAttributeCount.incrementAndGet();
        return getAttribute(attributeName, true, HashObjSets.newMutableSet());
    }

    private static final AtomicInteger getAttribute2Count = new AtomicInteger(0);

    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked) {
        return getAttribute(attributeName, parentCheck, checked, this, 0);
    }

    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked, Name origName, int level) {
        attributeName = attributeName.trim().toUpperCase(); // edd adding (back?) in, need to do this since all attributes are uppercase internally
        getAttribute2Count.incrementAndGet();
        String attribute = nameAttributes.getAttribute(attributeName);
        if (attribute != null) return attribute;
        //look up the chain for any parent with the attribute
        if (parentCheck) {
            return findParentAttributes(this, attributeName, checked, origName, level);
        }
        return null;
    }

    @Override
    final protected void setNeedsPersisting() {
        getAzquoMemoryDB().setNameNeedsPersisting(this);
    }

    // in here is a bit more efficient I think but should it be in the DAO?

    private static final AtomicInteger getAttributesForFastStoreCount = new AtomicInteger(0);

    public String getAttributesForFastStore() {
        getAttributesForFastStoreCount.incrementAndGet();
        return nameAttributes.getAttributesForFastStore();
    }

    NameAttributes getRawAttributes() {
        getAttributesForFastStoreCount.incrementAndGet();
        return nameAttributes;
    }

    public byte[] getChildrenIdsAsBytes() {
        return getChildrenIdsAsBytes(0);
    }

    // not really bothered about factoring at the moment - it would involve using the getChildren and getNames functions which needlesly create garbage (this function will be hammered when persisting, would rather not make the garbage)

    private static final AtomicInteger getChildrenIdsAsBytesCount = new AtomicInteger(0);

    private byte[] getChildrenIdsAsBytes(int tries) {
        getChildrenIdsAsBytesCount.incrementAndGet();
        try {
            if (childrenAsSet != null) {
                // theoretical possibility of size changing between allocate and creating the byte array
                // my initial method to sort this will be to catch the exception and try again.
                ByteBuffer buffer = ByteBuffer.allocate(childrenAsSet.size() * 4);
                for (Name name : childrenAsSet) {
                    buffer.putInt(name.getId());
                }
                return buffer.array();
            } else {
                // don't ref copy as we have the try/catch? Hmmmmmmmmm
                ByteBuffer buffer = ByteBuffer.allocate(children.length * 4);
                for (Name name : children) {
                    buffer.putInt(name.getId());
                }
                return buffer.array();
            }
        } catch (BufferOverflowException e) {
            if (tries < 50) { // a bit arbitrary - todo, change later
                tries++;
                System.out.println("retrying after buffer overflow in getChildrenIdsAsBytes on " + getDefaultDisplayName() + " try number " + tries);
                return getChildrenIdsAsBytes(tries);
            } else {
                throw e;
            }
        }
    }

    /* protected to only be used by the database loading, can't be called in the constructor as name by id maps may not be populated
    changing synchronized to only relevant portions
    note : this function is absolutely hammered while linking (surprise!) so optimisations here are helpful */

    private static final AtomicInteger linkCount = new AtomicInteger(0);

    // 12/09/18 - adding the boolean for backup restore. Looks a bit less elegant, might be a way around it
    void link(byte[] childrenCache, boolean backupRestore) throws Exception {
        linkCount.incrementAndGet();
        if ((getAzquoMemoryDB().getNeedsLoading() || backupRestore) && childrenCache != null) { // if called and these conditions are not true then there's a problem
            synchronized (this) {
                // it's things like this that I believe will be way faster and lighter on garbage than the old json method
                int noChildren = childrenCache.length / 4;
                ByteBuffer byteBuffer = ByteBuffer.wrap(childrenCache);
                if (noChildren > ARRAYTHRESHOLD) {
                    this.childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(noChildren));
                    for (int i = 0; i < noChildren; i++) {
                        if (getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4)) == null){
                            System.out.println("can't find name with id : " + byteBuffer.getInt(i * 4) + " while linking");
                        } else {
                            this.childrenAsSet.add(getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4)));
                        }
                    }
                } else {
                    boolean removeNull = false;
                    Name[] newChildren = new Name[noChildren];
                    for (int i = 0; i < noChildren; i++) {
                        newChildren[i] = getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4));
                        if (newChildren[i] == null){
                            System.out.println("can't find name with id : " + byteBuffer.getInt(i * 4) + " while linking");
                            removeNull = true;
                        }
                    }
                    if (removeNull){
                        List<Name> check = Lists.newArrayList(newChildren);
                        while (check.remove(null));
                        newChildren = check.toArray(new Name[0]);
                    }

                    this.children = newChildren;
                }
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
            // directly hitting the array could cause a problem if it were reassigned while this happened but here it should be fine,
            // loading doesn't alter the array lengths, they're set in preparation.
            for (Name aChildren : children) {
                aChildren.addToParents(this, true);
            }
        }
    }

    // first of its kind. Try to be comprehensive
    // want to make it synchronized but I'm calling synchronized functions on other objects. Hmmmmmmmm.
    private static final AtomicInteger deleteCount = new AtomicInteger(0);

    public void delete(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        deleteCount.incrementAndGet();
//        List<Value> values;
//        List<Name> parents;
        Collection<Value> values;
        Collection<Name> parents;

        // ok we want a thread safe snapshot really of things that are synchronized on other objects
        synchronized (this) {
            /*
            todo : not copying values wasn't clever but what about parents? It doesn't need a copy . . . how can it get screwed up?

            I wasn't copying the parents and values here (my making a new ArrayList) which was a big mistake. Since these are "derived"
            then they will change as a result of the actions after the synchronized block.
            v.delete() will remove that value from values and parent.removeFromChildrenWillBePersisted will similarly remove that parent
            from parents. Notably the Iterator never threw a concurrent modification exception as underlying was either a thread safe set
            backed by ConcurrentHashMap or a simple array behind Arrays.AsList which can be modified and seemingly won't cause throw a
            wobbler if the same thread that's iterating removes the element.

            Although I now wonder - the set might change behind but the arrays are switched out - so surely if reduced then it wouldn't matter? Going to test
             */
            values = new ArrayList<>(getValues());
            //parents = new ArrayList<>(getParents());
            parents = getParents();
            // ok, having made a copy zap the parents! This is to avoid a performance issue. The iteration below will remove the parents one by one
            // but if the parents (due to a misconfigured db) have say a million items then the name array copy above will be slow. This removes that issue
            this.parents = new Name[0];
            // the basics are done here in the synchronized block
            getAzquoMemoryDB().removeNameFromDb(this);
            setNeedsDeleting();
            setNeedsPersisting();
            // remove children - this is using the same lock so do it in here
            for (Name child : getChildren()) {
                removeFromChildrenWillBePersisted(child, azquoMemoryDBConnection);
            }
            for (String attribute : nameAttributes.getAttributeKeys()) {
                setAttributeWillBePersisted(attribute, null, azquoMemoryDBConnection); // simple way to clear it from the indexes
            }
        }
        // then the actions on other objects - we now delete such values and don't have history, without the name it makes no sense
        for (Value v : values) {
            v.deleteNoHistory();
        }
        // remove from parents
        for (Name parent : parents) {
            parent.removeFromChildrenWillBePersisted(this, azquoMemoryDBConnection);
        }
    }

    static void printFunctionCountStats() {
        System.out.println("######### NAME FUNCTION COUNTS");
        System.out.println("newNameCount\t\t\t\t" + newNameCount.get());
        System.out.println("newNameCount3\t\t\t\t" + newName3Count.get());
        System.out.println("getDefaultDisplayNameCount\t\t\t\t" + getDefaultDisplayNameCount.get());
        System.out.println("getValuesCount\t\t\t\t" + getValuesCount.get());
        System.out.println("addToValuesCount\t\t\t\t" + addToValuesCount.get());
        System.out.println("removeFromValuesCount\t\t\t\t" + removeFromValuesCount.get());
        System.out.println("getParentsCount\t\t\t\t" + getParentsCount.get());
        System.out.println("addToParentsCount\t\t\t\t" + addToParentsCount.get());
        System.out.println("removeFromParentsCount\t\t\t\t" + removeFromParentsCount.get());
        System.out.println("findAllParentsCount\t\t\t\t" + findAllParentsCount.get());
        System.out.println("findAllParents2Count\t\t\t\t" + findAllParents2Count.get());
        System.out.println("memberNameCount\t\t\t\t" + memberNameCount.get());
        System.out.println("addNamesCount\t\t\t\t" + addNamesCount.get());
        System.out.println("findATopParentCount\t\t\t\t" + findATopParentCount.get());
        System.out.println("finaAllChildrenCount\t\t\t\t" + finaAllChildrenCount.get());
        System.out.println("finaAllChildren2Count\t\t\t\t" + finaAllChildren2Count.get());
        System.out.println("findValuesIncludingChildrenCount\t\t\t\t" + findValuesIncludingChildrenCount.get());
        System.out.println("clearChildrenCachesCount\t\t\t\t" + clearChildrenCachesCount.get());
        System.out.println("getChildrenCount\t\t\t\t" + getChildrenCount.get());
        System.out.println("setChildrenCount\t\t\t\t" + setChildrenCount.get());
        System.out.println("addChildWillBePersistedCount\t\t\t\t" + addChildWillBePersistedCount.get());
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
        System.out.println("linkCount\t\t\t\t" + linkCount.get());
        System.out.println("deleteCount\t\t\t\t" + deleteCount.get());
    }

    static void clearFunctionCountStats() {
        newNameCount.set(0);
        newName3Count.set(0);
        getDefaultDisplayNameCount.set(0);
        getValuesCount.set(0);
        addToValuesCount.set(0);
        removeFromValuesCount.set(0);
        getParentsCount.set(0);
        addToParentsCount.set(0);
        removeFromParentsCount.set(0);
        findAllParentsCount.set(0);
        findAllParents2Count.set(0);
        memberNameCount.set(0);
        addNamesCount.set(0);
        findATopParentCount.set(0);
        finaAllChildrenCount.set(0);
        finaAllChildren2Count.set(0);
        findValuesIncludingChildrenCount.set(0);
        clearChildrenCachesCount.set(0);
        getChildrenCount.set(0);
        setChildrenCount.set(0);
        addChildWillBePersistedCount.set(0);
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
        linkCount.set(0);
        deleteCount.set(0);
    }
}