package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Representing a user who can log in
 *
 * Why do we have json properties? A hangover? The old UI??
 */
public final class User extends StandardEntity {

    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";
    private static final String STATUS_MASTER = "MASTER";

    private static final String STATUS_DEVELOPER = "DEVELOPER";
    //developer means like admin but just reports and databases. Means those tables and the upload tables will need user Id and I'll need to make the right checks . .hhhhhhhhhhhhhhhngh

    private LocalDateTime endDate;
    private int businessId;
    private String email;
    private String name;
    private String status;
    private String password;
    private String salt;
    private String createdBy;

    // salt will probably never be passed
    public User(int id
            , LocalDateTime endDate
            , int businessId
            , String email
            , String name
            , String status
            , String password
            , String salt
            , String createdBy) {
        this.id = id;
        this.endDate = endDate;
        this.businessId = businessId;
        this.email = email;
        this.name = name;
        this.status = status;
        this.password = password;
        this.salt = salt;
        this.createdBy = createdBy;
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

    @Override
    public String toString() {
        return "User{" +
                ", endDate=" + endDate +
                ", businessId=" + businessId +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", password='" + password + '\'' +
                ", salt='" + salt + '\'' +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }
}