package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

public final class ExternalDataRequest extends StandardEntity {

    final private int reportId;
    private String sheetRangeName;
    private int connectorId;
    private String readSQL;
    private String saveKeyfield;
    private String saveFilename;
    private String saveInsertkey;
    private boolean allowDelete;

    public ExternalDataRequest(int id, int reportId, String sheetRangeName, int connectorId, String readSQL, String saveKeyfield, String saveFilename, String saveInsertkey, boolean allowDelete) {
        this.id = id;
        this.reportId = reportId;
        this.sheetRangeName = sheetRangeName;
        this.connectorId = connectorId;
        this.readSQL = readSQL;
        this.saveKeyfield = saveKeyfield;
        this.saveFilename = saveFilename;
        this.saveInsertkey = saveInsertkey;
        this.allowDelete = allowDelete;
    }

    public int getReportId(){return reportId; }

    public String getSheetRangeName() {
        return sheetRangeName;
    }

    public void setSheetRangeName(String sheetRangeName) {
        this.sheetRangeName = sheetRangeName;
    }

    public int getConnectorId() {
        return connectorId;
    }

    public String getConnectorName(){
        try {
            ExternalDatabaseConnection externalDatabaseConnection = ExternalDatabaseConnectionDAO.findById(connectorId);
            if (externalDatabaseConnection!=null){
                return externalDatabaseConnection.getName();
            }
        }catch(Exception e){
        }
        return null;
    }

    public void setConnectorId(int connectorId) {
        this.connectorId = connectorId;
    }

    public String getReadSQL() {
        return readSQL;
    }

    public void setReadSQL(String readSQL) {
        this.readSQL = readSQL;
    }

    public String getSaveKeyfield() {
        return saveKeyfield;
    }

    public void setSaveKeyfield(String saveKeyfield) {
        this.saveKeyfield = saveKeyfield;
    }

    public String getSaveFilename() {
        return saveFilename;
    }

    public void setSaveFilename(String saveFilename) {
        this.saveFilename = saveFilename;
    }

    public String getSaveInsertkey() {
        return saveInsertkey;
    }

    public String getSaveInsertKeyValue(){
        return saveInsertkey;
    }
    public void setSaveInsertkey(String saveInsertkey) {
        this.saveInsertkey = saveInsertkey;
    }

    public boolean getAllowDelete() {
        return allowDelete;
    }

    public void setAllowDelete(boolean allowDelete) {
        this.allowDelete = allowDelete;
    }

    public String getSaveAllowDelete(){
        if (allowDelete) return "Y";
        return "N";
    }

}

