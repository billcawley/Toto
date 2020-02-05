package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesValuesSetChildrenArray implements Attributes, ValuesSet, ChildrenArray {

    private volatile NameAttributes nameAttributes;
    private volatile Set<Value> values;
    private volatile NewName[] children;

    public AttributesValuesSetChildrenArray(){
        nameAttributes = new NameAttributes();
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
        children = new NewName[0];
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
    public NewName[] internalGetChildren() {
        return children;
    }

    @Override
    public void internalSetChildren(NewName[] children) {
        this.children = children;
    }

    @Override
    public Set<Value> internalGetValues() {
        return values;
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