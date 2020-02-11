package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NameInterface;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;

public class DefaultDisplayNameChildrenArray implements DefaultDisplayName, ChildrenArray {

    private volatile String defaultDisplayName;
    private volatile NameInterface[] children;

    public DefaultDisplayNameChildrenArray(){
        defaultDisplayName = null;
        children = new NameInterface[0];
    }

    public DefaultDisplayNameChildrenArray(String defaultDisplayName) {
        this.defaultDisplayName = defaultDisplayName;
    }

    @Override
    public String internalGetDefaultDisplayName() {
        return defaultDisplayName;
    }

    @Override
    public void internalSetDefaultDisplayName(String defaultDisplayName) {
        this.defaultDisplayName = defaultDisplayName;
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
    public NameData getImplementationThatCanAddValue() {
        return new DefaultDisplayNameValuesArrayChildrenArray(defaultDisplayName, children);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return canAddChild() ? this : new DefaultDisplayNameChildrenSet(defaultDisplayName, children);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception {
        return new AttributesChildrenArray(defaultDisplayName, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}