package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.StringLiterals;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import com.azquo.spreadsheet.transport.json.NameJsonRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 22/04/14.
 * <p>
 * For inspecting databases
 * <p>
 * modified by Edd to deal with new client/server model
 * <p>
 * Jackson and the logic from the service now in here. Need to clean this up a bit.
 */

@Controller
@RequestMapping("/Jstree")
public class JstreeController {

    private static final Logger logger = Logger.getLogger(JstreeController.class);

    private static final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "mode", required = false) String jsTreeMode
            , @RequestParam(value = "query", required = false) String query
            , @RequestParam(value = "id", required = false) String jsTreeId
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "parents", required = false) String parents
            , @RequestParam(value = "attribute", required = false) String attribute //only for use at root.
            , @RequestParam(value = "itemschosen", required = false) String itemsChosen
    ) {
        if (attribute == null || attribute.length() == 0) {
            attribute = StringLiterals.DEFAULT_DISPLAY_NAME;
        }

        if (query != null){
            // items chosen = search, allow that to override the query on the first window
            if (itemsChosen == null){
                itemsChosen = query;
            }


            jsTreeMode = "chosentree";
        }
        String jsonFunction = "azquojsonfeed";
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            model.addAttribute("content", "error:not logged in");
            return "utf8page";
        }
        if (!"children".equals(op) && !loggedInUser.getUser().isDeveloper() && !loggedInUser.getUser().isAdministrator()&& (query==null || query.length()==0)) {
            model.addAttribute("content", "error:access denied");
            return "utf8page";
        }
        try {
            // todo - is this duplicated below? SLight security concern re developers and database switching . . .
            if ((database == null || database.length() == 0) && loggedInUser.getDatabase() != null) {
                database = loggedInUser.getDatabase().getName();
            } else {
                LoginService.switchDatabase(loggedInUser, database);
            }
            int topNodeInt = ServletRequestUtils.getIntParameter(request, "topnode", 0);
            int parentInt = ServletRequestUtils.getIntParameter(request, "parent", 0);
            // todo - clean up the logic here
            String result = null;
            try {
                if (json != null && json.length() > 0) {
                    //json = json.replace("\n","\\n");
                    NameJsonRequest nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class); // still going to leave this as Json as the attribute parsing might be a pain otherwise
                    JsonChildren.Node currentNode = loggedInUser.getFromJsTreeLookupMap(nameJsonRequest.id); // we assume it is there, the code did before
                    if (currentNode.nameId != -1) {
                        nameJsonRequest.id = currentNode.nameId;//convert from jstree id to the name id
                        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).editAttributes(loggedInUser.getDataAccessToken(), nameJsonRequest.id, nameJsonRequest.attributes); // Now we pass through to the back end
                        result = "true";
                    }
                } else {
                    JsonChildren.Node currentNode = new JsonChildren.Node(-1, null, false, -1, -1);
                    if ("true".equals(jsTreeId)) {
                        currentNode = loggedInUser.getFromJsTreeLookupMap(parentInt);
                    } else if (jsTreeId == null || jsTreeId.equals("#") || jsTreeId.equals("j1_1")) { // EFC - I don't know where j1_1 came from but it seems to be used when updating the root list
                        if (topNodeInt > 0) {
                            currentNode.nameId = topNodeInt;
                        }
                        jsTreeId = "0";
                    } else { // on standard children there will be a tree id
                        if (NumberUtils.isNumber(jsTreeId)) {
                            currentNode = loggedInUser.getFromJsTreeLookupMap(Integer.parseInt(jsTreeId));
                        }
                    }
                    if (op.equals("new")) { // on the first call to the tree it will be new
                        Map<String, String> params = new HashMap<>();
                        params.put("Database", database);
                        loggedInUser.userLog("Inspect", params);
                        int rootId = 0;
                        if (currentNode != null && currentNode.nameId != -1) { // but on new current will be null
                            rootId = currentNode.nameId;
                        }
                        if (parents == null) {
                            parents = "false";
                        }
                        model.addAttribute("message", "");
                        if (database != null && database.length() > 0) {
                            Database newDB = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                            if (newDB == null) {
                                model.addAttribute("message", "no database chosen");
                            }
                            LoginService.switchDatabase(loggedInUser, newDB);
                        }
                        if (itemsChosen == null) itemsChosen = "";
                        if (itemsChosen.lastIndexOf(",") > 0){
                            //not sure why itemsChosen has become a list, but removing the list!
                            if (!itemsChosen.startsWith("edit")){
                                itemsChosen = itemsChosen.substring(itemsChosen.lastIndexOf(",") + 1).trim();
                            }
                        }
                        model.addAttribute("parents", parents);
                        model.addAttribute("rootid", rootId);
                        if (itemsChosen.length() > 0){
                            model.addAttribute("searchnames", URLEncoder.encode(itemsChosen,"UTF-8"));
                        }else{
                            model.addAttribute("searchnames",itemsChosen);
                        }
                        model.addAttribute("attributeChosen", attribute);
                        model.addAttribute("itemsChosen", itemsChosen);
                        if (jsTreeMode==null) {
                            jsTreeMode = "";
                        }
                        model.addAttribute("mode",jsTreeMode);


                        List<String> attributes = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getAttributeList(loggedInUser.getDataAccessToken());
                        model.addAttribute("attributes", attributes);
                        return "jstree";
                    }
                    if (op.equals("children")) { // the first call to JSTree gets returned quickly 2 lines above, this one is the seccond and is different as it has the "children" in op
                        if (itemsChosen != null && itemsChosen.startsWith(",")) {
                            itemsChosen = itemsChosen.substring(1);
                        }
                        if (itemsChosen == null) {
                            itemsChosen = "";
                        }
                        int hundredsMoreInt = ServletRequestUtils.getIntParameter(request, "hundredsmore", 0);

                        // the return type JsonChildren is designed to produce javascript that js tree understands
                        final JsonChildren jsonChildren = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                .getJsonChildren(loggedInUser.getDataAccessToken(), Integer.parseInt(jsTreeId), currentNode.nameId, parents.equals("true"),CommonReportUtils.replaceUserChoicesInQuery(loggedInUser,itemsChosen), attribute, hundredsMoreInt);
                        // Now, the node id management is no longer done server side, need to do it here, let logged in user assign each node id
                        if (itemsChosen.length() > 0){
                            if (jsonChildren.id.equals("0")){
                                jsonChildren.id = "j1_1";// edd trying to hack updating the root properly
                            }
                        }

                        jsonChildren.children.forEach(loggedInUser::assignIdForJsTreeNode);
                        result = jacksonMapper.writeValueAsString(jsonChildren);
                    } else if (currentNode != null && currentNode.nameId != -1) { // assuming it is not null!
                        if ("details".equals(op)) { // still used?
                            result = "true,\"namedetails\":" + jacksonMapper.writeValueAsString(RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .getNameDetailsJson(loggedInUser.getDataAccessToken(), currentNode.nameId));
                        }
                        if ("delete_node".equals(op)) { // still used?
                            if (loggedInUser.getUser().isAdministrator()){
                                RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                        .deleteNode(loggedInUser.getDataAccessToken(), currentNode.nameId);
                                result = "true";
                            }
                        }
                    }
                    if ("create_node".equals(op)) { // moved outside, it can operate with a null current node, adding to root
                        JsonChildren.Node newNode = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                .createNode(loggedInUser.getDataAccessToken(), currentNode != null ? currentNode.nameId : -1);
                        loggedInUser.assignIdForJsTreeNode(newNode);
                        result = newNode.id + "";
                    }
                }
            } catch (Exception e) {
                result = e.getMessage();
            }
            if (result == null) {
                result = "\"no action taken\"";
            }
            // seems to be the logic from before, if children/new then don't do the function. Not sure why . . .
            if (op == null) op = "";
            if (!op.equals("children") && !op.equals("create_node")) {
                model.addAttribute("content", jsonFunction + "({\"response\":" + result + "})");
            } else {
                model.addAttribute("content", result);
            }
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Content-type", "application/json");
        } catch (Exception e) {
            logger.error("jstree controller error", e);
            model.addAttribute("content", "error:" + e.getMessage());
        }
        return "utf8page";
    }
}