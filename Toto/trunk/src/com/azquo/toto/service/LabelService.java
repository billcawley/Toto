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
        // level -1 means get me the lowest
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this
        int currentLevel = 1;
        List<Label> foundAtCurrentLevel = labelDAO.findChildren(label);
        while ((currentLevel < level || level == -1) && !foundAtCurrentLevel.isEmpty()) {
            // we can't loop over foundAtCurrentLevel and modify it at the same time, this asks for trouble
            List<Label> nextLevelList = new ArrayList<Label>();
            for (Label l : foundAtCurrentLevel) {
                nextLevelList.addAll(labelDAO.findChildren(l));
            }
            if (nextLevelList.isEmpty() && level == -1) { // wanted the lowest, we've hit a level with none so don't go further
                break;
            }
            foundAtCurrentLevel = nextLevelList;
            currentLevel++;
        }
        return foundAtCurrentLevel;
    }

    public List<Label> findPeers(final Label label) throws Exception {
        return labelDAO.findChildren(label, true, LabelDAO.PEERSETDEFINITION);
    }

    public List<Label> findChildrenSorted(final Label label) throws Exception {
        return labelDAO.findChildren(label, true);
    }

    public List<Label> findChildrenFromTo(final Label label, final int from, final int to) throws Exception {
        return labelDAO.findChildren(label, from, to);
    }

    public List<Label> findChildrenFromTo(final Label label, final String from, final String to) throws Exception {
        System.out.println("from " + from);
        System.out.println("to " + to);
        // doing this in java, not sure SQL can do it
        List<Label> all = labelDAO.findChildren(label);
        List<Label> toReturn = new ArrayList<Label>();
        boolean okByFrom = false;
        if (from == null) {
            okByFrom = true;
        }
        for (Label child : all) {
            System.out.println("child `" + child.getName() + "`");
            if (!okByFrom && child.getName().equalsIgnoreCase(from)) {
                okByFrom = true;
            }
            if (okByFrom) {
                toReturn.add(child);
            }
            if (to != null && child.getName().equalsIgnoreCase(to)) { // we just hit the last one
                break;
            }
        }
        return toReturn;
    }

    public void createPeers(final Label parentLabel, final List<String> childNames) throws Exception {
        createMembers(parentLabel, childNames, LabelDAO.PEERSETDEFINITION);
    }

    public void createMembers(final Label parentLabel, final List<String> childNames) throws Exception {
        createMembers(parentLabel, childNames, LabelDAO.LABELSETDEFINITION);
    }

    private void createMembers(final Label parentLabel, final List<String> childNames, String setDefinitionTable) throws Exception {
        int position = labelDAO.getMaxChildPosition(parentLabel, setDefinitionTable);
        // by default we look for existing and create if we can't find them
        for (String childName : childNames) {
            position += 1;
            Label existingChild = labelDAO.findByName(childName);
            if (existingChild != null) {
                labelDAO.linkParentAndChild(parentLabel, existingChild, position, setDefinitionTable);
            } else {
                Label newChild = new Label(childName);
                labelDAO.store(newChild);
                labelDAO.linkParentAndChild(parentLabel, newChild, position, setDefinitionTable);
            }
        }
    }

    public void createMember(final Label parentLabel, final String childName, final String afterString, final int after) throws Exception {
        int position = labelDAO.getMaxChildPosition(parentLabel) + 1; // default to the end
        if (after != -1) { // int used before string should both be passed
            position = after + 1; // the actual insert point is the next ono since it's after that position :)
        } else if (afterString != null) {
            Label child = labelDAO.findByName(afterString);
            if (child != null) {
                int childPosition = labelDAO.getChildPosition(parentLabel, child);
                if (childPosition != -1) {
                    position = childPosition + 1;
                }
            }
        }
        // we look for existing and create if we can't find it
        Label existingChild = labelDAO.findByName(childName);
        if (existingChild != null) {
            labelDAO.linkParentAndChild(parentLabel, existingChild, position);
        } else {
            Label newChild = new Label(childName);
            labelDAO.store(newChild);
            labelDAO.linkParentAndChild(parentLabel, newChild, position);
        }
    }

    public void removeMember(final Label parentLabel, final String childName) throws Exception {
        Label existingChild = labelDAO.findByName(childName);
        if (existingChild != null) {
            labelDAO.unlinkParentAndChild(parentLabel, existingChild);
        }
    }

}
