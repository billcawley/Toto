package com.azquo.admin.controller;

import com.azquo.memorydb.TreeNode;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by bill on 05/08/15.
 */

@Controller
@RequestMapping("/Showdata")

public class ShowdataController {

    @Autowired
    SpreadsheetService spreadsheetService;

      @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "chosen", required = false) String chosen
    ) throws Exception

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            TreeNode node = spreadsheetService.getTreeNode(loggedInUser, chosen, 1000);//1000 is the number of items to show before showing 'more...'
            modelMap.addAttribute("node", node);
            return "showdata";
        }
    }
}