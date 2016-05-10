package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.jsonentities.JsonChildStructure;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.jsonentities.NameJsonRequest;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 13/05/15.
 * <p>
 * All JSTree code that deals with DB objects to go in here.
 * <p>
 * It had controller like code in here but this has been moved out. Might take a few more passes before the representation is up to scratch.
 */
public class JSTreeService {

    @Autowired
    DSSpreadsheetService dsSpreadsheetService; // to get the connection
    @Autowired
    NameService nameService;

    public Set<Name> interpretNameFromStrings(DatabaseAccessToken databaseAccessToken, Set<String> nameStrings) throws Exception {
        Set<Name> names = new HashSet<>();
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        for (String nString : nameStrings) {
            Name name = nameService.findByName(azquoMemoryDBConnection, nString);
            if (name != null) names.add(name);
        }
        return names;
    }

    public Set<Name> interpretNameFromIds(DatabaseAccessToken databaseAccessToken, Set<Integer> ids) throws Exception {
        Set<Name> names = new HashSet<>();
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        for (int id : ids) {
            Name name = nameService.findById(azquoMemoryDBConnection, id);
            if (name != null) names.add(name);
        }
        return names;
    }

    public List<String> getAttributeList(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        return nameService.attributeList(azquoMemoryDBConnection);
    }

    // pretty much replaced the original set of functions to do basic name manipulation
    // should more functionality be pushed forward? The errors being in here for example are probably not correct.
    // also it's taking a json object parsed directly from the front end. Hmmm.

