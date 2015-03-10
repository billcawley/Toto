package com.azquo.app.reviews.service;

import com.azquo.admindao.BusinessDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.adminentities.User;
import com.azquo.memorydb.*;
import com.azquo.service.*;
import com.azquo.util.AzquoMailer;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.xml.sax.helpers.AttributesImpl;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by bill on 12/09/14.
 */

public class ReviewService {

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss");
    SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/YY");
    private static final String REVIEWS = "reviews";
    private static final String RESPONSESHEET = "Supplier responses";


    @Autowired
    private Environment env;

    @Autowired
    private NameService nameService;

    @Autowired
    private OnlineService onlineService;

    @Autowired
    private AppDBConnectionMap reviewsConnectionMap;

    @Autowired
    private AzquoMailer azquoMailer;

    @Autowired
    ServletContext servletContext;

    @Autowired
    ReviewsCustomerService reviewsCustomerService;

    @Autowired
    UserService userService;

    @Autowired
    ImportService importService;

    @Autowired
    AdminService adminService;

    @Autowired
    LoginService loginService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    BusinessDAO businessDAO;

    @Autowired
    MemoryDBManager memoryDBManager;

    @Autowired
    ValueService valueService;

    public static final String SUPPLIER = "SUPPLIER";
    public static final String ALL_RATINGS = "ALL RATINGS";
    public static final String ALL_PRODUCTS = "ALL PRODUCTS";
    public static final String EMAILS_TO_BE_SENT = "EMAILS TO BE SENT";
    public static final String ORDERS_WITH_EMAIL_SENT = "ORDERS WITH EMAIL SENT";
    public static final String SALE_DATE = "Sale date";
    public static final String SERVICE = "Service";

    public interface SUPPLIER_ATTRIBUTE {
        String EMAIL_TEMPLATE = "EMAIL TEMPLATE";
        String LOGO = "LOGO";
    }

    public interface ORDER_ATTRIBUTE {
        String FEEDBACK_DATE = "Feedback date";
        String EMAIL_SENT = "Email sent";
        String CUSTOMER_NAME = "Customer name";
        String CUSTOMER_EMAIL = "Customer email";
    }

    public interface ORDER_ITEM_ATTRIBUTE {
        String REVIEW_DATE = "Review date";
        String COMMENT = "Comment";
    }


    public interface PRODUCT_ATTRIBUTE {
        String PRODUCT_CODE = "PRODUCT CODE";
    }


