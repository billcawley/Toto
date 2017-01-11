package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by edward on 24/10/16.
 *
 * Simple DB copying functionality
 */
@Controller
@RequestMapping("/CopyDatabase")
public class CopyDatabaseController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "copyName", required = false) String copyName
    )
    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            StringBuilder error = new StringBuilder();
            try {
                Database databaseById = null;
                if (databaseId != null && NumberUtils.isNumber(databaseId)) {
                    databaseById = AdminService.getDatabaseById(Integer.parseInt(databaseId), loggedInUser);
                }
                if (databaseById == null || (loggedInUser.getUser().isDeveloper() && databaseById.getUserId() != loggedInUser.getUser().getId())){
                    error.append("Bad database Id.");
                } else {
                    model.put("database", databaseById.getName());
                    model.put("databaseId", databaseById.getId());
                }
                if (copyName != null && !copyName.isEmpty()){
                    Database existing = DatabaseDAO.findForNameAndBusinessId(copyName, loggedInUser.getUser().getBusinessId());
                    if (existing != null) {
                        error.append("A database with that name already exists");
                    } else {
                        AdminService.copyDatabase(loggedInUser, databaseById, copyName);
                        return "redirect:/api/ManageDatabases";
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("developer", loggedInUser.getUser().isDeveloper());
            return "copydatabase";
        } else {
            return "redirect:/api/Login";
        }
    }

}
