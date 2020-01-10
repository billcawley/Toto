package com.azquo.memorydb.core.namedata;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultDisplayNameOnly implements NameData{

    String defaultDisplayName;

    public DefaultDisplayNameOnly() {
        this.defaultDisplayName = null; // emulate empty attributes
    }

    @Override
    public boolean hasValues() {
        return false;
    }

    @Override
    public void valueArrayCheck() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addToValues(Value value, boolean backupRestore) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeFromValues(Value value) {
        // existing behavior is to do nothing if asked to remove a value that isn't there
    }

    @Override
    public Value[] directArrayValues() {
        return null;
    }

    @Override
    public Set<Value> directSetValues() {
        return null;
    }

    @Override
    public boolean canAddValue() {
        return false;
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return null;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public void addToChildren(Name name, boolean backupRestore) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeFromChildren(Name name) {
        // as with values just do nothing, we have no children her
    }

    @Override
    public Name[] directArrayChildren() {
        return null;
    }

    @Override
    public Set<Name> directSetChildren() {
        return null;
    }

    @Override
    public boolean canAddChild() {
        return false;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return null;
    }

    @Override
    public Map<String, String> getAttributes() {
        return null;
    }

    @Override
    public List<String> getAttributeKeys() {
        return null;
    }

    @Override
    public boolean canSetAttributesOtherThanDefaultDisplayName() {
        return false;
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return null;
    }

    @Override
    public void setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception {
        if (!attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            throw new UnsupportedOperationException();
        }
        defaultDisplayName = attributeValue;
    }

    @Override
    public void removeAttributeWillBePersisted(String attributeName) throws Exception {
        if (attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            defaultDisplayName = null; // will cause NPEs but this emulates NameAttributes
        }
    }
}
