package com.azquo.memorydb;

import java.io.Serializable;

public class ProvenanceForBackup implements Serializable {
    public final int id;
    public final String json;

    public ProvenanceForBackup(int id, String json) {
        this.id = id;
        this.json = json;
    }

    public int getId() {
        return id;
    }

    public String getJson() {
        return json;
    }
}
