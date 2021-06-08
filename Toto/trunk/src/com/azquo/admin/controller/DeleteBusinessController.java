package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Comment;
import com.azquo.admin.database.CommentDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.UserActivity;
import com.azquo.admin.onlinereport.UserActivityDAO;
import com.azquo.admin.user.*;
import com.azquo.dataimport.ImportTemplate;
import com.azquo.dataimport.ImportTemplateDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.azquo.dataimport.ImportService.dbPath;
import static com.azquo.dataimport.ImportService.onlineReportsDir;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 09/07/15.
 * <p/>
 * A simple page to create a new business. Currently for internal use, it has not been beautified.
 */
@Controller
@RequestMapping("/DeleteBusiness")
public class DeleteBusinessController {

    //private static final Logger logger = Logger.getLogger(ManageUsersController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpSession session
            , @RequestParam(value = "userId", required = false) String userId
    ) throws Exception {
        LoggedInUser loggedInUser1 = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser1 == null || !loggedInUser1.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (session.getAttribute(LoginController.LOGGED_IN_USERS_SESSION) != null) { // I won't let a user delete the last business they're a part of if that makes sense
                List<LoggedInUser> loggedInUsers = (List<LoggedInUser>) session.getAttribute(LoginController.LOGGED_IN_USERS_SESSION);
                if (loggedInUsers.size() > 1){
                    List<User> usersToShow = new ArrayList<>();
                    for (LoggedInUser l : loggedInUsers) {
                        usersToShow.add(l.getUser());
                    }
                    LoggedInUser relevantLIU = null;
                    if (NumberUtils.isNumber(userId)) {
                        for (LoggedInUser loggedInUser : loggedInUsers) {
                            if (loggedInUser.getUser().getId() == Integer.parseInt(userId)) {
                                relevantLIU = loggedInUser;
                                // then delete the business!
                                // delete all databases, all reports, all import templates, users, useractivity, usert choice
                                // , user region options, comments, final directory
                                // the business itself . .
                                List<Database> databaseListForBusinessWithBasicSecurity = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
                                for (Database database : databaseListForBusinessWithBasicSecurity){
                                    AdminService.removeDatabaseByIdWithBasicSecurity(loggedInUser, database.getId());
                                }
                                List<OnlineReport> reportList = AdminService.getReportList(loggedInUser, false);
                                for (OnlineReport report : reportList){
                                    AdminService.removeReportByIdWithBasicSecurity(loggedInUser, report.getId());
                                }
                                List<ImportTemplate> forBusinessId = ImportTemplateDAO.findForBusinessId(loggedInUser.getBusiness().getId());
                                for (ImportTemplate importTemplate : forBusinessId){
                                    AdminService.removeImportTemplateByIdWithBasicSecurity(loggedInUser, importTemplate.getId());
                                }
                                List<User> userListForBusinessWithBasicSecurity = AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser);
                                // todo - factor this off? I don't think the normal user delete is this thorough
                                for (User user : userListForBusinessWithBasicSecurity){
                                    List<UserActivity> forUserAndBusinessId = UserActivityDAO.findForUserAndBusinessId(loggedInUser.getBusiness().getId(), user.getEmail(), 0, 1000);
                                    while (forUserAndBusinessId.size() > 0){
                                        for (UserActivity userActivity : forUserAndBusinessId){
                                            UserActivityDAO.removeById(userActivity);
                                        }
                                        forUserAndBusinessId = UserActivityDAO.findForUserAndBusinessId(loggedInUser.getBusiness().getId(), loggedInUser.getUser().getEmail(), 0, 1000);
                                    }
                                    List<UserChoice> forUserId = UserChoiceDAO.findForUserId(user.getId());
                                    for (UserChoice userChoice : forUserId){
                                        UserChoiceDAO.removeById(userChoice);
                                    }
                                    List<UserRegionOptions> forUserId1 = UserRegionOptionsDAO.findForUserId(user.getId());
                                    for (UserRegionOptions userRegionOptions : forUserId1){
                                        UserRegionOptionsDAO.removeById(userRegionOptions);
                                    }

                                    UserDAO.removeById(user);
                                }
                                List<Comment> forBusinessId1 = CommentDAO.findForBusinessId(loggedInUser.getBusiness().getId());
                                for (Comment comment : forBusinessId1){
                                    CommentDAO.removeById(comment);
                                }
                                BusinessDAO.removeById(loggedInUser.getBusiness());
                                Path businessPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory());
                                FileUtils.deleteDirectory(businessPath.toFile());
                            }
                        }
                    }
                    if (relevantLIU != null){
                        loggedInUsers.remove(relevantLIU);
                        request.getSession().setAttribute(LoginController.LOGGED_IN_USER_SESSION, loggedInUsers.get(0));
                        return "redirect:/api/Login?select=true";
                    }
                    model.put("users", usersToShow);
                }
            }
            return "deletebusiness";
        }
    }
}