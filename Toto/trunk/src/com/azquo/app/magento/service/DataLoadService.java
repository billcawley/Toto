package com.azquo.app.magento.service;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 07/08/14.
 * <p>
 * This loading class now proxies through to the relevant database to run there.
 */
public final class DataLoadService {

    public static String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).findLastUpdate(databaseAccessToken, remoteAddress);
    }

    public static boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws Exception {
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).magentoDBNeedsSettingUp(databaseAccessToken);
    }

    public static String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        return RMIClient.getServerInterface(databaseAccessToken.getServerIp()).findRequiredTables(databaseAccessToken, remoteAddress);
    }

    public static void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws Exception {
        RMIClient.getServerInterface(databaseAccessToken.getServerIp()).loadData(databaseAccessToken, filePath, remoteAddress, user);
    }
}
