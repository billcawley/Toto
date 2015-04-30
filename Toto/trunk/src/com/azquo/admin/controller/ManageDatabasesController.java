package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.LoggedInConnection;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by cawley on 24/04/15.
 */
@Controller
@RequestMapping("/ManageDatabases")
public class ManageDatabasesController {
    @Autowired
    private AdminService adminService;
    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "createDatabase", required = false) String createDatabase
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "backupTarget", required = false) String backupTarget
            , @RequestParam(value = "summaryLevel", required = false) String summaryLevel
    )

    {
        LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);

        if (loggedInConnection == null || !loggedInConnection.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            StringBuilder error = new StringBuilder();
            try {
                if (createDatabase != null && !createDatabase.isEmpty()) {
                    adminService.createDatabase(createDatabase, loggedInConnection);
                }
                if (deleteId != null && NumberUtils.isNumber(deleteId)) {
                    adminService.removeDatabaseById(loggedInConnection, Integer.parseInt(deleteId));
                }
                if (backupTarget != null) {
                    adminService.copyDatabase(loggedInConnection, backupTarget, summaryLevel);
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
/*

        if (op.equalsIgnoreCase("upload")) {
            InputStream uploadFile = file.getInputStream();
            String fileName = file.getOriginalFilename();
            importService.importTheFile(loggedInConnection, fileName, uploadFile, "", true, loggedInConnection.getLanguages());
        }

             */
            if (error.length() > 0) {
                model.put("error", error.toString());
            }
            model.put("databases", adminService.getDatabaseListForBusiness(loggedInConnection));
            model.put("uploads", adminService.getUploadRecordsForDisplayForBusiness(loggedInConnection));
            return "managedatabases";
        }
    }

}
