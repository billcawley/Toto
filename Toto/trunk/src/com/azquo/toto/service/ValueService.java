package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 16:49
 * Value service which will heavily use the ValueDAO. Depending on usage may need to be split up later.
 */
public class ValueService {

    String databaseName = "toto"; // hard code here for the moment

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public static String VALUE = "VALUE";

    @Autowired
    private LabelService labelService;

    @Autowired
    private TotoMemoryDB totoMemoryDB;

    // set the value as deleted and unlink? I assume unlink but I'm not 100% - maybe don't unlink but don't reload from the database?
    // For the moment unlink and delete
    public void deleteValue(Value value){
        value.setDeletedWillBePersisted(true);
        unlinkAllLabelsFromValue(value);
    }

    public void unlinkAllLabelsFromValue(Value value){
        for (Label label : value.getLabels()){
            Set<Value> linksOnLabelToChange = new HashSet<Value>(label.getValues()); // copy so we can modify
            if (linksOnLabelToChange.remove(value)){ // it was removed from the label links
                label.setValuesWillBePersisted(linksOnLabelToChange); // so set the list back on the label
            }
        }
        value.setLabelsWillBePersisted(new HashSet<Label>()); // zap the labels against this value.
    }

    public Value createValue(int provenanceId, Value.Type type, int intValue, double doubleValue, String varChar, String text, Date timeStamp){
        // TODO : provenance
        return totoMemoryDB.createValue(provenanceId,type, intValue,doubleValue,varChar,text,timeStamp,false);
    }

    public void linkValueToLabels(Value value, Set<Label> labels){
        unlinkAllLabelsFromValue(value);
        value.setLabelsWillBePersisted(labels);
        for (Label label : labels){
            Set<Value> linksOnLabelToChange = new HashSet<Value>(label.getValues());
            if (linksOnLabelToChange.add(value)){ // only set it back if we changed something
                label.setValuesWillBePersisted(linksOnLabelToChange); // add to each label's value lists
            }
        }
    }

    public String storeValueWithLabels(final String valueString, final Set<String> labelNames) throws Exception {
        String toReturn = "";
        Set<Label> validLabels = new HashSet<Label>();
        long track = System.nanoTime();
        Map<String, String> labelCheckResult = labelService.isAValidLabelSet(labelNames, validLabels);
        String error = labelCheckResult.get(LabelService.ERROR);
        String warning = labelCheckResult.get(LabelService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        System.out.println("track 1   : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        Set<Value> existingValues = findForLabels(validLabels);
        System.out.println("track 2-1 : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();

        for (Value existingValue : existingValues){
            deleteValue(existingValue);
            // provenance table : person, time, method, name
            toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
        }

        System.out.println("track 2-2 : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        Value value = createValue(0, Value.Type.VARCHAR,0,0,valueString,null,new Date());
        // now add the value??
        System.out.println("track 2-3 : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        toReturn += "  stored";
        // and link to labels
        linkValueToLabels(value, validLabels);
        //valueDAO.linkValueToLabels(databaseName, value, validLabels);
        System.out.println("track 3   : " + (System.nanoTime() - track) + "  ---   ");

        return toReturn;
    }

    public Set<Value> findForLabels(Set<Label> labels){
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        long track = System.nanoTime();
        Set<Value> values = new HashSet<Value>();
        // first get the shortest value list
        int smallestLabelSetSize = -1;
        Label smallestLabel = null;
        for (Label label : labels){
            if (smallestLabelSetSize == -1 || label.getValues().size() < smallestLabelSetSize){
                smallestLabelSetSize = label.getValues().size();
                smallestLabel = label;
            }
        }

        System.out.println("track a   : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        // changing to sets for speed (hopefully!)
        int count = 0;


        assert smallestLabel != null; // make intellij happy :P
        for (Value value : smallestLabel.getValues()){
            boolean theValueIsOk = true;
            for (Label label : labels){
                if (!label.equals(smallestLabel)){ // ignore the one we started with
                    /*
                    boolean foundInThisLabel = false;
                    for(Value valueInThisLabel : label.getValues()){
                        if (valueInThisLabel.equals(value)){
                            count++;
                            foundInThisLabel = true;
                            break; // don't keep looking through those values, we found it
                        }
                    }
                    if (!foundInThisLabel){ // then there's no point looking at further labels
                        theValueIsOk = false;
                        break;
                    }
                    */
                    // option b, let's see what we can do with set functions
                    if (!value.getLabels().contains(label)){
                        count++;
                        theValueIsOk = false;
                        break; // important, stop checking that that value contains he labels we're interested in as, we didn't find one no point checking for the rest
                    }
                }
            }
            if (theValueIsOk){ // it was in all the labels :)
                values.add(value);
            }
        }

        System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " labels");
        track = System.nanoTime();

        return values;
    }


}
