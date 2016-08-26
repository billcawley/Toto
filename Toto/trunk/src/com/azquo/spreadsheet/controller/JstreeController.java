package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.memorydb.Constants;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.view.NameJsonRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
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
            , @RequestParam(value = "id", required = false) String jsTreeId
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "parents", required = false) String parents
            , @RequestParam(value = "attribute", required = false) String attribute //only for use at root.
            , @RequestParam(value = "itemschosen", required = false) String itemsChosen
    ) throws Exception {
        if (attribute == null || attribute.length() == 0) {
            attribute = Constants.DEFAULT_DISPLAY_NAME;
        }
        String jsonFunction = "azquojsonfeed";
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            model.addAttribute("content", "error:not logged in");
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
                    } else if (jsTreeId == null || jsTreeId.equals("#")) {
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
                        int rootId = 0;
                        if (currentNode != null && currentNode.nameId != -1) { // but on new current will be null
                            rootId = currentNode.nameId;
                        }
                        if (parents == null) {
                            parents = "false";
                        }
                        model.addAttribute("message", "");
                        if (database != null && database.length() > 0) {
                            Database newDB = DatabaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
                            if (newDB == null) {
                                model.addAttribute("message", "no database chosen");
                            }
                            LoginService.switchDatabase(loggedInUser, newDB);
                        }
                        if (itemsChosen == null) itemsChosen = "";
                        model.addAttribute("parents", parents);
                        model.addAttribute("rootid", rootId);
                        model.addAttribute("searchnames", itemsChosen);
                        model.addAttribute("attributeChosen", attribute);
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
                        // the return type JsonChildren is designed to produce javascript that js tree understands
                        final JsonChildren jsonChildren = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                .getJsonChildren(loggedInUser.getDataAccessToken(), Integer.parseInt(jsTreeId), currentNode.nameId, parents.equals("true"), itemsChosen, attribute);
                        // Now, the node id management is no longer done server side, need to do it here, let logged in user assign each node id
                        jsonChildren.children.forEach(loggedInUser::assignIdForJsTreeNode);
                        result = jacksonMapper.writeValueAsString(jsonChildren);
                    } else if (currentNode != null && currentNode.nameId != -1) { // assuming it is not null!
                        if ("details".equals(op)) { // still used?
                            result = "true,\"namedetails\":" + jacksonMapper.writeValueAsString(RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .getNameDetailsJson(loggedInUser.getDataAccessToken(), currentNode.nameId));
                        }
                        if ("delete_node".equals(op)) { // still used?
                            RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .deleteNode(loggedInUser.getDataAccessToken(), currentNode.nameId);
                            result = "true";
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