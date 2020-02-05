package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;

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
        return this;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return null;
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return null;
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}