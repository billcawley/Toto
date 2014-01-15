package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * I've been convinced that a service layer is probably a good idea, with the DAO/Database it can form the model.
 * <p/>
 * Changes to how the data access will work certainly support this!
 * It will be passed credentials by the controller, that will determine which memory DB to use - it won;t access the DAO any more.
 * <p/>
 * <p/>
 * It now does quite a lot of string parsing . . .should this be done by the controller? Not sure. At least validated there.
 */
public final class NameService {
    public static final String LEVEL = "level";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String LOWEST = "lowest";
    public static final String NAMEMARKER = "!";
    public static final String PEERS = "peers";
    public static final String STRUCTURE = "structure";
    public static final String CREATE = "create";
    public static final String REMOVE = "remove";
    public static final String SEARCH = "search";
    public static final String AFTER = "after";
    public static final String RENAMEAS = "rename as";


    // hacky but testing for the moment

    public void persist(final LoggedInConnection loggedInConnection) {
        loggedInConnection.getTotoMemoryDB().saveDataToMySQL();
    }

    // wonder if this can be done more clearly?

    private String findParentFromList(final String name) {
        if (name.lastIndexOf(",") < 0) return null;
        int nStartPos = name.indexOf("\"");
        int commaPos = name.lastIndexOf(",");
        if (nStartPos > 0 && nStartPos < commaPos) {
            int nEndPos = name.indexOf("\"", nStartPos);
            if (nEndPos < 0) return null;
            return findParentFromList(name.substring(nEndPos + 1));
        }
        return name.substring(commaPos + 1).trim();
    }


    public ArrayList<Name> sortNames(final ArrayList<Name> namesList) {
        /*Comparator<Name> compareName = new Comparator<Name>() {
            public int compare(Name n1, Name n2) {
                return n1.getName().compareTo(n2.getName());
            }
        };*/

        Collections.sort(namesList);
        return namesList;

    }

    // untested!

    public ArrayList<Name> sortNames(final ArrayList<Name> namesList, final String language) {
        Comparator<Name> compareName = new Comparator<Name>() {
            public int compare(Name n1, Name n2) {
                return n1.getAttribute(language).compareTo(n1.getAttribute(language));
            }
        };

        Collections.sort(namesList, compareName);
        return namesList;

    }

    // get names from a comma separated list

    public Set<Name> decodeString(LoggedInConnection loggedInConnection, String searchByNames) {
        if (searchByNames.startsWith("\"")) {
            searchByNames = searchByNames.substring(1);
        }
        if (searchByNames.endsWith("\"")) {
            searchByNames = searchByNames.substring(0, searchByNames.length() - 1);
        }
        StringTokenizer st = new StringTokenizer(searchByNames, "\",\"");
        Set<Name> names = new HashSet<Name>();
        while (st.hasMoreTokens()) {
            String nameName = st.nextToken().trim();
            Name name = findByName(loggedInConnection, nameName);
            if (name != null) {
                names.add(name);
            }
        }
        return names;

    }

    public ArrayList<Name> findContainingName(final LoggedInConnection loggedInConnection, final String name) {
        // go for the default for the moment
        return sortNames(new ArrayList<Name>(loggedInConnection.getTotoMemoryDB().getNamesWithAttributeContaining(Name.DEFAULT_DISPLAY_NAME, name)));
    }

    public Name findById(final LoggedInConnection loggedInConnection, int id) {
        return loggedInConnection.getTotoMemoryDB().getNameById(id);
    }


