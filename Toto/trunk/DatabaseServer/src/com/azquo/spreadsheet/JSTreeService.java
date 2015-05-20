package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.view.NameJsonRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by cawley on 13/05/15.
 *
 * All JSTree code that deals with DB objects to go in here. It will initially have some "View" code i.e. JSON creation
 * This needs to be moved out in time. Ideally Jackson in the controller.
 *
 * Initial set of functions from NameService along with code moved from the controller
 *
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


    Map<String, Map<String, JSTreeService.JsTreeNode>> lookupMap = new ConcurrentHashMap<String, Map<String, JSTreeService.JsTreeNode>>();
    Map<String, Integer> lastJSTreeIdMap = new ConcurrentHashMap<String, Integer>();

    // from the controller, as I say in many comments need to move some of the code back to the controller but for the moment it's forced DB side
    // ok annoyingly the lookup needs to be persisted across calls, so I'll need a map in here of the lookups.
    // THis is a pretty crude way of sorting the problem, todo : make this better given the client server split.
    // This lookup was against the LIC but I can't use this on client/server. Could find a way to zap it fully if I understand the logic, putting the last id in there as well
    public String processRequest(DatabaseAccessToken databaseAccessToken, String json, String jsTreeId, String topNode, String op
            , String parent, String parents, String database, String itemsChosen, String position, String backupSearchTerm) throws Exception{
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);

        // trying for the tree id here, hope that will work
        Map<String, JSTreeService.JsTreeNode> lookup = lookupMap.get(jsTreeId);
        if (lookup == null){
            lookup = new HashMap<String, JSTreeService.JsTreeNode>();
            lookupMap.put(jsTreeId, lookup);
        }
        if (json != null && json.length() > 0) {
            NameJsonRequest nameJsonRequest;
                nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
            JSTreeService.JsTreeNode currentNode = lookup.get(nameJsonRequest.id + "");
            JSTreeService.NameOrValue lineChosen = currentNode.child;
            if (lineChosen.name != null) {
                nameJsonRequest.id = lineChosen.name.getId();//convert from jstree id.
                return processJsonRequest(azquoMemoryDBConnection, nameJsonRequest, databaseAccessToken.getLanguages());
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
            //if (current==null&& op.equals("rename_node")){
            //a new node has just been created
            //}
            if (op.equals("new")) {
                if (parents == null) parents = "false";
                int rootId = 0;
                if (current.child.name != null) {
                    rootId = current.child.name.getId();
                }
                return rootId + "";
            }
            if (op.equals("children")) {
                if (itemsChosen != null && itemsChosen.startsWith(",")) {
                    itemsChosen = itemsChosen.substring(1);
                }
                if (itemsChosen == null){
                    itemsChosen = backupSearchTerm;
                }
                return getJsonChildren(azquoMemoryDBConnection, jsTreeId, current.child.name, parents, lookup, true, itemsChosen);
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
                if (op.equals("details")) {
                    return "true,\"namedetails\":" + jsonNameDetails(current.child.name);
                }
                throw new Exception(op + " not understood");
            }
        }
        return "no action taken";
    }

    // pretty much replaced the original set of functions to do basic name manipulation
    // needs a logged in connection for the structure return

    public String processJsonRequest(AzquoMemoryDBConnection azquoMemoryDBConnection, NameJsonRequest nameJsonRequest, List<String> attributeNames) throws Exception {
        String toReturn = "";
        // type; elements level 1; from a to b
        if (nameJsonRequest.operation.equalsIgnoreCase(NameService.STRUCTURE)) {
            return getStructureForNameSearch(azquoMemoryDBConnection, nameJsonRequest.name, -1, attributeNames);//-1 indicates to show the children
        }
        if (nameJsonRequest.operation.equalsIgnoreCase(NameService.NAMELIST)) {
            try {
                return getNamesFormattedForOutput(nameService.parseQuery(azquoMemoryDBConnection, nameJsonRequest.name, attributeNames));
            } catch (Exception e) {
                return "Error:" + e.getMessage();
            }
        }

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
                    name = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance("imported"), true);
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
                            LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>();
                            if (key.equalsIgnoreCase(NameService.PEERS)) { // if it's not then we're in here because no peers were sent so leave the peer list blank
                                StringTokenizer st = new StringTokenizer(nameJsonRequest.attributes.get(key), ",");
                                while (st.hasMoreTokens()) {
                                    String peerName = st.nextToken().trim();
                                    Name peer = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, peerName, null);
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


    // right now ONLY called for the column heading in uploads, set peers on existing names

    // should use jackson??

    private String getNamesFormattedForOutput(final Collection<Name> names) {
        // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("{\"names\":[");
        for (Name n : names) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("{\"name\":");
            sb.append("\"").append(n.getDefaultDisplayName()).append("\"}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    private int getTotalValues(Name name) {
        int values = name.getValues().size();
        for (Name child : name.getChildren()) {
            values += getTotalValues(child);
        }
        return values;
    }

    // use jackson?

    public String getStructureForNameSearch(AzquoMemoryDBConnection azquoMemoryDBconnection, String nameSearch, int nameId, List<String> attributeNames) throws Exception {

        boolean withChildren = false;
        if (nameId == -1) withChildren = true;
        Name name = nameService.findByName(azquoMemoryDBconnection, nameSearch, attributeNames);
        if (name != null) {
            return "{\"names\":[" + getChildStructureFormattedForOutput(name, withChildren) + "]}";
        } else {
            List<Name> names = new ArrayList<Name>();
            if (nameId > 0) {
                name = nameService.findById(azquoMemoryDBconnection, nameId);
                //children is a set, so cannot be cast directly as a list.  WHY ISN'T CHILDREN A LIST?
                names = new ArrayList<Name>();
                for (Name child : name.getChildren()) {
                    names.add(child);
                }
            } else {

                if (nameSearch.length() > 0) {
                    names = nameService.findContainingName(azquoMemoryDBconnection, nameSearch.replace("`", ""));
                }
                if (names.size() == 0) {
                    names = nameService.findTopNames(azquoMemoryDBconnection);
                    Collections.sort(names, nameService.defaultLanguageCaseInsensitiveNameComparator);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"names\":[");
            int count = 0;
            for (Name outputName : names) {
                String nameJson = getChildStructureFormattedForOutput(outputName, withChildren);
                if (nameJson.length() > 0) {
                    if (count > 0) sb.append(",");
                    sb.append(nameJson);
                    count++;
                }
            }
            sb.append("]}");
/*            if (azquoMemoryDBconnection.getAzquoBook()!=null){
                azquoMemoryDBconnection.getAzquoBook().nameChosenJson = sb.toString();
            }*/
            return sb.toString();
        }
    }

    // again should use jackson?

    private String getChildStructureFormattedForOutput(final Name name, boolean showChildren) {
        int totalValues = getTotalValues(name);
        //if (totalValues > 0) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":");
        sb.append("\"").append(name.getDefaultDisplayName().replace("\"", "''")).append("\"");//trapping quotes in name - should not be there
        sb.append(", \"id\":\"").append(name.getId()).append("\"");

        sb.append(", \"dataitems\":\"").append(totalValues).append("\"");
        if (name.getValues().size() > 0) {
            sb.append(", \"mydataitems\":\"").append(name.getValues().size()).append("\"");
        }
        //putputs the peer list as an attribute  - CURRENTLY MARKING SINGULAR PEERS WITH A '--'
        int count = 0;
        if (name.getAttributes().size() > 0 || name.getPeers().size() > 0) {
            sb.append(",\"attributes\":{");
            if (name.getPeers().size() > 0) {
                String peerList = "";
                for (Name peer : name.getPeers().keySet()) {
                    if (peerList.length() > 0) {
                        peerList += ", ";
                    }
                    peerList += peer.getDefaultDisplayName();
                    if (!name.getPeers().get(peer)) {
                        peerList += "--";
                    }
                }
                // here and a few lines below is a bit of manual JSON building. Not sure how much this is a good idea or not. Jackson?
                sb.append("\"peers\":\"").append(peerList).append("\"");
                count++;

            }
            for (String attName : name.getAttributes().keySet()) {
                if (count > 0) sb.append(",");
                try {
                    sb.append("\"").append(attName).append("\":\"").append(URLEncoder.encode(name.getAttributes().get(attName).replace("\"", "''"), "UTF-8")).append("\"");//replacing quotes again
                } catch (UnsupportedEncodingException e) {
                    // this really should not happen!
                    e.printStackTrace();
                }
                count++;
            }
            sb.append("}");
        }
        final Collection<Name> children = name.getChildren();
        sb.append(", \"elements\":\"").append(children.size()).append("\"");
        if (showChildren) {
            if (!children.isEmpty()) {
                sb.append(", \"children\":[");
                count = 0;
                for (Name child : children) {
                    String childData = getChildStructureFormattedForOutput(child, false);
                    if (childData.length() > 0) {
                        if (count > 0) sb.append(",");
                        sb.append(childData);
                        count++;
                    }
                }
                sb.append("]");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    public String jsonNameDetails(Name name) {
        return getChildStructureFormattedForOutput(name, false);

    }

    // todo : jackson!

    private String getJsonChildren(AzquoMemoryDBConnection loggedInConnection, String jsTreeId, Name name, String parents, Map<String, JSTreeService.JsTreeNode> lookup, boolean details, String searchTerm) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("[{\"id\":" + jsTreeId + ",\"state\":{\"opened\":true},\"text\":\"");
        List<Name> children = new ArrayList<Name>();
        if (jsTreeId.equals("0") && name == null) {
            result.append("root");
            if (searchTerm == null || searchTerm.length() == 0) {
                children = nameService.findTopNames(loggedInConnection);
            } else {
                children = nameService.findContainingName(loggedInConnection, searchTerm, Name.DEFAULT_DISPLAY_NAME);
            }

        } else if (name != null) {
            result.append(name.getDefaultDisplayName().replace("\"", "\\\""));
            if (!parents.equals("true")) {
                for (Name child : name.getChildren()) {
                    children.add(child);
                }
            } else {
                for (Name nameParent : name.getParents()) {
                    children.add(nameParent);
                }
            }

        }

        result.append("\"");
        int maxdebug = 500;
        if (children.size() > 0 || (details && name != null && name.getAttributes() != null && name.getAttributes().size() > 1)) {
            result.append(",\"children\":[");
            int lastId = 0;
            if (lastJSTreeIdMap.get(jsTreeId) != null){
                lastId = lastJSTreeIdMap.get(jsTreeId);
            }
            int count = 0;
            for (Name child : children) {
                if (maxdebug-- == 0) break;
                if (count++ > 0) {
                    result.append(",");
                }
                lastJSTreeIdMap.put(jsTreeId, ++lastId);
                JSTreeService.NameOrValue nameOrValue = new JSTreeService.NameOrValue();
                nameOrValue.values = null;
                nameOrValue.name = child;
                JSTreeService.JsTreeNode newNode = new JSTreeService.JsTreeNode(nameOrValue, name);
                lookup.put(lastId + "", newNode);
                if (count > 100) {
                    result.append("{\"id\":" + lastId + ",\"text\":\"" + (children.size() - 100) + " more....\"}");
                    break;
                }
                result.append("{\"id\":" + lastId + ",\"text\":\"" + child.getDefaultDisplayName().replace("\"", "\\\"") + "\"");
                if (child.getChildren().size() > 0 || child.getValues().size() > 0 || (details && child.getAttributes().size() > 1)) {
                    result.append(",\"children\":true");
                }
                result.append("}");

            }
            if (details && name != null) {
                for (String attName : name.getAttributes().keySet()) {
                    if (!attName.equals(Name.DEFAULT_DISPLAY_NAME)) {
                        if (count++ > 0) {
                            result.append(",");
                        }
                        lastJSTreeIdMap.put(jsTreeId, ++lastId);
                        JSTreeService.NameOrValue nameOrValue = new JSTreeService.NameOrValue();
                        nameOrValue.values = null;
                        nameOrValue.name = name;
                        JSTreeService.JsTreeNode newNode = new JSTreeService.JsTreeNode(nameOrValue, name);

                        lookup.put(lastId + "", newNode);

                        result.append("{\"id\":" + lastId + ",\"text\":\"" + attName + ":" + name.getAttributes().get(attName).replace("\"", "\\\"") + "\"}");
                    }
                }

            }

            result.append("]");
        } else {
            result.append(getJsonDataforOneName(name, jsTreeId));
        }
        result.append(",\"type\":\"");
        if (children.size() > 0) {
            result.append("parent");

        } else if (name != null && name.getValues().size() > 0) {
            if (name.getValues().size() > 1) {
                result.append("values");
            } else {
                result.append("value");
            }
        } else {
            result.append("child");
        }
        result.append("\"}]");
        return result.toString();
    }

    public String getJsonDataforOneName(final Name name, String jsTreeId) throws Exception {
        final StringBuilder sb = new StringBuilder();
        Set<Name> names = new HashSet<Name>();
        names.add(name);
        List<Set<Name>> searchNames = new ArrayList<Set<Name>>();
        searchNames.add(names);
        Map<Set<Name>, Set<Value>> showValues = valueService.getSearchValues(searchNames);
        if (showValues == null) {
            return "";
        }
        sb.append(", \"children\":[");
        int lastId = 0;
        if (lastJSTreeIdMap.get(jsTreeId) != null){
            lastId = lastJSTreeIdMap.put(jsTreeId, ++lastId);
        }
        int count = 0;
        for (Set<Name> valNames : showValues.keySet()) {
            Set<Value> values = showValues.get(valNames);
            if (count++ > 0) {
                sb.append(",");
            }
            lastJSTreeIdMap.put(jsTreeId, ++lastId);
            JSTreeService.NameOrValue nameOrValue = new JSTreeService.NameOrValue();
            nameOrValue.values = values;
            nameOrValue.name = null;
            JSTreeService.JsTreeNode newNode = new JSTreeService.JsTreeNode(nameOrValue, name);
            final Map<String, JsTreeNode> stringJsTreeNodeMap = lookupMap.get(jsTreeId);
            stringJsTreeNodeMap.put(lastId + "", newNode);// asking for a null pointer perhaps, geuss lets see what happens
            if (count > 100) {
                sb.append("{\"id\":" + lastId + ",\"text\":\"" + (showValues.size() - 100) + " more....\"}");
                break;
            }
            sb.append("{\"id\":" + lastId + ",\"text\":\"" + valueService.addValues(values) + " ");
            for (Name valName : valNames) {
                if (valName.getId() != name.getId()) {
                    sb.append(valName.getDefaultDisplayName().replace("\"", "\\\"") + " ");
                }
            }
            sb.append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }


}
