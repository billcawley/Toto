package com.azquo.toto.memorydb;

import com.azquo.toto.memorydbdao.StandardDAO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 * <p/>
 * After some thinking and learning about generics : entity objects should have as little reference to Mysql as possible.
 * Hence I'm going to move the row mapper, table name and column name value map out of here
  * <p/>
 * OK with the new in memory DB thing these objects form the in memory database - it would be awkward to make them immutable
 * as I'd kind of like to so instead we want to make it so that it's very clear that modification after creation will
 * AUTOMATICALLY be reflected in MySQL as it catches up . . .
 * TODO : how to lock an object while it's being persisted??? Note time of modification?
 * <p/>
 * Also, these objects act as data objects hence each object SHOULD only exist in context of a toto memory db
 * Writing the code to check this may be a pain and have some performance hits but the idea that changes to the
 * memory database are not checked by DB id worries me so much I'm going to try now.
 * <p/>
 * in summary : id and database id need to be rigidly controlled in this object or all hell could break loose
 * <p/>
 * I think I may even go so far as actually holding the object reference to the database as opposed to a database id.
 * <p/>
 * If there is a way I don't yet know about for a constructor to only be called by another class this could be simplified
 * <p/>
 * So far have seen no performance hits from constraining objects by DB
 */

public abstract class TotoMemoryDBEntity {

    protected static final ObjectMapper jacksonMapper = new ObjectMapper();

    // I am going to hold a reference here, then we simply compare objects by == to check that objects are in their created databases

    private final TotoMemoryDB totoMemoryDB;

    private final int id;
    // it's a new object, this is private as all dealt with here
    private boolean needsInserting;
    // flag for deletion, to be picked up by the persistence
    protected boolean needsDeleting;

    // I think protected is right here, while the class may be referenced externally (for generics) this makes it difficult to be subclassed externally?
    //key with this is it makes the setting of an Id only in context of a memory db
    protected TotoMemoryDBEntity(final TotoMemoryDB totoMemoryDB, final int id) throws Exception {
        this.totoMemoryDB = totoMemoryDB;
        // This getNeedsLoading is important, an instance of TotoMemoryDB should only be in needsloading during the constructor and hence it will stop
        // other bits of code overriding the entities ID
        if (totoMemoryDB.getNeedsLoading()) { // building objects from persistence (Mysql currently) store, rules are different, we can set the id as a parameter
            this.id = id;
            // does not need inserting
            needsInserting = false;
        } else { // normal create - as in a service has made a new one
            if (id != 0) {
                throw new Exception("id is trying to be assigned to an entity after the database is loaded!");
            }
            this.id = totoMemoryDB.getNextId();
            // point of this is attempt at lockdown on entity constructors. Any entity that's instantiated is part of the
            // memory database and fair game for persistence
            // if it's not been built with an assigned ID then it needs to be persisted
            setNeedsPersisting();
            // we assume new, inserting true
            needsInserting = true;
        }
        needsDeleting = false;
    }

    // each class will say where it's persisted
    protected abstract StandardDAO.PersistedTable getPersistTable();

    // all entities need this to save in the key/pair store
    public abstract String getAsJson();

    // this seems to be the best way too constrain, others in teh package can get the instance but not reset it's value
    protected final TotoMemoryDB getTotoMemoryDB() {
        return totoMemoryDB;
    }

    // no setter for id, that should only be done by the constructor

    public final int getId() {
        return id;
    }

    /* part of why I'm so funny about the ids, these two functions!
     might be unorthodox but these entities are part of the memory DB, their interaction with collections sets etc is crucial
     for speed and data integrity, since id should NOT change over an objects life this should be ok and fast
     */

    @Override
    public final int hashCode() {
        return getId();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true; // this should do the job for much of the memory db matching

        if (o == null || getClass() != o.getClass()) return false;

        TotoMemoryDBEntity totoMemoryDBEntity = (TotoMemoryDBEntity) o;
        return getId() == totoMemoryDBEntity.getId();
    }

    // should be called pretty much wwherever objects are going to be added to each others maps/sets/lists

    protected final void checkDatabaseMatches(final TotoMemoryDB totoMemoryDB) throws Exception {
        if (this.totoMemoryDB != totoMemoryDB) {
            throw new Exception("Error, objects from different databases interacting!");
        }
    }

    protected final void checkDatabaseMatches(final TotoMemoryDBEntity totoMemoryDBEntity) throws Exception {
        checkDatabaseMatches(totoMemoryDBEntity.totoMemoryDB);
    }

    protected final void checkDatabaseForSet(final Set<? extends TotoMemoryDBEntity> entities) throws Exception {
        for (TotoMemoryDBEntity toCheck : entities) {
            checkDatabaseMatches(toCheck);
        }
    }

    protected final void setAsPersisted() {
        needsInserting = false;
        totoMemoryDB.removeEntityNeedsPersisting(getPersistTable(),this);
    }

    protected final void setNeedsPersisting() {
        totoMemoryDB.setEntityNeedsPersisting(getPersistTable(), this);
    }

    // public for the DAOs
    public final boolean getNeedsInserting() {
        return needsInserting;
    }

    // public for the DAOs
    public final boolean getNeedsDeleting() {
        return needsDeleting;
    }


}
