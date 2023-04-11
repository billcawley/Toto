package com.azquo.admin.controller;

import com.azquo.RowColumn;
import com.azquo.StringLiterals;
import com.azquo.admin.AdminService;
import com.azquo.admin.DisplayService;
import com.azquo.admin.database.*;
import com.azquo.dataimport.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.HeadingWithInterimLookup;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipUtil;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * User CRUD.
 */
@Controller
@RequestMapping("/PendingUpload")
public class PendingUploadController {

    private static final String PARAMSPASSTHROUGH = "PARAMSPASSTHROUGH";
    private static final String LATESTPROVENANCE = "LATESTPROVENANCE";
    public static final String WARNINGSWORKBOOK = "WARNINGSWORKBOOK";
    private static final String LOOKUPS = "LOOKUPS ";

    // perhaps use this pattern in other places? Could tidy the ManageDatabasesController a littleMonday 8th
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "paramssubmit", required = false) String paramssubmit
            , @RequestParam(value = "finalsubmit", required = false) String finalsubmit
            , @RequestParam(value = "reject", required = false) String reject
            , @RequestParam(value = "dbcheck", required = false) String dbcheck
            , @RequestParam(value = "maxcounter", required = false) String maxcounter
            , @RequestParam(value = "commentsave", required = false) String commentSave
            , @RequestParam(value = "commentid", required = false) String commentId
    ) throws Exception {
        return handleRequest(model, request, id, databaseId, paramssubmit, finalsubmit, reject, dbcheck, maxcounter, commentSave, commentId, null);
    }

    //    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "id", required = false) String id
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "paramssubmit", required = false) String paramssubmit
            , @RequestParam(value = "finalsubmit", required = false) String finalsubmit
            , @RequestParam(value = "reject", required = false) String reject
            , @RequestParam(value = "dbcheck", required = false) String dbcheck
            , @RequestParam(value = "maxcounter", required = false) String maxcounter
            , @RequestParam(value = "commentsave", required = false) String commentSave
            , @RequestParam(value = "commentid", required = false) String commentId
            , @RequestParam(value = "amendmentsFile", required = false) MultipartFile amendmentsFile
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null) {
            return "redirect:/api/Login";
        } else if (NumberUtils.isDigits(id)) {
            if (request.getSession().getAttribute("newdesign") != null){
                model.put("cancelUrl", "ManageDatabases?newdesign=pendinguploads");
            } else {
                model.put("cancelUrl", "ManageDatabases#3");
            }
            model.put("admin", loggedInUser.getUser().isAdministrator());
            AdminService.setBanner(model, loggedInUser);
            final PendingUpload pu = PendingUploadDAO.findById(Integer.parseInt(id));
            HttpSession session = request.getSession();
            boolean runClearExecute = request.getParameter("runClearExecute") != null;
            // todo - pending upload security for non admin users?
            if (pu.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                Set<Integer> nonAdminDBids = null;
                ArrayList<Database> databaseList = null;
                if (!loggedInUser.getUser().isAdministrator()) {
                    // now a normal user can access this spacer
                    model.put("pheader", "../includes/public_header2.jsp");
                    model.put("pfooter", "../includes/public_footer.jsp");
                        model.put("cancelUrl", "UserUpload");
                    nonAdminDBids = new HashSet<>();
                    databaseList = new ArrayList<>();
                    for (LoggedInUser.ReportIdDatabaseId securityPair : loggedInUser.getReportIdDatabaseIdPermissions().values()) {
                        if (!nonAdminDBids.contains(securityPair.getDatabaseId())) {
                            databaseList.add(DatabaseDAO.findById(securityPair.getDatabaseId()));
                        }
                        nonAdminDBids.add(securityPair.getDatabaseId());
                    }
                    if (!nonAdminDBids.contains(pu.getDatabaseId())) {
                        return "redirect:/api/Login";
                    }
                }
                // on general principle
                session.removeAttribute(WARNINGSWORKBOOK);
                if ("true".equalsIgnoreCase(reject)) {
                    Path loaded = Paths.get(SpreadsheetService.getFilesToImportDir() + "/loaded");
                    if (!Files.exists(loaded)) {
                        Files.createDirectories(loaded);
                    }
                    long timestamp = System.currentTimeMillis();
                    String newFileName = pu.getFileName().substring(0, pu.getFileName().lastIndexOf(".")) + "rejected" + pu.getFileName().substring(pu.getFileName().lastIndexOf("."));
                    Path rejectedDestination = loaded.resolve(timestamp + newFileName);
                    Files.copy(Paths.get(pu.getFilePath()), rejectedDestination, StandardCopyOption.REPLACE_EXISTING);
                    // then adjust the pending upload record to have the report
                    pu.setImportResultPath(rejectedDestination.toString());
                    pu.setFileName(newFileName);
                    pu.setProcessedByUserId(loggedInUser.getUser().getId());
                    pu.setProcessedDate(LocalDateTime.now());
                    PendingUploadDAO.store(pu);
                    if (nonAdminDBids != null) {
                            return "redirect:/api/UserUpload?uploadreports=true";
                    } else {
                            return "redirect:/api/ManageDatabases?uploadreports=true#3";
                    }
                }
                if (commentSave != null && commentId != null) {
                    dealWithComment(pu, commentId, commentSave);
                    return "utf8page"; // stop it there, work is done
                }

                if ("true".equals(dbcheck)) {
                    String lastProvenance = (String) session.getAttribute(LATESTPROVENANCE);
                    // not actually going to remove the last provenance until we actually import - someone could close the dialog which calls this
                    String provenanceAfterValidation = RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).getMostRecentProvenance(loggedInUser.getDatabase().getPersistenceName());
                    // what if last provenance is null? Can it be?
                    if (lastProvenance != null && !lastProvenance.equals(provenanceAfterValidation)) { // then we need to warn the user, how to do this without losing any settings?
                        model.addAttribute("content", provenanceAfterValidation);
                    } else {
                        model.addAttribute("content", "ok");
                    }
                    return "utf8page"; // stop it there, work is done
                }

                String mainFileName = pu.getFileName();
                model.put("id", pu.getId());
                model.put("filename", mainFileName);
                if (NumberUtils.isDigits(databaseId)) {
                    if (nonAdminDBids != null && !nonAdminDBids.contains(Integer.parseInt(databaseId))) {
                        return "redirect:/api/Login";
                    }
                    pu.setDatabaseId(Integer.parseInt(databaseId));
                    PendingUploadDAO.store(pu);
                }
                String month;
                String importVersion;
                String[] fileSplit = mainFileName.replace("  ", " ").split(" "); // make double spaces be like single spaces for the purposes of parsing basic info
                String password = null; // password for excel files
                ImportTemplate preProcess = null;
                if (fileSplit.length < 3) {
                    model.put("error", "Filename in unknown format.");
                    if (session.getAttribute("newdesign") != null){
                        return "pendingupload";
                    }
                    return "pendingupload2";
                } else {
                    //need to think about option to find a pre processor with the first and second words, if so the second word is the version
                    String possiblePreprocessorName = fileSplit[0] + " " + fileSplit[1] + " preprocessor.xlsx";
                    preProcess = ImportTemplateDAO.findForNameAndBusinessId(possiblePreprocessorName, loggedInUser.getUser().getBusinessId());
                    if (preProcess != null){
                        importVersion = fileSplit[1];
                    } else {
                        importVersion = fileSplit[0] + fileSplit[1];
                    }
                    month = fileSplit[2];
                    if (month.contains(".")) {
                        month = month.substring(0, month.indexOf("."));
                    }
                }
                // we say password can be at the end
                if (fileSplit.length > 3){
                    password = fileSplit[fileSplit.length - 1];
                    if (password.contains(".")) {
                        password = password.substring(0, password.indexOf("."));
                    }
                }
                model.put("month", month);

                // before starting an import thread check we have database and parameters
                Database database = DatabaseDAO.findById(pu.getDatabaseId());
                if (database == null) {
                    model.put("dbselect", "true");
                    if (databaseList != null) {
                        model.put("databases", databaseList);
                    } else {
                        model.put("databases", AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser));
                    }
                    if (session.getAttribute("newdesign") != null){
                        return "pendingupload";
                    }
                    return "pendingupload2";
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
                String runClearExecuteCommand  = null;
                final HashMap<String, String> params = new HashMap<>();
                if (importTemplateForUploadedFile == null) {
                    model.put("error", "Import template not found for " + database.getName() + ", please upload one for it.");
                    if (session.getAttribute("newdesign") != null){
                        return "pendingupload";
                    }
                    return "pendingupload2";
                } else {
                    boolean found = false;
                    for (String sheetName : importTemplateForUploadedFile.getSheets().keySet()) {
                        if (sheetName.equalsIgnoreCase(importVersion)) {
                            found = true;
                            // a paste from import service stripped down to find just the parameters
                            Map<String, String> templateParameters = new HashMap<>(); // things like pre processor, file encoding etc
                            List<List<String>> standardHeadings = new ArrayList<>();// required to stop NPE internally, perhaps can zap . . .
                            ImportService.importSheetScan(importTemplateForUploadedFile.getSheets().get(sheetName), null, standardHeadings, null, templateParameters, null);
                            if (templateParameters.get(ImportService.PENDINGDATACLEAR) != null){
                                runClearExecuteCommand = templateParameters.get(ImportService.PENDINGDATACLEAR);
                                if (session.getAttribute(ManageDatabasesController.IMPORTRESULT) == null){// only show the tickbox on the first screen, it makes no sense on the validation results screen
                                    model.put("runClearExecute", true);
                                }
                            }

                        }
                        if (sheetName.equalsIgnoreCase(LOOKUPS + importVersion)) {
                            lookupSheet = importTemplateForUploadedFile.getSheets().get(sheetName);
                        }
                    }
                    if (!found) {
                        model.put("error", "Import version " + importVersion + " not found.");
                        if (session.getAttribute("newdesign") != null){
                            return "pendingupload";
                        }
                        return "pendingupload2";
                    }
                    model.put(ImportService.IMPORTVERSION, importVersion);
                    if (preProcess != null){
                        model.put(ImportService.PREPROCESSOR, preProcess.getFilename());
                        params.put(ImportService.PREPROCESSOR, preProcess.getFilename());
                    }
                }
                params.put(ImportService.IMPORTVERSION, importVersion);
                params.put("month", month);
                if (password != null){
                    params.put("password", password);
                }

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
                    if (mainFileName.toLowerCase().endsWith(".zip")) { // I'm going to go down one level for the moment
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
                        if (session.getAttribute("newdesign") != null){
                            return "pendingupload";
                        }
                        return "pendingupload2";
                    }
                }

                // note, unlike the lookup parameters I can't do this by file name, it will be per spreadsheet and there may be more than one . . .
                Set<Integer> fileRejectFlags = new HashSet<>();
                Map<Integer, Map<Integer, String>> fileRejectLines = new HashMap<>();
                // as in go ahead and import
                boolean actuallyImport = false;
                if ("finalsubmit".equals(finalsubmit)) {
                    actuallyImport = true;
                    // so we have load-counter where counter is the files in order, the order should be reliable
                    // then counter-lines, a comma separated list of lines that will be rejected UNLESS
                    // counter-linenumber checkbox is checked
                    // I guess grabbing in a map would be a starter, will need maxcounter to help I think

                    if (amendmentsFile != null && !amendmentsFile.isEmpty()) {
                        // I need to collect comments and which lines to load
                        OPCPackage opcPackage = OPCPackage.open(amendmentsFile.getInputStream());
                        Workbook book = new XSSFWorkbook(opcPackage);
                        Sheet warnings = book.getSheet("Warnings");
                        // try catch as I need to make sure the package is closed under windows
                        try {
                            if (warnings != null) {
                                boolean inData = false;
                                // first two always load and comment, look up the other two
                                int fileIndexIndex = 0;
                                int lineIndexIndex = 0;
                                int identifierIndex = 0;
                                for (Row row : warnings) {
                                    if (inData) {
                                        int fileIndex = Integer.parseInt(ImportService.getkCellValue(row.getCell(fileIndexIndex)));
                                        int lineIndex = Integer.parseInt(ImportService.getkCellValue(row.getCell(lineIndexIndex)));
                                        String identifier = ImportService.getkCellValue(row.getCell(identifierIndex));
                                        // load and comment will always be the first and second cells
                                        String load = ImportService.getkCellValue(row.getCell(0));
                                        String comment = ImportService.getkCellValue(row.getCell(1));
                                        // ok first deal with the comment
                                        dealWithComment(pu, identifier, comment);
                                        // now the line, the question being whether it's to be rejected
                                        if (load.isEmpty()) {
                                            fileRejectLines.computeIfAbsent(fileIndex, HashMap::new).put(lineIndex, identifier);
                                        }
                                    } else if (row.getCell(0).getStringCellValue().equalsIgnoreCase("load")) {
                                        for (Cell cell : row) {
                                            if (ImportService.getkCellValue(cell).equalsIgnoreCase("file index")) {
                                                fileIndexIndex = cell.getColumnIndex();
                                            }
                                            if (ImportService.getkCellValue(cell).equalsIgnoreCase("line no")) {
                                                lineIndexIndex = cell.getColumnIndex();
                                            }
                                            if (ImportService.getkCellValue(cell).equalsIgnoreCase("identifier")) {
                                                identifierIndex = cell.getColumnIndex();
                                            }
                                        }
                                        if (lineIndexIndex == 0 || fileIndexIndex == 0) {
                                            throw new Exception("couldn't find line no and file index headings in amended warnings upload");
                                        }
                                        inData = true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        opcPackage.revert();
                    }
                    int maxCounter = Integer.parseInt(maxcounter);
                    for (int i = 0; i <= maxCounter; i++) {
                        if (request.getParameter("load-" + i) == null) { // not ticked to save it . . .
                            fileRejectFlags.add(i);
                        }
                        // so, we're allowing an excel file to define lines rejected, check if this was uploaded, and use it instead of the checkboxes if it was
                        if (amendmentsFile == null || amendmentsFile.isEmpty()) { // dammit null wasn't good enough! need to check empty too . . .
                            // todo - string literals . . . :|
                            String rejectedLinesString = request.getParameter(i + "-lines");
                            String identifiersString = request.getParameter(i + "-identifier");
                            if (rejectedLinesString != null) {
                                Map<Integer, String> rejectedLines = new HashMap<>(); // this is now going to hold the identifier too - this way I can have the comments available on rejected lines. If I don't get the identidifiers here it's difficult
                                String[] split = rejectedLinesString.split(",");
                                String[] identifiersSplit = identifiersString.split("\\|\\|");
                                if (split.length == identifiersSplit.length) {
                                    for (int j = 0; j < split.length; j++) {
                                        if (request.getParameter(i + "-" + split[j]) == null) { // if that line was selected then *don't* reject it
                                            rejectedLines.put(Integer.parseInt(split[j]), identifiersSplit[j]);
                                        }
                                    }
                                } // if it's not then what?
                                if (!rejectedLines.isEmpty()) {
                                    fileRejectLines.put(i, rejectedLines);
                                }
                            }
                        }
                    }
                }

                if (session.getAttribute(ManageDatabasesController.IMPORTRESULT) != null) {
                    StringBuilder maintext = new StringBuilder();
                    @SuppressWarnings("unchecked")
                    List<UploadedFile> importResult = (List<UploadedFile>) request.getSession().getAttribute(ManageDatabasesController.IMPORTRESULT);

                    // for the moment just jam a workbook against the session in case they want it. Need to remember to zap on submit or a new validation


                    Workbook wb = new XSSFWorkbook();
                    Sheet warnings = wb.createSheet("Warnings");
                    int rowIndex = 0;
                    // like below gathering errors for headings but this isn't per file, it's errors for all the files
                    Set<String> errorsSet = new HashSet<>();
                    for (UploadedFile uf : importResult) {
                        for (UploadedFile.WarningLine warningLine : uf.getWarningLines()) {
                            errorsSet.addAll(warningLine.getErrors().keySet());
                        }
                    }
                    List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
                    Set<String> paramsSet = new HashSet<>(); //
                    for (UploadedFile uf : importResult) {
                        paramsSet.addAll(uf.getParameters().keySet());
                    }
                    paramsSet.remove(ImportService.IMPORTVERSION);
                    List<String> paramsForWarnings = new ArrayList<>(paramsSet);
                    int fileIndex = 0;
                    for (UploadedFile uf : importResult) {
                        rowIndex = lineWarningsForSheet(pu, uf, rowIndex, warnings, errors, rowIndex == 0, paramsForWarnings, fileIndex);
                        fileIndex++;
                    }
                    request.getSession().setAttribute(WARNINGSWORKBOOK, wb);

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
                                    commentValue = comment.getText().replace("\n", "\\n"); // don't break lines on JS todo - escape other things e.g. ' " !
                                }
                                maintext.append("commentValues[\"" + count + "-" + warningLine.getLineNo() + "\"] = \"" + commentValue + "\";\n");
                            }
                        }
                        count++;
                    }
                    maintext.append("</script>\n");
                    maintext.append("<input name=\"finalsubmit\" id=\"finalsubmit\" value=\"finalsubmit\" type=\"hidden\" />\n");
                    maintext.append("<input name=\"maxcounter\" value=\"" + (importResult.size() - 1) + "\" type=\"hidden\" />\n");

                        maintext.append("<table><tr><td><a href=\"/api/Download?puid=" + id + "\" class=\"button is-small\">Download Warning Report</a>&nbsp;&nbsp;" +
                                "</td><td><div class=\"file has-name is-small\" id=\"file-js-example\">\n" +
                                "                                        <label class=\"file-label\">\n" +
                                "                                            <input class=\"file-input is-small\" type=\"file\" name=\"amendmentsFile\"\n" +
                                "                                                   id=\"uploadFile\"\n" +
                                "                                                   multiple>\n" +
                                "                                            <span class=\"file-cta is-small\">\n" +
                                "                                              <span class=\"file-icon is-small\">\n" +
                                "                                                <i class=\"fas fa-upload\"></i>\n" +
                                "                                              </span>\n" +
                                "                                              <span class=\"file-label is-small\">\n" +
                                "                                                Upload Amendments\n" +
                                "                                              </span>\n" +
                                "                                            </span>\n" +
                                "                                            <span class=\"file-name is-small\">\n" +
                                "                                            </span>\n" +
                                "                                        </label>\n" +
                                "                                    </div></td></tr></table>\n\n" +
                                "<script>" +
                                "    const fileInput = document.querySelector('#file-js-example input[type=file]');\n" +
                                "    fileInput.onchange = () => {\n" +
                                "        if (fileInput.files.length > 0) {\n" +
                                "            const fileName = document.querySelector('#file-js-example .file-name');\n" +
                                "            if (fileInput.files.length > 1) {\n" +
                                "                fileName.textContent = \"Multiple files selected\";\n" +
                                "            } else {\n" +
                                "                fileName.textContent = fileInput.files[0].name;\n" +
                                "            }\n" +
                                "        }\n" +
                                "    }\n" +
                                "</script>");

                        maintext.append("<table class=\"table is-striped is-fullwidth\">\n");
                    maintext.append("<thead>\n");
                    maintext.append("<th>Name</th>\n");
                    maintext.append("<th>Status</th>\n");
                        maintext.append("<th><div align=\"center\">Load</div></th>\n");
                    maintext.append("<th></th>\n");
                    //maintext.append(ManageDatabasesController.formatUploadedFiles(Collections.singletonList(uploadedFile), false));
                    maintext.append("</thead>");
                    int counter = 0;
                   if (loggedInUser.getBusiness().isNewDesign()) {
                        DisplayService.compileUploadedFileResults(importResult, counter,  hasComments);


                    }
                    for (UploadedFile uploadedFile : importResult) {
                        String quickFeedback = "";
                        if (uploadedFile.getError() != null) {
                            quickFeedback = uploadedFile.getError();
                        } else {
                            if (uploadedFile.getLinesRejected() != null && !uploadedFile.getLinesRejected().isEmpty()) {
                                quickFeedback = uploadedFile.getNoLinesRejected() + " Lines Rejected";
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
                                if (uploadedFile.isNoData()) {
                                    quickFeedback += "No Data";
                                } else {
                                    quickFeedback += "No Data Modified";
                                }
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
                        if (session.getAttribute("newdesign") != null ){
                            maintext.append("<td><button title=\"Details\"  onclick=\"showHideDiv('details" + counter + "'); return false;\" >Details</button></td>");

                        } else {
                            maintext.append("<td><a href=\"#\" class=\"button is-small\" title=\"Details\"  onclick=\"showHideDiv('details" + counter + "'); return false;\" >Details</a></td>");

                        }
                        // there will be a
                        maintext.append("</tr>");
                        maintext.append("<tr>");
                        maintext.append("<td colspan=\"4\"><div id=\"details" + counter + "\" style=\"overflow-x: auto;display : none\">" + ManageDatabasesController.formatUploadedFiles(Collections.singletonList(uploadedFile), counter, false, hasComments, session) + "</div></td>");
                        maintext.append("</tr>");
                        counter++;
                    }
                    maintext.append("<table>");
                     // need to jam in the import result, need better feedback than before
                    model.put("maintext", maintext.toString());
                    if (session.getAttribute("newdesign") != null){
                        return "pendingupload";
                    }
                    return "pendingupload2";
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
                if (runClearExecute) {
                    lookupValuesForFilesHTML.append("<input name=\"runClearExecute\" id=\"runClearExecute\" value=\"true\" type=\"hidden\" />\n");
                }


                final Map<String, Map<String, String>> finalLookupValuesForFiles = lookupValuesForFiles;
                if (!actuallyImport) { // before running validation grab the latest provenance - if it doesn't match when the actual import happens then warn the user about concurrent modification
                    session.setAttribute(LATESTPROVENANCE, RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).getMostRecentProvenance(loggedInUser.getDatabase().getPersistenceName()));
                } else {
                    session.removeAttribute(LATESTPROVENANCE);
                }
                boolean finalActuallyImport = actuallyImport;
                String finalRunClearExecuteCommand = runClearExecute ? runClearExecuteCommand : null;// pass it through if they checked it . . .
                new Thread(() -> {
                    if (finalLookupValuesForFiles != null && finalLookupValuesForFiles.get(pu.getFileName()) != null) { // could happen on a single xlsx upload. Apparently always zips but I'm concerned it may not be . . .
                        params.putAll(finalLookupValuesForFiles.get(pu.getFileName()));
                    }
                    UploadedFile uploadedFile = new UploadedFile(pu.getFilePath(), Collections.singletonList(pu.getFileName()), params, false, !finalActuallyImport);
                    try {
                        PendingUploadConfig puc = new PendingUploadConfig(finalLookupValuesForFiles, fileRejectFlags, fileRejectLines, finalRunClearExecuteCommand);
                        List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile, session, puc);
                        if (!finalActuallyImport) {
                            session.setAttribute(PARAMSPASSTHROUGH, lookupValuesForFilesHTML.toString());
                        } else {
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
                                if (rejectCheck.getFileNames().size() > 1 && StringLiterals.REJECTEDBYUSER.equals(rejectCheck.getError())) {
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
                            Path loaded = Paths.get(SpreadsheetService.getFilesToImportDir() + "/loaded");
                            if (!Files.exists(loaded)) {
                                Files.createDirectories(loaded);
                            }
                            // then move it somewhere useful
                            long timestamp = System.currentTimeMillis();
                            Files.copy(zipforpendinguploadresult, loaded.resolve(timestamp + pu.getFileName() + "results.zip"), StandardCopyOption.REPLACE_EXISTING);
                            // then adjust the pending upload record to have the report
                            pu.setImportResultPath(loaded.resolve(timestamp + pu.getFileName() + "results.zip").toString());
                            pu.setProcessedByUserId(loggedInUser.getUser().getId());
                            pu.setProcessedDate(LocalDateTime.now());
                            pu.setFileName(pu.getFileName() + " - " + "results"); // to make clear to users they'll be downloading results not the source file. See no harm in adjusting this thought perhaps some kind extra field should be used
                            PendingUploadDAO.store(pu);
                            if (loggedInUser.getUser().isAdministrator()) {
                                    session.setAttribute(ManageDatabasesController.IMPORTURLSUFFIX, "?uploadreports=true#3"); // if actually importing will land back on the pending uploads page
                            } else {
                                    session.setAttribute(ManageDatabasesController.IMPORTURLSUFFIX, "?uploadreports=true#1");
                            }
                        }
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
                    if (loggedInUser.getUser().isAdministrator()) {
                        model.addAttribute("targetController", "ManageDatabases");
                    } else {
                        model.addAttribute("targetController", "UserUpload");
                    }
                        return "importrunning2";
                } else {
                    if (session.getAttribute("newdesign") != null){
                        return "validationready";
                    }
                        return "validationready2";
                }
            }
        }
        return "redirect:/api/ManageDatabases";
    }

    private static void dealWithComment(PendingUpload pu, String commentId, String commentSave) {
        Comment forBusinessIdAndIdentifierAndTeam = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), commentId, pu.getTeam());
        if (forBusinessIdAndIdentifierAndTeam != null) {
            if (commentSave.isEmpty()) {
                CommentDAO.removeById(forBusinessIdAndIdentifierAndTeam);
            } else {
                forBusinessIdAndIdentifierAndTeam.setText(commentSave);
                CommentDAO.store(forBusinessIdAndIdentifierAndTeam);
            }
        } else if (!commentSave.isEmpty()) {
            CommentDAO.store(new Comment(0, pu.getBusinessId(), commentId, pu.getTeam(), commentSave));
        }
    }

    // like the one in ManageDatabasesController but for sheets. Started by modifying that but it got messy quickly hence a new function in here . . .
    public static void formatUploadedFilesForSheet(PendingUpload pu, List<UploadedFile> uploadedFiles, Sheet sheet) {
        int rowIndex = 0;
        int cellIndex = 0;
        int fileIndex = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            Row row = sheet.createRow(rowIndex++);
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            for (String name : names) {
                row.createCell(cellIndex++).setCellValue(name);
            }
            if (uploadedFile.isConvertedFromWorksheet()) {
                row.createCell(cellIndex).setCellValue("Converted from worksheet");
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
            row.createCell(cellIndex).setCellValue(uploadedFile.getProcessingDuration() + " ms");
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Time to Number of lines imported");
            row.createCell(cellIndex).setCellValue(uploadedFile.getNoLinesImported());
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Number of values adjusted");
            row.createCell(cellIndex).setCellValue(uploadedFile.getNoValuesAdjusted());
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Number of names adjusted");
            row.createCell(cellIndex).setCellValue(uploadedFile.getNoNamesAdjusted());

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            row = sheet.createRow(rowIndex++);

            if (uploadedFile.getTopHeadings() != null && !uploadedFile.getTopHeadings().isEmpty()) {
                row.createCell(cellIndex).setCellValue("Top headings");
                for (RowColumn rowColumn : uploadedFile.getTopHeadings().keySet()) {
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(BookUtils.rangeToText(rowColumn.getRow(), rowColumn.getColumn()));
                    row.createCell(cellIndex).setCellValue(uploadedFile.getTopHeadings().get(rowColumn));
                }
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }

            if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) {
                row.createCell(cellIndex).setCellValue("Headings with file headings");
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
                    HeadingWithInterimLookup headingWithInterimLookup = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                    if (headingWithInterimLookup != null) {
                        if (headingWithInterimLookup.getInterimLookup() != null) {
                            row.createCell(cellIndex++).setCellValue(headingWithInterimLookup.getInterimLookup());
                        }
                        row.createCell(cellIndex).setCellValue(headingWithInterimLookup.getInterimLookup());
                    } else {
                        row.createCell(cellIndex).setCellValue("** UNUSED **");
                    }
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Headings without file headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (HeadingWithInterimLookup headingWithInterimLookup : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
                    if (headingWithInterimLookup.getInterimLookup() != null) { // it could be null now as we support a non file heading azquo heading on the Import Model sheet
                        row.createCell(cellIndex++).setCellValue(headingWithInterimLookup.getInterimLookup());
                    }
                    row.createCell(cellIndex).setCellValue(headingWithInterimLookup.getHeading());
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getSimpleHeadings() != null) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Simple Headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String heading : uploadedFile.getSimpleHeadings()) {
                    row.createCell(cellIndex).setCellValue(heading);
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (!uploadedFile.getLinesRejected().isEmpty()) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Line Errors");
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
                    sheet.createRow(rowIndex++);
                }
            }
            if (uploadedFile.getPostProcessingResult() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Post processing result");
                row.createCell(cellIndex).setCellValue(uploadedFile.getPostProcessingResult());
                cellIndex = 0;
                sheet.createRow(rowIndex++);
            }
            // now the warning lines that will be a little more complex and have tickboxes
            // We're going to need descriptions of the errors, put them all in a set
            Set<String> errorsSet = new HashSet<>();
            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                errorsSet.addAll(warningLine.getErrors().keySet());
            }
            List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
            rowIndex = lineWarningsForSheet(pu, uploadedFile, rowIndex, sheet, errors, true, null, fileIndex);
            if (uploadedFile.getError() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("ERROR");
                row.createCell(cellIndex).setCellValue(uploadedFile.getError());
                cellIndex = 0;
                sheet.createRow(rowIndex++);
            }

            if (!uploadedFile.isDataModified()) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                if (uploadedFile.isNoData()) {
                    row.createCell(cellIndex).setCellValue("NO DATA");
                } else {
                    row.createCell(cellIndex).setCellValue("NO DATA MODIFIED");
                }
                cellIndex = 0;
                sheet.createRow(rowIndex++);
            }
            fileIndex++;
        }
    }

    // overview that will hopefully be useful to the user
    private static void summaryUploadFeedbackForSheet(List<UploadedFile> uploadedFiles, Sheet sheet, PendingUpload pu) {
        int rowIndex = 0;
        int cellIndex;
        int fileIndex = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            cellIndex = 0;
            Row row = sheet.createRow(rowIndex++);
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            for (String name : names) {
                row.createCell(cellIndex++).setCellValue(name);
            }
            if (uploadedFile.getError() != null) {
                row.createCell(cellIndex).setCellValue(uploadedFile.getError());
                sheet.createRow(rowIndex++);
                sheet.createRow(rowIndex++);
                continue; // necessary?
            }

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            sheet.createRow(rowIndex++);
            // need to jam in parameters. Firstly as it's useful info and secondly as it will be needed if doing a reimport based on this file
            if (uploadedFile.getParameters() != null && !uploadedFile.getParameters().isEmpty()){
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue(StringLiterals.PARAMETERS);
                for (Map.Entry<String, String> pair : uploadedFile.getParameters().entrySet()){
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(pair.getKey());
                    if (pair.getKey().equalsIgnoreCase(ImportService.IMPORTVERSION)){ // then knock off any trailing numbers
                        String value = pair.getValue();
                        String end = value.substring(value.length() - 1); // should be last char
                        if (NumberUtils.isNumber(end)){
                            value = value.substring(0, value.length() - 1);
                        }
                        row.createCell(cellIndex).setCellValue(value);
                    } else {
                        row.createCell(cellIndex).setCellValue(pair.getValue());
                    }
                }
                cellIndex = 0;
                sheet.createRow(rowIndex++);
                sheet.createRow(rowIndex++);
            }

            // We're going to need descriptions of the errors, put them all in a set
            Set<String> errorsSet = new HashSet<>();
            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                errorsSet.addAll(warningLine.getErrors().keySet());
            }
            List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
            rowIndex = lineWarningsForSheet(pu, uploadedFile, rowIndex, sheet, errors, true, null, fileIndex);
            if (uploadedFile.getIgnoreLinesValues() != null) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue(StringLiterals.MANUALLYREJECTEDLINES);
                // jumping the cell index across align it with the table above
                cellIndex = errors.size();
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Comment");
                row.createCell(cellIndex).setCellValue("#");
                cellIndex = errors.size();
                row = sheet.createRow(rowIndex++);
                ArrayList<Integer> sort = new ArrayList<>(uploadedFile.getIgnoreLinesValues().keySet());
                Collections.sort(sort);
                for (Integer lineNo : sort) {
                    // ok try to get comments now we have a line reference
                    // don't need to remove " from the identifier, it should have been zapped already
                    Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), uploadedFile.getIgnoreLines().get(lineNo), pu.getTeam());
                    row.createCell(cellIndex++).setCellValue(comment != null ? comment.getText() : "");
                    row.createCell(cellIndex++).setCellValue(lineNo);
                    String[] split = uploadedFile.getIgnoreLinesValues().get(lineNo).split("\t");
                    for (String cell : split) {
                        // redundant and it stops things being lined up
                        if (!cell.startsWith(StringLiterals.DELIBERATELYSKIPPINGLINE)){
                            row.createCell(cellIndex++).setCellValue(cell);
                        }
                    }
                    cellIndex = errors.size();
                    row = sheet.createRow(rowIndex++);
                }
            }
            fileIndex++;
        }
    }

    private static int lineWarningsForSheet(PendingUpload pu, UploadedFile uploadedFile, int rowIndex, Sheet sheet, List<String> errors, boolean addHeadings, List<String> paramsHeadings, int fileIndex) {
        if (!uploadedFile.getWarningLines().isEmpty()) {
            int cellIndex = 0;
            Row row;
            if (addHeadings) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Line Warnings" + (paramsHeadings != null ? ", sorting rows below the headings is fine but please do not modify data outside the first two columns, this may confuse the system." : ""));
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String error : errors) {
                    row.createCell(cellIndex).setCellValue(error);
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
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("Load");
                    }
                    row.createCell(cellIndex++).setCellValue("Comment");
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File");
                        for (String param : paramsHeadings) {
                            row.createCell(cellIndex++).setCellValue(param);
                        }
                        row.createCell(cellIndex++).setCellValue("Identifier");
                    }
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File Index");
                    }
                    row.createCell(cellIndex++).setCellValue("Line No");
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (!headingCol.isEmpty()) {
                            row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - 1));// think that's right . . .
                        } else {
                            row.createCell(cellIndex++).setCellValue("");
                        }
                    }
                } else {
                    row = sheet.createRow(rowIndex++);
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("Load");
                    }
                    row.createCell(cellIndex++).setCellValue("Comment");
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File");
                        for (String param : paramsHeadings) {
                            row.createCell(cellIndex++).setCellValue(param);
                        }
                        row.createCell(cellIndex++).setCellValue("Identifier");
                    }
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File Index");
                    }
                    row.createCell(cellIndex++).setCellValue("Line No");
                }
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            } else {
                row = sheet.getRow(sheet.getLastRowNum());
            }

            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                if (paramsHeadings != null) {
                    row.createCell(cellIndex++).setCellValue(""); // empty load cell, expects a x or maybe anything?
                }
                String commentValue = "";
                Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), warningLine.getIdentifier().replace("\"", ""), pu.getTeam());
                if (comment != null) {
                    commentValue = comment.getText();
                }
                row.createCell(cellIndex++).setCellValue(commentValue);
                if (paramsHeadings != null) {
                    row.createCell(cellIndex++).setCellValue(uploadedFile.getFileNamesAsString());
                    for (String param : paramsHeadings) {
                        row.createCell(cellIndex++).setCellValue(uploadedFile.getParameter(param) != null ? uploadedFile.getParameter(param) : "");
                    }
                    row.createCell(cellIndex++).setCellValue(warningLine.getIdentifier().replace("\"", ""));
                }

                for (String error : errors) {
                    row.createCell(cellIndex++).setCellValue(warningLine.getErrors().getOrDefault(error, ""));
                }
                if (paramsHeadings != null) {
                    row.createCell(cellIndex++).setCellValue(fileIndex);
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
        return rowIndex;
    }
}