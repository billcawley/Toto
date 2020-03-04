package com.azquo.spreadsheet.transport.json;

import java.io.Serializable;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * Created by edward on 11/11/15.
 *
 * This was a local class but now it needs to be moved between the DB/Report servers, the problem is that the tree UI (inspect database) needs close access to the DB.
 */
public class JsonChildStructure implements Serializable {
    // public for jackson to see them
    public final String name;
    public final int id;
    public final int dataitems;
    public final int mydataitems;
    public final Map<String, Object> attributes;
    public final int elements;
    public final String provenance;

    public JsonChildStructure(String name, int id, int dataitems, int mydataitems, Map<String, Object> attributes, int elements, String provenance) {
        this.name = name;
        this.id = id;
        this.dataitems = dataitems;
        this.mydataitems = mydataitems;
        this.attributes = attributes;
        this.elements = elements;
        this.provenance = provenance;
    }
}

