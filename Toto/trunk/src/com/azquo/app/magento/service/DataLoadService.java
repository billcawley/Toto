package com.azquo.app.magento.service;

import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created by cawley on 07/08/14.
 * <p/>
 * This class is used to parse files delivered by the Magento Plugin. If it ever needed to be really heavy duty one could use Spring Batch I suppose but I think
 * that would be overkill.
 *
 * This report bit has ended up as a straight proxy, all functions. Hmmmmmmmmmm . . . .
 */
public final class DataLoadService {

    @Autowired
    RMIClient rmiClient;

/*    final Map<Integer, Name> products = new HashMap<Integer, Name>();
    final Map<Integer, Name> categories = new HashMap<Integer, Name>();
    //final Map<Integer, MagentoOrderLineItem> orderLineItems = new HashMap<Integer, MagentoOrderLineItem>();
    final Map<String, String> optionValueLookup = new HashMap<String, String>();*/

    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return rmiClient.serverInterface.findLastUpdate(databaseAccessToken, remoteAddress);
    }

    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws Exception{
        return rmiClient.serverInterface.magentoDBNeedsSettingUp(databaseAccessToken);
    }


    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return rmiClient.serverInterface.findRequiredTables(databaseAccessToken, remoteAddress);
    }

    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress) throws Exception {
        rmiClient.serverInterface.loadData(databaseAccessToken, filePath, remoteAddress);
    }
}
