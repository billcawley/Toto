package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
import com.azquo.dataimport.ImportTemplateData;
import com.azquo.spreadsheet.LoggedInUser;
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
    public static final String PARAMSPASSTHROUGH = "PARAMSPASSTHROUGH";
    public static final String LOOKUPS = "LOOKUPS ";

    //    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "paramssubmit", required = false) String paramssubmit
            , @RequestParam(value = "finalsubmit", required = false) String finalsubmit
            , @RequestParam(value = "maxcounter", required = false) String maxcounter
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
                String month;
                String importVersion;
                String[] fileSplit = mainFileName.split(" ");
                if (fileSplit.length < 3) {
                    model.put("error", "Filename in unknown format.");
                    return "pendingupload";
                } else {
                    importVersion = fileSplit[0] + fileSplit[1];
                    month = fileSplit[2];
                    if (month.contains(".")) {
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
                // we're going to need to move to an import template object I think
                ImportTemplateData importTemplateForUploadedFile = ImportService.getImportTemplateForUploadedFile(loggedInUser, null, null);
                List<List<String>> lookupSheet = null;
                if (importTemplateForUploadedFile == null) {
                    model.put("error", "Import template not found for " + database.getName() + ", please upload one for it.");
                    return "pendingupload";
                } else {
                    boolean found = false;
                    for (String sheetName : importTemplateForUploadedFile.getSheets().keySet()) {
                        if (sheetName.equalsIgnoreCase(importVersion)) {
                            found = true;
                        }
                        if (sheetName.equalsIgnoreCase(LOOKUPS + importVersion)) {
                            lookupSheet = importTemplateForUploadedFile.getSheets().get(sheetName);
                        }
                    }
                    if (!found) {
                        model.put("error", "Import version " + importVersion + " not found.");
                        return "pendingupload";
                    }
                    model.put(ImportService.IMPORTVERSION, importVersion);
                }
                final HashMap<String, String> params = new HashMap<>();
                params.put(ImportService.IMPORTVERSION, importVersion);
                params.put("month", month);

                Map<String, Map<String, String>> lookupValuesForFiles = null;
                List<String> files = new ArrayList<>();
                List<String> lookupHeadings = new ArrayList<>();
                if (lookupSheet != null) {
                    lookupValuesForFiles = new HashMap<>();
                    // could probably be a list but anyway. Prepare what I can from the lookup file
                    Map<String, List<String>> lookUpMap = new HashMap<>();
                    for (List<String> row : lookupSheet) {
                        boolean first = true;
                        if (lookupHeadings.isEmpty()) {
                            // top row we assume
                            for (String cell : row) {
                                if (!first) {
                                    if (!cell.isEmpty()) {// I guess there may be some space straggling at the end
                                        lookupHeadings.add(cell);
                                    }
                                }
                                first = false;
                            }
                        } else { // a lookup line
                            String[] keys = null;
                            List<String> values = new ArrayList<>();
                            for (String cell : row) {
                                if (first) {
                                    keys = cell.split(",");
                                } else {
                                    values.add(cell);
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
                                if (request.getParameter(counter + "-" + counter2) != null) {
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

                // note, unlike the lookup parameters I cna't do this by file name, it will be per spreadsheet and there may be more than one . . .
                Map<Integer,Boolean> fileLoadFlags = new HashMap<>();
                Map<Integer,Set<Integer>> fileRejectLines = new HashMap<>();
                // as in go ahead and import
                if ("finalsubmit".equals(finalsubmit)) {
                    // so we have load-counter where counter is the files in order, the order should be reliable
                    // then counter-lines, a comma separated list of lines that will be rejected UNLESS
                    // counter-linenumber checkbox is checked
                    // I guess grabbing in a map would be a starter, will need maxcounter to help I think
                    int maxCounter = Integer.parseInt(maxcounter);
                    for (int i = 0; i <= maxCounter; i++){
                        fileLoadFlags.put(i, request.getParameter("load-" + i) != null);

                        String rejectedLinesString = request.getParameter(i +"-lines");
                        if (rejectedLinesString != null){
                            Set<Integer> rejectedLines = new HashSet<>();
                            String[] split = rejectedLinesString.split(",");
                            for (int j = 0; j < split.length; j++){
                                if (request.getParameter(i + "-" + split[j]) == null){ // if that line was selected then *don't* reject it
                                    rejectedLines.add(Integer.parseInt(split[j]));
                                }
                            }
                            if (!rejectedLines.isEmpty()){
                                fileRejectLines.put(i, rejectedLines);
                            }
                        }
                    }
                }

                if (session.getAttribute(PENDINGREADY) != null) {
                    StringBuilder maintext = new StringBuilder();
                    session.removeAttribute(PENDINGREADY);
                    @SuppressWarnings("unchecked")
                    List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
                    if (importResult != null) {
                        request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULT);
                        String paramsPassThrough = (String) session.getAttribute(PARAMSPASSTHROUGH);
                        session.removeAttribute(PARAMSPASSTHROUGH);
                        if (paramsPassThrough != null) {
                            maintext.append(paramsPassThrough);
                        }
                        maintext.append("<input name=\"finalsubmit\" value=\"finalsubmit\" type=\"hidden\" />");
                        maintext.append("<input name=\"maxcounter\" value=\"" + (importResult.size() - 1) + "\" type=\"hidden\" />");
                        maintext.append("<table>");
                        maintext.append("<tr>");
                        maintext.append("<td>Name</td>");
                        maintext.append("<td>Status</td>");
                        maintext.append("<td><div align=\"center\">Load</div></td>");
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
                            maintext.append("<td>");
                            if (uploadedFile.getError() == null && uploadedFile.isDataModified()) {
                                maintext.append("<div align=\"center\"><input type=\"checkbox\" name=\"load-" + counter + "\" checked/></div>");
                            }
                            maintext.append("</td>");
                            maintext.append("<td><a href=\"#\" class=\"button\" title=\"Details\"  onclick=\"showHideDiv('details" + counter + "'); return false;\" >Details</a></td>");
                            // there will be a
                            maintext.append("</tr>");
                            maintext.append("<tr>");
                            maintext.append("<td colspan=\"3\"><div id=\"details" + counter + "\" style=\"overflow-x: auto;display : none\">" + ManageDatabasesController.formatUploadedFiles(Collections.singletonList(uploadedFile), counter, false) + "</div></td>");
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
                StringBuilder lookupValuesForFilesHTML = new StringBuilder();
                lookupValuesForFilesHTML.append("<input name=\"paramssubmit\" value=\"paramssubmit\" type=\"hidden\" />");
                int counter = 0;
                // if no lookups  files will be empty, a moot point
                // to push the lookup values through the validation screen
                for (String file : files) {
                    Map<String, String> mapForFile = lookupValuesForFiles.get(file);
                    int counter2 = 0;
                    for (String heading : lookupHeadings) {
                        lookupValuesForFilesHTML.append("<input type=\"hidden\" name=\"" + counter + "-" + counter2 + "\" value=\"" + (mapForFile.get(heading) != null ? mapForFile.get(heading) : "") + "\"/>\n");
                        counter2++;
                    }
                    counter++;
                }

                final Map<String, Map<String, String>> finalLookupValuesForFiles = lookupValuesForFiles;
                new Thread(() -> {
                    try {
                        if (finalLookupValuesForFiles != null && finalLookupValuesForFiles.get(pu.getFileName()) != null) { // could happen on a single xlsx upload. Apparently always zips but I'm concerned it may not be . . .
                            params.putAll(finalLookupValuesForFiles.get(pu.getFileName()));
                        }
                        UploadedFile uploadedFile = new UploadedFile(pu.getFilePath(), Collections.singletonList(pu.getFileName()), params, false, true);
                        List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile, finalLookupValuesForFiles);

                        session.setAttribute(PARAMSPASSTHROUGH, lookupValuesForFilesHTML.toString());
                        session.setAttribute(ManageDatabasesController.IMPORTRESULT, uploadedFiles);
                        session.setAttribute(PENDINGREADY, "done");
                    } catch (Exception e) {
                        e.printStackTrace();
                        session.setAttribute(PENDINGREADY, "problem!");
                    }
                }).start();
                model.put("id", id);
                // will be nothing if there's no manual paramteres
                model.put("paramspassthrough", lookupValuesForFilesHTML.toString());
                return "validationready";
            }
        }
        return "redirect:/api/ManageDatabases";
    }
}