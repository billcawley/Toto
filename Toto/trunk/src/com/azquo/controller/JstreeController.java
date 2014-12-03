package com.azquo.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.memorydb.Name;
import com.azquo.service.*;
//import com.azquo.util.Chart;
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

    private static final Logger logger = Logger.getLogger(JstreeController.class);

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
        String jsonFunction = "azquojsonfeed";
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
        try {
            StringBuffer result = new StringBuffer();
            Map<String, Integer> lookup = loggedInConnection.getJsTreeIds();
            Integer id = 0;
            if (jsTreeId.equals("#")){
                jsTreeId = "0";
            } else{
                id = lookup.get(jsTreeId);
            }
            if (jsTreeId.equals("true")){
                id = lookup.get(parent);
            }
            Name name = null;
            if (id==null&& op.equals("rename_node")){
                //a new node has just been created
            }
            if (id > 0){
                name = nameService.findById(loggedInConnection,id);
                if (name == null){
                    result.append("error: unknown node");
                }
            }
            if (result.length()== 0) {
                if (op.equals("create_node")){
                    Name newName = nameService.findOrCreateNameInParent(loggedInConnection,"newnewnew",name,true);
                    newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME,"New node");
                    result.append("true");
                }

                if (op.equals("rename_node")){
                    name.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME,position);
                    result.append("true");
                }
                if (op.equals("children")) {
                    jsonFunction = null;
                    result.append("[{\"id\":" + jsTreeId + ",\"text\":\"");
                    List<Name> children = new ArrayList<Name>();
                    if (jsTreeId.equals("0")) {
                        loggedInConnection.setLastJstreeId(0);
                        lookup = new HashMap<String, Integer>();
                        loggedInConnection.setJsTreeIds(lookup);
                        result.append("root");
                        children = nameService.findTopNames(loggedInConnection);

                    } else {
                        Name nodeName = nameService.findById(loggedInConnection, id);
                        result.append(nodeName.getDefaultDisplayName());
                        for (Name child : name.getChildren()) {
                            children.add(child);
                        }

                    }
                    result.append("\"");
                    if (children.size() > 0) {
                        result.append(",\"children\":[");
                        int lastId = loggedInConnection.getLastJstreeId();
                        int count = 0;
                        for (Name child : children) {
                            if (count++ > 0) {
                                result.append(",");
                            }
                            loggedInConnection.setLastJstreeId(++lastId);
                            lookup.put(lastId + "", child.getId());
                            result.append("{\"id\":" + lastId + ",\"text\":\"" + child.getDefaultDisplayName() + "\"");
                            if (child.getChildren().size() > 0) {
                                result.append(",\"children\":true");
                            }
                            result.append("}");

                        }
                        result.append("]");
                    }
                    result.append("}]");

                }
                if (op.equals("details")){
                    result.append("true,\"namedetails\":" + nameService.jsonNameDetails(loggedInConnection, id));
                    //result = jsonFunction + "({\"namedetails\":" + result + "})";

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
