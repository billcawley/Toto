package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Representing access a user can have
 */
public final class Access extends StandardEntity{

    Date startDate;
    Date endDate;
    int userId;
    int databaseId;

    // these two may become arrays later

    String readList;
    String writeList;

    public Access(int id, Date startDate, Date endDate, int userId, int databaseId, String readList, String writeList) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.userId = userId;
        this.databaseId = databaseId;
        this.readList = readList;
        this.writeList = writeList;
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

    @Override
    public String toString() {
        return "Access{" +
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
