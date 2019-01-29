package com.azquo.admin.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.UploadedFile;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zkoss.poi.ss.usermodel.Cell;
import org.zkoss.poi.ss.usermodel.Row;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    public static final String LOOKUPS = "LOOKUPS ";

    //    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "paramssubmit", required = false) String paramssubmit
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else if (NumberUtils.isDigits(id)) {
            AdminService.setBanner(model, loggedInUser);
            final PendingUpload pu = PendingUploadDAO.findById(Integer.parseInt(id));
            HttpSession session = request.getSession();
            // todo - pending upload security for non admin users?
            if (pu.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                String mainFileName = pu.getFileName();
                model.put("id", pu.getId());
                model.put("filename", mainFileName);
                if (NumberUtils.isDigits(databaseId)) {
                    pu.setDatabaseId(Integer.parseInt(databaseId));
                    PendingUploadDAO.store(pu);
                }
                String month = null;
                String importVersion = null;
                String[] fileSplit = mainFileName.split(" ");
                if (fileSplit.length < 3) {
                    model.put("error", "Filename in unknown format.");
                    return "pendingupload";
                } else {
                    importVersion = fileSplit[0] + fileSplit[1];
                    month = fileSplit[2];
                    if (month.contains(".")){
                        month = month.substring(0, month.indexOf("."));
                    }
                }
                model.put("month", month);


                // before starting an import thread check we have database and parameters
                Database database = DatabaseDAO.findById(pu.getDatabaseId());
                if (database == null) {
                    model.put("dbselect", "true");
                    model.put("databases", AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser));
                    return "pendingupload";
                } else {
                    model.put("database", database.getName());
                }
                // we have the database so set it to the user and look for the template
                Database db = DatabaseDAO.findById(pu.getDatabaseId());
                DatabaseServer databaseServer = DatabaseServerDAO.findById(db.getDatabaseServerId());
                loggedInUser.setDatabaseWithServer(databaseServer, db);
                // we've got the database but how about the import template?
                Workbook importTemplate = ImportService.getImportTemplateForUploadedFile(loggedInUser, null);
                Sheet lookupSheet = null;
                if (importTemplate == null) {
                    model.put("error", "Import template not found for " + database.getName() + ", please upload one for it.");
                    return "pendingupload";
                } else {
                    boolean found = false;
                    for (int sheetNo = 0; sheetNo < importTemplate.getNumberOfSheets(); sheetNo++) {
                        if (importTemplate.getSheetAt(sheetNo).getSheetName().trim().equalsIgnoreCase(importVersion)) {
                            found = true;
                        }
                        if (importTemplate.getSheetAt(sheetNo).getSheetName().trim().equalsIgnoreCase(LOOKUPS + importVersion)) {
                            lookupSheet = importTemplate.getSheetAt(sheetNo);
                        }
                    }
                    if (!found) {
                        model.put("error", "Import version " + importVersion + " not found.");
                        return "pendingupload";
                    }
                    model.put("importVersion", importVersion);
                }
                final HashMap<String, String> params = new HashMap<>();
                params.put(ImportService.IMPORTVERSION, importVersion);
                params.put("month", month);

                Map<String, Map<String, String>> lookupValuesForFiles = null;
                if (lookupSheet != null) {
                    lookupValuesForFiles = new HashMap<>();
                    List<String> lookupHeadings = new ArrayList<>();
                    // could probably be a list but anyway. Prepare what I can from the lookup file
                    Map<String, List<String>> lookUpMap = new HashMap<>();
                    for (Row row : lookupSheet) {
                        boolean first = true;
                        if (lookupHeadings.isEmpty()) {
                            // top row we assume
                            for (Cell cell : row) {
                                if (!first) {
                                    String heading = ImportService.getCellValue(cell);
                                    if (!heading.isEmpty()) {// I guess there may be some space straggling at the end
                                        lookupHeadings.add(heading);
                                    }
                                }
                                first = false;
                            }
                        } else { // a lookup line
                            String[] keys = null;
                            List<String> values = new ArrayList<>();
                            for (Cell cell : row) {
                                if (first) {
                                    keys = ImportService.getCellValue(cell).split(",");
                                } else {
                                    values.add(ImportService.getCellValue(cell));
                                }
                                first = false;
                            }
                            if (keys != null) {
                                for (String key : keys) {
                                    if (!key.isEmpty()) {
                                        lookUpMap.put(key.toLowerCase().trim(), values);
                                    }
                                }
                            }
                        }
                    }
                    // now apply to the file names
                    // todo - standardize API calls?
                    List<String> files = new ArrayList<>();
                    if (mainFileName.endsWith(".zip")) { // I'm going to go down one level for the moment
                        ZipFile zipFile = new ZipFile(pu.getFilePath());
                        zipFile.stream().map(ZipEntry::getName).forEach(files::add);
                    } else {
                        files.add(mainFileName);
                    }
                    for (String file : files) {
                        Map<String, String> mapForFile = new HashMap<>();
                        for (String lookUp : lookUpMap.keySet()) {
                            if (file.toLowerCase().contains(lookUp)) {
                                List<String> values = lookUpMap.get(lookUp);
                                int index = 0;
                                for (String heading : lookupHeadings) {
                                    if (index < values.size() && !values.get(index).isEmpty()) {
                                        mapForFile.put(heading, values.get(index));
                                    }
                                    index++;
                                }
                            }
                        }
                        lookupValuesForFiles.put(file, mapForFile);
                    }

                    if ("paramssubmit".equals(paramssubmit)) {
                        int counter = 0;
                        for (String file : files) {
                            Map<String, String> mapForFile = lookupValuesForFiles.get(file);
                            int counter2 = 0;
                            for (String heading : lookupHeadings) {
                                if (request.getParameter(counter + "-" + counter2) != null){
                                    mapForFile.put(heading, request.getParameter(counter + "-" + counter2));
                                }
                                counter2++;
                            }
                            counter++;
                        }
                    } else {
                        StringBuilder paramAdjustTable = new StringBuilder();
                        // yes this should be taken out but for the mo it's easier in here
                        paramAdjustTable.append("<input name=\"paramssubmit\" value=\"paramssubmit\" type=\"hidden\" />");
                        paramAdjustTable.append("<table>");
                        paramAdjustTable.append("<tr>");
                        paramAdjustTable.append("<td>File</td>");
                        for (String heading : lookupHeadings) {
                            paramAdjustTable.append("<td>" + heading + "</td>");
                        }
                        paramAdjustTable.append("</tr>");
                        int counter = 0;
                        for (String file : files) {
                            paramAdjustTable.append("<tr>");
                            paramAdjustTable.append("<td>" + file + "</td>");
                            Map<String, String> mapForFile = lookupValuesForFiles.get(file);
                            int counter2 = 0;
                            for (String heading : lookupHeadings) {
                                paramAdjustTable.append("<td><input type=\"text\" name=\"" + counter + "-" + counter2 + "\" value=\"" + (mapForFile.get(heading) != null ? mapForFile.get(heading) : "") + "\"/></td>");
                                counter2++;
                            }
                            paramAdjustTable.append("</tr>");
                            counter++;
                        }
                        paramAdjustTable.append("</table>");


                        model.put("setparams", true);
                        model.put("maintext", paramAdjustTable.toString());
                        //model.put("params", paramsMap);
                        return "pendingupload";
                    }
                }

                if (session.getAttribute(PENDINGREADY) != null) {
                    StringBuilder maintext = new StringBuilder();
                    session.removeAttribute(PENDINGREADY);
                    @SuppressWarnings("unchecked")
                    List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
                    if (importResult != null) {
                        request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULT);
                        maintext.append("<input name=\"finalsubmit\" value=\"finalsubmit\" type=\"hidden\" />");
                        maintext.append("<table>");
                        maintext.append("<tr>");
                        maintext.append("<td>Name</td>");
                        maintext.append("<td>Status</td>");
                        maintext.append("<td></td>");
                        //maintext.append(ManageDatabasesController.formatUploadedFiles(Collections.singletonList(uploadedFile), false));
                        maintext.append("</tr>");
                        int counter = 0;
                        for (UploadedFile uploadedFile : importResult) {
                            String quickFeedback = "";
                            if (uploadedFile.getError() != null) {
                                quickFeedback = uploadedFile.getError();
                            } else {
                                if (uploadedFile.getLinesRejected() != null && !uploadedFile.getLinesRejected().isEmpty()) {
                                    quickFeedback = uploadedFile.getLinesRejected().size() + " Lines Rejected";
                                }
                                if (uploadedFile.getWarningLines() != null && !uploadedFile.getWarningLines().isEmpty()) {
                                    if (!quickFeedback.isEmpty()) {
                                        quickFeedback += ", ";
                                    }
                                    quickFeedback += uploadedFile.getWarningLines().size() + " Warnings";
                                }
                                // todo - number of lines failed on qualified - add as no data modified
                                if (!uploadedFile.isDataModified()) {
                                    if (!quickFeedback.isEmpty()) {
                                        quickFeedback += ", ";
                                    }
                                    quickFeedback += "No Data Modified";
                                }
                            }

                            maintext.append("<tr>");
                            String fileName = uploadedFile.getFileName();
                            if (uploadedFile.getFileNames().size() > 1) {
                                StringBuilder sb = new StringBuilder();
                                for (String name : uploadedFile.getFileNames().subList(1, uploadedFile.getFileNames().size())) {
                                    sb.append(name).append(", ");
                                }
                                if (sb.length() > 0) {
                                    fileName = sb.substring(0, sb.length() - 2);
                                }
                            }
                            maintext.append("<td>" + fileName + "</td>");
                            maintext.append("<td>" + (quickFeedback.isEmpty() ? "Success" : quickFeedback) + "</td>");
                            // <input name="excel" type="checkbox" id="excelinterface" checked/>
                            maintext.append("<td><a href=\"#\" class=\"button\" title=\"Details\"  onclick=\"showHideDiv('details" + counter + "'); return false;\" >Details</a></td>");
                            // there will be a
                            maintext.append("</tr>");
                            maintext.append("<tr>");
                            maintext.append("<td colspan=\"3\"><div id=\"details" + counter + "\" style=\"overflow-x: auto;display : none\">" + ManageDatabasesController.formatUploadedFiles(Collections.singletonList(uploadedFile), false) + "</div></td>");
                            maintext.append("</tr>");
                            counter++;
                        }
                        maintext.append("<table>");
                    }
                    // need to jam in the import result, need better feedback than before
                    model.put("maintext", maintext.toString());
                    return "pendingupload";
                }

                // so we can go on this pending upload
                // new thread and then defer to import running as we do stuff
                final Map<String, Map<String, String>> finalLookupValuesForFiles = lookupValuesForFiles;
                new Thread(() -> {
                    try {
                        if (finalLookupValuesForFiles != null && finalLookupValuesForFiles.get(pu.getFileName()) != null){ // could happen on a single xlsx upload. Apparently always zips but I'm concerned it may not be . . .
                            params.putAll(finalLookupValuesForFiles.get(pu.getFileName()));
                        }
                        UploadedFile uploadedFile = new UploadedFile(pu.getFilePath(), Collections.singletonList(pu.getFileName()), params, false, true);
                        List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile, finalLookupValuesForFiles);
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