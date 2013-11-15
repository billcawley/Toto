package com.azquo.toto.memorydb;

import com.azquo.toto.dao.NameDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * OK, SQL isn't fast enough, it will be persistence but not much more. Need to think about how a Toto memory DB would work
 * As soon as this starts to be  used in anger there must be a db to file dump in case it goes out of sync with MySQL
 */
public final class TotoMemoryDB {

    /* damn, I don't think I can auto wire these as I can't guarantee they'll be
       ready for the constructor
     */

    private final ValueDAO valueDAO;
    private final NameDAO nameDAO;
    private final ProvenanceDAO provenanceDAO;


    private final HashMap<String, Name> nameByNameMap;
    private final HashMap<Integer, Name> nameByIdMap;
    private final HashMap<Integer, Value> valueByIdMap;
    private final HashMap<Integer, Provenance> provenanceByIdMap;

    private boolean needsLoading;

    private final String databaseName;

    private int maxIdAtLoad;
    private int nextId;

    // when objects are modified they are added to these lists

    private Set<Name> namesNeedPersisting;
    private Set<Value> valuesNeedPersisting;
    private Set<Provenance> provenanceNeedsPersisting;

    public TotoMemoryDB(String databaseName, NameDAO nameDAO, ValueDAO valueDAO, ProvenanceDAO provenanceDAO) throws Exception {
        this.databaseName = databaseName;
        this.nameDAO = nameDAO;
        this.valueDAO = valueDAO;
        this.provenanceDAO = provenanceDAO;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByNameMap = new HashMap<String, Name>();
        nameByIdMap = new HashMap<Integer, Name>();
        valueByIdMap = new HashMap<Integer, Value>();
        provenanceByIdMap = new HashMap<Integer, Provenance>();
        namesNeedPersisting = new HashSet<Name>();
        valuesNeedPersisting = new HashSet<Value>();
        provenanceNeedsPersisting = new HashSet<Provenance>();
        loadData();
        nextId = maxIdAtLoad + 1;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public boolean getNeedsLoading() {
        return needsLoading;
    }

    // right now this will not run properly!

    synchronized private void loadData() throws Exception {
        if (needsLoading) { // only allow it once!
            // here we'll populate the memory DB from the database
            long track = System.currentTimeMillis();

            // these 3 commands will automatically load teh data into the memory DB set as persisted

            final List<Name> allNames = nameDAO.findAll(this);
            final List<Value> allValues = valueDAO.findAll(this);
            final List<Provenance> allProvenance = provenanceDAO.findAll(this);

            System.out.println(allNames.size() + allValues.size() + allProvenance.size() + " unlinked entities loaded in " + (System.currentTimeMillis() - track) + "ms");

            // now gotta link 'em

            track = System.currentTimeMillis();

            int linkCounter = 0;
            // going to speed things up by selecting the lot like we do with value


            int currentParentId = -1;
            LinkedHashSet<Name> childSet = new LinkedHashSet<Name>();
            for (String parentIdChildIdPair : nameDAO.findAllParentChildLinksOrderByParentIdPosition(this)) {
                int parentId = Integer.parseInt(parentIdChildIdPair.substring(0, parentIdChildIdPair.indexOf(",")));
                int childId = Integer.parseInt(parentIdChildIdPair.substring(parentIdChildIdPair.indexOf(",") + 1));
                // ok we're switching to a new value ID, link the ones previously
                if (parentId != currentParentId) {
                    if (currentParentId != -1) { // assign ones just passed if not the first
                        Name parent = nameByIdMap.get(currentParentId);
                        parent.setChildrenWillBePersisted(childSet);
                    }
                    currentParentId = parentId;
                    childSet = new LinkedHashSet<Name>();
                }
                childSet.add(nameByIdMap.get(childId));
                linkCounter++;
            }
            // clear up the last one
            if (childSet.size() > 0) {
                Name parent = nameByIdMap.get(currentParentId);
                parent.setChildrenWillBePersisted(childSet);
            }
            System.out.println(linkCounter + " child parent linked created in " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();


            linkCounter = 0;
            // again need too speed things up . . .


            currentParentId = -1;
            LinkedHashSet<Name> peerSet = new LinkedHashSet<Name>();
            for (String parentIdPeerIdPair : nameDAO.findAllPeerLinksOrderByParentIdPosition(this)) {
                int parentId = Integer.parseInt(parentIdPeerIdPair.substring(0, parentIdPeerIdPair.indexOf(",")));
                int peerId = Integer.parseInt(parentIdPeerIdPair.substring(parentIdPeerIdPair.indexOf(",") + 1));
                // ok we're switching to a new value ID, link the ones previously
                if (parentId != currentParentId) {
                    if (currentParentId != -1) { // assign ones just passed if not the first
                        Name parent = nameByIdMap.get(currentParentId);
                        parent.setPeersWillBePersisted(peerSet);
                    }
                    currentParentId = parentId;
                    peerSet = new LinkedHashSet<Name>();
                }
                peerSet.add(nameByIdMap.get(peerId));
                linkCounter++;
            }
            // clear up the last one
            if (peerSet.size() > 0) {
                Name parent = nameByIdMap.get(currentParentId);
                parent.setPeersWillBePersisted(peerSet);
            }
            System.out.println(linkCounter + " peer names links created in " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            // ok this was taking a while so let's try a new idea where we select the whole lot ordering by value id
            // might be a bit hacky but should massively speed loading

            linkCounter = 0;
/*            for (Value value : valueByIdMap.values()){
                List<Integer> nameIdsForThisValue = valueDAO.findNameIdsForValue(this, value);
                Set<Name> nameSet = new HashSet<Name>(nameIdsForThisValue.size());
                for (Integer nameId: nameIdsForThisValue){
                    nameSet.add(nameByIdMap.get(nameId));
                    linkCounter++;
                }
                value.setNamesWillBePersisted(nameSet);
                if (value.getId() > maxIdAtLoad){
                    maxIdAtLoad = value.getId();
                }
            }*/
            int currentValueId = -1;
            Set<Name> nameSet = new HashSet<Name>();
            for (String valueNameIdPair : valueDAO.findAllValueNameLinksOrderByValue(this)) {
                int valueId = Integer.parseInt(valueNameIdPair.substring(0, valueNameIdPair.indexOf(",")));
                int nameId = Integer.parseInt(valueNameIdPair.substring(valueNameIdPair.indexOf(",") + 1));
                // ok we're switching too a new value ID, link the ones previously
                if (valueId != currentValueId) {
                    if (currentValueId != -1) { // assign ones just passed if not the first
                        Value value = valueByIdMap.get(currentValueId);
                        value.setNamesWillBePersisted(nameSet);
                    }
                    currentValueId = valueId;
                    nameSet = new HashSet<Name>();
                }
                nameSet.add(nameByIdMap.get(nameId));
                linkCounter++;
            }
            // clear up the last one
            if (nameSet.size() > 0) {
                Value value = valueByIdMap.get(currentValueId);
                value.setNamesWillBePersisted(nameSet);
            }

            System.out.println(linkCounter + " values name links created in " + (System.currentTimeMillis() - track) + "ms");
            //track = System.currentTimeMillis();

            // check ids for max, a bit hacky

            for (Provenance provenance : allProvenance) {
                if (provenance.getId() > maxIdAtLoad) {
                    maxIdAtLoad = provenance.getId();
                }
            }
            for (Value value : allValues) {
                if (value.getId() > maxIdAtLoad) {
                    maxIdAtLoad = value.getId();
                }
            }
            needsLoading = false;
        }
    }

    // reads from a list of changed objects

    public synchronized void saveDataToMySQL() {
        // need to go through each object and links I guess!

        // this is where I need to think carefully about concurrency, totodb has the last say when the maps are modified although the flags are another point
        // for the moment just make it work.
        System.out.println("nnp size : " + namesNeedPersisting.size());
        for (Name name : new ArrayList<Name>(namesNeedPersisting)) {
            if (name.getEntityColumnsChanged()) {
                // store the name
                nameDAO.store(this, name);
            }
            int links = 0;
            if (name.getChildrenChanged()) { // then add to a sat to be passed to a faster function??
                nameDAO.unlinkAllForParent(this, NameDAO.SetDefinitionTable.name_set_definition, name);
                nameDAO.linkParentAndChildren(this, NameDAO.SetDefinitionTable.name_set_definition, name);
                links += name.getChildren().size();
                System.out.println(name.getName() + " changed children size : " + name.getChildren().size() + " links : " + links);
            }


            if (name.getPeersChanged()) {
                int position = 1;
                nameDAO.unlinkAllForParent(this, NameDAO.SetDefinitionTable.peer_set_definition, name);
                for (Name peer : name.getPeers()) {
                    nameDAO.linkParentAndChild(this, NameDAO.SetDefinitionTable.peer_set_definition, name, peer, position);
                    position++;
                }
            }
            name.setAsPersisted(); // is this dangerous here???
            // going to save value label links by value to label rather than label to value as the lists on the latter will be big. Change too one value may cause much relinking
            // we don't deal with parents, they're just convenience lookup lists
        }
        System.out.println("vnp size : " + valuesNeedPersisting.size());

        Set<Value> upTo500toLink = new HashSet<Value>();
        Set<Value> upTo500toInsert = new HashSet<Value>();

        for (Value value : new ArrayList<Value>(valuesNeedPersisting)) {
            if (value.getEntityColumnsChanged()) {
                if (value.getNeedsInserting()) {
                    upTo500toInsert.add(value);
                } else { // this really means update then
                    System.out.println("value needed updating : " + value);
                    valueDAO.store(this, value);
                }
            }


            // ok going to go in groups of 500 here for linking. May need to do the same for store also

            if (value.getNamesChanged()) {
                upTo500toLink.add(value);
            }
            if (upTo500toLink.size() == 500) {
                valueDAO.unlinkValuesFromNames(this, upTo500toLink);
                valueDAO.linkValuesToNames(this, upTo500toLink);
                upTo500toLink = new HashSet<Value>();
            }
            if (upTo500toInsert.size() == 500) {
                valueDAO.bulkInsert(this, upTo500toInsert);
                upTo500toInsert = new HashSet<Value>();
            }
            value.setAsPersisted();
        }
        if (!upTo500toLink.isEmpty()) {
            valueDAO.unlinkValuesFromNames(this, upTo500toLink);
            valueDAO.linkValuesToNames(this, upTo500toLink);
        }
        if (!upTo500toInsert.isEmpty()) {
            valueDAO.bulkInsert(this, upTo500toInsert);
        }
        System.out.println("pnp size : " + provenanceNeedsPersisting.size());
        for (Provenance provenance : new ArrayList<Provenance>(provenanceNeedsPersisting)) {
            if (provenance.getEntityColumnsChanged()) {
                provenanceDAO.store(this, provenance);
            }
            provenance.setAsPersisted();
        }
/*        valuesNeedPersisting = new HashSet<Value>();
        provenanceNeedsPersisting = new HashSet<Provenance>();*/

    }

    protected synchronized int getNextId() {
        nextId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextId - 1;
    }

    // for search purposes probably should trim

    public Name getNameByName(String name) {
        return nameByNameMap.get(name.toLowerCase().trim());
    }

    public List<Name> findTopNames() {
        long track = System.currentTimeMillis();
        final List<Name> toReturn = new ArrayList<Name>();
        System.out.println("top name : " + (System.currentTimeMillis() - track));
        for (Name name : nameByIdMap.values()){
            if (name.getParents().size() == 0){
                toReturn.add(name);
            }
        }
        return toReturn;
    }


    public List<Name> searchNames(String search) {
        long track = System.currentTimeMillis();
        search = search.trim().toLowerCase();
        boolean wildCardAtBeginning = false;
        boolean wildCardAtEnd = false;
        if (search.startsWith("*")){
            wildCardAtBeginning = true;
            search = search.substring(1);
        }
        if (search.endsWith("*")){
            wildCardAtEnd = true;
            search = search.substring(0,search.length() - 1);
        }
        final List<Name> toReturn = new ArrayList<Name>();
        if (!wildCardAtBeginning && !wildCardAtEnd){
            if (getNameByName(search) != null){
                toReturn.add(getNameByName(search));
            }
        } else {
            // search the lot!
            for (Name name : nameByIdMap.values()){
                if (wildCardAtBeginning && wildCardAtEnd){
                    if (name.getName().toLowerCase().contains(search)){
                        toReturn.add(name);
                    }
                } else if(wildCardAtBeginning){
                    if (name.getName().toLowerCase().endsWith(search)){
                        toReturn.add(name);
                    }
                } else {
                    if (name.getName().toLowerCase().startsWith(search)){
                        toReturn.add(name);
                    }
                }
            }

        }
        System.out.println("search time : " + (System.currentTimeMillis() - track));
        return toReturn;
    }

/*    public Name getNameById(int id) {
        return nameByIdMap.get(id);
    }

    public Value getValueById(int id) {
        return valueByIdMap.get(id);
    }*/

/*    public Provenance getProvenanceById(int id) {
        return provenanceByIdMap.get(id);
    }*/

    // synchronised?

    protected void addNameToDb(Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (nameByIdMap.get(newName.getId()) != null) {
            throw new Exception("tried to add a name to the database with an existing id! new id = " + newName.getId());
        } else {
            nameByIdMap.put(newName.getId(), newName);
        }
    }

    // ok I'd have liked this to be part of the above function but the name won't have been initialised, has to be called in the name constructor
    // custom maps here need to be dealt with in the constructors I think

    protected void addNameToDbNameMap(Name newName) throws Exception {
        newName.checkDatabaseMatches(this);
        if (nameByNameMap.get(newName.getName().toLowerCase()) != null) {
            throw new Exception("tried to add a name to the database with an existing name!");
        } else {
            nameByNameMap.put(newName.getName().toLowerCase(), newName);
        }
    }

    protected void setNameNeedsPersisting(Name name) {
        namesNeedPersisting.add(name);
    }

    protected void removeNameNeedsPersisting(Name name) {
        namesNeedPersisting.remove(name);
    }

    protected void addValueToDb(Value newValue) throws Exception {
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.get(newValue.getId()) != null) {
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            valueByIdMap.put(newValue.getId(), newValue);
        }
    }

    protected void setValueNeedsPersisting(Value value) {
        valuesNeedPersisting.add(value);
    }

    protected void removeValueNeedsPersisting(Value value) {
        valuesNeedPersisting.remove(value);
    }

    protected void addProvenanceToDb(Provenance newProvenance) throws Exception {
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.get(newProvenance.getId()) != null) {
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            provenanceByIdMap.put(newProvenance.getId(), newProvenance);
        }
    }

    protected void setProvenanceNeedsPersisting(Provenance provenance) {
        provenanceNeedsPersisting.add(provenance);
    }

    protected void removeProvenanceNeedsPersisting(Provenance provenance) {
        provenanceNeedsPersisting.remove(provenance);
    }

}