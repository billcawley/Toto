package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.spreadsheet.LoggedInUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/*
 * Bring up the user log, mainly to allow cancellation of anything running
 */

@Controller
@RequestMapping("/UserLog")
public class UserLogController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            return "redirect:/api/Login";
        }
        AdminService.setBanner(model,loggedInUser);
        return "userlog";
    }

}