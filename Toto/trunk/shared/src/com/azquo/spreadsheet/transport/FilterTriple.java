package com.azquo.spreadsheet.transport;

import java.io.Serializable;

/**
 * Created by edward on 06/04/16.
 *
 * Used for the filter selection boxes. Maybe create a generic triple? No need right now.
 */
public class FilterTriple implements Serializable {
    public final int nameId;
    public final String name;
    public final Boolean selected;

    public FilterTriple(int nameId, String name, Boolean selected) {
        this.nameId = nameId;
        this.name = name;
        this.selected = selected;
    }
}
