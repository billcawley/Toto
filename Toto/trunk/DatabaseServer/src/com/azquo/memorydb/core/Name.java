package com.azquo.memorydb.core;

import com.azquo.memorydb.Constants;
import com.azquo.memorydb.dao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Azquo object, names now have attributes and what was the name (as in the text) is now simply an attribute of the name
 * defined currently in a static below. Names can have parent and child relationships with multiple other names. Sets of names.
 * <p/>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on
 * <p/>
 * <p/>
 * Of course we care about thread safety even if crude but we have another concern now : this object was weighing in at over 2k average in an example magento db
 * the goal is to bring it to sub 500 bytes. THis means in some cases variables being null until used and using arraylists instead of sets switching for performance
 * when the list gets too big.
 * <p/>
 * Update on memory : got it to about 850 bytes (magento example DB). Will park for the mo, further change would probably involve changes to attributes,
 * I mean the name of attributes not being held here for example
 * <p/>
 * attributes case insensitive . . .not entirely happy about this
 * <p/>
 * was comparable but this resulted in a code warning I've moved the comparator to NameService
 * <p/>
 * Having considered CopyOnWriteArrayLists and storing as UnmodifiableList I'm now considering plain arrays internally.
 */
public final class Name extends AzquoMemoryDBEntity {

    public static final String CALCULATION = "CALCULATION";
    public static final String LOCAL = "LOCAL";

    public static final char QUOTE = '`';

    private static final int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets

    // name needs this as it links to itself hence have to load all names THEN parse the json, other objects do not hence it's in here not the memory db entity
    // as mentioned just a cache while the names by id map is being populated

    private String jsonCache;

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

    ok using arrays here has saved about 5% on a database's memory useage. They stay unless there's a big unfixable problem.

    and put the get in here, stops worry about an object switch in the middle of a get

    */
    private static final class NameAttributes {
        private final String[] attributeKeys;
        private final String[] attributeValues;

        public NameAttributes(List<String> attributeKeys, List<String> attributeValues) throws Exception {
            if (attributeKeys.size() != attributeValues.size()){
                throw new Exception("Keys and values for attributes must match!");
            }
            this.attributeKeys = new String[attributeKeys.size()];
            attributeKeys.toArray(this.attributeKeys);
            this.attributeValues = new String[attributeValues.size()];
            attributeValues.toArray(this.attributeValues);
        }

        public NameAttributes()  { // blank default. Fine.
            attributeKeys = new String[0];
            attributeValues = new String[0];
        }

        public List<String> getAttributeKeys(){
            return Arrays.asList(attributeKeys);
        }

        public List<String> getAttributeValues(){
            return Arrays.asList(attributeValues);
        }

        public String getAttribute(String attributeName){
            attributeName = attributeName.toUpperCase();
            int index = getAttributeKeys().indexOf(attributeName);
            if (index != -1) {
                return attributeValues[index];
            }
            return null;
        }

