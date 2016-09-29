package com.azquo.memorydb.core;

import com.azquo.ThreadPools;
import com.azquo.memorydb.dao.*;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * Created after it became apparent that Mysql in the way I'd arranged the objects didn't have a hope in hell of
 * delivering data fast enough. Leverage collections to implement Azquo spec.
 * <p>
 * I'm using intern when adding strings to objects, it should be used wherever that string is going to hang around.
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

    private final AzquoMemoryDBIndex index;

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    // to manage value locking, first says when a given user last put on a lock
    private final Map<String, LocalDateTime> valueLockTimes;
    // and the specific values which are locked
    private final Map<Value, String> valueLocks;

    // does this database need loading from the data store, a significant flag that affects rules for memory db entity instantiation for example
    private boolean needsLoading;

    // back to just mysql for the mo
    private String persistenceName;

    // no need to max id at load, it's used for this
    private final AtomicInteger nextId = new AtomicInteger(0); // new java 8 stuff should allow me to safely get the top id regardless of multiple threads

    // Used to be a map of sets for all entities but since speeding up storing of names and values with custom DAOs I figure it's best to simply have three sets
    private final Map<String, Set<AzquoMemoryDBEntity>> jsonEntitiesToPersist;
    private final Set<Name> namesToPersist;
    private final Set<Value> valuesToPersist;
    // A convenience reference to the user log while loading, null it at the end of the constructor
    private StringBuffer sessionLog;

    private static AtomicInteger newDatabaseCount = new AtomicInteger(0);

    protected AzquoMemoryDB(String persistenceName, StringBuffer sessionLog) {
        index = new AzquoMemoryDBIndex(this);
        newDatabaseCount.incrementAndGet();
        this.sessionLog = sessionLog;
        this.persistenceName = persistenceName;
        needsLoading = true;
        // commenting the map init numbers, inno db can overestimate quite heavily.
/*        int numberOfNames = standardDAO.findNumberOfRows(persistenceName, "fast_name");
        if (numberOfNames == -1){
            numberOfNames = standardDAO.findNumberOfRows(persistenceName, "name");
        }
        System.out.println("number of names : " + numberOfNames);*/
        nameByIdMap = new ConcurrentHashMap<>();
/*        int numberOfValues = standardDAO.findNumberOfRows(persistenceName, "fast_value");
        if (numberOfValues == -1){
            numberOfValues = standardDAO.findNumberOfRows(persistenceName, "value");
        }
        System.out.println("number of values : " + numberOfValues);*/
        valueByIdMap = new ConcurrentHashMap<>();
        provenanceByIdMap = new ConcurrentHashMap<>();

        valueLockTimes = new ConcurrentHashMap<>();
        valueLocks = new ConcurrentHashMap<>();

        jsonEntitiesToPersist = new ConcurrentHashMap<>();
        namesToPersist = Collections.newSetFromMap(new ConcurrentHashMap<>());
        valuesToPersist = Collections.newSetFromMap(new ConcurrentHashMap<>());

        loadData();
        nextId.incrementAndGet(); // bump it up one, the logic later is get and increment;
        needsLoading = false;
        this.sessionLog = null; // don't hang onto the reference here
    }

    // convenience
    public String getPersistenceName() {
        return persistenceName;
    }

    boolean getNeedsLoading() {
        return needsLoading;
    }

    void removeAttributeFromNameInAttributeNameMap(String attributeName, String existing, Name name) throws Exception{
        index.removeAttributeFromNameInAttributeNameMap(attributeName,existing, name);
    }

    void setAttributeForNameInAttributeNameMap(String attributeName, String attributeValue, Name name) {
        index.setAttributeForNameInAttributeNameMap(attributeName,attributeValue, name);
    }

    /**
     * Interface to enable initialization of json persisted entities to be more generic.
     * This sort of solves a language problem I was concerned by before but now it will only be used by Provenance and indeed in here hence why it's internal
     * It's an interface for a lambda passed to a map which will instantiate an memory db object based off the
     * JsonRecordTransport, initialise takes these two variables to then say new AzquoEntity(azquoMemoryDB, jsonRecordTransport)
     */
    interface JsonSerializableEntityInitializer {
        void initializeEntity(final AzquoMemoryDB azquoMemoryDB, JsonRecordTransport jsonRecordTransport) throws Exception;
    }

    // task for multi threaded loading of a database, JSON storing style, now just for provenance.

    private static AtomicInteger newSQLBatchLoaderRunCount = new AtomicInteger(0);

    private class SQLBatchLoader implements Callable<Void> {
        private final String tableName;
        private final JsonSerializableEntityInitializer jsonSerializableEntityInitializer;
        private final int minId;
        private final int maxId;
        private final AzquoMemoryDB memDB;
        private final AtomicInteger loadTracker;

        SQLBatchLoader(String tableName, JsonSerializableEntityInitializer jsonSerializableEntityInitializer, int minId, int maxId, AzquoMemoryDB memDB, AtomicInteger loadTracker) {
            this.tableName = tableName;
            this.jsonSerializableEntityInitializer = jsonSerializableEntityInitializer;
            this.minId = minId;
            this.maxId = maxId;
            this.memDB = memDB;
            this.loadTracker = loadTracker;
        }

        @Override
        public Void call() throws Exception {
            newSQLBatchLoaderRunCount.incrementAndGet();
            List<JsonRecordTransport> dataToLoad = JsonRecordDAO.findFromTableMinMaxId(memDB, tableName, minId, maxId);
            for (JsonRecordTransport dataRecord : dataToLoad) {
                nextId.getAndUpdate(current -> current < dataRecord.id ? dataRecord.id : current);
                jsonSerializableEntityInitializer.initializeEntity(memDB, dataRecord);
            }
            if (minId % 100_000 == 0) {
                loadTracker.addAndGet(dataToLoad.size());
            }
            if (minId % 1_000_000 == 0) {
                logInSessionLogAndSystem("loaded " + loadTracker.get());
            }
            return null;
        }
    }

    private static AtomicInteger newNameBatchLoaderRunCount = new AtomicInteger(0);

    private class NameBatchLoader implements Callable<Void> {
        private final int minId;
        private final int maxId;
        private final AzquoMemoryDB memDB;
        private final AtomicInteger loadTracker;

        NameBatchLoader(int minId, int maxId, AzquoMemoryDB memDB, AtomicInteger loadTracker) {
            this.minId = minId;
            this.maxId = maxId;
            this.memDB = memDB;
            this.loadTracker = loadTracker;
        }

        @Override
        public Void call() {
            newNameBatchLoaderRunCount.incrementAndGet();
            List<Name> names = NameDAO.findForMinMaxId(memDB, minId, maxId);
            for (Name name : names) {// bit of an overhead just to get the max id? I guess no concern - zapping the lists would save garbage but
                nextId.getAndUpdate(current -> current < name.getId() ? name.getId() : current);
            }
            // this was only when min id % 100_000, not sure why . . .
            loadTracker.addAndGet(names.size());
            if (minId % 1_000_000 == 0) {
                logInSessionLogAndSystem("loaded " + loadTracker.get());
            }
            return null;
        }
    }

    private static AtomicInteger newValueBatchLoaderRunCount = new AtomicInteger(0);

    private class ValueBatchLoader implements Callable<Void> {
        private final int minId;
        private final int maxId;
        private final AzquoMemoryDB memDB;
        private final AtomicInteger loadTracker;

        ValueBatchLoader(int minId, int maxId, AzquoMemoryDB memDB, AtomicInteger loadTracker) {
            this.minId = minId;
            this.maxId = maxId;
            this.memDB = memDB;
            this.loadTracker = loadTracker;
        }

        @Override
        public Void call() {
            newValueBatchLoaderRunCount.incrementAndGet();
            List<Value> values = ValueDAO.findForMinMaxId(memDB, minId, maxId);
            for (Value value : values) {// bit of an overhead just to get the max id? I guess no concern.
                nextId.getAndUpdate(current -> current < value.getId() ? value.getId() : current);
            }
            if (minId % 100_000 == 0) {
                loadTracker.addAndGet(values.size());
            }
            if (minId % 1_000_000 == 0) {
                logInSessionLogAndSystem("loaded " + loadTracker.get());
            }
            return null;
        }
    }

    private void logInSessionLogAndSystem(String s) {
        if (sessionLog != null) {
            sessionLog.append(s).append("\n");
        }
        System.out.println(s);
    }

    private static AtomicInteger loadDataCount = new AtomicInteger(0);

    synchronized private void loadData() {
        ValueDAO.createValueHistoryTableIfItDoesntExist(getPersistenceName());
        loadDataCount.incrementAndGet();
        boolean memoryTrack = "true".equals(azquoProperties.getProperty("memorytrack"));
        if (needsLoading) { // only allow it once!
            long startTime = System.currentTimeMillis();
            logInSessionLogAndSystem("loading data for " + getPersistenceName());
            long marker = System.currentTimeMillis();
            Runtime runtime = Runtime.getRuntime();
            long usedMB = 0;
            int mb = 1024 * 1024;
            if (memoryTrack) {
                System.gc(); // assuming of course the JVM actually listens :)
                System.out.println("gc time : " + (System.currentTimeMillis() - marker));
                usedMB = (runtime.totalMemory() - runtime.freeMemory()) / mb;
                System.out.println("Used Memory:" + usedMB);
                System.out.println("Free Memory:" + runtime.freeMemory() / mb);
            }
            try {
                // here we'll populate the memory DB from the database

                /*
                Load order is important as value and name use provenance and value uses names. Names uses itself hence all names need initialisation finished after the id map is sorted

                This is why when multi threading we wait util a type of entity is fully loaded before moving onto the next

                Atomic integers to pass through to the multi threaded code to track numbers

                */
                AtomicInteger provenanceLoaded = new AtomicInteger();// not used?
                AtomicInteger namesLoaded = new AtomicInteger();
                AtomicInteger valuesLoaded = new AtomicInteger();
                AtomicInteger jsonRecordsLoaded = new AtomicInteger();

                final int step = 100_000; // not so much step now as id range given how we're now querying mysql. CUtting down to 100,000 to reduce the chance of SQL errors
                marker = System.currentTimeMillis();
                // create thread pool, rack up the loading tasks and wait for it to finish. Repeat for name and values.
                // going to set up for multiple json persisted entities even if it's only provenance for the mo
                Map<String, JsonSerializableEntityInitializer> jsonTablesAndInitializers = new HashMap<>();
                // add lines like this for loading other json entities. A note is table names repeated, might have a think about that
                jsonTablesAndInitializers.put(Provenance.PERSIST_TABLE, (azquoMemoryDB, jsonRecordTransport) -> new Provenance(azquoMemoryDB, jsonRecordTransport.id, jsonRecordTransport.json));
                int from;
                int maxIdForTable;
                List<Future<?>> futureBatches;
                // currently just provenance
                for (String tableName : jsonTablesAndInitializers.keySet()) {
                    jsonRecordsLoaded.set(0);
                    from = 0;
                    maxIdForTable = JsonRecordDAO.findMaxId(this, tableName);
                    futureBatches = new ArrayList<>();
                    while (from < maxIdForTable) {
                        futureBatches.add(ThreadPools.getSqlThreadPool().submit(new SQLBatchLoader(tableName, jsonTablesAndInitializers.get(tableName), from, from + step, this, tableName.equals(Provenance.PERSIST_TABLE) ? provenanceLoaded : jsonRecordsLoaded)));
                        from += step;
                    }
                    for (Future future : futureBatches) {
                        future.get(1, TimeUnit.HOURS);
                    }
                    logInSessionLogAndSystem(tableName + ", " + jsonRecordsLoaded + " records loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                }
                marker = System.currentTimeMillis();
                from = 0;
                NameDAO.zapAdditive(getPersistenceName());
                maxIdForTable = NameDAO.findMaxId(this);
                futureBatches = new ArrayList<>();
                while (from < maxIdForTable) {
                    futureBatches.add(ThreadPools.getSqlThreadPool().submit(new NameBatchLoader(from, from + step, this, namesLoaded)));
                    from += step;
                }
                for (Future future : futureBatches) {
                    future.get(1, TimeUnit.HOURS);
                }
                logInSessionLogAndSystem("Names loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                marker = System.currentTimeMillis();
                from = 0;
                maxIdForTable = ValueDAO.findMaxId(this);
                futureBatches = new ArrayList<>();
                while (from < maxIdForTable) {
                    futureBatches.add(ThreadPools.getSqlThreadPool().submit(new ValueBatchLoader(from, from + step, this, valuesLoaded)));
                    from += step;
                }
                for (Future future : futureBatches) {
                    future.get(1, TimeUnit.HOURS);
                }
                logInSessionLogAndSystem("Values loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                marker = System.currentTimeMillis();
                // wait until all are loaded before linking
                System.out.println(provenanceLoaded.get() + valuesLoaded.get() + namesLoaded.get() + " unlinked entities loaded in " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
                if (memoryTrack) {
                    System.out.println("Used Memory after list load:"
                            + (runtime.totalMemory() - runtime.freeMemory()) / mb);
                }
                linkEntities();
                if (memoryTrack) {
                    System.out.println("Used Memory after init names :"
                            + (runtime.totalMemory() - runtime.freeMemory()) / mb);
                }
                logInSessionLogAndSystem("Names init/linked in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
            } catch (Exception e) {
                logger.error("could not load data for " + getPersistenceName() + "!", e);
                // todo, stop the threads
            }
            needsLoading = false;
            if (memoryTrack) {
                marker = System.currentTimeMillis();
                // using system.gc before and after loading to get an idea of DB memory overhead
                System.gc();
                System.out.println("gc time : " + (System.currentTimeMillis() - marker));
                long newUsed = (runtime.totalMemory() - runtime.freeMemory()) / mb;
                NumberFormat nf = NumberFormat.getInstance();
                System.out.println("Guess at DB size " + nf.format(newUsed - usedMB) + "MB");
                System.out.println("--- MEMORY USED :  " + nf.format(runtime.totalMemory() - runtime.freeMemory() / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb));
            }
            logInSessionLogAndSystem("Total load time for " + getPersistenceName() + " " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
            //AzquoMemoryDB.printAllCountStats();
        }
    }

    // I'm going to force the import to wait if persisting or the like is going on
    public synchronized void lockTest() {
    }

    // reads from a list of changed objects
    // should we synchronize on a write lock object? I think it might be a plan.

    private static AtomicInteger persistToDataStoreCount = new AtomicInteger(0);

    public synchronized void persistToDataStore() {
        System.out.println("PERSIST STARTING");
        persistToDataStoreCount.incrementAndGet();
        // todo : write locking the db probably should start here
        // this is where I need to think carefully about concurrency, azquodb has the last say when the sets are modified although the flags are another point
        // just a simple DB write lock should to it
        // for the moment just make it work.
        // ok first do the json bits, as mentioned currently this is just provenance, may well be others
        for (String tableName : jsonEntitiesToPersist.keySet()) {
            Set<AzquoMemoryDBEntity> entities = jsonEntitiesToPersist.get(tableName);
            if (!entities.isEmpty()) {
                // multi thread this chunk? It can slow things down a little . . .
                System.out.println("Json entities to put in " + tableName + " : " + entities.size());
                List<JsonRecordTransport> recordsToStore = new ArrayList<>(entities.size()); // it's now bothering me a fair bit that I didn't used to initialise such lists!
                for (AzquoMemoryDBEntity entity : new ArrayList<>(entities)) { // we're taking a copy of the set before running through it. Copy perhaps expensive but consistency is important
                    JsonRecordTransport.State state = JsonRecordTransport.State.UPDATE;
                    if (entity.getNeedsDeleting()) {
                        state = JsonRecordTransport.State.DELETE;
                    }
                    if (entity.getNeedsInserting()) {
                        state = JsonRecordTransport.State.INSERT;
                    }
                    entity.setAsPersisted(); /* ok we do this before setting to save so we don't get a state for save, state changed, then set as persisted (hence not allowing for latest changes)
                        the multi threaded handling of sets to persist is via concurrent hash map, hence ordering which is important here should be right I hope. The only concern is for the same
                        element being removed being added back into the map and I think the internal locking (via striping?) should deal with this*/
                    recordsToStore.add(new JsonRecordTransport(entity.getId(), entity.getAsJson(), state));
                }
                // and end here
                try {
                    JsonRecordDAO.persistJsonRecords(this, tableName, recordsToStore);// note this is multi threaded internally
                } catch (Exception e) {
                    // currently I'll just stack trace this, not sure of what would be the best strategy
                    e.printStackTrace();
                }
            }
        }
        // todo re-examine given new patterns. can genericize? As in pass the DAOs around? Maybe not as they're typed . . .
        System.out.println("name store : " + namesToPersist.size());
        // I think here the copy happens to be defensive, there should be no missed changes or rather entities flagged as persisted that need to be persisted (change right after save but before set as persisted)
        List<Name> namesToStore = new ArrayList<>(namesToPersist.size());
        for (Name name : namesToPersist) {
            namesToStore.add(name);
            // note! Doing this changes the inserting flag meaning there may be a bunch of unnecessary deletes. Doesn't break anything but not necessary, need to sort that TODO
            name.setAsPersisted();// The actual saving of the state happens later. If modified in the mean time a name will be added back onto the set
        }
        try {
            NameDAO.persistNames(this, namesToStore);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("value store : " + valuesToPersist.size());
        List<Value> valuesToStore = new ArrayList<>(valuesToPersist.size());
        for (Value value : valuesToPersist) {
            valuesToStore.add(value);
            value.setAsPersisted();
        }
        try {
            ValueDAO.persistValues(this, valuesToStore);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("persist done.");
    }

    int getNextId() {
        return nextId.getAndIncrement(); // should correctly replace the old logic, exactly what atomic ints are for.
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
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        // there was a check that the name had an Id greater than 0, I don't know why
        //if (newName.getId() > 0 && nameByIdMap.get(newName.getId()) != null) {
        if (nameByIdMap.putIfAbsent(newName.getId(), newName) != null) {
            throw new Exception("tried to add a name to the database with an existing id!");
        }

    }

    void removeNameFromDb(final Name toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        nameByIdMap.remove(toRemove.getId());
    }

    private static AtomicInteger batchLinkerRunCount = new AtomicInteger(0);

    private class BatchLinker implements Callable<Void> {
        private final AtomicInteger loadTracker;
        private final List<Name> batchToLink;

        BatchLinker(AtomicInteger loadTracker, List<Name> batchToLink) {
            this.loadTracker = loadTracker;
            this.batchToLink = batchToLink;
        }

        @Override
        public Void call() throws Exception { // well this is what's going to truly test concurrent modification of a database
            batchLinkerRunCount.incrementAndGet();
            for (Name name : batchToLink) {
                name.link();
                index.addNameToAttributeNameMap(name);
            }
            logInSessionLogAndSystem("Linked : " + loadTracker.addAndGet(batchToLink.size()));
            return null;
        }
    }

    /* to be called after loading moves the json and extracts attributes to useful maps here called after loading as the names reference themselves
    going to try a basic multi-thread - it was 100,000 but I wonder if this is as efficient as it could be given that at the end leftover threads
    can hang around (particularly hefty child sets). Trying for 50,000 */

    private static AtomicInteger linkEntitiesCount = new AtomicInteger(0);

    private synchronized void linkEntities() throws Exception {
        linkEntitiesCount.incrementAndGet();
        // there may be a certain overhead to building the batches but otherwise it's dealing with millions of callables
        int batchLinkSize = 50_000;
        ArrayList<Name> batchLink = new ArrayList<>(batchLinkSize);
        List<Future<?>> linkFutures = new ArrayList<>();
        AtomicInteger loadTracker = new AtomicInteger(0);
        for (Name name : nameByIdMap.values()) {
            batchLink.add(name);
            if (batchLink.size() == batchLinkSize) {
                linkFutures.add(ThreadPools.getMainThreadPool().submit(new BatchLinker(loadTracker, batchLink)));
                batchLink = new ArrayList<>(batchLinkSize);
            }
        }
        // link leftovers
        linkFutures.add(ThreadPools.getMainThreadPool().submit(new BatchLinker(loadTracker, batchLink)));
        // instead of the old shutdown do the gets on the futures to ensure all work is done before returning from the function
        // tracking and exception handling easier with this method
        // note tracking on the linking seems bad on some databases is heavy stuff happens first. Hmmm . . . tracking back into the linker?
        for (Future linkFuture : linkFutures) {
            linkFuture.get(1, TimeUnit.HOURS);
        }
    }

    /* Json then custom ones, maybe refactor later*/

    private static AtomicInteger setJsonEntityNeedsPersistingCount = new AtomicInteger(0);

    void setJsonEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity entity) {
        setJsonEntityNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            jsonEntitiesToPersist.computeIfAbsent(tableToPersistIn, t -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(entity);
        }
    }

    private static AtomicInteger removeJsonEntityNeedsPersistingCount = new AtomicInteger(0);

    void removeJsonEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity entity) {
        removeJsonEntityNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            jsonEntitiesToPersist.computeIfAbsent(tableToPersistIn, t -> Collections.newSetFromMap(new ConcurrentHashMap<>())).remove(entity);
        }
    }

    private static AtomicInteger setNameNeedsPersistingCount = new AtomicInteger(0);

    void setNameNeedsPersisting(Name name) {
        setNameNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            namesToPersist.add(name);
        }
    }

    private static AtomicInteger removeNameNeedsPersistingCount = new AtomicInteger(0);

    void removeNameNeedsPersisting(Name name) {
        removeNameNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            namesToPersist.remove(name);
        }
    }

    private static AtomicInteger setValueNeedsPersistingCount = new AtomicInteger(0);

    void setValueNeedsPersisting(Value value) {
        setValueNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            valuesToPersist.add(value);
        }
    }

    private static AtomicInteger removeValueNeedsPersistingCount = new AtomicInteger(0);

    void removeValueNeedsPersisting(Value value) {
        removeNameNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            valuesToPersist.remove(value);
        }
    }

    void addValueToDb(final Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.putIfAbsent(newValue.getId(), newValue) != null) { // != null means there was something in there, this really should not happen hence the exception
            throw new Exception("tried to add a value to the database with an existing id!");
        }
    }

    // last modified according to provenance!
    private final AtomicLong lastModified = new AtomicLong();

    void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.putIfAbsent(newProvenance.getId(), newProvenance) != null) {
            throw new Exception("tried to add a provenance to the database with an existing id!");
        }
        // should I be tolerating no timestamp?
        lastModified.getAndUpdate(n -> newProvenance.getTimeStamp() != null && n < newProvenance.getTimeStamp().getTime() ? newProvenance.getTimeStamp().getTime() : n); // think that logic it correct for thread safety
    }

    public long getLastModifiedTimeStamp() {
        return lastModified.get();
    }

    // note - this function will NOT check for existing locks for these values, it just clears for this user then sets new locks
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

    // I may change these later, for the mo I just want to stop drops and clears at the same time as persistence
    public synchronized void synchronizedClear() throws Exception {
        DSAdminService.emptyDatabaseInPersistence(getPersistenceName());
    }

    public synchronized void synchronizedDrop() throws Exception {
        DSAdminService.dropDatabaseInPersistence(getPersistenceName());
    }

    private static void printFunctionCountStats() {
        System.out.println("######### AZQUO MEMORY DB FUNCTION COUNTS");
        System.out.println("newDatabaseCount\t\t\t\t" + newDatabaseCount.get());
        System.out.println("newSQLBatchLoaderRunCount\t\t\t\t" + newSQLBatchLoaderRunCount.get());
        System.out.println("newNameBatchLoaderRunCount\t\t\t\t" + newNameBatchLoaderRunCount.get());
        System.out.println("newValueBatchLoaderRunCount\t\t\t\t" + newValueBatchLoaderRunCount.get());
        System.out.println("loadDataCount\t\t\t\t" + loadDataCount.get());
        System.out.println("persisttoDataStoreCount\t\t\t\t" + persistToDataStoreCount.get());
        System.out.println("findTopNames2Count\t\t\t\t" + findTopNames2Count.get());
        System.out.println("clearCachesCount\t\t\t\t" + clearCachesCount.get());
        System.out.println("batchLinkerRunCount\t\t\t\t" + batchLinkerRunCount.get());
        System.out.println("linkEntitiesCount\t\t\t\t" + linkEntitiesCount.get());
        System.out.println("setJsonEntityNeedsPersistingCount\t\t\t\t" + setJsonEntityNeedsPersistingCount.get());
        System.out.println("removeJsonEntityNeedsPersistingCount\t\t\t\t" + removeJsonEntityNeedsPersistingCount.get());
        System.out.println("setNameNeedsPersistingCount\t\t\t\t" + setNameNeedsPersistingCount.get());
        System.out.println("removeNameNeedsPersistingCount\t\t\t\t" + removeNameNeedsPersistingCount.get());
        System.out.println("setValueNeedsPersistingCount\t\t\t\t" + setValueNeedsPersistingCount.get());
        System.out.println("removeValueNeedsPersistingCount\t\t\t\t" + removeValueNeedsPersistingCount.get());
    }

    private static void clearFunctionCountStats() {
        newDatabaseCount.set(0);
        newSQLBatchLoaderRunCount.set(0);
        newNameBatchLoaderRunCount.set(0);
        newValueBatchLoaderRunCount.set(0);
        loadDataCount.set(0);
        persistToDataStoreCount.set(0);
        findTopNames2Count.set(0);
        clearCachesCount.set(0);
        batchLinkerRunCount.set(0);
        linkEntitiesCount.set(0);
        setJsonEntityNeedsPersistingCount.set(0);
        removeJsonEntityNeedsPersistingCount.set(0);
        setNameNeedsPersistingCount.set(0);
        removeNameNeedsPersistingCount.set(0);
        setValueNeedsPersistingCount.set(0);
        removeValueNeedsPersistingCount.set(0);
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