package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Representing a user who can log in
 */
public final class User extends StandardEntity {

    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";
    public static final String STATUS_MASTER = "MASTER";

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int businessId;
    private String email;
    private String name;
    private String status;
    private String password;
    private String salt;
    private String createdBy;

    // salt will probably never be passed
    @JsonCreator
    public User(@JsonProperty("id") int id
            , @JsonProperty("startDate") LocalDateTime startDate
            , @JsonProperty("endDate") LocalDateTime endDate
            , @JsonProperty("businessId") int businessId
            , @JsonProperty("email") String email
            , @JsonProperty("name") String name
            , @JsonProperty("status") String status
            , @JsonProperty("password") String password
            , @JsonProperty("salt") String salt
            , @JsonProperty("createdBy") String createdBy) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.businessId = businessId;
        this.email = email;
        this.name = name;
        this.status = status;
        this.password = password;
        this.salt = salt;
        this.createdBy = createdBy;
    }

    @JsonIgnore
    public boolean isAdministrator() {
        return status.equalsIgnoreCase(STATUS_ADMINISTRATOR);
    }

    @JsonIgnore
    public boolean isMaster() {
        return status.equalsIgnoreCase(STATUS_MASTER);
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
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

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @JsonIgnore
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
                "startDate=" + startDate +
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