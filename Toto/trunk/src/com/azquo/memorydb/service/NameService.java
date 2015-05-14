package com.azquo.memorydb.service;

import com.azquo.spreadsheet.JSTreeService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.jsonrequestentities.NameJsonRequest;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.spreadsheet.LoggedInConnection;
import com.azquo.spreadsheet.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
//dataimport java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * <p/>
 * Ok, outside of the memorydb package this may be the the most fundamental class.
 * Edd trying to understand it properly and trying to get string parsing out of it but not sure how easy that will be
 * <p/>
 * I don't feel that tere are obviously better places for the funcitons right now except that
 * todo : can the string parsing and json generation be moved out of here?
 */
public final class NameService {

    @Autowired
    ValueService valueService;//used only in formating children for output
    @Autowired
    SpreadsheetService spreadsheetService;//used only in formating children for output

    public StringUtils stringUtils = new StringUtils(); // just make it quickly like this for the mo
    //    private static final ObjectMapper jacksonMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(NameService.class);

    public static final String LEVEL = "level";
    public static final String PARENTS = "parents";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String COUNT = "count";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String SELECT = "select";
    //public static final String LOWEST = "lowest";
    //public static final String ALL = "all";
    public static final char NAMEMARKER = '!';
    public static final char ATTRIBUTEMARKER = '|';
    public static final String STRUCTURE = "structure";
    public static final String NAMELIST = "namelist";
    public static final String CREATE = "create";
    public static final String PEERS = "peers";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";
    public static final String COUNTBACK = "countback";
    public static final String COMPAREWITH = "comparewith";
    public static final String AS = "as";
    public static final String WHERE = "where";

    // hopefully thread safe??
    public final Comparator<Name> defaultLanguageCaseInsensitiveNameComparator = new Comparator<Name>() {
        @Override
        public int compare(Name n1, Name n2) {
            return n1.getDefaultDisplayName().toUpperCase().compareTo(n2.getDefaultDisplayName().toUpperCase()); // think that will give us a case insensitive sort!
        }
    };

    // get names from a comma separated list. Well expressions describing names.

    public final List<Set<Name>> decodeString(AzquoMemoryDBConnection azquoMemoryDBConnection, String searchByNames, List<String> attributeNames) throws Exception {
        final List<Set<Name>> toReturn = new ArrayList<Set<Name>>();
        List<String> formulaStrings = new ArrayList<String>();
        List<String> nameStrings = new ArrayList<String>();
        List<String> attributeStrings = new ArrayList<String>(); // attribute names is taken. Perhaps need to think about function parameter names
        searchByNames = stringUtils.parseStatement(searchByNames, nameStrings, formulaStrings, attributeStrings);
        List<Name> referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
        // given that parse statement treats "," as an operator this should be ok.
        StringTokenizer st = new StringTokenizer(searchByNames, ",");
        while (st.hasMoreTokens()) {
            String nameName = st.nextToken().trim();
            List<Name> nameList = interpretSetTerm(nameName, formulaStrings, referencedNames, attributeStrings);
            toReturn.add(new HashSet<Name>(nameList));
        }
        return toReturn;
    }

    // we replace the names with markers for parsing. Then we need to resolve them later, here is where the exception will be thrown. Should be NameNotFoundException?

    public List<Name> getNameListFromStringList(List<String> nameStrings, AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> attributeNames) throws Exception {
        List<Name> referencedNames = new ArrayList<Name>();
        for (String nameString : nameStrings) {
            Name toAdd = findByName(azquoMemoryDBConnection, nameString, attributeNames);
            if (toAdd == null) {
                throw new Exception("error: cannot resolve reference to a name " + nameString);
            }
            referencedNames.add(toAdd);
        }
        return referencedNames;
    }

