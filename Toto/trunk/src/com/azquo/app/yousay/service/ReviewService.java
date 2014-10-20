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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

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


    public String sendEmails(HttpServletRequest request,LoggedInConnection loggedInConnection, int maxCount, String velocityTemplate)throws Exception{

        Name topSupplier = nameService.findByName(loggedInConnection,"supplier");
        Name supplier = null;
        for (Name name:topSupplier.getChildren()){
            supplier = name;
            break;
        }

        if (velocityTemplate==null) {
            velocityTemplate = supplier.getAttribute("email template");

        }
        if (velocityTemplate==null){
            velocityTemplate="email.vm";
        }else{
            velocityTemplate = "/home/azquo/databases/" + loggedInConnection.getCurrentDBName() + "/velocitytemplates/" + velocityTemplate;
        }
        ServletContext servletContext = request.getServletContext();
        String thisURL = request.getRequestURL().toString();
        String error = "";
        Name emailsToBeSent  = nameService.findByName(loggedInConnection,"Emails to be sent");
        Name ordersWithEmailSent = nameService.findByName(loggedInConnection, "Orders with email sent");
        Set<Name> emailsSentThisTime = new HashSet<Name>();
         String now = todayString();
        int count = 0;
        String todaysEmailsSent = "Emails sent on " + df2.format(new Date());
        Name todaysEmails = nameService.findOrCreateNameInParent(loggedInConnection,todaysEmailsSent,ordersWithEmailSent, false);
        for (Name order:emailsToBeSent.getChildren()) {
            String feedbackDate = order.getAttribute("Feedback date");
            if (feedbackDate == null){
                error = "no feedback date for order " + order.getDefaultDisplayName();
            }else{
                if (feedbackDate.compareTo(now) < 0) {
                    //todo  consider what happens if the server crashes
                    error = sendEmail(thisURL, servletContext, loggedInConnection, order, velocityTemplate, supplier);
                    if (error.length() > 0) return error;
                    order.setAttributeWillBePersisted("Email sent", now);
                    emailsSentThisTime.add(order);
                    todaysEmails.addChildWillBePersisted(order);
                    count++;
                    if (count >= maxCount) {

                        break;
                    }
                }

            }

        }
        for (Name order:emailsSentThisTime){
            emailsToBeSent.removeFromChildrenWillBePersisted(order);
        }
        nameService.persist(loggedInConnection);

        return "";

    }

    public String sendEmail(String thisURL, ServletContext servletContext,LoggedInConnection loggedInConnection,Name order, String velocityTemplate, Name supplier) throws Exception{
        Map<String, String> context = new HashMap<String, String>();
        Set<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return ("No items in order " + order.getDefaultDisplayName());
        Name allProducts = nameService.findByName(loggedInConnection,"All products");
        Name saleDate = nameService.findByName(loggedInConnection, "Sale date");
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
        if (velocityTemplate==null){
            velocityTemplate="email.vm";
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
        String orderSaleDate = getValueFromParent(order,saleDate);
          String thisSite = thisURL.substring(0,thisURL.lastIndexOf("/",thisURL.length() -2));
        context.put("supplierlogo",thisSite + "/Image/?supplierdb=" + loggedInConnection.getCurrentDBName() + "&image=" + supplier.getAttribute("logo"));
        context.put("saledate", showDate(orderSaleDate));
        context.put("customername", order.getAttribute("Customer Name"));
        context.put("saledescription", saledesc.toString());
        context.put("supplier", supplier.getDefaultDisplayName());
        context.put("feedbacklink", thisURL + "?op=reviewform&supplierdb=" + loggedInConnection.getCurrentDBName() + "&orderref=" + order.getDefaultDisplayName());
        context.put("reviewslink", thisURL + "?op=showreviews&supplierdb=" + loggedInConnection.getCurrentDBName() + "&division=" + supplier.getDefaultDisplayName());
        String result = convertToVelocity(servletContext, context,"", null, velocityTemplate);
        if (!order.getAttribute("Customer email").equals("demo@azquo.com")){
            azquoMailer.sendEMail(order.getAttribute("Customer email"), order.getAttribute("Customer name"),"Feedback request on behalf of " + supplier.getDefaultDisplayName(), result);
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

    public class VelocityReview {
        public String rating;
        public String ratingName;
        public String product;
        public String comment;
        public String commentName;
        public String formattedDate;
        public String date;
        public String type;

        //velocity wants getters


        public String getRating() {
            return rating;
        }

        public String getRatingName() {
            return ratingName;
        }

        public String getProduct() {
            return product;
        }

        public String getComment() {
            return comment;
        }

        public String getCommentName() {
            return commentName;
        }

        public String getFormattedDate() {
            return formattedDate;
        }
        public String getdate() {
            return date;
        }

        public String getType() {
            return type;
        }

        // for XML, ideally will remove later
        public Map<String, String> toStringMap(){
            Map<String, String> toReturn = new HashMap<String, String>();
            toReturn.put("rating", rating);
            toReturn.put("ratingName", ratingName);
            toReturn.put("product", product);
            toReturn.put("comment", comment);
            toReturn.put("commentName", commentName);
            toReturn.put("date",date);
            toReturn.put("type", type);
            return toReturn;
        }
    }

    VelocityReview getVelocityReview(Name orderItem, Name rating, Name product) {

        VelocityReview vr = new VelocityReview();

        vr.rating = getValueFromParent(orderItem, rating);
        vr.product = getValueFromParent(orderItem, product);
       System.out.println("order item " + orderItem);
        vr.comment = orderItem.getAttribute("Comment");
        if (vr.comment == null) {
            vr.comment = "";
        }
        if (vr.comment.indexOf("|Supplier:") > 0) {
            vr.comment = vr.comment.replace("|Supplier:", "<div class=\"suppliercomment\">") + "</div>";
        }
        vr.formattedDate = showDate(orderItem.getAttribute("Feedback date"));
        vr.date = orderItem.getAttribute("Feedback date");

        return vr;
    }

    public static void addXmlElements(TransformerHandler hd, org.xml.sax.helpers.AttributesImpl atts, Map<String,String>items)        throws Exception {
        for (String key:items.keySet()) {
            String value = items.get(key);
            if (value == null) value = "";

            hd.startElement("", "", key, atts);
            hd.characters(value.toCharArray(), 0, value.length());
            hd.endElement("", "", key);
        }

    }


    public String showReviews(HttpServletRequest request, LoggedInConnection loggedInConnection, String division, String startDate, String velocityTemplate) throws Exception {

        boolean XML = true;
        if (velocityTemplate !=null){
            XML = false;
        }
        List<Name> orderItems = new ArrayList<Name>();
        Map<String, String> context = new HashMap<String, String>();
        List<VelocityReview> reviews = new ArrayList<VelocityReview>();
        String error = nameService.interpretName(loggedInConnection, orderItems, division + ";level lowest;WHERE Feedback date >= \"" + startDate + "\" * order;level lowest * All ratings;level lowest");
        if (error.length() > 0) {
            return error;
        }
        Name rating = nameService.findByName(loggedInConnection, "All ratings");
        Name product = nameService.findByName(loggedInConnection, "All Products");
        int posCount = 0;
        for (Name orderItem : orderItems) {

            VelocityReview vr = getVelocityReview(orderItem, rating, product);
            if (vr.comment.length()==0){
                vr.comment = "No comment";
            }
            if (vr.rating.contains("+")) {
                posCount++;
            }
           reviews.add(vr);


        }
        int reviewCount = orderItems.size();
        context.put("reviewcount", reviewCount + "");
        context.put("overallrating", (posCount * 100 / reviewCount) + "");
        if (reviewCount > 0) {
            if (XML) {

                  return convertToXML(context,"reviews", reviews);



            }else {
                return convertToVelocity(request.getServletContext(),context,"reviews", reviews, velocityTemplate);
                }
        } else {
            return "no reviews found";
        }
    }


    private String convertToXML(Map<String, String> context, String itemName, List<VelocityReview> items)throws Exception{

        StringWriter sw = new StringWriter();

        StreamResult streamResult = new StreamResult(sw);
        SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler hd = tf.newTransformerHandler();
        Transformer serializer = hd.getTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        hd.setResult(streamResult);
        hd.startDocument();
        org.xml.sax.helpers.AttributesImpl atts = new org.xml.sax.helpers.AttributesImpl();
        addXmlElements(hd, atts, context);
        for (VelocityReview item:items){
            hd.startElement("","","review", atts);
            addXmlElements(hd,atts, item.toStringMap());
            hd.endElement("","","review");
        }
        hd.endDocument();
        String result = sw.toString();
        return result;


    }


    public String createReviewForm(HttpServletRequest request,LoggedInConnection loggedInConnection, String orderRef, String velocityTemplate)throws Exception{

        boolean XML = true;
        if (velocityTemplate!=null) XML = false;
        Map<String, String> context = new HashMap<String, String>();
        List<VelocityReview> reviews = new ArrayList<VelocityReview>();


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

        StringBuffer validationScript = new StringBuffer();
        context.put("supplierlogo", supplier.getAttribute("logo"));
        context.put("intro", intro);
        context.put("connectionid", loggedInConnection.getConnectionId());
        context.put("submit", "Submit");
        Name rating = nameService.findByName(loggedInConnection, "All ratings");
        Name product = nameService.findByName(loggedInConnection, "All Products");
        for (Name orderItem:orderItems){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ")+1);
            VelocityReview vr = getVelocityReview(orderItem, rating, product);
            if (service.getChildren().contains(orderItem)) {
                // todo, these from public static finals
                vr.type = "service";
                 validationScript.append("frmvalidator.addValidation(\"comment" + productCode + "\",\"req\",\"Please enter a comment for " + vr.product + "\");\n");
            }else{
                vr.type = "product";
            }
            vr.ratingName =  "rating" + productCode;
            vr.commentName = "comment" + productCode;
            validationScript.append("frmvalidator.addValidation(\"rating" + productCode + "\",\"req\",\"Please enter a rating for " + vr.product + "\");\n");
            validationScript.append("frmvalidator.EnableOnPageErrorDisplaySingleBox();\n");
            validationScript.append("frmvalidator.EnableMsgsTogether();\n");

            reviews.add(vr);


        }
        context.put("validationscript", validationScript.toString());
        if (XML){
            return convertToXML(context,"reviews", reviews);
        }else{
            return convertToVelocity(request.getServletContext(),context,"reviews", reviews, velocityTemplate);
        }





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
                    orderItem.setAttributeWillBePersisted("Review date", feedbackDate);
                }
            }
            String comment = comments.get(productCode);
            if (comment != null){
                orderItem.setAttributeWillBePersisted("comment", comment);
                orderItem.setAttributeWillBePersisted("Review date", feedbackDate);
            }
        }

        nameService.persist(loggedInConnection);




        return "";
    }

    private void addVelocityElements(VelocityContext context, Map<String,String>items){
        for (String key:items.keySet()){
            String value = items.get(key);
            if (value==null) value="";
            context.put(key, value);
        }
    }

    private String convertToVelocity(ServletContext servletContext, Map<String,String> context, String itemName, List<VelocityReview> items, String velocityTemplate){

        VelocityEngine ve = new VelocityEngine();
        Properties properties = new Properties();
        Template t;
        if (velocityTemplate==null){
            velocityTemplate = "email.vm";
        }

        if (velocityTemplate != null && (velocityTemplate.startsWith("http://") || velocityTemplate.startsWith("https://")) && velocityTemplate.indexOf("/", 8) != -1) {
            properties.put("resource.loader", "url");
            properties.put("url.resource.loader.class", "org.apache.velocity.runtime.resource.loader.URLResourceLoader");
            properties.put("url.resource.loader.root", velocityTemplate.substring(0, velocityTemplate.lastIndexOf("/") + 1));
            ve.init(properties);
            t = ve.getTemplate(velocityTemplate.substring(velocityTemplate.lastIndexOf("/") + 1));
        } else {
            properties.setProperty("resource.loader", "webapp");
            properties.setProperty("webapp.resource.loader.class", "org.apache.velocity.tools.view.WebappResourceLoader");
            properties.setProperty("webapp.resource.loader.path", "/WEB-INF/velocity/");
            ve.setApplicationAttribute("javax.servlet.ServletContext", servletContext);
            ve.init(properties);

            //ve.init();
        /*  next, get the Template  */
            t = ve.getTemplate(velocityTemplate);
        }
        /*  create a context and add data */
        VelocityContext vcontext = new VelocityContext();
        addVelocityElements(vcontext,context);
        if (items != null){
            vcontext.put(itemName, items);
        }
         /* now render the template into a StringWriter */
        StringWriter writer = new StringWriter();
        t.merge(vcontext, writer);
        /* show the World */
        return writer.toString();



    }



    public String createSupplierResponseForm(ServletContext servletContext,LoggedInConnection loggedInConnection, String orderRef, String velocityTemplate)throws Exception{
        boolean XML = true;
        if (velocityTemplate!=null) XML = false;
        Map<String, String> context = new HashMap<String, String>();
        List<VelocityReview> reviews = new ArrayList<VelocityReview>();

        // note by edd, todo there should be a better way, ha!

        Name order = nameService.findByName(loggedInConnection, orderRef + ", Order");
        if (order==null){
            // todo : this is what exceptions are for
            return "error: unrecognised order ref " + orderRef;
        }
        // so the children of order will only ever be order items? I suppose this makes sense
        Set<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return "error: No items in order " + order.getDefaultDisplayName();
        // so just not using? I'll comment
        //Name allProducts = nameService.findByName(loggedInConnection,"All products");
        // interesting
        Name service = nameService.findByName(loggedInConnection,"Service");
        Set<Name> productItems = new HashSet<Name>();
        Set<Name> serviceItems = new HashSet<Name>();
        /* so we're going though the order items seeing if they're in the set service or not and categorising accordingly.
        This truly is using the set principle. I wonder how fast .contains is. Probably pretty fast and if looking up by tree
        the as the number increases the "cost" or extra set members will be minimal
        hash function on the name objects is very important!
         */
        for (Name name : orderItems){
            //ASSUMING THAT 'service' HAS ONLY ONE LEVEL OF CHILDREN!
            if (service.getChildren().contains(name)){
                serviceItems.add(name);
            }else{
                productItems.add(name);
            }
        }
        Name topSupplier = nameService.findByName(loggedInConnection,"supplier");
        /*Name supplier = null;
        for (Name name:topSupplier.getChildren()){
            supplier = name;
            break;
        }*/
        Name supplier = topSupplier.getChildren().iterator().next();

        StringBuffer validationScript = new StringBuffer();
        context.put("supplierlogo", supplier.getAttribute("logo"));
        // todo : connection id shoult NOT be in here, its a DB access connection. Deal with later.
        context.put("connectionid", loggedInConnection.getConnectionId());
        context.put("submit", "Submit");
        // todo : probably should not look up this way, config file and cache the object?
        Name rating = nameService.findByName(loggedInConnection, "All ratings");
        Name product = nameService.findByName(loggedInConnection, "All Products");
        for (Name orderItem:orderItems){
            String orderItemName = orderItem.getDefaultDisplayName();
            // this is very hacky, I think we need another way
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ")+1);




            // I'm zapping the comment validation bits for the mo


            VelocityReview vr = getVelocityReview(orderItem, rating, product);
            if (service.getChildren().contains(orderItem)) {
                // todo, these from public static finals
                vr.type = "service";
            }else{
                vr.type = "product";
            }
            vr.ratingName =  "rating" + productCode;
            vr.commentName = "suppliercomment" + productCode;
            reviews.add(vr);


        }
        if (XML){
            return convertToXML(context,"reviews", reviews);
        }else{
            return convertToVelocity(servletContext,context,"reviews", reviews, velocityTemplate);
        }
    }

    public String processSupplierResponseForm(LoggedInConnection loggedInConnection, String orderRef, Map<String, String> comments)throws Exception{

        Name order = nameService.findByName(loggedInConnection, orderRef + ", Order");

        for (Name orderItem: order.getChildren()){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ") + 1);
            String comment = comments.get(productCode);
            if (comment != null && orderItem.getAttribute("Comment") != null){
                orderItem.setAttributeWillBePersisted("Comment", orderItem.getAttribute("Comment") + "|Supplier:" + comment);
            }
        }

        nameService.persist(loggedInConnection);




        return "";
    }



}
