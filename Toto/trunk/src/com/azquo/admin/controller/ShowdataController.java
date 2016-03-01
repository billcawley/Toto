package com.azquo.admin.controller;

import com.azquo.memorydb.TreeNode;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 05/08/15.
 *
 * Used by JSTree, inspect database.
 */

@Controller
@RequestMapping("/Showdata")

public class ShowdataController {

    @Autowired
    SpreadsheetService spreadsheetService;

    @Autowired
    RMIClient rmiClient;

    @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "chosen", required = false) String chosen
    ) throws Exception
    {
        // I assume secure until we move to proper spring security
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            // ok we'll put the id parsing bits in here now, much better
            String[] namesString = chosen.split(",");
            if (namesString[0].startsWith("jstreeids:")){
                Set<Integer> nameIds = new HashSet<>();
                namesString[0] = namesString[0].substring("jstreeids:".length());
                // I note that we are trusting lookup not to be null
                for(String jstreeId : namesString){
                    JsonChildren.Node node = loggedInUser.getFromJsTreeLookupMap(Integer.parseInt(jstreeId));
                    if (node != null){
                        nameIds.add(node.nameId);
                    }
                }
                TreeNode node = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getJstreeDataForOutputUsingIds(loggedInUser.getDataAccessToken(), nameIds, 1000);
                modelMap.addAttribute("node", node);
            } else {
                Set<String> nameNames = new HashSet<>();
                for (String nString : namesString){
                    nameNames.add(nString);
                }
                TreeNode node = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getJstreeDataForOutputUsingNames(loggedInUser.getDataAccessToken(), nameNames, 1000);
                modelMap.addAttribute("node", node);
            }
            // this jsp has JSTL which will render tree nodes correctly
            return "showdata";
        }
    }
}