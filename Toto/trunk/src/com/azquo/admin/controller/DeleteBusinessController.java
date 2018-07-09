package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.StandardDAO;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.azquo.dataimport.ImportService.dbPath;
import static com.azquo.dataimport.ImportService.onlineReportsDir;

/**
 * Created by edward on 05/04/17.
 *
 * Since we can now copy businesses deleting is also helpful. Of course this should be used with caution!
 */
@Controller
@RequestMapping("/DeleteBusiness")
public class DeleteBusinessController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "confirm", required = false) String confirm
    )
    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && loggedInUser.getUser().isAdministrator()) {
            StringBuilder error = new StringBuilder();
            Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
            try {
                if (b.getBusinessName().equals(confirm)){
                    // then we zap this (delete the directory)
                    List<User> users = UserDAO.findForBusinessId(b.getId());
                    for (User user : users){
                        List<UserRegionOptions> forUserId = UserRegionOptionsDAO.findForUserId(user.getId());
                        for (UserRegionOptions userRegionOptions : forUserId){
                            UserRegionOptionsDAO.removeById(userRegionOptions);
                        }
                        List<UserChoice> userChoices = UserChoiceDAO.findForUserId(user.getId());
                        for (UserChoice userChoice : userChoices){
                            UserChoiceDAO.removeById(userChoice);
                        }
                        UserDAO.removeById(user);
                    }
                    for (OnlineReport or : OnlineReportDAO.findForBusinessId(b.getId())){
                        AdminService.removeReportById(loggedInUser, or.getId());
                    }
                    for (Database database : DatabaseDAO.findForBusinessId(b.getId())){
                        AdminService.removeDatabaseById(loggedInUser, database.getId());
                    }
                    BusinessDAO.removeById(b);
                    String fullPath = SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory();
                    File file = new File(fullPath);
                    if (file.exists() && file.isDirectory()) {
                        FileUtils.deleteDirectory(file);
                    }
                    return "redirect:/api/Login?logoff=true";
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("businessname", b.getBusinessName());
            AdminService.setBanner(model,loggedInUser);
            return "deletebusiness";
        } else {
            return "redirect:/api/Login";
        }
    }
}
