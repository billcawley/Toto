package com.azquo.memorydb.core;

import com.azquo.memorydb.AzquoMemoryDBConnection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*

Created by EFC 10/02/2020

I need a superclass while there are multiple implementations of Name. There may be a case for l

 */

public abstract class Name extends AzquoMemoryDBEntity {
    public static int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets. A bit arbitrary, might be worth testing (speed vs memory usage)

    Name(AzquoMemoryDB azquoMemoryDB, int id) throws Exception {
        super(azquoMemoryDB, id);
    }

    Name(AzquoMemoryDB azquoMemoryDB, int id, boolean forceIdForBackupRestore) throws Exception {
        super(azquoMemoryDB, id, forceIdForBackupRestore);
    }

    // for convenience but be careful where it is used . . .
    public abstract String getDefaultDisplayName();

    public abstract Provenance getProvenance();

    // some of these were not exposed before - an issue?

    abstract void addToParents(final Name name, boolean databaseIsLoading);

    abstract void removeFromParents(final Name name);

    abstract void addToParents(final Name name);

    public abstract void findAllParents(final Set<Name> allParents);

    abstract void setProvenanceWillBePersisted(final Provenance provenance);

    public abstract void addParentNamesToCollection(Collection<Name> names, final int currentLevel, final int level);

    public abstract void addChildrenToCollection(Collection<Name> namesFound, final int currentLevel, final int level);

    abstract void addValuesToCollection(Collection<Value> values);

    public abstract Name memberName(Name topSet);

    abstract void findAllChildren(final Set<Name> allChildren);







    public abstract Collection<Value> getValues();

    public abstract int getValueCount();

    public abstract boolean hasValues();

    abstract void removeFromValues(final Value value);

    abstract void addToValues(final Value value) throws Exception;

    public abstract List<Name> getParents();

    public abstract boolean hasParents();

    public abstract Collection<Name> findAllParents();

    public abstract Name findATopParent();

    public abstract Collection<Name> findAllChildren();

    public abstract Set<Value> findValuesIncludingChildren();

    public abstract Collection<Name> getChildren();

    // if used incorrectly means NPE, I don't mind about this for the mo. We're allowing interpretSetTerm low level access to the list and sets to stop unnecessary collection copying in the query parser
    public abstract Set<Name> getChildrenAsSet();

    public abstract List<Name> getChildrenAsList();

    public abstract boolean hasChildren();

    // todo - new implementations might mean that a false to true race condition may result in a null array reference. Since it won't go set->array then trying for the array first would be the thing to do
    public abstract boolean hasChildrenAsSet();

    // pass conneciton not provenance - we want the connection to know if the provenance was used or not
    public abstract void setChildrenWillBePersisted(Collection<Name> newChildren, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    public abstract void addChildWillBePersisted(Name child, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    public abstract void removeFromChildrenWillBePersisted(Name NameInterface, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    public abstract Map<String, String> getAttributes();

    public abstract void setAttributeWillBePersisted(String attributeNameInterface, String attributeValue, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    public abstract void clearAttributes(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    public abstract String getAttribute(String attributeNameInterface);

    public abstract String getAttribute(String attributeNameInterface, boolean parentCheck, Set<Name> checked);

    public abstract String getAttribute(String attributeNameInterface, boolean parentCheck, Set<Name> checked, Name origNameInterface, int level);

    public abstract String getAttributesForFastStore();

    public abstract byte[] getChildrenIdsAsBytes();

    public abstract void delete(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    abstract void clearChildrenCaches();

    abstract void checkValue(final Value value, boolean backupRestore) throws Exception;

    abstract List<String> getAttributeKeys();

    abstract void link(byte[] childrenCache, boolean backupRestore) throws Exception;

    abstract void parentArrayCheck();

    abstract void valueArrayCheck();

    abstract NameAttributes getRawAttributes();
}
