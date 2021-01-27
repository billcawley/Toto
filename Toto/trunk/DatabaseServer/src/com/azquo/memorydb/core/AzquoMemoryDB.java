package com.azquo.memorydb.core;

import com.azquo.StringLiterals;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.service.DSAdminService;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copyright (C) 2019 Azquo Ltd.
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

    // todo - move these somewhere else
    private static Properties azquoProperties = new Properties();
    private static final String host;
    private static final String GROOVYDIR = "groovydir";
    private static final String RMIIP = "rmiip";
    private static final String MAXTHREADS = "maxthreads";

    // no point doing this on every constructor!
    static {
        try {
            azquoProperties.load(AzquoMemoryDB.class.getClassLoader().getResourceAsStream("azquo.properties")); // easier than messing around with spring
        } catch (IOException e) {
            e.printStackTrace();
        }
        String thost = "";
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // may as well in case it goes wrong
        }
        System.out.println("host : " + thost);
        System.out.println("Available processors : " + Runtime.getRuntime().availableProcessors());
        host = thost;
    }

    private static String groovyDir = null;

    public static String getGroovyDir() {
        if (groovyDir == null) {
            groovyDir = azquoProperties.getProperty(host + "." + GROOVYDIR);
            if (groovyDir == null) {
                groovyDir = azquoProperties.getProperty(GROOVYDIR);
            }
        }
        return groovyDir;
    }

    private static String rmiip = null;

    public static String getRMIIP() {
        if (rmiip == null) {
            rmiip = azquoProperties.getProperty(host + "." + RMIIP);
            if (rmiip == null) {
                rmiip = azquoProperties.getProperty(RMIIP);
            }
        }
        return rmiip;
    }

    private static String maxthreads = null;

    public static String getMaxthreads() {
        if (maxthreads == null) {
            maxthreads = azquoProperties.getProperty(host + "." + MAXTHREADS);
            if (maxthreads == null) {
                maxthreads = azquoProperties.getProperty(MAXTHREADS);
            }
        }
        return maxthreads;
    }

