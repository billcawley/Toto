package com.azquo.spreadsheet.view;

import java.io.Serializable;

/**
 * Created by cawley on 11/05/15.
 *
 * Ok in our new client server idea then there have to be classes to move the data about, this is one such class
 * and the first in the shared module
 */
public class CellForDisplay implements Serializable {
    private boolean locked;
    private String stringValue;
    private double doubleValue;
    private boolean highlighted;
    // it's just convenient to put this here
    private boolean changed;

    public CellForDisplay(boolean locked, String stringValue, double doubleValue, boolean highlighted) {
        this.locked = locked;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
        this.highlighted = highlighted;
        changed = false;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        changed = true;
        this.stringValue = stringValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(double doubleValue) {
        changed = true;
        this.doubleValue = doubleValue;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    public boolean isChanged() {
        return changed;
    }

    @Override
    public String toString() {
        return "AzquoCellForDisplay{" +
                "locked=" + locked +
                ", stringValue='" + stringValue + '\'' +
                ", doubleValue=" + doubleValue +
                ", highlighted=" + highlighted +
                '}';
    }

}
