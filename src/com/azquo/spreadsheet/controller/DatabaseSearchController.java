package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.StringLiterals;
import com.azquo.dataimport.ImportWizard;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import com.azquo.spreadsheet.transport.json.NameJsonRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

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
@RequestMapping("/SearchDatabase")
public class DatabaseSearchController {


    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "query", required = false) String query
            , @RequestParam(value = "id", required = false) String nameIdString
            , @RequestParam(value = "database", required = false) String database
    ) {

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
            // todo - clean up the logic here
            try{
               loggedInUser.setSearchCategories(RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                        .getJsonChildren(loggedInUser.getDataAccessToken(), 0, 0, false, StringLiterals.TOPNAMES, StringLiterals.DEFAULT_DISPLAY_NAME, 0).children);


            }catch(Exception e){
                model.put("error","No database chosen");
            }
            return "searchdatabase";
        } catch (Exception e) {
            return ImportWizard.errorToJson(e.getMessage());
        }


    }


    public static String respondTo(HttpServletRequest request, LoggedInUser loggedInUser) {
        Map<String, Object> result = new HashMap<>();
        int hundredsMoreInt = 0;

        try {
            Logger logger = Logger.getLogger(JstreeController.class);
            ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String query = request.getParameter("query");
            String nameIdString = request.getParameter("nameId");
            int nameId = 0;
            try {
                nameId = Integer.parseInt(nameIdString);
            } catch (Exception e) {
                //blank
            }

            if (query != null && query.length() > 2) {
                for (JsonChildren.Node topName : loggedInUser.getSearchCategories()) {
                   String att = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getNameAttribute(loggedInUser.getDataAccessToken(), 0, topName.text, "searchable");
                  if (att==null || !att.equalsIgnoreCase("false")) {
                      // the return type JsonChildren is designed to produce javascript that js tree understands.  Insert 'topname' condition.
                      final JsonChildren jsonChildren = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                              .getJsonChildren(loggedInUser.getDataAccessToken(), 0, nameId, false,  query + "&" + StringLiterals.QUOTE + topName.text + "``` limit 20", StringLiterals.DEFAULT_DISPLAY_NAME, hundredsMoreInt);
                      // Now, the node id management is no longer done server side, need to do it here, let logged in user assign each node id

                      result.put(topName.text, jsonChildren);
                  }
                }
                return jacksonMapper.writeValueAsString(result);
            } else if (nameId > 0) {
                result.put("details", RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).getNameDetailsJson(loggedInUser.getDataAccessToken(), nameId));
                JsonChildren jsonChildren = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                        .getJsonChildren(loggedInUser.getDataAccessToken(), -1, nameId, false, " limit 100", StringLiterals.DEFAULT_DISPLAY_NAME, hundredsMoreInt);
                ;
                List<JsonChildren.Node> newList = new ArrayList<>();
                String dateAttribute = null;
                for (JsonChildren.Node searchCategory:loggedInUser.getSearchCategories()){
                    if (searchCategory.text.toLowerCase(Locale.ROOT).endsWith("date")){
                        dateAttribute = searchCategory.text;
                        break;
                    }
                }
                if (dateAttribute!=null && jsonChildren.children.size() > 0) {
                    for (JsonChildren.Node node : jsonChildren.children) {
                            String sortName = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getNameAttribute(loggedInUser.getDataAccessToken(), node.nameId, node.text, dateAttribute);

                            newList.add(new JsonChildren.Node(node.id, node.text, node.children, node.nameId, node.parentNameId, node.parentName, sortName));//adding in a sort name based on date.  This can be programmed through 'Role'
                     }
                    jsonChildren = new JsonChildren(jsonChildren.id, jsonChildren.state, jsonChildren.text, newList, jsonChildren.nameId, jsonChildren.type);
                }
                result.put("children", jsonChildren);
                jsonChildren = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                        .getJsonChildren(loggedInUser.getDataAccessToken(), -1, nameId, true, " limit 100", StringLiterals.DEFAULT_DISPLAY_NAME, hundredsMoreInt);
                ;

                 newList = new ArrayList<>();
                for (JsonChildren.Node node:jsonChildren.children){
                    if (checkInCategories(jsonChildren, node.parentName)){
                          newList.add(node);
                    }
                }
                jsonChildren = new JsonChildren(jsonChildren.id, jsonChildren.state, jsonChildren.text, newList, jsonChildren.nameId, jsonChildren.type);
                result.put("parents", jsonChildren);
                return jacksonMapper.writeValueAsString(result);


            }else{
                //Show the categories
                  JsonChildren jsonChildren = new JsonChildren("",null,"Categories", loggedInUser.getSearchCategories(),0,"");
                  result.put("categories", jsonChildren);
                return jacksonMapper.writeValueAsString(result);
            }
        } catch (Exception e) {
            return ImportWizard.errorToJson(e.getMessage());

        }
    }

    private static boolean checkInCategories(JsonChildren jsonChildren, String toTest){
        for (JsonChildren.Node node:jsonChildren.children){
            if (node.text.equals(toTest)) {
                return true;
            }
        }
        return false;
    }

}