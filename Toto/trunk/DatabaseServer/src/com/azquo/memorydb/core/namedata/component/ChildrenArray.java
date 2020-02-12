package com.azquo.memorydb.core.namedata.component;

import com.azquo.memorydb.core.NameUtils;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.UnsupportedOperationException;

import java.util.*;

public interface ChildrenArray extends NameData {

    default boolean hasChildren() {
        return internalGetChildren().length > 0;
    }

    default Collection<Name> getChildren() {
        return Arrays.asList(internalGetChildren());
    }

    default boolean addToChildren(Name name) throws Exception {
        final Name[] children = internalGetChildren();
        List<Name> childrenList = Arrays.asList(children);
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

    default boolean removeFromChildren(Name name) {
        final Name[] children = internalGetChildren();
        List<Name> namesList = Arrays.asList(name);
        if (namesList.contains(name)) {
            internalSetChildren(NameUtils.nameArrayRemove(children, name));
            return true;
        }
        return false;
    }

    default Name[] directArrayChildren() {
        return internalGetChildren();
    }

    default boolean canAddChild() {
        return internalGetChildren().length < ARRAYTHRESHOLD;
    }

    default void setArrayChildren(Name[] children) {
        internalSetChildren(children);
    }

    default boolean canSetArrayChildren() {
        return true;
    }



    // must be implemented by the "roll your own" class

    Name[] internalGetChildren();

    void internalSetChildren(Name[] children);

}
