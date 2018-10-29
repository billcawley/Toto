package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2018 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 23/10/2018
 * Initially copied from UploadRecord, PendingUpload is made for Ed Broking's query area but might be useful for others so
 * I won't put it in an Ed Broking package for the moment
 *
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `business_id` int(11) NOT NULL,
 `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `statusChangedDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `file_path` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 `source` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `status` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
 `parameters` text COLLATE utf8_unicode_ci NOT NULL,
 `database_id` int(11) NOT NULL,
 `user_id` int(11) NOT NULL,
 PRIMARY KEY (`id`)
 *
 */
public final class PendingUpload extends StandardEntity {

    public static final String PROVISIONALLY_LOADED = "Provisionally Loaded";
    public static final String WAITING = "Waiting";
    public static final String REJECTED = "Rejected";

    private static final String paramDivider = "↑";

    final private int businessId;
    final private LocalDateTime date;
    private LocalDateTime statusChangedDate;
    final private String fileName;
    final private String filePath; //maybe use a path??
    final private String source;
    private String status;
    private Map<String, String> parameters;
    private int databaseId;
    final private int userId;
    private String importResult;
    private boolean committed;

    public PendingUpload(int id, int businessId, LocalDateTime date, LocalDateTime statusChangedDate, String fileName, String filePath, String source, String status, String parametersString, int databaseId, int userId, String importResult, boolean committed) {
        this.id = id;
        this.businessId = businessId;
        this.date = date;
        this.statusChangedDate = statusChangedDate;
        this.fileName = fileName;
        this.filePath = filePath;
        this.source = source;
        this.status = status;
        String[] paramArray = parametersString.split(paramDivider);
        parameters = new HashMap<>();
        for (int i = 0; i < paramArray.length/2; i++) {
            parameters.put(paramArray[i*2], paramArray[(i*2) + 1]);
        }
        this.databaseId = databaseId;
        this.userId = userId;
        this.importResult = importResult;
        this.committed = committed;
    }

    public int getBusinessId() {
        return businessId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public LocalDateTime getStatusChangedDate() {
        return statusChangedDate;
    }

    public void setStatusChangedDate(LocalDateTime statusChangedDate) {
        this.statusChangedDate = statusChangedDate;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public int getUserId() {
        return userId;
    }

    public String getParametersAsString() {
        if (parameters.isEmpty()){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> stringStringEntry : parameters.entrySet()) {
            sb.append(paramDivider).append(stringStringEntry.getKey()).append(paramDivider).append(stringStringEntry.getValue());
        }
        return sb.substring(1); // knock off the first divider
    }

    public String getImportResult() {
        return importResult;
    }


    public void setImportResult(String importResult) {
        this.importResult = importResult;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public boolean getCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    // better for display
    public static class PendingUploadForDisplay {

        final private int id;
        final private String businessName;
        final private LocalDateTime date;
        final private LocalDateTime statusChangedDate;
        final private String fileName;
        final private String filePath; //maybe use a path??
        final private String source;
        final private String status;
        final private Map<String, String> parameters;
        final private String databaseName;
        final private String userName;
        final private String importResult;


        public PendingUploadForDisplay(PendingUpload pu, String businessName, String databaseName, String userName) {
            this.id = pu.id;
            this.businessName = businessName;
            this.date = pu.date;
            this.statusChangedDate = pu.statusChangedDate;
            this.fileName = pu.fileName;
            this.filePath = pu.filePath;
            this.source = pu.source;
            this.status = pu.status;
            this.parameters = pu.parameters;
            this.databaseName = databaseName;
            this.userName = userName;
            this.importResult = pu.importResult;
        }

        public String getBusinessName() {
            return businessName;
        }

        public LocalDateTime getDate() {
            return date;
        }

        public LocalDateTime getStatusChangedDate() {
            return statusChangedDate;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getSource() {
            return source;
        }

        // perhaps formatting shouldn't be in here
        public String getStatus()
        {
            return status;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getUserName() {
            return userName;
        }

        public String getImportResult() {
            return importResult;
        }

        public int getId() {
            return id;
        }


    }
}