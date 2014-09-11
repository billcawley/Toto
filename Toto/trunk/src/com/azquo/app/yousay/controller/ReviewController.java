package com.azquo.app.yousay.controller;

/**
 * Created by bill on 09/09/14.
 */
import com.azquo.admindao.DatabaseDAO;
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



    @RequestMapping
    @ResponseBody


    public String handleRequest(ModelMap model,HttpServletRequest request) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String user = null;
        String supplierDB = null;
        String startDate = "2014-01-01";
        String division = "";//should be the division topparent
        String connectionId = null;
        List<Map<String, String>> reviews = new ArrayList<Map<String, String>>();

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
        List<Name> orderItems = new ArrayList<Name>();
        String error = nameService.interpretName(loggedInConnection, orderItems, division + ";associated order items;WHERE `feedback date` >= \"" + startDate + "\" * Service Order Items;level lowest");
        if (error.length() > 0) {
            return error;
        }
        Name serviceRating = nameService.findByName(loggedInConnection, "Order Items by Rating");
        int posCount = 0;
        for (Name orderItem : orderItems) {
            Map<String, String> r = new HashMap<String, String>();
            List<Name> rating = orderItem.findAllParents();
            rating.retainAll(serviceRating.getChildren());
            String ratingStr = rating.get(0).getDefaultDisplayName().replace(" Order Items","");
            r.put("rating", ratingStr);
            if (ratingStr.contains("+")){
                posCount++;
            }
            String comment = orderItem.getAttribute("Comment");
            if (comment == null){
                comment = "No comment";
            }
            if (comment.indexOf("|Supplier:") > 0){
                comment = comment.replace("|Supplier:","<div class=\"suppliercomment\">") + "</div>";
            }
            r.put("comment", comment);
            r.put("date", showDate(orderItem.getAttribute("Feedback date")));
            reviews.add(r);


        }


        VelocityEngine ve = new VelocityEngine();
        Properties properties = new Properties();
        properties.setProperty("file.resource.loader.path", "/home/azquo/velocity");
        ve.init(properties);

        ve.init();
        /*  next, get the Template  */
        Template t = ve.getTemplate("form.vm");
        /*  create a context and add data */
        VelocityContext context = new VelocityContext();
        int reviewCount = orderItems.size();

        context.put("reviewcount", reviewCount);

        context.put("overallrating", (posCount * 100 / reviewCount));
        context.put("reviews", reviews);
        /* now render the template into a StringWriter */
        StringWriter writer = new StringWriter();
        t.merge(context, writer);
        /* show the World */
        return writer.toString();
    }


    public String showDate(String fileDate){

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/YY");

        try{
            Date date = df.parse(fileDate);
            //checks needed here for '5 minutes ago'
            return outputFormat.format(date);
        }catch(Exception e){
            return "unrecognised date";
        }



     }
}

