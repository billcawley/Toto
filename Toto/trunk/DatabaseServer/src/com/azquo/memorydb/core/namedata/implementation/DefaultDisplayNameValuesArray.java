package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ValuesArray;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;

public class DefaultDisplayNameValuesArray implements DefaultDisplayName, ValuesArray {

    private volatile String defaultDisplayName;
    private volatile Value[] values;

    public DefaultDisplayNameValuesArray(){
        defaultDisplayName = null;
        values = new Value[0];
    }

    public DefaultDisplayNameValuesArray(String defaultDisplayName) {
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
    public Value[] internalGetValues() {
        return values;
    }

    @Override
    public void internalSetValues(Value[] values) {
        this.values = values;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return canAddValue() ? this : new DefaultDisplayNameValuesSet(defaultDisplayName, values);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return new DefaultDisplayNameValuesArrayChildrenArray(defaultDisplayName, values);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception{
        return new AttributesValuesArray(defaultDisplayName, values);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}