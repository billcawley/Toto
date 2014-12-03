package com.azquo.app.reviews.service;

import com.azquo.memorydb.Name;
import com.azquo.service.AppDBConnectionMap;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by cawley on 23/10/14.
 */
public class ReviewsCustomerService {

    public static final String MASTERDBNAME = "revie_master";
    public static final String REVIEWS_CUSTOMER = "REVIEWS_CUSTOMER";

    private NameService nameService;

    private AppDBConnectionMap reviewsConnectionMap;

    AzquoMemoryDBConnection masterDBConnection;

    Name reviewsCustomer;

    public ReviewsCustomerService(NameService nameService, AppDBConnectionMap reviewsConnectionMap) throws Exception{
        this.nameService = nameService;
        this.reviewsConnectionMap = reviewsConnectionMap;
        if (reviewsConnectionMap.getConnection(MASTERDBNAME) == null){ // should only happen once!
            reviewsConnectionMap.newDatabase("master"); // note, I assume the main reviews business is called reviews!
        }
        masterDBConnection = reviewsConnectionMap.getConnection(MASTERDBNAME);
        reviewsCustomer = nameService.findOrCreateNameInParent(masterDBConnection, REVIEWS_CUSTOMER, null, false);
    }

    public interface REVIEWS_CUSTOMER_ATTRIBUTE {
        String ADDRESS = "ADDRESS";
        String EMAIL = "EMAIL";
        String TELEPHONENO = "TELEPHONENO";
        String SUPPLIERMYSQLDB = "SUPPLIERMYSQLDB";
    }

    public String createReviewsCustomer(String name, String address, String email, String telephoneno) throws Exception{
        Name exists = nameService.getNameByAttribute(masterDBConnection, name, reviewsCustomer);
        if (exists != null){
            return "error:a reviews customer with that name already exists";
        }
        Name newReviewsCustomer = nameService.findOrCreateNameInParent(masterDBConnection, name, reviewsCustomer, true);
        newReviewsCustomer.setAttributeWillBePersisted(REVIEWS_CUSTOMER_ATTRIBUTE.ADDRESS, address);
        newReviewsCustomer.setAttributeWillBePersisted(REVIEWS_CUSTOMER_ATTRIBUTE.EMAIL, email);
        newReviewsCustomer.setAttributeWillBePersisted(REVIEWS_CUSTOMER_ATTRIBUTE.TELEPHONENO, telephoneno);
        masterDBConnection.persist();
        return "";
    }

    public Name getReviewsCustomerByName(String name) throws Exception{
        return nameService.getNameByAttribute(masterDBConnection, name, reviewsCustomer);
    }

    public Name getReviewsCustomerForUser(Name user) throws Exception{
        List<Name> userParents = user.findAllParents();
        userParents.retainAll(reviewsCustomer.getChildren());
        if (!userParents.isEmpty()){
            return userParents.iterator().next();
        }
        return null;
    }
}
