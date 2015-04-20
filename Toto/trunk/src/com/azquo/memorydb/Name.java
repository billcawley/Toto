package com.azquo.memorydb;

import com.azquo.memorydb.dao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
 * I mean the name attributes not being held here for example
 * <p/>
 * attributes case insensitive . . .not entirely happy about this
 * <p/>
 * was comparable but this resulted in a code warning I've moved the comparator to NameService
 */
public final class Name extends AzquoMemoryDBEntity {

    public static final String DEFAULT_DISPLAY_NAME = "DEFAULT_DISPLAY_NAME";
    public static final String CALCULATION = "CALCULATION";
    public static final String ANON = "ANON";
    public static final String LOCAL = "LOCAL";

    public static final char QUOTE = '`';

    private static final int ARRAYLISTTHRESHOLD = 512; // if lists which need distinct members hit above this switch to sets

    // name needs this as it links to itself hence have to load all names THEN parse the json, other objects do not hence it's in here not the memory db entity
    // as mentioned just a cache while the names by id map is being populated

    private String jsonCache;

//    private static final Logger logger = Logger.getLogger(Name.class);

    private Provenance provenance;
    private boolean additive;

    /* going to try for attributes as two arraylists as this should save a lot of space vs a linked hash map
    that is to say one makes a new one of these when updating attributes and then switch it in. Hence atomic (but not necessarily visible!) switch of two arraylists
    if an object reference is out of date it will at least be two consistent arrays

    I have not problem with out of date versions of nameAttributes as the JVM makes things visible to other threads as long as object contents remain consistent.

    According to what I've read I believe this object (NameAttributes) to be thread safe / Immutable.

    */
    private static final class NameAttributes {
        public final List<String> attributeKeys;
        public final List<String> attributeValues;

        private NameAttributes(List<String> attributeKeys, List<String> attributeValues) {
            this.attributeKeys = Collections.unmodifiableList(new ArrayList<String>(attributeKeys)); // copy and unmodifiable
            this.attributeValues = Collections.unmodifiableList(new ArrayList<String>(attributeValues));
        }
    }

    private NameAttributes nameAttributes;

    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo
    // these 3 (values, parents, peerparents) are for quick lookup, must be modified appropriately e.g.add a peer add to that peer's peer parents
    // to be clear, these are not used when persisting, they are derived from the name sets in values and the two below
    // Sets are expensive in terms of memory, will use immutable lists instead unless they get really big
    // I'm not going to make these volatile as the ony time it really matters is on writes which are synchronized and I understand this deals with memory barriers
    private Set<Value> valuesAsSet;
    private List<Value> values;
    private Set<Name> parentsAsSet;
    private List<Name> parents;
    private List<Name> peerParents;

    // we want thread safe != null test on changes but this should be done by synchronized
    private Set<Name> childrenAsSet;
    private List<Name> children;
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

    protected Name(final AzquoMemoryDB azquoMemoryDB, int id, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        jsonCache = jsonFromDB;
        additive = true; // by default
        valuesAsSet = null;
        values = Collections.unmodifiableList(new ArrayList<Value>(0)); // overhead low and unmodifiable!
        parentsAsSet = null;
        parents = Collections.unmodifiableList(new ArrayList<Name>(0));
        peerParents = null;
        childrenAsSet = null;
        children = Collections.unmodifiableList(new ArrayList<Name>(0));
        peers = null; // keep overhead really low! I'm assuming this won't be used often - gets over the problem of linked hash map being expensive
        // ok attributes are different as the lists are not made available externally, they can be modified
        nameAttributes = new NameAttributes(new ArrayList<String>(0), new ArrayList<String>(0)); // attributes will nearly always be written over, this is just a placeholder
        getAzquoMemoryDB().addNameToDb(this);
    }

    // for convenience but be careful where it is used . . .
    public String getDefaultDisplayName() {
        int index = nameAttributes.attributeKeys.indexOf(DEFAULT_DISPLAY_NAME);
        if (index != -1) {
            return nameAttributes.attributeValues.get(index);
        }

        return null;
    }

