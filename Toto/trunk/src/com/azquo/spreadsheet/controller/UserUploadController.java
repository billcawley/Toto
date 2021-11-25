package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.database.*;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.CommonReportUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Copyright (C) 2019 Azquo Ltd.
 * <p>
 * Created March 5th. Will allow a user to upload data to a database either directly or via pending uploads
 * <p>
 * Copy paste of manage databases then massively stripped down. Need to watch for different security concerns
 */
@Controller
@RequestMapping("/UserUpload")
public class UserUploadController {

    //    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);
    private static DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "pendingUploadSearch", required = false) String pendingUploadSearch
            , @RequestParam(value = "sort", required = false) String sort
    ) {
        final LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && loggedInUser.getPendingUploadPermissions() != null && !loggedInUser.getPendingUploadPermissions().isEmpty()) {
            if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                return "redirect:/api/ManageDatabases";
            }
            StringBuilder error = new StringBuilder();
            // EFC - I can't see a way around this one currently. I want to use @SuppressWarnings very sparingly
            @SuppressWarnings("unchecked")
            List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(com.azquo.admin.controller.ManageDatabasesController.IMPORTRESULT);
            if (importResult != null) {
                error.append(ManageDatabasesController.formatUploadedFiles(importResult, -1, false, null));
                request.getSession().removeAttribute(com.azquo.admin.controller.ManageDatabasesController.IMPORTRESULT);
            }

            List<Database> databaseList = new ArrayList<>();
            Set<Integer> integerSet = new HashSet<>();
            for (LoggedInUser.ReportIdDatabaseId securityPair : loggedInUser.getReportIdDatabaseIdPermissions().values()) {
                if (!integerSet.contains(securityPair.getDatabaseId())) {
                    databaseList.add(DatabaseDAO.findById(securityPair.getDatabaseId()));
                }
                integerSet.add(securityPair.getDatabaseId());
            }

            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("databases", databaseList);
            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, pendingUploadSearch, request.getParameter("allteams") != null, request.getParameter("uploadreports") != null)); // no search for the mo
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("showSave", false);
            model.put("showUnlockButton", false);
            model.put("importTemplates", ImportTemplateDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()));
            AdminService.setBanner(model, loggedInUser);
            if (request.getSession().getAttribute("newui") != null) {
                return "useruploads2";
            }
            return "useruploads";
        } else {
            return "redirect:/api/Login";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
            , @RequestParam(value = "team", required = false) String team
    ) {
        if (database != null) {
            request.getSession().setAttribute("lastSelected", database);
        }

        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && loggedInUser.getPendingUploadPermissions() != null && !loggedInUser.getPendingUploadPermissions().isEmpty()) {
            Set<Integer> integerSet = new HashSet<>();
            ArrayList<Database> databaseList = new ArrayList<>();
            for (LoggedInUser.ReportIdDatabaseId securityPair : loggedInUser.getReportIdDatabaseIdPermissions().values()) {
                if (!integerSet.contains(securityPair.getDatabaseId())) {
                    databaseList.add(DatabaseDAO.findById(securityPair.getDatabaseId()));
                }
                integerSet.add(securityPair.getDatabaseId());
            }
            if (uploadFile != null && !uploadFile.isEmpty()) {
                try {
                    if (database != null) {
                        if (database.isEmpty()) {
                            model.put("error", "Please select a database");
                        } else {
                            // data file or zip. New thing - it can be added to the Pending Uploads manually
                            Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                            if (databaseList.contains(db)) {
                                LoginService.switchDatabase(loggedInUser, db);
                            }
                            if (team != null) { // Pending uploads
                                String targetPath = SpreadsheetService.getFilesToImportDir() + "/tagged/" + System.currentTimeMillis() + uploadFile.getOriginalFilename();
                                uploadFile.transferTo(new File(targetPath));
                                // ok it's moved now make the pending upload record
                                // todo - assign the database and team automatically!
                                PendingUpload pendingUpload = new PendingUpload(0, loggedInUser.getUser().getBusinessId()
                                        , LocalDateTime.now()
                                        , null
                                        , uploadFile.getOriginalFilename()
                                        , targetPath
                                        , loggedInUser.getUser().getId()
                                        , -1
                                        , loggedInUser.getDatabase().getId()
                                        , null, team);
                                PendingUploadDAO.store(pendingUpload);
                            } else {
                                HttpSession session = request.getSession();
                                String fileName = uploadFile.getOriginalFilename();
                                Map<String, String> params = new HashMap<>();
                                params.put("File", fileName);
                                loggedInUser.userLog("Upload file", params);
                                // always move uplaoded files now, they'll need to be transferred to the DB server after code split
                                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                                uploadFile.transferTo(moved);
                                return handleImport(loggedInUser, session, model, fileName, moved.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                }
            } else {
                model.put("error", "Please select a file to upload");
            }
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, null, request.getParameter("allteams") != null, request.getParameter("uploadedreports") != null));
            model.put("developer", loggedInUser.getUser().isDeveloper());
            model.put("databases", databaseList);
            model.put("showSave", false);
            model.put("showUnlockButton", false);
            AdminService.setBanner(model, loggedInUser);
            if (request.getSession().getAttribute("newui") != null) {
                return "useruploads2";
            }
            return "useruploads";
        } else {
            return "redirect:/api/Login";
        }
    }

    private static String handleImport(LoggedInUser loggedInUser, HttpSession session, ModelMap model, String fileName, String filePath) {
        // need to add in code similar to report loading to give feedback on imports
        final Map<String, String> fileNameParams = new HashMap<>();
        ImportService.addFileNameParametersToMap(fileName, fileNameParams);
        UploadedFile uploadedFile = new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false, false);
        new Thread(() -> {
            // so in here the new thread we set up the loading as it was originally before and then redirect the user straight to the logging page
            try {
                session.setAttribute(com.azquo.admin.controller.ManageDatabasesController.IMPORTRESULT, ImportService.importTheFile(loggedInUser, uploadedFile, session, null));
            } catch (Exception e) {
                e.printStackTrace();
                uploadedFile.setError(CommonReportUtils.getErrorFromServerSideException(e));
                session.setAttribute(com.azquo.admin.controller.ManageDatabasesController.IMPORTRESULT, Collections.singletonList(uploadedFile));
            }
        }).start();
        // edd pasting in here to get the banner colour working
        AdminService.setBanner(model, loggedInUser);
        if (session.getAttribute("newui") != null) {
            return "importrunning2";
        }
        return "importrunning";
    }
}