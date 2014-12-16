package com.azquo.service;

import com.azquo.jsonrequestentities.NameJsonRequest;
import com.azquo.jsonrequestentities.NameListJson;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 *
 * Ok, outside of the memorydb package this may be the the most fundamental class.
 * Edd trying to understand it properly and trying to get string parsing out of it but not sure how easy that will be
 *
 */
public final class NameService {

    // ok attempt to work things out, Edd putting code he is happy with at the top

    public StringUtils stringUtils = new StringUtils(); // just make it quickly like this for the mo
    private static final ObjectMapper jacksonMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(NameService.class);


    public static final String LEVEL = "level";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String COUNT = "count";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String PARENTS = "parents";
    public static final String LOWEST = "lowest";
    public static final String ALL = "all";
    public static final char NAMEMARKER = '!';
    public static final String PEERS = "peers";
    public static final String COUNTBACK = "count back";
    public static final String COMPAREWITH = "compare with";
    public static final String TOTALLEDAS = "totalled as";
    public static final String STRUCTURE = "structure";
    public static final String NAMELIST = "namelist";
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";
    public static final String ASSOCIATED = "associated";
    public static final String WHERE = "where";


    // get names from a comma separated list
    // edd: this is fine in principle, I'm concerned by the interpreter
    // interpret name list?

    public final List<Set<Name>> decodeString(AzquoMemoryDBConnection azquoMemoryDBConnection, String searchByNames, List<String> attributeNames) throws Exception {
        final List<Set<Name>> toReturn = new ArrayList<Set<Name>>();
        //System.out.println("search by names before strip quotes : " + searchByNames);
        searchByNames = stripQuotes(azquoMemoryDBConnection, searchByNames, attributeNames);
        //System.out.println("search by names after strip quotes : " + searchByNames);
        List<String> strings = new ArrayList<String>();
        //System.out.println("search by names before extract strings : " + searchByNames);
        searchByNames = stringUtils.extractStrings(searchByNames, strings);
        //System.out.println("search by names after extract strings : " + searchByNames);
        StringTokenizer st = new StringTokenizer(searchByNames, ",");
        while (st.hasMoreTokens()) {
            String nameName = st.nextToken().trim();
            //System.out.println("new name in decode string : " + nameName);
            List<Name> nameList = interpretSetTerm(azquoMemoryDBConnection, nameName, strings, attributeNames);
            toReturn.add(new HashSet<Name>(nameList));
        }
        return toReturn;
    }

    public ArrayList<Name> findContainingName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) {
        // go for the default for the moment
        return findContainingName(azquoMemoryDBConnection, name, Name.DEFAULT_DISPLAY_NAME);
    }

    public ArrayList<Name> findContainingName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, String attribute) {
        ArrayList<Name> namesList = new ArrayList<Name>(azquoMemoryDBConnection.getAzquoMemoryDB().getNamesWithAttributeContaining(attribute, name));
        Collections.sort(namesList);
        return namesList;
    }


    public Name findById(final AzquoMemoryDBConnection azquoMemoryDBConnection, int id) {
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(id);
    }


    public Name getNameByAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeValue, Name parent) {
        if (attributeValue.charAt(0) == NAMEMARKER) {
            try {
                int nameId = Integer.parseInt(attributeValue.substring(1).trim());
                return findById(azquoMemoryDBConnection, nameId);
            } catch (Exception e) {
                return null;
            }
        }

        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, attributeValue.replace(Name.QUOTE, ' ').trim(), parent);

    }

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) {
        return findByName(azquoMemoryDBConnection, name, null);
    }

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final List<String> attributeNames) {

     /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.
        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.
        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London
        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

        It will also recognise an interim substitution starting '!'
        */

        // language effectively being the attribute name
        // so london, ontario, canada
        // parent name would be canada
        if (name == null || name.length() == 0) return null;
        String parentName = stringUtils.findParentFromList(name);
        String remainder = name;
        Name parent = null;
        // keep chopping away at the string until we find the closest parent we can
        // the point of all of this is to be able to ask for a name with the nearest parent but we can't just try and get it from the string directly e.g. get me WHsmiths on High street
        // we need to look from the top to distinguish high street in different towns
        while (parentName != null) {
            parent = getNameByAttribute(azquoMemoryDBConnection, parentName, parent);
            if (parent == null) { // parent was null, since we're just trying to find that stops us right here
                return null;
            }
            // so chop off the last name, lastindex of moves backwards from the index
            // the reason for this is to deal with quotes, we could have said simply the substring take off the parent name length but we don't know about quotes or spaces after the comma
            // remainder is the rest of the string, could be london, ontario - Canada was taken off
            remainder = name.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length()));
            parentName = stringUtils.findParentFromList(remainder);
        }

        return getNameByAttribute(azquoMemoryDBConnection, remainder, parent);
    }

