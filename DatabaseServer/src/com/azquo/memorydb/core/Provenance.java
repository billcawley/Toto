package com.azquo.memorydb.core;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.spreadsheet.transport.ProvenanceForDisplay;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:38
 * <p>
 * Represents the Provenance, or Audit as we now call it, against a name or value.
 * Unlike Value and Name immutable. Currently the only one using the Json persist pattern,
 * Value and Name now have custom classes to improve speed on loading and saving.
 * <p>
 * Some old databases may have null timestamps, I'm not going to tolerate this, hence timestamps will default to 1st Jan 1970.
 * <p>
 * It may be worth, at some point, revisiting te structure here. Whether we need name and context for example.
 */
public final class Provenance extends AzquoMemoryDBEntity {

    private static final Logger logger = Logger.getLogger(Provenance.class);

    static String PERSIST_TABLE = "provenance";

    private final String user;
    private final LocalDateTime timeStamp; // should I be using local date time or another class?
    private final String method;
    private final String name; // name of the report or upload file? A bit vague!
    private final String context;

    // won't have this call the package local constructor below, does not factor in the same way now
    // this is the practical use constructor, calling it adds it to the memory db. I used to pass the date, that made no sense, set it in here.
    public Provenance(final AzquoMemoryDB azquoMemoryDB
            , final String user
            , final String method
            , final String name
            , final String context) throws Exception {
        super(azquoMemoryDB, 0);
        this.user = user;
        this.timeStamp = LocalDateTime.now();
        this.method = method;
        this.name = name;
        this.context = context;
        getAzquoMemoryDB().addProvenanceToDb(this);
    }

    // protected as only to be called by AzquoMemoryDB, populates from JSON. If provenance had many instances maybe I'd intern strings but I don't think there's much point.

    Provenance(final AzquoMemoryDB azquoMemoryDB, final int id, String jsonFromDB, boolean forceIdForBackup) throws Exception {
        super(azquoMemoryDB, id, forceIdForBackup);
        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
        this.user = transport.user;
        this.timeStamp = transport.timeStamp != null ? DateUtils.getLocalDateTimeFromDate(transport.timeStamp) : LocalDateTime.now();
        this.method = transport.method;
        this.name = transport.name;
        this.context = transport.context;
        getAzquoMemoryDB().addProvenanceToDb(this);
    }

    public String getUser() {
        return user;
    }

    public LocalDateTime getTimeStamp() {
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

        Ignore the IntelliJ warnings about public here, the jacksonMapper needs to see them.
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
    public String getAsJson() {
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(user,  DateUtils.getDateFromLocalDateTime(timeStamp), method, name, context));
        } catch (Exception e) {
            logger.error("can't get a provenance as json", e);
        }
        return "";
    }

    @Override
    protected void setNeedsPersisting() {
        getAzquoMemoryDB().setJsonEntityNeedsPersisting(PERSIST_TABLE, this);
    }

    public ProvenanceForDisplay getProvenanceForDisplay() {
        return new ProvenanceForDisplay(method != null && method.toLowerCase().startsWith(StringLiterals.IN_SPREADSHEET), user, method, name, context, timeStamp);
    }
}