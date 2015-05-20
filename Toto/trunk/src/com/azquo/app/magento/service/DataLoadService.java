package com.azquo.app.magento.service;

import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created by cawley on 07/08/14.
 * <p/>
 * This class is used to parse files delivered by the Magento Plugin. If it ever needed to be really heavy duty one could use Spring Batch I suppose but I think
 * that would be overkill.
 */
public final class DataLoadService {

    @Autowired
    SpreadsheetService spreadsheetService;

    @Autowired
    LoginService loginService;

    @Autowired
    ImportService importService;

/*    final Map<Integer, Name> products = new HashMap<Integer, Name>();
    final Map<Integer, Name> categories = new HashMap<Integer, Name>();
    //final Map<Integer, MagentoOrderLineItem> orderLineItems = new HashMap<Integer, MagentoOrderLineItem>();
    final Map<String, String> optionValueLookup = new HashMap<String, String>();*/

    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        // todo proxy through
        return null;
    }

    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws Exception{
        // todo proxy through
        return false;
    }


    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws Exception {
        // todo proxy through
        return null;
    }

    // todo : since this is a single thread maybe use koloboke maps? I guess if it's going slowly. Also NIO options? We are on Java 8 now . . .
    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress) throws Exception {
        // todo proxy through
    }
}
