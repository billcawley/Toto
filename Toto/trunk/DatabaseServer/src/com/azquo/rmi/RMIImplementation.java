package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.springframework.beans.factory.annotation.Autowired;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
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


    public RMIImplementation(DSSpreadsheetService dsSpreadsheetService, DSAdminService dsAdminService, DSDataLoadService dsDataLoadService, DSImportService dsImportService, JSTreeService jsTreeService) {
        this.dsSpreadsheetService = dsSpreadsheetService;
        this.dsAdminService = dsAdminService;
        this.dsDataLoadService = dsDataLoadService;
        this.dsImportService = dsImportService;
        this.jsTreeService = jsTreeService;
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
    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames) throws RemoteException {
        try {
            dsImportService.readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
    // spreadsheet service
    @Override
    public CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, int filterCount, int maxRows, int maxCols, String sortRow, String sortCol, int highlightDays) throws RemoteException {
        try {
            return dsSpreadsheetService.getCellsAndHeadingsForDisplay(databaseAccessToken,rowHeadingsSource,colHeadingsSource,contextSource,filterCount,maxRows,maxCols,sortRow,sortCol, highlightDays);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String processJSTreeRequest(DatabaseAccessToken dataAccessToken, String json, String jsTreeId, String topNode, String op, String parent, String parents, String database, String itemsChosen, String position, String backupSearchTerm) throws RemoteException {
        try {
            return jsTreeService.processRequest(dataAccessToken, json, jsTreeId, topNode, op, parent, parents, database, itemsChosen, position, backupSearchTerm);
        } catch (Exception e) {
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
    public String formatDataRegionProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, String jsonFunction) throws RemoteException {
        try {
            return dsSpreadsheetService.formatDataRegionProvenanceForOutput(databaseAccessToken,rowHeadingsSource,colHeadingsSource,contextSource,unsortedRow,unsortedCol,jsonFunction);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String formatRowHeadingProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource, int unsortedRow, int col, String jsonFunction) throws RemoteException {
        try {
            return dsSpreadsheetService.formatRowHeadingProvenanceForOutput(databaseAccessToken, rowHeadingsSource, unsortedRow, col, jsonFunction);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }

    @Override
    public String formatColumnHeadingProvenanceForOutput(DatabaseAccessToken databaseAccessToken, List<List<String>> columnHeadingsSource, int row, int unsortedCol, String jsonFunction) throws RemoteException {
        try {
            return dsSpreadsheetService.formatRowHeadingProvenanceForOutput(databaseAccessToken,columnHeadingsSource,row,unsortedCol,jsonFunction);
        } catch (Exception e) {
            throw new RemoteException("Database Server Exception", e);
        }
    }
}