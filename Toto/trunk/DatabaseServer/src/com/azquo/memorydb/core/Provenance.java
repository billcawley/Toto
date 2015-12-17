package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.JsonRecordDAO;
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
    private final Date timeStamp;
    private final String method;
    private final String name;
    private final String context;
    // won't have this call the other constructor, does not factor in the same way now
    // this is the practical use constructor, calling it adds it to the memory db
    public Provenance(final AzquoMemoryDB azquoMemoryDB
            , final String user
            , final Date timeStamp
            , final String method
            , final String name
            , final String context) throws Exception {
        super(azquoMemoryDB, 0);
        this.user = user;
        this.timeStamp = timeStamp;
        this.method = method;
        this.name = name;
        this.context = context;
        getAzquoMemoryDB().addProvenanceToDb(this);
    }

    // protected as only to be called by AzquoMemoryDB, populates from JSON. If provenance had many instances maybe I'd intern here but I don't think there's much point.

    protected Provenance(final AzquoMemoryDB azquoMemoryDB, final int id, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);

        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
        this.user = transport.user;
        this.timeStamp = transport.timeStamp;
        this.method = transport.method;
        this.name = transport.name;
        this.context = transport.context;
        getAzquoMemoryDB().addProvenanceToDb(this);
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
                ", context='" + context + '\'' +
                '}';
    }

    /* ok I'm well aware one could just annotate this class but there is a problem : I want it immutable.
        This would mean json using the constructor and given things like the AzquoMemoryDB in there this is
        just not going to be elegant, best to just hive the json off to here to be called in the constructor
         */
    private static class JsonTransport {
        public final String user;
        public final Date timeStamp;
        public final String method;
        public final String name;
        public final String context;
        @JsonCreator
        private JsonTransport(@JsonProperty("user") String user
                , @JsonProperty("timeStamp") Date timeStamp
                , @JsonProperty("method") String method
                , @JsonProperty("name") String name
                , @JsonProperty("context") String context) {
            this.user = user;
            this.timeStamp = timeStamp;
            this.method = method;
            this.name = name;
            this.context = context;
        }
    }

    @Override
    protected String getPersistTable() {
        return JsonRecordDAO.PersistedTable.provenance.name();
    }

    @Override
    public String getAsJson() {
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(user, timeStamp, method, name, context));
        } catch (Exception e) {
            logger.error("can't get a provenance as json", e);
        }
        return "";
    }
}