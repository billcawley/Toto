package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.namedata.NameData;

public class DefaultDisplayName implements com.azquo.memorydb.core.namedata.component.DefaultDisplayName {

    private volatile String defaultDisplayName;

    public DefaultDisplayName(){
        defaultDisplayName = null;
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
    public NameData getImplementationThatCanAddValue() {
        return new DefaultDisplayNameValuesArray(defaultDisplayName);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return new DefaultDisplayNameChildrenArray(defaultDisplayName);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception{
        return new Attributes(defaultDisplayName);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}
