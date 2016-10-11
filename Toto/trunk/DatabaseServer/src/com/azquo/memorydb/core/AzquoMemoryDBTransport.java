package com.azquo.memorydb.core;

import com.azquo.ThreadPools;
import com.azquo.memorydb.dao.JsonRecordDAO;
import com.azquo.memorydb.dao.JsonRecordTransport;
import com.azquo.memorydb.dao.NameDAO;
import com.azquo.memorydb.dao.ValueDAO;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from AzquoMemoryDB by edward on 29/09/16.
 * <p>
 * This class is responsible for loading an saving the AzquoMemoryDB. Currently MySQL is the only form of persistence.
 *
 * I'm a little bothered about memory visibility of the
 * populated memory db but breaking the loading off into this class does't affect the logic as it was before or which thread it was running in.
 * Persist sets are using Java concurrency classes, that will be fine, it's population of names for example that might be a concern.
 *
 * The issue is object publication - I think all is well or certainly no worse than before factoring this off.
 *
 * As mentioned in AzquoMemoryDB since all Names have to reference the instance of AzquoMemoryDB then to stop this escaping from the constructor one would
 * either need to assign the AzquoMemory dbs later or somehow not put a reference in the entities which I don't think is practical.
 */
class AzquoMemoryDBTransport {

    // I figure these should be final, you initialise the transport against a given persistence store and memory db and that's that
    private final String persistenceName;
    private final AzquoMemoryDB azquoMemoryDB;

    // Used to be a map of sets for all entities but since speeding up storing of names and values with custom DAOs I figure it's best to simply have three sets
    private final Map<String, Set<AzquoMemoryDBEntity>> jsonEntitiesToPersist;
    private final Set<Name> namesToPersist;
    private final Set<Value> valuesToPersist;

    private StringBuffer sessionLog;

    AzquoMemoryDBTransport(AzquoMemoryDB azquoMemoryDB, String persistenceName, StringBuffer sessionLog) {
        this.azquoMemoryDB = azquoMemoryDB;
        this.persistenceName = persistenceName;
        jsonEntitiesToPersist = new ConcurrentHashMap<>();
        namesToPersist = Collections.newSetFromMap(new ConcurrentHashMap<>());
        valuesToPersist = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.sessionLog = sessionLog;
    }

