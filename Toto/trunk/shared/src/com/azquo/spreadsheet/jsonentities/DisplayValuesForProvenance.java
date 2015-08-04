package com.azquo.spreadsheet.jsonentities;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Created by cawley on 14/07/15.
 *
 * object to be passed to the front end to display provenance. Might need to be refactored after zapping AzquoBook.
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisplayValuesForProvenance implements Serializable{
    public final String heading;
    public final List<DisplayValuesForProvenance> items;
    public final String name;
    public final String value;

    public DisplayValuesForProvenance(String heading, List<DisplayValuesForProvenance> items) {
        this.heading = heading;
        this.items = items;
        this.name = null;
        this.value = null;
    }

    public DisplayValuesForProvenance(String name, String value) {
        this.heading = null;
        this.items = null;
        this.name = name;
        this.value = value;
    }
}