package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 16:49
 * Value service which will heavily use the ValueDAO. Depending on usage may need to be split up later.
 */
public class ValueService {
    String databaseName = "toto1"; // hard code here for the moment

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    @Autowired
    private ValueDAO valueDAO;

    @Autowired
    private LabelService labelService;

    public Value findByLabels(final ArrayList labels) {
        return new Value();
    }

    public String storeValueWithLabels(final Value value, final List<String> labelNames) throws Exception {
        String toReturn = "";
        String labelCheckResult = labelService.isAValidLabelSet(labelNames);
        // here's a question : if there are superfluous labels do we still store them??
        if (labelCheckResult.startsWith("true")){
            toReturn += labelCheckResult;
        } else {
            return  labelCheckResult;
        }

        // the labels should be viable, we need an array for this function. This could perhaps be optimised later.
        List<Label> labels = new ArrayList<Label>();
        for (String labelName : labelNames){
            labels.add(labelService.findByName(labelName));
        }

        List<Value> existingValues = valueDAO.findForLabels(databaseName, labels);

        for (Value existingValue : existingValues){
            valueDAO.setDeleted(databaseName,existingValue);
            valueDAO.unlinkValueFromAnyLabel(databaseName,existingValue);
            // provenance table : person, time, method, name
            toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
        }

        // now add the value??
        valueDAO.store(databaseName, value);
        toReturn += "  stored";
        // and link to labels
        for (Label label : labels){
            valueDAO.linkValueToLabel(databaseName, value, label);
            toReturn += "  linked to " + label.getName();
        }

        return toReturn;
    }


}
