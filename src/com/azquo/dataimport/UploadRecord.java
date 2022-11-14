package com.azquo.dataimport;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 09/01/14
 * An entry for each uploaded file
 */
public final class UploadRecord extends StandardEntity {

    final private LocalDateTime date;
    final private int businessId;
    final private int databaseId;
    final private int userId;
    final private String fileName;
    final private String fileType;
    final private String comments;
    private String tempPath;// where the file might still be!
    private String userComment;

    public UploadRecord(int id, LocalDateTime date, int businessId, int databaseId, int userId, String fileName, String fileType, String comments, String tempPath, String userComment) {
        this.id = id;
        this.date = date;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.comments = comments;
        this.tempPath = tempPath;
        this.userComment = userComment;
    }

    public LocalDateTime getDate() {
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

    public String getTempPath() {
        return tempPath;
    }

    public void setTempPath(String tempPath) {
        this.tempPath = tempPath;
    }

    public String getUserComment() {
        return userComment;
    }

    public void setUserComment(String userComment) {
        this.userComment = userComment;
    }

    @Override
    public String toString() {
        return "UploadRecord{" +
                "date=" + date +
                ", businessId=" + businessId +
                ", databaseId=" + databaseId +
                ", userId=" + userId +
                ", fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", comments='" + comments + '\'' +
                ", tempPath='" + tempPath + '\'' +
                ", userComment='" + userComment + '\'' +
                ", id=" + id +
                '}';
    }

    // Saw for JSON, now JSTL, need the getters

    public static class UploadRecordForDisplay {
        public final int id;
        public final LocalDateTime date;
        final String businessName;
        final String databaseName;
        final String userName;
        final String fileName;
        int count;
        final String fileType;
        final String comments;
        final boolean downloadable;
        final String userComment;

        public UploadRecordForDisplay(UploadRecord ur, String businessName, String databaseName, String userName, boolean downloadable) {
            this.id = ur.id;
            this.date = ur.date;
            this.businessName = businessName;
            this.databaseName = databaseName;
            this.userName = userName;
            fileName = ur.fileName;
            this.count = 1;
            fileType = ur.fileType;
            comments = ur.comments != null ? ur.getComments() : ""; // might NPE
            this.downloadable = downloadable;
            this.userComment = ur.userComment != null ? ur.userComment : "";
        }

        public LocalDateTime getDate() {
            return date;
        }

        static DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

        static DateTimeFormatter df2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        public String getFormattedDate() {
            return df.format(date);
        }

        public String getTextOrderedDate() {
            return df2.format(date);
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

        public int getCount() {return count; }

        public void setCount(int count) {this.count = count; }

        public String getFileType() {
            return fileType;
        }

        public String getComments() {
            return comments;
        }

        public String getUserComment() {
            return userComment;
        }

        public boolean getDownloadable() {
            return downloadable;
        }

        public int getId() {
            return id;
        }
    }
}