package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.user.Permission;
import com.azquo.admin.user.PermissionDAO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * Permissions CRUD. Need to add pagination though now we'd need to check with Visual Code about this.
 */
@Controller
@RequestMapping("/ManagePermissions")
public class ManagePermissionsController {
    @Autowired
    private AdminService adminService;
    @Autowired
    private PermissionDAO permissionDAO;
    @Autowired
    private UserDAO userDAO;

    private static final Logger logger = Logger.getLogger(ManagePermissionsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "reportId", required = false) String reportId
            , @RequestParam(value = "userId", required = false) String userId
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "readList", required = false) String readList
            , @RequestParam(value = "writeList", required = false) String writeList
            , @RequestParam(value = "submit", required = false) String submit
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (deleteId != null && NumberUtils.isDigits(deleteId)) {
                adminService.deletePermissionById(Integer.parseInt(deleteId), loggedInUser);
            }
            if (editId != null && NumberUtils.isDigits(editId)) {
                Permission toEdit = adminService.getPermissionById(Integer.parseInt(editId), loggedInUser);
                    // ok check to see if data was submitted
                    StringBuilder error = new StringBuilder();
                    if (submit != null) {
                        if (userId == null || !NumberUtils.isNumber(userId)) {
                            error.append("User Id Required<br/>");
                        } else {
                            User user = adminService.getUserById(Integer.parseInt(userId), loggedInUser);
                            if (user == null) {
                                error.append("Applicable User Id Required<br/>");
                            }
                        }
                        if (databaseId == null || !NumberUtils.isNumber(databaseId)) {
                            error.append("Database Id Required<br/>");
                        } else {
                            Database database = adminService.getDatabaseById(Integer.parseInt(databaseId), loggedInUser);
                            if (database == null) {
                                error.append("Applicable Database Id Required<br/>");
                            }
                        }
                        List<OnlineReport> reports = new ArrayList<>();
                        if (toEdit == null){ // then it could be multiple
                            if (reportId != null && reportId.length() > 0){
                                StringTokenizer st = new StringTokenizer(reportId, ",");
                                while (st.hasMoreTokens()){ // will exception if someone plays silly buggers
                                    OnlineReport onlineReport = adminService.getReportById(Integer.parseInt(st.nextToken()), loggedInUser);
                                    if (onlineReport != null){
                                        reports.add(onlineReport);
                                    }
                                }
                            }
                        } else { // editing a simgle
                            if (reportId == null || !NumberUtils.isNumber(reportId)) {
                                error.append("Report Id Required<br/>");
                            } else {
                                OnlineReport onlineReport = adminService.getReportById(Integer.parseInt(reportId), loggedInUser);
                                if (onlineReport == null) {
                                    error.append("Applicable Repor Id Required<br/>");
                                }
                            }
                        }
                        // no error checking on read and write list??
                        if (error.length() == 0) {
                            // to keep intelliJ happy, I'm not sure if this is a good idea or not? I suppose protects against logic above being changed unpredictably
                            assert userId != null;
                            assert databaseId != null;
                            assert reportId != null;
                            // then store, it might be new
                            if (toEdit == null) {
                                    for (OnlineReport report : reports) {
                                        adminService.createUserPermission(report.getId(), Integer.parseInt(userId), Integer.parseInt(databaseId),
                                                readList, writeList, loggedInUser);
                                    }
                                if (reports.isEmpty()){ // we'll allow a blank one
                                    adminService.createUserPermission(0, Integer.parseInt(userId), Integer.parseInt(databaseId),
                                            readList, writeList, loggedInUser);
                                }
                            } else {
                                User check = userDAO.findById(toEdit.getUserId());
                                if (check.getBusinessId() == loggedInUser.getUser().getBusinessId()){
                                    toEdit.setReportId(Integer.parseInt(reportId));
                                    toEdit.setUserId(Integer.parseInt(userId));
                                    toEdit.setDatabaseId(Integer.parseInt(databaseId));
                                    toEdit.setReadList(readList);
                                    toEdit.setWriteList(writeList);
                                    permissionDAO.store(toEdit);
                                }
                            }
                            model.put("permissions", adminService.getDisplayPermissionList(loggedInUser));
                            return "managepermissions";
                        } else {
                            model.put("error", error.toString());
                        }
                        model.put("id", editId);
                        model.put("databaseId", databaseId);
                        model.put("userId", userId);
                        model.put("readList", readList);
                        model.put("writeList", writeList);
                } else {
                    if (toEdit != null) {
                        User check = userDAO.findById(toEdit.getUserId());
                        if (check.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                            model.put("id", toEdit.getId());
                            model.put("reportId", toEdit.getReportId());
                            model.put("databaseId", toEdit.getDatabaseId());
                            model.put("userId", toEdit.getUserId());
                            model.put("readList", toEdit.getReadList());
                            model.put("writeList", toEdit.getWriteList());
                        }
                        model.put("multiple", "");
                    } else {
                        model.put("id", "0");
                        model.put("multiple", "multiple");
                    }
                }
                model.put("databases", adminService.getDatabaseListForBusiness(loggedInUser));
                model.put("reports", adminService.getReportListForBusiness(loggedInUser));
                model.put("users", adminService.getUserListForBusiness(loggedInUser));
                return "editpermission";
            }
            model.put("permissions", adminService.getDisplayPermissionList(loggedInUser));
            return "managepermissions";
        }
    }
}
