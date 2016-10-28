package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Extracted from NameQueryParser by edward on 27/10/16.
 * <p>
 * Functions in here to make NameQueryParser clearer and as they're dedicated highly optimised bits of code.
 */
class NameStackOperators {

    // * operator
    static void setIntersection(final List<NameSetList> nameStack, int stackCount) {
        //assume that the second term implies 'level all'
        //long start = System.currentTimeMillis();
//                System.out.println("starting * set sizes  nameStack(stackcount)" + nameStack.get(stackCount).getAsCollection().size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).getAsCollection().size());
        NameSetList previousSet = nameStack.get(stackCount - 1);
        // preserving ordering important - retainall on a mutable set, if available, might save a bit vs creating a new one
        // for the moment create a new collection, list or set based on the type of "previous set"
        Set<Name> setIntersectionSet = null;
        List<Name> setIntersectionList = null;
        if (previousSet.set != null) { // not ordered
            setIntersectionSet = HashObjSets.newMutableSet();
            Set<Name> previousSetSet = previousSet.set;
            for (Name name : nameStack.get(stackCount).getAsCollection()) { // if the last one on the stack is a list or set it doens't matter I'm not doing a contains on it
                if (previousSetSet.contains(name)) {
                    setIntersectionSet.add(name);
                }
                for (Name child : name.findAllChildren()) {
                    if (previousSetSet.contains(child)) {
                        setIntersectionSet.add(child);
                    }
                }
            }
        } else { // I need to use previous set as the outside loop for ordering
            setIntersectionList = new ArrayList<>(); // keep it as a list
            Set<Name> lastSet = nameStack.get(stackCount).set != null ? nameStack.get(stackCount).set : HashObjSets.newMutableSet(nameStack.get(stackCount).list); // wrap the last one in a set if it's not a set
            for (Name name : previousSet.list) {
                if (lastSet.contains(name)) {
                    setIntersectionList.add(name);
                } else { // we've already checked the top members, check all children to see if it's in there also
                    for (Name intersectName : lastSet) {
                        if (intersectName.findAllChildren().contains(name)) {
                            setIntersectionList.add(name);
                        }
                    }
                }
            }
        }
        nameStack.set(stackCount - 1, new NameSetList(setIntersectionSet, setIntersectionList, true)); // replace the previous NameSetList
        //System.out.println("after new retainall " + (System.currentTimeMillis() - start) + "ms");
        nameStack.remove(stackCount);
    }

    // / operator
    // go down one level then find all parents then intersect that. E.g. customer / all dates. Down one level on
    // customer to find orders then go up all through parents and intersect dates to get the dates for those orders
    static void childParentsSetIntersection(AzquoMemoryDBConnection azquoMemoryDBConnection, final List<NameSetList> nameStack, int stackCount) throws Exception {
        // a possible performance hit here, not sure of other possible optimseations
        // ok what's on the stack may be mutable but I'm going to have to make a copy - if I modify it the iterator on the loop below will break
        Set<Name> parents = HashObjSets.newMutableSet(nameStack.get(stackCount).getAsCollection());
        //long start = System.currentTimeMillis();
        //long heapMarker = ((runtime.totalMemory() - runtime.freeMemory()) / mb);
        //System.out.println("aft mutable init " + heapMarker);
        //System.out.println("starting / set sizes  nameStack(stackcount)" + nameStack.get(stackCount).getAsCollection().size() + " nameStack(stackcount - 1) " + nameStack.get(stackCount - 1).getAsCollection().size());
        Collection<Name> lastName = nameStack.get(stackCount).getAsCollection();
        // if filtering brand it means az_brand - this is for the pivot functionality, pivot filter and pivot header
        if (lastName.size() == 1) {
            Name setName = lastName.iterator().next();
            lastName = setName.findAllChildren();
            if (lastName.size() == 0 && setName.getDefaultDisplayName().startsWith("az_")) {
                setName = NameService.findByName(azquoMemoryDBConnection, setName.getDefaultDisplayName().substring(3));
                if (setName != null) {
                    lastName = setName.getChildren();
                }
            }
        }
        for (Name child : lastName) {
            Name.findAllParents(child, parents); // new call to static function cuts garbage generation a lot
        }
        //System.out.println("find all parents in parse query part 1 " + (now - start) + " set sizes parents " + parents.size() + " heap increase = " + (((runtime.totalMemory() - runtime.freeMemory()) / mb) - heapMarker) + "MB");
        //start = System.currentTimeMillis();
        //nameStack.get(stackCount - 1).retainAll(parents); //can't do this any more, need to make a new one
        NameSetList previousSet = nameStack.get(stackCount - 1);
        // ok going to try to get a little clever here since it can be mutable
        if (previousSet.mutable) {
            if (previousSet.list != null) {
                previousSet.list.retainAll(parents);
            } else { // I keep assuming set won't be null. I guess we'll see
                previousSet.set.retainAll(parents);
            }
        } else { // need to make a new one
            Set<Name> setIntersectionSet = null;
            List<Name> setIntersectionList = null;
            if (previousSet.list != null) { // ordered
                setIntersectionList = new ArrayList<>();
                // we must use previous set on the outside
                for (Name name : previousSet.list) {
                    if (parents.contains(name)) {
                        setIntersectionList.add(name);
                    }
                }
            } else { // need to make a new set, unordered, check set sizes in an attempt to keep speed high
                setIntersectionSet = HashObjSets.newMutableSet(); // testing shows no harm, should be a bit faster and better on memory
                Set<Name> previousSetSet = previousSet.set;
                if (previousSetSet.size() < parents.size()) { // since contains should be the same regardless of set size we want to iterate the smaller one to create the intersection
                    for (Name name : previousSetSet) {
                        if (parents.contains(name)) {
                            setIntersectionSet.add(name);
                        }
                    }
                } else {
                    for (Name name : parents) {
                        if (previousSetSet.contains(name)) {
                            setIntersectionSet.add(name);
                        }
                    }
                }
            }
            nameStack.set(stackCount - 1, new NameSetList(setIntersectionSet, setIntersectionList, true)); // replace the previous NameSetList
        }
        //System.out.println("after retainall " + (System.currentTimeMillis() - start));
        nameStack.remove(stackCount);
    }

