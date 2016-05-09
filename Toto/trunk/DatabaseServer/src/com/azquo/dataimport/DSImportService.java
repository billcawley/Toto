package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
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
 * The cell on a line can be a value or an attribute or a name.
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
    private static final String EXISTING = "existing"; // only works in in context of child of
    // essentially using either of these keywords switches to pivot mode where a name is created from the line number and in a set called the name of the file, uploading successive files with the same name would of course cause problems for this system
    private static final String LINEHEADING = "lineheading";//lineheading and linedata are shortcuts for data destined for a pivot table
    private static final String LINEDATA = "linedata";

    // these two are not for clauses, it's to do with reading the file in the first place, do we read the headers or not, how many lines to skip before data
    private static final String HEADINGSSTRING = "HEADINGS";
    private static final String SKIPLINESSTRING = "SKIPLINES";
    // new functionality for pre processing of the file to be handed to a groovy script
    private static final String GROOVYPROCESSOR = "GROOVYPROCESSOR";
    /*
    To multi thread I wanted this to be immutable but there are things that are only set after in context of other headings so I can't do this initially.
    No problem, make this very simple and mutable then have an immutable version for the multi threaded stuff which is held against line.
    */

    private class MutableImportHeading {
        // the name of the heading - often referenced by other headings in clauses e.g. parent of
        String heading = null;
        // the Azquo Name that might be set on the heading
        Name name = null;
        // this class used to use the now removed peers against the name object, in its absence just put a set here, and this set simply refers to headings which may be names or not
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
        // result of the attribute clause. Notable that "." is replaced with ;attribute
        String attribute = null;
        //should we try to treat the cell as a date?
        boolean isDate = false;
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
        // when using the heading divider (a pipe at the moment) this indicates context headings which are now stacked against this heading
        List<MutableImportHeading> contextHeadings = new ArrayList<>();
        // Affects child of and parent of clauses - the other heading is local in the case of parent of and this one in the case of child of. Local as in Azquo name logic.
        boolean isLocal = false;
        // If `only` is specified on the first heading, the import will ignore any line that does not have this line value. Typically to deal with a file of mixed data where we want only some to go in the database.
        String only = null;
        /* to make the line value a composite of other values. Syntax is pretty simple replacing anything in quotes with the referenced line value
        `a column name`-`another column name` might make 1233214-1234. Such columns would probably be at the end,
        they are virtual in the sense that these values are made on uploading they are not there in the source file though the components are.
        A newer use of this is to create name->name2->name3, a name structure in a virtual column at the end
        also supports left, right, mid Excel string functions*/
        String compositionPattern = null;
        // a default value if the line value is blank
        String defaultValue = null;
        // don't import zero values
        boolean blankZeroes = false;
        // is this a column representing names (as opposed to values or attributes). Derived from parent of child of and being referenced by other headings, it's saying : does name, the variable above, need to be populated?
        boolean lineNameRequired = false;
        /* used in context of "parent of". Can be blank in which case it means that the child can't have two siblings as parents, this heading will override existing parents
        , if it has a value it references a set higher up e.g. if a product is being moved in a structure (this heading is parent of the product) with many levels then the
        set referenced in the exclusive clause might be "Categories", the top set, so that the product would be removed from any names in Categories before being added to this heading*/
        String exclusive = null;
        // in context of childof - only load the line if this name is in the set already
        boolean existing = false;
    }

    /* I see no reason for getters here. Class members only, saves a load of space. Note added later : getters and setters may make the code clearer though this could be done by better names also I think
    From a purely pragmatic point of view this class is not necessary but I'm very keen to make sure that heading info is fixed before data loading - possible errors resulting from modifying the mutable
    headings could be a real pain, this will stop that.
     */
    private class ImmutableImportHeading {
        final String heading;
        final Name name;
        final int indexForAttribute;
        final int indexForChild;
        // ok the set will be fixed, I suppose names can be modified but they should be thread safe
        final Set<Name> parentNames;
        final String attribute;
        final boolean isDate;
        final Set<Integer> peerCellIndexes;
        final Set<Name> peersFromContext;
        final Set<Integer> contextPeerCellIndexes;
        final Set<Name> contextPeersFromContext;
        final boolean isAttributeSubject;
        final boolean isLocal;
        final String only;
        final String compositionPattern;
        final String defaultValue;
        final boolean blankZeroes;
        final boolean lineNameRequired;
        final String exclusive;
        final boolean existing;

        ImmutableImportHeading(MutableImportHeading mutableImportHeading) {
            this.heading = mutableImportHeading.heading;
            this.name = mutableImportHeading.name;
            this.indexForAttribute = mutableImportHeading.indexForAttribute;
            this.indexForChild = mutableImportHeading.indexForChild;
            this.parentNames = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.parentNames));
            this.attribute = mutableImportHeading.attribute;
            this.isDate = mutableImportHeading.isDate;
            this.peerCellIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peerCellIndexes));
            this.peersFromContext = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.peersFromContext));
            this.contextPeerCellIndexes = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.contextPeerCellIndexes));
            this.contextPeersFromContext = Collections.unmodifiableSet(new HashSet<>(mutableImportHeading.contextPeersFromContext));
            this.isAttributeSubject = mutableImportHeading.isAttributeSubject;
            this.isLocal = mutableImportHeading.isLocal;
            this.only = mutableImportHeading.only;
            this.compositionPattern = mutableImportHeading.compositionPattern;
            this.defaultValue = mutableImportHeading.defaultValue;
            this.blankZeroes = mutableImportHeading.blankZeroes;
            this.lineNameRequired = mutableImportHeading.lineNameRequired;
            this.exclusive = mutableImportHeading.exclusive;
            this.existing = mutableImportHeading.existing;
        }
    }

    // going to follow the pattern above, no getters
    // I'd have liked to make this immutable but existing logic for things like composite mean this may be changed before loading

    private class ImportCellWithHeading {
        private final ImmutableImportHeading immutableImportHeading;
        private String lineValue;// prefix  line to try to avoid confusion
        private Name lineName;

        ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value, Name name) {
            this.immutableImportHeading = immutableImportHeading;
            this.lineValue = value;
            this.lineName = name;
        }
    }

    // Switched to Java 8 API calls. I zapped ukdf5, it was a duplicate of 3.

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate isADate(String maybeDate) {
        LocalDate date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf4);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf3);
        if (date != null) return date;
        return tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
    }

    // get a file name for provenance. Used only once could inline

    private String findOrigName(String filePath) {
        //provenance should not show the temporary file name....
        int dirEnd = filePath.lastIndexOf("/");
        if (dirEnd < 0) {
            dirEnd = filePath.lastIndexOf("\\");
        }
        String provFile = filePath.substring(dirEnd + 1);
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
            provFile += finalSuffix;
        }
        return provFile;
    }

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Sets being as mentioned at the top one of the two files that are needed along with import headers to set up a database ready to load data.
    */

    public String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        System.out.println("reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        String provFile = findOrigName(filePath);
        if (!isSpreadsheet) {
            //fileType is the truncated file name
            provFile = fileType + filePath.substring(filePath.indexOf("."));
        }
        azquoMemoryDBConnection.setProvenance(user, "imported", provFile, "");
        return readPreparedFile(azquoMemoryDBConnection, filePath, fileType, attributeNames, persistAfter, isSpreadsheet);
    }

    public String readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        System.out.println("Preparing to import, lock test");
        azquoMemoryDBConnection.lockTest();
        System.out.println("Import lock passed");
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        String toReturn;
        if (fileType.toLowerCase().startsWith("sets")) {
            toReturn = setsImport(azquoMemoryDBConnection, new FileInputStream(filePath), attributeNames, fileType);
        } else {
            toReturn = valuesImport(azquoMemoryDBConnection, filePath, fileType, attributeNames, isSpreadsheet);
        }
        if (persistAfter) { // get back to the user straight away. Should not be a problem, multiple persists would be queued. The only issue is of changes while persisting, need to check this in the memory db.
            new Thread(azquoMemoryDBConnection::persist).start();
        }
        return toReturn;
    }

    /* This is called for all the ; separated clauses in a header e.g. Gender; parent of Customer; child of Genders
    Called multiple times per header. I assume clause is trimmed! */

    private void interpretClause(final AzquoMemoryDBConnection azquoMemoryDBConnection, final MutableImportHeading heading, final String clause) throws Exception {
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
                    heading.parentNames.add(nameService.findOrCreateNameInParent(azquoMemoryDBConnection, parent, null, false));
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
            case DEFAULT: // if there's no value on the line a default
                if (result.length() > 0) {
                    heading.defaultValue = result;
                }
                break;
            case PEERS: // in new logic this is the only place that peers are used in Azquo
                heading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);
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
            case EXCLUSIVE:// it can be blank OR have a value
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

    private void interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, MutableImportHeading heading, List<String> attributeNames) throws Exception {
        StringTokenizer clauses = new StringTokenizer(headingString, ";");
        heading.heading = clauses.nextToken().replace(Name.QUOTE + "", ""); // the heading name being the first
        heading.name = nameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames); // at this stage, look for a name, but don't create it unless necessary
        // loop over the clauses making sense and modifying the heading object as you go
        while (clauses.hasMoreTokens()) {
            interpretClause(azquoMemoryDBConnection, heading, clauses.nextToken().trim());
        }
    }

    /* Used to find component cells for composite values
    The extra logic aside simply from heading matching is the identifier flag (multiple attributes mean many headings with the same name)
    Or attribute being null (thus we don't care about identifier)
    */

    private ImportCellWithHeading findCellWithHeading(String nameToFind, List<ImportCellWithHeading> importCellWithHeadings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        ImportCellWithHeading toReturn = null;
        for (ImportCellWithHeading importCellWithHeading : importCellWithHeadings) {
            ImmutableImportHeading heading = importCellWithHeading.immutableImportHeading;
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind))
                    && (heading.isAttributeSubject || heading.attribute == null || heading.isDate)) {
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


    // I think the cache is purely a performance thing though it's used for a little logging later (total number of names inserted)

    private Name findOrCreateNameStructureWithCache(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent - note it will fail if the name was changed e.g. parents removed by exclusive but that's not a problem
        String np = name + ",";
        if (parent != null) {
            np += parent.getId();
        }
        np += attributeNames.get(0);
        Name found = namesFoundCache.get(np);
        if (found != null) {
            return found;
        }
        found = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, name, parent, local, attributeNames);
        namesFoundCache.put(np, found);
        return found;
    }

    // to make a batch call to the above if there are a list of parents a name should have

    private Name includeInParents(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Set<Name> parents, boolean local, List<String> attributeNames) throws Exception {
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

    /* Created by EFC to try to improve speed through multi threading.
     The basic file parsing is single threaded but since this can start while later lines are being read I don't think this is a problem.
     That is to say on a large file the threads will start to stack up fairly quickly
     Adapted to Callable from Runnable
     */

    private class BatchImporter implements Callable<Void> {

        private final AzquoMemoryDBConnection azquoMemoryDBConnection;
        private final AtomicInteger valueTracker;
        private int lineNo;
        private final List<List<ImportCellWithHeading>> dataToLoad;
        private final Map<String, Name> namesFoundCache;
        private final List<String> attributeNames;

        BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valueTracker, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) {
            this.azquoMemoryDBConnection = azquoMemoryDBConnection;
            this.valueTracker = valueTracker;
            this.dataToLoad = dataToLoad;
            this.namesFoundCache = namesFoundCache;
            this.attributeNames = attributeNames;
            this.lineNo = lineNo;
        }

        @Override
        public Void call() throws Exception {
            long trigger = 10;
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
                /* skip any line that has a blank in the first column unless the first column had no header
                   of course if the first column has no header and then the second has data but not on this line then it would get loaded
                   happy for the check to remain in here - more stuff for the multi threaded bit */
                ImportCellWithHeading first = lineToLoad.get(0);
                if (first.lineValue.length() > 0 || first.immutableImportHeading.heading == null) {
                    List<String> languages = attributeNames;
                    if (getCompositeValuesCheckOnlyAndExisting(azquoMemoryDBConnection, lineToLoad, lineNo, languages)) {
                        try {
                            // valueTracker simply the number of values imported
                            valueTracker.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, lineNo));
                        } catch (Exception e) {
                            azquoMemoryDBConnection.addToUserLogNoException(e.getMessage(), true);
                            throw e;
                        }
                        Long now = System.currentTimeMillis();
                        if (now - time > trigger) {
                            System.out.println("line no " + lineNo + " time = " + (now - time) + "ms");
                        }
                        time = now;
                    }
                }
                lineNo++;
            }
            azquoMemoryDBConnection.addToUserLogNoException("Batch finishing : " + DecimalFormat.getInstance().format(lineNo) + " imported.", true);
            azquoMemoryDBConnection.addToUserLogNoException("Values Imported : " + DecimalFormat.getInstance().format(valueTracker), true);
            return null;
        }
    }

    /* calls header validation and batches up the data with headers ready for batch importing
    Get headings first, they can be in a name or in the file, if in a file then they will be set on a name. The key is to set up info in names so a file can be uploaded from a client "as is"
    There was a "name per heading" option that might have facilitated columns being shifted around but thi seems to have been removed
    */

    private String valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames, boolean isSpreadsheet) throws Exception {
        try {
            // Preparatory stuff
            String fileName = "";
            if (fileType.length() > 10) {
                fileName = fileType;
            }
            // Local cache of names just to speed things up, the name name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
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
                return "First line blank"; //if he first line is blank, ignore the sheet
            }
            // now we know the delimiter can CSV read, I've read jackson is pretty quick
            CsvMapper csvMapper = new CsvMapper();
            csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
            CsvSchema schema = csvMapper.schemaFor(String[].class)
                    .withColumnSeparator(delimiter)
                    .withLineSeparator("\n");
            if (delimiter == '\t') {
                schema = schema.withoutQuoteChar();
            }

            // keep this one separate so it can be closed at the end
            Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
            while (!isSpreadsheet && importInterpreter == null && (fileType.contains(" ") || fileType.contains("_"))) { //we can use the import interpreter to import different files by suffixing the name with _ or a space and suffix.
                //There may, though, be separate interpreters for A_B_xxx and A_xxx, so we try A_B first
                if (fileType.contains(" ")) {
                    fileType = fileType.substring(0, fileType.lastIndexOf(" "));
                } else {
                    fileType = fileType.substring(0, fileType.lastIndexOf("_"));
                }
                importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
            }
            if (importInterpreter != null && importInterpreter.getAttribute(GROOVYPROCESSOR) != null) {
                System.out.println("Groovy found! Running  . . . ");
                Object[] groovyParams = new Object[3];
                groovyParams[0] = filePath;
                groovyParams[1] = azquoMemoryDBConnection;
                groovyParams[2] = nameService;
                GroovyShell shell = new GroovyShell();
                try {
                    final Script script = shell.parse(importInterpreter.getAttribute(GROOVYPROCESSOR));
                    filePath = (String) script.invokeMethod("fileProcess", groovyParams);
                } catch (GroovyRuntimeException e) {
                    e.printStackTrace();
                    throw new Exception("groovy error " + e.getMessage());
                }
                System.out.println("Groovy done.");

            }
            MappingIterator<String[]> originalLineIterator = csvMapper.reader(String[].class).with(schema).readValues(new File(filePath));
            Iterator<String[]> lineIterator = originalLineIterator; // for the data, it might be reassigned in the case of transposing
            String[] headers = null;
            // It looks for a name for the file type, this name can have headers and/or the definitions for each header
            // in this case looking for a list of headers. Could maybe make this make a bit more sense . . .
            int skipLines = 0;
            if (!isSpreadsheet && importInterpreter != null) {
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
                String importHeaders = importInterpreter.getAttribute(HEADINGSSTRING);
                if (importHeaders == null) {//no longer used - Nov 2015
                    // todo - get rid of this and change to an attribute like transpose to skip a number of lines
                    importHeaders = importInterpreter.getAttribute(HEADINGSSTRING + "1");
                    if (importHeaders != null) {
                        skipLines = 1;
                    }
                }
                if (importHeaders != null) {
                    String skipLinesSt = importInterpreter.getAttribute(SKIPLINESSTRING);
                    if (skipLinesSt != null) {
                        try {
                            skipLines = Integer.parseInt(skipLinesSt);
                        } catch (Exception ignored) {
                        }
                    }
                    System.out.println("has headers " + importHeaders);
                    headers = importHeaders.split("¬"); // a bit arbitrary, would like a better solution if I can think of one.
                }
            }
            // finally we might use the headers on the data file, this is notably used when setting up the headers themselves :)
            if (headers == null) {
                headers = lineIterator.next();
                if (isSpreadsheet) {
                    Name importSheets = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All import sheets", null, false);
                    Name dataImportThis = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "DataImport " + fileType, importSheets, true);
                    StringBuilder sb = new StringBuilder();
                    for (String header : headers) {
                        sb.append(header).append("¬");
                    }
                    dataImportThis.setAttributeWillBePersisted(HEADINGSSTRING, sb.toString());
                    dataImportThis.setAttributeWillBePersisted(SKIPLINESSTRING, "1");//  currently assuming one line - may need to adjust
                }
            } else {
                while (skipLines-- > 0) {
                    lineIterator.next();
                }
            }
        /*
        End preparatory stuff
        readHeaders is about creating a set of ImportHeadings
        notable that internally it might use attributes from the relevant data import name to supplement the header information
        to be more specific : that name called by "dataimport " + fileType has been hit for its "HEADINGSSTRING" attribute already to produce headers
        but it could be asked for something more specific according to the header name.
        This method where columns can be called by name will look nicer in the heading set up but it requires data files to have headings.
        */
            List<MutableImportHeading> mutableImportHeadings = new ArrayList<>();
            // read the clauses, assign the heading.name if you can find it, add on the context headings
            readHeaders(azquoMemoryDBConnection, headers, mutableImportHeadings, fileType, attributeNames, fileName);
            // further information put into the ImportHeadings based off the initial info
            fillInHeaderInformation(azquoMemoryDBConnection, mutableImportHeadings);
            // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
            final List<ImmutableImportHeading> immutableImportHeadings = new ArrayList<>(mutableImportHeadings.size());
            immutableImportHeadings.addAll(mutableImportHeadings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList())); // not sure if stream is that clear here but it gives me a green light from IntelliJ
            // having read the headers go through each record
            // now, since this will be multi threaded need to make line objects, Cannot be completely immutable due to the current logic e.g. composite values
            int lineNo = 1; // start at 1, we think of the first line being 1 not 0.
            // pretty vanilla multi threading bits
            AtomicInteger valueTracker = new AtomicInteger(0);
            int batchSize = 100000; // a bit arbitrary, I wonder should I go smaller?
            ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<>(batchSize);
            int colCount = immutableImportHeadings.size();
            while (immutableImportHeadings.get(colCount - 1).compositionPattern != null)
                colCount--;
            List<Future> futureBatches = new ArrayList<>();

            while (lineIterator.hasNext()) {
                String[] lineValues = lineIterator.next();
      /*  CHANGE OF RULES - WE'LL NEED TO MAKE IT EXPLICIT IF WE ARE TO ACCEPT CARRIAGE RETURNS IN THE MIDDLE OF LINES
          EXPORTS FROM MICROSOFT SQL TRUNCATE NULLS AT THE END OF LINES

            while (lineValues.length < colCount && lineIterator.hasNext()) { // if there are carriage returns in columns, we'll assume on this import that every line must have the same number of columns (may need an option later to miss this)
                String[] additionalValues = lineIterator.next();
                if (additionalValues.length == 0) break;
                if (additionalValues.length >= colCount) {
                    lineValues = additionalValues;
                } else {
                    lineValues[lineValues.length - 1] += "\n" + additionalValues[0];
                    lineValues = (String[]) ArrayUtils.addAll(lineValues, ArrayUtils.subarray(additionalValues, 1, additionalValues.length)); // not sure I like this cast here, will have a think
                }
            }
            */
                lineNo++;
                List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
                int columnIndex = 0;
                boolean corrupt = false;
                for (ImmutableImportHeading immutableImportHeading : immutableImportHeadings) {
                    // intern may save a little memory. Column Index could point past line values for things like composite. Possibly other things but I can't think of them at the moment
                    String lineValue = columnIndex < lineValues.length ? lineValues[columnIndex].trim().intern().replace("~~", "\r\n") : "";//hack to replace carriage returns from Excel sheets
                    if (lineValue.equals("\"")) {
                        //this has happened
                        corrupt = true;
                        break;
                    }
                    if (lineValue.startsWith("\"") && lineValue.endsWith("\""))
                        lineValue = lineValue.substring(1, lineValue.length() - 1).replace("\"\"", "\"");//strip spurious quote marks inserted by Excel
                    //remove spurious quotes (put in when Groovyscript sent)
                    importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue, null));
                    columnIndex++;
                }
                if (!corrupt) {
                    //batch it up!
                    linesBatched.add(importCellsWithHeading);
                    // rack up the futures to check in a mo to see that things are complete
                    if (linesBatched.size() == batchSize) {
                        futureBatches.add(AzquoMemoryDB.mainThreadPool.submit(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFoundCache, attributeNames, lineNo - batchSize)));// line no should be the start
                        linesBatched = new ArrayList<>(batchSize);
                    }
                }
            }
            // load leftovers
            int loadLine = lineNo - linesBatched.size(); // NOT batch size!
            if (loadLine < 1) loadLine = 1; // could it ever be? Need to confirm this check
            futureBatches.add(AzquoMemoryDB.mainThreadPool.submit(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFoundCache, attributeNames, loadLine)));// line no should be the start
            // check all work is done and memory is in sync
            for (Future<?> futureBatch : futureBatches) {
                futureBatch.get(1, TimeUnit.HOURS);
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
            String toReturn = fileType + " imported. Dataimport took " + (System.currentTimeMillis() - track) / 1000 + " second(s) for " + (lineNo - 1) + " lines<br/>\n";
            azquoMemoryDBConnection.addToUserLogNoException(toReturn, true);
            System.out.println("---------- names found cache size " + namesFoundCache.size());
            return toReturn;
        } catch (Exception e) {
            // the point of this is to add the file type to the exception message
            e.printStackTrace();
            Throwable t = e;
            if (t.getCause() != null){ // once should do it, unwrap to reduce java.lang.exception being shown to the user
                t = t.getCause();
            }
            throw new Exception(fileType + " : " + t.getMessage());
        }
    }

    // run through the headers. Mostly this means running through clauses,

    private void readHeaders(AzquoMemoryDBConnection azquoMemoryDBConnection, String[] headers, List<MutableImportHeading> headings, String fileType, List<String> attributeNames, String fileName) throws Exception {
        int col = 0;
        //  if the file is of type (e.g.) 'sales' and there is a name 'dataimport sales', this is used as an interpreter.
        //  It need not interpret every column heading, but any attribute of the same name as a column heading will be used.
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
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
                    Name headset = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All headings", null, false);
                    // create the set the line heading name will go in
                    nameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), headset, true);//note - headings in different import files will be considered the same if they have the same name
                    head = head.replace(LINEHEADING, ";parent of LINENO;child of " + headname.replace("_", " ") + ";language " + headname);
                }
                if (head.contains(LINEDATA) && head.indexOf(";") > 0) {
                    pivot = true;
                    String headname = head.substring(0, head.indexOf(";"));
                    Name alldataset = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All data", null, false);
                    Name thisDataSet = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, fileType + " data", alldataset, false);
                    // create the set the line data name will go in
                    nameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), thisDataSet, false);
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
                            if (len < fileName.length()) {
                                replacement = fileName.substring(fileName.length() - len);
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
            Name allLines = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All lines", null, false);
            // create the name based on this file name where we put the names generated to deal with pivot tables. Note this means uploading a file with the same name and different data causes havok!
            nameService.findOrCreateNameInParent(azquoMemoryDBConnection, fileType + " lines", allLines, false);
            MutableImportHeading pivotHeading = new MutableImportHeading();
            interpretHeading(azquoMemoryDBConnection, "LINENO;composition LINENO;language " + fileType + ";child of " + fileType + " lines", pivotHeading, attributeNames);
            headings.add(pivotHeading);
        }
    }

    // sort peer headings, attribute headings, child of, parent of, context peer headings
    // called right after readHeaders, try to do as much checking as possible here. Some of this logic was unnecessarily being done each line

    private void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
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
                        if (immutableImportHeading.blankZeroes) {
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

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) throws Exception {
        int valueCount = 0;
        // initial pass to deal with defaults, dates and local parents
        //set defaults before dealing with local parent/child
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.immutableImportHeading.defaultValue != null && importCellWithHeading.lineValue.length() == 0) {
                importCellWithHeading.lineValue = importCellWithHeading.immutableImportHeading.defaultValue;
            }
        }
        for (ImportCellWithHeading importCellWithHeading : cells) {
            // this basic value checking was outside, I see no reason it shouldn't be in here
            if (importCellWithHeading.immutableImportHeading.attribute != null && importCellWithHeading.immutableImportHeading.isDate) {
                    /*
                    interpret the date and change to standard form
                    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    */
                LocalDate date = isADate(importCellWithHeading.lineValue);
                if (date != null) {
                    importCellWithHeading.lineValue = dateTimeFormatter.format(date);
                }
            }
            /* 3 headings. Town, street, whether it's pedestrianized. Pedestrianized parent of street. Town parent of street local.
             the key here is that the resolveLineNameParentsAndChildForCell has to resolve line Name for both of them - if it's called on "Pedestrianized parent of street" first
             both pedestrianized (ok) and street (NOT ok!) will have their line names resolved
             whereas resolving "Town parent of street local" first means that the street should be correct by the time we resolve "Pedestrianized parent of street".
             essentially sort all local names */
            if (importCellWithHeading.immutableImportHeading.lineNameRequired && importCellWithHeading.immutableImportHeading.isLocal) {
                // local and it is a parent of another heading (has child heading), inside this function it will use the child heading set up
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        // now sort non local names
        for (ImportCellWithHeading cell : cells) {
            if (cell.immutableImportHeading.lineNameRequired && !cell.immutableImportHeading.isLocal) {
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, cell, cells, attributeNames, lineNo);
            }
        }

        long tooLong = 2000000;
        long time = System.nanoTime();
        // now do the peers
        for (ImportCellWithHeading cell : cells) {
            /* ok the gist seems to be that there's peers as defined in a context item in which case it's looking in context items and peers
            peers as defined in the context will look for other columns and in the context (those in the context having been pre prepared)
             */
            boolean peersOk = true;
            final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
            if (!cell.immutableImportHeading.contextPeersFromContext.isEmpty() || !cell.immutableImportHeading.contextPeerCellIndexes.isEmpty()) { // new criteria,this means there are context peers to deal with
                namesForValue.addAll(cell.immutableImportHeading.contextPeersFromContext);// start with the ones we have to hand, including the main name
                for (int peerCellIndex : cell.immutableImportHeading.contextPeerCellIndexes) {
                    // Clarified now - normally contextPeerCellIndexes refers to the line name but if it's "this" then it's the heading name. Inconsistent.
                    if (peerCellIndex == -1) {
                        namesForValue.add(cell.immutableImportHeading.name);
                    } else {
                        ImportCellWithHeading peerCell = cells.get(peerCellIndex);
                        if (peerCell.lineName != null) {
                            namesForValue.add(peerCell.lineName);
                        } else {// fail - I plan to have resolved all possible line names by this point!
                            peersOk = false;
                            break;
                        }
                    }
                }
                // can't have more than one peers defined so if not from context check standard peers - peers from context is as it says, from context but not defined in there!
            } else if (!cell.immutableImportHeading.peerCellIndexes.isEmpty() || !cell.immutableImportHeading.peersFromContext.isEmpty()) {
                namesForValue.add(cell.immutableImportHeading.name); // the one at the top of this headingNo, the name with peers.
                // the old logic added the context peers straight in so I see no problem doing this here - this is what might be called inherited peers, from a col to the left.
                // On the col where context peers are defined normal peers should not be defined or used
                namesForValue.addAll(cell.immutableImportHeading.peersFromContext);
                // Ok I had to stick to indexes to get the cells
                for (Integer peerCellIndex : cell.immutableImportHeading.peerCellIndexes) { // go looking for non context peers
                    ImportCellWithHeading peerCell = cells.get(peerCellIndex); // get the cell
                    if (peerCell.lineName != null) {// under new logic the line name would have been created if possible so if it's not there fail
                        namesForValue.add(peerCell.lineName);
                    } else {
                        peersOk = false;
                        break; // no point continuing gathering the names
                    }
                }
            }
            if (peersOk && !namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                String value = cell.lineValue;
                if (!(cell.immutableImportHeading.blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    valueCount++;
                    // finally store our value and names for it
                    valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                }
            }
            // ok that's the peer/value stuff done I think, now onto attributes
            if (cell.immutableImportHeading.indexForAttribute >= 0 && cell.immutableImportHeading.attribute != null
                    && cell.lineValue.length() > 0) {
                // handle attribute was here, we no longer require creating the line name so it can in lined be cut down a lot
                ImportCellWithHeading identityCell = cells.get(cell.immutableImportHeading.indexForAttribute); // get our source cell
                if (identityCell.lineName != null) {
                    identityCell.lineName.setAttributeWillBePersisted(cell.immutableImportHeading.attribute, cell.lineValue);
                }
                // else an error? If the line name couldn't be made in resolveLineNamesParentsChildren above there's nothing to be done about it
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

    private List<String> setLocalLanguage(ImmutableImportHeading heading, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<>();
        if (heading.attribute != null) {
            languages.add(heading.attribute);
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;
    }

    // namesFound is a cache. Then the heading we care about then the list of all headings.
    // This used to be called handle parent and deal only with parents and children but it also resolved line names. Should be called for local first then non local
    // it tests to see if the current line name is null or not as it may have been set by a call to resolveLineNamesParentsChildrenRemove on a different cell setting the child name
    private void resolveLineNameParentsAndChildForCell(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache,
                                                       ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int lineNo) throws Exception {
        if (cellWithHeading.lineValue.length() == 0) { // so nothing to do
            return;
        }
        if (cellWithHeading.lineValue.contains(",") && !cellWithHeading.lineValue.contains(Name.QUOTE + "")) {//beware of treating commas in cells as set delimiters....
            cellWithHeading.lineValue = Name.QUOTE + cellWithHeading.lineValue + Name.QUOTE;
        }
        if (cellWithHeading.lineName == null) { // then create it, this will take care of the parents ("child of") while creating
            cellWithHeading.lineName = includeInParents(azquoMemoryDBConnection, namesFoundCache, cellWithHeading.lineValue
                    , cellWithHeading.immutableImportHeading.parentNames, cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(cellWithHeading.immutableImportHeading, attributeNames));
        } else { // it existed (created below as a child name), sort parents if necessary
            for (Name parent : cellWithHeading.immutableImportHeading.parentNames) { // apparently there can be multiple child ofs, put the name for the line in the appropriate sets, pretty vanilla based off the parents set up
                parent.addChildWillBePersisted(cellWithHeading.lineName);
            }
        }
        // ok that's "child of" (as in for names) done
        // now for "parent of", the, child of this line
        if (cellWithHeading.immutableImportHeading.indexForChild != -1) {
            ImportCellWithHeading childCell = cells.get(cellWithHeading.immutableImportHeading.indexForChild);
            if (childCell.lineValue.length() == 0) {
                throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.lineValue + " " + cellWithHeading.immutableImportHeading.heading);
            }

            // ok got the child cell, need to find the child cell name to add it to this cell's children
            // I think here's it's trying to add to the cells name
            if (childCell.lineName == null) {
                childCell.lineName = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childCell.lineValue, cellWithHeading.lineName
                        , cellWithHeading.immutableImportHeading.isLocal, setLocalLanguage(childCell.immutableImportHeading, attributeNames));
            } else { // check exclusive logic, only if the child cell line name exists then remove the child from parents if necessary - this replaces the old "remove from" funcitonality
                // the exclusiveSetToCheckAgainst means that if the child we're about to sort has a parent in this set we need to get rid of it before re adding the child to the new location
                Collection<Name> exclusiveSetToCheckAgainst = null;
                if ("".equals(cellWithHeading.immutableImportHeading.exclusive) && cellWithHeading.immutableImportHeading.parentNames.size() == 1) {
                    // blank exclusive clause, use child of if there's one (check all the way down. all children, necessary due due to composite option name1->name2->name3->etc
                    exclusiveSetToCheckAgainst = cellWithHeading.immutableImportHeading.parentNames.iterator().next().findAllChildren(false);
                } else if (cellWithHeading.immutableImportHeading.exclusive != null) { // exclusive is referring to a higher name
                    Name specifiedExclusiveSet = nameService.findByName(azquoMemoryDBConnection, cellWithHeading.immutableImportHeading.exclusive);
                    if (specifiedExclusiveSet != null) {
                        specifiedExclusiveSet.removeFromChildrenWillBePersisted(childCell.lineName); // if it's directly against the top it won't be caught by the set below, don't want to add to the set I'd have to make a new, potentially large, set
                        exclusiveSetToCheckAgainst = specifiedExclusiveSet.findAllChildren(false);
                    }
                }
                if (exclusiveSetToCheckAgainst != null) {
                    // essentially if we're saying that this heading is a category e.g. swimwear and we're about to add another name (a swimsuit one assumes) then go through other categories removing the swimsuit from them if it is in there
                    for (Name nameToRemoveFrom : childCell.lineName.getParents()) {
                        if (exclusiveSetToCheckAgainst.contains(nameToRemoveFrom) && nameToRemoveFrom != cellWithHeading.lineName) { // the existing parent is one to be zapped by exclusive criteria and it's not the one we're about to add
                            nameToRemoveFrom.removeFromChildrenWillBePersisted(childCell.lineName);
                        }
                    }
                }
            }
            cellWithHeading.lineName.addChildWillBePersisted(childCell.lineName);
        }
    }

    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values
    // now seems to support basic excel like string operations, left right and mid. Checking only and existing means "should we import the line at all" bases on these criteria

    private boolean getCompositeValuesCheckOnlyAndExisting(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, int lineNo, List<String> languages) {
        int adjusted = 2;
        //loops in case there are multiple levels of dependencies
        while (adjusted > 1) {
            adjusted = 0;
            for (ImportCellWithHeading cell : cells) {
                if (cell.immutableImportHeading.compositionPattern != null) {
                    String result = cell.immutableImportHeading.compositionPattern;
                    // do line number first, I see no reason not to
                    String LINENO = "LINENO";
                    result = result.replace(LINENO, lineNo + "");
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            String expression = result.substring(headingMarker + 1, headingEnd);
                            String function = null;
                            int funcInt = 0;
                            int funcInt2 = 0;
                            if (expression.contains("(")) {
                                int bracketpos = expression.indexOf("(");
                                function = expression.substring(0, bracketpos);
                                int commaPos = expression.indexOf(",", bracketpos + 1);
                                int secondComma;
                                if (commaPos > 0) {
                                    secondComma = expression.indexOf(",", commaPos + 1);
                                    String countString;
                                    try {
                                        if (secondComma < 0) {
                                            countString = expression.substring(commaPos + 1, expression.length() - 1);
                                            funcInt = Integer.parseInt(countString.trim());
                                        } else {
                                            countString = expression.substring(commaPos + 1, secondComma);
                                            funcInt = Integer.parseInt(countString.trim());
                                            countString = expression.substring(secondComma + 1, expression.length() - 1);
                                            funcInt2 = Integer.parseInt(countString);
                                        }
                                    } catch (Exception ignore) {
                                    }
                                    expression = expression.substring(bracketpos + 1, commaPos);
                                }
                            }
                            ImportCellWithHeading compCell = findCellWithHeading(expression, cells);
                            if (compCell != null) {
                                String sourceVal = compCell.lineValue;
                                // the two ints need to be as they are used in excel
                                if (function != null && (funcInt > 0 || funcInt2 > 0) && sourceVal.length() > funcInt) {
                                    if (function.equalsIgnoreCase("left")) {
                                        sourceVal = sourceVal.substring(0, funcInt);
                                    }
                                    if (function.equalsIgnoreCase("right")) {
                                        sourceVal = sourceVal.substring(sourceVal.length() - funcInt);
                                    }
                                    if (function.equalsIgnoreCase("mid")) {
                                        //the second parameter of mid is the number of characters, not the end character
                                        sourceVal = sourceVal.substring(funcInt - 1, (funcInt - 1) + funcInt2);
                                    }
                                }
                                result = result.replace(result.substring(headingMarker, headingEnd + 1), sourceVal);
                            }
                        }
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    if (result.toLowerCase().startsWith("calc")) {
                        result = result.substring(5);
                        Pattern p = Pattern.compile("[\\+\\-\\*\\/]");
                        Matcher m = p.matcher(result);
                        if (m.find()) {
                            double dresult = 0.0;
                            try {
                                double first = Double.parseDouble(result.substring(0, m.start()));
                                double second = Double.parseDouble(result.substring(m.end()));
                                char c = m.group().charAt(0);
                                switch (c) {
                                    case '+':
                                        dresult = first + second;
                                        break;
                                    case '-':
                                        dresult = first - second;
                                        break;
                                    case '*':
                                        dresult = first * second;
                                        break;
                                    case '/':
                                        dresult = first / second;
                                        break;

                                }
                            } catch (Exception ignored) {
                            }
                            result = dresult + "";
                        }
                    }
                    if (!result.equals(cell.lineValue)) {
                        cell.lineValue = result;
                        adjusted++;
                    }
                }
                if (cell.immutableImportHeading.only != null) {
                    //`only' can have wildcards  '*xxx*'
                    String only = cell.immutableImportHeading.only.toLowerCase();
                    String lineValue = cell.lineValue.toLowerCase();
                    if (only.startsWith("*")) {
                        if (only.endsWith("*")) {
                            if (!lineValue.contains(only.substring(1, only.length() - 1))) {
                                return false;
                            }
                        } else if (!lineValue.startsWith(only.substring(1))) {
                            return false;
                        }
                    } else if (only.endsWith("*")) {
                        if (!lineValue.startsWith(only.substring(0, only.length() - 1))) {
                            return false;
                        }
                    } else {
                        if (!lineValue.equals(only)) {
                            return false;
                        }
                    }
                }
                // we could be deriving the name from composite so check existing here
                if (cell.immutableImportHeading.existing) {
                    if (cell.immutableImportHeading.attribute != null && cell.immutableImportHeading.attribute.length() > 0) {
                        languages = Collections.singletonList(cell.immutableImportHeading.attribute);
                    }
                    if (languages == null) { // same logic as used when creating the line names, not sure of this
                        languages = Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME);
                    }
                    // note I'm not going to check parentNames are not empty here, if someone put existing wihthout specifying child of then I think it's fair to say the line isn't valid
                    for (Name parent : cell.immutableImportHeading.parentNames) { // try to find any names from anywhere
                        if (!azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(languages, cell.lineValue, parent).isEmpty()) { // NOT empty, we found one!
                            return true; // no point carrying on
                        }
                    }
                    return false; // none found
                }
            }
        }
        return true;
    }

    private boolean isZero(String text) {
        try {
            double d = Double.parseDouble(text);
            return d == 0.0;
        } catch (Exception e) {
            return true;
        }
    }

    private String setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final InputStream uploadFile, List<String> attributeNames, String fileName) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        if (fileName.length() > 4 && fileName.charAt(4) == '-') {
            String sheetLanguage = fileName.substring(5);
            attributeNames = new ArrayList<>();
            attributeNames.add(sheetLanguage);
        }
        String line;
        int lines = 0;
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            MutableImportHeading mutableImportHeading = new MutableImportHeading();
            if (st.hasMoreTokens()) {
                List<Name> children = new ArrayList<>();
                String setName = st.nextToken().replace("\"", "");//sometimes the last line of imported spreadsheets has come up as ""
                if (setName.length() > 0) {
                    interpretHeading(azquoMemoryDBConnection, setName, mutableImportHeading, attributeNames);
                    mutableImportHeading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, mutableImportHeading.heading, null, true, attributeNames);
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
                                    child = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, element, null, true, attributeNames);
                                    //new names will have been added to sheet set unnecessarily, so:
                                }
                                children.add(child);
                            }
                        }
                        nameService.clearChildren(set);
                        for (Name child : children) {
                            set.addChildWillBePersisted(child);
                        }
                    } else {
                        throw new Exception("Import heading name was null : " + mutableImportHeading);
                    }
                }
            }
            lines++;
        }
        return fileName + " imported. " + lines + " line(s) of a set file.<br/>";
    }
}