    public ArrayList<Name> findContainingName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) {
        // go for the default for the moment
        return findContainingName(azquoMemoryDBConnection, name, Name.DEFAULT_DISPLAY_NAME);
    }

    // the parameter is called name as the get attribute function will look up and derive attributes it can't find from parent/combinations

    public ArrayList<Name> findContainingName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, String attribute) {
        ArrayList<Name> namesList = new ArrayList<Name>(azquoMemoryDBConnection.getAzquoMemoryDB().getNamesWithAttributeContaining(attribute, name));
        Collections.sort(namesList, defaultLanguageCaseInsensitiveNameComparator);
        return namesList;
    }

    public Name findById(final AzquoMemoryDBConnection azquoMemoryDBConnection, int id) {
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameById(id);
    }

    public Name getNameByAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, String attributeValue, Name parent, final List<String> attributeNames) throws Exception {
        if (attributeValue.charAt(0) == NAMEMARKER) {
            throw new Exception("error: getNameByAttribute should no longer have name marker passed to it!");
        }
        return azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, attributeValue.replace(Name.QUOTE, ' ').trim(), parent);
    }

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name) throws Exception {
        // aha, not null passed here now, jam in a default display name I think
        List<String> attNames = new ArrayList<String>();
        attNames.add(Name.DEFAULT_DISPLAY_NAME);
        return findByName(azquoMemoryDBConnection, name, attNames);
    }

    public Name findByName(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final List<String> attributeNames) throws Exception {

     /* this routine now accepts a comma separated list to indicate a 'general' hierarchy.
        This may not be an immediate hierarchy.
        e.g.  if 'London, place' is sent, then the system will look for any 'London' that is ultimately in the set 'Place', whether through direct parent, or parent of parents.
        It can accept multiple layers - ' London, Ontario, Place' would find   Place/Canada/Ontario/London
        It should also recognise ""    "London, North", "Ontario", "Place"     should recognise that the 'North' is part of 'London, North'

        */

        // language effectively being the attribute name
        // so london, ontario, canada
        // parent name would be canada
        if (name == null || name.length() == 0) return null;
        String parentName = stringUtils.findParentFromList(name);
        String remainder = name;
        Set<Name> possibleParents = null;
        // keep chopping away at the string until we find the closest parent we can
        // the point of all of this is to be able to ask for a name with the nearest parent but we can't just try and get it from the string directly e.g. get me WHsmiths on High street
        // we need to look from the top to distinguish high street in different towns
        while (parentName != null) {
            remainder = name.substring(0, name.lastIndexOf(",", remainder.length() - parentName.length()));
            if (possibleParents==null){
                possibleParents= azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(attributeNames, parentName.replace(Name.QUOTE, ' ').trim(),null);
             }else{
                Set<Name>nextParents = new HashSet<Name>();
                for (Name parent:possibleParents) {
                    Name foundParent = getNameByAttribute(azquoMemoryDBConnection, parentName, parent, attributeNames);
                    if (foundParent!=null){
                        nextParents.add(foundParent);
                    }
                    possibleParents = nextParents;
                }
            }
            if (possibleParents == null || possibleParents.size() == 0) { // parent was null, since we're just trying to find that stops us right here
                return null;
            }
            // so chop off the last name, lastindex of moves backwards from the index
            // the reason for this is to deal with quotes, we could have said simply the substring take off the parent name length but we don't know about quotes or spaces after the comma
            // remainder is the rest of the string, could be london, ontario - Canada was taken off
            parentName = stringUtils.findParentFromList(remainder);
        }
        if (possibleParents==null){
            return getNameByAttribute(azquoMemoryDBConnection, remainder, null, attributeNames );

        }else {
            for (Name parent : possibleParents) {
                Name found = getNameByAttribute(azquoMemoryDBConnection, remainder, parent, attributeNames);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public void clearChildren(Name name) throws Exception {
        if (name.getParents().size() == 0) {
            //can't clear from topparent
            return;
        }
        // after changes to name it's safe to iterate over name.getChildren
        for (Name child : name.getChildren()) {
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
        /* NEW BEHAVIOUR APRIL 2015
           If, on the first pass at finding a parent, more than one parent is found, it will see if it can find the whole string in any of the parent sets.
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

    private long addToTimesForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection, String trackName, long marker) {
        long now = System.currentTimeMillis();
        long toAdd = marker - now;
        long current = 0;
        if (timeTrack.get(azquoMemoryDBConnection) != null) {
            if (timeTrack.get(azquoMemoryDBConnection).get(trackName) != null) {
                current = timeTrack.get(azquoMemoryDBConnection).get(trackName);
            }
        } else {
            timeTrack.put(azquoMemoryDBConnection, new HashMap<String, Long>());
        }
        timeTrack.get(azquoMemoryDBConnection).put(trackName, current + toAdd);
        return now;
    }

    public Map<String, Long> getTimeTrackMapForConnection(AzquoMemoryDBConnection azquoMemoryDBConnection) {
        return timeTrack.get(azquoMemoryDBConnection);
    }

    private static final boolean profile = false;

    public Name findOrCreateNameInParent(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String name, final Name parent, boolean local, List<String> attributeNames) throws Exception {

        long marker = System.currentTimeMillis();
        if (name.length() == 0) {
            return null;
        }
     /* this routine is designed to be able to find a name that has been put in with little structure (e.g. directly from an dataimport),and insert a structure into it*/
        if (attributeNames == null) {
            attributeNames = new ArrayList<String>();
            attributeNames.add(Name.DEFAULT_DISPLAY_NAME);
        }

        String storeName = name.replace(Name.QUOTE, ' ').trim();
        Name existing;

        if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent1", marker);
        if (parent != null) { // ok try to find it in that parent
            //try for an existing name already with the same parent
            if (local) {// ok looking only below that parent or just in it's whole set or top parent.
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, parent);
            } else {
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent2", marker);
            // find an existing name with no parents. (note that if there are multiple such names, then the return will be null)
            // if we cant' find the name in parent then it's acceptable to find one with no parents
            if (existing == null) {
                existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
                if (existing != null && existing.getParents().size() > 0) {
                    existing = null;
                }
            }
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent3", marker);
        } else { // no parent passed go for a vanilla lookup
            existing = azquoMemoryDBConnection.getAzquoMemoryDB().getNameByAttribute(attributeNames, storeName, null);
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent4", marker);
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
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent5", marker);
            return existing;
        } else {
            logger.debug("New name: " + storeName + ", " + (parent != null ? "," + parent.getDefaultDisplayName() : ""));
            // todo - we should not be getting the provenance from the connection
            Provenance provenance = azquoMemoryDBConnection.getProvenance("imported");
            Name newName = new Name(azquoMemoryDBConnection.getAzquoMemoryDB(), provenance, true); // default additive to true
            // was != which would probably have worked but safer with !.equals
            if (!attributeNames.get(0).equals(Name.DEFAULT_DISPLAY_NAME)) { // we set the leading attribute name, I guess the secondary ones should not be set they are for searches
                newName.setAttributeWillBePersisted(attributeNames.get(0), storeName);
            }
            newName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, storeName); // and set the default regardless
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent6", marker);
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
            if (profile) marker = addToTimesForConnection(azquoMemoryDBConnection, "findOrCreateNameInParent7", marker);
            return newName;
        }
    }

    public static final int LOWEST_LEVEL_INT = 100;
    public static final int ALL_LEVEL_INT = 101;

    // needs to be a list to preserve order when adding. Or could use a linked set, don't see much advantage

    public List<Name> findChildrenAtLevel(final Name name, final String levelString) throws Exception {
        // level 100 means get me the lowest
        // level 101 means 'ALL' (including the top level
        // notable that with current logic asking for a level with no data returns no data not the nearest it can get. Would be simple to change this
        int level = 1;
        if (levelString != null) {
            try {
                level = Integer.parseInt(levelString);
            } catch (NumberFormatException nfe) {
                //carry on regardless!
            }
        }
        List<Name> namesFound = new ArrayList<Name>();
        addNames(name, namesFound, 0, level);
        return namesFound;
    }

    public void addNames(final Name name, Collection<Name> namesFound, final int currentLevel, final int level) throws Exception {
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

    // when parsing expressions we replace names with markers and jam them on a list. The expression is manipulated before being executed. On execution the referenced names need to be read from a list.

    public Name getNameFromListAndMarker(String nameMarker, List<Name> nameList) throws Exception {
        if (nameMarker.charAt(0) == NAMEMARKER) {
            try {
                int nameNumber = Integer.parseInt(nameMarker.substring(1).trim());
                return nameList.get(nameNumber);
            } catch (Exception e) {
                throw new Exception("error: " + nameMarker + " is not a valid name");
            }
        } else {
            throw new Exception("error: " + nameMarker + " is not a valid name");
        }

    }

    // since we need different from the standard set ordering use a list, I see no real harm in that in these functions
    // note : in default language!

    public List<Name> findChildrenFromToCount(final List<Name> names, String fromString, String toString, final String countString, final String countbackString, final String compareWithString, List<Name> referencedNames) throws Exception {
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
                    Name fromName = getNameFromListAndMarker(fromString, referencedNames);
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
                    Name toName = getNameFromListAndMarker(toString, referencedNames);
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

    // to find a set of names, a few bits that were part of the original set of functions
    // add the default display name since no attributes were specified.
    public final List<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula) throws Exception {
        List<String> langs = new ArrayList<String>();
        langs.add(Name.DEFAULT_DISPLAY_NAME);
        return parseQuery(azquoMemoryDBConnection, setFormula, langs);
    }

    // todo : sort exceptions? Move to another class?

    public final List<Name> parseQuery(final AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> attributeNames) throws Exception {
        /*
        * This routine now amended to allow for union (+) and intersection (*) of sets.
        *
        * This entails first sorting out the names in quotes (which may contain the reserved characters),
        * starting from the end (there may be "name","parent" in the list)
        *
        * These will be replaced by !<id>   e.g. !1234
        * */
        final List<Name> toReturn = new ArrayList<Name>();
        List<List<Name>> nameStack = new ArrayList<List<Name>>();
        List<String> formulaStrings = new ArrayList<String>();
        List<String> nameStrings = new ArrayList<String>();
        List<String> attributeStrings = new ArrayList<String>(); // attribute names is taken. Perhaps need to think about function parameter names

        setFormula = stringUtils.parseStatement(setFormula, nameStrings, attributeStrings, formulaStrings);
        List<Name> referencedNames = getNameListFromStringList(nameStrings, azquoMemoryDBConnection, attributeNames);
        setFormula = stringUtils.shuntingYardAlgorithm(setFormula);
        Pattern p = Pattern.compile("[\\+\\-\\*/" + NAMEMARKER + "&]");//recognises + - * / NAMEMARKER  NOTE THAT - NEEDS BACKSLASHES (not mentioned in the regex tutorial on line

        logger.debug("Set formula after SYA " + setFormula);
        logger.debug("Set formula after SYA " + setFormula);
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
                //  need to check for this....
                while (nextTerm < setFormula.length() && (stringUtils.precededBy(setFormula, AS, nextTerm) || stringUtils.precededBy(setFormula, TO, nextTerm) || stringUtils.precededBy(setFormula, FROM, nextTerm) || stringUtils.precededBy(setFormula, AS, nextTerm))) {
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
                List<Name> nextNames = interpretSetTerm(setFormula.substring(pos, nextTerm - 1), formulaStrings, referencedNames, attributeStrings);
                nameStack.add(nextNames);
            } else if (stackCount-- < 2) {
                throw new Exception("not understood:  " + setFormula);
            } else if (op == '*') { // * meaning intersection here . . .
                //assume that the second term implies 'level all'
                Set<Name> allNames = new HashSet<Name>();
                for (Name name : nameStack.get(stackCount)) {
                    addNames(name, allNames, 0, ALL_LEVEL_INT);
                }
                nameStack.get(stackCount - 1).retainAll(allNames);
                nameStack.remove(stackCount);
            } else if (op == '/') {
                Set<Name> parents = new HashSet<Name>();
                for (Name child : nameStack.get(stackCount)) {
                    parents.addAll(child.findAllParents());

                }
                nameStack.get(stackCount - 1).retainAll(parents);
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

        boolean hasPermissions = false;
        if (azquoMemoryDBConnection.getReadPermissions().size() > 0) {
            hasPermissions = true;
        }
        for (Name possible : nameStack.get(0)) {
            if (possible == null || (possible.getAttribute("CONFIDENTIAL") == null && (!hasPermissions || isAllowed(possible, azquoMemoryDBConnection.getReadPermissions())))) {
                toReturn.add(possible);
            }
        }
        return toReturn;
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
        if (name == null || names == null) {
            return true;
        }
         /*
         * check if name is in one relevant set from names.  If so then OK
         * If not, then depends if the name is confidential.
          *
          * */
        for (Set<Name> listNames : names) {
            if (!listNames.isEmpty()) {
                //Name listName = listNames.iterator().next();//all names in each list have the same topparent, so don't try further (just get the first)
                if (inParentSet(name, listNames) != null) {
                    return true;
                }
            }
        }
        String confidential = name.getAttribute("CONFIDENTIAL");
        return confidential == null || !confidential.equalsIgnoreCase("true");
    }

    private void filter(List<Name> names, String condition, List<String> strings, List<String> attributeNames) {
        //NOT HANDLING 'OR' AT PRESENT
        int andPos = condition.toLowerCase().indexOf(" and ");
        Set<Name> namesToRemove = new HashSet<Name>();
        int lastPos = 0;
        while (lastPos < condition.length()) {
            if (andPos < 0) {
                andPos = condition.length();
            }
            String clause = condition.substring(lastPos, andPos).trim();
            Pattern p = Pattern.compile("[<=>]+");
            Matcher m = p.matcher(clause);

            if (m.find()) {
                String opfound = m.group();
                int pos = m.start();
                String clauseLhs = clause.substring(0, pos).trim();
                String clauseRhs = clause.substring(m.end()).trim();

                // note, given the new parser these clauses will either be literals or begin .
                // there may be code improvements that can be made knowing this

                if (clauseLhs.charAt(0) == ATTRIBUTEMARKER) {// we need to replace it
                    clauseLhs = attributeNames.get(Integer.parseInt(clauseLhs.substring(1, 3)));
                }
                if (clauseRhs.charAt(0) == ATTRIBUTEMARKER) {// we need to replace it
                    clauseRhs = attributeNames.get(Integer.parseInt(clauseRhs.substring(1, 3)));
                }
                String valRhs = "";
                boolean fixed = false;
                if (clauseRhs.charAt(0) == '"') {
                    valRhs = strings.get(Integer.parseInt(clauseRhs.substring(1, 3)));// anything left in quotes is referenced in the strings list
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

    private List<Name> interpretSetTerm(String setTerm, List<String> strings, List<Name> referencedNames, List<String> attributeStrings) throws Exception {
        //System.out.println("interpret set term . . ." + setTerm);
        List<Name> namesFound = new ArrayList<Name>();

        final String levelString = stringUtils.getInstruction(setTerm, LEVEL);
        String fromString = stringUtils.getInstruction(setTerm, FROM);
        String parentsString = stringUtils.getInstruction(setTerm, PARENTS);
        String childrenString = stringUtils.getInstruction(setTerm, CHILDREN);
        final String sorted = stringUtils.getInstruction(setTerm, SORTED);
        String toString = stringUtils.getInstruction(setTerm, TO);
        String countString = stringUtils.getInstruction(setTerm, COUNT);
        final String countbackString = stringUtils.getInstruction(setTerm, COUNTBACK);
        final String compareWithString = stringUtils.getInstruction(setTerm, COMPAREWITH);
        String totalledAsString = stringUtils.getInstruction(setTerm, AS);
        String selectString = stringUtils.getInstruction(setTerm, SELECT);
        // removed totalled as

        //final String associatedString = stringUtils.getInstruction(setTerm, ASSOCIATED);
        int wherePos = setTerm.toLowerCase().indexOf(WHERE.toLowerCase());
        String whereString = null;
        if (wherePos >= 0) {
            whereString = setTerm.substring(wherePos + 6);//the rest of the string???   maybe need 'group by' in future
        }
        if (levelString != null) {
            childrenString = "true";
        }
        List<Name> names = new ArrayList<Name>();
        // used to be ; at the end of a name
        String nameString = setTerm;
        if (setTerm.indexOf(' ') > 0) {
            nameString = setTerm.substring(0, setTerm.indexOf(' ')).trim();
        }
        final Name name = getNameFromListAndMarker(nameString, referencedNames);
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

            //THIRD  trim that down to the subset defined by from, to, count
            if (fromString.length() > 0 || toString.length() > 0 || countString.length() > 0) {
                names = findChildrenFromToCount(names, fromString, toString, countString, countbackString, compareWithString, referencedNames);
            }
        }
        namesFound.addAll(names);
        if (totalledAsString != null) {
            // now will only work on existing names
            Name totalName = getNameFromListAndMarker(totalledAsString, referencedNames);
            totalName.setChildrenWillBePersisted(namesFound);
            namesFound.clear();
            namesFound.add(totalName);
        }
        if (whereString != null) {
            filter(namesFound, whereString, strings, attributeStrings);
        }
        if (parentsString != null) {
            //remove the childless names
            List<Name> filteredList = new ArrayList<Name>();
            for (Name possibleName : namesFound) {
                if (possibleName.getChildren().size() > 0) {
                    filteredList.add(possibleName);
                }

            }
            namesFound = filteredList;
        }
        if (selectString != null){
            String toFind = strings.get(Integer.parseInt(selectString.substring(1, 3))).toLowerCase();
            List<Name> selectedNames = new ArrayList<Name>();
            for (Name sname:namesFound){
                if (sname.getDefaultDisplayName().toLowerCase().contains(toFind)){

                    selectedNames.add(sname);
                }
            }
            namesFound = selectedNames;
        }
        if (sorted != null) {
            Collections.sort(namesFound, defaultLanguageCaseInsensitiveNameComparator);
        }
        return namesFound;
    }

    // return the intersection of the sets

    public Set<Name> setIntersection(Set<Name> sets, boolean payAttentionToAdditive) {
        // ok going to make this very simple for the moment
        // find the smallest of the sets
        int smallestNameSetSize = -1;
        Name smallestSet = null;
        for (Name set : sets) {
            int setSize = set.findAllChildren(payAttentionToAdditive).size();
            if (smallestNameSetSize == -1 || setSize < smallestNameSetSize) {
                smallestNameSetSize = setSize;
                smallestSet = set;
            }
        }
        if (smallestSet == null) {
            return new HashSet<Name>();
        }
        // we want the smallest set as a retainall against it should be faster then a big set. I think.
        Set<Name> toReturn = new HashSet<Name>(smallestSet.findAllChildren(payAttentionToAdditive));
        toReturn.add(smallestSet);

        for (Name set : sets) {
            if (set != smallestSet) { // then check the intersection (no point checking on the smallest set!)
                boolean retainSetItself = false;
                if (toReturn.contains(set)) { //then add it abck in after the retain all just featuring the children. I don't want to have to copy the find all chidren results
                    retainSetItself = true;
                }
                toReturn.retainAll(set.findAllChildren(payAttentionToAdditive)); // basic intersection
                if (retainSetItself) {
                    toReturn.add(set);
                }
            }
        }
        return toReturn;
    }

        /* only relevant where there are peers, not completely sure of it
    It wants to make sure all names are in the same top set as peers
    but only up to the number of peers? Well leave for the mo.

    was in value service, not sure why
    */


    Set<Name> trimNames(Name name, Set<Name> nameSet) {
        //this is for weeding out peers when an element of the calc has less peers
        int required = name.getPeers().size();
        Set<Name> applicableNames = new HashSet<Name>();
        for (Name peer : name.getPeers().keySet()) {
            for (Name listName : nameSet) {
                if (listName.findATopParent() == peer.findATopParent()) {
                    applicableNames.add(listName);
                    if (--required == 0) {
                        return applicableNames;
                    }
                }
            }
        }
        return applicableNames;
    }
}