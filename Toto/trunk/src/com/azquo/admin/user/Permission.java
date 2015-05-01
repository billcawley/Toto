package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Representing access a user can have
 * Considered immutability but things like dates may be adjusted
 */
public final class Permission extends StandardEntity {

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int userId;
    private int databaseId;
    // these two may become arrays later
    private String readList;
    private String writeList;

    @JsonCreator
    public Permission(@JsonProperty("id") int id
            , @JsonProperty("startDate") LocalDateTime startDate
            , @JsonProperty("endDate") LocalDateTime endDate
            , @JsonProperty("userId") int userId
            , @JsonProperty("databaseId") int databaseId
            , @JsonProperty("readList") String readList
            , @JsonProperty("writeList") String writeList
) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.userId = userId;
        this.databaseId = databaseId;
        this.readList = readList;
        this.writeList = writeList;
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


    /**
     * Created by cawley on 29/04/15.
     */
    public static class PermissionForDisplay {

        private final int id;
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final int userId;
        private final int databaseId;
        // these two may become arrays later
        private final String readList;
        private final String writeList;
        private final String databaseName;
        private final String userEmail;

        // todo - maybe move the DAO calls out?
        public PermissionForDisplay(Permission permission, DatabaseDAO databaseDAO, UserDAO userDAO){
            this.id = permission.getId();
            this.startDate = permission.getStartDate();
            this.endDate = permission.getEndDate();
            this.userId = permission.getUserId();
            this.databaseId = permission.getDatabaseId();
            this.readList = permission.getReadList();
            this.writeList = permission.getWriteList();
            Database database = databaseDAO.findById(databaseId);
            if (database != null){
                databaseName = database.getName();
            } else {
                databaseName = null;
            }
            User user = userDAO.findById(userId);
            if (user != null){
                userEmail = user.getEmail();
            } else {
                userEmail = null;
            }
        }

        public int getId() {
            return id;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public int getUserId() {
            return userId;
        }

        public int getDatabaseId() {
            return databaseId;
        }

        public String getReadList() {
            return readList;
        }

        public String getWriteList() {
            return writeList;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getUserEmail() {
            return userEmail;
        }
    }
}
