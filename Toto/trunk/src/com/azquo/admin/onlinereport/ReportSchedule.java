package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by edward on 21/09/15.
 * <p>
 * Schedule for sending reports.
 */
public class ReportSchedule extends StandardEntity {

    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String period;
    private String recipients;
    private LocalDateTime nextDue;
    private int databaseId;
    private int reportId;
    private String type;
    private String parameters;
    private String emailSubject;

    public ReportSchedule(int id, String period, String recipients, LocalDateTime nextDue, int databaseId, int reportId, String type, String parameters, String emailSubject) {
        this.id = id;
        this.period = period;
        this.recipients = recipients;
        this.nextDue = nextDue;
        this.databaseId = databaseId;
        this.reportId = reportId;
        this.type = type;
        this.parameters = parameters;
        this.emailSubject = emailSubject;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public LocalDateTime getNextDue() {
        return nextDue;
    }

    // putting this in here is easier for the jsp to use it as a property
    public String getNextDueFormatted() {
        return dateFormatter.format(nextDue);
    }

    public void setNextDue(LocalDateTime nextDue) {
        this.nextDue = nextDue;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }
}
