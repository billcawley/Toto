package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by edward on 10/09/16.
 * <p>
 * This class resolves the headers on a data import file. These headers along with lines of data are passed to the BatchImporter.
 * <p>
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

    How these are used is described in more detail in MutableImportHeading and the clause interpreter.
     */

    private static final String CHILDOF = "child of "; // trailing space I suppose one could otherwise get a false "child ofweryhwrs" match which can't happen with the others
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
    // essentially using either of these keywords switches to pivot mode (like an Excel pivot) where a name is created
    // from the line number and in a set called the name of the file, uploading successive files with the same name would of course cause problems for this system, data should be cleared before re uploading
    private static final String LINEHEADING = "lineheading";//lineheading and linedata are shortcuts for data destined for a pivot table, they are replaced before parsing starts properly
    private static final String LINEDATA = "linedata";

    // Manages the context being assigned automatically to subsequent headers. Aside from that calls other functions to
    // produce a finished set of ImmutableImportHeadings to be used by the BatchImporter.
    static List<ImmutableImportHeading> readHeaders(AzquoMemoryDBConnection azquoMemoryDBConnection, String[] headers, List<String> attributeNames) throws Exception {
        List<MutableImportHeading> headings = new ArrayList<>();
        List<MutableImportHeading> contextHeadings = new ArrayList<>();
        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                int dividerPos = header.lastIndexOf(headingDivider); // is there context defined here?
                // works backwards simply for convenience to chop off the context headings until only the heading is left, there is nothing significant about the ordering in contextHeadings
                contextHeadings = new ArrayList<>(); // reset/build the context headings
                while (dividerPos >= 0) {
                     final String contextHeadingString = header.substring(dividerPos + 1);
                    // if the heading ends with | the context heading will be blank, ignore it. A way to clear context if you just put a single | at the end of a heading
                    if (contextHeadingString.length() > 0) {
                        // context headings may not use many features but using the standard heading objects and interpreter is fine
                        contextHeadings.add(interpretHeading(azquoMemoryDBConnection, contextHeadingString, attributeNames));
                    }
                    header = header.substring(0, dividerPos);
                    dividerPos = header.lastIndexOf(headingDivider);
                }
                final MutableImportHeading heading = interpretHeading(azquoMemoryDBConnection, header, attributeNames);
                heading.contextHeadings = contextHeadings;
                headings.add(heading);
            } else {// add an empty one, the headings ArrayList should match the number of headings even if that heading is empty
                headings.add(new MutableImportHeading());
            }
        }
        // further processing of the Mutable headings - the bits where headings interact with each other
        resolvePeersAttributesAndParentOf(azquoMemoryDBConnection, headings);
        // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
        return headings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList());
    }

    // deal with attribute short hand and pivot stuff, essentially pre processing that can be done before making any MutableImportHeadings

    static String[] preProcessHeadersAndCreatePivotSetsIfRequired(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> attributeNames, String[] headers, String importInterpreterLookup, String fileName) throws Exception {
        //  if the file is of type (e.g.) 'sales' and there is a name 'dataimport sales', this is used as an interpreter. Attributes with the header's name override the header.
        Name importInterpreter = NameService.findByName(azquoMemoryDBConnection, "dataimport " + importInterpreterLookup, attributeNames);
        String lastHeading = "";
        boolean pivot = false;
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i];
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                // stored header overrides one on the file
                if (importInterpreter != null && importInterpreter.getAttribute(header) != null) {
                    header = importInterpreter.getAttribute(header);
                }
                // attribute headings can start with . shorthand for the last heading followed by .
                // of course starting the first header with a . causes a problem here but it would make no sense to do so!
                if (header.startsWith(".")) {
                    header = lastHeading + header;
                } else {
                    if (header.contains(";")) {
                        lastHeading = header.substring(0, header.indexOf(";"));
                    } else {
                        lastHeading = header;
                    }
                }
                int fileNamePos = header.toLowerCase().indexOf("right(filename,");
                if (fileNamePos > 0) {
                    //extract this part of the file name.  This is currently limited to this format
                    int functionEnd = header.indexOf(")", fileNamePos);
                    if (functionEnd > 0) {
                        try {
                            int len = Integer.parseInt(header.substring(fileNamePos + "right(filename,".length(), functionEnd));
                            String replacement = "";
                            if (fileName.contains(".")) {// hack aagh todo
                                fileName = fileName.substring(0, fileName.lastIndexOf("."));
                            }
                            if (len < fileName.length()) {
                                replacement = fileName.substring(fileName.length() - len);
                            }
                            header = header.replace(header.substring(fileNamePos - 1, functionEnd + 2), replacement);// accomodating the quote marks
                        } catch (Exception ignored) {
                        }
                    }
                }
                header = header.replace(".", ";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                /* line heading and data
                Line heading means that the cell data on the line will be a name that is a parent of the line no
                Line data means that the cell data on the line will be a value which is attached to the line number name (for when there's not for example an order reference to be a name on the line)
                Line heading and data essentially are shorthand, this expands them and creates supporting names for the pivot if necessary.  */
                if (header.contains(LINEHEADING) && header.indexOf(";") > 0) {
                    pivot = true;
                    String headname = header.substring(0, header.indexOf(";"));
                    Name headset = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All headings", null, false);
                    // create the set the line heading name will go in
                    // note - headings in different import files will be considered the same if they have the same name
                    NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), headset, true);
                    header = header.replace(LINEHEADING, "parent of LINENO;child of " + headname.replace("_", " ") + ";language " + headname);
                }
                if (header.contains(LINEDATA) && header.indexOf(";") > 0) {
                    pivot = true;
                    String headname = header.substring(0, header.indexOf(";"));
                    Name alldataset = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All data", null, false);
                    Name thisDataSet = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, importInterpreterLookup + " data", alldataset, false);
                    // create the set the line data name will go in
                    NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), thisDataSet, false);
                    header = header.replace(LINEDATA, "peers {LINENO}").replace("_", " ");
                }
            }
            headers[i] = header;
        }
        if (pivot) {
            Name allLines = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All lines", null, false);
            // create the name based on this file name where we put the names generated to deal with pivot tables. Note this means uploading a file with the same name and different data causes havok!
            NameService.findOrCreateNameInParent(azquoMemoryDBConnection, importInterpreterLookup + " lines", allLines, false);
            // need to add a new header, hence new array. A bit clunky but of course this happens inside ArrayList all the time. We're using arrays here as it's what split and the line iterator return.
            String[] toReturn = new String[headers.length + 1];
            System.arraycopy(headers, 0, toReturn, 0, headers.length);
            toReturn[headers.length] = "LINENO;composition LINENO;language " + importInterpreterLookup + ";child of " + importInterpreterLookup + " lines|"; // pipe on the end, clear context if there was any
            return toReturn;
        } else {
            return headers;
        }
    }

    //headings are clauses separated by semicolons, first is the heading name then onto the extra stuff
    //essentially parsing through all the relevant things in a heading to populate a MutableImportHeading
    private static MutableImportHeading interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, List<String> attributeNames) throws Exception {
        MutableImportHeading heading = new MutableImportHeading();
        String[] clauses = headingString.split(";");
        heading.heading = clauses[0].replace(StringLiterals.QUOTE + "", ""); // the heading name being the first
        heading.name = NameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames); // at this stage, look for a name, but don't create it unless necessary
        // loop over the clauses making sense and modifying the heading object as you go
        for (int i = 1; i < clauses.length; i++) {
            interpretClause(azquoMemoryDBConnection, heading, clauses[i].trim());
        }
        // exclusive error checks
        if ("".equals(heading.exclusive) && heading.parentNames.isEmpty()) { // then exclusive what is the name exclusive of?
            throw new Exception("blank exclusive and no \"child of\" clause in " + heading.heading + " in headings"); // other clauses cannot be blank!
        } else if (heading.exclusive != null && heading.parentOfClause == null) { // exclusive means nothing without parent of
            throw new Exception("exclusive and no \"parent of\" clause in " + heading.heading + " in headings");
        }
        return heading;
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
                heading.parentOfClause = result.replace(StringLiterals.QUOTE + "", "");// parent of names in the specified column
                break;
            case CHILDOF: // e.g. child of all orders, unlike above this references data in the DB
                String childOfString = result.replace(StringLiterals.QUOTE + "", "");
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
                if (heading.attribute == null || !heading.isDate) {
                    heading.attribute = result;
                }
                break;
            case ATTRIBUTE: // same as language really but .Name is special - it means default display name. Watch out for this.
                heading.attribute = result.replace(StringLiterals.QUOTE + "", "");
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
            case PEERS: // in new logic this is the only place that peers are defined in Azquo - previously they were agains the Name object
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
            case EXCLUSIVE:
                heading.exclusive = result;
                break;
            case EXISTING: // currently simply a boolean that can work with childof
                heading.existing = true;
                break;
            default:
                throw new Exception(firstWord + " not understood in headings");
        }
    }

    /* Fill header information that is interdependent */

    private static void resolvePeersAttributesAndParentOf(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        // while looping collect column indexes that indicate that the cell value in that column needs to be resolved to a name
        Set<Integer> indexesNeedingNames = new HashSet<>();
        for (MutableImportHeading mutableImportHeading : headings) {
            if (mutableImportHeading.heading != null) { // could be null in the case of an empty heading
                // Resolve context names. Context names are used by peers.
                for (MutableImportHeading contextCheck : mutableImportHeading.contextHeadings) {
                    if (contextCheck.name == null) {
                        contextCheck.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, contextCheck.heading, null, false, null); // no attributes, default display name internally
                    }
                }
                // Resolve any peers in headers
                if (mutableImportHeading.peers.size() > 0) {
                    resolvePeers(mutableImportHeading, null, headings);
                }
                // Resolve context heading peers if we have them.
                if (mutableImportHeading.contextHeadings.size() > 0) {
                    for (MutableImportHeading contextHeading : mutableImportHeading.contextHeadings) {
                        // non zero in context pushes onto the heading
                        if (contextHeading.blankZeroes) {
                            mutableImportHeading.blankZeroes = true;
                        }
                        if (!contextHeading.peers.isEmpty()) { // then try to resolve the peers! Generally context headings will feature one with peers but it's not 100%
                            // Context only really works with name in the heading otherwise how would the context differ over different headings, hence make the main heading name if it's not there
                            mutableImportHeading.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, mutableImportHeading.heading, null, false);
                            if (!mutableImportHeading.peerNames.isEmpty() || !mutableImportHeading.peerIndexes.isEmpty()) {
                                throw new Exception("error: context peers trying to overwrite normal heading peers " + mutableImportHeading.name.getDefaultDisplayName());
                            }
                            resolvePeers(mutableImportHeading, contextHeading, headings);
                        }
                    }
                }
                // Resolve Attributes. Having an attribute means the content of this column relates to a name in another column,
                // need to find that column's index. Fairly simple stuff, it's using findMutableHeadingIndex to find the subject of attributes and parents
                if (mutableImportHeading.attribute != null) {
                    // so if it's Customer,Address1 we need to find customer.
                    mutableImportHeading.indexForAttribute = findMutableHeadingIndex(mutableImportHeading.heading, headings);
                    if (mutableImportHeading.indexForAttribute < 0) {
                        throw new Exception("error: cannot find column " + mutableImportHeading.attribute + " for attribute of " + mutableImportHeading.heading);
                    }
                }
                // Resolve parent of. Parent of in the context of columns in this upload not the Azquo Name sense.
                if (mutableImportHeading.parentOfClause != null) {
                    mutableImportHeading.indexForChild = findMutableHeadingIndex(mutableImportHeading.parentOfClause, headings);
                    if (mutableImportHeading.indexForChild < 0) {
                        throw new Exception("error: cannot find column " + mutableImportHeading.parentOfClause + " for child of " + mutableImportHeading.heading);
                    }
                }
                // Mark column indexes where the line cells will be resolved to names
                indexesNeedingNames.addAll(mutableImportHeading.peerIndexes);
                if (mutableImportHeading.indexForChild != -1) {
                    indexesNeedingNames.add(mutableImportHeading.indexForChild);
                }
                if (mutableImportHeading.indexForAttribute != -1) {
                    indexesNeedingNames.add(mutableImportHeading.indexForAttribute);
                }
            }
        }
        // Finally set the line name flags. This has to be done after in a separate loop as indexesNeedingNames needs to be complete before testing each heading
        for (int i = 0; i < headings.size(); i++) {
            MutableImportHeading mutableImportHeading = headings.get(i);
            mutableImportHeading.lineNameRequired = mutableImportHeading.indexForChild != -1 || !mutableImportHeading.parentNames.isEmpty() || indexesNeedingNames.contains(i) || mutableImportHeading.isAttributeSubject;
        }
    }

    /* Supports resolving peers defined in the heading or the context. Context means the definition and main name (the one with the peers)
     comes from the context heading and there's a special case for "this" for context where it can assign the main headings name as a peer.
     since a set of context headings is spread across multiple columns "this" will change. For non context the contextHeading parameter will be null.
     Fairly simple - add the name attached to the source heading then run through the peers, try and find them by looking for another column
       that matches or in the context names. */
    private static void resolvePeers(MutableImportHeading heading, MutableImportHeading contextHeading, List<MutableImportHeading> headings) throws Exception {
        MutableImportHeading peersSource = contextHeading != null ? contextHeading : heading;
        heading.peerNames.add(peersSource.name);// ok the "defining" name with the peers.
        for (String peer : peersSource.peers) { // we assume the source has peers otherwise this function wouldn't be called
            peer = peer.trim();
            if (contextHeading != null && peer.equalsIgnoreCase("this")) { // context peers asking for the main heading's name
                // add the name from the main heading. Notable that this means that peerNames built from context can change across different columns even if the context is the same
                heading.peerNames.add(heading.name);
            } else {
                boolean found = false;
                // try peer from the headings then try context
                int peerHeadingIndex = findMutableHeadingIndex(peer, headings);
                if (peerHeadingIndex >= 0) {
                    heading.peerIndexes.add(peerHeadingIndex);
                    found = true;
                } else { // try context for a name we can resolve now rather than one that will be resolved based on the line value
                    for (MutableImportHeading contextCheck : heading.contextHeadings) {
                        if (contextCheck.name.getDefaultDisplayName().equalsIgnoreCase(peer)) {
                            heading.peerNames.add(contextCheck.name);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    throw new Exception("error: cannot find peer " + peer + " for " + peersSource.name.getDefaultDisplayName() + (contextHeading != null ? "(context source)" : ""));
                }
            }
        }
    }

    // Used for finding peer and attribute indexes
    //look for a column with identifier (isAttributeSubject), or, if not found, a column that does not specify an attribute
    private static int findMutableHeadingIndex(String nameToFind, List<MutableImportHeading> headings) {
        nameToFind = nameToFind.trim();
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null
                    && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().startsWith(nameToFind.toLowerCase() + ","))
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