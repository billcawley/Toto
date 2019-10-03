package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.DownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Paths;

/*
Created by EFC 11/09/2018

Download a database and its reports
 */

@Controller
@RequestMapping("/DownloadBackup")

public class DownloadBackupController {

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "justreports", required = false) String justreports
    ) throws Exception {
        final LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null) {
            request.getSession().setAttribute("working", "working");
            try {
                Database db = AdminService.getDatabaseByIdWithBasicSecurityCheck(Integer.parseInt(id), loggedInUser);
                if (db != null) {
                    DatabaseServer dbs = DatabaseServerDAO.findById(db.getDatabaseServerId());
                    loggedInUser.setDatabaseWithServer(dbs, db);
                    File tempzip = BackupService.createDBandReportsAndTemplateBackup(loggedInUser, "true".equals(justreports));
                    DownloadController.streamFileToBrowser(Paths.get(tempzip.getAbsolutePath()), response, db.getName() + ".zip");
                }
                request.getSession().removeAttribute("working");
            } catch (Exception e) {
                // what to do with the error? todo
                request.getSession().removeAttribute("working");
                e.printStackTrace();
            }
        }
    }
}