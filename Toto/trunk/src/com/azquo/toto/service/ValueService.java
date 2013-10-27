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
 * Workhorse hammering away at the memory DB. Will later be used in context of a toto session - instead we'll just
 * have a memory db here for the moment
 */
public class ValueService {

    public static String VALUE = "VALUE";

    @Autowired
    private LabelService labelService;

    @Autowired
    private TotoMemoryDB totoMemoryDB;

    // set the labels in delete info and unlink - best I can come up with at the moment
    public void deleteValue(Value value) throws Exception {
        String labelNames = "";
        for (Label l : value.getLabels()){
            labelNames += ", `" + l.getName() + "`";
        }
        if (labelNames.length() > 0){
            labelNames = labelNames.substring(2);
        }
        value.setDeletedInfoWillBePersisted(labelNames);
        unlinkAllLabelsFromValue(value);
    }

    public void unlinkAllLabelsFromValue(Value value) throws Exception {
        for (Label label : value.getLabels()){
            label.removeFromValuesWillBePersisted(value);
        }
        value.setLabelsWillBePersisted(new HashSet<Label>()); // zap the labels against this value.
    }

    public Value createValue(int provenanceId, double doubleValue, String text) throws Exception {
        // TODO : provenance
//        return totoMemoryDB.createValue(provenanceId,doubleValue,text);
        return new Value(totoMemoryDB,provenanceId,doubleValue,text,null);
    }

    public void linkValueToLabels(Value value, Set<Label> labels) throws Exception {
        unlinkAllLabelsFromValue(value);
        value.setLabelsWillBePersisted(labels);
        for (Label label : labels){
            long track = System.nanoTime();
            label.addToValuesWillBePersisted(value);
            //System.out.println("linkValueToLabels loop1 " + (track - System.nanoTime()));
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
        //System.out.println("track 1   : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        List<Value> existingValues = findForLabels(validLabels);
        //System.out.println("track 2-1 : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();

        for (Value existingValue : existingValues){
            deleteValue(existingValue);
            // provenance table : person, time, method, name
            toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
        }

        //System.out.println("track 2-2 : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        Value value = createValue(0, 0,valueString);
        // now add the value??
        //System.out.println("track 2-3 : " + (System.nanoTime() - track) + "  ---   ");
        track = System.nanoTime();
        toReturn += "  stored";
        // and link to labels
        linkValueToLabels(value, validLabels);
        //valueDAO.linkValueToLabels(databaseName, value, validLabels);
        //System.out.println("track 3   : " + (System.nanoTime() - track) + "  ---   ");

        return toReturn;
    }

    public List<Value> findForLabels(Set<Label> labels){
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        long track = System.nanoTime();
        List<Value> values = new ArrayList<Value>();
        // first get the shortest value list
        int smallestLabelSetSize = -1;
        Label smallestLabel = null;
        for (Label label : labels){
            if (smallestLabelSetSize == -1 || label.getValues().size() < smallestLabelSetSize){
                smallestLabelSetSize = label.getValues().size();
                smallestLabel = label;
            }
        }

        //System.out.println("track a   : " + (System.nanoTime() - track) + "  ---   ");
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

        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " labels");
        track = System.nanoTime();

        return values;
    }


}
