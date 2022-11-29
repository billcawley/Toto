package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.AzquoMemoryDBIndex;
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

    private static Collection<Name> getFilterSet(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String,Set<String>>filters){
        Collection<Name> filterSet = new HashSet<>();
        for (String filter:filters.keySet()){
            try {

                Collection<Name> thisFilterSet = new HashSet<>();
                for (String filterVal:filters.get(filter)){
                    Collection<Name> filterNames;
                    if (filterVal.length()==0){
                        filterNames = NameQueryParser.parseQuery(azquoMemoryDBConnection, filter);
                        thisFilterSet.addAll(filterNames.iterator().next().getChildren());
                    }else {
                        Name parentName = NameService.findByName(azquoMemoryDBConnection, filter);
                        //this search can accept more than one name....
                        filterNames = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(Collections.singletonList(StringLiterals.DEFAULT_DISPLAY_NAME),filterVal, parentName);
                        for (Name filterName:filterNames){
                            if (filterName.getAttribute(filter)!=null){
                                thisFilterSet.addAll(filterName.findAllChildren());

                            }
                        }
                    }
                }
                if (filterSet.size()==0){
                    filterSet = thisFilterSet;
                }else{
                    filterSet.retainAll(thisFilterSet);
                    if (filterSet.size()==0){
                        return filterSet;
                    }
                }
            }catch(Exception e){
                return filterSet;
            }
        }
        return filterSet;
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
        connectionFromAccessToken.persist();
        return new JsonChildren.Node(-1, "New node", false, newName.getId(), nameId, null);
    }

    // left it pretty simple
    public static void deleteJsTreeNode(DatabaseAccessToken databaseAccessToken, int nameId) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name name = NameService.findById(connectionFromAccessToken, nameId);
        name.delete(connectionFromAccessToken);
        connectionFromAccessToken.persist();
    }

    // reports may be modified or use the same choice. This function is to check that the saved user choice is one that's in the tree, logic chouls be pretty transparent
    public static boolean nameValidForChosenTree(DatabaseAccessToken databaseAccessToken, String chosenName, String searchTerm) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name n = NameService.findByName(azquoMemoryDBConnection, chosenName);
        if (n == null){
            return false;
        }
        Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, searchTerm);
        if (names.contains(n)){
            return true;
        }
        for (Name parent : n.findAllParents()){
            if (names.contains(parent)){
                return true;
            }
        }
        return false;
    }

    // default choice for chosen tree
    public static String getFirstChoiceForChosenTree(DatabaseAccessToken databaseAccessToken, String query) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, query);
        return names.isEmpty() ? "" : AzquoCellResolver.getUniqueName(azquoMemoryDBConnection, names.iterator().next(), Collections.EMPTY_LIST);
    }

    private static String firstString(String toSplit){
        if (toSplit.charAt(0)!=StringLiterals.QUOTE){
            return toSplit;
        }
        return toSplit.substring(1, toSplit.indexOf(StringLiterals.QUOTE, 1));
    }

    // Ok this now won't deal with the jstree ids (as it should not!), that can be dealt with on the front end
    public static JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean findParents, String searchTerm, String language, int hundredMore) {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        int limit = StringLiterals.FINDLIMIT;// there probably should be some sort of limit - 10 million is arbitrary.
        int limitPos = searchTerm.toLowerCase(Locale.ROOT).indexOf(" limit ");
        if (limitPos >= 0){
            String limitString = searchTerm.substring(limitPos + 7).trim();
            try{
                limit = Integer.parseInt(limitString);
                searchTerm = searchTerm.substring(0, limitPos);
            }catch (Exception e){
                //carry on
            }
        }
        Map <String,Set<String>> filters = new HashMap<>();
        if (searchTerm.startsWith(StringLiterals.QUOTE+"")){
            String queryString = searchTerm;
            searchTerm = firstString(queryString);
            queryString = queryString.substring(searchTerm.length() + 2);
             String lastFilterName = "";
            while (queryString.length()>0){
                //querystring is &`<name>``<value>`&`<name.....
                String filterName = firstString(queryString.substring(1));
                queryString = queryString.substring(filterName.length() + 3);
                String filterValue = firstString(queryString);
                queryString = queryString.substring(filterValue.length() + 2);

                if (!filterName.equals(lastFilterName)) {
                    filters.put(filterName, new HashSet<>());
                }
                filters.get(filterName).add(filterValue);
                lastFilterName = filterName;

            }

        }

          int childrenLimit = (hundredMore + 1) * 100;
        Map<String, Boolean> state = new HashMap<>();
        state.put("opened", true);
        String text = "";
        Collection<Name> children = new ArrayList<>();
        List<JsonChildren.Node> childNodes = new ArrayList<>();
        Collection<Name>filterSet=new HashSet<>();
        if (filters.size()>0){
            filterSet = getFilterSet(azquoMemoryDBConnection,filters);
            if (filterSet.size()==0){
                return null;
            }
        }
        Name name = nameId > 0 ? NameService.findById(azquoMemoryDBConnection, nameId) : null;
        if (jsTreeId == 0 && name == null) {// will be true on the initial call
            text = "Azquo Sets";
            if (searchTerm == null || searchTerm.length() == 0 || searchTerm.equals(StringLiterals.TOPNAMES)) {// also true on the initial call
                if (filters.size() == 0) {
                    children = NameService.findTopNames(azquoMemoryDBConnection, language);// hence we get the top names, OK
                }else{
                    children = filterSet;
                }
            } else {
                if (!searchTerm.contains(StringLiterals.MEMBEROF)){
                    try {
                        children = NameQueryParser.parseQuery(azquoMemoryDBConnection, searchTerm);
                        if (filterSet.size()>0){
                            children.retainAll(filterSet);
                        }
                    } catch (Exception e) {//carry on
                    }
                }
                if (children == null || children.size() !=1) {
                    if (filterSet.size() > 0) {
                        //used in DatabaseSearch
                        try{
                            children = NameService.getNamesFromSetWithAttributeContaining(azquoMemoryDBConnection, language, searchTerm, filterSet, limit);

                        } catch (Exception e) {
                            return null;
                        }
                    }else{
                        children = NameService.getNamesWithAttributeContaining(azquoMemoryDBConnection, language, searchTerm, limit);
                    }
                }
            }
        } else if (name != null) { // typically on open
            text = name.getAttribute(language);
            if (!language.equals(StringLiterals.DEFAULT_DISPLAY_NAME)) {
                text += (" (" + name.getDefaultDisplayName() + ")");
            }
            if (findParents) {
                Collection<Name> parents = name.getParents();
                if (jsTreeId>=0){
                    for (Name parent:parents){
                        if (parent!=null){
                            childNodes.add(new JsonChildren.Node(-1, parent.getDefaultDisplayName(), parent.getParents().size() > 0, parent.getId(), 0, null));
                        }
                    }
                }else{
                    parents = name.findAllParents();
                    for (Name parent : parents) {
                        Collection<Name> grandParents = parent.getParents();
                        if (grandParents.size() > 0) {
                            for (Name grandParent : grandParents) {
                                if (grandParent != null && grandParent.getParents().size() == 0) {
                                    childNodes.add(new JsonChildren.Node(-1, parent.getDefaultDisplayName(), parent.getParents().size() > 0, parent.getId(), grandParent.getId(), grandParent.getDefaultDisplayName()));
                                }
                            }
                        } else {
                            childNodes.add(new JsonChildren.Node(-1, parent.getDefaultDisplayName(), parent.getParents().size() > 0, parent.getId(), 0, null));
                        }
                    }
                }
                return new JsonChildren(jsTreeId + "", state, text, childNodes, nameId,getChildrenType(parents,name));

            } else {
                for (Name child : name.getChildren()) {
                    if (child != null) {
                        children.add(child);//see above - in case of corruption
                    }
                    if (filterSet.size() > 0){
                        children.retainAll(filterSet);
                    }
                }
            }
        }

        if (children.size() > 0 || (name != null && name.getAttributes().size() > 1)) {
            int count = 0;
            for (Name child : children) {
                // efc note - has values? I don't think this helps, it leaves nodes that look empty
//                boolean childrenBoolean = child.hasChildren() || child.hasValues() || child.getAttributes().size() > 1;
                boolean childrenBoolean = child.hasChildren() || child.getAttributes().size() > 1;
                if (count > childrenLimit) {
                    childNodes.add(new JsonChildren.Node(-1, (children.size() - childrenLimit) + " more....", childrenBoolean, -1, -1, null));
                    break;
                }
                String childText = child.getAttribute(language);
                if (!language.equals(StringLiterals.DEFAULT_DISPLAY_NAME)) {
                    childText += (" (" + child.getDefaultDisplayName() + ")");
                }
                //cut off names to 5000 chars
                if (childText != null && childText.length() > 5000) {
                    childText = childText.substring(0, 5000);
                }
                childNodes.add(new JsonChildren.Node(-1, childText, childrenBoolean, child.getId(), name != null ? name.getId() : 0, name != null ? name.getDefaultDisplayName() : null));
                count++;
            }
            if (jsTreeId > 0 && name != null) { // if it's not top then we add non DEFAULT_DISPLAY_NAME attributes to the bottom of the list - BUT NOT ON searchdatabase
                for (String attName : name.getAttributes().keySet()) {
                    if (!attName.equals(language)) {
                        childNodes.add(new JsonChildren.Node(-1, attName + ":" + name.getAttributes().get(attName), false, name.getId(), name.getId(), name.getDefaultDisplayName()));
                    }
                }
            }
        } else {
            return new JsonChildren("0", state, searchTerm, new ArrayList<>(), nameId, "");
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
         return new JsonChildren(jsTreeId + "", state, text, childNodes, nameId,getChildrenType(children,name));
    }

    public static String getChildrenType(Collection<Name>children, Name name){
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
        return type;

    }

    public static String getNameAttribute(DatabaseAccessToken databaseAccessToken, int nameId, String nameString, String attribute) throws Exception {
        try {
            AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
            Name name = null;
            if (nameId > 0) {
                name = NameService.findById(azquoMemoryDBConnection, nameId);
            } else {
                name = NameService.findByName(azquoMemoryDBConnection, nameString);
            }
            if (name != null) {
                return name.getAttribute(attribute);
            }
        }catch(Exception e){
            //may arrive here if two or more names are found
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