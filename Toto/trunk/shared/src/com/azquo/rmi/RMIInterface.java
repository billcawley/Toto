package com.azquo.rmi;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Created by cawley on 20/05/15.
 */
public interface RMIInterface extends Remote {
    public final String serviceName = "AzquoRMI";

    public void emptyDatabase(String mysqlName) throws RemoteException;

    public void dropDatabase(String mysqlName) throws RemoteException;

    public void createDatabase(String mysqlName) throws RemoteException;

    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress) throws RemoteException;

    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames) throws RemoteException;

    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, String sortCol) throws RemoteException;

    public String processJSTreeRequest(DatabaseAccessToken dataAccessToken, String json, String jsTreeId, String topNode, String op, String parent, String parents, String database, String itemsChosen, String position, String backupSearchTerm) throws RemoteException;

    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException;
}