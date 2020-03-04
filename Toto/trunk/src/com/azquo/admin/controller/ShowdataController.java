package com.azquo.admin.controller;

import com.azquo.memorydb.TreeNode;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 05/08/15.
 * <p>
 * Used by JSTree, inspect database.
 */

@Controller
@RequestMapping("/Showdata")

public class ShowdataController {

    private static final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "chosen", required = false) String chosen
    ) throws Exception {
        // I assume secure until we move to proper spring security
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            // ok we'll put the id parsing bits in here now, much better
            String[] namesString = chosen.split(",");
            if (namesString[0].startsWith("changed") && loggedInUser.getDatabase() != null) {
                ProvenanceDetailsForDisplay provenanceDetailsForDisplay = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getListOfChangedValues(loggedInUser.getDataAccessToken(), 100);
                if (provenanceDetailsForDisplay.getAuditForDisplayList() != null && !provenanceDetailsForDisplay.getAuditForDisplayList().isEmpty()) {
                    modelMap.addAttribute("audit", jacksonMapper.writeValueAsString(provenanceDetailsForDisplay));
                    modelMap.addAttribute("op", "audit");
                    return ("Excel");
                }
                return "showdata";
            }
            if (namesString[0].startsWith("jstreeids:")) {
                Set<Integer> nameIds = new HashSet<>();
                namesString[0] = namesString[0].substring("jstreeids:".length());
                for (String jstreeId : namesString) {
                    if (NumberUtils.isNumber(jstreeId)) {
                        JsonChildren.Node node = loggedInUser.getFromJsTreeLookupMap(Integer.parseInt(jstreeId));
                        if (node != null) {
                            nameIds.add(node.nameId);
                        }
                    } else {
                        System.out.println("Non number passed to show data : " + jstreeId);
                    }
                }
                TreeNode node = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getJstreeDataForOutputUsingIds(loggedInUser.getDataAccessToken(), nameIds, 1000);
                modelMap.addAttribute("node", node);
            } else {
                Set<String> nameNames = new HashSet<>();
                Collections.addAll(nameNames, namesString);
                TreeNode node = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getJstreeDataForOutputUsingNames(loggedInUser.getDataAccessToken(), nameNames, 1000);
                modelMap.addAttribute("node", node);
            }
            // this jsp has JSTL which will render tree nodes correctly
            return "showdata";
        } else {
            return "redirect:/api/Login";
        }
    }
}