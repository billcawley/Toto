package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Representing a user who can log in
 */
public final class User extends StandardEntity implements Serializable {

    //ALTER TABLE `user` ADD `database_id` INT(11) NOT NULL DEFAULT '0' , ADD `report_id` INT(11) NOT NULL DEFAULT '0' ;

    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";
    private static final String STATUS_MASTER = "MASTER";

    public static final String STATUS_DEVELOPER = "DEVELOPER";
    //developer means like admin but just reports and databases. Means those tables and the upload tables will need user Id and I'll need to make the right checks . .hhhhhhhhhhhhhhhngh

    private LocalDateTime endDate;
    private int businessId;
    private String email;
    private String name;
    private String status;
    private String password;
    private String salt;
    private String createdBy;
    private int databaseId;
    private int reportId;
    private String selections;
    private String team;

    public User(int id, LocalDateTime endDate, int businessId, String email, String name, String status, String password, String salt, String createdBy, int databaseId, int reportId, String selections, String team) {
        this.id = id;
        this.endDate = endDate;
        this.businessId = businessId;
        this.email = email;
        this.name = name;
        this.status = status;
        this.password = password;
        this.salt = salt;
        this.createdBy = createdBy;
        this.databaseId = databaseId;
        this.reportId = reportId;
        this.selections = selections;
        this.team = team;
    }

    public boolean isAdministrator() {
        return status.equalsIgnoreCase(STATUS_ADMINISTRATOR);
    }

    public boolean isMaster() {
        return status.equalsIgnoreCase(STATUS_MASTER);
    }

    public boolean isDeveloper() {
        return status.equalsIgnoreCase(STATUS_DEVELOPER);
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    // for display
    public String getDatabaseName() {
        final Database byId = DatabaseDAO.findById(databaseId);
        return byId != null ? byId.getName() : "";
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public int getReportId() {
        return reportId;
    }

    // for display
    public String getReportName() {
        final OnlineReport byId = OnlineReportDAO.findById(reportId);
        return byId != null ? byId.getReportName() : "";
    }

    // for display
    public String getBusinessName() {
        Business byId = BusinessDAO.findById(businessId);
        return byId != null ? byId.getBusinessName() : "";
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    public String getSelections(){ return this.selections; }

    public void setSelections(String selections){this.selections = selections; }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", endDate=" + endDate +
                ", businessId=" + businessId +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", password='" + password + '\'' +
                ", salt='" + salt + '\'' +
                ", createdBy='" + createdBy + '\'' +
                ", databaseId=" + databaseId +
                ", reportId=" + reportId +
                ", selections=" + selections +
                '}';
    }
}