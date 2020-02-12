package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

import java.util.Collections;

public class AttributesValuesArrayChildrenArray implements Attributes, ValuesArray, ChildrenArray {

    private volatile NameAttributes nameAttributes;
    private volatile Value[] values;
    private volatile Name[] children;

    public AttributesValuesArrayChildrenArray(NameAttributes nameAttributes, int noValues){
        this.nameAttributes = nameAttributes;
        values = new Value[noValues];
        children = new Name[0];
    }

    public AttributesValuesArrayChildrenArray(NameAttributes nameAttributes, int noValues, Name[] children) {
        this.nameAttributes = nameAttributes;
        values = new Value[noValues];
        this.children = children;
    }

    public AttributesValuesArrayChildrenArray(NameAttributes nameAttributes, Value[] values) {
        this.nameAttributes = nameAttributes;
        this.values = values;
        children = new Name[0];
    }

    public AttributesValuesArrayChildrenArray(String defaultDisplayName, Value[] values, Name[] children) throws Exception {
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
    public Value[] internalGetValues() {
        return values;
    }

    @Override
    public void internalSetValues(Value[] values) {
        this.values = values;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return canAddValue() ? this : new AttributesValuesSetChildrenArray(nameAttributes, values, children);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return canAddChild() ? this : new AttributesValuesArrayChildrenSet(nameAttributes, values, children);
    }

}