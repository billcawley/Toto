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

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss");
    SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/YY");

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
        return df.format(new Date());

    }

    public String getValueFromParent(Name child, Name parent){
        List<Name> parentName = child.findAllParents();
        parentName.retainAll(parent.getChildren());
        if (parentName.size()==0){
            return "";
        }
        return parentName.get(0).getDefaultDisplayName();

    }


    public String showDate(String fileDate){


        try{
            Date date = df2.parse(fileDate);
            //checks needed here for '5 minutes ago'
            return outputFormat.format(date);
        }catch(Exception e){
            return "unrecognised date";
        }



    }

    Map<String,String> velocityReview(Name orderItem, Name rating, Name product) {

        Map<String, String> r = new HashMap<String, String>();
        String ratingStr = getValueFromParent(orderItem, rating);
        String productStr = getValueFromParent(orderItem, product);

        r.put("rating", ratingStr);
        r.put("product", productStr);
         String comment = orderItem.getAttribute("comment");
        if (comment == null) {
            comment = "";
        }
        if (comment.indexOf("|Supplier:") > 0) {
            comment = comment.replace("|Supplier:", "<div class=\"suppliercomment\">") + "</div>";
        }
        r.put("comment", comment);
        r.put("date", showDate(orderItem.getAttribute("Feedback date")));
        return r;
    }



    public String showReviews(ServletContext servletContext, LoggedInConnection loggedInConnection, String division, String startDate) throws Exception {
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

            Map<String, String> r = velocityReview(orderItem, rating, product);
            if (r.get("comment").length()==0){
                r.put("comment", "No comment");
            }
            if (r.get("rating").contains("+")) {
                posCount++;
            }
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
            Template t = ve.getTemplate("showreviews.vm");
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
        } else {
            return "no reviews found";
        }
    }

    public String createReviewForm(ServletContext servletContext,LoggedInConnection loggedInConnection, String orderRef)throws Exception{

        List<Map<String, String>> reviews = new ArrayList<Map<String, String>>();


        Name order = nameService.findByName(loggedInConnection, orderRef + ", Order");
        if (order==null){
            return "error: unrecognised order ref " + orderRef;
        }
        Set<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return "error: No items in order " + order.getDefaultDisplayName();
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
        Name topSupplier = nameService.findByName(loggedInConnection,"supplier");
        Name supplier = null;
        for (Name name:topSupplier.getChildren()){
            supplier = name;
            break;
        }

        String intro = "<p>Thank you for taking the time to leave feedback for " + supplier.getDefaultDisplayName() + ".</p><p>Please note that your feedback is PUBLIC and will be displayed innediately on our site</p>";
        if (supplier.getAttribute("feedbackintrotext") != null){
            intro = supplier.getAttribute("feedbackintrotext");
        }


        VelocityEngine ve = new VelocityEngine();
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "webapp");
        properties.setProperty("webapp.resource.loader.class", "org.apache.velocity.tools.view.WebappResourceLoader");
        properties.setProperty("webapp.resource.loader.path", "/WEB-INF/velocity/");
        ve.setApplicationAttribute("javax.servlet.ServletContext", servletContext);
        ve.init(properties);
        StringBuffer validationScript = new StringBuffer();
        //ve.init();
        /*  next, get the Template  */
        Template t = ve.getTemplate("form.vm");
        /*  create a context and add data */
        VelocityContext context = new VelocityContext();
        context.put("supplierlogo", supplier.getAttribute("logo"));
        context.put("intro", intro);
        context.put("connectionid", loggedInConnection.getConnectionId());
        context.put("submit", "Submit");
        Name rating = nameService.findByName(loggedInConnection, "All ratings");
        Name product = nameService.findByName(loggedInConnection, "All Products");
        for (Name orderItem:orderItems){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ")+1);
            Map<String, String> r = velocityReview(orderItem, rating, product);
            if (service.getChildren().contains(orderItem)) {
                r.put("type", "service");
                 validationScript.append("frmvalidator.addValidation(\"comment" + productCode + "\",\"req\",\"Please enter a comment for " + r.get("product") + "\");\n");
            }else{
                r.put("type", "product");
            }
            r.put("ratingname", "rating" + productCode);
            r.put("commentname", "comment" + productCode);
            validationScript.append("frmvalidator.addValidation(\"rating" + productCode + "\",\"req\",\"Please enter a rating for " + r.get("product") + "\");\n");
            validationScript.append("frmvalidator.EnableOnPageErrorDisplaySingleBox();\n");
            validationScript.append("frmvalidator.EnableMsgsTogether();\n");

            reviews.add(r);


        }
        context.put("reviews", reviews);
        context.put("validationscript", validationScript.toString());
     /* now render the template into a StringWriter */
        StringWriter writer = new StringWriter();
        t.merge(context, writer);
        /* show the World */



        return writer.toString();





    }


    public String processReviewForm(LoggedInConnection loggedInConnection, String orderRef, Map<String, String> ratings, Map<String, String> comments)throws Exception{

        Name order = nameService.findByName(loggedInConnection, orderRef + ", Order");

        for (Name orderItem: order.getChildren()){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ") + 1);
            String feedbackDate = df.format(new Date());
            String rating = ratings.get(productCode);
            if (rating != null){
                Name ratingSet = nameService.findByName(loggedInConnection,rating + ", Rating");
                if (ratingSet != null) {
                    ratingSet.addChildWillBePersisted(orderItem);
                    orderItem.setAttributeWillBePersisted("feedback date", feedbackDate);
                }
            }
            String comment = comments.get(productCode);
            if (comment != null){
                orderItem.setAttributeWillBePersisted("comment", comment);
                orderItem.setAttributeWillBePersisted("feedback date", feedbackDate);
            }
        }

        nameService.persist(loggedInConnection);




        return "";
    }

}
