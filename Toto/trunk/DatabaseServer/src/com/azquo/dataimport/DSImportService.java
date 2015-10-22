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

/**
 * Created by cawley on 20/05/15.
 * <p>
 * Has a fair bit of the logic that was in the original import service.
 * Note : large chunks of this were originally written by WFC and then refactored by EFC.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * <p>
 * I'm trying to write up as much of the non obvious logic as possible in comments in the code.
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
    These are heading clauses. I heading definitions can be in the data file but Azquo is setup to support data
    "as it comes". Hence when dealing with a new set of data the key is to set up sets and headings so that the system can load the data.
    Setting up the sets and headings could be seen as similar to setting up the tables in an SQL database.

    Note : the clauses here tend to reverse the subject/object used in the code. If an object in the code has children we'll say object.children, not object.parentOf.
    This isn't a big problem and the way the clauses are set up probably makes sense in their context, I just want to note that as they are parsed naming may reverse - parentOf to children etc.

    How these are used is described in more detail in the heading fields and the clause interpreter.
     */

    public static final String CHILDOF = "child of ";
    public static final String REMOVEFROM = "remove from ";
    // parent of another heading (as opposed to name), would like the clause to be more explicit,
    public static final String PARENTOF = "parent of ";
    public static final String ATTRIBUTE = "attribute";
    public static final String LANGUAGE = "language";
    public static final String PEERS = "peers";
    public static final String LOCAL = "local";
    public static final String EQUALS = "equals";
    public static final String COMPOSITION = "composition";
    public static final String DEFAULT = "default";
    public static final String NONZERO = "nonzero";
    public static final String headingsString = "HEADINGS";
    public static final String dateLang = "date";
    public static final String ONLY = "only";

    /*
    To multi thread I wanted this to be immutable but there are things that are only set after in context of other headings so I can't
    do this initially. No problem, initially make this very simple and mutable then have an immutable version for the multi threaded stuff which is held against line.
    could of course copy all fields into line but this makes the constructor needlessly complex.
    */

    private class MutableImportHeading {
        // the name of the heading - often referenced by other headings e.g. parent of
        String heading = null;
        // the Azquo Name that might be set on the heading, certainly used if there are peers but otherwise may not be set if the heading is referenced by other headings
        Name name = null;
        // this class used to use the now removed peers against the name object, in its absence just put a set here, and this set simply refers to headings which may be names or not - hence why I renamed it from peerNames).
        Set<String> peers = new HashSet<>();
        /* the index of the heading that an attribute refers to so if the heading is Customer.Address1 then this is the index of customer.
        Has been kept as an index as it will be used to access the data itself (the array of Strings from each line) */
        int indexForAttribute = -1;
        // The parent of clause is an internal reference - to other headings, as in what is this a parent of - need to have it here to resolve later when we have a complete headings list
        // it will be resolved into indexForChild
        String parentOfClause = null;
        // index in the headings array of a child derived from parent of
        int indexForChild = -1;
        // derived from the "child of" clause, a comma separated list of names
        Set<Name> parentNames = new HashSet<>();
        // same format or logic as parentNames - now I look at this I'm a bit puzzled what it's for
        Set<Name> removeParentNames = new HashSet<>();
        // result of the attribute clause. Notable that "." is replaced with ;attribute
        String attribute = null;
        /* the results of the peers clause are jammed in peers but then we need to know which headings those peers refer to - if context assign the name otherwise it's going to be the cell index that's used */
        Set<Integer> peerCellIndexes = new HashSet<>();
        // if context provides any of the peers they're in here
        Set<Name> peersFromContext = new HashSet<>();
        // the same as the above two but for a peer set defined in the context. The normal peers can get names from context, the context can get names from itself
        Set<Integer> contextPeerCellIndexes = new HashSet<>();
        Set<Name> contextPeersFromContext = new HashSet<>();

        /*if there are multiple attributes then effectively there will be multiple columns with the same "heading", define which one we're using when the heading is referenced by other headings.
        Language will trigger something as being the attribute subject, after if on searching there is only one it might be set for convenience when sorting attributes */
        boolean isAttributeSubject = false;
        /*when using the heading divider (a pipe at the moment) we now have new logic - don't stack the context headers in the same array at the others attach them to the heading where they were defined
        I think a list is fine for this. Doing this means the outside array of headings will match the data array        */
        List<MutableImportHeading> contextHeadings = new ArrayList<>();
        // local in the azquo sense. Affects child of and parent of clauses - the other heading is local in the case of parent of and this one in the case of child of. Local as in Azquo name logic.
        boolean isLocal = false;
        // If `only` is specified on the first heading, the import will ignore any line that does not have this name
        String only = null;
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
    }

    // I see no reason for getters here. Class only used here. Note added later : getters and setters may make the code clearer though this could be done by better names also I think
    private class ImmutableImportHeading {
        final String heading;
        final Name name;
        final Set<String> peers;
        final int indexForAttribute;
        final int indexForChild;
        // ok the set will be fixed, I suppose names can be modified but they should be thread safe. Well that's the plan.
        final Set<Name> parentNames;
        final Set<Name> removeParentNames;
        final String attribute;
        final Set<Integer> peerCellIndexes;
        final Set<Name> peersFromContext;
        final Set<Integer> contextPeerCellIndexes;
        final Set<Name> contextPeersFromContext;
        final boolean isAttributeSubject;
        // not required here it seems
//        final List<ImmutableImportHeading> contextHeadings;
        final boolean isLocal;
        final String only;
        final String compositionPattern;
        final String defaultValue;
        final boolean blankZeroes;
        final String headingAlias;

        public ImmutableImportHeading(MutableImportHeading mutableImportHeading) {
            this.heading = mutableImportHeading.heading;
            this.name = mutableImportHeading.name;
            this.peers = mutableImportHeading.peers;
            this.indexForAttribute = mutableImportHeading.indexForAttribute;
            this.indexForChild = mutableImportHeading.indexForChild;
            this.parentNames = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.parentNames));
            this.removeParentNames = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.removeParentNames));
            this.attribute = mutableImportHeading.attribute;
            this.peerCellIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peerCellIndexes));
            this.peersFromContext = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peersFromContext));
            this.contextPeerCellIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.contextPeerCellIndexes));
            this.contextPeersFromContext = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.contextPeersFromContext));
            this.isAttributeSubject = mutableImportHeading.isAttributeSubject;
            /*ArrayList<ImmutableImportHeading> contextHeadings = new ArrayList<>();
            for (MutableImportHeading m : mutableImportHeading.contextHeadings) {
                contextHeadings.add(new ImmutableImportHeading(m));
            }
            this.contextHeadings = Collections.unmodifiableList(contextHeadings);*/
            this.isLocal = mutableImportHeading.isLocal;
            this.only = mutableImportHeading.only;
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

    private String findOrigName(String filePath) {
        //provenance should not show the temporary file name....
        String provFile = filePath.substring(filePath.lastIndexOf("/") + 1);
        String suffix = provFile.substring(provFile.indexOf(".") + 1);
        provFile = provFile.substring(0, provFile.indexOf(".") + 1);
        String finalSuffix = suffix.substring(suffix.indexOf(".") + 1);
        int num = 0;
        try {
            num = Integer.parseInt(suffix.substring(0, 3));
        } catch (Exception ignored) {
        }
        if (num > 0) {
            //sheet from a workbook
            provFile += finalSuffix;
        } else {
            //probably should look for the first number, but usually this would be OK.
            provFile += suffix.substring(0, 3);
        }
        return provFile;
    }

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Sets being as mentioned at the top one of the two files that are needed along with import headers to set up a database ready to load data.
    */

    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user) throws Exception {
        System.out.println("reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        String provFile = findOrigName(filePath);
        azquoMemoryDBConnection.setProvenance(user, "imported", provFile, "");
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
    Edd : it feels like an enum or array could help here but I'm not sure, about 5 are what you might call vanilla, the rest have other conditions*/

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
                String[] parents = childOfString.split(",");//TODO this does not take into account names with commas inside.......
                for (String parent : parents) {
                    heading.parentNames.add(nameService.findOrCreateNameInParent(azquoMemoryDBConnection, parent, null, false));
                }
            }
            // opposite of above
        } else if (REMOVEFROM.startsWith(firstWord)) {
            String removeFromString = readClause(REMOVEFROM, clause).replace(Name.QUOTE + "", "");// child of relates to a name in the database - the hook to existing data
            if (removeFromString.length() == 0) {
                throw new Exception(clause + notUnderstood);
            } else {
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
            // a way of excluding "all lines but". A bit of a hack to deal with data for more than one database in the same file.
        } else if (firstWord.equals(ONLY)) {
            heading.only = readClause(ONLY, clause);
            if (heading.only.length() == 0) {
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
            // in new logic this is the only palce that peers are used in Azquo
            heading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);// todo - this will make the name regardless, should it?
            String peersString = readClause(PEERS, clause);
            if (peersString.startsWith("{")) { // array, typically when creating in the first place, the spreadsheet call will insert after any existing
                if (peersString.contains("}")) {
                    peersString = peersString.substring(1, peersString.indexOf("}"));
                    Collections.addAll(heading.peers, peersString.split(","));
                } else {
                    throw new Exception("Unclosed }");
                }
            }
        } else {
            throw new Exception(firstWord + notUnderstood);
        }
    }

    /*
    headings are clauses separated by semicolons, first is the heading name then onto the extra stuff
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

    /* Used to find component cells for composite values
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
                    return null;// found more than one possibility, return null now
                }
            }
        }
        return toReturn;
    }

    /* Very similar to above, used for finding peer and attribute indexes. not sure of an obvious factor - just the middle lines or does this make things more confusing?
    I'd need an interface between Mutable and Immutable import heading, quite a few more lines of code to reduce this slightly.
    Notable that this function now WON'T find a context heading. This is fine.*/

    private int findMutableHeadingIndex(String nameToFind, List<MutableImportHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        nameToFind = nameToFind.trim();
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().startsWith(nameToFind.toLowerCase() + ","))
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


    // I think the cache is purely a performance thing though it's used for a little logging later (total number of names inserted)

    public Name findOrCreateNameStructureWithCache(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent
        String np = name + ",";
        if (parent != null) {
            np += parent.getId();
        }
        Name found = namesFoundCache.get(np);
        if (found != null) {
            return found;
        }
        found = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, name, parent, local, attributeNames);
        namesFoundCache.put(np, found);
        return found;
    }

    // to make a batch call to the above if there are a list of parents a name should have

    public Name includeInParents(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Set<Name> parents, boolean local, List<String> attributeNames) throws Exception {
        Name child = null;
        if (parents == null || parents.size() == 0) {
            child = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, name, null, local, attributeNames);
        } else {
            for (Name parent : parents) {
                child = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, name, parent, local, attributeNames);
            }
        }
        return child;
    }

    /* Created by EFC to try to improve speed through multi threading. There's still a bottleneck in the initial parsing
    (maybe use a Spliterator?) but it can now batch up simply parsed lines and stack them here for importing. */

    private class BatchImporter implements Runnable {

        private final AzquoMemoryDBConnection azquoMemoryDBConnection;
        private final AtomicInteger valueTracker;
        private int lineNo;
        private final List<List<ImportCellWithHeading>> dataToLoad;
        private final Map<String, Name> namesFoundCache;
        private final List<String> attributeNames;

        public BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valueTracker, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) {
            this.azquoMemoryDBConnection = azquoMemoryDBConnection;
            this.valueTracker = valueTracker;
            this.dataToLoad = dataToLoad;
            this.namesFoundCache = namesFoundCache;
            this.attributeNames = attributeNames;
            this.lineNo = lineNo;
        }

        @Override
        public void run() {
            long trigger = 10;
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
                /*skip any line that has a blank in the first column unless the first column had no header
               of course if the first column has no header and then the second has data but not on this line then it would get loaded
                 happy for the check to remain in here - more stuff for */
                ImportCellWithHeading first = lineToLoad.get(0);
                if ((first.lineValue.length() > 0 || first.immutableImportHeading.heading == null) && (first.immutableImportHeading.only == null || first.lineValue.equals(first.immutableImportHeading.only))) {
                    getCompositeValues(lineToLoad);
                    try {
                        valueTracker.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, lineNo));
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
        final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
        if (fileType.indexOf(" ") > 0) {
            //file type should be first word only
            fileType = fileType.substring(0, fileType.indexOf(" "));
        }
        if (fileType.contains("_")) {
            fileType = fileType.substring(0, fileType.indexOf("_"));
        }
        long track = System.currentTimeMillis();
        char delimiter = ',';
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        // grab the first line to check on delimiters
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
        // It looks for a name for the file type, this name can have headers and/or the definitions for each header
        // in this case looking for a list of headers. Could maybe make this make a bit more sense . . .
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
        boolean skipTopLine = false;
        if (importInterpreter != null) {
             // hack for spark response, I'll leave in here for the moment, it could be useful for others
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
        // finally we might use the headers on the data file, this is notably used when setting up the headers themselves :)
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
        fillInHeaderInformation(azquoMemoryDBConnection, mutableImportHeadings);
        // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
        final List<ImmutableImportHeading> immutableImportHeadings = new ArrayList<>(mutableImportHeadings.size());
        for (MutableImportHeading mutableImportHeading : mutableImportHeadings) {
            immutableImportHeadings.add(new ImmutableImportHeading(mutableImportHeading));
        }
        // having read the headers go through each record
        // now, since this will be multi threaded need to make line objects, Cannot be completely immutable due to the current logic e.g. composite values
        int lineNo = 1; // start at 1, we think of the first line being 1 not 0.
        // pretty vanilla multi threading bits
        ExecutorService executor = Executors.newFixedThreadPool(azquoMemoryDBConnection.getAzquoMemoryDB().getReportFillerThreads());
        AtomicInteger valueTracker = new AtomicInteger(0);
        int batchSize = 100000; // a bit arbitrary, I wonder shuld I go smaller?
        ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<>(batchSize);
        while (lineIterator.hasNext()) { // new Jackson call . . .
            String[] lineValues = lineIterator.next();
            lineNo++;
            List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
            int columnIndex = 0;
            for (ImmutableImportHeading immutableImportHeading : immutableImportHeadings) {
                // intern may save a little memory. Column Index could point past line values for things like composite. Possibly other things but I can't think of them at the moment
                String lineValue = columnIndex < lineValues.length ? lineValues[columnIndex].trim().intern() : "";
                importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue, null));
                columnIndex++;
            }
            //batch it up!
            linesBatched.add(importCellsWithHeading);
            if (linesBatched.size() == batchSize) {
                executor.execute(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFoundCache, attributeNames, lineNo - batchSize)); // line no should be the start
                linesBatched = new ArrayList<>(batchSize);
            }
        }
        // load leftovers
        executor.execute(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFoundCache, attributeNames, lineNo - linesBatched.size()));
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
        System.out.println("---------- names found cache size " + namesFoundCache.size());
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

                if (dividerPos == -1 && col > 0) { // no context headings defined for this one, copy the previous (may well be empty)
                    heading.contextHeadings = headings.get(col - 1).contextHeadings;
                }

                // right, headingDivider, |. It seems to work backwards, stacking context headings for this heading, now this is against the heading as opposed to putting them in the same array
                while (dividerPos >= 0) {
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
                if (head.length() > 0) {
                    interpretHeading(azquoMemoryDBConnection, head, heading, attributeNames);
                }
                headings.add(heading);
            } else {// add an empty one, the headings ArrayList should match the number of headings even if that heading is empty
                headings.add(new MutableImportHeading());
            }
            col++;
        }
    }

    // sort peer headings, attribute headings, child of remove from, parent of, context peer headings
    // called right after readHeaders, try to do as much checking as possible here. Some of this logic was unnecessarily being done each line

    private void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        int currentHeadingIndex = 0;
        // use a for loop like this as we need the index
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading mutableImportHeading = headings.get(headingNo);
            if (mutableImportHeading.heading != null) {
                // ok find the indexes of peers and get shirty if you can't find them
                // this had a check that is wasn't a context heading so presumably we don't need to do this to context headings
                if (mutableImportHeading.name != null && mutableImportHeading.peers.size() > 0) { // has peers (of course) and a name. Little unsure on the name criteria - could one refine peers against no name?
                    for (String peer : mutableImportHeading.peers) {
                        //three possibilities to find the peer:
                        int peerHeadingIndex;
                        if (peer.equalsIgnoreCase("this")) { // should be self explanatory
                            peerHeadingIndex = headingNo;
                        } else { // standard lookup criteria - matching name that's not an attribute
                            peerHeadingIndex = findMutableHeadingIndex(peer, headings);
                        }
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
                                            contextCheck.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, contextCheck.heading, null, false, languages);
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
         worth noting that after the above the headings were made immutable so the state of the headers should be the same
         Since the above loop will populate some context names I'm going to leave this as a separate loop below
         */
        for (MutableImportHeading mutableImportHeading : headings) {
            if (mutableImportHeading.contextHeadings.size() > 0 && mutableImportHeading.name != null) { // ok so some context headings and a name for this column? I guess as in not an attribute column for example
                MutableImportHeading contextPeersHeading = null;
                List<Name> contextNames = new ArrayList<>();
                // gather the context names and peers
                for (MutableImportHeading immutableImportHeading : mutableImportHeading.contextHeadings) {
                    contextNames.add(immutableImportHeading.name);
                    if (!immutableImportHeading.peers.isEmpty()) {
                        contextPeersHeading = immutableImportHeading;
                    }
                }
                contextNames.add(mutableImportHeading.name);// add this name onto the context stack
                if (contextPeersHeading != null) { // a value cell HAS to have peers, context headings are only for values
                    final Set<Name> namesForValue = new HashSet<>(); // the names we're preparing for values
                    namesForValue.add(contextPeersHeading.name);// ok the "defining" name with the peers.
                    final Set<Integer> possiblePeerIndexes = new HashSet<>(); // the names we're preparing for values
                    boolean foundAll = true;
                    for (String peer : contextPeersHeading.peers) { // ok so a value with peers
                        if (peer.equalsIgnoreCase("this")) {
                            possiblePeerIndexes.add(0); // what if the index was the first column?? If so a dangerous hack! todo - confirm with WFC
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
    }

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) throws Exception {
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
            // ok local is done up here and not local below so its only being called once. Parameters the same
            if (importCellWithHeading.immutableImportHeading.indexForChild != -1 && importCellWithHeading.immutableImportHeading.isLocal) {
                // local and it is a parent of another heading (has child heading), inside this function it will use the child heading set up
                handleParent(azquoMemoryDBConnection, namesFoundCache, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        long tooLong = 2000000;
        long time = System.nanoTime();
        for (ImportCellWithHeading cell : cells) {
            /* ok the gist seems to be that there's peers as defined in a context item in which case it's looking in context items and peers
            a notable thing about context : after something has been added to context names it stays there for subsequent cells.
            again this logic is dependant on headers, it really needn't be done every time
            ok context peers will look for other columns and in the context for names where it allows members off sets e.g. a peer might be year so 2014, 2015 etc will be ok.
             */
            boolean peersOk = true;
            final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
            if (!cell.immutableImportHeading.contextPeersFromContext.isEmpty() || !cell.immutableImportHeading.contextPeerCellIndexes.isEmpty()) { // new criteria,this means there are context peers to deal with
                namesForValue.addAll(cell.immutableImportHeading.contextPeersFromContext);// start with the ones we have to hand, including the main name
                for (int peerCellIndex : cell.immutableImportHeading.contextPeerCellIndexes) {
                    // hack from the above meaning this cell' heading name . . .hang on a minute what if the cell index was 0 for another reason??
                    // And surely the heading's name (rather than line name) is part of the contextPeersFromContext set anyway?? todo - confirm with WFC
                    if (peerCellIndex == 0) {
                        namesForValue.add(cell.immutableImportHeading.name);
                    } else {
                        ImportCellWithHeading peerCell = cells.get(peerCellIndex);
                        if (peerCell.lineName != null) {// If the current cell is dealt with by "this" which needs investigating (see above)
                            // then this would only work for previous columns as otherwise the line name could only have been populated byt the handle parent above if a local "child of" clause.
                            namesForValue.add(peerCell.lineName);
                        } else {
                            peersOk = false;
                            break;
                        }
                    }
                }
            } else if (!cell.immutableImportHeading.peerCellIndexes.isEmpty() || !cell.immutableImportHeading.peersFromContext.isEmpty()) { // can't have more than one peers defined so if not from context check standard peers
                // check for peers as defined in peerHeadings, this will create peers if it can't find them. It will fail if a peer heading has no line value
                // - thus peers have to be to the left on the line unless they had a local "child of" caluse meaning it might get set in the "handleParent" above? Another check with WFC - todo
                // should we inline this function? It's only called here?
                peersOk = findPeers(azquoMemoryDBConnection, namesFoundCache, cell, cells, namesForValue, attributeNames);
            }
            if (peersOk && !namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                String value = cell.lineValue;
                if (cell.immutableImportHeading.blankZeroes && isZero(value)) value = "";
                if (value.trim().length() > 0) { // no point storing if there's no value
                    valueCount++;
                    // finally store our value and names for it
                    valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                }
            }
            // ok that's the peer/value stuff done I think, now onto attributes
            if (cell.immutableImportHeading.indexForAttribute >= 0 && cell.immutableImportHeading.attribute != null
                    && cell.lineValue.length() > 0
                    && (!cell.immutableImportHeading.attribute.equalsIgnoreCase(dateLang) || (isADate(cell.lineValue) != null))) {
                // funnily enough no longer using attributes
                // inline it?
                handleAttribute(azquoMemoryDBConnection, namesFoundCache, cell, cells);
            }
            // and child heading (as defined by the "parent of" clause), not local this time - handle attribute and find peers might have set the cell name? todo - clarify with WFC the before after if local or not thing
            if (cell.immutableImportHeading.indexForChild != -1 && !cell.immutableImportHeading.isLocal) {
                handleParent(azquoMemoryDBConnection, namesFoundCache, cell, cells, attributeNames, lineNo);
            }
            // names which are parents of this value,as defined by "child of" todo - hang on a minute wasn't this dealt with in handle parent???
            if (!cell.immutableImportHeading.parentNames.isEmpty()) {
                if (cell.lineName != null) {
                    for (Name parent : cell.immutableImportHeading.parentNames) {
                        parent.addChildWillBePersisted(cell.lineName);
                    }
                } else {
                    String childNameString = cell.lineValue;
                    if (childNameString.length() > 0) {
                        for (Name parent : cell.immutableImportHeading.parentNames) {
                            cell.lineName = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childNameString, parent, cell.immutableImportHeading.isLocal, attributeNames);
                        }
                    }
                }
            }
            // as above but for remove from
            if (!cell.immutableImportHeading.removeParentNames.isEmpty()) {
                if (cell.lineName != null) {
                    for (Name remove : cell.immutableImportHeading.removeParentNames) {
                        remove.removeFromChildrenWillBePersisted(cell.lineName);
                    }
                }
            }
            long now = System.nanoTime();
            if (now - time > tooLong) {
                System.out.println(cell.immutableImportHeading.heading + " took " + (now - time));
            }
            time = System.nanoTime();
        }
        return valueCount;
    }

    // factored to make applying this to context headings easier

    private void fillAttributeAndParentOfForHeading(MutableImportHeading mutableImportHeading, List<MutableImportHeading> headings) throws Exception {
        if (mutableImportHeading.attribute != null) { // && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
            String headingName = mutableImportHeading.heading;
            if (mutableImportHeading.headingAlias != null) {// a way for a heading to have an alias, not completely clear on the usage but I'm guessing by this point the alias is no longer important so overwrite it with the equals value
                headingName = mutableImportHeading.headingAlias;
            }
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
            //error = findTopParent(azquoMemoryDBConnection, importHeading, headings, attributeNames);
            // if (error.length() > 0) return error;
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
    // This is effectively the action derived from the "child of" and "parent of" clause, called before the bulk of interpretLine if local and after it if not
    private void handleParent(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int lineNo) throws Exception {
        if (cellWithHeading.lineValue.length() == 0) { // so nothing to do
            return;
        }
        if (cellWithHeading.lineValue.contains(",") && !cellWithHeading.lineValue.contains(Name.QUOTE + "")) {//beware of treating commas in cells as set delimiters....
            cellWithHeading.lineValue = Name.QUOTE + cellWithHeading.lineValue + Name.QUOTE;
        }
        if (cellWithHeading.lineName != null) { // This function is called in two places in interpret line, an either or based on local or not, if local this will be null, if not then line name will probably have been set
            // definitely not local I'd say, but regardless the name exists so local is a moot point, put it in parents
            if (!cellWithHeading.immutableImportHeading.parentNames.isEmpty()) {
                for (Name parent : cellWithHeading.immutableImportHeading.parentNames) { // apparently there can be multiple childofs, put the name for the line in th appropriate sets, pretty vanilla based off the parents set up
                    parent.addChildWillBePersisted(cellWithHeading.lineName);
                }
            }
        } else { // probably local (though not necessarily!) call findOrCreateNameInParent for all the parents passing the local flag
            cellWithHeading.lineName = includeInParents(azquoMemoryDBConnection, namesFoundCache, cellWithHeading.lineValue
                    , cellWithHeading.immutableImportHeading.parentNames, cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(cellWithHeading.immutableImportHeading, attributeNames));
        }
        // ok that's child of (as in for names) done, now for "parent of"

        ImportCellWithHeading childCell = cells.get(cellWithHeading.immutableImportHeading.indexForChild);
        if (childCell.lineValue.length() == 0) {
            throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.lineValue);
        }

        // ok got the child cell, need to find the child cell name to add it to this cell's children
        // I think here's it's trying to add to the cells name
        if (childCell.lineName == null) {
            // can findOrCreateNameStructureWithCache return null? I'm nto sure it can, does the following call to the same function make any sense? todo - ask wfc
            childCell.lineName = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childCell.lineValue, cellWithHeading.lineName
                    , cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(childCell.immutableImportHeading, attributeNames));
        }
        // and it if can't it adds to the heading's name
        if (cellWithHeading.lineName == null) {
            cellWithHeading.lineName = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, cellWithHeading.lineValue, cellWithHeading.immutableImportHeading.name,
                    cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(cellWithHeading.immutableImportHeading, attributeNames));
        }
        cellWithHeading.lineName.addChildWillBePersisted(childCell.lineName);
    }

    // only called in one place, could inline

    public void handleAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cell, List<ImportCellWithHeading> cells) throws Exception {
        ImportCellWithHeading identityCell = cells.get(cell.immutableImportHeading.indexForAttribute); // get our source cell
        if (identityCell.lineName == null) { // no name on the cell I want to set the attribute on
            // I guess for the first attribute, if not referencing another line the default display name would be the attribute value and then the attribute value would be set also (new name and attribute set at the same time)
            if (identityCell.lineValue.length() == 0) { // and no line value for the line I want to set the attribute on, can't find the name, nothing to do!
                return;
            }
            List<String> localAttributes = new ArrayList<>();
            if (identityCell.immutableImportHeading.attribute != null && !identityCell.immutableImportHeading.attribute.equals(dateLang)) {
                localAttributes.add(identityCell.immutableImportHeading.attribute);
            } else {
                localAttributes.add(Constants.DEFAULT_DISPLAY_NAME);
            }
            identityCell.lineName = includeInParents(azquoMemoryDBConnection, namesFound, identityCell.lineValue, identityCell.immutableImportHeading.parentNames, false, localAttributes);
        }
        // should we be checking line value not empty?
        identityCell.lineName.setAttributeWillBePersisted(cell.immutableImportHeading.attribute, cell.lineValue);
    }

    // ok what's notable here is that this will create names to complete the peers if it can't find them
    // this is called per line in ony one place, tempted to inline

    private boolean findPeers(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, ImportCellWithHeading cell, List<ImportCellWithHeading> cells, Set<Name> namesForValue, List<String> attributeNames) throws Exception {
        //ImportHeading headingWithPeers = heading;
        boolean hasRequiredPeers = true;
        namesForValue.add(cell.immutableImportHeading.name); // the one at the top of this headingNo, the name with peers.
        // the old logic added the context peers straight in so I see no problem doing this here - this is what might be called inherited peers, from a col to the left.
        // On the col where context peers are defined normal peers should not be defined or used
        namesForValue.addAll(cell.immutableImportHeading.peersFromContext);
        // Ok I had to stick to indexes to get the cells
        for (Integer peerCellIndex : cell.immutableImportHeading.peerCellIndexes) { // go looking for non context peers
            ImportCellWithHeading peerCell = cells.get(peerCellIndex); // get the cell
            if (peerCell.lineName == null) {// then try and create the name
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
                    peerCell.lineName = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, peerCell.lineValue, null, false, peerLanguages);
                }
            }
            // if we created the name (or it was there already) add to the set
            // if it somehow was null should we return false?? todo
            if (peerCell.lineName != null) {
                namesForValue.add(peerCell.lineName);
            }
            //namesForValue.add(nameService.findOrCreateName(azquoMemoryDBConnection,peerVal + "," + peer.getName())) ;
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