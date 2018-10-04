package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by Cawley on 08/01/14.
 * <p/>
 * Representing information on the report server about an Azquo memory database.
 */
public final class Database extends StandardEntity implements Serializable {

    private int businessId;
    private int userId;
    private String name;
    private String persistenceName;
    private String databaseType;
    private int nameCount;
    private int valueCount;
    private int databaseServerId;
    private final LocalDateTime created;
    private String lastProvenance;
    private boolean autoBackup;


    public Database(int id
            , int businessId
            , int userId
            , String name
            , String persistenceName
            , String databaseType
            , int nameCount
            , int valueCount
            , int databaseServerId
            , LocalDateTime created, String lastProvenance, boolean autoBackup) {
        this.lastProvenance = lastProvenance;
        this.autoBackup = autoBackup;
        this.id = id;
        this.businessId = businessId;
        this.userId = userId;
        this.name = name;
        this.persistenceName = persistenceName;
        this.databaseType = databaseType;
        this.nameCount = nameCount;
        this.valueCount = valueCount;
        this.databaseServerId = databaseServerId;
        this.created = created != null ? created : LocalDateTime.now();
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

    public String getName() {
        return name;
    }

    public String getUrlEncodedName() {
        if (name != null) {
            try {
                URLEncoder.encode(name, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPersistenceName() {
        return persistenceName;
    }

    public void setPersistenceName(String persistenceName) {
        this.persistenceName = persistenceName;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public int getNameCount() {
        return nameCount;
    }

    public void setNameCount(int nameCount) {
        this.nameCount = nameCount;
    }

    public int getValueCount() {
        return valueCount;
    }

    public void setValueCount(int valueCount) {
        this.valueCount = valueCount;
    }

    public int getDatabaseServerId() {
        return databaseServerId;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public String getLastProvenance() {
        return lastProvenance;
    }

    public void setLastProvenance(String lastProvenance) {
        this.lastProvenance = lastProvenance;
    }

    public boolean getAutoBackup() {
        return autoBackup;
    }

    public void setAutoBackup(boolean autoBackup) {
        this.autoBackup = autoBackup;
    }

    @Override
    public String toString() {
        return "Database{" +
                "businessId=" + businessId +
                ", userId=" + userId +
                ", name='" + name + '\'' +
                ", persistenceName='" + persistenceName + '\'' +
                ", databaseType='" + databaseType + '\'' +
                ", nameCount=" + nameCount +
                ", valueCount=" + valueCount +
                ", databaseServerId=" + databaseServerId +
                '}';
    }
}