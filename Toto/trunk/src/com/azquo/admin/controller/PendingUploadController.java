package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.UploadedFile;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.security.access.method.P;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * User CRUD.
 */
@Controller
@RequestMapping("/PendingUpload")
public class PendingUploadController {

    public static final String PENDINGREADY = "PENDINGREADY";
    public static final String PARAMS = "PARAMS";

//    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "submit", required = false) String submit
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else if (NumberUtils.isDigits(id)){
            StringBuilder maintext = new StringBuilder();
            AdminService.setBanner(model,loggedInUser);
            final PendingUpload pu = PendingUploadDAO.findById(Integer.parseInt(id));
            HttpSession session = request.getSession();
            // todo - pending upload security for non admin users?
            if (pu.getBusinessId() == loggedInUser.getUser().getBusinessId()){
                model.put("id", pu.getId());
                model.put("filename", pu.getFileName());
                if (NumberUtils.isDigits(databaseId)){
                    pu.setDatabaseId(Integer.parseInt(databaseId));
                    PendingUploadDAO.store(pu);
                }

                // before starting an import thread check we have database and parameters
                Database database = DatabaseDAO.findById(pu.getDatabaseId());
                if (database == null){
                    model.put("dbselect", "true");
                    model.put("databases", AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser));
                    return "pendingupload";
                } else {
                    model.put("database", database.getName());
                }

                if (session.getAttribute(PENDINGREADY) != null){
                    session.removeAttribute(PENDINGREADY);
                    @SuppressWarnings("unchecked")
                    List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
                    if (importResult != null) {
                        request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULT);
                        maintext.append(ManageDatabasesController.formatUploadedFiles(importResult, false));
                    }
                    // need to jam in the import result, need better feedback than before
                    model.put("maintext", maintext.toString());
                    return "pendingupload";
                }

                // todo - get from file name based off lookup and zap from session as appropriate. Also auto populate dropdowns - applicable?
                final HashMap<String, String> params;
                // old parameters source, will change
                if (session.getAttribute(PARAMS + pu.getId()) == null){
                    params = new HashMap<>();
                    Enumeration<String> rparams = request.getParameterNames();
                    while (rparams.hasMoreElements()) {
                        String param = rparams.nextElement();
                        // todo - remove string literals
                        if (param.startsWith("param-")) {
//                                    System.out.println("param : " + param.substring("pendingupload-".length(), param.length() - ("-" + pendingUpload.getId()).length()) + " value : " + request.getParameter(param));
                            if (!"N/A".equals(request.getParameter(param))) {
                                params.put(param.substring("param-".length()), request.getParameter(param));
                            }
                        }
                    }
                    if (params.isEmpty()){
                        model.put("setparams", true);
                        Map<String, List<String>> paramsMap = new HashMap<>();
                        String scanParams = SpreadsheetService.getScanParams();
                        if (!scanParams.isEmpty()) {
                            StringTokenizer stringTokenizer = new StringTokenizer(scanParams, "|");
                            while (stringTokenizer.hasMoreTokens()) {
                                String name = stringTokenizer.nextToken().trim();
                                if (stringTokenizer.hasMoreTokens()) {
                                    String list = stringTokenizer.nextToken().trim();
                                    List<String> values = new ArrayList<>();
                                    StringTokenizer stringTokenizer1 = new StringTokenizer(list, ",");
                                    values.add("N/A");
                                    while (stringTokenizer1.hasMoreTokens()) {
                                        values.add(stringTokenizer1.nextToken().trim());
                                    }
                                    paramsMap.put(name.toLowerCase().trim(), values);
                                }
                            }
                        }
                        model.put("params", paramsMap);
                        return "pendingupload";
                    }
                } else {
                    params = (HashMap<String, String>) session.getAttribute(PARAMS + pu.getId());
                }
                // so we can go on this pending upload
                // new thread and then defer to import running as we do stuff
                new Thread(() -> {
                    try {
                        Database db = DatabaseDAO.findById(pu.getDatabaseId());
                        DatabaseServer databaseServer = DatabaseServerDAO.findById(db.getDatabaseServerId());
                        loggedInUser.setDatabaseWithServer(databaseServer,db);
                        UploadedFile uploadedFile = new UploadedFile(pu.getFilePath(), Collections.singletonList(pu.getFileName()), params, false, true);
                        List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile);
                        //Thread.sleep(2_000);
                        session.setAttribute(PENDINGREADY, "done");
                        session.setAttribute(ManageDatabasesController.IMPORTRESULT, uploadedFiles);
                    } catch (Exception e) {
                        e.printStackTrace();
                        session.setAttribute(PENDINGREADY, "problem!");
                    }
                }).start();
                return "validationready";
            }
        }
        return "redirect:/api/ManageDatabases";
    }
}