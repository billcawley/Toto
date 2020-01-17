package com.azquo.memorydb.core.namedata;

import com.azquo.StringLiterals;

import java.util.*;

public class DefaultDisplayNameOnly implements NameData{

    String defaultDisplayName;

    public DefaultDisplayNameOnly() {
        this.defaultDisplayName = null; // emulate empty attributes
    }

    @Override
    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }



    @Override
    public boolean setAttribute(String attributeName, String attributeValue) throws Exception {
        if (!attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            throw new UnsupportedOperationException();
        }
        if (!defaultDisplayName.equals(attributeValue)){
            defaultDisplayName = attributeValue;
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAttribute(String attributeName) {
        if (attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME) && defaultDisplayName != null){
            defaultDisplayName = null; // will cause NPEs but this emulates NameAttributes
            return true;
        }
        return false;
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
    public Map<String, String> getAttributes() {
        return null;
    }

    @Override
    public List<String> getAttributeKeys() {
        return null;
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return null;
    }

    @Override
    public String getAttribute(String attribute) {
        if (attribute.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            return defaultDisplayName;
        }
        return null;
    }

    @Override
    public String getAttributesForFastStore() {
        // todo
        return null;
    }
}
