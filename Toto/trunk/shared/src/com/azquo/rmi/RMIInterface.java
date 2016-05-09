package com.azquo.rmi;

import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.spreadsheet.jsonentities.JsonChildStructure;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.jsonentities.NameJsonRequest;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.FilterTriple;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * I decided to use RMI to communicate between the servers, it seems to work.
 *
 */
public interface RMIInterface extends Remote {
    String serviceName = "AzquoRMI";

    void emptyDatabase(String persistenceName) throws RemoteException;

    void dropDatabase(String persistenceName) throws RemoteException;

    void createDatabase(String persistenceName) throws RemoteException;

    String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws RemoteException;

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

    List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName, List<String> languages) throws RemoteException;

    void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException;

    void resolveQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException;

    List<TreeNode> formatDataRegionProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingNames(DatabaseAccessToken databaseAccessToken, Set<String> nameNames, int maxSize) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingIds(DatabaseAccessToken databaseAccessToken, Set<Integer> nameIds, int maxSize) throws RemoteException;

    boolean isDatabaseLoaded(String persistenceName) throws RemoteException;

    int getNameCount(String persistenceName) throws RemoteException;

    int getValueCount(String persistenceName) throws RemoteException;

    String getMemoryReport(boolean suggestGc) throws RemoteException;

    void unloadDatabase(String persistenceName) throws RemoteException;

    void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context) throws RemoteException;

    String getSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void clearSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    JsonChildren.Node createNode(DatabaseAccessToken dataAccessToken, int nameId) throws RemoteException;

    boolean renameNode(DatabaseAccessToken dataAccessToken, int nameId, String position) throws RemoteException;

    void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws RemoteException;

    String getNameAttribute(DatabaseAccessToken dataAccessToken, String nameString, String attribute) throws RemoteException;

    void setNameAttribute(DatabaseAccessToken dataAccessToken, String nameString, String attribute, String attVal) throws RemoteException;
}