    // for convenience but be careful where it is used . . .
    public String getDisplayNameForLanguages(List<String> languages)
    {
        for (String language : languages){
            String toReturn = getAttribute(language, false, new HashSet<Name>());
            if (toReturn != null){
                return toReturn;
            }
        }
        return getDefaultDisplayName();
    }
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
                "id='" + getId() + '\'' +
                "attributes='" + nameAttributes.attributeKeys + '\'' +
                "attribute values='" + nameAttributes.attributeValues + '\'' +
                '}';
    }

    // we assume sets are built on concurrent hash map and lists are not modifiable
    // even if based on concurrent hash map we don't want anything external modifying it, make the set unmodifiable

    public Collection<Value> getValues() {
        return valuesAsSet != null ? Collections.unmodifiableCollection(valuesAsSet) : values;
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
                if (!values.contains(value)) { // it's this contains expense that means we should stop using arraylists over a certain size
                    // OK, here it may get interesting, what about size???
                    if (values.size() >= ARRAYLISTTHRESHOLD) { // we need to convert. copy the array to a new concurrent hashset then set it
                        valuesAsSet = Collections.newSetFromMap(new ConcurrentHashMap<Value, Boolean>());// the way to get a thread safe set!
                        valuesAsSet.addAll(values); // add the existing ones
                        valuesAsSet.add(value);
                        //values = new ArrayList<Value>(); // to save memory, leaving commented as the saving probably isn't that much and I'm a little worried about concurrency.
                    } else { // ok we have to switch a new one in
                        // note : the key here is to have thread safety on these lists, this seems the best way to do it
                        ArrayList<Value> newValues = new ArrayList<Value>(values);
                        newValues.add(value);
                        values = Collections.unmodifiableList(newValues);
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
                if (values.contains(value)) {
                    ArrayList<Value> newValues = new ArrayList<Value>(values);
                    newValues.remove(value);
                    values = Collections.unmodifiableList(newValues);
                }
            }
        }
    }


    public Collection<Name> getParents() {
        return parentsAsSet != null ? Collections.unmodifiableCollection(parentsAsSet) : parents;
    }

    // don't allow external classes to set the parents I mean by function or otherwise, Name can manage this based on set children
    // before could just edit the parents as I pleased now I can't need getters and setters based on the map

    private void addToParents(final Name name) throws Exception {
        if (parentsAsSet != null) {
            parentsAsSet.add(name);
        } else {
            synchronized (this) {
                if (!parents.contains(name)) {
                    if (parents.size() >= ARRAYLISTTHRESHOLD) {
                        parentsAsSet = Collections.newSetFromMap(new ConcurrentHashMap<Name, Boolean>());
                        parentsAsSet.addAll(parents);
                        parentsAsSet.add(name);
                        // parents  new arraylist?
                    } else {
                        ArrayList<Name> newParents = new ArrayList<Name>(parents);
                        newParents.add(name);
                        parents = Collections.unmodifiableList(newParents);
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
                if (parents.contains(name)) {
                    ArrayList<Name> newParents = new ArrayList<Name>(parents);
                    newParents.remove(name);
                    parents = Collections.unmodifiableList(newParents);
                }
            }
        }
    }

    // ok for thread safety I need a few functions to manage peer parents. Just a plain sync should be fine for the mo
    // since peerparents is private and only accesed in here synchronized it is safe to change the lists here rather than assign new ones

    private synchronized void addToPeerParents(final Name name) {
        if (peerParents == null) {
            peerParents = new ArrayList<Name>();
        }
        if (!peerParents.contains(name)) {
            peerParents.add(name);
        }
    }

    private synchronized void removeFromPeerParents(final Name name) {
        if (peerParents != null) {
            peerParents.remove(name);
        }
    }


    // returns a collection, I think this is just iterated over to check stuff
    // todo - check use of this and then whether we use sets internally or not
    // these two functions moved here from the spreadsheet

    public Collection<Name> findAllParents() {
        final Set<Name> allParents = new HashSet<Name>();
        findAllParents(this, allParents);
        return allParents;
    }

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
            Set<Name> allChildrenPayAttentionToAdditive = new HashSet<Name>();
            findAllChildren(this, true, allChildrenPayAttentionToAdditive);
            if (!allChildrenPayAttentionToAdditive.isEmpty()) {
                findAllChildrenPayAttentionToAdditiveCache = allChildrenPayAttentionToAdditive;
            }
            return Collections.unmodifiableCollection(allChildrenPayAttentionToAdditive);
        } else {
            if (findAllChildrenCache != null) {
                return Collections.unmodifiableCollection(findAllChildrenCache);
            }
            Set<Name> allChildren = new HashSet<Name>();
            findAllChildren(this, false, allChildren);
            if (!allChildren.isEmpty()) {
                findAllChildrenCache = allChildren;
            }
            return Collections.unmodifiableCollection(allChildren);
        }
    }

    public Collection<Name> getChildren() {
        return childrenAsSet != null ? Collections.unmodifiableCollection(childrenAsSet) : children;
    }

    // might seem inefficient but the adds and removes deal with parents and things. Might reconsider code if used more heavily
    // each add/remove is safe but for predictability best to synchronize the lot here I think
    public synchronized void setChildrenWillBePersisted(List<Name> children) throws Exception {
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
        if (childrenAsSet != null || children.size() > ARRAYLISTTHRESHOLD) { // then overwrite the map . . I just noticed how this could be dangerous in terms of external references. Make a copy.
            this.childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<Name, Boolean>()); // NOTE! now we're not using linked, position and ordering will be ignored for large sets of children!!
            this.childrenAsSet.addAll(children);
        } else {
            this.children = Collections.unmodifiableList(new ArrayList<Name>(children));
        }
    }

    public void addChildWillBePersisted(Name child) throws Exception {
        addChildWillBePersisted(child, 0);
    }

    // with position, will just add if none passed note : this sees position as starting at 1!

    public void addChildWillBePersisted(Name child, int position) throws Exception {
        checkDatabaseMatches(child);
        if (child.equals(this) || findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        // NOTE!! for the set version we're now just using a set backed by a concurrent hash map NOT liked hashset, for large child sets ordering will be ignored!
        // while childrenasaset is thread safe I think I'm going to need to synchronize the lot to make make state more consistent
        synchronized (this) {
            if (childrenAsSet != null) {
                childrenAsSet.add(child);
            } else {
                if (!children.contains(child)) {
                    // like parents, hope the logic is sound
                    if (children.size() >= ARRAYLISTTHRESHOLD) { // we need to convert. copy the array to a new hashset then set it
                        childrenAsSet = Collections.newSetFromMap(new ConcurrentHashMap<Name, Boolean>());
                        childrenAsSet.addAll(children);
                        childrenAsSet.add(child);
                        // children new arraylist?;
                    } else {
                        ArrayList<Name> newChildren = new ArrayList<Name>(children);
                        if (position != 0 && position <= newChildren.size()) {
                            newChildren.add(position - 1, child);
                        } else {
                            newChildren.add(child);
                        }
                        children = Collections.unmodifiableList(newChildren);
                    }
                }
            }
            findAllChildrenCache = null;
            child.addToParents(this);//synchronized internally with this also so will not deadlock
            setNeedsPersisting();
            //and check that there are not indirect connections which should be deleted (e.g. if London exists in UK and Europe, and we are now
            //specifying that UK is in Europe, we must remove the direct link from London to Europe
            for (Name descendant : child.findAllChildren(false)) {
                if (getChildren().contains(descendant)) {
                    removeFromChildrenWillBePersisted(descendant);
                }
            }
        }
    }

    public void removeFromChildrenWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the exception!
        // maybe could narrow this a little?
        synchronized (this) {
            if (getChildren().contains(name)) { // then something to remove
                name.removeFromParents(this);
                //don't allow names that have previously had parents to fall out of topparent set
                if (name.getParents().size() == 0) {
                    this.findATopParent().addChildWillBePersisted(name);//revert to top parent (Note that, if 'this' is top parent, then it will be re-instated!
                }

                if (childrenAsSet != null) {
                    childrenAsSet.remove(name);
                } else {
                    ArrayList<Name> newChildren = new ArrayList<Name>(children);
                    newChildren.remove(name);
                    children = Collections.unmodifiableList(newChildren);
                }
                findAllChildrenCache = null;
                setNeedsPersisting();
            }
        }
    }

    public Map<Name, Boolean> getPeers() {
        if (peers!=null){
            return Collections.unmodifiableMap(peers);
        }
        Collection<Name> parents = findAllParents();
        for (Name parent:parents){
            if (parent.peers !=null){
                return Collections.unmodifiableMap(parent.peers);
            }
        }
        return new HashMap<Name, Boolean>();
    }

    public Map<String, String> getAttributes() {
        Map<String, String> attributesAsMap = new HashMap<String, String>();
        int count = 0;
        NameAttributes nameAttributes = this.nameAttributes;// grab a reference in case it changes
        for (String key : nameAttributes.attributeKeys) {
            attributesAsMap.put(key, nameAttributes.attributeValues.get(count));
            count++;
        }
        return Collections.unmodifiableMap(attributesAsMap);
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

    // Not entirely clear on usage here, basic thread safety should be ok I think

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

    }

    // todo - addname to attribute map . . . not efficient?
    // I think plain old synchronized here is safe enough if not that fast

    public synchronized String setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {
        attributeName = attributeName.toUpperCase();
        // important, manage persistence, allowed name rules, db look ups
        // only care about ones in this set
        // code adapted from map based code to lists, may need rewriting
        // again assume nameAttributes reference only set in code synchronized on this block
        List<String> attributeKeys = new ArrayList<String>(nameAttributes.attributeKeys);
        List<String> attributeValues = new ArrayList<String>(nameAttributes.attributeValues);

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
            return "";
        }
        if (existing != null && existing.equals(attributeValue)) {
            return "";
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
        return "";
    }

    public synchronized void removeAttributeWillBePersisted(String attributeName) throws Exception {
        attributeName = attributeName.toUpperCase();
        int index = nameAttributes.attributeKeys.indexOf(attributeName);
        if (index != -1) {
            List<String> attributeKeys = new ArrayList<String>(nameAttributes.attributeKeys);
            List<String> attributeValues = new ArrayList<String>(nameAttributes.attributeValues);
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
            if (!checked.contains(parent)){
                checked.add(parent);
                if (parent.getDefaultDisplayName().equalsIgnoreCase(attributeName)) {
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
        return getAttribute(attributeName, true, new HashSet<Name>());
    }


    public String getAttribute(String attributeName, boolean parentCheck, Set<Name> checked) {
        attributeName = attributeName.toUpperCase();
        String attribute = null;
        NameAttributes nameAttributes = this.nameAttributes;// grab a reference in case it changes
        int index = nameAttributes.attributeKeys.indexOf(attributeName);
        if (index != -1) {
            attribute = nameAttributes.attributeValues.get(index);
        }
        if (attribute != null) return attribute;
        //look up the chain for any parent with the attribute
        if (parentCheck){
            return findParentAttributes(this, attributeName, checked);
        }
        return null;
    }

    public boolean hasInParentTree(final Name testParent) {
        for (Name parent : getParents()) {
            if (testParent == parent || parent.hasInParentTree(testParent)) {
                return true;
            }
        }
        return false;
    }

/*  peers needs to be as values children etc, reassigned as a new LinkedHashMap in a synchronized block. Deal with the other objects out side of this block to avoid deadlocks
            of course the state of this object and its external ones will not be atomic
            To clarify - since peers is returned I either need to copy on set or get. I choose on set and make it unmodifiable
            */


    public void removeFromPeersWillBePersisted(Name name) throws Exception {
        synchronized (this) {
            if (peers != null) {
                checkDatabaseMatches(name);// even if not needed throw the damn exception!
                LinkedHashMap<Name, Boolean> newPeers = new LinkedHashMap<Name, Boolean>(peers);
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
        LinkedHashMap<Integer, Boolean> peerIds = new LinkedHashMap<Integer, Boolean>();
        if (peers != null) {
            for (Name peer : peers.keySet()) {
                peerIds.put(peer.getId(), peers.get(peer));
            }
        }
        // yes could probably use list but lets match collection types . . .
        LinkedHashSet<Integer> childrenIds = new LinkedHashSet<Integer>();
        for (Name child : getChildren()) {
            childrenIds.add(child.getId());
        }
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(), additive, getAttributes(), peerIds, childrenIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // protected to only be used by the database loading, can't be called in the constructor as name by id maps may not be populated

    protected synchronized void populateFromJson() throws Exception {
        if (getAzquoMemoryDB().getNeedsLoading() || jsonCache != null) { // only acceptable if we have json and it's during the loading process
            try {
                JsonTransport transport = jacksonMapper.readValue(jsonCache, JsonTransport.class);
                jsonCache = null;// free the memory
                this.provenance = getAzquoMemoryDB().getProvenanceById(transport.provenanceId);
                this.additive = transport.additive;
                //this.attributes = transport.attributes;
                List<String> attributeKeys = new ArrayList<String>();
                List<String> attributeValues = new ArrayList<String>();
                for (String key : transport.attributes.keySet()) {
                    attributeKeys.add(key.toUpperCase());
                    attributeValues.add(transport.attributes.get(key));
                }
                nameAttributes = new NameAttributes(attributeKeys, attributeValues);
                LinkedHashMap<Integer, Boolean> peerIds = transport.peerIds;
                if (!peerIds.isEmpty()) {
                    peers = new LinkedHashMap<Name, Boolean>();
                }
                for (Integer peerId : peerIds.keySet()) {
                    peers.put(getAzquoMemoryDB().getNameById(peerId), peerIds.get(peerId));
                }
                LinkedHashSet<Name> children = new LinkedHashSet<Name>();
                LinkedHashSet<Integer> childrenIds = transport.childrenIds;
                for (Integer childId : childrenIds) {
                    children.add(getAzquoMemoryDB().getNameById(childId));
                }

                // what we're doign here is the same as setchildrenwillbepersisted but without checks as during loading conditions may not be met
                // now a low level function that just checks the size for where to put the data
                //
                setChildrenNoChecks(children);
                // need to sort out the parents
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
            peerParents = this.peerParents == null ? new ArrayList<Name>() : new ArrayList<Name>(this.peerParents);
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
            Set<Name> namesForValue = v.getNames();
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