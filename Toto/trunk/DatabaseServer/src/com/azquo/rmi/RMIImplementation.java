package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.memorydb.service.ProvenanceService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.jsonentities.JsonChildStructure;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.FilterTriple;
import com.azquo.spreadsheet.view.RegionOptions;

import java.rmi.RemoteException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * Proxying through. I wonder if there should be multiple implementations, much point?
 *
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
    public String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileNameWithoutSuffix, List<String> attributeNames, String user, boolean persistAfter, boolean isSpreadsheet) throws RemoteException {
        try {
            return DSImportService.readPreparedFile(databaseAccessToken, filePath, fileNameWithoutSuffix, attributeNames, user, persistAfter, isSpreadsheet);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
    // spreadsheet service
    @Override
    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, boolean quiet) throws RemoteException {
        try {
            return DSSpreadsheetService.getCellsAndHeadingsForDisplay(databaseAccessToken, regionName, valueId, rowHeadingsSource, colHeadingsSource, contextSource, regionOptions, quiet);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language) throws RemoteException {
        try {
            return JSTreeService.getJsonChildren(databaseAccessToken,jsTreeId,nameId,parents,searchTerm,language);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildStructure getNameDetailsJson(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        try {
            return JSTreeService.getNameDetailsJson(databaseAccessToken,nameId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void editAttributes(DatabaseAccessToken databaseAccessToken, int nameId, Map<String, String> attributes) throws RemoteException {
        try {
            JSTreeService.editAttributes(databaseAccessToken,nameId,attributes);
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
    public List<String> getAttributeList(DatabaseAccessToken databaseAccessToken)throws RemoteException{
        try {
            return JSTreeService.getAttributeList(databaseAccessToken);
        }catch (Exception e){
            throw new RemoteException("Database Server Exception", e);
        }
    }


    @Override
    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException {
        try {
            return DSSpreadsheetService.getDropDownListForQuery(databaseAccessToken, query, languages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName, List<String> languages) throws RemoteException {
        try {
            return DSSpreadsheetService.getFilterListForQuery(databaseAccessToken, query, filterName, userName, languages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException {
        try {
            DSSpreadsheetService.createFilterSet(databaseAccessToken, setName, userName, childrenIds);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void resolveQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException {
        try {
            DSSpreadsheetService.resolveQuery(databaseAccessToken,query,languages);
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
    public List<TreeNode> formatDataRegionProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException {
        try {
            return ProvenanceService.getDataRegionProvenance(databaseAccessToken, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getDebugForCell(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol) throws RemoteException {
        try {
            return DSSpreadsheetService.getDebugForCell(databaseAccessToken,rowHeadingsSource,colHeadingsSource,contextSource,unsortedRow,unsortedCol);
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
    public String getMemoryReport(boolean suggestGc) throws RemoteException {
        if (suggestGc){
            // note : on Azquo recommended production JVM settings this will be ignored!
            System.gc();
        }
        StringBuilder toReturn = new StringBuilder();
        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;
        if (suggestGc){
            toReturn.append("##### Garbage Collection Suggested #####<br/>");
        }
        NumberFormat nf = NumberFormat.getInstance();
        // variable here is a bit easier to read and makes intellij happier
        String message = "--- MEMORY USED :  " + nf.format((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb);
        toReturn.append(message);
        return toReturn.toString();
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
            return DSSpreadsheetService.getSessionLog(databaseAccessToken);
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
    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws RemoteException {
        try {
            DSAdminService.copyDatabase(source,target,nameList,readLanguages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute)throws RemoteException {
        try {
            return JSTreeService.getNameAttribute(databaseAccessToken, nameString, attribute);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void setNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute, String attVal)throws RemoteException {
        try {
            JSTreeService.setNameAttribute(databaseAccessToken, nameString, attribute, attVal);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<String> nameAutoComplete(DatabaseAccessToken dataAccessToken, String nameString, int limit) throws RemoteException {
        try {
            return DSSpreadsheetService.nameAutoComplete(dataAccessToken,nameString,limit);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
}