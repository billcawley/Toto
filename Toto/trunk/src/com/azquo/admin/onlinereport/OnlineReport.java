package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 15/04/14.
 *
 * All reports are online now, we used to have Excel ones.
 */
public class OnlineReport extends StandardEntity {

    private LocalDateTime dateCreated;
    private int businessId;
    private int userId;
    private String database; // for sending parameters only, I think visually, need to think on this given new structure, TODO
    private String reportName;
    private String filename;
    private String pathName; // runtime use again, need to zap at some point. todo
    private String explanation;

    public OnlineReport(int id
            , LocalDateTime dateCreated
            , int businessId
            , int userId
            , String database
            , String reportName
            , String filename
            , String explanation
            , String pathName
            ) {
        this.id = id;
        this.dateCreated = dateCreated;
        this.businessId = businessId;
        this.userId = userId;
        this.database = database;
        this.reportName = reportName;
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

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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
        return "OnlineReport{" +
                "id=" + id +
                ", dateCreated=" + dateCreated +
                ", businessId=" + businessId +
                ", userId=" + userId +
                ", database='" + database + '\'' +
                ", reportName='" + reportName + '\'' +
                ", filename='" + filename + '\'' +
                ", pathName='" + pathName + '\'' +
                ", explanation='" + explanation + '\'' +
                '}';
    }
}
