package com.azquo.app.magento.service;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 07/08/14.
 *
 * This loading class now proxies through to the relevant database to run there.
 */
public final class DataLoadService {

    @Autowired
    RMIClient rmiClient;

    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).findLastUpdate(databaseAccessToken, remoteAddress);
    }

    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws Exception{
        return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).magentoDBNeedsSettingUp(databaseAccessToken);
    }

    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return rmiClient.getServerInterface(databaseAccessToken.getServerIp()).findRequiredTables(databaseAccessToken, remoteAddress);
    }

    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws Exception {
        rmiClient.getServerInterface(databaseAccessToken.getServerIp()).loadData(databaseAccessToken, filePath, remoteAddress, user);
    }
}