        public Map<String, String> getAsMap(){
            Map<String, String> attributesAsMap = new HashMap<>();
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
    // these 3 (values, parents, peerparents) are for quick lookup, must be modified appropriately e.g.add a peer add to that peer's peer parents
    // to be clear, these are not used when persisting, they are derived from the name sets in values and the two below
    // Sets are expensive in terms of memory, will use arrays instead unless they get big (above threshold 512 at the mo) make a new array and switch on changing to make atomic and always wrap them unmodifiable on get
    // I'm not going to make these volatile as the ony time it really matters is on writes which are synchronized and I understand this deals with memory barriers
    private Set<Value> valuesAsSet;
    private Value[] values;
    private Set<Name> parentsAsSet;
    private Name[] parents;
    private Name[] peerParents;

    // we want thread safe != null test on changes but this should be done by synchronized
    private Set<Name> childrenAsSet;
    private Name[] children;
    // how often is peers used?? - gonna make null by default
    private Map<Name, Boolean> peers;

    // for the code to make new names

    public Name(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, boolean additive) throws Exception {
        this(azquoMemoryDB, 0, null);
        setProvenanceWillBePersisted(provenance);
        setAdditiveWillBePersisted(additive);
    }

    // protected, should only be called by azquo memory db
    // Lists/Sets are not set here as we need to wait then load from the json cache
    // use arrays internally?

    protected Name(final AzquoMemoryDB azquoMemoryDB, int id, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        jsonCache = jsonFromDB;
        additive = true; // by default
        valuesAsSet = null;
        values = new Value[0]; // Utrning these 3 lists to arrays, memory a priority
        parentsAsSet = null;
        parents = new Name[0];
        peerParents = null;
        childrenAsSet = null;
        children = new Name[0];
        peers = null; // keep overhead really low! I'm assuming this won't be used often - gets over the problem of linked hash map being expensive
        nameAttributes = new NameAttributes(); // attributes will nearly always be written over, this is just a placeholder
        getAzquoMemoryDB().addNameToDb(this);
    }

    // for convenience but be careful where it is used . . .
    public String getDefaultDisplayName() {
        return nameAttributes.getAttribute(Constants.DEFAULT_DISPLAY_NAME);
    }

    /* what was this for? Commenting
    public String getDisplayNameForLanguages(List<String> languages) {
        for (String language : languages) {
            String toReturn = getAttribute(language, false, new HashSet<Name>());
            if (toReturn != null) {
                return toReturn;
            }
        }
        return getDefaultDisplayName();
    }*/
    // provenance immutable. If it were not then would need to clone

    public Provenance getProvenance() {
        return provenance;
    }

/*    public boolean getAdditive() {
        return additive;
    }*/

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

    public Collection<Value> getValues() {
        return valuesAsSet != null ? Collections.unmodifiableCollection(valuesAsSet) : Collections.unmodifiableCollection(Arrays.asList(values));
    }

    // added by WFC I guess, need to check on this - Edd
    // this could be non thread safe.

    public void transferValues(Name from) throws Exception {
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
        checkDatabaseMatches(value);
        if (valuesAsSet != null) {
            valuesAsSet.add(value);// Backed by concurrent hash map should be thread safe
        } else {
            synchronized (this) { // syncing changes on this is fine, don't want to on values itself as it's about to be changed - synchronizing on a non final field is asking for trouble
                List<Value> valuesList = Arrays.asList(values);
                if (!valuesList.contains(value)) { // it's this contains expense that means we should stop using arraylists over a certain size
                    // OK, here it may get interesting, what about size???
                    if (valuesList.size() >= ARRAYTHRESHOLD) { // we need to convert. copy the array to a new concurrent hashset then set it
                        valuesAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>());// the way to get a thread safe set!
                        valuesAsSet.addAll(valuesList); // add the existing ones
                        valuesAsSet.add(value);
                        //values = new ArrayList<Value>(); // to save memory, leaving commented as the saving probably isn't that much and I'm a little worried about concurrency.
                        // todo - profile how many of these are left over in, for example, Damart.
                    } else { // ok we have to switch a new one in, need to think of the best way with the new array model
                        // this is synchronized, I should be able to be simple about this and be safe still
                        Value[] newValuesArray = new Value[values.length + 1];
                        System.arraycopy(values, 0, newValuesArray, 0, values.length); // intellij simplified it to this, should be fine
                        newValuesArray[values.length] = value;
                        values = newValuesArray;
                    }
                }
            }
        }
    }

