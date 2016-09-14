package com.azquo.dataimport;

import com.azquo.memorydb.core.Name;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Extracted from DSImportService by edward on 09/09/16.
 *
 * See MutableImportHeading, does not have a few "interim" fields that has.
 * I see no reason for getters here. Class members only, saves a load of space. Note added later : getters and setters may make the code clearer though this could be done by better names also I think
 * From a purely pragmatic point of view this class is not necessary but I'm very keen to make sure that heading info is fixed before data loading - possible errors resulting from modifying the mutable
 * headings could be a real pain, this will stop that.
 */
class ImmutableImportHeading {
    final String heading;
    final Name name;
    final int indexForAttribute;
    final int indexForChild;
    // ok the set will be fixed, I suppose names can be modified but they should be thread safe
    final Set<Name> parentNames;
    final String attribute;
    final boolean isDate;
    final Set<Integer> peerCellIndexes;
    final Set<Name> peersFromContext;
    final Set<Integer> contextPeerCellIndexes;
    final Set<Name> contextPeersFromContext;
    final boolean isAttributeSubject;
    final boolean isLocal;
    final String only;
    final String compositionPattern;
    final String defaultValue;
    final boolean blankZeroes;
    final boolean lineNameRequired;
    final String exclusive;
    final boolean existing;

    ImmutableImportHeading(MutableImportHeading mutableImportHeading) {
        this.heading = mutableImportHeading.heading;
        this.name = mutableImportHeading.name;
        this.indexForAttribute = mutableImportHeading.indexForAttribute;
        this.indexForChild = mutableImportHeading.indexForChild;
        this.parentNames = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.parentNames)); // copying the sets in a perhaps paranoid way
        this.attribute = mutableImportHeading.attribute;
        this.isDate = mutableImportHeading.isDate;
        this.peerCellIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peerCellIndexes));
        this.peersFromContext = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peersFromContext));
        this.contextPeerCellIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.contextPeerCellIndexes));
        this.contextPeersFromContext = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.contextPeersFromContext));
        this.isAttributeSubject = mutableImportHeading.isAttributeSubject;
        this.isLocal = mutableImportHeading.isLocal;
        this.only = mutableImportHeading.only;
        this.compositionPattern = mutableImportHeading.compositionPattern;
        this.defaultValue = mutableImportHeading.defaultValue;
        this.blankZeroes = mutableImportHeading.blankZeroes;
        this.lineNameRequired = mutableImportHeading.lineNameRequired;
        this.exclusive = mutableImportHeading.exclusive;
        this.existing = mutableImportHeading.existing;
    }
}