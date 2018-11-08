package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.*;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ProvenanceService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.UserChoiceService;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.transport.json.JsonChildStructure;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.transport.FilterTriple;
import com.azquo.spreadsheet.transport.RegionOptions;


import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.rmi.RemoteException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Proxying through. I wonder if there should be multiple implementations, much point?
 */
class RMIImplementation implements RMIInterface {

    //Admin stuff
    @Override
    public void emptyDatabase(String persistenceName) throws RemoteException {
        try {
            DSAdminService.emptyDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);// I think this is reasonable for the mo?
        }
    }

    @Override
    public void checkDatabase(String persistenceName) throws RemoteException {
        try {
            DSAdminService.checkDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);// I think this is reasonable for the mo?
        }
    }

    @Override
    public void dropDatabase(String persistenceName) throws RemoteException {
        try {
            DSAdminService.dropDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createDatabase(String persistenceName) throws RemoteException {
        try {
            DSAdminService.createDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void copyDatabase(String from, String to) throws RemoteException {
        try {
            DSAdminService.copyDatabase(from, to);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    //Magento data load stuff (I could break these up but I'm not sure a thte moment it's so important for what's essentially proxy code)

    @Override
    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException {
        try {
            return DSDataLoadService.findLastUpdate(databaseAccessToken, remoteAddress);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            return DSDataLoadService.magentoDBNeedsSettingUp(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException {
        try {
            return DSDataLoadService.findRequiredTables(databaseAccessToken, remoteAddress);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws RemoteException {
        try {
            DSDataLoadService.loadData(databaseAccessToken, filePath, remoteAddress, user);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    // import
    @Override
    public String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileNameWithoutSuffix, String fileSource, Map<String, String> fileNameParameters, String user, boolean persistAfter, boolean isSpreadsheet) throws RemoteException {
        try {
            return DSImportService.readPreparedFile(databaseAccessToken, filePath, fileNameWithoutSuffix, fileSource, fileNameParameters, user, persistAfter, isSpreadsheet);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    // spreadsheet service
    @Override
    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String user, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, boolean quiet) throws RemoteException {
        try {
            return DSSpreadsheetService.getCellsAndHeadingsForDisplay(databaseAccessToken, user, regionName, valueId, rowHeadingsSource, colHeadingsSource, contextSource, regionOptions, quiet);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language, int hundredMore) throws RemoteException {
        try {
            return JSTreeService.getJsonChildren(databaseAccessToken, jsTreeId, nameId, parents, searchTerm, language, hundredMore);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildStructure getNameDetailsJson(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        try {
            return JSTreeService.getNameDetailsJson(databaseAccessToken, nameId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void editAttributes(DatabaseAccessToken databaseAccessToken, int nameId, Map<String, String> attributes) throws RemoteException {
        try {
            JSTreeService.editAttributes(databaseAccessToken, nameId, attributes);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildren.Node createNode(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        try {
            return JSTreeService.createJsTreeNode(databaseAccessToken, nameId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void deleteNode(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        try {
            JSTreeService.deleteJsTreeNode(databaseAccessToken, nameId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<String> getAttributeList(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            return JSTreeService.getAttributeList(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, boolean justUser) throws RemoteException {
        try {
            return UserChoiceService.getDropDownListForQuery(databaseAccessToken, query, user, justUser);
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }

    @Override
    public int getNameQueryCount(DatabaseAccessToken databaseAccessToken, String query, String user) throws RemoteException {
        try {
            List<String> languages = NameService.getDefaultLanguagesList(user);
            return NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query, languages, true).size();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName) throws RemoteException {
        try {
            return UserChoiceService.getFilterListForQuery(databaseAccessToken, query, filterName, userName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException {
        try {
            UserChoiceService.createFilterSet(databaseAccessToken, setName, userName, childrenIds);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, String query) throws RemoteException {
        try {
            UserChoiceService.createFilterSet(databaseAccessToken, setName, userName, query);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean resolveQuery(DatabaseAccessToken databaseAccessToken, String query, String user) throws RemoteException {
        try {
            return UserChoiceService.resolveQuery(databaseAccessToken, query, user);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    public TreeNode getJstreeDataForOutputUsingNames(DatabaseAccessToken databaseAccessToken, Set<String> nameNames, int maxSize) throws RemoteException {
        try {
            return JSTreeService.getDataList(databaseAccessToken, nameNames, null, maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    public TreeNode getJstreeDataForOutputUsingIds(DatabaseAccessToken databaseAccessToken, Set<Integer> nameIds, int maxSize) throws RemoteException {
        try {
            return JSTreeService.getDataList(databaseAccessToken, null, nameIds, maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public ProvenanceDetailsForDisplay getProvenanceDetailsForDisplay(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException {
        try {
            return ProvenanceService.getDataRegionProvenance(databaseAccessToken, user, rowHeadingsSource, colHeadingsSource, contextSource, regionOptionsForTransport, unsortedRow, unsortedCol, maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getDebugForCell(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol) throws RemoteException {
        try {
            return DSSpreadsheetService.getDebugForCell(databaseAccessToken, user, rowHeadingsSource, colHeadingsSource, contextSource, regionOptionsForTransport, unsortedRow, unsortedCol);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context, boolean persist) throws RemoteException {
        try {
            return DSSpreadsheetService.saveData(databaseAccessToken, cellsAndHeadingsForDisplay, user, reportName, context, persist);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void unlockData(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            DSSpreadsheetService.unlockData(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    // to allow execute to save multiple times then persist
    @Override
    public void persistDatabase(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            DSSpreadsheetService.persistDatabase(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void unloadDatabase(String persistenceName) throws RemoteException {
        try {
            AzquoMemoryDB.removeDBFromMap(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean isDatabaseLoaded(String persistenceName) throws RemoteException {
        try {
            return AzquoMemoryDB.isDBLoaded(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public int getNameCount(String persistenceName) throws RemoteException {
        try {
            return AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null).getNameCount();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getMemoryReport(boolean suggestGc) {
        if (suggestGc) {
            // note : on Azquo recommended production JVM settings this will be ignored!
            System.gc();
        }
        StringBuilder toReturn = new StringBuilder();
        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;
        if (suggestGc) {
            toReturn.append("##### Garbage Collection Suggested #####<br/>");
        }
        NumberFormat nf = NumberFormat.getInstance();
        // variable here is a bit easier to read and makes intellij happier
        String message = "--- MEMORY USED :  " + nf.format((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb);
        toReturn.append(message);
        return toReturn.toString();
    }

    @Override
    public String getCPUReport() throws RemoteException {
        try {
            StringBuilder toReturn = new StringBuilder();
            OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
            double percent = -1;
            for (Method method : operatingSystemMXBean.getClass().getDeclaredMethods()) {
                method.setAccessible(true);
                if (method.getName().equals("getSystemCpuLoad")
                        && Modifier.isPublic(method.getModifiers())) {
                    Object value;
                    try {
                        value = method.invoke(operatingSystemMXBean);
                        percent = (Double)value;
                    } catch (Exception e) {
                    } // try
//                    toReturn.append(method.getName() + " = " + value);
                } // if
            } // for            // variable here is a bit easier to read and makes intellij happier
            NumberFormat nf = NumberFormat.getInstance();
            String message = "--- CPU USAGE :  " + nf.format(percent * 100) + "%";
            toReturn.append(message);
            return toReturn.toString();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public int getValueCount(String persistenceName) throws RemoteException {
        try {
            return AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null).getValueCount();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            return AzquoMemoryDBConnection.getSessionLog(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void clearSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            DSSpreadsheetService.clearLog(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void clearTemporaryNames(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            DSSpreadsheetService.clearTemporaryNames(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            DSSpreadsheetService.sendStopMessageToLog(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void addToLog(DatabaseAccessToken databaseAccessToken, String message) throws RemoteException {
        try {
            DSSpreadsheetService.addToLog(databaseAccessToken, message);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, String user) throws RemoteException {
        try {
            DSAdminService.copyDatabase(source, target, nameList, user);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute) throws RemoteException {
        try {
            return JSTreeService.getNameAttribute(databaseAccessToken, nameString, attribute);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void setNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute, String attVal) throws RemoteException {
        try {
            JSTreeService.setNameAttribute(databaseAccessToken, nameString, attribute, attVal);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<String> nameAutoComplete(DatabaseAccessToken dataAccessToken, String nameString, int limit) throws RemoteException {
        try {
            return DSSpreadsheetService.nameAutoComplete(dataAccessToken, nameString, limit);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean databaseWithNameExists(String nameCheck) throws RemoteException {
        try {
            return DSAdminService.databaseWithNameExists(nameCheck);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public ProvenanceDetailsForDisplay getListOfChangedValues(DatabaseAccessToken databaseAccessToken, int limit) throws RemoteException {
        try {
            return ProvenanceService.getListOfChangedValues(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), limit);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<NameForBackup> getBatchOfNamesForBackup(DatabaseAccessToken databaseAccessToken, int batchNumber) {
        return AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).getAzquoMemoryDB().getBackupTransport().getBatchOfNamesForBackup(batchNumber);
    }

    @Override
    public List<ValueForBackup> getBatchOfValuesForBackup(DatabaseAccessToken databaseAccessToken, int batchNumber) {
        return AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).getAzquoMemoryDB().getBackupTransport().getBatchOfValuesForBackup(batchNumber);
    }

    @Override
    public List<ValueForBackup> getBatchOfValuesHistoryForBackup(DatabaseAccessToken databaseAccessToken, int batchNumber) {
        return AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).getAzquoMemoryDB().getBackupTransport().getBatchOfValuesHistoryForBackup(batchNumber);
    }

    @Override
    public List<ProvenanceForBackup> getBatchOfProvenanceForBackup(DatabaseAccessToken databaseAccessToken, int batchNumber) {
        return AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).getAzquoMemoryDB().getBackupTransport().getBatchOfProvenanceForBackup(batchNumber);
    }


    @Override
    public void sendBatchOfNamesFromBackup(DatabaseAccessToken dataAccessToken, List<NameForBackup> namesForBackup) throws RemoteException {
        try {
            AzquoMemoryDBConnection.getConnectionFromAccessToken(dataAccessToken).getAzquoMemoryDB().getBackupTransport().setBatchOfNamesFromBackup(namesForBackup);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void linkNamesForBackupRestore(DatabaseAccessToken dataAccessToken) throws RemoteException {
        try {
            AzquoMemoryDBConnection.getConnectionFromAccessToken(dataAccessToken).getAzquoMemoryDB().getBackupTransport().linkNames();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void sendBatchOfValuesFromBackup(DatabaseAccessToken dataAccessToken, List<ValueForBackup> valuesForBackup) throws RemoteException {
        try {
            AzquoMemoryDBConnection.getConnectionFromAccessToken(dataAccessToken).getAzquoMemoryDB().getBackupTransport().setBatchOfValuesFromBackup(valuesForBackup);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void sendBatchOfValueHistoriesFromBackup(DatabaseAccessToken dataAccessToken, List<ValueForBackup> valuesForBackup) throws RemoteException {
        try {
            AzquoMemoryDBConnection.getConnectionFromAccessToken(dataAccessToken).getAzquoMemoryDB().getBackupTransport().setBatchOfValueHistoriesFromBackup(valuesForBackup);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void sendBatchOfProvenanceFromBackup(DatabaseAccessToken dataAccessToken, List<ProvenanceForBackup> provenanceForBackup) throws RemoteException {
        try {
            AzquoMemoryDBConnection.getConnectionFromAccessToken(dataAccessToken).getAzquoMemoryDB().getBackupTransport().setBatchOfProvenanceFromBackup(provenanceForBackup);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getMostRecentProvenance(String persistenceName) {
        return AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null).getMostRecentProvenance() != null ? AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null).getMostRecentProvenance().getProvenanceForDisplay().toString() : "";
    }
}