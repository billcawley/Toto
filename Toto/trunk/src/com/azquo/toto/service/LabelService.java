package com.azquo.toto.service;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.entity.Label;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * I've been convinced that a service layer is probably a good idea, with the DAO/Database it can form the model.
 */
public class LabelService {

    @Autowired
    private LabelDAO labelDAO;

    public Label findByName(final String name) {
        return labelDAO.findByName(name);
    }

    public Label findOrCreateLabel(final String name) {
        Label existing = labelDAO.findByName(name);
        if (existing != null) {
            return existing;
        } else {
            Label l = new Label();
            l.setName(name);
            labelDAO.store(l);
            return l;
        }
    }

    public List<Label> findChildrenAtLevel(final Label label, final int level) throws Exception {
        int currentLevel = 1;
        List<Label> foundAtCurrentLevel = labelDAO.findChildren(label);
        while (currentLevel < level && !foundAtCurrentLevel.isEmpty()) {
            // we can't loop over foundAtCurrentLevel and modify it at the same time, this asks for trouble
            List<Label> nextLevelList = new ArrayList<Label>();
            for (Label l : foundAtCurrentLevel) {
                nextLevelList.addAll(labelDAO.findChildren(l));
            }
            foundAtCurrentLevel = nextLevelList;
            currentLevel++;
        }
        return foundAtCurrentLevel;
    }

    public void createMembers(final Label parentLabel, final List<String> childNames) throws Exception {
        int position = labelDAO.getMaxChildPosition(parentLabel);
        // by default we look for existing and create if we can't find them. Dealing with adjusting positions under these circumstances could be interesting
        for (String childName : childNames) {
            position += 1;
            Label existingChild = labelDAO.findByName(childName);
            if (existingChild != null) {
                labelDAO.linkParentAndChild(parentLabel, existingChild, position);
            } else {
                Label newChild = new Label(childName);
                labelDAO.store(newChild);
                labelDAO.linkParentAndChild(parentLabel, newChild, position);
            }
        }
    }
}
