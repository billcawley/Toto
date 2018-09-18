package com.azquo.rmi;

import com.azquo.memorydb.*;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.transport.json.JsonChildStructure;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.transport.FilterTriple;
import com.azquo.spreadsheet.transport.RegionOptions;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * I decided to use RMI to communicate between the servers. I simply want communication between two JVMs I have control over, it seems to work fine.
 */
public interface RMIInterface extends Remote {
    String serviceName = "AzquoRMI";

    void emptyDatabase(String persistenceName) throws RemoteException;

    void checkDatabase(String persistenceName) throws RemoteException;

    void dropDatabase(String persistenceName) throws RemoteException;

    void createDatabase(String persistenceName) throws RemoteException;

    String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws RemoteException;

    String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileName, Map<String, String> fileNameParameters, String user, boolean persistAfter, boolean isSpreadsheet) throws RemoteException;

    CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String user, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, boolean quiet) throws RemoteException;

    JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language, int hundredMore) throws RemoteException;

    List<String> getAttributeList(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, boolean justUser) throws RemoteException;

    int getNameQueryCount(DatabaseAccessToken databaseAccessToken, String query, String user) throws RemoteException;

    List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName) throws RemoteException;

    void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException;

    void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, String query) throws RemoteException;

    boolean resolveQuery(DatabaseAccessToken databaseAccessToken, String query, String user) throws RemoteException;

    ProvenanceDetailsForDisplay getProvenanceDetailsForDisplay(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException;

    String getDebugForCell(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingNames(DatabaseAccessToken databaseAccessToken, Set<String> nameNames, int maxSize) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingIds(DatabaseAccessToken databaseAccessToken, Set<Integer> nameIds, int maxSize) throws RemoteException;

    boolean isDatabaseLoaded(String persistenceName) throws RemoteException;

    int getNameCount(String persistenceName) throws RemoteException;

    int getValueCount(String persistenceName) throws RemoteException;

    String getMemoryReport(boolean suggestGc) throws RemoteException;

    void unloadDatabase(String persistenceName) throws RemoteException;

    String saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context, boolean persist) throws RemoteException;

    void unlockData(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void persistDatabase(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    String getSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void clearSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void clearTemporaryNames(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void addToLog(DatabaseAccessToken databaseAccessToken, String message) throws RemoteException;

    JsonChildStructure getNameDetailsJson(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException;

    void editAttributes(DatabaseAccessToken databaseAccessToken, int nameId, Map<String, String> attributes) throws RemoteException;

    JsonChildren.Node createNode(DatabaseAccessToken dataAccessToken, int nameId) throws RemoteException;

    void deleteNode(DatabaseAccessToken dataAccessToken, int nameId) throws RemoteException;

    void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, String user) throws RemoteException;

    String getNameAttribute(DatabaseAccessToken dataAccessToken, String nameString, String attribute) throws RemoteException;

    void setNameAttribute(DatabaseAccessToken dataAccessToken, String nameString, String attribute, String attVal) throws RemoteException;

    List<String> nameAutoComplete(DatabaseAccessToken dataAccessToken, String nameString, int limit) throws RemoteException;

    void copyDatabase(String from, String to) throws RemoteException;

    boolean databaseWithNameExists(String nameCheck) throws RemoteException;

    ProvenanceDetailsForDisplay getListOfChangedValues(DatabaseAccessToken databaseAccessToken, int limit) throws RemoteException;

    // backup functions

    List<NameForBackup> getBatchOfNamesForBackup(DatabaseAccessToken dataAccessToken, int batchNumber) throws RemoteException;

    List<ValueForBackup> getBatchOfValuesForBackup(DatabaseAccessToken dataAccessToken, int batchNumber) throws RemoteException;

    List<ValueForBackup> getBatchOfValuesHistoryForBackup(DatabaseAccessToken dataAccessToken, int batchNumber) throws RemoteException;

    List<ProvenanceForBackup> getBatchOfProvenanceForBackup(DatabaseAccessToken dataAccessToken, int batchNumber) throws RemoteException;

    void sendBatchOfNamesFromBackup(DatabaseAccessToken dataAccessToken, List<NameForBackup> namesForBackup) throws RemoteException;

    void linkNamesForBackupRestore(DatabaseAccessToken dataAccessToken) throws RemoteException;

    void sendBatchOfValuesFromBackup(DatabaseAccessToken dataAccessToken, List<ValueForBackup> valuesForBackup) throws RemoteException;

    void sendBatchOfValueHistoriesFromBackup(DatabaseAccessToken dataAccessToken, List<ValueForBackup> valuesForBackup) throws RemoteException;

    void sendBatchOfProvenanceFromBackup(DatabaseAccessToken dataAccessToken, List<ProvenanceForBackup> provenanceForBackup) throws RemoteException;
}
