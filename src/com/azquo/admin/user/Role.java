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
public final class Role extends StandardEntity implements Serializable {


    private int businessId;
    private String roleName;
    private String jsonDetails;

    public Role(int id, int businessId, String roleName, String jsonDetails) {
        this.id = id;
        this.businessId = businessId;
        this.roleName = roleName;
        this.jsonDetails = jsonDetails;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getJsonDetails() {
        return jsonDetails;
    }

    public void setJsonDetails(String jsonDetails) {
        this.jsonDetails = jsonDetails;
    }


    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", businessId=" + businessId +
                ", roleName=" + roleName +
                ", jsonDetails=" + jsonDetails +
                 '}';
    }
}