package com.azquo.admin.controller;

import com.azquo.admin.BackupService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.DownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
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
    ) throws Exception {
        final LoggedInUser  loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) && NumberUtils.isNumber(id)) {
            Database db = DatabaseDAO.findById(Integer.parseInt(id));
            if (db != null) {
                DatabaseServer dbs = DatabaseServerDAO.findById(db.getDatabaseServerId());
                loggedInUser.setDatabaseWithServer(dbs, db);
                File tempzip = BackupService.createDBandReportsBackup(loggedInUser);
                DownloadController.streamFileToBrowser(Paths.get(tempzip.getAbsolutePath()), response, db.getName() + ".zip");
            }
        }
    }
}
