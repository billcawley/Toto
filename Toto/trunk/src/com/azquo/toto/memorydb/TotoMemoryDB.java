package com.azquo.toto.memorydb;

import com.azquo.toto.adminentities.Database;
import com.azquo.toto.memorydbdao.JsonRecordTransport;
import com.azquo.toto.memorydbdao.StandardDAO;
import com.azquo.toto.service.LoggedInConnection;

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

    private final Set<Name> namesNeedPersisting;
    private final Set<Value> valuesNeedPersisting;
    private final Set<Provenance> provenanceNeedsPersisting;

    public TotoMemoryDB(Database database, StandardDAO standardDAO) throws Exception {
        this.database = database;
        this.standardDAO = standardDAO;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByAttributeMap = new ConcurrentHashMap<String, Map<String, Set<Name>>>();
        nameByIdMap = new ConcurrentHashMap<Integer, Name>();
        valueByIdMap = new ConcurrentHashMap<Integer, Value>();
        provenanceByIdMap = new ConcurrentHashMap<Integer, Provenance>();
        namesNeedPersisting = new HashSet<Name>();
        valuesNeedPersisting = new HashSet<Value>();
        provenanceNeedsPersisting = new HashSet<Provenance>();
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
            System.out.println("loading data for "  + getMySQLName());

            try{
                // here we'll populate the memory DB from the database
                long track = System.currentTimeMillis();

                // these 3 commands will automatically load teh data into the memory DB set as persisted

                // Must load provenance first as used by the other two!

                final List<JsonRecordTransport> allProvenance = standardDAO.findFromTable(this, StandardDAO.PROVENANCE);
                for (JsonRecordTransport provenanceRecord : allProvenance) {
                    if (provenanceRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = provenanceRecord.id;
                    }
                    new Provenance(this, provenanceRecord.id, provenanceRecord.json);
                }
                final List<JsonRecordTransport> allNames = standardDAO.findFromTable(this, StandardDAO.NAME);
                for (JsonRecordTransport nameRecord : allNames) {
                    if (nameRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = nameRecord.id;
                    }
                    new Name(this, nameRecord.id, nameRecord.json);
                }
                final List<JsonRecordTransport> allValues = standardDAO.findFromTable(this, StandardDAO.VALUE);
                for (JsonRecordTransport valueRecord : allValues) {
                    if (valueRecord.id > maxIdAtLoad) {
                        maxIdAtLoad = valueRecord.id;
                    }
                    new Value(this, valueRecord.id, valueRecord.json);
                }

                System.out.println(allNames.size() + allValues.size() + allProvenance.size() + " unlinked entities loaded in " + (System.currentTimeMillis() - track) + "ms");

                track = System.currentTimeMillis();

                int linkCounter = 0;
                // sort out the maps in names, they couldn't be fixed on load as names link to themselves
                initNames();
                System.out.println(linkCounter + " values name links created in " + (System.currentTimeMillis() - track) + "ms");
                //track = System.currentTimeMillis();

                System.out.println("loaded data for "  + getMySQLName());

            } catch (Exception e){
                System.out.println("could not load data for " + getMySQLName() + "! Stacktrace to follow");
                e.printStackTrace();
            }
            needsLoading = false;
        }
    }

    // reads from a list of changed objects

    public synchronized void saveDataToMySQL() {
        // this is where I need to think carefully about concurrency, totodb has the last say when the maps are modified although the flags are another point
        // for the moment just make it work.
        System.out.println("nnp size : " + namesNeedPersisting.size());
        List<JsonRecordTransport> nameRecordsToStore = new ArrayList<JsonRecordTransport>();
        for (Name name : new ArrayList<Name>(namesNeedPersisting)) {
            JsonRecordTransport.State state = JsonRecordTransport.State.UPDATE;
            if (name.getNeedsDeleting()) {
                state = JsonRecordTransport.State.DELETE;
            }
            if (name.getNeedsInserting()) {
                state = JsonRecordTransport.State.INSERT;
            }
            nameRecordsToStore.add(new JsonRecordTransport(name.getId(), name.getAsJson(), state));
            name.setAsPersisted(); // is this dangerous here???
        }
        standardDAO.persistJsonRecords(this, StandardDAO.NAME, nameRecordsToStore);

        System.out.println("vnp size : " + valuesNeedPersisting.size());
        List<JsonRecordTransport> valueRecordsToStore = new ArrayList<JsonRecordTransport>();
        for (Value value : new ArrayList<Value>(valuesNeedPersisting)) {
            JsonRecordTransport.State state = JsonRecordTransport.State.UPDATE;
            if (value.getNeedsDeleting()) {
                state = JsonRecordTransport.State.DELETE;
            }
            if (value.getNeedsInserting()) {
                state = JsonRecordTransport.State.INSERT;
            }
            valueRecordsToStore.add(new JsonRecordTransport(value.getId(), value.getAsJson(), state));
            value.setAsPersisted(); // is this dangerous here???
        }
        standardDAO.persistJsonRecords(this, StandardDAO.VALUE, valueRecordsToStore);

        System.out.println("pnp size : " + provenanceNeedsPersisting.size());
        List<JsonRecordTransport> provenanceRecordsToStore = new ArrayList<JsonRecordTransport>();
        for (Provenance provenance : new ArrayList<Provenance>(provenanceNeedsPersisting)) {
            JsonRecordTransport.State state = JsonRecordTransport.State.UPDATE;
            if (provenance.getNeedsDeleting()) {
                state = JsonRecordTransport.State.DELETE;
            }
            if (provenance.getNeedsInserting()) {
                state = JsonRecordTransport.State.INSERT;
            }
            provenanceRecordsToStore.add(new JsonRecordTransport(provenance.getId(), provenance.getAsJson(), state));
            provenance.setAsPersisted(); // is this dangerous here???
        }
        standardDAO.persistJsonRecords(this, StandardDAO.PROVENANCE, provenanceRecordsToStore);
    }

    // will block currently!

    protected synchronized int getNextId() {
        nextId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextId - 1;
    }

    // for search purposes probably should trim

    public Name getNameById(final int id) {
        return nameByIdMap.get(id);

    }

    public Name getNameByAttribute(final LoggedInConnection loggedInConnection, final String attributeValue, final Name parent) {
        String attributeName = loggedInConnection.getLanguage();
        if (nameByAttributeMap.get(attributeName.toLowerCase().trim()) != null) {// there is an attribute with that name in the whole db . . .
            Set<Name> possibles = nameByAttributeMap.get(attributeName.toLowerCase().trim()).get(attributeValue.toLowerCase().trim());
            if (possibles == null){
                if (!loggedInConnection.getLoose()){
                    return null;
                }
                //maybe if 'loose' we should look at ALL languages, but here will look at default language.
                possibles = nameByAttributeMap.get(Name.DEFAULT_DISPLAY_NAME.toLowerCase()).get(attributeValue.toLowerCase().trim());
                if (possibles == null){
                    return null;
                }
            }
            if (parent == null) {
                if (possibles.size() != 1) return null;
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

    // cet names containing an attribute using wildcards, start end both

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
/*    public Name getNameById(int id) {
        return nameByIdMap.get(id);
    }

    public Value getValueById(int id) {
        return valueByIdMap.get(id);
    }*/

    public Provenance getProvenanceById(final int id) {
        return provenanceByIdMap.get(id);
    }

    // synchronise against the map for the moment, I'm worried about all this!

    protected void addNameToDb(final Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        synchronized (nameByIdMap){
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
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        synchronized (nameByIdMap){
            nameByIdMap.remove(toRemove.getId());
        }
    }

    // ok I'd have liked this to be part of the above function but the name won't have been initialised, has to be called in the name constructor
    // custom maps here need to be dealt with in the constructors I think

    protected void addNameToAttributeNameMap(final Name newName) throws Exception {
        synchronized (nameByAttributeMap){
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
        synchronized (nameByAttributeMap){
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

    // hmmm syncronizing by the map objects. USe concurrent hashmaps???

    protected void setNameNeedsPersisting(final Name name) {
        if (!needsLoading) {
            synchronized (namesNeedPersisting){
                namesNeedPersisting.add(name);
            }
        }
    }

    protected void removeNameNeedsPersisting(final Name name) {
        synchronized (namesNeedPersisting){
        namesNeedPersisting.remove(name);
        }
    }

    protected void addValueToDb(final Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        synchronized (valueByIdMap){
            // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
            if (valueByIdMap.get(newValue.getId()) != null) {
                throw new Exception("tried to add a value to the database with an existing id!");
            } else {
                valueByIdMap.put(newValue.getId(), newValue);
            }
        }
    }

    protected void setValueNeedsPersisting(final Value value) {
        synchronized (valuesNeedPersisting){
            if (!needsLoading) {
                valuesNeedPersisting.add(value);
            }
        }
    }

    protected void removeValueNeedsPersisting(final Value value) {
        synchronized (valuesNeedPersisting){
            valuesNeedPersisting.remove(value);
        }
    }

    protected void addProvenanceToDb(final Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        synchronized (provenanceByIdMap){
            // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
            if (provenanceByIdMap.get(newProvenance.getId()) != null) {
                throw new Exception("tried to add a value to the database with an existing id!");
            } else {
                provenanceByIdMap.put(newProvenance.getId(), newProvenance);
            }
        }
    }

    protected void setProvenanceNeedsPersisting(final Provenance provenance) {
        if (!needsLoading) {
            synchronized (provenanceNeedsPersisting){
                provenanceNeedsPersisting.add(provenance);
            }
        }
    }

    protected void removeProvenanceNeedsPersisting(final Provenance provenance) {
        synchronized (provenanceNeedsPersisting){
            provenanceNeedsPersisting.remove(provenance);
        }
    }
}