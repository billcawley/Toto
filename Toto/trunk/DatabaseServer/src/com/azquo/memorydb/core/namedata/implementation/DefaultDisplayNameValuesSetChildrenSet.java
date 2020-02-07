package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameValuesSetChildrenSet implements DefaultDisplayName, ValuesSet, ChildrenSet {

    private volatile String defaultDisplayName;
    private volatile Set<Value> values;
    private volatile Set<NewName> children;

    public DefaultDisplayNameValuesSetChildrenSet(String defaultDisplayName){
        this.defaultDisplayName = defaultDisplayName;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
    }

    public DefaultDisplayNameValuesSetChildrenSet(String defaultDisplayName, Value[] values, Set<NewName> children) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        this.values.addAll(Arrays.asList(values));
        this.children = children;
    }

    public DefaultDisplayNameValuesSetChildrenSet(String defaultDisplayName, Set<Value> values, NewName[] children) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = values;
        this.children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        this.children.addAll(Arrays.asList(children));
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
    public Set<NewName> internalGetChildren() {
        return children;
    }

    @Override
    public Set<Value> internalGetValues() {
        return values;
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception {
        return new AttributesValuesSetChildrenSet(defaultDisplayName, values, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}