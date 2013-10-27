package com.azquo.toto.entity;

import com.azquo.toto.memorydb.TotoMemoryDB;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 *
 * After some thinking and learning about generics : entity objects should have as little reference to Mysql as possible.
 * Hence I'm going to move the row mapper, table name and column name value map out of here
 *
 * OK with the new in memory DB thing these objects form the in memory database - it would be awkward to make them immutable
 * as I'd kind of like to so instead we want to make it so that it's very clear that modification after creation will
 * AUTOMATICALLY be reflected in MySQL as it catches up . . .
 * TODO : how to lock an object while it's being persisted??? Note time of modification?
 *
 * Also, these objects act as data objects hence each object SHOULD only exist in context of a toto memory db
 * Writing the code to check this may be a pain and have some performance hits but the idea that changes to the
 * memory database are not checked by DB id worries me so much I'm going to try now.
 *
 * in summary : id and database id need to be rigidly controlled in this object or all hell could break loose
 *
 * I think I may even go so far as actually holding the object reference too the database as opposed to a database id.
 *
 * If there is a way I don't yet know about for a constructor to only be called by another class this could be simplified
 *
 * So far have seen no performance hits from constraining objects by DB
 *
 */

public abstract class TotoMemoryDBEntity {

    // I am going to hold a reference here privately, then we simply compare objects by == to check that objects are in their created databases
    // keep it private for safety

    private TotoMemoryDB totoMemoryDB;

    private int id;
    // as it says, see if any flags are set and do persistence!
    protected boolean inspectForPersistence;
    // it's a new object
    protected boolean needsInserting;
    // the columns for the entity were changed (as opposed to lists for example)
    protected boolean entityColumnsChanged;

    public TotoMemoryDBEntity(TotoMemoryDB totoMemoryDB, int id) throws Exception {
        this.totoMemoryDB = totoMemoryDB;
        // This getNeedsLoading is important, an instance of TotoMemoryDB should only be in needsloading during the constructor and hence it will stop
        // other bits of code overriding the ID
        if (totoMemoryDB.getNeedsLoading()){ // building objects from the disk store, rules are different, we can set the id as a parameter
            this.id = id;
        } else { // normal create
            if (id != 0){
                throw new Exception("id is trying to be assigned to an entity after the database is loaded!");
            }
            this.id = totoMemoryDB.getNextId();
            // point of this is attempt at lockdown on entity constructors. Any entity that's instantiated is part of the
            // memory database and fair game for persistence
        }
        addToDb(totoMemoryDB);
        // set them both true by default, if created by loading then the flags will be sorted after.
        needsInserting = true;
        entityColumnsChanged = true;
    }

    // this is where each subclass should make sure that it is added to the appropriate map in the totodb. Unfortunately this in practice will just be the id map for that object but hey ho
    protected abstract void addToDb(TotoMemoryDB totoMemoryDB) throws Exception;

    // no setter for id, that should only be done by the constructor

    final public int getId() {
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
        if (this == o) return true; // this should do the job fo much of the memory db matching

        if (o == null || getClass() != o.getClass()) return false;

        TotoMemoryDBEntity totoMemoryDBEntity = (TotoMemoryDBEntity) o;
        return getId() == totoMemoryDBEntity.getId();
    }


    public final void checkDatabaseMatches(TotoMemoryDB totoMemoryDB) throws Exception {
        if (this.totoMemoryDB != totoMemoryDB){
            throw new Exception("Error, objects from different databases interacting!");
        }
    }

    public final void checkDatabaseMatches(TotoMemoryDBEntity totoMemoryDBEntity) throws Exception{
        checkDatabaseMatches(totoMemoryDBEntity.totoMemoryDB);
    }

    public final void checkDatabaseForSet(Set<? extends TotoMemoryDBEntity> entities) throws Exception {
        for (TotoMemoryDBEntity toCheck : entities){
            checkDatabaseMatches(toCheck);
        }
    }

    public void syncedToDB(){
        inspectForPersistence = false;
        needsInserting = false;
        entityColumnsChanged = false;
    }

    public final boolean getNeedsInserting() {
        return needsInserting;
    }

    public final boolean getEntityColumnsChanged() {
        return entityColumnsChanged;
    }

    public final boolean getInspectForPersistence() {
        return inspectForPersistence;
    }

}
