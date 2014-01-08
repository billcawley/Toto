package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Representing a user who can log in
 */
public class User extends StandardEntity{

    private boolean active;
    Date startDate;
    String email;
    String name;
    String status;

    public User(int id, boolean active, Date startDate, String email, String name, String status) {
        this.id = id;
        this.active = active;
        this.startDate = startDate;
        this.email = email;
        this.name = name;
        this.status = status;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
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

    @Override
    public String toString() {
        return "User{" +
                "active=" + active +
                ", startDate=" + startDate +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
