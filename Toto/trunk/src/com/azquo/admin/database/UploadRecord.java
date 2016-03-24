package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.util.Date;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 09/01/14
 * An entry for each uploaded file
 * Going to make immutable
 */
public final class UploadRecord extends StandardEntity {

    final private Date date;
    final private int businessId;
    final private int databaseId;
    final private int userId;
    final private String fileName;
    final private String fileType;
    final private String comments;

    public UploadRecord(int id
            , Date date
            , int businessId
            , int databaseId
            , int userId
            , String fileName
            , String fileType
            , String comments) {
        this.id = id;
        this.date = date;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.comments = comments;
    }

    public Date getDate() {
        return date;
    }

    public int getBusinessId() {
        return businessId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public int getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public String getComments() {
        return comments;
    }

    @Override
    public String toString() {
        return "UploadRecord{" +
                "id=" + id +
                ", date=" + date +
                ", businessId=" + businessId +
                ", databaseId=" + databaseId +
                ", userId=" + userId +
                ", fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", comments='" + comments + '\'' +
                '}';
    }

    // Saw for JSON, now JSTL, need the getters

    public static class UploadRecordForDisplay {
        public final Date date;
        final String businessName;
        final String databaseName;
        final String userName;
        final String fileName;
        final String fileType;
        final String comments;

        public UploadRecordForDisplay(UploadRecord ur, String businessName, String databaseName, String userName) {
            this.date = ur.date;
            this.businessName = businessName;
            this.databaseName = databaseName;
            this.userName = userName;
            fileName = ur.fileName;
            fileType = ur.fileType;
            comments = ur.comments;
        }

        public Date getDate() {
            return date;
        }

        public String getBusinessName() {
            return businessName;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getUserName() {
            return userName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFileType() {
            return fileType;
        }

        public String getComments() {
            return comments;
        }
    }
}