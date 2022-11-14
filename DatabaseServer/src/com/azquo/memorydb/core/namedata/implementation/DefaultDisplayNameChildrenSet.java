package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameChildrenSet implements DefaultDisplayName, ChildrenSet {

    private volatile String defaultDisplayName;
    private volatile Set<Name> children;

    public DefaultDisplayNameChildrenSet(String defaultDisplayName){
        this.defaultDisplayName = defaultDisplayName;
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    public DefaultDisplayNameChildrenSet(String defaultDisplayName, Name[] children) {
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
    public Set<Name> internalGetChildren() {
        return children;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return new DefaultDisplayNameValuesArrayChildrenSet(defaultDisplayName, 0, children);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception{
        return new AttributesChildrenSet(defaultDisplayName, children);
    }

}