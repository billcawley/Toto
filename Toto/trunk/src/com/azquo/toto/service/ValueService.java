package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
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

    public static String VALUE = "VALUE";

    public Value findByLabels(final ArrayList labels) {
        return new Value();
    }

    public String storeValueWithLabels(final Value value, final List<String> labelNames) throws Exception {
        String toReturn = "";
        List<Label> validLabels = new ArrayList<Label>();
        long track = System.currentTimeMillis();
        Map<String, String> labelCheckResult = labelService.isAValidLabelSet1(labelNames, validLabels);
        String error = labelCheckResult.get(LabelService.ERROR);
        String warning = labelCheckResult.get(LabelService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        System.out.println("track 1 : " + (System.currentTimeMillis() - track) + "  ---   ");
        track = System.currentTimeMillis();
        List<Value> existingValues = valueDAO.findForLabels2(databaseName, validLabels);

        for (Value existingValue : existingValues){
            valueDAO.setDeleted(databaseName,existingValue);
            valueDAO.unlinkValueFromAnyLabel(databaseName,existingValue);
            // provenance table : person, time, method, name
            toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
        }
        // now add the value??
        valueDAO.store(databaseName, value);
        System.out.println("track 2 : " + (System.currentTimeMillis() - track) + "  ---   ");
        track = System.currentTimeMillis();
        toReturn += "  stored";
        // and link to labels
        valueDAO.linkValueToLabels(databaseName, value, validLabels);
        System.out.println("track 3 : " + (System.currentTimeMillis() - track) + "  ---   ");

        return toReturn;
    }


}
