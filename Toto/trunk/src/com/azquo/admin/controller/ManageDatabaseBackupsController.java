package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.util.CommandLineCalls;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 21/07/16.
 *
 * Basic back up and restore databases. Note that this only works if your report and db servers are on the same machine.
 */
@Controller
@RequestMapping("/ManageDatabaseBackups")
public class ManageDatabaseBackupsController {

//    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    // to play nice with velocity or JSP - so I don't want it to be private as Intellij suggests
    public class DisplayBackup {
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
            , @RequestParam(value = "deleteBackup", required = false) String deleteBackup
            , @RequestParam(value = "newBackup", required = false) String newBackup
            , @RequestParam(value = "restoreBackup", required = false) String restoreBackup
    )
    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            StringBuilder error = new StringBuilder();
            try {
                Database databaseById = null;
                if (databaseId != null && NumberUtils.isNumber(databaseId)) {
                    databaseById = AdminService.getDatabaseById(Integer.parseInt(databaseId), loggedInUser);
                }
                if (databaseById == null || (loggedInUser.getUser().isDeveloper() && databaseById.getUserId() != loggedInUser.getUser().getId())){
                    error.append("Bad database Id.");
                } else {
                    model.put("database", databaseById.getName());
                    model.put("databaseId", databaseById.getId());
                    File f = new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + "backups/");
                    if (!f.exists()){
                        if (!f.mkdir()){
                            throw new Exception("unable to make directory : " + f.getPath());
                        }
                    }
                    File finalDir = new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + "backups/" + databaseById.getPersistenceName() + "/");
                    if (!finalDir.exists()){
                        if (!finalDir.mkdir()){
                            throw new Exception("unable to make directory : " + finalDir.getPath());
                        }
                    }
                    if (newBackup != null && !newBackup.isEmpty()){ // since the user has to be admin I'm not sure how much I need to protect against things here
                        newBackup = newBackup.replace(" ", "");
                        newBackup = newBackup.replace("..", "");
                        newBackup = newBackup.replace("/", "");
                        newBackup = newBackup.replace("\\", "");
                        // todo, make this more secure from a unix point of transport?? Also parameters for packet size or whatever. Might be set in my.cnf
                        String dump = "mysqldump  --user='toto' --password='ark'  " + databaseById.getPersistenceName() + " > " + finalDir.getPath() + "/" + newBackup;
                        // for some reason need to use the command array, dunno why
                        String[] cmdarray = {"/bin/sh","-c", dump};
                        CommandLineCalls.runCommand(null, cmdarray, true, null);
                    }

                    if (deleteBackup != null && !deleteBackup.isEmpty()){
                        deleteBackup = deleteBackup.replace(" ", "");
                        deleteBackup = deleteBackup.replace("..", "");
                        deleteBackup = deleteBackup.replace("/", "");
                        deleteBackup = deleteBackup.replace("\\", "");
                        File toDelete = new File(finalDir.getPath() + "/" + deleteBackup);
                        if (!toDelete.delete()){
                            throw new Exception("unable to delete : " + toDelete.getPath());
                        }
                    }

                    if (restoreBackup != null && !restoreBackup.isEmpty()){
                        restoreBackup = restoreBackup.replace(" ", "");
                        restoreBackup = restoreBackup.replace("..", "");
                        restoreBackup = restoreBackup.replace("/", "");
                        restoreBackup = restoreBackup.replace("\\", "");
                        // unload the db first
                        AdminService.unloadDatabase(loggedInUser, databaseById.getId());
                        //todo - clear the tables first??
                        String clear = "mysql --user='toto' --password='ark' -e 'drop table if exists fast_name, fast_value, provenance;' " + databaseById.getPersistenceName();
                        String restore = "mysql --user='toto' --password='ark' " + databaseById.getPersistenceName() + " < " + finalDir.getPath() + "/" + restoreBackup;
                        // for some reason need to use the command array, dunno why
                        String[] cmdarray = {"/bin/sh","-c", clear};
                        CommandLineCalls.runCommand(null, cmdarray, true, null);
                        String[] cmdarray2 = {"/bin/sh","-c", restore};
                        CommandLineCalls.runCommand(null, cmdarray2, true, null);
                    }

                    // ok backup directory is there!
                    List<DisplayBackup> displayBackupList  = new ArrayList<>();
                    for (File file : finalDir.listFiles()){
                        displayBackupList.add(new DisplayBackup(file.getName(), df.format(file.lastModified())));
                    }
                    model.put("developer", loggedInUser.getUser().isDeveloper());
                    model.put("backups", displayBackupList);
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                String exceptionError = error.toString();
                //trim off the javaspeak
                if (exceptionError.contains("error:"))
                    exceptionError = exceptionError.substring(exceptionError.indexOf("error:"));
                model.put("error", exceptionError);
            }
            model.put("developer", loggedInUser.getUser().isDeveloper());
            return "managedatabasebackups";
        } else {
            return "redirect:/api/Login";
        }
    }
}