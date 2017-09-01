package com.azquo.dataimport;

import com.azquo.memorydb.core.Name;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

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
    private Set<Name> lineNames; // it could be a comma separated list. Added for PwC, I'm not entirely happy about this but if it's necessary it's necessary - EFC

    ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value) {
        this.immutableImportHeading = immutableImportHeading;
        this.lineValue = value;
        this.lineNames = null;
    }

    ImmutableImportHeading getImmutableImportHeading() {
        return immutableImportHeading;
    }

    String getLineValue() {
        return lineValue;
    }

    void setLineValue(String lineValue) {
        this.lineValue = lineValue;
    }

    Set<Name> getLineNames() {
        return lineNames;
    }

    // NOT thread safe - I assume that one thread will deal with one line
    void addToLineNames(Name name) {
        if (lineNames == null) {
            lineNames = HashObjSets.newMutableSet();
        }
        lineNames.add(name);
    }

    @Override
    public String toString() {
        return "ImportCellWithHeading{" +
                "immutableImportHeading=" + immutableImportHeading +
                ", lineValue='" + lineValue + '\'' +
                ", lineNames=" + lineNames +
                '}';
    }
}