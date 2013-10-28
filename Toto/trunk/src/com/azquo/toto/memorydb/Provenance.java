package com.azquo.toto.memorydb;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:38
 * Attached to each value including values that have been deleted.
 * I think this is immutable, why change a provenance?
 */
public final class Provenance extends TotoMemoryDBEntity {

    private String user;
    private Date timeStamp;
    private String method;
    private String name;
    private String rowHeadings;
    private String columnHeadings;
    private String context;

    public Provenance(TotoMemoryDB totoMemoryDB, String user, Date timeStamp, String method, String name, String rowHeadings, String columnHeadings, String context) throws Exception {
        this(totoMemoryDB, 0,user, timeStamp, method, name, rowHeadings, columnHeadings, context);
    }


    public Provenance(TotoMemoryDB totoMemoryDB, int id, String user, Date timeStamp, String method, String name, String rowHeadings, String columnHeadings, String context) throws Exception {
        super(totoMemoryDB, id);
        this.user = user;
        this.timeStamp = timeStamp;
        this.method = method;
        this.name = name;
        this.rowHeadings = rowHeadings;
        this.columnHeadings = columnHeadings;
        this.context = context;
    }

    @Override
    public void addToDb() throws Exception {
        getTotoMemoryDB().addProvenanceToDb(this);
    }

    @Override
    protected void needsPersisting() {
        getTotoMemoryDB().setProvenanceNeedsPersisting(this);
    }

    @Override
    protected void classSpecificPersisted() {
        getTotoMemoryDB().removeProvenanceNeedsPersisting(this);
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

    public String getRowHeadings() {
        return rowHeadings;
    }

    public String getColumnHeadings() {
        return columnHeadings;
    }

    public String getContext() {
        return context;
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
