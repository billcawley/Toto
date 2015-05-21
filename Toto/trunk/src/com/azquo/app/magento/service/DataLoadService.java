package com.azquo.app.magento.service;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created by cawley on 07/08/14.
 *
 * This loading class now proxies through to the relevant database to run there.
 */
public final class DataLoadService {

    @Autowired
    RMIClient rmiClient;

    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return rmiClient.getServerInterface().findLastUpdate(databaseAccessToken, remoteAddress);
    }

    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws Exception{
        return rmiClient.getServerInterface().magentoDBNeedsSettingUp(databaseAccessToken);
    }


    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return rmiClient.getServerInterface().findRequiredTables(databaseAccessToken, remoteAddress);
    }

    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress) throws Exception {
        rmiClient.getServerInterface().loadData(databaseAccessToken, filePath, remoteAddress);
    }
}
