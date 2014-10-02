package com.azquo.app.yousay.controller;

/**
 * Created by bill on 09/09/14.
 */
import com.azquo.admindao.DatabaseDAO;
import com.azquo.app.yousay.service.ReviewService;
import com.azquo.memorydb.MemoryDBManager;
import com.azquo.memorydb.Name;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.NameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.StringWriter;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;


import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 */

@Controller
@RequestMapping("/Reviews")


public class ReviewController {

    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private LoginService loginService;

    @Autowired
    private MemoryDBManager memoryDBManager;

    @Autowired
    private NameService nameService;

    @Autowired
    private ReviewService reviewService;



    @RequestMapping


    public String handleRequest(ModelMap model,HttpServletRequest request) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String user = null;
        String supplierDB = null;
        String startDate = "2014-01-01";
        String division = "";//should be the division topparent
        String connectionId = null;
        String sendEmails = null;

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("user")) {
                user = paramValue;
            } else if (paramName.equals("supplierdb")) {
                supplierDB = paramValue;
            } else if (paramName.equals("startdate")) {
                startDate = paramValue;
            } else if (paramName.equals("division")) {
                division = paramValue;
            } else if (paramName.equals("sendemails")) {
                sendEmails = paramValue;
            } else if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            }
        }
        LoggedInConnection loggedInConnection;

        if (connectionId == null) {
            loggedInConnection = loginService.login("yousay1", "bill@azquo.com", "password", 0, "", false);

        } else {
            loggedInConnection = loginService.getConnection(connectionId);
        }
        if (supplierDB != null) {
            loginService.switchDatabase(loggedInConnection, databaseDAO.findForName(loggedInConnection.getBusinessId(), supplierDB));
        }
        String result = "-";
        if (division.length()> 0){
            result = reviewService.showReviews(request.getServletContext(), loggedInConnection,division, startDate);
        }
        if (sendEmails != null){
            result = reviewService.sendEmails(request.getServletContext(), loggedInConnection,1000);
        }


        model.addAttribute("content", result);
        return "utf8page";
     }


  }

