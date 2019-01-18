package com.azquo.admin.controller;

import com.azquo.admin.database.PendingUpload;
import com.azquo.admin.database.PendingUploadDAO;
import com.azquo.admin.database.UploadRecord;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by edward on 25/10/18.
 * <p>
 * For pending uploads
 */
@Controller
@RequestMapping("/ImportResults")
public class ImportResultsController {

    @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "urid", required = false) String urid
            , @RequestParam(value = "count", required = false) String count
    )
    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        String result = "";
        if (loggedInUser != null && loggedInUser.getUser().isAdministrator()) {
            if (urid != null && urid.length() > 0){
                UploadRecord ur = UploadRecordDAO.findById(Integer.parseInt(urid));
                if (ur.getBusinessId() == loggedInUser.getUser().getBusinessId()){ // ok we're allowed to see it
                    result = ur.getComments();
                }
                modelMap.addAttribute("memoryReport", result);

            } else if (count != null && count.length() > 0){
                String sessionCache = (String) request.getSession().getAttribute("resultCache" + count);
                if (sessionCache != null){
                    modelMap.addAttribute("memoryReport", sessionCache);
                }
            }else {
                PendingUpload pendingUpload = PendingUploadDAO.findById(Integer.parseInt(id));
                if (pendingUpload.getBusinessId() == loggedInUser.getUser().getBusinessId()){ // ok we're allowed to see it
                    result = pendingUpload.getImportResult();
                }
                modelMap.addAttribute("memoryReport", result);

            }
            return "memoryreport";
        } else {
            return "redirect:/api/Login";
        }
    }
}