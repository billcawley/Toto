package com.azquo.toto.service;

import com.azquo.toto.admindao.BusinessDAO;
import com.azquo.toto.admindao.MySQLDatabaseManager;
import com.azquo.toto.admindao.UserDAO;
import com.azquo.toto.adminentities.Business;
import com.azquo.toto.adminentities.User;
import org.springframework.beans.factory.annotation.Autowired;
import sun.misc.BASE64Encoder;

import java.security.MessageDigest;
import java.util.Date;
import java.util.Formatter;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Used for admin functions. Register, validate
 */
public class BusinessService {

    @Autowired
    private BusinessDAO businessDao;
    @Autowired
    private UserDAO userDAO;
    @Autowired
    MySQLDatabaseManager mySQLDatabaseManager;

    public String registerBusiness(String email, String userName, String password, String businessName, String address1, String address2, String address3, String address4, String postcode, String telephone){
        String key = shaHash(System.currentTimeMillis() + "");
        Business.BusinessDetails bd = new Business.BusinessDetails(address1,address2,address3,address4,postcode, telephone,"website???", key);
        Business business = new Business(0,false,new Date(), businessName,0, bd);
        businessDao.store(business);
        String salt = shaHash(System.currentTimeMillis() + "salt");
        User user = new User(0, false, new Date(), email, userName, "registered", salt, encrypt(password, salt));
        userDAO.store(user);
        return key;
    }

    public boolean confirmKey(String businessName, String key){
        Business business = businessDao.findByName(businessName);
        if (business != null && business.getBusinessDetails().getValidationKey().equals(key)){
            business.setActive(true);
            businessDao.store(business);
        }
        return false;
    }

    //variation on a function I've used before

    /*                String salt = PasswordUtils.shaHash(System.currentTimeMillis() + "salt");
    v.setPasswordSalt(salt);
    v.setPassword(PasswordUtils.encrypt(password, salt)); // new better encryption . . .*/


    public static String encrypt(String password, String salt)
    {
        try {
            // WARNING! DO NOT MODIFY THE reference to "scapbadopbebedop"  bit in the code below or it will stop working! This is the extra bit on the salt or the number of hash cycles! stay at 3296
            salt += "scapbadopbebedop";
            byte[] passBytes = password.getBytes();
            byte[] saltBytes = salt.getBytes();
            int hashCycles = 3296;
            MessageDigest md = null;
            md = MessageDigest.getInstance("SHA");
            for (int i = 0; i < hashCycles; i++){
                // add the salt to the pass and encrypt, then add the salt to the result and encrypt. Repeat as necessary . . .
                byte[] combined = new byte[saltBytes.length + passBytes.length];
                for (int j = 0; j < combined.length; j++)
                {
                    combined[j] = j < saltBytes.length ? saltBytes[j] : passBytes[j - saltBytes.length];
                }
                md.update(combined);
                passBytes = md.digest();
            }
            String result = (new BASE64Encoder()).encode(passBytes);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "fail . . .";
    }

    public static String shaHash(String toHash){   // for making a password salt
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(toHash.getBytes());
            Formatter formatter = new Formatter();
            for (byte b : hash) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "fail . . .";
    }




}
