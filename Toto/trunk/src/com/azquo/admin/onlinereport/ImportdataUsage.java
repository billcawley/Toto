package com.azquo.admin.onlinereport;

import com.azquo.StringLiterals;
import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**

 Created 12/05/2020 by EFC

 Used to log user activity

 */
public final class ImportdataUsage extends StandardEntity {

    final private int businessId;
    final private String importdataName;
    final private int reportId;

    public ImportdataUsage(int id, int businessId, String importdataName, int reportId) {
        this.id = id;
        this.businessId = businessId;
        this.importdataName = importdataName;
        this.reportId = reportId;
    }

     public int getReportId() {
        return reportId;
    }


    public int getBusinessId() {
        return businessId;
    }


    public String getImportdataName() {
        return importdataName;
    }

  }