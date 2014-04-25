package com.azquo.adminentities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.xml.internal.bind.api.impl.NameConverter;

import java.util.Date;

/**
 * Created by bill on 15/04/14.
 */
public class OnlineReport extends StandardEntity {

    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";

    private int businessId;
    private int databaseId;
    private String database; // for sending parameters only
    private String reportName;
    private String userStatus;
    private String filename;

     @JsonCreator
    public OnlineReport(@JsonProperty("id") int id
            , @JsonProperty("businessId") int businessId
            , @JsonProperty("databaseId") int databaseId
             , @JsonProperty("database") String database
            , @JsonProperty("reportName") String reportName
            , @JsonProperty("userStatus") String userStatus
            , @JsonProperty("filename") String filename) {
        this.id = id;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.database = database;
        this.reportName = reportName;
        this.userStatus = userStatus;
        this.filename = filename;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) { this.databaseId = databaseId; }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getUserStatus() {return userStatus;  }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }


    @Override
    public String toString() {
        return "Report{" +
                "id=" + id +
                ", businessId=" + businessId +
                ", databaseId=" + databaseId +
                ", reportName='" + reportName +'\'' +
                ", userStatus='" + userStatus + '\'' +
                ", filename='" + filename + '\'' +
                '}';
    }
}
