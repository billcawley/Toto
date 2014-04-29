package com.azquo.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;


/**
 * Created by bill on 22/04/14.
 */

@Controller
@RequestMapping("/Online")
public class OnlineController {

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ValueService valueService;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private ImportService importService;
    @Autowired
    private OnlineService onlineService;

    // TODO : break up into separate functions

    private static final Logger logger = Logger.getLogger(OnlineController.class);
    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @RequestMapping
    public String handleRequest (ModelMap model,HttpServletRequest request){

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
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("user")){
                     user= paramValue;
            }else if (paramName.equals("password")){
                password = paramValue;
            }else if (paramName.equals("connectionid")){
                connectionId = paramValue;
            }else if (paramName.equals("editedname")){
                choiceName = paramValue;
            }else if (paramName.equals("editedvalue")){
                choiceValue = paramValue;
            }else if (paramName.equals("reportid")){
                reportId = paramValue;
            }
        }


        long startTime = System.currentTimeMillis();
        String spreadsheetName = null;
        String database = null;
        try {
            OnlineReport onlineReport = null;
            if (reportId != null && reportId.length() > 0){
                //report id is assumed to be integer - sent from the website
                onlineReport = onlineReportDAO.findById(Integer.parseInt(reportId));
                if (onlineReport != null){
                    spreadsheetName = onlineReport.getReportName();
                    onlineReport.setDatabase(databaseDAO.findById(onlineReport.getDatabaseId()).getName());
                    database = onlineReport.getDatabase();
                }
            }
            if (spreadsheetName == null){
                spreadsheetName = "unknown";
            }
            if (connectionId == null) {
                LoggedInConnection loggedInConnection = loginService.login(database, user, password, 0, spreadsheetName, false);

                if (loggedInConnection == null) {
                    return "error:no connection id";
                }
                connectionId = loggedInConnection.getConnectionId();

            }
            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }
            if (loggedInConnection.getProvenance()!=null){
                //will not have been set for online reports
                loggedInConnection.setNewProvenance("online report",spreadsheetName);

            }
            String result = "error: no action taken";

            /* expand the row and column headings.
            the result is jammed into result but may not be needed - getrowheadings is still important as it sets up the bits in the logged in connection

            ok, one could send the row and column headings at the same time as the data but looking at the export demo it's asking for rows headings then column headings then the context

             */
             if (choiceName != null){
                 onlineService.setUserChoice(loggedInConnection.getUser().getId(), onlineReport.getId(), choiceName, choiceValue);
             }

             if (onlineReport != null){
                result = onlineService.readExcel(loggedInConnection,onlineReport);


            }
            /*
            BufferedReader br = new BufferedReader(new StringReader(result));
            String line;
            logger.error("----- sent result");
            while ((line = br.readLine()) != null) {
                logger.info(line);
            }*/
            model.addAttribute("content", result);
        } catch (Exception e) {
            logger.error("value controller error", e);
            model.addAttribute("content", "error:" + e.getMessage());
        }
        return "utf8page";

    }


}
