package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

public class AttributesValuesArray implements Attributes, ValuesArray {

    private volatile NameAttributes nameAttributes;
    private volatile Value[] values;

    public AttributesValuesArray(){
        nameAttributes = new NameAttributes();
        values = new Value[0];
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