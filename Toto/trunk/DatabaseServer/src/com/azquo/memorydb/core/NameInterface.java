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

public abstract class NameInterface extends AzquoMemoryDBEntity {
    int ARRAYTHRESHOLD = 512; // if arrays which need distinct members hit above this switch to sets. A bit arbitrary, might be worth testing (speed vs memory usage)

    NameInterface(AzquoMemoryDB azquoMemoryDB, int id) throws Exception {
        super(azquoMemoryDB, id);
    }

    NameInterface(AzquoMemoryDB azquoMemoryDB, int id, boolean forceIdForBackupRestore) throws Exception {
        super(azquoMemoryDB, id, forceIdForBackupRestore);
    }

    // for convenience but be careful where it is used . . .
    abstract String getDefaultDisplayName();

    abstract Provenance getProvenance();

    // some of these were not exposed before - an issue?

    abstract void addToParents(final NameInterface name, boolean databaseIsLoading);

    abstract void removeFromParents(final NameInterface name);

    abstract void addToParents(final NameInterface name);

    abstract void findAllParents(final Set<NameInterface> allParents);

    abstract void setProvenanceWillBePersisted(final Provenance provenance);

    abstract void addParentNamesToCollection(Collection<NameInterface> names, final int currentLevel, final int level);

    abstract void addChildrenToCollection(Collection<NameInterface> namesFound, final int currentLevel, final int level);

    abstract void addValuesToCollection(Collection<Value> values);

    abstract NameInterface memberName(NewName topSet);





    abstract Collection<Value> getValues();

    abstract int getValueCount();

    abstract boolean hasValues();

    abstract List<NameInterface> getParents();

    abstract boolean hasParents();

    abstract Collection<NameInterface> findAllParents();

    abstract NameInterface findATopParent();

    abstract Collection<NameInterface> findAllChildren();

    abstract Set<Value> findValuesIncludingChildren();

    abstract Collection<NameInterface> getChildren();

    // if used incorrectly means NPE, I don't mind about this for the mo. We're allowing interpretSetTerm low level access to the list and sets to stop unnecessary collection copying in the query parser
    abstract Set<NameInterface> getChildrenAsSet();

    abstract List<NameInterface> getChildrenAsList();

    abstract boolean hasChildren();

    // todo - new implementations might mean that a false to true race condition may result in a null array reference. Since it won't go set->array then trying for the array first would be the thing to do
    abstract boolean hasChildrenAsSet();

    // pass conneciton not provenance - we want the connection to know if the provenance was used or not
    abstract void setChildrenWillBePersisted(Collection<NameInterface> newChildren, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    abstract void addChildWillBePersisted(NameInterface child, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    abstract void removeFromChildrenWillBePersisted(NameInterface NameInterface, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    abstract Map<String, String> getAttributes();

    abstract void setAttributeWillBePersisted(String attributeNameInterface, String attributeValue, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    abstract void clearAttributes(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;

    abstract String getAttribute(String attributeNameInterface);

    abstract String getAttribute(String attributeNameInterface, boolean parentCheck, Set<NameInterface> checked);

    abstract String getAttribute(String attributeNameInterface, boolean parentCheck, Set<NameInterface> checked, NameInterface origNameInterface, int level);

    abstract String getAttributesForFastStore();

    abstract byte[] getChildrenIdsAsBytes();

    abstract void delete(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception;
}
