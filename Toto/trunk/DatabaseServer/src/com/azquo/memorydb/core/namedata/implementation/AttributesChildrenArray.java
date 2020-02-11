package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;

import java.util.Collections;

public class AttributesChildrenArray implements Attributes, ChildrenArray {

    private volatile NameAttributes nameAttributes;
    private volatile Name[] children;

    public AttributesChildrenArray(){
        nameAttributes = new NameAttributes();
        children = new Name[0];
    }

    public AttributesChildrenArray(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        children = new Name[0];
    }

    public AttributesChildrenArray(String defaultDisplayName, Name[] children) throws Exception{
        this.nameAttributes = new NameAttributes(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, Collections.singletonList(defaultDisplayName));
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
    public NameData getImplementationThatCanAddValue() {
        return new AttributesValuesArrayChildrenArray(nameAttributes, children);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return canAddChild() ? this : new AttributesChildrenSet(nameAttributes, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}