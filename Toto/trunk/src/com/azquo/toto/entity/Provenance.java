package com.azquo.toto.entity;

import com.azquo.toto.memorydb.TotoMemoryDB;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:38
 * Attached to each value including values that have been deleted.
 * I think this is immutable, why change a provenance?
 */
public class Provenance extends TotoMemoryDBEntity {

    private String user;
    private Date timeStamp;
    private String method;
    private String name;

    public Provenance(TotoMemoryDB totoMemoryDB, String user, Date timeStamp, String method, String name) throws Exception {
        this(totoMemoryDB, 0,user, timeStamp, method, name);
    }


    public Provenance(TotoMemoryDB totoMemoryDB, int id, String user, Date timeStamp, String method, String name) throws Exception {
        super(totoMemoryDB, id);
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
    public String toString() {
        return "Provenance{" +
                "user='" + user + '\'' +
                ", timeStamp=" + timeStamp +
                ", method='" + method + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public void addToDb(TotoMemoryDB totoMemoryDB) throws Exception {
        totoMemoryDB.addProvenanceToDb(this);
    }
}
