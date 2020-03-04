package com.azquo.memorydb.dao;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * now we've simplified the way records are persisted we need to be able to move them around
 */
public class JsonRecordTransport {

    public enum State {LOADED, DELETE, UPDATE, INSERT}

    public final int id;
    public final String json;
    final State state;

    public JsonRecordTransport(int id, String json, State state) {
        this.id = id;
        this.json = json;
        this.state = state;
    }
}
