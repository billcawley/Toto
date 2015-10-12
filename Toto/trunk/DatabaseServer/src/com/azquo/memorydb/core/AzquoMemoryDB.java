package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.JsonRecordTransport;
import com.azquo.memorydb.dao.NameDAO;
import com.azquo.memorydb.dao.StandardDAO;
import com.azquo.memorydb.dao.ValueDAO;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * created after it became apparent that Mysql in the way I'd arranged the objects didn't have a hope in hell of
 * delivering data fast enough. Leverage collections to implement Azquo spec.
 * <p>
 * I'm using intern when adding strings to objects, it should be used wherever that string is going to hang around.
 */

public final class AzquoMemoryDB {

    // have given up trying this though spring for the moment
    Properties azquoProperties = new Properties();

    private static final Logger logger = Logger.getLogger(AzquoMemoryDB.class);

    //I don't think I can auto wire this as I can't guarantee it will be ready for the constructor

    private final StandardDAO standardDAO;

    private final NameDAO nameDAO;

    private final ValueDAO valueDAO;

    private final Map<String, Map<String, List<Name>>> nameByAttributeMap; // a map of maps of lists of names. Fun! Moved back to lists to save memory, the lists are unlikely to be big

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    // does this database need loading from mysql, a significant flag that affects rules for memory db entity instantiation for example
    private boolean needsLoading;

    // reference to the mysql db, not final so it can be nullable to stop persistence
    private String mysqlName;

    // object ids. We handle this here, it's not done by MySQL
    private volatile int maxIdAtLoad; // volatile as it may be hit by multiple threads
    // should this also be volatile??
    private int nextId;

    // when objects are modified they are added to these sets held in a map. AzquoMemoryDBEntity has all functions require to persist.
    private final Map<String, Set<AzquoMemoryDBEntity>> entitiesToPersist;
    // how many threads when loading from and saving to MySQL
    private final int loadingThreads;
    // how many threads when creating a report
    private final int reportFillerThreads;

    // available to StandardDAO
    public int getLoadingThreads() {
        return loadingThreads;
    }

    public int getReportFillerThreads() {
        return reportFillerThreads;
    }

    // for convenience while loading, null it at the end of the constuctor.
    private StringBuffer sessionLog;

    private boolean fastLoaded = false;

    // Initialising as concurrent hashmaps here, needs careful thought as to whether heavy concurrent access is actually a good idea, what could go wrong
    private static AtomicInteger newDatabaseCount = new AtomicInteger(0);

    protected AzquoMemoryDB(String mysqlName, StandardDAO standardDAO, NameDAO nameDAO, ValueDAO valeuDAO, StringBuffer sessionLog) throws Exception {
        newDatabaseCount.incrementAndGet();
        this.sessionLog = sessionLog;
        azquoProperties.load(getClass().getClassLoader().getResourceAsStream("azquo.properties")); // easier than messing around with spring
        // now where the default multi threading number is defined. Different number based on the task? Can decide later.
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int possibleLoadingThreads = availableProcessors < 4 ? availableProcessors : (availableProcessors / 2);
        if (possibleLoadingThreads > 8) { // I think more than this asks for trouble - processors isn't really the prob with mysql it's IO! I should be asking : is the disk SSD?
            possibleLoadingThreads = 8;
        }
        loadingThreads = possibleLoadingThreads;
        reportFillerThreads = availableProcessors < 4 ? availableProcessors : ((availableProcessors * 2) / 3); // slightly more for report generation, 2/3
        System.out.println("memory db transport threads : " + loadingThreads);
        System.out.println("reportFillerThreads : " + reportFillerThreads);
        this.mysqlName = mysqlName;
        this.standardDAO = standardDAO;
        this.nameDAO = nameDAO;
        this.valueDAO = valeuDAO;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByAttributeMap = new ConcurrentHashMap<>();
        // commented koloboke should we wish to try that. Not thread safe though.
/*        nameByIdMap = HashIntObjMaps.newMutableMap();
        valueByIdMap = HashIntObjMaps.newMutableMap();
        provenanceByIdMap = HashIntObjMaps.newMutableMap();*/
        // todo - get id counts from the tables and init the id maps to a proper size?
        nameByIdMap = new ConcurrentHashMap<>();
        valueByIdMap = new ConcurrentHashMap<>();
        provenanceByIdMap = new ConcurrentHashMap<>();
        entitiesToPersist = new ConcurrentHashMap<>();
        // loop over the possible persisted tables making the empty sets, cunning
        if (standardDAO != null) {
            for (StandardDAO.PersistedTable persistedTable : StandardDAO.PersistedTable.values()) {
                // wasn't concurrent, surely it should be?
                entitiesToPersist.put(persistedTable.name(), Collections.newSetFromMap(new ConcurrentHashMap<>()));
            }
        }
        if (standardDAO != null) {
            loadData();
        }
        needsLoading = false;
        nextId = maxIdAtLoad + 1;
        this.sessionLog = null; // don't hang onto the reference here
    }

    // convenience
    public String getMySQLName() {
        return mysqlName;
    }

    // not sure what to do with this, on its own it would stop persisting but not much else
    /*public void zapDatabase() {
        mysqlName = null;
    }*/

    public boolean getNeedsLoading() {
        return needsLoading;
    }

    final int mb = 1024 * 1024;
    private static final int PROVENANCE_MODE = 0;
    private static final int NAME_MODE = 1;
    private static final int VALUE_MODE = 2;


    // task for multi threaded loading of a database

    private static AtomicInteger newSQLBatchLoaderRunCount = new AtomicInteger(0);

    private class SQLBatchLoader implements Runnable {
        private final StandardDAO standardDAO;
        private final int mode;
        private final int minId;
        private final int maxId;
        private final AzquoMemoryDB memDB;
        private final AtomicInteger loadTracker;

        public SQLBatchLoader(StandardDAO standardDAO, int mode, int minId, int maxId, AzquoMemoryDB memDB, AtomicInteger loadTracker) {
            this.standardDAO = standardDAO;
            this.mode = mode;
            this.minId = minId;
            this.maxId = maxId;
            this.memDB = memDB;
            this.loadTracker = loadTracker;
        }

