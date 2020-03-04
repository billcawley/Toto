package com.azquo.memorydb.core;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.namedata.implementation.*;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.service.NameService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

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
 * <p>
 * I'm going to attempt to hive off everything except parents and provenance to a name data class. Prototyping in NewName first then move into name and comment properly when it's tested todo
 */
public final class NewName extends Name {

    // same logic as find all parents but returns a set, should be correct
    // Koloboke makes the sets as light as can be expected, volatile to comply with double-checked locking pattern https://en.wikipedia.org/wiki/Double-checked_locking
//    private volatile Set<Name> findAllChildrenCache = null;

    /* as above but for values, proved to provide a decent speed increase
    plus I can check some alternative set intersection stuff
    Tie its invalidation to the find all children invalidation
    See comments in findAllChildren for cache pattern notes
    */
    //private volatile Set<Value> valuesIncludingChildrenCache = null;
    // ok so a pointer for a cache on every name is silly, the caches will be very sparse so jam 'em in a concurrent map. This may simplify the code also

//    private static final Logger logger = Logger.getLogger(Name.class);

    private Provenance provenance; // should be volatile? Don't care about being completely up to date but could a partially constructed object get in here?

    // note there's some parents normalisation code kicking around, results were mixed but something to consider (share parent arrays across names)
    private volatile Name[] parents;

    // in the new model there are various implementations of NameData. This will hopefully save memory/increase performance
    // and more importantly will allow new functionality e.g. children with a relationship e.g. Edward Cawley "Born In" 04-12-1980 without extra memory overhead
    private volatile NameData nameData;

    // For the code to make new names (not when loading).

    private static AtomicInteger newNameCount = new AtomicInteger(0);

    public NewName(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance) throws Exception {
        super(azquoMemoryDB, 0);
        newNameCount.incrementAndGet();
        parents = new Name[0];
        nameData = new DefaultDisplayName(null);
        getAzquoMemoryDB().addNameToDb(this);
        newNameCount.incrementAndGet();
        this.provenance = provenance;
    }

    private static AtomicInteger newName3Count = new AtomicInteger(0);

    // For loading, it should only be used by the NameDAO, I can't really restrict it and make it non public without rearranging the package I don't think.
    // yes I am exposing "this". Seems ok so far. Interning the attributes should help memory usage.

    public NewName(final AzquoMemoryDB azquoMemoryDB, int id, int provenanceId, String attributes, int noParents, int noValues, int noChildren) throws Exception {
        this(azquoMemoryDB, id, provenanceId, attributes, noParents, noValues, noChildren, false);
    }

