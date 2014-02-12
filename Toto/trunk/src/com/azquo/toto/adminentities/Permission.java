package com.azquo.toto.adminentities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * Representing access a user can have
 * Considered immutability but things like dates may be adjusted
 */
public final class Permission extends StandardEntity {

    private Date startDate;
    private Date endDate;
    private int userId;
    int databaseId;

    // these two may become arrays later

    String readList;
    String writeList;

    // used by the excel not the database. Easiest to put here
    String database;
    String email;

    public Permission(int id, Date startDate, Date endDate, int userId, int databaseId, String readList, String writeList) {
        this(id, startDate, endDate, userId, databaseId, readList, writeList,null,null);
    }

    @JsonCreator
    public Permission(@JsonProperty("id") int id, @JsonProperty("startDate") Date startDate, @JsonProperty("endDate") Date endDate, @JsonProperty("userId") int userId,
                  @JsonProperty("databaseId") int databaseId, @JsonProperty("readList") String readList, @JsonProperty("writeList") String writeList,
                  @JsonProperty("database") String database, @JsonProperty("email") String email) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.userId = userId;
        this.databaseId = databaseId;
        this.readList = readList;
        this.writeList = writeList;
        this.database = database;
        this.email = email;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public String getReadList() {
        return readList;
    }

    public void setReadList(String readList) {
        this.readList = readList;
    }

    public String getWriteList() {
        return writeList;
    }

    public void setWriteList(String writeList) {
        this.writeList = writeList;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Permission{" +
                "id=" + id +
                ", endDate=" + endDate +
                ", startDate=" + startDate +
                ", userId=" + userId +
                ", databaseId=" + databaseId +
                ", readList='" + readList + '\'' +
                ", writeList='" + writeList + '\'' +
                '}';
    }


}
