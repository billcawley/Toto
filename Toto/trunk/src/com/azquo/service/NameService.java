package com.azquo.service;

import com.azquo.jsonrequestentities.NameJsonRequest;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import org.apache.commons.lang.math.NumberUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 */
public final class NameService {
    public static final String LEVEL = "level";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String COUNT = "count";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String LOWEST = "lowest";
    public static final String ALL = "all";
    public static final char NAMEMARKER = '!';
    public static final String PEERS = "peers";
    public static final String STRUCTURE = "structure";
    public static final String NAMELIST = "namelist";
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";


    // hacky but testing for the moment

    public void persist(final LoggedInConnection loggedInConnection) {
        loggedInConnection.getAzquoMemoryDB().saveDataToMySQL();
    }

    // replaces commas in quotes (e.g. "shop", "location", "region with a , in it's name" should become "shop", "location", "region with a - in it's name")  with -, useful for parsing name lists

    public String replaceCommasInQuotes(String s) {
        boolean inQuotes = false;
        StringBuilder withoutCommasInQuotes = new StringBuilder();
        char[] charactersString = s.toCharArray();
        for (char c : charactersString) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            }
            if (c == ',') {
                withoutCommasInQuotes.append(inQuotes ? '-' : ',');
            } else {
                withoutCommasInQuotes.append(c);
            }
        }
        return withoutCommasInQuotes.toString();
    }

    // when passed a name tries to find the last in the list e.g. london, ontario, canada gets canada
    private String findParentFromList(final String name) {
        // ok preprocess to remove commas in quotes, easiest way.
        String nameWithoutCommasInQuotes = replaceCommasInQuotes(name);
        if (!nameWithoutCommasInQuotes.contains(",")) return null;
        // get the position from the string with commas in quotes removed
        int commaPos = nameWithoutCommasInQuotes.lastIndexOf(",");
        // but return from the unmodified string
        return name.substring(commaPos + 1).trim();
    }

    // without language uses the default display name

    public ArrayList<Name> sortNames(final ArrayList<Name> namesList) {
        Collections.sort(namesList);
        return namesList;

    }

    // untested!
/*
    public ArrayList<Name> sortNames(final ArrayList<Name> namesList, final String language) {
        Comparator<Name> compareName = new Comparator<Name>() {
            public int compare(Name n1, Name n2) {
            return n1.getAttribute(language).compareTo(n1.getAttribute(language));
            }
        };
        Collections.sort(namesList, compareName);
        return namesList;
    }*/

    // get names from a comma separated list

    public  String decodeString(LoggedInConnection loggedInConnection, String searchByNames, final List<Set<Name>> names) throws Exception {
        searchByNames = stripQuotes(loggedInConnection, searchByNames);
        StringTokenizer st = new StringTokenizer(searchByNames, ",");
        while (st.hasMoreTokens()) {
            String nameName = st.nextToken().trim();
            List <Name> nameList = new ArrayList<Name>();
            String error = interpretSetTerm(loggedInConnection,nameList,nameName);

            if (nameList != null) {
                names.add(new HashSet<Name>(nameList));
            }else{
                return error;
            }
        }
        return "";

    }

    public ArrayList<Name> findContainingName(final LoggedInConnection loggedInConnection, final String name) {
        // go for the default for the moment
        return sortNames(new ArrayList<Name>(loggedInConnection.getAzquoMemoryDB().getNamesWithAttributeContaining(Name.DEFAULT_DISPLAY_NAME, name)));
    }

    public Name findById(final LoggedInConnection loggedInConnection, int id) {
        return loggedInConnection.getAzquoMemoryDB().getNameById(id);
    }

    private Name getNameByAttribute(LoggedInConnection loggedInConnection, String name, Name parent) {
        if (name.charAt(0) == NAMEMARKER) {
            try {
                int nameId = Integer.parseInt(name.substring(1).trim());
                return findById(loggedInConnection, nameId);
            } catch (Exception e) {
                return null;
            }
        }
        return loggedInConnection.getAzquoMemoryDB().getNameByAttribute(loggedInConnection, name.replace("\"", ""), parent);

    }

    public Name findByName(final LoggedInConnection loggedInConnection, final String name) {

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
        String parentName = findParentFromList(name);
        String remainder = name;
        Name parent = null;
        // keep chopping away at the string until we find the closest parent we can
        // the point of all of this is to be able to ask for a name with the nearest parent but we can't just try and get it from the string directly e.g. get me WHsmiths on High street
        // we need to look from the top to distinguish high street in different towns
        while (parentName != null) {
            parent = getNameByAttribute(loggedInConnection, parentName, parent);
            if (parent == null) { // parent was null, since we're just trying to find that stops us right here
                return null;
            }
            // so chop off the last name, lastindex of moves backwards from the index
            // the reason for this is to deal with quotes, we could have said simply the substring take off the parent name length but we don't know about quotes or spaces after the comma
            // remainder is the rest of the string, could be london, ontario - Canada was taken off
            remainder = name.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length()));
            parentName = findParentFromList(remainder);
        }

        return getNameByAttribute(loggedInConnection, remainder, parent);
    }

