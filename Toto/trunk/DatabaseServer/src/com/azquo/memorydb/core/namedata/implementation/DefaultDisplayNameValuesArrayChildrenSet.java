package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameValuesArrayChildrenSet implements DefaultDisplayName, ValuesArray, ChildrenSet {

    private volatile String defaultDisplayName;
    private volatile Value[] values;
    private volatile Set<NewName> children;

    public DefaultDisplayNameValuesArrayChildrenSet(String defaultDisplayName){
        this.defaultDisplayName = defaultDisplayName;
        values = new Value[0];
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    public DefaultDisplayNameValuesArrayChildrenSet(String defaultDisplayName, Set<NewName> children) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = new Value[0];
        this.children = children;
    }

    public DefaultDisplayNameValuesArrayChildrenSet(String defaultDisplayName, Value[] values, NewName[] children) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = values;
        this.children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
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
    public Value[] internalGetValues() {
        return values;
    }

    @Override
    public void internalSetValues(Value[] values) {
        this.values = values;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return canAddValue() ? this : new DefaultDisplayNameValuesSetChildrenSet(defaultDisplayName, values, children);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception {
        return new AttributesValuesArrayChildrenSet(defaultDisplayName, values, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}