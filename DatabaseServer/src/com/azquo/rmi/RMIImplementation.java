package com.azquo.rmi;

import com.azquo.LineIdentifierLineValue;
import com.azquo.StringLiterals;
import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.*;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ProvenanceService;
import com.azquo.spreadsheet.AzquoCellResolver;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.UserChoiceService;
import com.azquo.spreadsheet.transport.*;
import com.azquo.spreadsheet.transport.json.JsonChildStructure;
import com.azquo.spreadsheet.transport.json.JsonChildren;


import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.rmi.RemoteException;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Proxying through. I wonder if there should be multiple implementations, much point?
 */
class RMIImplementation implements RMIInterface {

    public void testConnection() {
    }

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
    public UploadedFile readPreparedFile(DatabaseAccessToken databaseAccessToken, UploadedFile uploadedFile, String user) throws RemoteException {
        try {
            return DSImportService.readPreparedFile(databaseAccessToken, uploadedFile, user);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void uploadWizardData(DatabaseAccessToken databaseAccessToken, String fileName, Map<String,String>headings, Map<String,List<String>>data) throws RemoteException {
        try {
            DSImportService.uploadWizardData(databaseAccessToken, fileName, headings,data);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }


    @Override
    public void checkTemporaryCopyExists(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            DSImportService.checkTemporaryCopyExists(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    // to lookup error lines based off report feedback, for Ed Broking pending uploads
    @Override
    public Map<Integer, LineIdentifierLineValue> getLinesWithValuesInColumn(UploadedFile uploadedFile, int columnIndex, Set<String> valuesToCheck) throws RemoteException {
        try {
            return DSImportService.getLinesWithValuesInColumn(uploadedFile, columnIndex, valuesToCheck);
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
    public boolean nameValidForChosenTree(DatabaseAccessToken databaseAccessToken, String chosenName, String searchTerm) throws RemoteException {
        try {
            return JSTreeService.nameValidForChosenTree(databaseAccessToken, chosenName, searchTerm);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getFirstChoiceForChosenTree(DatabaseAccessToken databaseAccessToken, String query) throws RemoteException {
        try {
            return JSTreeService.getFirstChoiceForChosenTree(databaseAccessToken, query);
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
    public String getUniqueNameFromId(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name n = NameService.findById(connectionFromAccessToken, nameId);
        return n != null ? AzquoCellResolver.getUniqueName(connectionFromAccessToken, n, Collections.EMPTY_LIST) : ""; // don't deal with things that don't have default display names
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
    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, boolean justUser, int provenanceId) throws RemoteException {
        try {
            return UserChoiceService.getDropDownListForQuery(databaseAccessToken, query, user, null, justUser, provenanceId);
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, String searchTerm, boolean justUser, int provenanceId) throws RemoteException {
        try {
            return UserChoiceService.getDropDownListForQuery(databaseAccessToken, query, user, searchTerm, justUser, provenanceId);
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

    public void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException {
        try {
            UserChoiceService.createFilterSetWithQuery(databaseAccessToken, setName, userName, childrenIds);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createFilterSetWithQuery(DatabaseAccessToken databaseAccessToken, String setName, String userName, String query) throws RemoteException {
        try {
            UserChoiceService.createFilterSetWithQuery(databaseAccessToken, setName, userName, query);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String resolveQuery(DatabaseAccessToken databaseAccessToken, String query, String user, List<List<String>> contextSource) throws RemoteException {
        try {
            return UserChoiceService.resolveQuery(databaseAccessToken, query, user, contextSource);
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

    public TreeNode getIndividualProvenanceCounts(DatabaseAccessToken databaseAccessToken, int maxSize, String pChosen) throws RemoteException {
        try {
            return ProvenanceService.getIndividualProvenanceCounts(databaseAccessToken,maxSize,pChosen);
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
    public String saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String userName, String reportName, String context, boolean persist) throws RemoteException {
        try {
            return DSSpreadsheetService.saveData(databaseAccessToken, cellsAndHeadingsForDisplay, user, userName, reportName, context, persist);
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

    // to allow execute/import to save multiple times then persist
    @Override
    public void persistDatabase(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            // get back to the user straight away. Should not be a problem, multiple persists would be queued.
            new Thread(
                    () -> {
                        // braces and a belt, as this is in a new thread its possible a persist might be called on a copied database when it's been zapped so check here
                        // more careful handling of temporary databases will hopefully make this redundant
                        if (!databaseAccessToken.getPersistenceName().startsWith(StringLiterals.copyPrefix)) {
                            DSSpreadsheetService.persistDatabase(databaseAccessToken);
                        }
                    }
            ).start();
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
        // while we're here report on the threads, could be very useful for checking on deadlocks
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        long[] ids = tmx.findDeadlockedThreads();
        if (ids != null) {
            ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
            System.out.println("Following Threads are deadlocked");
            for (ThreadInfo info : infos) {
                System.out.println(info);
            }
        }
        return toReturn.toString();
    }

    @Override
    public String getCPUReport() throws RemoteException {
        try {
            StringBuilder toReturn = new StringBuilder();
            OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
            NumberFormat nf = NumberFormat.getInstance();
/*            double percent = -1;
            for (Method method : operatingSystemMXBean.getClass().getDeclaredMethods()) {
                method.setAccessible(true);
                if (method.getName().equals("getSystemCpuLoad")
                        && Modifier.isPublic(method.getModifiers())) {
                    Object value;
                    try {
                        value = method.invoke(operatingSystemMXBean);
                        percent = (Double) value;
                    } catch (Exception ignored) {
                    } // try
//                    toReturn.append(method.getName() + " = " + value);
                } // if
            } // for            // variable here is a bit easier to read and makes intellij happier
            */
//            String message = "--- CPU USAGE :  " + nf.format(percent * 100) + "%";
            String message = "--- System load average :  " + nf.format(operatingSystemMXBean.getSystemLoadAverage());
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
    public String getNameAttribute(DatabaseAccessToken databaseAccessToken, int nameId, String nameString, String attribute) throws RemoteException {
        try {
            return JSTreeService.getNameAttribute(databaseAccessToken, nameId, nameString, attribute);
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
    public ProvenanceDetailsForDisplay getDatabaseAuditList(DatabaseAccessToken databaseAccessToken, String dateTime, int maxCount) throws  RemoteException {
        try {
            return ProvenanceService.getDatabaseAuditList(databaseAccessToken, dateTime, maxCount);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }


    @Override
    public String getBackupFileForDatabase(String databaseName, String subsetName, DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
            return connectionFromAccessToken.getAzquoMemoryDB().getBackupTransport().createDBBackupFile(databaseName,
                subsetName != null ? NameService.findByName(connectionFromAccessToken, subsetName) : null);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
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

    @Override
    public void zapTemporaryCopy(DatabaseAccessToken databaseAccessToken) {
        AzquoMemoryDB.zapTemporarayCopyOfAzquoMemoryDB(databaseAccessToken);
    }


    public void addToUserLog(DatabaseAccessToken databaseAccessToken, String loginfo) {
        try {
            AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).addToUserLog(loginfo);
        } catch (Exception e) {
            //if no log - ignore!
        }
    }

    public List<String>getPossibleHeadings(DatabaseAccessToken databaseAccessToken, String dataItem) throws RemoteException {
        try {
            return NameService.getPossibleHeadings(databaseAccessToken, dataItem);
        } catch (Exception e) {
            throw new RemoteException("Database server Exception", e);
        }
    }




 }


