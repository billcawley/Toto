package com.azquo.memorydb.core.namedata.component;

import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.namedata.NameData;

import java.util.Collection;
import java.util.Set;

public interface ChildrenSet extends NameData {

    default boolean hasChildren() {
        return !internalGetChildren().isEmpty(); // should it always be true? This implementation should only be called with children
    }

    default Collection<NewName> getChildren() {
        return internalGetChildren();
    }

    default boolean addToChildren(NewName name, boolean backupRestore) throws Exception {
        return internalGetChildren().add(name);
    }

    default boolean removeFromChildren(NewName name) {
        return internalGetChildren().remove(name);
    }

    default Set<NewName> directSetChildren() {
        return internalGetChildren();
    }

    default boolean canAddChild() {
        return true;
    }

    default NameData getImplementationThatCanAddChild() {
        return this;
    }

    // must be implemented by the "roll your own" class - note since sets are mutable we just need a get

    Set<NewName> internalGetChildren();

}
