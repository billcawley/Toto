package com.azquo.memorydb;

import com.azquo.adminentities.Database;
import com.azquo.memorydbdao.JsonRecordTransport;
import com.azquo.memorydbdao.StandardDAO;
import com.azquo.service.AppEntityService;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * created after it became apparent that Mysql in the way I'd arranged the objects didn't have a hope in hell of
 * delivering data fast enough. Leverage collections to implement Azquo spec.
 */
public final class AzquoMemoryDB {


    private static final Logger logger = Logger.getLogger(AzquoMemoryDB.class);

    //I don't think I can auto wire this as I can't guarantee it will be ready for the constructor

    private final StandardDAO standardDAO;

    private final Map<String, Map<String, Set<Name>>> nameByAttributeMap; // a map of maps of sets of names. Fun!

    // simple by id maps, if an object is in one of these three it's in the database
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    // does this database need loading from mysql, a significant flag that affects rules for memory db instantiation for example
    private boolean needsLoading;

    // reference to the mysql db
    private final Database database;

    // object ids. We handle this here, it's not done by MySQL
    private int maxIdAtLoad;
    private int nextId;

    // when objects are modified they are added to these sets held in a map. AzquoMemoryDBEntity has all functions require to persist.

    private final Map<String, Set<AzquoMemoryDBEntity>> entitiesToPersist;

    // Initialising maps concurrently here,