    // - operator. Remove from set
    static void removeFromSet(final List<NameSetList> nameStack, int stackCount) {
        // using immutable sets on the stack causes more code here but populating the stack should be MUCH faster
        // ok I have the mutable option now
        if (nameStack.get(stackCount - 1).mutable) {
            nameStack.get(stackCount - 1).getAsCollection().removeAll(nameStack.get(stackCount).getAsCollection());
        } else { // make a new one
            Set<Name> currentSet = nameStack.get(stackCount).set != null ? nameStack.get(stackCount).set : HashObjSets.newMutableSet(nameStack.get(stackCount).list); // convert to a set if it's not. Faster contains.
            NameSetList previousSet = nameStack.get(stackCount - 1);
            // standard list or set check
            Set<Name> resultAsSet = null;
            List<Name> resultAsList = null;
            // instantiate the correct type of collection and point result at it
            Collection<Name> result = previousSet.set != null ? (resultAsSet = HashObjSets.newMutableSet()) : (resultAsList = new ArrayList<>()); // assignment expression, I hope clear.
            // populate result with the difference
            for (Name name : previousSet.getAsCollection()) {
                if (!currentSet.contains(name)) { // only the ones not in the current set
                    result.add(name);
                }
            }
            nameStack.set(stackCount - 1, new NameSetList(resultAsSet, resultAsList, true)); // replace the previous NameSetList
        }
        nameStack.remove(stackCount);
    }

    // + operator.
    static void addSets(final List<NameSetList> nameStack, int stackCount) {
        //nameStack.get(stackCount - 1).addAll(nameStack.get(stackCount));
        if (nameStack.get(stackCount - 1).mutable) { // can use the old simple call :)
            nameStack.get(stackCount - 1).getAsCollection().addAll(nameStack.get(stackCount).getAsCollection()); // simple - note lists won't detect duplicates but I guess they never did
        } else { // need to make a new one preserving type for ordering
            NameSetList previousSet = nameStack.get(stackCount - 1);
            Set<Name> resultAsSet = null;
            List<Name> resultAsList = null;
            Collection<Name> result;
            // instantiate the correct type of collection with the data and point result at it
            result = previousSet.set != null ? (resultAsSet = HashObjSets.newMutableSet(previousSet.set)) : (resultAsList = new ArrayList<>(previousSet.list));
            result.addAll(nameStack.get(stackCount).getAsCollection());
            nameStack.set(stackCount - 1, new NameSetList(resultAsSet, resultAsList, true)); // replace the previous NameSetList
        }
        nameStack.remove(stackCount);
    }

    // "As" assign the results of a query as a certain name
    static void assignSetAsName(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> attributeNames, final List<NameSetList> nameStack, int stackCount) throws Exception {
        Name totalName = nameStack.get(stackCount).getAsCollection().iterator().next();// get(0) relies on list, this works on a collection
                /* ok here's the thing. We don't want this to be under the default display name, new logic jams the user email as the first "language"
                therefore if there's more than one language, we use the first one as the way to define this name.
                The point being that the result of "blah blah blah as 'Period Chosen'" will now mean different 'Period Chosen's for each user
                Need to watch out regarding creating user specific sets : when we get the name see if it's for this user, if so then just change it otherwise make a new one
                */
        if (attributeNames.size() > 1) { // just checking we have have the user added to the list
            String userEmail = attributeNames.get(0);
            if (totalName.getAttribute(userEmail) == null) { // there is no specific set for this user yet, need to do something
                Name userSpecificSet = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance()); // a basic copy of the set
                //userSpecificSet.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, userEmail + totalName.getDefaultDisplayName()); // GOing to set the default display name as bits of the suystem really don't like it not being there
                userSpecificSet.setAttributeWillBePersisted(userEmail, totalName.getDefaultDisplayName()); // set the name (usually default_display_name) but for the "user email" attribute
                totalName.addChildWillBePersisted(userSpecificSet);
                totalName = userSpecificSet; // switch the new one in, it will be used as normal
            }
        }
        totalName.setChildrenWillBePersisted(nameStack.get(stackCount - 1).getAsCollection());
        nameStack.remove(stackCount);
        Set<Name> totalNameSet = HashObjSets.newMutableSet();
        totalNameSet.add(totalName);
        nameStack.set(stackCount - 1, new NameSetList(totalNameSet, null, true));

    }
}