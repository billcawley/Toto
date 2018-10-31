package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.zk.ReportExecutor;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.azquo.spreadsheet.zk.BookUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.SName;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 22/04/14.
 * <p>
 * EFC : Aiming to reduce parameters as much as possible
 * <p>
 * The main report online report viewing controller, deals with upload/save/execute and a more graceful loading of reports showing a loading screen online as opposed to simply
 * blocking the response until done.
 * <p>
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

        public DisplayHeading(String id,String name, String type, boolean isset, boolean hasGrandchildren) {
            this.id = id;
              this.name = name;
            this.type = type;
            this.isset = isset;
            this.hasGrandchildren = hasGrandchildren;

        }

        public String getId() {return id; }

        public String getName() {
            return name;
        }

        public String getType() { return type; }

        public boolean getIsset() {return isset; }

        public boolean getHasGrandchildren() { return hasGrandchildren; }


    }


    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;
    // break up into separate functions? I'm not sure it's that important while the class is under 500 lines. I suppose one could move uploading for example.

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String BOOK = "BOOK";
    public static final String BOOK_PATH = "BOOK_PATH";
    private static final String SAVE_FLAG = "SAVE_FLAG";
    private static final String EXECUTE_FLAG = "EXECUTE_FLAG";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";
    public static final String CELL_SELECT = "CELL_SELECT";
    public static final String LOCKED = "LOCKED";
    public static final String LOCKED_RESULT = "LOCK_RESULT";

    private static final String EXECUTE = "EXECUTE";
    private static final String TEMPLATE = "TEMPLATE";
    private static final String UPLOAD = "UPLOAD";
    private static final String UPLOADTEMPLATE = "UPLOADTEMPLATE";

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
            , @RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionId
            , @RequestParam(value = "uploadfile", required = false) MultipartFile uploadfile

    ) {
        if (!"1".equals(reportId) && "true".equals(request.getSession().getAttribute("excelToggle"))) {
            return "redirect:/api/ExcelInterface?" + request.getQueryString();
        }
        try {
               //long startTime = System.currentTimeMillis();
            try {
                LoggedInUser tempLoggedInUser = null;
                if (sessionId != null && sessionId.length() > 0){
                    tempLoggedInUser = ExcelController.excelConnections.get(sessionId);
                    request.getSession().setAttribute(LoginController.LOGGED_IN_USER_SESSION, tempLoggedInUser); // so it keeps working as they click around!
                }
                if (tempLoggedInUser==null){
                    tempLoggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                }
                if (tempLoggedInUser == null) {
                    return "redirect:/api/Login";// I guess redirect to login page
                }
                final LoggedInUser loggedInUser = tempLoggedInUser;
                // dealing with the report/database combo WAS below but I see no reason for this, try and resolve it now
                OnlineReport onlineReport = null;
                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) && reportId != null && reportId.length() > 0 && !reportId.equals("1")) { // admin, we allow a report and possibly db to be set
                    //report id is assumed to be integer - sent from the website
                    if (loggedInUser.getUser().isDeveloper()) { // for the user
                        onlineReport = OnlineReportDAO.findForIdAndUserId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                    } else { //any for the business for admin
                        if (reportId.equals("ADHOC")){
                            List<String> databases = new ArrayList<>();
                            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
                           for (Database db : databaseList) {
                                databases.add(db.getName());
                           }
                            model.put("databases",databases);
                            List<DisplayHeading> displayHeadings = new ArrayList<DisplayHeading>();
                            for (int i=0;i<10;i++){
                                displayHeadings.add(new DisplayHeading("heading" +i,"","",false,false));

                            }
                            model.put("headings", displayHeadings);
                            model.put("reportDatabase","");
                            return "adHocReport";

                        }else{
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
                    if (database != null) {
                        LoginService.switchDatabase(loggedInUser, database);
                    }
                } else if (permissionId != null && permissionId.length() > 0) {
                    //new logic for permissions ad hoc on a report
                    if (loggedInUser.getPermissionsFromReport().get(permissionId.toLowerCase()) != null) { // then we have a permission as set by a report
                        onlineReport = OnlineReportDAO.findForNameAndBusinessId(permissionId, loggedInUser.getUser().getBusinessId());
                        if (onlineReport != null) {
                            reportId = onlineReport.getId() + ""; // hack for permissions
                            LoginService.switchDatabase(loggedInUser, loggedInUser.getPermissionsFromReport().get(permissionId.toLowerCase()).getSecond());
                        }
                    }
                }
                String result = "error: user has no permission for this report";
                // highlighting etc. From the top right menu and the azquobook context menu, can be zapped later
                // I wonder if this should be a different controller
                if (opcode.equalsIgnoreCase(UPLOAD) || opcode.equalsIgnoreCase(UPLOADTEMPLATE)) {
                    boolean isData = true;
                    if (opcode.equalsIgnoreCase(UPLOADTEMPLATE)){
                        isData = false;
                    }
                    reportId = "";
                    if (submit.length() > 0) {
                        // getting rid of database switch
                        String fileName = uploadfile.getOriginalFilename();
                        if (imageName.length() > 0) {
                            result = ImportService.uploadImage(loggedInUser, uploadfile, imageName);
                        } else {
                            if (fileName.length() > 0) {
                                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp the upload to stop overwriting with a file with the same name is uploaded after
                                uploadfile.transferTo(moved);
                                result = ImportService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), isData);
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
                            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZPDF)) {
                                pdfMerges.add(name.getName().substring(ReportRenderer.AZPDF.length()).replace("_", " "));
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
                        if (bannerColor==null || bannerColor.length()==0) bannerColor = "#F58030";
                        String logo = business.getLogo();
                        if (logo==null || logo.length()==0) logo = "logo_alt.png";
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
                                long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                                String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + finalOnlineReport.getFilenameForDisk();
                                Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
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
                                            if (sName.getName().equalsIgnoreCase(ReportRenderer.EXECUTE)) {
                                                executeName = true;
                                            }
                                        }
                                    }
                                    // todo, lock check here like execute
                                    session.setAttribute(finalReportId + EXECUTE_FLAG, executeName); // pretty crude but should do it
                                    loggedInUser.userLog(loggedInUser.getUser().getEmail() + " Load report : " + finalOnlineReport.getReportName());
                                    session.setAttribute(finalReportId + SAVE_FLAG, ReportRenderer.populateBook(book, valueId));
                                } else {
                                    loggedInUser.setImageStoreName(""); // legacy thing to stop null pointer, should be zapped after getting rid of aspose
                                }

                                if (executeMode) {
                                    book = ReportExecutor.runExecuteCommandForBook(book, ReportRenderer.EXECUTE); // standard, there's the option to execute the contents of a different names
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
                    if (bannerColor==null || bannerColor.length()==0) bannerColor = "#F58030";
                    String logo = business.getLogo();
                    if (logo==null || logo.length()==0) logo = "logo_alt.png";
                    model.addAttribute("bannerColor", bannerColor);
                    model.addAttribute("logo", logo);
                    return "zsloading";
                    // was provenance setting here,
                }
                model.addAttribute("content", result);
            } catch (Exception e) {
                logger.error("online controller error", e);
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
            , @RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionId
    ) {
        return handleRequest(model, request, reportId, databaseId, permissionId, opcode, database, imageName, submit, sessionId, null);
    }
}