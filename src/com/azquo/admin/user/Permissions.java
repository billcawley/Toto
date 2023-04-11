package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Representing a user who can log in
 */
public final class Permissions extends StandardEntity implements Serializable {


    private String roleName;
    private int businessId;
    String fileName;
    String fieldName;
    private boolean readOnly;

    public Permissions(int id, String roleName, int businessId, String fileName, String fieldName, boolean readOnly) {
        this.id = id;
        this.roleName = roleName;
        this.businessId = businessId;
        this.fileName = fileName;
        this.fieldName = fieldName;
        this.readOnly = readOnly;
    }


    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }


    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", roleName=" + roleName +
                ", businessId=" + businessId +
                ", fileName=" + fileName +
                ", fieldName=" + fieldName +
                ", readonly=" + readOnly +
                '}';
    }
}