package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by edward on 10/09/16.
 *
 * To extract heading reading logic from DSImportService.
 *
 */
class HeadingReader {
    // to define context headings use this divider
    private static final String headingDivider = "|";

    /*
    These are heading clauses. Heading definitions can be in the data file but Azquo is setup to support data "as it comes".
    Hence when dealing with a new set of data the key is to set up sets and headings so that the system can load the data.
    Setting up the sets and headings could be seen as similar to setting up the tables in an SQL database.

    Note : the clauses here tend to reverse the subject/object used in the code. If an object in the code has children we'll say object.children, not object.parentOf.
    This isn't a big problem and the way the clauses are set up probably makes sense in their context, I just want to note that as they are parsed naming may reverse - parentOf to children etc.

    How these are used is described in more detail in the heading fields and the clause interpreter.
     */

    private static final String CHILDOF = "child of "; // trailing space I suppose one could otherwise get a false child ofweryhwrs match which can't happen with the others
    // parent of another heading (as opposed to name), would like the clause to be more explicit, as in differentiate between a name in the database and a column
    private static final String PARENTOF = "parent of ";
    private static final String ATTRIBUTE = "attribute";
    private static final String LANGUAGE = "language";
    private static final String PEERS = "peers";
    private static final String LOCAL = "local";
    private static final String COMPOSITION = "composition";
    private static final String DEFAULT = "default";
    private static final String NONZERO = "nonzero";
    private static final String DATELANG = "date";
    private static final String ONLY = "only";
    private static final String EXCLUSIVE = "exclusive";
    private static final String COMMENT = "comment";
    private static final String EXISTING = "existing"; // only works in in context of child of
    // essentially using either of these keywords switches to pivot mode (as in Excel pivot) where a name is created from the line number and in a set called the name of the file, uploading successive files with the same name would of course cause problems for this system
    private static final String LINEHEADING = "lineheading";//lineheading and linedata are shortcuts for data destined for a pivot table
    private static final String LINEDATA = "linedata";


