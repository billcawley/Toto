package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.user.Permission;
import com.azquo.admin.user.User;
import com.azquo.spreadsheet.LoggedInConnection;
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

/**
 * Created by cawley on 24/04/15.
 *
 * new basic HTML admin, manage permissions. Need to add pagination.
 */
@Controller
@RequestMapping("/ManagePermissions")
public class ManagePermissionsController {
    @Autowired
    private AdminService adminService;

    private static final Logger logger = Logger.getLogger(ManagePermissionsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "startDate", required = false) String startDate
            , @RequestParam(value = "endDate", required = false) String endDate
            , @RequestParam(value = "userId", required = false) String userId
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "readList", required = false) String readList
            , @RequestParam(value = "writeList", required = false) String writeList
            , @RequestParam(value = "submit", required = false) String submit
    ) throws Exception

    {
        LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);
        if (loggedInConnection == null || !loggedInConnection.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            if (deleteId != null && NumberUtils.isDigits(deleteId)){
                adminService.deletePermissionById(Integer.parseInt(deleteId), loggedInConnection);
            }


            if (editId != null && NumberUtils.isDigits(editId)){
                Permission toEdit = adminService.getPermissionById(Integer.parseInt(editId), loggedInConnection);
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                if (submit != null){
                    if (startDate == null || startDate.isEmpty()){
                        error.append("Start date required (yyyy-MM-dd)<br/>");
                    } else {
                        try{
                            formatter.parse(startDate);
                        } catch (DateTimeParseException e) {
                            error.append("Start date format not yyyy-MM-dd<br/>");
                        }
                    }
                    if (endDate == null || endDate.isEmpty()){
                        error.append("End date required (yyyy-MM-dd)<br/>");
                    } else {
                        try{
                            formatter.parse(endDate);
                        } catch (DateTimeParseException e) {
                            error.append("End date format not yyyy-MM-dd<br/>");
                        }
                    }
                    if (userId == null || !NumberUtils.isNumber(userId)){
                        error.append("User Id Required<br/>");
                    } else {
                        User user = adminService.getUserById(Integer.parseInt(userId), loggedInConnection);
                        if (user == null){
                            error.append("Applicable User Id Required<br/>");
                        }
                    }
                    if (databaseId == null || !NumberUtils.isNumber(databaseId)){
                        error.append("Database Id Required<br/>");
                    } else {
                        Database database = adminService.getDatabaseById(Integer.parseInt(databaseId), loggedInConnection);
                        if (database == null){
                            error.append("Applicable Database Id Required<br/>");
                        }
                    }
                    // no error checking on read and write list??
                    if (error.length() == 0){
                        // then store, it might be new
                        if (toEdit == null){
                            // Have to use  alocadate on the parse which is annoying http://stackoverflow.com/questions/27454025/unable-to-obtain-localdatetime-from-temporalaccessor-when-parsing-localdatetime
                            adminService.createUserPermission(Integer.parseInt(userId), Integer.parseInt(databaseId), LocalDate.parse(startDate, formatter).atStartOfDay()
                                    ,LocalDate.parse(startDate, formatter).atStartOfDay(), readList, writeList, loggedInConnection);
                        } else {
                            toEdit.setUserId(Integer.parseInt(userId));
                            toEdit.setDatabaseId(Integer.parseInt(databaseId));
                            toEdit.setStartDate(LocalDate.parse(startDate, formatter).atStartOfDay());
                            toEdit.setEndDate(LocalDate.parse(endDate, formatter).atStartOfDay());
                            toEdit.setReadList(readList);
                            toEdit.setWriteList(writeList);
                            adminService.storePermission(toEdit);
                        }
                        model.put("permissions", adminService.getDisplayPermissionList(loggedInConnection));
                        return "managepermissions";
                    } else {
                        model.put("error", error.toString());
                    }
                    model.put("id", editId);
                    model.put("databaseId", databaseId);
                    model.put("userId", userId);
                    model.put("startDate", startDate);
                    model.put("endDate", endDate);
                    model.put("readList", readList);
                    model.put("writeList", writeList);
                } else {
                    if (toEdit != null){
                        model.put("id", toEdit.getId());
                        model.put("databaseId", toEdit.getDatabaseId());
                        model.put("userId", toEdit.getUserId());
                        model.put("startDate", formatter.format(toEdit.getStartDate()));
                        model.put("endDate", formatter.format(toEdit.getEndDate()));
                        model.put("readList", toEdit.getReadList());
                        model.put("writeList", toEdit.getWriteList());
                    } else {
                        model.put("id", "0");
                    }
                }
                model.put("databases", adminService.getDatabaseListForBusiness(loggedInConnection));
                model.put("users", adminService.getUserListForBusiness(loggedInConnection));
                return "editpermission";
            }


            model.put("permissions", adminService.getDisplayPermissionList(loggedInConnection));
            return "managepermissions";
        }
    }
}
