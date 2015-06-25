package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.view.AzquoBook;
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

import javax.servlet.http.HttpServletRequest;
import java.io.File;

/**
 * Created by bill on 22/04/14.
 *
 * Currently deals with a fair bit for AzquoBook, this may shrink in time.
 *
 */

@Controller
@RequestMapping("/Online")
public class OnlineController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private AdminService adminService;
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
            , @RequestParam(value = "chart", required = false) String chart
            , @RequestParam(value = "jsonfunction", required = false, defaultValue = "azquojsonfeed") String jsonFunction
            , @RequestParam(value = "row", required = false, defaultValue = "") String rowStr
            , @RequestParam(value = "col", required = false, defaultValue = "") String colStr
            , @RequestParam(value = "value", required = false) String changedValue
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "spreadsheetname", required = false, defaultValue = "") String spreadsheetName
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "reporttoload", required = false, defaultValue = "") String reportToLoad
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
            , @RequestParam(value = "uploadfile", required = false) MultipartFile uploadfile

    ) {
        try {
            String callerId = request.getRemoteAddr();
            if (callerId != null && user != null && user.equals("demo@user.com")) {
                user += callerId;
            }
            if (reportToLoad != null && reportToLoad.length() > 0) {
                reportId = reportToLoad;
            }
            //long startTime = System.currentTimeMillis();
            String workbookName = null;
            Database db;
            try {
                OnlineReport onlineReport = null;
                if (reportId != null && reportId.length() > 0) {
                    //report id is assumed to be integer - sent from the website
                    onlineReport = onlineReportDAO.findById(Integer.parseInt(reportId));
                    if (onlineReport != null) {
                        workbookName = onlineReport.getReportName();
                    }
                }
                if (workbookName == null) {
                    workbookName = "unknown";
                }
                LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

                if (loggedInUser == null) {
                    if (user == null) {
                        return "utf8page";
                    }
                    if (user.equals("demo@user.com")) {
                        user += request.getRemoteAddr();
                    }
                    loggedInUser = loginService.loginLoggedInUser(database, user, password, workbookName, false);
                    if (loggedInUser == null) {
                        model.addAttribute("content", "error:no connection id");
                        return "utf8page";
                    } else {
                        request.getSession().setAttribute(LoginController.LOGGED_IN_USER_SESSION, loggedInUser);
                    }
                }
                if (onlineReport != null) {
                    if (onlineReport.getId() != 1) {
                        if (onlineReport.getDatabaseId() > 0) {
                            db = databaseDAO.findById(onlineReport.getDatabaseId());
                            loginService.switchDatabase(loggedInUser, db);
                            onlineReport.setPathname(loggedInUser.getDatabase().getMySQLName());
                        } else {
                            db = loggedInUser.getDatabase();
                            onlineReport.setPathname(adminService.getBusinessPrefix(loggedInUser));

                        }
                        if (db != null) {
                            onlineReport.setDatabase(db.getName());
                            database = onlineReport.getDatabase();
                        }
                    }
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
                if (opcode.equals("choosefromlist")){
                    result =  spreadsheetService.getJsonList(loggedInUser.getDataAccessToken(),choiceName, loggedInUser.getAzquoBook().getRangeData(choiceName + "choice"), choiceValue, jsonFunction);
                }
                if ((opcode.equals("setchosen")) && choiceName != null) {
                    if (choiceName.startsWith("region options:")) {
                        String region = choiceName.substring(15);
                        System.out.println("saving choices: " + choiceName + " " + choiceValue);
                        if (choiceValue.equals("clear")) {
                            spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "clear overrides", "");
                        } else {
                            spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "maxrows" + region, request.getParameter("maxrows" + region));
                            spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "maxcols" + region, request.getParameter("maxcols" + region));
                            spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "hiderows" + region, request.getParameter("hiderows" + region));
                            spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "sortable" + region, request.getParameter("sortable" + region));
                        }
                    } else {
                        spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), onlineReport.getId(), choiceName, choiceValue);
                    }
                    opcode = "loadsheet";
                }
                if (opcode.equals("upload")) {
                    if (submit.length() > 0) {
                        if (database.length() > 0) {
                            loginService.switchDatabase(loggedInUser, database);
                        }
                        String fileName = uploadfile.getOriginalFilename();
                        File moved = new File(spreadsheetService.getHomeDir() + "/temp/" + fileName);
                        uploadfile.transferTo(moved);

                        importService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), "", true, loggedInUser.getLanguages());
                        result = "File imported successfully";

                    } else {
                        model.addAttribute("azquodatabaselist", spreadsheetService.createDatabaseSelect(loggedInUser));
                        return "upload";
                    }
                }
                if (opcode.equals("valuesent")) {
                    result = spreadsheetService.changeValue(loggedInUser, row, Integer.parseInt(colStr), changedValue);
                    result = jsonFunction + "({\"changedvalues\":" + result + "})";
                }
                if (opcode.equals("provenance")) {
                    result = spreadsheetService.getProvenance(loggedInUser, row, Integer.parseInt(colStr), jsonFunction);
                    model.addAttribute("content", result);

                    return "utf8javascript";
                }
                if (opcode.equals("provenance")) {
                    result = spreadsheetService.getProvenance(loggedInUser, row, Integer.parseInt(colStr), jsonFunction);
                    model.addAttribute("content", result);
                    return "utf8";
                }
                // will only work on admin
                if (opcode.equals("savedata")) {
                    AzquoBook azquoBook = loggedInUser.getAzquoBook();
                    azquoBook.saveData(loggedInUser);
                    result = "data saved successfully";
                }
                if (opcode.equals("chart")) {
                    result = jsonFunction + "({\"chart\":\"" + chart + "\"})";
                }

                if ((opcode.length() == 0 || opcode.equals("loadsheet")) && onlineReport != null) {
                    // logic here is going to change to support the different renderers
                    if (onlineReport.getId() != 1) {// todo, make this makem a bit more sense, get rid of report id 1 being a special case
                        loggedInUser.setReportId(onlineReport.getId());// that was below, whoops!
                        if (onlineReport.getRenderer() == OnlineReport.ZK_AZQUO_BOOK){
                            long time = System.currentTimeMillis();
                            String bookPath = spreadsheetService.getHomeDir() + ImportService.dbPath + onlineReport.getPathname() + "/onlinereports/" + onlineReport.getFilename();
                            final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                            // the first two make sense. Little funny about the second two but we need a reference to these
                            book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                            book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
                            // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                            book.getInternalBook().setAttribute(REPORT_ID, loggedInUser.getReportId());
                            ZKAzquoBookUtils bookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO);
                            bookUtils.populateBook(book);
                            request.setAttribute(BOOK, book);
                            if (loggedInUser.getDatabase() != null) {
                                model.addAttribute("databaseChosen", loggedInUser.getDatabase().getName());
                            }
                            System.out.println("time to prepare the book : " + (System.currentTimeMillis() - time));
                            return "zstest";
                        }
                        // default to old one. THis does a fair bit of work adding info for velocity so I passed the model through. Perhaps not best practice.
                        spreadsheetService.readExcel(model, loggedInUser, onlineReport, spreadsheetName);
                        return "onlineReport";
                        // was provenance setting here,
                    } else {
                        if (!loggedInUser.getUser().isAdministrator()) {
                            spreadsheetService.showUserMenu(model, loggedInUser);// user menu being what magento users typically see when logging in, a velocity page
                            return "azquoReports";
                        } else {
                            return "redirect:/api/ManageReports";
                        }
                    }
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
            , @RequestParam(value = "chart", required = false) String chart
            , @RequestParam(value = "jsonfunction", required = false, defaultValue = "azquojsonfeed") String jsonFunction
            , @RequestParam(value = "row", required = false, defaultValue = "") String rowStr
            , @RequestParam(value = "col", required = false, defaultValue = "") String colStr
            , @RequestParam(value = "value", required = false) String changedValue
            , @RequestParam(value = "opcode", required = false, defaultValue = "") String opcode
            , @RequestParam(value = "spreadsheetname", required = false, defaultValue = "") String spreadsheetName
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "reporttoload", required = false, defaultValue = "") String reportToLoad
            , @RequestParam(value = "submit", required = false, defaultValue = "") String submit
    ) {
        return handleRequest(model, request, user, password, choiceName, choiceValue, reportId, chart, jsonFunction, rowStr, colStr, changedValue, opcode, spreadsheetName, database, reportToLoad, submit, null);
    }
}
