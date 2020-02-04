package com.azquo.memorydb.core.namedata;

public class DefaultDisplayName implements DefaultDisplayNameInterface {

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
    public String getAttributesForFastStore() {
        return null;
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return null;
    }

}
