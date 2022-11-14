package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.onlinereport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * User CRUD.
 */
@Controller
@RequestMapping("/ManageDatabaseConnections")
public class ManageExternalDatabaseConnectionsController {

    //    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "editFOId", required = false) String editFOId
            , @RequestParam(value = "deleteFOId", required = false) String deleteFOId
            , @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "connectionString", required = false) String connectionString
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "submit", required = false) String submit
            , @RequestParam(value = "submitFO", required = false) String submitFO
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (NumberUtils.isDigits(deleteId)) {
                AdminService.deleteExternalDatabaseConnectionById(Integer.parseInt(deleteId), loggedInUser);
            }
            if (NumberUtils.isDigits(deleteFOId)) {
                AdminService.deleteFileOutputConfigById(Integer.parseInt(deleteFOId), loggedInUser);
            }
            if (NumberUtils.isDigits(editId)) {
                ExternalDatabaseConnection toEdit = AdminService.getExternalDatabaseConnectionById(Integer.parseInt(editId), loggedInUser);
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                if (submit != null) {
                    if (toEdit == null && ExternalDatabaseConnectionDAO.findForNameAndBusinessId(name, loggedInUser.getUser().getBusinessId()) != null) {
                        error.append("External Connection with that name already exists<br/>");
                    }
                    if (toEdit == null && (name == null || name.isEmpty())) {
                        error.append("Name required<br/>");
                    }
                    if (connectionString == null || connectionString.isEmpty()) {
                        error.append("Connection string required<br/>");
                    }
                    if (error.length() == 0) {
                        // then store, it might be new
                        if (toEdit == null) {
                            ExternalDatabaseConnectionDAO.store(new ExternalDatabaseConnection(0, loggedInUser.getBusiness().getId(), name, connectionString, user, password, database));
                        } else {
                            toEdit.setConnectionString(connectionString);
                            toEdit.setName(name);
                            toEdit.setUser(user);
                            toEdit.setPassword(password);
                            toEdit.setDatabase(database);
                            ExternalDatabaseConnectionDAO.store(toEdit);
                        }
                        model.put("connections", AdminService.getExternalDatabaseConnectionListForBusinessWithBasicSecurity(loggedInUser));
                        model.put("fileOutputs", AdminService.getFileOutputConfigListForBusinessWithBasicSecurity(loggedInUser));
                        AdminService.setBanner(model, loggedInUser);
                        if (request.getSession().getAttribute("newdesign") != null) {
                            return "manageexternaldatabaseconnectionsnew";
                        }
                        return "manageexternaldatabaseconnections";
                    } else {
                        model.put("error", error.toString());
                    }
                    model.put("id", editId);
                    model.put("name", name);
                    model.put("connectionString", connectionString);
                    model.put("user", user);
                    model.put("password", password);
                    model.put("database", database);
                } else {
                    if (toEdit != null) {
                        model.put("id", toEdit.getId());
                        model.put("name", toEdit.getName());
                        model.put("connectionString", toEdit.getConnectionString());
                        model.put("user", toEdit.getUser());
                        model.put("password", toEdit.getPassword());
                        model.put("database", toEdit.getDatabase());
                    } else {
                        model.put("id", "0");
                    }
                }


                AdminService.setBanner(model, loggedInUser);
                return "editexternaldatabaseconnectionnew";
            }


            if (NumberUtils.isDigits(editFOId)) {
                FileOutputConfig toEdit = AdminService.getFileOutputConfigById(Integer.parseInt(editFOId), loggedInUser);
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                if (submit != null) {
                    if (toEdit == null && FileOutputConfigDAO.findForNameAndBusinessId(name, loggedInUser.getUser().getBusinessId()) != null) {
                        error.append("File Output with that name already exists<br/>");
                    }
                    if (toEdit == null && (name == null || name.isEmpty())) {
                        error.append("Name required<br/>");
                    }
                    if (connectionString == null || connectionString.isEmpty()) {
                        error.append("Connection string required<br/>");
                    }
                    if (error.length() == 0) {
                        // then store, it might be new
                        if (toEdit == null) {
                            FileOutputConfigDAO.store(new FileOutputConfig(0, loggedInUser.getBusiness().getId(), name, connectionString, user, password));
                        } else {
                            toEdit.setConnectionString(connectionString);
                            toEdit.setName(name);
                            toEdit.setUser(user);
                            toEdit.setPassword(password);
                            FileOutputConfigDAO.store(toEdit);
                        }
                        model.put("connections", AdminService.getExternalDatabaseConnectionListForBusinessWithBasicSecurity(loggedInUser));
                        model.put("fileOutputs", AdminService.getFileOutputConfigListForBusinessWithBasicSecurity(loggedInUser));
                        AdminService.setBanner(model, loggedInUser);
                        if (request.getSession().getAttribute("newdesign") != null) {
                            return "manageexternaldatabaseconnectionsnew";
                        }
                        return "manageexternaldatabaseconnections";
                    } else {
                        model.put("error", error.toString());
                    }
                    model.put("id", editId);
                    model.put("name", name);
                    model.put("connectionString", connectionString);
                    model.put("user", user);
                    model.put("password", password);
                } else {
                    if (toEdit != null) {
                        model.put("id", toEdit.getId());
                        model.put("name", toEdit.getName());
                        model.put("connectionString", toEdit.getConnectionString());
                        model.put("user", toEdit.getUser());
                        model.put("password", toEdit.getPassword());
                    } else {
                        model.put("id", "0");
                    }
                }
                AdminService.setBanner(model, loggedInUser);
                return "editfileoutputconfig";
            }
            model.put("fileOutputs", AdminService.getFileOutputConfigListForBusinessWithBasicSecurity(loggedInUser));
            model.put("connections", AdminService.getExternalDatabaseConnectionListForBusinessWithBasicSecurity(loggedInUser));
            AdminService.setBanner(model, loggedInUser);

            return "manageexternaldatabaseconnectionsnew";
        }
    }
}
