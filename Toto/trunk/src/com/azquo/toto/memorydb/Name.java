package com.azquo.toto.memorydb;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Toto object, names only have names but they can have parent and child relationships with multiple
 * other names. Sets of names. Used to be called label, there may be remnants of this in the code.
 * <p/>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on
 */
public final class Name extends TotoMemoryDBEntity implements Comparable<Name>{

    // leaving here as a reminder to consider proper logging
    //private static final Logger logger = Logger.getLogger(Name.class.getName());
    // data fields
    private Provenance provenance;
    private String name;
    private boolean additive;

    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo

    // ok they're all sets, but some need ordering :)
    private Set<Value> values;
    private Set<Name> parents;
    /* these two have position which we'll reflect by the place in the list. WHen modifying these sets one has to recreate teh set anyway
    and when doing so changes to the order are taken into account.

    Peers is going to become a linked hash map as opposed to set as I need to store whether they are additive or not and this cannot be against the
     name object. A name may be an additive peer in one scenario and not in another.
     */
    private LinkedHashSet<Name> children;
    private LinkedHashMap<Name, Boolean> peers;

    private boolean childrenChanged;
    private boolean peersChanged;

    // parents is maintained according to children, it isn't persisted in the same way

    public Name(TotoMemoryDB totoMemoryDB, Provenance provenance, String name, boolean additive) throws Exception {
        this(totoMemoryDB, 0, provenance, name, additive);
    }

    public Name(TotoMemoryDB totoMemoryDB, int id, Provenance provenance,String name, boolean additive) throws Exception {
        super(totoMemoryDB, id);
        if (name == null || name.trim().length() == 0){ // then I think we thrown an exception, can't have a blank name!
            throw new Exception("error cannot create name with blank oor null name!");
        }
        // Is there anything wrong with trimming here?
        this.name = name.trim();
        this.provenance = provenance;
        this.additive = additive;
        values = new HashSet<Value>();
        parents = new HashSet<Name>();
        children = new LinkedHashSet<Name>();
        peers = new LinkedHashMap<Name, Boolean>();
        childrenChanged = false;
        peersChanged = false;
        // it annoys me that this can't be folded into addToDb but I can't see how it would as the name won't be initialised when that is called
        // we could pull a trick above as in they have to override "assign variables" or something . . .not sure
        totoMemoryDB.addNameToDbNameMap(this);
    }

    @Override
    protected void addToDb() throws Exception {
        getTotoMemoryDB().addNameToDb(this);
    }

    @Override
    protected void setNeedsPersisting() {
        getTotoMemoryDB().setNameNeedsPersisting(this);
    }

    @Override
    protected void classSpecificSetAsPersisted() {
        childrenChanged = false;
        peersChanged = false;
        getTotoMemoryDB().removeNameNeedsPersisting(this);
    }

    protected boolean getChildrenChanged() {
        return childrenChanged;
    }

    protected boolean getPeersChanged() {
        return peersChanged;
    }

