package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2018 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 23/10/2018
 * Initially copied from UploadRecord, PendingUpload is made for Ed Broking's query area but might be useful for others so
 * I won't put it in an Ed Broking package for the moment
 * <p>
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `business_id` int(11) NOT NULL,
 `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `processed_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `file_path` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `created_by_user_id` int(11) NOT NULL,
 `processed_by_user_id` int(11) DEFAULT NULL,
 `database_id` int(11) NOT NULL,
 `import_result_path` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 * PRIMARY KEY (`id`)
 */
public final class PendingUpload extends StandardEntity {

    final private int businessId;
    final private LocalDateTime createdDate;
    private LocalDateTime processedDate;
    private String fileName;
    final private String filePath;
    private int createdByUserId;
    private int processedByUserId;
    private int databaseId;
    private String importResultPath;
    private String team;

    public PendingUpload(int id, int businessId, LocalDateTime createdDate, LocalDateTime processedDate, String fileName, String filePath, int createdByUserId, int processedByUserId, int databaseId, String importResultPath, String team) {
        this.id = id;
        this.businessId = businessId;
        this.createdDate = createdDate;
        this.processedDate = processedDate;
        this.fileName = fileName;
        this.filePath = filePath;
        this.createdByUserId = createdByUserId;
        this.processedByUserId = processedByUserId;
        this.databaseId = databaseId;
        this.importResultPath = importResultPath;
        this.team = team;
    }

    public int getBusinessId() {
        return businessId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public LocalDateTime getProcessedDate() {
        return processedDate;
    }

    public int getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(int createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public int getProcessedByUserId() {
        return processedByUserId;
    }

    public void setProcessedByUserId(int processedByUserId) {
        this.processedByUserId = processedByUserId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public String getImportResultPath() {
        return importResultPath;
    }

    public void setImportResultPath(String importResultPath) {
        this.importResultPath = importResultPath;
    }

    public static DateTimeFormatter getDateFormatter() {
        return dateFormatter;
    }

    // should it be cached? Don't know if I care . . .
    public String getSize() {
        try {
            return NumberFormat.getInstance().format(Files.size(Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public void setProcessedDate(LocalDateTime processedDate) {
        this.processedDate = processedDate;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // better for display
    public static class PendingUploadForDisplay {

        final private int id;
        final private String businessName;
        final private String fileName;
        final private String filePath; //maybe use a path??
        final private String databaseName;
        final private String createdDate;
        final private String processedDate;
        final private String createdByUserName;
        final private String processedByUserName;
        final private String importResultPath;
        final private String size;
        final private String team;


        public PendingUploadForDisplay(PendingUpload pu) {
            this.id = pu.id;
            Business byId = BusinessDAO.findById(pu.getBusinessId());
            this.businessName = byId != null ? byId.getBusinessName() : "";
            this.fileName = pu.fileName;
            this.filePath = pu.filePath;
            Database byId2 = DatabaseDAO.findById(pu.getDatabaseId());
            this.databaseName = byId2 != null ? byId2.getName() : "";
            this.createdDate = pu.createdDate != null ?  dateFormatter.format(pu.createdDate) : "";
            this.processedDate = pu.processedDate != null ? dateFormatter.format(pu.processedDate) : "";
            User byId1 = UserDAO.findById(pu.createdByUserId);
            this.createdByUserName = byId1 != null ? byId1.getName() : "";
            byId1 = UserDAO.findById(pu.processedByUserId);
            this.processedByUserName = byId1 != null ? byId1.getName() : "";
            this.importResultPath = pu.importResultPath;
            this.size = pu.getSize();
            this.team = pu.team;
        }

        public int getId() {
            return id;
        }

        public String getBusinessName() {
            return businessName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getCreatedDate() {
            return createdDate;
        }

        public String getProcessedDate() {
            return processedDate;
        }

        public String getCreatedByUserName() {
            return createdByUserName;
        }

        public String getProcessedByUserName() {
            return processedByUserName;
        }

        public String getImportResultPath() {
            return importResultPath;
        }

        public String getSize() {
            return size;
        }

        public boolean getLoaded(){
            return importResultPath != null;
        }
    }


}