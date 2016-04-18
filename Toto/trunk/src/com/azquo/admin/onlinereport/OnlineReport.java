package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 15/04/14.
 *
 * All reports are online now, we used to have Excel ones.
 */
public class OnlineReport extends StandardEntity {

//    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";

    private LocalDateTime dateCreated;
    private int businessId;
    private String database; // for sending parameters only, I think visually, need to think on this given new structure, TODO
    private String reportName;
    private String reportCategory;
    private String filename;
    private String pathName; //internal use
    private String explanation;
    private int renderer;
    private boolean active;

    public static final int AZQUO_BOOK = 0;
    public static final int ZK_AZQUO_BOOK = 1;

    @JsonCreator
    public OnlineReport(@JsonProperty("id") int id
            , @JsonProperty("businessId") LocalDateTime dateCreated
            , @JsonProperty("businessId") int businessId
            , @JsonProperty("database") String database
            , @JsonProperty("reportName") String reportName
            , @JsonProperty("reportCategory") String reportCategory
            , @JsonProperty("filename") String filename
            , @JsonProperty("pathName") String pathName
            , @JsonProperty("explanation") String explanation
            , @JsonProperty("renderer") int renderer
            , @JsonProperty("active") boolean active) {
        this.id = id;
        this.dateCreated = dateCreated;
        this.businessId = businessId;
        this.database = database;
        this.reportName = reportName;
        this.reportCategory = reportCategory;
        this.filename = filename;
        this.pathName = pathName;
        this.explanation = explanation;
        this.renderer = renderer;
        this.active = active;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }


    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
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

    public int getRenderer() {
        return renderer;
    }

    public void setRenderer(int renderer) {
        this.renderer = renderer;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "OnlineReport{" +
                "dateCreated=" + dateCreated +
                "businessId=" + businessId +
                ", database='" + database + '\'' +
                ", reportName='" + reportName + '\'' +
                ", reportCategory='" + reportCategory + '\'' +
                ", filename='" + filename + '\'' +
                ", pathName='" + pathName + '\'' +
                ", explanation='" + explanation + '\'' +
                ", renderer=" + renderer +
                ", active=" + active +
                '}';
    }
}
