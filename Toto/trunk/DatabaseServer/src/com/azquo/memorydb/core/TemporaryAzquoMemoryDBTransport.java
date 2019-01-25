package com.azquo.memorydb.core;

/*

I need a version of the transport to populate a temporary copy of a database, initially extending the original
transport class and

 */

import com.azquo.ThreadPools;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class TemporaryAzquoMemoryDBTransport extends AzquoMemoryDBTransport {

    final private AzquoMemoryDB source;

    TemporaryAzquoMemoryDBTransport(AzquoMemoryDB azquoMemoryDB, AzquoMemoryDB source) {
        super(azquoMemoryDB, null, null);
        this.source = source;
    }

    private class NameBatchLoader implements Callable<Void> {
        private final List<Name> sourceNames;
        private final AtomicInteger loadTracker;

        NameBatchLoader(List<Name> sourceNames, AtomicInteger loadTracker) {
            this.sourceNames = sourceNames;
            this.loadTracker = loadTracker;
        }

        @Override
        public Void call() throws Exception {
            for (Name name : sourceNames) {
                azquoMemoryDB.nameChildrenLoadingCache.put(name.getId(), name.getChildrenIdsAsBytes());
                new Name(azquoMemoryDB, name.getId(), name.getProvenance().getId(), name.getRawAttributes(),null, name.getParents().size(), name.getValues().size(), false);
                azquoMemoryDB.setNextId(name.getId());
            }
            loadTracker.addAndGet(sourceNames.size());
            return null;
        }
    }

    private class ValueBatchLoader implements Callable<Void> {
        private final List<Value> sourceValues;
        private final AtomicInteger loadTracker;

        ValueBatchLoader(List<Value> sourceValues, AtomicInteger loadTracker) {
            this.sourceValues = sourceValues;
            this.loadTracker = loadTracker;
        }

        @Override
        public Void call() throws Exception {
            for (Value value : sourceValues) {
                new Value(azquoMemoryDB, value.getId(), value.getProvenance().getId(), value.getText(), value.getNameIdsAsBytes());
                azquoMemoryDB.setNextId(value.getId());
            }
            loadTracker.addAndGet(sourceValues.size());
            return null;
        }
    }


    // should only be called in the memory db constructor, is there a way to enforce this?
    // It should be noted that a fair amount of effort has been put into optimising this, consider carefully before making changes
    void loadData(boolean memoryTrack) {
        long startTime = System.currentTimeMillis();
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
            final int step = 100_000;
            marker = System.currentTimeMillis();
            // I'm not going to bother multi threading provenance loading here
            for (Provenance sourceProvenance : source.getAllProvenances()){
                new Provenance(azquoMemoryDB, sourceProvenance.getId(), sourceProvenance.getAsJson(), false);
                provenanceLoaded.incrementAndGet();
            }
            // create thread pool, rack up the loading tasks and wait for it to finish. Repeat for name and values.
            List<Future<?>> futureBatches;
            marker = System.currentTimeMillis();
            futureBatches = new ArrayList<>();
            ArrayList<Name> nameBatch = new ArrayList<>(step);
            for (Name sourceName : source.getAllNames()){
                nameBatch.add(sourceName);
                if (nameBatch.size() == step){
                    futureBatches.add(ThreadPools.getSqlThreadPool().submit(new NameBatchLoader(nameBatch, namesLoaded)));
                    nameBatch = new ArrayList<>(step);
                }
            }
            futureBatches.add(ThreadPools.getSqlThreadPool().submit(new NameBatchLoader(nameBatch, namesLoaded)));
            for (Future future : futureBatches) {
                future.get(1, TimeUnit.HOURS);
            }
            marker = System.currentTimeMillis();
            futureBatches = new ArrayList<>();
            ArrayList<Value> valueBatch = new ArrayList<>(step);
            for (Value sourceValue : source.getAllValues()){
                valueBatch.add(sourceValue);
                if (valueBatch.size() == step){
                    futureBatches.add(ThreadPools.getSqlThreadPool().submit(new ValueBatchLoader(valueBatch, valuesLoaded)));
                    valueBatch = new ArrayList<>(step);
                }
            }
            futureBatches.add(ThreadPools.getSqlThreadPool().submit(new ValueBatchLoader(valueBatch, valuesLoaded)));
            for (Future future : futureBatches) {
                future.get(1, TimeUnit.HOURS);
            }
            marker = System.currentTimeMillis();
            // wait until all are loaded before linking
            System.out.println(provenanceLoaded.get() + valuesLoaded.get() + namesLoaded.get() + " unlinked entities loaded in " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
            if (memoryTrack) {
                System.out.println("Used Memory after list load:"
                        + (runtime.totalMemory() - runtime.freeMemory()) / mb);
            }
            linkEntities(true);
            if (memoryTrack) {
                System.out.println("Used Memory after init names :"
                        + (runtime.totalMemory() - runtime.freeMemory()) / mb);
            }
        } catch (Exception e) {
            logInSessionLogAndSystem("could not load data for COPY OF " + source.getPersistenceName() + "!");
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
        logInSessionLogAndSystem("Total load time for for COPY OF " + source.getPersistenceName() + " " + (System.currentTimeMillis() - startTime) / 1000 + " second(s)");
        //azquoMemoryDB.getIndex().printIndexStats();
        //AzquoMemoryDB.printAllCountStats();
    }

    // these are all stubbed

    void setJsonEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity entity) {
    }

    void setNameNeedsPersisting(Name name) {
    }

    void setValueNeedsPersisting(Value value) {
    }

    List<Value> getValuesChanged() {
        return Collections.emptyList();
    }

    boolean hasNamesToPersist() {
        return false;
    }

    void persistDatabase() {
        System.out.println("Not persisting a temporary copy of a database");
    }
}