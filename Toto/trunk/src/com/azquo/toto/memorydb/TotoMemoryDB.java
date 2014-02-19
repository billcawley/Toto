package com.azquo.toto.memorydb;

import com.azquo.toto.adminentities.Database;
import com.azquo.toto.memorydbdao.JsonRecordTransport;
import com.azquo.toto.memorydbdao.StandardDAO;
import com.azquo.toto.service.LoggedInConnection;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * OK, SQL isn't fast enough, it will be persistence but not much more. Need to think about how a Toto memory DB would work
 * As soon as this starts to be  used in anger there must be a db to file dump in case it goes out of sync with MySQL
 */
public final class TotoMemoryDB {

    private static final Logger logger = Logger.getLogger(TotoMemoryDB.class);

    /* damn, I don't think I can auto wire this as I can't guarantee it will be
       ready for the constructor
     */

    private final StandardDAO standardDAO;


    private final Map<String, Map<String, Set<Name>>> nameByAttributeMap; // a map of maps of sets of names. Fun!
    private final Map<Integer, Name> nameByIdMap;
    private final Map<Integer, Value> valueByIdMap;
    private final Map<Integer, Provenance> provenanceByIdMap;

    private boolean needsLoading;

    private final Database database;

    private int maxIdAtLoad;
    private int nextId;

    // when objects are modified they are added to these lists

    private final Map<StandardDAO.PersistedTable, Set<TotoMemoryDBEntity>> entitiesToPersist;

