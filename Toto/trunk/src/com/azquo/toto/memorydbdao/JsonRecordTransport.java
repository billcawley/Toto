package com.azquo.toto.memorydbdao;

/**
 * now we've simplfied the way records are persisted we need to be able to move them around
 */
public class JsonRecordTransport {

    public enum State {LOADED, DELETE, UPDATE, INSERT}

    public final int id;
    public final String json;
    public final State state;

    public JsonRecordTransport(int id, String json, State state) {
        this.id = id;
        this.json = json;
        this.state = state;
    }
}
