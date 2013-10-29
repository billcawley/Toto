package com.azquo.toto.memorydb;

import com.azquo.toto.dao.NameDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    private ValueDAO valueDAO;
    @Autowired
    private NameDAO nameDAO;
    @Autowired
    private ProvenanceDAO provenanceDAO;


    private HashMap<String, Name> nameByNameMap;
    private HashMap<Integer, Name> nameByIdMap;
    private HashMap<Integer, Value> valueByIdMap;
    private HashMap<Integer, Provenance> provenanceByIdMap;

    private boolean needsLoading;

    private String databaseName;

    private int maxIdAtLoad;
    private int nextId;

    // when objects are modified they are added to these lists

    private Set<Name> namesNeedPersisting;
    private Set<Value> valuesNeedPersisting;
    private Set<Provenance> provenanceNeedsPersisting;

    // convenience constructor, will be zapped later
    public TotoMemoryDB() throws Exception {
        this("toto");
    }

    public TotoMemoryDB(String databaseName) throws Exception {
        this.databaseName = databaseName;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByNameMap = new HashMap<String, Name>();
        nameByIdMap = new HashMap<Integer, Name>();
        valueByIdMap = new HashMap<Integer, Value>();
        provenanceByIdMap = new HashMap<Integer, Provenance>();
        namesNeedPersisting = new HashSet<Name>();
        valuesNeedPersisting = new HashSet<Value>();
        provenanceNeedsPersisting = new HashSet<Provenance>();
        nextId = maxIdAtLoad + 1;
    }

    public String getDatabaseName(){
        return databaseName;
    }

    public boolean getNeedsLoading(){
        return needsLoading;
    }

    // right now this will not run properly!

    synchronized protected void loadData() throws Exception{
        if (needsLoading){ // only allow it once!
            // here we'll populate the memory DB from the database
            long track = System.currentTimeMillis();

            // these 3 commands will automatically load teh data into the memory DB set as persisted

            List<Name> allNames = nameDAO.findAll(this);
            List<Value> allValues = valueDAO.findAll(this);
            List<Provenance> allProvenance = provenanceDAO.findAll(this);

            System.out.println(allNames.size() + allValues.size() + allProvenance.size() + " unlinked entities loaded in " + (System.currentTimeMillis() - track) + "ms");

            // now gotta link 'em

            track = System.currentTimeMillis();
            int linkCounter = 0;

            for (Name name : nameByIdMap.values()){
                List<Integer> childIdsForThisName = nameDAO.findChildIdsForName(this, NameDAO.SetDefinitionTable.name_set_definition, name);
                LinkedHashSet<Name> childSet = new LinkedHashSet<Name>(childIdsForThisName.size());
                for (Integer childId: childIdsForThisName){
                    childSet.add(nameByIdMap.get(childId));
                    linkCounter++;
                }
                // this function will now take care of the parents
                name.setChildrenWillBePersisted(childSet);
                if (name.getId() > maxIdAtLoad){
                    maxIdAtLoad = name.getId();
                }
            }

            System.out.println(linkCounter + " child.parent names linked " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();
            linkCounter = 0;

            for (Name name : nameByIdMap.values()){
                List<Integer> peerIdsForThisName = nameDAO.findChildIdsForName(this, NameDAO.SetDefinitionTable.peer_set_definition, name);
                LinkedHashSet<Name> peerSet = new LinkedHashSet<Name>(peerIdsForThisName.size());
                for (Integer peerId: peerIdsForThisName){
                    peerSet.add(nameByIdMap.get(peerId));
                    linkCounter++;
                }
                name.setPeersWillBePersisted(peerSet);
            }

            System.out.println(linkCounter + " peer names linked " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            linkCounter = 0;
            for (Value value : valueByIdMap.values()){
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
            }

            System.out.println(linkCounter + " values linked to names " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            // check provenance ids

            for (Provenance provenance : allProvenance){
                if (provenance.getId() > maxIdAtLoad){
                    maxIdAtLoad = provenance.getId();
                }
            }
            needsLoading = false;
        }
    }

    // reads from a list of changed objects

    public synchronized void saveDataToMySQL(){
        // need to go through each object and links I guess!

        // this is where I need to think carefully about concurrency, totodb has the last say when the maps are modified although the flags are another point
        // for the moment just make it work.
        System.out.println("nnp size : " +  namesNeedPersisting.size());
        for (Name name : new ArrayList<Name>(namesNeedPersisting)){
            if (name.getEntityColumnsChanged()){
                // store the name
                nameDAO.store(this, name);
            }
            int links = 0;
            if (name.getChildrenChanged()){
                int position = 1;
                nameDAO.unlinkAllForParent(this, NameDAO.SetDefinitionTable.name_set_definition,name);
                for (Name child : name.getChildren()){
                    nameDAO.linkParentAndChild(this, NameDAO.SetDefinitionTable.name_set_definition,name, child, position);
                    System.out.println("linking " + name + " child " + child);
                    position++;
                    links++;
                }
            }

            System.out.println(name.getName() + "children size : " + name.getChildren().size() + " links : " +  links);

            if (name.getPeersChanged()){
                int position = 1;
                nameDAO.unlinkAllForParent(this, NameDAO.SetDefinitionTable.peer_set_definition,name);
                for (Name peer : name.getPeers()){
                    nameDAO.linkParentAndChild(this, NameDAO.SetDefinitionTable.peer_set_definition,name, peer, position);
                    position++;
                }
            }
            name.setAsPersisted(); // is this dangerous here???
            // going to save value label links by value to label rather than label to value as the lists on the latter will be big. Change too one value may cause much relinking
            // we don't deal with parents, they're just convenience lookup lists
        }
        for (Value value : new ArrayList<Value>(valuesNeedPersisting)){
            if(value.getEntityColumnsChanged()){
                valueDAO.store(this, value);
            }

            if(value.getNamesChanged()){
                valueDAO.unlinkValueFromNames(this, value);
                valueDAO.linkValueToNames(this, value, value.getNames());
            }
            value.setAsPersisted();
        }
        for (Provenance  provenance : new ArrayList<Provenance>(provenanceNeedsPersisting)){
            if(provenance.getEntityColumnsChanged()){
                provenanceDAO.store(this, provenance);
            }
            provenance.setAsPersisted();
        }
/*        valuesNeedPersisting = new HashSet<Value>();
        provenanceNeedsPersisting = new HashSet<Provenance>();*/

    }

    protected synchronized int getNextId(){
        nextId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextId -1;
    }

    public Name getNameByName(String name){
        return nameByNameMap.get(name.toLowerCase());
    }

    public Name getNameById(int id){
        return nameByIdMap.get(id);
    }

    public Value getValueById(int id){
        return valueByIdMap.get(id);
    }

    public Provenance getProvenanceById(int id){
        return provenanceByIdMap.get(id);
    }

    // synchronised?

    protected void addNameToDb(Name newName) throws Exception{
        newName.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (nameByIdMap.get(newName.getId()) != null){
            throw new Exception("tried to add a name to the database with an existing id!");
        } else {
            nameByIdMap.put(newName.getId(), newName);
        }
    }

    // ok I'd have liked this to be part of the above function but the name won't have been initialised, has to be called in the name constructor
    // custom maps here need to be dealt with in the constructors I think

    protected void addNameToDbNameMap(Name newName) throws Exception{
        newName.checkDatabaseMatches(this);
        if (nameByNameMap.get(newName.getName().toLowerCase()) != null){
            throw new Exception("tried to add a name to the database with an existing name!");
        } else {
            nameByNameMap.put(newName.getName().toLowerCase(), newName);
        }
    }

    protected void setNameNeedsPersisting(Name name){
        namesNeedPersisting.add(name);
    }

    protected void removeNameNeedsPersisting(Name name){
        namesNeedPersisting.remove(name);
    }

    protected void addValueToDb(Value newValue) throws Exception{
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.get(newValue.getId()) != null){
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            valueByIdMap.put(newValue.getId(), newValue);
        }
    }

    protected void setValueNeedsPersisting(Value value){
        valuesNeedPersisting.add(value);
    }

    protected void removeValueNeedsPersisting(Value value){
        valuesNeedPersisting.remove(value);
    }

    protected void addProvenanceToDb(Provenance newProvenance) throws Exception{
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.get(newProvenance.getId()) != null){
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            provenanceByIdMap.put(newProvenance.getId(), newProvenance);
        }
    }

    protected void setProvenanceNeedsPersisting(Provenance provenance){
        provenanceNeedsPersisting.add(provenance);
    }

    protected void removeProvenanceNeedsPersisting(Provenance provenance){
        provenanceNeedsPersisting.remove(provenance);
    }

}