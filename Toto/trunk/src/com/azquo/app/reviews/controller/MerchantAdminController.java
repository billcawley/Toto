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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        System.out.println("Merchant admin accessed");
        String testitem = request.getParameter("testitem");
        if (testitem != null){
            model.addAttribute("content", "received testitem = " + testitem);
            return "utf8page";

        }
        List<String> values = new ArrayList<String>();
        String op = request.getParameter("op");
        String itemName = request.getParameter("itemname").replace("_"," ");
        String type = request.getParameter("itemtype");
        String nameId = request.getParameter("nameid");
        Map<String,String> fieldnames = new HashMap<String, String>();
        String download = request.getParameter("download");
        String field = request.getParameter("field");
        Map<String,String> fieldvalues = new HashMap<String, String>();
        String velocityTemplate = request.getParameter("velocitytemplate");
        String submit = request.getParameter("submit");
         // scan through for parameters rating1 rating2 comment1 comment2 etc. One could scan with a for loop but this seems as good a way as any.
        int i=1;
        String fieldVal = request.getParameter("field" + i++);
        while (fieldVal != null){
            values.add(fieldVal);
            fieldVal = request.getParameter("field" + i++);
        }


        if (op==null){
            op="displaypage";
            if (itemName==null)  itemName="";
            if (type==null) type = "";
        }
        String result = "";
        if (download!=null && download.length() > 0){
            op = "";
            reviewService.download(response, itemName, Integer.parseInt(nameId), field);
        }
        if(op.equals("displaypage")){
            if (submit == null){
                result = reviewService.createPageSpec(itemName,type, nameId);
            }else{
                reviewService.saveData(response, itemName,values, Integer.parseInt(nameId));
            }

        }

         model.addAttribute("content", result);
        return "utf8page";
    }


}

