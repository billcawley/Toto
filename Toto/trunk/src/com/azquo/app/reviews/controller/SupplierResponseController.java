package com.azquo.app.reviews.controller;

/**
 * Created by edd on 09/10/14.
 *
 The supplier rep will need to be logged in

 The form should show, on each line, the product, rating, comment, and space for the response

 The response, together with the date of the response, should be added to the customer attribute.
 The order item should be put in the set relating to that particular rep
 The delay between comment and response should be stored as an attribute on the order item

 A separate email should be sent to the customer for each response, with a link to respond to that thread

 If you then have time, we need to create a form for that link.

 works off the order



 lots of pasted code from review controller initially
 *
 */
import com.azquo.admindao.DatabaseDAO;
import com.azquo.app.reviews.service.ReviewService;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;


import javax.servlet.http.HttpServletRequest;
import java.util.*;


@Controller
@RequestMapping("/SupplierResponse")


public class SupplierResponseController {

    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ReviewService reviewService;

    @RequestMapping
    public String handleRequest(ModelMap model,HttpServletRequest request) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String supplierDB = null;
        String connectionId = null;
        String orderRef = null;
        int businessId = 0;
        String submit = null;
        Map<String,String> comments = new HashMap<String, String>();
        String velocityTemplate = null;

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("velocityTemplate")) {
                velocityTemplate = paramValue;
            } else if (paramName.equals("orderref")) {
                orderRef = paramValue;
            } else if (paramName.equals("submit")) {
                submit = paramValue;
            } else if (paramName.startsWith("suppliercomment")){
                String comment = paramName.substring("suppliercomment".length()); // probably not best practice :)
                comments.put(comment, paramValue);
            }
        }
        LoggedInConnection loggedInConnection;

        if (connectionId == null) {
            if (businessId > 0){//someone filling in a review
                loggedInConnection = loginService.login(supplierDB,"","",0,"",false);
            }else{
                //temporary connection .. need to think about this
                loggedInConnection = loginService.login(supplierDB,"","",0,"",false);
                // edd just wants it to work for the mo!
                //loggedInConnection = loginService.login("yousay1", "edd@azquo.com", "eddtest", 0, "", false);
            }

        } else {
            loggedInConnection = loginService.getConnection(connectionId);
        }
        if (supplierDB != null) {
            loginService.switchDatabase(loggedInConnection, databaseDAO.findForName(loggedInConnection.getBusinessId(), supplierDB));
        }
            if (submit!=null){
                reviewService.processSupplierResponseForm(loggedInConnection, orderRef, comments);
            }
                String result = reviewService.createSupplierResponseForm(loggedInConnection, orderRef, velocityTemplate);
        model.addAttribute("content", result);
        return "utf8page";
    }
}
