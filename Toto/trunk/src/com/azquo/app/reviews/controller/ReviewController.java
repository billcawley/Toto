package com.azquo.app.reviews.controller;

/**
 * Created by bill on 09/09/14.
 *
 */
import com.azquo.app.reviews.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
@RequestMapping("/Reviews")


public class ReviewController {

    @Autowired
    private ReviewService reviewService;



    @RequestMapping
    public String handleRequest(ModelMap model,HttpServletRequest request) throws Exception {

        Map<String, String[]> parameterMap = request.getParameterMap();

        String op = request.getParameter("op");
        String supplierDB = request.getParameter("supplierdb");
        String startDate = request.getParameter("startdate") != null ? request.getParameter("startdate")  : "2014-01-01";
        String division = request.getParameter("division") != null ? request.getParameter("division")  : "";//should be the division topparent
        String orderRef = request.getParameter("orderref");
        String reviewType = request.getParameter("reviewtype");
        Map<String,String> ratings = new HashMap<String, String>();
        Map<String,String> comments = new HashMap<String, String>();
        String velocityTemplate = request.getParameter("velocitytemplate");

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
        if (op==null){
            op="showreviews";
        }
        String result = "";
        if (op.equals("showreviews")){
            result = reviewService.showReviews(supplierDB,division, startDate, reviewType, velocityTemplate);
        }
        if (op.equals("sendemails")){
            result = reviewService.sendEmails(request.getRequestURL().toString(), supplierDB,1000, velocityTemplate);
        }
        if (op.equals("reviewform")){
            if (ratings.size() > 0){
                reviewService.processReviewForm(supplierDB, orderRef, ratings, comments);
                result = reviewService.showReviews(supplierDB,division, startDate, reviewType,velocityTemplate);
            } else {
                result = reviewService.createReviewForm(request, supplierDB, orderRef, velocityTemplate);
            }
        }
        model.addAttribute("content", result);
        return "utf8page";
    }


  }

