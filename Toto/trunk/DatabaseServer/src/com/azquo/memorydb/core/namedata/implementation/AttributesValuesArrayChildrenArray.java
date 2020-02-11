package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.NameInterface;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

import java.util.Collections;

public class AttributesValuesArrayChildrenArray implements Attributes, ValuesArray, ChildrenArray {

    private volatile NameAttributes nameAttributes;
    private volatile Value[] values;
    private volatile NameInterface[] children;

    public AttributesValuesArrayChildrenArray(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        values = new Value[0];
        children = new NameInterface[0];
    }

    public AttributesValuesArrayChildrenArray(NameAttributes nameAttributes, NameInterface[] children) {
        this.nameAttributes = nameAttributes;
        values = new Value[0];
        this.children = children;
    }

    public AttributesValuesArrayChildrenArray(NameAttributes nameAttributes, Value[] values) {
        this.nameAttributes = nameAttributes;
        this.values = values;
        children = new NameInterface[0];
    }

    public AttributesValuesArrayChildrenArray(String defaultDisplayName, Value[] values, NameInterface[] children) throws Exception {
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
    public NameInterface[] internalGetChildren() {
        return children;
    }

    @Override
    public void internalSetChildren(NameInterface[] children) {
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

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}