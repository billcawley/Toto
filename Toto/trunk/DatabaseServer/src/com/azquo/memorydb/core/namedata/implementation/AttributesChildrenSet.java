package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesChildrenSet implements Attributes, ChildrenSet {

    private volatile NameAttributes nameAttributes;
    private volatile Set<Name> children;

    public AttributesChildrenSet(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    AttributesChildrenSet(NameAttributes nameAttributes, Name[] children) {
        this.nameAttributes = nameAttributes;
        this.children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        this.children.addAll(Arrays.asList(children));
    }

    public AttributesChildrenSet(String defaultDisplayName, Set<Name> children) throws Exception{
        this.nameAttributes = new NameAttributes(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, Collections.singletonList(defaultDisplayName));
        this.children = children;
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
    public Set<Name> internalGetChildren() {
        return children;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return new AttributesValuesArrayChildrenSet(nameAttributes, 0, children);
    }

}