    protected void removeFromValues(final Value value) throws Exception {
        if (valuesAsSet != null) {
            valuesAsSet.remove(value);
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
                }
            }
        }
    }


    public Collection<Name> getParents() {
        return parentsAsSet != null ? Collections.unmodifiableCollection(parentsAsSet) : Collections.unmodifiableCollection(Arrays.asList(parents));
    }

    // note these two should be called in synchronized blocks if acting on things like parents, children etc
    // doesn't check contains, there is logic after the contains when adding which can't go in here (as in are we going to switch to set?)

    private Name[] nameArrayAppend(Name[] source, Name toAppend){
        Name[] newArray = new Name[source.length + 1];
        System.arraycopy(source, 0, newArray, 0, source.length); // intellij simplified it to this, should be fine
        newArray[source.length] = toAppend;
        return newArray;
    }

    // I realise some of this stuff is probably very like the internal workings of ArrayList! Important here to save space with vanilla arrays I'm rolling my own.

    private Name[] nameArrayAppend(Name[] source, Name toAppend, int position){
        if (position >= source.length){
            return  nameArrayAppend(source, toAppend);
        }
        Name[] newArray = new Name[source.length + 1];
        for (int i = 0; i < source.length; i++) { // do one copy skipping the element we want removed
            if (i <= position){
                newArray[i] = source[i];
                if (i == position){
                    newArray[i + 1] = toAppend;
                }
            } else {
                newArray[i + 1] = source[i];
            }
        }
        return newArray;
    }

    // can check contains

    private Name[] nameArrayRemoveIfExists(Name[] source, Name toRemove){
        List<Name> sourceList = Arrays.asList(source);
        if (sourceList.contains(toRemove)) {
            return nameArrayRemove(source, toRemove);
        } else {
            return source;
        }
    }

    // note, assumes it is in there! Otherwise will be an exception

    private Name[] nameArrayRemove(Name[] source, Name toRemove){
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



    // don't allow external classes to set the parents I mean by function or otherwise, Name can manage this based on set children
    // synchronize on parents? It's not final is the thing

    private void addToParents(final Name name) throws Exception {
        if (parentsAsSet != null) {
            parentsAsSet.add(name);
        } else {
            synchronized (this) {
                List<Name> parentsList = Arrays.asList(parents);
                if (!parentsList.contains(name)) {
                    if (parentsList.size() >= ARRAYTHRESHOLD) {
                        parentsAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
                        parentsAsSet.addAll(parentsList);
                        parentsAsSet.add(name);
                        // parents  new arraylist?
                    } else {
                        parents = nameArrayAppend(parents, name);
                    }
                }
            }
        }
    }

    private void removeFromParents(final Name name) throws Exception {
        if (parentsAsSet != null) {
            parentsAsSet.remove(name);
        } else {
            synchronized (this) { // just sync on this object to protect the lists
                parents = nameArrayRemoveIfExists(parents, name);
            }
        }
    }

    // ok for thread safety I need a few functions to manage peer parents. Just a plain sync should be fine for the mo
    // since peerparents is private and only accesed in here synchronized it is safe to change the lists here rather than assign new ones

    private synchronized void addToPeerParents(final Name name) {
        if (peerParents == null) {
            peerParents = new Name[1];
            peerParents[0] = name;
        } else {
            List<Name> peerParentsList = Arrays.asList(peerParents);
            if (!peerParentsList.contains(name)) {
                peerParents = nameArrayAppend(peerParents, name);
            }
        }
    }

    private synchronized void removeFromPeerParents(final Name name) {
        if (peerParents != null) {
            peerParents = nameArrayRemoveIfExists(peerParents, name);
        }
    }


    // returns a collection, I think this is just iterated over to check stuff
    // todo - check use of this and then whether we use sets internally or not
    // these two functions moved here from the spreadsheet

    public Collection<Name> findAllParents() {
        final Set<Name> allParents = new HashSet<>();
        findAllParents(this, allParents);
        return allParents;
    }

    // option for collect call but I don't really get it
    private void findAllParents(Name name, final Set<Name> allParents) {
        for (Name parent : name.getParents()) {
            if (!allParents.contains(parent)) {
                allParents.add(parent);
                findAllParents(parent, allParents);
            }
        }
    }

    // we are now allowing a name to be in more than one top parent, hence the name change

    public Name findATopParent() {
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
    private Set<Name> findAllChildrenCache = null;
    private Set<Name> findAllChildrenPayAttentionToAdditiveCache = null;

    private void findAllChildren(Name name, boolean payAttentionToAdditive, final Set<Name> allChildren) {
        if (payAttentionToAdditive && !name.additive) return;
        for (Name child : name.getChildren()) {
            if (!allChildren.contains(child)) {
                allChildren.add(child);
                findAllChildren(child, payAttentionToAdditive, allChildren);
            }
        }
    }

    public Collection<Name> findAllChildren(boolean payAttentionToAdditive) {
        if (payAttentionToAdditive) {
            if (findAllChildrenPayAttentionToAdditiveCache != null) {
                return Collections.unmodifiableCollection(findAllChildrenPayAttentionToAdditiveCache);
            }
            Set<Name> allChildrenPayAttentionToAdditive = new HashSet<>();
            findAllChildren(this, true, allChildrenPayAttentionToAdditive);
            if (!allChildrenPayAttentionToAdditive.isEmpty()) {
                findAllChildrenPayAttentionToAdditiveCache = allChildrenPayAttentionToAdditive;
            }
            return Collections.unmodifiableCollection(allChildrenPayAttentionToAdditive);
        } else {
            if (findAllChildrenCache != null) {
                return Collections.unmodifiableCollection(findAllChildrenCache);
            }
            Set<Name> allChildren = new HashSet<>();
            findAllChildren(this, false, allChildren);
            if (!allChildren.isEmpty()) {
                findAllChildrenCache = allChildren;
            }
            return Collections.unmodifiableCollection(allChildren);
        }
    }

    public Collection<Name> getChildren() {
        return childrenAsSet != null ? Collections.unmodifiableCollection(childrenAsSet) : Collections.unmodifiableCollection(Arrays.asList(children));
    }

    // might seem inefficient but the adds and removes deal with parents and things. Might reconsider code if used more heavily
    // each add/remove is safe but for predictability best to synchronize the lot here I think
    public synchronized void setChildrenWillBePersisted(Collection<Name> children) throws Exception {
        for (Name oldChild : this.getChildren()) {
            removeFromChildrenWillBePersisted(oldChild);
        }
        for (Name child : children) {
            addChildWillBePersisted(child);
        }
        findAllChildrenCache = null;
        findAllChildrenPayAttentionToAdditiveCache = null;
    }

    // no checks on persistence and parents

    private synchronized void setChildrenNoChecks(LinkedHashSet<Name> children) {
        if (childrenAsSet != null || children.size() > ARRAYTHRESHOLD) { // then overwrite the map . . I just noticed how this could be dangerous in terms of external references. Make a copy.
            this.childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>()); // NOTE! now we're not using linked, position and ordering will be ignored for large sets of children!!
            this.childrenAsSet.addAll(children);
        } else {
            Name[] newChildren = new Name[children.size()];
            children.toArray(newChildren);
            this.children = newChildren;
        }
    }

    public void addChildWillBePersisted(Name child) throws Exception {
        addChildWillBePersisted(child, 0);
    }

    // with position, will just add if none passed note : this sees position as starting at 1!
    // note : modified to do nothing if the name is in the set. No changing of position.

    public void addChildWillBePersisted(Name child, int position) throws Exception {
        checkDatabaseMatches(child);
        if (child.equals(this)) return;//don't put child into itself
        /*removing this check - it takes far too long - maybe should make it optional
        if (findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        */
        // NOTE!! for the set version we're now just using a set backed by a concurrent hash map NOT liked hashset, for large child sets ordering will be ignored!
        // while childrenasaset is thread safe I think I'm going to need to synchronize the lot to make make state more consistent
        synchronized (this) {
            boolean changed = false; // only do the after stuff if something changed
            if (childrenAsSet != null) {
                changed = childrenAsSet.add(child);
            } else {
                List<Name> childrenList = Arrays.asList(children);
                if (!childrenList.contains(child)) {
                    changed = true;
                    // like parents, hope the logic is sound
                    if (childrenList.size() >= ARRAYTHRESHOLD) { // we need to convert. copy the array to a new hashset then set it
                        childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
                        childrenAsSet.addAll(childrenList);
                        childrenAsSet.add(child);
                        // children new arraylist?;
                    } else {
                        if (position != 0) {
                            children = nameArrayAppend(children, child,position);
                        } else {
                            children = nameArrayAppend(children, child);
                        }
                    }
                }
            }
            if (changed) { // new logic, only do these things if something was changed
                findAllChildrenCache = null;
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
    }

    public void removeFromChildrenWillBePersisted(Name name) throws Exception {
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
                findAllChildrenCache = null;
                setNeedsPersisting();
            }
        }
    }

    public Map<Name, Boolean> getPeers() {
        if (peers != null) {
            return Collections.unmodifiableMap(peers);
        }
        Collection<Name> parents = findAllParents();
        for (Name parent : parents) {
            if (parent.peers != null) {
                return Collections.unmodifiableMap(parent.peers);
            }
        }
        return new HashMap<>();
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(nameAttributes.getAsMap());
    }

    public void setPeersWillBePersisted(LinkedHashMap<Name, Boolean> peers) throws Exception {
        // synchronize on the block that affects this object not the whole function or we might have a deadlock (two names setting peers on each other!)
        // I get the existing outside so I can reassign peers in the synchronized block then do other object stuff outside
        Map<Name, Boolean> oldPeers = this.peers;
        synchronized (this) {
            //check if identical to existing
            if (this.peers != null && this.getPeers().size() == peers.size()) { // not null check as it will be on init
                boolean identical = true;
                for (Name peer : this.getPeers().keySet()) {
                    Boolean newAdditive = peers.get(peer);
                    if (newAdditive == null || newAdditive != this.getPeers().get(peer)) {
                        identical = false;
                    }
                }
                if (identical) {
                    return;
                }
            }
            checkDatabaseForSet(peers.keySet());
            for (Name peer : peers.keySet()) {
                if (peer.equals(this)) {
                    throw new Exception("error name cannot be a peer of itself " + this);
                }
            }
            setNeedsPersisting();
            this.peers = peers;
        }

        // now change the references to peer parents, just used for delete really.
        if (oldPeers != null) { // added not null check as it will be on init
            for (Name existingPeer : oldPeers.keySet()) {
                existingPeer.removeFromPeerParents(this);
            }
        }
        // add the adjusted back in back in to the peer parents of the peers
        for (Name existingPeer : this.peers.keySet()) {
            existingPeer.addToPeerParents(this);
        }
    }

/*    // Not entirely clear on usage here, basic thread safety should be ok I think

    public synchronized void setTemporaryAttribute(String attributeName, String attributeValue) throws Exception {
        attributeName = attributeName.toUpperCase();
        // ok, I am assuming nameAttribute will ONLY be assigned in code synchronized on this object
        // that is to say I'm assuming the reference won't change over the next two lines
        List<String> attributeKeys = new ArrayList<String>(nameAttributes.attributeKeys);
        List<String> attributeValues = new ArrayList<String>(nameAttributes.attributeValues);
        int index = attributeKeys.indexOf(attributeName);
        if (index != -1) {
            attributeValues.remove(index);
            attributeValues.add(index, attributeValue);
        } else {
            attributeKeys.add(attributeName);
            attributeValues.add(attributeValue);
        }
        // yes I know this is a few array copies in a row (this constructor copies), trying to make things as safe as possible
        // if there's a big overhead will have to revisit I suppose
        nameAttributes = new NameAttributes(attributeKeys, attributeValues);

//        attributes.put(attributeName, attributeValue);
        getAzquoMemoryDB().addNameToAttributeNameMap(this); // will overwrite but that's fine

    }*/

    // todo - be sure this makes sense given new name attributes
    // I think plain old synchronized here is safe enough if not that fast

    public synchronized void setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {
        attributeName = attributeName.toUpperCase();
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

    public synchronized void removeAttributeWillBePersisted(String attributeName) throws Exception {
        attributeName = attributeName.toUpperCase();
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
    public synchronized void clearAttributes() throws Exception {
        for (String attribute : nameAttributes.attributeKeys) { // nameAttributes will be reassigned by the function but that should be ok, hang onto the key set as it was at the beginning
            removeAttributeWillBePersisted(attribute);
        }
    }

    private String findParentAttributes(Name child, String attributeName, Set<Name> checked) {
        attributeName = attributeName.toUpperCase();
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

    public String getAttribute(String attributeName) {
        return getAttribute(attributeName, true, new HashSet<>());
    }


    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked) {
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

/*  peers needs to be as values children etc, reassigned as a new LinkedHashMap in a synchronized block. Deal with the other objects out side of this block to avoid deadlocks
            of course the state of this object and its external ones will not be atomic
            To clarify - since peers is returned I either need to copy on set or get. I choose on set and make it unmodifiable
            */


    public void removeFromPeersWillBePersisted(Name name) throws Exception {
        synchronized (this) {
            if (peers != null) {
                checkDatabaseMatches(name);// even if not needed throw the damn exception!
                LinkedHashMap<Name, Boolean> newPeers = new LinkedHashMap<>(peers);
                newPeers.remove(name);
                peers = Collections.unmodifiableMap(newPeers);
                setNeedsPersisting();
            }
        }
        name.removeFromPeerParents(this);
    }


    // for Jackson mapping, trying to attach to actual fields would be dangerous in terms of allowing unsafe access
    // think important to use a linked hash map to preserve order.
    private static class JsonTransport {
        public int provenanceId;
        public boolean additive;
        public Map<String, String> attributes;
        public LinkedHashMap<Integer, Boolean> peerIds;
        public LinkedHashSet<Integer> childrenIds;

        @JsonCreator
        public JsonTransport(@JsonProperty("provenanceId") int provenanceId
                , @JsonProperty("additive") boolean additive
                , @JsonProperty("attributes") Map<String, String> attributes
                , @JsonProperty("peerIds") LinkedHashMap<Integer, Boolean> peerIds
                , @JsonProperty("childrenIds") LinkedHashSet<Integer> childrenIds) {
            this.provenanceId = provenanceId;
            this.additive = additive;
            this.attributes = attributes;
            this.peerIds = peerIds;
            this.childrenIds = childrenIds;
        }
    }

    @Override
    protected String getPersistTable() {
        return StandardDAO.PersistedTable.name.name();
    }

    @Override
    public String getAsJson() {
        LinkedHashMap<Integer, Boolean> peerIds = new LinkedHashMap<>();
        if (peers != null) {
            for (Name peer : peers.keySet()) {
                peerIds.put(peer.getId(), peers.get(peer));
            }
        }
        // yes could probably use list but lets match collection types, that is to say unique members in the transport though this may have performance implications
        LinkedHashSet<Integer> childrenIds = getChildren().stream().map(Name::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(), additive, getAttributes(), peerIds, childrenIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
    /*
    ints to a string example. Could halve the size of some big names although would I need to change to ISO in the DB? utf can support this but it will not be as efficient as it could be
        int[] testInts = new int[6];
        testInts[0] = 123235;
        testInts[1] = 0;
        testInts[2] = 6543823;
        testInts[3] = 8353495;
        testInts[4] = 7354056;
        testInts[5] = 78;
        ByteBuffer byteBuffer = ByteBuffer.allocate(testInts.length * 4);
        for (int i = 0; i < testInts.length; i++){
            byteBuffer.putInt(testInts[i]);
        }
        String asString = new String(byteBuffer.array(), StandardCharsets.ISO_8859_1);
        System.out.println("byte test : " + asString);
        // now to convert back?
        byte[] fromString = asString.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer anotherByteBuffer = ByteBuffer.wrap(fromString);
        int index = 0;
        while (index < anotherByteBuffer.capacity()){
            System.out.println("int : " + anotherByteBuffer.getInt(index));
            index += 4;
        }

     */

    // protected to only be used by the database loading, can't be called in the constructor as name by id maps may not be populated
    // changing synchronized to only relevant portions
    // note : this function is absolutely hammered while "linking" so optimiseations here are helpful

    protected void populateFromJson() throws Exception {
        if (getAzquoMemoryDB().getNeedsLoading() || jsonCache != null) { // only acceptable if we have json and it's during the loading process
            try {
                // if I define this inside the synchronized block then it won't be visible outside, the add to parents will act on the sometimes empty choldren class variable
                // need to think clearly about what needs synchronization. Given how it is used need it be synchronized at all? Does it matter that much to performance?
                LinkedHashSet<Name> children = new LinkedHashSet<>();
                synchronized (this) {
                    JsonTransport transport = jacksonMapper.readValue(jsonCache, JsonTransport.class);
                    jsonCache = null;// free the memory
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
                    LinkedHashMap<Integer, Boolean> peerIds = transport.peerIds;
                    if (!peerIds.isEmpty()) {
                        peers = new LinkedHashMap<>();
                    }
                    for (Integer peerId : peerIds.keySet()) {
                        peers.put(getAzquoMemoryDB().getNameById(peerId), peerIds.get(peerId));
                    }
                    LinkedHashSet<Integer> childrenIds = transport.childrenIds;
                    for (Integer childId : childrenIds) {
                        children.add(getAzquoMemoryDB().getNameById(childId));
                    }

                    // what we're doign here is the same as setchildrenwillbepersisted but without checks as during loading conditions may not be met
                    // now a low level function that just checks the size for where to put the data
                    setChildrenNoChecks(children);
                }
                // need to sort out the parents - I deliberately excluded this from synchronizeation or one could theroetically hit a deadlock
                // addToParents can be synchronized on the child
                for (Name newChild : children) {
                    newChild.addToParents(this);
                }
            } catch (IOException e) {
                System.out.println("jsoncache = " + jsonCache);
                e.printStackTrace();
            }
        }
    }

    // first of its kind. Try to be comprehensive
    // want to make it synchronized but I'm calling synchronized functions on other objects. Hmmmmmmmm.

    public void delete() throws Exception {
        Collection<Value> values;
        Collection<Name> parents;
        List<Name> peerParents;

        // ok we want a thread safe snapshot really of things that are synchronized on other objects
        // peer parents is a bit different in that it can be null and is modified internally so to have a safe iterator I need a copy
        synchronized (this) {
            values = getValues();
            parents = getParents();
            peerParents = this.peerParents == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(this.peerParents));
            // the basics are done here in the synchronized block
            getAzquoMemoryDB().removeNameFromDb(this);
            needsDeleting = true;
            setNeedsPersisting();
            // remove children - this is using the same lock so do it in here
            for (Name child : getChildren()) {
                removeFromChildrenWillBePersisted(child);
            }
        }
        // then the actions on other objects
        // remove from values - this will change when value is changed to make more safe
        for (Value v : values) {
            Set<Name> namesForValue = new HashSet<>(v.getNames());
            namesForValue.remove(this);
            v.setNamesWillBePersisted(namesForValue);
        }
        // remove from parents
        for (Name parent : parents) {
            parent.removeFromChildrenWillBePersisted(this);
        }
        // peers - new lookup to use
        for (Name peerParent : peerParents) {
            peerParent.removeFromPeersWillBePersisted(this);
        }
    }
}