    public Name findByName(final LoggedInConnection loggedInConnection, final String name) {

     /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.
        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.
        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London
        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'
        */

        String parentName = findParentFromList(name);
        if (parentName == null) {
            // just a simple name passed, no structure
            return loggedInConnection.getTotoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, name, null);
        }
        Name parent = loggedInConnection.getTotoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, parentName, null);
        if (parent == null) { // parent was null, since we're just trying to find that stops us right here
            return null;
        }
        String remainder = name.substring(0, name.lastIndexOf(",", name.length() - parentName.length()));
        // remainder is the rest of the string, could be Ontario, Place
        parentName = findParentFromList(remainder);
        // keep chopping away at the string until we find the closest parent we can
        // TODO : what if parent is null? Would the search then happen against a nul parent?
        // while (parentName != null && parent != null){ // maybe that instead??
        while (parentName != null) {
            parent = loggedInConnection.getTotoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, parentName, null);
            remainder = name.substring(0, remainder.lastIndexOf(",", name.length() - parentName.length()));
            parentName = findParentFromList(remainder);
        }
        return loggedInConnection.getTotoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, remainder, parent);
    }

    public List<Name> searchNames(final LoggedInConnection loggedInConnection, final String search) {
        return loggedInConnection.getTotoMemoryDB().searchNames(Name.DEFAULT_DISPLAY_NAME, search);
    }

    public List<Name> findTopNames(final LoggedInConnection loggedInConnection) {
        return loggedInConnection.getTotoMemoryDB().findTopNames();
    }

    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name) throws Exception {
        if (name.toLowerCase().endsWith(";unique")) {
            return findOrCreateName(loggedInConnection, name.substring(0, name.length() - 7), true);
        }
        return findOrCreateName(loggedInConnection, name, false);
    }

    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name, boolean unique) throws Exception {

        /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.

        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.

        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London

        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

         */

        // as I (Edd) understand this will be the top parent
        String parentName = findParentFromList(name);

        if (parentName == null) {
            return findOrCreateName(loggedInConnection, name, null, null);
        }
        Name parent = findOrCreateName(loggedInConnection, parentName, null, null);
        Name topParent = parent;
        String remainder = name.substring(0, name.lastIndexOf(",", name.length() - parentName.length()));
        parentName = findParentFromList(remainder);

        // ok teh key here is to step through the parent -> child list as defined in the name string creating teh hierachy as you go along
        // the top parent is the context in which names should be searched for and created if not existing, the parent name and parent is the direct parent we may have just created
        // so what unique is saying is : ok we have the parent we want to add a name to : the question is do we search under that parent to find or create or under the top parent?
        // I thikn maybe the names of varables could be clearer here!, maybe look into on second pass
        while (parentName != null) {
            if (!unique) {
                topParent = parent;
            }
            parent = findOrCreateName(loggedInConnection, parentName, topParent, parent);
            remainder = name.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length()));
            parentName = findParentFromList(remainder);
        }
        if (!unique) {
            topParent = parent;
        }
        return findOrCreateName(loggedInConnection, remainder, topParent, parent);

    }


    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name, final Name parent, final Name newparent) throws Exception {

     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an import,and insert a structure into it
        the 'parent' will usually be the top of the tree, and the new parent will be a name created as a branch.  */

        String storeName = name.replace("\"", "");
        final Name existing = loggedInConnection.getTotoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, storeName, parent);
        if (existing != null) {
            // I think this is in the case of unique = true, the name to be created is in fact being moved down the hierachy
            if (newparent != null && newparent != parent) {
                parent.removeFromChildrenWillBePersisted(existing);
                newparent.addChildWillBePersisted(existing);
            }
            return existing;
        } else {
            // actually creating a new one
            Provenance provenance = loggedInConnection.getProvenance();
            Name newName = new Name(loggedInConnection.getTotoMemoryDB(), provenance, true); // default additive to true
            newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, storeName);
            // TODO the remove here makes no sense, names are equal by ID, it will never match. Check with dad . . .
            if (newparent != null) {
                if (newparent != parent) {
                    parent.removeFromChildrenWillBePersisted(newName);
                }
                newparent.addChildWillBePersisted(newName);
            }
            return newName;
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
    // note : in default language!

    public List<Name> findChildrenSortedAlphabetically(final Name name) throws Exception {
        List<Name> childList = new ArrayList<Name>(name.getChildren());
        Collections.sort(childList);
        return childList;
    }

    public List<Name> findChildrenFromTo(final Name name, final int from, final int to) throws Exception {
        final ArrayList<Name> toReturn = new ArrayList<Name>();
        // internally we know the children are ordered, so iterate over the set adding those in teh positions we care about
        int position = 1;
        for (Name child : name.getChildren()) {
            if ((position >= from || from == -1) && (position <= to || to == -1)) {
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
            System.out.println("child \"" + child.getDefaultDisplayName() + "\"");
            if (!okByFrom && child.getDefaultDisplayName().equalsIgnoreCase(from)) {
                okByFrom = true;
            }
            if (okByFrom) {
                toReturn.add(child);
            }
            if (to != null && child.getDefaultDisplayName().equalsIgnoreCase(to)) { // we just hit the last one
                break;
            }
        }
        return toReturn;
    }


    // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
    // also use an add peer type thing as below? what about blank, throw an exception here?

    public void createPeer(final LoggedInConnection loggedInConnection, final Name parentName, final String peerName) throws Exception {
        final Name peer = findOrCreateName(loggedInConnection, peerName);
        if (!parentName.getPeers().keySet().contains(peer)) { // it doesn't already have the peer
            LinkedHashMap<Name, Boolean> withNewPeer = new LinkedHashMap<Name, Boolean>(parentName.getPeers());
            withNewPeer.put(peer, true); // default to additive for the mo
            parentName.setPeersWillBePersisted(withNewPeer);
        }
    }

    public Name addOrCreateChild(final LoggedInConnection loggedInConnection, final Name parentName, final String nameName) throws Exception {
        final Name name = findOrCreateName(loggedInConnection, nameName);
        // TODO : check peers here?
        parentName.addChildWillBePersisted(name);
        return name;
    }

    // copied from the one below but for peers. Probably scope for some factoring
    public void createPeer(final LoggedInConnection loggedInConnection, final Name parentName, final String peerName, final String afterString, final int after) throws Exception {
        final Name newPeer = findOrCreateName(loggedInConnection, peerName);
        if (!parentName.getPeers().keySet().contains(newPeer)) { // it doesn't already have the peer
            LinkedHashMap<Name, Boolean> withNewPeer = new LinkedHashMap<Name, Boolean>();
            int position = 1;
            for (Name peer : parentName.getPeers().keySet()) {
                withNewPeer.put(peer, true); // additive by default
                if (afterString != null) {
                    if (peer.getDefaultDisplayName().equalsIgnoreCase(afterString)) {
                        withNewPeer.put(newPeer, true);// additive by default
                        // no backward link like with normal children
                    }
                } else if (after == position) { // only check the numeric position if there's no string passed
                    withNewPeer.put(newPeer, true); // additive by default
                }
                position++;
            }
            parentName.setPeersWillBePersisted(withNewPeer);
        }
    }

    public void createPeers(final LoggedInConnection loggedInConnection, final Name parentName, final List<String> peerNames) throws Exception {
        // in this we're going assume that we overwrite existing name links, the single one can be used for adding
        final LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>(peerNames.size());
        for (String peerName : peerNames) {
            if (peerName.trim().length() > 0) {
                peers.put(findOrCreateName(loggedInConnection, peerName), true); // additive by default for the mo
            }
        }
        parentName.setPeersWillBePersisted(peers);
    }

    public boolean removePeer(final LoggedInConnection loggedInConnection, final Name parentName, final String peerName) throws Exception {
        Name existingPeer = findByName(loggedInConnection, peerName);
        if (existingPeer != null) {
            parentName.removeFromPeersWillBePersisted(existingPeer);
            return true;
        }
        return false;
    }

    public Map<Name, Boolean> getPeersIncludeParents(final Name name) throws Exception {
        if (name.getPeers().size() > 0) {
            return name.getPeers();
        }
        final List<Name> parents = name.findAllParents();
        for (Name parent : parents) {
            if (!parent.getPeers().isEmpty()) { // this name is the one that defines what names the data will require
                return parent.getPeers();
            }
        }
        return new LinkedHashMap<Name, Boolean>();
    }


    public void createChild(final LoggedInConnection loggedInConnection, final Name parentName, final String childName, final String afterString, final int after) throws Exception {
        final Name newChild = findOrCreateName(loggedInConnection, childName);
        if (!parentName.getChildren().contains(newChild)) { // it doesn't already have the child
            final LinkedHashSet<Name> withNewChild = new LinkedHashSet<Name>();
            int position = 1;
            for (Name child : parentName.getChildren()) {
                withNewChild.add(child);
                // try for the string and number,whichever is hit first will be the position
                if (after != -1 && after == position) {
                    withNewChild.add(newChild);
                }
                if (afterString != null) {
                    if (child.getDefaultDisplayName().equalsIgnoreCase(afterString)) {
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
                Name child = findOrCreateName(loggedInConnection, childNameString);
                // here we need to check the peer status.
                childNames.add(child);
            }
        }
        System.out.println("names to add : " + childNames);
        parentName.setChildrenWillBePersisted(childNames);
    }

    public boolean removeChild(LoggedInConnection loggedInConnection, final Name parentName, final String childName) throws Exception {
        final Name existingChild = findByName(loggedInConnection, childName);
        if (existingChild != null) {
            parentName.removeFromChildrenWillBePersisted(existingChild);
            return true;
        }
        return false;
    }

    public void renameName(LoggedInConnection loggedInConnection, String name, String renameAs) throws Exception {
        final Name existing = findByName(loggedInConnection, name);
        if (existing != null) {
            // internally this does check duplicate rules
            existing.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, renameAs);
        }
    }

    // this expects valid names really, used by things like import routines

    public Set<Name> getNameSetForStrings(final LoggedInConnection loggedInConnection, final Set<String> nameStrings) throws Exception {
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

    public Map<String, String> isAValidNameSet(final Set<Name> names, final Set<Name> validNameList) throws Exception {

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
                error += has.getDefaultDisplayName() + ", ";
            }
            error += "I don't know what names are required for this value";
        } else { // one set of peers, ok :)
            // match peers child names are ok, ignore extra names, warn about this
            // think that is a bit ofo dirty way of getting the single item in the set . . .just assign it?
            for (Name requiredPeer : hasPeers.iterator().next().getPeers().keySet()) {
                boolean found = false;
                // do a first direct pass, see old logic below, I think(!) this will work and be faster. Need to think about that equals on name, much cost of tolowercase?
                if (namesToCheck.remove(requiredPeer)) {// skip to the next one and remove the name from names to check and add it to the validated list to return
                    validNameList.add(requiredPeer);
                    found = true;
                }

                if (!found) { // couldn't find this peer, need to look up through parents of each name for the peer
                    // again new logic here
                    for (Name nameToCheck : namesToCheck) {
                        final List<Name> allParents = nameToCheck.findAllParents();
                        // again trying for more efficient logic
                        if (allParents.contains(requiredPeer)) {
                            namesToCheck.remove(nameToCheck); // skip to the next one and remove the name from names to check and add it to the validated list to return
                            validNameList.add(nameToCheck);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    error += "  I can't find a required peer : " + requiredPeer.getDefaultDisplayName() + " among the names";
                }
            }

            if (namesToCheck.size() > 0) { // means they were not used by the required peers, issue a warning
                for (Name nameToCheck : namesToCheck) {
                    warning += "  additional name not required by peers " + nameToCheck.getDefaultDisplayName();
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
        System.out.println(name.getDefaultDisplayName());
        final Set<Name> children = name.getChildren();
        if (!children.isEmpty()) {
            level++;
            for (Name child : children) {
                logNameHierarchy(child, level);
            }
        }
    }

    // used to be in the controller, should it be back there???

    public String getInstruction(final String instructions, final String instructionName) {
        String toReturn = null;
        if (instructions.toLowerCase().contains(instructionName.toLowerCase())) {
            int commandStart = instructions.toLowerCase().indexOf(instructionName.toLowerCase()) + instructionName.length();
            if (instructions.indexOf(";", commandStart) != -1) {
                toReturn = instructions.substring(commandStart, instructions.indexOf(";", commandStart)).trim();
            } else {
                toReturn = instructions.substring(commandStart).trim();
            }
            if (toReturn.startsWith("`")) {
                toReturn = toReturn.substring(1, toReturn.length() - 1); // trim escape chars
            }
        }
        return toReturn;
    }

    // to find a set of names

    public List<Name> interpretName(final LoggedInConnection loggedInConnection, final String instructions) throws Exception {

        final String levelString = getInstruction(instructions, LEVEL);
        final String fromString = getInstruction(instructions, FROM);
        final String childrenString = getInstruction(instructions, CHILDREN);
        final String toString = getInstruction(instructions, TO);

        List<Name> names = new ArrayList<Name>();
        String nameString = instructions;
        if (instructions.indexOf(';') > 0) {
            nameString = instructions.substring(0, instructions.indexOf(';')).trim();
        }
        final Name name = findByName(loggedInConnection, nameString);
        if (name == null) {
            return null;
        }

        if (childrenString == null) {
            names.add(name);
            // what was this for? Not used . . .
            //String reversePolish = shuntingYardAlgorithm(loggedInConnection, name);
            return names;
        }

        int level = 1;
        if (levelString != null) {
            if (levelString.equalsIgnoreCase(LOWEST)) {
                System.out.println("lowest");
                level = -1;
            } else {
                try {
                    level = Integer.parseInt(levelString);
                } catch (NumberFormatException nfe) {
                    //carry on regardless!
                }
            }
        }

        int from = -1;
        int to = -1;
        if (fromString != null) {
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
            }

        }
        if (toString != null) {
            try {
                to = Integer.parseInt(toString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
            }

        }
        if (from != -1 || to != -1) { // numeric, I won't allow mixed for the moment
            names = findChildrenFromTo(name, from, to);
        } else if (fromString != null || toString != null) {
            names = findChildrenFromTo(name, fromString, toString);
        } else { // also won't allow from/to/level mixes either
            // sorted means level won't work
            if (getInstruction(instructions, SORTED) != null) {
                names = findChildrenSortedAlphabetically(name);
            } else {
                names = findChildrenAtLevel(name, level);
            }
        }
        for (Name name2 : names) {
            shuntingYardAlgorithm(loggedInConnection, name2);
        }
        return names;
    }

    // Edd guessing this to find either a number or a name in a formula

    private String interpretTerm(final LoggedInConnection loggedInConnection, final Name name, final String term) {


        if (term.startsWith(NAMEMARKER)) return term + " ";
        Name nameFound = findByName(loggedInConnection, term);
        try {
            double d = Double.parseDouble(term);
            return d + " ";
        } catch (Exception e) {

        }

        if (nameFound == null) {
            return "error: formula for " + name.getDefaultDisplayName() + " not understood: " + term;
        }
        return (NAMEMARKER + nameFound.getId() + " ");

    }


    private String shuntingYardAlgorithm(LoggedInConnection loggedInConnection, Name name) throws Exception {
/*   TODO SORT OUT ACTION ON ERROR
        Routine to convert a formula (if it exists) to reverse polish.

        Read a token.
                If the token is a number, then add it to the output queue.
        If the token is a function token, then push it onto the stack.
                If the token is a function argument separator (e.g., a comma):
        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue. If no left parentheses are encountered, either the separator was misplaced or parentheses were mismatched.
        If the token is an operator, o1, then:
        while there is an operator token, o2, at the top of the stack, and
        either o1 is left-associative and its precedence is equal to that of o2,
                or o1 has precedence less than that of o2,
        pop o2 off the stack, onto the output queue;
        push o1 onto the stack.
                If the token is a left parenthesis, then push it onto the stack.
                If the token is a right parenthesis:
        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
        Pop the left parenthesis from the stack, but not onto the output queue.
                If the token at the top of the stack is a function token, pop it onto the output queue.
                If the stack runs out without finding a left parenthesis, then there are mismatched parentheses.
        When there are no more tokens to read:
        While there are still operator tokens in the stack:
        If the operator token on the top of the stack is a parenthesis, then there are mismatched parentheses.
        Pop the operator onto the output queue.
                Exit.
*/
        String calc = name.getAttribute("CALCULATION");
        if (calc == null || calc.length() == 0) return "";

        Pattern p = Pattern.compile("\"[^\"]+\"");//sort out variables in quotes first

        Matcher m = p.matcher(calc);
        while (m.find()) {
            String nameString = m.group();
            String result = interpretTerm(loggedInConnection, name, nameString.substring(1, nameString.length() - 1));
            if (result.startsWith("error:")) {
                return result;
            }
            calc = calc.replace(nameString, result);
        }
        p = Pattern.compile("[\\+-/\\*\\(\\)]"); // only simple maths allowed at present
        StringBuffer sb = new StringBuffer();
        String stack = "";
        m = p.matcher(calc);
        String origCalc = calc;
        int startPos = 0;
        while (m.find()) {
            String opfound = m.group();
            char thisOp = opfound.charAt(0);
            int pos = m.start();
            String namefound = calc.substring(startPos, pos).trim();
            if (namefound.length() > 0) {
                String result = interpretTerm(loggedInConnection, name, namefound);
                if (result.startsWith("error:")) {
                    return result;
                }
                sb.append(result);
            }
            char lastOffStack = ' ';
            while (!(thisOp == ')' && lastOffStack == '(') && (stack.length() > 0 && ")+-*/(".indexOf(thisOp) <= "(+-*/".indexOf(stack.charAt(0)))) {

                if (stack.charAt(0) != '(') {
                    sb.append(stack.charAt(0) + " ");
                }
                lastOffStack = stack.charAt(0);
                stack = stack.substring(1);
            }
            if ((thisOp == ')' && lastOffStack != '(') || (thisOp != ')' && lastOffStack == '(')) {
                return "error mismatched brackets in " + origCalc;
            }
            if (thisOp != ')') {
                stack = thisOp + stack;
            }
            startPos = m.end();

        }

        if (calc.substring(startPos).trim().length() > 0) {
            String result = interpretTerm(loggedInConnection, name, calc.substring(startPos).trim());
            if (result.startsWith("error:")) {
                return result;
            }
            sb.append(result);
        }
        while (stack.length() > 0) {
            sb.append(stack.charAt(0) + " ");
            stack = stack.substring(1);
        }

        if (name.getAttribute("RPCALC") == null || name.getAttribute("RPCALC") != sb.toString()) {
            name.setAttributeWillBePersisted("RPCALC", sb.toString());
        }

        return "";
    }

    // ok this was moved in from teh controller. Probably need to factor some of it out later.
    // much of the stuff in here was to implement the very first set of functions required by the software.
    // now returns Json, probably should convert to Gson

    public String handleRequest(LoggedInConnection loggedInConnection, String instructions)
            throws Exception {
        try {
            String nameString = instructions;
            if (instructions == null) {
                return "error:no instructions passed";
            }

            System.out.println("instructions : |" + instructions + "|");
            if (instructions.equals("lognames")) {
                for (Name topName : findTopNames(loggedInConnection)) {
                    logNameHierarchy(topName, 0);
                }
            }

            // this certainly will NOT stay here :)
            if (instructions.equals("persist")) {
                persist(loggedInConnection);
            }
            instructions = instructions.trim();
            // typically a command will start with a name

            String search = getInstruction(instructions, SEARCH);
            if (instructions.indexOf(';') > 0) {
                nameString = instructions.substring(0, instructions.indexOf(';')).trim();
                // now we have it strip off the name, use getInstruction to see what we want to do with the name
                String origInstructions = instructions;
                instructions = instructions.substring(instructions.indexOf(';') + 1).trim();

                String children = getInstruction(instructions, CHILDREN);
                String peers = getInstruction(instructions, PEERS);
                String structure = getInstruction(instructions, STRUCTURE);
                String create = getInstruction(instructions, CREATE);
                String afterString = getInstruction(instructions, AFTER);
                String remove = getInstruction(instructions, REMOVE);
                String renameas = getInstruction(instructions, RENAMEAS);
                // since children can be part of structure definition we do structure first
                if (structure != null) {
                    // New logic.  If name is not found, then first find names containing the name sent.  If none are found, return top names in structure
                    final Name name = findByName(loggedInConnection, nameString);
                    if (name != null) {
                        //return getParentStructureFormattedForOutput(name, true) + getChildStructureFormattedForOutput(name, false, json);
                        return "{\"names\":[" + getChildStructureFormattedForOutput(name) + "]}";
                    } else {
                        ArrayList<Name> names = findContainingName(loggedInConnection, nameString);
                        if (names.size() == 0) {
                            names = (ArrayList<Name>) findTopNames(loggedInConnection);
                            names = sortNames(names);
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\"names\":[");
                        int count = 0;
                        for (Name outputName : names) {
                            String nameJson = getChildStructureFormattedForOutput(outputName);
                            if (nameJson.length() > 0) {
                                if (count > 0) sb.append(",");
                                sb.append(nameJson);
                                count++;
                            }
                        }
                        sb.append("]}");
                        return sb.toString();


                    }

                } else if (children != null) {

                    if (children.length() > 0) { // we want to affect the structure, add, remove, create
                        if (remove != null) { // remove a child form the set
                            final Name name = findByName(loggedInConnection, nameString);
                            if (name != null) {
                                if (removeChild(loggedInConnection, name, children)) {
                                    return children + " removed";
                                } else {
                                    return "error:name not found:`" + children + "`";
                                }
                            } else {
                                return "error:name not found:`" + nameString + "`";
                            }
                        } else { // some kind of create or set add
                            Name name;
                            String notFoundError = "";
                            if (create != null) {
                                name = findOrCreateName(loggedInConnection, nameString);
                            } else {
                                name = findByName(loggedInConnection, nameString);
                                if (name == null) {
                                    notFoundError = "error:name not found:`" + nameString + "`";
                                }
                            }
                            // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                            if (children.startsWith("{")) { // array, typically when creating in the first place, the service call will insert after any existing
                                // EXAMPLE : 2013;children {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                                if (children.contains("}")) {
                                    children = children.substring(1, children.indexOf("}"));
                                    final StringTokenizer st = new StringTokenizer(children, ",");
                                    final List<String> namesToAdd = new ArrayList<String>();
                                    while (st.hasMoreTokens()) {
                                        String childName = st.nextToken().trim();
                                        if (childName.startsWith("`")) {
                                            childName = childName.substring(1, childName.length() - 1); // trim escape chars
                                        }
                                        if (create == null && findByName(loggedInConnection, childName) == null) {
                                            if (notFoundError.isEmpty()) {
                                                notFoundError = childName;
                                            } else {
                                                notFoundError += (", `" + childName + "`");
                                            }
                                        }
                                        namesToAdd.add(childName);
                                    }
                                    if (notFoundError.isEmpty()) {
                                        createChildren(loggedInConnection, name, namesToAdd);
                                        System.out.println("created children : " + name + " " + namesToAdd);
                                    } else {
                                        return "error:name not found:`" + notFoundError + "`";
                                    }
                                    return "array saved " + namesToAdd.size() + " names";
                                } else {
                                    return "error:Unclosed }";
                                }
                            } else { // insert after a certain position
                                // currently won't support before and after on create arrays, probably could later
                                int after = -1;
                                try {
                                    after = Integer.parseInt(afterString);
                                } catch (NumberFormatException ignored) {
                                }
                                if (create == null && findByName(loggedInConnection, children) == null) {
                                    return "error:name not found:`" + children + "`";
                                }
                                createChild(loggedInConnection, name, children, afterString, after);
                                assert name != null; // just to shut intellij up
                                return children + " added to " + name.getDefaultDisplayName();
                            }

                        }

                    } else {// they want to read data

                        List<Name> names = interpretName(loggedInConnection, origInstructions);
                        if (names != null) {
                            return getNamesFormattedForOutput(names);
                        } else {
                            return "error:name not found:`" + nameString + "`";
                        }
                    }
                } else if (peers != null) {
                    System.out.println("peers : |" + peers + "|");
                    if (peers.length() > 0) { // we want to affect the structure, add, remove, create
                        if (remove != null) { // remove a peer form the set
                            final Name name = findByName(loggedInConnection, nameString);
                            if (name != null) {
                                if (removePeer(loggedInConnection, name, peers)) {
                                    return peers + " removed";
                                } else {
                                    return "error:name not found:`" + peers + "`";
                                }
                            }
                        } else { // copied from above but for peers, probably should factor at some point
                            Name name;
                            if (create != null) {
                                name = findOrCreateName(loggedInConnection, nameString);
                            } else {
                                name = findByName(loggedInConnection, nameString);
                                if (name == null) {
                                    return "error:name not found:`" + nameString + "`";
                                }
                            }
                            // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                            if (peers.startsWith("{")) { // array, typically when creating in the first place, the service call will insert after any existing
                                if (peers.contains("}")) {
                                    peers = peers.substring(1, peers.indexOf("}"));
                                    final StringTokenizer st = new StringTokenizer(peers, ",");
                                    final List<String> peersToAdd = new ArrayList<String>();
                                    String notFoundError = "";
                                    while (st.hasMoreTokens()) {
                                        String peerName = st.nextToken().trim();
                                        if (peerName.startsWith("`")) {
                                            peerName = peerName.substring(1, peerName.length() - 1); // trim escape chars
                                        }
                                        if (create == null && findByName(loggedInConnection, peerName) == null) {
                                            if (notFoundError.isEmpty()) {
                                                notFoundError = peerName;
                                            } else {
                                                notFoundError += (",`" + peerName + "`");
                                            }
                                        }
                                        peersToAdd.add(peerName);
                                    }
                                    if (notFoundError.isEmpty()) {
                                        createPeers(loggedInConnection, name, peersToAdd);
                                    } else {
                                        return "error:name not found:`" + notFoundError + "`";
                                    }
                                    return "array saved " + peersToAdd.size() + " names";
                                } else {
                                    return "error:Unclosed }";
                                }
                            } else { // insert after a certain position
                                // currently won't support before and after on create arrays, probably could later
                                int after = -1;
                                try {
                                    after = Integer.parseInt(afterString);
                                } catch (NumberFormatException ignored) {
                                }
                                if (create == null && findByName(loggedInConnection, peers) == null) {
                                    return "error:name not found:`" + nameString + "`";
                                }
                                createPeer(loggedInConnection, name, peers, afterString, after);
                                return peers + " added to " + name.getDefaultDisplayName();
                            }

                        }

                    } else {// they want to read data
                        final Name name = findByName(loggedInConnection, nameString);
                        if (name != null) {
                            //  Fees; peers {Period, Analysis, Merchant};create;
                            // TODO, how to deal with additive?
                            return getNamesFormattedForOutput(getPeersIncludeParents(name).keySet());
                        } else {
                            return "error:name not found:`" + nameString + "`";
                        }
                    }


                } else if (renameas != null) { // not specific to peers or children
                    renameName(loggedInConnection, nameString, renameas);
                    return "rename " + nameString + " to " + renameas;
                } else if (search != null) { // search
                    return getNamesFormattedForOutput(searchNames(loggedInConnection, nameString));
                }
            }
            if (search != null) { // blank search
                return getNamesFormattedForOutput(findTopNames(loggedInConnection));
            }
            return nameString;
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }

    }


    private String getNamesFormattedForOutput(final Collection<Name> names) {
        // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("{\"names\":[");
        for (Name n : names) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("{\"name\":");
            sb.append("\"").append(n.getDefaultDisplayName()).append("\"}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

/*    private String getParentStructureFormattedForOutput(final Name name, final boolean showParent) {
        StringBuilder sb = new StringBuilder();
        if (showParent) {
            sb.append("`").append(name.getDisplayName()).append("`");
        }
        Set<Name> parents = name.getParents();
        if (!parents.isEmpty()) {
            sb.append("; parents {");
            int count = 1;
            for (Name parent : parents) {
                sb.append(getParentStructureFormattedForOutput(parent, true));
                if (count < parents.size()) {
                    sb.append(",");
                }
                count++;
            }
            sb.append("}");
        }
        return sb.toString();
    }*/

    private int getTotalValues(Name name) {
        int values = name.getValues().size();
        for (Name child : name.getChildren()) {
            values += getTotalValues(child);
        }
        return values;
    }




    private String getChildStructureFormattedForOutput(final Name name) {
        //SHOULD USE GSON HERE!
        int totalValues = getTotalValues(name);
        if (totalValues > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"name\":");
            sb.append("\"").append(name.getDefaultDisplayName()).append("\"");
            sb.append(", \"id\":\"" + name.getId() + "\"");

            if (totalValues > name.getValues().size()) {
                sb.append(", \"dataitems\":\"" + totalValues + "\"");
            }
            if (name.getValues().size() > 0) {
                sb.append(", \"mydataitems\":\"" + name.getValues().size() + "\"");
            }
            //putputs the peer list as an attribute  - CURRENTLY MARKING SINGULAR PEERS WITH A '--'
            if (name.getAttributes().size() > 0 || name.getPeers().size() > 0){
                sb.append(",\"attributes\":{");
                if (name.getPeers().size() > 0){
                    String peerList = "";
                    for (Name peer : name.getPeers().keySet()) {
                        if (peerList.length() >0) {
                            peerList += ", ";
                        }
                        peerList += peer.getDefaultDisplayName();
                        if (!name.getPeers().get(peer)){
                           peerList+="--";
                        }
                    }
                    sb.append("\"peers\":\"" + peerList + "\"");

                }
                int count = 0;
                for (String attName:name.getAttributes().keySet()){
                    if (count > 0) sb.append(",");
                    sb.append("\"" + attName + "\":\"" + name.getAttributes().get(attName) + "\"");
                    count++;
                }
                sb.append("}");
            }
            final Set<Name> children = name.getChildren();
            if (!children.isEmpty()) {
                sb.append(", \"elements\":\"" + children.size() + "\"");
                sb.append(", \"children\":[");
                int count = 0;
                for (Name child : children) {
                    String childData = getChildStructureFormattedForOutput(child);
                    if (childData.length() > 0) {
                        if (count > 0) sb.append(",");
                        sb.append(childData);
                        count++;
                    }
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        }
        return "";
    }


}

