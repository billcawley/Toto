package com.azquo.toto.service;

import com.azquo.toto.entity.Name;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * I've been convinced that a service layer is probably a good idea, with the DAO/Database it can form the model.
 * <p/>
 * Changes to how the data access will work certainly support this!
 * It will be passed credentials by the controller, that will determine which memory DB to use - it won;t access the DAO any more. For the moment will hard code one memory DB
 */
public class NameService {


    @Autowired
    private TotoMemoryDB totoMemoryDB;

    public Name findByName(final String name) {
        return totoMemoryDB.getNameByName(name);
    }

    public Name findOrCreateName(final String name) throws Exception {
        Name existing = totoMemoryDB.getNameByName(name);
        if (existing != null) {
            return existing;
        } else {
            return new Name(totoMemoryDB,name);
        }
    }

    public Set<Name> findChildrenAtLevel(final Name name, final int level) throws Exception {
        // level -1 means get me the lowest
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this
        int currentLevel = 1;
        Set<Name> foundAtCurrentLevel = name.getChildren();
        while ((currentLevel < level || level == -1) && !foundAtCurrentLevel.isEmpty()) {
            // we can't loop over foundAtCurrentLevel and modify it at the same time, this asks for trouble
            Set<Name> nextLevelSet = new HashSet<Name>();
            for (Name n : foundAtCurrentLevel) {
                nextLevelSet.addAll(n.getChildren());
            }
            if (nextLevelSet.isEmpty() && level == -1) { // wanted the lowest, we've hit a level with none so don't go further
                break;
            }
            foundAtCurrentLevel = nextLevelSet;
            currentLevel++;
        }
        return foundAtCurrentLevel;
    }

    // since we need different from the standard set ordering use a list, I see no real harm in that in these functions

    public List<Name> findChildrenSortedAlphabetically(final Name name) throws Exception {
        List<Name> childList = new ArrayList<Name>(name.getChildren());
        Collections.sort(childList);
        return childList;
    }

    public List<Name> findChildrenFromTo(final Name name, final int from, final int to) throws Exception {
        ArrayList<Name> toReturn = new ArrayList<Name>();
        // internally we know the children are ordered, so iterate over the set adding those in teh positions we care about
        int position = 1;
        for (Name child : name.getChildren()){
            if (position >= from && position <= to){
                toReturn.add(child);
            }
            position++;
        }
        return toReturn;
    }

    public List<Name> findChildrenFromTo(final Name name, final String from, final String to) throws Exception {
        List<Name> toReturn = new ArrayList<Name>();
        boolean okByFrom = false;
        if (from == null) {
            okByFrom = true;
        }
        for (Name child : findChildrenSortedAlphabetically(name)) {
            System.out.println("child `" + child.getName() + "`");
            if (!okByFrom && child.getName().equalsIgnoreCase(from)) {
                okByFrom = true;
            }
            if (okByFrom) {
                toReturn.add(child);
            }
            if (to != null && child.getName().equalsIgnoreCase(to)) { // we just hit the last one
                break;
            }
        }
        return toReturn;
    }

    public void createMembers(final Name parentName, final List<String> childNameStrings) throws Exception {
        // in this we're going assume that we overwrite existing name links, the single one can be used for adding
        LinkedHashSet<Name> childNames = new LinkedHashSet<Name>(childNameStrings.size());
        for (String childNameString : childNameStrings) {
            if (childNameString.trim().length() > 0) {
                Name child = findOrCreateName(childNameString);
                childNames.add(child);
                child.addToParentsWillBePersisted(parentName);
            }
        }
        parentName.setChildrenWillBePersisted(childNames);
    }

    // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!

