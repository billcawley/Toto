package com.azquo.admin.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.StandardDAO;
import com.azquo.admin.database.*;
import com.azquo.dataimport.ImportService;
import com.azquo.dataimport.ImportTemplateData;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.security.access.method.P;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zeroturnaround.zip.ZipEntryCallback;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.poi.ss.usermodel.Row;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;
import sun.misc.IOUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
            , @RequestParam(value = "commentsave", required = false) String commentSave
            , @RequestParam(value = "commentid", required = false) String commentId
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
                if (commentSave != null && commentId != null) {
                    Comment forBusinessIdAndIdentifierAndTeam = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), commentId, pu.getTeam());
                    if (forBusinessIdAndIdentifierAndTeam != null) {
                        forBusinessIdAndIdentifierAndTeam.setText(commentSave);
                        CommentDAO.store(forBusinessIdAndIdentifierAndTeam);
                    } else {
                        CommentDAO.store(new Comment(0, pu.getBusinessId(), commentId, pu.getTeam(), commentSave));
                    }
                    return "utf8page"; // stop it there, work is done
                }

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
                Set<Integer> fileLoadFlags = new HashSet<>();
                Map<Integer, Set<Integer>> fileRejectLines = new HashMap<>();
                // as in go ahead and import
                boolean actuallyImport = false;
                if ("finalsubmit".equals(finalsubmit)) {
                    //actuallyImport = true;
                    // so we have load-counter where counter is the files in order, the order should be reliable
                    // then counter-lines, a comma separated list of lines that will be rejected UNLESS
                    // counter-linenumber checkbox is checked
                    // I guess grabbing in a map would be a starter, will need maxcounter to help I think
                    int maxCounter = Integer.parseInt(maxcounter);
                    for (int i = 0; i <= maxCounter; i++) {
                        if (request.getParameter("load-" + i) == null) { // not ticked to save it . . .
                            fileLoadFlags.add(i);
                        }

                        String rejectedLinesString = request.getParameter(i + "-lines");
                        if (rejectedLinesString != null) {
                            Set<Integer> rejectedLines = new HashSet<>();
                            String[] split = rejectedLinesString.split(",");
                            for (int j = 0; j < split.length; j++) {
                                if (request.getParameter(i + "-" + split[j]) == null) { // if that line was selected then *don't* reject it
                                    rejectedLines.add(Integer.parseInt(split[j]));
                                }
                            }
                            if (!rejectedLines.isEmpty()) {
                                fileRejectLines.put(i, rejectedLines);
                            }
                        }
                    }
                }

                if (session.getAttribute(ManageDatabasesController.IMPORTRESULT) != null) {
                    StringBuilder maintext = new StringBuilder();
                    @SuppressWarnings("unchecked")
                    List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);
                    request.getSession().removeAttribute(ManageDatabasesController.IMPORTRESULT);
                    String paramsPassThrough = (String) session.getAttribute(PARAMSPASSTHROUGH);
                    session.removeAttribute(PARAMSPASSTHROUGH);
                    if (paramsPassThrough != null) {
                        maintext.append(paramsPassThrough);
                    }
                    Set<String> hasComments = new HashSet<>();// so we can tell the formatting whether a comment exists for that identifier
                    // todo - maps init will stay here but the rest really has to go into the jsp
                    maintext.append("<script>\n");
                    maintext.append("var commentIds = {};\n");
                    maintext.append("var commentValues = {};\n");
                    int count = 0;
                    for (UploadedFile uploadedFile : importResult) {
                        if (uploadedFile.getWarningLines() != null) {
                            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                                maintext.append("commentIds[\"" + count + "-" + warningLine.getLineNo() + "\"] = \"" + warningLine.getIdentifier().replace("\"", "") + "\";\n");
                                String commentValue = "";
                                // bulk load?
                                Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(loggedInUser.getUser().getBusinessId(), warningLine.getIdentifier().replace("\"", ""), pu.getTeam());
                                if (comment != null) {
                                    hasComments.add(warningLine.getIdentifier());
                                    commentValue = comment.getText().replace("\n", "\\n"); // don't break lines on JS
                                }
                                maintext.append("commentValues[\"" + count + "-" + warningLine.getLineNo() + "\"] = \"" + commentValue + "\";\n");
                            }
                        }
                        count++;
                    }
                    maintext.append("function doComment(commentId){\n" +
                            "document.getElementById('comment').value = commentValues[commentId];\n" +
                            "document.getElementById('commentId').value = commentId;\n" +
                            "$( '#dialog' ).dialog(); \n" +
                            "$('#dialog').dialog('option', 'width', 800);" +
                            "$('#dialog').dialog('option', 'title', commentIds[commentId]);" +
                            "}\n" +
                            "function saveComment(){\n" +
                            "            $.post(\"/api/PendingUpload\", {id:\"" + id + "\", commentid:commentIds[document.getElementById('commentId').value], commentsave:document.getElementById('comment').value});\n" +
                            "commentValues[document.getElementById('commentId').value] = document.getElementById('comment').value; // or it will reset when clicked on even if saved\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "}\n" +
                            "");
                    maintext.append("</script>\n");
                    maintext.append("<input name=\"finalsubmit\" value=\"finalsubmit\" type=\"hidden\" />\n");
                    maintext.append("<input name=\"maxcounter\" value=\"" + (importResult.size() - 1) + "\" type=\"hidden\" />\n");
                    maintext.append("<table>\n");
                    maintext.append("<tr>\n");
                    maintext.append("<td>Name</td>\n");
                    maintext.append("<td>Status</td>\n");
                    maintext.append("<td><div align=\"center\">Load</div></td>\n");
                    maintext.append("<td></td>\n");
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
                        maintext.append("<td colspan=\"3\"><div id=\"details" + counter + "\" style=\"overflow-x: auto;display : none\">" + ManageDatabasesController.formatUploadedFiles(Collections.singletonList(uploadedFile), counter, false, hasComments) + "</div></td>");
                        maintext.append("</tr>");
                        counter++;
                    }
                    maintext.append("<table>");
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
                    if (finalLookupValuesForFiles != null && finalLookupValuesForFiles.get(pu.getFileName()) != null) { // could happen on a single xlsx upload. Apparently always zips but I'm concerned it may not be . . .
                        params.putAll(finalLookupValuesForFiles.get(pu.getFileName()));
                    }
                    UploadedFile uploadedFile = new UploadedFile(pu.getFilePath(), Collections.singletonList(pu.getFileName()), params, false, !actuallyImport);
                    try {
                        List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile, finalLookupValuesForFiles, fileLoadFlags, fileRejectLines);
                        if (!actuallyImport) {
                            session.setAttribute(PARAMSPASSTHROUGH, lookupValuesForFilesHTML.toString());
                        } else {
                            session.setAttribute(ManageDatabasesController.IMPORTURLSUFFIX, "#tab4"); // if actually importing will land back on the pending uploads page
                        }
                        // will be moved to "actuallyImport"

                        Workbook wb = new XSSFWorkbook();
                        Sheet summarySheet = wb.createSheet("Summary");
                        summaryUploadFeedbackForSheet(uploadedFiles, summarySheet, pu);
                        Sheet sheet = wb.createSheet("Details");
                        formatUploadedFilesForSheet(pu, uploadedFiles, sheet);
                        // so we have the details sheet sheet but now we need to zip it up along with any files that were rejected.
                        // notably individual sheets in an xlsx file could be rejected so it's about collecting files from the original zip where any sheet might have been rejected
                        // also nested zips might not work at the mo
                        Set<String> filesRejected = new HashSet<>(); // gather level two files - those that will be zip entries. Set due to the point above - if there are multiple sheets in a file
                        for (UploadedFile rejectCheck : uploadedFiles) {
                            if (rejectCheck.getFileNames().size() > 1 && "Rejected by user".equalsIgnoreCase(rejectCheck.getError())) {// todo - string literals
                                filesRejected.add(rejectCheck.getFileNames().get(1));
                            }
                        }
