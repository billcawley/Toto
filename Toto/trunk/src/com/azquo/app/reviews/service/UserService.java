package com.azquo.app.reviews.service;

import com.azquo.memorydb.Name;
import com.azquo.service.AdminService;
import com.azquo.service.AppDBConnectionMap;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by cawley on 23/10/14.
 *
 */
public class UserService {

    public static final String MASTERDBNAME = "revie_master";
    public static final String USER = "user";

    private NameService nameService;

    private AdminService adminService;


    AzquoMemoryDBConnection masterDBConnection;

    private Name userSet = null;

    public UserService(NameService nameService, AdminService adminService, AppDBConnectionMap reviewsConnectionMap) throws Exception{
        this.nameService = nameService;
        this.adminService = adminService;
        if (reviewsConnectionMap.getConnection(MASTERDBNAME) == null){ // should only happen once!
            reviewsConnectionMap.newDatabase("master"); // note, I assume the main reviews business is called reviews!
        }
        masterDBConnection = reviewsConnectionMap.getConnection(MASTERDBNAME);
        userSet = nameService.findOrCreateNameInParent(masterDBConnection, USER, null, false);
    }

    public interface USER_ATTRIBUTE {
        String EMAIL = "EMAIL";
        String PASSWORD = "PASSWORD";
        String SALT = "SALT";
    }

    public String createUser(String reviewsCustomer, String email, String password) throws Exception{
        Name exists = getUserByEmail(email);
        if (exists != null){
            return "error:a user with that name already exists";
        }
        Name newUser = nameService.findOrCreateNameInParent(masterDBConnection, email, userSet, true);

        String salt = System.currentTimeMillis() + "salt";
        String encryptedPassword = adminService.encrypt(password, salt);
        newUser.setAttributeWillBePersisted(USER_ATTRIBUTE.EMAIL, email);
        newUser.setAttributeWillBePersisted(USER_ATTRIBUTE.PASSWORD, encryptedPassword);
        newUser.setAttributeWillBePersisted(USER_ATTRIBUTE.SALT, salt);
        Name merchant = nameService.findByName(masterDBConnection,reviewsCustomer);
        merchant.addChildWillBePersisted(newUser);
        masterDBConnection.persist();
        return "";
    }

    public Name getUserByEmail(String email) throws Exception{
        List<String> languages = new ArrayList<String>();
        languages.add(Name.DEFAULT_DISPLAY_NAME);
        return nameService.getNameByAttribute(masterDBConnection, email, userSet, languages);
    }

    public Name checkEmailAndPassword(String email, String password) throws Exception{
        Name user = getUserByEmail(email);
        if (user != null && adminService.encrypt(password, user.getAttribute(USER_ATTRIBUTE.SALT)).equals(user.getAttribute(USER_ATTRIBUTE.PASSWORD))){
            return user;
        }
        return null;
    }

}
