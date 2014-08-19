package com.azquo.memorydb;

import com.azquo.memorydbdao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:38
 * Attached to each value including values that have been deleted.
 * I think this is immutable, pleasing
 */
public final class Provenance extends AzquoMemoryDBEntity {

    private static final Logger logger = Logger.getLogger(Provenance.class);

    private final String user;
    private Date timeStamp;
    String method;
    String name;
    private String rowHeadings;
    private String columnHeadings;
    private String context;


    // won't have this call the other constructor, does not factor in the same way now
    // this is the practical use constructor, calling it adds it to the memory db
    public Provenance(final AzquoMemoryDB azquoMemoryDB
            , final String user
            , final Date timeStamp
            , final String method
            , final String name
            , final String rowHeadings
            , final String columnHeadings
            , final String context) throws Exception {
        super(azquoMemoryDB, 0);
        this.user = user;
        this.timeStamp = timeStamp;
        this.method = method;
        this.name = name;
        this.rowHeadings = rowHeadings;
        this.columnHeadings = columnHeadings;
        this.context = context;
    }

    // protected as only to be called by azquo memorydb, populates from JSON

    protected Provenance(final AzquoMemoryDB azquoMemoryDB, final int id, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);

        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
        this.user = transport.user;
        this.timeStamp = transport.timeStamp;
        this.method = transport.method;
        this.name = transport.name;
        this.rowHeadings = transport.rowHeadings;
        this.columnHeadings = transport.columnHeadings;
        this.context = transport.context;
        getAzquoMemoryDB().addProvenanceToDb(this);
    }

    public String getUser() {
        return user;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(){
        this.timeStamp = new Date();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method){ this.method = method; }

    public String getName() {
        return name;
    }

    public void  setName(String name){ this.name = name; }

    public String getRowHeadings() {
        return rowHeadings;
    }

    public void setRowHeadings(String rowHeadings){
        this.rowHeadings = rowHeadings;
    }

    public String getColumnHeadings() {
        return columnHeadings;
    }

    public void setColumnHeadings(String columnHeadings){
        this.columnHeadings = columnHeadings;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context){
        this.context = context;
    }

    @Override
    public String toString() {
        return "Provenance{" +
                "user='" + user + '\'' +
                ", timeStamp=" + timeStamp +
                ", method='" + method + '\'' +
                ", name='" + name + '\'' +
                ", rowHeadings='" + rowHeadings + '\'' +
                ", columnHeadings='" + columnHeadings + '\'' +
                ", context='" + context + '\'' +
                '}';
    }

    /* ok I'm well aware one could just annotate this class but there is a problem : I want it immutable.
        This would mean json using the constructor and given things like the azquomemorydb in there this is
        just not going to be elegant, best to just hive the json off to here to be called in the constructor
         */
    private static class JsonTransport {
        public final String user;
        public final Date timeStamp;
        public final String method;
        public final String name;
        public final String rowHeadings;
        public final String columnHeadings;
        public final String context;

        @JsonCreator
        private JsonTransport(@JsonProperty("user") String user
                , @JsonProperty("timeStamp") Date timeStamp
                , @JsonProperty("method") String method
                , @JsonProperty("name") String name
                , @JsonProperty("rowHeadings") String rowHeadings
                , @JsonProperty("columnHeadings") String columnHeadings
                , @JsonProperty("context") String context) {
            this.user = user;
            this.timeStamp = timeStamp;
            this.method = method;
            this.name = name;
            this.rowHeadings = rowHeadings;
            this.columnHeadings = columnHeadings;
            this.context = context;
        }
    }

    @Override
    protected String getPersistTable() {
        return StandardDAO.PersistedTable.provenance.name();
    }

    @Override
    public String getAsJson() {
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(user, timeStamp, method, name, rowHeadings, columnHeadings, context));
        } catch (Exception e) {
            logger.error("can't get a provenance as json", e);
        }
        return "";
    }


}
