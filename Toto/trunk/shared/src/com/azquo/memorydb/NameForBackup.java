package com.azquo.memorydb;

/*
Created 10/09/2018

to help backup of databases and reports in a downloadable file

Similar in structure to the sql transport, I hope to reuse chunks of the code.

 */

import java.io.Serializable;

public class NameForBackup implements Serializable {
    private final int id;
    private final int provenanceId;
    private final String attributes;
    private final byte[] children;
    private final int noParents;
    private final int noValues;

    public NameForBackup(int id, int provenanceId, String attributes, byte[] children, int noParents, int noValues) {
        this.id = id;
        this.provenanceId = provenanceId;
        this.attributes = attributes;
        this.children = children;
        this.noParents = noParents;
        this.noValues = noValues;
    }

    public int getId() {
        return id;
    }

    public int getProvenanceId() {
        return provenanceId;
    }

    public String getAttributes() {
        return attributes;
    }

    public byte[] getChildren() {
        return children;
    }

    public int getNoParents() {
        return noParents;
    }

    public int getNoValues() {
        return noValues;
    }
}
