package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by cawley on 20/05/15.
 * <p>
 * Has a fair bit of the logic that was in the original import service.
 * Note : large chunks of this were originally written by WFC and then refactored by EFC.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * <p>
 * I'm trying to write up as much of the non obvious logic as possible in comments in the code
 * <p>
 * The value on a line can be a value or an attribute or a name.
 */
public class DSImportService {

    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private DSSpreadsheetService dsSpreadsheetService;

    // to define context headings use this divider
    private static final String headingDivider = "|";

    /*
    These are heading clauses. I think heading definitions can be in the data file but Azquo is setup to support data
    "as it comes". Hence when dealing with a new set of data the key is to set up sets and headings so that the system can load the data.
    Setting up the sets and headings could be seen as similar to setting up the tables in an SQL database.

    Note : the clauses here tend to reverse the subject/object used in the code. If an object in the code has children we'll say object.children, not object.parentOf.
    This isn't a big problem and the way the clauses are set up probably makes sense in their context, I just want to note that as they are parsed naming may reverse - parentOf to children etc.
     */

    public static final String CHILDOF = "child of ";
    public static final String REMOVEFROM = "remove from ";
    // parent of another heading, would like the clause to be more explicit,
    public static final String PARENTOF = "parent of ";
    public static final String ATTRIBUTE = "attribute";
    public static final String LANGUAGE = "language";
    public static final String PEERS = "peers";
    public static final String LOCAL = "local";
    public static final String EQUALS = "equals";
    public static final String COMPOSITION = "composition";
    public static final String DEFAULT = "default";
    public static final String NONZERO = "nonzero";
    public static final String headingsString = "headings";
    public static final String dateLang = "date";

    /*
    To multi thread I wanted this to be immutable but there are things that are only set after in context of other headings so I can't
    do this initially. No problem, initially make this very simple and mutable then have an immutable version for the multi threaded stuff which is held against line.
    could of course copy all fields into line but this makes the constructor needlessly complex.

    Notably there are things calculated on every line that could perhaps be moved.

    I was thinking that indexes could instead hold the headings but they often are required as indexes when reading values off lines. Inconclusive currently.

    The logic of how indexes are held that will point to cell arrays seems a bit off but I can't see an obvious way around it - a classic example of code incrementally modified.

    I might, after I get a proper understanding, think of how I'd code from scratch,
    */
    private class MutableImportHeading {
        // column index - used to find data off each data line and also to see if a column has a header - if not this value won't be set but that's kind of inconsistent, surely heading being null would be a better check?
        // so only really needed to find the value off the line array but if headers really matched headers this would become redundant - the places in two lists would match
        int columnIndex = -1;
        // the name of the heading - often referenced by other headings e.g. parent of
        String heading = null;
        // the Azquo Name that might be set on the heading, certainly used if there are peers but otherwise may not be set if the heading is referenced by other headings
        Name name = null;
        // this class used to use the now removed peers against the name object, in its absence just put a set here.
        Set<Name> peerNames = new HashSet<>(); // empty is fine
        // The parent of clause is an internal reference - to other headings, as in what is this a parent of - need to have it here to resolve later when we have a complete headings list
        // it will be resolved into indexForAttribute
        String parentOfClause = null;
        /* the index of the heading that an attribute refers to so if the heading is Customer.Address1 then this is the index of customer.
        Note : not the same as column index this is index in the header array that might not match the source columns due to context headers.
         This is clarifying that there should probably be a better way of arranging this logic, it is not good.
        Seems only to be used with attribute, could change to a reference to the heading but it is used for data look up. */
        int indexForAttribute = -1;
        // index in the headings array of a child derived from parent of
        int indexForChild = -1;
        // derived from the "child of" clause, a comma separated list of names
        Set<Name> parentNames = null;
        // same format or logic as parentNames - now I look at this I'm a bit puzzled what it's for
        Set<Name> removeParentNames = null;
        // result of the attribute clause. Notable that "." is replaced with ;attribute
        String attribute = null;
        /* the results of the peers clause are jammed in peerNames but then we need to know which headings those peers refer to. */
        Set<Integer> indexesForPeers = new HashSet<>();
        /*if there are multiple attributes then effectively there will be multiple columns with the same "heading", define which one we're using when the heading is referenced by other headings.
         Language will trigger something as being the attribute subject, after if on searching there is only one it might be set for convenience when sorting attributes
         */
        boolean isAttributeSubject = false;
        /*when using the heading divider (a pipe at the moment) then other headers are stacked up under the same column. These extras, after the first | are applied to all subsequent columns
        the context logic is only dependant on the headings - we're validating what's there not the values on each lines */
        boolean isContextHeading = false;
        // local in the azquo sense. Affects child of and parent of clauses - the other heading is local in the case of parent of and this one in the case of child of. Local as in Azquo name logic.
        boolean isLocal = false;
        /* to make the line value a composite of other values. Syntax is pretty simple replacing anything in quotes with the referenced line value
        `a column name`-`another column name` might make 1233214-1234. Such columns would probably be at the end,
        they are virtual in the sens that these values are made on uploading they are not there in the source file though the components are.*/
        String compositionPattern = null;
        // a default value if the line value is blank
        String defaultValue = null;
        //don't import zero values;
        boolean blankZeroes = false;
        // a way for a heading to have an alias or more specifically for its name to be overridden for the purposes of how headings find each other
        // need to clarify usage of this - not able to right now. I think it's something to do with the heading.name and how it's looked up being different,
        // that's how this is different than just changing the heading's name to whatever you'd use for the alias.
        String headingAlias = null;
        // todo : add context headings here? They build up according to the headers, no need to calculate each time
    }

    // I see no reason for getters here. Class only used here. Note added later : getters and setters may make the code clearer though this could be done by better names also I think
    private class ImmutableImportHeading {
        final int columnIndex;
        final String heading;
        final Name name;
        final Set<Name> peerNames;
        final int indexForAttribute;
        final int indexForChild;
        // ok the set will be fixed, I suppose names can be modified but they should be thread safe. Well that's the plan.
        final Set<Name> parentNames;
        final Set<Name> removeParentNames;
        final String attribute;
        final Set<Integer> indexesForPeers;
        final boolean isAttributeSubject;
        final boolean isContextHeading;
        final boolean isLocal;
        final String compositionPattern;
        final String defaultValue;
        final boolean blankZeroes;
        final String headingAlias;

