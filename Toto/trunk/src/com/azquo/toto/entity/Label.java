package com.azquo.toto.entity;

import com.azquo.toto.memorydb.TotoMemoryDB;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Toto object, labels only have names but they can have parent and child relationships with multiple
 * other labels. Sets of labels.
 * <p/>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on
 */
public final class Label extends TotoMemoryDBEntity {

    // leaving here as a reminder to consider proper logging
    //private static final Logger logger = Logger.getLogger(Label.class.getName());
    // data fields
    private String name;

    // memory db structure bits. There may be better ways to do this but we'll leave it here for the mo

    // ok they're all sets, but some need ordering :)
    private Set<Value> values;
    private Set<Label> parents;
    /* these two have position which we'll reflect by the place in the list. WHen modifying these sets one has to recreate teh set anyway
    and when doing soo changes to the order are taken into account.
     */
    private LinkedHashSet<Label> children;
    private LinkedHashSet<Label> peers;

    boolean valuesChanged;
    boolean parentsChanged;
    boolean childrenChanged;
    boolean peersChanged;

    public Label(TotoMemoryDB totoMemoryDB, String name) throws Exception {
        this(totoMemoryDB, 0, name);
    }

    public Label(TotoMemoryDB totoMemoryDB, int id, String name) throws Exception {
        super(totoMemoryDB, id);
        this.name = name;
        values = new HashSet<Value>();
        parents = new HashSet<Label>();
        children = new LinkedHashSet<Label>();
        peers = new LinkedHashSet<Label>();
        valuesChanged = false;
        parentsChanged = false;
        childrenChanged = false;
        peersChanged = false;
        // it annoys me that this can't be folded into addToDb but I can't see how it would
        totoMemoryDB.addLabelToDbNameMap(this);
    }

    @Override
    protected void addToDb(TotoMemoryDB totoMemoryDB) throws Exception {
        totoMemoryDB.addLabelToDb(this);
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

    public synchronized void changeLabelNameWillBePersisted(String name, TotoMemoryDB totoMemoryDB) throws Exception {
        if (totoMemoryDB.getLabelByName(name) != null) {
            throw new Exception("that label name already exists in the database");
        }
        this.name = name;
        entityColumnsChanged = true;
        inspectForPersistence = true;
    }

    @Override
    public String toString() {
        return "Label{" +
                "id='" + getId() + '\'' +
                "name='" + name + '\'' +
                '}';
    }

    public Set<Value> getValues() {
        return Collections.unmodifiableSet(values);
    }

    public synchronized void setValuesWillBePersisted(Set<Value> values) throws Exception {
        checkDatabaseIdsForSet(values);
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

    // turns out recreating sets is not so efficient so while the object can accept a new value set it's better too allow it to be changed but not externally

    public synchronized void removeFromValuesWillBePersisted(Value value) throws Exception {
        checkDatabaseMatches(value);// even if not needed throw the damn exception!
        if (values.remove(value)) { // it changed the set
            valuesChanged = true;
            inspectForPersistence = true;
        }
    }

    public Set<Label> getParents() {
        return Collections.unmodifiableSet(parents);
    }

    public synchronized void setParentsWillBePersisted(Set<Label> parents) throws Exception {
        checkDatabaseIdsForSet(parents);
        this.parents = parents;
        parentsChanged = true;
        inspectForPersistence = true;
    }

    public synchronized void addToParentsWillBePersisted(Label parent) throws Exception {
        checkDatabaseMatches(parent);
        if (parents.add(parent)) { // something changed, this was a new label
            parentsChanged = true;
            inspectForPersistence = true;
        }
    }

    /* ok just return it as a Set so we can use unmodifiableSet, this does make the getters and setters a little inconsistent
      but hey ho,
       */

    public Set<Label> getChildren() {
        return Collections.unmodifiableSet(children);
    }
    // as mentioned above, force the set to a linked set, we want it to be ordered :)

    public synchronized void setChildrenWillBePersisted(LinkedHashSet<Label> children) throws Exception {
        checkDatabaseIdsForSet(children);
        this.children = children;
        childrenChanged = true;
        inspectForPersistence = true;
    }

    public Set<Label> getPeers() {
        return Collections.unmodifiableSet(peers);
    }

    public synchronized void setPeersWillBePersisted(LinkedHashSet<Label> peers) throws Exception {
        checkDatabaseIdsForSet(peers);
        this.peers = peers;
        peersChanged = true;
        inspectForPersistence = true;
    }

}
