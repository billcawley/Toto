package com.azquo.memorydb.core.namedata.component;

import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;

import java.util.*;

public interface ValuesSet extends NameData {

    default boolean hasValues() {
        return !internalGetValues().isEmpty();
    }

    default Collection<Value> getValues() {
        return internalGetValues();
    }

    default boolean addToValues(Value value, boolean backupRestore, boolean databaseIsLoading) {
        return internalGetValues().add(value);
    }

    default boolean removeFromValues(Value value) {
        return internalGetValues().remove(value);
    }

    // should provide direct access to the field - replacing the direct access calls used before. Often implementations might return null.
    default Set<Value> directSetValues() {
        return internalGetValues();
    }

    default boolean canAddValue() {
        return true;
    }

    default NameData getImplementationThatCanAddValue() {
        return this;
    }

    // must be implemented by the "roll your own" class - note since sets are mutable we just need a get

    Set<Value> internalGetValues();
}