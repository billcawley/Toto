package com.azquo.app.reviews.service;

import com.azquo.memorydb.Name;
import com.azquo.service.AdminService;
import com.azquo.service.AppDBConnectionMap;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created by cawley on 23/10/14.
 */
public class MerchantService {

    public static final String MASTERDBNAME = "revie_master";
    public static final String MERCHANT_SET = "merchant";

    private NameService nameService;

    private AppDBConnectionMap reviewsConnectionMap;

    AzquoMemoryDBConnection masterDBConnection;

    Name merchantSet;

    public MerchantService(NameService nameService, AppDBConnectionMap reviewsConnectionMap) throws Exception{
        this.nameService = nameService;
        this.reviewsConnectionMap = reviewsConnectionMap;
        masterDBConnection = reviewsConnectionMap.getConnection(MASTERDBNAME);
        merchantSet = nameService.findOrCreateNameInParent(masterDBConnection, MERCHANT_SET, null, false);
    }

    public interface MERCHANT_ATTRIBUTE {
        String ADDRESS = "ADDRESS";
        String EMAIL = "EMAIL";
        String TELEPHONENO = "TELEPHONENO";
    }

    public String createMerchant(String name, String address, String email, String telephoneno) throws Exception{
        if (reviewsConnectionMap.getConnection(MASTERDBNAME) == null){ // should only happen once!
            reviewsConnectionMap.newDatabase("master"); // note, I assume the main reviews business is called reviews!
        }
        Name exists = nameService.getNameByAttribute(masterDBConnection, name, merchantSet);
        if (exists != null){
            return "error:a merchant with that name already exists";
        }
        Name newMerchant = nameService.findOrCreateNameInParent(masterDBConnection, name, merchantSet, true);
        newMerchant.setAttributeWillBePersisted(MERCHANT_ATTRIBUTE.ADDRESS, address);
        newMerchant.setAttributeWillBePersisted(MERCHANT_ATTRIBUTE.EMAIL, email);
        newMerchant.setAttributeWillBePersisted(MERCHANT_ATTRIBUTE.TELEPHONENO, telephoneno);
        nameService.persist(masterDBConnection);
        return "";
    }

    public Name getMerchantByName(String name) throws Exception{
        return nameService.getNameByAttribute(masterDBConnection, name, merchantSet);
    }
}
