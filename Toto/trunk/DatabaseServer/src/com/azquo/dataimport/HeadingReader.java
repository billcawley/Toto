package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;

import java.time.LocalDateTime;
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

    // allows extra (typically composite) headings to be added when using the lookup heading by attribute process in preProcessHeadersAndCreatePivotSetsIfRequired
    private static final String COMPOSITEHEADINGS = "COMPOSITEHEADINGS";

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
    static final String PEERS = "peers";
    static final String LOCAL = "local";
    /*
    COMPOSITION  <phrase with column heading names enclosed in ``>
    e.g   COMPOSITION  `Name`, `address` Tel No: `Telephone No`

    NOTE the values ofthe fields will be the final values - some fields using 'language' 'dictionary' or 'lookup' will find names, and it is the default value of those names which is used in 'composition'
    */

    static final String COMPOSITION = "composition";
    static final String CLASSIFICATION = "classification";
    static final String DEFAULT = "default";
    private static final String OVERRIDE = "override";
    static final String NONZERO = "nonzero";
    static final String REMOVESPACES = "removespaces";
    private static final String REQUIRED = "required";
    private static final String CLEARDATA = "cleardata";
    static final String DATELANG = "date";
    private static final String USDATELANG = "us date";
    static final String ONLY = "only";
    static final String TOPHEADING = "topheading";
    static final String IGNORE = "ignore";
    static final String EXCLUSIVE = "exclusive";
    static final String CLEAR = "clear";
    static final String COMMENT = "comment";
     static final String EXISTING = "existing"; // only works in in context of child of
    // essentially using either of these keywords switches to pivot mode (like an Excel pivot) where a name is created
    // from the line number and in a set called the name of the file, uploading successive files with the same name would of course cause problems for this system, data should be cleared before re uploading
    static final String LINEHEADING = "lineheading";//lineheading and linedata are shortcuts for data destined for a pivot table, they are replaced before parsing starts properly
    static final String LINEDATA = "linedata";
    static final String SPLIT = "split";
    static final String TOPLINE = "topline";
    static final String CHECK = "check";
    static final String REPLACE = "replace";

    /*DICTIONARY finds a name based on the string value of the cell.  The system will search all names for the attribute given by the 'dictionary' term.  For instance if the phrase is 'dictionary complaint terms'
    the system will look through all the attributes 'complaint terms' to see if any match the value of this cell.
    the 'terms' consist of words or phrases separated by '+','-' or ','.   ',' means  'or'  '+' means 'and' and '-' means 'and not'
    e.g      'car, bus, van + accident - sunday,saturday' would find any phrase containg 'car' or 'bus' or 'van' AND 'accident' but NOT containing 'saturday' or 'sunday'
    DICTIONARY can be used in conjunction with the set 'SYNONYMS`.  The elements of 'Synonyms` are names witth an attriubte 'sysnonyms'.  The attribute gives a comma-separated list of synonyms.
    e.g  if an element of 'Synonyms' is 'car'    then 'car' may have an attribute 'synonyms' consisting of 'motor, auto, vehicle'  which DICTIONARY  would consider to mean the same as 'car'
     */
    static final String DICTIONARY = "dictionary";
    /*
    LOOKUP FROM  `<start attribute>` {TO `<end attribute>`}
    used in conjunction with 'child of' or 'classification'

    used where you are not looking for an exact match, but for a name with the given attribute which falls within the range.

    e.g. there might be a value for 'Weight' which you wish to categorise into 'light'  (Min weight = 0, Max weight = 10) and 'heavy' (Min weight = 10, max weight = 1000)
    'classification weight categories;lookup from `min weight` to `max weight`

    the system will find the first element of the set which satisfies the requirements.   'TO' is optional



     */
    static final String LOOKUP = "lookup";

    private static final int FINDATTRIBUTECOLUMN = -2;

    /*
    <heading> classification <other heading>
        =  parent of <other heading>;child of <heading>
todo - add classification here
     */

    // Manages the context being assigned automatically to subsequent headers. Aside from that calls other functions to
    // produce a finished set of ImmutableImportHeadings to be used by the BatchImporter.
    static void readHeaders(ValuesImportConfig valuesImportConfig) throws Exception{
        boolean clearData = valuesImportConfig.getClearData();
         AzquoMemoryDBConnection azquoMemoryDBConnection = valuesImportConfig.getAzquoMemoryDBConnection();
        List<String>attributeNames = valuesImportConfig.getLanguages();
        List<MutableImportHeading> headings = new ArrayList<>();
        List<MutableImportHeading> contextHeadings = new ArrayList<>();
        for (String header : valuesImportConfig.getHeaders()) {
            // on some spreadsheets, pseudo headers are created because there is text in more than one line.  The divider must also be accompanied by a 'peers' clause
            int dividerPos = header.lastIndexOf(headingDivider); // is there context defined here?
            if (header.trim().length() > 0 && (dividerPos < 0 || header.toLowerCase().indexOf(PEERS) > 0)) { // miss out blanks also.
                // works backwards simply for convenience to chop off the context headings until only the heading is left, there is nothing significant about the ordering in contextHeadings
                if (dividerPos > 0 || header.indexOf(";") > 0) {//any further clauses void context headings
                    contextHeadings = new ArrayList<>(); // reset/build the context headings
                }
                while (dividerPos >= 0) {
                    final String contextHeadingString = header.substring(dividerPos + 1);
                    // if the heading ends with | the context heading will be blank, ignore it. A way to clear context if you just put a single | at the end of a heading
                    if (contextHeadingString.length() > 0) {
                        // context headings may not use many features but using the standard heading objects and interpreter is fine
                        MutableImportHeading contextHeading = interpretHeading(azquoMemoryDBConnection, contextHeadingString, attributeNames, clearData);
                        if (contextHeading.attribute != null) {
                            attributeNames.add(contextHeading.attribute);//not sure when this should be removed.  It must apply until the context is changed THIS IS AN ADDITIONAL LANGUAGE USED TO UNDERSTAND THE HEADERS - NOT THE DATA!
                        }
                        contextHeadings.add(contextHeading);
                    }
                    header = header.substring(0, dividerPos);
                    dividerPos = header.lastIndexOf(headingDivider);
                }
                final MutableImportHeading heading = interpretHeading(azquoMemoryDBConnection, header, attributeNames, clearData);
                heading.contextHeadings = contextHeadings;
                headings.add(heading);
            } else {// add an empty one, the headings ArrayList should match the number of headings even if that heading is empty
                MutableImportHeading newHeading =new MutableImportHeading();
                newHeading.clearData = clearData;
                headings.add(newHeading);
             }
        }
        // further processing of the Mutable headings - the bits where headings interact with each other
        resolvePeersAttributesAndParentOf(azquoMemoryDBConnection, headings);
        // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
        valuesImportConfig.setHeadings(headings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList()));
    }

    /* deal with attribute short hand and pivot stuff, essentially pre processing that can be done before making any MutableImportHeadings
    a recent modification is indexing composite headings in advance - required for speed and because headers might be referenced by more than one name
    as part of the Ed Broking functionality, One insurer might say "Policy No." the other "Policy Number". Using this Ed Broking Logic
    on the way into this function the headers are as they will have been from the file it's here that, given a prepared structure,
    these file name headings can be matched back to the names that define headings. As in the headings will be replaced,
    resulting the indexing for composite issue described above
    resulting the indexing for composite issue described above

    still too complex for INtellij to analyse - todo
    */

    static void preProcessHeadersAndCreatePivotSetsIfRequired(ValuesImportConfig valuesImportConfig) throws  Exception{
        /*             valuesImportConfig.getAzquoMemoryDBConnection(),
                             valuesImportConfig.getFileName(),

*/
        // option for extra composite headings - I think for PwC, a little odd but harmless.
            AzquoMemoryDBConnection azquoMemoryDBConnection = valuesImportConfig.getAzquoMemoryDBConnection();
            Name importInterpreter = valuesImportConfig.getImportInterpreter();
            List<String> headers = valuesImportConfig.getHeaders();
            if (importInterpreter != null && importInterpreter.getAttribute(COMPOSITEHEADINGS) != null) {
            List<String> extraCompositeHeadings = Arrays.asList(importInterpreter.getAttribute(COMPOSITEHEADINGS).split("¬")); // delimiter match the other headings string
            headers.addAll(extraCompositeHeadings);
        }

        String importAttribute = null;
        if (importInterpreter != null) {
            importAttribute = importInterpreter.getDefaultDisplayName().replace("DATAIMPORT", "HEADINGS"); // this again, factor - todo
        }

        List<String> origHeaders = new ArrayList<>(headers);
        String lastHeading = "";
        boolean pivot = false;
        // a composite heading will refer to headings surrounded by sql like quotes e.g. `heading` `another heading`, this will simply be replaced with `1` `2` etc.
        // The reason this function was called twice is that a composition command can reference either the name on the actual file OR the name of the child name
        // it would be nice if this could be called once but I know why it is not. todo : fix
        headers = replaceFieldNamesWithNumbersInCompositeHeadings(origHeaders, headers);
        boolean headersReplaced = true;
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header.trim().length() > 0) {
                // stored header overrides one on the file
                if (importInterpreter != null) {
                    if (importInterpreter.getAttribute(header) != null) {
                        header = importInterpreter.getAttribute(header);
                    } else if (importInterpreter.getChildren() != null && importInterpreter.getChildren().size() > 0) {//PROCESS FOR ZIP FILE
                        headersReplaced = false;
                        char c = 10;
                        List<String> languages = valuesImportConfig.getLanguages();
                        header = header.replace("\\\\n",c + "");
                        // given preparation we should be able to find a name with this header in the correct language
                        Name headerName = NameService.findByName(azquoMemoryDBConnection, header, languages);
                        //amendment to catch cases where there happens to be a heading with the given name, but we don't want it
                        String localImportAttribute = importAttribute + " " + languages.get(0);
                        if (headerName != null && (headerName.getAttribute(localImportAttribute)== null ||  !headerName.getAttribute(localImportAttribute).toLowerCase().equals("ignore"))) {
                        /*

                        Ok examining the risk database makes this clearer. There's "Transaction Type" under "Data Import Risk"
                        When it was first put into the DB the attribute RLD had "<Policy Type> language NEWRENEWAL" which got broken into

                        RLD: Policy Type
                        HEADINGS RISK RLD: language RENEWAL

                        there is also

                        HEADINGS RISK: classification Policy Reference; required

                        The header is overwritten with "Transaction Type" then a combination of HEADINGS RISK and HEADINGS RISK RLD
                        are grabbed making "classification Policy Reference; required; language RENEWAL"

                        "Transaction Type"
                         unless it contains "." in which case replace the header with the combined created attribute
                         that "." business might come from HEADINGS RISK e.g. "Policy Reference.DateReceived;default NOW" in the name "Date Received"

                         A note about "<Policy Type> language NEWRENEWAL", it's a little confusing as the beginning isn't definition, it's how to look
                         the column up. The latter is clauses that will be jammed on the end for this variant (e.g. HISCOX or RLD)

                         Since the heading (e.g. Date Received) can be mangled we do want to know what the original header is for composition purposes

                         THis is very much Ed broking stuff, I'd like to put it in the EdBrokingExtension class but I'm not sure how to factor right now
                         */
                            String topHeadingFound = valuesImportConfig.getTopHeadings().get(header);
                            header = headerName.getDefaultDisplayName();
                            origHeaders.set(i, header);
                            String attribute = getCompositeAttributes(headerName, importAttribute, localImportAttribute);
                            if (topHeadingFound!=null){
                                attribute+=";default " + topHeadingFound;
                            }
                            if (attribute != null) {
                                header = attribute;
                                /*
                                if (attribute.contains(".")) {
                                    header = attribute;
                                } else {
                                    header = header + ";" + attribute;
                                }
                                */
                            }
                        } else {
                            header = "";
                        }
                    }

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
//                header = header.replace("IMPORTLANGUAGE", valuesImportConfig.getLanguages().get(0));
                header = header.replace(".", ";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                /* line heading and data
                Line heading means that the cell data on the line will be a name that is a parent of the line no
                Line data means that the cell data on the line will be a value which is attached to the line number name (for when there's not for example an order reference to be a name on the line)
                Line heading and data essentially are shorthand, this expands them and creates supporting names for the pivot if necessary.
                As the boolean shows, pivot stuff
                */
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
                    Name thisDataSet = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, importInterpreter.getDefaultDisplayName() + " data", alldataset, false);
                    // create the set the line data name will go in
                    NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), thisDataSet, false);
                    header = header.replace(LINEDATA, "peers {LINENO}").replace("_", " ");
                }
            }
            headers.set(i, header);
        }
        if (pivot) {
            Name allLines = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All lines", null, false);
            // create the name based on this file name where we put the names generated to deal with pivot tables. Note this means uploading a file with the same name and different data causes havok!
            NameService.findOrCreateNameInParent(azquoMemoryDBConnection, importInterpreter.getDefaultDisplayName() + " lines", allLines, false);
            headers.add("LINENO;composition LINENO;language " + importInterpreter.getDefaultDisplayName() + ";child of " + importInterpreter.getDefaultDisplayName() + " lines|"); // pipe on the end, clear context if there was any
        }

        if (!headersReplaced){
            valuesImportConfig.setHeaders(replaceFieldNamesWithNumbersInCompositeHeadings(origHeaders, headers));
         }else{
            valuesImportConfig.setHeaders(headers);
        }
     }

    // headings replaced by WFC into indexes as the headings were changed by the new Ed Broking children of the data import name functionality
    // also headings shouldn't be resolved on every line when dealing with composite
    // The first time this is called it doens't really need headers and original headers as they're the same but the second time
    // the headers might have been changed so we need the originals for lookup
    // I'm keen to try to workout how to stop this being called multiple times - just once won't do if we want flexible composition names

    private static List<String> replaceFieldNamesWithNumbersInCompositeHeadings(List<String> origHeaders, List<String> headers) {
        List<String> headerNames = new ArrayList<>();
        for (String header : origHeaders) {
            String[] clauses = header.split(";");
            headerNames.add(clauses[0].toLowerCase().trim());
        }
        int headerNo = 0;
        for (String header : headers) {
            StringBuilder newHeader = new StringBuilder();
            String[] clauses = header.split(";");
            boolean adjusted = false;
            for (String clause : clauses) {
                if (clause.toLowerCase().startsWith(COMPOSITION)) {
                    int startFieldPos = clause.indexOf("`");
                    while (startFieldPos > 0) {
                        int endFieldPos = clause.indexOf("`", ++startFieldPos);
                        if (endFieldPos < 0) {
                            startFieldPos = endFieldPos;
                        } else {
                            String component = clause.substring(startFieldPos, endFieldPos);
                            String[] functions = {"left","middle","right"};
                            boolean hasFunction = false;
                            for (String function:functions){
                                if (component.toLowerCase().startsWith(function + "(")){
                                    int commaPos = component.indexOf(",",function.length() + 1);
                                    if (commaPos > 0){
                                        String element = component.substring(function.length() + 1,commaPos).toLowerCase();
                                        if (headerNames.contains(element)){
                                            String fieldNo = headerNames.indexOf(element) + "";
                                            clause = clause.substring(0,startFieldPos) + component.substring(0,function.length() + 1) + fieldNo + component.substring(commaPos) + clause.substring(endFieldPos);
                                            hasFunction = true;
                                            adjusted = true;
                                            startFieldPos = startFieldPos + function.length() + 1 + fieldNo.length();
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!hasFunction && headerNames.contains(component.toLowerCase())) {
                                String fieldNo = headerNames.indexOf(component.toLowerCase()) + "";
                                clause = clause.replace("`" + component + "`", "`" + fieldNo + "`"); // EFC added the quotes or "field" counld interfere with "anoterfield"
                                adjusted = true;
                                startFieldPos += fieldNo.length() + 1;
                            } else {
                                startFieldPos = endFieldPos + 1;
                            }
                            startFieldPos = clause.indexOf("`", startFieldPos);
                        }
                    }

                }
                newHeader.append(clause).append(";");
            }
            if (adjusted) {
                headers.set(headerNo, newHeader.substring(0, newHeader.length() - 1));
            }
            headerNo++;
        }
        return headers;
    }

    //headings are clauses separated by semicolons, first is the heading name then onto the extra stuff
    //essentially parsing through all the relevant things in a heading to populate a MutableImportHeading
    private static MutableImportHeading interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, List<String> attributeNames, boolean clearData) throws Exception {
        MutableImportHeading heading = new MutableImportHeading();
        heading.clearData = clearData;
        List<String> clauses = new ArrayList<>(Arrays.asList(headingString.split(";")));
        Iterator clauseIt = clauses.iterator();
          heading.heading = ((String) clauseIt.next()).replace(StringLiterals.QUOTE + "", ""); // the heading name being the first
        try {
            //WFC - I do not understand why we're trying to set up a name for an attribute!
            heading.name = NameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames); // at this stage, look for a name, but don't create it unless necessary
        }catch(Exception e){

        }
        // loop over the clauses making sense and modifying the heading object as you go

        while (clauseIt.hasNext()) {
            String clause = ((String) clauseIt.next()).trim();
            // classification just being shorthand. According to this code it needs to be the first of the clauses
            // should classificaltion go inside interpretClause?
            if (clause.toLowerCase().startsWith(CLASSIFICATION)) {
                interpretClause(azquoMemoryDBConnection, heading, "parent of " + clause.substring(CLASSIFICATION.length()));
                interpretClause(azquoMemoryDBConnection, heading, "child of " + heading.heading);
                interpretClause(azquoMemoryDBConnection,heading, "exclusive");
            } else {
                interpretClause(azquoMemoryDBConnection, heading, clause);
            }
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
    Called multiple times per header. I assume clause is trimmed!  Simple initial parsing, greater resolution happens
    in resolvePeersAttributesAndParentOf where relationships between headings are handled*/
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
        if (clause.length() == firstWord.length()
                && !firstWord.equals(TOPHEADING)
                && !firstWord.equals(COMPOSITION)
                && !firstWord.equals(LOCAL)
                && !firstWord.equals(REQUIRED)
                && !firstWord.equals(NONZERO)
                && !firstWord.equals(REMOVESPACES)
                && !firstWord.equals(EXCLUSIVE)
                && !firstWord.equals(CLEAR)
                && !firstWord.equals(TOPLINE)
                && !firstWord.equals(REPLACE)
                && !firstWord.equals(EXISTING)) { // empty clause, exception unless one which allows blank
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
                if (result.equalsIgnoreCase(DATELANG) || result.equalsIgnoreCase(USDATELANG)) {
                    if (result.equalsIgnoreCase(DATELANG)) {
                        heading.dateForm = StringLiterals.UKDATE;
                    } else {
                        heading.dateForm = StringLiterals.USDATE;
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
                if (heading.attribute == null || heading.dateForm == 0) {
                    heading.attribute = result;
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
            case COMPOSITION:// combine more than one column
                heading.compositionPattern = result;
                break;
            case IGNORE:
                heading.ignoreList = new ArrayList<>();
                String[] ignores = result.split(",");
                for (int i=0;i< ignores.length;i++){
                    String ignoreItem = ignores[i].toLowerCase().trim();
                    if (ignoreItem.equals("{blank")){
                        heading.ignoreList.add("");
                        heading.ignoreList.add("0");
                    }else{
                        heading.ignoreList.add(ignoreItem);
                    }
                }
                break;
            case SPLIT:// character to use to split the line values of this column, so that if referring to a name instead it's a list of names
                heading.splitChar = result.trim();
                break;
            case COMMENT: // ignore
                break;
            case DEFAULT: // if there's no value on the line a default
                if (result.length() > 0) {
                    if (result.equals("NOW")) {
                        heading.defaultValue = LocalDateTime.now() + "";
                    } else {
                        heading.defaultValue = result;
                    }
                }
                break;
            case OVERRIDE: // if there's no value on the line a default
                if (result.length() > 0) {
                    if (result.equals("NOW")) {
                        heading.override = LocalDateTime.now() + "";
                    } else {
                        heading.override = result;
                    }
                    heading.defaultValue = heading.override;
                }
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
            case LOCAL:  // local names in child of, can work with parent of but then it's the subject that it affects
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
            case CLEARDATA:
                heading.clearData = true;
                break;
            case EXCLUSIVE:
                heading.exclusive = result;
                break;
            case EXISTING: // currently simply a boolean that can work with childof
                heading.existing = true;
                break;
            case CLEAR:
                if (heading.parentNames != null) {
                    for (Name name : heading.parentNames) {
                        name.setChildrenWillBePersisted(Collections.emptyList());
                    }
                }
                break;
            case TOPHEADING:
                //used elsewhere
                break;

            case TOPLINE:
                //used elsewhere
                break;
            case DICTIONARY:
                if (heading.parentNames==null || heading.parentNames.size()==0){
                    throw new Exception("dictionary terms must specify the parent first, heading " + heading.heading);

                }
                Name parent = heading.parentNames.iterator().next();
                heading.dictionaryMap = new LinkedHashMap<>();
                for (Name name:parent.getChildren()) {
                    String term = name.getAttribute(result);
                    if (term!=null) {
                        List<DictionaryTerm> dictionaryTerms = new ArrayList<>();
                        boolean exclude = false;
                        while (term.length() > 0) {
                            if(term.startsWith("{")) {
                                int endSet = term.indexOf("}");
                                if (endSet < 0) break;
                                String stringList = term.substring(1, endSet);
                                dictionaryTerms.add(new DictionaryTerm(exclude, (List<String>) Arrays.asList(stringList.split(","))));
                                term = term.substring(endSet + 1).trim();
                            }else{
                                int plusPos = (term + "+").indexOf("+");
                                int minusPos = (term + "-").indexOf("-");
                                int termEnd = plusPos;
                                if (minusPos < plusPos) termEnd = minusPos;
                                dictionaryTerms.add(new DictionaryTerm(exclude, (List<String>) Arrays.asList(term.substring(0,termEnd).split(","))));
                                if (termEnd == term.length()){
                                    term = "";
                                }else{
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
                Name synonymList = NameService.findByName(azquoMemoryDBConnection,"synonyms");

                if (synonymList!=null){
                    heading.synonyms = new HashMap<>();
                    for (Name synonym:synonymList.getChildren()){
                        String synonyms = synonym.getAttribute("synonyms");
                        if (synonyms!=null){
                            heading.synonyms.put(synonym.getDefaultDisplayName(),Arrays.asList(synonyms.split(",")));
                        }
                    }
                }

                heading.exclusive = "";
                break;
            case LOOKUP:
                if (!result.toLowerCase().startsWith("from ") || result.indexOf("`",6) < 0){
                    throw new Exception("lookup FROM `<attribute`");
                }
                result = result.substring(6);
                int endFrom = result.indexOf("`");
                heading.lookupFrom = result.substring(0,endFrom);
                result = result.substring(endFrom + 1);
                if (result.toLowerCase().contains("to")){
                    int startTo = result.indexOf("to") + 2;
                    startTo = result.indexOf("`", startTo);
                    if (startTo < 0 || result.indexOf("`", startTo + 1)<0){
                        throw new Exception ("lookup FROM `attribute` TO `attribute`");
                    }
                    heading.lookupTo = result.substring(startTo + 1, result.indexOf("`", startTo + 1)).trim();
                }


                break;
            case CHECK:
                String[] checks = result.split(";");
               for (String check:checks){
                   boolean ok = false;
                   check = check.toLowerCase().trim();
                   if (check.startsWith("letters ")){
                        String letterCheck = check.substring(7).trim();

                        while (letterCheck.length() > 0 && (letterCheck.startsWith(">") || letterCheck.startsWith("=") || letterCheck.startsWith("<"))){
                            ok = true;
                            letterCheck = letterCheck.substring(1);
                        }
                        if (ok) {
                            try {
                                int i = Integer.parseInt(letterCheck.trim());

                            } catch (Exception e){
                                ok = false;
                            }
                        }
                   }else{
                       if (check.equals("number")){
                           ok = true;
                       }
                   }
                   if (!ok){
                       throw new Exception("heading "+ heading.heading + " has unknown check " + check);
                   }
                }
                heading.checkList = result;
            case REPLACE:
                heading.replace = true;
                break;
            default:
                throw new Exception(firstWord + " not understood in heading '" + heading.heading + "'");
        }
    }

    /* Fill header information that is interdependent, so called after resolving individual headings as much as possible */

    private static void resolvePeersAttributesAndParentOf(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        // while looping collect column indexes that indicate that the cell value in that column needs to be resolved to a name
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
                // Resolve any peers in headers
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
                        // Context only really works with name in the heading otherwise how would the context differ over different headings, hence make the main heading name if it's not there
                        if (mutableImportHeading.name == null) {
                            mutableImportHeading.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, mutableImportHeading.heading, null, false);
                        }
                        if (!mutableImportHeading.peerNames.isEmpty() || !mutableImportHeading.peerIndexes.isEmpty()) {
                            throw new Exception("context peers trying to overwrite normal heading peers " + mutableImportHeading.name.getDefaultDisplayName() + ", heading " + contextHeading.heading);
                        }
                        resolvePeers(mutableImportHeading, contextHeading, headings);
                    }
                }
                // Resolve Attributes. Having an attribute means the content of this column relates to a name in another column,
                // need to find that column's index. Fairly simple stuff, it's using findMutableHeadingIndex to find the subject of attributes and parents
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
                        headings.get(mutableImportHeading.indexForChild).localParentIndexes.add(headingNo);
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
            mutableImportHeading.lineNameRequired = mutableImportHeading.indexForChild != -1 || !mutableImportHeading.parentNames.isEmpty() || indexesNeedingNames.contains(i) || mutableImportHeading.isAttributeSubject;
        }
    }

    /* Updated 31 Aug 2017. Now doens't look up heading names by name, just columns. Gather the heading name and all context names as peers
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
                    && (heading.isAttributeSubject || heading.attribute == null || ((heading.attribute.toLowerCase().equals(USDATELANG) || heading.attribute.toLowerCase().equals(DATELANG)) && heading.dateForm > 0))) {
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

    static String getCompositeAttributes(Name name, String attributeName1, String attributeName2) {
        String attribute1 = name.getAttribute(attributeName1);
        String attribute2 = name.getAttribute(attributeName2);
        if (attribute1 == null) return attribute2;
        if (attribute2 == null) return attribute1;
        return attribute1 + ";" + attribute2;
    }
}