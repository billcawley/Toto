package com.azquo.rmi;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Created by cawley on 20/05/15.
 *
 */
public interface RMIInterface extends Remote {
    String serviceName = "AzquoRMI";

    void emptyDatabase(String mysqlName) throws RemoteException;

    void dropDatabase(String mysqlName) throws RemoteException;

    void createDatabase(String mysqlName) throws RemoteException;

    String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress) throws RemoteException;

    String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames) throws RemoteException;

    CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, String sortCol, int highlightDays, int eddMaxRows) throws RemoteException;

    String processJSTreeRequest(DatabaseAccessToken dataAccessToken, String json, String jsTreeId, String topNode, String op, String parent, String parents, String database, String itemsChosen, String position, String backupSearchTerm) throws RemoteException;

    List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException;

    String formatDataRegionProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, String jsonFunction) throws RemoteException;

    String formatRowHeadingProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, int unsortedRow, int col, String jsonFunction) throws RemoteException;

    String formatColumnHeadingProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> columnHeadingsSource, int row, int unsortedCol, String jsonFunction)  throws RemoteException;

    String getJsonList(DatabaseAccessToken databaseAccessToken, String listName, String listChoice, String entered, String jsonFunction) throws RemoteException;

    void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay) throws RemoteException;
}