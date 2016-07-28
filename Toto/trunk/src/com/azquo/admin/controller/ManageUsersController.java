package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 24/04/15.
 *
 * User CRUD.
 */
@Controller
@RequestMapping("/ManageUsers")
public class ManageUsersController {
    // TODO : break up into separate functions

    private static final Logger logger = Logger.getLogger(ManageUsersController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "endDate", required = false) String endDate
            , @RequestParam(value = "email", required = false) String email
            , @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "status", required = false) String status
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "submit", required = false) String submit
    ) throws Exception

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (deleteId != null && NumberUtils.isDigits(deleteId)){
                AdminService.deleteUserById(Integer.parseInt(deleteId), loggedInUser);
            }
            if (editId != null && NumberUtils.isDigits(editId)){
                User toEdit = AdminService.getUserById(Integer.parseInt(editId), loggedInUser);
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                if (submit != null){
                    if (endDate == null || endDate.isEmpty()){
                        error.append("End date required (yyyy-MM-dd)<br/>");
                    } else {
                        try{
                            formatter.parse(endDate);
                        } catch (DateTimeParseException e) {
                            error.append("End date format not yyyy-MM-dd<br/>");
                        }
                    }
                    if (email == null || email.isEmpty()){
                        error.append("Email required<br/>");
                    }
                    email = email.trim();
                    if (toEdit == null && UserDAO.findByEmail(email) != null){
                        error.append("User Exists<br/>");
                    }
                    if (name == null || name.isEmpty()){
                        error.append("Name required<br/>");
                    }
                    if (toEdit == null && (password == null || password.isEmpty())){
                        error.append("Password required<br/>");
                    }
                    if (error.length() == 0){
                        assert endDate != null;
                        // then store, it might be new
                        if (toEdit == null){
                            // Have to use  a LocalDate on the parse which is annoying http://stackoverflow.com/questions/27454025/unable-to-obtain-localdatetime-from-temporalaccessor-when-parsing-localdatetime
                            AdminService.createUser(email, name, LocalDate.parse(endDate, formatter).atStartOfDay(), status, password, loggedInUser);
                        } else {
                            toEdit.setEndDate(LocalDate.parse(endDate, formatter).atStartOfDay());
                            toEdit.setEmail(email);
                            toEdit.setName(name);
                            toEdit.setStatus(status);
                            if (password != null && !password.isEmpty()){
                                final String salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                                toEdit.setSalt(salt);
                                toEdit.setPassword(AdminService.encrypt(password, salt));
                            }
                            UserDAO.store(toEdit);
                        }
                        model.put("users", AdminService.getUserListForBusiness(loggedInUser));
                        return "manageusers";
                    } else {
                        model.put("error", error.toString());
                    }
                    model.put("id", editId);
                    model.put("endDate", endDate);
                    model.put("email", email);
                    model.put("name", name);
                    model.put("status", status);
                } else {
                    if (toEdit != null){
                        model.put("id", toEdit.getId());
                        model.put("endDate", formatter.format(toEdit.getEndDate()));
                        model.put("email", toEdit.getEmail());
                        model.put("name", toEdit.getName());
                        model.put("status", toEdit.getStatus());
                    } else {
                        model.put("id", "0");
                    }
                }
                return "edituser";
            }
            final List<User> userListForBusiness = AdminService.getUserListForBusiness(loggedInUser);
            model.put("users", userListForBusiness);
            if (userListForBusiness.size() > 1){
                model.put("showDownload", true);
            }
            return "manageusers";
        }
    }
}