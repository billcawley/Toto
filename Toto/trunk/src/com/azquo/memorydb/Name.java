package com.azquo.memorydb;

import com.azquo.memorydbdao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.*;

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
 *
 * Of course we care about thread safety even if crude but we have another concern now : this object was weighing in at over 2k average in an example magento db
 * the goal is to bring it to sub 500 bytes. THis means in some cases variables being null until used and using arraylists instead of sets switching for performance
 * when the list gets too big.
 *
 * Update on memory : got it to about 850 bytes (magento example DB). Will park for the mo, further change would probably involve changes to attributes.
 *
 * TODO : cleanup and check thread safety and comments, ongoing.
 *
 * attributes case insensitive . . .not entirely happy about this
 *
 */
public final class Name extends AzquoMemoryDBEntity implements Comparable<Name> {

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
    // going to try for attributes as two arraylists as this should save a lot of space vs a linked hash map
    private final List<String> attributeKeys;
    private final List<String> attributeValues;


    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo
    // these 3 are for quick lookup, must be modified appropriately e.g.add a peer add to that peer's peer parents
    // to be clear, these are not used when persisting, they are derived from the name sets in values and the two below
    // Edd note 11/04/2014 : Sets are expensive in terms of memory, will use lists instead unless they get really big
    // need the atomic reference for thread safety
    // Hmm I thought I could get away with values as just a list, it slowed right down, will follow the pattern for children and parents
    private volatile Set<Value> valuesAsSet;
    private final List<Value> values;
    // we want thread safe != null test hence volatile
    private volatile Set<Name> parentsAsSet;
    private final ArrayList<Name> parents;
    private volatile List<Name> peerParents;

    /* these two have position which we'll reflect by the place in the list. WHen modifying these sets one has to recreate teh set anyway
    and when doing so changes to the order are taken into account.

    Peers is going to become a linked hash map as opposed to set as I need to store whether they are additive or not and this cannot be against the
     name object. A name may be an additive peer in one scenario and not in another.

     These sets are the ones which define data structure and the ones persisted, parents and peerParents above are derived from these

     I'm not currently using thread safe objects so I need to syncronize modifiers. I also make copies of returned lists.
     */
    // we want thread safe != null test hence volatile
    private volatile Set<Name> childrenAsSet;
    private final List<Name> children;
    // how often is peers used?? - gonna make null by default
    private LinkedHashMap<Name, Boolean> peers;

    // for the code to make new names

    public Name(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, boolean additive) throws Exception {
        this(azquoMemoryDB, 0, null);
        setProvenanceWillBePersisted(provenance);
        setAdditiveWillBePersisted(additive);
    }

    // protected, should only be called by azquo memory db
    // maps are not set here as we need to wait then load from the json cache

    protected Name(final AzquoMemoryDB azquoMemoryDB, int id, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        jsonCache = jsonFromDB;
        additive = true; // by default
        valuesAsSet = null;
        values = new ArrayList<Value>(0);
        parentsAsSet = null;
        parents = new ArrayList<Name>(0); // keep overhead low
        peerParents = null;
        childrenAsSet = null;
        children = new ArrayList<Name>(0); // keep overhead low
        peers = null; // keep overhead really low! I'm assuming this won't be used often - gets over the problem of linked hash map being expensive

        attributeKeys = new ArrayList<String>(1);// a name will have at least one attribute we cna assume
        attributeValues = new ArrayList<String>(1);

        getAzquoMemoryDB().addNameToDb(this);
    }

    // for convenience but be careful where it is used . . .

    public String getDefaultDisplayName() {
        return getAttribute(DEFAULT_DISPLAY_NAME);
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
                "id='" + getId() + '\'' +
                "attributes='" + attributeKeys + '\'' +
                "attribute values='" + attributeValues + '\'' +
                '}';
    }

    // I'm assuming (Ha!) that this collection copying is thread safe enough

