package com.azquo.rmi;

import com.azquo.LineIdentifierLineValue;
import com.azquo.memorydb.*;
import com.azquo.spreadsheet.transport.*;
import com.azquo.spreadsheet.transport.json.JsonChildStructure;
import com.azquo.spreadsheet.transport.json.JsonChildren;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * I decided to use RMI to communicate between the servers. I simply want communication between two JVMs I have control over, it seems to work fine.
 */
public interface RMIInterface extends Remote {
    String serviceName = "AzquoRMI";

    void testConnection() throws RemoteException;

    void emptyDatabase(String persistenceName) throws RemoteException;

    void checkDatabase(String persistenceName) throws RemoteException;

    void dropDatabase(String persistenceName) throws RemoteException;

    void createDatabase(String persistenceName) throws RemoteException;

    String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws RemoteException;

    String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException;

    UploadedFile readPreparedFile(DatabaseAccessToken databaseAccessToken, UploadedFile uploadedFile, String user) throws RemoteException;

    void uploadWizardData(DatabaseAccessToken databaseAccessToken, String fileName, Map<String,String>headings, Map<String,List<String>>data) throws RemoteException;

    void checkTemporaryCopyExists(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    Map<Integer, LineIdentifierLineValue> getLinesWithValuesInColumn(UploadedFile uploadedFile, int columnIndex, Set<String> valuesToCheck) throws RemoteException;

    CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String user, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, boolean quiet) throws RemoteException;

    JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language, int hundredMore) throws RemoteException;

    boolean nameValidForChosenTree(DatabaseAccessToken databaseAccessToken, String chosenName, String searchTerm) throws RemoteException;

    String getFirstChoiceForChosenTree(DatabaseAccessToken databaseAccessToken, String query) throws RemoteException;

    List<String> getAttributeList(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, boolean justUser, int provenanceId) throws RemoteException;

    List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, String searchTerm, boolean justUser, int provenanceId) throws RemoteException;

    int getNameQueryCount(DatabaseAccessToken databaseAccessToken, String query, String user) throws RemoteException;

    List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName) throws RemoteException;

    void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException;

    void createFilterSetWithQuery(DatabaseAccessToken databaseAccessToken, String setName, String userName, String query) throws RemoteException;

    String resolveQuery(DatabaseAccessToken databaseAccessToken, String query, String user, List<List<String>> contextSource) throws RemoteException;

    ProvenanceDetailsForDisplay getProvenanceDetailsForDisplay(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException;

    String getDebugForCell(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingNames(DatabaseAccessToken databaseAccessToken, Set<String> nameNames, int maxSize) throws RemoteException;

    TreeNode getJstreeDataForOutputUsingIds(DatabaseAccessToken databaseAccessToken, Set<Integer> nameIds, int maxSize) throws RemoteException;

    TreeNode getIndividualProvenanceCounts(DatabaseAccessToken databaseAccessToken, int maxCount, String pChosen) throws RemoteException;

    boolean isDatabaseLoaded(String persistenceName) throws RemoteException;

    int getNameCount(String persistenceName) throws RemoteException;

    String getCPUReport() throws RemoteException;

    int getValueCount(String persistenceName) throws RemoteException;

    String getMemoryReport(boolean suggestGc) throws RemoteException;

    void unloadDatabase(String persistenceName) throws RemoteException;

    String saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String userName, String reportName, String context, boolean persist) throws RemoteException;

    void unlockData(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void persistDatabase(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void zapTemporaryCopy(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    String getSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void clearSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws RemoteException;

    void addToLog(DatabaseAccessToken databaseAccessToken, String message) throws RemoteException;

    JsonChildStructure getNameDetailsJson(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException;

    String getUniqueNameFromId(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException;

    void editAttributes(DatabaseAccessToken databaseAccessToken, int nameId, Map<String, String> attributes) throws RemoteException;

    JsonChildren.Node createNode(DatabaseAccessToken dataAccessToken, int nameId) throws RemoteException;

    void deleteNode(DatabaseAccessToken dataAccessToken, int nameId) throws RemoteException;

    String getNameAttribute(DatabaseAccessToken dataAccessToken, int nameId, String nameString, String attribute) throws RemoteException;

    void setNameAttribute(DatabaseAccessToken dataAccessToken, String nameString, String attribute, String attVal) throws RemoteException;

    List<String> nameAutoComplete(DatabaseAccessToken dataAccessToken, String nameString, int limit) throws RemoteException;

    boolean databaseWithNameExists(String nameCheck) throws RemoteException;

    ProvenanceDetailsForDisplay getListOfChangedValues(DatabaseAccessToken databaseAccessToken, int limit) throws RemoteException;

    ProvenanceDetailsForDisplay getDatabaseAuditList(DatabaseAccessToken databaseAccessToken, String dateTime, int maxCount) throws  RemoteException;

    // backup functions

    String getBackupFileForDatabase(String databasseName, String subsetName, DatabaseAccessToken dataAccessToken) throws RemoteException;

    void sendBatchOfNamesFromBackup(DatabaseAccessToken dataAccessToken, List<NameForBackup> namesForBackup) throws RemoteException;

    void linkNamesForBackupRestore(DatabaseAccessToken dataAccessToken) throws RemoteException;

    void sendBatchOfValuesFromBackup(DatabaseAccessToken dataAccessToken, List<ValueForBackup> valuesForBackup) throws RemoteException;

    void sendBatchOfValueHistoriesFromBackup(DatabaseAccessToken dataAccessToken, List<ValueForBackup> valuesForBackup) throws RemoteException;

    void sendBatchOfProvenanceFromBackup(DatabaseAccessToken dataAccessToken, List<ProvenanceForBackup> provenanceForBackup) throws RemoteException;

    String getMostRecentProvenance(String persistenceName) throws RemoteException;

    List<String>getPossibleHeadings(DatabaseAccessToken databaseAccessToken, String dataItem) throws RemoteException;




    }
