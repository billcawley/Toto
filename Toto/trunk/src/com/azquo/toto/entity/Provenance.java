package com.azquo.toto.entity;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:38
 * Attached to each value including values that have been deleted. Should enable rollback
 */
public class Provenance extends StandardEntity{

    private String user;
    private Date timeStamp;
    private String method;
    private String name;


    public Provenance(int id, String user, Date timeStamp, String method, String name) {
        this.id = id;
        this.user = user;
        this.timeStamp = timeStamp;
        this.method = method;
        this.name = name;
    }

    public String getUser() {
        return user;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public String getMethod() {
        return method;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Provenance that = (Provenance) o;

        if (method != null ? !method.equals(that.method) : that.method != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (timeStamp != null ? !timeStamp.equals(that.timeStamp) : that.timeStamp != null) return false;
        if (user != null ? !user.equals(that.user) : that.user != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = user != null ? user.hashCode() : 0;
        result = 31 * result + (timeStamp != null ? timeStamp.hashCode() : 0);
        result = 31 * result + (method != null ? method.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Provenance{" +
                "user='" + user + '\'' +
                ", timeStamp=" + timeStamp +
                ", method='" + method + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
