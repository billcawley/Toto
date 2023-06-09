package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

import java.util.Collections;

public class AttributesValuesArray implements Attributes, ValuesArray {

    private volatile NameAttributes nameAttributes;
    private volatile Value[] values;

    public AttributesValuesArray(NameAttributes nameAttributes, int noValues){
        this.nameAttributes = nameAttributes;
        values = new Value[noValues];
    }

    public AttributesValuesArray(String defaultDisplayName, Value[] values) throws Exception {
        this.nameAttributes = new NameAttributes(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, Collections.singletonList(defaultDisplayName));
        this.values = values;
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
    public Value[] internalGetValues() {
        return values;
    }

    @Override
    public void internalSetValues(Value[] values) {
        this.values = values;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return canAddValue() ? this : new AttributesValuesSet(nameAttributes, values);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return new AttributesValuesArrayChildrenArray(nameAttributes, values);
    }

}