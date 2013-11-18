package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;

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
public final class NameService {


    // hacky but testing for the moment

    public void persist(final LoggedInConnection loggedInConnection) {
        loggedInConnection.getTotoMemoryDB().saveDataToMySQL();
    }

    public Name findByName(final LoggedInConnection loggedInConnection, final String name) {
        return loggedInConnection.getTotoMemoryDB().getNameByName(name);
    }

    public List<Name> searchNames(final LoggedInConnection loggedInConnection, final String search) {
        return loggedInConnection.getTotoMemoryDB().searchNames(search);
    }

    public List<Name> findTopNames(final LoggedInConnection loggedInConnection) {
        return loggedInConnection.getTotoMemoryDB().findTopNames();
    }

    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name) throws Exception {
        final Name existing = loggedInConnection.getTotoMemoryDB().getNameByName(name);
        if (existing != null) {
            return existing;
        } else {
            return new Name(loggedInConnection.getTotoMemoryDB(),name);
        }
    }

    // needs to be a list to preserve order when adding. Or could use a linked set, don't see much advantage

    public List<Name> findChildrenAtLevel(final Name name, final int level) throws Exception {
        // level -1 means get me the lowest
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this
        int currentLevel = 1;
        List<Name> foundAtCurrentLevel = new ArrayList<Name>(name.getChildren());
        while ((currentLevel < level || level == -1) && !foundAtCurrentLevel.isEmpty()) {
            // we can't loop over foundAtCurrentLevel and modify it at the same time, this asks for trouble
            List<Name> nextLevelSet = new ArrayList<Name>();
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
        final ArrayList<Name> toReturn = new ArrayList<Name>();
        // internally we know the children are ordered, so iterate over the set adding those in teh positions we care about
        int position = 1;
        for (Name child : name.getChildren()){
            if ((position >= from || from ==  -1) && (position <= to || to == -1)){
                toReturn.add(child);
            }
            position++;
        }
        return toReturn;
    }

    public List<Name> findChildrenFromTo(final Name name, final String from, final String to) throws Exception {
        final List<Name> toReturn = new ArrayList<Name>();
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

    // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
    // also use an add peer type thing as below? what about blank, throw an exception here?

    public void createPeer(final LoggedInConnection loggedInConnection, final Name parentName, final String peerName) throws Exception {
        final Name peer = findOrCreateName(loggedInConnection, peerName);
        if (!parentName.getPeers().contains(peer)) { // it doesn't already have the peer
            LinkedHashSet<Name> withNewPeer = new LinkedHashSet<Name>(parentName.getPeers());
            withNewPeer.add(peer);
            parentName.setPeersWillBePersisted(withNewPeer);
        }
    }

    public Name addOrCreateChild(final LoggedInConnection loggedInConnection, final Name parentName, final String nameName) throws Exception {
        final Name name = findOrCreateName(loggedInConnection, nameName);
        // check peers here

        parentName.addChildWillBePersisted(name);
        return name;
    }

    // copied from the one below but for peers. Probably scope for some factoring
    public void createPeer(final LoggedInConnection loggedInConnection, final Name parentName, final String peerName, final String afterString, final int after) throws Exception {
        final Name newPeer = findOrCreateName(loggedInConnection, peerName);
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

    public void createPeers(final LoggedInConnection loggedInConnection, final Name parentName, final List<String> peerNames) throws Exception {
        // in this we're going assume that we overwrite existing name links, the single one can be used for adding
        final LinkedHashSet<Name> peers = new LinkedHashSet<Name>(peerNames.size());
        for (String peerName : peerNames) {
            if (peerName.trim().length() > 0) {
                peers.add(findOrCreateName(loggedInConnection, peerName));
            }
        }
        parentName.setPeersWillBePersisted(peers);
    }

    public boolean removePeer(final LoggedInConnection loggedInConnection, final Name parentName, final String peerName) throws Exception {
        Name existingPeer = loggedInConnection.getTotoMemoryDB().getNameByName(peerName);
        if (existingPeer != null) {
            parentName.removeFromPeersWillBePersisted(existingPeer);
            return true;
        }
        return false;
    }

    public Set<Name> getPeersIncludeParents(final Name name) throws Exception {
        if (name.getPeers().size() > 0){
            return name.getPeers();
        }
        final List<Name> parents = name.findAllParents();
        for (Name parent : parents) {
            if (!parent.getPeers().isEmpty()) { // this name is the one that defines what names the data will require
                return parent.getPeers();
            }
        }
        return new HashSet<Name>();
    }

    public void createChild(final LoggedInConnection loggedInConnection, final Name parentName, final String childName, final String afterString, final int after) throws Exception {
        final Name newChild = findOrCreateName(loggedInConnection,childName);
        if (!parentName.getChildren().contains(newChild)) { // it doesn't already have the peer
            final LinkedHashSet<Name> withNewChild = new LinkedHashSet<Name>();
            int position = 1;
            for (Name child : parentName.getChildren()){
                withNewChild.add(child);
                // try for the string and number,whichever is hit first will be the position
                if (after != -1 && after == position){
                    withNewChild.add(newChild);
                }
                if (afterString != null){
                    if (child.getName().equalsIgnoreCase(afterString)){
                        withNewChild.add(newChild);
                    }
                }
                position++;
            }
            parentName.setChildrenWillBePersisted(withNewChild);
        }
    }

    public void createChildren(LoggedInConnection loggedInConnection, final Name parentName, final List<String> childNameStrings) throws Exception {
        // in this we're going assume that we overwrite existing name links, the single one can be used for adding
        final LinkedHashSet<Name> childNames = new LinkedHashSet<Name>(childNameStrings.size());
        for (String childNameString : childNameStrings) {
            if (childNameString.trim().length() > 0) {
                Name child = findOrCreateName(loggedInConnection,childNameString);
                // here we need to check the peer status.
                childNames.add(child);
            }
        }
        parentName.setChildrenWillBePersisted(childNames);
    }

    public boolean removeChild(LoggedInConnection loggedInConnection, final Name parentName, final String childName) throws Exception {
        final Name existingChild = loggedInConnection.getTotoMemoryDB().getNameByName(childName);
        if (existingChild != null) {
            parentName.removeFromChildrenWillBePersisted(existingChild);
            return true;
        }
        return false;
    }

    public void renameName(LoggedInConnection loggedInConnection, String name, String renameAs) throws Exception {
        final Name existing = loggedInConnection.getTotoMemoryDB().getNameByName(name);
        if (existing != null) {
            existing.changeNameWillBePersisted(renameAs);
        }
    }

    // this expects valid names really, used by things like import routines

    public Set<Name> getNameSetForStrings(final LoggedInConnection loggedInConnection, final Set<String> nameStrings) throws Exception{
        Set<Name> toReturn = new HashSet<Name>();
        for (String nameString : nameStrings) {
            Name name = findByName(loggedInConnection, nameString);
            if (name == null) {
                throw new Exception("  I can't find the name : " + nameString);
            } else {
                toReturn.add(name);
            }
        }
        return toReturn;
    }

    // these should probably live somewhere more global
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    public Map<String, String> isAValidNameSet(final LoggedInConnection loggedInConnection, final Set<Name> names, final Set<Name> validNameList) throws Exception {

        //System.out.println("pure java function");
        //long track = System.currentTimeMillis();

        final Map<String, String> toReturn = new HashMap<String, String>();

        String error = "";
        String warning = "";

        final Set<Name> hasPeers = new HashSet<Name>(); // the names (or their parents) in this list which have peer requirements, should only be one
        final Set<Name> namesToCheck = new HashSet<Name>();

            for (Name name : names) {
                    boolean thisNameHasPeers = false;
                    if (!name.getPeers().isEmpty()) { // this name is the one that defines what names the data will require
                        hasPeers.add(name);
                        thisNameHasPeers = true;
                    } else { // try looking up the chain and find the first with peers
                        final List<Name> parents = name.findAllParents();
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



        //System.out.println("track 1-1 : " + (System.currentTimeMillis() - track) + "  ---   ");
        //track = System.currentTimeMillis();

        if (hasPeers.isEmpty()) {
            error += "  none of the names passed have peers, I don't know what names are required for this value";
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
                        final List<Name> allParents = nameToCheck.findAllParents();
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
        //track = System.currentTimeMillis();
        return toReturn;
    }

    public void logNameHierarchy(final Name name, int level) {
        for (int i = 1; i <= level; i++) {
            System.out.print("- ");
        }
        System.out.println(name.getName());
        final Set<Name> children = name.getChildren();
        if (!children.isEmpty()) {
            level++;
            for (Name child : children) {
                logNameHierarchy(child, level);
            }
        }
    }
}
