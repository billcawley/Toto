package com.azquo.dataimport;

import com.azquo.admin.StandardEntity;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 15/04/14.
 * <p>
 * All reports are online now, we used to have Excel ones.
 */
public class ImportTemplate extends StandardEntity implements Serializable {

    private LocalDateTime dateCreated;
    private int businessId;
    private int userId;
    private String templateName;
    private String filename;
    private String notes;

    public ImportTemplate(int id
            , LocalDateTime dateCreated
            , int businessId
            , int userId
            , String templateName
            , String filename
            , String notes
    ) {
        this.id = id;
        this.dateCreated = dateCreated;
        this.businessId = businessId;
        this.userId = userId;
        this.templateName = templateName;
        this.filename = filename;
        this.notes = notes;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUser() {
        User u = UserDAO.findById(userId);
        if (u != null){
            return u.getName();
        }
        return null;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getFilename() {
        return filename;
    }

    public String getFilenameForDisk() {
            return id + "-" + filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    @Override
    public String toString() {
        return "OnlineReport{" +
                "id=" + id +
                ", dateCreated=" + dateCreated +
                ", businessId=" + businessId +
                ", userId=" + userId +
                ", templateName='" + templateName + '\'' +
                ", filename='" + filename + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}