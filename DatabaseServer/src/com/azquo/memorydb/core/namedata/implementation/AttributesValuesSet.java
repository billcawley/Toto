package com.azquo.memorydb.core.namedata.implementation;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.component.Attributes;
import com.azquo.memorydb.core.namedata.component.ValuesSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttributesValuesSet implements Attributes, ValuesSet {

    private volatile NameAttributes nameAttributes;
    private volatile Set<Value> values;

    public AttributesValuesSet(NameAttributes nameAttributes){
        this.nameAttributes = nameAttributes;
        values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));// the way to get a thread safe set!
    }

    public AttributesValuesSet(NameAttributes nameAttributes, Value[] values) {
        this.nameAttributes = nameAttributes;
        this.values = Collections.newSetFromMap(new ConcurrentHashMap<>(ARRAYTHRESHOLD + 1));
        this.values.addAll(Arrays.asList(values));
    }

    public AttributesValuesSet(String defaultDisplayName, Set<Value> values) throws Exception {
        this.nameAttributes = new NameAttributes(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, Collections.singletonList(defaultDisplayName));
        this.values = values;
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
    public Set<Value> internalGetValues() {
        return values;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return new AttributesValuesSetChildrenArray(nameAttributes, values);
    }

}
