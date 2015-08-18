package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.MemoryDBManager;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;

import java.rmi.RemoteException;
import java.util.List;

/**
 * Created by cawley on 20/05/15.
 *
 */
public class RMIImplementation implements RMIInterface {

    private final DSSpreadsheetService dsSpreadsheetService;
    private final DSAdminService dsAdminService;
    private final DSDataLoadService dsDataLoadService;
    private final DSImportService dsImportService;
    private final JSTreeService jsTreeService;
    private final MemoryDBManager memoryDBManager;


    public RMIImplementation(DSSpreadsheetService dsSpreadsheetService, DSAdminService dsAdminService, DSDataLoadService dsDataLoadService
            , DSImportService dsImportService, JSTreeService jsTreeService, MemoryDBManager memoryDBManager) {
        this.dsSpreadsheetService = dsSpreadsheetService;
        this.dsAdminService = dsAdminService;
        this.dsDataLoadService = dsDataLoadService;
        this.dsImportService = dsImportService;
        this.jsTreeService = jsTreeService;
        this.memoryDBManager = memoryDBManager;
    }

    //Admin stuf
    @Override
    public void emptyDatabase(String mysqlName) throws RemoteException {
        try {
            dsAdminService.emptyDatabase(mysqlName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);// I think this is reasonable for the mo?
        }
    }

    @Override
    public void dropDatabase(String mysqlName) throws RemoteException {
        try {
            dsAdminService.dropDatabase(mysqlName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void createDatabase(String mysqlName) throws RemoteException {
        try {
            dsAdminService.createDatabase(mysqlName);
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
    public void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress) throws RemoteException {
        try {
            dsDataLoadService.loadData(databaseAccessToken, filePath, remoteAddress);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
    // import
    @Override
    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user) throws RemoteException {
        try {
            dsImportService.readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames, user);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
    // spreadsheet service
    @Override
    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int filterCount, int maxRows, int maxCols, String sortRow,
                                                                    boolean sortRowAsc, String sortCol, boolean sortColumnAsc, int highlightDays) throws RemoteException {
        try {
            return dsSpreadsheetService.getCellsAndHeadingsForDisplay(databaseAccessToken, rowHeadingsSource, colHeadingsSource, contextSource, filterCount, maxRows, maxCols, sortRow, sortRowAsc, sortCol, sortColumnAsc, highlightDays);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String processJSTreeRequest(DatabaseAccessToken dataAccessToken, String json, String jsTreeId, String topNode, String op, String parent, boolean parents, String itemsChosen, String position, String backupSearchTerm, String language) throws RemoteException {
        try {
            return jsTreeService.processRequest(dataAccessToken, json, jsTreeId, topNode, op, parent, parents, itemsChosen, position, backupSearchTerm, language);
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


    public TreeNode formatJstreeDataForOutput(DatabaseAccessToken databaseAccessToken, String jsTreeString, int maxSize) throws RemoteException {
        try {
              return dsSpreadsheetService.getDataList(jsTreeService.interpretNameString(databaseAccessToken, jsTreeString), maxSize);
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
            dsSpreadsheetService.saveData(databaseAccessToken,cellsAndHeadingsForDisplay, user, reportName, context);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public void unloadDatabase(String mysqlName) throws RemoteException {
        try {
            memoryDBManager.removeDBfromMap(mysqlName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public boolean isDatabaseLoaded(String mysqlName) throws RemoteException {
        try {
            return memoryDBManager.isDBLoaded(mysqlName);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public int getNameCount(String mysqlName) throws RemoteException {
        try {
            return memoryDBManager.getAzquoMemoryDB(mysqlName, null).getNameCount();
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public int getValueCount(String mysqlName) throws RemoteException {
        try {
            return memoryDBManager.getAzquoMemoryDB(mysqlName, null).getValueCount();
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

    //memoryDBManager.getAzquoMemoryDB(databaseAccessT)
}