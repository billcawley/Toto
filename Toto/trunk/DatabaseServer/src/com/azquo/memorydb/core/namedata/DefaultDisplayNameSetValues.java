package com.azquo.memorydb.core.namedata;

import com.azquo.memorydb.core.Value;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameSetValues implements DefaultDisplayNameInterface, SetValuesInterface {

    private volatile String defaultDisplayName;
    private volatile Set<Value> values;

    public DefaultDisplayNameSetValues(){
        defaultDisplayName = null;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    @Override
    public String internalGetDefaultDisplayName() {
        return defaultDisplayName;
    }

    @Override
    public void internalSetDefaultDisplayName(String defaultDisplayName) {
        this.defaultDisplayName = defaultDisplayName;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return null;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return null;
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return null;
    }

    @Override
    public Set<Value> internalGetValues() {
        return values;
    }
}
