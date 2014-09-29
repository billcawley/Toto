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

    public String sendEmails(ServletContext servletContext,LoggedInConnection loggedInConnection, int maxCount)throws Exception{

        String error = "";
        Name emailsToBeSent  = nameService.findByName(loggedInConnection,"Emails to be sent");
        Name ordersWithEmailSent = nameService.findByName(loggedInConnection, "Orders with email sent");
        Set<Name> emailsSent = new HashSet<Name>();
         String now = todayString();
        int count = 0;

        for (Name order:emailsToBeSent.getChildren()) {
            String feedbackDate = order.getAttribute("Feedback date");
            if (feedbackDate == null){
                error = "no feedback date for order " + order.getDefaultDisplayName();
            }else{
                if (feedbackDate.compareTo(now) < 0) {
                    //todo  consider what happens if the server crashes
                    error = sendEmail(servletContext, loggedInConnection, order);
                    if (error.length() > 0) return error;
                    order.setAttributeWillBePersisted("Email sent", now);
                    emailsSent.add(order);
                    ordersWithEmailSent.addChildWillBePersisted(order);
                    count++;
                    if (count >= maxCount) {

                        break;
                    }
                }

            }

        }
        return "";

    }

    public String sendEmail(ServletContext servletContext,LoggedInConnection loggedInConnection,Name order) throws Exception{
        Set<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return ("No items in order " + order.getDefaultDisplayName());
        Name allProducts = nameService.findByName(loggedInConnection,"All products");
        Name service = nameService.findByName(loggedInConnection,"Service");
        Set<Name> productItems = new HashSet<Name>();
        Set<Name> serviceItems = new HashSet<Name>();
        for (Name name:orderItems){
            //ASSUMING THAT 'service' HAS ONLY ONE LEVEL OF CHILDREN!
            if (service.getChildren().contains(name)){
                serviceItems.add(name);
             }else{
                productItems.add(name);
            }
        }
        StringBuffer saledesc = new StringBuffer();
        int productCount = 0;
        for(Name orderItem:productItems){

            if (productCount > 0){
                saledesc.append(", ");
                if (productCount == 2){
                    saledesc.append("etc.");
                    break;
                }
            }
            saledesc.append(getValueFromParent(orderItem, allProducts));
            productCount++;
        }
         Name topSupplier = nameService.findByName(loggedInConnection,"supplier");
        Name supplier = null;
        for (Name name:topSupplier.getChildren()){
            supplier = name;
            break;
        }



        VelocityEngine ve = new VelocityEngine();
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "webapp");
        properties.setProperty("webapp.resource.loader.class", "org.apache.velocity.tools.view.WebappResourceLoader");
        properties.setProperty("webapp.resource.loader.path", "/WEB-INF/velocity/");
        ve.setApplicationAttribute("javax.servlet.ServletContext", servletContext);
        ve.init(properties);

        //ve.init();
        /*  next, get the Template  */
        Template t = ve.getTemplate("email.vm");
        /*  create a context and add data */
        VelocityContext context = new VelocityContext();
        context.put("saledescription", saledesc.toString());
        context.put("saledate", showDate(order.getAttribute("Sale Date")));
        context.put("customername", order.getAttribute("Customer Name"));
        context.put("supplier", supplier.getDefaultDisplayName());
        context.put("feedbacklink", "http://bomorgan.co.uk:8080/api/Reviews/?supplierdb=yousay1&division=SUTTON&startdate=2014-01-06");
        context.put("reviewslink", "http://bomorgan.co.uk:8080/api/Reviews/?supplierdb=yousay1&division=SUTTON&startdate=2014-01-06");
    /* now render the template into a StringWriter */
        StringWriter writer = new StringWriter();
        t.merge(context, writer);
        /* show the World */
        if (order.getAttribute("Customer email").equals("bill@azquo.com")){
            azquoMailer.sendEMail(order.getAttribute("Customer email"), order.getAttribute("Customer name"),"Feedback request on behalf of " + supplier.getDefaultDisplayName(), writer.toString());
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
        String error = nameService.interpretName(loggedInConnection, orderItems, division + ";level lowest;WHERE Feedback date >= \"" + startDate + "\" * order;level lowest * All ratings;level lowest");
        if (error.length() > 0) {
            return error;
        }
        Name rating = nameService.findByName(loggedInConnection, "All ratings");
        Name product = nameService.findByName(loggedInConnection, "All Products");
        int posCount = 0;
        for (Name orderItem : orderItems) {
            Map<String, String> r = new HashMap<String, String>();
            String ratingStr = getValueFromParent(orderItem,rating);
            String productStr = getValueFromParent(orderItem, product);

            r.put("rating", ratingStr);
            r.put("product", productStr);
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
