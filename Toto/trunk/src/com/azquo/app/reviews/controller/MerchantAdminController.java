package com.azquo.app.reviews.controller;

/**
 * Created by edd on 09/10/14.
 *
 */
import com.azquo.app.reviews.service.ReviewService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;


@Controller
@RequestMapping("/MerchantAdmin")


public class MerchantAdminController {

    @Autowired
    ReviewService reviewService;


    private static final Logger logger = Logger.getLogger(MerchantAdminController.class);



    @RequestMapping
    public String handleRequest(ModelMap model,HttpServletRequest request, HttpServletResponse response) throws Exception {
//        Map<String, String[]> parameterMap = request.getParameterMap();

        System.out.println("Merchant admin accessed");
        String testitem = request.getParameter("testitem");
        if (testitem != null){
            model.addAttribute("content", "received testitem = " + testitem);
            return "utf8page";

        }
        Map<Integer, String> values = new HashMap<Integer, String>();
        String op = request.getParameter("op");
        String itemName = request.getParameter("itemname");
        if (itemName==null){
            itemName = "";

        }
        itemName = itemName.replace("_"," ");
        String type = request.getParameter("itemtype");
        String nameId = request.getParameter("nameid");
//        Map<String,String> fieldnames = new HashMap<String, String>();
        String download = request.getParameter("download");
        String field = request.getParameter("field");
//        Map<String,String> fieldvalues = new HashMap<String, String>();
//        String velocityTemplate = request.getParameter("velocitytemplate");
        String submit = request.getParameter("submit");
        for (int i=1;i < 200;i++){//assuming that there will never be more than 200 fields!
            String fieldVal = request.getParameter("field" + i);
            if (fieldVal != null) {
                values.put(i,fieldVal);
            }
        }
        String result = "";
        try {

        if (op==null){
            op="displaypage";
            if (itemName==null)  itemName="";
            if (type==null) type = "";
        }
        if (download!=null && download.length() > 0){
            op = "";
            reviewService.download(response, itemName, Integer.parseInt(nameId), field);
        }
        if(op.equals("displaypage")){
            if (submit == null){
                    result = reviewService.createPageSpec(itemName, type, nameId);
            }else{
                reviewService.saveData(response,itemName,values, Integer.parseInt(nameId));
            }

            }
        }catch(Exception e){
            result = e.getMessage();
            logger.error(result, e);

        }

         model.addAttribute("content", result);
        return "utf8page";
    }


}

