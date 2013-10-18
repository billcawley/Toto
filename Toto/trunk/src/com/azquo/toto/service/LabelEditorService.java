package com.azquo.toto.service;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.entity.Label;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * I've been convinced that a service layer is probably a good idea, with the DAO/Database it can form the model.
 */
public class LabelEditorService {

    @Autowired
    private LabelDAO labelDAO;

    public Label findByName(final String name){
        return labelDAO.findByName(name);
    }

    public void createMembers(final String parentLabelName, final List<String> childNames) throws Exception {
        Label parent = labelDAO.findByName(parentLabelName);
        if (parent == null){
            throw new Exception("label not found : " + parentLabelName);
        }
        int position = labelDAO.getMaxChildPosition(parent);
        // by default we look for existing and create if we can't find them. Dealing with adjusting positions under these circumstances could be interesting
        for(String childName : childNames) {
            position += 1;
            Label existingChild = labelDAO.findByName(childName);
            if (existingChild != null){
                labelDAO.linkParentAndChild(parent, existingChild, position);
            } else {
                Label newChild = new Label(childName);
                labelDAO.store(newChild);
            }
        }
    }
}
