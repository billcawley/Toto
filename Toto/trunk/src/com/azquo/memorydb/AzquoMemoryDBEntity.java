package com.azquo.memorydb;

import com.azquo.memorydbdao.StandardDAO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 * After some thinking and learning about generics : entity objects should have as little reference to Mysql as possible - the table they live in should be it?
 * OK with the new in memory DB thing these objects form the in memory database - it would be awkward to make them immutable
 * as I'd kind of like to so instead we want to make it so that it's very clear that modification after creation will
 * AUTOMATICALLY be reflected in MySQL as it catches up . . .
 * TODO : how to lock an object while it's being persisted??? Note time of modification?
 * Also, these objects act as data objects hence each object SHOULD only exist in context of a azquo memory db, code here is designed to enforce this
 * id and database id need to be rigidly controlled in this object or all hell could break loose
 * I think I may even go so far as actually holding the object reference to the database as opposed to a database id.
 * If there is a way I don't yet know about for a constructor to only be called by another class this could be simplified
 * So far have seen no performance hits from constraining objects by DB
 */

public abstract class AzquoMemoryDBEntity {

    protected static final ObjectMapper jacksonMapper = new ObjectMapper();

    // I am going to hold a reference here, then we simply compare objects by == to check that objects are in their created databases

    private final AzquoMemoryDB azquoMemoryDB;
    // final id, can on;y be set in the constructor, we like that!
    private final int id;
    // it's a new object, this is private as all dealt with here
    private boolean needsInserting;
    // flag for deletion, to be picked up by the persistence
    protected boolean needsDeleting;

    //key with this is it makes the setting of an Id only in context of a memory db and hence one can only make one of these in this package (I think!)
    protected AzquoMemoryDBEntity(final AzquoMemoryDB azquoMemoryDB, final int id) throws Exception {
        this.azquoMemoryDB = azquoMemoryDB;
        // This getNeedsLoading is important, an instance of AzquoMemoryDB should only be in needsloading during the constructor and hence it will stop
        // other bits of code overriding the entities ID
        if (azquoMemoryDB.getNeedsLoading()) { // building objects from persistence (Mysql currently) store, rules are different, we can set the id as a parameter
            this.id = id;
            // does not need inserting
            needsInserting = false;
        } else { // normal create - as in a service has made a new one
            if (id != 0) {
                throw new Exception("id is trying to be assigned to an entity after the database is loaded!");
            }
            this.id = azquoMemoryDB.getNextId();
            // point of this is attempt at lockdown on entity constructors. Any entity that's instantiated is part of the
            // memory database and fair game for persistence
            // if it's not been built with an assigned ID then it needs to be persisted
            setNeedsPersisting();
            // we assume new, inserting true
            needsInserting = true;
        }
        needsDeleting = false;
    }

    // each class will say where it's persisted - this seems to be the most simple way to indicate shich persistence set to put this in
    protected abstract StandardDAO.PersistedTable getPersistTable();

    // all entities need this to save in the key/pair store
    public abstract String getAsJson();

    // this seems to be the best way to constrain, others in teh package can get the instance but not reset it's value
    // not that they could anyway, it's final
    protected final AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
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

        AzquoMemoryDBEntity azquoMemoryDBEntity = (AzquoMemoryDBEntity) o;
        return getId() == azquoMemoryDBEntity.getId();
    }

    // should be called pretty much wherever objects are going to be added to each others maps/sets/lists

    protected final void checkDatabaseMatches(final AzquoMemoryDB azquoMemoryDB) throws Exception {
        if (this.azquoMemoryDB != azquoMemoryDB) {
            throw new Exception("Error, objects from different databases interacting!");
        }
    }

    protected final void checkDatabaseMatches(final AzquoMemoryDBEntity azquoMemoryDBEntity) throws Exception {
        checkDatabaseMatches(azquoMemoryDBEntity.azquoMemoryDB);
    }

    protected final void checkDatabaseForSet(final Set<? extends AzquoMemoryDBEntity> entities) throws Exception {
        for (AzquoMemoryDBEntity toCheck : entities) {
            checkDatabaseMatches(toCheck);
        }
    }

    protected final void setAsPersisted() {
        needsInserting = false;
        azquoMemoryDB.removeEntityNeedsPersisting(getPersistTable(), this);
    }

    protected final void setNeedsPersisting() {
        azquoMemoryDB.setEntityNeedsPersisting(getPersistTable(), this);
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
