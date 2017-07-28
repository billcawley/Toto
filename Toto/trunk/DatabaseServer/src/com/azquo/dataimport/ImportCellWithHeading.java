package com.azquo.dataimport;

import com.azquo.memorydb.core.Name;

import java.util.Set;

/**
 * Extracted from DSImportService by edward on 09/09/16.
 * <p>
 * As it says, an import cell coupled with a heading, Lists of Lists of these are passed to the BatchImporter.
 * I'd have liked to make this immutable but existing logic for things like composite mean this may be changed before loading
 * I've added getters and setters as I think it may provide a little warning regarding setting the lineValue or lineName.
 */
class ImportCellWithHeading {
    private final ImmutableImportHeading immutableImportHeading;
    private String lineValue;// prefix  line to try to avoid confusion
    private Name lineName;
    private Set<Name> splitNames;

    ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value, Name name) {
        this.immutableImportHeading = immutableImportHeading;
        this.lineValue = value;
        this.lineName = name;
    }

    ImmutableImportHeading getImmutableImportHeading() {
        return immutableImportHeading;
    }

    String getLineValue() {
        return lineValue;
    }

    // I've called it this as it's the only reason I can think that the value would be overwritten
    void setLineValue(String lineValue) {
        this.lineValue = lineValue;
    }

    Name getLineName() {
        return lineName;
    }

    void setLineName(Name lineName) {
        this.lineName = lineName;
    }

    Set<Name> getSplitNames() {
        return splitNames;
    }

    void setSplitNames(Set<Name> splitNames) {
        this.splitNames = splitNames;
    }

    @Override
    public String toString() {
        return "ImportCellWithHeading{" +
                "immutableImportHeading=" + immutableImportHeading +
                ", lineValue='" + lineValue + '\'' +
                ", lineName=" + lineName +
                ", splitNames=" + splitNames +
                '}';
    }
}