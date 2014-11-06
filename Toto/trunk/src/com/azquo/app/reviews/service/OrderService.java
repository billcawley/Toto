package com.azquo.app.reviews.service;

import com.azquo.memorydb.Name;
import com.azquo.service.AdminService;
import com.azquo.service.AppDBConnectionMap;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;

/**
 * Created by cawley on 29/10/14.
 */
public class OrderService {

    public static final String ORDER = "order";

    public static final String MASTERDBNAME = "revie_master";
    private NameService nameService;

    private AppDBConnectionMap reviewsConnectionMap;

    AzquoMemoryDBConnection masterDBConnection;

    private Name orderSet = null;

    // TODO : these constructors are similar, factor off?

    public OrderService(NameService nameService, AdminService adminService, AppDBConnectionMap reviewsConnectionMap) throws Exception{
        this.nameService = nameService;
        this.reviewsConnectionMap = reviewsConnectionMap;
        masterDBConnection = reviewsConnectionMap.getConnection(MASTERDBNAME);
        orderSet = nameService.findOrCreateNameInParent(masterDBConnection, ORDER, null, false);
    }

    public Name getOrder(String supplierDb, String orderNumber) throws Exception{

        return nameService.getNameByAttribute(masterDBConnection, orderNumber, orderSet);
    }


}
