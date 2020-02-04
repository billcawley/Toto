package com.azquo.memorydb.core.namedata;

import com.azquo.StringLiterals;

import java.util.*;

public interface DefaultDisplayNameInterface extends NameData {

    default String getDefaultDisplayName() {
        return internalGetDefaultDisplayName();
    }

    default boolean setAttribute(String attributeName, String attributeValue) throws Exception {
        if (!attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            throw new UnsupportedOperationException();
        }
        if (!internalGetDefaultDisplayName().equals(attributeValue)){
            internalSetDefaultDisplayName(attributeValue);
            return true;
        }
        return false;
    }

    default boolean removeAttribute(String attributeName) throws Exception {
        if (attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME) && internalGetDefaultDisplayName() != null){
            internalSetDefaultDisplayName(null); // will cause NPEs but this emulates NameAttributes
            return true;
        }
        return false;
    }

    default Map<String, String> getAttributes() {
        if (internalGetDefaultDisplayName() != null){
            Map<String, String> map = new HashMap<>();
            map.put(StringLiterals.DEFAULT_DISPLAY_NAME, internalGetDefaultDisplayName());
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    default List<String> getAttributeKeys() {
        return StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
    }

    default String getAttribute(String attribute) {
        if (attribute.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            return internalGetDefaultDisplayName();
        }
        return null;
    }

    String internalGetDefaultDisplayName();

    void internalSetDefaultDisplayName(String defaultDisplayName);
}