    String getPersistenceName() {
        return persistenceName;
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

    private class SQLBatchLoader implements Callable<Void> {
        private final String tableName;
        private final JsonSerializableEntityInitializer jsonSerializableEntityInitializer;
        private final int minId;
        private final int maxId;
        private final AtomicInteger loadTracker;

        SQLBatchLoader(String tableName, JsonSerializableEntityInitializer jsonSerializableEntityInitializer, int minId, int maxId, AtomicInteger loadTracker) {
            this.tableName = tableName;
            this.jsonSerializableEntityInitializer = jsonSerializableEntityInitializer;
            this.minId = minId;
            this.maxId = maxId;
            this.loadTracker = loadTracker;
        }

        @Override
        public Void call() throws Exception {
            List<JsonRecordTransport> dataToLoad = JsonRecordDAO.findFromTableMinMaxId(persistenceName, tableName, minId, maxId);
            for (JsonRecordTransport dataRecord : dataToLoad) {
                azquoMemoryDB.setNextId(dataRecord.id);
                jsonSerializableEntityInitializer.initializeEntity(azquoMemoryDB, dataRecord);
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
            List<Name> names = NameDAO.findForMinMaxId(memDB, minId, maxId);
            for (Name name : names) {// bit of an overhead just to get the max id? I guess no concern - zapping the lists would save garbage but
                azquoMemoryDB.setNextId(name.getId());
            }
            // this was only when min id % 100_000, not sure why . . .
            loadTracker.addAndGet(names.size());
            if (minId % 1_000_000 == 0) {
                logInSessionLogAndSystem("loaded " + loadTracker.get());
            }
            return null;
        }
    }

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
            List<Value> values = ValueDAO.findForMinMaxId(memDB, minId, maxId);
            for (Value value : values) {// bit of an overhead just to get the max id? I guess no concern.
                azquoMemoryDB.setNextId(value.getId());
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

    private class BatchLinker implements Callable<Void> {
        private final AtomicInteger loadTracker;
        private final List<Name> batchToLink;

        BatchLinker(AtomicInteger loadTracker, List<Name> batchToLink) {
            this.loadTracker = loadTracker;
            this.batchToLink = batchToLink;
        }

        @Override
        public Void call() throws Exception { // well this is what's going to truly test concurrent modification of a database
            for (Name name : batchToLink) {
                name.link();
            }
            logInSessionLogAndSystem("Linked : " + loadTracker.addAndGet(batchToLink.size()));
            return null;
        }
    }

    private void logInSessionLogAndSystem(String s) {
        if (sessionLog != null) {
            sessionLog.append(s).append("\n");
        }
        System.out.println(s);
    }

    // should only be called in the memory db constructor, is there a way to enforce this?
    void loadData(boolean memoryTrack) {
        ValueDAO.createValueHistoryTableIfItDoesntExist(persistenceName); // should get rid of this at some point!
        long startTime = System.currentTimeMillis();
        logInSessionLogAndSystem("loading data for " + persistenceName);
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
                /*
                Load order is important as value and name use provenance and value uses names. Names uses itself (child/parents)
                hence all names need initialisation finished after the id map is sorted
                This is why when multi threading we wait util a type of entity is fully loaded before moving onto the next
                Atomic integers to pass through to the multi threaded code for logging, tracking numbers loaded
                */
            AtomicInteger provenanceLoaded = new AtomicInteger();
            AtomicInteger namesLoaded = new AtomicInteger();
            AtomicInteger valuesLoaded = new AtomicInteger();
            AtomicInteger jsonRecordsLoaded = new AtomicInteger();
            final int step = 100_000; // not so much step now as id range given how we're now querying mysql. Cutting down to 100,000 to reduce the chance of SQL errors
            marker = System.currentTimeMillis();
            // create thread pool, rack up the loading tasks and wait for it to finish. Repeat for name and values.
            // going to set up for multiple json persisted entities even if it's only provenance for the mo
            Map<String, JsonSerializableEntityInitializer> jsonTablesAndInitializers = new HashMap<>();
            // add lines like this for loading other json entities. A note is table names repeated, might have a think about that
            jsonTablesAndInitializers.put(Provenance.PERSIST_TABLE, (memoryDB, jsonRecordTransport) -> new Provenance(memoryDB, jsonRecordTransport.id, jsonRecordTransport.json));
            int from;
            int maxIdForTable;
            List<Future<?>> futureBatches;
            // currently just provenance
            for (String tableName : jsonTablesAndInitializers.keySet()) {
                jsonRecordsLoaded.set(0);
                from = 0;
                maxIdForTable = JsonRecordDAO.findMaxId(persistenceName, tableName);
                futureBatches = new ArrayList<>();
                while (from < maxIdForTable) {
                    futureBatches.add(ThreadPools.getSqlThreadPool().submit(new SQLBatchLoader(tableName, jsonTablesAndInitializers.get(tableName), from, from + step, tableName.equals(Provenance.PERSIST_TABLE) ? provenanceLoaded : jsonRecordsLoaded)));
                    from += step;
                }
                for (Future future : futureBatches) {
                    future.get(1, TimeUnit.HOURS);
                }
                logInSessionLogAndSystem(tableName + ", " + jsonRecordsLoaded + " records loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
            }
            marker = System.currentTimeMillis();
            from = 0;
            maxIdForTable = NameDAO.findMaxId(persistenceName);
            futureBatches = new ArrayList<>();
            while (from < maxIdForTable) {
                futureBatches.add(ThreadPools.getSqlThreadPool().submit(new NameBatchLoader(from, from + step, azquoMemoryDB, namesLoaded)));
                from += step;
            }
            for (Future future : futureBatches) {
                future.get(1, TimeUnit.HOURS);
            }
            logInSessionLogAndSystem("Names loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
            marker = System.currentTimeMillis();
            from = 0;
            maxIdForTable = ValueDAO.findMaxId(persistenceName);
            futureBatches = new ArrayList<>();
            while (from < maxIdForTable) {
                futureBatches.add(ThreadPools.getSqlThreadPool().submit(new ValueBatchLoader(from, from + step, azquoMemoryDB, valuesLoaded)));
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
            logInSessionLogAndSystem("could not load data for " + persistenceName + "!");
            e.printStackTrace();
            // todo, stop the threads
        }
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
        logInSessionLogAndSystem("Total load time for " + persistenceName + " " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
        //AzquoMemoryDB.printAllCountStats();
    }


    /* to be called after loading moves the json and extracts attributes to useful maps here called after loading as the names reference themselves
    going to try a basic multi-thread - it was 100,000 but I wonder if this is as efficient as it could be given that at the end leftover threads
    can hang around (particularly hefty child sets). Trying for 50,000 */

    private void linkEntities() throws Exception {
        // there may be a certain overhead to building the batches but otherwise it's dealing with millions of callables
        int batchLinkSize = 50_000;
        ArrayList<Name> batchLink = new ArrayList<>(batchLinkSize);
        List<Future<?>> linkFutures = new ArrayList<>();
        AtomicInteger loadTracker = new AtomicInteger(0);
        for (Name name : azquoMemoryDB.getAllNames()) {
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

    void setJsonEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity entity) {
        jsonEntitiesToPersist.computeIfAbsent(tableToPersistIn, t -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(entity);
    }

    void setNameNeedsPersisting(Name name) {
        namesToPersist.add(name);
    }

    void setValueNeedsPersisting(Value value) {
        valuesToPersist.add(value);
    }

    void persistDatabase() {
        System.out.println("PERSIST STARTING");
        // ok first do the json bits, currently this is just provenance, may well be others
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
                    /* ok we do this before setting to save so we don't get a state for save, state changed, then set as persisted (hence not allowing for latest changes)
                        the multi threaded handling of sets to persist is via concurrent hash map, hence ordering which is important here should be right I hope. The only concern is for the same
                        element being removed being added back into the map and I think the internal locking (via striping?) should deal with this*/
                    entity.setAsPersisted();
                    // used to be done in the above line by proxying back through AzquoMemoryDb. I see little point in this, the "to persist" sets are held in here.
                    entities.remove(entity);
                    recordsToStore.add(new JsonRecordTransport(entity.getId(), entity.getAsJson(), state));
                }
                // and end here
                try {
                    JsonRecordDAO.persistJsonRecords(persistenceName, tableName, recordsToStore);// note this is multi threaded internally
                } catch (Exception e) {
                    // currently I'll just stack trace this, not sure of what would be the best strategy
                    e.printStackTrace();
                }
            }
        }
        // todo re-examine given new patterns. can genericize? As in pass the DAOs around? Maybe not as they're typed . . .
        System.out.println("name store : " + namesToPersist.size());
        // I think here the copy happens to be defensive, there should be no missed changes or rather entities flagged as persisted that need to be persisted (change right after save but before set as persisted)
        List<Name> namesToStore = new ArrayList<>(namesToPersist); // make a copy before persisting anf removing
        // Notably removing elements from the set while iterating didn't cause a problem. Anyway copying now.
        for (Name name : namesToStore) {
            // note! Doing this changes the inserting flag meaning there may be a bunch of unnecessary deletes. Doesn't break anything but not necessary, need to sort that TODO
            name.setAsPersisted();
            // The actual saving of the state happens later. If modified in the mean time a name will be added back onto the set
            namesToPersist.remove(name);
        }
        try {
            NameDAO.persistNames(persistenceName, namesToStore);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("value store : " + valuesToPersist.size());
        List<Value> valuesToStore = new ArrayList<>(valuesToPersist);
        for (Value value : valuesToPersist) {
            value.setAsPersisted();
            valuesToPersist.remove(value);
        }
        try {
            ValueDAO.persistValues(persistenceName, valuesToStore);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("persist done.");
    }
}