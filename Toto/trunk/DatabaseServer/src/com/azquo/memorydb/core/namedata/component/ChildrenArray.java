package com.azquo.memorydb.core.namedata.component;

import com.azquo.memorydb.core.NameUtils;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.UnsupportedOperationException;

import java.util.*;

public interface ChildrenArray extends NameData {

    default boolean hasChildren() {
        return internalGetChildren().length > 0;
    }

    default Collection<NewName> getChildren() {
        return Arrays.asList(internalGetChildren());
    }

    default boolean addToChildren(NewName name) throws Exception {
        final NewName[] children = internalGetChildren();
        List<NewName> childrenList = Arrays.asList(children);
        if (!childrenList.contains(name)) {
            if (childrenList.size() >= ARRAYTHRESHOLD) {
                throw new UnsupportedOperationException();
            } else {
                // unlike with parents and values we don't want to look for an empty initialised array here,
                // children can be dealt with more cleanly in the linking (as in we'll make the array to size in one shot there after the names have been set in the maps in AzquoMemoryDB)
                internalSetChildren(NameUtils.nameArrayAppend(children, name));
                return true;
            }
        }
        return false;
    }

    default boolean removeFromChildren(NewName name) {
        final NewName[] children = internalGetChildren();
        List<NewName> namesList = Arrays.asList(name);
        if (namesList.contains(name)) {
            internalSetChildren(NameUtils.nameArrayRemove(children, name));
            return true;
        }
        return false;
    }

    default NewName[] directArrayChildren() {
        return internalGetChildren();
    }

    default boolean canAddChild() {
        return internalGetChildren().length < ARRAYTHRESHOLD;
    }

    // must be implemented by the "roll your own" class

    NewName[] internalGetChildren();

    void internalSetChildren(NewName[] children);

}
