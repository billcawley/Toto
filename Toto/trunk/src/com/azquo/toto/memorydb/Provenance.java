package com.azquo.toto.memorydb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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


    // won't have this call the other constructor, does not factor in teh same way now
    public Provenance(final TotoMemoryDB totoMemoryDB, final String user, final Date timeStamp, final String method, final String name, final String rowHeadings,
                      final String columnHeadings, final String context) throws Exception {
        super(totoMemoryDB, 0);
        this.user = user;
        this.timeStamp = timeStamp;
        this.method = method;
        this.name = name;
        this.rowHeadings = rowHeadings;
        this.columnHeadings = columnHeadings;
        this.context = context;
    }


    public Provenance(final TotoMemoryDB totoMemoryDB, final int id, String jsonFromDB) throws Exception {
        super(totoMemoryDB, id);
        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);

        this.user = transport.user;
        this.timeStamp = transport.timeStamp;
        this.method = transport.method;
        this.name = transport.name;
        this.rowHeadings = transport.rowHeadings;
        this.columnHeadings = transport.columnHeadings;
        this.context = transport.context;
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

    /* ok I'm well aware one could just annotate this class but there is a problem : I want it immutable.
    This would mean json using the constructor and given things like the totomemorydb in there this is
    just not going to be elegant, best to just hive the json off to here to be called in the constructor
     */
    private static class JsonTransport{
        public final String user;
        public final Date timeStamp;
        public final String method;
        public final String name;
        public final String rowHeadings;
        public final String columnHeadings;
        public final String context;
        @JsonCreator
        private JsonTransport(@JsonProperty("user") String user, @JsonProperty("timeStamp") Date timeStamp, @JsonProperty("method") String method, String name,
                              @JsonProperty("rowHeadings") String rowHeadings, @JsonProperty("columnHeadings") String columnHeadings, @JsonProperty("context") String context) {
            this.user = user;
            this.timeStamp = timeStamp;
            this.method = method;
            this.name = name;
            this.rowHeadings = rowHeadings;
            this.columnHeadings = columnHeadings;
            this.context = context;
        }
    }

    public String getAsJson() {
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(user,timeStamp,method,name,rowHeadings,columnHeadings,context));
        } catch (Exception e){
            e.printStackTrace();
        }
        return "";
    }



}
