package com.azquo.dataimport;


import com.azquo.admin.StandardEntity;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.ExternalDatabaseConnection;
import com.azquo.admin.onlinereport.ExternalDatabaseConnectionDAO;
import com.azquo.admin.onlinereport.FileOutputConfig;
import com.azquo.admin.onlinereport.FileOutputConfigDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;

import javax.xml.crypto.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 15/04/14.
 * <p>
 * All reports are online now, we used to have Excel ones.
 */
public class ImportSchedule extends StandardEntity implements Serializable {

    private String name;
    private int count;
    private String frequency;
    private LocalDateTime nextDate;
    private int businessId;
    private int databaseId;
    private int connectorId;
    private int userId;
    private String sql;
    private String templateName;
    private int outputConnectorId;
    private String notes;

    public ImportSchedule(int id
            , String name
            , int count
            , String frequency
            , LocalDateTime nextDate
            , int businessId
            , int databaseId
            , int connectorId
            , int userId
            , String sql
            , String templateName
            , int outputConnectorId
            , String notes
    ) {
        this.id = id;
        this.name = name;
        this.count = count;
        this.frequency = frequency;
        this.nextDate = nextDate;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.connectorId = connectorId;
        this.userId = userId;
        this.sql = sql;
        this.templateName = templateName;
        this.outputConnectorId = outputConnectorId;
        this.notes = notes;
    }

    public String getName(){return name; }

    public void setName(String name){this.name = name; }

    public int getCount(){return count; }

    public void setCount(int count){this.count = count; }

    public String getFrequency(){return frequency; }

    public void setFrequency(String frequency){this.frequency = frequency; }

    public LocalDateTime getNextDate() {
        return nextDate;
    }

    public void setNextDate(LocalDateTime nextDate) {
        this.nextDate = nextDate;
    }

   public int getBusinessId() {return businessId; }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public int getConnectorId() {
        return connectorId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUser() {
        User u = UserDAO.findById(userId);
        if (u != null){
            return u.getName();
        }
        return "";
    }

    public void setUserId(int userId){
        this.userId = userId;
    }


    public void setConnectorId(int connectorId) {
        this.connectorId = connectorId;
    }

     public String getSql() {
         return sql;
     }

     public void setSql(String sql) {
        this.sql = sql;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public int getOutputConnectorId() {
        return outputConnectorId;
    }


    public void setOutputConnectorId(int outputConnectorId) {
        this.outputConnectorId = outputConnectorId;
    }


    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDatabase(){
        Database database = DatabaseDAO.findById(databaseId);
        if (database==null){
            return "unassigned";
        }else{
            return database.getName();
        }
    }


    @Override
    public String toString() {
        return "OnlineReport{" +
                "id=" + id +
                ", name=" + name +
                ", count=" + count +
                ", nextDate" + nextDate +
                ", businessId=" + businessId +
                ", connectorId=" + connectorId +
                ", sql='" + sql + '\'' +
                ", templatename='" + templateName + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}


