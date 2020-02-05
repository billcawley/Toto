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
