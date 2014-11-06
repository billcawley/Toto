package com.azquo.app.reviews.service;

import com.azquo.memorydb.Name;
import com.azquo.service.AdminService;
import com.azquo.service.AppDBConnectionMap;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;

/**
 * Created by cawley on 23/10/14.
 */
public class UserService {

    public static final String MASTERDBNAME = "revie_master";
    public static final String USER = "user";

    private NameService nameService;

    private AdminService adminService;

    private AppDBConnectionMap reviewsConnectionMap;

    AzquoMemoryDBConnection masterDBConnection;

    private Name userSet = null;

    public UserService(NameService nameService, AdminService adminService, AppDBConnectionMap reviewsConnectionMap) throws Exception{
        this.nameService = nameService;
        this.adminService = adminService;
        this.reviewsConnectionMap = reviewsConnectionMap;
        masterDBConnection = reviewsConnectionMap.getConnection(MASTERDBNAME);
        userSet = nameService.findOrCreateNameInParent(masterDBConnection, USER, null, false);
    }

    public interface USER_ATTRIBUTE {
        String EMAIL = "EMAIL";
        String PASSWORD = "PASSWORD";
        String SALT = "SALT";
    }

    public String createUser(Name reviewsCustomer, String email, String password) throws Exception{
        Name exists = nameService.getNameByAttribute(masterDBConnection, email, userSet);
        if (exists != null){
            return "error:a user with that name already exists";
        }
        Name newUser = nameService.findOrCreateNameInParent(masterDBConnection, email, userSet, true);

        String salt = System.currentTimeMillis() + "salt";
        String encryptedPassword = adminService.encrypt(password, salt);
        newUser.setAttributeWillBePersisted(USER_ATTRIBUTE.EMAIL, email);
        newUser.setAttributeWillBePersisted(USER_ATTRIBUTE.PASSWORD, encryptedPassword);
        newUser.setAttributeWillBePersisted(USER_ATTRIBUTE.SALT, salt);
        reviewsCustomer.addChildWillBePersisted(newUser);
        masterDBConnection.persist();
        return "";
    }

    public Name getUserByEmail(String email) throws Exception{
        return nameService.getNameByAttribute(masterDBConnection, email, userSet);
    }

    public Name checkEmailAndPassword(String email, String password) throws Exception{
        Name user = getUserByEmail(email);
        if (user != null && adminService.encrypt(password, user.getAttribute(USER_ATTRIBUTE.SALT)).equals(user.getAttribute(USER_ATTRIBUTE.PASSWORD))){
            return user;
        }
        return null;
    }

}
