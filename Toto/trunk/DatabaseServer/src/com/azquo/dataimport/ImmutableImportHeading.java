package com.azquo.dataimport;

import com.azquo.memorydb.core.Name;

import java.util.*;

/**
 * Extracted from DSImportService by edward on 09/09/16.
 * <p>
 * See MutableImportHeading, does not have a few "interim" fields that that has, notable is removal of the context info, it has no reason to care how its peers were constructed.
 * I see no reason for getters here. Class members only, saves a load of space. Note added later : getters and setters may make the code clearer though this could be done by better names also I think
 * From a purely pragmatic point of transport this class is not necessary but I'm very keen to make sure that heading info is fixed before data loading - possible errors resulting from modifying the mutable
 * headings could be a real pain, this will stop that.
 */


public class ImmutableImportHeading {

    final String heading;
    final int indexForAttribute;
    final int indexForChild;
    // ok the set will be fixed, I suppose names can be modified but they should be thread safe
    final Set<Name> parentNames;
    final String attribute;
    final int attributeColumn;
    final int dateForm;
    final Set<Name> peerNames;
    final Set<Integer> peerIndexes;
    final boolean isLocal;
    final String only;
    final String compositionPattern;
    final boolean noFileHeading;
    final String override;
    final List<String> ignoreList;
    final boolean blankZeroes;
    final boolean removeSpaces;
    final boolean lineNameRequired;
    final int exclusiveIndex;
    final boolean existing;
    final boolean optional;
    final String splitChar;
    final List<Integer> localParentIndexes;
    Map<Name, List<DictionaryTerm>> dictionaryMap;
    Map<String, List<String>> synonyms;
     // not being used, perhaps zap
    boolean replace;
    boolean provisional;
    int lookupParentIndex;
    String lookupString;


    ImmutableImportHeading(MutableImportHeading mutableImportHeading) {
        this.heading = mutableImportHeading.heading;
        this.indexForAttribute = mutableImportHeading.indexForAttribute;
        this.indexForChild = mutableImportHeading.indexForChild;
        this.parentNames = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.parentNames)); // copying the sets in a perhaps paranoid way
        this.attribute = mutableImportHeading.attribute;
        this.attributeColumn = mutableImportHeading.attributeColumn;
        this.dateForm = mutableImportHeading.dateForm;
        this.peerNames = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peerNames));
        this.peerIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peerIndexes));
        this.isLocal = mutableImportHeading.isLocal;
        this.only = mutableImportHeading.only;
        this.compositionPattern = mutableImportHeading.compositionPattern;
        this.noFileHeading = mutableImportHeading.noFileHeading;
        this.override = mutableImportHeading.override;
        this.ignoreList = mutableImportHeading.ignoreList;
        this.blankZeroes = mutableImportHeading.blankZeroes;
        this.removeSpaces = mutableImportHeading.removeSpaces;
        this.lineNameRequired = mutableImportHeading.lineNameRequired;
        this.exclusiveIndex = mutableImportHeading.exclusiveIndex;
        this.existing = mutableImportHeading.existing;
        this.optional = mutableImportHeading.optional;
        this.splitChar = mutableImportHeading.splitChar;
        this.localParentIndexes = mutableImportHeading.localParentIndexes;
        this.dictionaryMap = mutableImportHeading.dictionaryMap;
        this.synonyms = mutableImportHeading.synonyms;
        this.replace = mutableImportHeading.replace;
        this.provisional = mutableImportHeading.provisional;
        this.lookupParentIndex = mutableImportHeading.lookupParentIndex;
        this.lookupString = mutableImportHeading.lookupString;
    }

    public static class DictionaryTerm {
        public final boolean exclude;
        public final List<String> items;

        public DictionaryTerm(boolean exclude, List<String> items) {
            this.exclude = exclude;
            this.items = Collections.unmodifiableList(items);
        }
    }
}