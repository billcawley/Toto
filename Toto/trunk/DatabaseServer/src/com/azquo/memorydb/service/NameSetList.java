package com.azquo.memorydb.service;

import com.azquo.memorydb.core.Name;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Extracted from NameService by edward on 18/10/16.
 *
 * in order to avoid unnecessary collection copying (we could be talking millions of names) I made this little container to move a collection that could be a list or set and possibly mutable
 */
class NameSetList {
    public final Set<Name> set;
    public final List<Name> list;
    final boolean mutable;

    NameSetList(Set<Name> set, List<Name> list, boolean mutable) {
        this.set = set;
        this.list = list;
        this.mutable = mutable;
    }

    // make from an existing (probably immutable) one
    NameSetList(NameSetList nameSetList) {
        set = nameSetList.set != null ? HashObjSets.newMutableSet(nameSetList.set) : null;
        list = nameSetList.list != null ? new ArrayList<>(nameSetList.list) : null;
        mutable = true;
    }

    Collection<Name> getAsCollection() {
        return set != null ? set : list != null ? list : null;
    }
}
