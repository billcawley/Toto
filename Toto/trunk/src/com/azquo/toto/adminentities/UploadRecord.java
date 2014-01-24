package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Created by cawley on 09/01/14
 * An entry for each uploaded file
 */
public final class UploadRecord extends StandardEntity {

    private Date date;
    private int businessId;
    private int databaseId;
    private int userId;
    private String fileName;
    private String fileType;
    private String comments;

    public UploadRecord(int id, Date date, int businessId, int databaseId, int userId, String fileName, String fileType, String comments) {
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

    public void setDate(Date date) {
        this.date = date;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
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

    // convenience object for returning JSON to Excel, one can build a list of these and let Jackson take care of it

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
