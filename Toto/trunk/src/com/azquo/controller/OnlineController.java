package com.azquo.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.memorydb.Name;
import com.azquo.service.*;
//import com.azquo.util.Chart;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
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

    // TODO : break up into separate functions

    private static final Logger logger = Logger.getLogger(OnlineController.class);

    @RequestMapping
    public String handleRequest (ModelMap model,HttpServletRequest request)throws Exception{

    /*
    public String handleRequest(@RequestParam(value = "connectionid", required = false) String connectionId
            , @RequestParam(value = "user", required = false)  String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "choicename", required = false) String choiceName
            , @RequestParam(value = "choicevalue", required = false) String choiceValue
            , @RequestParam(value = "reportid", required = false) String reportid) throws Exception {
*/
         Enumeration<String> parameterNames = request.getParameterNames();

        String user = null;
        String password = null;
        String connectionId = null;
        String choiceName = null;
        String choiceValue = null;
        String reportId = null;
        //String chartParams = null;
        String chart = null;
        String jsonFunction = "azquojsonfeed";
        //String region = null;
        String rowStr = "";
        String colStr = "";
        String changedValue = null;
        String opcode = "";
        String nameId = null;
        String spreadsheetName = "";
        String database = "";
        String reportToLoad = "";

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("user")) {
                user = paramValue;
            } else if (paramName.equals("password")) {
                password = paramValue;
            } else if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            } else if (paramName.equals("editedname")) {
                choiceName = paramValue;
            } else if (paramName.equals("editedvalue")) {
                choiceValue = paramValue;
            } else if (paramName.equals("reportid")) {
                reportId = paramValue;
            } else if (paramName.equals("reporttoload")) {
                reportToLoad = paramValue;
            } else if (paramName.equals("jsonfunction")) {
                jsonFunction = paramValue;
            //} else if (paramName.equals("chart")) {
              //  chartParams = paramValue;
            } else if (paramName.equals("opcode")) {
                opcode = paramValue;
            //} else if (paramName.equals("region")) {
              //  region = paramValue;
            } else if (paramName.equals("row")) {
                rowStr = paramValue;
            } else if (paramName.equals("col")) {
                colStr = paramValue;
            } else if (paramName.equals("value")) {
                changedValue = paramValue;
            }else if (paramName.equals("spreadsheetname")){
                spreadsheetName = paramValue;
            }else if (paramName.equals("nameid")){
                nameId = paramValue;
            }else if (paramName.equals("database")){
                database = paramValue;
            }

            String callerId = request.getRemoteAddr();
            if (callerId != null && user != null && user.equals("demo@user.com")) {
                user += callerId;
            }
        }
        FileItem item;
        FileItem file = null;
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if (isMultipart){
            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();

// Set factory constraints
            factory.setSizeThreshold(10000000);
            ServletContext servletContext = request.getServletContext();
            File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
            factory.setRepository(repository);

// Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload(factory);

// Set overall request size constraint
            upload.setSizeMax(100000000);

// Parse the request
            List<FileItem> items = upload.parseRequest(request);
            Iterator it = items.iterator();
            item = (FileItem) it.next();
             boolean macMode = false;
            if (!item.getFieldName().equals("parameters")) { // no parameters file passed, used to be a plain error but mac may have other ideas - parameters sent via curl
                while (item != null){
                    if (item.getName() !=null){
                        file = item;
                    }else {
                        if (item.getFieldName().equals("connectionid")) {
                            macMode = true;
                            connectionId = item.getString();
                        } else if (item.getFieldName().equals("spreadsheetname")) {
                            spreadsheetName = item.getString();
                        }else if (item.getFieldName().equals("reportid")){
                            reportId = item.getString();
                        }else if (item.getFieldName().equals("row")){
                            rowStr = item.getString();
                        }else if (item.getFieldName().equals("col")){
                            colStr = item.getString();
                        }else if (item.getFieldName().equals("database")){
                            database = item.getString();
                        }else if (item.getFieldName().equals("opcode")){
                            opcode = item.getString();
                        }
                    }
                    if (it.hasNext()) {
                        item = (FileItem) it.next();
                    }else{
                        item = null;
                    }
                }

                if (!macMode){ // either mac or windows not sending what we want
                    model.addAttribute("content", "error: expecting parameters");
                    return "utf8page";
                }
            } else { // parameters file built on windows
                String parameters = item.getString();
                StringTokenizer st = new StringTokenizer(parameters, "&");
                while (st.hasMoreTokens()) {
                    file = item;
                    String parameter = st.nextToken();
                    if (!parameter.endsWith("=")){
                        StringTokenizer st2 = new StringTokenizer(parameter, "=");
                        String parameterName = st2.nextToken();
                        if (parameterName.equals("connectionid")) {
                            connectionId = st2.nextToken();
                        }else if (parameterName.equals("spreadsheetname")) {
                            spreadsheetName = st2.nextToken();
                        }else if (parameterName.equals("reportid")){
                            reportId = st2.nextToken();
                        }else if (parameterName.equals("row")){
                            rowStr = st2.nextToken();
                        }else if (parameterName.equals("col")){
                            colStr = st2.nextToken();
                        }else if (parameterName.equals("database")){
                            database = st2.nextToken();
                        }else if (parameterName.equals("opcode")){
                            opcode = st2.nextToken();
                        }
                    }
                }
                // don't get this, edd commenting
                /*if (it.hasNext()){
                    item = (FileItem) it.next();
                }else{
                    item = null;

                } */
            }

        }
        //assuming that the last item is the file;
        item = file;
        if (reportToLoad.length() > 0){
            reportId = reportToLoad;
        }

        //long startTime = System.currentTimeMillis();
        String workbookName = null;
        Database db = null;
        try {
            OnlineReport onlineReport = null;
            if (reportId != null && reportId.length() > 0){
                //report id is assumed to be integer - sent from the website
                onlineReport = onlineReportDAO.findById(Integer.parseInt(reportId));
                if (onlineReport != null){
                    workbookName = onlineReport.getReportName();
                    if (onlineReport.getId()!=1) {
                        db = databaseDAO.findById(onlineReport.getDatabaseId());
                        onlineReport.setDatabase(db.getName());
                        database = onlineReport.getDatabase();
                    }
                }
            }
            if (workbookName == null){
                workbookName = "unknown";
            }
            if (connectionId == null) {
                if (user.equals("demo@user.com")){
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
            if (onlineReport != null && onlineReport.getId() > 1 && loggedInConnection.hasAzquoMemoryDB()){
                loggedInConnection.setNewProvenance("spreadsheet", onlineReport.getReportName(),"","","");

            }
            if (db!=null){
                loginService.switchDatabase(loggedInConnection,db);
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

            }catch(Exception e){
                //rowStr can be blank or '0'
            }
            /* expand the row and column headings.
            the result is jammed into result but may not be needed - getrowheadings is still important as it sets up the bits in the logged in connection

            ok, one could send the row and column headings at the same time as the data but looking at the export demo it's asking for rows headings then column headings then the context

             */
            //String sortRegion = "";
            if ((opcode.equals("sortcol") || opcode.equals("highlight") || opcode.equals("selectchosen")) && choiceName != null){
                onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), choiceName, choiceValue);

                opcode="loadsheet";
            }
            if (opcode.equals("valuesent")){
                result = onlineService.changeValue(loggedInConnection, row, Integer.parseInt(colStr), changedValue);
                result = jsonFunction + "({\"changedvalues\":" + result + "})";
            }
            if (opcode.equals("nameidchosen")){
                try {
                    List<Set<Name>> names = new ArrayList<Set<Name>>();
                    //this routine should accept much more than a single name....
                    try{
                        Name name =nameService.findById(loggedInConnection,Integer.parseInt(choiceName));
                        Set<Name> names1 = new HashSet<Name>();
                        names1.add(name);
                        names.add(0, names1);
                        loggedInConnection.setNamesToSearch(names);
                    }catch(Exception e){
                        //ignore - this is an internal parameter
                    }
                    opcode="loadsheet";


                }catch(Exception ignored){
                }

            }

            if (opcode.equals("provenance")){
                result = onlineService.getProvenance(loggedInConnection, row, Integer.parseInt(colStr), jsonFunction);
            }
            if (opcode.equals("savedata")){
                  result = onlineService.saveData(loggedInConnection, jsonFunction);
             }
            if (opcode.equals("details")){

                result = nameService.jsonNameDetails(loggedInConnection, Integer.parseInt(nameId));
                result = jsonFunction + "({\"namedetails\":" + result + "})";
            }
            if (opcode.equals("children")){

                result = nameService.getStructureForNameSearch(loggedInConnection,"", Integer.parseInt(nameId), loggedInConnection.getLanguages());
                result = jsonFunction + "(" + result + ")";
            }



            if (opcode.equals("chart")){
                /*if (chartParams.length() > 6){ //params start with 'chart '
                    chartParams = chartParams.substring(6);
                }else{
                    chartParams="";
                }*/
                //chart =  onlineService.getChart(loggedInConnection, chartParams);
                result = jsonFunction + "({\"chart\":\"" + chart + "\"})";

            }

            if ((opcode.length()==0 || opcode.equals("loadsheet")) && onlineReport != null) {
                if (onlineReport.getId()!=1 && spreadsheetName.length() > 0){
                     loggedInConnection.setNewProvenance("spreadsheet", spreadsheetName,"","","");
                }
                loggedInConnection.setReportId(onlineReport.getId());
                result = onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName,"Right-click mouse for provenance");
            }
            if (opcode.equals("buttonpressed") && row > 0){//button pressed - follow instructions and reload admin sheet
                String message = onlineService.followInstructionsAt(loggedInConnection, jsonFunction, row, Integer.parseInt(colStr), database, item);

                if (onlineReport == null){
                    onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                }
                result =  onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName, message);
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


}
