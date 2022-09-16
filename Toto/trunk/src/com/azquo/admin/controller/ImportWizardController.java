package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 21/07/16.
 * <p>
 * Changed 03/10/2018 to use new backup system - old MySQL based one obsolete
 * <p>
 * todo - spinning cog on restoring
 */
@Controller
@RequestMapping("/ImportWizard")
public class ImportWizardController {


//    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            String submit = request.getParameter("submit");
            if ("import".equals(submit)) {
                try {
                    ImportWizard.createDB(loggedInUser);
                    return "redirect:/api/ReportWizard";
                } catch (Exception e) {
                    String err = e.getMessage();
                    int ePos = err.indexOf("Exception:");
                    if (ePos >= 0) {
                        err = err.substring(ePos + 10);
                    }
                    model.put("error", err);
                    return "importwizard";
                }
            }
            String database = request.getParameter("database");
            if (database != null) {
                try {
                    LoginService.switchDatabase(loggedInUser, database);
                } catch (Exception e) {
                    model.put("error", "No such database: " + database);
                    return "importwizard";
                }
            }
            AdminService.setBanner(model, loggedInUser);
            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            List<String> databases = new ArrayList<>();
            if (databaseList != null) {
                for (Database db : databaseList) {
                    databases.add(db.getName());
                }
            }
            databases.add("New database");
            model.put("databases", databases);
            String selectedDatabase = "";
            if (loggedInUser.getDatabase() != null) {
                selectedDatabase = loggedInUser.getDatabase().getName();
            }

            return "importwizard";
        } else {
            return "redirect:/api/Login";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile

    ) {

        StringBuffer error = new StringBuffer();
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        }
        WizardInfo wizardInfo;
            if (uploadFile != null && !uploadFile.isEmpty()) {
            //do we need to move it??
            try {
                String database = request.getParameter("database");
                if (database==null){
                    return "importWizard";
                }
                LoginService.switchDatabase(loggedInUser,database);
                String fileName = uploadFile.getOriginalFilename();
                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                uploadFile.transferTo(moved);
                if (fileName.toLowerCase(Locale.ROOT).contains(".xls")) {
                    Map<String, WizardField> wizardFields = new LinkedHashMap<>();
                    wizardInfo = new WizardInfo(fileName, null);
                    int lineCount = ImportWizard.readBook(moved, wizardFields, false, wizardInfo.getLookups());
                     wizardInfo.setFields(wizardFields);
                    wizardInfo.setLineCount(lineCount);
                    loggedInUser.setWizardInfo(wizardInfo);

                } else if (fileName.toLowerCase(Locale.ROOT).contains(".json") || fileName.toLowerCase(Locale.ROOT).contains(".xml")) {
                    try {
                        String data = new String(Files.readAllBytes(Paths.get(moved.getAbsolutePath())), Charset.defaultCharset());
                        JSONObject jsonObject = null;
                        if (moved.getAbsolutePath().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                            jsonObject = XML.toJSONObject(data);
                        } else {
                            if (data.charAt(0) == '[') {
                                data = "{data:" + data + "}";
                            }
                            data = data.replace("\n", "");//remove line feeds
                            jsonObject = new JSONObject(data);
                        }

                        wizardInfo = new WizardInfo(moved.getName(), jsonObject);
                        loggedInUser.setWizardInfo(wizardInfo);
                    } catch (Exception e) {
                        model.put("error", "nothing to read");
                        return "importwizard";
                    }
                } else {
                    Map<String, String> fileNameParameters = new HashMap<>();
                    ImportService.addFileNameParametersToMap(moved.getName(), fileNameParameters);
                    wizardInfo = new WizardInfo(fileName, null);

                    Map<String, WizardField> wizardFields = ImportWizard.readCSVFile(moved.getAbsolutePath(), fileNameParameters.get("fileencoding"));
                      wizardInfo.setFields(wizardFields);
                    for (String field : wizardFields.keySet()) {
                        WizardField wizardField = wizardFields.get(field);
                        wizardInfo.setLineCount(wizardField.getValuesFound().size());
                        break;
                    }

                    loggedInUser.setWizardInfo(wizardInfo);
                }


            } catch (Exception e) {
                model.put("error", e.getMessage());
                return "importwizard";
            }


        }
        return "importwizard";

    }
}