//    private static final Logger logger = Logger.getLogger(AzquoMemoryDB.class);

    // The old memory db manager logic is going in here, want to make the constructor private
    private static final ConcurrentHashMap<String, AzquoMemoryDB> memoryDatabaseMap = new ConcurrentHashMap<>(); // by data store name. Will be unique

    // vanilla use of ConcurrentHashMap, should be fine
    public static AzquoMemoryDB getAzquoMemoryDB(String persistenceName, StringBuffer sessionLog) {
        return memoryDatabaseMap.computeIfAbsent(persistenceName, t -> new AzquoMemoryDB(persistenceName, null, sessionLog));
        // open database logging could maybe be added back in client side
    }

    public static boolean copyExists(DatabaseAccessToken databaseAccessToken) {
        return memoryDatabaseMap.containsKey(StringLiterals.copyPrefix + databaseAccessToken.getUserId() + databaseAccessToken.getPersistenceName());
    }

    // vanilla use of ConcurrentHashMap, should be fine
    public static AzquoMemoryDB getCopyOfAzquoMemoryDB(DatabaseAccessToken databaseAccessToken) {
        return memoryDatabaseMap.computeIfAbsent(StringLiterals.copyPrefix + databaseAccessToken.getUserId() + databaseAccessToken.getPersistenceName(), t -> {
                    AzquoMemoryDB sourceDB = getAzquoMemoryDB(databaseAccessToken.getPersistenceName(), null);
                    Provenance mostRecentProvenance = sourceDB.getMostRecentProvenance();
                    AzquoMemoryDB toReturn = new AzquoMemoryDB(null, sourceDB, null);
                    while (mostRecentProvenance != sourceDB.getMostRecentProvenance()) {
                        System.out.println("Copying again as source DB changed " + databaseAccessToken.getPersistenceName());
                        // this means the source db changed in the mean time, try again
                        mostRecentProvenance = sourceDB.getMostRecentProvenance();
                        toReturn = new AzquoMemoryDB(null, sourceDB, null);
                    }
                    return toReturn;
                }
        );
    }

    // *should* make it available for garbage collection
    public static void zapTemporarayCopyOfAzquoMemoryDB(DatabaseAccessToken databaseAccessToken) {
        memoryDatabaseMap.remove(StringLiterals.copyPrefix + databaseAccessToken.getUserId() + databaseAccessToken.getPersistenceName());
    }

    // worth being aware that if the db is still referenced somewhere then the garbage collector won't chuck it (which is what we want)
    public static void removeDBFromMap(String persistenceName) {
        memoryDatabaseMap.remove(persistenceName);
    }

    public static boolean isDBLoaded(String persistenceName) {
        return memoryDatabaseMap.containsKey(persistenceName);
    }

    // end static stuff (excepting some counters), now on to instance

    private final AzquoMemoryDBIndex index;

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    // while the names are loading they can't link their children so cache the ids until all names are loaded. This used to be held against each name.
    // public here as a utility really - to be accesses by NameDAO and AzquoMemoryDBTransport
    public final Map<Integer, byte[]> nameChildrenLoadingCache;

    private final AzquoMemoryDBTransport azquoMemoryDBTransport;

    private final BackupTransport backupTransport;

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

    // as in does the transport need to accommodate longer values - see ValueDAO.convertForLongTextValues
    private volatile boolean checkValueLengths;

    // replace the caches held against each name, should save memory
    private final Map<Name, Set<Name>> findAllChildrenCacheMap;
    private final Map<Name, Set<Value>> valuesIncludingChildrenCacheMap;


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

    There are times we want a temporary copy of a database. It is passed a source database and uses TemporaryAzquoMemoryDBTransport
    , it cannot persist. This load doesn't do an integrity check and jams the pointers to NameAttributes through as they're immutable
    , saving a bit on memory and a lot on name load time.
     */

    private AzquoMemoryDB(String persistenceName, AzquoMemoryDB sourceDB, StringBuffer sessionLog) {
        newDatabaseCount.incrementAndGet();
        index = new AzquoMemoryDBIndex();
        needsLoading = true;
        // is it dodgy letting "this" escape here? As a principle yes but these objects that use "this" are held against the instance this constructor is making
        // to put it another way - AzquoMemoryDBTransport could be an inner class but I'm trying to factor such stuff off.
        if (sourceDB != null) {
            azquoMemoryDBTransport = new TemporaryAzquoMemoryDBTransport(this, sourceDB);
            backupTransport = null;
        } else {
            azquoMemoryDBTransport = new AzquoMemoryDBTransport(this, persistenceName, sessionLog);
            backupTransport = new BackupTransport(this);
        }
        nameByIdMap = new ConcurrentHashMap<>();
        valueByIdMap = new ConcurrentHashMap<>();
        provenanceByIdMap = new ConcurrentHashMap<>();
        nameChildrenLoadingCache = new ConcurrentHashMap<>();
        valueLockTimes = new ConcurrentHashMap<>();
        valueLocks = new ConcurrentHashMap<>();
        findAllChildrenCacheMap = new ConcurrentHashMap<>();
        valuesIncludingChildrenCacheMap = new ConcurrentHashMap<>();

        boolean memoryTrack = "true".equals(azquoProperties.getProperty("memorytrack"));
        // loading in here was synchronized but only one constructor can be run, should be ok.
        // internally all the loading uses future gets to this thread SHOULD all be in sync with loaded data.
        // It is then being jammed into a ConcurrentHashMap so by the time it's called from said map all should be visible in memory correctly.
        azquoMemoryDBTransport.loadData(memoryTrack);
        nextId.incrementAndGet(); // bump it up one, the logic later is get and increment;
        needsLoading = false;
        if (azquoMemoryDBTransport.hasNamesToPersist()) {
            System.out.println("***************** name integrity problems detected/fixed on load, persisting fixes");
            persistToDataStore();
        }
        checkValueLengths = false;
    }


    void checkValueLengths() {
        checkValueLengths = true;
    }

    boolean needToCheckValueLengths() {
        return checkValueLengths;
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
        nextId.getAndUpdate(current -> Math.max(current, newNext));
    }

    public AzquoMemoryDBIndex getIndex() {
        return index;
    }

    // for debug purposes