//                        System.out.println(filesRejected);
                        // now, we need to make a new zip. A directory in the temp directory is probably the thing
                        Path zipforpendinguploadresult = Files.createTempDirectory("zipforpendinguploadresult");
                        // Write the output to a file
                        try (OutputStream fileOut = Files.newOutputStream(zipforpendinguploadresult.resolve("uploadreport.xlsx"))) {
                            wb.write(fileOut);
                        }
                        if (uploadedFile.getFileName().endsWith(".zip") || uploadedFile.getFileName().endsWith(".7z")) { // it will have been exploded already - just need to move relevant files in it
                            Files.list(Paths.get(uploadedFile.getPath())).forEach(path ->
                            {
                                if (filesRejected.contains(path.getFileName().toString())) {
                                    try {
                                        Files.copy(path, zipforpendinguploadresult.resolve(path.getFileName()));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } else { // just copy the file into there if it wasn't a zip
                            Files.copy(Paths.get(uploadedFile.getPath()), zipforpendinguploadresult.resolve(uploadedFile.getFileName()));
                        }
                        // pack up the directory
                        ZipUtil.unexplode(zipforpendinguploadresult.toFile());
                        Path loaded = Paths.get(SpreadsheetService.getScanDir() + "/loaded");
                        if (!Files.exists(loaded)) {
                            Files.createDirectories(loaded);
                        }
                        /* just commented for easy testing
                        // then move it somewhere useful
                        Files.copy(zipforpendinguploadresult, loaded.resolve(pu.getFileName() + "results.zip"), StandardCopyOption.REPLACE_EXISTING);
                        // then adjust the pending upload record to have the report
                        pu.setImportResultPath(loaded.resolve(pu.getFileName() + "results.zip").toString());
                        PendingUploadDAO.store(pu);*/

                        // end move to actually import??

                        session.setAttribute(ManageDatabasesController.IMPORTRESULT, uploadedFiles);
                    } catch (Exception e) {
                        uploadedFile.setError(CommonReportUtils.getErrorFromServerSideException(e));
                        session.setAttribute(ManageDatabasesController.IMPORTRESULT, Collections.singletonList(uploadedFile));
                        e.printStackTrace();
                    }
                }).start();
                model.put("id", id);
                // will be nothing if there's no manual paramteres
                model.put("paramspassthrough", lookupValuesForFilesHTML.toString());
                if (actuallyImport) {
                    return "importresult";
                } else {
                    return "validationready";
                }
            }
        }
        return "redirect:/api/ManageDatabases";
    }

    // like the one in ManageDatabasesController but for sheets. Started by modifying that but it got messy quickly hence a new function in here . . .
    public static void formatUploadedFilesForSheet(PendingUpload pu, List<UploadedFile> uploadedFiles, Sheet sheet) {
        int rowIndex = 0;
        int cellIndex = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            Row row = sheet.createRow(rowIndex++);
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            for (String name : names) {
                row.createCell(cellIndex++).setCellValue(name);
            }
            if (uploadedFile.isConvertedFromWorksheet()) {
                row.createCell(cellIndex++).setCellValue("Converted from worksheet");
            }

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            row = sheet.createRow(rowIndex++);

            for (String key : uploadedFile.getParameters().keySet()) {
                row.createCell(cellIndex++).setCellValue(key);
            }
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            for (String key : uploadedFile.getParameters().keySet()) {
                row.createCell(cellIndex++).setCellValue(uploadedFile.getParameter(key));
            }
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Time to process");
            row.createCell(cellIndex++).setCellValue(uploadedFile.getProcessingDuration() + " ms");
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Time to Number of lines imported");
            row.createCell(cellIndex++).setCellValue(uploadedFile.getNoLinesImported());
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Number of values adjusted");
            row.createCell(cellIndex++).setCellValue(uploadedFile.getNoValuesAdjusted().get());

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            row = sheet.createRow(rowIndex++);

            if (uploadedFile.getTopHeadings() != null && !uploadedFile.getTopHeadings().isEmpty()) {
                row.createCell(cellIndex++).setCellValue("Top headings");
                for (TypedPair<Integer, Integer> key : uploadedFile.getTopHeadings().keySet()) {
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(BookUtils.rangeToText(key.getFirst(), key.getSecond()));
                    row.createCell(cellIndex++).setCellValue(uploadedFile.getTopHeadings().get(key));
                }
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }

            if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) {
                row.createCell(cellIndex++).setCellValue("Headings with file headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                Collection<List<String>> toShow;
                if (uploadedFile.getFileHeadings() != null) {
                    toShow = uploadedFile.getFileHeadings();
                } else {
                    toShow = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().keySet();
                }

                for (List<String> fileHeading : toShow) {
                    for (String subHeading : fileHeading) {
                        row.createCell(cellIndex++).setCellValue(subHeading);
                    }
                    TypedPair<String, String> stringStringTypedPair = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                    if (stringStringTypedPair != null) {
                        if (stringStringTypedPair.getSecond() != null) {
                            row.createCell(cellIndex++).setCellValue(stringStringTypedPair.getSecond());
                        }
                        row.createCell(cellIndex++).setCellValue(stringStringTypedPair.getFirst());
                    } else {
                        row.createCell(cellIndex++).setCellValue("** UNUSED **");
                    }
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Headings without file headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (TypedPair<String, String> stringStringTypedPair : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
                    if (stringStringTypedPair.getSecond() != null) { // it could be null now as we support a non file heading azquo heading on the Import Model sheet
                        row.createCell(cellIndex++).setCellValue(stringStringTypedPair.getSecond());
                    }
                    row.createCell(cellIndex++).setCellValue(stringStringTypedPair.getFirst());
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getSimpleHeadings() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Simple Headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String heading : uploadedFile.getSimpleHeadings()) {
                    row.createCell(cellIndex++).setCellValue(heading);
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (!uploadedFile.getLinesRejected().isEmpty()) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Line Errors");
                cellIndex = 0;


                int maxHeadingsDepth = 0;
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (headingCol.size() > maxHeadingsDepth) {
                            maxHeadingsDepth = headingCol.size();
                        }
                    }
                    // slightly janky looking, the key is to put in the headings as they are in the file - the catch is that the entries in file headings can be of variable size
                    if (maxHeadingsDepth > 1) {
                        for (int i = maxHeadingsDepth; i > 1; i--) {
                            row = sheet.createRow(rowIndex++);
                            row.createCell(cellIndex++).setCellValue("");
                            row.createCell(cellIndex++).setCellValue("");
                            for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                                if (headingCol.size() >= i) {
                                    row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - i));// think that's right . . .
                                } else {
                                    row.createCell(cellIndex++).setCellValue("");
                                }
                            }
                        }
                    }
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue("Error");
                    row.createCell(cellIndex++).setCellValue("#");
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (!headingCol.isEmpty()) {
                            row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - 1));// think that's right . . .
                        } else {
                            row.createCell(cellIndex++).setCellValue("");
                        }
                    }
                } else {
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue("Error");
                    row.createCell(cellIndex++).setCellValue("#");
                }

                for (UploadedFile.RejectedLine lineRejected : uploadedFile.getLinesRejected()) {
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(lineRejected.getErrors());
                    row.createCell(cellIndex++).setCellValue(lineRejected.getLineNo());
                    String[] split = lineRejected.getLine().split("\t");
                    for (String cell : split) {
                        row.createCell(cellIndex++).setCellValue(cell);
                    }
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }
            if (uploadedFile.getPostProcessingResult() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Post processing result");
                row.createCell(cellIndex++).setCellValue(uploadedFile.getPostProcessingResult());
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }
            // now the warning lines that will be a little more complex and have tickboxes
            // todo - factor later!
            if (!uploadedFile.getWarningLines().isEmpty()) {
                // We're going to need descriptions of the errors, put them all in a set
                Set<String> errorsSet = new HashSet<>();
                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    errorsSet.addAll(warningLine.getErrors().keySet());
                }
                List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Line Warnings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String error : errors) {
                    row.createCell(cellIndex++).setCellValue(error);
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }


                int maxHeadingsDepth = 0;
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (headingCol.size() > maxHeadingsDepth) {
                            maxHeadingsDepth = headingCol.size();
                        }
                    }
                    // slightly janky looking, the key is to put in the headings as they are in the file - the catch is that the entries in file headings can be of variable size
                    if (maxHeadingsDepth > 1) {
                        for (int i = maxHeadingsDepth; i > 1; i--) {
                            row = sheet.createRow(rowIndex++);
                            row.createCell(cellIndex++).setCellValue("");
                            row.createCell(cellIndex++).setCellValue("");
                            for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                                if (headingCol.size() >= i) {
                                    row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - i));// think that's right . . .
                                } else {
                                    row.createCell(cellIndex++).setCellValue("");
                                }
                            }
                        }
                    }
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue("Comment");
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }

                    row.createCell(cellIndex++).setCellValue("#");
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (!headingCol.isEmpty()) {
                            row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - 1));// think that's right . . .
                        } else {
                            row.createCell(cellIndex++).setCellValue("");
                        }
                    }
                } else {
                    row.createCell(cellIndex++).setCellValue("Comment");
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }
                    row.createCell(cellIndex++).setCellValue("#");
                }

                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    String commentValue = "";
                    Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), warningLine.getIdentifier().replace("\"", ""), pu.getTeam());
                    if (comment != null) {
                        commentValue = comment.getText();
                    }
                    row.createCell(cellIndex++).setCellValue(commentValue);
                    for (String error : errors) {
                        row.createCell(cellIndex++).setCellValue(warningLine.getErrors().getOrDefault(error, ""));
                    }
                    row.createCell(cellIndex++).setCellValue(warningLine.getLineNo());
                    String[] split = warningLine.getLine().split("\t");
                    for (String cell : split) {
                        row.createCell(cellIndex++).setCellValue(cell);
                    }
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getError() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("ERROR");
                row.createCell(cellIndex++).setCellValue(uploadedFile.getError());
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }

            if (!uploadedFile.isDataModified()) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("NO DATA MODIFIED");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }
        }
    }

    // overview that will hopefully be useful to the user
    public static void summaryUploadFeedbackForSheet(List<UploadedFile> uploadedFiles, Sheet sheet, PendingUpload pu) {
        int rowIndex = 0;
        int cellIndex = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            cellIndex = 0;
            Row row = sheet.createRow(rowIndex++);
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            for (String name : names) {
                row.createCell(cellIndex++).setCellValue(name);
            }
            if (uploadedFile.getError() != null) {
                row.createCell(cellIndex++).setCellValue(uploadedFile.getError());
                sheet.createRow(rowIndex++);
                sheet.createRow(rowIndex++);
                continue; // necessary?
            }

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            row = sheet.createRow(rowIndex++);
            List<String> errors = null;
            if (!uploadedFile.getWarningLines().isEmpty()) {
                // We're going to need descriptions of the errors, put them all in a set
                Set<String> errorsSet = new HashSet<>();
                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    errorsSet.addAll(warningLine.getErrors().keySet());
                }
                errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Line Warnings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String error : errors) {
                    row.createCell(cellIndex++).setCellValue(error);
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }




                int maxHeadingsDepth = 0;
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (headingCol.size() > maxHeadingsDepth) {
                            maxHeadingsDepth = headingCol.size();
                        }
                    }
                    // slightly janky looking, the key is to put in the headings as they are in the file - the catch is that the entries in file headings can be of variable size
                    if (maxHeadingsDepth > 1) {
                        for (int i = maxHeadingsDepth; i > 1; i--) {
                            row = sheet.createRow(rowIndex++);
                            row.createCell(cellIndex++).setCellValue("");
                            row.createCell(cellIndex++).setCellValue("");
                            for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                                if (headingCol.size() >= i) {
                                    row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - i));// think that's right . . .
                                } else {
                                    row.createCell(cellIndex++).setCellValue("");
                                }
                            }
                        }
                    }
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue("Comment");
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }

                    row.createCell(cellIndex++).setCellValue("#");
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (!headingCol.isEmpty()) {
                            row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - 1));// think that's right . . .
                        } else {
                            row.createCell(cellIndex++).setCellValue("");
                        }
                    }
                } else {
                    row.createCell(cellIndex++).setCellValue("Comment");
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }
                    row.createCell(cellIndex++).setCellValue("#");
                }

                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                    String commentValue = "";
                    Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), warningLine.getIdentifier().replace("\"", ""), pu.getTeam());
                    if (comment != null) {
                        commentValue = comment.getText();
                    }
                    row.createCell(cellIndex++).setCellValue(commentValue);
                    for (String error : errors) {
                        row.createCell(cellIndex++).setCellValue(warningLine.getErrors().getOrDefault(error, ""));
                    }
                    row.createCell(cellIndex++).setCellValue(warningLine.getLineNo());
                    String[] split = warningLine.getLine().split("\t");
                    for (String cell : split) {
                        row.createCell(cellIndex++).setCellValue(cell);
                    }
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getIgnoreLinesValues() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Manually Rejected Lines");
                // jumping the cell index across align it with the table above
                cellIndex = errors != null ? errors.size() : 1;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("#");
                cellIndex = errors != null ? errors.size() : 1;
                row = sheet.createRow(rowIndex++);
                ArrayList<Integer> sort = new ArrayList<>(uploadedFile.getIgnoreLinesValues().keySet());
                Collections.sort(sort);
                for (Integer lineNo : sort) {
                    row.createCell(cellIndex++).setCellValue(lineNo);
                    String[] split = uploadedFile.getIgnoreLinesValues().get(lineNo).split("\t");
                    for (String cell : split) {
                        row.createCell(cellIndex++).setCellValue(cell);
                    }
                    cellIndex = errors != null ? errors.size() : 1;
                    row = sheet.createRow(rowIndex++);
                }
            }
        }
    }
}