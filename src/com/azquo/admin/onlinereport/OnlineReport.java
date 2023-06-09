package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 15/04/14.
 * <p>
 * All reports are online now, we used to have Excel ones.
 */
public class OnlineReport extends StandardEntity implements Serializable {

    private LocalDateTime dateCreated;
    private int businessId;
    private int userId;
    private String database; // for sending parameters only, I think visually, need to think on this given new structure, TODO
    private String reportName;
    private String filename;
    private String explanation;
    private String category;

    public OnlineReport(int id
            , LocalDateTime dateCreated
            , int businessId
            , int userId
            , String database
            , String reportName
            , String filename
            , String explanation
            , String category
    ) {
        this.id = id;
        this.dateCreated = dateCreated;
        this.businessId = businessId;
        this.userId = userId;
        this.database = database;
        this.reportName = reportName;
        this.filename = filename;
        this.explanation = explanation;
        this.category = category;
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

    public String getFilenameForDisk() {
        if (dateCreated.isAfter(LocalDateTime.of(2016, 10, 13, 0, 0))) { // then use the new convention - this date should be when the code was deployed. Bit of a hack - remove later? Does it matter?
            return id + "-" + filename;
        }
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUntaggedReportName() {
        int pos = reportName.indexOf(" uploaded by ");
        if (pos < 0) return reportName;
        return reportName.substring(0, pos);
    }

    public String getName(){
        return getUntaggedReportName();
    }

    public String getAuthor() {
        int pos = reportName.indexOf(" uploaded by");
        if (pos < 0) return "";
        return reportName.substring(pos + " uploaded by ".length());
    }

    @Override
    public String toString() {
        return "OnlineReport{" +
                "dateCreated=" + dateCreated +
                ", businessId=" + businessId +
                ", userId=" + userId +
                ", database='" + database + '\'' +
                ", reportName='" + reportName + '\'' +
                ", filename='" + filename + '\'' +
                ", explanation='" + explanation + '\'' +
                ", category='" + category + '\'' +
                ", id=" + id +
                '}';
    }
}