        public ImmutableImportHeading(MutableImportHeading mutableImportHeading) {
            this.columnIndex = mutableImportHeading.columnIndex;
            this.heading = mutableImportHeading.heading;
            this.name = mutableImportHeading.name;
            this.peerNames = mutableImportHeading.peerNames;
            this.indexForAttribute = mutableImportHeading.indexForAttribute;
            this.indexForChild = mutableImportHeading.indexForChild;
            this.parentNames = mutableImportHeading.parentNames != null ? Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.parentNames)) : null;
            this.removeParentNames = mutableImportHeading.removeParentNames != null ? Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.removeParentNames)) : null;
            this.attribute = mutableImportHeading.attribute;
            this.indexesForPeers = mutableImportHeading.indexesForPeers != null ? Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.indexesForPeers)) : null;
            this.isAttributeSubject = mutableImportHeading.isAttributeSubject;
            this.isContextHeading = mutableImportHeading.isContextHeading;
            this.isLocal = mutableImportHeading.isLocal;
            this.compositionPattern = mutableImportHeading.compositionPattern;
            this.defaultValue = mutableImportHeading.defaultValue;
            this.blankZeroes = mutableImportHeading.blankZeroes;
            this.headingAlias = mutableImportHeading.headingAlias;
        }
    }

    // going to follow the pattern above, no getters, the final will take care of setting
    // I'd have liked to make this immutable but existing logic for things like composite mean this may be changed before loading

    public class ImportCellWithHeading {
        private final ImmutableImportHeading immutableImportHeading;
        private String lineValue;// prefix  line to try to avoid confusion
        private Name lineName;

        public ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value, Name name) {
            this.immutableImportHeading = immutableImportHeading;
            this.lineValue = value;
            this.lineName = name;
        }
    }

    // Switched to Java 8 calls.

    static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("dd/MM/yy");
    static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("dd MMM yyyy");
    static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter ukdf5 = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public LocalDate isADate(String maybeDate) {
        LocalDate date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf4);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf3);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
        if (date != null) return date;
        return tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf5);
    }

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Sets being as mentioned at the top one of the two files that are needed along with import headers to set up a database ready to load data.
    */

    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user) throws Exception {
        System.out.println("reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user, "imported", filePath, "");
        readPreparedFile(azquoMemoryDBConnection, filePath, fileType, attributeNames);

    }


    public void readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames) throws Exception {
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        if (fileType.toLowerCase().startsWith("sets")) {
            setsImport(azquoMemoryDBConnection, new FileInputStream(filePath), attributeNames, fileType);
        } else {
            valuesImport(azquoMemoryDBConnection, filePath, fileType, attributeNames);
        }
        azquoMemoryDBConnection.persist();
    }

    // the readClause function would say that "parent of thing123" would return " thing123" if it was a readClause on "parent of"
    private String readClause(String keyName, String phrase) {
        if (phrase.length() >= keyName.length() && phrase.toLowerCase().startsWith(keyName)) {
            return phrase.substring(keyName.length()).trim();
        }
        return "";
    }

    /* this is called for all the ; separated clauses in a header e.g. Gender; parent of Customer; child of Genders
    Called multiple times per header and currently there may be multiple headers per actual header - something I'd like to change
    Edd : it feels like an enum or array could help here but I'm not sure, about 5 are what you might call vanilla, the rest have other conditions
    lambda?*/

    private void interpretClause(AzquoMemoryDBConnection azquoMemoryDBConnection, MutableImportHeading heading, String clause) throws Exception {
        // not NOT parent of an existing name in the DB, parent of other data in the line
        final String notUnderstood = " not understood";
        int wordEnd = clause.indexOf(" ");
        if (wordEnd < 0) {
            wordEnd = clause.length();
        }
        String firstWord = clause.substring(0, wordEnd).toLowerCase();
        // not NOT parent of an existing name in the DB, parent of other data in the line
        if (PARENTOF.startsWith(firstWord)) {
            heading.parentOfClause = readClause(PARENTOF, clause).replace(Name.QUOTE + "", "");// parent of names in the specified column
            if (heading.parentOfClause.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // e.g. child of all orders, unlike above this references data in the DB
        } else if (CHILDOF.startsWith(firstWord)) {        // e.g. child of all orders
            String childOfString = readClause(CHILDOF, clause).replace(Name.QUOTE + "", "");
            if (childOfString.length() == 0) {
                throw new Exception(clause + notUnderstood);
            } else {
                // used to store the shild of string here and interpret it later, I see no reason not to do it here.
                heading.parentNames = new HashSet<>();
                String[] parents = childOfString.split(",");//TODO this does not take into account names with commas inside.......
                for (String parent : parents) {
                    heading.parentNames.add(nameService.findOrCreateNameInParent(azquoMemoryDBConnection, parent, null, false));
                }
            }
            // e.g. opposite of above
        } else if (REMOVEFROM.startsWith(firstWord)) {
            // e.g. opposite of above
            String removeFromString = readClause(REMOVEFROM, clause).replace(Name.QUOTE + "", "");// child of relates to a name in the database - the hook to existing data
            if (removeFromString.length() == 0) {
                throw new Exception(clause + notUnderstood);
            } else {
                heading.removeParentNames = new HashSet<>();
                String[] removes = removeFromString.split(",");//TODO this does not take into account names with commas inside.......
                for (String remove : removes) {// also not language specific. THis is a simple lookup, don't want a find or create
                    heading.removeParentNames.add(nameService.findByName(azquoMemoryDBConnection, remove));
                }
            }
            // language being attribute
        } else if (firstWord.equals(LANGUAGE)) {
            heading.attribute = readClause(LANGUAGE, clause);
            heading.isAttributeSubject = true; // language is important so we'll default it as the attribute subject if attributes are used later - I might need to check this
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // same as language really but .Name is special - it means default display name. Watch out for this.
        } else if (firstWord.equals(ATTRIBUTE)) {
            // same as language really but .Name is special - it means default display name. Watch out for this.
            heading.attribute = readClause(ATTRIBUTE, clause).replace("`", "");
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            if (heading.attribute.equalsIgnoreCase("name")) {
                heading.attribute = Constants.DEFAULT_DISPLAY_NAME;
            }
        } else if (firstWord.equals(LOCAL)) { // local names in child of, can work with parent of but then it's the subject that it affects
            heading.isLocal = true;
        } else if (firstWord.equals(EQUALS)) {
            heading.headingAlias = readClause(EQUALS, clause);
            if (heading.headingAlias.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
        } else if (firstWord.equals(COMPOSITION)) {
            // combine more than one row
            heading.compositionPattern = readClause(COMPOSITION, clause);
            if (heading.compositionPattern.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // if there's no value on the line a default
        } else if (firstWord.equals(DEFAULT)) {
            String subClause = readClause(DEFAULT, clause);
            if (subClause.length() > 0) {
                heading.defaultValue = subClause;
            }
        } else if (firstWord.equals(NONZERO)) {
            heading.blankZeroes = true;
        } else if (firstWord.equals(PEERS)) {
            // in enw logic this is the only palce that peers are used in Azquo
            heading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);
            String peersString = readClause(PEERS, clause);
            if (peersString.startsWith("{")) { // array, typically when creating in the first place, the spreadsheet call will insert after any existing
                if (peersString.contains("}")) {
                    peersString = peersString.substring(1, peersString.indexOf("}"));
                    final StringTokenizer st = new StringTokenizer(peersString, ",");
                    //final List<String> peersToAdd = new ArrayList<String>();
                    String notFoundError = "";
                    final Set<Name> peers = new HashSet<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        String peerName = st.nextToken().trim();
                        if (peerName.indexOf(Name.QUOTE) == 0) {
                            peerName = peerName.substring(1, peerName.length() - 1); // trim escape chars
                        }
                        Name peer = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, peerName, null, false);
                        if (peer == null) {
                            if (notFoundError.isEmpty()) {
                                notFoundError = peerName;
                            } else {
                                notFoundError += (",`" + peerName + "`");
                            }
                        }
                        peers.add(peer);
                    }
                    if (notFoundError.isEmpty()) {
                        heading.peerNames = peers;
                    } else {
                        throw new Exception("name not found:`" + notFoundError + "`");
                    }
                } else {
                    throw new Exception("Unclosed }");
                }
            }
        } else {
            throw new Exception(firstWord + notUnderstood);
        }
    }

    /*
    headings are clauses separated by semicolons, first is the heading name the onto the extra stuff
    essentially parsing through all the relevant things in a heading to populate an ImportHeading
    */

    private void interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, MutableImportHeading heading, List<String> attributeNames) throws Exception {
        StringTokenizer clauses = new StringTokenizer(headingString, ";");
        heading.heading = clauses.nextToken().replace(Name.QUOTE + "", ""); // the heading na,e being the first
        heading.name = nameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames);//at this stage, look for a name, but don't create it unless necessary
        // loop over the clauses making sense and modifying the heading object as you go
        while (clauses.hasMoreTokens()) {
            interpretClause(azquoMemoryDBConnection, heading, clauses.nextToken().trim());
        }
    }

    // when dealing with populating peer headings first look for the headings then look at the context headings, that's what this does.
    // notably it returns an index that is not column index but the number in the headings with cells array. This feels wrong.

    private int findContextHeadingIndex(Name name, List<MutableImportHeading> headings) {
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            if (heading.isContextHeading && heading.name.findAllParents().contains(name)) {
                return headingNo;
            }
        }
        return -1;
    }

    /* Find a heading, return its index, is used when trying to find peer headings and composite values
    The extra logic aside simply from heading matching is the identifier flag (multiple attributes mean many headings with the same name)
    Or attribute being null (thus we don't care about identifier) or equalsString not being null? equals String parked for the mo after talking with WFC
    */

    private ImportCellWithHeading findCellWithHeading(String nameToFind, List<ImportCellWithHeading> importCellWithHeadings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        ImportCellWithHeading toReturn = null;
        for (ImportCellWithHeading importCellWithHeading : importCellWithHeadings) {
            ImmutableImportHeading heading = importCellWithHeading.immutableImportHeading;
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().startsWith(nameToFind.toLowerCase() + ","))
                    && (heading.isAttributeSubject || heading.attribute == null || heading.headingAlias != null)) {
                if (heading.isAttributeSubject) {
                    return importCellWithHeading;
                }
                // ah I see the logic here. Identifier means it's the one to use, if not then there must be only one - if more than one are found then it's too ambiguous to work with.
                if (toReturn == null) {
                    toReturn = importCellWithHeading; // our possibility but don't return yet, need to check if there's more than one match
                } else {
                    return null;// found mroe than one possibility, return null now
                }
            }
        }
        return toReturn;
    }

    // very similar to above, not sure of an obvious factor - just the middle lines or does this make things more confusing?
    // I'd need an interface between Mutable and Immutable import heading, quite a few more lines of code to reduce this slightly.

    private int findMutableHeadingIndex(String nameToFind, List<MutableImportHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        nameToFind = nameToFind.trim();
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().indexOf(nameToFind.toLowerCase() + ",") == 0)
                    && (heading.isAttributeSubject || heading.attribute == null || heading.headingAlias != null)) {
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

    // last shot at getting a heading for peer headings, upon finding the right heading then create a name for the heading based on the heading (not line value)

    private int findLowerLevelHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String peerName, List<MutableImportHeading> headings) throws Exception {
        //look for a column with a set name specified as a subset of the peer name
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.toLowerCase().contains("," + peerName.toLowerCase()))) {
                heading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);//may need to create it
                return headingNo;
            }
        }
        return headingFound;
    }

    // essentially the same as findOrCreateNameStructure but with the cache
    // called to find or create peers in findPeers

    public Name includeInSet(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent
        String np = name + ",";
        if (parent != null) {
            np += parent.getId();
        }
        Name found = namesFound.get(np);
        if (found != null) {
            return found;
        }
        found = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, name, parent, local, attributeNames);
        namesFound.put(np, found);
        return found;
    }

    // to make a batch call to the above if there are a list of parents

    public Name includeInParents(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, String name, Set<Name> parents, boolean local, List<String> attributeNames) throws Exception {
        Name child = null;
        if (parents == null) {
            child = includeInSet(azquoMemoryDBConnection, namesFound, name, null, local, attributeNames);
        } else {
            for (Name parent : parents) {
                child = includeInSet(azquoMemoryDBConnection, namesFound, name, parent, local, attributeNames);
            }
        }
        return child;
    }

    /*
    Created by EFC to try to improve speed through multi threading. There's still a bottleneck in the initial parsing
    (maybe use a Spliterator?) but it can now batch up simply parsed lines and stack them here for importing.
     */

    private class BatchImporter implements Runnable {

        private final AzquoMemoryDBConnection azquoMemoryDBConnection;
        private final AtomicInteger valueTracker;
        private int lineNo;
        private final List<List<ImportCellWithHeading>> dataToLoad;
        private final Map<String, Name> namesFound;
        private final List<String> attributeNames;

        public BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valueTracker, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFound, List<String> attributeNames, int lineNo) {
            this.azquoMemoryDBConnection = azquoMemoryDBConnection;
            this.valueTracker = valueTracker;
            this.dataToLoad = dataToLoad;
            this.namesFound = namesFound;
            this.attributeNames = attributeNames;
            this.lineNo = lineNo;
        }

        @Override
        public void run() {
            long trigger = 10;
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
                // todo, move this check outside??
                /*skip any line that has a blank in the first column unless the first column had no header
               of course if the first column has no header and then the second has data but not on this line then it would get loaded
                 */
                if (lineToLoad.get(0).lineValue.length() > 0 || lineToLoad.get(0).immutableImportHeading.columnIndex == -1) {
                    getCompositeValues(lineToLoad);
                    try {
                        valueTracker.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFound, attributeNames, lineNo));
                    } catch (Exception e) {
                        System.out.println("error: line " + lineNo);
                        e.printStackTrace();
                        break;
                    }
                    Long now = System.currentTimeMillis();
                    if (now - time > trigger) {
                        System.out.println("line no " + lineNo + " time = " + (now - time) + "ms");
                    }
                    time = now;
                }
                lineNo++;
            }
            System.out.println("Batch finishing : " + DecimalFormat.getInstance().format(lineNo) + " imported.");
            System.out.println("Values Imported : " + DecimalFormat.getInstance().format(valueTracker));
        }
    }

    // calls header validation and batches up the data with headers ready for batch importing

    public void valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames) throws Exception {
        // Preparatory stuff
        // Local cache just to speed things up
        final Map<String, Name> namesFound = new ConcurrentHashMap<>();
        if (fileType.indexOf(" ") > 0) {
            //file type should be first word only
            fileType = fileType.substring(0, fileType.indexOf(" "));
        }
        if (fileType.contains("_")) {
            fileType = fileType.substring(0, fileType.indexOf("_"));
        }
        // grab the first line to check on delimiters
        long track = System.currentTimeMillis();
        char delimiter = ',';
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String firstLine = br.readLine();
        br.close();
        if (firstLine != null) {
            if (firstLine.contains("|")) {
                delimiter = '|';
            }
            if (firstLine.contains("\t")) {
                delimiter = '\t';
            }
        } else {
            return;//if he first line is blank, ignore the sheet
        }
        // now we know the delimiter can CSV read, I've read jackson is pretty quick
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
        CsvSchema schema = csvMapper.schemaFor(String[].class)
                .withColumnSeparator(delimiter)
                .withLineSeparator("\n");
        // keep this one separate so it can be closed at the end
        MappingIterator<String[]> originalLineIterator = csvMapper.reader(String[].class).with(schema).readValues(new File(filePath));
        Iterator<String[]> lineIterator = originalLineIterator; // for the data, it might be reassigned in the case of transposing
        String[] headers = null;
        // ok beginning to understand. It looks for a name for the file type, this name can have headers and/or the definitions for each header
        // in this case looking for a list of headers. Could maybe make this make a bit more sense . . .
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
        boolean skipTopLine = false;
        if (importInterpreter != null) {
            if ("true".equalsIgnoreCase(importInterpreter.getAttribute("transpose"))) {
                // ok we want to transpose, will use similar logic to the server side transpose
                final List<String[]> sourceList = new ArrayList<>();
                while (lineIterator.hasNext()) { // it will be closed at the end. Worth noting that transposing shouldn't really be done on massive files, I can't imagine it would be
                    sourceList.add(lineIterator.next());
                }
                final List<String[]> flipped = new ArrayList<>(); // from ths I can get a compatible iterator
                if (!sourceList.isEmpty()) { // there's data to transpose
                    final int oldXMax = sourceList.get(0).length; // size of nested list, as described above (that is to say get the length of one row)
                    for (int newY = 0; newY < oldXMax; newY++) {
                        String[] newRow = new String[sourceList.size()]; // make a new row
                        int index = 0;
                        for (String[] oldRow : sourceList) { // and step down each of the old rows
                            newRow[index] = oldRow[newY];//so as we're moving across the new row we're moving down the old rows on a fixed column
                            index++;
                        }
                        flipped.add(newRow);
                    }
                    lineIterator = flipped.iterator(); // replace the iterator, I was keen to keep the pattern the Jackson uses, this seems to support it, the original is closed at the bottom either way
                }
            }
            // The code below should be none the wiser that a transpose happened if it did.
            String importHeaders = importInterpreter.getAttribute(headingsString);
            if (importHeaders == null) {
                // todo - get rid of this and change to an attribute like transpose to skip a number of lines
                importHeaders = importInterpreter.getAttribute(headingsString + "1");
                if (importHeaders != null) {
                    skipTopLine = true;
                }
            }
            if (importHeaders != null) {
                headers = importHeaders.split("¬"); // a bit arbitrary, would like a better solution if I can think of one.
            }
        }
        // we might use the headers on the data file, this is notably used when setting up the headers themselves :)
        if (headers == null) {
            headers = lineIterator.next();
        } else {
            if (skipTopLine) {
                lineIterator.next();
            }
        }
        /*
        End preparatory stuff
        readHeaders is about creating a set of ImportHeadings
        notable that internally it might use attributes from the relevant data import name to supplement the header information
        to be more specific : that name called by "dataimport " + fileType has been hit for its "headingsString" attribute already to produce headers
        but it could be asked for something more specific according to the header name.
        Notably this method where columns can be called by name will look nicer in the heading set up but it requires data files to have headings.
        The ¬ separated option is a pain, I feel one should use .column1, .column2 etc. Should be more simple to set up.
        */
        List<MutableImportHeading> mutableImportHeadings = new ArrayList<>();
        // read the clauses, assign the heading.name if you can find it, add on the context headings
        readHeaders(azquoMemoryDBConnection, headers, mutableImportHeadings, fileType, attributeNames);
        // further information put into the ImportHeadings based off the initial info
        // I could put more in here - there's stuff going on in the values import that is header only stuff (context?)
        fillInHeaderInformation(azquoMemoryDBConnection, mutableImportHeadings);
        // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - new java 8 notation, not completely comfortable with this but what is happening here is simple
        final List<ImmutableImportHeading> immutableImportHeadings = mutableImportHeadings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList());
        // having read the headers go through each record
        // now, since this will be multi threaded need to make line objects, Cannot be completely immutable due to the current logic, I may be able to change this, not sure
        int lineNo = 1; // start at 1, we think of the first line being 1 not 0.
        // pretty vanilla multi threading bits
        ExecutorService executor = Executors.newFixedThreadPool(azquoMemoryDBConnection.getAzquoMemoryDB().getLoadingThreads());
        AtomicInteger valueTracker = new AtomicInteger(0);
        int batchSize = 100000; // a bit arbitrary, I wonder shuld I go smaller?
        ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<>(batchSize);
        while (lineIterator.hasNext()) { // new Jackson call . . .
            String[] lineValues = lineIterator.next();
            lineNo++;
            List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
            for (ImmutableImportHeading immutableImportHeading : immutableImportHeadings) {
                // since strings may be repeated intern, should save a bit of memory using the String pool. Hopefully not a big performance hit? Also I figure trimming here does no harm
                String lineValue = immutableImportHeading.columnIndex != -1 && immutableImportHeading.columnIndex < lineValues.length ? lineValues[immutableImportHeading.columnIndex].trim().intern() : "";
                importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue, null));
            }
            //batch it up!
            linesBatched.add(importCellsWithHeading);
            if (linesBatched.size() == batchSize) {
                executor.execute(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFound, attributeNames, lineNo - batchSize)); // line no should be the start
                linesBatched = new ArrayList<>(batchSize);
            }
        }
        // load leftovers
        executor.execute(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFound, attributeNames, lineNo - linesBatched.size()));
        executor.shutdown();
        if (!executor.awaitTermination(8, TimeUnit.HOURS)) {
            throw new Exception("File " + filePath + " took longer than 8 hours to load for : " + azquoMemoryDBConnection.getAzquoMemoryDB().getMySQLName());
        }
        // wasn't closing before, maybe why the files stayed there
        originalLineIterator.close();
        // edd adding a delete check for tomcat temp files, if read from the other temp directly then leave it alone
        if (filePath.contains("/usr/")) {
            File test = new File(filePath);
            if (test.exists()) {
                if (!test.delete()) {
                    System.out.println("unable to delete " + filePath);
                }
            }
        }
        System.out.println("csv dataimport took " + (System.currentTimeMillis() - track) / 1000 + " second(s) for " + (lineNo - 1) + " lines");
        System.out.println("---------- namesfound size " + namesFound.size());
    }

    // run through the headers. Mostly this means running through clauses,

    private void readHeaders(AzquoMemoryDBConnection azquoMemoryDBConnection, String[] headers, List<MutableImportHeading> headings, String fileType, List<String> attributeNames) throws Exception {
        int col = 0;
        //  if the file is of type (e.g.) 'sales' and there is a name 'dataimport sales', this is used as an interpreter.
        //  It need not interpret every column heading, but any attribute of the same name as a column heading will be used.
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
        String lastHeading = "";
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

                head = head.replace(".", ";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                int dividerPos = head.lastIndexOf(headingDivider);
                // right, headingDivider, |. It seems to work backwards, stacking context headings for this heading.
                // it is this that makes headings a sort of virtual - I don't like this and want to zap it. Notable that column index is not left as -1 . . .how can column index ever be -1 then?
                while (dividerPos > 0) {
                    MutableImportHeading contextHeading = new MutableImportHeading();
                    interpretHeading(azquoMemoryDBConnection, head.substring(dividerPos + 1), contextHeading, attributeNames);
                    contextHeading.columnIndex = col;
                    contextHeading.isContextHeading = true;
                    headings.add(contextHeading);
                    head = head.substring(0, dividerPos);
                    dividerPos = head.lastIndexOf(headingDivider);
                }
                heading.columnIndex = col;
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
                interpretHeading(azquoMemoryDBConnection, head, heading, attributeNames);
                headings.add(heading);
            } else {// add an empty one. Makes sense I suppose?
                headings.add(new MutableImportHeading());
            }
            col++;
        }
    }

    // TODO - EDD UNDERSTANDS PROPERLY UP TO THIS LINE, TRY TO GET IT TO THE BOTTOM

    // todo - edd understand properly! Bet there's some factoring to do

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFound, List<String> attributeNames, int lineNo) throws Exception {
        String value;
        int valueCount = 0;
        // initial pass to deal with defaults, dates and local parents
        for (ImportCellWithHeading importCellWithHeading : cells) {
            // this basic value checking was outside, I see no reason it shouldn't be in here
            if (importCellWithHeading.immutableImportHeading.defaultValue != null && importCellWithHeading.lineValue.length() == 0) {
                importCellWithHeading.lineValue = importCellWithHeading.immutableImportHeading.defaultValue;
            }
            if (importCellWithHeading.immutableImportHeading.attribute != null && importCellWithHeading.immutableImportHeading.attribute.equalsIgnoreCase(dateLang)) {
                    /*
                    interpret the date and change to standard form
                    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    */
                LocalDate date = isADate(importCellWithHeading.lineValue);
                if (date != null) {
                    importCellWithHeading.lineValue = dateTimeFormatter.format(date);
                }
            }
            // prepare the local parent of columns. Customer is in all customers local
            // ok local is done up here and not local below (handle parent being called twice)? Plus the name attached to the cell (not heading!) will be set below . . . perhaps how local is dealt with. Hmmmmmmmm
            if (importCellWithHeading.immutableImportHeading.isLocal && importCellWithHeading.immutableImportHeading.indexForChild != -1) { // local and it is a parent of another heading (has child heading), inside this function it will use the child heading set up
                handleParent(azquoMemoryDBConnection, namesFound, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        long toolong = 2000000;
        long time = System.nanoTime();
        ImportCellWithHeading contextPeersItem = null;
        List<Name> contextNames = new ArrayList<>(); // stacks cumulatively across the line
        for (ImportCellWithHeading cell : cells) {
            /* ok the gist seems to be that there's peers as defined in a context item in which case it's looking in context items and peers
            a notable thing about context : after something has been added to context names it stays there for subsequent cells.
            again this logic is dependant on headers, it really needn't be done every time
            ok context peers will look for other columns and in the context for names where it allows members off sets e.g. a peer might be year so 2014, 2015 etc will be ok.
             */
            if (cell.immutableImportHeading.isContextHeading) {
                contextNames.add(cell.immutableImportHeading.name);
                if (cell.immutableImportHeading.peerNames.size() > 0) {
                    contextPeersItem = cell; // so skip this heading but now contextPeersItem is set? I assume one name with peers allowed. Or the most recent one.
                }
            } else {
                if (contextNames.size() > 0 && cell.immutableImportHeading.name != null) { // ok so some context names and a name for this column? I guess as in not an attribute column for example
                    contextNames.add(cell.immutableImportHeading.name);// add this name onto the context stack - for our purposes now it's
                    if (contextPeersItem != null) { // a value cell HAS to have peers, context headings are only for values
                        final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
                        namesForValue.add(contextPeersItem.immutableImportHeading.name);// ok the "defining" name with the peers.
                        boolean foundAll = true;
                        for (Name peer : contextPeersItem.immutableImportHeading.peerNames) { // ok so a value with peers
                            //is this peer in the contexts?
                            Name possiblePeer = null;
                            for (Name contextPeer : contextNames) {
                                if (contextPeer.findAllParents().contains(peer)) {
                                    possiblePeer = contextPeer;
                                    break;
                                }
                            }
                            // couldn't find it in the context so look through the headings?
                            if (possiblePeer == null) {
                                //look at the headings
                                ImportCellWithHeading peerCell = findCellWithHeading(peer.getDefaultDisplayName(), cells);
                                if (peerCell == null) {
                                    foundAll = false;
                                    break;
                                }
                                /*   UNNECESSARY CODE REMOVED BY WFC
                                if (peerHeading.lineName != null) {
                                    peer.addChildWillBePersisted(peerHeading.lineName);
                                } else {
                                    String peerValue = peerHeading.lineValue;
                                    peerHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, peerValue, peer, heading.local, attributeNames);
                                }
                                */
                                // edd note : everything up to here seems to be all header stuff - one could prepare the other names and put markers in here I think ergh . . .
                                possiblePeer = peerCell.lineName;
                            }
                            if (nameService.inParentSet(possiblePeer, peer.getChildren()) != null) {
                                namesForValue.add(possiblePeer);
                            } else {
                                foundAll = false;
                                break;
                            }
                        }
                        if (foundAll) {
                            // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                            value = cell.lineValue;
                        } else {
                            value = "";
                        }
                        if (cell.immutableImportHeading.blankZeroes && isZero(value)) value = "";
                        if (value.trim().length() > 0) { // no point storing if there's no value!
                            valueCount++;
                            // finally store our value and names for it
                            valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                        }
                    }
                    contextNames.remove(cell.immutableImportHeading.name);
                }
                if (cell.immutableImportHeading.indexesForPeers.size() > 0) { // ok so context stuff has happened now this happens too, maybe should be an else? Or one could get two entries for each line . . .
                    final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
                    // check for peers as defined in peerHeadings, this will create peers if it can't find them. It will fail if a peer heading has no line value
                    boolean hasRequiredPeers = findPeers(azquoMemoryDBConnection, namesFound, cell, cells, namesForValue, attributeNames);
                    if (hasRequiredPeers) {
                        // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                        value = cell.lineValue;
                    } else {
                        value = "";
                    }
                    if (cell.immutableImportHeading.blankZeroes && isZero(value)) value = "";
                    if (value.trim().length() > 0) { // no point storing if there's no value!
                        valueCount++;
                        // finally store our value and names for it
                        valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                    }
                }
                // ok that's the peer/value stuff done I think
                if (cell.immutableImportHeading.indexForAttribute >= 0 && cell.immutableImportHeading.attribute != null
                        && cell.lineValue.length() > 0
                        && (!cell.immutableImportHeading.attribute.equalsIgnoreCase(dateLang) || (isADate(cell.lineValue) != null))) {
                    // funnily enough no longer using attributes
                    handleAttribute(azquoMemoryDBConnection, namesFound, cell, cells);
                }
                // not local this time - handle attribute and find peers might have set the cell name?
                if (cell.immutableImportHeading.indexForChild != -1 && !cell.immutableImportHeading.isLocal) {
                    handleParent(azquoMemoryDBConnection, namesFound, cell, cells, attributeNames, lineNo);
                }
                if (cell.immutableImportHeading.parentNames != null) {
                    if (cell.lineName != null) {
                        for (Name parent : cell.immutableImportHeading.parentNames) {
                            parent.addChildWillBePersisted(cell.lineName);
                        }
                    } else {
                        String childNameString = cell.lineValue;
                        if (childNameString.length() > 0) {
                            for (Name parent : cell.immutableImportHeading.parentNames) {
                                cell.lineName = includeInSet(azquoMemoryDBConnection, namesFound, childNameString, parent, cell.immutableImportHeading.isLocal, attributeNames);
                            }
                        }
                    }
                }
                if (cell.immutableImportHeading.removeParentNames != null) {
                    if (cell.lineName != null) {
                        for (Name remove : cell.immutableImportHeading.removeParentNames) {
                            remove.removeFromChildrenWillBePersisted(cell.lineName);
                        }
                    }
                }
            }
            long now = System.nanoTime();
            if (now - time > toolong) {
                System.out.println(cell.immutableImportHeading.heading + " took " + (now - time));
            }
            time = System.nanoTime();
        }
        return valueCount;
    }

    // sort peer headings, attribute headings, child of remove from, parent of

    private void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        for (MutableImportHeading mutableImportHeading : headings) {
            if (mutableImportHeading.heading != null) {
                // ok find the indexes of peers and get shirty if you can't find them
                if (mutableImportHeading.name != null && mutableImportHeading.peerNames.size() > 0 && !mutableImportHeading.isContextHeading) {
                    for (Name peer : mutableImportHeading.peerNames) {
                        //three possibilities to find the peer:
                        int peerHeadingIndex = findMutableHeadingIndex(peer.getDefaultDisplayName(), headings);
                        if (peerHeadingIndex == -1) {
                            peerHeadingIndex = findContextHeadingIndex(peer, headings);
                            if (peerHeadingIndex == -1) {
                                peerHeadingIndex = findLowerLevelHeading(azquoMemoryDBConnection, peer.getDefaultDisplayName(), headings);
                                if (peerHeadingIndex == -1) {
                                    throw new Exception("error: cannot find peer " + peer.getDefaultDisplayName() + " for " + mutableImportHeading.name.getDefaultDisplayName());
                                }
                            }
                        }
                        if (peerHeadingIndex >= 0) { // doesn't it have to be???
                            MutableImportHeading importPeer = headings.get(peerHeadingIndex);
                            if (importPeer.name == null) {
                                importPeer.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importPeer.heading, null, false);
                            }
                        }
                        mutableImportHeading.indexesForPeers.add(peerHeadingIndex);
                    }
                }
                // having an attribute means the content of this column relates to a name in another column, need to find that name
                if (mutableImportHeading.attribute != null) { // && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
                    String headingName = mutableImportHeading.heading;
                    if (mutableImportHeading.headingAlias != null) {// the equals string seems to be a way for a heading to have an alias, not completely clear on the usage but I'm guessing by this point the alias is no longer important so overwrite it with the equals value
                        headingName = mutableImportHeading.headingAlias;
                    }
                    // so if it's Customer,Address1 we need to find customer.
                    // This findHeadingIndex will look for the Customer with isAttributeSubject = true or the first one without an attribute
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
                    //error = findTopParent(azquoMemoryDBConnection, importHeading, headings, attributeNames);
                    // if (error.length() > 0) return error;
                }
            }
        }
    }

    private List<String> setLocalLanguage(ImmutableImportHeading heading, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<>();
        if (heading.attribute != null && !heading.attribute.equalsIgnoreCase(dateLang)) {
            languages.add(heading.attribute);
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;
    }

    // namesFound is a cache. Then the heading we care about then the list of all headings.
    private void handleParent(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int lineNo) throws Exception {
        if (cellWithHeading.lineValue.length() == 0) { // so nothing to do
            return;
        }
        if (cellWithHeading.lineValue.contains(",") && !cellWithHeading.lineValue.contains(Name.QUOTE + "")) {//beware of treating commas in cells as set delimiters....
            cellWithHeading.lineValue = Name.QUOTE + cellWithHeading.lineValue + Name.QUOTE;
        }
        if (cellWithHeading.lineName != null) { // This function is called in two places in interpret line, the first time this will be null the second time not
            if (cellWithHeading.immutableImportHeading.parentNames != null) {
                for (Name parent : cellWithHeading.immutableImportHeading.parentNames) { // apparently there can be multiple childofs, put the name for the line in th appropriate sets.
                    parent.addChildWillBePersisted(cellWithHeading.lineName);
                }
            }
        } else {
            cellWithHeading.lineName = includeInParents(azquoMemoryDBConnection, namesFound, cellWithHeading.lineValue
                    , cellWithHeading.immutableImportHeading.parentNames, cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(cellWithHeading.immutableImportHeading, attributeNames));
        }
        ImportCellWithHeading childCell = cells.get(cellWithHeading.immutableImportHeading.indexForChild);
        if (childCell.lineValue.length() == 0) {
            throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.lineValue);
        }
        if (childCell.lineName == null) {
            childCell.lineName = includeInSet(azquoMemoryDBConnection, namesFound, childCell.lineValue, cellWithHeading.lineName
                    , cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(childCell.immutableImportHeading, attributeNames));
        }
        cellWithHeading.lineName.addChildWillBePersisted(childCell.lineName);
    }

    // only called in one place, inline?

    public void handleAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cell, List<ImportCellWithHeading> cells) throws Exception {
        ImportCellWithHeading identityCell = cells.get(cell.immutableImportHeading.indexForAttribute); // get our source cell
        if (identityCell.lineName == null) { // no name on the cell I want to set the attribute on - need to create the name. Can this happen? I mean the name being null? I guess so.
            if (identityCell.lineValue.length() == 0) { // and no line value for the line I want to set the attribute on, can't find the name, nothing to do!
                return;
            }
            List<String> localAttributes = new ArrayList<>();
            localAttributes.add(identityCell.immutableImportHeading.attribute);
            // here's a thing - this either finds a name with the attribute/value combo already or it creates it, so we're done? I'm adding an else below
            identityCell.lineName = includeInParents(azquoMemoryDBConnection, namesFound, identityCell.lineValue, identityCell.immutableImportHeading.parentNames, false, localAttributes);
        }
        identityCell.lineName.setAttributeWillBePersisted(cell.immutableImportHeading.attribute, cell.lineValue);
    }

    // ok what's notable here is that this will create names to complete the peers if it can't find them

    private boolean findPeers(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cell, List<ImportCellWithHeading> cells, Set<Name> namesForValue, List<String> attributeNames) throws Exception {
        //ImportHeading headingWithPeers = heading;
        boolean hasRequiredPeers = true;
        namesForValue.add(cell.immutableImportHeading.name); // the one at the top of this headingNo, the name with peers.
        for (int peerHeadingIndex : cell.immutableImportHeading.indexesForPeers) { // go looking for the peers
            ImportCellWithHeading peerCell = cells.get(peerHeadingIndex);
            if (peerCell.immutableImportHeading.isContextHeading) {// can it be a context item? If so it seems just add it - we assume context items have names
                namesForValue.add(peerCell.immutableImportHeading.name);
            } else {// otherwise there may be a name there but we need to check first
                if (peerCell.lineName == null) {
                    if (peerCell.lineValue.length() == 0) { // null name and no line value, I guess we can't find this peer
                        hasRequiredPeers = false;
                    } else { // we do have a value on this line
                        List<String> peerLanguages = new ArrayList<>();
                        //It seems language is either attribute if it was set on this heading or the languages passed through right from the front end (attributeNames)
                        if (peerCell.immutableImportHeading.attribute != null) {
                            peerLanguages.add(peerCell.immutableImportHeading.attribute);
                        } else {
                            peerLanguages.addAll(attributeNames);
                        }
                        /* ok connection is obvious, namesfound is the speedy cache, the peerCell.value is the name we want to create or find
                        it's the line value that so often refers to a name ratehr than a value. After that cells.get(peerHeadingNo).name was passed as a parent but this will always be null!
                        Hence make it null. false is local, peerLanguages is how we'll look up the name.
                        As I'll comment below : includeInSet is essentially the same as findOrCreateNameStructure but with the cache
                        */
                        peerCell.lineName = includeInSet(azquoMemoryDBConnection, namesFound, peerCell.lineValue, null, false, peerLanguages);
                    }
                }
                // add to the set of names we're going to store against this value
                if (peerCell.lineName != null) {
                    namesForValue.add(peerCell.lineName);
                }
                //namesForValue.add(nameService.findOrCreateName(azquoMemoryDBConnection,peerVal + "," + peer.getName())) ;
            }
        }
        return hasRequiredPeers;
    }

    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values

    private void getCompositeValues(List<ImportCellWithHeading> cells) {
        int adjusted = 2;
        //loops in case there are multiple levels of dependencies
        while (adjusted > 1) {
            adjusted = 0;
            for (ImportCellWithHeading cell : cells) {
                if (cell.immutableImportHeading.compositionPattern != null) {
                    String result = cell.immutableImportHeading.compositionPattern;
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            String expression = result.substring(headingMarker + 1, headingEnd);
                            String function = null;
                            int funcInt = 0;
                            if (expression.contains("(")) {
                                int bracketpos = expression.indexOf("(");
                                function = expression.substring(0, bracketpos);
                                int commaPos = expression.indexOf(",", bracketpos + 1);
                                if (commaPos > 0) {
                                    String countString = expression.substring(commaPos + 1, expression.length() - 1);
                                    try {
                                        funcInt = Integer.parseInt(countString.trim());
                                    } catch (Exception ignore) {
                                    }

                                    expression = expression.substring(bracketpos + 1, commaPos);

                                }
                            }
                            ImportCellWithHeading compCell = findCellWithHeading(expression, cells);
                            if (compCell != null) {
                                String sourceVal = compCell.lineValue;
                                if (function != null && funcInt > 0 && sourceVal.length() > funcInt) {
                                    if (function.equalsIgnoreCase("left")) {
                                        sourceVal = sourceVal.substring(0, funcInt);
                                    }
                                    if (function.equalsIgnoreCase("right")) {
                                        sourceVal = sourceVal.substring(sourceVal.length() - funcInt);
                                    }
                                }
                                result = result.replace(result.substring(headingMarker, headingEnd + 1), sourceVal);
                            }
                        }
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    if (!result.equals(cell.lineValue)) {
                        cell.lineValue = result;
                        adjusted++;
                    }
                }
            }
        }
    }


    private boolean isZero(String text) {
        try {
            double d = Double.parseDouble(text);
            return d == 0.0;
        } catch (Exception e) {
            return true;
        }
    }

    public void setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final InputStream uploadFile, List<String> attributeNames, String fileName) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String sheetSetName = "";
        Name sheetSet = null;
        if (fileName.length() > 4 && fileName.charAt(4) == '-') {
            sheetSetName = fileName.substring(5);
            sheetSet = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, sheetSetName, null, false, attributeNames);
        }
        String line;
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            MutableImportHeading mutableImportHeading = new MutableImportHeading();
            if (st.hasMoreTokens()) {
                List<Name> children = new ArrayList<>();
                String setName = st.nextToken().replace("\"", "");//sometimes the last line of imported spreadsheets has come up as ""
                if (setName.length() > 0) {
                    interpretHeading(azquoMemoryDBConnection, setName, mutableImportHeading, attributeNames);
                    if (mutableImportHeading.heading.equalsIgnoreCase(sheetSetName)) {
                        mutableImportHeading.name = sheetSet;
                    } else {
                        mutableImportHeading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, mutableImportHeading.heading, sheetSet, true, attributeNames);
                    }
                    if (mutableImportHeading.name != null) { // is this a concern? I'll throw an exception in case (based on IntelliJ warning)
                        Name set = mutableImportHeading.name;
                        while (st.hasMoreTokens()) {
                            String element = st.nextToken();
                            Name child;
                            if (element.length() > 0) {
                                int localPos = element.toLowerCase().indexOf(";local");
                                if (localPos > 0 || mutableImportHeading.isLocal) {
                                    if (localPos > 0) {
                                        element = element.substring(0, localPos);
                                    }
                                    child = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, element, set, false, attributeNames);
                                } else {
                                    child = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, element, sheetSet, true, attributeNames);
                                    //new names will have been added to sheet set unnecessarily, so:
                                }
                                children.add(child);
                            }
                        }
                        nameService.clearChildren(set);
                        for (Name child : children) {
                            if (sheetSet != null) sheetSet.removeFromChildrenWillBePersisted(child);
                            set.addChildWillBePersisted(child);
                        }

                    } else {
                        throw new Exception("Import heading name was null : " + mutableImportHeading);
                    }
                }
            }
        }

    }
}