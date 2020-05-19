package com.azquo.spreadsheet.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.database.*;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.ExcelController;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportRenderer;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
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
        if (loggedInUser != null) {
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
            for (TypedPair<Integer, Integer> securityPair : loggedInUser.getReportIdDatabaseIdPermissions().values()) {
                if (!integerSet.contains(securityPair.getSecond())) {
                    databaseList.add(DatabaseDAO.findById(securityPair.getSecond()));
                }
                integerSet.add(securityPair.getSecond());
            }

            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("databases", databaseList);
// ok adding back in the uploaded files list but constrained to this DB and with file type
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplayForBusiness = AdminService.getUploadRecordsForDisplayForUserWithBasicSecurity(loggedInUser);
            if (uploadRecordsForDisplayForBusiness != null) {
                if ("database".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getDatabaseName));
                }
                if ("databasedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getDatabaseName().compareTo(o1.getDatabaseName())));
                }
                if ("date".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getDate));
                }
                if ("datedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getDate().compareTo(o1.getDate())));
                }
                if ("businessname".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getBusinessName));
                }
                if ("businessnamedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getBusinessName().compareTo(o1.getBusinessName())));
                }
                if ("username".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort(Comparator.comparing(UploadRecord.UploadRecordForDisplay::getUserName));
                }
                if ("usernamedown".equals(sort)) {
                    uploadRecordsForDisplayForBusiness.sort((o1, o2) -> (o2.getUserName().compareTo(o1.getUserName())));
                }
            }

            model.put("dbsort", "database".equals(sort) ? "databasedown" : "database");
            model.put("datesort", "date".equals(sort) ? "datedown" : "date");
            model.put("businessnamesort", "businessname".equals(sort) ? "businessnamedown" : "businessname");
            model.put("usernamesort", "username".equals(sort) ? "usernamedown" : "username");
            model.put("uploads", uploadRecordsForDisplayForBusiness);



            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            model.put("pendinguploads", AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, pendingUploadSearch, request.getParameter("allteams") != null, request.getParameter("uploadreports") != null)); // no search for the mo
            model.put("lastSelected", request.getSession().getAttribute("lastSelected"));
            model.put("importTemplates", ImportTemplateDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()));
            AdminService.setBanner(model, loggedInUser);
            return "useruploads";
        } else {
            return "redirect:/api/Login";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
            , @RequestParam(value = "template", required = false) String template
            , @RequestParam(value = "team", required = false) String team
    ) {
        if (database != null) {
            request.getSession().setAttribute("lastSelected", database);
        }

        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null) {
            Set<Integer> integerSet = new HashSet<>();
            ArrayList<Database> databaseList = new ArrayList<>();
            for (TypedPair<Integer, Integer> securityPair : loggedInUser.getReportIdDatabaseIdPermissions().values()) {
                if (!integerSet.contains(securityPair.getSecond())) {
                    databaseList.add(DatabaseDAO.findById(securityPair.getSecond()));
                }
                integerSet.add(securityPair.getSecond());
            }
            if (uploadFile != null && !uploadFile.isEmpty()) {
                try {
                    if ("true".equals(template)) {
                        try {
                            String fileName = uploadFile.getOriginalFilename();
                            File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                            uploadFile.transferTo(moved);
                            Workbook book;
                            UploadedFile uploadedFile = new UploadedFile(moved.getAbsolutePath(), Collections.singletonList(fileName), new HashMap<>(), false, false);
                            FileInputStream fs = new FileInputStream(new File(uploadedFile.getPath()));
                            OPCPackage opcPackage = null;
                            if (uploadedFile.getFileName().endsWith("xlsx")) {
                                opcPackage = OPCPackage.open(fs);
                                book = new XSSFWorkbook(opcPackage);
                            } else {
                                book = new HSSFWorkbook(fs);
                            }
                            // detect an import template by a sheet name
                            boolean isImportTemplate = false;
                            for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                                Sheet sheet = book.getSheetAt(sheetNo);
                                if (sheet.getSheetName().equalsIgnoreCase(ImportService.IMPORTMODEL)) {
                                    isImportTemplate = true;
                                    break;
                                }
                            }
                            //detect from workbook name
                            if (uploadedFile.getFileName().toLowerCase().contains("import templates")) {
                                isImportTemplate = true;
                            }
                            // detect Ben Jones contract style template

                            Name importName = BookUtils.getName(book, ReportRenderer.AZIMPORTNAME);
                            if (importName != null) {
                                isImportTemplate = true;
                            }
                            // windows file write locking paranoia
                            if (opcPackage != null){
                                opcPackage.revert();
                            }
                            boolean assignTemplateToDatabase = false;
                            if (database != null && !database.isEmpty()) {
                                Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                                if (databaseList.contains(db)) {
                                    LoginService.switchDatabase(loggedInUser, db);
                                    assignTemplateToDatabase = true;

                                }
                            }

                            if (!isImportTemplate) {
                                model.put("error", "That does not appear to be an import template.");
                            } else {
                                model.put("error", ManageDatabasesController.formatUploadedFiles(Collections.singletonList(ImportService.uploadImportTemplate(uploadedFile, loggedInUser, assignTemplateToDatabase)), -1, false, null));
                            }
                        } catch (Exception e) {
                            model.put("error", e.getMessage());
                        }
                    } else if (database != null) {
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
            AdminService.setBanner(model, loggedInUser);
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
        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
        String bannerColor = business.getBannerColor();
        if (bannerColor == null || bannerColor.length() == 0) bannerColor = "#F58030";
        String logo = business.getLogo();
        if (logo == null || logo.length() == 0) logo = "logo_alt.png";
        model.addAttribute("bannerColor", bannerColor);
        model.addAttribute("logo", logo);
        model.addAttribute("targetController", "UserUpload");
        return "importrunning";
    }
}