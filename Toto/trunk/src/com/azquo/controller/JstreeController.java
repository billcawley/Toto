package com.azquo.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.jsonrequestentities.NameJsonRequest;
import com.azquo.memorydb.Name;
import com.azquo.service.*;
//import com.azquo.util.Chart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
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
    public String handleRequest (ModelMap model,HttpServletRequest request, HttpServletResponse response)throws Exception{


        String op = request.getParameter("op");
        String jsTreeId = request.getParameter("id");
        String connectionId = request.getParameter("connectionid");
        String user = request.getParameter("user");
        String password = request.getParameter("password");
        String database = request.getParameter("database");
        String position = request.getParameter("position");
        String parent = request.getParameter("parent");
        LoggedInConnection loggedInConnection = null;
        String json = request.getParameter("json");
        String parents = request.getParameter("parents");
        String topNode = request.getParameter("topnode");//only for use at root.
        String itemsChosen = request.getParameter("itemschosen");
        String jsonFunction = "azquojsonfeed";
         try {
            StringBuffer result = new StringBuffer();
             if (connectionId == null) {
                 if (user.equals("demo@user.com")){
                     user += request.getRemoteAddr();
                 }
                 loggedInConnection = loginService.login(database, user, password, 0, "", false);

                 if (loggedInConnection == null) {
                     model.addAttribute("content", "error:no connection id");
                     return "utf8page";
                 }
                 connectionId = loggedInConnection.getConnectionId();

             }
             loggedInConnection = loginService.getConnection(connectionId);
             Map<String, LoggedInConnection.JsTreeNode> lookup = loggedInConnection.getJsTreeIds();
             if (json != null && json.length() > 0) {
                 NameJsonRequest nameJsonRequest;
                try {
                    nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
                } catch (Exception e) {
                    logger.error("name json parse problem", e);
                    return "error:badly formed json " + e.getMessage();
                }
                if (loggedInConnection == null) {
                    return "error:invalid connection id or login credentials";
                }
                LoggedInConnection.JsTreeNode currentNode = lookup.get(nameJsonRequest.id+ "");
                 nameJsonRequest.id = currentNode.child.getId();//convert from jstree id.
                 result.append(nameService.processJsonRequest(loggedInConnection, nameJsonRequest, loggedInConnection.getLanguages()));
             }else{
                 if (itemsChosen != null && itemsChosen.length() > 0){
                     //find the relevant data and show it
                     String[] jsItems = itemsChosen.split(",");
                     List<Set<Name>>namesChosen = new ArrayList<Set<Name>>();
                     for (String jsItem:jsItems){
                         if (jsItem.length() > 0){
                             try {
                                 Name nameChosen = lookup.get(jsItem).child;
                                 Set<Name> nameChosenSet = new HashSet<Name>();
                                 nameChosenSet.add(nameChosen);
                                 namesChosen.add(nameChosenSet);
                             }catch(Exception e){
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
                 LoggedInConnection.JsTreeNode current = new LoggedInConnection.JsTreeNode(null,null);
                 if (jsTreeId==null || jsTreeId.equals("#")){
                     if (topNode != null){
                         current.child = nameService.findById(loggedInConnection,Integer.parseInt(topNode));
                     }
                     jsTreeId = "0";
                 } else{
                     current = lookup.get(jsTreeId);
                 }
                 if (jsTreeId.equals("true")){
                     current = lookup.get(parent);
                 }
                 if (current==null&& op.equals("rename_node")){
                     //a new node has just been created
                }
                 if (op.equals("new")){
                     if (parents==null) parents = "false";
                     int rootId = 0;
                     if (current.child != null){
                         rootId = current.child.getId();
                     }
                     result.append(onlineService.showNameDetails(loggedInConnection, database, rootId, parents));
                     model.addAttribute("content", result);
                     return "utf8page";

                 }
                 if (op.equals("children")) {
                     jsonFunction = null;
                     result.append("[{\"id\":" + jsTreeId + ",\"state\":{\"opened\":true},\"text\":\"");
                     List<Name> children = new ArrayList<Name>();
                     if (jsTreeId.equals("0")&& current.child == null) {
                         String searchTerm = loggedInConnection.getAzquoBook().getRangeData("az_inputInspectChoice");
                         result.append("root");
                         if (searchTerm == null || searchTerm.length()==0){
                             children = nameService.findTopNames(loggedInConnection);
                         }else{
                             children= nameService.findContainingName(loggedInConnection,searchTerm,Name.DEFAULT_DISPLAY_NAME);
                         }

                     } else if (current.child != null) {
                         result.append(current.child.getDefaultDisplayName().replace("\"","\\\""));
                         if (!parents.equals("true")) {
                             int count = 0;
                             for (Name child : current.child.getChildren()) {
                                 if (count++ >100){
                                     break;
                                 }
                                 children.add(child);
                             }
                         }else {
                             for (Name nameParent : current.child.getParents()) {
                                 children.add(nameParent);
                             }
                         }

                     }

                     result.append("\"");
                     if (children.size() > 0) {
                         result.append(",\"children\":[");
                         int lastId = loggedInConnection.getLastJstreeId();
                         int count = 0;
                         for (Name child : children) {
                               if (count > 100){
                                 //result.append(",{\"id\":10000000,\"text\":\"" + (children.size()-100) + " more....\"}");
                                 break;
                             }
                             if (count++ > 0) {
                                 result.append(",");
                             }
                             loggedInConnection.setLastJstreeId(++lastId);
                             LoggedInConnection.JsTreeNode newNode = new LoggedInConnection.JsTreeNode(child, current.child);
                             lookup.put(lastId + "", newNode);
                             result.append("{\"id\":" + lastId + ",\"text\":\"" + child.getDefaultDisplayName().replace("\"","\\\"") + "\"");
                             if (child.getChildren().size() > 0) {
                                 result.append(",\"children\":true");
                             }
                              result.append("}");

                         }
                         result.append("]");
                     }
                     result.append("}]");

                 }
                 if (current.child != null){
                     if (op.equals("move_node")){
                        lookup.get(parent).child.addChildWillBePersisted(current.child);
                        result.append("true");
                    }
                    if (op.equals("create_node")) {
                        Name newName = nameService.findOrCreateNameInParent(loggedInConnection, "newnewnew", current.child, true);
                        newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, "New node");
                        result.append("true");
                    }

                    if (op.equals("rename_node")) {
                        current.child.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, position);
                        result.append("true");
                    }
                    if (op.equals("details")) {
                        result.append("true,\"namedetails\":" + nameService.jsonNameDetails(current.child));
                        //result = jsonFunction + "({\"namedetails\":" + result + "})";

                    }
                    if (result.length()==0){
                        result.append("error:" + op + " not understood");
                    }
                }
            }
            if (jsonFunction != null){
                model.addAttribute("content",jsonFunction + "({\"response\":" + result + "})");
            }else{
                model.addAttribute("content", result);
            }
            response.setHeader("Access-Control-Allow-Origin","*");
            response.setHeader("Content-type","application/json");


        } catch (Exception e) {
            logger.error("jstree controller error", e);
            model.addAttribute("content", "error:" + e.getMessage());
        }
        return "utf8page";

    }


}
