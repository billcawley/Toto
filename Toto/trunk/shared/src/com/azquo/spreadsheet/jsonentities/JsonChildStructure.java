package com.azquo.spreadsheet.jsonentities;

import java.util.Map;

/**
 * Created by edward on 11/11/15.
 *
 * This was a local class but now it's
 *
 */
public class JsonChildStructure{
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

