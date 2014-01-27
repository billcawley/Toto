package com.azquo.toto.memorydb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Toto object, names now have attributes and what was the name is now simply an attribute of the name, defined currently in a static below. Names can have parent and child relationships with multiple
 * other names. Sets of names. Used to be called label, there may be remnants of this in the code.
 * <p/>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on
 * <p/>
 * ok I've been trying to make this class more thread safe with syncronized, not sure if I'm using it in the best way.
 * <p/>
 * Should I  be using synchronized hash sets e.g.     Set s = Collections.synchronizedSet(new HashSet(...)); ?? Need to read up on concurrency.
 * Brain metling stuff!
 */
public final class Name extends TotoMemoryDBEntity implements Comparable<Name> {

    public static final String DEFAULT_DISPLAY_NAME = "DEFAULT_DISPLAY_NAME";

    // name needs this as it links to itself hence have to load all names THEN parse the json, other objects do not hence it's in here not the memory db entity
    // as mentioned just a cache while the names by id map is being populated

    private String jsonCache;

    // leaving here as a reminder to consider proper logging
    //private static final Logger logger = Logger.getLogger(Name.class.getName());
    // data fields
    private Provenance provenance;
    private boolean additive;
    private Map<String, String> attributes;

    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo

    // ok they're all sets, but some need ordering :)
    // these 3 are for quick lookup, must be modified appropriately e.g.add a peer add to that peer's peer parents
    private Set<Value> values;
    private Set<Name> parents;
    private Set<Name> peerParents;
    /* these two have position which we'll reflect by the place in the list. WHen modifying these sets one has to recreate teh set anyway
    and when doing so changes to the order are taken into account.

    Peers is going to become a linked hash map as opposed to set as I need to store whether they are additive or not and this cannot be against the
     name object. A name may be an additive peer in one scenario and not in another.
     */
    private LinkedHashSet<Name> children;
    private LinkedHashMap<Name, Boolean> peers;

    // for the code to make enw names

    public Name(final TotoMemoryDB totoMemoryDB, final Provenance provenance, boolean additive) throws Exception {
        this(totoMemoryDB, 0, null);
        setProvenanceWillBePersisted(provenance);
        setAdditiveWillBePersisted(additive);
    }

    // protected, should only be called by totomemory db
    // maps are not set here as we need to wait then load from the json cache

    protected Name(final TotoMemoryDB totoMemoryDB, int id, String jsonFromDB) throws Exception {
        super(totoMemoryDB, id);
        jsonCache = jsonFromDB;
        additive = true; // by default
        values = new HashSet<Value>();
        parents = new HashSet<Name>();
        peerParents = new HashSet<Name>();
        children = new LinkedHashSet<Name>();
        peers = new LinkedHashMap<Name, Boolean>();
        attributes = new LinkedHashMap<String, String>();
        getTotoMemoryDB().addNameToDb(this);
    }

    @Override
    protected void setNeedsPersisting() {
        getTotoMemoryDB().setNameNeedsPersisting(this);
    }

    @Override
    protected void classSpecificSetAsPersisted() {
        getTotoMemoryDB().removeNameNeedsPersisting(this);
    }

    // for convenience but be careful where it is used . . .

    public String getDefaultDisplayName() {
        return getAttribute(DEFAULT_DISPLAY_NAME);
    }

