package com.azquo.adminentities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Representing access a user can have
 * Considered immutability but things like dates may be adjusted
 */
public final class Permission extends StandardEntity {

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int userId;
    int databaseId;
    // these two may become arrays later
    String readList;
    String writeList;
    // used by the excel not the database. Easiest to put here
    String database;
    String email;

    // the normal use constructor, does not use the database and email fields which are there for the excel and are not persisted

    public Permission(int id, LocalDateTime startDate, LocalDateTime endDate, int userId, int databaseId, String readList, String writeList) {
        this(id, startDate, endDate, userId, databaseId, readList, writeList, null, null);
    }

    @JsonCreator
    public Permission(@JsonProperty("id") int id
            , @JsonProperty("startDate") LocalDateTime startDate
            , @JsonProperty("endDate") LocalDateTime endDate
            , @JsonProperty("userId") int userId
            , @JsonProperty("databaseId") int databaseId
            , @JsonProperty("readList") String readList
            , @JsonProperty("writeList") String writeList
            , @JsonProperty("database") String database
            , @JsonProperty("email") String email) {
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

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
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
