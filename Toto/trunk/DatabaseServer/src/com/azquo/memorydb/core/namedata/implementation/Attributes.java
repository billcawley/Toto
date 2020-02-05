package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.namedata.NameData;

import java.util.Collections;

public class Attributes implements com.azquo.memorydb.core.namedata.component.Attributes {

    private volatile NameAttributes nameAttributes;

    public Attributes(){
        nameAttributes = new NameAttributes();
    }

    public Attributes(String defaultDisplayName) throws Exception{
        this.nameAttributes = new NameAttributes(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, Collections.singletonList(defaultDisplayName));
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
        return new AttributesValuesArray(nameAttributes);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return new AttributesChildrenArray(nameAttributes);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}
