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
import java.net.InetAddress;
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
        String orderRef = null;
        int businessId = 0;
        String submit = null;
        Map<String,String> ratings = new HashMap<String, String>();
        Map<String,String> comments = new HashMap<String, String>();
        String velocityTemplate = null;

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("user")) {
                user = paramValue;
            } else if (paramName.equals("velocitytemplate")) {
                velocityTemplate = paramValue;
            } else if (paramName.equals("supplierdb")) {
                supplierDB = paramValue;
            } else if (paramName.equals("startdate")) {
                startDate = paramValue;
            } else if (paramName.equals("division")) {
                division = paramValue;
            } else if (paramName.equals("businessid")) {
                try{
                    businessId = Integer.parseInt(paramValue);
                }catch(Exception e){
                    //ignore!
                }
             } else if (paramName.equals("orderref")) {
                orderRef = paramValue;
            } else if (paramName.equals("sendemails")) {
                sendEmails = paramValue;
            } else if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            } else if (paramName.equals("submit")) {
                submit = paramValue;
            }else if (paramName.startsWith("rating")){
                String rating = paramName.substring(6);
                ratings.put(rating,paramValue);
            }else if (paramName.startsWith("comment")){
                String comment = paramName.substring(7);
                ratings.put(comment, paramValue);
            }
        }
        LoggedInConnection loggedInConnection;

        if (connectionId == null) {
            if (businessId > 0){//someone filling in a review
                loggedInConnection = loginService.login(supplierDB,"","",0,"",false,businessId);
            }else if (InetAddress.getLocalHost().getHostName().equalsIgnoreCase("linux-9m2a.site") || InetAddress.getLocalHost().getHostName().equalsIgnoreCase("edwards-air")){
                loggedInConnection = loginService.login("yousay1", "edd@azquo.com", "eddtest", 0, "", false);
            } else {
                loggedInConnection = loginService.login("yousay1", "bill@azquo.com", "password", 0, "", false);
            }

        } else {
            loggedInConnection = loginService.getConnection(connectionId);
        }
        if (supplierDB != null) {
            loginService.switchDatabase(loggedInConnection, databaseDAO.findForName(loggedInConnection.getBusinessId(), supplierDB));
        }
        String result = "";

        if (division.length()> 0){
            result = reviewService.showReviews(request.getServletContext(), loggedInConnection,division, startDate, velocityTemplate);
        }
        if (sendEmails != null){
            result = reviewService.sendEmails(request.getServletContext(), loggedInConnection,1000, velocityTemplate);
        }
        if (businessId > 0){
            if (submit!=null){
                reviewService.processReviewForm(loggedInConnection, orderRef, ratings, comments);
                result = reviewService.showReviews(request.getServletContext(), loggedInConnection,division, startDate, velocityTemplate);
            }
            result = reviewService.createReviewForm(request.getServletContext(), loggedInConnection, orderRef, velocityTemplate);
        }
        model.addAttribute("content", result);
        return "utf8page";
    }


  }

