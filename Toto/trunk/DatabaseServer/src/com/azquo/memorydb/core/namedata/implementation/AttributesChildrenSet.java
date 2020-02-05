package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesChildrenSet implements Attributes, ChildrenSet {

    private volatile NameAttributes nameAttributes;
    private volatile Set<NewName> children;

    public AttributesChildrenSet(){
        nameAttributes = new NameAttributes();
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    @Override
    public NameAttributes internalGetNameAttributes() {
        return nameAttributes;
    }

    @Override
    public void internalSetNameAttributes(NameAttributes nameAttributes) {
        this.nameAttributes = nameAttributes;
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