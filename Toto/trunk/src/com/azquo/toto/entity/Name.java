package com.azquo.toto.entity;

import com.azquo.toto.memorydb.TotoMemoryDB;

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

    boolean valuesChanged;
    boolean parentsChanged;
    boolean childrenChanged;
    boolean peersChanged;

    public Name(TotoMemoryDB totoMemoryDB, String name) throws Exception {
        this(totoMemoryDB, 0, name);
    }

    public Name(TotoMemoryDB totoMemoryDB, int id, String name) throws Exception {
        super(totoMemoryDB, id);
        this.name = name;
        values = new HashSet<Value>();
        parents = new HashSet<Name>();
        children = new LinkedHashSet<Name>();
        peers = new LinkedHashSet<Name>();
        valuesChanged = false;
        parentsChanged = false;
        childrenChanged = false;
        peersChanged = false;
        // it annoys me that this can't be folded into addToDb but I can't see how it would
        totoMemoryDB.addNameToDbNameMap(this);
    }

    @Override
    protected void addToDb(TotoMemoryDB totoMemoryDB) throws Exception {
        totoMemoryDB.addNameToDb(this);
    }

    // used after database load and saves
    @Override
    public void syncedToDB() {
        super.syncedToDB();
        valuesChanged = false;
        parentsChanged = false;
        childrenChanged = false;
        peersChanged = false;

    }

    public String getName() {
        return name;
    }

    // needs the db to check the name is not there

    public synchronized void changeNameWillBePersisted(String name, TotoMemoryDB totoMemoryDB) throws Exception {
        if (totoMemoryDB.getNameByName(name) != null) {
            throw new Exception("that name name already exists in the database");
        }
        this.name = name;
        entityColumnsChanged = true;
        inspectForPersistence = true;
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

    public synchronized void setValuesWillBePersisted(Set<Value> values) throws Exception {
        checkDatabaseForSet(values);
        this.values = values;
        valuesChanged = true;
        inspectForPersistence = true;
    }

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better too allow it to be changed but not externally

    public synchronized void addToValuesWillBePersisted(Value value) throws Exception {
        checkDatabaseMatches(value);
        if (values.add(value)) { // it changed the set
            valuesChanged = true;
            inspectForPersistence = true;
        }
    }

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better to allow it to be changed but not externally

    public synchronized void removeFromValuesWillBePersisted(Value value) throws Exception {
        checkDatabaseMatches(value);// even if not needed throw the damn exception!
        if (values.remove(value)) { // it changed the set
            valuesChanged = true;
            inspectForPersistence = true;
        }
    }

    public Set<Name> getParents() {
        return Collections.unmodifiableSet(parents);
    }

    public synchronized void setParentsWillBePersisted(Set<Name> parents) throws Exception {
        checkDatabaseForSet(parents);
        this.parents = parents;
        parentsChanged = true;
        inspectForPersistence = true;
    }

    public synchronized void addToParentsWillBePersisted(Name parent) throws Exception {
        checkDatabaseMatches(parent);
        if (parents.add(parent)) { // something changed, this was a new name
            parentsChanged = true;
            inspectForPersistence = true;
        }
    }

    public synchronized void removeFromParentsWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        if (parents.remove(name)) { // it changed the set
            parentsChanged = true;
            inspectForPersistence = true;
        }
    }

    /* ok I can return a linked hash set but I'm not sure oof the advantage

        return (LinkedHashSet<Name>)Collections.unmodifiableSet(children);
    leave as set for the mo

       */

    public Set<Name> getChildren() {
        return Collections.unmodifiableSet(children);
    }
    // as mentioned above, force the set to a linked set, we want it to be ordered :)

    public synchronized void setChildrenWillBePersisted(LinkedHashSet<Name> children) throws Exception {
        checkDatabaseForSet(children);
        this.children = children;
        childrenChanged = true;
        inspectForPersistence = true;
    }

    // removal ok on linked lists

    public synchronized void removeFromChildrenWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        if (children.remove(name)) { // it changed the set
            childrenChanged = true;
            inspectForPersistence = true;
        }
    }

    public Set<Name> getPeers() {
        return Collections.unmodifiableSet(peers);
    }

    public synchronized void setPeersWillBePersisted(LinkedHashSet<Name> peers) throws Exception {
        checkDatabaseForSet(peers);
        this.peers = peers;
        peersChanged = true;
        inspectForPersistence = true;
    }

    // removal ok on linked lists

    public synchronized void removeFromPeersWillBePersisted(Name name) throws Exception {
        checkDatabaseMatches(name);// even if not needed throw the damn exception!
        if (peers.remove(name)) { // it changed the set
            peersChanged = true;
            inspectForPersistence = true;
        }
    }

    @Override
    public int compareTo(Name n) {
        return name.toLowerCase().compareTo(n.getName().toLowerCase()); // think that will give us a case insensitive sort!
    }
}