    public String sendEmails(String thisURL,String supplierDb, int maxCount, String velocityTemplate)throws Exception{

        AzquoMemoryDBConnection azquoMemoryDBConnection = reviewsConnectionMap.getConnection(supplierDb);
        if (azquoMemoryDBConnection==null){
            return "error: cannot find " + supplierDb;
        }

        // todo handle error if the supplier db is duff

        Name topSupplier = nameService.findByName(azquoMemoryDBConnection, SUPPLIER);
        Name supplier = topSupplier.getChildren().iterator().next();

        if (velocityTemplate == null) {
            velocityTemplate = supplier.getAttribute(SUPPLIER_ATTRIBUTE.EMAIL_TEMPLATE);
        }
        if (velocityTemplate == null){
            velocityTemplate = "email.vm";
        } else {
            velocityTemplate = onlineService.getHomeDir() + "/databases/" + azquoMemoryDBConnection.getCurrentDBName() + "/velocitytemplates/" + velocityTemplate;
        }
        String error = "";
        Name emailsToBeSent  = nameService.findByName(azquoMemoryDBConnection,EMAILS_TO_BE_SENT);
        Name ordersWithEmailSent = nameService.findByName(azquoMemoryDBConnection, ORDERS_WITH_EMAIL_SENT);
        Set<Name> emailsSentThisTime = new HashSet<Name>();
        String now = todayString();
        int count = 0;
        String todaysEmailsSent = "Emails sent on " + df2.format(new Date());
        Name todaysEmails = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,todaysEmailsSent,ordersWithEmailSent, false);
        for (Name order : emailsToBeSent.getChildren()) {
            String feedbackDate = order.getAttribute(ORDER_ATTRIBUTE.FEEDBACK_DATE);
            if (feedbackDate == null){
                error = "no feedback date for order " + order.getDefaultDisplayName();
            }else{
                if (feedbackDate.compareTo(now) < 0) {
                    //todo  consider what happens if the server crashes
                    error = sendEmail(thisURL, supplierDb, order, velocityTemplate, supplier);
                    if (error.length() > 0) return error;
                    order.setAttributeWillBePersisted(ORDER_ATTRIBUTE.EMAIL_SENT, now);
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

        azquoMemoryDBConnection.persist();
        return error;
    }

    public String sendEmail(String thisURL,String supplierDb,Name order, String velocityTemplate, Name supplier) throws Exception{
        AzquoMemoryDBConnection azquoMemoryDBConnection = reviewsConnectionMap.getConnection(supplierDb);

        // todo handle error if the supplier db is duff
        String thisSite = thisURL.substring(0,thisURL.lastIndexOf("/",thisURL.length() -2));
        Map<String, String> context = new HashMap<String, String>();
        Collection<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return ("No items in order " + order.getDefaultDisplayName());
        Name allProducts = nameService.findByName(azquoMemoryDBConnection,ALL_PRODUCTS);
        Name saleDate = nameService.findByName(azquoMemoryDBConnection, SALE_DATE);
        Name service = nameService.findByName(azquoMemoryDBConnection,SERVICE);
        Set<Name> productItems = new HashSet<Name>();
        //Set<Name> serviceItems = new HashSet<Name>();
        for (Name name:orderItems){
            //ASSUMING THAT 'service' HAS ONLY ONE LEVEL OF CHILDREN!
            if (service==null || !service.getChildren().contains(name)){
                productItems.add(name);
            }
        }
        if (velocityTemplate==null){
            velocityTemplate="email.vm";
        }
        StringBuilder saledesc = new StringBuilder();
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
         context.put("supplierlogo",thisSite + "/Image/?supplierdb=" + azquoMemoryDBConnection.getCurrentDBName() + "&image=MerchantDetails/" + supplier.getAttribute(SUPPLIER_ATTRIBUTE.LOGO));
        context.put("saledate", showDate(orderSaleDate));
        context.put("customername", order.getAttribute(ORDER_ATTRIBUTE.CUSTOMER_NAME));
        context.put("saledescription", saledesc.toString());
        context.put("supplier", supplier.getDefaultDisplayName());
        context.put("feedbacklink", thisURL + "?op=reviewform&supplierdb=" + azquoMemoryDBConnection.getCurrentDBName() + "&orderref=" + order.getDefaultDisplayName() + "&businessid=" + azquoMemoryDBConnection.getBusinessId() + "&velocitytemplate=form2.vm");
        context.put("reviewslink", "http://www.azquoreviews.com/reviews/?op=showreviews&supplierdb=" + azquoMemoryDBConnection.getCurrentDBName() );
        String result = convertToVelocity(context,"", null, velocityTemplate);
        if (!order.getAttribute(ORDER_ATTRIBUTE.CUSTOMER_EMAIL).equals("demo@azquo.com")){
            azquoMailer.sendEMail(order.getAttribute(ORDER_ATTRIBUTE.CUSTOMER_EMAIL), order.getAttribute(ORDER_ATTRIBUTE.CUSTOMER_NAME),"Feedback request on behalf of " + supplier.getDefaultDisplayName(), result);
        }
        return "";


    }




    public String todayString(){
        return df.format(new Date());

    }



    public Name getParent(Name child, Name parent){

        Set<Name> parentName = new HashSet<Name>();
        parentName.addAll(child.getParents());
        parentName.retainAll(parent.findAllChildren(true));
        if (parentName.size()==0){
            return null;
        }
        if (parentName.size() > 0) {
            return parentName.iterator().next();
        }
        return null;


    }

    private String getValueFromParent(Name child, Name parent){
        Name name = getParent(child,parent);
        if (name == null) return "";
        return name.getDefaultDisplayName();
    }

    private Name todayName(AzquoMemoryDBConnection azquoMemoryDBConnection)throws Exception{
        String todayString = df2.format(new Date());
        return nameService.findOrCreateNameInParent(azquoMemoryDBConnection,todayString,null,false);//dates should not be local!


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
        public String productcode;
        public String comment;
        public String supplierComment;
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

        public String getProductCode() {
            return productcode;
        }

        public String getProduct() {
            return product;
        }
        public String getComment() {
            return comment;
        }
        public String getSupplierComment() { return supplierComment;}

        public String getCommentName() {
            return commentName;
        }

        public String getFormattedDate() {
            return formattedDate;
        }
        public String getDate() {
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
            toReturn.put("comment", comment.replace("\n","<br>"));
            toReturn.put("commentName", commentName);
            toReturn.put("productcode", productcode);
            toReturn.put("suppliercomment", supplierComment);
            toReturn.put("date",date);
            toReturn.put("type", type);
            return toReturn;
        }
    }

    VelocityReview getVelocityReview(Name orderItem, Name rating, Name product) {

        VelocityReview vr = new VelocityReview();

        vr.rating = getValueFromParent(orderItem, rating);
        Name thisProd = getParent(orderItem, product);
        vr.product = thisProd.getDefaultDisplayName();
        vr.productcode = thisProd.getAttribute((PRODUCT_ATTRIBUTE.PRODUCT_CODE));
        String comment = orderItem.getAttribute(ORDER_ITEM_ATTRIBUTE.COMMENT);
        if (comment == null){
            comment = "";
        }
        int supplierCommentPos = comment.indexOf("|Supplier:");
        if (supplierCommentPos >= 0){
            vr.supplierComment = comment.substring(supplierCommentPos + 10);
            comment = comment.substring(0,supplierCommentPos);
        }
        vr.comment = comment;
        vr.formattedDate = showDate(orderItem.getAttribute(ORDER_ITEM_ATTRIBUTE.REVIEW_DATE));
        vr.date = orderItem.getAttribute(ORDER_ITEM_ATTRIBUTE.REVIEW_DATE);

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


    public String showReviews(String supplierDb, String division, String startDate, String reviewType, String velocityTemplate) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDbConnection = reviewsConnectionMap.getConnection(supplierDb);

        // todo handle error if the supplier db is duff

        boolean XML = true;
        if (velocityTemplate !=null){
            XML = false;
        }
        if (reviewType == null){
            reviewType = "S";
        }
        List<Name> orderItems = new ArrayList<Name>();
        Map<String, String> context = new HashMap<String, String>();
        List<VelocityReview> reviews = new ArrayList<VelocityReview>();
        Name supplier = nameService.findByName(azquoMemoryDbConnection, SUPPLIER);
        Name topSupplier = supplier.getChildren().iterator().next();
        String supplierName = topSupplier.getDefaultDisplayName();
        if (division == null || division.length()==0){
            division = supplierName;
        }
        try{
            if (startDate == null){
                orderItems = nameService.parseQuery(azquoMemoryDbConnection, "`"+division + "`;level lowest * `All ratings`;level lowest");
            }else{
                orderItems = nameService.parseQuery(azquoMemoryDbConnection, "`"+division + "`;level lowest;WHERE .`Review date` >= \"" + startDate + "\" * order;level lowest * `All ratings`;level lowest");
            }

        } catch (Exception e){
            return "error:" + e.getMessage();
        }
        Name rating = nameService.findByName(azquoMemoryDbConnection, ALL_RATINGS);
        Name product = nameService.findByName(azquoMemoryDbConnection, ALL_PRODUCTS);
        int posCount = 0;
        int reviewCount = 0;
        for (Name orderItem : orderItems) {

            VelocityReview vr = getVelocityReview(orderItem, rating, product);
            if (vr.comment.length()==0){
                vr.comment = "No comment";
            }
             if ((reviewType.equals("S") && vr.productcode.equals("S")) || (!reviewType.equals("S") && !vr.productcode.equals("S"))){
                reviewCount++;
                if (vr.rating.equals("4") || vr.rating.equals("5")) {
                    posCount++;
                }
                reviews.add(vr);
            }


        }
        context.put("reviewcount", reviewCount + "");
        context.put("suppliername", supplierName);
        context.put("division", division);

         if (reviewCount > 0) {
             context.put("overallrating", (posCount * 100 / reviewCount) + "");
             if (XML) {

                  return convertToXML(context, reviews);



            }else {
                 if (velocityTemplate.length()==0){
                     velocityTemplate = "showreviews.vm";
                 }
                return convertToVelocity(context,"reviews", reviews, velocityTemplate);
                }
        } else {
            return "no reviews found";
        }
    }


    private TransformerHandler initHd(StringWriter sw)throws Exception{
        StreamResult streamResult = new StreamResult(sw);
         SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler hd = tf.newTransformerHandler();
        Transformer serializer = hd.getTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        hd.setResult(streamResult);
        hd.startDocument();
         return hd;

    }

    private String convertToXML(Map<String, String> context, List<VelocityReview> items)throws Exception{

        StringWriter sw = new StringWriter();
        org.xml.sax.helpers.AttributesImpl atts = new org.xml.sax.helpers.AttributesImpl();
        TransformerHandler hd = initHd(sw);
        hd.startElement("","","AZQUO",atts);
        addXmlElements(hd, atts, context);
        for (VelocityReview item:items){
            hd.startElement("","","review", atts);
            addXmlElements(hd,atts, item.toStringMap());
            hd.endElement("","","review");
        }
        hd.endElement("","","AZQUO");
        hd.endDocument();
        return sw.toString();
    }


    public String createReviewForm(HttpServletRequest request,String supplierDb, String orderRef, String velocityTemplate)throws Exception{

        AzquoMemoryDBConnection azquoMemoryDBConnection = reviewsConnectionMap.getConnection(supplierDb);

        // todo handle error if the supplier db is duff
        boolean XML = true;
        if (velocityTemplate!=null) XML = false;
        Map<String, String> context = new HashMap<String, String>();
        List<VelocityReview> reviews = new ArrayList<VelocityReview>();


        Name order = nameService.findByName(azquoMemoryDBConnection, orderRef + ", Order");
        if (order==null){
            return "error: unrecognised order ref " + orderRef;
        }
        Collection<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return "error: No items in order " + order.getDefaultDisplayName();
        Name service = nameService.findByName(azquoMemoryDBConnection,"Service");
        Name topSupplier = nameService.findByName(azquoMemoryDBConnection,"supplier");
        Name supplier = topSupplier.getChildren().iterator().next();
        String intro = "<p>Thank you for taking the time to leave feedback for " + supplier.getDefaultDisplayName() + ".</p><p>Please note that your feedback is PUBLIC and will be displayed innediately on our site</p>";
        if (supplier.getAttribute("feedbackintrotext") != null){
            intro = supplier.getAttribute("feedbackintrotext");
        }

        StringBuilder validationScript = new StringBuilder();
        validationScript.append(" var frmvalidator  = new Validator(\"review\");\n");
        String thisURL = request.getRequestURL().toString();
        String thisSite = thisURL.substring(0,thisURL.lastIndexOf("/",thisURL.length() -2));
        context.put("supplierlogo",thisSite + "/Image/?supplierdb=" + azquoMemoryDBConnection.getCurrentDBName() + "&image=MerchantDetails/" + supplier.getAttribute("logo"));
        context.put("suppliername", supplier.getDefaultDisplayName());
        context.put("intro", intro);
        context.put("thisurl",thisURL);
        // nope, can't have a connection id any more
        //context.put("connectionid", azquoMemoryDBConnection.getConnectionId());
        context.put("submit", "Submit");
        Name rating = nameService.findByName(azquoMemoryDBConnection, "All ratings");
        Name product = nameService.findByName(azquoMemoryDBConnection, "All Products");
        for (Name orderItem:orderItems){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ")+1);
            VelocityReview vr = getVelocityReview(orderItem, rating, product);
            if (service != null && service.getChildren().contains(orderItem)) {
                // todo, these from public static finals
                vr.type = "service";
                 validationScript.append("frmvalidator.addValidation(\"comment" + productCode + "\",\"req\",\"Please enter a comment for " + vr.product + "\");\n");
            }else{
                vr.type = "product";
            }
            vr.ratingName =  "rating" + productCode;
            vr.commentName = "comment" + productCode;
            validationScript.append("frmvalidator.addValidation(\"rating" + productCode + "\",\"gt=0\",\"Please enter a rating for " + vr.product + "\");\n");
            validationScript.append("frmvalidator.EnableOnPageErrorDisplaySingleBox();\n");
            validationScript.append("frmvalidator.EnableMsgsTogether();\n");

            reviews.add(vr);


        }
        context.put("validationscript", validationScript.toString());
        if (XML){
            return convertToXML(context, reviews);
        }else{
            return convertToVelocity(context,"reviews", reviews, velocityTemplate);
        }





    }



    public String processReviewForm(String supplierDb, String orderRef, Map<String, String> ratings, Map<String, String> comments)throws Exception{
        AzquoMemoryDBConnection azquoMemoryDBConnection = reviewsConnectionMap.getConnection(supplierDb);

        // todo handle error if the supplier db is duff

        Name order = nameService.findByName(azquoMemoryDBConnection, orderRef + ", Order");

        for (Name orderItem: order.getChildren()){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ") + 1);
            String feedbackDate = df.format(new Date());
            String rating = ratings.get(productCode);
            Name ratingSet = null;
            if (rating != null){
                ratingSet = nameService.findByName(azquoMemoryDBConnection,rating + ", Rating");
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
            if (ratingSet != null) {
                //store rating with date, product, rating value
                azquoMemoryDBConnection.setNewProvenance("feedback form","reviews system","");
                Provenance provenance = azquoMemoryDBConnection.getProvenance();
                Set<Name> valueNames = new HashSet<Name>();
                Name product = nameService.findByName(azquoMemoryDBConnection, "All products");
                Collection<Name> itemParents = orderItem.findAllParents();
                itemParents.retainAll(product.getChildren());
                Name countName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"count",null,false);
                valueNames.add(countName);
                valueNames.addAll(itemParents);//should be only one!
                valueNames.add(ratingSet);
                valueNames.add(todayName(azquoMemoryDBConnection));
                List<Value> vlist = valueService.findForNames(valueNames);
                Value v = null;
                if (vlist == null){
                    v = vlist.get(0);
                    valueService.overWriteExistingValue(azquoMemoryDBConnection,v,Integer.parseInt(v.getText() + 1) +"");//increase value by 1
                }else{
                    valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection,"1",valueNames);
                }

            }
        }

        azquoMemoryDBConnection.persist();




        return "";
    }

    private void addVelocityElements(VelocityContext context, Map<String,String>items){
        for (String key:items.keySet()){
            String value = items.get(key);
            if (value==null) value="";
            context.put(key, value);
        }
    }

    private String convertToVelocity(Map<String,String> context, String itemName, List<VelocityReview> items, String velocityTemplate){

        VelocityEngine ve = new VelocityEngine();
        Properties properties = new Properties();
        Template t;
        if (velocityTemplate == null){
            velocityTemplate = "email.vm";
        }

        if ((velocityTemplate.startsWith("http://") || velocityTemplate.startsWith("https://")) && velocityTemplate.indexOf("/", 8) != -1) {
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



    public String createSupplierResponseForm(LoggedInConnection loggedInConnection, String orderRef, String velocityTemplate)throws Exception{
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
        Collection<Name> orderItems = order.getChildren();
        if (orderItems.size() == 0) return "error: No items in order " + order.getDefaultDisplayName();
        // so just not using? I'll comment
        //Name allProducts = nameService.findByName(azquoMemoryDbConnection,"All products");
        // interesting
        Name service = nameService.findByName(loggedInConnection,"Service");
        Name topSupplier = nameService.findByName(loggedInConnection,"supplier");
        /*Name supplier = null;
        for (Name name:topSupplier.getChildren()){
            supplier = name;
            break;
        }*/
        Name supplier = topSupplier.getChildren().iterator().next();

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
            return convertToXML(context, reviews);
        }else{
            return convertToVelocity(context,"reviews", reviews, velocityTemplate);
        }
    }

    public String processSupplierResponseForm(LoggedInConnection loggedInConnection, String orderRef, Map<String, String> comments)throws Exception{

        Name order = nameService.findByName(loggedInConnection, orderRef + ", Order");

        for (Name orderItem: order.getChildren()){
            String orderItemName = orderItem.getDefaultDisplayName();
            String productCode = orderItemName.substring(orderItemName.lastIndexOf(" ") + 1);
            String comment = comments.get(productCode);
            if (comment != null && orderItem.getAttribute("comment") != null){
                orderItem.setAttributeWillBePersisted("comment", orderItem.getAttribute("comment") + "|Supplier:" + comment);
            }
        }

        loggedInConnection.persist();
        return "";
    }

    private void addElement(TransformerHandler hd, AttributesImpl atts, String type, String value)throws Exception{
        hd.startElement("","",type, atts);
        hd.characters(value.toCharArray(), 0, value.length());
        hd.endElement("","",type);

    }

    public void setToXML(TransformerHandler hd, org.xml.sax.helpers.AttributesImpl atts, Name set, String itemname)        throws Exception {
         addElement(hd,atts, "NAME", set.getDefaultDisplayName());
        String link = set.getAttribute("link");
        if (link == null){
            if (set.getChildren().size()> 0) {
                hd.startElement("", "", itemname, atts);
                for (Name child : set.getChildren()) {
                    hd.startElement("", "", "ITEM", atts);
                    setToXML(hd, atts, child, itemname);
                    hd.endElement("", "", "ITEM");
                }
                hd.endElement("", "", itemname);
            }
        }else{
            addElement(hd, atts, "LINK", link);

        }
    }




    public String createPageSpec(String itemName, String type, String itemNo)throws Exception{
        /// either type or itemNo, not both.

        //first create the menu,
        int itemToEdit = -1;
        if (itemNo!=null){
            try{
                itemToEdit = Integer.parseInt(itemNo);
                type="form";
            }catch(Exception e){
                type="list";
            }
        }


        AzquoMemoryDBConnection masterDBConnection = reviewsConnectionMap.getConnection(UserService.MASTERDBNAME);

        Name business = getUserBusiness();





        Name menu = nameService.findByName(masterDBConnection,"Main menu");
        StringWriter sw = new StringWriter();
        org.xml.sax.helpers.AttributesImpl atts = new org.xml.sax.helpers.AttributesImpl();
        TransformerHandler hd = initHd(sw);
        hd.startElement("","","AZQUO",atts);
        setToXML(hd, atts, menu, "MENU");
         //then find the requirements for the page
        Name menuItem = nameService.findByName(masterDBConnection,itemName);
        String choiceSet = menuItem.getAttribute("Choice set");
        boolean thisIsASetItem = true;
       if (choiceSet==null){
           thisIsASetItem = false;
           choiceSet = itemName;
       }
        boolean structured = false;
        if (choiceSet.indexOf("structured") > 0 && choiceSet.indexOf(",")>0){
            structured = true;
            choiceSet = choiceSet.substring(0, choiceSet.indexOf(","));//assumes that the comma is before 'structured'!
        }
        Name editset = nameService.findByName(masterDBConnection, choiceSet);
        if (menuItem!=null){
             addElement(hd, atts, "TYPE", type);

            if (type.equals("list")){
                 if (choiceSet != null) {
                    String listToShow = "`" + choiceSet + "`;children * `" + business.getDefaultDisplayName() + "`;level all";
                    List<Name> namesFound = nameService.parseQuery(masterDBConnection, listToShow);
                    hd.startElement("","","HEADING",atts);
                    int fieldNo = 0;
                    for (Name fieldName : menuItem.getChildren()) {
                        if (fieldName.getAttribute("SummaryScreenItem") != null) {
                            hd.startElement("", "", "LINEITEM", atts);
                            String attribute = fieldName.getDefaultDisplayName();
                            addElement(hd, atts, "NAME", attribute);
                            hd.endElement("", "", "LINEITEM");
                        }

                    }
                    hd.endElement("","","HEADING");
                    hd.startElement("", "", "LIST", atts);
                    for (Name lineName : namesFound) {
                        hd.startElement("", "", "LISTITEM", atts);
                        hd.startElement("", "", "LINEITEM", atts);
                        addElement(hd, atts, "NAME", lineName.getDefaultDisplayName());
                        addElement(hd, atts, "LINK", "itemname=" + menuItem.getDefaultDisplayName().replace(" ","_") + "&nameid=" + lineName.getId());
                                hd.endElement("", "", "LINEITEM");
                        fieldNo = 0;
                        for (Name fieldName : menuItem.getChildren()) {
                               if (fieldNo++ > 0 && fieldName.getAttribute("SummaryScreenItem") != null) {
                                hd.startElement("", "", "LINEITEM", atts);
                                String attribute = lineName.getAttribute(fieldName.getDefaultDisplayName());
                                if (attribute == null) attribute = "";
                                addElement(hd, atts, "NAME", attribute);
                                hd.endElement("", "", "LINEITEM");
                            }
                        }

                        hd.endElement("", "", "LISTITEM");
                    }
                    hd.startElement("", "", "LISTITEM", atts);
                    hd.startElement("", "", "LINEITEM", atts);
                    addElement(hd, atts, "NAME", "NEW");
                    addElement(hd, atts, "LINK", "itemname=" + menuItem.getDefaultDisplayName().replace(" ","_") + "&nameid=0");
                    hd.endElement("", "", "LINEITEM");
                    hd.endElement("", "", "LISTITEM");

                    hd.endElement("", "", "LIST");
                }


            }
            StringBuilder validationScript = new StringBuilder();
            validationScript.append(" var frmvalidator  = new Validator(\"form\");\n");
            validationScript.append("frmvalidator.EnableOnPageErrorDisplaySingleBox();\n");
            validationScript.append("frmvalidator.EnableMsgsTogether();\n");
            if (type.equals("form")){
                Name nameToEdit = null;
                if (itemToEdit> 0){
                    nameToEdit = nameService.findById(masterDBConnection,itemToEdit);
                }else{
                    if (thisIsASetItem) {
                        Provenance provenance = masterDBConnection.getProvenance("in app");
                        nameToEdit = nameService.findOrCreateNameInParent(masterDBConnection, "new " + itemName, editset, true);
                        nameToEdit.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, "New name " + nameToEdit.getId());
                        if (structured) {
                            Name topName = null;
                            for (Name name : editset.getChildren()) {
                                if (topName == null) topName = name;
                                if (name.findAllParents().contains(topName)) {
                                    topName = name;
                                }
                            }
                            topName.addChildWillBePersisted(nameToEdit);


                        }
                    }
                }
                int tabno = 0;
                int fieldNo = 0;
                String tabname = "";
                int debugmax = 500;//only here for debugging if XML causes problems

                for (Name fieldName:menuItem.getChildren()){
                    if (debugmax-- == 0) break;
                    if (!fieldName.getAttribute("tab").equals(tabname)) {
                        if (tabname.length() > 0) {
                            hd.endElement("", "", "TAB");
                        }
                        hd.startElement("","","TAB", atts);
                        tabname = fieldName.getAttribute("tab");
                        addElement(hd, atts,"TABNAME", tabname);
                        addElement(hd, atts, "TABNO", tabno++ + "");
                    }
                    hd.startElement("", "", "LISTITEM", atts);
                    String field = fieldName.getDefaultDisplayName();
                    addElement(hd,atts,"ITEMNO",++fieldNo + "");
                    addElement(hd, atts, "ITEMID", "item" + fieldNo);
                    addElement(hd,atts,"ITEMNAME",field);
                    String fieldType = fieldName.getAttribute("type").toLowerCase();
                    String clause = "";
                    int nameEnd = fieldType.indexOf(" ");
                    if (nameEnd > 0){
                        clause = fieldType.substring(nameEnd + 1);
                        fieldType = fieldType.substring(0,nameEnd);

                    }
                    if (clause.contains("[business]")){
                        clause = clause.replace("[business]", "`" + business.getDefaultDisplayName() + "`");
                    }
                    if (fieldType.equals("boolean")|| fieldType.equals("select") || fieldType.equals("file")){
                        addElement(hd,atts,"ITEMTYPE", fieldType);
                    }else{
                        addElement(hd,atts,"ITEMTYPE","text");
                    }
                    if (fieldType.equals("select")){
                        String fieldVal = nameToEdit.getAttribute(field);
                        hd.startElement("", "", "OPTIONS", atts);
                        String options = fieldName.getAttribute("options");
                        if (options != null){
                            String[] option = options.split(",");
                            for (String opt:option){
                                addElement(hd,atts,"OPTION", opt);
                            }

                        }else {
                            try {
                                List<Name> selectName = nameService.parseQuery(masterDBConnection, clause);
                                if (selectName != null) {
                                    //hd.startElement("", "", "SELECT", atts);
                                    for (Name child : selectName) {
                                        addElement(hd, atts, "OPTION", child.getDefaultDisplayName());
                                    }
                                    //hd.endElement("","","SELECT");
                                }
                            }catch(Exception e){
                                return e.getMessage();
                            }
                          }
                        hd.endElement("", "", "OPTIONS");
                    }
                    String fieldVal = null;
                    if (thisIsASetItem) {
                        if (fieldNo == 1) {
                            addElement(hd, atts, "VALUE", nameToEdit.getDefaultDisplayName());

                        } else {
                            fieldVal = nameToEdit.getAttribute(field);
                            if (fieldVal == null) {
                                fieldVal = fieldName.getAttribute("default");
                            }
                            if (fieldVal != null) {
                                addElement(hd, atts, "VALUE", fieldVal);
                            }
                        }
                    }

                    String fieldValidation = fieldName.getAttribute("validation");
                    if (fieldType.equals("file") && fieldVal != null && fieldVal.length() > 0){
                        fieldValidation = null;
                    }
                    while (fieldValidation != null && fieldValidation.length() > 0){
                        String thisTest = "";
                        if (fieldValidation.contains(";")){
                            thisTest = fieldValidation.substring(0, fieldValidation.indexOf(";"));
                            fieldValidation = fieldValidation.substring(thisTest.length() + 1);
                        }else{
                            thisTest = fieldValidation;
                            fieldValidation = "";
                        }
                        if (thisTest.equals("req") && thisTest.indexOf(",") < 0){
                            thisTest = thisTest + ",Please enter " + field;
                        }

                        validationScript.append("frmvalidator.addValidation(\"field" + fieldNo + "\",\"" + thisTest.replace(",","\",\"") + "\");\n");

                    }
                    String help = fieldName.getAttribute("help");
                    if (help != null){
                        addElement(hd,atts,"HELP", help);
                    }

                    hd.endElement("","","LISTITEM");
                }
                if (tabno > 0) {
                    hd.endElement("", "", "TAB");
                }

            }
            addElement(hd, atts, "JAVASCRIPTVALIDATION", validationScript.toString());
        }

        hd.endElement("","","AZQUO");

        hd.endDocument();
        return sw.toString();




    }


    public void download(HttpServletResponse response, String itemName, int nameId, String fieldNameStr) throws Exception{


        AzquoMemoryDBConnection masterDBConnection = reviewsConnectionMap.getConnection(UserService.MASTERDBNAME);
        Name menuItem = nameService.findByName(masterDBConnection, itemName);

        List<Name> fieldNames = nameService.parseQuery(masterDBConnection, "`" + itemName + "`;children;from " + fieldNameStr + ";to " + fieldNameStr);
        Name fieldName = fieldNames.iterator().next();
        Name nameToEdit = nameService.findById(masterDBConnection, nameId);
        String uploadDir = menuItem.getAttribute("uploaddir");
        if (uploadDir == null){
            uploadDir = menuItem.getDefaultDisplayName().replace(" ","");
        }
        Name azquoCustomers = nameService.findByName(masterDBConnection,ReviewsCustomerService.REVIEWS_CUSTOMER);
        Collection<Name> topMerchants = nameToEdit.findAllParents();
        topMerchants.add(nameToEdit);
        topMerchants.retainAll(azquoCustomers.getChildren());
        Name topMerchant = topMerchants.iterator().next();//should be only one!

        String dbName = topMerchant.getAttribute("dbname");
        if (dbName==null){
            dbName = "revie_" + topMerchant.getDefaultDisplayName().replace(" ","");
        }

        String fName = nameToEdit.getAttribute(fieldName.getDefaultDisplayName());
        if (fName == null){
            fName = fieldName.getAttribute("default");
            dbName = "revie_default";
            if (fName == null){
                return;
            }
        }
        String fullPath = "";
        if (fName.contains("/")){
            fullPath = onlineService.getHomeDir() + ImportService.dbPath + fName;
        }else {
            fullPath = onlineService.getHomeDir() + ImportService.dbPath + dbName + "/" + uploadDir + "/" + fName;
        }
        InputStream input = new BufferedInputStream((new FileInputStream(fullPath)));
        response.setContentType("application/force-download"); // Set up mime type
        OutputStream out = response.getOutputStream();
        byte[] bucket = new byte[32*1024];
        int length = 0;
        try  {
            try {
                //Use buffering? No. Buffering avoids costly access to disk or network;
                //buffering to an in-memory stream makes no sense.
                int bytesRead = 0;
                while(bytesRead != -1){
                    //aInput.read() returns -1, 0, or more :
                    bytesRead = input.read(bucket);
                    if(bytesRead > 0){
                        out.write(bucket, 0, bytesRead);
                        length += bytesRead;
                    }
                }
            }
            finally {
                input.close();
                //result.close(); this is a no-operation for ByteArrayOutputStream
            }
            response.setHeader("Content-Disposition", "attachment;filename=\"" +fName + "\"");
            response.setHeader("Content-Length", String.valueOf(length));
            out.flush();
            return;
        }catch(Exception e){
            throw e;
        }



    }

    public void saveData(HttpServletResponse response, String itemName, Map<Integer, String>  values, int itemToEdit)throws  Exception{
        AzquoMemoryDBConnection masterDBConnection = reviewsConnectionMap.getConnection(UserService.MASTERDBNAME);

        Name nameToEdit = null;
        boolean thisIsASetItem = true;
        if (itemToEdit >= 0){
            nameToEdit = nameService.findById(masterDBConnection,itemToEdit);
        }else{
            nameToEdit = nameService.findByName(masterDBConnection,itemName);
            thisIsASetItem = false;
        }

        int fieldNo = 1;
        Name menuItem = nameService.findByName(masterDBConnection,itemName);
        Name business = getUserBusiness();
        if (thisIsASetItem && itemToEdit == 0){
            Name choiceSet = nameService.findByName(masterDBConnection, menuItem.getAttribute("choice set"));
            nameToEdit = nameService.findOrCreateNameInParent(masterDBConnection, values.get(1), choiceSet, true);
            business.addChildWillBePersisted(nameToEdit);//everything connected with the business is stored under it
        }
        for (Name fieldName:menuItem.getChildren()){
            String uploadDir = menuItem.getAttribute("uploaddir");
            if (uploadDir == null){
                uploadDir = menuItem.getDefaultDisplayName().replace(" ","");
            }

            if (thisIsASetItem && fieldNo == 1){
                 nameToEdit.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, values.get(fieldNo));

            }else{
                String field = fieldName.getDefaultDisplayName();
                String oldVal = nameToEdit.getAttribute(field);
                if (oldVal==null) oldVal = "";
                String newVal = values.get(fieldNo);
                if (newVal==null){
                    newVal = "";
                }
                String fieldType = fieldName.getAttribute("type");
                if (fieldType.toLowerCase().startsWith("file")&& (values.get(fieldNo)==null || values.get(fieldNo).length()==0)){//no new file has been uploaded
                        newVal = oldVal;
                }

                if (!oldVal.equals(newVal)) {
                    nameToEdit.setAttributeWillBePersisted(field,"");//zap first, to discover if new value is identical to parent value;
                    oldVal = nameToEdit.getAttribute(field);
                    if (oldVal==null || !oldVal.equals(newVal)) {
                        if (fieldType.toLowerCase().startsWith("file") && values.get(fieldNo).length() > 0) {
                            String subDir = "";
                            if (fieldType.length() > 5){
                                subDir = fieldType.substring(5);
                                if (!subDir.endsWith("/")){
                                    subDir += "/";
                                }
                            }
                            String fileName = values.get(fieldNo);
                            String dbName = getBusinessDBPath(business);
                            String fName = fileName.substring(fileName.lastIndexOf("_") + 1);
                            if (fileName != null) {
                                URL input = new URL(fileName);
                                if (itemName.equals("upload")){//uploading review data NOTE THAT THIS IS SPECIFIC
                                    String supplierDb = getBusinessDBPath(business);
                                    AzquoMemoryDBConnection azquoMemoryDBConnection = reviewsConnectionMap.getConnection(supplierDb);
                                    InputStream loadFile = input.openStream();
                                    importService.importTheFile(azquoMemoryDBConnection,fName, loadFile);
                                }else{
                                       String fullPath = onlineService.getHomeDir() + ImportService.dbPath + dbName + "/" + uploadDir + subDir + "/" + URLEncoder.encode(fName);
                                       File output = new File(fullPath);
                                       FileUtils.copyURLToFile(input, output, 5000, 5000);
                                       nameToEdit.setAttributeWillBePersisted(field, fName);
                                }


                            }

                        } else {
                            nameToEdit.setAttributeWillBePersisted(fieldName.getDefaultDisplayName(), values.get(fieldNo));
                        }
                    }
                }
            }
            fieldNo++;

        }
        masterDBConnection.persist();
        String supplierDb = getBusinessDBPath(business);
        AzquoMemoryDBConnection azquoMemoryDBConnection = reviewsConnectionMap.getConnection(supplierDb);
        List<String> languages = new ArrayList<String>();
        languages.add(Name.DEFAULT_DISPLAY_NAME);
        Map<Name, Name> dictionary = new HashMap<Name,Name>();
        adminService.copyName(azquoMemoryDBConnection,business,null,languages, null, dictionary);


    }