    public Collection<Value> getValues() {
        return  Collections.unmodifiableCollection(valuesAsSet != null ? valuesAsSet : values);
    }

    // these two are becoming protected so they can be set by Value.
    // Value will be the reference point for the value name link, the ones here are for fast lookup, no need for persistence
    // following parent pattern re switching to sets etc


    protected void addToValues(final Value value) throws Exception {
        checkDatabaseMatches(value);
        synchronized (values) { // we can sync on this object whether it's used or not
            if (valuesAsSet != null){
                valuesAsSet.add(value);
            } else {
                if (!values.contains(value)){ // it's this contains expense that means we should stop using arraylists over a certain size
                    // OK, here it may get interesting, what about size???
                    if (values.size() >= ARRAYLISTTHRESHOLD){ // we need to convert. copy the array to a new hashset then set it
                        valuesAsSet = new HashSet<Value>(values);
                        valuesAsSet.add(value);
                        // TODO - what about the possibility the get above just misses this set and then gets a clear collection? Maybe don't blank it? By this point the set will be eating way more memory anyway
                        //parents.clear();
                        //parents.trimToSize();
                    } else {
                        values.add(value);
                    }
                }
            }
        }
    }

    protected void removeFromValues(final Value value) throws Exception {
        synchronized (values) { // we can sync on this object whether it's used or not
            if (valuesAsSet != null){
                valuesAsSet.remove(value);
            } else {
                values.remove(value);
            }
        }
    }


    public Collection<Name> getParents() {
        return  Collections.unmodifiableCollection(parentsAsSet != null ? parentsAsSet : parents);
    }

    public Collection<Name> getPeerParents() {
        return peerParents != null ? Collections.unmodifiableCollection(peerParents) : new ArrayList<Name>();
    }

    // don't allow external classes to set the parents I mean by function or otherwise, Name can manage this based on set children
    // before could just edit the parents as I pleased now I can'[t need getters and setters based on the map

    private void addToParents(final Name name) throws Exception {
        synchronized (parents) { // we can sync on this object whether it's used or not
            if (parentsAsSet != null){
                parentsAsSet.add(name);
            } else {
                if (!parents.contains(name)){ // it's this contains expense that means we should stop using arraylists over a certain size
                    // OK, here it may get interesting, what about size???
                    if (parents.size() >= ARRAYLISTTHRESHOLD){ // we need to convert. copy the array to a new hashset then set it
                        parentsAsSet = new HashSet<Name>(parents);
                        parentsAsSet.add(name);
                        // TODO - what about the possibility the get above just misses this set and then gets a clear collection? Maybe don't blank it? By this point the set will be eating way more memory anyway
                        //parents.clear();
                        //parents.trimToSize();
                    } else {
                        parents.add(name);
                    }
                }
            }
        }
    }

    private void removeFromParents(final Name name) throws Exception {
        synchronized (parents) { // we can sync on this object whether it's used or not
            if (parentsAsSet != null){
                parentsAsSet.remove(name);
            } else {
                parents.remove(name);
            }
        }
    }


    // returns a collection, I think this is just iterated over to check stuff
    // todo - check use of this and then whether we use sets internally or not
    // these two functions moved here from the service

    private void findAllParents(Name name, final Set<Name>allParents){
        for (Name parent:name.getParents()){
            if (!allParents.contains(parent)){
                allParents.add(parent);
                findAllParents(parent,allParents);
            }
        }

    }



