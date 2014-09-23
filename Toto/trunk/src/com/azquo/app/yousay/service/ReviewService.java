package com.azquo.app.yousay.service;

import com.azquo.memorydb.Name;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.NameService;
import com.azquo.util.AzquoMailer;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletContext;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by bill on 12/09/14.
 */

public class ReviewService {

    @Autowired
    private NameService nameService;


    @Autowired
    private AzquoMailer azquoMailer;

    public String sendEmails(LoggedInConnection loggedInConnection, int maxCount)throws Exception{

        String error = "";
        Name emailsToBeSent  = nameService.findByName(loggedInConnection,"Emails to be sent");
         String now = todayString();
        int count = 0;
        for (Name order:emailsToBeSent.getChildren()) {
            String feedbackDate = order.getAttribute("Feedback date");
            if (feedbackDate.compareTo(now) < 0) {
                error = sendEmail(loggedInConnection, order);
                if (error.length() > 0) return error;
                order.setAttributeWillBePersisted("Email sent", now);
                emailsToBeSent.removeFromChildrenWillBePersisted(order);
                count++;
                if (count >= maxCount) {
                    break;
                }

            }
            return error;
        }
        return "";

    }

    public String sendEmail(LoggedInConnection loggedInConnection,Name order) throws Exception{
        List<Name> orderItems = new ArrayList<Name>();
        String error = nameService.interpretName(loggedInConnection, orderItems, order.getDefaultDisplayName() + " Order items");
        if (error.length() > 0) return error;
        if (orderItems.size() == 0) return ("No items in order " + order.getDefaultDisplayName());
        String description = "";
        int itemCount = 0;
        for (Name orderItem:orderItems){
            //may need to create an order here....
            String product = orderItem.getAttribute("product");
            if (!product.equalsIgnoreCase("service")){
                if (itemCount++ > 0) {
                    description +=", ";
                }
                if (itemCount == 3){
                    description += "etc.";
                    break;
                }
                description += product;
            }
            String supplierName = "Not yet set";

            VelocityEngine ve = new VelocityEngine();
            Properties properties = new Properties();
            properties.setProperty("file.resource.loader.path", "/home/azquo/velocity");
            ve.init(properties);


            ve.init();
        /*  next, get the Template  */
            Template t = ve.getTemplate("email.vm");
        /*  create a context and add data */
            VelocityContext context = new VelocityContext();

            String customerName = order.getAttribute("Customer name");
            context.put("customername", customerName);
            String email =  order.getAttribute("Customer email");
            Name ordersByDate = nameService.findByName(loggedInConnection,"Orders by date");
            String orderDate = showDate(getValueFromParent(order,ordersByDate));
            context.put("orderdate", orderDate);
            StringWriter writer = new StringWriter();
            t.merge(context, writer);



            azquoMailer.sendEMail(email,customerName,"Feedback request on behalf of " + supplierName, writer.toString());



        }
        return "";


    }

    public String todayString(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(new Date());

    }

    public String getValueFromParent(Name child, Name parent){
        List<Name> parentName = child.findAllParents();
        parentName.retainAll(parent.getChildren());
        if (parentName.size()==0){
            return "none";
        }
        return parentName.get(0).getDefaultDisplayName();

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


    public String showReviews(ServletContext servletContext, LoggedInConnection loggedInConnection, String division, String startDate) throws Exception{
        List<Name> orderItems = new ArrayList<Name>();
        List<Map<String, String>> reviews = new ArrayList<Map<String, String>>();
        String error = nameService.interpretName(loggedInConnection, orderItems, division + ";level lowest;WHERE Feedback date >= \"" + startDate + "\" * order;level lowest");
        if (error.length() > 0) {
            return error;
        }
        Name rating = nameService.findByName(loggedInConnection, "All ratings");
        int posCount = 0;
        for (Name orderItem : orderItems) {
            Map<String, String> r = new HashMap<String, String>();
            String ratingStr = getValueFromParent(orderItem,rating);
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
        int reviewCount = orderItems.size();
        if (reviewCount > 0) {

            VelocityEngine ve = new VelocityEngine();
            Properties properties = new Properties();
            properties.setProperty("resource.loader", "webapp");
            properties.setProperty("webapp.resource.loader.class", "org.apache.velocity.tools.view.WebappResourceLoader");
            properties.setProperty("webapp.resource.loader.path", "/WEB-INF/velocity/");
            ve.setApplicationAttribute("javax.servlet.ServletContext", servletContext);
            ve.init(properties);

            //ve.init();
        /*  next, get the Template  */
            Template t = ve.getTemplate("form.vm");
        /*  create a context and add data */
            VelocityContext context = new VelocityContext();
            context.put("reviewcount", reviewCount);

            context.put("overallrating", (posCount * 100 / reviewCount));
            context.put("reviews", reviews);
        /* now render the template into a StringWriter */
            StringWriter writer = new StringWriter();
            t.merge(context, writer);
        /* show the World */
            return writer.toString();
        }else{
            return "no reviews found";
        }




    }


}
