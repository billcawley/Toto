package com.azquo.spreadsheet.controller;

import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.SName;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bill on 22/04/14.
 * <p>
 * Currently deals with a fair bit for AzquoBook, this may shrink in time.
 */

@Controller
@RequestMapping("/Online")
public class OnlineController {

    // todo get rid of dao objects in controllers

    @Autowired
    private LoginService loginService;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private DatabaseServerDAO databaseServerDAO;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private UserRegionOptionsDAO userRegionOptionsDAO;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private ImportService importService;


    // TODO : break up into separate functions

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    public static final String BOOK = "BOOK";
    public static final String BOOK_PATH = "BOOK_PATH";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";


    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "editedname", required = false) String choiceName
            , @RequestParam(value = "editedvalue", required = false) String choiceValue
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "jsonfunction", required = false, defaultValue = "azquojsonfeed") String jsonFunction
            , @RequestParam(value = "row", required = false, defaultValue = "") String rowStr
            , @RequestParam(value = "col", required = false, defaultValue = "") String colStr
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "spreadsheetname", required = false, defaultValue = "") String spreadsheetName
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "reporttoload", required = false, defaultValue = "") String reportToLoad
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "provline", required = false, defaultValue = "") String provline
            , @RequestParam(value = "uploadfile", required = false) MultipartFile uploadfile

    ) {
        try {
            String callerId = request.getRemoteAddr();
            if (callerId != null && user != null && user.equals("demo@user.com")) { // for reports linked directly from the website
                user += callerId;
            }
            if (reportToLoad != null && reportToLoad.length() > 0) {
                reportId = reportToLoad;
            }
            //long startTime = System.currentTimeMillis();
            Database db;
            try {
                LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                if (loggedInUser == null) {
                    if (user == null) {
                        return "utf8page";// note - this means a blank if no session or credentials. Should maybe redirect to login page?
                    }
                    if (user.equals("demo@user.com")) {
                        user += request.getRemoteAddr();
                    }
                    loggedInUser = loginService.loginLoggedInUser(request.getSession().getId(), database, user, password, false);
                    if (loggedInUser == null) {
                        model.addAttribute("content", "error:no connection id");
                        return "utf8page";
                    } else {
                        request.getSession().setAttribute(LoginController.LOGGED_IN_USER_SESSION, loggedInUser);
                    }
                }
                OnlineReport onlineReport = null;
                if (reportId != null && reportId.length() > 0 && !reportId.equals("1")) {
                    //report id is assumed to be integer - sent from the website
                    onlineReport = onlineReportDAO.findById(Integer.parseInt(reportId));
                }
                /* todo, sort provenance setting later
                if (onlineReport != null && onlineReport.getId() > 1 && loggedInConnection.hasAzquoMemoryDB()) {
                    loggedInConnection.setNewProvenance("spreadsheet", onlineReport.getReportName(), "");
                }*/
                String result = "error: no action taken";
                int row = 0;
                try {
                    row = Integer.parseInt(rowStr);
                } catch (Exception e) {
                    //rowStr can be blank or '0'
                }
                if (opcode.equals("provline")) {
                    String reportName = spreadsheetService.setChoices(loggedInUser, provline);
                    onlineReport = null;
                    if (reportName != null) {
                        onlineReport = onlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName);
                        if (onlineReport == null) {
                            onlineReport = onlineReportDAO.findForDatabaseIdAndName(0, reportName);
                        }
                    }
                    opcode = "loadsheet";
                }
                // highlighting etc. From the top right menu and the azquobook context menu, can be zapped later
                if ((opcode.equals("setchosen")) && choiceName != null) {
                    if (choiceName.startsWith("region options:")) {
                        String region = choiceName.substring(15);
                        System.out.println("saving choices: " + choiceName + " " + choiceValue);
                        if (choiceValue.equals("clear")) {
                            // todo, zap values from the new options record
                            UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), onlineReport.getId(), region);
                            userRegionOptionsDAO.removeById(userRegionOptions);
                        } else {
                            UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), onlineReport.getId(), region);
                            if (userRegionOptions == null) {
                                userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), onlineReport.getId(), region, loggedInUser.getAzquoBook().getSheetDefinedOptionsStringForRegion(region));
                            }
                            // set under the new method and store
                            userRegionOptions.setRowLimit(Integer.parseInt(request.getParameter("maxrows" + region)));
                            userRegionOptions.setColumnLimit(Integer.parseInt(request.getParameter("maxcols" + region)));
                            userRegionOptions.setHideRows(Integer.parseInt(request.getParameter("hiderows" + region)));
                            userRegionOptions.setSortable(request.getParameter("sortable" + region) != null && request.getParameter("sortable" + region).length() > 0);
                            userRegionOptionsDAO.store(userRegionOptions);
                        }
                    } else if (choiceName.startsWith("sort ")) { // the syntax passed is "sort " + region + " by column"; todo - fix this?? or wait for AB to be disabled
                        String region = choiceName.substring("sort ".length(), choiceName.length() - " by column".length());
                        // currently just support columns
                        UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), onlineReport.getId(), region);
                        if (userRegionOptions == null) {
                            userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), onlineReport.getId(), region, loggedInUser.getAzquoBook().getSheetDefinedOptionsStringForRegion(region));
                        }
                        boolean asc = true;
                        if (choiceValue.endsWith("-desc")) {
                            asc = false;
                            choiceValue = choiceValue.substring(0, choiceValue.length() - "-desc".length());
                        }
                        userRegionOptions.setSortColumn(choiceValue);
                        userRegionOptions.setSortColumnAsc(asc);
                        userRegionOptionsDAO.store(userRegionOptions);
                    } else {
                        // then we assume vanilla
                        spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceName, choiceValue);
                    }
                    opcode = "loadsheet";
                }
                if (opcode.equals("upload")) {
                    reportId = "";
                    if (submit.length() > 0) {
                        if (database.length() > 0) {
                            loginService.switchDatabase(loggedInUser, database);
                        }
                        String fileName = uploadfile.getOriginalFilename();
                        File moved = new File(spreadsheetService.getHomeDir() + "/temp/" + fileName);
                        uploadfile.transferTo(moved);
                        //importing here cannot set 'useType' to a value
                        importService.importTheFile(loggedInUser, fileName, "", moved.getAbsolutePath(), "", true, loggedInUser.getLanguages());
                        result = "File imported successfully";


                    } else {
                        model.addAttribute("azquodatabaselist", spreadsheetService.createDatabaseSelect(loggedInUser));
                        return "upload";
                    }
                }
                if (opcode.equals("provenance")) {
                    result = spreadsheetService.getProvenance(loggedInUser, row, Integer.parseInt(colStr), jsonFunction, 40);
                    model.addAttribute("content", result);
                    return "utf8javascript";
                }
                if (reportId != null && reportId.equals("1")) {
                    if (!loggedInUser.getUser().isAdministrator()) {
                        spreadsheetService.showUserMenu(model, loggedInUser);// user menu being what magento users typically see when logging in, a velocity page
                        return "azquoReports";
                    } else {
                        return "redirect:/api/ManageReports";
                    }

                }
                if ((opcode.length() == 0 || opcode.equals("loadsheet")) && onlineReport != null) {
                    // logic here is going to change to support the different renderers
                    loggedInUser.setReportId(onlineReport.getId());// that was below, whoops!
                    if (onlineReport.getDatabaseId() > 0) {
                        db = databaseDAO.findById(onlineReport.getDatabaseId());
                        loginService.switchDatabase(loggedInUser, db);
                        onlineReport.setPathname(loggedInUser.getDatabase().getMySQLName());
                    } else {
                        db = loggedInUser.getDatabase();
                        if (db == null && database != null && database.length() > 0) {
                            db = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
                            if (db != null) {
                                loggedInUser.setDatabaseWithServer(databaseServerDAO.findById(db.getDatabaseServerId()), db);
                            }
                        }
                        onlineReport.setPathname(onlineReport.getDatabaseType());
                    }
                    if (db != null) {
                        onlineReport.setDatabase(db.getName());
                    }
                    if (onlineReport.getRenderer() == OnlineReport.ZK_AZQUO_BOOK) {
                        HttpSession session = request.getSession();
                        if (session.getAttribute(reportId) != null) {
                            request.setAttribute(OnlineController.BOOK, session.getAttribute(reportId)); // push the rendered book into the request to be sent to the user
                            session.removeAttribute(reportId);// get rid of it from the session
                            return "zsshowsheet";// show the sheet
                        }
                        // ok now I need to set the sheet loading but on a new thread
                        // hmm, how to stop multiple loadings?
                        if (session.getAttribute(reportId + "loading") == null) { // don't wanna load it twice! THis could be hit if the user refreshes while generating the report.
                            // yes there's a chance a user could cause a double load if they were really quick but I'm not that bothered about this
                            session.setAttribute(reportId + "loading", Boolean.TRUE);
                            // this is a bit hacky, the new thread doesn't want them reassigned, fair enough
                            final String finalReportId = reportId;
                            final OnlineReport finalOnlineReport = onlineReport;
                            final LoggedInUser finalLoggedInUser = loggedInUser;
                            new Thread(() -> {
                                // so in here the new thread we set up the loading as it was originally before
                                try {
                                    String bookPath = spreadsheetService.getHomeDir() + ImportService.dbPath + finalOnlineReport.getPathname() + "/onlinereports/" + finalOnlineReport.getFilename();
                                    final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                                    // the first two make sense. Little funny about the second two but we need a reference to these
                                    book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                                    book.getInternalBook().setAttribute(LOGGED_IN_USER, finalLoggedInUser);
                                    // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                                    book.getInternalBook().setAttribute(REPORT_ID, finalLoggedInUser.getReportId());
                                    ZKAzquoBookUtils bookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO);
                                    model.put("showSave", bookUtils.populateBook(book));
                                    final List<SName> names = book.getInternalBook().getNames();
                                    List<String> pdfMerges = new ArrayList<>();
                                    for (SName name : names) {
                                        if (name.getName().startsWith("az_PDF")) {
                                            pdfMerges.add(name.getName().substring("az_PDF".length()).replace("_", " "));
                                        }
                                    }
                                    model.addAttribute("pdfMerges", pdfMerges);
                                    session.setAttribute(finalReportId, book);
                                } catch (Exception e) {
                                    model.addAttribute("content", "error:" + e.getMessage());// think that works!
                                }
                            }).start();
                            session.removeAttribute(reportId + "loading");
                        }
                        model.addAttribute("reportid", reportId); // why not? should block on refreshes then
                        return "zsloading";
                    }
                    // default to old one. THis does a fair bit of work adding info for velocity so I passed the model through. Perhaps not best practice.
                    spreadsheetService.readExcel(model, loggedInUser, onlineReport, spreadsheetName);
                    return "onlineReport";
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
            , @RequestParam(value = "editedname", required = false) String choiceName
            , @RequestParam(value = "editedvalue", required = false) String choiceValue
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "jsonfunction", required = false, defaultValue = "azquojsonfeed") String jsonFunction
            , @RequestParam(value = "row", required = false, defaultValue = "") String rowStr
            , @RequestParam(value = "col", required = false, defaultValue = "") String colStr
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "spreadsheetname", required = false, defaultValue = "") String spreadsheetName
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "reporttoload", required = false, defaultValue = "") String reportToLoad
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "provline", required = false, defaultValue = "") String provline
    ) {
        return handleRequest(model, request, user, password, choiceName, choiceValue, reportId, jsonFunction, rowStr, colStr, opcode, spreadsheetName, database, reportToLoad, submit, provline, null);
    }
}
