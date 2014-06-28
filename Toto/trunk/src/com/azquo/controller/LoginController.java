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
        boolean online = false;
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("json")) {
                json = paramValue;
            }else if(paramName.equals("user")){
                userEmail = paramValue;
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
            }

        }
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
        if ((connectionId == null || connectionId.length()==0 || connectionId.equals("aborted")) && userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
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
                    OnlineReport onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                      return onlineService.readExcel(loggedInConnection, onlineReport, spreadsheetName, "");
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