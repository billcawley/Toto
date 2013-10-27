package com.azquo.toto.memorydb;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
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
    private LabelDAO labelDAO;
    @Autowired
    private ProvenanceDAO provenanceDAO;


    private HashMap<String, Label> labelByNameMap;
    private HashMap<Integer, Label> labelByIdMap;
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
        boolean needsLoading = true;
        maxIdAtLoad = 0;
        labelByNameMap = new HashMap<String, Label>();
        labelByIdMap = new HashMap<Integer, Label>();
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

    synchronized public void loadData() throws Exception{
        if (needsLoading){ // only allow it once!
            // here we'll populate the memory DB from the database
            long track = System.currentTimeMillis();

            List<Label> allLabels = labelDAO.findAll(this);

            for (Label label : allLabels){
                labelByNameMap.put(label.getName().toLowerCase(), label);
                labelByIdMap.put(label.getId(), label);
                if (label.getId() > maxIdAtLoad){
                    maxIdAtLoad = label.getId();
                }
            }

            System.out.println(allLabels.size() + " labels loaded in " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();


            int linkCounter = 0;

            for (Label label : labelByIdMap.values()){
                List<Integer> parentIdsForThisLabel = labelDAO.findParentIdsForLabel(this, LabelDAO.SetDefinitionTable.label_set_definition, label);
                Set<Label> parentSet = new HashSet<Label>(parentIdsForThisLabel.size());
                for (Integer parentId: parentIdsForThisLabel){
                    parentSet.add(labelByIdMap.get(parentId));
                }
                label.setParentsWillBePersisted(parentSet);
            }

            System.out.println(linkCounter + " parent labels linked to labels " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            linkCounter = 0;
            for (Label label : labelByIdMap.values()){
                List<Integer> childIdsForThisLabel = labelDAO.findChildIdsForLabel(this, LabelDAO.SetDefinitionTable.label_set_definition, label);
                LinkedHashSet<Label> childSet = new LinkedHashSet<Label>(childIdsForThisLabel.size());
                for (Integer childId: childIdsForThisLabel){
                    childSet.add(labelByIdMap.get(childId));
                    linkCounter++;
                }
                label.setChildrenWillBePersisted(childSet);
            }

            System.out.println(linkCounter + " child labels linked to labels " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();
            linkCounter = 0;

            for (Label label : labelByIdMap.values()){
                List<Integer> peerIdsForThisLabel = labelDAO.findChildIdsForLabel(this, LabelDAO.SetDefinitionTable.peer_set_definition, label);
                LinkedHashSet<Label> peerSet = new LinkedHashSet<Label>(peerIdsForThisLabel.size());
                for (Integer peerId: peerIdsForThisLabel){
                    peerSet.add(labelByIdMap.get(peerId));
                    linkCounter++;
                }
                label.setPeersWillBePersisted(peerSet);
            }

            System.out.println(linkCounter + " peer labels linked to labels " + (System.currentTimeMillis() - track) + "ms");
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
            for (Label label : labelByIdMap.values()){
                List<Integer> valueIdsForThisLabel = valueDAO.findValueIdsForLabel(this,label);
                Set<Value> valueSet = new HashSet<Value>(valueIdsForThisLabel.size());
                for (Integer valueId: valueIdsForThisLabel){
                    valueSet.add(valueByIdMap.get(valueId));
                    linkCounter++;
                }
                label.setValuesWillBePersisted(valueSet);
            }

            System.out.println(linkCounter + " values linked to labels " + (System.currentTimeMillis() - track) + "ms");
            track = System.currentTimeMillis();

            // tell all objects that they're up to date as we just loaded 'em
            for (Label label : labelByIdMap.values()){
                label.syncedToDB();
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

    public Label getLabelByName(String name){
        return labelByNameMap.get(name.toLowerCase());
    }

    public Label getLabelById(int id){
        return labelByIdMap.get(id);
    }

    // synchronised?

    public void addLabelToDb(Label newLabel) throws Exception{
        newLabel.checkDatabaseMatches(this);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        if (labelByIdMap.get(newLabel.getId()) != null){
            throw new Exception("tried to add a label to the database with an existing id!");
        } else {
            labelByIdMap.put(newLabel.getId(), newLabel);
        }
    }

    // ok I'd have liked this to be part of the above function but the name won't have been initialised, has to be called in the label constructor
    // custom maps here need to be dealt with in the constructors I think

    public void addLabelToDbNameMap(Label newLabel) throws Exception{
        newLabel.checkDatabaseMatches(this);
        if (labelByNameMap.get(newLabel.getName().toLowerCase()) != null){
            throw new Exception("tried to add a label to the database with an existing name!");
        } else {
            labelByNameMap.put(newLabel.getName().toLowerCase(), newLabel);
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