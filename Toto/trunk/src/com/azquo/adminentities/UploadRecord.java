package com.azquo.adminentities;

import java.util.Date;

/**
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

    // convenience object for returning JSON to Excel, one can build a list of these and let Jackson serialise it

    public static class UploadRecordForDisplay {
        public final Date date;
        public final String businessName;
        public final String databaseName;
        public final String userName;
        public final String fileName;
        public final String fileType;
        public final String comments;

        public UploadRecordForDisplay(UploadRecord ur, String businessName, String databaseName, String userName) {
            date = ur.date;
            this.businessName = businessName;
            this.databaseName = databaseName;
            this.userName = userName;
            fileName = ur.fileName;
            fileType = ur.fileType;
            comments = ur.comments;
        }
    }
}
