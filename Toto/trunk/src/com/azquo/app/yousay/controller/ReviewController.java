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
import javax.servlet.http.HttpServletResponse;
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

/*    @Autowired
    private MemoryDBManager memoryDBManager;*/

/*    @Autowired
    private NameService nameService;*/

    @Autowired
    private ReviewService reviewService;



    @RequestMapping
    public String handleRequest(ModelMap model,HttpServletRequest request) throws Exception {

        Map<String, String[]> parameterMap = request.getParameterMap();

        String op = request.getParameter("op");
        String supplierDB = request.getParameter("supplierdb");
        String startDate = request.getParameter("startdate") != null ? request.getParameter("startdate")  : "2014-01-01";
        String division = request.getParameter("division") != null ? request.getParameter("division")  : "";//should be the division topparent
        String connectionId = request.getParameter("connectionid");
        String orderRef = request.getParameter("orderref");
        int businessId = 0;
        if (request.getParameter("businessid") != null) {
            try {
                businessId = Integer.parseInt(request.getParameter("businessid"));
            } catch (Exception ignored) {
                //ignore!
            }
        }
        String submit = request.getParameter("submit");
        Map<String,String> ratings = new HashMap<String, String>();
        Map<String,String> comments = new HashMap<String, String>();
        String velocityTemplate = request.getParameter("velocitytemplate");
        String user = request.getParameter("user");
        String password = request.getParameter("password");

        // edd changing from getParameterNames as I wince a little seeing an enumeration . . .
        // scan through for parameters rating1 rating2 comment1 comment2 etc. One could scan with a for loop but this seems as good a way as any.
        for (String paramName : parameterMap.keySet()){
            String paramValue = parameterMap.get(paramName)[0];
            if (paramName.startsWith("rating")){
                String rating = paramName.substring(6);
                ratings.put(rating,paramValue);
            } else if (paramName.startsWith("comment")){
                String comment = paramName.substring(7);
                ratings.put(comment, paramValue);
            }
        }
        LoggedInConnection loggedInConnection;

        if (connectionId == null) {
            if (user != null){
                loggedInConnection = loginService.login(supplierDB,user,password,0,null,false);
            }else {
                if (businessId > 0) {//someone filling in a review
                    loggedInConnection = loginService.login(supplierDB, "", "", 0, "", false, businessId);
                } else {
                    //temporary connection .. need to think about this
                    loggedInConnection = loginService.login(supplierDB, "", "", 0, "", false, 1);
                    // edd just wants it to work for the mo!
                    //loggedInConnection = loginService.login("yousay1", "edd@azquo.com", "eddtest", 0, "", false);
                }
            }

        } else {
            loggedInConnection = loginService.getConnection(connectionId);
        }
        if (supplierDB != null) {
            loginService.switchDatabase(loggedInConnection, databaseDAO.findForName(loggedInConnection.getBusinessId(), supplierDB));
        }
        String result = "";

        if (op.equals("showreviews")){
            result = reviewService.showReviews(request, loggedInConnection,division, startDate, velocityTemplate);
        }
        if (op.equals("sendemails")){
            result = reviewService.sendEmails(request, loggedInConnection,1000, velocityTemplate);
        }
        if (op.equals("reviewform")){
            if (submit!=null){
                reviewService.processReviewForm(loggedInConnection, orderRef, ratings, comments);
                result = reviewService.showReviews(request, loggedInConnection,division, startDate, velocityTemplate);
            } else {
                result = reviewService.createReviewForm(request, loggedInConnection, orderRef, velocityTemplate);
            }
        }
        model.addAttribute("content", result);
        return "utf8page";
    }


  }

