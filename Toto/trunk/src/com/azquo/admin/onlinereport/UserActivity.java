package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardEntity;

import java.util.Map;

/**

 Created 12/05/2020 by EFC

 Used to log user activity

 */
public final class UserActivity extends StandardEntity {

    final private int businessId;
    final private String user;
    final private String activity;
    private final Map<String, String> parameters;

    public UserActivity(int id, int businessId, String user, String activity, Map<String, String> parameters) {
        this.id = id;
        this.businessId = businessId;
        this.user = user;
        this.activity = activity;
        this.parameters = parameters;
    }

    public int getBusinessId() {
        return businessId;
    }

    public String getUser() {
        return user;
    }

    public String getActivity() {
        return activity;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameter(String key, String value){
        parameters.put(key, value);
    }
}