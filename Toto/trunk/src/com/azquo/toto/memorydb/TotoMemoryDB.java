package com.azquo.toto.memorydb;

import com.azquo.toto.dao.NameDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Name;
import com.azquo.toto.entity.Provenance;
import com.azquo.toto.entity.Value;
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
public class TotoMemoryDB {

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


    // convenience constructor, will be zapped later
    public TotoMemoryDB() throws Exception {
        this("toto", true);
    }

    public TotoMemoryDB(String databaseName, boolean dontLoad) throws Exception {
        this.databaseName = databaseName;
        needsLoading = true;
        maxIdAtLoad = 0;
        nameByNameMap = new HashMap<String, Name>();
        nameByIdMap = new HashMap<Integer, Name>();
        valueByIdMap = new HashMap<Integer, Value>();
        if (dontLoad){
            needsLoading = false;
        } else {
            loadData();
        }
    }

    public String getDatabaseName(){
        return databaseName;
    }

    public boolean getNeedsLoading(){
        return needsLoading;
    }

    // right now this will not run properly!

    synchronized public void loadData() throws Exception{
        if (needsLoading){ // only allow it once!
            // here we'll populate the memory DB from the database
            long track = System.currentTimeMillis();

            List<Name> allNames = nameDAO.findAll(this);

            for (Name name : allNames){
                nameByNameMap.put(name.getName().toLowerCase(), name);
                nameByIdMap.put(name.getId(), name);
                if (name.getId() > maxIdAtLoad){
                    maxIdAtLoad = name.getId();
                }
            }

            System.out.println(allNames.size() + " names loaded in " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();


            int linkCounter = 0;

            for (Name name : nameByIdMap.values()){
                List<Integer> parentIdsForThisName = nameDAO.findParentIdsForName(this, NameDAO.SetDefinitionTable.name_set_definition, name);
                Set<Name> parentSet = new HashSet<Name>(parentIdsForThisName.size());
                for (Integer parentId: parentIdsForThisName){
                    parentSet.add(nameByIdMap.get(parentId));
                }
                name.setParentsWillBePersisted(parentSet);
            }

            System.out.println(linkCounter + " parent names linked to names " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            linkCounter = 0;
            for (Name name : nameByIdMap.values()){
                List<Integer> childIdsForThisName = nameDAO.findChildIdsForName(this, NameDAO.SetDefinitionTable.name_set_definition, name);
                LinkedHashSet<Name> childSet = new LinkedHashSet<Name>(childIdsForThisName.size());
                for (Integer childId: childIdsForThisName){
                    childSet.add(nameByIdMap.get(childId));
                    linkCounter++;
                }
                name.setChildrenWillBePersisted(childSet);
            }

            System.out.println(linkCounter + " child names linked to names " + (System.currentTimeMillis() - track) + "ms");
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

            System.out.println(linkCounter + " peer names linked to names " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();


            List<Value> allValues = valueDAO.findAll(this);

            for (Value value : allValues){
                valueByIdMap.put(value.getId(), value);
                if (value.getId() > maxIdAtLoad){
                    maxIdAtLoad = value.getId();
                }
            }

            System.out.println(allValues.size() + " values loaded in " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            linkCounter = 0;
            for (Name name : nameByIdMap.values()){
                List<Integer> valueIdsForThisName = valueDAO.findValueIdsForName(this, name);
                Set<Value> valueSet = new HashSet<Value>(valueIdsForThisName.size());
                for (Integer valueId: valueIdsForThisName){
                    valueSet.add(valueByIdMap.get(valueId));
                    linkCounter++;
                }
                name.setValuesWillBePersisted(valueSet);
            }

            System.out.println(linkCounter + " values linked to names " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            // tell all objects that they're up to date as we just loaded 'em
            for (Name name : nameByIdMap.values()){
                name.syncedToDB();
            }
            for (Value value : valueByIdMap.values()){
                value.syncedToDB();
            }

            nextId = maxIdAtLoad + 1;

            needsLoading = false;
        }
    }

    public synchronized int getNextId(){
        nextId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextId -1;
    }

    public Name getNameByName(String name){
        return nameByNameMap.get(name.toLowerCase());
    }

    public Name getNameById(int id){
        return nameByIdMap.get(id);
    }

    // synchronised?

    public void addNameToDb(Name newName) throws Exception{
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

    public void addNameToDbNameMap(Name newName) throws Exception{
        newName.checkDatabaseMatches(this);
        if (nameByNameMap.get(newName.getName().toLowerCase()) != null){
            throw new Exception("tried to add a name to the database with an existing name!");
        } else {
            nameByNameMap.put(newName.getName().toLowerCase(), newName);
        }
    }

    public void addValueToDb(Value newValue) throws Exception{
        newValue.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (valueByIdMap.get(newValue.getId()) != null){
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            valueByIdMap.put(newValue.getId(), newValue);
        }
    }

    public void addProvenanceToDb(Provenance newProvenance) throws Exception{
        newProvenance.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (provenanceByIdMap.get(newProvenance.getId()) != null){
            throw new Exception("tried to add a value to the database with an existing id!");
        } else {
            provenanceByIdMap.put(newProvenance.getId(), newProvenance);
        }
    }

}