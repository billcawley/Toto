package com.azquo.rmi;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.spreadsheet.jsonentities.JsonChildStructure;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.jsonentities.NameJsonRequest;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

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

    String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user, boolean persistAfter, boolean isSpreadsheet) throws RemoteException;

    CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int filterCount, int maxRows, int maxCols, String sortRow, boolean sortRowAsc, String sortCol, boolean sortColumnAsc, int highlightDays) throws RemoteException;

    String processJSTreeRequest(DatabaseAccessToken dataAccessToken, NameJsonRequest nameJsonRequest) throws RemoteException;

    JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language) throws RemoteException;

    JsonChildStructure getChildDetailsFormattedForOutput(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException;

    boolean moveJsTreeNode(DatabaseAccessToken databaseAccessToken, int parentId, int childId) throws RemoteException;

    List<String> getAttributeList(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException;

    void resolveQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException;

    List<TreeNode> formatDataRegionProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingNames(DatabaseAccessToken databaseAccessToken, Set<String> nameNames, int maxSize) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingIds(DatabaseAccessToken databaseAccessToken, Set<Integer> nameIds, int maxSize) throws RemoteException;

    boolean isDatabaseLoaded(String mysqlName) throws RemoteException;

    boolean wasFastLoaded(String mysqlName) throws RemoteException;

    void convertDatabase(String mysqlName) throws RemoteException;

    int getNameCount(String mysqlName) throws RemoteException;

    int getValueCount(String mysqlName) throws RemoteException;

    String getMemoryReport(boolean suggestGc) throws RemoteException;

    void unloadDatabase(String mysqlName) throws RemoteException;

    void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context) throws RemoteException;

    String getSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void clearSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    boolean createNode(DatabaseAccessToken dataAccessToken, int nameId) throws RemoteException;

    boolean renameNode(DatabaseAccessToken dataAccessToken, int nameId, String position) throws RemoteException;

    void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws RemoteException;
}