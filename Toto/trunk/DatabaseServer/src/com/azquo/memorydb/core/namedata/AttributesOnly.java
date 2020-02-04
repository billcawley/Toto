package com.azquo.memorydb.core.namedata;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AttributesOnly implements NameData{

    private volatile NameAttributes nameAttributes; // since NameAttributes is immutable there's no need for this to be volatile I don't think. Could test performance, I suppose volatile is slightly preferable? Happens before?

    public AttributesOnly() {
        this.nameAttributes = new NameAttributes();
    }

    @Override
    public String getDefaultDisplayName() {
        return nameAttributes.getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME);
    }

    // todo - on true will have to remove outside. Will cause some redundancy I guess.
    // maybe check with a
    //getAzquoMemoryDB().getIndex().removeAttributeFromNameInAttributeNameMap(attributeName, existing, this);

    @Override
    public boolean setAttribute(String attributeName, String attributeValue) throws Exception {
        // deal with uppercasing or whatever outside, this just deals with storage
        /* code adapted from map based code to lists assume nameAttributes reference only set in code synchronized in these three functions and constructors*/
        List<String> attributeKeys = new ArrayList<>(nameAttributes.getAttributeKeys());
        List<String> attributeValues = new ArrayList<>(nameAttributes.getAttributeValues());

        int index = attributeKeys.indexOf(attributeName);
        String existing = null;
        if (index != -1) {
            // we want an index out of bounds to be thrown here if they don't match
            existing = attributeValues.get(index);
        }
        if (attributeValue == null || attributeValue.length() == 0) {
            // delete it
            if (existing != null) {
                attributeKeys.remove(index);
                attributeValues.remove(index);
                nameAttributes = new NameAttributes(attributeKeys, attributeValues);
                return true;
            }
        }
        if (existing != null && existing.equals(attributeValue)) {
            return false;
        }
        if (existing != null) {
            // just update the values
            attributeValues.remove(index);
            attributeValues.add(index, attributeValue);
        } else {
            // a new one
            attributeKeys.add(attributeName);
            attributeValues.add(attributeValue);
        }
        nameAttributes = new NameAttributes(attributeKeys, attributeValues);
        return true;
    }

    @Override
    public boolean removeAttribute(String attributeName) throws Exception {
        int index = nameAttributes.getAttributeKeys().indexOf(attributeName);
        if (index != -1) {
            List<String> attributeKeys = new ArrayList<>(nameAttributes.getAttributeKeys());
            List<String> attributeValues = new ArrayList<>(nameAttributes.getAttributeValues());
            attributeKeys.remove(index);
            attributeValues.remove(index);
            nameAttributes = new NameAttributes(attributeKeys, attributeValues);
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
        return nameAttributes.getAsMap();
    }

    @Override
    public List<String> getAttributeKeys() {
        return nameAttributes.getAttributeKeys();
    }

    @Override
    public NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return this;
    }

    @Override
    public String getAttribute(String attribute) {
        return nameAttributes.getAttribute(attribute);
    }

    @Override
    public String getAttributesForFastStore() {
        // todo
        return null;
    }
}
