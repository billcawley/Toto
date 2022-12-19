package com.azquo.admin.onlinereport;

import com.azquo.StringLiterals;
import com.azquo.admin.StandardEntity;
import com.azquo.admin.database.DatabaseDAO;

import java.time.LocalDateTime;
import java.util.Map;

/**

 Created 12/05/2020 by EFC

 Used to log user activity

 */
public final class UserActivity extends StandardEntity {

    final private int businessId;
    final private int databaseId;
    final private String user;
    final private String activity;
    private final Map<String, String> parameters;
    private LocalDateTime timeStamp;

    public UserActivity(int id, int businessId, int databaseId, String user, String activity, Map<String, String> parameters, LocalDateTime timeStamp) {
        this.id = id;
        this.businessId = businessId;
        this.databaseId = databaseId;
        this.user = user;
        this.activity = activity;
        this.parameters = parameters;
        this.timeStamp = timeStamp;
    }

    public UserActivity(int id, int businessId, int databaseId, String user, String activity, Map<String, String> parameters) {
        this(id, businessId, databaseId, user,activity,parameters, LocalDateTime.now());
    }

    public int getBusinessId() {
        return businessId;
    }

    public int getDatabaseId() {return databaseId; }

    public String getUser() {
        return user;
    }

    public String getActivity() {
        return activity;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getParametersForDisplay() {
        StringBuilder stringBuilder = new StringBuilder();
        String link = null;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("link")){
                link = entry.getValue();
            } else {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append("<br/>");
                }
                stringBuilder.append(entry.getKey());
                stringBuilder.append(", ");
                stringBuilder.append(entry.getValue());
            }
        }
        if (link != null){
            return "<a href=\"" + link + "\">" + stringBuilder.toString() + "</a>";
        }
        return stringBuilder.toString();
    }

    public String getParametersForWorkbook() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(",");
                }
                stringBuilder.append(entry.getKey());
                stringBuilder.append(", ");
                stringBuilder.append(entry.getValue());
        }
        return stringBuilder.toString();
    }

    public void setParameter(String key, String value){
        parameters.put(key, value);
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public String getDatabase(){ return DatabaseDAO.findById(databaseId).getName(); }
}