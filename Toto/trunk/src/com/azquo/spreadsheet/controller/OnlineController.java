package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
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
import java.io.InputStream;
import java.util.*;


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
    private NameService nameService;
    @Autowired
    private ValueService valueService;
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
    public static final String LOGGED_IN_CONNECTION = "LOGGED_IN_CONNECTION";
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
            // test code, the useful objects will have been set up below
            if (request.getParameter("trynewsheet") != null && request.getParameter("trynewsheet").length() > 0) {
                // ok new plan, make the book with all it needs here and set against the request for the provider to return
                long time = System.currentTimeMillis();
                String bookPath = (String) request.getSession().getAttribute(BOOK_PATH);
                final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                final LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LOGGED_IN_CONNECTION);
                // the first two make sense. Little funny about teh second two but we need a reference to these
                book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
                book.getInternalBook().setAttribute(LOGGED_IN_CONNECTION, loggedInConnection);
                // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
                book.getInternalBook().setAttribute(REPORT_ID, loggedInConnection.getReportId());
                ZKAzquoBookUtils bookUtils = new ZKAzquoBookUtils(valueService, spreadsheetService, nameService, userChoiceDAO);
                bookUtils.populateBook(book);
                request.setAttribute(BOOK, book);
                if (loggedInConnection.getCurrentDBName() != null) {
                    model.addAttribute("databaseChosen", loggedInConnection.getCurrentDBName());
                }
                System.out.println("time to prepare the book : " + (System.currentTimeMillis() - time));
                return "zstest";
            }

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
                LoggedInConnection loggedInConnection = (LoggedInConnection) request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);

                if (loggedInConnection == null) {
                    if (user == null) {
                        return "utf8page";
                    }
                    if (user.equals("demo@user.com")) {
                        user += request.getRemoteAddr();
                    }
                    loggedInConnection = loginService.login(database, user, password, 0, workbookName, false);
                    if (loggedInConnection == null) {
                        model.addAttribute("content", "error:no connection id");
                        return "utf8page";
                    }
                }
                if (onlineReport != null) {
                    if (onlineReport.getId() != 1) {
                        if (onlineReport.getDatabaseId() > 0) {
                            db = databaseDAO.findById(onlineReport.getDatabaseId());
                            loginService.switchDatabase(loggedInConnection, db);
                            onlineReport.setPathname(loggedInConnection.getCurrentDBName());
                        } else {
                            db = loggedInConnection.getCurrentDatabase();
                            onlineReport.setPathname(adminService.getBusinessPrefix(loggedInConnection));

                        }
                        if (db != null) {
                            onlineReport.setDatabase(db.getName());
                            database = onlineReport.getDatabase();
                        }
                    }
                }
                if (onlineReport != null && onlineReport.getId() > 1 && loggedInConnection.hasAzquoMemoryDB()) {
                    loggedInConnection.setNewProvenance("spreadsheet", onlineReport.getReportName(), "");

                }
             /*
            THIS GIVES A NULL POINTER EXCEPTION - NOT SURE WHY I PUT IT IN!
            if (loggedInConnection.getProvenance()==null){
                //will not have been set for online reports
                loggedInConnection.getProvenance().setMethod("Azquosheet");
                if (workbookName.length() > 0){
                    loggedInConnection.getProvenance().setName(workbookName);
                }
            }
            */
                String result = "error: no action taken";
                int row = 0;
                try {
                    row = Integer.parseInt(rowStr);
                } catch (Exception e) {
                    //rowStr can be blank or '0'
                }
            /* expand the row and column headings.
            the result is jammed into result but may not be needed - getrowheadings is still important as it sets up the bits in the logged in connection

            ok, one could send the row and column headings at the same time as the data but looking at the export demo it's asking for rows headings then column headings then the context

             */
                //String sortRegion = "";
                if (opcode.equals("choosefromlist")){
                    result =  spreadsheetService.getJsonList(loggedInConnection,choiceName,choiceValue, jsonFunction);

                }
                if ((opcode.equals("setchosen")) && choiceName != null) {
                    if (choiceName.startsWith("region options:")) {
                        String region = choiceName.substring(15);
                        System.out.println("saving choices: " + choiceName + " " + choiceValue);
                        if (choiceValue.equals("clear")) {
                            spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "clear overrides", "");
                        } else {
                            spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "maxrows" + region, request.getParameter("maxrows" + region));
                            spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "maxcols" + region, request.getParameter("maxcols" + region));
                            spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "hiderows" + region, request.getParameter("hiderows" + region));
                            spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "sortable" + region, request.getParameter("sortable" + region));
                        }
                    } else {
                        spreadsheetService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), choiceName, choiceValue);
                    }
                    opcode = "loadsheet";
                }
                if (opcode.equals("upload")) {
                    if (submit.length() > 0) {
                        if (database.length() > 0) {
                            spreadsheetService.switchDatabase(loggedInConnection, database);
                        }
                        InputStream uploadFile = uploadfile.getInputStream();
                        String fileName = uploadfile.getOriginalFilename();
                        importService.importTheFile(loggedInConnection, fileName, uploadFile, "", true, loggedInConnection.getLanguages());
                        result = "File imported successfully";

                    } else {
                        result = spreadsheetService.showUploadFile(loggedInConnection);
                    }

                }
                if (opcode.equals("valuesent")) {
                    result = spreadsheetService.changeValue(loggedInConnection, row, Integer.parseInt(colStr), changedValue);
                    result = jsonFunction + "({\"changedvalues\":" + result + "})";
                }
                if (opcode.equals("nameidchosen")) {
                    try {
                        List<Set<Name>> names = new ArrayList<Set<Name>>();
                        //this routine should accept much more than a single name....
                        try {
                            Name name = nameService.findById(loggedInConnection, Integer.parseInt(choiceName));
                            Set<Name> names1 = new HashSet<Name>();
                            names1.add(name);
                            names.add(0, names1);
                            loggedInConnection.setNamesToSearch(names);
                        } catch (Exception e) {
                            //ignore - this is an internal parameter
                        }
                        opcode = "loadsheet";
                    } catch (Exception ignored) {
                    }
                }

                if (opcode.equals("provenance")) {
                    result = spreadsheetService.getProvenance(loggedInConnection, row, Integer.parseInt(colStr), jsonFunction);
                    model.addAttribute("content", result);

                    return "utf8javascript";
                }
                // will only work on admin
                if (opcode.equals("savedata")) {
                    spreadsheetService.saveData(loggedInConnection);
                    result = "data saved successfully";
                }
                //if (opcode.equals("children")){

                //result = nameService.getStructureForNameSearch(loggedInConnection,"", Integer.parseInt(nameId), loggedInConnection.getLanguages());
                // result = jsonFunction + "(" + result + ")";
                // }
                if (opcode.equals("chart")) {
                /*if (chartParams.length() > 6){ //params start with 'chart '
                    chartParams = chartParams.substring(6);
                }else{
                    chartParams="";
                }*/
                    //chart =  onlineService.getChart(loggedInConnection, chartParams);
                    result = jsonFunction + "({\"chart\":\"" + chart + "\"})";
                }

                if ((opcode.length() == 0 || opcode.equals("loadsheet")) && onlineReport != null) {
                    if (onlineReport.getId() != 1) {
                        request.getSession().setAttribute(BOOK_PATH, spreadsheetService.getHomeDir() + ImportService.dbPath + onlineReport.getPathname() + "/onlinereports/" + onlineReport.getFilename());
                        if (spreadsheetName.length() > 0) {
                            loggedInConnection.setNewProvenance("spreadsheet", spreadsheetName, "");
                        }

                    } else {
                        request.getSession().setAttribute(BOOK_PATH, onlineReport.getFilename());
                        if (!loggedInConnection.getUser().isAdministrator()) {
                            // I relaise making a velocity and passing it to jsp is a bit crap, I just want it to work
                            model.put("content",spreadsheetService.showUserMenu(loggedInConnection) );// user menu being what magento users typically see when logging in, a velocity page
                            return "utf8page";
                        } else {
                            return "redirect:/api/ManageReports";
                        }

                    }
                    loggedInConnection.setReportId(onlineReport.getId());
                    // jam 'em in the session for the moment, makes testing easier. As in see a report then try with &trynewsheet=true after
                    request.getSession().setAttribute(LOGGED_IN_CONNECTION, loggedInConnection);
                    result = spreadsheetService.readExcel(loggedInConnection, onlineReport, spreadsheetName, "Right-click mouse for provenance");
                }
            /*
            BufferedReader br = new BufferedReader(new StringReader(result));
           / String line;
            logger.error("----- sent result");
            while ((line = br.readLine()) != null) {
                logger.info(line);
            }*/
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
