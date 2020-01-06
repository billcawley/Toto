package com.azquo.memorydb.core;

import com.azquo.ThreadPools;
import com.azquo.memorydb.dao.JsonRecordDAO;
import com.azquo.memorydb.dao.JsonRecordTransport;
import com.azquo.memorydb.dao.NameDAO;
import com.azquo.memorydb.dao.ValueDAO;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import org.apache.commons.lang.math.NumberUtils;

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
 * <p>
 * I'm a little bothered about memory visibility of the
 * populated memory db but breaking the loading off into this class does't affect the logic as it was before or which thread it was running in.
 * Persist sets are using Java concurrency classes, that will be fine, it's population of names for example that might be a concern.
 * <p>
 * The issue is object publication - I think all is well or certainly no worse than before factoring this off.
 * <p>
 * As mentioned in AzquoMemoryDB since all Names have to reference the instance of AzquoMemoryDB then to stop this escaping from the constructor one would
 * either need to assign the AzquoMemory dbs later or somehow not put a reference in the entities which I don't think is practical.
 */
class AzquoMemoryDBTransport {

    // I figure these should be final, you initialise the transport against a given persistence store and memory db and that's that
    private final String persistenceName;
    final AzquoMemoryDB azquoMemoryDB;

    // Used to be a map of sets for all entities but since speeding up storing of names and values with custom DAOs I figure it's best to simply have three sets
    private final Map<String, Set<AzquoMemoryDBEntity>> jsonEntitiesToPersist;
    private final Set<Name> namesToPersist;
    private final Set<Value> valuesToPersist;
    private List<Value> valuesToStore;

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

