package com.azquo.toto.service;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.entity.Label;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * I've been convinced that a service layer is probably a good idea, with the DAO/Database it can form the model.
 * <p/>
 * THis does the grunt work for label manipulation, might not need to be that quick.
 */
public class LabelService {

    String databaseName = "toto"; // hard code here for the moment

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    @Autowired
    private LabelDAO labelDAO;

    public Label findByName(final String name) {
        return labelDAO.findByName(databaseName, name);
    }

    public Label findOrCreateLabel(final String name) {
        Label existing = labelDAO.findByName(databaseName, name);
        if (existing != null) {
            return existing;
        } else {
            Label l = new Label();
            l.setName(name);
            labelDAO.store(databaseName, l);
            return l;
        }
    }

    public List<Label> findChildrenAtLevel(final Label label, final int level) throws Exception {
        // level -1 means get me the lowest
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this
        int currentLevel = 1;
        List<Label> foundAtCurrentLevel = labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label, false);
        while ((currentLevel < level || level == -1) && !foundAtCurrentLevel.isEmpty()) {
            // we can't loop over foundAtCurrentLevel and modify it at the same time, this asks for trouble
            List<Label> nextLevelList = new ArrayList<Label>();
            for (Label l : foundAtCurrentLevel) {
                nextLevelList.addAll(labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l, false));
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
        return labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.peer_set_definition, label, true);
    }

    public List<Label> findChildrenSorted(final Label label) throws Exception {
        return labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label, true);
    }

    public List<Label> findChildrenFromTo(final Label label, final int from, final int to) throws Exception {
        return labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label, from, to);
    }

    public List<Label> findChildrenFromTo(final Label label, final String from, final String to) throws Exception {
        System.out.println("from " + from);
        System.out.println("to " + to);
        // doing this in java, not sure SQL can do it
        List<Label> all = labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label, false);
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
        createMembers(parentLabel, childNames, LabelDAO.SetDefinitionTable.peer_set_definition);
    }

    public void createMembers(final Label parentLabel, final List<String> childNames) throws Exception {
        createMembers(parentLabel, childNames, LabelDAO.SetDefinitionTable.label_set_definition);
    }

    private void createMembers(final Label parentLabel, final List<String> childNames, LabelDAO.SetDefinitionTable setDefinitionTable) throws Exception {
        int position = labelDAO.getMaxChildPosition(databaseName, setDefinitionTable, parentLabel);
        // by default we look for existing and create if we can't find them
        for (String childName : childNames) {
            if (childName.trim().length() > 0) {
                position += 1;
                Label existingChild = labelDAO.findByName(databaseName, childName);
                if (existingChild != null) {
                    labelDAO.linkParentAndChild(databaseName, setDefinitionTable, parentLabel, existingChild, position);
                } else {
                    Label newChild = new Label(childName);
                    labelDAO.store(databaseName, newChild);
                    labelDAO.linkParentAndChild(databaseName, setDefinitionTable, parentLabel, newChild, position);
                }
            }
        }
    }
    // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent labels with peer lists

    public void createPeer(final Label parentLabel, final String peerName) throws Exception {
        createMember(parentLabel, peerName, null, -1, LabelDAO.SetDefinitionTable.peer_set_definition);
    }

    public void createPeer(final Label parentLabel, final String peerName, final String afterString, final int after) throws Exception {
        createMember(parentLabel, peerName, afterString, after, LabelDAO.SetDefinitionTable.peer_set_definition);
    }

    public void createMember(final Label parentLabel, final String childName, final String afterString, final int after) throws Exception {
        createMember(parentLabel, childName, afterString, after, LabelDAO.SetDefinitionTable.label_set_definition);
    }

    public void createMember(final Label parentLabel, final String childName, final String afterString, final int after, LabelDAO.SetDefinitionTable setDefinitionTable) throws Exception {
        if (childName.trim().length() > 0) {
            int position = labelDAO.getMaxChildPosition(databaseName, setDefinitionTable, parentLabel) + 1; // default to the end
            if (after != -1) { // int used before string should both be passed
                position = after + 1; // the actual insert point is the next ono since it's after that position :)
            } else if (afterString != null) {
                Label child = labelDAO.findByName(databaseName, afterString);
                if (child != null) {
                    int childPosition = labelDAO.getChildPosition(databaseName, setDefinitionTable, parentLabel, child);
                    if (childPosition != -1) {
                        position = childPosition + 1;
                    }
                }
            }
            // we look for existing and create if we can't find it
            Label existingChild = labelDAO.findByName(databaseName, childName);
            if (existingChild != null) {
                labelDAO.linkParentAndChild(databaseName, setDefinitionTable, parentLabel, existingChild, position);
            } else {
                Label newChild = new Label(childName);
                labelDAO.store(databaseName, newChild);
                labelDAO.linkParentAndChild(databaseName, setDefinitionTable, parentLabel, newChild, position);
            }
        }
    }

    public void removePeer(final Label parentLabel, final String childName) throws Exception {
        Label existingChild = labelDAO.findByName(databaseName, childName);
        if (existingChild != null) {
            labelDAO.unlinkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.peer_set_definition, parentLabel, existingChild);
        }
    }

    public void removeMember(final Label parentLabel, final String childName) throws Exception {
        Label existingChild = labelDAO.findByName(databaseName, childName);
        if (existingChild != null) {
            labelDAO.unlinkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, parentLabel, existingChild);
        }
    }

    public void renameLabel(String labelName, String renameAs) {
        Label existing = labelDAO.findByName(databaseName, labelName);
        if (existing != null) {
            existing.setName(renameAs);
            labelDAO.store(databaseName, existing);
        }
    }

    // these should probably live somewhere more global
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    public Map<String, String> isAValidLabelSet(List<String> labelNames, List<Label> validLabelList) throws Exception {


        Map<String, String> toReturn = new HashMap<String, String>();

        String error = "";
        String warning = "";

        ArrayList<Label> hasPeers = new ArrayList<Label>(); // the labels (oor their parents) in this list which have peer requirements, should only be one
        ArrayList<Label> labelsToCheck = new ArrayList<Label>();

        for (String labelName : labelNames) {
            Label label = findByName(labelName);
            if (label == null) {
                error += "  I can't find the label : " + labelName;
            } else { // the label exists . . .
                boolean thisLabelHasPeers = false;
                if (!findPeers(label).isEmpty()) { // this label is the one that defines what labels the data will require
                    hasPeers.add(label);
                    thisLabelHasPeers = true;
                } else { // try looking up the chain and find the first with peers
                    List<Label> parents = labelDAO.findAllParents(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label);
                    for (Label parent : parents){
                        if (!findPeers(parent).isEmpty()) { // this label is the one that defines what labels the data will require
                            hasPeers.add(parent); // put the parent not the actual label in as it will be used to determine the criteria for this value
                            thisLabelHasPeers = true;
                            break;
                        }
                    }
                }
                // it wasn't a label with peers hence it's on the list of labels to match up to the peer list of the label that DOES have peers :)
                // not adding the label with peers to labelsToCheck is more efficient and it stops the label with peers from showing up as being superfluous to the peer list if that makes sense
                if (!thisLabelHasPeers){
                    labelsToCheck.add(label);
                }
            }
        }


        if (hasPeers.isEmpty()) {
            error += "  none of the labels passed have peers, I don't know what labels are required for this value";
        } else if (hasPeers.size() > 1) {
            error += "  more than one label passed has peers ";
            for (Label has : hasPeers) {
                error += has.getName() + ", ";
            }
            error += "I don't know what labels are required for this value";
        } else { // one set of peers, ok :)
            // match peersm child labels are ok, ignore extra labels, warn about this
            List<Label> requiredPeers = findPeers(hasPeers.get(0));
            validLabelList.add(hasPeers.get(0)); // the rest will be added below but we need to add this here as the peer defining label is not on the list of peers
            for (Label requiredPeer : requiredPeers) {
                boolean found = false;
                // do a first direct pass
                for (Label labelToCheck : labelsToCheck) {
                    if (labelToCheck.getName().equalsIgnoreCase(requiredPeer.getName())) { // we found it
                        labelsToCheck.remove(labelToCheck); // skip to the next one and remove the label from labels to check and add it to the validated list to return
                        validLabelList.add(labelToCheck);
                        found = true;
                        break;
                    }
                }

                if (!found) { // couldn't find this peer, need to look up through parents of each label for the peer
                    for (Label labelToCheck : labelsToCheck) {
                        List<Label> allParents = labelDAO.findAllParents(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, labelToCheck);
                        for (Label parent : allParents) {
                            if (parent.getName().equalsIgnoreCase(requiredPeer.getName())) { // we found it
                                labelsToCheck.remove(labelToCheck); // one of its parents matched so this peer is matched, skip to the next one and remove the label from labels to check
                                validLabelList.add(labelToCheck);
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                }

                if (!found) {
                    error += "  I can't find a required peer : " + requiredPeer.getName() + " among the labels";
                }
            }

            if (labelsToCheck.size() > 0) { // means they were not used by the required peers, issue a warning
                for (Label labelToCheck : labelsToCheck) {
                    warning += "  additional label not required by peers " + labelToCheck.getName();
                }
            }
        }

        if (error.length() > 0) {
            toReturn.put(ERROR, error);
        }
        if (warning.length() > 0) {
            toReturn.put(WARNING, error);
        }
        return toReturn;
    }

    public void logLabelHierarchy(Label label, int level) {
        for (int i = 1; i <= level; i++) {
            System.out.print("- ");
        }
        System.out.println(label.getName());
        List<Label> children = labelDAO.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, label, false);
        if (!children.isEmpty()) {
            level++;
            for (Label child : children) {
                logLabelHierarchy(child, level);
            }
        }
    }
}
