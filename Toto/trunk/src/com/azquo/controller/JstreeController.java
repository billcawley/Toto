package com.azquo.controller;

import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.jsonrequestentities.NameJsonRequest;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;


/**
 * Created by bill on 22/04/14.
 *
 */

@Controller
@RequestMapping("/Jstree")
public class JstreeController {

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;

    @Autowired
    OnlineService onlineService;

    @Autowired
    ValueService valueService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    private static final Logger logger = Logger.getLogger(JstreeController.class);

    private static final ObjectMapper jacksonMapper = new ObjectMapper();


    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "op", required = false)  String op
            , @RequestParam(value = "id", required = false)  String jsTreeId
            , @RequestParam(value = "user", required = false)  String user
            , @RequestParam(value = "password", required = false)  String password
            , @RequestParam(value = "database", required = false)  String database
            , @RequestParam(value = "position", required = false)  String position
            , @RequestParam(value = "parent", required = false)  String parent
            , @RequestParam(value = "json", required = false)  String json
            , @RequestParam(value = "parents", required = false)  String parents
            , @RequestParam(value = "topnode", required = false)  String topNode //only for use at root.
            , @RequestParam(value = "itemschosen", required = false)  String itemsChosen
                                ) throws Exception {


        String jsonFunction = "azquojsonfeed";


        LoggedInConnection loggedInConnection = (LoggedInConnection)request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);
        try {
            StringBuffer result = new StringBuffer();
            if (loggedInConnection == null) {
                if (user.equals("demo@user.com")) {
                    user += request.getRemoteAddr();
                }
                loggedInConnection = loginService.login(database, user, password, 0, "", false);
                if (loggedInConnection == null) {
                    model.addAttribute("content", "error:not logged in");
                    return "utf8page";
                }
            }
            if ((database == null || database.length() == 0) && loggedInConnection.getCurrentDatabase() != null) {
                database = loggedInConnection.getCurrentDatabase().getName();
            }

            Map<String, LoggedInConnection.JsTreeNode> lookup = loggedInConnection.getJsTreeIds();

            if (json != null && json.length() > 0) {
                NameJsonRequest nameJsonRequest;
                try {
                    nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
                } catch (Exception e) {
                    logger.error("name json parse problem", e);
                    model.addAttribute("content", "error:badly formed json " + e.getMessage());
                    return "utf8page";
                }
                LoggedInConnection.JsTreeNode currentNode = lookup.get(nameJsonRequest.id + "");
                LoggedInConnection.NameOrValue lineChosen = currentNode.child;
                if (lineChosen.name != null) {
                    nameJsonRequest.id = lineChosen.name.getId();//convert from jstree id.
                    result.append(nameService.processJsonRequest(loggedInConnection, nameJsonRequest, loggedInConnection.getLanguages()));

                }
            } else {
                if (!op.equals("new") && !op.equals("children")){
                    if (itemsChosen != null && itemsChosen.length() > 0) {
                        //find the relevant data and show it
                        String[] jsItems = itemsChosen.split(",");
                        List<Set<Name>> namesChosen = new ArrayList<Set<Name>>();
                        for (String jsItem : jsItems) {
                            if (jsItem.length() > 0) {
                                try {
                                    LoggedInConnection.NameOrValue lineChosen = lookup.get(jsItem).child;
                                    Name nameChosen = lineChosen.name;
                                    if (nameChosen != null) {
                                        Set<Name> nameChosenSet = new HashSet<Name>();
                                        nameChosenSet.add(nameChosen);
                                        namesChosen.add(nameChosenSet);
                                    }
                                } catch (Exception e) {
                                    //should never happen.  If it does, ignore!
                                }
                            }
                        }
                        loggedInConnection.setNamesToSearch(namesChosen);
                     /*
                     OnlineReport onlineReport = onlineReportDAO.findById(1);
                     loggedInConnection.setReportId(1);
                     String sheet = onlineService.readExcel(loggedInConnection, onlineReport, "inspection","Right-click mouse for provenance");
                     model.addAttribute("content", sheet);
                     */
                        //return goes to a void - needs to refresh the original report....??
                        return "utf8page";

                    }
                }
                LoggedInConnection.JsTreeNode current = new LoggedInConnection.JsTreeNode(null, null);
                current.child = new LoggedInConnection.NameOrValue();
                current.child.values = null;
                if (jsTreeId == null || jsTreeId.equals("#")) {
                    if (topNode != null && !topNode.equals("0")) {
                        current.child.name = nameService.findById(loggedInConnection, Integer.parseInt(topNode));
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
                    result.append(onlineService.showNameDetails(loggedInConnection, database, rootId, parents, itemsChosen));
                    model.addAttribute("content", result);
                    return "utf8page";

                }
                if (op.equals("children")) {
                    if (itemsChosen != null && itemsChosen.startsWith(",")){
                        itemsChosen = itemsChosen.substring(1);
                    }

                    jsonFunction = null;
                    result.append(nameService.getJsonChildren(loggedInConnection, jsTreeId, current.child.name, parents, lookup, true, itemsChosen));

                }
                if (current.child.name != null) {
                    if (op.equals("move_node")) {
                        lookup.get(parent).child.name.addChildWillBePersisted(current.child.name);
                        result.append("true");
                    }
                    if (op.equals("create_node")) {
                        Name newName = nameService.findOrCreateNameInParent(loggedInConnection, "newnewnew", current.child.name, true);
                        newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, "New node");
                        result.append("true");
                    }

                    if (op.equals("rename_node")) {
                        current.child.name.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, position);
                        result.append("true");
                    }
                    if (op.equals("details")) {
                        result.append("true,\"namedetails\":").append(nameService.jsonNameDetails(current.child.name));
                        //result = jsonFunction + "({\"namedetails\":" + result + "})";

                    }
                    if (result.length() == 0) {
                        result.append("error:").append(op).append(" not understood");
                    }
                }
            }
            if (jsonFunction != null) {
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
