package com.azquo.spreadsheet.view;

import java.io.Serializable;

/**
 * Created by edward on 06/04/16.
 *
 */
public class FilterTriple implements Serializable {
    final int nameId;
    public final String name;
    public final Boolean selected;

    public FilterTriple(int nameId, String name, Boolean selected) {
        this.nameId = nameId;
        this.name = name;
        this.selected = selected;
    }
}
