package com.azquo.controller;

import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.jsonrequestentities.LoginJsonRequest;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 * For logging in, should verify against a DB then return a connection id which expires if the credentials check out.
 */
@Controller
@RequestMapping("/Login")

public class LoginController {

    private static final Logger logger = Logger.getLogger(LoginController.class);
    private static final ObjectMapper jacksonMapper = new ObjectMapper();
    @Autowired
    private LoginService loginService;

    @Autowired
    private OnlineService onlineService;

    @Autowired
    private OnlineReportDAO onlineReportDAO;

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request)throws Exception{

        boolean isMultipart = ServletFileUpload.isMultipartContent(request);

        Enumeration<String> parameterNames = request.getParameterNames();

        String userEmail=null;
        String password=null;
        String spreadsheetName=null;
        String timeout=null;
        String connectionId=null;
        String json=null;
        String database=null;
        String checkConnectionId = null;
        String result = null;
        String rowChosen = "";
        String colChosen = "";
        String jsonFunction = "azquojsonfeed";
        boolean online = false;
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("json")) {
                json = paramValue;
            }else if(paramName.equals("useremail")){
                userEmail = paramValue;
            }else if(paramName.equals("password")){
                password=paramValue;
            }else if(paramName.equals("spreadsheetname")){
                spreadsheetName = paramValue;
            }else if(paramName.equals("timeout")){
                timeout = paramValue;
            }else if (paramName.equals("connectionid")){
                connectionId = paramValue;
            }else if (paramName.equals("database")){
                database = paramValue;
            }else if (paramName.equals("checkconnectionid")){
                checkConnectionId = paramValue;
            }else if (paramName.equals("online")){
                online = true;
            }else if (paramName.equals("rowchosen")){
                rowChosen = paramValue;
            }else if (paramName.equals("colchosen")){
                colChosen = paramValue;
            }else if (paramName.equals("jsonfunction")){
                jsonFunction = paramValue;
            }

        }
        FileItem item = null;
        FileItem file = null;
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
                        } else if (item.getFieldName().equals("rowchosen")) {
                            rowChosen = item.getString();
                        } else if (item.getFieldName().equals("colchosen")) {
                            colChosen = item.getString();
                        } else if (item.getFieldName().equals("database")) {
                            database = item.getString();
                        }
                    }
                    if (it.hasNext()) {
                        item = (FileItem) it.next();
                    }else{
                        item = null;
                    }
                }

                if (!macMode){ // either mac or windows not sending what we want
                    return "error: expecting parameters";
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
                        }
                        if (parameterName.equals("spreadsheetname")) {
                            spreadsheetName = st2.nextToken();
                        }
                        if (parameterName.equals("rowchosen")) {
                            rowChosen = st2.nextToken();
                        }
                        if (parameterName.equals("colchosen")) {
                            colChosen = st2.nextToken();
                        }
                        if (parameterName.equals("database")){
                            database = st2.nextToken();
                        }
                    }
                }
                if (it.hasNext()){
                    item = (FileItem) it.next();
                }else{
                    item = null;

                }
            }

        }
        //assuming that the last item is the file;
        item = file;
        String callerId = request.getRemoteAddr();
        if (callerId != null && userEmail != null && userEmail.equals("demo@user.com")){
            userEmail += callerId;
        }
        if (json!=null && json.length() > 0){
            // for Google sheets, better to send all parameters as JSON
            LoginJsonRequest loginJsonRequest;
            try {
                loginJsonRequest = jacksonMapper.readValue(json, LoginJsonRequest.class);
            } catch (Exception e) {
                logger.error("name json parse problem", e);
                return "error:badly formed json " + e.getMessage();
            }

            if (loginJsonRequest.database != null) database = loginJsonRequest.database;
            if (loginJsonRequest.user!= null) userEmail = loginJsonRequest.user;
            if (loginJsonRequest.password != null) password = loginJsonRequest.password;
            if (loginJsonRequest.spreadsheetname != null) spreadsheetName = loginJsonRequest.spreadsheetname;
            if (loginJsonRequest.timeout != null) timeout = loginJsonRequest.timeout;
            if (loginJsonRequest.connectionid !=null) connectionId = loginJsonRequest.connectionid;


        }

        if (!online && connectionId != null && connectionId.length() > 0 && loginService.getConnection(connectionId) != null) {
                return connectionId;
        }
        LoggedInConnection loggedInConnection = null;
        if ((connectionId == null || connectionId.equals("aborted")) && userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
            logger.info("spreadsheet name " + spreadsheetName);
            if (database == null || database.length() == 0) {
                database = "unknown";
            }
            int minutesTimeout = 0;
            if (timeout != null && timeout.length() > 0) {
                try {
                    minutesTimeout = Integer.parseInt(timeout);
                } catch (Exception ignored) {
                    return "error:timeout is not an integer";
                }
            }

           loggedInConnection = loginService.login(database, userEmail, password, minutesTimeout, spreadsheetName, false);
        }else {
            loggedInConnection = loginService.getConnection(connectionId);
        }
         if (loggedInConnection != null) {
                result =  loggedInConnection.getConnectionId();
                if (online){
                    String message = "";
                    if (rowChosen.length() > 0){
                        message = onlineService.followInstructionsAt(loggedInConnection, jsonFunction, Integer.parseInt(rowChosen), Integer.parseInt(colChosen), database, item);
                    }
                        OnlineReport onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                        return onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName, message);
                  }
                return result;
            }
        if (checkConnectionId != null && checkConnectionId.length() > 0) {
            if (loginService.getConnection(checkConnectionId) != null) {
                result = "ok";
            } else {
                result =  "error:expired or incorrect connection id";
            }
            return result;
        }
        return "error:incorrect login details";
    }
}