package com.azquo.memorydb.core.namedata;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.NameUtils;
import com.azquo.memorydb.core.NewName;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface SetNamesInterface extends NameData {

    default boolean hasChildren() {
        return !internalGetNames().isEmpty(); // should it always be true? This implementation should only be called with children
    }

    default Collection<NewName> getChildren() {
        return internalGetNames();
    }

    default boolean addToChildren(NewName name, boolean backupRestore) throws Exception {
        return internalGetNames().add(name);
    }

    default boolean removeFromChildren(NewName name) {
        return internalGetNames().remove(name);
    }

    default Set<NewName> directSetChildren() {
        return internalGetNames();
    }

    default boolean canAddChild() {
        return true;
    }

    // must be implemented by the "roll your own" class - note since sets are mutable we just need a get

    Set<NewName> internalGetNames();

}
