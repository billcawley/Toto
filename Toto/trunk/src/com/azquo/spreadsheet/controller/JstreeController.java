package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.memorydb.Constants;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.jsonentities.NameJsonRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by bill on 22/04/14.
 *
 * For inspecting databases
 *
 * modified by Edd to deal with new client/server model
 *
 * Jackson and the logic from the service now in here. Need to clean this up a bit.
 *
 */

@Controller
@RequestMapping("/Jstree")
public class JstreeController {

    @Autowired
    private LoginService loginService;

    @Autowired
    SpreadsheetService spreadsheetService;

    @Autowired
    AdminService adminService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    DatabaseDAO databaseDAO;

    // I wonder about this being in here but for the moment I think it makes a bit more sense
    @Autowired
    RMIClient rmiClient;

    private static final Logger logger = Logger.getLogger(JstreeController.class);

    protected static final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "id", required = false) String jsTreeId
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "position", required = false) String position
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "parents", required = false) String parents
            , @RequestParam(value = "attribute", required = false) String attribute //only for use at root.
            , @RequestParam(value = "itemschosen", required = false) String itemsChosen
    ) throws Exception {
        if (attribute == null || attribute.length() == 0){
            attribute = Constants.DEFAULT_DISPLAY_NAME;
        }
        String jsonFunction = "azquojsonfeed";
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            model.addAttribute("content", "error:not logged in");
            return "utf8page";
        }

        try {
            if ((database == null || database.length() == 0) && loggedInUser.getDatabase() != null) {
                database = loggedInUser.getDatabase().getName();
            } else {
                loginService.switchDatabase(loggedInUser, database);
            }
            int topNodeInt = ServletRequestUtils.getIntParameter(request, "topnode", 0);
            int parentInt = ServletRequestUtils.getIntParameter(request, "parent", 0);
            // todo - clean up the logic here
            String result = null;
            if (json != null && json.length() > 0) {
                NameJsonRequest nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
                JsonChildren.Node currentNode = loggedInUser.getFromJsTreeLookupMap(nameJsonRequest.id); // we assume it is there, the code did before
                if (currentNode.nameId != -1) {
                    nameJsonRequest.id = currentNode.nameId;//convert from jstree id to the name id
                    result = rmiClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).processJSTreeRequest(loggedInUser.getDataAccessToken(), nameJsonRequest); // Now we pass through to the back end
                }
            } else {
                JsonChildren.Node currentNode = new JsonChildren.Node(-1, null, false,-1,-1);
                if (jsTreeId == null || jsTreeId.equals("#")) {
                    if (topNodeInt > 0) {
                        currentNode.nameId = topNodeInt;
                    }
                    jsTreeId = "0";
                } else { // on standard children there will be a tree id
                    currentNode = loggedInUser.getFromJsTreeLookupMap(Integer.parseInt(jsTreeId));
                }
                // need to understand syntax on these 3
                if (jsTreeId.equals("true")) {
                    currentNode = loggedInUser.getFromJsTreeLookupMap(parentInt);
                }
                if (op.equals("new")) { // on the first call to the tree it will be new
                    int rootId = 0;
                    if (currentNode != null && currentNode.nameId != -1) { // but on new current will be null
                        rootId = currentNode.nameId;
                    }
                    if (parents == null){
                        parents = "false";
                    }
                    model.addAttribute("message","");
                    if (database != null && database.length() > 0) {
                        Database newDB = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
                        if (newDB == null) {
                            model.addAttribute("message","no database chosen");
                        }
                        loginService.switchDatabase(loggedInUser, newDB);
                    }
                    if (itemsChosen == null) itemsChosen = "";
                    model.addAttribute("parents", parents);
                    model.addAttribute("rootid", rootId);
                    model.addAttribute("searchnames", itemsChosen);
                    model.addAttribute("attributeChosen", attribute);
                    List<String> attributes = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getAttributeList(loggedInUser.getDataAccessToken());
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
                    final JsonChildren jsonChildren = rmiClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                            .getJsonChildren(loggedInUser.getDataAccessToken(), Integer.parseInt(jsTreeId), currentNode.nameId, parents.equals("true"), itemsChosen, attribute);
                    // Now, the node id management is no longer done server side, need to do it here, let logged in user assign each node id
                    jsonChildren.children.forEach(loggedInUser::assignIdForJsTreeNode);
                    result = jacksonMapper.writeValueAsString(jsonChildren);
                } else if (currentNode != null && currentNode.nameId != -1) { // assuming it is not null!
                    switch (op) {
                        case "move_node":
                            //lookup.get(parent).child.addChildWillBePersisted(current.child);
                            result = "" + rmiClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .moveJsTreeNode(loggedInUser.getDataAccessToken(), loggedInUser.getFromJsTreeLookupMap(parentInt).nameId, currentNode.nameId);
                            break;
                        case "create_node":
                            result = "" + rmiClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .createNode(loggedInUser.getDataAccessToken(), currentNode.nameId);
                            break;
                        case "rename_node":
                            result = "" + rmiClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .renameNode(loggedInUser.getDataAccessToken(), currentNode.nameId, position);
                            break;
                        case "details":
                            result = "true,\"namedetails\":" + jacksonMapper.writeValueAsString(rmiClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                                    .getChildDetailsFormattedForOutput(loggedInUser.getDataAccessToken(), currentNode.nameId));
                            break;
                        default:
                            throw new Exception(op + " not understood");
                    }
                }
            }
            if (result == null){
                result =  "no action taken";
            }

            // seems to be the logic from before, if children/new then don't do the function. Not sure why . . .
            if (op == null) op = "";

            if (!op.equals("children")) {
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
