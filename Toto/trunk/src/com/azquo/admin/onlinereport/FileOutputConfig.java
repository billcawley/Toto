package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

/**

 created 26/10/22 by EFC

 */
public final class FileOutputConfig extends StandardEntity {

    final private int businessId;
    private String name;
    private String connectionString;
    private String user;
    private String password;

    public FileOutputConfig(int id, int businessId, String name, String connectionString, String user, String password) {
        this.id = id;
        this.businessId = businessId;
        this.name = name;
        this.connectionString = connectionString;
        this.user = user;
        this.password = password;
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

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}