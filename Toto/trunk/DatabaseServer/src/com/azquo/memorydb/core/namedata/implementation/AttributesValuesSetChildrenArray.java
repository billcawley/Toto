package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesValuesSetChildrenArray implements Attributes, ValuesSet, ChildrenArray {

    private volatile NameAttributes nameAttributes;
    private volatile Set<Value> values;
    private volatile Name[] children;

    public AttributesValuesSetChildrenArray(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        children = new Name[0];
    }

    public AttributesValuesSetChildrenArray(NameAttributes nameAttributes, Set<Value> values) {
        this.nameAttributes = nameAttributes;
        this.values = values;
        children = new Name[0];
    }

    public AttributesValuesSetChildrenArray(NameAttributes nameAttributes, Value[] values, Name[] children) {
        this.nameAttributes = nameAttributes;
        this.values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        this.values.addAll(Arrays.asList(values));
        this.children = children;
    }

    public AttributesValuesSetChildrenArray(String defaultDisplayName, Set<Value> values, Name[] children) throws Exception {
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
        return canAddChild() ? this : new AttributesValuesSetChildrenSet(nameAttributes, values, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}