    // todo - enable intellij to analyse?
    static List<ImmutableImportHeading> readHeaders(AzquoMemoryDBConnection azquoMemoryDBConnection, String[] headers, String importInterpreterLookup, List<String> attributeNames) throws Exception {
        List<MutableImportHeading> headings = new ArrayList<>();
        int col = 0;
        //  if the file is of type (e.g.) 'sales' and there is a name 'dataimport sales', this is used as an interpreter.
        //  It need not interpret every column heading, but any attribute of the same name as a column heading will be used.
        Name importInterpreter = NameService.findByName(azquoMemoryDBConnection, "dataimport " + importInterpreterLookup, attributeNames);
        String lastHeading = "";
        int colWithContext = 0;
        boolean pivot = false;
        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                MutableImportHeading heading = new MutableImportHeading();
                String head = null;
                // stored header overrides one on the file. A little odd IMO - as also mentioned in the comment where the function is called
                if (importInterpreter != null) {
                    head = importInterpreter.getAttribute(header);
                }
                if (head == null) {
                    head = header;
                }

                /*
                line heading and data

                Line heading means that the cell data on the line will be a name that is a parent of the line no

                Line data means that the cell data on the line will be a value which is attached to the line number name (for when there's not for example an order reference to be a name on the line)
                 */

                head = head.replace(".", ";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                if (head.contains(LINEHEADING) && head.indexOf(";") > 0) {
                    pivot = true;
                    String headname = head.substring(0, head.indexOf(";"));
                    Name headset = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All headings", null, false);
                    // create the set the line heading name will go in
                    NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), headset, true);//note - headings in different import files will be considered the same if they have the same name
                    head = head.replace(LINEHEADING, ";parent of LINENO;child of " + headname.replace("_", " ") + ";language " + headname);
                }
                if (head.contains(LINEDATA) && head.indexOf(";") > 0) {
                    pivot = true;
                    String headname = head.substring(0, head.indexOf(";"));
                    Name alldataset = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All data", null, false);
                    Name thisDataSet = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, importInterpreterLookup + " data", alldataset, false);
                    // create the set the line data name will go in
                    NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), thisDataSet, false);
                    head = head.replace(LINEDATA, ";peers {LINENO}").replace("_", " ");
                }
                int dividerPos = head.lastIndexOf(headingDivider);

                if (dividerPos == -1 && col > 0) { // no context headings defined for this one, copy the previous (may well be empty)
                    // hence context headings may be re-resolved multiple times if they're copied, this doesn't bother me that much
                    heading.contextHeadings = headings.get(colWithContext).contextHeadings;
                }

                // right, headingDivider, |. It seems to work backwards, stacking context headings for this heading, now this is against the heading as opposed to putting them in the same array
                while (dividerPos >= 0) {
                    colWithContext = col;
                    MutableImportHeading contextHeading = new MutableImportHeading();
                    interpretHeading(azquoMemoryDBConnection, head.substring(dividerPos + 1), contextHeading, attributeNames);
                    //headings.add(contextHeading); // no we're not doing that now, headings list matches the actual number of headings
                    heading.contextHeadings.add(contextHeading);
                    head = head.substring(0, dividerPos);
                    dividerPos = head.lastIndexOf(headingDivider);
                }
                //  to deal with the .replace(".", ";attribute ")
                if (head.startsWith(";")) {
                    head = lastHeading + head;
                } else {
                    if (head.contains(";")) {
                        lastHeading = head.substring(0, head.indexOf(";"));
                    } else {
                        lastHeading = head;
                    }
                }
                int fileNamePos = head.toLowerCase().indexOf("right(filename,");
                if (fileNamePos > 0) {
                    //extract this part of the file name.  This is currently limited to this format
                    int functionEnd = head.indexOf(")", fileNamePos);
                    if (functionEnd > 0) {
                        try {
                            int len = Integer.parseInt(head.substring(fileNamePos + "right(filename,".length(), functionEnd));
                            String replacement = "";
                            if (len < importInterpreterLookup.length()) {
                                replacement = importInterpreterLookup.substring(importInterpreterLookup.length() - len);
                            }
                            head = head.replace(head.substring(fileNamePos - 1, functionEnd + 2), replacement);// accomodating the quote marks
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (head.length() > 0) {
                    interpretHeading(azquoMemoryDBConnection, head, heading, attributeNames);
                }
                headings.add(heading);
            } else {// add an empty one, the headings ArrayList should match the number of headings even if that heading is empty
                headings.add(new MutableImportHeading());
            }
            col++;
        }

        // pivot true if line heading or data used, add an artifical heading on so that the line no will be created for each line no
        // one could add this to the top of the file . . .
        if (pivot) {
            Name allLines = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All lines", null, false);
            // create the name based on this file name where we put the names generated to deal with pivot tables. Note this means uploading a file with the same name and different data causes havok!
            NameService.findOrCreateNameInParent(azquoMemoryDBConnection, importInterpreterLookup + " lines", allLines, false);
            MutableImportHeading pivotHeading = new MutableImportHeading();
            interpretHeading(azquoMemoryDBConnection, "LINENO;composition LINENO;language " + importInterpreterLookup + ";child of " + importInterpreterLookup + " lines", pivotHeading, attributeNames);
            headings.add(pivotHeading);
        }
        // further information put into the ImportHeadings based off the initial info
        fillInHeaderInformation(azquoMemoryDBConnection, headings);
        // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
        final List<ImmutableImportHeading> immutableImportHeadings = new ArrayList<>(headings.size());
        immutableImportHeadings.addAll(headings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList())); // not sure if stream is that clear here but it gives me a green light from IntelliJ
        return immutableImportHeadings;
    }

    /* This is called for all the ; separated clauses in a header e.g. Gender; parent of Customer; child of Genders
    Called multiple times per header. I assume clause is trimmed! */

    private static void interpretClause(final AzquoMemoryDBConnection azquoMemoryDBConnection, final MutableImportHeading heading, final String clause) throws Exception {
        String firstWord = clause.toLowerCase(); // default, what it could legitimately be in the case of blank clauses (local, exclusive, non zero)
        // I have to add special cases for parent of and child of as they have spaces in them. A bit boring
        if (firstWord.startsWith(PARENTOF)) { // a blank won't match here as its trailing space would be trimmed but no matter, blank not allowed anyway
            firstWord = PARENTOF;
        } else if (firstWord.startsWith(CHILDOF)) {
            firstWord = CHILDOF;
        } else {
            if (firstWord.contains(" ")) {
                firstWord = firstWord.substring(0, firstWord.indexOf(" "));
            }
        }
        if (clause.length() == firstWord.length() && !firstWord.equals(COMPOSITION) && !firstWord.equals(LOCAL) && !firstWord.equals(NONZERO) && !firstWord.equals(EXCLUSIVE) && !firstWord.equals(EXISTING)) { // empty clause, exception unless one which allows blank
            throw new Exception(clause + " empty in " + heading.heading + " in headings"); // other clauses cannot be blank!
        }
        String result = clause.substring(firstWord.length()).trim();
        switch (firstWord) {
            case PARENTOF: // not NOT parent of an existing name in the DB, parent of other data in the line
                heading.parentOfClause = result.replace(Name.QUOTE + "", "");// parent of names in the specified column
                break;
            case CHILDOF: // e.g. child of all orders, unlike above this references data in the DB
                String childOfString = result.replace(Name.QUOTE + "", "");
                // used to store the child of string here and interpret it later, I see no reason not to do it here.
                String[] parents = childOfString.split(",");//TODO this does not take into account names with commas inside
                for (String parent : parents) {
                    heading.parentNames.add(NameService.findOrCreateNameInParent(azquoMemoryDBConnection, parent, null, false));
                }
                break;
            case LANGUAGE: // language being attribute
                if (result.equalsIgnoreCase(DATELANG)) {
                    heading.isDate = true;
                } else {
                    heading.isAttributeSubject = true; // language is important so we'll default it as the attribute subject if attributes are used later - I might need to check this
                }
                if (heading.attribute == null || !heading.isDate) {//any other named attribute overrides 'language date'
                    heading.attribute = result;
                }
                break;
            case ATTRIBUTE: // same as language really but .Name is special - it means default display name. Watch out for this.
                heading.attribute = result.replace(Name.QUOTE + "", "");
                if (heading.attribute.equalsIgnoreCase("name")) {
                    heading.attribute = Constants.DEFAULT_DISPLAY_NAME;
                }
                break;
            case ONLY:
                heading.only = result;
                break;
            case COMPOSITION:// combine more than one column
                heading.compositionPattern = result;
                break;
            case COMMENT: // ignore
                break;
            case DEFAULT: // if there's no value on the line a default
                if (result.length() > 0) {
                    heading.defaultValue = result;
                }
                break;
            case PEERS: // in new logic this is the only place that peers are used in Azquo
                heading.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);
                String peersString = result;
                if (peersString.startsWith("{")) { // array, typically when creating in the first place, the spreadsheet call will insert after any existing. Like children not robust to commas
                    if (peersString.contains("}")) {
                        peersString = peersString.substring(1, peersString.indexOf("}"));
                        Collections.addAll(heading.peers, peersString.split(","));
                    } else {
                        throw new Exception("Unclosed } in headings");
                    }
                }
                break;
            case LOCAL:  // local names in child of, can work with parent of but then it's the subject that it affects
                heading.isLocal = true;
                break;
            case NONZERO: // Ignore zero values. This and local will just ignore values after e.g. "nonzero something" I see no harm in this
                heading.blankZeroes = true;
                break;
            case EXCLUSIVE:// it can be blank OR have a value EFC 28/07/16 : it seems just blank at the moment? Should clarify.
                heading.exclusive = "";
                break;
            case EXISTING: // currently simply a boolean that can work with childof
                heading.existing = true;
                break;
            default:
                throw new Exception(firstWord + " not understood in headings");
        }
    }

    /*
    headings are clauses separated by semicolons, first is the heading name then onto the extra stuff
    essentially parsing through all the relevant things in a heading to populate a MutableImportHeading
    */

    private static void interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, MutableImportHeading heading, List<String> attributeNames) throws Exception {
        StringTokenizer clauses = new StringTokenizer(headingString, ";");
        heading.heading = clauses.nextToken().replace(Name.QUOTE + "", ""); // the heading name being the first
        heading.name = NameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames); // at this stage, look for a name, but don't create it unless necessary
        // loop over the clauses making sense and modifying the heading object as you go
        while (clauses.hasMoreTokens()) {
            interpretClause(azquoMemoryDBConnection, heading, clauses.nextToken().trim());
        }
    }

    // sort peer headings, attribute headings, child of, parent of, context peer headings
    // called right after readHeaders, try to do as much checking as possible here. Some of this logic was unnecessarily being done each line

    private static void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        int currentHeadingIndex = 0;
        // use a for loop like this as we need the index
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading mutableImportHeading = headings.get(headingNo);
            if (mutableImportHeading.heading != null) {
                // ok find the indexes of peers and get shirty if you can't find them
                // this had a check that is wasn't a context heading so presumably we don't need to do this to context headings
                if (mutableImportHeading.name != null && mutableImportHeading.peers.size() > 0) { // has peers (of course) and a name. Little unsure on the name criteria - could one define peers against no name?
                    for (String peer : mutableImportHeading.peers) {
                        peer = peer.trim();
                        //three possibilities to find the peer:
                        int peerHeadingIndex = findMutableHeadingIndex(peer, headings);
                        if (peerHeadingIndex >= 0) {
                            mutableImportHeading.peerCellIndexes.add(peerHeadingIndex);
                        } else {
                            // when dealing with populating peer headings first look for the headings then look at the context headings, that's what this does - now putting the context names in their own field
                            // note : before all contexts were scanned, this is not correct!
                            int lookForContextIndex = currentHeadingIndex;
                            // the point it to start for the current and look back until we find headings (if we can)
                            while (lookForContextIndex >= 0) {
                                MutableImportHeading check = headings.get(lookForContextIndex);// first time it will be the same as teh heading we're checking
                                if (!check.contextHeadings.isEmpty()) { // then it's this one that's relevant
                                    for (MutableImportHeading contextCheck : check.contextHeadings) {
                                        if (contextCheck.name == null) {// create the name for the context if it doesn't exist
                                            List<String> languages = new ArrayList<>();
                                            languages.add(Constants.DEFAULT_DISPLAY_NAME);
                                            contextCheck.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, contextCheck.heading, null, false, languages);
                                        } // then if it matched add it to the peers from context set
                                        if (contextCheck.name != null && contextCheck.name.getDefaultDisplayName().equalsIgnoreCase(peer)) {//WFC: this used to look in the parents of the context name.  Now only looks at the name itself.
                                            mutableImportHeading.peersFromContext.add(contextCheck.name);
                                            peerHeadingIndex = 0;
                                            break;
                                        }
                                    }
                                    break;
                                }
                                lookForContextIndex--;
                            }
                        }
                        if (peerHeadingIndex == -1) {
                            throw new Exception("error: cannot find peer " + peer + " for " + mutableImportHeading.name.getDefaultDisplayName());
                        }
                    }
                }
                // having an attribute means the content of this column relates to a name in another column, need to find that name
                // fairly simple stuff, it's using findMutableHeadingIndex to find the subject of attributes and parents
                fillAttributeAndParentOfForHeading(mutableImportHeading, headings);
                for (MutableImportHeading contextHeading : mutableImportHeading.contextHeadings) {
                    fillAttributeAndParentOfForHeading(contextHeading, headings);
                }
            }
            currentHeadingIndex++;
        }

        /* ok here I'm putting some logic that WAS in the actual line reading relating to context headings and peers etc.
         worth noting that after the above the headings were (I mean in the previous code) made immutable so the state of the headers should be the same
         Since the above loop will populate some context names I'm going to leave this as a separate loop below
         */
        for (MutableImportHeading mutableImportHeading : headings) {
            if (mutableImportHeading.contextHeadings.size() > 0 && mutableImportHeading.name != null) { // ok so some context headings and a name for this column? I guess as in not an attribute column for example
                MutableImportHeading contextPeersHeading = null;
                List<Name> contextNames = new ArrayList<>();
                // gather the context names and peers
                for (MutableImportHeading contextHeading : mutableImportHeading.contextHeadings) {
                    contextNames.add(contextHeading.name);
                    if (!contextHeading.peers.isEmpty()) {
                        contextPeersHeading = contextHeading;
                        if (contextHeading.blankZeroes) {
                            mutableImportHeading.blankZeroes = true;
                        }
                    }
                }
                contextNames.add(mutableImportHeading.name);// add this name onto the context stack - "this" referenced below will mean it's added again but only the first time, on subsequent headings it will be that heading (what with headings inheriting contexts)
                if (contextPeersHeading != null) { // a value cell HAS to have peers, context headings are only for values
                    final Set<Name> namesForValue = new HashSet<>(); // the names we're preparing for values
                    namesForValue.add(contextPeersHeading.name);// ok the "defining" name with the peers.
                    final Set<Integer> possiblePeerIndexes = new HashSet<>(); // the names we're preparing for values
                    boolean foundAll = true;
                    for (String peer : contextPeersHeading.peers) { // ok so a value with peers
                        if (peer.equalsIgnoreCase("this")) {
                            possiblePeerIndexes.add(-1); // can't use 0, this means "this" as in this heading - since context peer indexes are passed along what "this" is will change
                            // essentially an inconsistent use of possiblePeerIndexes - in most cases it refers to the line name and in this case it's the heading name
                        } else {
                            Name possiblePeer = null;
                            for (Name contextPeer : contextNames) {
                                if (contextPeer.getDefaultDisplayName().equalsIgnoreCase(peer)) {
                                    possiblePeer = contextPeer;
                                    namesForValue.add(contextPeer);
                                    break;
                                }
                            }
                            // couldn't find it in the context so look through the headings?
                            if (possiblePeer == null) {
                                int possiblePeerIndex = findMutableHeadingIndex(peer, headings);
                                if (possiblePeerIndex == -1) {
                                    foundAll = false;
                                    break;
                                } else {
                                    possiblePeerIndexes.add(possiblePeerIndex);
                                }
                            }
                        }
                    }
                    if (foundAll) { // the peers based of indexes will not of course have been checked but we have a set of names which have been checked and indexes to check against
                        mutableImportHeading.contextPeersFromContext = namesForValue;
                        mutableImportHeading.contextPeerCellIndexes = possiblePeerIndexes;
                    }
                }
            }
        }
        // and finally, detect whether each column will be referencing names or not
        Set<Integer> indexesNeedingNames = new HashSet<>();
        for (MutableImportHeading mutableImportHeading : headings) {
            indexesNeedingNames.addAll(mutableImportHeading.peerCellIndexes);
            indexesNeedingNames.addAll(mutableImportHeading.contextPeerCellIndexes);
            if (mutableImportHeading.indexForChild != -1) {
                indexesNeedingNames.add(mutableImportHeading.indexForChild);
            }
            if (mutableImportHeading.indexForAttribute != -1) {
                indexesNeedingNames.add(mutableImportHeading.indexForChild);
            }
        }
        for (int i = 0; i < headings.size(); i++) {
            MutableImportHeading mutableImportHeading = headings.get(i);
            mutableImportHeading.lineNameRequired = mutableImportHeading.indexForChild != -1 || !mutableImportHeading.parentNames.isEmpty() || indexesNeedingNames.contains(i) || mutableImportHeading.isAttributeSubject;
        }
    }

    // factored to make applying this to context headings easier

    private static void fillAttributeAndParentOfForHeading(MutableImportHeading mutableImportHeading, List<MutableImportHeading> headings) throws Exception {
        if (mutableImportHeading.attribute != null) { // && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
            String headingName = mutableImportHeading.heading;
            // so if it's Customer,Address1 we need to find customer.
            // This findHeadingIndex will look for the Customer with isAttributeSubject = true or the first one without an attribute
            // attribute won't be context
            mutableImportHeading.indexForAttribute = findMutableHeadingIndex(headingName, headings);
            if (mutableImportHeading.indexForAttribute >= 0) {
                headings.get(mutableImportHeading.indexForAttribute).isAttributeSubject = true;//it may not be true (as in found due to no attribute rather than language), in which case set it true now . . .need to consider this logic
                // so now we have an attribute subject for this name go through all columns for other headings with the same name
                // and if the attribute is NOT set then default it and set indexForAttribute to be this one. How much would this be practically used??
                // some unclear logic here, this needs refactoring
                for (MutableImportHeading heading2 : headings) {
                    //this is for the cases where the default display name is not the identifier.
                    if (heading2.heading != null && heading2.heading.equals(mutableImportHeading.heading) && heading2.attribute == null) {
                        heading2.attribute = Constants.DEFAULT_DISPLAY_NAME;
                        heading2.indexForAttribute = mutableImportHeading.indexForAttribute;
                        break;
                    }
                }
            }
        }
        // parent of being in context of this upload, if you can't find the heading throw an exception
        if (mutableImportHeading.parentOfClause != null) {
            mutableImportHeading.indexForChild = findMutableHeadingIndex(mutableImportHeading.parentOfClause, headings);
            if (mutableImportHeading.indexForChild < 0) {
                throw new Exception("error: cannot find column " + mutableImportHeading.parentOfClause + " for child of " + mutableImportHeading.heading);
            }
        }
    }

    /* Very similar to above, used for finding peer and attribute indexes. not sure of an obvious factor - just the middle lines or does this make things more confusing?
    I'd need an interface between Mutable and Immutable import heading, quite a few more lines of code to reduce this slightly.
    Notable that this function now WON'T find a context heading. This is fine.*/

    private static int findMutableHeadingIndex(String nameToFind, List<MutableImportHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        nameToFind = nameToFind.trim();
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().startsWith(nameToFind.toLowerCase() + ","))
                    && (heading.isAttributeSubject || heading.attribute == null || heading.isDate)) {
                if (heading.isAttributeSubject) {
                    return headingNo;
                }
                if (headingFound == -1) {
                    headingFound = headingNo;
                } else {
                    return -1;//too many possibilities
                }
            }
        }
        return headingFound;
    }
}