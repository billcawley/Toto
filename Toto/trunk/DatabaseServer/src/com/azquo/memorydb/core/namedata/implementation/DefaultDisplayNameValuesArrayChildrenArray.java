package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.memorydb.core.NameInterface;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.ChildrenArray;
import com.azquo.memorydb.core.namedata.component.DefaultDisplayName;
import com.azquo.memorydb.core.namedata.component.ValuesArray;

public class DefaultDisplayNameValuesArrayChildrenArray implements DefaultDisplayName, ValuesArray, ChildrenArray {

    private volatile String defaultDisplayName;
    private volatile Value[] values;
    private volatile NameInterface[] children;

    public DefaultDisplayNameValuesArrayChildrenArray(String defaultDisplayName){
        this.defaultDisplayName = defaultDisplayName;
        values = new Value[0];
        children = new NameInterface[0];
    }

    public DefaultDisplayNameValuesArrayChildrenArray(String defaultDisplayName, NameInterface[] children) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = new Value[0];
        this.children = children;
    }

    public DefaultDisplayNameValuesArrayChildrenArray(String defaultDisplayName, Value[] values) {
        this.defaultDisplayName = defaultDisplayName;
        this.values = values;
        children = new NameInterface[0];
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
    public Value[] internalGetValues() {
        return values;
    }

    @Override
    public void internalSetValues(Value[] values) {
        this.values = values;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return canAddValue() ? this : new DefaultDisplayNameValuesSetChildrenArray(defaultDisplayName, values, children);
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return canAddChild() ? this : new DefaultDisplayNameValuesArrayChildrenSet(defaultDisplayName, values, children);
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() throws Exception {
        return new AttributesValuesArrayChildrenArray(defaultDisplayName, values, children);
    }

    @Override
    public String getAttributesForFastStore() {
        return null;
    }

}