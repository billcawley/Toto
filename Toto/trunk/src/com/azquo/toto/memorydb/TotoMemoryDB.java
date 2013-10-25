package com.azquo.toto.memorydb;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * OK, SQL isn't fast enough, it will be persistence but not much more. Need to think about how a Toto memory DB would work
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

    int maxLabelIdAtLoad;
    int maxValueIdAtLoad;

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
            List<Label> parentList = new ArrayList<Label>(parentIdsForThisLabel.size());
            for (Integer parentId: parentIdsForThisLabel){
                parentList.add(labelByIdMap.get(parentId));
                linkCounter++;
            }
            label.setParents(parentList);
        }

        System.out.println(linkCounter + " parent labels linked to labels " + (System.currentTimeMillis() - track) + "ms");
        track = System.currentTimeMillis();

        for (Label label : labelByIdMap.values()){
            List<Integer> childIdsForThisLabel = labelDAO.findChildIdsForLabel(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label);
            List<Label> childList = new ArrayList<Label>(childIdsForThisLabel.size());
            for (Integer childId: childIdsForThisLabel){
                childList.add(labelByIdMap.get(childId));
                linkCounter++;
            }
            label.setChildren(childList);
        }

        System.out.println(linkCounter + " child labels linked to labels " + (System.currentTimeMillis() - track) + "ms");
        track = System.currentTimeMillis();

        for (Label label : labelByIdMap.values()){
            List<Integer> peerIdsForThisLabel = labelDAO.findChildIdsForLabel(databaseName, LabelDAO.SetDefinitionTable.peer_set_definition, label);
            List<Label> peerList = new ArrayList<Label>(peerIdsForThisLabel.size());
            for (Integer peerId: peerIdsForThisLabel){
                peerList.add(labelByIdMap.get(peerId));
                linkCounter++;
            }
            label.setPeers(peerList);
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
            List<Value> valueList = new ArrayList<Value>(valueIdsForThisLabel.size());
            for (Integer valueId: valueIdsForThisLabel){
                valueList.add(valueByIdMap.get(valueId));
                linkCounter++;
            }
            label.setValues(valueList);
        }

        System.out.println(linkCounter + " values linked to labels " + (System.currentTimeMillis() - track) + "ms");
        track = System.currentTimeMillis();



        readyToGo = true;
    }

    public Label getLabelByName(String name){
        return labelByNameMap.get(name.toLowerCase());
    }

    public Label getLabelById(int id){
        return labelByIdMap.get(id);
    }

}
