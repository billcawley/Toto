package com.azquo.spreadsheet.view;

import java.io.Serializable;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
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
    // for looking stuff up later (can do a fast lookup based on the unsorted position)
    private final int unsortedRow;
    private final int unsortedCol;
    // as in a . on row or col heading. The restore saved values function would like to know to ignore these
    private boolean ignored;
    // to auto select a cell from provenance or drilldown
    private boolean selected;

    public CellForDisplay(boolean locked, String stringValue, double doubleValue, boolean highlighted, int unsortedRow, int unsortedCol, boolean ignored, boolean selected) {
        this.locked = locked;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
        this.highlighted = highlighted;
        changed = false;
        this.unsortedRow = unsortedRow;
        this.unsortedCol = unsortedCol;
        this.ignored = ignored;
        this.selected = selected;
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

    public int getUnsortedRow() {
        return unsortedRow;
    }

    public int getUnsortedCol() {
        return unsortedCol;
    }

    public boolean getIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public boolean getSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return "CellForDisplay{" +
                "locked=" + locked +
                ", stringValue='" + stringValue + '\'' +
                ", doubleValue=" + doubleValue +
                ", highlighted=" + highlighted +
                ", changed=" + changed +
                ", unsortedRow=" + unsortedRow +
                ", unsortedCol=" + unsortedCol +
                ", ignored=" + ignored +
                ", selected=" + selected +
                '}';
    }
}
