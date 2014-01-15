package com.azquo.toto.service;

import com.azquo.toto.admindao.*;
import com.azquo.toto.adminentities.Access;
import com.azquo.toto.adminentities.Business;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.adminentities.User;
import org.springframework.beans.factory.annotation.Autowired;
import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Formatter;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:31
 * Used for admin functions. Register, validate
 */
public class AdminService {

    @Autowired
    private BusinessDAO businessDao;
    @Autowired
    private DatabaseDAO databaseDao;
    @Autowired
    private UserDAO userDao;
    @Autowired
    private AccessDAO accessDao;
    @Autowired
    MySQLDatabaseManager mySQLDatabaseManager;

    public String registerBusiness(final String email, final String userName, final String password, final String businessName, final String address1
            , final String address2, final String address3, final String address4, final String postcode, final String telephone){
        final String key = shaHash(System.currentTimeMillis() + "");
        final Business.BusinessDetails bd = new Business.BusinessDetails(address1,address2,address3,address4,postcode, telephone,"website???", key);
        final Business business = new Business(0,new Date(),new Date(), businessName,0, bd);
        businessDao.store(business);
        final String salt = shaHash(System.currentTimeMillis() + "salt");
        final User user = new User(0, new Date(),new Date(),business.getId(), email, userName, "registered", encrypt(password, salt), salt);
        userDao.store(user);
        return key;
    }

    public boolean confirmKey(final String businessName, final String key){
        final Business business = businessDao.findByName(businessName);
        if (business != null && business.getBusinessDetails().validationKey.equals(key)){
            business.setEndDate(new Date(130,1,1));
            businessDao.store(business);
            User user = userDao.findForBusinessId(business.getId()).get(0);
            user.setEndDate(new Date(130,1,1));
            user.setStatus(User.STATUS_ADMINISTRATOR);
            userDao.store(user);
            return true;
        }
        return false;
    }

    public String getSQLDatabaseName(final LoggedInConnection loggedInConnection, final String databaseName) {
        Business b = businessDao.findById(loggedInConnection.getUser().getBusinessId());
        //TODO  Check name below is unique.
        String mysqlName = b.getBusinessName()+ "     ".substring(0,5).trim() + "_" + databaseName;
        return mysqlName.replaceAll("[^A-Za-z0-9_]", "");


    }

    public boolean createDatabase(final String databaseName, final LoggedInConnection loggedInConnection) throws IOException {

        if (loggedInConnection.getUser().isAdministrator()){
            final String mysqlName = getSQLDatabaseName(loggedInConnection,databaseName);
            final Business b = businessDao.findById(loggedInConnection.getUser().getBusinessId());
            final Database database = new Database(0,new Date(), new Date(130,1,1), b.getId(), databaseName,mysqlName,0,0);
            mySQLDatabaseManager.createNewDatabase(mysqlName);
            databaseDao.store(database);
            return true;

        }
        return false;
    }

    public boolean createUser(final String email, final String userName, final String status, final String password, final LoggedInConnection loggedInConnection) throws IOException {

        if (loggedInConnection.getUser().isAdministrator()){
            final String salt = shaHash(System.currentTimeMillis() + "salt");
            final User user = new User(0, new Date(), new Date(130,1,1),loggedInConnection.getUser().getBusinessId(), email, userName, status, encrypt(password, salt), salt);
            userDao.store(user);
            return true;
        }
        return false;
    }

    public boolean createUserAccess(final String email, final String readList, final String writeList, final LoggedInConnection loggedInConnection) throws IOException {
        if (loggedInConnection.getUser().isAdministrator() && loggedInConnection.getTotoMemoryDB() != null){ // actually have a DB selected
            final Access access = new Access(0, new Date(), new Date(130,1,1), userDao.findByEmail(email).getId(), loggedInConnection.getTotoMemoryDB().getDatabase().getId(), readList,writeList);
            accessDao.store(access);
            return true;
        }
        return false;
    }

    //variation on a function I've used before

    /*                String salt = PasswordUtils.shaHash(System.currentTimeMillis() + "salt");
    v.setPasswordSalt(salt);
    v.setPassword(PasswordUtils.encrypt(password, salt)); // new better encryption . . .*/


    public static String encrypt(final String password, String salt)
    {
        try {
            // WARNING! DO NOT MODIFY THE reference to "scapbadopbebedop"  bit in the code below or it will stop working! This is the extra bit on the salt or the number of hash cycles! stay at 3296
            salt += "scapbadopbebedop";
            byte[] passBytes = password.getBytes();
            byte[] saltBytes = salt.getBytes();
            int hashCycles = 3296;
            MessageDigest md = MessageDigest.getInstance("SHA");
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
            return (new BASE64Encoder()).encode(passBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "fail . . .";
    }

    public static String shaHash(final String toHash){   // for making a password salt
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


    public List<Database> getDatabaseListForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()){
            return databaseDao.findForBusinessId(loggedInConnection.getUser().getBusinessId());
        }
        return null;
    }

    public List<User> getUserListForBusiness(final LoggedInConnection loggedInConnection) {
        if (loggedInConnection.getUser().isAdministrator()){
            return userDao.findForBusinessId(loggedInConnection.getUser().getBusinessId());
        }
        return null;
    }

    public List<Access> getAccessList(final LoggedInConnection loggedInConnection, String userEmail) {
        if (loggedInConnection.getUser().isAdministrator()){
            User user = userDao.findByEmail(userEmail);
            if (user != null && user.getBusinessId() == loggedInConnection.getUser().getBusinessId()){ // we can show access for that user
                return accessDao.findForUserId(user.getId());
            }
        }
        return null;
    }
}
