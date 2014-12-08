package com.azquo.app.reviews.controller;

/**
 * Created by edd on 09/10/14.
 */
import com.azquo.app.reviews.service.ReviewService;
import com.azquo.app.reviews.service.ReviewsCustomerService;
import com.azquo.app.reviews.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */

@Controller
@RequestMapping("/MerchantAdmin")


public class MerchantAdminController {

    @Autowired
    private ReviewsCustomerService reviewsCustomerService;
    @Autowired
    private UserService userService;
    @Autowired
    ReviewService reviewService;

    @RequestMapping
    public String handleRequest(ModelMap model,HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, String[]> parameterMap = request.getParameterMap();

        String op = request.getParameter("op");
        String itemName = request.getParameter("itemname").replace("_"," ");
        String type = request.getParameter("itemtype");
        String nameId = request.getParameter("nameid");
        Map<String,String> fieldnames = new HashMap<String, String>();
        Map<String,String> fieldvalues = new HashMap<String, String>();
        String velocityTemplate = request.getParameter("velocitytemplate");

        // scan through for parameters rating1 rating2 comment1 comment2 etc. One could scan with a for loop but this seems as good a way as any.
        for (String paramName : parameterMap.keySet()){
            String paramValue = parameterMap.get(paramName)[0];
            if (paramName.startsWith("fieldname")){
                String fieldno = paramName.substring(9);
                fieldnames.put(fieldno,paramValue);
            } else if (paramName.startsWith("fieldvalue")){
                String comment = paramName.substring(10);
                fieldvalues.put(comment, paramValue);
            }
        }

        if (op==null){
            op="displaypage";
            if (itemName==null)  itemName="";
            if (type==null) type = "";
        }
        String result = "";
        if(op.equals("displaypage")){
            result = reviewService.createPageSpec(itemName,type, nameId);

        }

         model.addAttribute("content", result);
        return "utf8page";
    }


}

