package com.azquo.toto.entity;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 *
 * After some thinking and learning about generics : entity objects should have as little reference to the database as possible.
 * Hence I'm going to move the row mapper, table name and column name value map out of here. All we can really say about an Entity at the
 * moment is that it has an id.
 *
 * OK with the new in memory DB thing these objects form the in memory database - it would be awkward to make them immutable
 * as I'd kind of like too so instead we want to make it so that it's very clear that modification after creation will
 * AUTOMATICALLY be reflected in MySQL as it catches up . . .
 *TODO : how to lock an object while it's being persisted???
 */

public abstract class StandardEntity{

    protected int id;

    // as it says, see if any flags are set and doo persistence!
    protected boolean inspectForPersistence;
    // it's a new object
    protected boolean needsInserting;
    // the columns for the entity were changed (as opposed to lists for example)
    protected boolean entityColumnsChanged;

    public StandardEntity(){
        // set them both true by default, if created by loading then the flags will be sorted after.
        needsInserting = true;
        entityColumnsChanged = true;
    }

    // no setter for id, that should only be done by the constructor

    public int getId() {
        return id;
    }

    public void syncedToDB(){
        inspectForPersistence = false;
        needsInserting = false;
        entityColumnsChanged = false;
    }

    public boolean getNeedsInserting() {
        return needsInserting;
    }

    public boolean getEntityColumnsChanged() {
        return entityColumnsChanged;
    }

    public boolean getInspectForPersistence() {
        return inspectForPersistence;
    }

}
