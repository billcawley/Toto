package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Representing a user who can log in
 */
public final class User extends StandardEntity{

    public static final String STATUS_ADMINISTRATOR = "ADMINISTRATOR";

    private Date startDate;
    private Date endDate;
    private int businessId;
    private String email;
    private String name;
    private String status;
    private String password;
    private String salt;

    public User(int id, Date startDate, Date endDate, int businessId, String email, String name, String status, String password, String salt) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.businessId = businessId;
        this.email = email;
        this.name = name;
        this.status = status;
        this.password = password;
        this.salt = salt;
    }

    public boolean isAdministrator(){
        return status.equalsIgnoreCase(STATUS_ADMINISTRATOR);
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt= salt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", businessId=" + businessId +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", password='" + password + '\'' +
                ", salt='" + salt + '\'' +
                '}';
    }
}
