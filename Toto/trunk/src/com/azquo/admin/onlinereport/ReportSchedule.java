package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;

/**
 * Created by edward on 21/09/15.
 *
 */
public class ReportSchedule extends StandardEntity{
    private String period;
    private String recipients;
    private LocalDateTime nextDue;
    private int databaseId;
    private int reportId;
    private String type;
    private String parameters;

    public ReportSchedule(int id, String period, String recipients, LocalDateTime nextDue, int databaseId, int reportId, String type, String parameters) {
        this.id = id;
        this.period = period;
        this.recipients = recipients;
        this.nextDue = nextDue;
        this.databaseId = databaseId;
        this.reportId = reportId;
        this.type = type;
        this.parameters = parameters;
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
}
