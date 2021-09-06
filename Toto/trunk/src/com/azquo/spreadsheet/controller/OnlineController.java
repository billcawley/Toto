package com.azquo.spreadsheet.controller;

import com.azquo.DoubleAndOrString;
import com.azquo.StringLiterals;
import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.SFTPUtilities;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.zk.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.CellRegion;
import io.keikai.model.SName;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 22/04/14.
 * <p>
 * EFC : Aiming to reduce parameters as much as possible
 * <p>
 * The main report online report viewing controller, deals with upload/save/execute and a more graceful loading of reports showing a loading screen online as opposed to simply
 * blocking the response until done.
 * <p>
 * Notably it also contains the code for dealing with a report that has been downloaded, the data modified, then uploaded again - todo - move this to another controller
 */

@Controller
@RequestMapping("/Online")
public class OnlineController {


    public static class DisplayHeading {
        private final String id;
        private final String name;
        private final String type;
        private final boolean isset;
        private final boolean hasGrandchildren;

        public DisplayHeading(String id, String name, String type, boolean isset, boolean hasGrandchildren) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.isset = isset;
            this.hasGrandchildren = hasGrandchildren;

        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean getIsset() {
            return isset;
        }

        public boolean getHasGrandchildren() {
            return hasGrandchildren;
        }


    }


    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;
    // break up into separate functions? I'm not sure it's that important while the class is under 500 lines. I suppose one could move uploading for example.


    public static final String BOOK = "BOOK";
    public static final String BOOK_PATH = "BOOK_PATH";
    private static final String SAVE_FLAG = "SAVE_FLAG";
    private static final String EXECUTE_FLAG = "EXECUTE_FLAG";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";
    public static final String CELL_SELECT = "CELL_SELECT";
    public static final String LOCKED = "LOCKED";
    public static final String LOCKED_RESULT = "LOCK_RESULT";
    public static final String XML = "XML";
    public static final String XMLZIP = "XMLZIP";

    private static final String EXECUTE = "EXECUTE";
    private static final String TEMPLATE = "TEMPLATE";
    private static final String UPLOAD = "UPLOAD";

