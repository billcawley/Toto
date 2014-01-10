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

    private final String user;
    private final Date timeStamp;
    private final String method;
    private final String name;
    private final String rowHeadings;
    private final String columnHeadings;
    private final String context;

    public Provenance(final TotoMemoryDB totoMemoryDB, final String user, final Date timeStamp, final String method, final String name, final String rowHeadings,
                      final String columnHeadings, final String context) throws Exception {
        this(totoMemoryDB, 0,user, timeStamp, method, name, rowHeadings, columnHeadings, context);
    }


    public Provenance(final TotoMemoryDB totoMemoryDB, final int id, final String user, final Date timeStamp, final String method, final String name,
                      final String rowHeadings, final String columnHeadings, final String context) throws Exception {
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
    protected void setNeedsPersisting() {
        getTotoMemoryDB().setProvenanceNeedsPersisting(this);
    }

    @Override
    protected void classSpecificSetAsPersisted() {
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