    public void createPeer(final Name parentName, final String peerName) throws Exception {
        Name peer = findOrCreateName(peerName);

        if (!parentName.getPeers().contains(peer)) { // it doesn't already have the peer
            LinkedHashSet<Name> withNewPeer = new LinkedHashSet<Name>(parentName.getPeers());
            withNewPeer.add(peer);
            parentName.setPeersWillBePersisted(withNewPeer);
        }
    }
    // copied from the one below but for peers. Probably scope for some factoring
    public void createPeer(final Name parentName, final String peerName, final String afterString, final int after) throws Exception {
        Name newPeer = findOrCreateName(peerName);
        if (!parentName.getPeers().contains(newPeer)) { // it doesn't already have the peer
            LinkedHashSet<Name> withNewPeer = new LinkedHashSet<Name>();
            int position = 1;
            for (Name peer : parentName.getPeers()){
                withNewPeer.add(peer);
                if (afterString != null){
                    if (peer.getName().equalsIgnoreCase(afterString)){
                        withNewPeer.add(newPeer);
                        // no backward link like with normal children
                    }
                } else if(after == position){ // only check the numeric position if there's no string passed
                    withNewPeer.add(newPeer);
                }
                position++;
            }
            parentName.setPeersWillBePersisted(withNewPeer);
        }
    }

    public void createPeers(final Name parentName, final List<String> peerNames) throws Exception {
        // in this we're going assume that we overwrite existing name links, the single one can be used for adding
        LinkedHashSet<Name> peers = new LinkedHashSet<Name>(peerNames.size());
        for (String peerName : peerNames) {
            if (peerName.trim().length() > 0) {
                peers.add(findOrCreateName(peerName));
            }
        }
        parentName.setPeersWillBePersisted(peers);
    }

    public void removePeer(final Name parentName, final String peerName) throws Exception {
        Name existingPeer = totoMemoryDB.getNameByName(peerName);
        if (existingPeer != null) {
            parentName.removeFromPeersWillBePersisted(existingPeer);
        }
    }


    public void createMember(final Name parentName, final String childName, final String afterString, final int after) throws Exception {
        Name newChild = findOrCreateName(childName);
        if (!parentName.getChildren().contains(newChild)) { // it doesn't already have the peer
            LinkedHashSet<Name> withNewChild = new LinkedHashSet<Name>();
            int position = 1;
            for (Name child : parentName.getChildren()){
                withNewChild.add(child);
                if (afterString != null){
                    if (child.getName().equalsIgnoreCase(afterString)){
                        withNewChild.add(newChild);
                        newChild.addToParentsWillBePersisted(parentName); // link the other way too!
                    }
                } else if(after == position){ // only check the numeric position if there's no string passed
                    withNewChild.add(newChild);
                }
                position++;
            }
            parentName.setPeersWillBePersisted(withNewChild);
        }
    }

    public void removeMember(final Name parentName, final String childName) throws Exception {
        Name existingChild = totoMemoryDB.getNameByName(childName);
        if (existingChild != null) {
            parentName.removeFromChildrenWillBePersisted(existingChild);
            existingChild.removeFromParentsWillBePersisted(parentName); // and unlink the other way
        }
    }

    public void renameName(String name, String renameAs) throws Exception {
        Name existing = totoMemoryDB.getNameByName(name);
        if (existing != null) {
            existing.changeNameWillBePersisted(renameAs, totoMemoryDB);
        }
    }

    // returns a set as I don't think we care about duplicates here

    public List<Name> findAllParents(final Name name) throws DataAccessException {
        final List<Name> allParents = new ArrayList<Name>();
        Set<Name> foundAtCurrentLevel = name.getParents();
        while (!foundAtCurrentLevel.isEmpty()) {
            allParents.addAll(foundAtCurrentLevel);
            Set<Name> nextLevelSet = new HashSet<Name>();
            for (Name n : foundAtCurrentLevel) {
                nextLevelSet.addAll(n.getParents());
            }
            if (nextLevelSet.isEmpty()) { // no more parents to find
                break;
            }
            foundAtCurrentLevel = nextLevelSet;
        }
        return allParents;
    }