/*    public List<Name> searchNames(final LoggedInConnection loggedInConnection, final String search) {
        return loggedInConnection.getAzquoMemoryDB().searchNames(Name.DEFAULT_DISPLAY_NAME, search);
    }*/

    public void clearChildren(Name name) throws Exception{
        // DON'T DELETE SET WHILE ITERATING, SO MAKE A COPY FIRST
        Set <Name> children = new HashSet<Name>();
        for (Name child:name.getChildren()){
            children.add(child);
        }
        for (Name child:children){
            name.removeFromChildrenWillBePersisted(child);
        }

    }

    public List<Name> findTopNames(final LoggedInConnection loggedInConnection) {
        return loggedInConnection.getAzquoMemoryDB().findTopNames();
    }


    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name) throws Exception {
        if (name.toLowerCase().endsWith(";plural")) {
            return findOrCreateName(loggedInConnection, name.substring(0, name.length() - 7), false);
        }
        return findOrCreateName(loggedInConnection, name, true);
    }


    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name, boolean unique) throws Exception {

        /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.

        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.

        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London

        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

         */


        // as I (Edd) understand this will be the top parent
        Name topParent = null;
        String parentName = findParentFromList(name);
        String remainder = name;
        if (parentName == null) {
            return findOrCreateName(loggedInConnection, name, null, null);
        }


        /*
        ok teh key here is to step through the parent -> child list as defined in the name string creating teh hierarchy as you go along
        the top parent is the context in which names should be searched for and created if not existing, the parent name and parent is the direct parent we may have just created
        so what unique is saying is : ok we have the parent we want to add a name to : the question is do we search under that parent to find or create or under the top parent?
        More specifically : if it is unique check for the name anywhere under the top parent to find it and then move it if necessary, if not unique then it could, for example, be another name called London
        I think maybe the names of variables could be clearer here!, maybe look into on second pass
        */
        Name parent = null;
        while (parentName != null) {
            remainder = remainder.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length() - 1));
            //if two commas in succession occur, ignore the blank parent
            if (parentName.length() > 0) {
                parent = findOrCreateName(loggedInConnection, parentName, topParent, parent);
                //the attribute 'PLURAL' means that the name cannot be used to identify as a parent
                if (parent != null && (topParent == null || !unique) && (parent.getAttribute("PLURAL") == null || !parent.getAttribute("PLURAL").equalsIgnoreCase("true"))) {
                    topParent = parent;
                }
            }
            parentName = findParentFromList(remainder);
        }

        return findOrCreateName(loggedInConnection, remainder, topParent, parent);

    }

    // TODO : address the two parents passed through in this function and how it interacts with the above function.

    public Name findOrCreateName(final LoggedInConnection loggedInConnection, final String name, final Name parent, final Name newparent) throws Exception {

     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an import,and insert a structure into it
        the 'parent' will usually be the top of the tree, and the new parent will be a name created as a branch.  */


        String storeName = name.replace("\"", "");

        final Name existing = loggedInConnection.getAzquoMemoryDB().getNameByAttribute(loggedInConnection, storeName, parent);
        if (existing != null) {
            // I think this is in the case of unique = true, the name to be created is in fact being moved down the hierachy
            if (newparent != null && newparent != parent && existing != newparent) {
                if (parent != null) {
                    parent.removeFromChildrenWillBePersisted(existing);
                }
                newparent.addChildWillBePersisted(existing);
            }
            return existing;
        } else {
            // actually creating a new one
            Provenance provenance = loggedInConnection.getProvenance();
            Name newName = new Name(loggedInConnection.getAzquoMemoryDB(), provenance, true); // default additive to true
            newName.setAttributeWillBePersisted(loggedInConnection.getLanguage(), storeName);
            if (!loggedInConnection.getLanguage().equals(Name.DEFAULT_DISPLAY_NAME)) {
                String displayName = newName.getAttribute(Name.DEFAULT_DISPLAY_NAME);
                if (displayName == null || displayName.length() == 0) {
                    newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, storeName);
                }
            }
            //  the remove here makes no sense, names are equal by ID, it will never match. Check with dad . . .
            //  the 'parent' in this case may be the top parent, while the new parent may be next up the hierarchy.
            if (newparent != null) {
                if (newparent != parent && parent != null) {
                    parent.removeFromChildrenWillBePersisted(newName);
                }
                newparent.addChildWillBePersisted(newName);
            }
            return newName;
        }
    }

    // needs to be a list to preserve order when adding. Or could use a linked set, don't see much advantage

    public List<Name> findChildrenAtLevel(final Name name, final String levelString) throws Exception {
        // level -1 means get me the lowest
        // level -2 means 'ALL' (including the top level
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this

        int level = 1;
        if (levelString != null) {
            if (levelString.equalsIgnoreCase(LOWEST)) {
                System.out.println("lowest");
                level = -1;
            } else if (levelString.equalsIgnoreCase(ALL)) {
                level = -2;
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
        if (currentLevel == level || level == -2) {
            namesFound.add(name);
        }
        if (currentLevel == level) {
            return;
        }
        if (name.getChildren().size() == 0) {
            if (level == -1) {
                namesFound.add(name);
            }
            return;
        }
        for (Name child : name.getChildren()) {
            addNames(child, namesFound, currentLevel + 1, level);
        }

    }

    // since we need different from the standard set ordering use a list, I see no real harm in that in these functions
    // note : in default language!


    public List<Name> findChildrenFromToCount(final LoggedInConnection loggedInConnection, final List<Name> names, String fromString, String toString, final String countString) throws Exception {
        final ArrayList<Name> toReturn = new ArrayList<Name>();
        int to = -10000;
        int from = 1;
        int count = -1;

        //first look for integers and encoded names...

        if (fromString.length() > 0) {
            from = -1;
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (fromString.charAt(0) == NAMEMARKER) {
                    Name fromName = findByName(loggedInConnection, fromString);
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
                    Name toName = findByName(loggedInConnection, toString);
                    toString = toName.getDefaultDisplayName();
                }
            }

        }

        if (countString.length() > 0) {
            try {
                count = Integer.parseInt(countString);
            } catch (NumberFormatException nfe) {

                // should I actually throw an exception as count should really just work?
            }

        }


        int position = 1;
        boolean inSet = false;
        if (to != -1000 && to < 0) {
            to = names.size() + to;
        }


        int added = 0;

        for (Name name : names) {
            if (position == from || name.getDefaultDisplayName().equals(fromString)) inSet = true;
            if (inSet) {
                toReturn.add(name);
                added++;
            }
            if (position == to || name.getDefaultDisplayName().equals(toString) || added == count) inSet = false;
            position++;
        }
        return toReturn;
    }


    // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
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

    // used to be in the controller, should it be back there???

    public String getInstruction(final String instructions, final String instructionName) {
        String toReturn = null;
        //needs to detect that e.g. 'from' is an instruction, and not contained in a word
        int iPos = instructions.toLowerCase().indexOf(instructionName.toLowerCase());
        if (iPos >= 0) {
            while (iPos > 0 && instructions.charAt(iPos - 1) != ';') {
                iPos = instructions.toLowerCase().indexOf(instructionName.toLowerCase(), iPos + 1);
            }
        }
        if (iPos >= 0) {
            int commandStart = instructions.toLowerCase().indexOf(instructionName.toLowerCase()) + instructionName.length();
            if (instructions.indexOf(";", commandStart) != -1) {
                toReturn = instructions.substring(commandStart, instructions.indexOf(";", commandStart)).trim();
            } else {
                toReturn = instructions.substring(commandStart).trim();
            }
            if (toReturn.startsWith("\"")) {
                toReturn = toReturn.substring(1, toReturn.length() - 1); // trim quotes
            }
            if (toReturn.length() > 0 && toReturn.charAt(0) == '=') {
                toReturn = toReturn.substring(1).trim();
            }
        }
        return toReturn;
    }


    private String stripQuotes(LoggedInConnection loggedInConnection, String instructions) throws Exception {
        int lastQuoteEnd = instructions.lastIndexOf("\"");
        while (lastQuoteEnd >= 0) {
            int lastQuoteStart = instructions.lastIndexOf("\"", lastQuoteEnd - 1);
            //find the parents - if they exist
            String nameToFind = instructions.substring(lastQuoteStart, lastQuoteEnd + 1);
            if (lastQuoteEnd < instructions.length() - 1 && instructions.charAt(lastQuoteEnd + 1) == ',') {
                Pattern p = Pattern.compile("[ ;\\+\\*]");
                Matcher m = p.matcher(instructions.substring(lastQuoteEnd + 1));
                if (m.find()) {
                    lastQuoteEnd += m.start();
                    nameToFind = instructions.substring(lastQuoteStart, lastQuoteEnd);
                }
            }
            Name quoteName = findByName(loggedInConnection, nameToFind);
            if (quoteName != null) {
                instructions = instructions.substring(0, lastQuoteStart) + NAMEMARKER + quoteName.getId() + " " + instructions.substring(lastQuoteEnd + 1);
                lastQuoteEnd = instructions.lastIndexOf("\"");
            } else {
                lastQuoteEnd = -1;
            }

        }
        return instructions;
    }

    // to find a set of names, a few bits that were part of the original set of functions
    //seems a bit overkill in teh case where a name is just the name but works still I think

    public String interpretName(final LoggedInConnection loggedInConnection, final List<Name> nameList, String setFormula) throws Exception {


        /*
        * This routine now amended to allow for union (+) and intersection (*) of sets.
        *
        * This entails first sorting out the names in quotes (which may contain the reserved characters),
        * starting from the end (there may be "name","parent" in the list)
        *
        * These will be replaced by !<id>   e.g. !1234
        *
        *
        * */
        List<List<Name>> nameStack = new ArrayList<List<Name>>();
        setFormula = shuntingYardAlgorithm(loggedInConnection, setFormula);
        if (setFormula.startsWith("error:")) {
            return setFormula;
        }


        Pattern p = Pattern.compile("[\\+-/\\*\\(\\)" + NAMEMARKER + "]"); // only simple maths allowed at present
        int pos = 0;
        int stackCount = 0;
        while (pos < setFormula.length()) {
            Matcher m = p.matcher(setFormula.substring(pos + 2));
            // HANDLE SET INTERSECTIONS UNIONS AND EXCLUSIONS (* + - )
            char op = setFormula.charAt(pos);
            int nextTerm = setFormula.length() + 1;
            if (m.find()) {
                nextTerm = m.start() + pos + 2;
                //PROBLEM!   The name found may have been following 'from ' or 'to ' (e.g. dates contain '-' so need to be encapsulated in quotes)
                //  neet to check for this....
                while (nextTerm > 5 && nextTerm < setFormula.length() && (setFormula.substring(nextTerm - 5, nextTerm).equalsIgnoreCase("from ") || setFormula.substring(nextTerm - 3, nextTerm).equalsIgnoreCase("to "))) {
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
                List<Name> nextNames = new ArrayList<Name>();
                String error = interpretSetTerm(loggedInConnection, nextNames, setFormula.substring(pos, nextTerm - 1));
                if (error.length() > 0) {
                    return error;
                }
                nameStack.add(nextNames);
            } else if (stackCount-- < 2) {
                return "error: not understood:  " + setFormula;

            } else if (op == '*') {
                nameStack.get(stackCount - 1).retainAll(nameStack.get(stackCount));
            } else if (op == '-') {
                nameStack.get(stackCount - 1).removeAll(nameStack.get(stackCount));
            } else if (op == '+') {
                nameStack.get(stackCount - 1).addAll(nameStack.get(stackCount));
            }
            pos = nextTerm;
        }
        nameList.addAll(nameStack.get(0));
        return "";
    }

    // arguably should be called on store and the RPCALC stored as that attribute only changes when "CALCULATION" changes

    public String calcReversePolish(LoggedInConnection loggedInConnection, Name name) throws Exception {
        String calc = name.getAttribute("CALCULATION");
        if (calc != null && calc.length() > 0) {
            String result = shuntingYardAlgorithm(loggedInConnection, calc);
            if (result != null && result.length() > 0) {
                if (result.startsWith("error:")) {
                    return result;
                } else {
                    if (name.getAttribute("RPCALC") == null || !name.getAttribute("RPCALC").equals(result)) {
                        name.setAttributeWillBePersisted("RPCALC", result);
                    }
                }
            }
        }
        return "";
    }


    public Name inParentSet(Name name, Set<Name> maybeParents){
        if (maybeParents.contains(name)) {
            return name;
        }
        for (Name parent: name.getParents()){
            Name maybeParent =inParentSet(parent, maybeParents);
            if (maybeParent != null){
                return maybeParent;
            }
        }
        return null;
    }



    public boolean isAllowed(Name name, List<Set<Name>> names){
        Name topParent = name.findTopParent();
        for (Set<Name> listNames:names){
            for (Name listName:listNames){
                if (topParent == listName.findTopParent()){
                    if(inParentSet(name, listNames) != null) {
                        return true;
                    }
                }
                break; //all names in each list have the same topparent, so don't try further
            }
        }
        String confidential = name.getAttribute("CONFIDENTIAL");
        if (confidential == null || !confidential.equalsIgnoreCase("true")) return true;
        return false;
    }


    private String interpretSetTerm(LoggedInConnection loggedInConnection, List<Name> namesFound, String setTerm) throws Exception {

        final String levelString = getInstruction(setTerm, LEVEL);
        String fromString = getInstruction(setTerm, FROM);
        final String childrenString = getInstruction(setTerm, CHILDREN);
        String toString = getInstruction(setTerm, TO);
        String countString = getInstruction(setTerm, COUNT);
        List<Name> names = new ArrayList<Name>();

        String nameString = setTerm;
        if (setTerm.indexOf(';') > 0) {
            nameString = setTerm.substring(0, setTerm.indexOf(';')).trim();
        }
        final Name name = findByName(loggedInConnection, nameString);
        if (name == null) {
            return "error:  not understood: " + nameString;
        }
        if (childrenString == null){
             names.add(name);
         } else {
            // FIRST - get the set of names given the level
            names = findChildrenAtLevel(name, levelString);
            if (fromString == null) fromString = "";
            if (toString == null) toString = "";
            if (countString == null) countString = "";
            // SECOND  Sort if necessary
            if (getInstruction(setTerm, SORTED) != null) {
                Collections.sort(names);
            }

            //THIRD  trim that down to the subset defined by from, to, count
            if (fromString.length() > 0 || toString.length() > 0 || countString.length() > 0) {
                names = findChildrenFromToCount(loggedInConnection, names, fromString, toString, countString);
            }
         }
        if (loggedInConnection.getReadPermissions() != null){
           for (Name possible:names){
               if (isAllowed(possible, loggedInConnection.getReadPermissions())){
                    namesFound.add(possible);
               }
           }
        }else{
           namesFound.addAll(names);
        }
        return "";
    }

    // ok it seems the name is passed purely for debugging purposes
    // called from shuntingyardalgorithm 3 times, think not on operations
    // it seems the term can be one of two things, a double value or a name.
    // first tries to parse the double value and then returns it with a space, just confirming what
    // otherwise it tries to find by name and if it finds it jams in the name ID between NAMEMARKER
    // but NAMEMARKER is only used here so what's going on there??

    //  NAMEMARKER is used to remove any contentious characters from expressions (e.g. operators that should not be there)

    private String interpretTerm(final LoggedInConnection loggedInConnection, final String term) {


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
        Name nameFound = findByName(loggedInConnection, term.substring(0, nameEnd));
        if (nameFound == null) {
            return "error: formula not understood: " + term;
        }
        return ("" + NAMEMARKER + nameFound.getId() + term.substring(nameEnd) + " ");

    }

    // reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    // it's a list of values and operations
    // ok, edd here, I don't 100% understand  the exact logic but I do know what it's doing. Maybe some more checking into it later.

    private String shuntingYardAlgorithm(LoggedInConnection loggedInConnection, String calc) throws Exception {
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
        calc = stripQuotes(loggedInConnection, calc);

        Pattern p = Pattern.compile("[\\+\\-/\\*\\(\\)]"); // only simple maths allowed at present
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
                String result = interpretTerm(loggedInConnection, namefound);
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
                return "error mismatched brackets in " + origCalc;
            }
            if (thisOp != ')') {
                stack = thisOp + stack;
            }
            startPos = m.end();

        }
        // the last term...

        if (calc.substring(startPos).trim().length() > 0) {
            String result = interpretTerm(loggedInConnection, calc.substring(startPos).trim());
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

    public String processJsonRequest(LoggedInConnection loggedInConnection, NameJsonRequest nameJsonRequest) throws Exception {
        String toReturn = "";

        // type; elements level 1; from a to b
        if (nameJsonRequest.operation.equalsIgnoreCase(STRUCTURE)) {
            return getStructureForNameSearch(loggedInConnection, nameJsonRequest.name);
        }
        if (nameJsonRequest.operation.equalsIgnoreCase(NAMELIST)) {
            List<Name> nameList = new ArrayList<Name>();
            String error = interpretName(loggedInConnection, nameList, URLDecoder.decode(nameJsonRequest.name));
            if (error.length() > 0) {
                return error;
            }
            return getNamesFormattedForOutput(nameList);
        }

        if (nameJsonRequest.operation.equalsIgnoreCase(DELETE)) {
            if (nameJsonRequest.name.equals("all"))
                loggedInConnection.getAzquoMemoryDB().zapUnusedNames();
            else {
                if (nameJsonRequest.id == 0) {
                    return "error: id not passed for delete";
                } else {
                    Name name = loggedInConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.id);
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
                    name = loggedInConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.id);
                } else {
                    // new name . . .
                    name = new Name(loggedInConnection.getAzquoMemoryDB(), loggedInConnection.getProvenance(), true);
                }
                if (name == null) {
                    return "error: name for id not found : " + nameJsonRequest.id;
                }
                Name newParent = null;
                Name oldParent = null;
                if (nameJsonRequest.newParent > 0) {
                    newParent = loggedInConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.newParent);
                    if (newParent == null) {
                        return "error: new parent for id not found : " + nameJsonRequest.newParent;
                    }
                }
                if (nameJsonRequest.oldParent > 0) {
                    oldParent = loggedInConnection.getAzquoMemoryDB().getNameById(nameJsonRequest.oldParent);
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
                                    Name peer = loggedInConnection.getAzquoMemoryDB().getNameByAttribute(loggedInConnection, peerName, null);
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
                                    if (getPeersIncludeParents(name).size() == 0) { // no peers on the aprent
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
                calcReversePolish(loggedInConnection,name);
                // re set attributes, use single functions so checks happen
            }
        }
        persist(loggedInConnection);
        return toReturn;
    }


    // right now ONLY called for the column heading in uploads, set peers on existing names

    public String setPeersForImportHeading(LoggedInConnection loggedInConnection, String instructions)
            throws Exception {
        try {
            String nameString = instructions;
            if (instructions == null) {
                return "error:no instructions passed";
            }
            System.out.println("instructions : |" + instructions + "|");
            instructions = instructions.trim();
            // typically a command will start with a name
            if (instructions.indexOf(';') > 0) { // actually something to do
                nameString = instructions.substring(0, instructions.indexOf(';')).trim();
                instructions = instructions.substring(instructions.indexOf(';') + 1).trim();
                String peers = getInstruction(instructions, PEERS);
                String create = getInstruction(instructions, CREATE);
                if (peers != null) {
                    System.out.println("peers : |" + peers + "|");
                    if (peers.length() > 0) { // ok, add the peers. No create, just from existing
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
                                return nameString;
                            } else {
                                return "error:Unclosed }";
                            }
                        }
                        // taken away support for inserting/removing a single peer
                    }
                }
            }
            return nameString;
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }

    }

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

    private String getStructureForNameSearch(LoggedInConnection loggedInConnection, String nameSearch) {
        final Name name = findByName(loggedInConnection, nameSearch);
        if (name != null) {
            return "{\"names\":[" + getChildStructureFormattedForOutput(name) + "]}";
        } else {
            ArrayList<Name> names = new ArrayList<Name>();
            if (nameSearch.length() > 0) {
                names = findContainingName(loggedInConnection, nameSearch.replace("\"", ""));
            }
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
    }

    // again should use jackson?

    private String getChildStructureFormattedForOutput(final Name name) {
        int totalValues = getTotalValues(name);
        //if (totalValues > 0) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":");
        sb.append("\"").append(name.getDefaultDisplayName()).append("\"");
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
                sb.append("\"peers\":\"" + peerList + "\"");
                count++;

            }
            for (String attName : name.getAttributes().keySet()) {
                if (count > 0) sb.append(",");
                sb.append("\"" + attName + "\":\"" + URLEncoder.encode(name.getAttributes().get(attName)) + "\"");
                count++;
            }
            sb.append("}");
        }
        final Set<Name> children = name.getChildren();
        sb.append(", \"elements\":\"" + children.size() + "\"");
        if (!children.isEmpty()) {
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


}

