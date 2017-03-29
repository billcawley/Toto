package com.azquo.memorydb.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 *
 * OK with the new in memory DB thing these objects form the in memory database - it would be awkward to make them immutable
 * as I'd kind of like to so instead we want to make it so that it's very clear that modification after creation will flag them to be persisted.
 * Also, these objects act as data objects hence each object SHOULD only exist in context of a azquo memory db, code here is designed to enforce this
 * id and database id need to be rigidly controlled in this object or all hell could break loose
 * I think I may even go so far as actually holding the object reference to the database as opposed to a database id.
 * If there is a way I don't yet know about for a constructor to only be called by another class this could be simplified
 * So far have seen no performance hits from constraining objects by DB
 *
 * A small note - with overridden functions it's possible that virtual function calling, that is to say functions called from this superclass that have to be worked out at runtime,
 * MIGHT be a performance problem but I've not seen it so far. I was made aware of this in a JVM talk.
 */

public abstract class AzquoMemoryDBEntity {

    static final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // I am going to hold a reference here, then we simply compare objects by == to check that objects are in their created databases

    private final AzquoMemoryDB azquoMemoryDB;
    // final id, can only be set in the constructor, we like that! Note I'm limiting to 2 billion entities total per database. Not a problem right now.
    private final int id;
    // it's a new object, this is private as all dealt with here
    private boolean needsInserting;
    // flag for deletion, to be picked up by the persistence
    boolean needsDeleting;

    //key with this is it makes the setting of an Id only in context of a memory db and hence one can only make one of these in this package (I think!)
    AzquoMemoryDBEntity(final AzquoMemoryDB azquoMemoryDB, final int id) throws Exception {
        this.azquoMemoryDB = azquoMemoryDB;
        // This getNeedsLoading is important, an instance of AzquoMemoryDB should only be in needsLoading during the constructor and hence it will stop
        // other bits of code overriding the entities ID
        if (azquoMemoryDB.getNeedsLoading()) { // building objects from persistence store, rules are different, we can set the id as a parameter
            this.id = id;
            // does not need inserting
            needsInserting = false;
        } else { // normal create - as in a spreadsheet has made a new one
            if (id != 0) {
                throw new Exception("id is trying to be assigned to an entity after the database is loaded!");
            }
            this.id = azquoMemoryDB.getNextId();
            // point of this is attempt at lock down on entity constructors. Any entity that's instantiated is part of the
            // memory database and fair game for persistence
            // if it's not been built with an assigned ID then it needs to be persisted
            setNeedsPersisting();
            // we assume new, inserting true
            needsInserting = true;
        }
        needsDeleting = false;
    }

    // ok, a class that can json serialize needs to override this, not sure if this is best practice but it seems to reduce the amount of code. Otherwise could be a few extra interfaces.
    protected String getAsJson(){
        return null;
    }

    protected final AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
    }

    // no setter for id, that should only be done by the constructor
    public final int getId() {
        return id;
    }

    /*
    Hash is important for sets and maps, it was plain ID but koloboke doesn't like this, this hack from a github comment

  https://github.com/OpenHFT/Koloboke/issues/35

   I know equals being empty is kind of dirty but plain object == is correct for equals in Azquo, this should improve the performance a little (I mean not having the commented equals)

   And this does fulfill the contract - only that equals give the same hash, which it must for ==, not that matching hashes means equals (objects from two different dbs having the same id, they should not be in the same sets anyway)

   One can do this also on the set instantiation but as the Koloboke author says "if you cannot fix the keys' hashCode() implementation" but we can. .withKeyEquivalence(HashCodeMixingEquivalence.INSTANCE) is how it would be done.

   Essentially in going for top speed koloboke trusts the hashes (unlike standard Java hash based collections), you either give it good hashes or tell it to mix them up. Or have poor performance.

     */

    @Override
    public final int hashCode() {
        return getId() * -1640531527; // mix it up as per koloboke advice - we might hit ids of of a few hundred million but often they'll be at the bottom, relatively small range from int possibles of -2billion to +2billion.
    }

    // should be called pretty much wherever objects are going to be added to each others maps/sets/lists

    final void checkDatabaseMatches(final AzquoMemoryDB azquoMemoryDB) throws Exception {
        if (this.azquoMemoryDB != azquoMemoryDB) {
            throw new Exception("Error, objects from different databases interacting!");
        }
    }

    final void checkDatabaseMatches(final AzquoMemoryDBEntity azquoMemoryDBEntity) throws Exception {
        checkDatabaseMatches(azquoMemoryDBEntity.azquoMemoryDB);
    }

    final void checkDatabaseForSet(final Set<? extends AzquoMemoryDBEntity> entities) throws Exception {
        for (AzquoMemoryDBEntity toCheck : entities) {
            checkDatabaseMatches(toCheck);
        }
    }

    void setAsPersisted(){
        needsInserting = false;
    }

    protected abstract void setNeedsPersisting();

    // public for the DAOs
    public final boolean getNeedsInserting() {
        return needsInserting;
    }

    // public for the DAOs
    public final boolean getNeedsDeleting() {
        return needsDeleting;
    }

}