package com.azquo.memorydb.core;

import com.azquo.admin.database.Database;
import com.azquo.memorydb.dao.JsonRecordTransport;
import com.azquo.memorydb.dao.StandardDAO;
import com.github.holodnov.calculator.ObjectSizeCalculator;
import org.apache.log4j.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * created after it became apparent that Mysql in the way I'd arranged the objects didn't have a hope in hell of
 * delivering data fast enough. Leverage collections to implement Azquo spec.
 * <p/>
 * TODO - this whole intern business, not sure if it matters so much on the get, it's the set I care about
 */
public final class AzquoMemoryDB {


    private static final Logger logger = Logger.getLogger(AzquoMemoryDB.class);

    //I don't think I can auto wire this as I can't guarantee it will be ready for the constructor

    private final StandardDAO standardDAO;

    private final Map<String, Map<String, List<Name>>> nameByAttributeMap; // a map of maps of sets of names. Fun!

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    // does this database need loading from mysql, a significant flag that affects rules for memory db instantiation for example
    private boolean needsLoading;

    // reference to the mysql db
    private Database database;//removed 'final' to allow temporary database to be loaded

    // object ids. We handle this here, it's not done by MySQL
    private int maxIdAtLoad;
    private int nextId;

    // when objects are modified they are added to these sets held in a map. AzquoMemoryDBEntity has all functions require to persist.

    private final Map<String, Set<AzquoMemoryDBEntity>> entitiesToPersist;

    // Initialising maps concurrently here,

    protected AzquoMemoryDB(Database database, StandardDAO standardDAO) throws Exception {
        this.database = database;
        this.standardDAO = standardDAO;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByAttributeMap = new ConcurrentHashMap<String, Map<String, List<Name>>>();
/*        nameByIdMap = HashIntObjMaps.newMutableMap();
        valueByIdMap = HashIntObjMaps.newMutableMap();
        provenanceByIdMap = HashIntObjMaps.newMutableMap();*/
        nameByIdMap = new ConcurrentHashMap<Integer, Name>();
        valueByIdMap = new ConcurrentHashMap<Integer, Value>();
        provenanceByIdMap = new ConcurrentHashMap<Integer, Provenance>();
        entitiesToPersist = new ConcurrentHashMap<String, Set<AzquoMemoryDBEntity>>();
        // loop over the possible persisted tables making the empty sets, cunning
        if (standardDAO != null) {
            for (StandardDAO.PersistedTable persistedTable : StandardDAO.PersistedTable.values()) {
                entitiesToPersist.put(persistedTable.name(), Collections.newSetFromMap(new HashMap<AzquoMemoryDBEntity, Boolean>()));
            }
        }
/*        if (appServices != null) {
            for (AppEntityService appEntityService : appServices) {
                // seems a good a place as any to create the MySQL table if it doesn't exist
                appEntityService.checkCreateMySQLTable(this);
                entitiesToPersist.put(appEntityService.getTableName(), Collections.newSetFromMap(new HashMap<AzquoMemoryDBEntity, Boolean>()));
            }
        }*/
        if (standardDAO != null) {
            loadData();
        }
        needsLoading = false;
        nextId = maxIdAtLoad + 1;
    }

    // convenience
    public String getMySQLName() {
        return database.getMySQLName();
    }

    public Database getDatabase() {
        return database;
    }

    public void zapDatabase() {
        database = null;
    }

    public boolean getNeedsLoading() {
        return needsLoading;
    }

    // now passing app services
    final int mb = 1024 * 1024;
    // Todo : possible optimiseation from this : http://www.4pmp.com/2010/02/scalable-mysql-avoid-offset-for-large-tables/

    private static final int PROVENANCE_MODE = 0;
    private static final int NAME_MODE = 1;
    private static final int VALUE_MODE = 2;

    private class SQLLoadRunner implements Runnable {
        private final int mode;
        private final List<JsonRecordTransport> dataToLoad;
        private final AzquoMemoryDB memDB;