    public Collection<Name> findAllParents() {
        final Set<Name> allParents = new HashSet<Name>();
        findAllParents(this,allParents);
        return allParents;
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
    // todo - check whether we need a set returned or not . . .

    // switching to lists as better on the memory
    private Set<Name> findAllChildrenCache = null;
    private Set<Name> findAllChildrenPayAttentionToAdditiveCache = null;


    private void findAllChildren(Name name, boolean payAttentionToAdditive, final Set<Name>allChildren){
        if (payAttentionToAdditive && !name.additive) return;
        for (Name child:name.getChildren()){
            if (!allChildren.contains(child)){
                allChildren.add(child);
                findAllChildren(child,payAttentionToAdditive,allChildren);
            }
        }

    }


    public Collection<Name> findAllChildren(boolean payAttentionToAdditive) {
        if (payAttentionToAdditive) {
            if (findAllChildrenPayAttentionToAdditiveCache != null) {
                return findAllChildrenPayAttentionToAdditiveCache;
            }
            Set<Name> allChildrenPayAttentionToAdditive = new HashSet<Name>();
            findAllChildren(this,payAttentionToAdditive, allChildrenPayAttentionToAdditive);
            if (!allChildrenPayAttentionToAdditive.isEmpty()){ // only cache if there's something to cache!
                findAllChildrenPayAttentionToAdditiveCache = allChildrenPayAttentionToAdditive;
            }
            return allChildrenPayAttentionToAdditive; // just return the set , don't se the harm
        } else {
            if (findAllChildrenCache != null) {
                return findAllChildrenCache;
            }
            Set<Name> allChildren = new HashSet<Name>();
            findAllChildren(this,payAttentionToAdditive, allChildren);
            if (!allChildren.isEmpty()){
                findAllChildrenCache = allChildren;
            }
            return allChildren; // just return the set , don't se the harm
        }
    }


    public Collection<Name> getChildren() {
        return  Collections.unmodifiableCollection(childrenAsSet != null ? childrenAsSet : children);
    }

    // as mentioned above, force the set to a linked set, we want it to be ordered, BUT internally it may not stay as a linked set . . .
    //should we just use a damn list???

    public synchronized void setChildrenWillBePersisted(List<Name> children) throws Exception {
        //removed this check - the only use of this function is in creating temporary names in the same database....

        //checkDatabaseForSet(children);
        /*
        We're taking a much more flexible view of sets now, so it is possible for a name to have another name as both child and parent.  Need to check in 'findAllParents
        // check for circular references
        for (Name newChild : children) {
            // now we need to check the top parent is not changing on the child
            if (newChild.getParents().size() > 0) { // it has parents so we need to check
                Name newChildFindATopParent = newChild.findATopParent();
                Collection<Name> getparents = getParents();
                Name findAtopParent = findATopParent();
                if (!newChild.findATopParent().equals(this) && getParents().size() > 0 && !newChild.findATopParent().equals(findATopParent())) {
                    throw new Exception("error cannot assign child as it has a different top parent" + newChild + " has top parent " + newChild.findATopParent() + " " + this + " has or is a different top parent");
                }
            }
        }
        */
        // remove all parents on the old one


        if (this.getChildren()!=null) {
            Set<Name> oldChildren = new HashSet<Name>(this.getChildren());
            for (Name oldChild : oldChildren) {
                removeFromChildrenWillBePersisted(oldChild);
           }
        }
         for (Name child:children){
            addChildWillBePersisted(child);
         }
        findAllChildrenCache = null;
        findAllChildrenPayAttentionToAdditiveCache = null;




    }

    private synchronized void setChildrenNoChecks(LinkedHashSet<Name> children){
        if (childrenAsSet != null || children.size() > ARRAYLISTTHRESHOLD){ // then overwrite the map . . I just noticed how this could be dangerous in terms of external references. Make a copy.
            this.childrenAsSet = new HashSet<Name>(children); // NOTE! now we're not using linked, position and ordering will be ignored for large sets of children!!
        } else {
            this.children.clear(); // clear the children for this name
            // could use add all, would this preserve order???
            for (Name child : children){
                this.children.add(child);
            }
        }
    }

    public void addChildWillBePersisted(Name child) throws Exception {
        addChildWillBePersisted(child, 0);
    }

    // with position, will just add if none passed
    // note : this sees position as starting at 1!
    // todo : is this syncroniseation ok? Maybe on the name object and just for a block?

    public synchronized void addChildWillBePersisted(Name child, int position) throws Exception {
        checkDatabaseMatches(child);
        if (child.equals(this) || findAllParents().contains(child)) {
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        /* SINGLE CHILDREN MAY NOW HAVE A DIFFERENT TOP PARENT (e.g. ORDER ITEM in PRODUCT)
        // now we need to check the top parent is not changing ont eh child
        if (child.getParents().size() > 0) { // it has parents so we need to check
            if (!child.findATopParent().equals(this) && parents.size() > 0 && !child.findATopParent().equals(findATopParent())) {
                throw new Exception("error cannot assign child as it has a different top parent" + child + " has top parent " + child.findATopParent() + " " + this + " has or is a different top parent");
            }
        }
        */

        // NOTE!! for the set version we're now just using hassle NOT liked hashset, for large child sets ordering will be ignored!

        // TODO, could it happen that an add or remove misses a switch to the set and actions on the list?? Make sure this cannot happen
        // well the add is right here and syncronised :) And the remove is syncronized too, this makes it safe?
        if (childrenAsSet != null){
            childrenAsSet.add(child); // plain put, will check for duplicates
        } else {
            if (!children.contains(child)){
                // like parents, hope the logic is sound
                if (children.size() >= ARRAYLISTTHRESHOLD){ // we need to convert. copy the array to a new hashset then set it
                    childrenAsSet = new HashSet<Name>(children);
                    childrenAsSet.add(child);
                    // TODO - what about the possibility the get above just misses this set and then gets a clear collection? Maybe don't blank it? By this point the set will be eating way more memory anyway
                    //children.clear();
                    //children.trimToSize();
                } else {
                    // then add
                    // check position first
                    if (position != 0 && position <= children.size()){
                        children.add(position - 1, child);
                    } else {
                        children.add(child);
                    }
                }


            }
        }
        findAllChildrenCache = null;
        child.addToParents(this);
        setNeedsPersisting();
        //and check that there are not indirect connections which should be deleted (e.g. if London exists in UK and Europe, and we are now
        //specifying that UK is in Europe, we must remove the direct link from London to Europe
        for (Name descendant:child.findAllChildren(false)){
            if (getChildren().contains(descendant)){
                removeFromChildrenWillBePersisted(descendant);
            }
        }
    }

    // removal ok on linked lists

    public synchronized void removeFromChildrenWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        if (getChildren().contains(name)){ // then something to remove
            name.removeFromParents(this);
            //don't allow names that have previously had parents to fall out of topparent set
            if (name.getParents().size() == 0){
                this.findATopParent().addChildWillBePersisted(name);//revert to top parent (Note that, if 'this' is top parent, then it will be re-instated!
            }

            if (childrenAsSet != null){
                childrenAsSet.remove(name);
            } else {
                children.remove(name);
            }

            findAllChildrenCache = null;
            setNeedsPersisting();
        }
    }

    public Map<Name, Boolean> getPeers() {
        return peers != null ? Collections.unmodifiableMap(peers) : new HashMap<Name, Boolean>();
    }

    public Map<String, String> getAttributes() {
        HashMap attributesAsMap = new HashMap();
        int count = 0;
        // if I'm going to for loop I should probably copy
        ArrayList valuesCopy = new ArrayList(attributeValues);
        for (String key : new ArrayList<String>(attributeKeys)){
            attributesAsMap.put(key, valuesCopy.get(count));
            count++;
        }
        return Collections.unmodifiableMap(attributesAsMap);
    }

    public synchronized void setPeersWillBePersisted(LinkedHashMap<Name, Boolean> peers) throws Exception {
        //check if identical to existing
        if (this.peers != null && this.getPeers().size()== peers.size()){ // not null check as it will be on init
            boolean identical = true;
            for (Name peer:this.getPeers().keySet()){
                Boolean newAdditive = peers.get(peer);
                if (newAdditive == null || newAdditive != this.getPeers().get(peer)){
                    identical = false;
                }
            }
            if (identical){
                return;
            }
        }
        checkDatabaseForSet(peers.keySet());
        for (Name peer : peers.keySet()) {
            if (peer.equals(this)) {
                throw new Exception("error name cannot be a peer of itself " + this);
            }
        }

        // we're ok, now before assigning remove this name form the member of peers look ups map
        if (this.peers != null){ // added not null chekc as it will be on init
            for (Name existingPeer : this.peers.keySet()) {
                    if (existingPeer.peerParents != null){
                        synchronized (existingPeer.peerParents) {
                            existingPeer.peerParents.remove(this);
                        }
                    }
            }
        }
        System.out.println("setting peers for " + this);
        this.peers = peers;
        // add the adjusted back in back in :)
        for (Name existingPeer : this.peers.keySet()) {
                if (existingPeer.peerParents == null){ // I think this check is all that's required for peerparents to be changed to a list
                    existingPeer.peerParents = new ArrayList<Name>();
                }
            synchronized (existingPeer.peerParents) {
                if (!existingPeer.peerParents.contains(this)){ // I think this check is all that's required for peerparents to be changed to a list
                    existingPeer.peerParents.add(this);
                }
            }
        }
        setNeedsPersisting();
    }

    public synchronized void setTemporaryAttribute(String attributeName, String attributeValue)throws Exception{
        attributeName = attributeName.toUpperCase();
        int index = attributeKeys.indexOf(attributeName);
        if (index != -1){
            attributeValues.remove(index);
            attributeValues.add(index, attributeValue);
        } else {
            attributeKeys.add(attributeName);
            attributeValues.add(attributeValue);
        }

//        attributes.put(attributeName, attributeValue);
        getAzquoMemoryDB().addNameToAttributeNameMap(this); // will overwrite but that's fine

    }

    // zapping set attribute set, only done by he populate from json below I think

    // todo - addname to attribute mapo . . . not efficient???

    public synchronized String setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {
        attributeName = attributeName.toUpperCase();
        // important, manage persistence, allowed name rules, db look ups
        // only care about ones in this set

        // code adapted from map based code to lists, may need rewriting

        int index = attributeKeys.indexOf(attributeName);
        String existing = null;
        if (index != -1){
            // we want an index out of bounds to be thrown here if they don't match
            existing = attributeValues.get(index);
        }
        if (attributeValue == null || attributeValue.length()==0){
            // delete it
            if (existing != null){
                attributeKeys.remove(index);
                attributeValues.remove(index);
                //attributes.remove(attributeName);
                getAzquoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
                setNeedsPersisting();
            }
            return "";
        }
        if (existing!= null && existing.equals(attributeValue)){
            return "";
        }
        /* THERE SHOULD NOT BE A NEED TO CHECK AMONG SIBLINGS,  NOT SURE WHY THIS CHECK IS THERE - MAYBE CHECKING DEFAULT DISPLAY NAME FOR DUPLICATES
        for (Name parent : parents) {
            for (Name fellowChild : parent.getChildren()) {
                if (fellowChild.getId() != getId() && fellowChild.getAttribute(attributeName) != null && fellowChild.getAttribute(attributeName).equalsIgnoreCase(attributeValue)) {
                     return "error: value : " + attributeValue + " already exists among siblings of " + getAttribute(DEFAULT_DISPLAY_NAME);
                }
            }
        }
        */
        if (existing != null){
            // just update the values
            attributeValues.remove(index);
            attributeValues.add(index, attributeValue);
            getAzquoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);
        } else {
            // a new one
            attributeKeys.add(attributeName);
            attributeValues.add(attributeValue);
        }
//        attributes.put(attributeName, attributeValue);
        // now deal with the DB maps!
        // ok here I did say addNameToAttributeNameMap but that is inefficient, it uses every attribute, we've only changed one
        getAzquoMemoryDB().setAttributeForNameInAttributeNameMap(attributeName, attributeValue, this);
        setNeedsPersisting();
        return "";
    }

