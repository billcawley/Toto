package com.azquo.spreadsheet.transport;

import java.io.Serializable;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 11/05/15.
 * <p>
 * Ok in our new client server idea then there have to be classes to move the data about, this is one such class
 * and the first in the shared module
 */
public class CellForDisplay implements Serializable {
    private boolean locked;
    private String stringValue;
    private double doubleValue;
    // in order to check if another user has modified the cell keep the old values and put the new ones in instead
    private boolean changed;
    private String newStringValue;
    private double newDoubleValue;
    private boolean highlighted;
    // for looking stuff up later (can do a fast lookup based on the unsorted position)
    private final int unsortedRow;
    private final int unsortedCol;
    // as in a . on row or col heading. The restore saved values function would like to know to ignore these
    private boolean ignored;
    // to auto select a cell from provenance or drilldown
    private boolean selected;

    private String comment; // being sent both ways now. It's in the provenance also but nice to put in here
    // the value id if this cell is made from a single value. For auditing drilldown where the source is on another sheet in the same workbook.
    // Where sheets are auto generated from a choice like list
    private final int valueId;


    public CellForDisplay(boolean locked, String stringValue, double doubleValue, boolean highlighted, int unsortedRow, int unsortedCol, boolean ignored, boolean selected, String comment, int valueId) {
        this.locked = locked;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
        this.highlighted = highlighted;
        this.valueId = valueId;
        newStringValue = null;
        newDoubleValue = 0;
        changed = false;
        this.unsortedRow = unsortedRow;
        this.unsortedCol = unsortedCol;
        this.ignored = ignored;
        this.selected = selected;
        this.comment = comment;
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

    public double getDoubleValue() {
        return doubleValue;
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

    public void setChanged() {
        changed = true;
    }

    public String getNewStringValue() {
        return newStringValue;
    }

    public void setNewStringValue(String newStringValue) {
        changed = true;
        this.newStringValue = newStringValue;
    }

    public double getNewDoubleValue() {
        return newDoubleValue;
    }

    public void setNewDoubleValue(double newDoubleValue) {
        changed = true;
        this.newDoubleValue = newDoubleValue;
    }

    //after saving - if we don't do this then sequential saves will cause a problem
    public void setNewValuesToCurrentAfterSaving() {
        stringValue = newStringValue;
        doubleValue = newDoubleValue;
        // as in the constructor
        newStringValue = null;
        newDoubleValue = 0;
        changed = false;
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

    // provisionally logic states that you can add a comment to an existing figure
    public void setComment(String comment) {
        changed = true;
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public int getValueId() {
        return valueId;
    }

    @Override
    public String toString() {
        return "CellForDisplay{" +
                "locked=" + locked +
                ", stringValue='" + stringValue + '\'' +
                ", doubleValue=" + doubleValue +
                ", changed=" + changed +
                ", newStringValue='" + newStringValue + '\'' +
                ", newDoubleValue=" + newDoubleValue +
                ", highlighted=" + highlighted +
                ", unsortedRow=" + unsortedRow +
                ", unsortedCol=" + unsortedCol +
                ", ignored=" + ignored +
                ", selected=" + selected +
                ", comment='" + comment + '\'' +
                ", valueId=" + valueId +
                '}';
    }
}