/*    public List<Name> searchNames(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String search) {
        return azquoMemoryDBConnection.getAzquoMemoryDB().searchNames(Name.DEFAULT_DISPLAY_NAME, search);
    }*/

    public void clearChildren(Name name) throws Exception {
        // DON'T DELETE SET WHILE ITERATING, SO MAKE A COPY FIRST
        if (name.getParents().size() == 0) {
            //can't clear from topparent
            return;
        }
        for (Name child : new ArrayList<Name>(name.getChildren())) { // loop over a copy
            name.removeFromChildrenWillBePersisted(child);
        }

    }

    public List<Name> findTopNames(final AzquoMemoryDBConnection azquoMemoryDBConnection) {
        return azquoMemoryDBConnection.getAzquoMemoryDB().findTopNames();
    }


    public Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, Name topParent, boolean local) throws Exception {
        return findOrCreateNameStructure(azquoMemoryDBConnection, name, topParent, local, null);
    }

    public Name findOrCreateNameStructure(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, Name topParent, boolean local, List<String> attributeNames) throws Exception {

        /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.

        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.

        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London

        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

         */


        String parentName = stringUtils.findParentFromList(name);
        String remainder = name;
        if (parentName == null) {
            return findOrCreateNameInParent(azquoMemoryDBConnection, name, topParent, local, attributeNames);
        }

       /*
        ok teh key here is to step through the parent -> child list as defined in the name string creating teh hierarchy as you go along
        the top parent is the context in which names should be searched for and created if not existing, the parent name and parent is the direct parent we may have just created
        so what unique is saying is : ok we have the parent we want to add a name to : the question is do we search under that parent to find or create or under the top parent?
        More specifically : if it is unique check for the name anywhere under the top parent to find it and then move it if necessary, if not unique then it could, for example, be another name called London
        I think maybe the names of variables could be clearer here!, maybe look into on second pass
        */
        Name parent = topParent;
        while (parentName != null) {
            remainder = remainder.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length() - 1));
            //if two commas in succession occur, ignore the blank parent
            if (parentName.length() > 0) {
                parent = findOrCreateNameInParent(azquoMemoryDBConnection, parentName, parent, local, attributeNames);
            }
            parentName = stringUtils.findParentFromList(remainder);
        }

        return findOrCreateNameInParent(azquoMemoryDBConnection, remainder, parent, local, attributeNames);

    }

    public void includeInSet(Name name, Name set) throws Exception {
        set.addChildWillBePersisted(name);//ok add as asked
        Collection<Name> setParents = set.findAllParents();
        for (Name parent : name.getParents()) { // now check the direct parents and see that none are in the parents of the set we just put it in.
            // e.g the name was Ludlow in in places. We decided to add Ludlow to Shropshire which is all well and good.
            // Among Shropshire's parents is places so remove Ludlow from Places as it's now in places via Shropshire.
            if (setParents.contains(parent)) {
                parent.removeFromChildrenWillBePersisted(name);// following my above example, take Ludlow out of places
                break;
            }
        }
    }

    public Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name newParent, boolean local) throws Exception {
        return findOrCreateNameInParent(azquoMemoryDBConnection, name, newParent, local, null);
    }

    Map<AzquoMemoryDBConnection, Map<String, Long>> timeTrack = new HashMap<AzquoMemoryDBConnection, Map<String, Long>>();

    private void addToTimesForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection, String trackName, long toAdd){
        long current = 0;
        if (timeTrack.get(azquoMemoryDBConnection) != null) {
            if (timeTrack.get(azquoMemoryDBConnection).get(trackName) != null) {
                current = timeTrack.get(azquoMemoryDBConnection).get(trackName);
            }
        } else {
            timeTrack.put(azquoMemoryDBConnection, new HashMap<String, Long>());
        }
        timeTrack.get(azquoMemoryDBConnection).put(trackName, current + toAdd);
    }

    public Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection){
        return timeTrack.get(azquoMemoryDBConnection);
    }

    public Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name parent, boolean local, List<String> attributeNames) throws Exception {

        //long marker = System.currentTimeMillis();
     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an import),and insert a structure into it*/

        if (attributeNames == null) {
            attributeNames = new ArrayList<String>();
            attributeNames.add(Name.DEFAULT_DISPLAY_NAME);
        }

        String storeName = name.replace(Name.QUOTE, ' ').trim();
        Name existing;

        //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent1", marker - System.currentTimeMillis());
        //marker = System.currentTimeMillis();
        if (parent != null) { // ok try to find it in that parent
            //try for an existing name already with the same parent
            if (local) {// ok looking only below that parent or just in it's whole set or top parent.
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, parent);
            } else {
                // Note the new name find A top parent. If names are in more than one top parent criteria to find the name might be a bit random - which top parent do you mean?
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, parent.findATopParent());
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent2", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
            // find an existing name with no parents. (note that if there are multiple such names, then the return will be null)
            // if we cant' find the name in parent then it's acceptable to find one with no parents
            if (existing == null) {
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
                if (existing != null && existing.getParents().size() > 0) {
                    existing = null;
                }
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent3", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
        } else { // no parent passed go for a vanilla lookup
            existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
            //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent4", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
        }
        if (existing != null) {
            // direct parents may be moved up the hierarchy (e.g. if existing parent is 'Europe' and new parent is 'London', which is in 'Europe' then
            // remove 'Europe' from the direct parent list.
            //NEW CONDITION ADDED - we are parent = child, but not bothering to put into the set.  This may need discussion - are the parent and child really the same?
            // I think I was just avoiding a circular reference
            if (parent != null && existing != parent && !existing.findAllParents().contains(parent)) {
                //only check if the new parent is not already in the parent hierarchy.
                includeInSet(existing, parent);
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent5", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
            return existing;
        } else {
            // actually creating a new one
            //System.out.println("New name: " + storeName + ", " + (parent != null ? "," + parent.getDefaultDisplayName() : ""));
            // todo - we should not be getting the provenance from the conneciton
            Provenance provenance = azquoMemoryDBConnection.getProvenance();
            Name newName = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), provenance, true); // default additive to true
            if (attributeNames.get(0) != Name.DEFAULT_DISPLAY_NAME) { // we set the leading attribute name, I guess the secondary ones should not be set they are for searches
                newName.setAttributeWillBePersisted(attributeNames.get(0), storeName);
            }
            newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, storeName); // and set the default regardless
            //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent6", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
            //if the parent already has peers, provisionally set the child peers to be the same.
            if (parent != null) {
                Map<Name, Boolean> newPeers = parent.getPeers();
                if (newPeers != null && !newPeers.isEmpty()) {
                    LinkedHashMap<Name, Boolean> peers2 = new LinkedHashMap<Name, Boolean>();
                    for (Name peer : newPeers.keySet()) {
                        peers2.put(peer, parent.getPeers().get(peer));

                    }
                    newName.setPeersWillBePersisted(peers2);
                }
                // and add the new name to the parent of course :)
                parent.addChildWillBePersisted(newName);
            }
            //addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent7", marker - System.currentTimeMillis());
            //marker = System.currentTimeMillis();
            return newName;
        }
    }

    public static final int LOWEST_LEVEL_INT = -1;
    public static final int ALL_LEVEL_INT = -2;

    // needs to be a list to preserve order when adding. Or could use a linked set, don't see much advantage

    public List<Name> findChildrenAtLevel(final Name name, final String levelString) throws Exception {
        // level -1 means get me the lowest
        // level -2 means 'ALL' (including the top level
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this

        int level = 1;
        if (levelString != null) {
            if (levelString.equalsIgnoreCase(LOWEST)) {
                System.out.println("lowest");
                level = LOWEST_LEVEL_INT;
            } else if (levelString.equalsIgnoreCase(ALL)) {
                level = ALL_LEVEL_INT;
            } else {
                try {
                    level = Integer.parseInt(levelString);
                } catch (NumberFormatException nfe) {
                    //carry on regardless!
                }
            }
        }


        List<Name> namesFound = new ArrayList<Name>();
        addNames(name, namesFound, 0, level);
        return namesFound;
    }

    public void addNames(final Name name, List<Name> namesFound, final int currentLevel, final int level) throws Exception {
        if (currentLevel == level || level == ALL_LEVEL_INT) {
            namesFound.add(name);
        }
        if (currentLevel == level) {
            return;
        }
        if (name.getChildren().size() == 0) {
            if (level == LOWEST_LEVEL_INT) {
                namesFound.add(name);
            }
            return;
        }
        for (Name child : name.getChildren()) {
            addNames(child, namesFound, currentLevel + 1, level);
        }

    }

    // edd : I wonder a little about this but will leave it for the mo

    private int parseInt(final String string, int existing) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return existing;
        }
    }

    // since we need different from the standard set ordering use a list, I see no real harm in that in these functions
    // note : in default language!

    public List<Name> findChildrenFromToCount(final AzquoMemoryDBConnection azquoMemoryDBConnection, final List<Name> names, String fromString, String toString, final String countString, final String countbackString, final String compareWithString, List<String> attributeNames) throws Exception {
        final ArrayList<Name> toReturn = new ArrayList<Name>();
        int to = -10000;
        int from = 1;
        int count = parseInt(countString, -1);
        int offset = parseInt(countbackString, 0);
        int compareWith = parseInt(compareWithString, 0);
        int space = 1; //spacing between 'compare with' fields
        //first look for integers and encoded names...

        if (fromString.length() > 0) {
            from = -1;
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (fromString.charAt(0) == NAMEMARKER) {
                    Name fromName = findByName(azquoMemoryDBConnection, fromString, attributeNames);
                    fromString = fromName.getDefaultDisplayName();
                }
            }

        }
        if (toString.length() > 0) {
            boolean fromEnd = false;
            if (toString.toLowerCase().endsWith("from end")) {
                fromEnd = true;
                toString = toString.substring(0, toString.length() - 9);
            }
            try {
                to = Integer.parseInt(toString);
                if (fromEnd) to = names.size() - to;
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (toString.charAt(0) == NAMEMARKER) {
                    Name toName = findByName(azquoMemoryDBConnection, toString, attributeNames);
                    toString = toName.getDefaultDisplayName();
                }
            }

        }
        int position = 1;
        boolean inSet = false;
        if (to != -1000 && to < 0) {
            to = names.size() + to;
        }


        int added = 0;

        for (int i = offset; i < names.size() + offset; i++) {

            if (position == from || (i < names.size() && names.get(i).getDefaultDisplayName().equals(fromString)))
                inSet = true;
            if (inSet) {
                toReturn.add(names.get(i - offset));
                if (compareWith != 0) {
                    toReturn.add(names.get(i - offset + compareWith));
                    for (int j = 0; j < space; j++) {
                        toReturn.add(null);
                    }
                }
                added++;
            }
            if (position == to || (i < names.size() && names.get(i).getDefaultDisplayName().equals(toString)) || added == count)
                inSet = false;
            position++;
        }
        while (added++ < count) {
            toReturn.add(null);

        }
        return toReturn;
    }


    // these should probably live somewhere more global
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    public Map<String, String> isAValidNameSet(AzquoMemoryDBConnection azquoMemoryDBConnection, final Set<Name> names, final Set<Name> validNameList) throws Exception {

        //long track = System.currentTimeMillis();

        final Map<String, String> toReturn = new HashMap<String, String>();

        String error = "";
        String warning = "";

        Name peerName = null;
        final Map<Name, Boolean> peers = new HashMap<Name, Boolean>(); // the names (or their parents) in this list which have peer requirements, should only be one
        final Set<Name> namesToCheck = new HashSet<Name>();

        for (Name name : names) {
            if (name != null) {
                String calc = name.getAttribute(Name.RPCALC);
                // if one term is calculated, the required peers will be the largest of any set of peers in the calculation
                // if one term has peers, then all should have peers, and all should be a subset of the largest set, but I'm not checking this currently
                if (calc != null && calc.length() > 0 && peers.isEmpty()) {
                    while (calc.length() > 0) {
                        String nextTerm;
                        if (calc.indexOf(" ") > 0) {
                            nextTerm = calc.substring(0, calc.indexOf(" ")).trim();
                        } else {
                            nextTerm = calc;
                        }
                        if (nextTerm.charAt(0) == NAMEMARKER) {
                            // Edd added a few != null checks here based on IntelliJ, completely necessary??
                            Name termName = getNameByAttribute(azquoMemoryDBConnection, nextTerm, null);
                            if (termName == null) {
                                error += "the formula for " + (peerName != null ? peerName.getDefaultDisplayName() : "null peer name!") + " is not understood";
                            }
                            if (termName != null && termName.getPeers().size() > peers.size()) {
                                peerName = name;
                                peers.clear();
                                for (Name peer : name.getPeers().keySet()) {
                                    peers.put(peer.findATopParent(), name.getPeers().get(peer));//we need to check that the TOP names are compatible for display in ranges
                                }
                            }
                        }
                        calc = calc.substring(nextTerm.length()).trim();
                    }
                    if (!peers.isEmpty()) {
                        peerName = name;
                        validNameList.add(name);
                    } else {
                        namesToCheck.add(name);
                    }
                } else {
                    if (!name.getPeers().isEmpty()) { // this name is the one that defines what names the data will require
                        if (peerName == null) {
                            peerName = name;
                            for (Name peer : name.getPeers().keySet()) {
                                peers.put(peer.findATopParent(), name.getPeers().get(peer));//we need to check that the TOP names are compatible for display in ranges
                            }
                        } else {
                            // commented by edd, can't be used
                            //error += "two names have peers: " + peerName.getDefaultDisplayName() + " and " + name.getDefaultDisplayName();
                            return toReturn;
                        }
                        validNameList.add(name); // the rest will be added below but we need to add this here as the peer defining name is not on the list of peers
                    } else {
                        // not adding the name with peers to namesToCheck is more efficient and it stops the name with peers from showing up as being superfluous to the peer list if that makes sense
                        namesToCheck.add(name);
                    }
                }
            }
        }


        //System.out.println("track 1-1 : " + (System.currentTimeMillis() - track) + "  ---   ");
        //track = System.currentTimeMillis();

        if (peers.isEmpty()) {
            error += "  none of the names passed have peers, I don't know what names are required for this value";
        } else { // one set of peers, ok :)
            // match peers child names are ok, ignore extra names, warn about this
            // think that is a bit ofo dirty way of getting the single item in the set . . .just assign it?
            for (Name requiredPeer : peers.keySet()) {
                boolean found = false;
                // do a first direct pass, see old logic below, I think(!) this will work and be faster. Need to think about that equals on name, much cost of tolowercase?
                if (namesToCheck.remove(requiredPeer)) {// skip to the next one and remove the name from names to check and add it to the validated list to return
                    validNameList.add(requiredPeer);
                    found = true;
                }

                if (!found) { // couldn't find this peer, need to look up through parents of each name for the peer
                    // again new logic here
                    for (Name nameToCheck : namesToCheck) {
                        if (nameToCheck.findAllParents().contains(requiredPeer)) {
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

    // hmm, looks like a string function but there's checks agains a valid name in there. Come back to that later.
    // I want to move this or rationalise it or something.

    private String stripQuotes(AzquoMemoryDBConnection azquoMemoryDBConnection, String instructions, List<String> attributeNames) throws Exception {
        //System.out.println("strip quotes : " + instructions + " attribute names  " + attributeNames);
        int lastQuoteEnd = instructions.lastIndexOf(Name.QUOTE);
        while (lastQuoteEnd >= 0) {
            int lastQuoteStart = instructions.lastIndexOf(Name.QUOTE, lastQuoteEnd - 1);
            //find the parents - if they exist
            String nameToFind = instructions.substring(lastQuoteStart, lastQuoteEnd + 1);
            if (lastQuoteEnd < instructions.length() - 1 && instructions.charAt(lastQuoteEnd + 1) == ',') {
                Pattern p = Pattern.compile("[;\\+\\*]");
                Matcher m = p.matcher(instructions.substring(lastQuoteEnd + 1));
                if (m.find()) {
                    lastQuoteEnd += m.start();//one too little....
                    nameToFind = instructions.substring(lastQuoteStart, lastQuoteEnd + 1);//adding one in here to be consistent with line adjustment below
                }
            }
            Name quoteName = findByName(azquoMemoryDBConnection, nameToFind, attributeNames);
            if (quoteName != null) {
                instructions = instructions.substring(0, lastQuoteStart) + NAMEMARKER + quoteName.getId() + " " + instructions.substring(lastQuoteEnd + 1);
                lastQuoteEnd = instructions.lastIndexOf(Name.QUOTE);
            } else {
                lastQuoteEnd = -1;
            }

        }
        return instructions;
    }

    // to find a set of names, a few bits that were part of the original set of functions
    // edd wondering how to break this up.
    public final List<Name> interpretName(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula) throws Exception {
        List<String> langs = new ArrayList<String>();
        langs.add(Name.DEFAULT_DISPLAY_NAME);
        return interpretName(azquoMemoryDBConnection, setFormula, langs);
    }

    // todo : rename - this is pretty much the start of expression parsing

    public final List<Name> interpretName(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames) throws Exception {
        //System.out.println("interpret name : " + setFormula + " attribute names " + attributeNames);

        final List<Name> nameList = new ArrayList<Name>();

        /*
        * This routine now amended t        o allow for union (+) and intersection (*) of sets.
        *
        * This entails first sorting out the names in quotes (which may contain the reserved characters),
        * starting from the end (there may be "name","parent" in the list)
        *
        * These will be replaced by !<id>   e.g. !1234
        *
        *
        * */
        List<List<Name>> nameStack = new ArrayList<List<Name>>();
        List<String> strings = new ArrayList<String>();
        setFormula = shuntingYardAlgorithm(azquoMemoryDBConnection, setFormula, strings, attributeNames);
        Pattern p = Pattern.compile("[\\+\\-\\*" + NAMEMARKER + "&]");//recognises + - * NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line

        int pos = 0;
        int stackCount = 0;
        //int stringCount = 0;
        while (pos < setFormula.length()) {
            Matcher m = p.matcher(setFormula.substring(pos + 2));
            // HANDLE SET INTERSECTIONS UNIONS AND EXCLUSIONS (* + - )
            char op = setFormula.charAt(pos);
            int nextTerm = setFormula.length() + 1;
            if (m.find()) {
                nextTerm = m.start() + pos + 2;
                //PROBLEM!   The name found may have been following 'from ' or 'to ' (e.g. dates contain '-' so need to be encapsulated in quotes)
                //  neet to check for this....
                while (nextTerm < setFormula.length() && (stringUtils.precededBy(setFormula, TO, nextTerm) || stringUtils.precededBy(setFormula, FROM, nextTerm) || stringUtils.precededBy(setFormula, TOTALLEDAS, nextTerm))) {
                    int startPos = nextTerm + 1;
                    nextTerm = setFormula.length() + 1;
                    m = p.matcher(setFormula.substring(startPos));
                    if (m.find()) {
                        nextTerm = m.start() + startPos;
                    }
                }
            }
            if (op == NAMEMARKER) {
                stackCount++;
                List<Name> nextNames = interpretSetTerm(azquoMemoryDBConnection, setFormula.substring(pos, nextTerm - 1), strings, attributeNames);
                nameStack.add(nextNames);
            } else if (op == '&') {
                if (strings.size() == 0) {
                    throw new Exception("'&' without a string");
                }
                List<Name> nextNames = new ArrayList<Name>();
                List<Name> baseNames = nameStack.get(stackCount - 1);
                for (Name name : baseNames) {
                    String nameToFind = name.getDefaultDisplayName() + strings.get(strings.size() - 1);
                    nextNames.addAll(interpretSetTerm(azquoMemoryDBConnection, nameToFind, strings, attributeNames));
                }
                strings.remove(strings.size() - 1);
                nameStack.remove(stackCount - 1);
                nameStack.add(nextNames);

            } else if (stackCount-- < 2) {
                throw new Exception("not understood:  " + setFormula);

            } else if (op == '*') {
                nameStack.get(stackCount - 1).retainAll(nameStack.get(stackCount));
                nameStack.remove(stackCount);
            } else if (op == '-') {
                nameStack.get(stackCount - 1).removeAll(nameStack.get(stackCount));
                nameStack.remove(stackCount);
            } else if (op == '+') {
                nameStack.get(stackCount - 1).addAll(nameStack.get(stackCount));
                nameStack.remove(stackCount);
            }

            pos = nextTerm;
        }
        nameList.addAll(nameStack.get(0));
        return nameList;
    }

    // arguably should be called on store and the RPCALC stored as that attribute only changes when "CALCULATION" changes

    public String calcReversePolish(AzquoMemoryDBConnection azquoMemoryDBConnection, Name name) throws Exception {
        String calc = name.getAttribute(Name.CALCULATION);
        if (calc != null && calc.length() > 0) {
            List<String> strings = new ArrayList<String>();
            String result = shuntingYardAlgorithm(azquoMemoryDBConnection, calc, strings, null);//TODO work out if we will accept the strings in calcs (pass as parameter in calcReversePolish)......
            if (result != null && result.length() > 0) {
                if (result.startsWith("error:")) {
                    return result;
                } else {
                    if (name.getAttribute(Name.RPCALC) == null || !name.getAttribute(Name.RPCALC).equals(result)) {
                        name.setAttributeWillBePersisted(Name.RPCALC, result);
                    }
                }
            }
        }
        return "";
    }


    public Name inParentSet(Name name, Collection<Name> maybeParents) {
        if (maybeParents.contains(name)) {
            return name;
        }
        for (Name parent : name.getParents()) {
            Name maybeParent = inParentSet(parent, maybeParents);
            if (maybeParent != null) {
                return maybeParent;
            }
        }
        return null;
    }


    public boolean isAllowed(Name name, List<Set<Name>> names) {

        if (name == null) {
            return true;
        }
        Name topParent = name.findATopParent();
        for (Set<Name> listNames : names) {
            if (!listNames.isEmpty()) {
                Name listName = listNames.iterator().next();//all names in each list have the same topparent, so don't try further (just get the first)
                if (topParent == listName.findATopParent()) {
                    if (inParentSet(name, listNames) != null) {
                        return true;
                    }
                }
            }
        }
        String confidential = name.getAttribute("CONFIDENTIAL");
        return confidential == null || !confidential.equalsIgnoreCase("true");
    }

    private void getAssociations(AzquoMemoryDBConnection azquoMemoryDBConnection, Collection<Name> names, String associatedString, Set<Name> namesFound, List<String> attributeNames) {
        /*
        * this routine finds sets associated with the given name.  e.g. if the name is 'UK' and the associatedString is 'shops' the
        * routine looks for 'UK shops'.  If it does not find that, it loops through subsets such as 'London shops', 'West End shops', 'Oxford Street shops', r
        * returning the set of sets.  The required names will be the elements of these sets (e.g. the shops themselves)
        *
        * */
        for (Name name : names) {
            Name associatedName = findByName(azquoMemoryDBConnection, name.getDefaultDisplayName() + " " + associatedString, attributeNames);
            if (associatedName != null) {
                namesFound.add(associatedName);
            } else {
                getAssociations(azquoMemoryDBConnection, name.getChildren(), associatedString, namesFound, attributeNames);
            }
        }
    }

    //

    private void filter(List<Name> names, String condition, List<String> strings) {
        //NOT HANDLING 'OR' AT PRESENT
        int andPos = condition.toLowerCase().indexOf(" and ");
        if (andPos < 0) {
            andPos = condition.length();
        }
        int stringCount = 0;
        Set<Name> namesToRemove = new HashSet<Name>();
        int lastPos = 0;
        while (andPos > 0) {
            String clause = condition.substring(0, andPos).trim();
            Pattern p = Pattern.compile("[<=>]+"); //
            Matcher m = p.matcher(clause);

            if (m.find()) {
                String opfound = m.group();
                int pos = m.start();
                String clauseLhs = clause.substring(0, pos).trim();
                String clauseRhs = clause.substring(m.end()).trim();
                String valRhs = "";
                boolean fixed = false;
                if (clauseRhs.charAt(0) == '"') {
                    valRhs = strings.get(stringCount++);// ignore the value in the clause - it must be a
                    fixed = true;
                }

                for (Name name : names) {
                    String valLhs = name.getAttribute(clauseLhs);
                    if (valLhs == null) {
                        valLhs = "";
                    }
                    if (!fixed) {
                        valRhs = name.getAttribute(clauseRhs);
                        if (valRhs == null) {
                            valRhs = "";
                        }
                    }
                    boolean OK = false;
                    int comp = valLhs.compareTo(valRhs);
                    for (int i = 0; i < opfound.length(); i++) {
                        char op = opfound.charAt(i);

                        switch (op) {
                            case '=':
                                if (comp == 0) OK = true;
                                break;
                            case '<':
                                if (comp < 0) OK = true;
                                break;
                            case '>':
                                if (comp > 0) OK = true;
                        }
                    }
                    if (!OK) {
                        namesToRemove.add(name);
                    }
                }
                names.removeAll(namesToRemove);
            }
            lastPos = andPos + 5;
            andPos = condition.toLowerCase().indexOf(" and ", lastPos);

        }

    }

    // edd trying to break up

    private List<Name> interpretSetTerm(AzquoMemoryDBConnection azquoMemoryDBConnection, String setTerm, List<String> strings, List<String> attributeNames) throws Exception {

        //System.out.println("interpret set term . . ." + setTerm);
        List<Name> namesFound = new ArrayList<Name>();

        final String levelString = stringUtils.getInstruction(setTerm, LEVEL);
        String fromString = stringUtils.getInstruction(setTerm, FROM);
        String childrenString = stringUtils.getInstruction(setTerm, CHILDREN);
        final String parentsString = stringUtils.getInstruction(setTerm, PARENTS);
        final String sorted = stringUtils.getInstruction(setTerm, SORTED);
        String toString = stringUtils.getInstruction(setTerm, TO);
        String countString = stringUtils.getInstruction(setTerm, COUNT);
        final String countbackString = stringUtils.getInstruction(setTerm, COUNTBACK);
        final String compareWithString = stringUtils.getInstruction(setTerm, COMPAREWITH);
        final String totalledAsString = stringUtils.getInstruction(setTerm, TOTALLEDAS);
        final String associatedString = stringUtils.getInstruction(setTerm, ASSOCIATED);
        final String whereString = stringUtils.getInstruction(setTerm, WHERE);
        if (levelString != null) {
            childrenString = "true";
        }
        List<Name> names = new ArrayList<Name>();

        String nameString = setTerm;
        if (setTerm.indexOf(';') > 0) {
            nameString = setTerm.substring(0, setTerm.indexOf(';')).trim();
        }
        final Name name = findByName(azquoMemoryDBConnection, nameString, attributeNames);
        if (name == null) {
            throw new Exception("error:  not understood: " + nameString);
        }
        if (childrenString == null && fromString == null && toString == null && countString == null) {
            names.add(name);
        } else {
            // FIRST - get the set of names given the level
            names = findChildrenAtLevel(name, levelString);
            if (fromString == null) fromString = "";
            if (toString == null) toString = "";
            if (countString == null) countString = "";
            // SECOND  Sort if necessary
            if (sorted != null) {
                Collections.sort(names);
            }

            //THIRD  trim that down to the subset defined by from, to, count
            if (fromString.length() > 0 || toString.length() > 0 || countString.length() > 0) {
                names = findChildrenFromToCount(azquoMemoryDBConnection, names, fromString, toString, countString, countbackString, compareWithString, attributeNames);
            }
        }
        if (azquoMemoryDBConnection.getReadPermissions() != null) {
            for (Name possible : names) {
                if (isAllowed(possible, azquoMemoryDBConnection.getReadPermissions())) {
                    namesFound.add(possible);
                }
            }
        } else {
            namesFound.addAll(names);
        }
        if (totalledAsString != null) {
            Name totalName;
            if (totalledAsString.charAt(0) == NAMEMARKER) {
                totalName = findByName(azquoMemoryDBConnection, totalledAsString, attributeNames);
            } else {
                totalName = findOrCreateNameInParent(azquoMemoryDBConnection, totalledAsString, namesFound.get(0).findATopParent(), false, attributeNames);//'local' is irrelevant
            }
            LinkedHashSet<Name> newChildren = new LinkedHashSet<Name>();
            for (Name child : namesFound) {
                newChildren.add(child);
            }
            totalName.setChildrenWillBePersisted(newChildren);
            namesFound.clear();
            namesFound.add(totalName);
        }
        if (associatedString != null) {
            Set<Name> associatedNames = new HashSet<Name>();
            //convert the list to a set.....
            Set<Name> originalNames = new HashSet<Name>();
            for (Name name2 : namesFound) {
                originalNames.add(name2);
            }
            getAssociations(azquoMemoryDBConnection, originalNames, associatedString, associatedNames, attributeNames);
            //and convert back to a list
            namesFound.clear();
            for (Name name2 : associatedNames) {
                namesFound.addAll(name2.findAllChildren(false));
            }
        }
        if (parentsString != null) {
            Set<Name> parents = new HashSet<Name>();
            for (Name child : namesFound) {
                parents.addAll(child.findAllParents());

            }
            namesFound.clear();
            namesFound.addAll(parents);

        }
        if (whereString != null) {
            filter(namesFound, whereString, strings);
        }
        if (sorted != null) {
            Collections.sort(namesFound);
        }
        return namesFound;
    }

    // ok it seems the name is passed purely for debugging purposes
    // called from shuntingyardalgorithm 3 times, think not on operations
    // it seems the term can be one of two things, a double value or a name.
    // first tries to parse the double value and then returns it with a space, just confirming what
    // otherwise it tries to find by name and if it finds it jams in the name ID between NAMEMARKER
    // but NAMEMARKER is only used here so what's going on there??

    //  NAMEMARKER is used to remove any contentious characters from expressions (e.g. operators that should not be there)

    private String interpretTerm(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String term, List<String> attributeNames) {

        //System.out.println("interpret term : " + term + " attribute names : " + attributeNames);

        if (term.startsWith("\"") && term.endsWith("\"")) {
            //strings already saved, so comment out the line below
            //savedStrings.add(term.substring(1,term.length()-1));
            return "";
        }
        if (term.charAt(0) == NAMEMARKER) return term + " ";

        if (NumberUtils.isNumber(term)) {
            // do we need to parse double here??
            return Double.parseDouble(term) + " ";
        }
        // this routine must interpret set formulae as well as calc formulae, hence the need to look for semicolons
        int nameEnd = term.indexOf(";");
        if (nameEnd < 0) {
            nameEnd = term.length();
        }
        Name nameFound = findByName(azquoMemoryDBConnection, term.substring(0, nameEnd), attributeNames);
        if (nameFound == null) {
            return "error: formula not understood: " + term;
        }
        return ("" + NAMEMARKER + nameFound.getId() + term.substring(nameEnd) + " ");

    }

/*    private String replaceStrings(String calc, List<String> strings) {

        int quotePos = calc.indexOf("\"");
        int constantNo = 0;
        while (quotePos >= 0) {
            int quoteEnd = calc.indexOf("\"", quotePos + 1);
            if (quoteEnd > 0) {
                calc = calc.substring(0, quotePos + 1) + strings.get(constantNo++) + calc.substring(quoteEnd);
                quotePos = calc.indexOf("\"", quoteEnd + 1);
            } else {
                quotePos = -1;
            }
        }
        return calc;
    }*/


    // reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    // it's a list of values and operations
    // ok, edd here, I don't 100% understand  the exact logic but I do know what it's doing. Maybe some more checking into it later.

    private String shuntingYardAlgorithm(AzquoMemoryDBConnection azquoMemoryDBConnection, String calc, List<String> strings, List<String> attributeNames) throws Exception {
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

        //start by replacing names in quotes (which may contain operators) with '!<name id>   - e.g.  '!1000'
        calc = stripQuotes(azquoMemoryDBConnection, calc, attributeNames);


        //save away constants as a separate array, replace temporarily with 'xxxxxxx'
        calc = stringUtils.extractStrings(calc, strings);


        Pattern p = Pattern.compile("[\\+\\-/\\*\\(\\)&]"); // only simple maths allowed at present
        StringBuilder sb = new StringBuilder();
        String stack = "";
        Matcher m = p.matcher(calc);
        String origCalc = calc;
        int startPos = 0;


        while (m.find()) {
            String opfound = m.group();
            char thisOp = opfound.charAt(0);
            int pos = m.start();
            String namefound = calc.substring(startPos, pos).trim();
            if (namefound.length() > 0) {
                String result = interpretTerm(azquoMemoryDBConnection, namefound, attributeNames);
                if (result.startsWith("error:")) {
                    return result;
                }
                sb.append(result);
            }
            char lastOffStack = ' ';
            while (!(thisOp == ')' && lastOffStack == '(') && (stack.length() > 0 && ")+-*/(".indexOf(thisOp) <= "(+-*/".indexOf(stack.charAt(0)))) {

                if (stack.charAt(0) != '(') {
                    sb.append(stack.charAt(0)).append(" ");
                }
                lastOffStack = stack.charAt(0);
                stack = stack.substring(1);
            }
            if ((thisOp == ')' && lastOffStack != '(') || (thisOp != ')' && lastOffStack == '(')) {
                return "error: mismatched brackets in " + origCalc;
            }
            if (thisOp != ')') {
                stack = thisOp + stack;
            }
            startPos = m.end();

        }
        // the last term...

        if (calc.substring(startPos).trim().length() > 0) {
            String result = interpretTerm(azquoMemoryDBConnection, calc.substring(startPos).trim(), attributeNames);
            if (result.startsWith("error:")) {
                return result;
            }
            sb.append(result);
        }

        //.. and clear the stack
        while (stack.length() > 0) {
            sb.append(stack.charAt(0)).append(" ");
            stack = stack.substring(1);
        }
        return sb.toString();
    }

    // pretty much replaced the original set of functions to do basic name manipulation
    // needs a logged in connection forn the structure return

    public String processJsonRequest(AzquoMemoryDBConnection azquoMemoryDBConnection, NameJsonRequest nameJsonRequest, List<String> attributeNames) throws Exception {
        String toReturn = "";

        // type; elements level 1; from a to b
        if (nameJsonRequest.operation.equalsIgnoreCase(STRUCTURE)) {
            return getStructureForNameSearch(azquoMemoryDBConnection, nameJsonRequest.name, -1, attributeNames);//-1 indicates to show the children
        }
        if (nameJsonRequest.operation.equalsIgnoreCase(NAMELIST)) {
            try {
                return getNamesFormattedForOutput(interpretName(azquoMemoryDBConnection, nameJsonRequest.name, attributeNames));
            } catch (Exception e) {
                return "Error:" + e.getMessage();
            }
        }

        if (nameJsonRequest.operation.equalsIgnoreCase(DELETE)) {
            if (nameJsonRequest.name.equals("all"))
                azquoMemoryDBConnection.getAzquoMemoryDB().zapUnusedNames();
            else {
                if (nameJsonRequest.id == 0) {
                    return "error: id not passed for delete";
                } else {
                    Name name = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.id);
                    if (name == null) {
                        return "error: name for id not found : " + nameJsonRequest.id;
                    }
                    if (name.getValues().size() > 0 && !nameJsonRequest.withData) {
                        return "error: cannot delete name with data : " + nameJsonRequest.id;
                    } else {
                        name.delete();
                    }
                }
            }
        }


        if (nameJsonRequest.operation.equalsIgnoreCase(EDIT) || nameJsonRequest.operation.equalsIgnoreCase(NEW)) {
            if (nameJsonRequest.id == 0 && nameJsonRequest.operation.equalsIgnoreCase(EDIT)) {
                return "error: id not passed for edit";
            } else {
                Name name;
                if (nameJsonRequest.operation.equalsIgnoreCase(EDIT)) {
                    name = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.id);
                } else {
                    // new name . . .
                    name = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), azquoMemoryDBConnection.getProvenance(), true);
                }
                if (name == null) {
                    return "error: name for id not found : " + nameJsonRequest.id;
                }
                Name newParent = null;
                Name oldParent = null;
                if (nameJsonRequest.newParent > 0) {
                    newParent = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.newParent);
                    if (newParent == null) {
                        return "error: new parent for id not found : " + nameJsonRequest.newParent;
                    }
                }
                if (nameJsonRequest.oldParent > 0) {
                    oldParent = azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.oldParent);
                    if (oldParent == null) {
                        return "error: old parent for id not found : " + nameJsonRequest.oldParent;
                    }
                }
                if (newParent != null) {
                    newParent.addChildWillBePersisted(name, nameJsonRequest.newPosition);
                }
                if (oldParent != null) {
                    oldParent.removeFromChildrenWillBePersisted(name);
                }
                boolean foundPeers = false;
                int position = 0;
                // only clear and re set if attributes passed!
                if (nameJsonRequest.attributes != null && !nameJsonRequest.attributes.isEmpty()) {
                    name.clearAttributes(); // and just re set them below
                    for (String key : nameJsonRequest.attributes.keySet()) {
                        position++;
                        if (!key.equalsIgnoreCase(PEERS)) {
                            name.setAttributeWillBePersisted(key, nameJsonRequest.attributes.get(key));
                        }
                        if (key.equalsIgnoreCase(PEERS) || (position == nameJsonRequest.attributes.keySet().size() && !foundPeers)) { // the second means run this if we hit the end having not run it
                            foundPeers = true;
                            boolean editingPeers = false;
                            LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>();
                            if (key.equalsIgnoreCase(PEERS)) { // if it's not then we're in here because no peers were sent so leave the peer list blank
                                StringTokenizer st = new StringTokenizer(nameJsonRequest.attributes.get(key), ",");
                                while (st.hasMoreTokens()) {
                                    String peerName = st.nextToken().trim();
                                    Name peer = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(Name.DEFAULT_DISPLAY_NAME, peerName, null);
                                    if (peer == null) {
                                        return "error: cannot find peer : " + peerName;
                                    } else {
                                        peers.put(peer, true);
                                    }
                                }
                            }

                            // ok need to to see if what was passed was different

                            if (peers.keySet().size() != name.getPeers().keySet().size()) {
                                editingPeers = true;
                            } else { // same size, check the elements . . .
                                for (Name peer : name.getPeers().keySet()) {
                                    if (peers.get(peer) == null) { // mismatch, old peers has something the new one does not
                                        editingPeers = true;
                                    }
                                }
                            }

                            if (editingPeers) {
                                if (name.getParents().size() == 0) { // top level, we can edit
                                    name.setPeersWillBePersisted(peers);
                                } else {
                                    if (name.getPeers().size() == 0) { // no peers on the aprent
                                        return "error: cannot edit peers, this is not a top level name and there is no peer set for  this name or it's parents, name id " + nameJsonRequest.id;
                                    }
                                    if (name.getValues().size() > 0) {
                                        return "error: cannot edit peers, this is not a top level name and there is data assigned to this name " + nameJsonRequest.id;
                                    }
                                    name.setPeersWillBePersisted(peers);
                                }
                            }
                        }
                    }
                }
                calcReversePolish(azquoMemoryDBConnection, name);
                // re set attributes, use single functions so checks happen
            }
        }
        azquoMemoryDBConnection.persist();
        return toReturn;
    }


    // right now ONLY called for the column heading in uploads, set peers on existing names


    // should use jackson??

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

    private int getTotalValues(Name name) {
        int values = name.getValues().size();
        for (Name child : name.getChildren()) {
            values += getTotalValues(child);
        }
        return values;
    }

    // use jackson?

    public String getStructureForNameSearch(AzquoMemoryDBConnection azquoMemoryDBconnection, String nameSearch, int nameId, List<String> attributeNames) {

        boolean withChildren = false;
        if (nameId == -1) withChildren = true;
        Name name = findByName(azquoMemoryDBconnection, nameSearch, attributeNames);
        if (name != null) {
            return "{\"names\":[" + getChildStructureFormattedForOutput(name, withChildren) + "]}";
        } else {
            List<Name> names = new ArrayList<Name>();
            if (nameId > 0) {
                name = findById(azquoMemoryDBconnection, nameId);
                //children is a set, so cannot be cast directly as a list.  WHY ISN'T CHILDREN A LIST?
                names = new ArrayList<Name>();
                for (Name child : name.getChildren()) {
                    names.add(child);
                }
            } else {

                if (nameSearch.length() > 0) {
                    names = findContainingName(azquoMemoryDBconnection, nameSearch.replace("`", ""));
                }
                if (names.size() == 0) {
                    names = findTopNames(azquoMemoryDBconnection);
                    Collections.sort(names);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"names\":[");
                int count = 0;
                for (Name outputName : names) {
                    String nameJson = getChildStructureFormattedForOutput(outputName, withChildren);
                    if (nameJson.length() > 0) {
                        if (count > 0) sb.append(",");
                        sb.append(nameJson);
                        count++;
                    }
                }
            sb.append("]}");
/*            if (azquoMemoryDBconnection.getAzquoBook()!=null){
                azquoMemoryDBconnection.getAzquoBook().nameChosenJson = sb.toString();
            }*/
            return sb.toString();
        }
    }

    // again should use jackson?

    private String getChildStructureFormattedForOutput(final Name name) {
        return getChildStructureFormattedForOutput(name, false);
    }


    private String getChildStructureFormattedForOutput(final Name name, boolean showChildren) {
        int totalValues = getTotalValues(name);
        //if (totalValues > 0) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":");
        sb.append("\"").append(name.getDefaultDisplayName().replace("\"", "''")).append("\"");//trapping quotes in name - should not be there
        sb.append(", \"id\":\"" + name.getId() + "\"");

        sb.append(", \"dataitems\":\"" + totalValues + "\"");
        if (name.getValues().size() > 0) {
            sb.append(", \"mydataitems\":\"" + name.getValues().size() + "\"");
        }
        //putputs the peer list as an attribute  - CURRENTLY MARKING SINGULAR PEERS WITH A '--'
        int count = 0;
        if (name.getAttributes().size() > 0 || name.getPeers().size() > 0) {
            sb.append(",\"attributes\":{");
            if (name.getPeers().size() > 0) {
                String peerList = "";
                for (Name peer : name.getPeers().keySet()) {
                    if (peerList.length() > 0) {
                        peerList += ", ";
                    }
                    peerList += peer.getDefaultDisplayName();
                    if (!name.getPeers().get(peer)) {
                        peerList += "--";
                    }
                }
                // here and a few lines below is a bit of manual JSON building. Not sure how much this is a good idea or not. Jackson?
                sb.append("\"peers\":\"" + peerList + "\"");
                count++;

            }
            for (String attName : name.getAttributes().keySet()) {
                if (count > 0) sb.append(",");
                try {
                    sb.append("\"" + attName + "\":\"" + URLEncoder.encode(name.getAttributes().get(attName).replace("\"", "''"), "UTF-8") + "\"");//replacing quotes again
                } catch (UnsupportedEncodingException e) {
                    // this really should not happen!
                    e.printStackTrace();
                }
                count++;
            }
            sb.append("}");
        }
        final Collection<Name> children = name.getChildren();
        sb.append(", \"elements\":\"" + children.size() + "\"");
        if (showChildren && !children.isEmpty()) {
            sb.append(", \"children\":[");
            count = 0;
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


    private StringBuffer printJsonName(NameListJson nameListJson) {
        StringBuffer sb = new StringBuffer();
        sb.append("<li");
        if (nameListJson.elements > 0) sb.append(" onclick=\"az_clicklist(this)\"");
        sb.append(">" + nameListJson.name + "\n");
        sb.append("</li>");

        sb.append("<div class=\"notseen\">" + nameListJson.id + "</div>\n");
        //ignoring the rest of the info for the moment....
        /*

        if (nameListJson.elements > 0){
            sb.append("<ul style=\"display:none\">");
            for (NameListJson child:nameListJson.children){
                sb.append(printJsonName(child));
            }
            sb.append("</ul>");
        }
        */
        return sb;
    }


    public String convertJsonToHTML(String json) {

        StringBuilder sb = new StringBuilder();
        sb.append("<ul class=\"namelist\">\n");
        try {
            NameListJson nameListJson = jacksonMapper.readValue(json, NameListJson.class);
            for (NameListJson child : nameListJson.names) {
                sb.append(printJsonName(child));
            }
            sb.append("</ul>\n");


        } catch (Exception e) {
            logger.error("name json parse problem", e);
            return "error:badly formed json " + e.getMessage();
        }
        return sb.toString();
    }

    public String jsonNameDetails(Name name) {
        return getChildStructureFormattedForOutput(name, false);
    }
}

