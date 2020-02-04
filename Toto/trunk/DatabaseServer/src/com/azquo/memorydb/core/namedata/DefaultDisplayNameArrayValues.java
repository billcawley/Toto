package com.azquo.memorydb.core.namedata;

import com.azquo.memorydb.core.Value;

public class DefaultDisplayNameArrayValues implements DefaultDisplayNameInterface, ArrayValuesInterface {

    private volatile String defaultDisplayName;
    private volatile Value[] values;

    public DefaultDisplayNameArrayValues(){
        defaultDisplayName = null;
        values = new Value[0];
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
        return this;
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

    @Override
    public Value[] internalGetValues() {
        return values;
    }

    @Override
    public void internalSetValues(Value[] values) {
        this.values = values;
    }
}