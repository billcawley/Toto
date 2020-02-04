package com.azquo.memorydb.core.namedata;

import com.azquo.memorydb.core.NameUtils;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;

import java.util.*;

public interface ArrayNamesInterface extends NameData {

    default boolean hasChildren() {
        return internalGetNames().length > 0;
    }

    default Collection<NewName> getChildren() {
        return Arrays.asList(internalGetNames());
    }

    default boolean addToChildren(NewName name) throws Exception {
        final NewName[] names = internalGetNames();
        List<NewName> childrenList = Arrays.asList(names);
        if (!childrenList.contains(name)) {
            if (childrenList.size() >= ARRAYTHRESHOLD) {
                throw new UnsupportedOperationException();
            } else {
                // unlike with parents and values we don't want to look for an empty initialised array here,
                // children can be dealt with more cleanly in the linking (as in we'll make the array to size in one shot there after the names have been set in the maps in AzquoMemoryDB)
                internalSetNames(NameUtils.nameArrayAppend(names, name));
                return true;
            }
        }
        return false;
    }

    default boolean removeFromChildren(NewName name) {
        final NewName[] names = internalGetNames();
        List<NewName> namesList = Arrays.asList(name);
        if (namesList.contains(name)) {
            internalSetNames(NameUtils.nameArrayRemove(names, name));
            return true;
        }
        return false;
    }

    default NewName[] directArrayChildren() {
        return internalGetNames();
    }

    default boolean canAddChild() {
        return internalGetNames().length < ARRAYTHRESHOLD;
    }

    // must be implemented by the "roll your own" class

    NewName[] internalGetNames();

    void internalSetNames(NewName[] names);

}
