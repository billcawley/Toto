package com.azquo.admin.business;

import com.azquo.admin.StandardEntity;

/**
 * Created by edward on 19/09/16.
 *
 * Parked for the mo while Sterling wait. Configuration to read import files I think.
 */
public class ImportConfig extends StandardEntity {
    private String filename;
    private int databaseId;
    private String dataSet;
    private boolean clear;

    public ImportConfig(int id, String filename, int databaseId, String dataSet, boolean clear) {
        this.id = id;
        this.filename = filename;
        this.databaseId = databaseId;
        this.dataSet = dataSet;
        this.clear = clear;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public String getDataSet() {
        return dataSet;
    }

    public void setDataSet(String dataSet) {
        this.dataSet = dataSet;
    }

    public boolean isClear() {
        return clear;
    }

    public void setClear(boolean clear) {
        this.clear = clear;
    }
}