    public String processJsonRequest(DatabaseAccessToken databaseAccessToken, NameJsonRequest nameJsonRequest) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        String toReturn = "true"; // think this will maike the parser at the other end happier
        if (nameJsonRequest.operation.equalsIgnoreCase(NameService.DELETE)) {
            if (nameJsonRequest.name.equals("all")) {
                azquoMemoryDBConnection.getAzquoMemoryDB().zapUnusedNames();
            } else {
                if (nameJsonRequest.id == 0) {
                    return "error: id not passed for delete";
                } else {
                    Name name = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.id);
                    if (name == null) {
                        return "error: name for id not found : " + nameJsonRequest.id;
                    }
                    if (name.getValues().size() > 0 && !nameJsonRequest.withData) {
                        return "error: cannot delete name with data : " + nameJsonRequest.id;
                    } else {
                        name.delete();
                    }
                }
            }
        }
        if (nameJsonRequest.operation.equalsIgnoreCase(NameService.EDIT) || nameJsonRequest.operation.equalsIgnoreCase(NameService.NEW)) {
            if (nameJsonRequest.id == 0 && nameJsonRequest.operation.equalsIgnoreCase(NameService.EDIT)) {
                return "error: id not passed for edit";
            } else {
                Name name;
                if (nameJsonRequest.operation.equalsIgnoreCase(NameService.EDIT)) {
                    name = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.id);
                } else {
                    // new name . . .
                    name = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), true);
                }
                if (name == null) {
                    return "error: name for id not found : " + nameJsonRequest.id;
                }
                Name newParent = null;
                Name oldParent = null;
                if (nameJsonRequest.newParent > 0) {
                    newParent = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.newParent);
                    if (newParent == null) {
                        return "error: new parent for id not found : " + nameJsonRequest.newParent;
                    }
                }
                if (nameJsonRequest.oldParent > 0) {
                    oldParent = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.oldParent);
                    if (oldParent == null) {
                        return "error: old parent for id not found : " + nameJsonRequest.oldParent;
                    }
                }
                if (newParent != null) {
                    newParent.addChildWillBePersisted(name, nameJsonRequest.newPosition);
                }
                if (oldParent != null) {
                    oldParent.removeFromChildrenWillBePersisted(name);
                }
                // only clear and re set if attributes passed!
                if (nameJsonRequest.attributes != null && !nameJsonRequest.attributes.isEmpty()) {
                    name.clearAttributes(); // and just re set them below
                    for (String key : nameJsonRequest.attributes.keySet()) {
                        // Edd removed peer stuff, names don't have peers any more!
                        name.setAttributeWillBePersisted(key, nameJsonRequest.attributes.get(key));
                    }
                }
            }
        }
        azquoMemoryDBConnection.persist();
        return toReturn;
    }

    private int getTotalValues(Name name) {
        int values = name.getValues().size();
        for (Name child : name.getChildren()) {
            values += getTotalValues(child);
        }
        return values;
    }

    // was about 40 lines before jackson though the class above is of course important. Changing name to details not structure which implies many levels.
    public JsonChildStructure getChildDetailsFormattedForOutput(DatabaseAccessToken databaseAccessToken, int nameId) throws Exception {
        Name name = nameService.findById(dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken), nameId);
        Map<String, Object> attributesForJackson = new HashMap<>();
        attributesForJackson.putAll(name.getAttributes());
        return new JsonChildStructure(name.getDefaultDisplayName()
                , name.getId(), getTotalValues(name), name.getValues().size(), attributesForJackson, name.getChildren().size(), "User : " + name.getProvenance().getUser() + "<br/>"
                + "Timestamp : " + name.getProvenance().getTimeStamp() + "<br/>"
                + "Method : " + name.getProvenance().getMethod() + "<br/>"
                + "Name : " + name.getProvenance().getName() + "<br/>"
                + "Context : " + name.getProvenance().getContext()
        );
    }

    public boolean moveJsTreeNode(DatabaseAccessToken databaseAccessToken, int parentId, int childId) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        Name parent = nameService.findById(connectionFromAccessToken, parentId);
        Name child = nameService.findById(connectionFromAccessToken, childId);
        if (parent != null && child != null) {
            parent.addChildWillBePersisted(child);
            return true;
        }
        return false;
    }

    public JsonChildren.Node createJsTreeNode(DatabaseAccessToken databaseAccessToken, int nameId) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        Name name = nameService.findById(connectionFromAccessToken, nameId); // the parent, will be null if -1 passed in the case of adding to root . . .
        Name newName = nameService.findOrCreateNameInParent(connectionFromAccessToken, "newnewnew", name, true);
        newName.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, "New node");
        return new JsonChildren.Node(-1, "New node", false, newName.getId(), nameId);
    }

    public boolean renameJsTreeNode(DatabaseAccessToken databaseAccessToken, int nameId, String newName) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        Name name = nameService.findById(connectionFromAccessToken, nameId);
        name.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, newName); // position?
        return true;
    }

    // Ok this now won't deal with the jstree ids (as it should not!), that can be dealt with on the front end
    // Another one where maybe it should not be passed a json object
    public JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        Map<String, Boolean> state = new HashMap<>();
        state.put("opened", true);
        String text = "";
        Collection<Name> children = new ArrayList<>();
        Name name = nameId > 0 ? nameService.findById(azquoMemoryDBConnection, nameId) : null;
        if (jsTreeId == 0 && name == null) {// will be true on the initial call
            text = "root";
            if (searchTerm == null || searchTerm.length() == 0) {// also true on the initial call
                children = nameService.findTopNames(azquoMemoryDBConnection, language);// hence we get the top names, OK
            } else {
                try {
                    children = nameService.parseQuery(azquoMemoryDBConnection, searchTerm);
                } catch (Exception e) {//carry on
                }
                if (children == null || children.size() == 0) {
                    children = nameService.getNamesWithAttributeContaining(azquoMemoryDBConnection, language, searchTerm);
                }
            }
        } else if (name != null) { // typically on open
            text = name.getAttribute(language);
            if (parents) {
                for (Name nameParent : name.getParents()) {
                    if (nameParent!=null){//in case of corruption - this should not happen
                        children.add(nameParent);
                    }
                }
            } else {
                for (Name child : name.getChildren()) {
                    if (child != null){
                        children.add(child);//see above - in case of corruption
                    }
                }
            }
        }
        List<JsonChildren.Node> childNodes = new ArrayList<>();
        if (children.size() > 0 || (name != null && name.getAttributes().size() > 1)) {
            int count = 0;
            for (Name child : children) {
                boolean childrenBoolean = child.hasChildren() || child.hasValues() || child.getAttributes().size() > 1;
                if (count > 100) {
                    childNodes.add(new JsonChildren.Node(-1, (children.size() - 100) + " more....", childrenBoolean, -1, -1));
                    break;
                }
                childNodes.add(new JsonChildren.Node(-1, child.getAttribute(language), childrenBoolean, child.getId(), name != null ? name.getId() : 0));
                count++;
            }
            if (name != null) { // if it's not top then we add non DEFAULT_DISPLAY_NAME attributes to the bottom of the list
                for (String attName : name.getAttributes().keySet()) {
                    if (!attName.equals(language)) {
                        childNodes.add(new JsonChildren.Node(-1, attName + ":" + name.getAttributes().get(attName), false, name.getId(), name.getId()));
                    }
                }
            }
        } else {
            return new JsonChildren(0, state, searchTerm, new ArrayList<>(), "");
        }
        if (searchTerm != null && !searchTerm.isEmpty() && childNodes.size() > 1){// check for duplicate names and qualify them
            Set<String> allNames = HashObjSets.newMutableSet();
            Set<String> duplicateNames = HashObjSets.newMutableSet();
            for (JsonChildren.Node node : childNodes){
                if (!allNames.add(node.text)){ // return false if it's already in tehre in which case this is a duplicate name
                    duplicateNames.add(node.text);
                }
            }
            if (!duplicateNames.isEmpty()){ // then run through qualifying as necessary
                for (JsonChildren.Node node : childNodes){
                    if (duplicateNames.contains(node.text)){ // return false if it's already in tehre in which case this is a duplicate name
                        Name n = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(node.nameId);
                        node.text = n.getParents().isEmpty() ? "Root" : n.getParents().iterator().next().getDefaultDisplayName() + "->" + node.text;
                    }
                }
            }
        }


        String type;
        if (children.size() > 0) {
            type = "parent";
        } else if (name != null && name.getValues().size() > 0) {
            if (name.getValues().size() > 1) {
                type = "values";
            } else {
                type = "value";
            }
        } else {
            type = "child";
        }
        return new JsonChildren(jsTreeId, state, text, childNodes, type);
    }

    public String getNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        Name name = nameService.findByName(azquoMemoryDBConnection, nameString);
        if (name != null) {
            return name.getAttribute(attribute);
        }
        return null;

    }

    public void setNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute, String attVal) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        Name name = nameService.findByName(azquoMemoryDBConnection, nameString);
        if (name != null) {
            name.setAttributeWillBePersisted(attribute, attVal);
        }
    }
}
