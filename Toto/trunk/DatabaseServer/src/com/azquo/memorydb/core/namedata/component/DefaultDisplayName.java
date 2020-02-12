package com.azquo.memorydb.core.namedata.component;

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.core.namedata.UnsupportedOperationException;

import java.util.*;

public interface DefaultDisplayName extends NameData {

    default String getDefaultDisplayName() {
        return internalGetDefaultDisplayName();
    }

    default String setAttribute(String attributeName, String attributeValue) throws Exception {
        if (!attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME)){
            throw new UnsupportedOperationException();
        }
        if (!internalGetDefaultDisplayName().equals(attributeValue)){
            String existing = internalGetDefaultDisplayName();
            internalSetDefaultDisplayName(attributeValue);
            return existing;
        }
        return null;
    }

    default String removeAttribute(String attributeName) throws Exception {
        if (attributeName.equals(StringLiterals.DEFAULT_DISPLAY_NAME) && internalGetDefaultDisplayName() != null){
            String existing = internalGetDefaultDisplayName();
            internalSetDefaultDisplayName(null); // will cause NPEs but this emulates NameAttributes
            return existing;
        }
        return null;
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

    default String getAttributesForFastStore() {
        StringBuilder stringBuilder = new StringBuilder();
        if (internalGetDefaultDisplayName() != null){
            stringBuilder.append(StringLiterals.DEFAULT_DISPLAY_NAME);
            stringBuilder.append(StringLiterals.ATTRIBUTEDIVIDER);
            stringBuilder.append(internalGetDefaultDisplayName());
        }
        return stringBuilder.toString();
    }

    String internalGetDefaultDisplayName();

    void internalSetDefaultDisplayName(String defaultDisplayName);
}