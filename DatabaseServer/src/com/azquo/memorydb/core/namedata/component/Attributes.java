package com.azquo.memorydb.core.namedata.component;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.NameAttributes;
import com.azquo.memorydb.core.namedata.NameData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface Attributes extends NameData {

    default String getDefaultDisplayName() {
        return internalGetNameAttributes().getAttribute(StringLiterals.DEFAULT_DISPLAY_NAME);
    }

    // if there was an old value that was overridden then return it. Required externally for the azquo memory db indexes
    default String setAttribute(String attributeName, String attributeValue) throws Exception {
        // deal with uppercasing or whatever outside, this just deals with storage
        /* code adapted from map based code to lists assume nameAttributes reference only set in code synchronized in these three functions and constructors*/
        final NameAttributes nameAttributes = internalGetNameAttributes();
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
                internalSetNameAttributes(new NameAttributes(attributeKeys, attributeValues));
                return existing;
            }
        }
        if (existing != null && existing.equals(attributeValue)) {
            return null; // don't return existing as nothing changed
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
        internalSetNameAttributes(new NameAttributes(attributeKeys, attributeValues));
        return existing;
    }

    default String removeAttribute(String attributeName) throws Exception {
        final NameAttributes nameAttributes = internalGetNameAttributes();
        int index = nameAttributes.getAttributeKeys().indexOf(attributeName);
        if (index != -1) {
            List<String> attributeKeys = new ArrayList<>(nameAttributes.getAttributeKeys());
            List<String> attributeValues = new ArrayList<>(nameAttributes.getAttributeValues());
            attributeKeys.remove(index);
            String existing = attributeValues.remove(index);
            internalSetNameAttributes(new NameAttributes(attributeKeys, attributeValues));
            return existing;
        }
        return null;
    }

    default Map<String, String> getAttributes() {
        return internalGetNameAttributes().getAsMap();
    }

    default List<String> getAttributeKeys() {
        return internalGetNameAttributes().getAttributeKeys();
    }

    default String getAttribute(String attribute) {
        return internalGetNameAttributes().getAttribute(attribute);
    }

    default NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName() {
        return this;
    }

    default String getAttributesForFastStore() {
        return internalGetNameAttributes().getAttributesForFastStore();
    }

    // must be implemented by the "roll your own" class

    NameAttributes internalGetNameAttributes();

    void internalSetNameAttributes(NameAttributes nameAttributes);

}
