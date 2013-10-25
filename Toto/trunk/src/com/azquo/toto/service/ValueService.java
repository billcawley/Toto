package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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


    @Autowired
    private ValueDAO valueDAO;

    @Autowired
    private LabelService labelService;

    @Autowired
    private TotoMemoryDB totoMemoryDB;

    public static String VALUE = "VALUE";

    public Value findByLabels(final ArrayList labels) {
        return new Value();
    }

    public String storeValueWithLabels(final Value value, final List<String> labelNames) throws Exception {
        String toReturn = "";
        List<Label> validLabels = new ArrayList<Label>();
        long track = System.currentTimeMillis();
        Map<String, String> labelCheckResult = labelService.isAValidLabelSet(labelNames, validLabels);
        String error = labelCheckResult.get(LabelService.ERROR);
        String warning = labelCheckResult.get(LabelService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        System.out.println("track 1 : " + (System.currentTimeMillis() - track) + "  ---   ");
        track = System.currentTimeMillis();
        List<Value> existingValues = findForLabels(validLabels);

/*        for (Value existingValue : existingValues){
            valueDAO.setDeleted(databaseName,existingValue);
            valueDAO.unlinkValueFromAnyLabel(databaseName,existingValue);
            // provenance table : person, time, method, name
            toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
        }
        // now add the value??
        valueDAO.store(databaseName, value);*/
        System.out.println("track 2 : " + (System.currentTimeMillis() - track) + "  ---   ");
        /*track = System.currentTimeMillis();
        toReturn += "  stored";
        // and link to labels
        valueDAO.linkValueToLabels(databaseName, value, validLabels);
        System.out.println("track 3 : " + (System.currentTimeMillis() - track) + "  ---   ");
*/
        return toReturn;
    }

    public List<Value> findForLabels(List<Label> labels){
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        ArrayList<Value> values = new ArrayList<Value>();
        // first get the shortest value list
        int smallestLabelSetSize = -1;
        Label smallestLabel = null;
        for (Label label : labels){
            if (smallestLabelSetSize == -1 || label.getValues().size() < smallestLabelSetSize){
                smallestLabelSetSize = label.getValues().size();
                smallestLabel = label;
            }
        }

        // sod it, just go through that set and any which are found in the others add to be returned, Hashmaps could really speed this up later
        // but will be interesting to see the speed now :)

        for (Value value : smallestLabel.getValues()){
            int numberOfLabelsTheValueHasBeenFoundIn = 1; // the label we started with :)
            for (Label label : labels){
                if (!label.equals(smallestLabel)){ // ignore the one we started with
                    for(Value valueInThisLabel : label.getValues()){
                        if (valueInThisLabel.equals(value)){
                            numberOfLabelsTheValueHasBeenFoundIn++;
                            break; // don't keep looking through those values, we found it
                        }
                    }
                }
            }
            if (numberOfLabelsTheValueHasBeenFoundIn == labels.size()){ // it was in all the labels :)
                values.add(value);
            }
        }


        return values;
    }


}