    protected AzquoMemoryDB(Database database, StandardDAO standardDAO, List<AppEntityService> appServices) throws Exception {
        this.database = database;
        this.standardDAO = standardDAO;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByAttributeMap = new ConcurrentHashMap<String, Map<String, Set<Name>>>();
        nameByIdMap = new ConcurrentHashMap<Integer, Name>();
        valueByIdMap = new ConcurrentHashMap<Integer, Value>();
        provenanceByIdMap = new ConcurrentHashMap<Integer, Provenance>();
        entitiesToPersist = new ConcurrentHashMap<String, Set<AzquoMemoryDBEntity>>();
        // loop over the possible persisted tables making the empty sets, cunning
        if (standardDAO != null) {
            for (StandardDAO.PersistedTable persistedTable : StandardDAO.PersistedTable.values()) {
                entitiesToPersist.put(persistedTable.name(), new HashSet<AzquoMemoryDBEntity>());
            }
        }
        if (appServices != null) {
            for (AppEntityService appEntityService : appServices) {
                // seems a good a place as any to create the MySQL table if it doesn't exist
                appEntityService.checkCreateMySQLTable(this);
                entitiesToPersist.put(appEntityService.getTableName(), new HashSet<AzquoMemoryDBEntity>());
            }
        }
        if (standardDAO != null){
           loadData(appServices);
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

    public boolean getNeedsLoading() {
        return needsLoading;
    }

    // now passing app services

    synchronized private void loadData(List<AppEntityService> appServices) {
        if (needsLoading) { // only allow it once!
            System.out.println("loading data for " + getMySQLName());

            try {
                // here we'll populate the memory DB from the database
                long track = System.currentTimeMillis();

                /* ok this code is a bit annoying, one could run through the table names from the outside but then you need to switch on it for the constructor
                one could use AzquomemoryDBEntity if having some kind of init from Json function but this makes the objects more mutable than I'd like
                so we stay with this for the moment
                these 3 commands will automatically load the data into the memory DB set as persisted
                Load order is important as value and name use provenance and value uses names. Names uses itself hence all names need initialisation finished after the id map is sorted
                */
                final List<JsonRecordTransport> allProvenance = standardDAO.findFromTable(this, StandardDAO.PersistedTable.provenance.name());
                for (JsonRecordTransport provenanceRecord : allProvenance) {
                    if (provenanceRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = provenanceRecord.id;
                    }
                    new Provenance(this, provenanceRecord.id, provenanceRecord.json);
                }
                final List<JsonRecordTransport> allNames = standardDAO.findFromTable(this, StandardDAO.PersistedTable.name.name());
                for (JsonRecordTransport nameRecord : allNames) {
                    if (nameRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = nameRecord.id;
                    }
                    new Name(this, nameRecord.id, nameRecord.json);
                }
                final List<JsonRecordTransport> allValues = standardDAO.findFromTable(this, StandardDAO.PersistedTable.value.name());
                for (JsonRecordTransport valueRecord : allValues) {
                    if (valueRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = valueRecord.id;
                    }
                    new Value(this, valueRecord.id, valueRecord.json);
                }

                System.out.println(allNames.size() + allValues.size() + allProvenance.size() + " unlinked entities loaded in " + (System.currentTimeMillis() - track) + "ms");


                // sort out the maps in names, they couldn't be fixed on load as names link to themselves
                initNames();
                System.out.println("names init in " + (System.currentTimeMillis() - track) + "ms");

                track = System.currentTimeMillis();
                if (appServices != null){ // dunno why it wasn't there before
                    for (AppEntityService appEntityService : appServices) {
                        final List<JsonRecordTransport> appEntities = standardDAO.findFromTable(this, appEntityService.getTableName());
                        for (JsonRecordTransport appEntityRecord : appEntities) {
                            if (appEntityRecord.id > maxIdAtLoad) {
                                maxIdAtLoad = appEntityRecord.id;
                            }
                            appEntityService.loadEntityFromJson(this, appEntityRecord.id, appEntityRecord.json);
                        }
                    }
                }
                System.out.println("app entities loaded in " + (System.currentTimeMillis() - track) + "ms");

                System.out.println("loaded data for " + getMySQLName());

            } catch (Exception e) {
                logger.error("could not load data for " + getMySQLName() + "!", e);
            }
            needsLoading = false;
        }
    }

    // reads from a list of changed objects
    // todo : should this be public??

    public synchronized void saveDataToMySQL() {
        // this is where I need to think carefully about concurrency, azquodb has the last say when the sets are modified although the flags are another point
        // for the moment just make it work.
        // map of sets to persist means that should we add another object type then this code should not need to change
        for (String tableToStoreIn : entitiesToPersist.keySet()) { // run through 'em was an enum of set table names, not it could be anything depending on app tables. See no harm in theh generic nature
            Set<AzquoMemoryDBEntity> entities = entitiesToPersist.get(tableToStoreIn);
            if (!entitiesToPersist.isEmpty()) {
                System.out.println("entities to put in " + tableToStoreIn + " : " + entities.size());
                List<JsonRecordTransport> recordsToStore = new ArrayList<JsonRecordTransport>();
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
                standardDAO.persistJsonRecords(this, tableToStoreIn, recordsToStore);
            }
        }
    }

    // will block currently!

    protected synchronized int getNextId() {
        nextId++; // increment but return what it was . . .a little messy but I want that value in memory to be what it says
        return nextId - 1;
    }

    public Name getNameById(final int id) {
        return nameByIdMap.get(id);
    }

    //fundamental low level function to get a set of names from the attribute indexes. Forces case insensitivity.

    private Set<Name> getNamesForAttribute(final String attributeName, final String attributeValue){
        Map <String,Set<Name>> map = nameByAttributeMap.get(attributeName.toLowerCase().trim());
        if (map != null){ // that attribute is there
            Set<Name> names = map.get(attributeValue.toLowerCase().trim());
            if (names != null){ // were there any entries for that value?
                return new HashSet<Name>(names);
            }
        }
        return Collections.emptySet(); // moving away from nulls
    }

    // same as above but then zap any not in the parent

    private Set<Name> getNamesForAttributeAndParent(final String attributeName, final String attributeValue, Name parent){
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

    private Set<Name> getNamesForAttributeNamesAndParent(final List<String> attributeNames, final String attributeValue, Name parent){
        if (parent != null){
            for (String attributeName : attributeNames){
                Set<Name> names = getNamesForAttributeAndParent(attributeName, attributeValue, parent);
                if (!names.isEmpty()){
                    return names;
                }
            }
        } else {
            for (String attributeName : attributeNames){
                Set<Name> names = getNamesForAttribute(attributeName, attributeValue);
                if (!names.isEmpty()){
                    return names;
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
        return getNameByAttribute( Arrays.asList(attributeName), attributeValue, parent);
    }

    public Name getNameByAttribute(final List<String> attributeNames, final String attributeValue, final Name parent) {
        Set<Name> possibles = getNamesForAttributeNamesAndParent(attributeNames, attributeValue, parent);
        // all well and good but now which one to return?
        if(possibles.size() == 1){ // simple
            return possibles.iterator().next();
        } else if (possibles.size() > 1) { // more than one . . . try and replicate logic that was there before
            if (parent == null){ // no parent criteria
                for (Name possible : possibles){
                    if (possible.getParents().size() == 0){ // we chuck back the first top level one. Not sure this is the best logic, more than one possible with no top levels means return null
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
        final String lctAttributeName = attributeName.toLowerCase().trim();
        final String lctAttributeValueSearch = attributeValueSearch.toLowerCase().trim();
        final Set<Name> names = new HashSet<Name>();
        if (nameByAttributeMap.get(lctAttributeName) == null){
            return names;
        }
        for (String attributeValue : nameByAttributeMap.get(lctAttributeName).keySet()) {
            if (startsWith && endsWith) {
                if (attributeValue.toLowerCase().contains(lctAttributeValueSearch.toLowerCase())) {
                    names.addAll(nameByAttributeMap.get(lctAttributeName).get(attributeValue));
                }
            } else if (startsWith) {
                if (attributeValue.toLowerCase().startsWith(lctAttributeValueSearch.toLowerCase())) {
                    names.addAll(nameByAttributeMap.get(lctAttributeName).get(attributeValue));
                }
            } else if (endsWith) {
                if (attributeValue.toLowerCase().endsWith(lctAttributeValueSearch.toLowerCase())) {
                    names.addAll(nameByAttributeMap.get(lctAttributeName).get(attributeValue));
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

    // synchronise against the map for the moment, I'm worried about all this!

    protected void addNameToDb(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        synchronized (nameByIdMap) {
            // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
            if (newName.getId() > 0 && nameByIdMap.get(newName.getId()) != null) {
                throw new Exception("tried to add a name to the database with an existing id! new id = " + newName.getId());
            } else {
                nameByIdMap.put(newName.getId(), newName);
            }
        }
    }

    protected void removeNameFromDb(final Name toRemove) throws Exception {
        toRemove.checkDatabaseMatches(this);
        synchronized (nameByIdMap) {
            nameByIdMap.remove(toRemove.getId());
        }
    }

    // ok I'd have liked this to be part of add name to db but the name won't have been initialised, add name to db is called in the name constructor
    // before the attributes have been initialised

    protected void addNameToAttributeNameMap(final Name newName) throws Exception {
        synchronized (nameByAttributeMap) {
            newName.checkDatabaseMatches(this);
            final Map<String, String> attributes = newName.getAttributes();

            for (String attributeName : attributes.keySet()) {
                if (nameByAttributeMap.get(attributeName.toLowerCase().trim()) == null) { // make a new map for the attributes
                    nameByAttributeMap.put(attributeName.toLowerCase().trim(), new HashMap<String, Set<Name>>());
                }
                final Map<String, Set<Name>> namesForThisAttribute = nameByAttributeMap.get(attributeName.toLowerCase().trim());
                String attributeValue = attributes.get(attributeName).toLowerCase().trim();
                if (attributeValue.indexOf(Name.QUOTE) >= 0 && !attributeName.equals(Name.CALCULATION)) {
                     attributeValue = attributeValue.replace(Name.QUOTE,'\'');
                  }
                if (namesForThisAttribute.get(attributeValue) != null) {
                    namesForThisAttribute.get(attributeValue).add(newName);
                } else {
                    final Set<Name> possibles = new HashSet<Name>();
                    possibles.add(newName);
                    namesForThisAttribute.put(attributeValue, possibles);
                }
            }
        }
    }

    protected void removeAttributeFromNameInAttributeNameMap(final String attributeName, final String attributeValue, final Name name) throws Exception {
        name.checkDatabaseMatches(this);
        synchronized (nameByAttributeMap) {
            if (nameByAttributeMap.get(attributeName.toLowerCase().trim()) != null) {// the map we care about
                final Map<String, Set<Name>> namesForThisAttribute = nameByAttributeMap.get(attributeName.toLowerCase().trim());
                final Set<Name> namesForThatAttributeAndAttributeValue = namesForThisAttribute.get(attributeValue.toLowerCase().trim());
                if (namesForThatAttributeAndAttributeValue != null) {
                    namesForThatAttributeAndAttributeValue.remove(name); // if it's there which it should be zap it from the set . . .
                }
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
        if (!needsLoading  && entitiesToPersist.size() > 0) {
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
        synchronized (valueByIdMap) {
            // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
            if (valueByIdMap.get(newValue.getId()) != null) {
                throw new Exception("tried to add a value to the database with an existing id!");
            } else {
                valueByIdMap.put(newValue.getId(), newValue);
            }
        }
    }

    protected void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        synchronized (provenanceByIdMap) {
            // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
            if (provenanceByIdMap.get(newProvenance.getId()) != null) {
                throw new Exception("tried to add a value to the database with an existing id!");
            } else {
                provenanceByIdMap.put(newProvenance.getId(), newProvenance);
            }
        }
    }
}