    // should I be cloning provenance? Not while it's immutable I don't think

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
                "id='" + getId() + '\'' +
                "attributes='" + attributes + '\'' +
                '}';
    }

    public Set<Value> getValues() {
        return Collections.unmodifiableSet(values);
    }

    // these two are becoming protected so they can be set by Value.
    // Value will be the reference point for the value name link, the ones here are for fast lookup, no need for persistence

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better to allow it to be changed but not externally

    // syncronized here along with Collections.unmodifiableSet should provide some basic thread safety.

    protected void addToValues(final Value value) throws Exception {
        checkDatabaseMatches(value);
        synchronized (values) {
            values.add(value);
        }
    }

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better to allow it to be changed but not externally

    protected void removeFromValues(final Value value) throws Exception {
        checkDatabaseMatches(value);// even if not needed throw the damn exception!
        synchronized (values) {
            values.remove(value);
        }
    }

    public Set<Name> getParents() {
        return Collections.unmodifiableSet(parents);
    }

    public Set<Name> getPeerParents() {
        return Collections.unmodifiableSet(peerParents);
    }


    // don't allow external classes to set the parents, Name can manage this based on set children

    /* ok I can return a linked hash set but I'm not sure oof the advantage

        return (LinkedHashSet<Name>)Collections.unmodifiableSet(children);
    leave as set for the mo

       */


    // returns a list as I don't think we care about duplicates here
    // these two functions moved here from the service


    public List<Name> findAllParents() {
        final List<Name> allParents = new ArrayList<Name>();
        Set<Name> foundAtCurrentLevel = parents;
        while (!foundAtCurrentLevel.isEmpty()) {
            allParents.addAll(foundAtCurrentLevel);
            final Set<Name> nextLevelSet = new HashSet<Name>();
            for (Name n : foundAtCurrentLevel) {
                nextLevelSet.addAll(n.getParents());
            }
            if (nextLevelSet.isEmpty()) { // no more parents to find
                break;
            }
            foundAtCurrentLevel = nextLevelSet;
        }
        return allParents;
    }

    // since we're not going to allow a name to exist in two top "root" names one should be able to just get the first from parent lists and get there

    public Name findTopParent() {
        if (parents.size() > 0) {
            Name parent = parents.iterator().next();
            while (parent != null) {
                if (parent.getParents().size() > 0) {
                    parent = parent.getParents().iterator().next();
                } else {
                    return parent; // it has no parents, must be top
                }
            }
        }
        //WFC amendment - used to be 'null' below
        return this;
    }

    // same logic as above but returns a set, should be correct

    private Set<Name> findAllChildrenCache = null;
    private Set<Name> findAllChildrenPayAttentionToAdditiveCache = null;

    public Set<Name> findAllChildren(boolean payAttentionToAdditive) {
        if (payAttentionToAdditive) {
            if (findAllChildrenPayAttentionToAdditiveCache != null) {
                return findAllChildrenPayAttentionToAdditiveCache;
            }
            findAllChildrenPayAttentionToAdditiveCache = new HashSet<Name>();
            Set<Name> foundAtCurrentLevel = children;
            if (!additive) { // stop it at the first hurdle
                foundAtCurrentLevel = new HashSet<Name>();
            }
            while (!foundAtCurrentLevel.isEmpty()) {
                findAllChildrenPayAttentionToAdditiveCache.addAll(foundAtCurrentLevel);
                final Set<Name> nextLevelSet = new HashSet<Name>();
                for (Name n : foundAtCurrentLevel) {
                    if (n.additive) {
                        nextLevelSet.addAll(n.getChildren());
                    }
                }
                if (nextLevelSet.isEmpty()) { // no more parents to find
                    break;
                }
                foundAtCurrentLevel = nextLevelSet;
            }
            return findAllChildrenPayAttentionToAdditiveCache;
        } else {
            if (findAllChildrenCache != null) {
                return findAllChildrenCache;
            }
            findAllChildrenCache = new HashSet<Name>();
            Set<Name> foundAtCurrentLevel = children;
            while (!foundAtCurrentLevel.isEmpty()) {
                findAllChildrenCache.addAll(foundAtCurrentLevel);
                Set<Name> nextLevelSet = new HashSet<Name>();
                for (Name n : foundAtCurrentLevel) {
                    nextLevelSet.addAll(n.getChildren());
                }
                if (nextLevelSet.isEmpty()) { // no more parents to find
                    break;
                }
                foundAtCurrentLevel = nextLevelSet;
            }
            return findAllChildrenCache;
        }
    }


    public Set<Name> getChildren() {
        return Collections.unmodifiableSet(children);
    }
    // as mentioned above, force the set to a linked set, we want it to be ordered :)

    public synchronized void setChildrenWillBePersisted(LinkedHashSet<Name> children) throws Exception {
        checkDatabaseForSet(children);

        // check for circular references
        for (Name newChild : children) {
            if (newChild.equals(this) || findAllParents().contains(newChild)) {
                throw new Exception("error cannot assign child due to circular reference, " + newChild + " cannot be added to " + this);
            }
            // now we need to check the top parent is not changing ont eh child
            if (newChild.getParents().size() > 0) { // it has parents so we need to check
                if (!newChild.findTopParent().equals(this) && parents.size() > 0 && !newChild.findTopParent().equals(findTopParent())) {
                    throw new Exception("error cannot assign child as it has a different top parent" + newChild + " has top parent " + newChild.findTopParent() + " " + this + " has or is a different top parent");
                }
            }
        }

        // remove all parents on the old one
        for (Name oldChild : this.children) {
            // need to stop concurrent modification of the object I'm directly modifying. Maybe this should be vis a setter?, what if this function is called simultaneously and an object is assigned two parents at the same time?
            synchronized (oldChild.parents) { // may as well be specific, less chance of locking, we just want to alter the parents
                oldChild.parents.remove(this);
            }
        }

        this.children = children;
        findAllChildrenCache = null;
        // set the parents based of the children

        // set all parents on the new list
        for (Name newChild : this.children) {
            synchronized (newChild.parents) {
                newChild.parents.add(this);
            }
        }

        setNeedsPersisting();

    }

    public void addChildWillBePersisted(Name child) throws Exception {
        addChildWillBePersisted(child, 0);
    }

    // with position, will just add if none passed
    // note : this sees position as starting at 1!

    public synchronized void addChildWillBePersisted(Name child, int position) throws Exception {
        checkDatabaseMatches(child);
        if (child.equals(this) || findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        // now we need to check the top parent is not changing ont eh child
        if (child.getParents().size() > 0) { // it has parents so we need to check
            if (!child.findTopParent().equals(this) && parents.size() > 0 && !child.findTopParent().equals(findTopParent())) {
                throw new Exception("error cannot assign child as it has a different top parent" + child + " has top parent " + child.findTopParent() + " " + this + " has or is a different top parent");
            }
        }

        if (position == 0 || (position > children.size() && !children.contains(child))) { // no position or it's off the end and the child isn't in there already
            if (children.add(child)) { // something actually changed :)
                findAllChildrenCache = null;
                synchronized (child.parents) {
                    child.parents.add(this);
                }
                setNeedsPersisting();
            }
        } else { //deal with the position
            // won't get clever here, remove the child if it's in there and rebuild the map with it
            children.remove(child);
            final LinkedHashSet<Name> withNewChild = new LinkedHashSet<Name>();
            int counter = 1;
            for (Name existingChild : children) {
                if (position == counter) {
                    withNewChild.add(child);
                }
                withNewChild.add(existingChild);
                counter++;
            }
            if (position >= counter) { // off the end, add it now.
                withNewChild.add(child);
            }
            children = withNewChild;
            findAllChildrenCache = null;
            synchronized (child.parents) {
                child.parents.add(this);
            }
            setNeedsPersisting();
        }
    }

    // removal ok on linked lists

    public synchronized void removeFromChildrenWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        // no need for the top parent check
        synchronized (name.parents) {
            name.parents.remove(this);
        }
        if (children.remove(name)) { // it changed the set
            findAllChildrenCache = null;
            setNeedsPersisting();
        }
    }

    public Map<Name, Boolean> getPeers() {
        return Collections.unmodifiableMap(peers);
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public synchronized void setPeersWillBePersisted(LinkedHashMap<Name, Boolean> peers) throws Exception {
        checkDatabaseForSet(peers.keySet());
        for (Name peer : peers.keySet()) {
            if (peer.equals(this)) {
                throw new Exception("error name cannot be a peer of itself " + this);
            }
        }

        // we're ok, now before assigning remove this name form the member of peers look ups map
        for (Name existingPeer : this.peers.keySet()) {
            synchronized (existingPeer.peerParents) {
                existingPeer.peerParents.remove(this);
            }
        }
        this.peers = peers;
        // add the adjusted back in back in :)
        for (Name existingPeer : this.peers.keySet()) {
            synchronized (existingPeer.peerParents) {
                existingPeer.peerParents.add(this);
            }
        }
        setNeedsPersisting();
    }

    // think I'm only going to allow bulk attribute settings in this package as it won't do the checks the single below will
    // really the below should only be called by totomemorydb

    protected synchronized void setAttributesWillBePersisted(LinkedHashMap<String, String> attributes) throws Exception {
        this.attributes = attributes;
        setNeedsPersisting();
    }

    public synchronized void setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {

        // important, manage persistence, allowed name rules, db look ups
        // only care about ones in this set
        for (Name parent : parents) {
            for (Name fellowChild : parent.getChildren()) {
                if (fellowChild.getId() != getId() && fellowChild.getAttribute(attributeName) != null && fellowChild.getAttribute(attributeName).equalsIgnoreCase(attributeValue)) {
                    throw new Exception(attributeName + " value : " + attributeValue + " already exists among siblings of " + getAttribute(DEFAULT_DISPLAY_NAME));
                }
            }
        }
        attributes.put(attributeName, attributeValue);
        // now deal with the DB maps!
        getTotoMemoryDB().addNameToAttributeNameMap(this); // will overwrite but that's fine
        setNeedsPersisting();
    }

    public synchronized void removeAttributeWillBePersisted(String attributeName) throws Exception {
        if (attributes.containsKey(attributeName)) {
            getTotoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, attributes.remove(attributeName), this);
        }
        setNeedsPersisting();
    }

    // convenience
    public synchronized void clearAttributes() throws Exception {
        for (String attribute : attributes.keySet()) {
            removeAttributeWillBePersisted(attribute);
        }
    }

    public String getAttribute(String attributeName) {
        return attributes.get(attributeName);
    }

    // removal ok on linked lists

    public synchronized void removeFromPeersWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        peers.remove(name);
        name.peerParents.remove(this);
        setNeedsPersisting();
    }

    // assign a comparator if wanted for a specific language!

    @Override
    public int compareTo(Name n) {
        return getDefaultDisplayName().toLowerCase().compareTo(getDefaultDisplayName().toLowerCase()); // think that will give us a case insensitive sort!
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
        public JsonTransport(@JsonProperty("provenanceId") int provenanceId, @JsonProperty("additive") boolean additive, @JsonProperty("attributes") Map<String, String> attributes, @JsonProperty("peerIds") LinkedHashMap<Integer, Boolean> peerIds, @JsonProperty("childrenIds") LinkedHashSet<Integer> childrenIds) {
            this.provenanceId = provenanceId;
            this.additive = additive;
            this.attributes = attributes;
            this.peerIds = peerIds;
            this.childrenIds = childrenIds;
        }
    }

    // suppose no harm in being public

    public String getAsJson() {
        LinkedHashMap<Integer, Boolean> peerIds = new LinkedHashMap<Integer, Boolean>();
        for (Name peer : peers.keySet()) {
            peerIds.put(peer.getId(), peers.get(peer));
        }
        // yes could probably use list but lets match collection types . . .
        LinkedHashSet<Integer> childrenIds = new LinkedHashSet<Integer>();
        for (Name child : children) {
            childrenIds.add(child.getId());
        }
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(), additive, attributes, peerIds, childrenIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // protected to only be used by the database loading, can't be called in the constructor as name by id maps may not be populated

    protected synchronized void populateFromJson() throws Exception {
        if (getTotoMemoryDB().getNeedsLoading() || jsonCache != null) { // only acceptable if we have json and it's during the loading process
            try {
                JsonTransport transport = jacksonMapper.readValue(jsonCache, JsonTransport.class);
                jsonCache = null;// free the memory
                this.provenance = getTotoMemoryDB().getProvenanceById(transport.provenanceId);
                this.additive = transport.additive;
                this.attributes = transport.attributes;
                LinkedHashMap<Integer, Boolean> peerIds = transport.peerIds;
                for (Integer peerId : peerIds.keySet()) {
                    peers.put(getTotoMemoryDB().getNameById(peerId), peerIds.get(peerId));
                }
                LinkedHashSet<Name> children = new LinkedHashSet<Name>();
                LinkedHashSet<Integer> childrenIds = transport.childrenIds;
                for (Integer childId : childrenIds) {
                    children.add(getTotoMemoryDB().getNameById(childId));
                }
                // need to sort out the parents
                for (Name newChild : children){
                    newChild.parents.add(this);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // first of its kind. Try to be comprehensive

    public void delete() throws Exception {
        // remove from values
        for (Value v : values) {
            Set<Name> namesForValue = v.getNames();
            namesForValue.remove(this);
            v.setNamesWillBePersisted(namesForValue);
        }
        // remove children
        for (Name child : children) {
            removeFromChildrenWillBePersisted(child);
        }
        // remove from parents
        for (Name parent : parents) {
            parent.removeFromChildrenWillBePersisted(this);
        }
        // peers - new lookup to use
        for (Name peerParent : peerParents) {
            peerParent.removeFromPeersWillBePersisted(this);
        }
        getTotoMemoryDB().removeNameFromDb(this);
        needsDeleting = true;
        setNeedsPersisting();
    }

}
