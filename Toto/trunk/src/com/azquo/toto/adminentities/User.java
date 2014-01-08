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
    String password;
    String seed;

    public User(int id, boolean active, Date startDate, String email, String name, String status, String password, String seed) {
        this.id = id;
        this.active = active;
        this.startDate = startDate;
        this.email = email;
        this.name = name;
        this.status = status;
        this.password = password;
        this.seed = seed;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSeed() {
        return seed;
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", active=" + active +
                ", startDate=" + startDate +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", password='" + password + '\'' +
                ", seed='" + seed + '\'' +
                '}';
    }
}
