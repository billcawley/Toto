package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.component.ChildrenSet;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesValuesSetChildrenSet implements Attributes, ValuesSet, ChildrenSet {

    private volatile NameAttributes nameAttributes;
    private volatile Set<Value> values;
    private volatile Set<NewName> children;

    public AttributesValuesSetChildrenSet(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
    }

    public AttributesValuesSetChildrenSet(NameAttributes nameAttributes, Value[] values, Set<NewName> children) {
        this.nameAttributes = nameAttributes;
        this.values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        this.values.addAll(Arrays.asList(values));
        this.children = children;
    }

    public AttributesValuesSetChildrenSet(NameAttributes nameAttributes, Set<Value> values, NewName[] children) {
        this.nameAttributes = nameAttributes;
        this.values = values;
        this.children = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        this.children.addAll(Arrays.asList(children));
    }

    public AttributesValuesSetChildrenSet(String defaultDisplayName, Set<Value> values, Set<NewName> children) throws Exception {
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
    public Set<Value> internalGetValues() {
        return values;
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}