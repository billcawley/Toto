package com.azquo.toto.memorydb;

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
 * I think I may even go so far as actually holding the object reference to the database as opposed to a database id.
 *
 * If there is a way I don't yet know about for a constructor to only be called by another class this could be simplified
 *
 * So far have seen no performance hits from constraining objects by DB
 *
 */

public abstract class TotoMemoryDBEntity {

    // I am going to hold a reference here, then we simply compare objects by == to check that objects are in their created databases
    // ok this was private but I'm going to make it protected, I think the entity implementations need it.

    private final TotoMemoryDB totoMemoryDB;

    private final int id;
    // it's a new object, this is private as all dealt with here
    private boolean needsInserting;
    // the columns for the entity were changed (as opposed to lists for example), sublasses will change this
    protected boolean entityColumnsChanged;
    // I think protected is right here, while the class may be referenced externally (for generics) this makes it difficult to be subclassed externally?
    //key with this is it makes the setting of an In only in context of a memory db
    protected TotoMemoryDBEntity(final TotoMemoryDB totoMemoryDB, final int id) throws Exception {
        this.totoMemoryDB = totoMemoryDB;
        // This getNeedsLoading is important, an instance of TotoMemoryDB should only be in needsloading during the constructor and hence it will stop
        // other bits of code overriding the entities ID
        if (totoMemoryDB.getNeedsLoading()){ // building objects from the disk store, rules are different, we can set the id as a parameter
            this.id = id;
            // and say that it's in sync
            needsInserting = false;
            entityColumnsChanged = false;
        } else { // normal create
            if (id != 0){
                throw new Exception("id is trying to be assigned to an entity after the database is loaded!");
            }
            this.id = totoMemoryDB.getNextId();
            // point of this is attempt at lockdown on entity constructors. Any entity that's instantiated is part of the
            // memory database and fair game for persistence
            // if it's not been built with an assigned ID then it needs to be persisted
            setNeedsPersisting();
            // set them both true by default, if created by loading then the flags will be sorted after.
            needsInserting = true;
            entityColumnsChanged = true;

        }
        addToDb();
    }

    // this is where each subclass should make sure that it is added to the appropriate map in the totodb. Unfortunately this in practice will just be the id map for that object but hey ho
    protected abstract void addToDb() throws Exception;

    // this is where each subclass should make sure that it is added to the appropriate modified list in the
    // not doing this in here as I'd like it class specific ad there may be quirks
    // and we're not going to make a removed, that will be dealt with by totomemorydb object
    protected abstract void setNeedsPersisting();

    //force implementing classes to add functions for their fields when setting persisted

    protected abstract void classSpecificSetAsPersisted();

    // this seems to be the best way too constrain, others in teh package can get the class but not reset it's value

    protected final TotoMemoryDB getTotoMemoryDB(){
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
        if (this == o) return true; // this should do the job fo much of the memory db matching

        if (o == null || getClass() != o.getClass()) return false;

        TotoMemoryDBEntity totoMemoryDBEntity = (TotoMemoryDBEntity) o;
        return getId() == totoMemoryDBEntity.getId();
    }


    protected final void checkDatabaseMatches(final TotoMemoryDB totoMemoryDB) throws Exception {
        if (this.totoMemoryDB != totoMemoryDB){
            throw new Exception("Error, objects from different databases interacting!");
        }
    }

    protected final void checkDatabaseMatches(final TotoMemoryDBEntity totoMemoryDBEntity) throws Exception{
        checkDatabaseMatches(totoMemoryDBEntity.totoMemoryDB);
    }

    protected final void checkDatabaseForSet(final Set<? extends TotoMemoryDBEntity> entities) throws Exception {
        for (TotoMemoryDBEntity toCheck : entities){
            checkDatabaseMatches(toCheck);
        }
    }

    protected final void setAsPersisted(){
        needsInserting = false;
        entityColumnsChanged = false;
        classSpecificSetAsPersisted();
    }
    // public for the DAOs
    public final boolean getNeedsInserting() {
        return needsInserting;
    }

    protected final boolean getEntityColumnsChanged() {
        return entityColumnsChanged;
    }

}
