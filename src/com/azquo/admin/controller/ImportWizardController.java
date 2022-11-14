package com.azquo.admin.controller;

import com.azquo.dataimport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2022 Azquo Holdings Ltd.
 * <p>
 * <p>
 */
@Controller
@RequestMapping("/ImportWizard")
public class ImportWizardController {


//    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            String importVal = request.getParameter("importbutton");
            if ("import".equals(importVal)) {
                try {
                    ImportWizard.importFromFile(loggedInUser);
                    if (loggedInUser.getWizardInfo().getMatchFields()!=null) {
                        ImportService.importTheFile(loggedInUser, loggedInUser.getWizardInfo().getImportFile(), request.getSession(), null,"Import Schedule Update");
                        return "redirect:/api/ManageReports";
                    }else{
                        return "redirect:/api/ReportWizard";
                    }
                } catch (Exception e) {
                    String err = e.getMessage();
                    int ePos = err.indexOf("Exception:");
                    if (ePos >= 0) {
                        err = err.substring(ePos + 10);
                    }
                    model.put("error", err);
                    return "importwizard";
                }
            }

            return "importwizard";
        } else {
            return "redirect:/api/Login";
        }
    }


 }





