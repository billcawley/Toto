package com.azquo.memorydb.core.namedata;

/*

Created EFC 01/01/20

The original name implementation had space for children and values both as sets and arrays and space for multiple attributes,

This is not efficient as often a name will only have a default display name attribute or will have no values or children or,
 if it does, it will of course have either a set or an array, not both.

 Adding objects to deal with this will add complexity and an object header and pointer overhead in memory but there should
 be a greater saving as each implementation won't have superfluous pointers etc.

I'm not putting parents in here for the mo. The vast majority of names have them and just as an array.

Stuff in here is only to be accessed from Name and may not be considered thread safe, Name should deal with that.

It is low level - the purpose here is to save memory so I'm leaving as much logic in Name as possible

Low level dealing with attributes (just default display name or more), values and children which are either none or arrays or sets

 */

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;

import java.util.*;

public interface NameData {

    public static final int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets. A bit arbitrary, might be worth testing (speed vs memory usage)

    boolean hasValues();

    void valueArrayCheck() throws UnsupportedOperationException;

    void addToValues(final Value value, boolean backupRestore) throws Exception;

    void removeFromValues(final Value value);

    Value[] directArrayValues();

    Set<Value> directSetValues();

    boolean canAddValue();

    NameData getImplementationThatCanAddValue();



    boolean hasChildren();

    void addToChildren(final Name name, boolean backupRestore) throws Exception;

    void removeFromChildren(final Name name);

    // should provide direct access to the field - replacing the direct access calls used before. Often implementations might return null.
    Name[] directArrayChildren();

    Set<Name> directSetChildren();

    boolean canAddChild();

    NameData getImplementationThatCanAddChild();


    Map<String, String> getAttributes();

    List<String> getAttributeKeys();

    boolean canSetAttributesOtherThanDefaultDisplayName();

    NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName();

    // error or not??
    void setAttributeWillBePersisted(String attributeName, String attributeValue) throws Exception;

    void removeAttributeWillBePersisted(String attributeName) throws Exception;

    // need check function that the implementation supports adding attributes
}
