package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.*;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
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

    // have given up trying this through spring for the moment
    private static Properties azquoProperties = new Properties();

    // no point doing this on every constructor!
    static {
        try {
            azquoProperties.load(AzquoMemoryDB.class.getClassLoader().getResourceAsStream("azquo.properties")); // easier than messing around with spring -
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final Logger logger = Logger.getLogger(AzquoMemoryDB.class);

    private final Map<String, Map<String, List<Name>>> nameByAttributeMap; // a map of maps of lists of names. Fun! Moved back to lists to save memory, the lists are unlikely to be big

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

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

    // may need to tweak this - since SQL is IO Bound then ramping it up more may not be the best idea. Also persistence will be using processors of course.
    private static ExecutorService getSQLThreadPool() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int possibleLoadingThreads = availableProcessors < 4 ? availableProcessors : (availableProcessors / 2);
        if (possibleLoadingThreads > 8) { // I think more than this asks for trouble - processors isn't really the prob with persistence it's IO! I should be asking : is the disk SSD?
            possibleLoadingThreads = 8;
        }
        System.out.println("memory db transport threads : " + possibleLoadingThreads);
        return Executors.newFixedThreadPool(possibleLoadingThreads);
    }

    private static ExecutorService getMainThreadPool() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (availableProcessors == 1) {
            return Executors.newFixedThreadPool(availableProcessors);
        } else {
            if (availableProcessors > 15){
                System.out.println("reportFillerThreads : " + 15);
                return Executors.newFixedThreadPool(15);
            } else {
                System.out.println("reportFillerThreads : " + (availableProcessors - 1));
                return Executors.newFixedThreadPool(availableProcessors - 1);
            }
        }
    }

    // it is true that having these two as separate pools could make more threads than processors (as visible to the OS) but it's not the end of the world and it's still
    // an improvement over the old situation. Not only for thread management but also not creating and destroying the thread pools is better for garbage.

    public static final ExecutorService mainThreadPool = getMainThreadPool();
    public static final ExecutorService sqlThreadPool = getSQLThreadPool();

    protected AzquoMemoryDB(String persistenceName, StringBuffer sessionLog) {
        newDatabaseCount.incrementAndGet();
        this.sessionLog = sessionLog;
        this.persistenceName = persistenceName;
        needsLoading = true;
        nameByAttributeMap = new ConcurrentHashMap<>();
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

    // not sure what to do with this, on its own it would stop persisting but not much else
    /*public void zapDatabase() {
        persistenceName = null;
    }*/

    boolean getNeedsLoading() {
        return needsLoading;
    }

    /**
     * Interface to enable initialization of json persisted entities to be more generic.
     * This sort of solves a language problem I was concerned by before but now it will only be used by Provenance and indeed in here hence why it's internal
     */
    public interface JsonSerializableEntityInitializer {
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

    // needs factoring

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

    // needs factoring - if the dao were passed then one could use one Batch Loader instead of theses two. Todo.

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
        loadDataCount.incrementAndGet();
        boolean memoryTrack = "true".equals(azquoProperties.getProperty("memorytrack"));
        if (needsLoading) { // only allow it once!
            long startTime = System.currentTimeMillis();
            logInSessionLogAndSystem("loading data for " + getPersistenceName());
            // using system.gc before and after loading to get an idea of DB memory overhead
            long marker = System.currentTimeMillis();
            Runtime runtime = Runtime.getRuntime();
            long usedMB = 0;
            int mb = 1024 * 1024;
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
                    for (String tableName : jsonTablesAndInitializers.keySet()) {
                        jsonRecordsLoaded.set(0);
                        from = 0;
                        maxIdForTable = JsonRecordDAO.findMaxId(this, tableName);
                        futureBatches = new ArrayList<>();
                        while (from < maxIdForTable) {
                            futureBatches.add(sqlThreadPool.submit(new SQLBatchLoader(tableName, jsonTablesAndInitializers.get(tableName), from, from + step, this, jsonRecordsLoaded)));
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
                        futureBatches.add(sqlThreadPool.submit(new NameBatchLoader(from, from + step, this, namesLoaded)));
                        from += step;
                    }
                    for (Future future : futureBatches) {
                        future.get(1, TimeUnit.HOURS);
                    }
                    logInSessionLogAndSystem("Names loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
                    marker = System.currentTimeMillis();
                    // todo finish hbase detection and loading!
                    from = 0;
                    maxIdForTable = ValueDAO.findMaxId(this);
                    futureBatches = new ArrayList<>();
                    while (from < maxIdForTable) {
                        futureBatches.add(sqlThreadPool.submit(new ValueBatchLoader(from, from + step, this, valuesLoaded)));
                        from += step;
                    }
                    for (Future future : futureBatches) {
                        future.get(1, TimeUnit.HOURS);
                    }
                    logInSessionLogAndSystem("Values loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");



                marker = System.currentTimeMillis();
                // wait until all are loaded before linking
                System.out.println(provenaceLoaded.get() + valuesLoaded.get() + namesLoaded.get() + " unlinked entities loaded in " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
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
    public synchronized void lockTest(){};

    // reads from a list of changed objects
    // should we synchronize on a write lock object? I think it might be a plan.

    private static AtomicInteger persisttoDataStoreCount = new AtomicInteger(0);

    public synchronized void persistToDataStore() {
        System.out.println("PERSIST STARTING");
        persisttoDataStoreCount.incrementAndGet();
        // todo : write locking the db probably should start here
        // this is where I need to think carefully about concurrency, azquodb has the last say when the sets are modified although the flags are another point
        // just a simple DB write lock should to it
        // for the moment just make it work.
        // ok first do the json bits, as mentioned currently this is just provenance, may well be others
        // new switch on habse or not
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

    private static AtomicInteger getAttributesCount = new AtomicInteger(0);

    public List<String> getAttributes() {
        getAttributesCount.incrementAndGet();
        return Collections.unmodifiableList(new ArrayList<>(nameByAttributeMap.keySet()));
    }

    //fundamental low level function to get a set of names from the attribute indexes. Forces case insensitivity.
    // Is wrapping in a hashSet such a big deal? Using koloboke immutable should be a little more efficient

    private static AtomicInteger getNamesForAttributeCount = new AtomicInteger(0);

    private Set<Name> getNamesForAttribute(final String attributeName, final String attributeValue) {
        getNamesForAttributeCount.incrementAndGet();
        Map<String, List<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map != null) { // that attribute is there
            List<Name> names = map.get(attributeValue.toLowerCase().trim());
            if (names != null) { // were there any entries for that value?
                return HashObjSets.newMutableSet(names); // I've seen this modified outside, I guess no harm in that
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
            if (possible.getParents().contains(parent)) { //trying for immediate parent first
                Set<Name> found = HashObjSets.newMutableSet();
                found.add(possible);
                return found; // and return straight away
            }
        }
        Iterator<Name> iterator = possibles.iterator();
        while (iterator.hasNext()) {
            Name possible = iterator.next();
            if (!possible.findAllParents().contains(parent)) {//logic changed by WFC 30/06/15 to allow sets import to search within a general set (e.g. 'date') rather than need an immediate parent (e.g. 'All dates')
                iterator.remove();
            }
        }
        return possibles; // so this could be more than one if there were multiple in a big parent set (presumably at different levels)
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

    public List<Name> findDuplicateNames(String attributeName, Set<String> exceptions) {
        List<Name> found = new ArrayList<>();
        Map<String, List<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map == null) return null;
        int dupCount = 0;
        int testCount = 0;
        for (String string : map.keySet()) {
            if (testCount++ % 50000 == 0)
                System.out.println("testing for duplicates - count " + testCount + " dups found " + dupCount);
            if (map.get(string).size() > 1) {
                List<Name> names = map.get(string);
                boolean nameadded = false;
                for (Name name : names) {
                    for (String attribute : name.getAttributeKeys()) {
                        if (name.getAttributes().size() == 1 || (!attribute.equals(attributeName) && !exceptions.contains(attribute))) {
                            String attValue = name.getAttribute(attribute);
                            for (Name name2 : names) {
                                if (name2.getId() == name.getId()) break;
                                List<String> attKeys2 = name2.getAttributeKeys();
                                //note checking here only on the attribute values of the name itself (not parent names)
                                if (attKeys2.contains(attribute) && name2.getAttribute(attribute).equals(attValue)) {
                                    if (!nameadded) {
                                        found.add(name);
                                        nameadded = true;
                                    }
                                    found.add(name2);
                                    dupCount++;

                                }
                            }
                        }

                    }

                }
                if (dupCount > 100) {
                    break;
                }
            }
        }
        return found;
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
                    if (!possible.hasParents()) { // we chuck back the first top level one. Not sure this is the best logic, more than one possible with no top levels means return null
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
        final Set<Name> names = HashObjSets.newMutableSet();
        if (attributeName.length() == 0) { // odd that it might be
            for (String attName : nameByAttributeMap.keySet()) {
                names.addAll(getNamesByAttributeValueWildcards(attName, attributeValueSearch, startsWith, endsWith)); // and when attribute name is blank we don't return for all attribute names, just the first that contains this
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
            if (name.hasParents() && !name.hasValues()) {
                Name topParent = name.findATopParent();
                for (Name child : name.getChildren()) {
                    topParent.addChildWillBePersisted(child);
                }
                name.delete();
            }
        }
        for (Name name : nameByIdMap.values()) {
            if (!name.hasParents() && !name.hasChildren() && !name.hasValues()) {
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
                if (!name.hasParents()) { // top parent full stop
                    toReturn.add(name);
                } else { // little hazy on the logic here but I think the point is to say that if all the parents of the name are NOT in the language specified then that's a top name for this language.
                    // Kind of makes sense but where is this used?
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

    // TODO. Also, compute if absent?
    public Set<Name> getSetFromCache(String key) {
        return setCache.get(key);
    }

    // should do the trick though I worry a little about concurrency, that is to say how fast does the cache invalidation need to become visible
    // headings should be sorted after the options so hopefully ok? Watch for this
    private static AtomicInteger clearSetAndCountCacheForNameCount = new AtomicInteger(0);

    void clearSetAndCountCacheForName(Name name) {
        clearSetAndCountCacheForNameCount.incrementAndGet();
        for (Name parent : name.findAllParents()) { // I hope this isn't too expensive
            clearSetAndCountCacheForString(parent.getDefaultDisplayName()); // in another language could cause a problem. If we could get the ids this would be more reliable.
            // Of course the ids means we could get a name list from parse query. A thought.
        }
    }

    private static AtomicInteger clearSetAndCountCacheForStringCount = new AtomicInteger(0);

    private void clearSetAndCountCacheForString(String s) {
        clearSetAndCountCacheForStringCount.incrementAndGet();
        if (s != null) {
            s = s.toUpperCase();
            for (Iterator<Map.Entry<String, Integer>> it = countCache.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Integer> entry = it.next();
                if (entry.getKey().toUpperCase().contains(s)) {
                    it.remove();
                }
            }
            for (Iterator<Map.Entry<String, Set<Name>>> it = setCache.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Set<Name>> entry = it.next();
                if (entry.getKey().toUpperCase().contains(s)) {
                    it.remove();
                }
            }
        }
    }

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

    // ok I'd have liked this to be part of add name to db but the name won't have been initialised, add name to db is called in the name constructor
    // before the attributes have been initialised

    private static AtomicInteger addNameToAttributeNameMapCount = new AtomicInteger(0);

    private void addNameToAttributeNameMap(final Name newName) throws Exception {
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

    // only used when looking up for "DEFINITION", inline?

    public Collection<Name> namesForAttribute(String attribute) {
        Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(attribute);
        if (namesForThisAttribute == null) return null;
        Collection<Name> toReturn = new HashSet<>();
        for (String key : namesForThisAttribute.keySet()) {
            toReturn.addAll(namesForThisAttribute.get(key));
        }
        return toReturn;
    }

    // Sets indexes for names, this needs to be thread safe to support multi threaded name linking.

    private static AtomicInteger setAttributeForNameInAttributeNameMapCount = new AtomicInteger(0);

    void setAttributeForNameInAttributeNameMap(String attributeName, String attributeValue, Name name) {
        setAttributeForNameInAttributeNameMapCount.incrementAndGet();
        // upper and lower seems a bit arbitrary, I need a way of making it case insensitive.
        // these interns have been tested as helping memory usage.
        String lcAttributeValue = attributeValue.toLowerCase().trim().intern();
        String ucAttributeName = attributeName.toUpperCase().trim().intern();
        if (lcAttributeValue.indexOf(Name.QUOTE) >= 0 && !ucAttributeName.equals(Name.CALCULATION)) {
            lcAttributeValue = lcAttributeValue.replace(Name.QUOTE, '\'').intern();
        }
        // The way to use putIfAbsent correctly according to a stack overflow example
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
        names.add(name); // thread safe, internally locked but of course just for this particular attribute and value heh.
        // Could maybe get a little speed by adding a special case for the first name (as in singleton)
    }

    // I think this is just much more simple re thread safety in that if we can't find the map and list we just don't do anything and the final remove should be safe according to CopyOnWriteArray

    private static AtomicInteger removeAttributeFromNameInAttributeNameMapCount = new AtomicInteger(0);

    void removeAttributeFromNameInAttributeNameMap(final String attributeName, final String attributeValue, final Name name) throws Exception {
        removeAttributeFromNameInAttributeNameMapCount.incrementAndGet();
        String ucAttributeName = attributeName.toUpperCase().trim();
        String lcAttributeValue = attributeValue.toLowerCase().trim();
        name.checkDatabaseMatches(this);
        final Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
        if (namesForThisAttribute != null) {// the map we care about
            final List<Name> namesForThatAttributeAndAttributeValue = namesForThisAttribute.get(lcAttributeValue);
            if (namesForThatAttributeAndAttributeValue != null) {
                namesForThatAttributeAndAttributeValue.remove(name); // if it's there which it should be zap it from the list . . .
            }
        }
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
                addNameToAttributeNameMap(name);
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
                linkFutures.add(mainThreadPool.submit(new BatchLinker(loadTracker, batchLink)));
                batchLink = new ArrayList<>(batchLinkSize);
            }
        }
        // link leftovers
        linkFutures.add(mainThreadPool.submit(new BatchLinker(loadTracker, batchLink)));
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

    void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.putIfAbsent(newProvenance.getId(), newProvenance) != null) {
            throw new Exception("tried to add a privenance to the database with an existing id!");
        }
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
        System.out.println("persisttoDataStoreCount\t\t\t\t" + persisttoDataStoreCount.get());
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
        persisttoDataStoreCount.set(0);
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
        setJsonEntityNeedsPersistingCount.set(0);
        removeJsonEntityNeedsPersistingCount.set(0);
        setNameNeedsPersistingCount.set(0);
        removeNameNeedsPersistingCount.set(0);
        setValueNeedsPersistingCount.set(0);
        removeValueNeedsPersistingCount.set(0);
    }

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