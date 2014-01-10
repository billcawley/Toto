package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Created by cawley on 09/01/14.
 */
public final class UploadRecord extends StandardEntity{

    private Date date;
    private int businnessId;
    private int databaseId;
    private int userId;
    private String fileName;
    private String fileType;
    private String comments;

    public UploadRecord(int id, Date date, int businnessId, int databaseId, int userId, String fileName, String fileType, String comments) {
        this.id = id;
        this.date = date;
        this.businnessId = businnessId;
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

    public int getBusinnessId() {
        return businnessId;
    }

    public void setBusinnessId(int businnessId) {
        this.businnessId = businnessId;
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
                ", businnessId=" + businnessId +
                ", databaseId=" + databaseId +
                ", userId=" + userId +
                ", fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", comments='" + comments + '\'' +
                '}';
    }
}
