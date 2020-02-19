package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private boolean needsResolving;

    ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value) {
        this.immutableImportHeading = immutableImportHeading;
        this.lineValue = value;
        this.lineNames = null;
        needsResolving = true;
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
        if (name != null) { // now there's the "optional" code a null name might be passed here. Could check outside but I don't really see the problem in here.
            if (lineNames == null) {
                lineNames = HashObjSets.newMutableSet();
            }
            lineNames.add(name);
        }
    }

    public boolean needsResolving(){
        return needsResolving;
    }

    // little hack to stop the log being hammered by a debug line below
    static volatile long lastErrorPrintMillis = 0;

    // it used to be that resolved was accessed directly but we want a line being resolved to be linked to a line rejection check
    // on being set as resolved we assume nothing more is going to happen to the line
    public void setResolved(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> languages) throws LineRejectionException {
        if (needsResolving){
            if (immutableImportHeading.only != null) {
                //`only' can have wildcards  '*xxx*'
                String only = immutableImportHeading.only.toLowerCase();
                String lineValue = this.lineValue.toLowerCase(); // if this NPEs then there's something very wrong
                if (only.startsWith("*")) {
                    if (only.endsWith("*")) {
                        if (!lineValue.contains(only.substring(1, only.length() - 1))) {
                            throw new LineRejectionException("not in only, " + only);
                        }
                    } else if (!lineValue.endsWith(only.substring(1))) {
                        throw new LineRejectionException("not in only, " + only);
                    }
                } else if (only.endsWith("*")) {
                    if (!lineValue.startsWith(only.substring(0, only.length() - 1))) {
                        throw new LineRejectionException("not in only, " + only);
                    }
                } else {
                    if (!lineValue.equals(only)) {
                        throw new LineRejectionException("not in only, " + only);
                    }
                }
            }
            if (immutableImportHeading.existing) {
                boolean cellOk = false;
                if (immutableImportHeading.attribute != null && immutableImportHeading.attribute.length() > 0) {
                    languages = new ArrayList<>();
                    String newLanguages = immutableImportHeading.attribute;
                    languages.addAll(Arrays.asList(newLanguages.split(",")));
                }
                if (languages == null) { // same logic as used when creating the line names, not sure of this
                    languages = StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
                }
                // note I'm not going to check parentNames are not empty here, if someone put existing without specifying child of then I think it's fair to say the line isn't valid
                for (Name parent : immutableImportHeading.parentNames) { // try to find any names from anywhere
                    if (!azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(languages, this.lineValue, parent).isEmpty()) { // NOT empty, we found one!
                        cellOk = true;
                        break; // no point continuing, we found one
                    }
                }
                if (!cellOk) {
                    throw  new LineRejectionException(immutableImportHeading.heading + ":" + this.lineValue + " not existing"); // none found break the line
                }
            }
            needsResolving = false;
        } else {
            if (lastErrorPrintMillis < (System.currentTimeMillis() - (1_000 * 10))){ // only log this kind of error once every 10 seconds, potential to jam things up a lot
                lastErrorPrintMillis = System.currentTimeMillis();
                System.out.println("setting resolved more than once on a cell " + this);
            }
        }
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