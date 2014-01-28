package com.azquo.toto.service;

import com.azquo.toto.jsonrequestentities.NameJsonRequest;
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
    public static final String NAMELIST = "namelist";
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";


    // hacky but testing for the moment

    public void persist(final LoggedInConnection loggedInConnection) {
        loggedInConnection.getTotoMemoryDB().saveDataToMySQL();
    }

    // replaces commas in quotes (e.g. "shop", "location", "region with a , in it's name" should become "shop", "location", "region with a - in it's name")  with -, useful for parsing name lists

    public String replaceCommasInQuotes(String s){
        boolean inQuotes = false;
        StringBuilder withoutCommasInQuotes = new StringBuilder();
        char[] charactersString = s.toCharArray();
        for (char c : charactersString){
            if (c == '\"'){
                inQuotes = !inQuotes;
            }
            if (c == ','){
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

    // TODO : find out what's going on with teh remainder

    public Name findByName(final LoggedInConnection loggedInConnection, final String name) {

     /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.
        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.
        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London
        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'
        */

        // language effectively being the attribute name
        String language = loggedInConnection.getLanguage();
        // so london, ontario, canada
        // parent name would be canada
        String parentName = findParentFromList(name);
        if (parentName == null) {
            // just a simple name passed, no structure
            return loggedInConnection.getTotoMemoryDB().getNameByAttribute(language, name, null);
        }
        Name parent = loggedInConnection.getTotoMemoryDB().getNameByAttribute(language, parentName, null);
        if (parent == null) { // parent was null, since we're just trying to find that stops us right here
            return null;
        }

        // so chop off the last name, lastindex of moves backwards from the index
        // the reason for this is to deal with quotes, we could have said simply the substring take off the parent name length but we don't know about quotes or spaces after the comma
        String remainder = name.substring(0, name.lastIndexOf(",", name.length() - parentName.length()));
        // remainder is the rest of the string, could be london, ontario - Canada was taken off
        parentName = findParentFromList(remainder);
        // keep chopping away at the string until we find the closest parent we can
        while (parentName != null) {
            parent = loggedInConnection.getTotoMemoryDB().getNameByAttribute(language, parentName, null);
            if (parent == null) {
                return null;
            }
            remainder = name.substring(0, remainder.lastIndexOf(",", name.length() - parentName.length()));
            parentName = findParentFromList(remainder);
        }
        // the point of all of this is to be able to ask for a name with the nearest parent but we can't just try and get it from the string directly e.g. get me WHsmiths on High street
        // we need to look from the top to distinguish high street in different towns
        return loggedInConnection.getTotoMemoryDB().getNameByAttribute(language, remainder, parent);
    }

/*    public List<Name> searchNames(final LoggedInConnection loggedInConnection, final String search) {
        return loggedInConnection.getTotoMemoryDB().searchNames(Name.DEFAULT_DISPLAY_NAME, search);
    }*/

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

        /*
        ok teh key here is to step through the parent -> child list as defined in the name string creating teh hierarchy as you go along
        the top parent is the context in which names should be searched for and created if not existing, the parent name and parent is the direct parent we may have just created
        so what unique is saying is : ok we have the parent we want to add a name to : the question is do we search under that parent to find or create or under the top parent?
        More specifically : if it is unique check for the name anywhere under the top parent to find it and then move it if necessary, if not unique then it could, for example, be another name called London
        I think maybe the names of variables could be clearer here!, maybe look into on second pass
        */
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
        final Name existing = loggedInConnection.getTotoMemoryDB().getNameByAttribute(loggedInConnection.getLanguage(), storeName, parent);
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

    // not used but I'll leave it here, could be good for debugging

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

    // to find a set of names, a few bits that were part of the original set of functions

    // TODO add count

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


    // TODO Edd try to understand
    // reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    // it's a list of values and operations

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

    public String processJsonRequest(LoggedInConnection loggedInConnection, NameJsonRequest nameJsonRequest) throws Exception {
        String toReturn = "";

        // type; elements level 1; from a to b
        if (nameJsonRequest.operation.equalsIgnoreCase(STRUCTURE)) {
            return getStructureForNameSearch(loggedInConnection, nameJsonRequest.name);
        }
        if (nameJsonRequest.operation.equalsIgnoreCase(NAMELIST)) {
            return getNamesFormattedForOutput(interpretName(loggedInConnection, nameJsonRequest.name));
        }

        if (nameJsonRequest.operation.equalsIgnoreCase(DELETE)) {
            if (nameJsonRequest.id == 0) {
                return "error: id not passed for delete";
            } else {
                Name name = loggedInConnection.getTotoMemoryDB().getNameById(nameJsonRequest.id);
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


        if (nameJsonRequest.operation.equalsIgnoreCase(EDIT) || nameJsonRequest.operation.equalsIgnoreCase(NEW)) {
            if (nameJsonRequest.id == 0 && nameJsonRequest.operation.equalsIgnoreCase(EDIT)) {
                return "error: id not passed for edit";
            } else {
                Name name;
                if (nameJsonRequest.operation.equalsIgnoreCase(EDIT)) {
                    name = loggedInConnection.getTotoMemoryDB().getNameById(nameJsonRequest.id);
                } else {
                    // new name . . .
                    name = new Name(loggedInConnection.getTotoMemoryDB(), loggedInConnection.getProvenance(), true);
                }
                if (name == null) {
                    return "error: name for id not found : " + nameJsonRequest.id;
                }
                Name newParent = null;
                Name oldParent = null;
                if (nameJsonRequest.newParent > 0) {
                    newParent = loggedInConnection.getTotoMemoryDB().getNameById(nameJsonRequest.newParent);
                    if (newParent == null) {
                        return "error: new parent for id not found : " + nameJsonRequest.newParent;
                    }
                }
                if (nameJsonRequest.oldParent > 0) {
                    newParent = loggedInConnection.getTotoMemoryDB().getNameById(nameJsonRequest.oldParent);
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
                if (!nameJsonRequest.attributes.isEmpty()){
                    name.clearAttributes(); // and just re set them below
                    for (String key : nameJsonRequest.attributes.keySet()) {
                        position++;
                          if (!key.equalsIgnoreCase(PEERS)){
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
                                    Name peer = loggedInConnection.getTotoMemoryDB().getNameByAttribute(loggedInConnection.getLanguage(), peerName, null);
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
                                return "array saved " + peersToAdd.size() + " names";
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
            ArrayList<Name> names = findContainingName(loggedInConnection, nameSearch);
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
        //SHOULD USE GSON HERE!
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
                sb.append("\"" + attName + "\":\"" + name.getAttributes().get(attName) + "\"");
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
        //}
        //return "";
    }


}

