package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by bill on 15/04/14.
 *
 */
public class OnlineReport extends StandardEntity {

//    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";

    private int businessId;
    private int databaseId;
    private String database; // for sending parameters only
    private String reportName;
    private String reportCategory;
    private String userStatus;
    private String filename;
    private String pathName; //internal use
    private String explanation;

    @JsonCreator
    public OnlineReport(@JsonProperty("id") int id
            , @JsonProperty("businessId") int businessId
            , @JsonProperty("databaseId") int databaseId
            , @JsonProperty("database") String database
            , @JsonProperty("reportName") String reportName
            , @JsonProperty("reportCategory") String reportCategory
            , @JsonProperty("userStatus") String userStatus
            , @JsonProperty("filename") String filename
            , @JsonProperty("pathName") String pathName
            , @JsonProperty("explanation") String explanation) {
        this.id = id;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.database = database;
        this.reportName = reportName;
        this.reportCategory = reportCategory;
        this.userStatus = userStatus;
        this.filename = filename;
        this.pathName = pathName;
        this.explanation = explanation;
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

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

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

    public String getReportCategory() { return reportCategory;  }

    public void setReportCategory(String reportCategory) {
        this.reportCategory = reportCategory;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPathname() {
        return pathName;
    }

    public void setPathname(String pathName) {
        this.pathName = pathName;
    }


    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    @Override
    public String toString() {
        return "Report{" +
                "id=" + id +
                ", businessId=" + businessId +
                ", databaseId=" + databaseId +
                ", reportName='" + reportName + '\'' +
                ", reportCategory'" + reportCategory + '\'' +
                ", userStatus='" + userStatus + '\'' +
                ", filename='" + filename + '\'' +
                ", explanation='" + explanation + '\'' +
                '}';
    }
}
