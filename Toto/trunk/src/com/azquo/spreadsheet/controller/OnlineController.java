package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import org.apache.commons.lang.math.NumberUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 22/04/14.
 * <p>
 * EFC : I'd like to zap a fair few of the parameters, now we've zapped AzquoBook more scope.
 */

@Controller
@RequestMapping("/Online")
public class OnlineController {
    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;
    // note : I'm now thinking dao objects are acceptable in controllers if moving the call to the service would just be a proxy

    // TODO : break up into separate functions?

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String BOOK = "BOOK";
    public static final String BOOK_PATH = "BOOK_PATH";
    private static final String SAVE_FLAG = "SAVE_FLAG";
    private static final String EXECUTE_FLAG = "EXECUTE_FLAG";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";
    public static final String CELL_SELECT = "CELL_SELECT";


    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "databaseid", required = false) String databaseId
            , @RequestParam(value = "permissionid", required = false) String permissionId
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "reporttoload", required = false, defaultValue = "") String reportToLoad
            , @RequestParam(value = "imagename", required = false, defaultValue = "") String imageName
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "uploadfile", required = false) MultipartFile uploadfile
            , @RequestParam(value = "template", required = false) String template
            , @RequestParam(value = "execute", required = false) String execute

    ) {
        try {
            if (reportToLoad != null && reportToLoad.length() > 0) {
                reportId = reportToLoad;
            }
            //long startTime = System.currentTimeMillis();
            Permission permission;
            try {
                LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                if (loggedInUser == null) {
                    if (user == null) {
                        return "redirect:/api/Login";// I guess redirect to login page
                    }
                    loggedInUser = LoginService.loginLoggedInUser(request.getSession().getId(), database, user, password, false);
                    if (loggedInUser == null) {
                        model.addAttribute("content", "error:no connection id");
                        return "utf8page";
                    } else {
                        request.getSession().setAttribute(LoginController.LOGGED_IN_USER_SESSION, loggedInUser);
                    }
                }
                // dealing with the report/database combo WAS below but I see no reason for this, try and resolve it now
                OnlineReport onlineReport = null;
                if (loggedInUser.getUser().isAdministrator() && reportId != null && reportId.length() > 0 && !reportId.equals("1")) { // admin, we allow a report and possibly db to be set
                    //report id is assumed to be integer - sent from the website
                    onlineReport = OnlineReportDAO.findForIdAndBusinessId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                    // todo - decide which method to switch databases
                    if (databaseId != null && databaseId.length() > 0){
                        final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(onlineReport.getId());
                        for (int dbId : databaseIdsForReportId){
                            if (dbId == Integer.parseInt(databaseId)){
                                LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(dbId));
                            }
                        }
                    }
                    if (database != null){
                        LoginService.switchDatabase(loggedInUser, database);
                    }
                } else if (permissionId != null && permissionId.length() > 0) {
                    if (NumberUtils.isNumber(permissionId)){
                        permission = PermissionDAO.findById(Integer.parseInt(permissionId));
                        if (permission != null && permission.getUserId() == loggedInUser.getUser().getId()){
                            onlineReport = OnlineReportDAO.findById(permission.getReportId());
                            reportId = onlineReport.getId() + ""; // hack for permissions
                            LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(permission.getDatabaseId()));
                        }
                    } else { //new logic for permissions ad hoc on a report
                        if (loggedInUser.getPermissionsFromReport().get(permissionId.toLowerCase()) != null){ // then we have a permission as set by a report
                            onlineReport = OnlineReportDAO.findForNameAndBusinessId(permissionId, loggedInUser.getUser().getBusinessId());
                            if (onlineReport != null){
                                reportId = onlineReport.getId() + ""; // hack for permissions
                                LoginService.switchDatabase(loggedInUser, DatabaseDAO.findForName(loggedInUser.getUser().getBusinessId(), loggedInUser.getPermissionsFromReport().get(permissionId.toLowerCase())));
                            }
                        }
                    }
                }
                if (onlineReport != null){
                    onlineReport.setPathname(loggedInUser.getBusinessDirectory()); // todo - sort this, it makes no sense
                }
                String result = "error: no action taken";
                // highlighting etc. From the top right menu and the azquobook context menu, can be zapped later
                // I wonder if this should be a different controller
                if (opcode.equals("upload")) {
                    reportId = "";
                    if (submit.length() > 0) {
                        // getting rid of database switch
                        String fileName = uploadfile.getOriginalFilename();
                        if (imageName.length() > 0){
                            result = ImportService.uploadImage(loggedInUser,uploadfile, imageName);
                        } else {
                            if (fileName.length()> 0) {
                                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + fileName);
                                uploadfile.transferTo(moved);
                                result = ImportService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), loggedInUser.getLanguages(), true); // always a data upload from here
                            }else{
                                result = "no file to import";
                            }
                        }
                        //result = "File imported successfully";
                    } else {
                        model.addAttribute("database", loggedInUser.getDatabase().getName());
                        model.addAttribute("imagestorename", loggedInUser.getImageStoreName());
                        if (loggedInUser.getUser().isAdministrator()){
                            model.addAttribute("datachoice","Y");
                        }else{
                            model.addAttribute("datachoice","N");
                        }
                        return "upload";
                    }
                }
                if ("1".equals(reportId)) {
                    if (!loggedInUser.getUser().isAdministrator()) {
                        final List<Permission> forUserId = PermissionDAO.findForUserId(loggedInUser.getUser().getId());// new model this should be all we need . . .
                        if (forUserId.size() == 1){// one report, show it
                            permission = forUserId.get(0);
                            onlineReport = OnlineReportDAO.findById(permission.getReportId());
                            onlineReport.setPathname(loggedInUser.getBusinessDirectory()); // hack! todo, sort
                            reportId = onlineReport.getId() + ""; // hack for permissions
                            LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(permission.getDatabaseId()));
                        } else {
                            SpreadsheetService.showUserMenu(model, loggedInUser);// user menu being what magento users typically see when logging in, a velocity page
                            return "azquoReports";
                        }
                    } else {
                        return "redirect:/api/ManageReports";
                    }
                }
                // db and report should be sorted by now
                if ((opcode.length() == 0 || opcode.equals("loadsheet")) && onlineReport != null) {
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
                            model.put("showSave", session.getAttribute(reportId + SAVE_FLAG));
                            model.put("masterUser", loggedInUser.getUser().isMaster());
                            model.put("templateMode", "TRUE".equalsIgnoreCase(template) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster()));
                            model.put("showTemplate", loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster());
                            model.put("execute", session.getAttribute(reportId + EXECUTE_FLAG));
                            session.removeAttribute(reportId + SAVE_FLAG);// get rid of it from the session
                            session.removeAttribute(reportId + EXECUTE_FLAG);
                            // sort the pdf merges, had forgotten this . . .
                            final List<SName> names = book.getInternalBook().getNames();
                            List<String> pdfMerges = new ArrayList<>();
                            for (SName name : names) {
                                if (name.getName().startsWith("az_PDF")) {
                                    pdfMerges.add(name.getName().substring("az_PDF".length()).replace("_", " "));
                                }
                            }
                            Map<String, String> images =  SpreadsheetService.getImageList(loggedInUser);
                            model.put("imagestorename", loggedInUser.getImageStoreName());

                            model.put("images", images);
                            model.addAttribute("pdfMerges", pdfMerges);
                            return "zsshowsheet";// show the sheet
                        }
                        // ok now I need to set the sheet loading but on a new thread
                        if (session.getAttribute(reportId + "loading") == null) { // don't wanna load it twice! This could be hit if the user refreshes while generating the report.
                            // yes there's a chance a user could cause a double load if they were really quick but I'm not that bothered about this
                            session.setAttribute(reportId + "loading", Boolean.TRUE);
                            // this is a bit hacky, the new thread doesn't want them reassigned, fair enough
                            final String finalReportId = reportId;
                            final OnlineReport finalOnlineReport = onlineReport;
                            final LoggedInUser finalLoggedInUser = loggedInUser;
                            final boolean templateMode = "TRUE".equalsIgnoreCase(template) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster());
                            final boolean executeMode = "TRUE".equalsIgnoreCase(execute);
                            new Thread(() -> {
                                // so in here the new thread we set up the loading as it was originally before
                                try {
                                    long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                                    long newHeapMarker = oldHeapMarker;
                                    String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + finalOnlineReport.getPathname() + "/onlinereports/" + finalOnlineReport.getFilename();
                                    final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                                    book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                                    book.getInternalBook().setAttribute(LOGGED_IN_USER, finalLoggedInUser);
                                    // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                                    System.out.println("Loading report : " + finalOnlineReport.getReportName());
                                    book.getInternalBook().setAttribute(REPORT_ID, finalOnlineReport.getId());
                                    if (!templateMode){
                                        boolean executeName = false;
                                        // annoying, factor?
                                        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
                                            Sheet sheet = book.getSheetAt(sheetNumber);
                                            List<SName> namesForSheet = ZKAzquoBookUtils.getNamesForSheet(sheet);
                                            for (SName sName : namesForSheet) {
                                                if (sName.getName().equalsIgnoreCase(ZKAzquoBookUtils.EXECUTE)) {
                                                    executeName = true;
                                                }
                                            }
                                        }
                                        session.setAttribute(finalReportId + EXECUTE_FLAG, executeName); // pretty crude but should do it
                                        if (executeMode){
                                            ZKAzquoBookUtils.runExecuteCommandForBook(book, ZKAzquoBookUtils.EXECUTE); // standard, there's the option to execute the contents of a different namers
                                            session.setAttribute(finalReportId + SAVE_FLAG, false); // no save button after an execute
                                        } else {
                                            session.setAttribute(finalReportId + SAVE_FLAG, ZKAzquoBookUtils.populateBook(book, valueId));
                                        }
                                    } else {
                                        finalLoggedInUser.setImageStoreName(""); // legacy thing to stop null pointer, should be zapped after getting rid of aspose
                                    }
                                    newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
                                    System.out.println();
                                    System.out.println("Heap cost to populate book : " + (newHeapMarker - oldHeapMarker) / mb);
                                    System.out.println();
                                    oldHeapMarker = newHeapMarker;
                                    session.setAttribute(finalReportId, book);
                                } catch (Exception e) { // changed to overall exception handling
                                    e.printStackTrace(); // Could be when importing the book, just log it
                                    session.setAttribute(finalReportId + "error", e.getMessage()); // put it here to puck up instead of the report
                                    // todo - put an error book here? That could hold the results of an exception . . .
                                }
                            }).start();
                            session.removeAttribute(reportId + "loading");
                        }
                        model.addAttribute("reportid", reportId); // why not? should block on refreshes then
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
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "databaseid", required = false) String databaseId
            , @RequestParam(value = "permissionid", required = false) String permissionId
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "reporttoload", required = false, defaultValue = "") String reportToLoad
            , @RequestParam(value = "imagename", required = false, defaultValue = "") String imageName
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "template", required = false, defaultValue = "") String template
            , @RequestParam(value = "execute", required = false, defaultValue = "") String execute
    ) {
        return handleRequest(model, request, user, password, reportId, databaseId, permissionId, opcode, database, reportToLoad, /*dataChoice,imageStoreName, */ imageName, submit, null, template, execute);
    }
}