        private SQLLoadRunner(int mode, List<JsonRecordTransport> dataToLoad, AzquoMemoryDB memDB) {
            this.mode = mode;
            this.dataToLoad = dataToLoad;
            this.memDB = memDB;
        }

        @Override
        public void run() {
            try {
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

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    synchronized private void loadData() {
        int loadingThreads = 3; // this may need adjusting depending on server power
        boolean memoryTrack = false;
        if (needsLoading) { // only allow it once!
            long track = System.currentTimeMillis();
            System.out.println("loading data for " + getMySQLName());
            // using system.gc before and after loading to get an idea of DB memory overhead
            long marker = System.currentTimeMillis();
            Runtime runtime = Runtime.getRuntime();
            long usedMB = 0;
            if (memoryTrack) {
                System.gc();
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
                one could use AzquomemoryDBEntity if having some kind of init from Json function but this makes the objects more mutable than I'd like
                so we stay with this for the moment
                these 3 commands will automatically load the data into the memory DB set as persisted
                Load order is important as value and name use provenance and value uses names. Names uses itself hence all names need initialisation finished after the id map is sorted

                //I'm going to load in chunks, should scale more.
                */

                int provenaceLoaded = 0;
                int namesLoaded = 0;
                int valuesLoaded = 0;

                final int step = 500000;
                int from = 0;

                ExecutorService executor = Executors.newFixedThreadPool(loadingThreads); // picking 10 based on an example I saw . . .
                List<JsonRecordTransport> provenance = standardDAO.findFromTable(this, StandardDAO.PersistedTable.provenance.name(), from, step);
                while (!provenance.isEmpty()) {
                    executor.execute(new SQLLoadRunner(PROVENANCE_MODE, provenance, this));
                    provenaceLoaded += provenance.size();
                    from += step;
                    provenance = standardDAO.findFromTable(this, StandardDAO.PersistedTable.provenance.name(), from, step);

                }
                from = 0;
                List<JsonRecordTransport> names = standardDAO.findFromTable(this, StandardDAO.PersistedTable.name.name(), from, step);
                while (!names.isEmpty()) {
                    executor.execute(new SQLLoadRunner(NAME_MODE, names, this));
                    namesLoaded += names.size();
                    from += step;
                    names = standardDAO.findFromTable(this, StandardDAO.PersistedTable.name.name(), from, step);
                }
                from = 0;
                List<JsonRecordTransport> values = standardDAO.findFromTable(this, StandardDAO.PersistedTable.value.name(), from, step);
                while (!values.isEmpty()) {
                    executor.execute(new SQLLoadRunner(VALUE_MODE, values, this));
                    valuesLoaded += values.size();
                    from += step;
                    values = standardDAO.findFromTable(this, StandardDAO.PersistedTable.value.name(), from, step);
                }
                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                    throw new Exception("Database " + getMySQLName() + " took longer than an hour to load");
                }
                // wait untill all are loaded before linking
                System.out.println(provenaceLoaded + valuesLoaded + namesLoaded + " unlinked entities loaded,  " + (System.currentTimeMillis() - track) + "ms");
                if (memoryTrack) {
                    System.out.println("Used Memory after list load:"
                            + (runtime.totalMemory() - runtime.freeMemory()) / mb);
                }
                initNames();
                if (memoryTrack) {
                    System.out.println("Used Memory after init names :"
                            + (runtime.totalMemory() - runtime.freeMemory()) / mb);
                }


                System.out.println("names init, " + (System.currentTimeMillis() - track) + "ms");
                // forget appentities for the moment,we may never use them anyway
/*
                if (appServices != null) { // dunno why it wasn't there before
                    for (AppEntityService appEntityService : appServices) {
                        final List<JsonRecordTransport> appEntities = standardDAO.findFromTable(this, appEntityService.getTableName());
                        for (JsonRecordTransport appEntityRecord : appEntities) {
                            if (appEntityRecord.id > maxIdAtLoad) {
                                maxIdAtLoad = appEntityRecord.id;
                            }
                            appEntityService.loadEntityFromJson(this, appEntityRecord.id, appEntityRecord.json);
                        }
                    }
                }*/

                System.out.println("loaded data for " + getMySQLName());

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
                System.out.println("Used Memory:"
                        + newUsed);
                System.out.println("Free Memory:"
                        + runtime.freeMemory() / mb);
                System.out.println("Total Memory:" + runtime.totalMemory() / mb);
                System.out.println("Max Memory:" + runtime.maxMemory() / mb);
            }
            System.out.println("Total load time : " + (System.currentTimeMillis() - track) + "ms");
        }
    }

    // reads from a list of changed objects
    // should we syncronize on a write lock object?? I think it might be a plan.

    public synchronized void saveDataToMySQL() {
        // this is where I need to think carefully about concurrency, azquodb has the last say when the sets are modified although the flags are another point
        // just a simple DB write lock should to it
        // for the moment just make it work.
        // map of sets to persist means that should we add another object type then this code should not need to change
        for (String tableToStoreIn : entitiesToPersist.keySet()) { // run through 'em was an enum of set table names, not it could be anything depending on app tables. See no harm in theh generic nature
            Set<AzquoMemoryDBEntity> entities = entitiesToPersist.get(tableToStoreIn);
            if (!entitiesToPersist.isEmpty()) {
                System.out.println("entities to put in " + tableToStoreIn + " : " + entities.size());
                List<JsonRecordTransport> recordsToStore = new ArrayList<JsonRecordTransport>();
                // todo : write nlocking the db probably should start here
                for (AzquoMemoryDBEntity entity : new ArrayList<AzquoMemoryDBEntity>(entities)) { // we're taking a copy of the set before running through it.
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
                standardDAO.persistJsonRecords(this, tableToStoreIn, recordsToStore);
            }
        }
    }

    // will block currently!

    protected synchronized int getNextId() {
        nextId++; // increment but return what it was . . .a little messy but I want that value in memory to be what it says
        return nextId - 1;
    }

    // for debug purposes, is there a harm in being public??

    public int getCurrentMaximumId() {
        return nextId;
    }

    public Name getNameById(final int id) {
        return nameByIdMap.get(id);
    }

    //fundamental low level function to get a set of names from the attribute indexes. Forces case insensitivity.
    // TODO - collection this up later? What hsould be returned?

    private Set<Name> getNamesForAttribute(final String attributeName, final String attributeValue) {
        Map<String, List<Name>> map = nameByAttributeMap.get(attributeName.toUpperCase().trim());
        if (map != null) { // that attribute is there
            List<Name> names = map.get(attributeValue.toLowerCase().trim());
            if (names != null) { // were there any entries for that value?
                return new HashSet<Name>(names);
            }
        }
        return Collections.emptySet(); // moving away from nulls
    }

    // same as above but then zap any not in the parent

    private Set<Name> getNamesForAttributeAndParent(final String attributeName, final String attributeValue, Name parent) {
        Set<Name> possibles = getNamesForAttribute(attributeName, attributeValue);
        Iterator<Name> iterator = possibles.iterator();
        while (iterator.hasNext()) {
            Name possible = iterator.next();
            if (!possible.hasInParentTree(parent)) {
                iterator.remove();
            }
        }
        return possibles;
    }

    // work through a list of possible names for a given attribute in order that the attribute names are listed. Parent optional

    private Set<Name> getNamesForAttributeNamesAndParent(final List<String> attributeNames, final String attributeValue, Name parent) {
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

    public Name getNameByAttribute(final String attributeName, final String attributeValue, final Name parent) {
        return getNameByAttribute(Collections.singletonList(attributeName), attributeValue, parent);
    }

    public Name getNameByAttribute(final List<String> attributeNames, final String attributeValue, final Name parent) {
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

    public Set<Name> getNamesWithAttributeContaining(final String attributeName, final String attributeValue) {
        return getNamesByAttributeValueWildcards(attributeName, attributeValue, true, true);
    }

    // get names containing an attribute using wildcards, start end both

    private Set<Name> getNamesByAttributeValueWildcards(final String attributeName, final String attributeValueSearch, final boolean startsWith, final boolean endsWith) {
        final String uctAttributeName = attributeName.toUpperCase().trim();
        final String lctAttributeValueSearch = attributeValueSearch.toLowerCase().trim();
        final Set<Name> names = new HashSet<Name>();
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

    public void zapUnusedNames() throws Exception {
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

    public List<Name> findTopNames() {
        final List<Name> toReturn = new ArrayList<Name>();
        for (Name name : nameByIdMap.values()) {
            if (name.getParents().size() == 0) {
                toReturn.add(name);
            }
        }
        return toReturn;
    }

    public Provenance getProvenanceById(final int id) {
        return provenanceByIdMap.get(id);
    }

    // syncronized back on for fast maps

    protected void addNameToDb(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (newName.getId() > 0 && nameByIdMap.get(newName.getId()) != null) {
            throw new Exception("tried to add a name to the database with an existing id! new id = " + newName.getId());
        } else {
            //synchronized (nameByIdMap){
            nameByIdMap.put(newName.getId(), newName);
            //}
        }
    }

    protected void removeNameFromDb(final Name toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        //synchronized (nameByIdMap) {
        nameByIdMap.remove(toRemove.getId());
        //}
    }

    // ok I'd have liked this to be part of add name to db but the name won't have been initialised, add name to db is called in the name constructor
    // before the attributes have been initialised

    protected void addNameToAttributeNameMap(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        final Map<String, String> attributes = newName.getAttributes();

        for (String attributeName : attributes.keySet()) {
            setAttributeForNameInAttributeNameMap(attributeName, attributes.get(attributeName), newName);
        }
    }

    // like above but for one attribute

    public void setAttributeForNameInAttributeNameMap(String attributeName, String attributeValue, Name name) {
        String lcAttributeValue = attributeValue.toLowerCase().trim();
        String ucAttributeName = attributeName.toUpperCase().trim();
        if (nameByAttributeMap.get(ucAttributeName) == null) { // make a new map for the attributes
            nameByAttributeMap.put(ucAttributeName.intern(), new ConcurrentHashMap<String, List<Name>>());
        }
        final Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
        if (lcAttributeValue.indexOf(Name.QUOTE) >= 0 && !ucAttributeName.equals(Name.CALCULATION)) {
            lcAttributeValue = lcAttributeValue.replace(Name.QUOTE, '\'');
        }
        List<Name> names = namesForThisAttribute.get(lcAttributeValue);
        if (names != null) {
            if (!names.contains(name)) {
                names.add(name);
            }
        } else {
            final List<Name> possibles = new ArrayList<Name>();
            possibles.add(name);
            namesForThisAttribute.put(lcAttributeValue.intern(), possibles);
        }
    }


    protected void removeAttributeFromNameInAttributeNameMap(final String attributeName, final String attributeValue, final Name name) throws Exception {
        String ucAttributeName = attributeName.toUpperCase().trim();
        String lcAttributeValue = attributeValue.toLowerCase().trim();
        name.checkDatabaseMatches(this);
        if (nameByAttributeMap.get(ucAttributeName) != null) {// the map we care about
            final Map<String, List<Name>> namesForThisAttribute = nameByAttributeMap.get(ucAttributeName);
            final List<Name> namesForThatAttributeAndAttributeValue = namesForThisAttribute.get(lcAttributeValue);
            if (namesForThatAttributeAndAttributeValue != null) {
                namesForThatAttributeAndAttributeValue.remove(name); // if it's there which it should be zap it from the set . . .
            }
        }
    }

    // to be called after loading moves the json and extracts attributes to useful maps here
    // called after loading as the names reference themselves

    private synchronized void initNames() throws Exception {
        for (Name name : nameByIdMap.values()) {
            name.populateFromJson();
            addNameToAttributeNameMap(name);
        }
    }

    // trying for new more simplified persistence - make functions not linked to classes
    // maps will be set up in the constructor. Think about any concurrency issues here???

    protected void setEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity azquoMemoryDBEntity) {
        if (!needsLoading && entitiesToPersist.size() > 0) {
            entitiesToPersist.get(tableToPersistIn).add(azquoMemoryDBEntity);
        }
    }

    protected void removeEntityNeedsPersisting(String tableToPersistIn, AzquoMemoryDBEntity azquoMemoryDBEntity) {
        if (!needsLoading) {
            entitiesToPersist.get(tableToPersistIn).remove(azquoMemoryDBEntity);
        }
    }

    protected void addValueToDb(final Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.get(newValue.getId()) != null) {
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            valueByIdMap.put(newValue.getId(), newValue);
        }
    }

    protected void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.get(newProvenance.getId()) != null) {
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            provenanceByIdMap.put(newProvenance.getId(), newProvenance);
        }
    }

    public void memoryReport() {
        try {
            /*System.out.println("sizing names");
            int count = 0;
            for (Name n : nameByIdMap.values()) {
                n.size = (int) ObjectSizeCalculator.sizeOfForAzquo(n, new ArrayList<StringBuilder>());
                count++;
                if (count%5000 == 0){
                    System.out.println(count);
                }
            }
            count = 0;
            for (Value v : valueByIdMap.values()) {
                v.size = (int) ObjectSizeCalculator.sizeOfForAzquo(v, new ArrayList<StringBuilder>());
                count++;
                if (count%5000 == 0){
                    System.out.println(count);
                }
            }
            ArrayList<Name> allNames = new ArrayList<Name>(nameByIdMap.values());
            ArrayList<Value> allValues = new ArrayList<Value>(valueByIdMap.values());
            System.out.println("sorting names/values");
            Collections.sort(allNames, new Comparator<Name>() {
                public int compare(Name o1, Name o2) {
                    return -Integer.compare(o1.size, o2.size);
                }
            });
            Collections.sort(allValues, new Comparator<Value>() {
                public int compare(Value o1, Value o2) {
                    return -Integer.compare(o1.size, o2.size);
                }
            });
            for (int i = 0; i < allNames.size() && i < 500; i++) {
                Name name = allNames.get(i);
                System.out.println(name.size + " " + name.getDefaultDisplayName() + " children " + name.getChildren().size() + " parents " + name.getParents().size() + " values " + name.getValues().size() + " peers " + name.getPeers().size());
            }
            for (int i = 0; i < allValues.size() && i < 100; i++) {
                Value value = allValues.get(i);
                System.out.println(value.size + " " + value.getText() + " names " + value.getNames().size());
            }*/
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
                /*if (count%10000 == 0){
                    System.out.println("Example name size : " + name.getDefaultDisplayName() + ", " + df.format(nameSize));
                    List<StringBuilder> report = new ArrayList<StringBuilder>();
                    ObjectSizeCalculator.sizeOfForAzquo(name, report);
                    for (StringBuilder sb : report){
                        System.out.println(sb);
                    }*/

            }
            System.out.println("total names size : " + df.format(totalNameSize));
            System.out.println("size per name : " + totalNameSize / nameByIdMap.size());


            long totalValuesSize = 0;
            Collection<Value> values = valueByIdMap.values();
            for (Value value : values) {
                long valueSize = ObjectSizeCalculator.sizeOfForAzquo(value, null);
                totalValuesSize += valueSize;
/*                if (count%10000 == 0){
                    System.out.println("Example value size : " + value + ", " + df.format(valueSize));
                    List<StringBuilder> report = new ArrayList<StringBuilder>();
                    ObjectSizeCalculator.sizeOfForAzquo(value, report);
                    for (StringBuilder sb : report){
                        System.out.println(sb);
                    }
                }*/
            }
            System.out.println("total values size : " + df.format(totalValuesSize));
            System.out.println("size per value : " + totalValuesSize / valueByIdMap.size());

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}