package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ProvenanceService;
import com.azquo.spreadsheet.transport.json.JsonChildStructure;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 13/05/15.
 * <p>
 * All JSTree code that deals with DB objects to go in here.
 * <p>
 * It had controller like code in here but this has been moved out. Might take a few more passes before the representation is up to scratch.
 *
 * This should be the only place making TreeNodes but currently the Provenance uses it too.
 */
public class JSTreeService {

    public static List<String> getAttributeList(DatabaseAccessToken databaseAccessToken)  {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        return NameService.attributeList(azquoMemoryDBConnection);
    }

    // being pared down to just the edit attribute stuff. Json is sueful here, lists of attributes . . .maybe parse to java objects by this point?

    public static void editAttributes(DatabaseAccessToken databaseAccessToken, int nameId, Map<String, String> attributes) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name name = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameId);
        if (name == null) {
            throw new Exception("Name not found for id " + nameId);
        }
        name.clearAttributes(azquoMemoryDBConnection); // and just re set them below
        for (Map.Entry<String, String> attNameValue : attributes.entrySet()) {
            name.setAttributeWillBePersisted(attNameValue.getKey(), attNameValue.getValue(),azquoMemoryDBConnection);
        }
        new Thread(azquoMemoryDBConnection::persist).start();
    }

    private static int getTotalValues(Name name) {
        int values = name.getValues().size();
        for (Name child : name.getChildren()) {
            values += getTotalValues(child);
        }
        return values;
    }

    // was about 40 lines before jackson though the class above is of course important. Changing name to details not structure which implies many levels.
    public static JsonChildStructure getNameDetailsJson(DatabaseAccessToken databaseAccessToken, int nameId) {
        Name name = NameService.findById(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), nameId);
        Map<String, Object> attributesForJackson = new HashMap<>(name.getAttributes());
        return new JsonChildStructure(name.getDefaultDisplayName()
                , name.getId(), getTotalValues(name), name.getValues().size(), attributesForJackson, name.getChildren().size(), "User : " + name.getProvenance().getUser() + "<br/>"
                + "Timestamp : " + name.getProvenance().getTimeStamp() + "<br/>"
                + "Method : " + name.getProvenance().getMethod() + "<br/>"
                + "Name : " + name.getProvenance().getName() + "<br/>"
                + "Context : " + name.getProvenance().getContext()
        );
    }

    public static JsonChildren.Node createJsTreeNode(DatabaseAccessToken databaseAccessToken, int nameId) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name name = NameService.findById(connectionFromAccessToken, nameId); // the parent, will be null if -1 passed in the case of adding to root . . .
        Name newName = NameService.findOrCreateNameInParent(connectionFromAccessToken, "newnewnew", name, true);
        newName.setAttributeWillBePersisted(StringLiterals.DEFAULT_DISPLAY_NAME, "New node",connectionFromAccessToken);
        return new JsonChildren.Node(-1, "New node", false, newName.getId(), nameId);
    }

    // left it pretty simple
    public static void deleteJsTreeNode(DatabaseAccessToken databaseAccessToken, int nameId) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name name = NameService.findById(connectionFromAccessToken, nameId);
        name.delete(connectionFromAccessToken);
    }

    // Ok this now won't deal with the jstree ids (as it should not!), that can be dealt with on the front end
    public static JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language, int hundredMore) {
        int childrenLimit = (hundredMore + 1) * 100;
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Map<String, Boolean> state = new HashMap<>();
        state.put("opened", true);
        String text = "";
        Collection<Name> children = new ArrayList<>();
        Name name = nameId > 0 ? NameService.findById(azquoMemoryDBConnection, nameId) : null;
        if (jsTreeId == 0 && name == null) {// will be true on the initial call
            text = "Azquo Sets";
            if (searchTerm == null || searchTerm.length() == 0) {// also true on the initial call
                children = NameService.findTopNames(azquoMemoryDBConnection, language);// hence we get the top names, OK
            } else {
                try {
                    children = NameQueryParser.parseQuery(azquoMemoryDBConnection, searchTerm);
                } catch (Exception e) {//carry on
                }
                if (children == null || children.size() == 0) {
                    children = NameService.getNamesWithAttributeContaining(azquoMemoryDBConnection, language, searchTerm);
                }
            }
        } else if (name != null) { // typically on open
            text = name.getAttribute(language);
            if (parents) {
                for (Name nameParent : name.getParents()) {
                    if (nameParent != null) {//in case of corruption - this should not happen
                        children.add(nameParent);
                    }
                }
            } else {
                for (Name child : name.getChildren()) {
                    if (child != null) {
                        children.add(child);//see above - in case of corruption
                    }
                }
            }
        }
        List<JsonChildren.Node> childNodes = new ArrayList<>();
        if (children.size() > 0 || (name != null && name.getAttributes().size() > 1)) {
            int count = 0;
            for (Name child : children) {
                // efc note - has values? I don't think this helps, it leaves nodes that look empty
//                boolean childrenBoolean = child.hasChildren() || child.hasValues() || child.getAttributes().size() > 1;
                boolean childrenBoolean = child.hasChildren() || child.getAttributes().size() > 1;
                if (count > childrenLimit) {
                    childNodes.add(new JsonChildren.Node(-1, (children.size() - childrenLimit) + " more....", childrenBoolean, -1, -1));
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
            return new JsonChildren(0, state, searchTerm, new ArrayList<>(), nameId,"");
        }
        if (searchTerm != null && !searchTerm.isEmpty() && childNodes.size() > 1) {// check for duplicate names and qualify them
            Set<String> allNames = HashObjSets.newMutableSet();
            Set<String> duplicateNames = HashObjSets.newMutableSet();
            for (JsonChildren.Node node : childNodes) {
                if (!allNames.add(node.text)) { // return false if it's already in tehre in which case this is a duplicate name
                    duplicateNames.add(node.text);
                }
            }
            if (!duplicateNames.isEmpty()) { // then run through qualifying as necessary
                for (JsonChildren.Node node : childNodes) {
                    if (duplicateNames.contains(node.text)) { // return false if it's already in tehre in which case this is a duplicate name
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
        return new JsonChildren(jsTreeId, state, text, childNodes, nameId,type);
    }

    public static String getNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name name = NameService.findByName(azquoMemoryDBConnection, nameString);
        if (name != null) {
            return name.getAttribute(attribute);
        }
        return null;

    }

    public static void setNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute, String attVal) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name name = NameService.findByName(azquoMemoryDBConnection, nameString);
        if (name != null) {
            name.setAttributeWillBePersisted(attribute, attVal,azquoMemoryDBConnection);
        }
    }

    // for inspect database I think - should be moved to the JStree service maybe?
    public static TreeNode getDataList(DatabaseAccessToken databaseAccessToken, Set<String> nameStrings, Set<Integer> nameIds, int maxSize) throws Exception {
        Set<Name> names = new HashSet<>();
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        if (nameStrings != null) {
            for (String nString : nameStrings) {
                Name name = NameService.findByName(azquoMemoryDBConnection, nString);
                if (name != null) names.add(name);
            }
        }
        if (nameIds != null) {
            for (int id : nameIds) {
                Name name = NameService.findById(azquoMemoryDBConnection, id);
                if (name != null) names.add(name);
            }
        }
        List<Value> values = null;
        StringBuilder heading = new StringBuilder();
        for (Name name : names) {
            if (values == null) {
//                values = new ArrayList<>(valueService.findValuesForNameIncludeAllChildren(name, true));
                values = new ArrayList<>(name.findValuesIncludingChildren());
            } else {
                values.retainAll(name.findValuesIncludingChildren());
            }
            if (heading.length() > 0) heading.append(", ");
            heading.append(name.getDefaultDisplayName());
        }
        TreeNode toReturn = new TreeNode();
        toReturn.setHeading(heading.toString());
        toReturn.setValue("");
        toReturn.setChildren(ProvenanceService.nodify(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), values, maxSize));
        ProvenanceService.addNodeValues(toReturn);
        return toReturn;
    }
}