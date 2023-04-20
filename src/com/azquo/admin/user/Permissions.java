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


    private int businessId;
    private String roleName;
    private String section;
    private String fieldName;
    private String nameOnFile;
    private String fieldType;
    private String fieldValue;
    private String isReadOnly;

    public Permissions(int id, int businessId, String roleName, String section, String fieldName, String nameOnFile, String fieldType, String fieldValue, String isReadOnly) {
        this.id = id;
        this.businessId = businessId;
        this.roleName = roleName;
        this.section = section;
        this.fieldName = fieldName;
        this.nameOnFile = nameOnFile;
        this.fieldType = fieldType;
        this.fieldValue = fieldValue;
        this.isReadOnly = isReadOnly;
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

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getNameOnFile() {
        return nameOnFile;
    }

    public void setNameOnFile(String nameOnFile) {
        this.nameOnFile = nameOnFile;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public String getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(String isReadOnly) {
        this.isReadOnly = isReadOnly;
    }


    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", roleName=" + roleName +
                ", businessId=" + businessId +
                ", section=" + section +
                ", fieldName=" + fieldName +
                ", nameOnFile=" + nameOnFile +
                ", fieldType=" + fieldType +
                ", fieldValue=" + fieldValue +
                ", isReadonly=" + isReadOnly +
                '}';
    }
}