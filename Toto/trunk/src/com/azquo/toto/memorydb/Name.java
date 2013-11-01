package com.azquo.toto.memorydb;

import org.springframework.dao.DataAccessException;

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
    private String name;

    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo

    // ok they're all sets, but some need ordering :)
    private Set<Value> values;
    private Set<Name> parents;
    /* these two have position which we'll reflect by the place in the list. WHen modifying these sets one has to recreate teh set anyway
    and when doing soo changes to the order are taken into account.
     */
    private LinkedHashSet<Name> children;
    private LinkedHashSet<Name> peers;

    private boolean childrenChanged;
    private boolean peersChanged;

    // parents is maintained according to children, it isn't persisted in the same way

    public Name(TotoMemoryDB totoMemoryDB, String name) throws Exception {
        this(totoMemoryDB, 0, name);
    }

    public Name(TotoMemoryDB totoMemoryDB, int id, String name) throws Exception {
        super(totoMemoryDB, id);
        if (name == null || name.trim().length() == 0){ // then I think we thrown an exception, can't have a blank name!
            throw new Exception("error cannot create name with blank oor null name!");
        }
        // Is there anything wrong with trimming here?
        this.name = name.trim();
        values = new HashSet<Value>();
        parents = new HashSet<Name>();
        children = new LinkedHashSet<Name>();
        peers = new LinkedHashSet<Name>();
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

    public boolean getChildrenChanged() {
        return childrenChanged;
    }

    public boolean getPeersChanged() {
        return peersChanged;
    }

    public String getName() {
        return name;
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

    // same logic as above but returns a set, should be correct

    public Set<Name> findAllChildren() {
        final Set<Name> allChildren = new HashSet<Name>();
        Set<Name> foundAtCurrentLevel = children;
        while (!foundAtCurrentLevel.isEmpty()) {
            allChildren.addAll(foundAtCurrentLevel);
            Set<Name> nextLevelSet = new HashSet<Name>();
            for (Name n : foundAtCurrentLevel) {
                nextLevelSet.addAll(n.getChildren());
            }
            if (nextLevelSet.isEmpty()) { // no more parents to find
                break;
            }
            foundAtCurrentLevel = nextLevelSet;
        }
        return allChildren;
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
        }


        // remove all parents on the old one
        for (Name oldChild : this.children){
            oldChild.parents.remove(this);
        }

        this.children = children;
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

        if (children.add(child)){ // something actually changed :)
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
        name.parents.remove(this);
        if (children.remove(name) && !getTotoMemoryDB().getNeedsLoading()) { // it changed the set and we're not loading
            childrenChanged = true;
            setNeedsPersisting();
        }
    }

    public Set<Name> getPeers() {
        return Collections.unmodifiableSet(peers);
    }

    public synchronized void setPeersWillBePersisted(LinkedHashSet<Name> peers) throws Exception {
        checkDatabaseForSet(peers);
        for (Name peer : peers){
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