/*(
    public int getCurrentMaximumId() {
        return nextId.get();
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

    Collection<Value> getAllValues() {
        return Collections.unmodifiableCollection(valueByIdMap.values());
    }

    Collection<Provenance> getAllProvenances() {
        return Collections.unmodifiableCollection(provenanceByIdMap.values());
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
    }

    // maybe these two should be moved, need to think about it. Used to count the number of names modified by an upload
    private static AtomicInteger countNamesForProvenance = new AtomicInteger(0);

    public int countNamesForProvenance(Provenance p) {
        countNamesForProvenance.incrementAndGet();
        int toReturn = 0;
        for (Name name : nameByIdMap.values()) {
            if (name.getProvenance() == p) {
                toReturn++;
            }
        }
        return toReturn;
    }

    private static AtomicInteger countValuesForProvenance = new AtomicInteger(0);

    public int countValuesForProvenance(Provenance p) {
        countValuesForProvenance.incrementAndGet();
        int toReturn = 0;
        for (Value Value : valueByIdMap.values()) {
            if (Value.getProvenance() == p) {
                toReturn++;
            }
        }
        return toReturn;
    }

    Provenance getProvenanceById(final int id) {
        return provenanceByIdMap.get(id);
    }

    /* Json then custom ones, maybe refactor later*/

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

    void addValueToDb(final Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.putIfAbsent(newValue.getId(), newValue) != null) { // != null means there was something in there, this really should not happen hence the exception
            throw new Exception("tried to add a value to the database with an existing id!");
        }
    }

    void removeValueFromDb(final Value toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        valueByIdMap.remove(toRemove.getId());
    }

    private volatile AtomicReference<Provenance> mostRecentProvenance = new AtomicReference<>();

    void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.putIfAbsent(newProvenance.getId(), newProvenance) != null) {
            throw new Exception("tried to add a provenance to the database with an existing id!");
        }
        mostRecentProvenance.getAndUpdate(provenance -> provenance != null && provenance.getTimeStamp().isAfter(newProvenance.getTimeStamp()) ? provenance : newProvenance);
    }

    // practically speaking could the last provenance ever be null when this is called? I guess be safe.
    public long getLastModifiedTimeStamp() {
        return mostRecentProvenance.get() != null ? mostRecentProvenance.get().getTimeStamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
    }

    public Provenance getMostRecentProvenance() {
        return mostRecentProvenance.get();
    }

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

    // for DB check on load
    private static AtomicInteger forceNameNeedsPersistingCount = new AtomicInteger(0);

    void forceNameNeedsPersisting(Name name) {
        forceNameNeedsPersistingCount.incrementAndGet();
        azquoMemoryDBTransport.setNameNeedsPersisting(name);
    }


    private static AtomicInteger setValueNeedsPersistingCount = new AtomicInteger(0);

    void setValueNeedsPersisting(Value value) {
        setValueNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            azquoMemoryDBTransport.setValueNeedsPersisting(value);
        }
    }

    public List<Value> getValuesChanged() {
        return azquoMemoryDBTransport.getValuesChanged();
    }

    public BackupTransport getBackupTransport() {
        return backupTransport;
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
        int minutesAllowed = 60; // arbitrary but seems fine
        for (Map.Entry<String, LocalDateTime> userLock : valueLockTimes.entrySet()) {
            final LocalDateTime lockTime = userLock.getValue();
            if (ChronoUnit.MINUTES.between(lockTime, LocalDateTime.now()) > minutesAllowed) {
                removeValuesLockForUser(userLock.getKey());
            }
        }
    }

    public boolean hasLocksAsideFromThisUser(String userId) {// intellij simplified, not sure how clear it is - empty = false, one is false too if it's the user we care about
        return !valueLockTimes.isEmpty() && !(valueLockTimes.size() == 1 && valueLockTimes.keySet().iterator().next().equals(userId));
    }

    // as in it's locked by another user, get the details of why
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
    public synchronized void synchronizedClear() {
        DSAdminService.emptyDatabaseInPersistence(azquoMemoryDBTransport.getPersistenceName());
    }

    public synchronized void synchronizedDrop() {
        DSAdminService.dropDatabaseInPersistence(azquoMemoryDBTransport.getPersistenceName());
    }

    public Map<Name, Set<Name>> getFindAllChildrenCacheMap() {
        return findAllChildrenCacheMap;
    }

    public Map<Name, Set<Value>> getValuesIncludingChildrenCacheMap() {
        return valuesIncludingChildrenCacheMap;
    }

/*
    private static void printFunctionCountStats() {
        System.out.println("######### AZQUO MEMORY DB FUNCTION COUNTS");
        System.out.println("newDatabaseCount\t\t\t\t" + newDatabaseCount.get());
        System.out.println("findTopNames2Count\t\t\t\t" + findTopNames2Count.get());
        System.out.println("clearCachesCount\t\t\t\t" + clearCachesCount.get());
        System.out.println("setJsonEntityNeedsPersistingCount\t\t\t\t" + setJsonEntityNeedsPersistingCount.get());
        System.out.println("setNameNeedsPersistingCount\t\t\t\t" + setNameNeedsPersistingCount.get());
        System.out.println("forceNameNeedsPersistingCount\t\t\t\t" + forceNameNeedsPersistingCount.get());
        System.out.println("setValueNeedsPersistingCount\t\t\t\t" + setValueNeedsPersistingCount.get());
    }

    private static void clearFunctionCountStats() {
        NameUtils.clearAtomicIntegerCounters(newDatabaseCount,
                findTopNames2Count,
                clearCachesCount,
                setJsonEntityNeedsPersistingCount,
                setNameNeedsPersistingCount,
                forceNameNeedsPersistingCount,
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
    }*/
}