    private String getBusinessDBPath(Name business) throws Exception{
        String dbName = business.getAttribute("dbname");
        if (dbName == null) {
            dbName = "revie_" + business.getDefaultDisplayName().replace(" ", "");
        }
        return dbName;


    }

    private Name getUserBusiness()throws Exception{
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Name user = userService.getUserByEmail(auth.getName());
        //user = userService.getUserByEmail("testuser5");
        return reviewsCustomerService.getReviewsCustomerForUser(user);

    }

    public int reviewsId(){
        return businessDAO.findByName(REVIEWS).getId();
    }



    public void respond() throws Exception{

        int reviewsId = reviewsId();
        Name userBusiness = getUserBusiness();
        String supplierDb = getBusinessDBPath(userBusiness);
       // User reviewsApp =

       //LoggedInConnection loggedInConnection = login(getBusinessDBPath(userBusiness), final User user, final int timeOutInMinutes, String spreadsheetName) throws  Exception{



        //OnlineReport responseSheet = onlineReportDAO.findForBusinessIdandName(reviewsId,RESPONSESHEET);
        //LoggedInConnection loggedInConnection =   new LoggedInConnection(System.nanoTime() + "", azquoMemoryDBConnection.getAzquoMemoryDB(), user, timeOutInMinutes * 60 * 1000, spreadsheetName);



    }

}
