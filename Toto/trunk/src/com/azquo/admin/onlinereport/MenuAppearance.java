package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**

 Created 12/05/2020 by EFC

 Used to log user activity

 */
public final class MenuAppearance extends StandardEntity {

    final private int businessId;
    final private int reportId;
    private String submenuName;
    private int importance;
    private String showname;

    public MenuAppearance(int id, int businessId, int reportId, String submenuName, int importance, String showname) {
        this.id = id;
        this.businessId = businessId;
        this.reportId = reportId;
        this.submenuName = submenuName;
        this.importance = importance;
        this.showname = showname;
     }

    public int getBusinesId(){return businessId; }

    public int getReportId(){return reportId; }

    public String getSubmenuName() {
        return submenuName;
    }

    public void setSubmenuName(String submenuName) {
        this.submenuName = submenuName;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = importance;
    }

   public String getShowname() {
        return showname;
    }

    public void setShowname(String showname) {
        this.showname = showname;
    }


}