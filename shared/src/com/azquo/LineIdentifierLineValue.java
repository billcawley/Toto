package com.azquo;

import java.io.Serializable;

/**
 * Created by edward on 15/09/16.
 *
 * Edd after a generic pair, could be useful in a few places but I don't want to over use
 *
 * Notably I'm now thinking I need to reduce where it's used
 **/
public class LineIdentifierLineValue implements Serializable {
    private final String lineIdentifier;
    private final String lineValue;

    public LineIdentifierLineValue(String lineIdentifier, String lineValue) {
        this.lineIdentifier = lineIdentifier;
        this.lineValue = lineValue;
    }

    public String getLineIdentifier() {
        return lineIdentifier;
    }

    public String getLineValue() {
        return lineValue;
    }
}