    // package private - BackupTransport can force the ID
    // note - currently this doesn't take NameAttributes as the old one did
    NewName(final AzquoMemoryDB azquoMemoryDB, int id, int provenanceId, String attributes, int noParents, int noValues, int noChildren, boolean forceIdForbackup) throws Exception {
        super(azquoMemoryDB, id, forceIdForbackup);
        newName3Count.incrementAndGet();
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId); // see no reason not to do this here now
        if (this.provenance == null) {
            System.out.println("Provenance null on backup restore!, id is " + provenanceId + ", making blank provenance");
            this.provenance = new Provenance(azquoMemoryDB, "-", "-", "-", "-");
        }
        parents = new Name[noParents]; // no parents as set! as mentioned parents will very rarely big big and certainly not in the same way children and values are.
        // old name could pass through a NameAttributes as an optimisation. I've zapped it for the moment can maybe reimplement later
        String defaultDisplayName = null;
        NameAttributes nameAttributes = null;
        String[] attsArray = attributes.split(StringLiterals.ATTRIBUTEDIVIDER);
        if (attsArray.length == 2 && attsArray[0].equalsIgnoreCase(StringLiterals.DEFAULT_DISPLAY_NAME)) {
            defaultDisplayName = attsArray[1];
            azquoMemoryDB.getIndex().setAttributeForNameInAttributeNameMap(StringLiterals.DEFAULT_DISPLAY_NAME, defaultDisplayName, this); // used to be done after, can't se a reason not to do this here
        } else if (attsArray.length % 2 == 0) {
            // a thought : if the attributes string was attributes then values might it be a little faster here? Am not sure
            String[] attributeKeys = new String[attsArray.length / 2];
            String[] attributeValues = new String[attsArray.length / 2];
            for (int i = 0; i < attributeKeys.length; i++) {
                attributeKeys[i] = attsArray[i * 2].intern();
                attributeValues[i] = attsArray[(i * 2) + 1].intern();
                azquoMemoryDB.getIndex().setAttributeForNameInAttributeNameMap(attributeKeys[i], attributeValues[i], this); // used to be done after, can't se a reason not to do this here
            }
            nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        }
        /* OK I realise that this criteria of greater than or equal to ARRAYTHRESHOLD assigning a set and less than an array is slightly inconsistent with addToValues
        that is to say one could get to an array of 512, save and reload and then that 512 would be a set. I do this because the value loading is a bit of a hack
        and if I copy the criteria then addToValues will crash on the edge case of 512. I don't really see a problem with this under typical operation. It might, I suppose,
        be an issue in a database with many names with 512 values but the rest of the time it's a moot point. DOcumenting here in case anyone sees the incinsistency and tries to "fix" it.
        */
        /*

        valuesAsSet = noValues >= ARRAYTHRESHOLD ? Collections.newSetFromMap(new ConcurrentHashMap<>(noValues)) : null; // should help loading - prepare the space
        // could values be nulled if the set is being used? The saving would be minor I think.
        values = new Value[noValues < ARRAYTHRESHOLD ? noValues : 0]; // prepare the array, as long as this is taken into account later should be fine
        childrenAsSet = null;// dealt with properly later
        children = new NewName[0];

         */
        //under new thing this decides the implementation of name data, think I'm gonna have to branch to the 18 options available . . .
        // should this logic be pushed into NameData?
        if (nameAttributes == null) { // so a default display name implementation will do it
            if (noValues == 0) { // no values
                if (noChildren == 0) { // no children
                    nameData = new DefaultDisplayName(defaultDisplayName);
                } else if (noChildren < NameData.ARRAYTHRESHOLD) { // array children
                    nameData = new DefaultDisplayNameChildrenArray(defaultDisplayName);
                } else { // set values
                    nameData = new DefaultDisplayNameChildrenSet(defaultDisplayName);
                }
            } else if (noValues < NameData.ARRAYTHRESHOLD) { // array values
                if (noChildren == 0) { // no children
                    nameData = new DefaultDisplayNameValuesArray(defaultDisplayName, noValues);
                } else if (noChildren < NameData.ARRAYTHRESHOLD) { // array children
                    nameData = new DefaultDisplayNameValuesArrayChildrenArray(defaultDisplayName, noValues);
                } else { // set values
                    nameData = new DefaultDisplayNameValuesArrayChildrenSet(defaultDisplayName, noValues);
                }
            } else { // set values
                if (noChildren == 0) { // no children
                    nameData = new DefaultDisplayNameValuesSet(defaultDisplayName);
                } else if (noChildren < NameData.ARRAYTHRESHOLD) { // array children
                    nameData = new DefaultDisplayNameValuesSetChildrenArray(defaultDisplayName);
                } else { // set values
                    nameData = new DefaultDisplayNameValuesSetChildrenSet(defaultDisplayName);
                }
            }
        } else { // name attributes
            if (noValues == 0) { // no values
                if (noChildren == 0) { // no children
                    nameData = new Attributes(nameAttributes);
                } else if (noChildren < NameData.ARRAYTHRESHOLD) { // array children
                    nameData = new AttributesChildrenArray(nameAttributes);
                } else { // set values
                    nameData = new AttributesChildrenSet(nameAttributes);
                }
            } else if (noValues < NameData.ARRAYTHRESHOLD) { // array values
                if (noChildren == 0) { // no children
                    nameData = new AttributesValuesArray(nameAttributes, noValues);
                } else if (noChildren < NameData.ARRAYTHRESHOLD) { // array children
                    nameData = new AttributesValuesArrayChildrenArray(nameAttributes, noValues);
                } else { // set values
                    nameData = new AttributesValuesArrayChildrenSet(nameAttributes, noValues);
                }
            } else { // set values
                if (noChildren == 0) { // no children
                    nameData = new AttributesValuesSet(nameAttributes);
                } else if (noChildren < NameData.ARRAYTHRESHOLD) { // array children
                    nameData = new AttributesValuesSetChildrenArray(nameAttributes);
                } else { // set values
                    nameData = new AttributesValuesSetChildrenSet(nameAttributes);
                }
            }
        }
        getAzquoMemoryDB().addNameToDb(this);
    }

    private static AtomicInteger getDefaultDisplayNameCount = new AtomicInteger(0);

    // for convenience but be careful where it is used . . .
    public String getDefaultDisplayName() {
        getDefaultDisplayNameCount.incrementAndGet();
        return nameData.getDefaultDisplayName();
    }

    // provenance immutable. If it were not then would need to clone

    public Provenance getProvenance() {
        return provenance;
    }

    synchronized void setProvenanceWillBePersisted(final Provenance provenance) {
        if (this.provenance == null || !this.provenance.equals(provenance)) {
            this.provenance = provenance;
            setNeedsPersisting();
            for (Name n : getParents()) {
                n.setProvenanceWillBePersisted(provenance);
            }
        }
    }

    @Override
    public String toString() {
        return "Name{" +
                "id=" + getId() +
                ", default display name=" + getDefaultDisplayName() +
                ", provenance=" + provenance +
                ", nameAttributes=" + nameData.getAttributes() +
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
        return nameData.getValues();
    }

    public int getValueCount() {
        return getValues().size();
    }

    public boolean hasValues() {
        return nameData.hasValues();
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
        nameData.valueArrayCheck();
    }

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
    private static AtomicInteger addToValuesCount = new AtomicInteger(0);

    void addToValues(final Value value) throws Exception {
        addToValues(value, false);
    }

    private void addToValues(final Value value, boolean backupRestore) throws Exception {
        addToValuesCount.incrementAndGet();
        boolean databaseIsLoading = getAzquoMemoryDB().getNeedsLoading() || backupRestore;
        checkDatabaseMatches(value);
        // defensive for the mo
        synchronized (this) {
            // may make this more clever as in clearing only if there's a change but not now
            getAzquoMemoryDB().getValuesIncludingChildrenCacheMap().remove(this);
            if (!nameData.canAddValue()) {
                nameData = nameData.getImplementationThatCanAddValue();
            }
            if (nameData.addToValues(value, backupRestore || databaseIsLoading)){
                setNeedsPersisting(); // will be ignored on loading. Best to put in here to be safe. Could maybe check it's actually necessary above if performance an issue.
            }
        }
    }

    private static AtomicInteger removeFromValuesCount = new AtomicInteger(0);

    void removeFromValues(final Value value) {
        removeFromValuesCount.incrementAndGet();
        // safer to synchronize here, most of the time it will be uncontended and I'm not currently worried about that performance
        synchronized (this) {
            if (nameData.removeFromValues(value)) {
                getAzquoMemoryDB().getValuesIncludingChildrenCacheMap().remove(this);
                setNeedsPersisting(); // will be ignored on loading. Best to put in here to be safe.
            }
        }
    }

    // returns list now as that's what it is!
    private static AtomicInteger getParentsCount = new AtomicInteger(0);

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
     called after it is confirmed that changed to children actually happened

     note that the new pattern has left this package private. Might be able to more it to protected
     */

    private static AtomicInteger addToParentsCount = new AtomicInteger(0);

    // don't trust no_parents, revert to old style but add logging
    void addToParents(final Name name, boolean databaseIsLoading) {
        addToParentsCount.incrementAndGet();
        synchronized (this) {
            if (databaseIsLoading || !Arrays.asList(parents).contains(name)) {
                if (parents.length != 0 && parents[parents.length - 1] == null) {
                    if (!databaseIsLoading) {
                        System.out.println("empty space in parents after the database has finished loading - no_parents wrong on name id " + getId() + " " + getDefaultDisplayName());
                        //getAzquoMemoryDB().forceNameNeedsPersisting(this);
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
                        //getAzquoMemoryDB().forceNameNeedsPersisting(this);
                    }
                    parents = NameUtils.nameArrayAppend(parents, name);
                }
                // won't of course be used if we're loading . . .
                setNeedsPersisting();
            }
        }
    }

    private static AtomicInteger removeFromParentsCount = new AtomicInteger(0);

    void removeFromParents(final Name name) {
        removeFromParentsCount.incrementAndGet();
        synchronized (this) { // just sync on this object to protect the lists
            parents = NameUtils.nameArrayRemoveIfExists(parents, name);
        }
        setNeedsPersisting();
    }

    /* returns a collection, I think this is just iterated over to check stuff but a set internally as it should not have duplicates. These two functions moved
    here from the spreadsheet as I want direct access to the parents array, saving a fair amount of garbage if it's hammered (array vs ArrayList.iterator())*/

    private static AtomicInteger findAllParentsCount = new AtomicInteger(0);

    public Collection<Name> findAllParents() {
        findAllParentsCount.incrementAndGet();
        final Set<Name> allParents = HashObjSets.newMutableSet(); // should be ok to use these now - maybe use updateable map?
        findAllParents(allParents);
        return allParents;
    }

    private static AtomicInteger findAllParents2Count = new AtomicInteger(0);

    // note : this was using getParents, since it was really being hammered this was no good due to the garbage generation of that function, hence the change
    // public and stateless, in here as it accesses things I don't want accessed outside
    public void findAllParents(final Set<Name> allParents) {
        findAllParents2Count.incrementAndGet();
        Name[] parentsRefCopy = parents; // ok in theory the parents could get modified in the for loop, this wouldn't be helpful so I'll keep a copy of the reference to use in case name.parents gets switched out
        for (Name parent : parentsRefCopy) { // should be the same as a for int i; i < parentsRefCopy.length; i++
            if (parent == null) { // having fixed a bug this should be rare now. Typical cause of a null parent would be no_parents in MySQL being incorrect and too big
                System.out.println("DATABASE CORRUPTION " + getDefaultDisplayName() + " id " + getId() + " has a null parent");
            } else {
                if (allParents.add(parent)) { // the function was moved in here to access this array directly. Externally it would need to be wrapped in an unmodifiable List. Means garbage!
                    parent.findAllParents(allParents);
                }
            }
        }
    }

    // used when permuting - for element we want to find a parent it has (possibly up the chain) in top set. So if we had a shop and we did member name with that and "All Counties" we'd hope to find the county the shop was in
    private static AtomicInteger memberNameCount = new AtomicInteger(0);

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


    private static AtomicInteger addNamesCount = new AtomicInteger(0);

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
                if (nameData.directSetChildren() != null) {
                    namesFound.addAll(nameData.directSetChildren());
                } else {
                    Name[] nameRef = nameData.directArrayChildren(); // try to stop a nasty swap out. As in not set go here oh it gets changed in the mean time then you get an NPE as the null pointer check was fooled
                    if (nameRef != null) {
                        //IntelliJ recommends Collections.addAll(namesFound, name.children); instead. I think it's not quite as efficient
                        for (int i = 0; i < nameRef.length; i++) {
                            namesFound.add(nameRef[i]);
                        }
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
        if (nameData.hasValues()){
            if (nameData.directSetValues() != null) {
                values.addAll(nameData.directSetValues());
            } else {
                Value[] valuesRef = nameData.directArrayValues(); // try to stop a nasty swap out. As in not set go here oh it gets changed in the mean time then you get an NPE as the null pointer check was fooled
                if (valuesRef != null) {
                    //IntelliJ recommends Collections.addAll(namesFound, name.children); instead. I think it's not quite as efficient
                    for (int i = 0; i < valuesRef.length; i++) {
                        values.add(valuesRef[i]);
                    }
                }
            }
        }
    }

    // we are now allowing a name to be in more than one top parent, hence the name change

    private static AtomicInteger findATopParentCount = new AtomicInteger(0);

    public Name findATopParent() {
        findATopParentCount.incrementAndGet();
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

    private static AtomicInteger finaAllChildrenCount = new AtomicInteger(0);

    public void findAllChildren(final Set<Name> allChildren) {
        finaAllChildrenCount.incrementAndGet();
        // similar to optimisation for get all parents
        if (hasChildren()){
            if (nameData.directSetChildren() != null) {
                for (Name child : nameData.directSetChildren()) { // as mentioned above I'll allow this kind of access in here
                    if (allChildren.add(child)) {
                        child.findAllChildren(allChildren);
                    }
                }
                // in theory could get switched but no exception just no children briefly. A concern? Would be sorted by getting namesref above. Consider . . .
            } else{
                Name[] namesRef = nameData.directArrayChildren();
                if (namesRef != null && namesRef.length > 0) {
                    for (Name child : namesRef) {
                        if (allChildren.add(child)) {
                            child.findAllChildren(allChildren);
                        }
                    }
                }
            }
        }
    }

    private static AtomicInteger finaAllChildren2Count = new AtomicInteger(0);

    public Collection<Name> findAllChildren() {
        finaAllChildren2Count.incrementAndGet();
        return Collections.unmodifiableSet(getAzquoMemoryDB().getFindAllChildrenCacheMap().computeIfAbsent(this, name -> {
            Set<Name> toReturn = HashObjSets.newUpdatableSet();
            findAllChildren(toReturn);
            return toReturn.isEmpty() ? Collections.emptySet() : toReturn;
        }));
    }

    private static AtomicInteger findValuesIncludingChildrenCount = new AtomicInteger(0);

    public Set<Value> findValuesIncludingChildren() {
        findValuesIncludingChildrenCount.incrementAndGet();
        return Collections.unmodifiableSet(getAzquoMemoryDB().getValuesIncludingChildrenCacheMap().computeIfAbsent(this, name -> {
            Set<Value> toReturn = HashObjSets.newUpdatableSet();
            for (Name child : findAllChildren()) {
                child.addValuesToCollection(toReturn);
            }
            return toReturn.isEmpty() ? Collections.emptySet() : toReturn;

        }));
    }

    // synchronized? Not sure if it matters, don't need immediate visibility and the cache read should (!) be thread safe.
    // The read uses synchronized to stop creating the cache more than necessary rather than to be totally up to date
    private static AtomicInteger clearChildrenCachesCount = new AtomicInteger(0);

    void clearChildrenCaches() {
        clearChildrenCachesCount.incrementAndGet();
        getAzquoMemoryDB().getFindAllChildrenCacheMap().remove(this);
        getAzquoMemoryDB().getValuesIncludingChildrenCacheMap().remove(this);
    }

    private static AtomicInteger getChildrenCount = new AtomicInteger(0);

    public Collection<Name> getChildren() {
        getChildrenCount.incrementAndGet();
        return Collections.unmodifiableCollection(nameData.getChildren());
    }

    private static AtomicInteger getChildrenAsSetCount = new AtomicInteger(0);

    // if used incorrectly means NPE, I don't mind about this for the mo. We're allowing interpretSetTerm low level access to the list and sets to stop unnecessary collection copying in the query parser
    public Set<Name> getChildrenAsSet() {
        getChildrenAsSetCount.incrementAndGet();
        return Collections.unmodifiableSet(nameData.directSetChildren());
    }

    private static AtomicInteger getChildrenAsListCount = new AtomicInteger(0);

    //could a race condition creep in here where nameData is replaced? If so would NPE
    public List<Name> getChildrenAsList() {
        getChildrenAsListCount.incrementAndGet();
        // given code structure this needs to return empty where there's no children
        return (nameData.directArrayChildren() != null && nameData.directArrayChildren().length > 0) ? Collections.unmodifiableList(Arrays.asList(nameData.directArrayChildren())) : Collections.emptyList();
    }

    public boolean hasChildren() {
        return nameData.hasChildren();
    }

    // todo - new implementations might mean that a false to true race condition may result in a null array reference. Since it won't go set->array then trying for the set first makes sense?
    public boolean hasChildrenAsSet() {
        return nameData.directSetChildren() != null;
    }

    // might seem inefficient but the adds and removes deal with parents and things. Might reconsider code if used more heavily
    // each add/remove is thread safe but should not allow two to run concurrently
    private static AtomicInteger setChildrenCount = new AtomicInteger(0);

    // pass connection not provenance - we want the connection to know if the provenance was used or not
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

    private static AtomicInteger addChildWillBePersistedCount = new AtomicInteger(0);

    public void addChildWillBePersisted(Name child, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        addChildWillBePersistedCount.incrementAndGet();
        if (addChildWillBePersisted(child, true)) {
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
        }
    }

    private static AtomicInteger addChildWillBePersisted3Count = new AtomicInteger(0);

    private boolean addChildWillBePersisted(Name child, boolean clearCache) throws Exception {
        addChildWillBePersisted3Count.incrementAndGet();
        checkDatabaseMatches(child);
        if (child.equals(this)) return false;//don't put child into itself
        /*removing this check - it takes far too long - maybe should make it optional
        if (findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        */
        // NOTE! for the set version we're now just using a set backed by a concurrent hash map, for large child sets ordering will be ignored.
        // While childrenAsSet is thread safe I think I'm going to need to synchronize the lot to make make state more consistent, at least with itself
        boolean changed = false; // only do the after stuff if something changed
        synchronized (this) {

            if (!nameData.canAddChild()) {
                nameData = nameData.getImplementationThatCanAddChild();
            }
            changed = nameData.addToChildren(child);

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

    private static AtomicInteger removeFromChildrenWillBePersistedCount = new AtomicInteger(0);

    public void removeFromChildrenWillBePersisted(Name name, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        removeFromChildrenWillBePersistedCount.incrementAndGet();
        if (removeFromChildrenWillBePersistedNoCacheClear(name)) {
            clearChildrenCaches();
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
        }
        //getAzquoMemoryDB().clearSetAndCountCacheForName(this);
    }

    private static AtomicInteger removeFromChildrenWillBePersisted2Count = new AtomicInteger(0);

    private boolean removeFromChildrenWillBePersistedNoCacheClear(Name name) throws Exception {
        removeFromChildrenWillBePersisted2Count.incrementAndGet();
        checkDatabaseMatches(name);// even if not needed throw the exception!
        // maybe could narrow this a little?
        synchronized (this) {
            if (getChildren().contains(name)) { // then something to remove
                name.removeFromParents(this);
                nameData.removeFromChildren(name);
                setNeedsPersisting();
                return true;
            }
        }
        return false;
    }

    // notably, since the map is created on the fly and not canonical I could just return it. A moot point I think.
    private static AtomicInteger getAttributesCount = new AtomicInteger(0);

    public Map<String, String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return Collections.unmodifiableMap(nameData.getAttributes());
    }

    // external access hence make unmodifiable. Currently just for a util function in azquomemorydb
    List<String> getAttributeKeys() {
        return Collections.unmodifiableList(nameData.getAttributeKeys());
    }

    // I think plain old synchronized here is safe enough if not that fast
    // Hammered on importing but not loading, hence why I've not been as careful on garbage as I might be

    private static AtomicInteger setAttributeWillBePersistedCount = new AtomicInteger(0);

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
        // since this is all synchronized I think we're ok here?
        if (!attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME) && !nameData.canSetAttributesOtherThanDefaultDisplayName()) {
            nameData = nameData.getImplementationThatCanSetAttributesOtherThanDefaultDisplayName();
        }
        String existing = nameData.setAttribute(attributeName, attributeValue);
        if (existing != null) {
            getAzquoMemoryDB().getIndex().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
        }
        // note - this means provenance ans persisting will currently be set even where the attribute hasn't changed.
        // Need to think about what else could be returned from nameData.setAttribute to inform existing or changed
        getAzquoMemoryDB().getIndex().setAttributeForNameInAttributeNameMap(attributeName, attributeValue, this);
        setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
        setNeedsPersisting();
    }

    private static AtomicInteger removeAttributeWillBePersistedCount = new AtomicInteger(0);

    private synchronized void removeAttributeWillBePersisted(String attributeName, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        removeAttributeWillBePersistedCount.incrementAndGet();
        attributeName = attributeName.trim().toUpperCase();
        String existing = nameData.removeAttribute(attributeName);
        if (existing != null) {
            //todo
            //getAzquoMemoryDB().getIndex().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
            setProvenanceWillBePersisted(azquoMemoryDBConnection.getProvenance());
            setNeedsPersisting();
        }
    }

    // convenience - plain clearing of this object won't change the indexes in the memory db. Hence remove on each one.
    private static AtomicInteger clearAttributesCount = new AtomicInteger(0);

    public synchronized void clearAttributes(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        clearAttributesCount.incrementAndGet();
        for (String attribute : nameData.getAttributeKeys()) { // nameAttributes will be reassigned by the function but that should be ok, hang onto the key set as it was at the beginning
            removeAttributeWillBePersisted(attribute, azquoMemoryDBConnection);
        }
    }

    // criteria for fall back attributes added by WFC, not entirely sure I'd have done this but anyway
    private static AtomicInteger findParentAttributesCount = new AtomicInteger(0);

    // todo make mroe generic?
    private static String findParentAttributes(NewName child, String attributeName, Set<Name> checked, Name origName, int level) {
        findParentAttributesCount.incrementAndGet();
        for (Name parent : child.parents) {
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

    private static AtomicInteger getAttributeCount = new AtomicInteger(0);

    public String getAttribute(String attributeName) {
        getAttributeCount.incrementAndGet();
        return getAttribute(attributeName, true, HashObjSets.newMutableSet());
    }

    private static AtomicInteger getAttribute2Count = new AtomicInteger(0);

    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked) {
        return getAttribute(attributeName, parentCheck, checked, this, 0);
    }

    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked, Name origName, int level) {
        attributeName = attributeName.trim().toUpperCase(); // edd adding (back?) in, need to do this since all attributes are uppercase internally
        getAttribute2Count.incrementAndGet();
        String attribute = nameData.getAttribute(attributeName);
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

    private static AtomicInteger getAttributesForFastStoreCount = new AtomicInteger(0);

    public String getAttributesForFastStore() {
        getAttributesForFastStoreCount.incrementAndGet();
        return nameData.getAttributesForFastStore();
    }

    public byte[] getChildrenIdsAsBytes() {
        return getChildrenIdsAsBytes(0);
    }

    // not really bothered about factoring at the moment - it would involve using the getChildren and getNames functions which needlesly create garbage (this function will be hammered when persisting, would rather not make the garbage)

    private static AtomicInteger getChildrenIdsAsBytesCount = new AtomicInteger(0);

    private byte[] getChildrenIdsAsBytes(int tries) {
        getChildrenIdsAsBytesCount.incrementAndGet();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(nameData.getChildren().size() * 4);
            for (Name name : nameData.getChildren()) {
                buffer.putInt(name.getId());
            }
            return buffer.array();
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

    private static AtomicInteger linkCount = new AtomicInteger(0);

    // 12/09/18 - adding the boolean for backup restore. Looks a bit less elegant, might be a way around it
    void link(byte[] childrenCache, boolean backupRestore) throws Exception {
        linkCount.incrementAndGet();
        if ((getAzquoMemoryDB().getNeedsLoading() || backupRestore) && childrenCache != null) { // if called and these conditions are not true then there's a problem
            synchronized (this) {
                // it's things like this that I believe will be way faster and lighter on garbage than the old json method
                int noChildren = childrenCache.length / 4;
                ByteBuffer byteBuffer = ByteBuffer.wrap(childrenCache);
                if (nameData.canSetArrayChildren()){
                    Name[] newChildren = new Name[noChildren];
                    for (int i = 0; i < noChildren; i++) {
                        newChildren[i] = getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4));
                    }
                    nameData.setArrayChildren(newChildren);
                } else { // adding them into a set
                    for (int i = 0; i < noChildren; i++) {
                        nameData.addToChildren(getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4)));
                    }
                }
            }
        } else {
            throw new Exception("Trying to link with no children cache or after loading?"); // children cache could be empty but should not be null!
        }
        /* need to sort out the parents - I deliberately excluded this from synchronisation or one could theoretically hit a deadlock.
         addToParents can be synchronized on the child. Changing from get children here as I want to avoid the array wrapping where I can (make less garbage)*/
        // todo I'm breaking the comment above and could the name data referenes get screwy?
        if (nameData.directSetChildren() != null) {
            for (Name newChild : nameData.directSetChildren()) {
                newChild.addToParents(this, true);
            }
        } else if (nameData.directArrayChildren() != null) {
            // directly hitting the array could cause a problem if it were reassigned while this happened but here it should be fine,
            // loading doesn't alter the array lengths, they're set in preparation.
            for (Name aChildren : nameData.directArrayChildren()) {
                aChildren.addToParents(this, true);
            }
        }
    }

    // first of its kind. Try to be comprehensive
    // want to make it synchronized but I'm calling synchronized functions on other objects. Hmmmmmmmm.
    private static AtomicInteger deleteCount = new AtomicInteger(0);

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
            // the basics are done here in the synchronized block
            getAzquoMemoryDB().removeNameFromDb(this);
            setNeedsDeleting();
            setNeedsPersisting();
            // remove children - this is using the same lock so do it in here
            for (Name child : getChildren()) {
                removeFromChildrenWillBePersisted(child, azquoMemoryDBConnection);
            }
            for (String attribute : nameData.getAttributeKeys()) {
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

    //for the moment this is correct
    @Override
    NameAttributes getRawAttributes() {
        return null;
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
        System.out.println("findTopParentsCount\t\t\t\t" + findTopParentsCount.get());
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
        findTopParentsCount.set(0);
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