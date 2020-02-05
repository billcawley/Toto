package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.namedata.NameData;

public class Attributes implements com.azquo.memorydb.core.namedata.component.Attributes {

    private volatile NameAttributes nameAttributes;

    public Attributes(){
        nameAttributes = new NameAttributes();
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
    public NameData getImplementationThatCanAddValue() {
        return null;
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
