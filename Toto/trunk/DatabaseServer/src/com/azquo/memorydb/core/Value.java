package com.azquo.memorydb.core;

//import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 22/10/13
 * Time: 22:31
 * To reflect a fundamental Azquo idea : a piece of data which has names attached
 * Notable that the names list object here is what defines the relationship between values and names, value sets against each name is just a lookup
 * I'm using an array internally to save memory,
 * <p>
 * Should store a double value also? Will it save loads of parsing?
 */
public final class Value extends AzquoMemoryDBEntity {

    //private static final Logger logger = Logger.getLogger(Value.class);

    // issue of final here and bits of the init being in a try block. Need to have a little think about that
    private Provenance provenance;
    private String text;//no longer final.   May be adjusted during imports (if duplicate lines are found will sum...)

    // changing to array to save memory
    private Name[] names;

    // to be used by the code when creating a new value
    // add the names after
    private static AtomicInteger newValueCount = new AtomicInteger(0);

    public Value(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, final String text) throws Exception {
        super(azquoMemoryDB, 0);
        newValueCount.incrementAndGet();
        this.provenance = provenance;
        this.text = text;
        names = new Name[0];
        // added 10/12/2014, wasn't there before, why??? I suppose it just worked. Inconsistent though!
        getAzquoMemoryDB().addValueToDb(this);
    }

    // todo - can we stop it being public?
    private static AtomicInteger newValue3Count = new AtomicInteger(0);

    public Value(final AzquoMemoryDB azquoMemoryDB, final int id, final int provenanceId, String text, byte[] namesCache) throws Exception {
        super(azquoMemoryDB, id);
        newValue3Count.incrementAndGet();
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId);
        this.text = text.intern();
        // ok populate the names here but unlike before we're not doing any managing of the names themselves (as in adding this value to them), this just sets the names straight in there so to speak
        int noNames = namesCache.length / 4;
        ByteBuffer byteBuffer = ByteBuffer.wrap(namesCache);
        // we assume the names are loaded (though they may not be linked yet)
        Name[] newNames = new Name[noNames];
        for (int i = 0; i < noNames; i++) {
            newNames[i] = getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4));
        }
        this.names = newNames;
        // this should be fine being handled here while loading a db - was storing this against the name but too much space
        for (Name newName : this.names) {
            newName.addToValues(this, true); // true here means don't check duplicates
        }
        getAzquoMemoryDB().addValueToDb(this);
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "Value{" +
                "id=" + getId() +
                ", provenance=" + provenance +
                ", text='" + text + '\'' +
                '}';
    }

    private static AtomicInteger getNamesCount = new AtomicInteger(0);

    public Collection<Name> getNames() {
        getNamesCount.incrementAndGet();
        return Collections.unmodifiableList(Arrays.asList(names)); // should be ok?
    }

    // make sure to adjust the values lists on the name objects :)
    // synchronised but I'm not sure if this is good enough? Certainly better then nothing
    // change to a list internally

    private static AtomicInteger setNamesWillBePersistedCount = new AtomicInteger(0);

    public synchronized void setNamesWillBePersisted(final Set<Name> newNameSet) throws Exception {
        setNamesWillBePersistedCount.incrementAndGet();
        checkDatabaseForSet(newNameSet);
        // remove from where it is right now
        for (Name oldName : this.names) {
            oldName.removeFromValues(this);
        }
        // same as above, copy into a local array before switching over
        Name[] newNames = new Name[newNameSet.size()];
        newNameSet.toArray(newNames);
        this.names = newNames; // keep it atomic - don't throw the array from here into toArray where there will be an array copy
        // set this against names on the new list
        for (Name newName : this.names) {
            newName.addToValues(this);
        }
        setNeedsPersisting();
    }

    // I think that's all that's needed now we're not saving deleted info
    private static AtomicInteger deleteCount = new AtomicInteger(0);

    public void delete() throws Exception {
        deleteCount.incrementAndGet();
        needsDeleting = true;
        for (Name newName : this.names) {
            newName.removeFromValues(this);
        }
        setNeedsPersisting();
    }

    @Override
    protected void entitySpecificSetAsPersisted() {
        getAzquoMemoryDB().removeValueNeedsPersisting(this);
    }

    @Override
    protected void entitySpecificSetNeedsPersisting() {
        getAzquoMemoryDB().setValueNeedsPersisting(this);
    }

    private static AtomicInteger getNameIdsAsBytesCount = new AtomicInteger(0);

    public byte[] getNameIdsAsBytes() {
        getNameIdsAsBytesCount.incrementAndGet();
        ByteBuffer buffer = ByteBuffer.allocate(names.length * 4);
        for (Name name : names) {
            buffer.putInt(name.getId());
        }
        return buffer.array();
    }

    public static void printFunctionCountStats() {
        System.out.println("######### VALUE FUNCTION COUNTS");
        System.out.println("newValueCount\t\t\t\t\t\t\t\t" + newValueCount.get());
        System.out.println("newValue3COunt\t\t\t\t\t\t\t\t" + newValue3Count.get());
        System.out.println("getNamesCount\t\t\t\t\t\t\t\t" + getNamesCount.get());
        System.out.println("setNamesWillBePersistedCount\t\t\t\t\t\t\t\t" + setNamesWillBePersistedCount.get());
        System.out.println("deleteCount\t\t\t\t\t\t\t\t" + deleteCount.get());
        System.out.println("getNameIdsAsBytesCount\t\t\t\t\t\t\t\t" + getNameIdsAsBytesCount.get());
    }

    public static void clearFunctionCountStats() {
        newValueCount.set(0);
        newValue3Count.set(0);
        getNamesCount.set(0);
        setNamesWillBePersistedCount.set(0);
        deleteCount.set(0);
        getNameIdsAsBytesCount.set(0);
    }
}