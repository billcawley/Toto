package com.azquo.memorydb.core;

import com.azquo.StringLiterals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracted from Name by edward on 07/10/16.
 *
  Going to try for attributes as two arrays as this should save a lot of space vs a LinkedHashMap.
 That is to say one makes a new one of these when updating attributes and then switch it in. Immutable, hence atomic
 (but not necessarily immediately visible!) switch of two arrays if an object reference is out of date it will at least be two consistent arrays.

 I have no problem with out of date versions as long as object contents remain consistent.

 Gets to wrap with as list, don't want them altering the length anyway, this does have a bit of an overhead vs the old model but I don't think this will be a biggy.
 Also I know that aslist on two of the gets doesn't make immutable, will be used in name which I consider trusted. If bothered can use unmodifiableList.

 Ok using arrays here has saved about 5% on a database's memory usage. They stay unless there's a big unfixable problem.

 I've put the get in here, stops worry about an object switch in the middle of a get (if it were referenceing the keys and values externally it could go out of sync).

 All fields final, means the constructor should complete before the object reference is generated and the object is published. Immutable as mentioned above.
 */
final class NameAttributes {
    private final String[] attributeKeys;
    private final String[] attributeValues;

    NameAttributes(List<String> attributeKeys, List<String> attributeValues) throws Exception {
        if (attributeKeys.size() != attributeValues.size()) {
            throw new Exception("Keys and values for attributes must match!");
        }
        this.attributeKeys = new String[attributeKeys.size()];
        attributeKeys.toArray(this.attributeKeys);
        this.attributeValues = new String[attributeValues.size()];
        attributeValues.toArray(this.attributeValues);
    }

    // faster init - note this is kind of dangerous in that the arrays could be externally modified, used by the new fast loader
    NameAttributes(String[] attributeKeys, String[] attributeValues) throws Exception {
        this.attributeKeys = attributeKeys;
        this.attributeValues = attributeValues;
    }

    NameAttributes() { // blank default. Fine.
        attributeKeys = new String[0];
        attributeValues = new String[0];
    }

    List<String> getAttributeKeys() {
        return Arrays.asList(attributeKeys);
    }

    List<String> getAttributeValues() {
        return Arrays.asList(attributeValues);
    }

    public String getAttribute(String attributeName) {
        for (int i = 0; i < attributeKeys.length; i++) {
            if (attributeKeys[i].equals(attributeName)) {
                return attributeValues[i];
            }
        }
        return null;
    }

    Map<String, String> getAsMap() {
        Map<String, String> attributesAsMap = new HashMap<>(attributeKeys.length);
        int count = 0;
        for (String key : attributeKeys) { // hmm, can still access and foreach on the internal array. Np I suppose!
            attributesAsMap.put(key, attributeValues[count]);
            count++;
        }
        return attributesAsMap;
    }

    String getAttributesForFastStore() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < attributeKeys.length; i++) {
            if (i != 0) {
                stringBuilder.append(StringLiterals.ATTRIBUTEDIVIDER);
            }
            stringBuilder.append(attributeKeys[i]);
            stringBuilder.append(StringLiterals.ATTRIBUTEDIVIDER);
            stringBuilder.append(attributeValues[i]);
        }
        return stringBuilder.toString();
    }
}
