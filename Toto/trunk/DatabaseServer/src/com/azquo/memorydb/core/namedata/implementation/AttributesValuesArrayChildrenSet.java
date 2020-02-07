package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesValuesArrayChildrenSet implements Attributes, ValuesArray, ChildrenSet {

    private volatile NameAttributes nameAttributes;
    private volatile Value[] values;
    private volatile Set<NewName> children;

    public AttributesValuesArrayChildrenSet(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        values = new Value[0];
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    public AttributesValuesArrayChildrenSet(NameAttributes nameAttributes, Set<NewName> children) {
        this.nameAttributes = nameAttributes;
        values = new Value[0];
        this.children = children;
    }

    public AttributesValuesArrayChildrenSet(NameAttributes nameAttributes, Value[] values, NewName[] children) {
        this.nameAttributes = nameAttributes;
        this.values = values;
        this.children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        this.children.addAll(Arrays.asList(children));
    }

    public AttributesValuesArrayChildrenSet(String defaultDisplayName, Value[] values, Set<NewName> children) throws Exception {
        this.nameAttributes = new NameAttributes(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, Collections.singletonList(defaultDisplayName));
        this.values = values;
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
        return values.length < ARRAYTHRESHOLD ? this : new AttributesValuesSetChildrenSet(nameAttributes, values, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}