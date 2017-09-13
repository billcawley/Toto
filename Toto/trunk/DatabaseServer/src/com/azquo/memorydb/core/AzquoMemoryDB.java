package com.azquo.memorydb.core;

import com.azquo.memorydb.service.DSAdminService;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * <p>
 * This class represents the azquo database itself though more practically it's holding references to all entities by id and dealing with locking values.
 * <p>
 * Also references to instances of this class are held against each entity in that database.
 * <p>
 * The entities define how they relate to each other, that's not done here.
 * <p>
 * Created after it became apparent that Mysql in the way I'd arranged the objects didn't have a hope in hell of
 * delivering data fast enough. Use collections to implement Azquo spec.
 * <p>
 */

public final class AzquoMemoryDB {

    private static Properties azquoProperties = new Properties();

    // no point doing this on every constructor!
    static {
        try {
            azquoProperties.load(AzquoMemoryDB.class.getClassLoader().getResourceAsStream("azquo.properties")); // easier than messing around with spring
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final Logger logger = Logger.getLogger(AzquoMemoryDB.class);

    // The old memory db manager logic is going in here, want to make the constructor private
    private static final ConcurrentHashMap<String, AzquoMemoryDB> memoryDatabaseMap = new ConcurrentHashMap<>(); // by data store name. Will be unique

    // vanilla use of ConcurrentHashMap, should be fine
    public static AzquoMemoryDB getAzquoMemoryDB(String persistenceName, StringBuffer sessionLog) throws Exception {
        return memoryDatabaseMap.computeIfAbsent(persistenceName, t -> new AzquoMemoryDB(persistenceName, sessionLog));
        // open database logging could maybe be added back in client side
    }

    // worth being aware that if the db is still referenced somewhere then the garbage collector won't chuck it (which is what we want)
    public static void removeDBFromMap(String persistenceName) throws Exception {
        memoryDatabaseMap.remove(persistenceName);
    }

    public static boolean isDBLoaded(String persistenceName) throws Exception {
        return memoryDatabaseMap.containsKey(persistenceName);
    }

    // end static stuff (excepting some counters), now on to instance

    private final AzquoMemoryDBIndex index;

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    private final AzquoMemoryDBTransport azquoMemoryDBTransport;

    // to manage value locking, first says when a given user last put on a lock
    private final Map<String, LocalDateTime> valueLockTimes;
    // and the specific values which are locked
    private final Map<Value, String> valueLocks;

    // does this database need loading from the data store, a significant flag that affects rules for memory db entity instantiation for example
    // should it be volatile or AtomicBoolean? Danger is that certain things are allowed while loading that are not after.
    private boolean needsLoading;

    // no need to max id at load, it's used for this
    private final AtomicInteger nextId = new AtomicInteger(0); // new java 8 stuff should allow me to safely get the top id regardless of multiple threads

    private static AtomicInteger newDatabaseCount = new AtomicInteger(0);

    /*

    I need to consider this https://www.securecoding.cert.org/confluence/display/java/TSM03-J.+Do+not+publish+partially+initialized+objects

    "During initialization of a shared object, the object must be accessible only to the thread constructing it.
    However, the object can be published safely (that is, made visible to other threads) once its initialization is complete.
    The Java memory model (JMM) allows multiple threads to observe the object after its initialization has begun but before it has concluded.
    Consequently, programs must prevent publication of partially initialized objects.

    This rule prohibits publishing a reference to a partially initialized member object instance before initialization has concluded.
    It specifically applies to safety in multithreaded code. TSM01-J.
    Do not let the this reference escape during object construction prohibits the this reference of the current object from escaping its constructor.
    OBJ11-J. Be wary of letting constructors throw exceptions describes the consequences of publishing partially initialized objects even in single-threaded programs."

    Of course this has to escape here unless we init all the entities without a reference to the azquo memory db then add it in after.
    I've moved the memory db map in here so the constructor can now be private. I think this is no bad thing.
     */

    private AzquoMemoryDB(String persistenceName, StringBuffer sessionLog) {
        newDatabaseCount.incrementAndGet();
        index = new AzquoMemoryDBIndex();
        needsLoading = true;
        // is it dodgy letting "this" escape here? As a principle yes but these objects that use "this" are held against the instance this constructor is making
        // to put it another way - AzquoMemoryDBTransport could be an inner class but I'm trying to factor such stuff off.
        azquoMemoryDBTransport = new AzquoMemoryDBTransport(this, persistenceName, sessionLog);
        nameByIdMap = new ConcurrentHashMap<>();
        valueByIdMap = new ConcurrentHashMap<>();
        provenanceByIdMap = new ConcurrentHashMap<>();
        valueLockTimes = new ConcurrentHashMap<>();
        valueLocks = new ConcurrentHashMap<>();
        boolean memoryTrack = "true".equals(azquoProperties.getProperty("memorytrack"));
        // loading in here was synchronized but only one constructor can be run, should be ok.
        // internally all the loading uses future gets to this thread SHOULD all be in sync with loaded data.
        // It is then being jammed into a ConcurrentHashMap so by the time it's called from said map all should be visible in memory correctly.
        azquoMemoryDBTransport.loadData(memoryTrack);
        nextId.incrementAndGet(); // bump it up one, the logic later is get and increment;
        needsLoading = false;
    }

    boolean getNeedsLoading() {
        return needsLoading;
    }

    // I'm going to force the import to wait if persisting or the like is going on
    public synchronized void lockTest() {
    }

    public String getPersistenceName() {
        return azquoMemoryDBTransport.getPersistenceName();
    }
    // reads from a list of changed objects
    // should we synchronize on a write lock object? I think it might be a plan.

    public synchronized void persistToDataStore() {
        // I was going to write lock the DB here but since I've been defensive when persisting I don't think it's necessary,
        // the worst that SHOULD happen is that an entity is persisted twice.
        // See AzquoMemoryDBTransport - more specifically I'm currently going to make it the responsibility of AzquoMemoryDBTransport
        azquoMemoryDBTransport.persistDatabase();
    }

    int getNextId() {
        return nextId.getAndIncrement(); // should correctly replace the old logic, exactly what atomic ints are for.
    }

    // while loading check that the id of the entity just loaded isn't above the current "next" id
    void setNextId(int newNext) {
        nextId.getAndUpdate(current -> current < newNext ? newNext : current);
    }

    public AzquoMemoryDBIndex getIndex() {
        return index;
    }

    // for debug purposes, is there a harm in being public??

    /*public int getCurrentMaximumId() {
        return nextId;
    }*/

    public Name getNameById(final int id) {
        return nameByIdMap.get(id);
    }

    public int getNameCount() {
        return nameByIdMap.size();
    }

    Collection<Name> getAllNames() {
        return nameByIdMap.values();
    }

    // these two for database integrity check
    public Collection<Integer> getAllNameIds() {
        return Collections.unmodifiableCollection(nameByIdMap.keySet());
    }

    public Collection<Integer> getAllValueIds() {
        return Collections.unmodifiableCollection(valueByIdMap.keySet());
    }

    public Value getValueById(final int id) {
        return valueByIdMap.get(id);
    }

    public int getValueCount() {
        return valueByIdMap.size();
    }

    private static AtomicInteger findTopNames2Count = new AtomicInteger(0);

    public List<Name> findTopNames() {
        findTopNames2Count.incrementAndGet();
        final List<Name> toReturn = new ArrayList<>();
        for (Name name : nameByIdMap.values()) {
            if (!name.hasParents()) {
                toReturn.add(name);
            }
        }
        return toReturn;
    }
    // this could be expensive on big databases

    private static AtomicInteger clearCachesCount = new AtomicInteger(0);

    public void clearCaches() {
        clearCachesCount.incrementAndGet();
        nameByIdMap.values().forEach(com.azquo.memorydb.core.Name::clearChildrenCaches);
        //countCache.clear();
        //setCache.clear();
    }

    // trying for a basic count and set cache

    Provenance getProvenanceById(final int id) {
        return provenanceByIdMap.get(id);
    }

    // would need to be synchronized if not on a concurrent map

    void addNameToDb(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        if (nameByIdMap.putIfAbsent(newName.getId(), newName) != null) {
            throw new Exception("tried to add a name to the database with an existing id!");
        }
    }

    void removeNameFromDb(final Name toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        nameByIdMap.remove(toRemove.getId());
    }

    void removeValueFromDb(final Value toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        valueByIdMap.remove(toRemove.getId());
    }

    /* Json then custom ones, maybe refactor later*/

    private static AtomicInteger setJsonEntityNeedsPersistingCount = new AtomicInteger(0);

    void setJsonEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity entity) {
        setJsonEntityNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            azquoMemoryDBTransport.setJsonEntityNeedsPersisting(tableToPersistIn, entity);
        }
    }

