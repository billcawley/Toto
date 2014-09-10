package com.azquo.app.yousay.controller;

/**
 * Created by bill on 09/09/14.
 */
import com.azquo.admindao.DatabaseDAO;
import com.azquo.memorydb.MemoryDBManager;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
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



    @RequestMapping
    @ResponseBody


    public String handleRequest(ModelMap model,HttpServletRequest request) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String user = null;
        String supplierDB = null;
        String startDate = null;
        String connectionId = null;
        List<Map<String,String>> reviews = new ArrayList<Map<String, String>>();

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("user")) {
                user = paramValue;
            } else if (paramName.equals("supplierdb")) {
                supplierDB = paramValue;
            } else if (paramName.equals("startdate")) {
                startDate = paramValue;
            } else if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            }
        }
        LoggedInConnection loggedInConnection;

         if (connectionId == null){
             loggedInConnection = loginService.login("yousay1","bill@azquo.com","password",0,"",false);

         }else{
             loggedInConnection = loginService.getConnection(connectionId);
         }

         Map<String,String> r = new HashMap<String, String>();
        r.put("rating","++");
        r.put("comment","<span class=\"customercomment\">Delighted</span><span class=\"suppliercomment\"> On 01/01/2014 Supplier wrote:<br>Thank you for your comments1</span>");
        r.put("date", "01/08/2014");
        reviews.add(r);
        r = new HashMap<String, String>();
        r.put("rating","--");
        r.put("comment","<span class=\"customercomment\">Awful - will not buy again</span><span class=\"suppliercomment\"> On 01/01/2014 Supplier wrote:<br>Thank you for your comments2</span>");
        r.put("date", "02/08/2014");
        reviews.add(r);


        VelocityEngine ve = new VelocityEngine();
        ve.init();
        /*  next, get the Template  */
        Template t = ve.getTemplate( "/home/bill/azquo/toto/trunk/velocity/test1.vm" );
        /*  create a context and add data */
        VelocityContext context = new VelocityContext();
        context.put("reviewcount", 100);
        context.put("overallrating",98);
        context.put("reviews",reviews);
        /* now render the template into a StringWriter */
        StringWriter writer = new StringWriter();
        t.merge( context, writer );
        /* show the World */
        return( writer.toString() );



     }
}

