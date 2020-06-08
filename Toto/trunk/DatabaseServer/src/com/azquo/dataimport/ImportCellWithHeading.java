package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.time.LocalDate;
import java.util.*;

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
    private boolean lineValueResolved;

    private Set<Name> lineNames; // split char could make multiple in here, I think that's the only thing that can . . .
    // so now we have this flag, the key will be to use it properly
    private boolean lineNamesResolved;
    // we'll need to track this as children may not be able to be sorted when names are
    private boolean lineNamesChildrenResolved;


    ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value) {
        this.immutableImportHeading = immutableImportHeading;
        this.lineValue = value;
        this.lineNames = null;
        lineValueResolved = false;
        lineNamesResolved = !immutableImportHeading.lineNameRequired ; // if line names are not requires we'll just say the names are resolved?? todo confirm
        lineNamesChildrenResolved = immutableImportHeading.indexForChild == -1; // if it's -1 then no children, mark it as resolved
    }

    ImmutableImportHeading getImmutableImportHeading() {
        return immutableImportHeading;
    }

    String getLineValue() {
        return lineValue;
    }

    Set<Name> getLineNames() {
        return lineNames;
    }

    // force resolved here now. Value can be cleared otherwise there's one shot to override it
    void setLineValue(String lineValue, AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> languages) throws Exception {
        this.lineValue = lineValue;
        setLineValueResolved(azquoMemoryDBConnection, languages);
    }

    // NOT thread safe - I assume that one thread will deal with one line
    // this means also that if line names is not null it's not empty either
    // should this be setting resolved? Need to double check the Batch Importer logic
    void addToLineNames(Name name) {
        if (name != null) { // now there's the "optional" code a null name might be passed here. Could check outside but I don't really see the problem in here.
            if (lineNames == null) {
                lineNames = HashObjSets.newMutableSet();
            }
            lineNames.add(name);
        }
    }

    public boolean lineValueResolved(){
        return lineValueResolved;
    }

    public boolean lineNamesResolved(){
        return lineNamesResolved;
    }

    public boolean lineNamesChildrenResolved(){
        return lineNamesChildrenResolved;
    }

    // little hack to stop the log being hammered by a debug line below
    static volatile long lastErrorPrintMillis = 0;

    // it used to be that resolved was accessed directly but we want a line being resolved to be linked to a line rejection check
    // as well as various other checks such as date standardising, removing spaces etc as we assume nothing more is going to happen to the line value now
    // might as well do dictionary in here. When resolved we can get dictionary names.
    public void setLineValueResolved(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> languages) throws Exception {
        if (!lineValueResolved){
            lineValueResolved = true;
    /*
    interpret the date and change to standard form
    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
    HeadingReader defines DATELANG and USDATELANG
    */
            LocalDate date=null;
            if (immutableImportHeading.datatype == StringLiterals.UKDATE) {
                date = DateUtils.isADate(lineValue);
            }
            if (immutableImportHeading.datatype == StringLiterals.USDATE) {
                date = DateUtils.isUSDate(lineValue);
            }
            if (date != null) {
                lineValue = DateUtils.dateTimeFormatter.format(date);
            }
            // spaces remove. Before date? Does it matter?
            if (immutableImportHeading.removeSpaces) {
                lineValue =  lineValue.replace(" ", "");
            }

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
        } else {
            if (lastErrorPrintMillis < (System.currentTimeMillis() - (1_000 * 10))){ // only log this kind of error once every 10 seconds, potential to jam things up a lot
                lastErrorPrintMillis = System.currentTimeMillis();
                System.out.println("*************setting line value more than once on a cell " + this); // just log it or the mo
            }
        }
    }

    // maybe add some other stuff later
    public void setLineNamesResolved()  {
        if (lineNamesResolved){
            if (lastErrorPrintMillis < (System.currentTimeMillis() - (1_000 * 10))){ // only log this kind of error once every 10 seconds, potential to jam things up a lot
                lastErrorPrintMillis = System.currentTimeMillis();
                System.out.println("*************setting line names more than once on a cell " + this); // just log it or the mo
            }
        }
        lineNamesResolved = true;
    }

    public void setLineNamesChildrenResolved()  {
        lineNamesChildrenResolved = true;
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