    private static AtomicInteger setNameNeedsPersistingCount = new AtomicInteger(0);

    void setNameNeedsPersisting(Name name) {
        setNameNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            azquoMemoryDBTransport.setNameNeedsPersisting(name);
        }
    }

    private static AtomicInteger setValueNeedsPersistingCount = new AtomicInteger(0);

    void setValueNeedsPersisting(Value value) {
        setValueNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            azquoMemoryDBTransport.setValueNeedsPersisting(value);
        }
    }

    void addValueToDb(final Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.putIfAbsent(newValue.getId(), newValue) != null) { // != null means there was something in there, this really should not happen hence the exception
            throw new Exception("tried to add a value to the database with an existing id!");
        }
    }

    private volatile AtomicReference<Provenance> mostRecentProvenance = new AtomicReference<>();

    // last modified according to provenance!
    private final AtomicLong lastModified = new AtomicLong();

    void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.putIfAbsent(newProvenance.getId(), newProvenance) != null) {
            throw new Exception("tried to add a provenance to the database with an existing id!");
        }
        mostRecentProvenance.getAndUpdate(provenance -> provenance != null && provenance.getTimeStamp().after(newProvenance.getTimeStamp()) ? provenance : newProvenance);
        lastModified.getAndUpdate(n -> n < newProvenance.getTimeStamp().getTime() ? newProvenance.getTimeStamp().getTime() : n); // think that logic is correct for thread safety
    }

    public long getLastModifiedTimeStamp() {
        return lastModified.get();
    }

    public Provenance getMostRecentProvenance() {
        return mostRecentProvenance.get();
    }

    // should lock stuff be in a different class?
    // note - this function will NOT check for existing locks for these values, it just sets time for this user then sets new locks
    public void setValuesLockForUser(Collection<Value> values, String userId) {
        valueLockTimes.put(userId, LocalDateTime.now());
        for (Value value : values) {
            valueLocks.put(value, userId);
        }
    }

    public void removeValuesLockForUser(String userId) {
        if (valueLockTimes.remove(userId) != null) { // only check the values if there was an entry in time so to speak
            valueLocks.values().removeAll(Collections.singleton(userId)); // need to force a collection rather than an instance to remove all values in the map that match. Hence do NOT remove .singleton here!
        }
        // and remove all old locks for all users
        removeOldLocks(60); // arbitrary time, this seems as good a palce as any to check
    }

    private void removeOldLocks(int minutesAllowed) {
        for (String user : valueLockTimes.keySet()) {
            final LocalDateTime lockTime = valueLockTimes.get(user);
            if (ChronoUnit.MINUTES.between(lockTime, LocalDateTime.now()) > minutesAllowed) {
                removeValuesLockForUser(user);
            }
        }
    }

    public boolean hasLocksAsideFromThisUser(String userId) {// intellij simplified, not sure how clear it is - empty = false, one is false too if it's the user we care about
        return !valueLockTimes.isEmpty() && !(valueLockTimes.size() == 1 && valueLockTimes.keySet().iterator().next().equals(userId));
    }

    public String checkLocksForValueAndUser(String userId, Collection<Value> valuesToCheck) {
        if (valueLockTimes.isEmpty() || (valueLockTimes.size() == 1 && valueLockTimes.get(userId) != null)) {
            return null; // what I'll call "ok" for the mo
        }
        Map<Value, String> locksCopy = HashObjObjMaps.newMutableMap(valueLocks);
        locksCopy.values().removeAll(Collections.singleton(userId)); // so all the locks that are related to this user are removed
        locksCopy.keySet().retainAll(valuesToCheck); // this should leave in locksCopy the values locked by another user
        if (locksCopy.size() > 0) {
            String anotherUser = locksCopy.values().iterator().next(); // just grab the first?
            return anotherUser + ", time : " + valueLockTimes.get(anotherUser); // maybe we can pass the formatting to the client later, or this could be considered a db message
        }
        return null;
    }

    // I may change these later, for the mo I just want to stop drops and clears at the same time as persistToDataStore
    public synchronized void synchronizedClear() throws Exception {
        DSAdminService.emptyDatabaseInPersistence(azquoMemoryDBTransport.getPersistenceName());
    }

    public synchronized void synchronizedDrop() throws Exception {
        DSAdminService.dropDatabaseInPersistence(azquoMemoryDBTransport.getPersistenceName());
    }

    private static void printFunctionCountStats() {
        System.out.println("######### AZQUO MEMORY DB FUNCTION COUNTS");
        System.out.println("newDatabaseCount\t\t\t\t" + newDatabaseCount.get());
        System.out.println("findTopNames2Count\t\t\t\t" + findTopNames2Count.get());
        System.out.println("clearCachesCount\t\t\t\t" + clearCachesCount.get());
        System.out.println("setJsonEntityNeedsPersistingCount\t\t\t\t" + setJsonEntityNeedsPersistingCount.get());
        System.out.println("setNameNeedsPersistingCount\t\t\t\t" + setNameNeedsPersistingCount.get());
        System.out.println("setValueNeedsPersistingCount\t\t\t\t" + setValueNeedsPersistingCount.get());
    }

    private static void clearFunctionCountStats() {
        NameUtils.clearAtomicIntegerCounters(newDatabaseCount,
                findTopNames2Count,
                clearCachesCount,
                setJsonEntityNeedsPersistingCount,
                setNameNeedsPersistingCount,
                setValueNeedsPersistingCount);
    }

    // debug stuff, I'll allow the warnings for the moment. Really need calls to be based off a flag, not commented/uncommented
    public static void printAllCountStats() {
        printFunctionCountStats();
        Name.printFunctionCountStats();
        Value.printFunctionCountStats();
        NameService.printFunctionCountStats();
        ValueService.printFunctionCountStats();
    }

    public static void clearAllCountStats() {
        clearFunctionCountStats();
        Name.clearFunctionCountStats();
        Value.clearFunctionCountStats();
        NameService.clearFunctionCountStats();
        ValueService.clearFunctionCountStats();
    }
}