        @Override
        public void run() {
            newSQLBatchLoaderRunCount.incrementAndGet();
            try {
                // may rearrange this later, could perhaps be more elegant
                String tableName = "";
                if (mode == PROVENANCE_MODE) {
                    tableName = StandardDAO.PersistedTable.provenance.name();
                }
                if (mode == NAME_MODE) {
                    tableName = StandardDAO.PersistedTable.name.name();
                }
                if (mode == VALUE_MODE) {
                    tableName = StandardDAO.PersistedTable.value.name();
                }
                List<JsonRecordTransport> dataToLoad = standardDAO.findFromTableMinMaxId(memDB, tableName, minId, maxId);
                for (JsonRecordTransport dataRecord : dataToLoad) {
                    if (dataRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = dataRecord.id;
                    }
                    if (mode == PROVENANCE_MODE) {
                        new Provenance(memDB, dataRecord.id, dataRecord.json);
                    }
                    if (mode == NAME_MODE) {
                        new Name(memDB, dataRecord.id, dataRecord.json);
                    }
                    if (mode == VALUE_MODE) {
                        new Value(memDB, dataRecord.id, dataRecord.json);
                    }
                }
                if (minId%100_000 == 0){
                    loadTracker.addAndGet(dataToLoad.size());
                }
                if (minId%1_000_000 == 0){
                    logInSessionLogAndSystem("loaded " + loadTracker.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // needs factoring, just testing for the moment

    private static AtomicInteger newNameBatchLoaderRunCount = new AtomicInteger(0);

    private class NameBatchLoader implements Runnable {
        private final NameDAO nameDAO;
        private final int minId;
        private final int maxId;
        private final AzquoMemoryDB memDB;
        private final AtomicInteger loadTracker;

        public NameBatchLoader(NameDAO nameDAO, int minId, int maxId, AzquoMemoryDB memDB, AtomicInteger loadTracker) {
            this.nameDAO = nameDAO;
            this.minId = minId;
            this.maxId = maxId;
            this.memDB = memDB;
            this.loadTracker = loadTracker;
        }

        @Override
        public void run() {
            newNameBatchLoaderRunCount.incrementAndGet();
            try {
                List<Name> names = nameDAO.findForMinMaxId(memDB, minId, maxId);
                for (Name name : names) {// bit of an overhead just to get the max id? I guess no concern.
                    if (name.getId() > maxIdAtLoad) {
                        maxIdAtLoad = name.getId();
                    }
                }
                if (minId%100_000 == 0){
                    loadTracker.addAndGet(names.size());
                }
                if (minId%1_000_000 == 0){
                    logInSessionLogAndSystem("loaded " + loadTracker.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // needs factoring, just testing for the moment

    private static AtomicInteger newValueBatchLoaderRunCount = new AtomicInteger(0);

    private class ValueBatchLoader implements Runnable {
        private final ValueDAO valueDAO;
        private final int minId;
        private final int maxId;
        private final AzquoMemoryDB memDB;
        private final AtomicInteger loadTracker;

        public ValueBatchLoader(ValueDAO valueDAO, int minId, int maxId, AzquoMemoryDB memDB, AtomicInteger loadTracker) {
            this.valueDAO = valueDAO;
            this.minId = minId;
            this.maxId = maxId;
            this.memDB = memDB;
            this.loadTracker = loadTracker;
        }

        @Override
        public void run() {
            newValueBatchLoaderRunCount.incrementAndGet();
            try {
                List<Value> values = valueDAO.findForMinMaxId(memDB, minId, maxId);
                for (Value value : values) {// bit of an overhead just to get the max id? I guess no concern.
                    if (value.getId() > maxIdAtLoad) {
                        maxIdAtLoad = value.getId();
                    }
                }
                if (minId%100_000 == 0){
                    loadTracker.addAndGet(values.size());
                }
                if (minId%1_000_000 == 0){
                    logInSessionLogAndSystem("loaded " + loadTracker.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        loadDataCount.incrementAndGet();
        boolean memoryTrack = "true".equals(azquoProperties.getProperty("memorytrack"));
        if (needsLoading) { // only allow it once!
            long startTime = System.currentTimeMillis();
            logInSessionLogAndSystem("loading data for " + getMySQLName());
            // using system.gc before and after loading to get an idea of DB memory overhead
            long marker = System.currentTimeMillis();
            Runtime runtime = Runtime.getRuntime();
            long usedMB = 0;
            if (memoryTrack) {
                System.gc(); // assuming of course the JVM actually listens :)
                System.out.println("gc time : " + (System.currentTimeMillis() - marker));
                usedMB = (runtime.totalMemory() - runtime.freeMemory()) / mb;
                System.out.println("Used Memory:"
                        + usedMB);
                System.out.println("Free Memory:"
                        + runtime.freeMemory() / mb);
            }
            try {
                // here we'll populate the memory DB from the database

                /* ok this code is a bit annoying, one could run through the table names from the outside but then you need to switch on it for the constructor
                one could use AzquoMemoryDBEntity if having some kind of init from Json function but this makes the objects more mutable than I'd like
                so we stay with this for the moment
                these 3 commands will automatically load the data into the memory DB set as persisted
                Load order is important as value and name use provenance and value uses names. Names uses itself hence all names need initialisation finished after the id map is sorted

                This is why when multi threading we wait util a type of entity is fully loaded before moving onto the next

                Atomic integers to pass through to the multi threaded code to track numbers

                */
                AtomicInteger provenaceLoaded = new AtomicInteger();
                AtomicInteger namesLoaded = new AtomicInteger();
                AtomicInteger valuesLoaded = new AtomicInteger();

                final int step = 100_000; // not so much step now as id range given how we're now querying mysql. CUtting down to 100,000 to reduce the chance of SQL errors
                marker = System.currentTimeMillis();
                // create thread pool, rack up the loading tasks and wait for it to finish. Repeat for name and values.
                ExecutorService executor = Executors.newFixedThreadPool(loadingThreads);
                int from = 0;
                int maxIdForTable = standardDAO.findMaxId(this, StandardDAO.PersistedTable.provenance.name());
                while (from < maxIdForTable) {
                    executor.execute(new SQLBatchLoader(standardDAO, PROVENANCE_MODE, from, from + step, this, provenaceLoaded));
                    from += step;
                }
                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                    throw new Exception("Database " + getMySQLName() + " took longer than an hour to load");
                }
                logInSessionLogAndSystem("Provenance loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                marker = System.currentTimeMillis();
                executor = Executors.newFixedThreadPool(loadingThreads);

                // ok now need code to switch to the new ones
                if (nameDAO.checkFastTableExists(this) && valueDAO.checkFastTableExists(this)){
                    System.out.println();
                    System.out.println("### Using new loading mechanism ###");
                    System.out.println();
                    from = 0;
                    maxIdForTable = nameDAO.findMaxId(this);
                    while (from < maxIdForTable) {
                        executor.execute(new NameBatchLoader(nameDAO, from, from + step, this, namesLoaded));
                        from += step;
                    }
                    executor.shutdown();
                    if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                        throw new Exception("Database " + getMySQLName() + " took longer than an hour to load");
                    }
                    logInSessionLogAndSystem("Names loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                    marker = System.currentTimeMillis();
                    executor = Executors.newFixedThreadPool(loadingThreads);
                    from = 0;
                    maxIdForTable = valueDAO.findMaxId(this);
                    while (from < maxIdForTable) {
                        executor.execute(new ValueBatchLoader(valueDAO, from, from + step, this, valuesLoaded));
                        from += step;
                    }
                    executor.shutdown();
                    if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                        throw new Exception("Database " + getMySQLName() + " took longer than an hour to load");
                    }
                    logInSessionLogAndSystem("Values loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                    marker = System.currentTimeMillis();
                    fastLoaded = true;
                } else {
                    from = 0;
                    maxIdForTable = standardDAO.findMaxId(this, StandardDAO.PersistedTable.name.name());
                    while (from < maxIdForTable) {
                        executor.execute(new SQLBatchLoader(standardDAO, NAME_MODE, from, from + step, this, namesLoaded));
                        from += step;
                    }
                    executor.shutdown();
                    if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                        throw new Exception("Database " + getMySQLName() + " took longer than an hour to load");
                    }
                    logInSessionLogAndSystem("Names loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                    marker = System.currentTimeMillis();
                    executor = Executors.newFixedThreadPool(loadingThreads);
                    from = 0;
                    maxIdForTable = standardDAO.findMaxId(this, StandardDAO.PersistedTable.value.name());
                    while (from < maxIdForTable) {
                        executor.execute(new SQLBatchLoader(standardDAO, VALUE_MODE, from, from + step, this, valuesLoaded));
                        from += step;
                    }
                    executor.shutdown();
                    if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                        throw new Exception("Database " + getMySQLName() + " took longer than an hour to load");
                    }
                    logInSessionLogAndSystem("Values loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                    marker = System.currentTimeMillis();
                }
                // wait until all are loaded before linking
                System.out.println(provenaceLoaded.get() + valuesLoaded.get() + namesLoaded.get() + " unlinked entities loaded in " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
                if (memoryTrack) {
                    System.out.println("Used Memory after list load:"
                            + (runtime.totalMemory() - runtime.freeMemory()) / mb);
                }
                // note : after multi threading the loading this init names (linking) now takes longer, need to consider thread safety if planning on multi threading this.
                linkEntities();
                if (memoryTrack) {
                    System.out.println("Used Memory after init names :"
                            + (runtime.totalMemory() - runtime.freeMemory()) / mb);
                }
                logInSessionLogAndSystem("Names init/linked in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
            } catch (Exception e) {
                logger.error("could not load data for " + getMySQLName() + "!", e);
            }
            needsLoading = false;
            if (memoryTrack) {
                marker = System.currentTimeMillis();
                // using system.gc before and after loading to get an idea of DB memory overhead
                System.gc();
                System.out.println("gc time : " + (System.currentTimeMillis() - marker));
                long newUsed = (runtime.totalMemory() - runtime.freeMemory()) / mb;
                System.out.println("Guess at DB size " + (newUsed - usedMB));
                System.out.println("Used Memory:" + newUsed);
                System.out.println("Free Memory:" + runtime.freeMemory() / mb);
                System.out.println("Total Memory:" + runtime.totalMemory() / mb);
                System.out.println("Max Memory:" + runtime.maxMemory() / mb);
            }
            logInSessionLogAndSystem("Total load time for " + getMySQLName() + " " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
            //AzquoMemoryDB.printAllCountStats();
            //AzquoMemoryDB.clearAllCountStats();
            int testlimit = 10_000_000;
            /*for (int i = 0; i < 3; i++){
                long track = System.currentTimeMillis();
                System.out.println("Set test " + testlimit * (i + 1));
                int count = 0;
                Set<Name> testSet = HashObjSets.newUpdatableSet();
                for (Name test : nameByIdMap.values()){
                    testSet.add(test);
                    count++;
                    if (count == testlimit * (i + 1)) break;
                }
                System.out.println("Koloboke Name    :   " + (System.currentTimeMillis() - track) + "ms");
                track = System.currentTimeMillis();
                count = 0;
                Set<Value> testSetV = HashObjSets.newUpdatableSet();
                for (Value test : valueByIdMap.values()){
                    testSetV.add(test);
                    count++;
                    if (count == testlimit * (i + 1)) break;
                }
                System.out.println("Koloboke Value :     " + (System.currentTimeMillis() - track) + "ms");
                track = System.currentTimeMillis();
                count = 0;
                testSet = new HashSet<>();
                for (Name test : nameByIdMap.values()){
                    testSet.add(test);
                    count++;
                    if (count == testlimit * (i + 1)) break;
                }
                System.out.println("Java Name :               " + (System.currentTimeMillis() - track) + "ms");
                track = System.currentTimeMillis();
                count = 0;
                testSetV = new HashSet<>();
                for (Value test : valueByIdMap.values()){
                    testSetV.add(test);
                    count++;
                    if (count == testlimit * (i + 1)) break;
                }
                System.out.println("Java Value :     " + (System.currentTimeMillis() - track) + "ms");
                track = System.currentTimeMillis();
                count = 0;
                testSetV = HashObjSets.newUpdatableSet();
                for (Value test : valueByIdMap.values()){
                    testSetV.contains(test);
                    testSetV.add(test);
                    testSetV.contains(test);
                    count++;
                    if (count == testlimit) break;
                }
                System.out.println("Koloboke Value contains :     " + (System.currentTimeMillis() - track) + "ms");
                track = System.currentTimeMillis();
                count = 0;
                testSetV = new HashSet<>();
                for (Value test : valueByIdMap.values()){
                    testSetV.contains(test);
                    testSetV.add(test);
                    testSetV.contains(test);
                    count++;
                    if (count == testlimit) break;
                }
                System.out.println("Java Value contains :               " + (System.currentTimeMillis() - track) + "ms");
            }*/
        }
    }

    // reads from a list of changed objects
    // should we synchronize on a write lock object?? I think it might be a plan.

    private static AtomicInteger saveDataToMySQLCount = new AtomicInteger(0);

    public synchronized void saveDataToMySQL() {
        saveDataToMySQLCount.incrementAndGet();
        // this is where I need to think carefully about concurrency, azquodb has the last say when the sets are modified although the flags are another point
        // just a simple DB write lock should to it
        // for the moment just make it work.
        // map of sets to persist means that should we add another object type then this code should not need to change
        for (String tableToStoreIn : entitiesToPersist.keySet()) { // could go back to an enum of persist table names? Only implemented like this due to the old app tables
            Set<AzquoMemoryDBEntity> entities = entitiesToPersist.get(tableToStoreIn);
            if (!entities.isEmpty()) {
                System.out.println("entities to put in " + tableToStoreIn + " : " + entities.size());
                List<JsonRecordTransport> recordsToStore = new ArrayList<>(entities.size()); // it's now bothering me a fair bit that I didn't used to initialise such lists!
                // todo : write locking the db probably should start here
                // multi thread this chunk? It can slow things down a little . . .
                for (AzquoMemoryDBEntity entity : new ArrayList<>(entities)) { // we're taking a copy of the set before running through it. Copy perhaps expensive but consistency is important
                    JsonRecordTransport.State state = JsonRecordTransport.State.UPDATE;
                    if (entity.getNeedsDeleting()) {
                        state = JsonRecordTransport.State.DELETE;
                    }
                    if (entity.getNeedsInserting()) {
                        state = JsonRecordTransport.State.INSERT;
                    }
                    recordsToStore.add(new JsonRecordTransport(entity.getId(), entity.getAsJson(), state));
                    entity.setAsPersisted(); // is this dangerous here???
                }
                // and end here
                try {
                    standardDAO.persistJsonRecords(this, tableToStoreIn, recordsToStore);// note this is multi threaded internally
                } catch (Exception e) {
                    // currently I'll just stack trace this, not sure of what would be the best strategy
                    e.printStackTrace();
                }
            }
        }
        System.out.println("persist done.");
    }

    // move name and value to the new tables
    private static AtomicInteger saveToNewTablesCount = new AtomicInteger(0);

    public void saveToNewTables() throws Exception {
        saveToNewTablesCount.incrementAndGet();
        nameDAO.createFastTableIfItDoesntExist(this);
        nameDAO.clearFastNameTable(this);
        valueDAO.createFastTableIfItDoesntExist(this);
        valueDAO.clearFastValueTable(this);
        nameDAO.persistNames(this, nameByIdMap.values(), true);
        valueDAO.persistValues(this, valueByIdMap.values(), true);
    }

    // will block currently! - a concern due to the writing synchronized above?

    protected synchronized int getNextId() {
        nextId++; // increment but return what it was . . .a little messy but I want that value in memory to be what it says
        return nextId - 1;
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

    //fundamental low level function to get a set of names from the attribute indexes. Forces case insensitivity.
    // TODO address whether wrapping in a hash set here is the best plan. Memory of that object not such of an issue since it should be small and disposable
    // The iterator from CopyOnWriteArray does NOT support changes e.g. remove. A point.

    private static AtomicInteger getAttributesCount = new AtomicInteger(0);

    public List<String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return Collections.unmodifiableList(new ArrayList<>(nameByAttributeMap.keySet()));
    }

    private static AtomicInteger getNamesForAttributeCount = new AtomicInteger(0);

    private Set<Name> getNamesForAttribute(final String attributeName, final String attributeValue) {
        getNamesForAttributeCount.incrementAndGet();
        Map<String, List<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map != null) { // that attribute is there
            List<Name> names = map.get(attributeValue.toLowerCase().trim());
            if (names != null) { // were there any entries for that value?
                return new HashSet<>(names);
            }
        }
        return Collections.emptySet(); // moving away from nulls
    }

    // for checking confidential, will save considerable time

    private static AtomicInteger attributeExistsInDBCount = new AtomicInteger(0);

    public boolean attributeExistsInDB(final String attributeName) {
        attributeExistsInDBCount.incrementAndGet();
        return nameByAttributeMap.get(attributeName.toUpperCase().trim()) != null;
    }

    // same as above but then zap any not in the parent

    private static AtomicInteger getNamesForAttributeAndParentCount = new AtomicInteger(0);

    private Set<Name> getNamesForAttributeAndParent(final String attributeName, final String attributeValue, Name parent) {
        getNamesForAttributeAndParentCount.incrementAndGet();
        Set<Name> possibles = getNamesForAttribute(attributeName, attributeValue);
        for (Name possible : possibles) {
            if (possible.getParents().contains(parent)) {//trying for immediate parent first
                Set<Name> found = new HashSet<>();
                found.add(possible);
                return found;
            }
        }
        Iterator<Name> iterator = possibles.iterator();
        while (iterator.hasNext()) {
            Name possible = iterator.next();
            if (!possible.findAllParents().contains(parent)) {//logic changed b y WFC 30/06/15 to allow sets import to search within a general set (e.g. 'date') rather than need an immediate parent (e.g. 'All dates')
                iterator.remove();
            }
        }
        return possibles;
    }

    // work through a list of possible names for a given attribute in order that the attribute names are listed. Parent optional

    private static AtomicInteger getNamesForAttributeNamesAndParentCount = new AtomicInteger(0);

    public Set<Name> getNamesForAttributeNamesAndParent(final List<String> attributeNames, final String attributeValue, Name parent) {
        getNamesForAttributeNamesAndParentCount.incrementAndGet();
        if (parent != null) {
            for (String attributeName : attributeNames) {
                Set<Name> names = getNamesForAttributeAndParent(attributeName, attributeValue, parent);
                if (!names.isEmpty()) {
                    return names;
                }
            }
        } else {
            if (attributeNames != null) {
                for (String attributeName : attributeNames) {
                    Set<Name> names = getNamesForAttribute(attributeName, attributeValue);
                    if (!names.isEmpty()) {
                        return names;
                    }
                }
            }
        }
        return Collections.emptySet();
    }

/*    public Name getNameByDefaultDisplayName(final String attributeValue) {
        return getNameByAttribute( Arrays.asList(Name.DEFAULT_DISPLAY_NAME), attributeValue, null);
    }

    public Name getNameByDefaultDisplayName(final String attributeValue, final Name parent) {
        return getNameByAttribute( Arrays.asList(Name.DEFAULT_DISPLAY_NAME), attributeValue, parent);
    }*/

    private static AtomicInteger getNameByAttributeCount = new AtomicInteger(0);

    public Name getNameByAttribute(final String attributeName, final String attributeValue, final Name parent) {
        getNameByAttributeCount.incrementAndGet();
        return getNameByAttribute(Collections.singletonList(attributeName), attributeValue, parent);
    }

    private static AtomicInteger getNameByAttribute2Count = new AtomicInteger(0);

    public Name getNameByAttribute(final List<String> attributeNames, final String attributeValue, final Name parent) {
        getNameByAttribute2Count.incrementAndGet();
        Set<Name> possibles = getNamesForAttributeNamesAndParent(attributeNames, attributeValue, parent);
        // all well and good but now which one to return?
        if (possibles.size() == 1) { // simple
            return possibles.iterator().next();
        } else if (possibles.size() > 1) { // more than one . . . try and replicate logic that was there before
            if (parent == null) { // no parent criteria
                for (Name possible : possibles) {
                    if (possible.getParents().size() == 0) { // we chuck back the first top level one. Not sure this is the best logic, more than one possible with no top levels means return null
                        return possible;
                    }
                }
            } else { // if there were more than one found taking into account parent criteria simply return the first
                return possibles.iterator().next();
            }
        }
        return null;
    }

    private static AtomicInteger getNamesWithAttributeContainingCount = new AtomicInteger(0);

    public Set<Name> getNamesWithAttributeContaining(final String attributeName, final String attributeValue) {
        getNamesWithAttributeContainingCount.incrementAndGet();
        return getNamesByAttributeValueWildcards(attributeName, attributeValue, true, true);
    }

    // get names containing an attribute using wildcards, start end both

    private static AtomicInteger getNamesByAttributeValueWildcardsCount = new AtomicInteger(0);

    private Set<Name> getNamesByAttributeValueWildcards(final String attributeName, final String attributeValueSearch, final boolean startsWith, final boolean endsWith) {
        getNamesByAttributeValueWildcardsCount.incrementAndGet();
        final Set<Name> names = new HashSet<>();
        if (attributeName.length() == 0) {
            for (String attName : nameByAttributeMap.keySet()) {
                names.addAll(getNamesByAttributeValueWildcards(attName, attributeValueSearch, startsWith, endsWith));
                if (names.size() > 0) {
                    return names;
                }
            }
            return names;
        }
        final String uctAttributeName = attributeName.toUpperCase().trim();
        final String lctAttributeValueSearch = attributeValueSearch.toLowerCase().trim();
        if (nameByAttributeMap.get(uctAttributeName) == null) {
            return names;
        }
        for (String attributeValue : nameByAttributeMap.get(uctAttributeName).keySet()) {
            if (startsWith && endsWith) {
                if (attributeValue.contains(lctAttributeValueSearch)) {
                    names.addAll(nameByAttributeMap.get(uctAttributeName).get(attributeValue));
                }
            } else if (startsWith) {
                if (attributeValue.startsWith(lctAttributeValueSearch)) {
                    names.addAll(nameByAttributeMap.get(uctAttributeName).get(attributeValue));
                }
            } else if (endsWith) {
                if (attributeValue.endsWith(lctAttributeValueSearch)) {
                    names.addAll(nameByAttributeMap.get(uctAttributeName).get(attributeValue));
                }
            }

        }
        return names;
    }

    private static AtomicInteger zapUnusedNamesCount = new AtomicInteger(0);

    public void zapUnusedNames() throws Exception {
        zapUnusedNamesCount.incrementAndGet();
        for (Name name : nameByIdMap.values()) {
            // remove everything except top layer and names with values. Change parents to top layer where sets deleted
            if (name.getParents().size() > 0 && name.getValues().size() == 0) {
                Name topParent = name.findATopParent();
                for (Name child : name.getChildren()) {
                    topParent.addChildWillBePersisted(child);
                }
                name.delete();
            }
        }
        for (Name name : nameByIdMap.values()) {
            if (name.getParents().size() == 0 && name.getChildren().size() == 0 && name.getValues().size() == 0) {
                name.delete();
            }
        }
    }

    private static AtomicInteger findTopNamesCount = new AtomicInteger(0);

    public List<Name> findTopNames(String language) {
        findTopNamesCount.incrementAndGet();
        Map<String, List<Name>> thisMap = nameByAttributeMap.get(language);

        final List<Name> toReturn = new ArrayList<>();
        for (List<Name> names : thisMap.values()) {
            for (Name name : names) {
                if (name.getParents().size() == 0) {
                    toReturn.add(name);
                } else {
                    boolean include = true;
                    for (Name parent : name.getParents()) {
                        if (parent.getAttribute(language) != null) {
                            include = false;
                            break;
                        }
                    }
                    if (include) {
                        toReturn.add(name);
                    }
                }
            }

        }
        return toReturn;
    }

    private static AtomicInteger findTopNames2Count = new AtomicInteger(0);

    public List<Name> findTopNames() {
        findTopNames2Count.incrementAndGet();
        final List<Name> toReturn = new ArrayList<Name>();
        for (Name name : nameByIdMap.values()) {
            if (name.getParents().size() == 0) {
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
        countCache.clear();
        setCache.clear();
    }

    // trying for a basic count and set cache

    private final Map<String, Integer> countCache = new ConcurrentHashMap<>();

    public void setCountInCache(String key, Integer count) {
        countCache.put(key, count);
    }

    public Integer getCountFromCache(String key) {
        return countCache.get(key);
    }

    private final Map<String, Set<Name>> setCache = new ConcurrentHashMap<>();

    public void setSetInCache(String key, Set<Name> set) {
        setCache.put(key, set);
    }

    public Set<Name> getSetFromCache(String key) {
        return setCache.get(key);
    }

    // should do the trick though I worry a little about concurrency, that is to say how fast does the cache invalidation need to become visible
    // headings should be sorted after the options so hopefully ok? Watch for this
    private static AtomicInteger clearSetAndCountCacheForNameCount = new AtomicInteger(0);

    public void clearSetAndCountCacheForName(Name name) {
        clearSetAndCountCacheForNameCount.incrementAndGet();
        for (Name parent : name.findAllParents()){ // I hope this isn't too expensive
            clearSetAndCountCacheForString(parent.getDefaultDisplayName()); // in another language could cause a problem. If we could get the ids this would be more reliable.
            // Of course the ids means we could get a name list from parse query. A thought.
        }
    }

    private static AtomicInteger clearSetAndCountCacheForStringCount = new AtomicInteger(0);

    private void clearSetAndCountCacheForString(String s) {
        clearSetAndCountCacheForStringCount.incrementAndGet();
        s = s.toUpperCase();
        for (Iterator<Map.Entry<String, Integer>> it = countCache.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Integer> entry = it.next();
            if (entry.getKey().toUpperCase().contains(s)) {
                it.remove();
            }
        }
        for (Iterator<Map.Entry<String, Set<Name>>> it = setCache.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Set<Name>> entry = it.next();
            if (entry.getKey().toUpperCase().contains(s)) {
                it.remove();
            }
        }
    }

    public Provenance getProvenanceById(final int id) {
        return provenanceByIdMap.get(id);
    }

    // would need to be synchronized if not on a concurrent map

    protected void addNameToDb(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        // there was a check that the name had an Id greater than 0, I don't know why
        //if (newName.getId() > 0 && nameByIdMap.get(newName.getId()) != null) {
        if (nameByIdMap.putIfAbsent(newName.getId(), newName) != null) {
            throw new Exception("tried to add a name to the database with an existing id!");
        }

    }

    protected void removeNameFromDb(final Name toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        nameByIdMap.remove(toRemove.getId());
    }

    // ok I'd have liked this to be part of add name to db but the name won't have been initialised, add name to db is called in the name constructor
    // before the attributes have been initialised

    private static AtomicInteger addNameToAttributeNameMapCount = new AtomicInteger(0);

    protected void addNameToAttributeNameMap(final Name newName) throws Exception {
        addNameToAttributeNameMapCount.incrementAndGet();
        newName.checkDatabaseMatches(this);
        // skip the map to save the memory
        int i = 0;
        List<String> attributeValues = newName.getAttributeValues();
        for (String attributeName : newName.getAttributeKeys()) {
            setAttributeForNameInAttributeNameMap(attributeName, attributeValues.get(i), newName);
            i++;
        }
    }

    // Sets indexes for names, this needs to be thread safe to support multi threaded name linking
    // todo - address the uppercase situation with attributes - are they always stored like that? Am I reprocessing strings unnecessarily?

    private static AtomicInteger setAttributeForNameInAttributeNameMapCount = new AtomicInteger(0);

    public void setAttributeForNameInAttributeNameMap(String attributeName, String attributeValue, Name name) {
        setAttributeForNameInAttributeNameMapCount.incrementAndGet();
        // upper and lower seems a bit arbitrary. Hmmm.
        // these interns have been tested as helping memory usage
        String lcAttributeValue = attributeValue.toLowerCase().trim().intern();
        String ucAttributeName = attributeName.toUpperCase().trim().intern();
        if (lcAttributeValue.indexOf(Name.QUOTE) >= 0 && !ucAttributeName.equals(Name.CALCULATION)) {
            lcAttributeValue = lcAttributeValue.replace(Name.QUOTE, '\'').intern();
        }

        // adapted from stack overflow, cheers!
        Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
        if (namesForThisAttribute == null) {
            final Map<String, List<Name>> newNamesForThisAttribute = new ConcurrentHashMap<>();
            namesForThisAttribute = nameByAttributeMap.putIfAbsent(ucAttributeName, newNamesForThisAttribute);// in ConcurrentHashMap this is atomic, thanks Doug!
            if (namesForThisAttribute == null) {// the new one went in, use it, otherwise use the one that "sneaked" in there in the mean time :)
                namesForThisAttribute = newNamesForThisAttribute;
            }
        }

        // same pattern but for the lists. Generally these lists will be single and not modified often so I think copy on write array should do the high read speed thread safe trick!

        List<Name> names = namesForThisAttribute.get(lcAttributeValue);
        if (names == null) {
            final List<Name> newNames = new CopyOnWriteArrayList<>();// cost on writes but thread safe reads, might take a little more memory than the ol arraylist, hopefully not a big prob
            names = namesForThisAttribute.putIfAbsent(lcAttributeValue, newNames);
            if (names == null) {
                names = newNames;
            }
        }
        // ok, got names
        names.add(name); // threadsafe, internally locked but of course just for this particular attribute and value heh.
        // Could maybe get a little speed by adding a special case for the first name . . .meh.
    }

    // I think this is just much more simple re thread safety in that if we can't find the map and list we just don't do anything and the final remove should be safe according to CopyOnWriteArray

    private static AtomicInteger removeAttributeFromNameInAttributeNameMapCount = new AtomicInteger(0);

    protected void removeAttributeFromNameInAttributeNameMap(final String attributeName, final String attributeValue, final Name name) throws Exception {
        removeAttributeFromNameInAttributeNameMapCount.incrementAndGet();
        String ucAttributeName = attributeName.toUpperCase().trim();
        String lcAttributeValue = attributeValue.toLowerCase().trim();
        name.checkDatabaseMatches(this);
        final Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
        if (namesForThisAttribute != null) {// the map we care about
            final List<Name> namesForThatAttributeAndAttributeValue = namesForThisAttribute.get(lcAttributeValue);
            if (namesForThatAttributeAndAttributeValue != null) {
                namesForThatAttributeAndAttributeValue.remove(name); // if it's there which it should be zap it from the set . . .
            }
        }
    }

    private static AtomicInteger batchLinkerRunCount = new AtomicInteger(0);

    private class BatchLinker implements Runnable {
        private final AtomicInteger loadTracker;
        private final List<Name> batchToLink;

        public BatchLinker(AtomicInteger loadTracker, List<Name> batchToLink) {
            this.loadTracker = loadTracker;
            this.batchToLink = batchToLink;
        }

        @Override
        public void run() { // well this is what's going to truly test concurrent modification of a database
            batchLinkerRunCount.incrementAndGet();
            for (Name name : batchToLink) {
                try { // I think the only exception is the db not matching one.
                    name.link();
                    addNameToAttributeNameMap(name);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            logInSessionLogAndSystem("Linked : " + loadTracker.addAndGet(batchToLink.size()));
        }
    }

    // to be called after loading moves the json and extracts attributes to useful maps here
    // called after loading as the names reference themselves
    // going to try a basic multi-thread - it was 100,000 but I wonder if this is as efficient as it could be given that at the end leftover threads can hang around. Trying for 50,000

    int batchLinkSize = 50000;

    private static AtomicInteger linkEntitiesCount = new AtomicInteger(0);

    private synchronized void linkEntities() throws Exception {
        linkEntitiesCount.incrementAndGet();
        // there may be a certain overhead to building the batches but otherwise it's dealing with millions of threads. My gut says this will be more of an overhead.
        ExecutorService executor = Executors.newFixedThreadPool(reportFillerThreads); // this is pure java, use the report filler criteria
        AtomicInteger loadTracker = new AtomicInteger(0);
        ArrayList<Name> batchLink = new ArrayList<>(batchLinkSize);
        for (Name name : nameByIdMap.values()) {
            batchLink.add(name);
            if (batchLink.size() == batchLinkSize) {
                executor.execute(new BatchLinker(loadTracker, batchLink));
                batchLink = new ArrayList<>(batchLinkSize);
            }
        }
        // link leftovers
        executor.execute(new BatchLinker(loadTracker, batchLink));
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            throw new Exception("Database " + getMySQLName() + " took longer than an hour to link");
        }
/*
        for (Name name : nameByIdMap.values()) {
            name.populateFromJson();
            addNameToAttributeNameMap(name);
        }*/

    }

    // trying for new more simplified persistence - make functions not linked to classes
    // maps will be set up in the constructor. Think about any concurrency issues here???
    // throw exception if loading?

    private static AtomicInteger setEntityNeedsPersistingCount = new AtomicInteger(0);

    protected void setEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity azquoMemoryDBEntity) {
        setEntityNeedsPersistingCount.incrementAndGet();
        if (!needsLoading && entitiesToPersist.size() > 0) {
            entitiesToPersist.get(tableToPersistIn).add(azquoMemoryDBEntity);
        }
    }

    private static AtomicInteger removeEntityNeedsPersistingCount = new AtomicInteger(0);

    protected void removeEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity azquoMemoryDBEntity) {
        removeEntityNeedsPersistingCount.incrementAndGet();
        if (!needsLoading) {
            entitiesToPersist.get(tableToPersistIn).remove(azquoMemoryDBEntity);
        }
    }

    protected void addValueToDb(final Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.putIfAbsent(newValue.getId(), newValue) != null) { // != null means there was something in there
            throw new Exception("tried to add a value to the database with an existing id!");
        }
    }

    protected void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.putIfAbsent(newProvenance.getId(), newProvenance) != null) {
            throw new Exception("tried to add a privenance to the database with an existing id!");
        }
    }

    // note : the jmap histogram may make this a little redundant.
    // leaving here commented for the moment

/*
    public void memoryReport() {
        try {
            // simple by id maps, if an object is in one of these three it's in the database
            System.out.println("size of nameByIdMap : " + ObjectSizeCalculator.sizeOfForAzquo(nameByIdMap, null));
            System.out.println("size of nameByAttributeMap : " + ObjectSizeCalculator.sizeOfForAzquo(nameByAttributeMap, null));
            System.out.println("size of valueByIdMap : " + ObjectSizeCalculator.sizeOfForAzquo(valueByIdMap, null));
            System.out.println("size of provenanceByIdMap : " + ObjectSizeCalculator.sizeOfForAzquo(provenanceByIdMap, null));
            // ok I want to size all the names here

            long totalNameSize = 0;
            Collection<Name> names = nameByIdMap.values();
            DecimalFormat df = new DecimalFormat("###,###,###,###");
            for (Name name : names) {
                //System.out.println("trying for " + name);
                long nameSize = ObjectSizeCalculator.sizeOfForAzquo(name, null);
                totalNameSize += nameSize;
                //if (count%10000 == 0){
                  //  System.out.println("Example name size : " + name.getDefaultDisplayName() + ", " + df.format(nameSize));
                  //  List<StringBuilder> report = new ArrayList<StringBuilder>();
                  //  ObjectSizeCalculator.sizeOfForAzquo(name, report);
                  //  for (StringBuilder sb : report){
                  //      System.out.println(sb);
                  //  }

            }
            System.out.println("total names size : " + df.format(totalNameSize));
            System.out.println("size per name : " + totalNameSize / nameByIdMap.size());


            long totalValuesSize = 0;
            Collection<Value> values = valueByIdMap.values();
            for (Value value : values) {
                long valueSize = ObjectSizeCalculator.sizeOfForAzquo(value, null);
                totalValuesSize += valueSize;
//                if (count%10000 == 0){
 //                   System.out.println("Example value size : " + value + ", " + df.format(valueSize));
   //                 List<StringBuilder> report = new ArrayList<StringBuilder>();
     //               ObjectSizeCalculator.sizeOfForAzquo(value, report);
       //             for (StringBuilder sb : report){
         //               System.out.println(sb);
           //         }
             //   }
            }
            System.out.println("total values size : " + df.format(totalValuesSize));
            System.out.println("size per value : " + totalValuesSize / valueByIdMap.size());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }*/

    public boolean getFastLoaded() {
        return fastLoaded;
    }

    public void saveInFastTables(){

    }

    public static void printFunctionCountStats() {
        System.out.println("######### AZQUO MEMORY DB FUNCTION COUNTS");
        System.out.println("newDatabaseCount\t\t\t\t" + newDatabaseCount.get());
        System.out.println("newSQLBatchLoaderRunCount\t\t\t\t" + newSQLBatchLoaderRunCount.get());
        System.out.println("newNameBatchLoaderRunCount\t\t\t\t" + newNameBatchLoaderRunCount.get());
        System.out.println("newValueBatchLoaderRunCount\t\t\t\t" + newValueBatchLoaderRunCount.get());
        System.out.println("loadDataCount\t\t\t\t" + loadDataCount.get());
        System.out.println("saveDataToMySQLCount\t\t\t\t" + saveDataToMySQLCount.get());
        System.out.println("saveToNewTablesCount\t\t\t\t" + saveToNewTablesCount.get());
        System.out.println("getAttributesCount\t\t\t\t" + getAttributesCount.get());
        System.out.println("getNamesForAttributeCount\t\t\t\t" + getNamesForAttributeCount.get());
        System.out.println("attributeExistsInDBCount\t\t\t\t" + attributeExistsInDBCount.get());
        System.out.println("getNamesForAttributeAndParentCount\t\t\t\t" + getNamesForAttributeAndParentCount.get());
        System.out.println("getNamesForAttributeNamesAndParentCount\t\t\t\t" + getNamesForAttributeNamesAndParentCount.get());
        System.out.println("getNameByAttributeCount\t\t\t\t" + getNameByAttributeCount.get());
        System.out.println("getNameByAttribute2Count\t\t\t\t" + getNameByAttribute2Count.get());
        System.out.println("getNamesWithAttributeContainingCount\t\t\t\t" + getNamesWithAttributeContainingCount.get());
        System.out.println("getNamesByAttributeValueWildcardsCount\t\t\t\t" + getNamesByAttributeValueWildcardsCount.get());
        System.out.println("zapUnusedNamesCount\t\t\t\t" + zapUnusedNamesCount.get());
        System.out.println("findTopNamesCount\t\t\t\t" + findTopNamesCount.get());
        System.out.println("findTopNames2Count\t\t\t\t" + findTopNames2Count.get());
        System.out.println("clearCachesCount\t\t\t\t" + clearCachesCount.get());
        System.out.println("clearSetAndCountCacheForNameCount\t\t\t\t" + clearSetAndCountCacheForNameCount.get());
        System.out.println("clearSetAndCountCacheForStringCount\t\t\t\t" + clearSetAndCountCacheForStringCount.get());
        System.out.println("setAttributeForNameInAttributeNameMapCount\t\t\t\t" + setAttributeForNameInAttributeNameMapCount.get());
        System.out.println("removeAttributeFromNameInAttributeNameMapCount\t\t\t\t" + removeAttributeFromNameInAttributeNameMapCount.get());
        System.out.println("batchLinkerRunCount\t\t\t\t" + batchLinkerRunCount.get());
        System.out.println("linkEntitiesCount\t\t\t\t" + linkEntitiesCount.get());
        System.out.println("setEntityNeedsPersistingCount\t\t\t\t" + setEntityNeedsPersistingCount.get());
        System.out.println("removeEntityNeedsPersistingCount\t\t\t\t" + removeEntityNeedsPersistingCount.get());
    }

    public static void clearFunctionCountStats() {
        newDatabaseCount.set(0);
        newSQLBatchLoaderRunCount.set(0);
        newNameBatchLoaderRunCount.set(0);
        newValueBatchLoaderRunCount.set(0);
        loadDataCount.set(0);
        saveDataToMySQLCount.set(0);
        saveToNewTablesCount.set(0);
        getAttributesCount.set(0);
        getNamesForAttributeCount.set(0);
        attributeExistsInDBCount.set(0);
        getNamesForAttributeAndParentCount.set(0);
        getNamesForAttributeNamesAndParentCount.set(0);
        getNameByAttributeCount.set(0);
        getNameByAttribute2Count.set(0);
        getNamesWithAttributeContainingCount.set(0);
        getNamesByAttributeValueWildcardsCount.set(0);
        zapUnusedNamesCount.set(0);
        findTopNamesCount.set(0);
        findTopNames2Count.set(0);
        clearCachesCount.set(0);
        clearSetAndCountCacheForNameCount.set(0);
        clearSetAndCountCacheForStringCount.set(0);
        setAttributeForNameInAttributeNameMapCount.set(0);
        removeAttributeFromNameInAttributeNameMapCount.set(0);
        batchLinkerRunCount.set(0);
        linkEntitiesCount.set(0);
        setEntityNeedsPersistingCount.set(0);
        removeEntityNeedsPersistingCount.set(0);
    }

    public static void printAllCountStats(){
        printFunctionCountStats();
        Name.printFunctionCountStats();
        Value.printFunctionCountStats();
        NameService.printFunctionCountStats();
        ValueService.printFunctionCountStats();
    }

    public static void clearAllCountStats(){
        clearFunctionCountStats();
        Name.clearFunctionCountStats();
        Value.clearFunctionCountStats();
        NameService.clearFunctionCountStats();
        ValueService.clearFunctionCountStats();
    }
}