    // these should probably live somewhere more global
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    public Map<String, String> isAValidNameSet(Set<String> names, Set<Name> validNameList) throws Exception {

        //System.out.println("pure java function");
        long track = System.currentTimeMillis();

        Map<String, String> toReturn = new HashMap<String, String>();

        String error = "";
        String warning = "";

        Set<Name> hasPeers = new HashSet<Name>(); // the names (or their parents) in this list which have peer requirements, should only be one
        Set<Name> namesToCheck = new HashSet<Name>();

        for (String nameString : names) {
            Name name = findByName(nameString);
            if (name == null) {
                error += "  I can't find the name : " + nameString;
            } else { // the name exists . . .
                boolean thisNameHasPeers = false;
                if (!name.getPeers().isEmpty()) { // this name is the one that defines what names the data will require
                    hasPeers.add(name);
                    thisNameHasPeers = true;
                } else { // try looking up the chain and find the first with peers
                    List<Name> parents = findAllParents(name);
                    for (Name parent : parents) {
                        if (!parent.getPeers().isEmpty()) { // this name is the one that defines what names the data will require
                            hasPeers.add(parent); // put the parent not the actual name in as it will be used to determine the criteria for this value
                            thisNameHasPeers = true;
                            break;
                        }
                    }
                }
                // it wasn't a name with peers hence it's on the list of names to match up to the peer list of the name that DOES have peers :)
                if (!thisNameHasPeers) {
                    namesToCheck.add(name);
                } else {
                    // not adding the name with peers to namesToCheck is more efficient and it stops the name with peers from showing up as being superfluous to the peer list if that makes sense
                    validNameList.add(name); // the rest will be added below but we need to add this here as the peer defining name is not on the list of peers
                }
            }
        }


        //System.out.println("track 1-1 : " + (System.currentTimeMillis() - track) + "  ---   ");
        track = System.currentTimeMillis();

        if (hasPeers.isEmpty()) {
            error += "  none of the namess passed have peers, I don't know what namess are required for this value";
        } else if (hasPeers.size() > 1) {
            error += "  more than one name passed has peers ";
            for (Name has : hasPeers) {
                error += has.getName() + ", ";
            }
            error += "I don't know what names are required for this value";
        } else { // one set of peers, ok :)
            // match peers child names are ok, ignore extra namess, warn about this
            // think that is a bit ofo dirty way of getting the single item in the set . . .just assign it?
            for (Name requiredPeer : hasPeers.iterator().next().getPeers()) {
                boolean found = false;
                // do a first direct pass, see old logic below, I think(!) this will work and be faster. Need to think about that equals on name, much cost of tolowercase?
                if (namesToCheck.remove(requiredPeer)){// skip to the next one and remove the name from names to check and add it to the validated list to return
                    validNameList.add(requiredPeer);
                    found = true;
                }

                if (!found) { // couldn't find this peer, need to look up through parents of each name for the peer
                    // again new logic here
                    for (Name nameToCheck : namesToCheck) {
                        List<Name> allParents = findAllParents(nameToCheck);
                        // again trying for more efficient logic
                        if (allParents.contains(requiredPeer)){
                            namesToCheck.remove(nameToCheck); // skip to the next one and remove the name from names to check and add it to the validated list to return
                            validNameList.add(nameToCheck);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    error += "  I can't find a required peer : " + requiredPeer.getName() + " among the names";
                }
            }

            if (namesToCheck.size() > 0) { // means they were not used by the required peers, issue a warning
                for (Name nameToCheck : namesToCheck) {
                    warning += "  additional name not required by peers " + nameToCheck.getName();
                }
            }
        }

        if (error.length() > 0) {
            toReturn.put(ERROR, error);
        }
        if (warning.length() > 0) {
            toReturn.put(WARNING, error);
        }
        //System.out.println("track 1-2 : " + (System.currentTimeMillis() - track) + "  ---   ");
        track = System.currentTimeMillis();
        return toReturn;
    }

    public void logNameHierarchy(Name name, int level) {
        for (int i = 1; i <= level; i++) {
            System.out.print("- ");
        }
        System.out.println(name.getName());
        Set<Name> children = name.getChildren();
        if (!children.isEmpty()) {
            level++;
            for (Name child : children) {
                logNameHierarchy(child, level);
            }
        }
    }
}