    private static final SimpleDateFormat logDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "databaseid", required = false) String databaseId
            , @RequestParam(value = "permissionid", required = false) String permissionId
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "imagename", required = false, defaultValue = "") String imageName
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "externalcall", required = false) String externalcall
            , @RequestParam(value = "uploadfile", required = false) MultipartFile uploadfile

    ) {
        //Log.information("Edd is testing");

        if (!"1".equals(reportId) && "true".equals(request.getSession().getAttribute("excelToggle"))) {
            return "redirect:/api/ExcelInterface?" + request.getQueryString();
        }
        String result = "error: report unavailable";

        try {
            //long startTime = System.currentTimeMillis();
            try {
                LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                   if (loggedInUser == null) {
                       if (externalcall!=null){
                           request.getSession().setAttribute("externalcall", externalcall);
                       }
                       return "redirect:/api/Login";// I guess redirect to login page
                }
                // dealing with the report/database combo WAS below but I see no reason for this, try and resolve it now
                OnlineReport onlineReport = null;

                // for direct linking to reports it's useful to be able to set choices. From an email sent to Shaun :
                // choice_month=Nov-20&choice_costcentre=somewhere
                Enumeration<String> parameterNames = request.getParameterNames();
                while (parameterNames.hasMoreElements()){
                    String paramName = parameterNames.nextElement();
                    if (paramName.toLowerCase().startsWith("choice_")){
                        String value = request.getParameter(paramName);
                        SpreadsheetService.setUserChoice(loggedInUser, paramName.substring(7), value);
                    }
                }
                boolean isExternal = false;
                 if (externalcall!=null && externalcall.length()>0){
                    externalcall = externalcall.replace("_"," ");
                    String reportName = externalcall;
                      if (externalcall.contains(" with ")) {
                        reportName = externalcall.substring(0, externalcall.indexOf(" with ")).replace("`", "");
                        String context = externalcall.substring(externalcall.indexOf(" with ") + 6);
                        ChoicesService.setChoices(loggedInUser,context);
                    }
                    OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName.replace("_"," "));
                    reportId = "" + or.getId();
                    isExternal = true;

                }
                 final boolean external = isExternal;

                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) && reportId != null && reportId.length() > 0 && !reportId.equals("1")) { // admin, we allow a report and possibly db to be set
                    //report id is assumed to be integer - sent from the website
                    if (loggedInUser.getUser().isDeveloper()) { // for the user
                        onlineReport = OnlineReportDAO.findForIdAndUserId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                    } else { //any for the business for admin
                        if (reportId.equals("ADHOC")) {
                            List<String> databases = new ArrayList<>();
                            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
                            for (Database db : databaseList) {
                                databases.add(db.getName());
                            }
                            model.put("databases", databases);
                            List<DisplayHeading> displayHeadings = new ArrayList<DisplayHeading>();
                            for (int i = 0; i < 10; i++) {
                                displayHeadings.add(new DisplayHeading("heading" + i, "", "", false, false));

                            }
                            model.put("headings", displayHeadings);
                            model.put("reportDatabase", "");
                            return "adHocReport";

                        } else {
                            onlineReport = OnlineReportDAO.findForIdAndBusinessId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                        }
                    }
                    // todo - decide which method to switch databases - it seems id is not being used? Or is it in the user menus?
                    if (databaseId != null && databaseId.length() > 0) {
                        final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(onlineReport.getId());
                        for (int dbId : databaseIdsForReportId) {
                            if (dbId == Integer.parseInt(databaseId)) {
                                LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(dbId));
                            }
                        }
                    }
                    if (database != null && database.length()>0) {
                        LoginService.switchDatabase(loggedInUser, database);
                    }
                } else if (permissionId != null && permissionId.length() > 0) {
                    //new logic for permissions ad hoc on a report
//                    System.out.println("Checking permission : " + permissionId);
                    if (loggedInUser.getPermission(permissionId.toLowerCase()) != null) { // then we have a permission as set by a report
                        LoggedInUser.ReportDatabase permission = loggedInUser.getPermission(permissionId.toLowerCase());
//                        System.out.println("found permission : " + permission);
                        onlineReport = permission.getReport();
                        if (onlineReport != null) {
                            reportId = onlineReport.getId() + ""; // hack for permissions
                            LoginService.switchDatabase(loggedInUser, permission.getDatabase());
                        }
                    }else{
                       result = "error: user has no permission for this report";

                    }
                }
                if (opcode.equalsIgnoreCase(UPLOAD)) {
                    // revised logic - this is ONLY for uploading data entered in a downloaded report
                    reportId = "";
                    if (submit.length() > 0) {
                        // getting rid of database switch
                        String fileName = uploadfile.getOriginalFilename();
                        if (imageName.length() > 0) {
                            result = uploadImage(loggedInUser, uploadfile, imageName);
                        } else {
                            if (fileName.length() > 0) {
                                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp the upload to stop overwriting with a file with the same name is uploaded after
                                uploadfile.transferTo(moved);
                                result = fileName + " : " + uploadDataInReport(loggedInUser, moved.getAbsolutePath());
                            } else {
                                result = "no file to import";
                            }
                        }
                        //result = "File imported successfully";
                    } else {
                        model.addAttribute("database", loggedInUser.getDatabase().getName());
                        model.addAttribute("imagestorename", loggedInUser.getImageStoreName());
                        // what is this, should developes have access?
                        if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                            model.addAttribute("datachoice", "Y");
                        } else {
                            model.addAttribute("datachoice", "N");
                        }
                        return "upload";
                    }
                }
                // "1" - perhaps don't do this, or make it the default?
                if ("1".equals(reportId)) {
                    if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                        // viewing the menu means remove any locks
                        if (loggedInUser.getDatabase() != null) {
                            SpreadsheetService.unlockData(loggedInUser);
                        }
                        return "redirect:/api/ManageReports";
                    } else {
                        // db should have been set by the login, just set the default report
                        onlineReport = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                    }
                }
                // db and report should be sorted by now
                if (onlineReport != null) {
                    // ok the new sheet and the loading screen have added chunks of code here, should it be in a service or can it "live" here?
                    final int valueId = ServletRequestUtils.getIntParameter(request, "valueid", 0); // the value to be selected if it's in any of the regions . . . how to select?
                    HttpSession session = request.getSession();
                    if (session.getAttribute(reportId + "error") != null) { // push exception to the user
                        model.addAttribute("content", session.getAttribute(reportId + "error")); // for a simple display
                        session.removeAttribute(reportId + "error");// get rid of it from the session
                        return "utf8page"; // just return now, show the error and stop
                    }
                    if (session.getAttribute(reportId) != null) {
                        Book book = (Book) session.getAttribute(reportId);
                        request.setAttribute(OnlineController.BOOK, book); // push the rendered book into the request to be sent to the user
                        session.removeAttribute(reportId);// get rid of it from the session
                        // hmm, is putting attributes agains the book the way to go? I've used session in other places but it would be a pain for these two. Todo : standardise on something that makes sense.
                        model.put("showUnlockButton", book.getInternalBook().getAttribute(LOCKED));
                        model.put("lockedResult", book.getInternalBook().getAttribute(LOCKED_RESULT));
                        model.put("xml", book.getInternalBook().getAttribute(XML) != null);
                        model.put("xmlzip", book.getInternalBook().getAttribute(XMLZIP) != null);
                        model.put("showSave", session.getAttribute(reportId + SAVE_FLAG));
                        model.put("masterUser", loggedInUser.getUser().isMaster());
                        model.put("templateMode", "template".equalsIgnoreCase(opcode) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster() || loggedInUser.getUser().isDeveloper()));
                        model.put("showTemplate", loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster() || loggedInUser.getUser().isDeveloper());
                        model.put("execute", session.getAttribute(reportId + EXECUTE_FLAG));
                        session.removeAttribute(reportId + SAVE_FLAG);// get rid of it from the session
                        session.removeAttribute(reportId + EXECUTE_FLAG);
                        // sort the pdf merges, had forgotten this . . .
                        final List<SName> names = book.getInternalBook().getNames();
                        List<String> pdfMerges = new ArrayList<>();
                        for (SName name : names) {
                            if (name.getName().toLowerCase().startsWith(StringLiterals.AZPDF)) {
                                pdfMerges.add(name.getName().substring(StringLiterals.AZPDF.length()).replace("_", " "));
                            }
                        }
                        // if this NPEs then it's probably to do with sessions crossing . . .
                        Map<String, String> images = SpreadsheetService.getImageList(loggedInUser);
                        model.put("imagestorename", loggedInUser.getImageStoreName());

                        model.put("images", images);
                        model.addAttribute("pdfMerges", pdfMerges);
                        model.addAttribute("databaseName", loggedInUser.getDatabase().getName());
                        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                        String bannerColor = business.getBannerColor();
                        if (bannerColor == null || bannerColor.length() == 0) bannerColor = "#F58030";
                        String logo = business.getLogo();
                        if (logo == null || logo.length() == 0) logo = "logo_alt.png";
                        model.addAttribute("bannerColor", bannerColor);
                        model.addAttribute("logo", logo);
                        return "zsshowsheet";// show the sheet
                    }
                    // ok now I need to set the sheet loading but on a new thread
                    if (session.getAttribute(reportId + "loading") == null) { // don't wanna load it twice! This could be hit if the user refreshes while generating the report.
                        // yes there's a chance a user could cause a double load if they were really quick but I'm not that bothered about this
                        session.setAttribute(reportId + "loading", Boolean.TRUE);
                        // this is a bit hacky, the new thread doesn't want them reassigned, fair enough
                        final String finalReportId = reportId;
                        final OnlineReport finalOnlineReport = onlineReport;
                        final boolean templateMode = TEMPLATE.equalsIgnoreCase(opcode) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster() || loggedInUser.getUser().isDeveloper());
                        final boolean executeMode = opcode.toUpperCase().startsWith(EXECUTE);//opcode seems to become execute.execute
                        new Thread(() -> {
                            // so in here the new thread we set up the loading as it was originally before
                            try {
                                boolean executeNow = executeMode;
                                long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                                String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + finalOnlineReport.getFilenameForDisk();
                                Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                                if (external) {
                                    String deflectReport = SpreadsheetService.findDeflects(loggedInUser, book);
                                    if (deflectReport != null) {
                                        OnlineReport or2 = OnlineReportDAO.findForNameAndBusinessId(deflectReport, loggedInUser.getUser().getBusinessId());
                                        if (or2 != null) {
                                            bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + or2.getFilenameForDisk();
                                            book = Importers.getImporter().imports(new File(bookPath), "Report name");
                                        }
                                    }
                                }
                                book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                                book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                                // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                                System.out.println("Loading report : " + finalOnlineReport.getReportName());
                                book.getInternalBook().setAttribute(REPORT_ID, finalOnlineReport.getId());
                                if (!templateMode) {
                                    boolean executeName = false;
                                    // annoying, factor?
                                    for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
                                        Sheet sheet = book.getSheetAt(sheetNumber);
                                        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
                                        for (SName sName : namesForSheet) {
                                            if (sName.getName().equalsIgnoreCase(StringLiterals.EXECUTE)) {
                                                executeName = true;
                                            }
                                            if (sName.getName().equalsIgnoreCase(StringLiterals.PREEXECUTE)) {
                                                executeNow = true;
                                            }
                                        }
                                    }
                                    // todo, lock check here like execute
                                    session.setAttribute(finalReportId + EXECUTE_FLAG, executeName); // pretty crude but should do it
                                    Map<String, String> params = new HashMap<>();
                                    params.put("Report", finalOnlineReport.getReportName());
                                    loggedInUser.userLog(" Load report", params);
                                    session.setAttribute(finalReportId + SAVE_FLAG, ReportRenderer.populateBook(book, valueId));
                                } else {
                                    loggedInUser.setImageStoreName(""); // legacy thing to stop null pointer, should be zapped after getting rid of aspose
                                }

                                if (executeNow) {
                                    book = ReportExecutor.runExecuteCommandForBook(book, StringLiterals.EXECUTE); // standard, there's the option to execute the contents of a different names
                                    session.setAttribute(finalReportId + SAVE_FLAG, false); // no save button after an execute
                                }
                                long newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                                System.out.println();
                                System.out.println(logDf.format(new Date()) + " - " + loggedInUser.getUser().getEmail() + " Heap cost to populate book : " + (newHeapMarker - oldHeapMarker) / mb);
                                System.out.println();
                                session.setAttribute(finalReportId, book);
                            } catch (Exception e) { // changed to overall exception handling
                                e.printStackTrace(); // Could be when importing the book, just log it
                                session.setAttribute(finalReportId + "error", e.getMessage()); // put it here to puck up instead of the report
                                // todo - put an error book here? That could hold the results of an exception . . .
                            }
                             session.removeAttribute(finalReportId + "loading");
                        }).start();
                    }
                    model.addAttribute("reportid", reportId); // why not? should block on refreshes then
                    // edd pasting in here to get the banner colour working
                    Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                    String bannerColor = business.getBannerColor();
                    if (bannerColor == null || bannerColor.length() == 0) bannerColor = "#F58030";
                    String logo = business.getLogo();
                    if (logo == null || logo.length() == 0) logo = "logo_alt.png";
                    model.addAttribute("bannerColor", bannerColor);
                    model.addAttribute("logo", logo);
                    return "zsloading";
                    // was provenance setting here,
                }
                model.addAttribute("content", result);
            } catch (Exception e) {
//                logger.error("online controller error", e);
                model.addAttribute("content", "error:" + e.getMessage());
            }
            return "utf8page";
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    // when not multipart - this is a bit annoying, hopefully can find a way around it later
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "databaseid", required = false) String databaseId
            , @RequestParam(value = "permissionid", required = false) String permissionId
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "imagename", required = false, defaultValue = "") String imageName
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "externalcall", required = false, defaultValue = "") String externalcall

    ) {
        return handleRequest(model, request, reportId, databaseId, permissionId, opcode, database, imageName, submit, externalcall,null);
    }

    // these functions probably should be booted into a service but they make more sense in here than the ImportService

    // for when a user has downloaded a report, modified data, then uploaded
    // uses ZK as it will have to render a copy
    public static String uploadDataInReport(LoggedInUser loggedInUser, String tempPath) throws Exception {
        Book book;
        try {
            book = Importers.getImporter().imports(new File(tempPath), "Imported");
        } catch (Exception e) {
            e.printStackTrace();
            return "Import error - " + e.getMessage();
        }
        String reportName = null;
        SName reportRange = book.getInternalBook().getNameByName(StringLiterals.AZREPORTNAME);
        if (reportRange != null) {
            reportName = BookUtils.getSnameCell(reportRange).getStringValue().trim();
        }
        if (reportName != null) {
            OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName);
            Map<String, String> choices = uploadChoices(book);
            for (Map.Entry<String, String> choiceAndValue : choices.entrySet()) {
                SpreadsheetService.setUserChoice(loggedInUser, choiceAndValue.getKey(), choiceAndValue.getValue());
            }
            checkEditableSets(book, loggedInUser);
            final Book reportBook = Importers.getImporter().imports(new File(tempPath), "Report name");
            reportBook.getInternalBook().setAttribute(OnlineController.BOOK_PATH, tempPath);
            reportBook.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
            reportBook.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
            // this REALLY should have been commented - the load before will populate the logged in users sent cells correctly, that's
            ReportRenderer.populateBook(reportBook, 0, false);
            return fillDataRangesFromCopy(loggedInUser, book, or);
        }
        return "file doesn't appear to be an Azquo report";
    }

    // EFC note - I don't like this, want to remove it.
    public static String uploadImage(LoggedInUser loggedInUser, MultipartFile sourceFile, String fileName) throws Exception {
        String success = "image uploaded successfully";
        String sourceName = sourceFile.getOriginalFilename();
        String suffix = sourceName.substring(sourceName.indexOf("."));
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        String pathOffset = loggedInUser.getDatabase().getPersistenceName() + "/images/" + fileName + suffix;
        String destinationPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + pathOffset;
        if (databaseServer.getIp().equals(ImportService.LOCALIP)) {
            Path fullPath = Paths.get(destinationPath);
            Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
            Files.copy(sourceFile.getInputStream(), fullPath); // and copy
        } else {
            destinationPath = databaseServer.getSftpUrl() + pathOffset;
            SFTPUtilities.copyFileToDatabaseServer(sourceFile.getInputStream(), destinationPath);
        }
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        String imageList = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images");
        if (imageList != null) {//check if it's already in the list
            String[] images = imageList.split(",");
            for (String image : images) {
                if (image.trim().equals(fileName + suffix)) {
                    return success;
                }
            }
            imageList += "," + fileName + suffix;
        } else {
            imageList = fileName + suffix;
        }
        RMIClient.getServerInterface(databaseAccessToken.getServerIp()).setNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images", imageList);
        return success;
    }

    private static Map<String, String> uploadChoices(Book book) {
        //this routine extracts the useful information from an uploaded copy of a report.  The report will then be loaded and this information inserted.
        Map<String, String> choices = new HashMap<>();
        for (SName sName : book.getInternalBook().getNames()) {
            String rangeName = sName.getName().toLowerCase();
            if (rangeName.endsWith("chosen")) {
                //there is probably a more elegant solution than this....
                choices.put(rangeName.substring(0, rangeName.length() - 6), ImportService.getCellValue(book.getSheet(sName.getRefersToSheetName()), sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getString());
            }
        }
        return choices;
    }

    private static void checkEditableSets(Book book, LoggedInUser loggedInUser) {
        for (SName sName : book.getInternalBook().getNames()) {
            if (sName.getName().toLowerCase().startsWith(StringLiterals.AZROWHEADINGS)) {
                String region = sName.getName().substring(StringLiterals.AZROWHEADINGS.length());
                io.keikai.api.model.Sheet sheet = book.getSheet(sName.getRefersToSheetName());
                String rowHeading = ImportService.getCellValue(sheet, sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getString();
                if (rowHeading.toLowerCase().endsWith(" children editable")) {
                    String setName = rowHeading.substring(0, rowHeading.length() - " children editable".length()).replace("`", "");
                    SName displayName = getNameByName(StringLiterals.AZDISPLAYROWHEADINGS + region, sheet);
                    if (displayName != null) {
                        StringBuilder editLine = new StringBuilder();
                        editLine.append("edit:saveset ");
                        editLine.append("`").append(setName).append("` ");
                        CellRegion dispRegion = displayName.getRefersToCellRegion();
                        for (int rowNo = 0; rowNo < dispRegion.getRowCount(); rowNo++) {
                            editLine.append("`").append(ImportService.getCellValue(sheet, dispRegion.getRow() + rowNo, dispRegion.getColumn()).getString()).append("`,");
                        }
                        CommonReportUtils.getDropdownListForQuery(loggedInUser, editLine.toString());
                    }
                }
            }
        }
    }

    // for the download, modify and upload the report
    // todo - can we convert to apache poi?
    private static String fillDataRangesFromCopy(LoggedInUser loggedInUser, Book sourceBook, OnlineReport onlineReport) {
        StringBuilder errorMessage = new StringBuilder();
        int saveCount = 0;
        for (SName sName : sourceBook.getInternalBook().getNames()) {
            String name = sName.getName();
            String regionName = getRegionName(name);
            io.keikai.api.model.Sheet sheet = sourceBook.getSheet(sName.getRefersToSheetName());
            if (regionName != null) {
                CellRegion sourceRegion = sName.getRefersToCellRegion();
                if (name.toLowerCase().contains(StringLiterals.AZREPEATSCOPE)) { // then deal with the multiple data regions sent due to this
                    // need to gather associated names for calculations, the region and the data region, code copied and changewd from getRegionRowColForRepeatRegion, it needs to work well for a batch of cells not just one
                    SName repeatRegion = getNameByName(StringLiterals.AZREPEATREGION + regionName, sheet);
                    SName repeatDataRegion = getNameByName(StringLiterals.AZDATAREGION + regionName, sheet);
                    // deal with repeat regions, it means getting sent cells that have been set as following : loggedInUser.setSentCells(reportId, region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay)
                    if (repeatRegion != null && repeatDataRegion != null) {
                        int regionHeight = repeatRegion.getRefersToCellRegion().getRowCount();
                        int regionWitdh = repeatRegion.getRefersToCellRegion().getColumnCount();
                        int dataHeight = repeatDataRegion.getRefersToCellRegion().getRowCount();
                        int dataWitdh = repeatDataRegion.getRefersToCellRegion().getColumnCount();
                        // where the data starts in each repeated region
                        int dataStartRow = repeatDataRegion.getRefersToCellRegion().getRow() - repeatRegion.getRefersToCellRegion().getRow();
                        int dataStartCol = repeatDataRegion.getRefersToCellRegion().getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
                        // we can't really do a size comparison as before, we can simply run the region and see where we think there should be repeat reagions in the scope
                        for (int row = 0; row < sourceRegion.getRowCount(); row++) {
                            int repeatRow = row / regionHeight;
                            int rowInRegion = row % regionHeight;
                            for (int col = 0; col < sourceRegion.getColumnCount(); col++) {
                                int colInRegion = col % regionWitdh;
                                int repeatCol = col / regionWitdh;
                                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), sName.getRefersToSheetName(), regionName + "-" + repeatRow + "-" + repeatCol); // getting each time might be a little inefficient, can optimise if there is a performance problem here
                                if (colInRegion >= dataStartCol && rowInRegion >= dataStartRow
                                        && colInRegion <= dataStartCol + dataWitdh
                                        && rowInRegion <= dataStartRow + dataHeight
                                        && cellsAndHeadingsForDisplay != null) {
                                    final List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                                    final DoubleAndOrString cellValue = ImportService.getCellValue(sheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
                                    data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setNewStringValue(cellValue.getString());
                                    // added by Edd, should sort some numbers being ignored!
                                    if (cellValue.getDouble() != null) {
                                        data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setNewDoubleValue(cellValue.getDouble());
                                    } else { // I think defaulting to zero is correct here?
                                        data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setNewDoubleValue(0.0);
                                    }
                                }
                            }
                        }
                    }
                    return null;
                } else { // a normal data region. Note that the data region used by a repeat scope should be harmless here as it will return a null on getSentCells, no need to be clever
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), sName.getRefersToSheetName(), regionName);
                    if (cellsAndHeadingsForDisplay != null) {
                        //needs to be able to handle repeat regions here....
                        List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                        //NOTE - the import sheet may contain blank lines at the bottom and/or blank columns at the right where the original data region exceeds the size of the data found (row/column headings are sets).  This is acceptable
                        // TODO - WE SHOULD CHECK THAT THE HEADINGS MATCH
                        if (data.size() > 0 && data.size() <= sourceRegion.getRowCount() && data.get(0).size() <= sourceRegion.getColumnCount()) {//ignore region sizes which do not match (e.g. on transaction entries showing past entries)
                            //work on the original data size, not the uploaded size with the blank lines
                            for (int row = 0; row < data.size(); row++) {
                                for (int col = 0; col < data.get(0).size(); col++) {
                                    // note that this function might return a null double but no null string. Perhaps could be mroe consistent? THis area is a bit hacky . . .
                                    final DoubleAndOrString cellValue = ImportService.getCellValue(sheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
                                    data.get(row).get(col).setNewStringValue(cellValue.getString());
                                    // added by Edd, should sort some numbers being ignored!
                                    if (cellValue.getDouble() != null) {
                                        data.get(row).get(col).setNewDoubleValue(cellValue.getDouble());
                                    } else { // I think defaulting to zero is correct here?
                                        data.get(row).get(col).setNewDoubleValue(0.0);
                                    }
                                }
                            }
                        }
                        //AND THE ROW HEADINGS IF EDITABLE.
                    }
                    try {
                        final String result = SpreadsheetService.saveData(loggedInUser, onlineReport.getId(), onlineReport.getReportName(), sName.getRefersToSheetName(), regionName);
                        sourceBook.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                        ReportExecutor.runExecuteCommandForBook(sourceBook, StringLiterals.FOLLOWON); // followon should work after an upload of this type
                        if (!result.startsWith("true")) {// unlikely to fail here I think but catch it anyway . . .
                            errorMessage.append("- in region ").append(regionName).append(" -").append(result);
                        } else {
                            try {
                                saveCount += Integer.parseInt(result.substring(5));  //count follows the word 'true'
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMessage.append("- in region ").append(regionName).append(" -").append(e.getMessage());
                    }
                }
            }
        }
        return errorMessage + " - " + saveCount + " data items amended successfully";
    }

    private static SName getNameByName(String name, io.keikai.api.model.Sheet sheet) {
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        if (toReturn != null) {
            return toReturn;
        }
        // should we check the formula refers to the sheet here? I'm not sure. Applies will have been checked for above.
        return sheet.getBook().getInternalBook().getNameByName(name);
    }

    private static String getRegionName(String name) {
        if (name.toLowerCase().startsWith(StringLiterals.AZDATAREGION)) {
            return name.substring(StringLiterals.AZDATAREGION.length()).toLowerCase();
        }
        if (name.toLowerCase().startsWith(StringLiterals.AZREPEATSCOPE)) {
            return name.substring(StringLiterals.AZREPEATSCOPE.length()).toLowerCase();
        }
        return null;
    }

}