    public synchronized void removeAttributeWillBePersisted(String attributeName) throws Exception {
        attributeName = attributeName.toUpperCase();
        int index = attributeKeys.indexOf(attributeName);
        if (index != -1) {
            getAzquoMemoryDB().removeAttributeFromNameInAttributeNameMap(attributeName, attributeValues.get(index), this);
            attributeKeys.remove(index);
            attributeValues.remove(index);
        }
        setNeedsPersisting();
    }

    // convenience
    public synchronized void clearAttributes() throws Exception {
        for (String attribute : new ArrayList<String>(attributeKeys)) { // need to wrap the keyset in an arraylist as removeAttributeWillBePersisted will modify the keyset
            removeAttributeWillBePersisted(attribute);
        }
    }

    private String findParentAttributes(Name child, String attributeName){
        attributeName = attributeName.toUpperCase();
        for (Name parent:child.getParents()){
            if (parent.getDefaultDisplayName().equalsIgnoreCase(attributeName)){
                return child.getDefaultDisplayName();
            }
            String attribute = parent.getAttribute(attributeName);
            if (attribute != null){
                return attribute;

            }
            attribute = findParentAttributes(parent, attributeName);
            if (attribute != null){
                return attribute;
            }
        }
        return null;

    }


    // todo : what happens if attributes changed? An exception is not a biggy but what if the lists go out of sync temporarily???

