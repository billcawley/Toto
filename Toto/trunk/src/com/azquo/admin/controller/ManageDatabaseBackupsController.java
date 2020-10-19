package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.dataimport.DBCron;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 21/07/16.
 * <p>
 * Changed 03/10/2018 to use new backup system - old MySQL based one obsolete
 *
 * todo - spinning cog on restoring
 */
@Controller
@RequestMapping("/ManageDatabaseBackups")
public class ManageDatabaseBackupsController {

//    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    // to play nice with velocity or JSP - so I don't want it to be private as Intellij suggests
    public static class DisplayBackup {
        private final String name;
        private final String date;

        public DisplayBackup(String name, String date) {
            this.name = name;
            this.date = date;
        }

        public String getName() {
            return name;
        }

        public String getDate() {
            return date;
        }
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "restoreBackup", required = false) String restoreBackup
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            StringBuilder error = new StringBuilder();
            try {
                Database databaseById = null;
                if (NumberUtils.isNumber(databaseId)) {
                    databaseById = AdminService.getDatabaseByIdWithBasicSecurityCheck(Integer.parseInt(databaseId), loggedInUser);
                }
                if (databaseById == null) {
                    error.append("Bad database Id.");
                } else {
                    model.put("database", databaseById.getName());
                    model.put("databaseId", databaseById.getId());
                    String dbBackupsDirectory = DBCron.getDBBackupsDirectory(databaseById);
                    // ok backup directory is there!
                    List<DisplayBackup> displayBackupList = new ArrayList<>();
                    File finalDir = new File(dbBackupsDirectory);
                    boolean restoreBackupOk = false;
                    for (File file : finalDir.listFiles()) {
                        if (file.getName().equals(restoreBackup)){
                            restoreBackupOk = true;
                        }
                        displayBackupList.add(new DisplayBackup(file.getName(), df.format(file.lastModified())));
                    }
                    Collections.sort(displayBackupList, Comparator.comparing(displayBackup -> displayBackup.date));
                    Collections.reverse(displayBackupList);

                    // todo - factor, this could cause problems!
                    if (restoreBackupOk) {
                        loggedInUser.setDatabaseWithServer(DatabaseServerDAO.findById(databaseById.getDatabaseServerId()), databaseById);
                        BackupService.loadDBBackup(loggedInUser, new File(dbBackupsDirectory + "/" + restoreBackup), null, new StringBuilder(), true);
                    }
                    // todo - reverse date sort!
                    model.put("developer", loggedInUser.getUser().isDeveloper());
                    model.put("backups", displayBackupList);
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("developer", loggedInUser.getUser().isDeveloper());
            AdminService.setBanner(model, loggedInUser);
            return "managedatabasebackups";
        } else {
            return "redirect:/api/Login";
        }
    }
}