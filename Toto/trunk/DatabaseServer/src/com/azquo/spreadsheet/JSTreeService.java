package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.jsonentities.NameJsonRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by cawley on 13/05/15.
 * <p/>
 * All JSTree code that deals with DB objects to go in here. It will initially have some "View" code i.e. JSON creation
 * This needs to be moved out in time. Initially I'll use jackson here but in time I can take those jackson objects and send them over RMI for rendering on the front end.
 * <p/>
 * Initial set of functions from NameService along with code moved from the controller
 */
public class JSTreeService {

    @Autowired
    ValueService valueService;//used only in formating children for output
    @Autowired
    DSSpreadsheetService dsSpreadsheetService;//used only in formating children for output
    @Autowired
    NameService nameService;

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    public static final class NameOrValue {
        public Name name;
        public Set<Value> values;
    }

    public static final class JsTreeNode {
        public NameOrValue child;
        public Name parent;

        public JsTreeNode(NameOrValue child, Name parent) {
            this.child = child;
            this.parent = parent;
        }
    }

    // todo - stop these being looked up by a dataaccesstoken to string, for the moment it's the nearest I can get to the old logged in connection, need to understand the logic

    Map<String, Map<String, JSTreeService.JsTreeNode>> lookupMap = new ConcurrentHashMap<>();
    Map<String, Integer> lastJSTreeIdMap = new ConcurrentHashMap<>();

    // from the controller, as I say in many comments need to move some of the code back to the controller but for the moment it's forced DB side
    // ok annoyingly the lookup needs to be persisted across calls, so I'll need a map in here of the lookups.
    // This lookup was against the LIC but I can't use this on client/server. Could find a way to zap it fully if I understand the logic, putting the last id in there as well
    // string literals in here . . .
    // jstree id really should be a number but it seems to be true on new? FOr the mo leave as string here
    public Set<Name> interpretNameString(DatabaseAccessToken databaseAccessToken, String nameString)throws Exception{
        Set<Name> names = new HashSet<>();
        String[] namesString = nameString.split(",");
        if (namesString[0].startsWith("jstreeids:")){
            Map<String, JSTreeService.JsTreeNode> lookup = lookupMap.get(databaseAccessToken.toString());
            namesString[0]= namesString[0].substring(10);
            for(String jstreeId:namesString){
                JSTreeService.JsTreeNode currentNode = lookup.get(jstreeId);
                if (currentNode.child.name != null){
                    names.add(currentNode.child.name);
                }

            }
            return names;

        }else{
            AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
            for (String nString:namesString){
                Name name = nameService.findByName(azquoMemoryDBConnection, nString);
                if (name!=null) names.add(name);

            }
        }
        return names;
    }

    public List<String>getAttributeList(DatabaseAccessToken databaseAccessToken)throws Exception{
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        return nameService.attributeList(azquoMemoryDBConnection);
    }


    public String processRequest(DatabaseAccessToken databaseAccessToken, String json, String jsTreeId, String topNode, String op
            , String parent, boolean parents, String itemsChosen, String position, String backupSearchTerm, String language) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        if (language == null || language.length() == 0){
            language = Constants.DEFAULT_DISPLAY_NAME;

        }

