package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**

 Created 12/05/2020 by EFC

 Used to log user activity

 */
public final class ExternalDatabaseConnection extends StandardEntity {

    final private int businessId;
    private String name;
    private String connectionString;
    private String user;
    private String password;
    private String database;

    public ExternalDatabaseConnection(int id, int businessId, String name, String connectionString, String user, String password, String database) {
        this.id = id;
        this.businessId = businessId;
        this.name = name;
        this.connectionString = connectionString;
        this.user = user;
        this.password = password;
        this.database = database;
    }

    public int getBusinessId() {
        return businessId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabase() {
        return database;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}