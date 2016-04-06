package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.MemoryDBManager;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.jsonentities.JsonChildStructure;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.jsonentities.NameJsonRequest;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.FilterTriple;

import java.rmi.RemoteException;
import java.text.NumberFormat;
import java.util.List;
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

    private final DSSpreadsheetService dsSpreadsheetService;
    private final DSAdminService dsAdminService;
    private final DSDataLoadService dsDataLoadService;
    private final DSImportService dsImportService;
    private final JSTreeService jsTreeService;
    private final MemoryDBManager memoryDBManager;


    RMIImplementation(DSSpreadsheetService dsSpreadsheetService, DSAdminService dsAdminService, DSDataLoadService dsDataLoadService
            , DSImportService dsImportService, JSTreeService jsTreeService, MemoryDBManager memoryDBManager) {
        this.dsSpreadsheetService = dsSpreadsheetService;
        this.dsAdminService = dsAdminService;
        this.dsDataLoadService = dsDataLoadService;
        this.dsImportService = dsImportService;
        this.jsTreeService = jsTreeService;
        this.memoryDBManager = memoryDBManager;
    }

    //Admin stuff
    @Override
    public void emptyDatabase(String persistenceName) throws RemoteException {
        try {
            dsAdminService.emptyDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);// I think this is reasonable for the mo?
        }
    }

    @Override
    public void dropDatabase(String persistenceName) throws RemoteException {
        try {
            dsAdminService.dropDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createDatabase(String persistenceName) throws RemoteException {
        try {
            dsAdminService.createDatabase(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    //Magento data load stuff (I could break these up but I'm not sure a thte moment it's so important for what's essentially proxy code)

    @Override
    public String findLastUpdate(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException {
        try {
            return dsDataLoadService.findLastUpdate(databaseAccessToken, remoteAddress);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean magentoDBNeedsSettingUp(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            return dsDataLoadService.magentoDBNeedsSettingUp(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String findRequiredTables(DatabaseAccessToken databaseAccessToken, String remoteAddress) throws RemoteException {
        try {
            return dsDataLoadService.findRequiredTables(databaseAccessToken, remoteAddress);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws RemoteException {
        try {
            dsDataLoadService.loadData(databaseAccessToken, filePath, remoteAddress, user);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
    // import
    @Override
    public String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user, boolean persistAfter, boolean isSpreadsheet) throws RemoteException {
        try {
            return dsImportService.readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames, user, persistAfter, isSpreadsheet);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
    // spreadsheet service
    @Override
    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int filterCount, int maxRows, int maxCols, String sortRow,
                                                                    boolean sortRowAsc, String sortCol, boolean sortColumnAsc, int highlightDays) throws RemoteException {
        try {
            return dsSpreadsheetService.getCellsAndHeadingsForDisplay(databaseAccessToken, regionName, valueId, rowHeadingsSource, colHeadingsSource, contextSource, filterCount, maxRows, maxCols, sortRow, sortRowAsc, sortCol, sortColumnAsc, highlightDays);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String processJSTreeRequest(DatabaseAccessToken dataAccessToken, NameJsonRequest nameJsonRequest) throws RemoteException {
        try {
            return jsTreeService.processJsonRequest(dataAccessToken, nameJsonRequest);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildren getJsonChildren(DatabaseAccessToken databaseAccessToken, int jsTreeId, int nameId, boolean parents, String searchTerm, String language) throws RemoteException {
        try {
            return jsTreeService.getJsonChildren(databaseAccessToken,jsTreeId,nameId,parents,searchTerm,language);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public JsonChildStructure getChildDetailsFormattedForOutput(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        try {
            return jsTreeService.getChildDetailsFormattedForOutput(databaseAccessToken,nameId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean moveJsTreeNode(DatabaseAccessToken databaseAccessToken, int parentId, int childId) throws RemoteException {
        try {
            return jsTreeService.moveJsTreeNode(databaseAccessToken,parentId,childId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean createNode(DatabaseAccessToken databaseAccessToken, int nameId) throws RemoteException {
        try {
            return jsTreeService.createJsTreeNode(databaseAccessToken, nameId);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean renameNode(DatabaseAccessToken databaseAccessToken, int nameId, String name) throws RemoteException {
        try {
            return jsTreeService.renameJsTreeNode(databaseAccessToken, nameId, name);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<String> getAttributeList(DatabaseAccessToken databaseAccessToken)throws RemoteException{
        try {
            return jsTreeService.getAttributeList(databaseAccessToken);
        }catch (Exception e){
            throw new RemoteException("Database Server Exception", e);
        }
    }


    @Override
    public List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException {
        try {
            return dsSpreadsheetService.getDropDownListForQuery(databaseAccessToken, query, languages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName, List<String> languages) throws RemoteException {
        try {
            return dsSpreadsheetService.getFilterListForQuery(databaseAccessToken, query, filterName, userName, languages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws RemoteException {
        try {
            dsSpreadsheetService.createFilterSet(databaseAccessToken, setName, userName, childrenIds);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void resolveQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws RemoteException {
        try {
            dsSpreadsheetService.resolveQuery(databaseAccessToken,query,languages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    public TreeNode getJstreeDataForOutputUsingNames(DatabaseAccessToken databaseAccessToken, Set<String> nameNames, int maxSize) throws RemoteException {
        try {
            return dsSpreadsheetService.getDataList(jsTreeService.interpretNameFromStrings(databaseAccessToken, nameNames), maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    public TreeNode getJstreeDataForOutputUsingIds(DatabaseAccessToken databaseAccessToken, Set<Integer> nameIds, int maxSize) throws RemoteException {
        try {
            return dsSpreadsheetService.getDataList(jsTreeService.interpretNameFromIds(databaseAccessToken, nameIds), maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }



    @Override
    public List<TreeNode> formatDataRegionProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws RemoteException {
        try {
            return dsSpreadsheetService.getDataRegionProvenance(databaseAccessToken, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, maxSize);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context) throws RemoteException {
        try {
            dsSpreadsheetService.saveData(databaseAccessToken, cellsAndHeadingsForDisplay, user, reportName, context);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void unloadDatabase(String persistenceName) throws RemoteException {
        try {
            memoryDBManager.removeDBfromMap(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean isDatabaseLoaded(String persistenceName) throws RemoteException {
        try {
            return memoryDBManager.isDBLoaded(persistenceName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public int getNameCount(String persistenceName) throws RemoteException {
        try {
            return memoryDBManager.getAzquoMemoryDB(persistenceName, null).getNameCount();
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
        toReturn.append("--- MEMORY USED :  " + nf.format(runtime.totalMemory() - runtime.freeMemory() / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb));
        return toReturn.toString();
    }

    @Override
    public int getValueCount(String persistenceName) throws RemoteException {
        try {
            return memoryDBManager.getAzquoMemoryDB(persistenceName, null).getValueCount();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            return dsSpreadsheetService.getSessionLog(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void clearSessionLog(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            dsSpreadsheetService.clearLog(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws RemoteException {
        try {
            dsSpreadsheetService.sendStopMessageToLog(databaseAccessToken);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws RemoteException {
        try {
            dsAdminService.copyDatabase(source,target,nameList,readLanguages);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String getNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute)throws RemoteException {
        try {
            return jsTreeService.getNameAttribute(databaseAccessToken, nameString, attribute);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void setNameAttribute(DatabaseAccessToken databaseAccessToken, String nameString, String attribute, String attVal)throws RemoteException {
        try {
            jsTreeService.setNameAttribute(databaseAccessToken, nameString, attribute, attVal);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
}