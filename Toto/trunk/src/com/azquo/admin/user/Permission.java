package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Representing access a user can have
 * Considered immutability but things like dates may be adjusted
 */
public final class Permission extends StandardEntity {

    private int userId;
    private int reportId;
    private int databaseId;
    // these two may become arrays later
    private String readList;
    private String writeList;

    @JsonCreator
    public Permission(@JsonProperty("id") int id
            , @JsonProperty("reportId") int reportId
            , @JsonProperty("userId") int userId
            , @JsonProperty("databaseId") int databaseId
            , @JsonProperty("readList") String readList
            , @JsonProperty("writeList") String writeList
) {
        this.id = id;
        this.reportId = reportId;
        this.userId = userId;
        this.databaseId = databaseId;
        this.readList = readList;
        this.writeList = writeList;
    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
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
                ", userId=" + userId +
                ", reportId=" + reportId +
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
        private final int userId;
        private final int reportId;
        private final int databaseId;
        // these two may become arrays later
        private final String readList;
        private final String writeList;
        private final String databaseName;
        private final String userEmail;
        private final String reportName;
        // todo - maybe move the DAO calls out?
        public PermissionForDisplay(Permission permission){
            this.id = permission.getId();
            this.userId = permission.getUserId();
            this.reportId = permission.getReportId();
            this.databaseId = permission.getDatabaseId();
            this.readList = permission.getReadList();
            this.writeList = permission.getWriteList();
            Database database = DatabaseDAO.findById(databaseId);
            if (database != null){
                databaseName = database.getName();
            } else {
                databaseName = null;
            }
            User user = UserDAO.findById(userId);
            if (user != null){
                userEmail = user.getEmail();
            } else {
                userEmail = null;
            }
            OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
            if (onlineReport != null){
                reportName = onlineReport.getReportName();
            } else {
                reportName = null;
            }
        }

        public int getId() {
            return id;
        }

        public int getUserId() {
            return userId;
        }

        public int getReportId() {
            return reportId;
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

        public String getReportName() {
            return reportName;
        }
    }
}