    public String getName() {
        return name;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public boolean getAdditive() {
        return additive;
    }

    // needs the db to check the name is not there

    public synchronized void changeNameWillBePersisted(String name) throws Exception {
        if (getTotoMemoryDB().getNameByName(name) != null) {
            throw new Exception("that name name already exists in the database");
        }
        this.name = name;
        entityColumnsChanged = true;
        setNeedsPersisting();
    }

    // needs the db to check the name is not there

    public synchronized void setAdditiveWillBePersisted(boolean additive) throws Exception {
        if (this.additive != additive){
            this.additive = additive;
            entityColumnsChanged = true;
            setNeedsPersisting();
        }
    }

    @Override
    public String toString() {
        return "Name{" +
                "id='" + getId() + '\'' +
                "name='" + name + '\'' +
                '}';
    }

    public Set<Value> getValues() {
        return Collections.unmodifiableSet(values);
    }

    // these tow are becoming protected so they can be set by Value.
    // Value will be the reference point for the value name link, the ones here are for fast lookup, no need for persistence

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better too allow it to be changed but not externally

    protected synchronized void addToValues(Value value) throws Exception {
        checkDatabaseMatches(value);
        values.add(value);
    }

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better to allow it to be changed but not externally

    protected synchronized void removeFromValues(Value value) throws Exception {
        checkDatabaseMatches(value);// even if not needed throw the damn exception!
        values.remove(value); // it changed the set
    }

    public Set<Name> getParents() {
        return Collections.unmodifiableSet(parents);
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
            Set<Name> nextLevelSet = new HashSet<Name>();
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
        if (parents.size() > 0){
            Name parent = parents.iterator().next();
            while (parent != null){
                if (parent.getParents().size() > 0){
                    parent = parent.getParents().iterator().next();
                } else {
                    return parent; // it has no parents, must be top
                }
            }
        }
        return null;
    }

    // same logic as above but returns a set, should be correct

    private Set<Name> findAllChildrenCache = null;

    public Set<Name> findAllChildren() {
        if (findAllChildrenCache != null){
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

    public Set<Name> getChildren() {
        return Collections.unmodifiableSet(children);
    }
    // as mentioned above, force the set to a linked set, we want it to be ordered :)

    public synchronized void setChildrenWillBePersisted(LinkedHashSet<Name> children) throws Exception {
        checkDatabaseForSet(children);

        // check for circular references
        for (Name newChild : children){
            if (newChild.equals(this) || findAllParents().contains(newChild)){
                throw new Exception("error cannot assign child due to circular reference, " + newChild + " cannot be added to " + this);
            }
            // now we need to check the top parent is not changing ont eh child
            if (newChild.getParents().size() > 0){ // it has parents so we need to check
                if (!newChild.findTopParent().equals(this) && parents.size() > 0 && !newChild.findTopParent().equals(findTopParent())){
                    throw new Exception("error cannot assign child as it has a different top parent" + newChild + " has top parent " + newChild.findTopParent() + " " + this + " has or is a different top parent");
                }
            }
        }

        // remove all parents on the old one
        for (Name oldChild : this.children){
            oldChild.parents.remove(this);
        }

        this.children = children;
        findAllChildrenCache = null;
        // set the parents based of the children

        // set all parents on the new list
        for (Name newChild : this.children){
            newChild.parents.add(this);
        }

        if (!getTotoMemoryDB().getNeedsLoading()){ // while loading we don't want to set any persistence flags
            childrenChanged = true;
            setNeedsPersisting();
        }

    }

    // more efficient if just building the set by adding

    public synchronized void addChildWillBePersisted(Name child) throws Exception {
        checkDatabaseMatches(child);
        if (child.equals(this) || findAllParents().contains(child)){
            throw new Exception("error cannot assign child due to circular reference, " + child + " cannot be added to " + this);
        }
        // now we need to check the top parent is not changing ont eh child
        if (child.getParents().size() > 0){ // it has parents so we need to check
            if (!child.findTopParent().equals(this) && parents.size() > 0 && !child.findTopParent().equals(findTopParent())){
                throw new Exception("error cannot assign child as it has a different top parent" + child + " has top parent " + child.findTopParent() + " " + this + " has or is a different top parent");
            }
        }

        if (children.add(child)){ // something actually changed :)
            findAllChildrenCache = null;
            child.parents.add(this);
            if(!getTotoMemoryDB().getNeedsLoading()){ // while loading we don't want to set any persistence flags
                childrenChanged = true;
                setNeedsPersisting();
            }
        }

    }

    // removal ok on linked lists

    public synchronized void removeFromChildrenWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        // no need for the top parent check
        name.parents.remove(this);
        if (children.remove(name) && !getTotoMemoryDB().getNeedsLoading()) { // it changed the set and we're not loading
            findAllChildrenCache = null;
            childrenChanged = true;
            setNeedsPersisting();
        }
    }

    public Map<Name,Boolean> getPeers() {
        return Collections.unmodifiableMap(peers);
    }

    public synchronized void setPeersWillBePersisted(LinkedHashMap<Name,Boolean> peers) throws Exception {
        checkDatabaseForSet(peers.keySet());
        for (Name peer : peers.keySet()){
            if (peer.equals(this)){
                throw new Exception("error name cannot be a peer of itself " + this);
            }
        }
        this.peers = peers;
        if (!getTotoMemoryDB().getNeedsLoading()){ // while loading we don't want to set any persistence flags
            peersChanged = true;
            setNeedsPersisting();
        }

    }

    // removal ok on linked lists

    public synchronized void removeFromPeersWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        if (peers.remove(name) && !getTotoMemoryDB().getNeedsLoading()) { // it changed the set
            peersChanged = true;
            setNeedsPersisting();
        }
    }

    @Override
    public int compareTo(Name n) {
        return name.toLowerCase().compareTo(n.getName().toLowerCase()); // think that will give us a case insensitive sort!
    }
}
