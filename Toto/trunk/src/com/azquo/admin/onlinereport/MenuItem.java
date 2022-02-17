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
public class MenuItem extends StandardEntity implements Serializable {

    private LocalDateTime dateCreated;
    private int businessId;
    private int reportId;
    private String submenuName;
    private String menuItemName;
    private String explanation;
    private String iFrame;
    private int position;
    private int databaseID;

    public MenuItem(int id
            , LocalDateTime dateCreated
            , int businessId
            , int reportId
            , String submenuName
            , String menuItemName
            , String explanation
             ,String iFrame
            ,int position
            , int databaseID
    ) {
        this.id = id;
        this.dateCreated = dateCreated;
        this.businessId = businessId;
        this.reportId = reportId;
        this.submenuName = submenuName;
        this.menuItemName = menuItemName;
        this.explanation = explanation;
        this.iFrame = iFrame;
        this.position = position;
        this.databaseID = databaseID;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getSubmenuName() {   return submenuName;    }

    public void setSubmenuName(String submenuName) {
        this.submenuName = submenuName;
    }

    public String getMenuItemName() {   return menuItemName;    }

    public void setMenuItemName(String menuItemName) {
        this.menuItemName = menuItemName;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getIframe() {
        return iFrame;
    }

    public void setiFrame(String iFrame) {
        this.iFrame = iFrame;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getDatabaseID() {
        return databaseID;
    }

    public void setDatabaseID(int databaseID) {
        this.databaseID = databaseID;
    }


    @Override
    public String toString() {
        return "MenuItem{" +
                "dateCreated=" + dateCreated +
                ", businessId=" + businessId +
                ", reportId=" + reportId +
                ", submenuName='" + submenuName + '\'' +
                ", menuItemName='" + menuItemName + '\'' +
                ", explanation='" + explanation + '\'' +
                ", position='" + position + '\'' +
                ", databaseId='" + databaseID + '\'' +
                ", id=" + id +
                '}';
    }
}