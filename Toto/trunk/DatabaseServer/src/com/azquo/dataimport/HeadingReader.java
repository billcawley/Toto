package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.dao.StatisticsDAO;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.transport.UploadedFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by edward on 10/09/16.
 * <p>
 * This class resolves the headings on a data import file. These headings along with lines of data are passed to the BatchImporter.
 * <p>
 * Some bits are still a little hard to understand, as of 04/01/19 I think it could be improved a bit
 * <p>
 * <p>
 * todo - log clause usage
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

    static final String CHILDOF = "child of "; // trailing space I suppose one could otherwise get a false "child ofweryhwrs" match which can't happen with the others
    // parent of another heading (as opposed to name), would like the clause to be more explicit, as in differentiate between a name in the database and a column. This distinction still bugs me . . .
    static final String PARENTOF = "parent of ";
    static final String ATTRIBUTE = "attribute";
    static final String LANGUAGE = "language";
    static final String DATATYPE = "datatype";
    static final String PEERS = "peers";
    static final String LOCAL = "local";
    /*
    COMPOSITION  <phrase with column heading names enclosed in ``>
    e.g   COMPOSITION  `Name`, `address` Tel No: `Telephone No`

Attributes of the names in other cells can be referenced also
    */

    static final String COMPOSITION = "composition";
    static final String COMPOSITIONXL = "compositionxl";
    static final String AZEQUALS = "az=";
    // shorthand for parent of/child of/exclusive, see comments below where it's used
    static final String CLASSIFICATION = "classification";
    static final String DEFAULT = "default";
    // if there's no file heading then make composite and default ignore any data found on that line - we assume it's irrelevant or junk
    static final String NOFILEHEADING = "nofileheading";
    private static final String OVERRIDE = "override";
    static final String NONZERO = "nonzero";
    static final String REMOVESPACES = "removespaces";
    private static final String REQUIRED = "required";
    static final String DATELANG = "date";
    static final String USDATELANG = "us date";
    private static final String STRING = "string";
    private static final String NUMBER = "number";
    static final String ONLY = "only";
    static final String IGNORE = "ignore";
    static final String EXCLUSIVE = "exclusive";
    static final String CLEAR = "clear";
    static final String CLEARDATA = "cleardata"; // like the file parameter but for a column
    static final String COMMENT = "comment";
    static final String EXISTING = "existing"; // only works in in context of child of - reject the line if not existing
    static final String OPTIONAL = "optional"; // only works in in context of child of - carry on with blank if not existing
    // essentially using either of these keywords switches to pivot mode (like an Excel pivot) where a name is created
    // from the line number and in a set called the name of the file, uploading successive files with the same name would of course cause problems for this system, data should be cleared before re uploading
    static final String LINEHEADING = "lineheading";//lineheading and linedata are shortcuts for data destined for a pivot table, they are replaced before parsing starts properly
    static final String LINEDATA = "linedata";
    static final String SPLIT = "split";
    public static final String REPLACE = "replace";
    private static final String PROVISIONAL = "provisional";//used with 'parent of' to indicate that the parent child relationship should only be created if none exists already (originally for Ed Broking Premium imports)
    public static final int EXCLUSIVETOCHILDOF = -1;
    public static final int NOTEXCLUSIVE = -2;

    /*DICTIONARY finds a name based on the string value of the cell.  The system will search all names for the attribute given by the 'dictionary' term.  For instance if the phrase is 'dictionary complaint terms'
    the system will look through all the attributes 'complaint terms' to see if any match the value of this cell.
    the 'terms' consist of words or phrases separated by '+','-' or ','.   ',' means  'or'  '+' means 'and' and '-' means 'and not'
    e.g      'car, bus, van + accident - sunday,saturday' would find any phrase containing 'car' or 'bus' or 'van' AND 'accident' but NOT containing 'saturday' or 'sunday'
    DICTIONARY can be used in conjunction with the set 'SYNONYMS`.  The elements of 'Synonyms` are names with an attribute 'synonyms'.  The attribute gives a comma-separated list of synonyms.
    e.g  if an element of 'Synonyms' is 'car'    then 'car' may have an attribute 'synonyms' consisting of 'motor, auto, vehicle'  which DICTIONARY  would consider to mean the same as 'car'
    EFC - initially I wanted to move this to reports but it's actually fairly manageable. Less of a concern than lookups. Check tryToResolveNames in BatchImporter
     */
    static final String DICTIONARY = "dictionary";
    /*
    see checkLookup in BatchImporter, this can be quite involved
     */
    public static final String LOOKUP = "lookup";

    private static final int FINDATTRIBUTECOLUMN = -2;

    static Map<String, AtomicInteger> clauseCounts = new ConcurrentHashMap<>();
    // don't update the stats every time, only if not done recently
    static volatile long lastStatSave = 0;

    // Manages the context being assigned automatically to subsequent headings. Aside from that calls other functions to
    // produce a finished set of ImmutableImportHeadings to be used by the BatchImporter.
    static List<ImmutableImportHeading> readHeadings(AzquoMemoryDBConnection azquoMemoryDBConnection, UploadedFile uploadedFile, List<String> headingsAsStrings) throws Exception {
        List<String> attributeNames = new ArrayList<>(uploadedFile.getLanguages());
        List<MutableImportHeading> headings = new ArrayList<>();
        List<MutableImportHeading> contextHeadings = new ArrayList<>();
        String lastClauses = "";
        for (String headingString : headingsAsStrings) {
            String namesString = headingString;
            //avoid finding dividers in az=
            if (namesString.contains("az=")){
                namesString = namesString.substring(0,namesString.indexOf("az="));
            }
            int dividerPos = namesString.lastIndexOf(headingDivider); // is there context defined here?
            if (headingString.trim().length() > 0) { // miss out blanks also.
                if (dividerPos > 0 || headingString.indexOf(";") > 0) {//any further clauses or new contexts  void existing context headings
                    contextHeadings = new ArrayList<>(); // reset/build the context headings
                    // ok this is a slightly odd bit of logic, if there are context headings but NO clauses then grab clauses from the last column which had clauses
                    // EFC note - I need to confirm with WFC where this is actually used
                    int clausePos = headingString.indexOf(";");
                    if (clausePos > 0) {
                        lastClauses = headingString.substring(clausePos);
                    } else {
                        headingString += lastClauses;
                    }
                }
                /* context headings are mainly about peers and there are two aspects to it
                 The first is that when context headings are set they persist across subsequent headings as long as these headings don't have context headings themselves or clauses.
                 The seconds aspect is that in defining a context heading the name of that heading is added to the peers. Normally peers is the name of the column heading
                 and names looked up from the cells in other columns. You cannot add another name in the peers {} it's only other columns and the cells will be the peers BUT if you say
                 name 1|name 2 peers {col1, col 2 etc} then you've got as your peers name1, name 2 and the contents of cells in col 1 & 2 & 3 so under those circumstances it's
                 not about context persisting past the current column but rather adding in name 2 which isn't possible under the standard peers clause.
                 */


                // works backwards simply for convenience to chop off the context headings until only the heading is left, there is nothing significant about the ordering in contextHeadings
                while (dividerPos >= 0) {
                    //if there are no clauses, inherit from last
                    final String contextHeadingString = headingString.substring(dividerPos + 1);
                    // if the heading ends with | the context heading will be blank, ignore it. A way to clear context if you just put a single | at the end of a heading
                    if (contextHeadingString.length() > 0) {
                        // context headings may not use many features but using the standard heading objects and interpreter is fine
                        contextHeadings.add(interpretHeading(azquoMemoryDBConnection, contextHeadingString, attributeNames, uploadedFile.getFileNames()));
                    }
                    headingString = headingString.substring(0, dividerPos);
                    dividerPos = headingString.lastIndexOf(headingDivider);
                }

                final MutableImportHeading heading = interpretHeading(azquoMemoryDBConnection, headingString, attributeNames, uploadedFile.getFileNames());
                heading.contextHeadings = contextHeadings;
                headings.add(heading);
            } else {// add an empty one, the headings ArrayList should match the number of headings even if that heading is empty
                MutableImportHeading newHeading = new MutableImportHeading();
                headings.add(newHeading);
            }
        }
        /* further processing of the Mutable headings - the bits where headings interact with each other
         more specifically we're trying to work out attribute subjects - a language clause will set isAttributeSubject to true, aside from that
         there's this criteria - it's a date and there's no obvious other candidate
         */

        for (MutableImportHeading heading : headings) {
            if (heading.attribute != null && (heading.attribute.equalsIgnoreCase(USDATELANG) || heading.attribute.equalsIgnoreCase(DATELANG))) {
                boolean hasAttributeSubject = false;
                for (MutableImportHeading heading2 : headings) {
                    if (heading2.heading != null && heading2.heading.equalsIgnoreCase(heading.heading) && (heading2.isAttributeSubject || heading2.attribute == null)) {
                        hasAttributeSubject = true;
                        break;
                    }
                }
                if (!hasAttributeSubject) {
                    heading.isAttributeSubject = true;
                }
            }
            if (heading.lookupString != null) {
                String error = handleLookup(heading, headings);
                if (error != null) {
                    throw new Exception(error);
                }
            }
        }
        resolvePeersAttributesAndParentOf(azquoMemoryDBConnection, headings);

        if (lastStatSave < (System.currentTimeMillis() - (1_000 * 60))) { // don't update stats more than once a minute
            saveStats();
        }

        // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
        return headings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList());
    }

    // I'm assuming this will be quick. Called periodically. synchronised a good idea as even if the map is ok with this running concurrently it seems like a bad idea on the database code
    private static synchronized void saveStats() {
        lastStatSave = System.currentTimeMillis();
        for (String name : clauseCounts.keySet()) {
            StatisticsDAO.addToNumber(name, clauseCounts.get(name).get());
        }
        clauseCounts.clear();
    }


    private static String handleLookup(MutableImportHeading heading, List<MutableImportHeading> headings) {
        //the syntax is:  in '<parentheadingname>' using <condition/attribute>.
        // (note - using (') for heading names as  because we are mizing azquo names with field names here)
        String lookupString = heading.lookupString;
        String error = "could not understand lookup: " + lookupString;
        Pattern p = Pattern.compile("'[^']*'");
        Matcher m = p.matcher(lookupString);
        if (!m.find()) {
            return error;
        }
        if (lookupString.length() < 10 || !lookupString.toLowerCase().startsWith("in ")) return error;
        heading.lookupParentIndex = findMutableHeadingIndex(lookupString.substring(m.start() + 1, m.end() - 1), headings);
        if (heading.lookupParentIndex < 0) return error;
        lookupString = lookupString.substring(m.end()).trim();
        if (lookupString.length() < 6 || !lookupString.toLowerCase().startsWith("using ")) return error;
        lookupString = lookupString.substring(6).trim();
        heading.lookupString = lookupString;
        return null;
    }

    //headings are clauses separated by semicolons, first is the heading name then onto the extra stuff
    //essentially parsing through all the relevant things in a heading to populate a MutableImportHeading
    private static MutableImportHeading interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, List<String> attributeNames, List<String> fileNames) throws Exception {
        MutableImportHeading heading = new MutableImportHeading();
        List<String> clauses = new ArrayList<>(Arrays.asList(headingString.split(";")));
        Iterator<String> clauseIt = clauses.iterator();
        heading.heading = clauseIt.next().replace(StringLiterals.QUOTE + "", ""); // the heading name being the first
        try {
            //WFC - I do not understand why we're trying to set up a name for an attribute!
            heading.name = NameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames); // at this stage, look for a name, but don't create it unless necessary
        } catch (Exception ignored) {

        }
        // loop over the clauses making sense and modifying the heading object as you go

        while (clauseIt.hasNext()) {
            String clause = clauseIt.next().trim();
            // classification just being shorthand. According to this code it needs to be the first of the clauses
            // should classification go inside interpretClause?
            if (clause.toLowerCase().startsWith(CLASSIFICATION)) {
                interpretClause(azquoMemoryDBConnection, heading, "parent of " + clause.substring(CLASSIFICATION.length()), fileNames);
                interpretClause(azquoMemoryDBConnection, heading, "child of " + heading.heading, fileNames);
                interpretClause(azquoMemoryDBConnection, heading, "exclusive", fileNames);
            } else {
                if (clause.length() > 0) {
                    interpretClause(azquoMemoryDBConnection, heading, clause, fileNames);
                }
            }
        }
        // exclusive error checks
        if (heading.exclusiveIndex == EXCLUSIVETOCHILDOF && heading.parentNames.isEmpty()) { // then exclusive what is the name exclusive of?
            throw new Exception("blank exclusive and no \"child of\" clause in " + heading.heading + " in headings"); // other clauses cannot be blank!
        } else if (heading.exclusiveIndex > NOTEXCLUSIVE && heading.parentOfClause == null) { // exclusive means nothing without parent of
            throw new Exception("exclusive and no \"parent of\" clause in " + heading.heading + " in headings");
        } else { // other problematic combos
            if (heading.dictionaryMap != null && heading.localParentIndex != -1) {
                throw new Exception("Column " + heading.heading + " has a dictionary map and a local parent. This combination is not allowed.");
            }
        }
        return heading;
    }

    /* This is called for all the ; separated clauses in a heading e.g. Gender; parent of Customer; child of Genders
    Called multiple times per heading. I assume clause is trimmed!  Simple initial parsing, greater resolution happens
    in resolvePeersAttributesAndParentOf where relationships between headings are handled*/
    private static void interpretClause(final AzquoMemoryDBConnection azquoMemoryDBConnection, final MutableImportHeading heading, final String clause, List<String> fileNames) throws Exception {
        String firstWord = clause.toLowerCase(); // default, what it could legitimately be in the case of blank clauses (local, exclusive, non zero)
        // I have to add special cases for parent of and child of as they have spaces in them. A bit boring
        if (firstWord.startsWith(PARENTOF)) { // a blank won't match here as its trailing space would be trimmed but no matter, blank not allowed anyway
            firstWord = PARENTOF;
        } else if (firstWord.startsWith(CHILDOF)) {
            firstWord = CHILDOF;
        } else if (firstWord.startsWith(AZEQUALS)) {
            firstWord = AZEQUALS;
        } else if (firstWord.contains(" ")) {
            firstWord = firstWord.substring(0, firstWord.indexOf(" "));
        }
        clauseCounts.computeIfAbsent("heading - " + firstWord, t -> new AtomicInteger()).incrementAndGet();
        if (clause.length() == firstWord.length()
                && !firstWord.equals(COMPOSITION)
                && !firstWord.equals(COMPOSITIONXL)
                && !firstWord.equals(AZEQUALS)
                && !firstWord.equals(LOCAL)
                && !firstWord.equals(REQUIRED)
                && !firstWord.equals(NONZERO)
                && !firstWord.equals(REMOVESPACES)
                && !firstWord.equals(EXCLUSIVE)
                && !firstWord.equals(CLEAR)
                && !firstWord.equals(CLEARDATA)
                && !firstWord.equals(REPLACE)
                && !firstWord.equals(EXISTING)
                && !firstWord.equals(OPTIONAL)
                && !firstWord.equals(NOFILEHEADING)
                && !firstWord.equals(PROVISIONAL)) { // empty clause, exception unless one which allows blank
            throw new Exception(clause + " empty in " + heading.heading + " in headings"); // other clauses cannot be blank!
        }
        String result = clause.substring(firstWord.length()).trim();
        switch (firstWord) {
            case PARENTOF: // NOT parent of an existing name in the DB, parent of other data in the line
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
                if (result.equalsIgnoreCase(DATELANG) || result.equalsIgnoreCase(USDATELANG)) {
                    if (result.equalsIgnoreCase(DATELANG)) {
                        heading.datatype = StringLiterals.UKDATE;
                    } else {
                        heading.datatype = StringLiterals.USDATE;
                        result = DATELANG;
                    }
                    //Bill had commented these three lines, Edd uncommenting 13/07/2018 as it broke DG import
                    /*
                    if (heading.attribute == null) {
                        heading.isAttributeSubject = true;
                    }
                    */
                } else {
                    heading.isAttributeSubject = true; // language is important so we'll default it as the attribute subject if attributes are used later - I might need to check this
                }
                if (heading.attribute == null || heading.datatype == 0) {
                    heading.attribute = result;
                }
                break;
            case DATATYPE:// was just for dates but now supports number and string which is helpful to compositexl
                if (result.equalsIgnoreCase(DATELANG)) {
                    heading.datatype = StringLiterals.UKDATE;
                } else if (result.equalsIgnoreCase(USDATELANG)) {
                    heading.datatype = StringLiterals.USDATE;
                } else if (result.equalsIgnoreCase(STRING)) {
                    heading.datatype = StringLiterals.STRING;
                } else if (result.equalsIgnoreCase(NUMBER)) {
                    heading.datatype = StringLiterals.NUMBER;
                }

                break;
            case ATTRIBUTE: // same as language really but .Name is special - it means default display name. Watch out for this.
                if (result.startsWith("`") && result.endsWith(("`"))) {// indicating that the attribute NAME is to be taken from another column of the import.
                    heading.attributeColumn = FINDATTRIBUTECOLUMN;
                }
                heading.attribute = result.replace(StringLiterals.QUOTE + "", "");
                if (heading.attribute.equalsIgnoreCase("name")) {
                    heading.attribute = StringLiterals.DEFAULT_DISPLAY_NAME;
                }
                break;
            case ONLY:
                heading.only = result;
                break;
            case AZEQUALS:
            case COMPOSITIONXL:// use excel formulae
                heading.compositionXL = true;
            case DEFAULT: // default = composition in now but we'll leave the syntax support in
            case COMPOSITION:// combine more than one column
                // EFC 03/04/20 relevant for straight defaults (e.g. saying 25% for the whole column) though it does look a little hacky
                if (result.endsWith("%") && !heading.compositionXL) { // don't do the hack on XL mode
                    try {
                        double d = Double.parseDouble(result.substring(0, result.length() - 1)) / 100;
                        result = d + "";
                    } catch (Exception ignored) {
                    }
                }
                heading.compositionPattern = result.replace("FILENAME", fileNames.get(fileNames.size() - 1))
                        .replace("PARENTFILENAME", fileNames.size() > 1 ? fileNames.get(fileNames.size() - 2) : fileNames.get(fileNames.size() - 1));
                break;
            case IGNORE:
                heading.ignoreList = new ArrayList<>();
                String[] ignores = result.split(",");
                for (String ignore : ignores) {
                    String ignoreItem = ignore.toLowerCase().trim();
                    if (ignoreItem.equals("{blank}")) {
                        heading.ignoreList.add("");
                        heading.ignoreList.add("0");
                    } else {
                        heading.ignoreList.add(ignoreItem);
                    }
                }
                break;
            case SPLIT:// character to use to split the line values of this column, so that if referring to a name instead it's a list of names
                heading.splitChar = result.trim();
                break;
            case COMMENT: // ignore
                break;
            case OVERRIDE: // forces this value on the column regardless of what's there, doesn't resolve like composite
                heading.override = result;
                break;
            case PEERS: // in new logic this is the only place that peers are defined in Azquo - previously they were against the Name object
                heading.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);
                String peersString = result;
                if (peersString.startsWith("{")) { // array, typically when creating in the first place, the spreadsheet call will insert after any existing. Like children not robust to commas
                    if (peersString.contains("}")) {
                        peersString = peersString.substring(1, peersString.indexOf("}"));
                        Collections.addAll(heading.peers, peersString.split(","));
                    } else {
                        throw new Exception("Unclosed } in headings, heading " + heading.heading);
                    }
                }
                break;
            case NOFILEHEADING:  // if there's no file heading then make composite and default ignore any data found on that line - we assume it's irrelevant or junk
                heading.noFileHeading = true;
                break;
            case LOCAL:  // local names in child of, can work with parent of but then it's the subject that it affects. Note - one flag for both may need to be changed, we've hit limits in some LSB work where we want one direction but not the other
                heading.isLocal = true;
                break;
            case NONZERO: // Ignore zero values. This and local will just ignore values after e.g. "nonzero something" I see no harm in this
                heading.blankZeroes = true;
                break;
            case REMOVESPACES: // remove spaces from the cell
                heading.removeSpaces = true;
                break;
            case REQUIRED:
                heading.required = true;
                break;
            case EXCLUSIVE:// documented properly in the batch importer
                heading.exclusiveClause = result.trim();
                break;
            case EXISTING: // currently simply a boolean that can work with childof
                heading.existing = true;
                break;
            case OPTIONAL: // currently simply a boolean that can work with childof
                heading.optional = true;
                break;
            case CLEAR:
                if (heading.parentNames != null) {
                    for (Name name : heading.parentNames) {
                        name.setChildrenWillBePersisted(Collections.emptyList(), azquoMemoryDBConnection);
                    }
                }
                break;
            case CLEARDATA:
                heading.cleardata = true;
                break;
            case DICTIONARY:
                if (heading.parentNames == null || heading.parentNames.size() == 0) {
                    throw new Exception("dictionary terms must specify the parent first, heading " + heading.heading);
                }
                Name parent = heading.parentNames.iterator().next();
                heading.dictionaryMap = new LinkedHashMap<>();
                for (Name name : parent.getChildren()) {
                    String term = name.getAttribute(result);
                    if (term != null) {
                        List<ImmutableImportHeading.DictionaryTerm> dictionaryTerms = new ArrayList<>();
                        boolean exclude = false;
                        while (term.length() > 0) {
                            if (term.startsWith("{")) {
                                int endSet = term.indexOf("}");
                                if (endSet < 0) break;
                                String stringList = term.substring(1, endSet);
                                dictionaryTerms.add(new ImmutableImportHeading.DictionaryTerm(exclude, Arrays.asList(stringList.split(","))));
                                term = term.substring(endSet + 1).trim();
                            } else {
                                int plusPos = (term + "+").indexOf("+");
                                int minusPos = (term + "-").indexOf("-");
                                int termEnd = plusPos;
                                if (minusPos < plusPos) termEnd = minusPos;
                                dictionaryTerms.add(new ImmutableImportHeading.DictionaryTerm(exclude, Arrays.asList(term.substring(0, termEnd).split(","))));
                                if (termEnd == term.length()) {
                                    term = "";
                                } else {
                                    term = term.substring(termEnd);
                                }
                            }
                            if (term.startsWith("+")) {
                                exclude = false;
                                term = term.substring(1).trim();
                            } else if (term.startsWith("-")) {
                                exclude = true;
                                term = term.substring(1).trim();
                            }
                        }
                        if (dictionaryTerms.size() > 0) {
                            heading.dictionaryMap.put(name, dictionaryTerms);
                        }
                    }
                }
                Name synonymList = NameService.findByName(azquoMemoryDBConnection, "synonyms");
                if (synonymList != null) {
                    heading.synonyms = new HashMap<>();
                    for (Name synonym : synonymList.getChildren()) {
                        String synonyms = synonym.getAttribute("synonyms");
                        if (synonyms != null) {
                            heading.synonyms.put(synonym.getDefaultDisplayName(), Arrays.asList(synonyms.split(",")));
                        }
                    }
                }
                heading.exclusiveIndex = NOTEXCLUSIVE;
                break;
            case LOOKUP:
                heading.lookupString = result;
                //leave handleLookup until all headings have been read.
                break;
            case REPLACE:
                heading.replace = true;
                break;
            case PROVISIONAL:
                heading.provisional = true;
                break;
            default:
                String headingName = heading.heading;
                if (heading.attribute != null) {
                    headingName += "." + heading.attribute;
                }
                throw new Exception(firstWord + " not understood in heading '" + headingName + "'");
        }
    }

    /* Fill heading information that is interdependent, so called after resolving individual headings as much as possible */

    private static void resolvePeersAttributesAndParentOf(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        // while looping collect column indexes that indicate that the cell value in that column needs to be resolved to a name, crucial to flagging things correctly
        Set<Integer> indexesNeedingNames = new HashSet<>();
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading mutableImportHeading = headings.get(headingNo);
            if (mutableImportHeading.heading != null) { // could be null in the case of an empty heading
                // Resolve context names. Context names are used by peers.
                for (MutableImportHeading contextCheck : mutableImportHeading.contextHeadings) {
                    if (contextCheck.name == null) {
                        contextCheck.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, contextCheck.heading, null, false, null); // no attributes, default display name internally
                    }
                }
                // Resolve any peers in headings
                if (mutableImportHeading.peers.size() > 0) {
                    resolvePeers(mutableImportHeading, null, headings);
                }
                // Resolve context heading peers if we have them.
                for (MutableImportHeading contextHeading : mutableImportHeading.contextHeadings) {
                    // non zero in context pushes onto the heading
                    if (contextHeading.blankZeroes) {
                        mutableImportHeading.blankZeroes = true;
                    }
                    if (!contextHeading.peers.isEmpty()) { // then try to resolve the peers! Generally context headings will feature one with peers but it's not 100%
                        // Context only really works with name in the heading otherwise how would the context differ over different headings, hence resolve the main heading name if it's not there
                        if (mutableImportHeading.name == null) {
                            mutableImportHeading.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, mutableImportHeading.heading, null, false);
                        }
                        if (!mutableImportHeading.peerNames.isEmpty() || !mutableImportHeading.peerIndexes.isEmpty()) {
                            throw new Exception("context peers trying to overwrite normal heading peers " + mutableImportHeading.name.getDefaultDisplayName() + ", heading " + contextHeading.heading);
                        }
                        // as with non zero the context composition overrides the heading one though I don't think
                        // there would be both on one heading, I guess about flexibility in allowing composition to be
                        // put on the end as usual. If composition was put on a subsequent heading it would reset the context as any clause does
                        if (contextHeading.compositionPattern != null) {
                            mutableImportHeading.compositionPattern = contextHeading.compositionPattern;
                            mutableImportHeading.compositionXL = contextHeading.compositionXL;
                        }
                        resolvePeers(mutableImportHeading, contextHeading, headings);
                    }
                }
                // Resolve Attributes. Having an attribute means the content of this column relates to a name in another column,
                // need to find that column's index. Fairly simple stuff, it's using findMutableHeadingIndex to find the subject of attributes and parents
                // as in other places dates are treated as a special case
                if (mutableImportHeading.attribute != null && !mutableImportHeading.attribute.equalsIgnoreCase(DATELANG) && !mutableImportHeading.attribute.equalsIgnoreCase(USDATELANG)) {
                    // so if it's Customer,Address1 we need to find customer.
                    mutableImportHeading.indexForAttribute = findMutableHeadingIndex(mutableImportHeading.heading, headings);
                    if (mutableImportHeading.indexForAttribute < 0) {
                        throw new Exception("cannot find column " + mutableImportHeading.heading + " for attribute of " + mutableImportHeading.heading + "." + mutableImportHeading.attribute);
                    }
                }
                // read the attribute name from another column so will be done as lines are imported
                if (mutableImportHeading.attributeColumn == FINDATTRIBUTECOLUMN) {
                    mutableImportHeading.attributeColumn = findMutableHeadingIndex(mutableImportHeading.attribute, headings);
                    if (mutableImportHeading.attributeColumn < 0) {
                        throw new Exception("cannot find column " + mutableImportHeading.attribute + " for attribute name of " + mutableImportHeading.heading + "." + mutableImportHeading.attribute);
                    }
                }
                // Resolve parent of. Parent of in the context of columns in this upload not the Azquo Name sense.
                if (mutableImportHeading.parentOfClause != null) {
                    mutableImportHeading.indexForChild = findMutableHeadingIndex(mutableImportHeading.parentOfClause, headings);
                    if (mutableImportHeading.indexForChild < 0) {
                        throw new Exception("cannot find column " + mutableImportHeading.parentOfClause + " for child of " + mutableImportHeading.heading);
                    }
                    // see comments on localParentIndexes and in resolveLineNameParentsAndChildForCell in BatchImporter
                    // when the column this is parent of is resolved it must first resolve THIS heading if this heading is it's parent with local set
                    // locals have to be resolved in order from the top first or the structure will not be correct
                    if (mutableImportHeading.isLocal) {
                        if (headings.get(mutableImportHeading.indexForChild).localParentIndex != -1) {
                            throw new Exception("heading " + headings.get(mutableImportHeading.indexForChild).heading + " has more than one parent column marked as local");
                        }
                        headings.get(mutableImportHeading.indexForChild).localParentIndex = headingNo;
                    }
                }
                if (mutableImportHeading.exclusiveClause != null) {
                    if (mutableImportHeading.exclusiveClause.length() == 0) {
                        mutableImportHeading.exclusiveIndex = EXCLUSIVETOCHILDOF;
                    } else {
                        mutableImportHeading.exclusiveIndex = findMutableHeadingIndex(mutableImportHeading.exclusiveClause, headings);
                        if (mutableImportHeading.exclusiveIndex < 0) {
                            throw new Exception("cannot find column " + mutableImportHeading.exclusiveClause + " for exclusive column of " + mutableImportHeading.heading + "." + mutableImportHeading.attribute);
                        }
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
        // Finally set the line name flags. This has to be done after in a separate loop as indexesNeedingNames needs to be complete (it accommodates headings saying that OTHER headings need line names) before testing each heading
        for (int i = 0; i < headings.size(); i++) {
            MutableImportHeading mutableImportHeading = headings.get(i);
            mutableImportHeading.lineNameRequired = mutableImportHeading.indexForChild != -1
                    || !mutableImportHeading.parentNames.isEmpty()
                    || indexesNeedingNames.contains(i)
                    || mutableImportHeading.isAttributeSubject
                    || mutableImportHeading.dictionaryMap != null;
        }
    }

    /* Updated 31 Aug 2017. Now doesn't look up heading names by name, just columns. Gather the heading name and all context names as peers
     * Only difference if optional context is sent is that the context is the source of the peers string list */
    private static void resolvePeers(MutableImportHeading heading, MutableImportHeading contextHeading, List<MutableImportHeading> headings) throws Exception {
        MutableImportHeading peersSource = contextHeading != null ? contextHeading : heading;
        // updated logic - starter for 10 is adding this heading and all context headings to the peers list
        heading.peerNames.add(heading.name);
        for (MutableImportHeading cHeading : heading.contextHeadings) {
            heading.peerNames.add(cHeading.name);
        }
        for (String peer : peersSource.peers) { // we assume the source has peers otherwise this function wouldn't be called
            peer = peer.trim();
            // try peer from the headings then try context
            int peerHeadingIndex = findMutableHeadingIndex(peer, headings);
            if (peerHeadingIndex >= 0) {
                heading.peerIndexes.add(peerHeadingIndex);
            } else {
                throw new Exception("Cannot find peer " + peer + " for " + peersSource.name.getDefaultDisplayName() + (contextHeading != null ? "(context source)" : ""));
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
                    && (heading.isAttributeSubject || heading.attribute == null)) {
                if (heading.isAttributeSubject) {
                    return headingNo;
                }
                if (headingFound == -1) {
                    headingFound = headingNo;
                } else {
                    return -1;//found more than one revert back to -1 as it's unclear which heading we're after
                }
            }
        }
        return headingFound;
    }
}