package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

/**
 * Created by cawley on 24/04/15.
 *
 * New HTML admin, upload files and manage databases
 *
 */
@Controller
@RequestMapping("/ManageDatabases")
public class ManageDatabasesController {
    @Autowired
    private AdminService adminService;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ImportService importService;

    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "createDatabase", required = false) String createDatabase
            , @RequestParam(value = "emptyId", required = false) String emptyId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "backupTarget", required = false) String backupTarget
            , @RequestParam(value = "summaryLevel", required = false) String summaryLevel
    )

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            StringBuilder error = new StringBuilder();
            try {
                if (createDatabase != null && !createDatabase.isEmpty()) {
                    adminService.createDatabase(createDatabase, loggedInUser);
                }
                if (emptyId != null && NumberUtils.isNumber(emptyId)) {
                    adminService.emptyDatabaseById(loggedInUser, Integer.parseInt(emptyId));
                }
                if (deleteId != null && NumberUtils.isNumber(deleteId)) {
                    adminService.removeDatabaseById(loggedInUser, Integer.parseInt(deleteId));
                }
                if (backupTarget != null) {
                    LoggedInUser loggedInUserTarget = loginService.loginLoggedInUser(backupTarget, loggedInUser.getUser().getEmail(), "", "", true); // targetted to destinationDB
                    adminService.copyDatabase(loggedInUser.getDataAccessToken(), loggedInUserTarget.getDataAccessToken(), summaryLevel, loggedInUserTarget.getLanguages());// re languages I should just be followign what was there before . . .
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                model.put("error", error.toString());
            }
            model.put("databases", adminService.getDatabaseListForBusiness(loggedInUser));
            model.put("uploads", adminService.getUploadRecordsForDisplayForBusiness(loggedInUser));
            return "managedatabases";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile

    ){
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (database != null && uploadFile != null) {
                try{
                        loginService.switchDatabase(loggedInUser, database); // could be blank now
                    String fileName = uploadFile.getOriginalFilename();
                    // always move uplaoded files now, they'll need to be transferred to the DB server after code split
                    File moved = new File(spreadsheetService.getHomeDir() + "/temp/" + fileName);
                    uploadFile.transferTo(moved);

                    importService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), "", true, loggedInUser.getLanguages());
                } catch (Exception e){
                    model.put("error", e.getMessage());
                    e.printStackTrace();
                }
            }
            model.put("databases", adminService.getDatabaseListForBusiness(loggedInUser));
            model.put("uploads", adminService.getUploadRecordsForDisplayForBusiness(loggedInUser));
            return "managedatabases";
        }
    }
}