    // todo - inconsistent use of memdb azquoMemoryDB

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
            List<Name> names = NameDAO.findForMinMaxId(memDB, minId, maxId); // internally this will be populating the names into the database
            for (Name name : names) {// bit of an overhead just to get the max id?I guess no concern -
                // zapping the lists would save garbage but noth bothered for the mo and I'd need to change findForMinMaxId
                // could save this if persistence saved a max id. A thought.
                azquoMemoryDB.setNextId(name.getId());
            }
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
            loadTracker.addAndGet(values.size());
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
                name.link(azquoMemoryDB.nameChildrenLoadingCache.get(name.getId()), false);
            }
            logInSessionLogAndSystem("Linked : " + loadTracker.addAndGet(batchToLink.size()));
            return null;
        }
    }

    void logInSessionLogAndSystem(String s) {
        if (sessionLog != null) {
            sessionLog.append(s).append("\n");
        }
        System.out.println(s);
    }

    // should only be called in the memory db constructor, is there a way to enforce this?
    // It should be noted that a fair amount of effort has been put into optimising this, consider carefully before making changes
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
                hence all names need initialisation (linking) finished after the id map is sorted
                This is why when multi threading we wait util a type of entity is fully loaded before moving onto the next
                Atomic integers to pass through to the multi threaded code for logging, tracking numbers loaded
                */
            AtomicInteger provenanceLoaded = new AtomicInteger();
            AtomicInteger namesLoaded = new AtomicInteger();
            AtomicInteger valuesLoaded = new AtomicInteger();
            AtomicInteger jsonRecordsLoaded = new AtomicInteger();
            final int step = 100_000; // not so much step now as id range given how we're now querying mysql. Cutting down to 100,000 to reduce the chance of SQL errors (buffer verflow on big query)
            marker = System.currentTimeMillis();
            // create thread pool, rack up the loading tasks and wait for it to finish. Repeat for name and values.
            // going to set up for multiple json persisted entities even if it's only provenance for the mo
            Map<String, JsonSerializableEntityInitializer> jsonTablesAndInitializers = new HashMap<>();
            // add lines like this for loading other json entities. A note is table names repeated, might have a think about that
            jsonTablesAndInitializers.put(Provenance.PERSIST_TABLE, (memoryDB, jsonRecordTransport) -> new Provenance(memoryDB, jsonRecordTransport.id, jsonRecordTransport.json, false));
            int from;
            int maxIdForTable;
            List<Future<?>> futureBatches;
            // currently just provenance
            for (Map.Entry<String, JsonSerializableEntityInitializer> tableNameEntities : jsonTablesAndInitializers.entrySet()) {
                jsonRecordsLoaded.set(0);
                from = 0;
                maxIdForTable = JsonRecordDAO.findMaxId(persistenceName, tableNameEntities.getKey());
                futureBatches = new ArrayList<>();
                while (from < maxIdForTable) {
                    futureBatches.add(ThreadPools.getSqlThreadPool().submit(new SQLBatchLoader(tableNameEntities.getKey(), tableNameEntities.getValue(), from, from + step, tableNameEntities.getKey().equals(Provenance.PERSIST_TABLE) ? provenanceLoaded : jsonRecordsLoaded)));
                    from += step;
                }
                for (Future future : futureBatches) {
                    future.get(1, TimeUnit.HOURS);
                }
                logInSessionLogAndSystem(tableNameEntities.getKey() + ", " + jsonRecordsLoaded + " records loaded in " + (System.currentTimeMillis() - marker) / 1000f + " second(s)");
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
            linkEntities(false);
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
            System.out.println("--- MEMORY USED :  " + nf.format((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb));
        }
        logInSessionLogAndSystem("Total load time for " + persistenceName + " " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
        //azquoMemoryDB.getIndex().printIndexStats();
        //AzquoMemoryDB.printAllCountStats();
        // stuff Ed added to profile data
        NumberFormat nf = NumberFormat.getInstance();
/*
        int namesWithOnlyDefaultDisplayName = 0;
        int namesRequiringAttributes = 0;
        int namesWithOnlyOneParent = 0;
        int namesWithMoreThanOneParent = 0;
        int namesWithNoValues = 0;
        int namesWithArrayValues = 0;
        int namesWithSetValues = 0;
        int namesWithNoChildremn = 0;
        int namesWithArrayChildren = 0;
        int namesWithSetChildren = 0;
        int totalNameCount = azquoMemoryDB.getAllNames().size();
        Map<List<Name>, Name> existingParents = HashObjObjMaps.newMutableMap();;
        for (Name n : azquoMemoryDB.getAllNames()){
            if (n.getDefaultDisplayName() != null && n.getAttributeKeys().size() == 1) {
                namesWithOnlyDefaultDisplayName++;
            } else { // I don't think it can be 0
                namesRequiringAttributes++;
            }
            if (n.getParents().size() == 1){
                namesWithOnlyOneParent++;
            } else if (n.getParents().size() > 1){
                namesWithMoreThanOneParent++;
            }
            if (n.getValues().isEmpty()){
                namesWithNoValues++;
            } else if (n.getValueCount() < Name.ARRAYTHRESHOLD){
                namesWithArrayValues++;
            } else {
                namesWithSetValues++;
            }
            if (n.getChildren().isEmpty()){
                namesWithNoChildremn++;
            } else if (n.getChildren().size() < Name.ARRAYTHRESHOLD){
                namesWithArrayChildren++;
            } else {
                namesWithSetChildren++;
            }
            if (!n.getParents().isEmpty()){
                List<Name> parents = new ArrayList<>(n.getParents());
                parents.sort(Comparator.comparing(Name::getId));
                if (existingParents.containsKey(parents)){
                    n.normaliseParents(existingParents.get(parents));
                } else {
                    existingParents.put(parents, n);
                }
            }
        }
        existingParents = null;*/
    /*    if (totalNameCount > 0){
            List<Map.Entry<List<Name>, AtomicInteger>> toSort = new ArrayList<>(parentsTrack.entrySet());
            toSort.sort(Comparator.comparing(listAtomicIntegerEntry -> listAtomicIntegerEntry.getValue().get()));
            int count = 0;
            //Collections.reverse(toSort);
            for (Map.Entry<List<Name>, AtomicInteger> entry : toSort){
                int noNames = entry.getValue().get();
                String namesDesc = new String();
                for (Name parent : entry.getKey()){
                    namesDesc += ", " + parent.getDefaultDisplayName();// I suppose may get some nulls, not so botered right now
                }
                System.out.println("Names with the following parents : " + namesDesc + " : " + nf.format(noNames) + ", " + nf.format((100*noNames)/totalNameCount) + "%");
                if (count > 100){
                    break;
                }
                count++;
            }
            System.out.println("namesWithOnlyDefaultDisplayName : " + nf.format(namesWithOnlyDefaultDisplayName) + ", " + nf.format((100*namesWithOnlyDefaultDisplayName)/totalNameCount) + "%");
            System.out.println("namesRequiringAttributes : " + nf.format(namesRequiringAttributes) + ", " + nf.format((100*namesRequiringAttributes)/totalNameCount) + "%");
            System.out.println("namesWithOnlyOneParent : " + nf.format(namesWithOnlyOneParent) + ", " + nf.format((100*namesWithOnlyOneParent)/totalNameCount) + "%");
            System.out.println("namesWithMoreThanOneParent : " + nf.format(namesWithMoreThanOneParent) + ", " + nf.format((100*namesWithMoreThanOneParent)/totalNameCount) + "%");
            System.out.println("namesWithNoValues : " + nf.format(namesWithNoValues) + ", " + nf.format((100*namesWithNoValues)/totalNameCount) + "%");
            System.out.println("namesWithArrayValues : " + nf.format(namesWithArrayValues) + ", " + nf.format((100*namesWithArrayValues)/totalNameCount) + "%");
            System.out.println("namesWithSetValues : " + nf.format(namesWithSetValues) + ", " + nf.format((100*namesWithSetValues)/totalNameCount) + "%");
            System.out.println("namesWithNoChildremn : " + nf.format(namesWithNoChildremn) + ", " + nf.format((100*namesWithNoChildremn)/totalNameCount) + "%");
            System.out.println("namesWithArrayChildren : " + nf.format(namesWithArrayChildren) + ", " + nf.format((100*namesWithArrayChildren)/totalNameCount) + "%");
            System.out.println("namesWithSetChildren : " + nf.format(namesWithSetChildren) + ", " + nf.format((100*namesWithSetChildren)/totalNameCount) + "%");
        }*/
        /*
        Risk solutions example
        namesWithOnlyDefaultDisplayName : 143,581, 46%
namesRequiringAttributes : 166,892, 53%
namesWithOnlyOneParent : 77,081, 24%
namesWithMoreThanOneParent : 233,360, 75%
namesWithNoValues : 130,342, 41%
namesWithArrayValues : 180,074, 57%
namesWithSetValues : 57, 0%
namesWithNoChildremn : 124,457, 40%
namesWithArrayChildren : 185,818, 59%
namesWithSetChildren : 198, 0%
easylife example
namesWithOnlyDefaultDisplayName : 1,940,309, 54%
namesRequiringAttributes : 1,616,234, 45%
namesWithOnlyOneParent : 671,409, 18%
namesWithMoreThanOneParent : 2,885,100, 81%
namesWithNoValues : 709,597, 19%
namesWithArrayValues : 2,846,913, 80%
namesWithSetValues : 33, 0%
namesWithNoChildremn : 1,939,665, 54%
namesWithArrayChildren : 1,615,642, 45%
namesWithSetChildren : 1,236, 0%

        // now for some Value analysis . . .
        int valuesWhichAreNumbers = 0;
        int valuesWhichAreNotNumbers = 0;
        int sopCount = 0;
        Set<String> printed = new HashSet<>();
        for (Value v : azquoMemoryDB.getAllValues()){
            if (NumberUtils.isNumber(v.getText())){
                valuesWhichAreNumbers++;
            } else {
                valuesWhichAreNotNumbers++;
                if (sopCount < 100 && printed.add(v.getText())){
                    System.out.println("non numeric value : " + v.getText());
                    sopCount++;
                }
            }
        }
        System.out.println("valuesWhichAreNumbers : " + nf.format(valuesWhichAreNumbers) + ", " + nf.format((100*valuesWhichAreNumbers)/azquoMemoryDB.getAllValues().size()) + "%");
        System.out.println("valuesWhichAreNotNumbers : " + nf.format(valuesWhichAreNotNumbers) + ", " + nf.format((100*valuesWhichAreNotNumbers)/azquoMemoryDB.getAllValues().size()) + "%");
        /*
        Risk solutions
        non numeric value : B0702BB302040LAB
        valuesWhichAreNumbers : 1,535,568, 95%
valuesWhichAreNotNumbers : 68,278, 4%
easylife
non numeric value : xxx
non numeric value : RN0472881
valuesWhichAreNumbers : 4,887,985, 98%
valuesWhichAreNotNumbers : 72,974, 1%
         */

        if (memoryTrack) {
            marker = System.currentTimeMillis();
            // using system.gc before and after loading to get an idea of DB memory overhead
            System.gc();
            System.out.println("gc time : " + (System.currentTimeMillis() - marker));
            long newUsed = (runtime.totalMemory() - runtime.freeMemory()) / mb;
            System.out.println("Guess at DB size after attempting parents normalisation " + nf.format(newUsed - usedMB) + "MB");
            System.out.println("--- MEMORY USED :  " + nf.format((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb));
        }
    }


    /* to be called after loading moves the json and extracts attributes to useful maps here called after loading as the names reference themselves
    going to try a basic multi-thread - it was 100,000 but I wonder if this is as efficient as it could be given that at the end leftover threads
    can hang around (particularly hefty child sets). Trying for 50,000 */

    void linkEntities(boolean skipCheck) throws Exception {
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
        azquoMemoryDB.nameChildrenLoadingCache.clear(); // free the memory. If it was really tight we could clear as we went along I suppose.
        if (!skipCheck){
            int counter = 0;
            long marker = System.currentTimeMillis();
            for (Name name : azquoMemoryDB.getAllNames()) {
                if (name.hasParents() && name.getParents().get(name.getParents().size() - 1) == null) {
                    int number = 0;
                    for (Name parent : name.getParents()) {
                        if (parent == null) {
                            break;
                        }
                        number++;
                    }
//                System.out.print("update fast_name set no_parents = " + number + " where id = " + name.getId() + ";     ");
                    System.out.println("parent problem on : " + name.getDefaultDisplayName() + " space = " + name.getParents().size() + ", number : " + number);
                    counter++;
                    name.parentArrayCheck();
                    azquoMemoryDB.forceNameNeedsPersisting(name);
                    if (counter > 10000) {
                        System.out.println("10k breaking;");
                        break;
                    }
                }
            }
            for (Name name : azquoMemoryDB.getAllNames()) {
                if (name.hasValues()) {
                    int number = 0;
                    for (Value v : name.getValues()) {
                        if (v != null) {
                            number++;
                        }
                    }
                    if (number != name.getValues().size()) {
//                    System.out.print("update fast_name set no_values = " + number + " where id = " + name.getId() + ";      ");
                        System.out.println("value problem on : " + name.getDefaultDisplayName() + " space = " + name.getValues().size() + ", number : " + number);
                        name.valueArrayCheck();
                        azquoMemoryDB.forceNameNeedsPersisting(name);
                        counter++;
                        if (counter > 10000) {
                            System.out.println("10k breaking;");
                            break;
                        }
                    }
                }
            }
            System.out.println("Name integrity check took " + (System.currentTimeMillis() - marker) + "ms");
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

    List<Value> getValuesChanged() {
        return new ArrayList<>(valuesToStore);
    }

    boolean hasNamesToPersist() {
        return !namesToPersist.isEmpty();
    }

    /* now, this is only called by the AzquoMemoryDB and is synchronized against that object so two can't run conncurrently
    BUT at the moment I'm allowing the database to be modified while persisting, this function should be robust to that
     */
    void persistDatabase() {
        if (azquoMemoryDB.needToCheckValueLengths()){
            System.out.println("***********************");
            System.out.println("***********************");
            System.out.println("***********************");
            System.out.println("***********************");
            System.out.println("Converting value and value history tables to 8k long varchar text");
            System.out.println("***********************");
            System.out.println("***********************");
            System.out.println("***********************");
            System.out.println("***********************");
            ValueDAO.convertForLongTextValues(azquoMemoryDB.getPersistenceName());
        }
        System.out.println("PERSIST STARTING");
        // ok first do the json bits, currently this is just provenance, may well be others
        for (Map.Entry<String, Set<AzquoMemoryDBEntity>> tableNameEntites : jsonEntitiesToPersist.entrySet()) {
            Set<AzquoMemoryDBEntity> entities = tableNameEntites.getValue();
            if (!entities.isEmpty()) {
                // multi thread this chunk? It can slow things down a little . . .
                System.out.println("Json entities to put in " + tableNameEntites.getKey() + " : " + entities.size());
                List<JsonRecordTransport> recordsToStore = new ArrayList<>(entities.size()); // it's now bothering me a fair bit that I didn't used to initialise such lists!
                // new logic, use an iterator, I don't see why not? ConcurrentHashMap should handle changes behind the scenes effectively.
                // if more are added in the background this is fine, persist needs to be started again which it should be anyway by whatever logic was modifying the object
                Iterator<AzquoMemoryDBEntity> it = entities.iterator();
                while (it.hasNext()) {
                    AzquoMemoryDBEntity entity = it.next();
                    /* take off the source immediately - since the access to a set backed by ConcurrentHashMap remove/add for this entity is atomic
                     * So in theory, yes there could be an entity stored in an inconsistent state if it's changing while being stored (during getAsJson for example) but it will be put back
                     * into the entities set and stored again. */
                    it.remove();
                    JsonRecordTransport.State state = JsonRecordTransport.State.UPDATE;
                    // this could be modified outside, a concern? If an entity is deleted there is no way to undelete - it would just be zapped twice.
                    if (entity.getNeedsDeleting()) {
                        state = JsonRecordTransport.State.DELETE;
                    }
                    // two of persistDatabase will not be running at the same time so we can't
                    // somehow get needsInserting Set to false when it shouldn't be which could cause an false store (update a record that doens't exist)
                    // that is to say : the only thing that modifies needsInserting is entity.setNeedsInsertingFalse() as below.
                    if (entity.getNeedsInserting()) {
                        state = JsonRecordTransport.State.INSERT;
                    }
                    entity.setNeedsInsertingFalse();
                    recordsToStore.add(new JsonRecordTransport(entity.getId(), entity.getAsJson(), state)); // getAsJson means the state at that time. If it changes afer it will have been re added and stored again
                }
                // and end here
                try {
                    JsonRecordDAO.persistJsonRecords(persistenceName, tableNameEntites.getKey(), recordsToStore);// note this is multi threaded internally
                } catch (Exception e) {
                    // currently I'll just stack trace this, not sure of what would be the best strategy
                    e.printStackTrace();
                }
            }
        }
        // todo re-examine given new patterns. can genericize? As in pass the DAOs around? Maybe not as they're typed . . .
        System.out.println("name store : " + namesToPersist.size());
        // make a copy before persisting and removing - defensive in that if an entity is changed during persistence it will be added to the list to be persisted again
        // Also this list will be passed through a few times and after removal so it makes sense (not making a copy consisting of JsonRecordTransports like above)
        List<Name> namesToStore = new ArrayList<>(namesToPersist);
        for (Name name : namesToStore) { // faster than removeall, way faster
            namesToPersist.remove(name);
        }
        //namesToPersist.removeAll(namesToStore); NO! it calls contains on the arraylist loads . . .
        // now persist. Notable that internally it doens't actually update it just deleted before inserting if needsInserting is false
        // but now the surrounding logic is more consistent - if we switch to proper update it should be fine
        // Note : as with the JSON is IS possible that a name could be stored in an inconsistent state
        // (the code for getting children ids takes this into account already) but stopping that is not trivial, it will be back on the queue to be stored again.
        try {
            NameDAO.persistNames(persistenceName, namesToStore);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // as noted above this function is the only that calls setNeedsInsertingFalse so it's value in this function (the only place it's read) will be consistent
        for (Name name : namesToStore) {
            name.setNeedsInsertingFalse();
        }
        // same pattern as with name
        System.out.println("value store : " + valuesToPersist.size());
        // EFC note : for reporting purposes WFC has made valuesToStore a field in class. Dangerous? Probably not as it's reassigned here right before use.
        // I'll mull this a bit. Perhaps it should be unmodifiable? Can two persists run simultaneously? They shouldn't anyway regardless of this field.
        valuesToStore = new ArrayList<>(valuesToPersist);
        for (Value value : valuesToStore) {
            valuesToPersist.remove(value);
        }
        try {
            ValueDAO.persistValues(persistenceName, valuesToStore);
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Value value : valuesToStore) {
            value.setNeedsInsertingFalse();
        }
        // Edd adding new defensive logic - if something changed in teh eman time fire another persist
        boolean jsonToStore = false;
        for (Set<AzquoMemoryDBEntity> entities : jsonEntitiesToPersist.values()) {
            if (!entities.isEmpty()) {
                jsonToStore = true;
                break;
            }
        }
        if (jsonToStore || !namesToPersist.isEmpty() || !valuesToPersist.isEmpty()) {
            System.out.println("=========Further entities to persist, persisting again");
            persistDatabase();
        } else {
            System.out.println("persist done.");
        }
    }
}