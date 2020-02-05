package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameChildrenSet implements DefaultDisplayName, ChildrenSet {

    private volatile String defaultDisplayName;
    private volatile Set<NewName> children;

    public DefaultDisplayNameChildrenSet(){
        defaultDisplayName = null;
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    public DefaultDisplayNameChildrenSet(String defaultDisplayName, NewName[] children) {
        this.defaultDisplayName = defaultDisplayName;
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
    public NameData getImplementationThatCanAddValue() {
        return new DefaultDisplayNameValuesArrayChildrenSet(defaultDisplayName, children);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception{
        return new AttributesChildrenSet(defaultDisplayName, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}