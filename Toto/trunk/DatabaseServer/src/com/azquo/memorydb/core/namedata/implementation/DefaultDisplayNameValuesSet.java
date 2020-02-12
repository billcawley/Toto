package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameValuesSet implements DefaultDisplayName, ValuesSet {

    private volatile String defaultDisplayName;
    private volatile Set<Value> values;

    public DefaultDisplayNameValuesSet(String defaultDisplayName){
        this.defaultDisplayName = defaultDisplayName;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    public DefaultDisplayNameValuesSet(String defaultDisplayName, Value[] values) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        this.values.addAll(Arrays.asList(values));
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
    public Set<Value> internalGetValues() {
        return values;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return new DefaultDisplayNameValuesSetChildrenArray(defaultDisplayName, values);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception {
        return new AttributesValuesSet(defaultDisplayName, values);
    }

}
