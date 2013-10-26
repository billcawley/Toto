package com.azquo.toto.memorydb;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
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

    private boolean readyToGo;

    private String databaseName = "toto";

    private int maxLabelIdAtLoad;
    private int maxValueIdAtLoad;

    private int nextLabelId;
    private int nextValueId;

    public TotoMemoryDB(){
        boolean readyToGo = false;
        maxLabelIdAtLoad = 0;
        maxValueIdAtLoad = 0;
        labelByNameMap = new HashMap<String, Label>();
        labelByIdMap = new HashMap<Integer, Label>();
        valueByIdMap = new HashMap<Integer, Value>();
    }

    public void loadData(){
        // here we'll populate the memory DB from the database
        long track = System.currentTimeMillis();

        List<Label> allLabels = labelDAO.findAll(databaseName);

        for (Label label : allLabels){
            labelByNameMap.put(label.getName().toLowerCase(), label);
            labelByIdMap.put(label.getId(), label);
            if (label.getId() > maxLabelIdAtLoad){
                maxLabelIdAtLoad = label.getId();
            }
        }

        System.out.println(allLabels.size() + " labels loaded in " + (System.currentTimeMillis() - track) + "ms");
        track = System.currentTimeMillis();


        int linkCounter = 0;

        for (Label label : labelByIdMap.values()){
            List<Integer> parentIdsForThisLabel = labelDAO.findParentIdsForLabel(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label);
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
            List<Integer> childIdsForThisLabel = labelDAO.findChildIdsForLabel(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label);
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
            List<Integer> peerIdsForThisLabel = labelDAO.findChildIdsForLabel(databaseName, LabelDAO.SetDefinitionTable.peer_set_definition, label);
            LinkedHashSet<Label> peerSet = new LinkedHashSet<Label>(peerIdsForThisLabel.size());
            for (Integer peerId: peerIdsForThisLabel){
                peerSet.add(labelByIdMap.get(peerId));
                linkCounter++;
            }
            label.setPeersWillBePersisted(peerSet);
        }

        System.out.println(linkCounter + " peer labels linked to labels " + (System.currentTimeMillis() - track) + "ms");
        track = System.currentTimeMillis();


        List<Value> allValues = valueDAO.findAll(databaseName);

        for (Value value : allValues){
            valueByIdMap.put(value.getId(), value);
            if (value.getId() > maxValueIdAtLoad){
                maxValueIdAtLoad = value.getId();
            }
        }

        System.out.println(allValues.size() + " values loaded in " + (System.currentTimeMillis() - track) + "ms");
        track = System.currentTimeMillis();

        linkCounter = 0;
        for (Label label : labelByIdMap.values()){
            List<Integer> valueIdsForThisLabel = valueDAO.findValueIdsForLabel(databaseName,label);
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

        nextLabelId = maxLabelIdAtLoad + 1;
        nextValueId = maxValueIdAtLoad + 1;

        readyToGo = true;
    }

    private synchronized int getNextLabelId(){
        nextLabelId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextLabelId -1;
    }

    private synchronized int getNextValueId(){
        nextValueId++; // increment but return what it was . . .a little messy but I want tat value in memory to be what it says
        return nextValueId -1;
    }

    public Label getLabelByName(String name){
        return labelByNameMap.get(name.toLowerCase());
    }

    public Label getLabelById(int id){
        return labelByIdMap.get(id);
    }

    public synchronized Label createLabel(String name){
        Label newLabel = new Label(getNextLabelId(), name);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        labelByNameMap.put(newLabel.getName().toLowerCase(), newLabel);
        labelByIdMap.put(newLabel.getId(), newLabel);
        return newLabel;
    }

    public synchronized Value createValue(int provenanceId, Value.Type type, int intValue, double doubleValue, String varChar, String text, Date timeStamp, boolean deleted){
        Value newValue = new Value(getNextValueId(),provenanceId, type, intValue, doubleValue, varChar, text, timeStamp, deleted);
        // add it to the memory database, this means it's in line for proper persistence (the ID map is considered reference)
        valueByIdMap.put(newValue.getId(), newValue);
        return newValue;
    }

}