        // trying for the tree id here, hope that will work
        Map<String, JSTreeService.JsTreeNode> lookup = lookupMap.get(databaseAccessToken.toString()); // todo, sort this hack later, it's called in
        if (lookup == null) {
            lookup = new HashMap<>();
            lookupMap.put(databaseAccessToken.toString(), lookup);
        }
        if (json != null && json.length() > 0) {
            NameJsonRequest nameJsonRequest;
            nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
            JSTreeService.JsTreeNode currentNode = lookup.get(nameJsonRequest.id + "");
            JSTreeService.NameOrValue lineChosen = currentNode.child;
            if (lineChosen.name != null) {
                nameJsonRequest.id = lineChosen.name.getId();//convert from jstree id.
                return processJsonRequest(azquoMemoryDBConnection, nameJsonRequest);
            }
        } else {
            JSTreeService.JsTreeNode current = new JSTreeService.JsTreeNode(null, null);
            current.child = new JSTreeService.NameOrValue();
            current.child.values = null;
            if (jsTreeId == null || jsTreeId.equals("#")) {
                if (topNode != null && !topNode.equals("0")) {
                    current.child.name = nameService.findById(azquoMemoryDBConnection, Integer.parseInt(topNode));
                }
                jsTreeId = "0";
            } else {
                current = lookup.get(jsTreeId);
            }
            if (jsTreeId.equals("true")) {
                current = lookup.get(parent);
            }
            if (op.equals("new")) {
                int rootId = 0;
                if (current!=null && current.child.name != null) {
                    rootId = current.child.name.getId();
                }
                return rootId + "";
            }
            if (op.equals("children")) {
                if (itemsChosen != null && itemsChosen.startsWith(",")) {
                    itemsChosen = itemsChosen.substring(1);
                }
                if (itemsChosen == null) {
                    itemsChosen = backupSearchTerm;
                }
                return getJsonChildren(azquoMemoryDBConnection, databaseAccessToken.toString(), Integer.parseInt(jsTreeId), current.child.name, parents, lookup, itemsChosen, language);
            }
            if (current.child.name != null) {
                if (op.equals("move_node")) {
                    lookup.get(parent).child.name.addChildWillBePersisted(current.child.name);
                    return "true";
                }
                if (op.equals("create_node")) {
                    Name newName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "newnewnew", current.child.name, true);
                    newName.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, "New node");
                    return "true";
                }
                if (op.equals("rename_node")) {
                    current.child.name.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, position);
                    return "true";
                }
                // add delete node?
                if (op.equals("details")) {
                    return "true,\"namedetails\":" + getChildStructureFormattedForOutput(current.child.name);
                }
                throw new Exception(op + " not understood");
            }
        }
        return "no action taken";
    }

    // pretty much replaced the original set of functions to do basic name manipulation
    // needs a logged in connection for the structure return

    public String processJsonRequest(AzquoMemoryDBConnection azquoMemoryDBConnection, NameJsonRequest nameJsonRequest) throws Exception {
        String toReturn = "";

        if (nameJsonRequest.operation.equalsIgnoreCase(NameService.DELETE)) {
            if (nameJsonRequest.name.equals("all"))
                azquoMemoryDBConnection.getAzquoMemoryDB().zapUnusedNames();
            else {
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
                boolean foundPeers = false;
                int position = 0;
                // only clear and re set if attributes passed!
                if (nameJsonRequest.attributes != null && !nameJsonRequest.attributes.isEmpty()) {
                    name.clearAttributes(); // and just re set them below
                    for (String key : nameJsonRequest.attributes.keySet()) {
                        position++;
                        if (!key.equalsIgnoreCase(NameService.PEERS)) {
                            name.setAttributeWillBePersisted(key, nameJsonRequest.attributes.get(key));
                        }
                        if (key.equalsIgnoreCase(NameService.PEERS) || (position == nameJsonRequest.attributes.keySet().size() && !foundPeers)) { // the second means run this if we hit the end having not run it
                            foundPeers = true;
                            boolean editingPeers = false;
                            LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<>();
                            if (key.equalsIgnoreCase(NameService.PEERS)) { // if it's not then we're in here because no peers were sent so leave the peer list blank
                                StringTokenizer st = new StringTokenizer(nameJsonRequest.attributes.get(key), ",");
                                while (st.hasMoreTokens()) {
                                    String peerName = st.nextToken().trim();
                                    Name peer = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(Constants.DEFAULT_DISPLAY_NAME, peerName, null);
                                    if (peer == null) {
                                        return "error: cannot find peer : " + peerName;
                                    } else {
                                        peers.put(peer, true);
                                    }
                                }
                            }

                            // ok need to to see if what was passed was different

                            if (peers.keySet().size() != name.getPeers().keySet().size()) {
                                editingPeers = true;
                            } else { // same size, check the elements . . .
                                for (Name peer : name.getPeers().keySet()) {
                                    if (peers.get(peer) == null) { // mismatch, old peers has something the new one does not
                                        editingPeers = true;
                                    }
                                }
                            }

                            if (editingPeers) {
                                if (name.getParents().size() == 0) { // top level, we can edit
                                    name.setPeersWillBePersisted(peers);
                                } else {
                                    if (name.getPeers().size() == 0) { // no peers on the aprent
                                        return "error: cannot edit peers, this is not a top level name and there is no peer set for  this name or it's parents, name id " + nameJsonRequest.id;
                                    }
                                    if (name.getValues().size() > 0) {
                                        return "error: cannot edit peers, this is not a top level name and there is data assigned to this name " + nameJsonRequest.id;
                                    }
                                    name.setPeersWillBePersisted(peers);
                                }
                            }
                        }
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

    class JsonChildStructure{
        // public for jackson to see them
        public final String name;
        public final int id;
        public final int dataitems;
        public final int mydataitems;
        public final Map<String, Object> attributes; // peers are jammed in here as a list, perhaps not so good, todo - move peers outside?
        public final int elements;

        public JsonChildStructure(String name, int id, int dataitems, int mydataitems, Map<String, Object> attributes, int elements) {
            this.name = name;
            this.id = id;
            this.dataitems = dataitems;
            this.mydataitems = mydataitems;
            this.attributes = attributes;
            this.elements = elements;
        }
    }

    // was about 40 lines before jackson though the class abopve is of course important
    private String getChildStructureFormattedForOutput(final Name name) throws JsonProcessingException {
        //puts the peer list as an attribute  - CURRENTLY MARKING SINGULAR PEERS WITH A '--'
        Map<String, Object> attributesForJackson = new HashMap<>();
        List<String> peers = new ArrayList<>();
        if (name.getPeers().size() > 0) {
            for (Name peer : name.getPeers().keySet()) {
                peers.add(peer.getDefaultDisplayName() + (name.getPeers().get(peer) ? "" : "--")); // add the -- if not additive
            }
            attributesForJackson.put("peers", peers);
        }
        attributesForJackson.putAll(name.getAttributes());
        JsonChildStructure childStructureForJackson = new JsonChildStructure(name.getDefaultDisplayName()
                , name.getId(), getTotalValues(name), name.getValues().size(), attributesForJackson, name.getChildren().size());
        return jacksonMapper.writeValueAsString(childStructureForJackson);
    }


    static class JsonChildren {
        // could use a map I suppose but why not define the structure properly
        static class Triple {
            public final int id;
            public final String text;
            public final boolean children;

            public Triple(int id, String text, boolean children) {
                this.id = id;
                this.text = text;
                this.children = children;
            }
        }
        // public for jackson to see them
        public final int id;
        public final Map<String, Boolean> state;
        public final String text;
        public final List<Triple> children;
        public final String type;

        public JsonChildren(int id, Map<String, Boolean> state, String text, List<Triple> children, String type) {
            this.id = id;
            this.state = state;
            this.text = text;
            this.children = children;
            this.type = type;
        }
    }

    // todo : can we move the object? It's called in a funciton that returns some other stuff, hmmmmmmmmm
    private String getJsonChildren(AzquoMemoryDBConnection loggedInConnection, String tokenString, int jsTreeId, Name name, boolean parents, Map<String, JSTreeService.JsTreeNode> lookup, String searchTerm, String language) throws Exception {
        Map<String,Boolean> state = new HashMap<>();
        state.put("opened", true);
        String text = "";
        List<Name> children = new ArrayList<>();
        if (jsTreeId == 0 && name == null) {
            text = "root";
            if (searchTerm == null || searchTerm.length() == 0) {
                children = nameService.findTopNames(loggedInConnection, language);
            } else {
                try {
                    children = nameService.parseQuery(loggedInConnection, searchTerm);
                } catch (Exception e) {//carry on
                }
                if (children == null || children.size() == 0) {
                    children = nameService.findContainingName(loggedInConnection, searchTerm, language);
                }
            }
        } else if (name != null) {
            text = name.getAttribute(language);
            if (!parents) {
                for (Name child : name.getChildren()) {
                    children.add(child);
                }
            } else {
                for (Name nameParent : name.getParents()) {
                    children.add(nameParent);
                }
            }
        }
        List<JsonChildren.Triple> childTriples = new ArrayList<>();
        if (children.size() > 0 || (name != null && name.getAttributes().size() > 1)) {
            int lastId = 0;
            if (lastJSTreeIdMap.get(tokenString) != null) {
                lastId = lastJSTreeIdMap.get(tokenString);
            }
            int count = 0;
            for (Name child : children) {
                lastJSTreeIdMap.put(tokenString, ++lastId);
                JSTreeService.NameOrValue nameOrValue = new JSTreeService.NameOrValue();
                nameOrValue.values = null;
                nameOrValue.name = child;
                JSTreeService.JsTreeNode newNode = new JSTreeService.JsTreeNode(nameOrValue, name);
                lookup.put(lastId + "", newNode);
                boolean childrenBoolean = child.getChildren().size() > 0 || child.getValues().size() > 0 || child.getAttributes().size() > 1;
                if (count > 100) {
                    childTriples.add(new JsonChildren.Triple(lastId, (children.size() - 100) + " more....", childrenBoolean));
                    break;
                }
                childTriples.add(new JsonChildren.Triple(lastId, child.getAttribute(language), childrenBoolean));
                count++;
            }
            if (name != null) {
                for (String attName : name.getAttributes().keySet()) {
                    if (!attName.equals(language)) {
                        lastJSTreeIdMap.put(tokenString, ++lastId);
                        JSTreeService.NameOrValue nameOrValue = new JSTreeService.NameOrValue();
                        nameOrValue.values = null;
                        nameOrValue.name = name;
                        JSTreeService.JsTreeNode newNode = new JSTreeService.JsTreeNode(nameOrValue, name);
                        lookup.put(lastId + "", newNode);
                        childTriples.add(new JsonChildren.Triple(lastId, attName + ":" + name.getAttributes().get(attName), false));
                    }
                }
            }
        } else {
            return jacksonMapper.writeValueAsString(new JsonChildren(0, state, searchTerm, new ArrayList<>(), ""));
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
        return jacksonMapper.writeValueAsString(new JsonChildren(jsTreeId, state, text, childTriples, type));
    }
}
