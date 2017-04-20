package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 09/01/14
 * An entry for each uploaded file
 */
public final class UploadRecord extends StandardEntity {

    final private Date date;
    final private int businessId;
    final private int databaseId;
    final private int userId;
    final private String fileName;
    final private String fileType;
    final private String comments;
    private String tempPath;// where the file might still be!

    public UploadRecord(int id, Date date, int businessId, int databaseId, int userId, String fileName, String fileType, String comments, String tempPath) {
        this.id = id;
        this.date = date;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.comments = comments;
        this.tempPath = tempPath;
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

    public String getTempPath() {
        return tempPath;
    }

    public void setTempPath(String tempPath) {
        this.tempPath = tempPath;
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
        public final int id;
        public final Date date;
        final String businessName;
        final String databaseName;
        final String userName;
        final String fileName;
        final String fileType;
        final String comments;
        final boolean downloadable;
        final boolean setup;

        public UploadRecordForDisplay(UploadRecord ur, String businessName, String databaseName, String userName, boolean downloadable, boolean setup) {
            this.id = ur.id;
            this.date = ur.date;
            this.businessName = businessName;
            this.databaseName = databaseName;
            this.userName = userName;
            fileName = ur.fileName;
            fileType = ur.fileType;
            comments = ur.comments;
            this.downloadable = downloadable;
            this.setup = setup;
        }

        public Date getDate() {
            return date;
        }

        static SimpleDateFormat df = new SimpleDateFormat("yyMMdd-hh:mm");

        public String getFormattedDate() {
            return df.format(date);
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

        public boolean getDownloadable() {
            return downloadable;
        }

        public boolean getSetup() {
            return setup;
        }

        public int getId() {
            return id;
        }
    }
}