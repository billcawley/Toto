package com.azquo.memorydb.core;

//import org.apache.log4j.Logger;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 22/10/13
 * Time: 22:31
 * This class represents a fundamental Azquo idea : a piece of data which has names attached.
 * Notable that the names list object here is what defines the relationship between values and names, value sets against each name is just a lookup
 * I'm using an array internally to save memory, there's an assumption it won't ever get that big. Like a CopyOnWriteArray but I'm saving a few bytes of memory.
 * <p>
 */
public final class Value extends AzquoMemoryDBEntity {

    //private static final Logger logger = Logger.getLogger(Value.class);

    private Provenance provenance;
    // todo, consider alternate value representation. Double option with this null? Char array?
    // Notable that getText is used in all of 19 places so this is doable though it would need changes to the DAO also.
    // Empty String is about 40 bytes I think so maybe 30 bytes saved if there's a null pointer and double instead.
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
        //alter the persistence to deal with longer value lengths
        if (text.length() > 255){
            getAzquoMemoryDB().checkValueLengths();
        }
        this.text = text;
        names = new Name[0];
        getAzquoMemoryDB().addValueToDb(this);
    }

    private static AtomicInteger newValue3Count = new AtomicInteger(0);

    public Value(final AzquoMemoryDB azquoMemoryDB, final int id, final int provenanceId, String text, byte[] namesCache) throws Exception {
        this(azquoMemoryDB, id, provenanceId, text, namesCache, false);
    }

    Value(final AzquoMemoryDB azquoMemoryDB, final int id, final int provenanceId, String text, byte[] namesCache, boolean forceIdForBackup) throws Exception {
        super(azquoMemoryDB, id, forceIdForBackup);
        newValue3Count.incrementAndGet();
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId);
        this.text = text.intern(); // important for memory, use the string pool where there will be any strings that are simple numbers
        //alter the persistence to deal with longer value lengths
        if (text.length() > 255){
            getAzquoMemoryDB().checkValueLengths();
        }
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
            newName.checkValue(this, forceIdForBackup);
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

    /* make sure to adjust the values lists on the name objects :)
     synchronised but I'm not sure if this is good enough? One problem is names/values not being 100% in sync
     (remove value from names, reassign names here, add value to new names). Not sure if it's a big deal or if there's even a way around that.
    */

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
        setNeedsDeleting();
        for (Name newName : this.names) {
            newName.removeFromValues(this);
        }
        getAzquoMemoryDB().removeValueFromDb(this);
        setNeedsPersisting();
    }

    // I think that's all that's needed now we're not saving deleted info
    private static AtomicInteger deleteNoHistoryCount = new AtomicInteger(0);

    public void deleteNoHistory() throws Exception {
        // ok this is hacky but I don't want to spare the memory on another boolean
        // this will tell the persist NOT to put this value in value history
        // relevant initially as when a name is zapped and the associated values are then those values are not to be put in value history
        // might NPE let's see . . .
        text = null;
        delete();
        deleteNoHistoryCount.incrementAndGet();
    }

    @Override
    protected void setNeedsPersisting() {
        getAzquoMemoryDB().setValueNeedsPersisting(this);
    }

    private static AtomicInteger getNameIdsAsBytesCount = new AtomicInteger(0);

    // now follows same pattern as the function for children ids in Name. Highly unlikely that the number of names will change during the function but it's possible
    public byte[] getNameIdsAsBytes() {
        return getNameIdsAsBytes(0);
    }

    private byte[] getNameIdsAsBytes(int tries) {
        getNameIdsAsBytesCount.incrementAndGet();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(names.length * 4);
            for (Name name : names) {
                buffer.putInt(name.getId());
            }
            return buffer.array();
        } catch (BufferOverflowException e) {
            if (tries < 5) { // a bit arbitrary
                tries++;
                System.out.println("retrying after buffer overflow in getNameIdsAsBytes on value id " + getId() + " try number " + tries);
                return getNameIdsAsBytes(tries);
            } else {
                throw e;
            }
        }
    }

    // used when deleting values and the value needs to be put in the values history table. We want an index by a combination of name ids
    // , that requires ordering of the ids
    public byte[] getNameIdsAsBytesSorted() {
        getNameIdsAsBytesCount.incrementAndGet();
        byte[] nameIdsAsBytes = getNameIdsAsBytes();
        Arrays.sort(nameIdsAsBytes); // I hope the same as the below! Hard to check. Could case a problem if different and there's existing data. Code tighter though, want to leave this here.
//        Arrays.sort(namesCopy, Comparator.comparing(Name::getId)); // I think that will sort it!
        return nameIdsAsBytes;
    }


    boolean hasName(Name name) {
        for (Name test : names) {
            if (test == name) {
                return true;
            }
        }
        return false;
    }

    static void printFunctionCountStats() {
        System.out.println("######### VALUE FUNCTION COUNTS");
        System.out.println("newValueCount\t\t\t\t\t\t\t\t" + newValueCount.get());
        System.out.println("newValue3COunt\t\t\t\t\t\t\t\t" + newValue3Count.get());
        System.out.println("getNamesCount\t\t\t\t\t\t\t\t" + getNamesCount.get());
        System.out.println("setNamesWillBePersistedCount\t\t\t\t\t\t\t\t" + setNamesWillBePersistedCount.get());
        System.out.println("deleteCount\t\t\t\t\t\t\t\t" + deleteCount.get());
        System.out.println("getNameIdsAsBytesCount\t\t\t\t\t\t\t\t" + getNameIdsAsBytesCount.get());
    }

    static void clearFunctionCountStats() {
        NameUtils.clearAtomicIntegerCounters(newValueCount
                , newValue3Count
                , getNamesCount
                , setNamesWillBePersistedCount
                , deleteCount
                , getNameIdsAsBytesCount);
    }
}