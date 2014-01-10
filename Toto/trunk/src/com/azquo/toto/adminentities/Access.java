package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Representing access a user can have
 */
public final class Access extends StandardEntity{

    private boolean active;
    Date startDate;
    int userId;
    int databaseId;

    // these two may become arrays later

    String readList;
    String writeList;

    public Access(int id, boolean active, Date startDate, int userId, int databaseId, String readList, String writeList) {
        this.id = id;
        this.active = active;
        this.startDate = startDate;
        this.userId = userId;
        this.databaseId = databaseId;
        this.readList = readList;
        this.writeList = writeList;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
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
                ", active=" + active +
                ", startDate=" + startDate +
                ", userId=" + userId +
                ", databaseId=" + databaseId +
                ", readList='" + readList + '\'' +
                ", writeList='" + writeList + '\'' +
                '}';
    }
}
