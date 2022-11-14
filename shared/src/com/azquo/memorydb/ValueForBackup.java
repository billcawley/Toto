package com.azquo.memorydb;

import java.io.Serializable;

public class ValueForBackup implements Serializable {
    private final int id;
    private final int provenanceId;
    private final String text;
    private final byte[] names;

    public ValueForBackup(int id, int provenanceId, String text, byte[] names) {
        this.id = id;
        this.provenanceId = provenanceId;
        this.text = text;
        this.names = names;
    }

    public int getId() {
        return id;
    }

    public int getProvenanceId() {
        return provenanceId;
    }

    public String getText() {
        return text;
    }

    public byte[] getNames() {
        return names;
    }
}