    public TotoMemoryDB(Database database, StandardDAO standardDAO) throws Exception {
        this.database = database;
        this.standardDAO = standardDAO;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByAttributeMap = new ConcurrentHashMap<String, Map<String, Set<Name>>>();
        nameByIdMap = new ConcurrentHashMap<Integer, Name>();
        valueByIdMap = new ConcurrentHashMap<Integer, Value>();
        provenanceByIdMap = new ConcurrentHashMap<Integer, Provenance>();
        entitiesToPersist = new ConcurrentHashMap<StandardDAO.PersistedTable, Set<TotoMemoryDBEntity>>();
        // loop over the possible persisted tables making the empty sets, cunning
        for (StandardDAO.PersistedTable persistedTable : StandardDAO.PersistedTable.values()) {
            entitiesToPersist.put(persistedTable, new HashSet<TotoMemoryDBEntity>());
        }

        loadData();
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

    synchronized private void loadData() {
        if (needsLoading) { // only allow it once!
            System.out.println("loading data for " + getMySQLName());

            try {
                // here we'll populate the memory DB from the database
                long track = System.currentTimeMillis();

                /* ok this code is a bit annoying, one could run through the table names from the outside but then you need to switch on it for the constructor
                one could use TotomemoryDBEntity if having some kind of init from Json function but this makes the objects more mutable than I'd like
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

                track = System.currentTimeMillis();

                // sort out the maps in names, they couldn't be fixed on load as names link to themselves
                initNames();
                System.out.println("names init in " + (System.currentTimeMillis() - track) + "ms");
                //track = System.currentTimeMillis();

                System.out.println("loaded data for " + getMySQLName());

            } catch (Exception e) {
                logger.error("could not load data for " + getMySQLName() + "!", e);
            }
            needsLoading = false;
        }
    }

    // reads from a list of changed objects

    public synchronized void saveDataToMySQL() {
        // this is where I need to think carefully about concurrency, totodb has the last say when the maps are modified although the flags are another point
        // for the moment just make it work.
        // new code that doesn't repeat nearly as much,
        for (StandardDAO.PersistedTable tableToStoreIn : StandardDAO.PersistedTable.values()) { // run through 'em. Worth remembering this enum syntax
            Set<TotoMemoryDBEntity> entities = entitiesToPersist.get(tableToStoreIn);
            if (!entitiesToPersist.isEmpty()) {
                System.out.println("entities to put in " + tableToStoreIn.name() + " : " + entities.size());
                List<JsonRecordTransport> recordsToStore = new ArrayList<JsonRecordTransport>();
                for (TotoMemoryDBEntity entity : new ArrayList<TotoMemoryDBEntity>(entities)) { // we're taking a copy of the set before running through it.
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
                standardDAO.persistJsonRecords(this, tableToStoreIn.name(), recordsToStore);
            }
        }
    }

    // will block currently!

    protected synchronized int getNextId() {
        nextId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextId - 1;
    }

    public Name getNameById(final int id) {
        return nameByIdMap.get(id);
    }

    public Name getNameByAttribute(final LoggedInConnection loggedInConnection, final String attributeValue, final Name parent) {
        String attributeName = loggedInConnection.getLanguage();
        if (nameByAttributeMap.get(attributeName.toLowerCase().trim()) != null) {// there is an attribute with that name in the whole db . . .
            Set<Name> possibles = nameByAttributeMap.get(attributeName.toLowerCase().trim()).get(attributeValue.toLowerCase().trim());
            if (possibles == null) {
                if (!loggedInConnection.getLoose()) {
                    return null;
                }
                //maybe if 'loose' we should look at ALL languages, but here will look at default language.
                possibles = nameByAttributeMap.get(Name.DEFAULT_DISPLAY_NAME.toLowerCase()).get(attributeValue.toLowerCase().trim());
                if (possibles == null) {
                    return null;
                }
            }
            if (parent == null) {
                if (possibles.size() != 1) {
                    return null;
                }
                return possibles.iterator().next();
            } else {
                for (Name possible : possibles) {
                    if (isInParentTreeOf(possible, parent)) {
                        return possible;
                    }
                }
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

    public boolean isInParentTreeOf(final Name child, final Name testParent) {
        for (Name parent : child.getParents()) {
            if (testParent == parent || isInParentTreeOf(parent, testParent)) {
                return true;
            }
        }
        return false;
    }

    private Name getTopParent(final Name name) {
        for (Name parent : name.getParents()) {
            return getTopParent(parent);
        }
        return name;
    }

    public void zapUnusedNames() throws Exception {
        for (Name name : nameByIdMap.values()) {
            // remove everything except top layer and names with values.   Change parents to top layer where sets deleted
            if (name.getParents().size() > 0 && name.getValues().size() == 0) {
                Name topParent = getTopParent(name);
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

/*
    public List<Name> searchNames(final String attribute, String search) {
        long track = System.currentTimeMillis();
        search = search.trim().toLowerCase();
        boolean wildCardAtBeginning = false;
        boolean wildCardAtEnd = false;
        if (search.startsWith("*")) {
            wildCardAtBeginning = true;
            search = search.substring(1);
        }
        if (search.endsWith("*")) {
            wildCardAtEnd = true;
            search = search.substring(0, search.length() - 1);
        }
        final List<Name> toReturn = new ArrayList<Name>();
        if (!wildCardAtBeginning && !wildCardAtEnd) {
            Name check = getNameByAttribute(attribute, search, null);
            if (check != null) {
                toReturn.add(check);
            }
        } else {
            // use new function, remember booleans are swapped
            toReturn.addAll(getNamesByAttributeValueWildcards(attribute, search, wildCardAtEnd, wildCardAtBeginning));
        }
        System.out.println("search time : " + (System.currentTimeMillis() - track));
        return toReturn;
    }
*/

    public Provenance getProvenanceById(final int id) {
        return provenanceByIdMap.get(id);
    }

    // synchronise against the map for the moment, I'm worried about all this!

    protected void addNameToDb(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        synchronized (nameByIdMap) {
            // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
            if (nameByIdMap.get(newName.getId()) != null) {
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

    // ok I'd have liked this to be part of the above function but the name won't have been initialised, has to be called in the name constructor
    // custom maps here need to be dealt with in the constructors I think

    protected void addNameToAttributeNameMap(final Name newName) throws Exception {
        synchronized (nameByAttributeMap) {
            newName.checkDatabaseMatches(this);
            final Map<String, String> attributes = newName.getAttributes();

            for (String attributeName : attributes.keySet()) {
                if (nameByAttributeMap.get(attributeName.toLowerCase().trim()) == null) { // make a new map for the attributes
                    nameByAttributeMap.put(attributeName.toLowerCase().trim(), new HashMap<String, Set<Name>>());
                }
                final Map<String, Set<Name>> namesForThisAttribute = nameByAttributeMap.get(attributeName.toLowerCase().trim());
                final String attributeValue = attributes.get(attributeName).toLowerCase().trim();
                if (attributeValue.contains("`")) {
                    String error = "has quotes";
                    throw new Exception(error);
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

    private synchronized void initNames() throws Exception {
        for (Name name : nameByIdMap.values()) {
            name.populateFromJson();
            addNameToAttributeNameMap(name);
        }
    }

/*    protected void removeNameFromDbNameMap(Name name) throws Exception {
        name.checkDatabaseMatches(this);
        String lcName = name.getDefaultDisplayName().toLowerCase();
        if (nameByNameMap.get(lcName) != null) {
            nameByNameMap.get(lcName).remove(name);
        }
    }*/

    // trying for new more simplifies persistence - make functions not linked to classes
    // maps will be set up in the constructor. Think about any concurrency issues here???

    protected void setEntityNeedsPersisting(StandardDAO.PersistedTable tableToPersistIn, TotoMemoryDBEntity totoMemoryDBEntity) {
        if (!needsLoading) {
            entitiesToPersist.get(tableToPersistIn).add(totoMemoryDBEntity);
        }
    }

    protected void removeEntityNeedsPersisting(StandardDAO.PersistedTable tableToPersistIn, TotoMemoryDBEntity totoMemoryDBEntity) {
        if (!needsLoading) {
            entitiesToPersist.get(tableToPersistIn).remove(totoMemoryDBEntity);
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