package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameValuesSetChildrenArray implements DefaultDisplayName, ValuesSet, ChildrenArray {

    private volatile String defaultDisplayName;
    private volatile Set<Value> values;
    private volatile Name[] children;

    public DefaultDisplayNameValuesSetChildrenArray(String defaultDisplayName){
        this.defaultDisplayName = defaultDisplayName;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        children = new Name[0];
    }

    public DefaultDisplayNameValuesSetChildrenArray(String defaultDisplayName, Value[] values, Name[] children) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        this.values.addAll(Arrays.asList(values));
        this.children = children;
    }

    public DefaultDisplayNameValuesSetChildrenArray(String defaultDisplayName, Set<Value> values) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = values;
        children = new Name[0];
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
    public Name[] internalGetChildren() {
        return children;
    }

    @Override
    public void internalSetChildren(Name[] children) {
        this.children = children;
    }

    @Override
    public Set<Value> internalGetValues() {
        return values;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return canAddChild() ? this : new DefaultDisplayNameValuesSetChildrenSet(defaultDisplayName, values, children);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception {
        return new AttributesValuesSetChildrenArray(defaultDisplayName, values, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}