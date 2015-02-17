package com.azquo.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.memorydb.Name;
import com.azquo.service.*;
import com.azquo.view.AzquoBook;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.*;


/**
 * Created by bill on 22/04/14.
 *
 */

@Controller
@RequestMapping("/Online")
public class OnlineController {

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private OnlineService onlineService;
    @Autowired
    private AdminService adminService;

    // TODO : break up into separate functions

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "connectionid", required = false) String connectionId
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
            , @RequestParam(value = "uploadfile", required = false) MultipartFile uploadfile

    ) throws Exception {
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
            if (connectionId == null) {
                if (user == null) {
                    return "utf8page";
                }
                if (user.equals("demo@user.com")) {
                    user += request.getRemoteAddr();
                }
                LoggedInConnection loggedInConnection = loginService.login(database, user, password, 0, workbookName, false);
                if (loggedInConnection == null) {
                    model.addAttribute("content", "error:no connection id");
                    return "utf8page";
                }
                connectionId = loggedInConnection.getConnectionId();

            }
            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                model.addAttribute("content", "error:invalid or expired connection id");
                return "utf8page";
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
            if ((opcode.equals("setchosen")) && choiceName != null) {
                if (choiceName.startsWith("region options:")) {
                    String region = choiceName.substring(15);
                    System.out.println("saving choices: " + choiceName + " " + choiceValue);
                    if (choiceValue.equals("clear")) {
                        onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "clear overrides", "");
                    } else {
                        onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "maxrows" + region, request.getParameter("maxrows" + region));
                        onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "maxcols" + region, request.getParameter("maxcols" + region));
                        onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "hiderows" + region, request.getParameter("hiderows" + region));
                        onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), AzquoBook.OPTIONPREFIX + "sortable" + region, request.getParameter("sortable" + region));
                    }
                } else {
                    onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), choiceName, choiceValue);
                }

                opcode = "loadsheet";
            }
            if (opcode.equals("valuesent")) {
                result = onlineService.changeValue(loggedInConnection, row, Integer.parseInt(colStr), changedValue);
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
                result = onlineService.getProvenance(loggedInConnection, row, Integer.parseInt(colStr), jsonFunction);
            }
            if (opcode.equals("savedata")) {
                onlineService.saveData(loggedInConnection, jsonFunction);
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
                if (onlineReport.getId() != 1 && spreadsheetName.length() > 0) {
                    loggedInConnection.setNewProvenance("spreadsheet", spreadsheetName, "");
                }
                loggedInConnection.setReportId(onlineReport.getId());
                result = onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName, "Right-click mouse for provenance");
            }
            if (opcode.equals("buttonpressed") && row > 0) {//button pressed - follow instructions and reload admin sheet
                // json function was being passed but ignored!
                onlineService.followInstructionsAt(loggedInConnection, row, Integer.parseInt(colStr), database, uploadfile);

                if (onlineReport == null) {
                    onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                }
                result = onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName, result);
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

    }
    // when not multipart
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "connectionid", required = false) String connectionId
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
            ) throws Exception{
        return handleRequest(model,request,user,password,connectionId,choiceName,choiceValue,reportId,chart,jsonFunction,rowStr,colStr, changedValue, opcode, spreadsheetName, database, reportToLoad, null);
    }


}
