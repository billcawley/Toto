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

import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.NewName;
import com.azquo.memorydb.core.Value;

import java.util.*;

public interface NameData {

    int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets. A bit arbitrary, might be worth testing (speed vs memory usage)

    default boolean hasValues(){
        return false;
    }

    default Collection<Value> getValues(){
        return Collections.emptyList();
    }

    default void valueArrayCheck(){};

    default boolean addToValues(Value value, boolean backupRestore, boolean databaseIsLoading) throws Exception {
        throw new UnsupportedOperationException();
    }

    default boolean removeFromValues(Value value) {
        // existing behavior is to do nothing if asked to remove a value that isn't there
        return false;
    }

    // should provide direct access to the field - replacing the direct access calls used before. Often implementations might return null.
    default Value[] directArrayValues() {
        return null;
    }

    default Set<Value> directSetValues() {
        return null;
    }

    default boolean canAddValue() {
        return false;
    }

    NameData getImplementationThatCanAddValue();

    default boolean hasChildren() {
        return false;
    }

    default Collection<NewName> getChildren() {
        return Collections.emptyList();
    }

    default void addToChildren(NewName name, boolean backupRestore) throws Exception {
        throw new UnsupportedOperationException();
    }

    default void removeFromChildren(NewName name) {
        // as with values just do nothing, we have no children here
    }

    default NewName[] directArrayChildren() {
        return null;
    }

    default Set<NewName> directSetChildren() {
        return null;
    }

    default boolean canAddChild() {
        return false;
    }

    NameData getImplementationThatCanAddChild();

    String getDefaultDisplayName();

    String getAttribute(String attribute);

    default boolean canSetAttributesOtherThanDefaultDisplayName() {
        return false;
    }

    Map<String, String> getAttributes();

    List<String> getAttributeKeys();

    String getAttributesForFastStore();

    NameData getImplementationThatCanSetAttributesOtherThanDefaultDisplayName();

    // need check function that the implementation supports adding attributes
    // error or not??
    boolean setAttribute(String attributeName, String attributeValue) throws Exception;

    boolean removeAttribute(String attributeName) throws Exception;
}