    public String getAttribute(String attributeName) {
        attributeName = attributeName.toUpperCase();
        String attribute = null;
        int index = attributeKeys.indexOf(attributeName);
        if (index != -1){
            attribute = attributeValues.get(index);
        }
        if (attribute != null) return attribute;
        //look up the chain for any parent with the attribute
        return findParentAttributes(this, attributeName);
    }

    public boolean hasInParentTree(final Name testParent) {
        for (Name parent : getParents()) {
            if (testParent == parent || parent.hasInParentTree(testParent)) {
                return true;
            }
        }
        return false;
    }


    // removal ok on linked lists

    public synchronized void removeFromPeersWillBePersisted(Name name) throws Exception {
        if (peers != null){
            checkDatabaseMatches(name);// even if not needed throw the damn exception!
            peers.remove(name);
            if (name.peerParents != null){
                synchronized (name.peerParents){
                    name.peerParents.remove(this);
                }
            }
            setNeedsPersisting();
        }
    }

    // assign a comparator if wanted for a specific language!

    @Override
    public int compareTo(Name n) {
        return getDefaultDisplayName().toUpperCase().compareTo(n.getDefaultDisplayName().toUpperCase()); // think that will give us a case insensitive sort!
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
        if (peers != null){
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
                for (String key : transport.attributes.keySet()){
                    attributeKeys.add(key.toUpperCase());
                    attributeValues.add(transport.attributes.get(key));
                }
                LinkedHashMap<Integer, Boolean> peerIds = transport.peerIds;
                if (!peerIds.isEmpty()){
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

    public void delete() throws Exception {
        // remove from values
        for (Value v : values) {
            Set<Name> namesForValue = v.getNames();
            namesForValue.remove(this);
            v.setNamesWillBePersisted(namesForValue);
        }
        // remove children
        for (Name child : getChildren()) {
            removeFromChildrenWillBePersisted(child);
        }
        // remove from parents
        for (Name parent : getParents()) {
            parent.removeFromChildrenWillBePersisted(this);
        }
        // peers - new lookup to use
        if (peerParents != null){
            for (Name peerParent : peerParents) {
                peerParent.removeFromPeersWillBePersisted(this);
            }
        }
        getAzquoMemoryDB().removeNameFromDb(this);
        needsDeleting = true;
        setNeedsPersisting();
    }
}