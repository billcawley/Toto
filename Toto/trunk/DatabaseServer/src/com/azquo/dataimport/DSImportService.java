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
 * <p/>
 * Has a fair bit of the logic that was in the original import service.
 * <p/>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * <p/>
 * I should add a full step through of logic here for values loading
 * <p/>
 * On a line is it a value or is it used for structure.
 */
public class DSImportService {

    private static final String headingDivider = "|";
    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private DSSpreadsheetService dsSpreadsheetService;

    /*
    These are heading clauses. I think heading definitions can be in the data file but Azquo is setup to support data
    "as it comes". Hence when dealing with a new set of data the key is to set up sets and headings so that the system can load the data.
    Setting up the sets and headings could be seen as similar to setting up the tables in an SQL database.
     */

    public static final String CHILDOF = "child of ";
    public static final String REMOVEFROM = "remove from ";
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

    Notably there are things calculated on every line that could perhaps be moved. Plus things like peer headings could hold the references to the other headings rather than indexes.
    */
    private class MutableImportHeading {
        // column index
        int column = -1;
        // the name of the heading - often references by other headings e.g. parent of
        String heading = null;
        // the name that might be set on the heading, certainly used if there are peers but otherwise may not be set if the heading is referenced by other headings
        Name name = null;
        // parent of is an internal reference - to otehr headings. Child of and remove from refer to names in the database
        String parentOf = null, childOfString = null, removeFromString = null;
        /* the index of the heading that an attribute refers to so if the heading is Customer.Address1 then this is the index of customer.
        Seems only to be used with attribute, could maybe have a better name?*/
        int identityHeading = -1;
        // used in conjunction with parent of - this is the column it is the parent of, This can only be parent of one column. Name of this could be better?
        int childHeading = -1;
        // child of string is a comma separated list, here are the names from that list. DO we need a childof string then? Also concerned about the naming again!
        Set<Name> childOf = null;
        // same format or logic as childof
        Set<Name> removeFrom = null;
        // result of the atribute caluse. Notable that "." is replaced with ;attribute
        String attribute = null;
        /* the results of the peers clause are jammed in name.peers but then we need to know which headings those peers refer to.
        Indexes here - should it just be object pointers? I mean the headings themselves? */
        Set<Integer> peerHeadings = new HashSet<Integer>();
        /*if there are multiple attributes then effectively there will be multiple columns with the same "heading", define which one we're using when the heading is referenced by other headings.
         Language will trigger an identifier, after if on searching there is only one it might be set for convenience when sorting attributes*/
        boolean identifier = false;
        /*when using the heading divider (a pipe at the moment) then other headers are stacked up under the same column. These extras, after the first | are applied to all subsequent columns
        the context logic is only dependant on the headings - we're validating what's there */
        boolean contextItem = false;
        // local in the azquo sense. Affects child of and parent of - the other heading is local in the case of parent of and this one in the case of child of.
        boolean local = false;
        /* to make the line value a composite of other values. Syntax is pretty simple replacing anything in quotes with the referenced line value
        `a column name`-`another column name` might make 1233214-1234. Such columns would probably be at the end,
        they are virtual in the sens that these values are made on uploading they are not there in the source file though the components are.*/
        String composition = null;
        // a default value if the line value is blank
        String defaultValue = null;
        // a way for a heading to have an alias or more specifically for its name to be overridden for the purposes of how headings find each other
        // need to clarify usage of this - not able to right now
        boolean nonZero = false;
        //don't import zero values;
        String equalsString = null;
    }

    // I see no reason for getters here. Class only used here.
    private class ImmutableImportHeading {
        final int column;
        final String heading;
        final Name name;
        final String parentOf, childOfString, removeFromString;
        final int identityHeading;
        final int childHeading;
        // ok the set will be fixed, I suppose names can be modified but they should be thread safe. Well that's the plan.
        final Set<Name> childOf;
        final Set<Name> removeFrom;
        final String attribute;
        final Set<Integer> peerHeadings;
        final boolean identifier;
        final boolean contextItem;
        final boolean local;
        final String composition;
        final String defaultValue;
        final boolean nonZero;
        final String equalsString;

        public ImmutableImportHeading(MutableImportHeading mutableImportHeading) {
            this.column = mutableImportHeading.column;
            this.heading = mutableImportHeading.heading;
            this.name = mutableImportHeading.name;
            this.parentOf = mutableImportHeading.parentOf;
            this.childOfString = mutableImportHeading.childOfString;
            this.removeFromString = mutableImportHeading.removeFromString;
            this.identityHeading = mutableImportHeading.identityHeading;
            this.childHeading = mutableImportHeading.childHeading;
            this.childOf = mutableImportHeading.childOf != null ? Collections.unmodifiableSet(new HashSet<Name>(mutableImportHeading.childOf)) : null;
            this.removeFrom = mutableImportHeading.removeFrom != null ? Collections.unmodifiableSet(new HashSet<Name>(mutableImportHeading.removeFrom)) : null;
            this.attribute = mutableImportHeading.attribute;
            this.peerHeadings = mutableImportHeading.peerHeadings != null ? Collections.unmodifiableSet(new HashSet<Integer>(mutableImportHeading.peerHeadings)) : null;
            this.identifier = mutableImportHeading.identifier;
            this.contextItem = mutableImportHeading.contextItem;
            this.local = mutableImportHeading.local;
            this.composition = mutableImportHeading.composition;
            this.defaultValue = mutableImportHeading.defaultValue;
            this.nonZero = mutableImportHeading.nonZero;
            this.equalsString = mutableImportHeading.equalsString;
        }
    }

    // going to follow the pattern above, no getters, the final will take care of setting
    // I'd have liked to make this immutable but existing logic for things like composite mean this may be changed before loading

    public class ImportCellWithHeading {
        private final ImmutableImportHeading immutableImportHeading;
        private String value;
        private Name name;
        public ImportCellWithHeading(ImmutableImportHeading immutableImportHeading, String value, Name name) {
            this.immutableImportHeading = immutableImportHeading;
            this.value = value;
            this.name = name;
        }
    }

    // Switched to Java 8 calls. Should really, finally, move away from java.util.Date. Of course the legacy with SQL code is not helpful.

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
        date =  tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
        if (date!=null) return date;
        return tryDate(maybeDate.length() > 11 ? maybeDate.substring(0,11):maybeDate, ukdf5);
    }

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Sets being as mentioned at the top one of the two files that are needed along with import headers to set up a database ready to load data.
    */

    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames, String user) throws Exception {
        System.out.println("reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user,"imported",filePath,"");
        readPreparedFile(azquoMemoryDBConnection, filePath, fileType, attributeNames);

    }



    public void readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames) throws Exception {


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

    // this is called for all the ; separated clauses in a header e.g. Gender; parent of Customer; child of Genders
    // Edd : it feels like an enum or array could help here but I'm not sure . . .

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
            String subClause = readClause(PARENTOF, clause); // parent of names in the specified column
            heading.parentOf = subClause.replace(Name.QUOTE + "", "");
            if (heading.parentOf.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // e.g. child of all orders
        } else if (CHILDOF.startsWith(firstWord)) {        // e.g. child of all orders
            heading.childOfString = readClause(CHILDOF, clause).replace(Name.QUOTE + "", "");
            if (heading.childOfString.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // e.g. opposite of above
        } else if (REMOVEFROM.startsWith(firstWord)) {
            // e.g. opposite of above
            String subClause = readClause(REMOVEFROM, clause); // child of relates to a name in the database - the hook to existing data
            heading.removeFromString = subClause.replace(Name.QUOTE + "", "");
            if (heading.removeFromString.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // language being attribute
        } else if (firstWord.equals(LANGUAGE)) {
            heading.attribute = readClause(LANGUAGE, clause);
            heading.identifier = true; // so language is important so it's the identifier, we'll use attribute later if we can't find one from here
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
            heading.local = true;
        }else if (firstWord.equals(EQUALS)) {
            heading.equalsString = readClause(EQUALS, clause);
            if (heading.equalsString.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
        } else if (firstWord.equals(COMPOSITION)) {
            // combine more than one row
            heading.composition = readClause(COMPOSITION, clause);
            if (heading.composition.length() == 0) {
                throw new Exception(clause + notUnderstood);
            }
            // if there's no value on the line a default
        } else if (firstWord.equals(DEFAULT)) {
            String subClause = readClause(DEFAULT, clause);
            if (subClause.length() > 0) {
                heading.defaultValue = subClause;
            }
        /* peers, {peer1, peer2, peer3}. Makes sure the heading exists as a name then set the peers (creating if necessary?) against this name - note it's using the name
          notable that we're not using a custom peers structure rather peers for the name, this is what is references later and will be persisted
          I think we're going to move away from peers in name itself whch means we'll need to store them in the heading probably
           */
        } else if (firstWord.equals(NONZERO)){
            heading.nonZero = true;
        } else if (firstWord.equals(PEERS)) {
            // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
            heading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);
            String peersString = readClause(PEERS, clause);
            if (peersString.startsWith("{")) { // array, typically when creating in the first place, the spreadsheet call will insert after any existing
                if (peersString.contains("}")) {
                    peersString = peersString.substring(1, peersString.indexOf("}"));
                    final StringTokenizer st = new StringTokenizer(peersString, ",");
                    //final List<String> peersToAdd = new ArrayList<String>();
                    String notFoundError = "";
                    final LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>(st.countTokens());
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
                        peers.put(peer, true);
                    }
                    if (notFoundError.isEmpty()) {
                        heading.name.setPeersWillBePersisted(peers);
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

    // when dealing populating peer headings first look for the headings then look at the context headings, that's what this does.

    private int findContextHeading(Name name, List<MutableImportHeading> headings) {
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            if (heading.contextItem && heading.name.findAllParents().contains(name)) {
                return headingNo;
            }
        }
        return -1;
    }

    /* find a heading by index, is used when trying to find peer headings and composite values
    The extra logic aside simply from heading matching is the identifier flag (multiple attributes mean many headings with the same name)
    Or attribute being null (thus we don't care about identifier) or equalsString not being null? equals String parked for the mo after talking with Bill
    */

    private int findHeading(String nameToFind, List<ImportCellWithHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            ImmutableImportHeading heading = headings.get(headingNo).immutableImportHeading;
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().indexOf(nameToFind.toLowerCase() + ",") == 0) && (heading.identifier || heading.attribute == null || heading.equalsString != null)) {
                if (heading.identifier) {
                    return headingNo;
                }
                // ah I see the logic here. Identifier means it's the one to use, if not then there must be only one - if more than one are found then it's too ambiguous to work with.
                if (headingFound == -1) {
                    headingFound = headingNo;
                } else {
                    return -1;//too many possibilities
                }
            }
        }
        return headingFound;
    }

    // very similar to above, not sure of an obvious factor

    private int findMutableHeading(String nameToFind, List<MutableImportHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().indexOf(nameToFind.toLowerCase() + ",") == 0) && (heading.identifier || heading.attribute == null || heading.equalsString != null)) {
                if (heading.identifier) {
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
    Created by Edd to try to improve speed through multi threading. There's still a bottleneck in the initial parsing
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
        public void run() { // well this is what's going to truly test concurrent modification of a database
            long trigger = 10;
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
                // todo, move this check outside??
                if (lineToLoad.get(0).value.length() > 0 || lineToLoad.get(0).immutableImportHeading.column == -1) {//skip any line that has a blank in the first column unless we're not interested in that column
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
        // little local cache just to speed things up
        final Map<String, Name> namesFound = new ConcurrentHashMap<String, Name>();
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
            if ("true".equalsIgnoreCase(importInterpreter.getAttribute("transpose"))){
                // ok we want to transpose, will use similar logic to the server side transpose
                final List<String[]> sourceList = new ArrayList<String[]>();
                while (lineIterator.hasNext()){ // it will be closed at the end. Worth noting that transposing shouldn't really be done on massive files, I can't imagine it would be
                    sourceList.add(lineIterator.next());
                }
                final List<String[]> flipped = new ArrayList<String[]>(); // from ths I can get a compatible iterator
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
            String importHeaders = importInterpreter.getAttribute(headingsString);
            if (importHeaders == null) {
                importHeaders = importInterpreter.getAttribute(headingsString + "1");
                if (importHeaders != null) {
                    skipTopLine = true;
                }
            }
            if (importHeaders != null) {
                headers = importHeaders.split("¬"); // a bit arbitrary, would like a better solution if I can think of one.
            }
        }
        // we might use the headers on the data file, are we ever actually doing this?
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
        but it could be asked for something more specific according to the header name. Redundant? todo - confirm logic with Bill but not right now
        */
        List<MutableImportHeading> mutableImportHeadings = new ArrayList<MutableImportHeading>();
        readHeaders(azquoMemoryDBConnection, headers, mutableImportHeadings, fileType, attributeNames);
        // further information put into the ImportHeadings based off the initial info
        // I could put more in here - there's stuff going on in the values import that is header only stuff
        fillInHeaderInformation(azquoMemoryDBConnection, mutableImportHeadings);
        final List<ImmutableImportHeading> immutableImportHeadings = new ArrayList<ImmutableImportHeading>();
        for (MutableImportHeading mutableImportHeading : mutableImportHeadings) {
            immutableImportHeadings.add(new ImmutableImportHeading(mutableImportHeading));
        }
        // having read the headers go through each record
        // now, since this will be multi threaded need to make line objects, Cannot be completely immutable due to the current logic, I may be able to change this, not sure
        int lineNo = 0;
        ExecutorService executor = Executors.newFixedThreadPool(azquoMemoryDBConnection.getAzquoMemoryDB().getLoadingThreads());
        AtomicInteger valueTracker = new AtomicInteger(0);
        int batchSize = 100000; // a bit arbitrary, I wonder shuld I go smaller?
        ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<List<ImportCellWithHeading>>(batchSize);
        while (lineIterator.hasNext()) { // new Jackson call . . .
            String[] lineValues = lineIterator.next();
            lineNo++;
            List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<ImportCellWithHeading>();
            for (ImmutableImportHeading immutableImportHeading : immutableImportHeadings) {
                // since strings may be repeated intern, should save a bit of memory using the String pool. Hopefully not a big performance hit? Also I figure trimming here does no harm
                String lineValue = immutableImportHeading.column != -1 && immutableImportHeading.column < lineValues.length ? lineValues[immutableImportHeading.column].trim().intern() : "";
                importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue, null));
            }
            //batch it up!
            linesBatched.add(importCellsWithHeading);
            if (linesBatched.size() == batchSize) {
                executor.execute(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFound, attributeNames, lineNo));
                linesBatched = new ArrayList<List<ImportCellWithHeading>>(batchSize);
            }
        }
        // load leftovers
        executor.execute(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFound, attributeNames, lineNo));
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
        System.out.println("csv dataimport took " + (System.currentTimeMillis() - track) / 1000 + " second(s) for " + lineNo + " lines");
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
                while (dividerPos > 0) {
                    MutableImportHeading contextHeading = new MutableImportHeading();
                    interpretHeading(azquoMemoryDBConnection, head.substring(dividerPos + 1), contextHeading, attributeNames);
                    contextHeading.column = col;
                    contextHeading.contextItem = true;
                    headings.add(contextHeading);
                    head = head.substring(0, dividerPos);
                    dividerPos = head.lastIndexOf(headingDivider);
                }
                heading.column = col;
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
            } else {
                headings.add(new MutableImportHeading());
            }
            col++;
        }
    }

    // TODO - EDD UNDERSTANDS PROPERLY UP TO THIS LINE, TRY TO GET IT TO THE BOTTOM

    // todo - edd understand properly! Bet there's some factoring to do

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFound, List<String> attributeNames, int lineNo) throws Exception {
        List<Name> contextNames = new ArrayList<Name>(); // stacks cumulatively across the line
        String value;
        int valueCount = 0;
        // initial pass to deal with defaults, dates and local parents
        for (ImportCellWithHeading importCellWithHeading : cells) {
            // this basic value checking was outside, I see no reason it shouldn't be in here
            if (importCellWithHeading.immutableImportHeading.defaultValue != null && importCellWithHeading.value.length() == 0) {
                importCellWithHeading.value = importCellWithHeading.immutableImportHeading.defaultValue;
            }
            if (importCellWithHeading.immutableImportHeading.attribute != null && importCellWithHeading.immutableImportHeading.attribute.equalsIgnoreCase(dateLang)) {
                    /*
                    interpret the date and change to standard form
                    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    edd switched to java 8 API calls, hope all will still work
                    */
                LocalDate date = isADate(importCellWithHeading.value);
                if (date != null) {
                    importCellWithHeading.value = dateTimeFormatter.format(date);
                }
            }
            // prepare the local parent of columns. Customer is in all customers local
            // ok local is done up here and not local below (handle parent being called twice)? Plus the name attached to the cell (not heading!) will be set below . . . perhaps how local is dealt with. Hmmmmmmmm
            if (importCellWithHeading.immutableImportHeading.local && importCellWithHeading.immutableImportHeading.parentOf != null) { // local and it is a parentof, inside this function it will use the childheading set up - should maybe check for that instead??
                handleParent(azquoMemoryDBConnection, namesFound, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        long toolong = 200000;
        long time = System.nanoTime();
        ImportCellWithHeading contextPeersItem = null;
        for (ImportCellWithHeading cell : cells) {


            /* ok the gist seems to be that there's peers as defined in a context item in which case it's looking in context items and peers
            a notable thing about context : after something has been added to context names it stays there for subsequent cells.
            again this logic is dependant on headers, it really needn't be done every time
            ok context peers will look for other columns and in the context for names where it allows emebers off sets e.g. a peer might be year so 2014, 2015 etc will be ok.
             */
            if (cell.immutableImportHeading.contextItem) {
                contextNames.add(cell.immutableImportHeading.name);
                if (cell.immutableImportHeading.name.getPeers().size() > 0) {
                    contextPeersItem = cell; // so skip this heading but now contextPeersItem is set? I assume one name with peers allowed. Or the most recent one.
                }
            } else {
                if (contextNames.size() > 0 && cell.immutableImportHeading.name != null) { // ok so some context names and a name for this column? I guess as in not an attribute column for example
                    contextNames.add(cell.immutableImportHeading.name);// add this name onto the context stack - for our purposes now it's
                    if (contextPeersItem != null) { // a value cell HAS to have peers, context headings are only for values
                        final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                        namesForValue.add(contextPeersItem.immutableImportHeading.name);// ok the "defining" name with the peers. Are we only using peers on importing?
                        boolean foundAll = true;
                        for (Name peer : contextPeersItem.immutableImportHeading.name.getPeers().keySet()) { // ok so a value with peers
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
                                // this is NOT dependant on the value of the line itself, really it should be outside this loop - though the peerCell.name below is a different matter . . .
                                int colFound = findHeading(peer.getDefaultDisplayName(), cells);
                                if (colFound < 0) {
                                    foundAll = false;
                                    break;
                                }
                                ImportCellWithHeading peerCell = cells.get(colFound);
                                /*   UNNECESSARY CODE REMOVED BY WFC
                                if (peerHeading.lineName != null) {
                                    peer.addChildWillBePersisted(peerHeading.lineName);
                                } else {
                                    String peerValue = peerHeading.lineValue;
                                    peerHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, peerValue, peer, heading.local, attributeNames);
                                }
                                */
                                possiblePeer = peerCell.name;
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
                            value = cell.value;
                        } else {
                            value = "";
                        }
                        if (cell.immutableImportHeading.nonZero && isZero(value)) value = "";
                        if (value.trim().length() > 0) { // no point storing if there's no value!
                            valueCount++;
                            // finally store our value and names for it
                            valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                        }
                    }
                    contextNames.remove(cell.immutableImportHeading.name);
                }
                if (cell.immutableImportHeading.peerHeadings.size() > 0) { // ok so context stuff has heppened now whis heppens too, maybe should be an else? Or one could get two entries for each line . . .
                    final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                    // check for peers as defined in peerHeadings, this will create peers if it can't find them. It will fail if a peer heading has no line value
                    boolean hasRequiredPeers = findPeers(azquoMemoryDBConnection, namesFound, cell, cells, namesForValue, attributeNames);
                    if (hasRequiredPeers) {
                        // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                        value = cell.value;
                    } else {
                        value = "";
                    }
                    if (cell.immutableImportHeading.nonZero && isZero(value)) value = "";
                    if (value.trim().length() > 0) { // no point storing if there's no value!
                        valueCount++;
                        // finally store our value and names for it
                        valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                    }
                }
                // ok that's the peer/value stuff done I think
                if (cell.immutableImportHeading.identityHeading >= 0 && cell.immutableImportHeading.attribute != null
                        && cell.value.length() > 0
                        && (!cell.immutableImportHeading.attribute.equalsIgnoreCase(dateLang) || (isADate(cell.value) != null))) {
                    // funnily enough no longer using attributes
                    handleAttribute(azquoMemoryDBConnection, namesFound, cell, cells);
                }
                // not local this time - handle attribute and find peers might have set the cell name?
                if (cell.immutableImportHeading.parentOf != null && !cell.immutableImportHeading.local) {
                    handleParent(azquoMemoryDBConnection, namesFound, cell, cells, attributeNames, lineNo);
                }
                if (cell.immutableImportHeading.childOf != null) {
                    if (cell.name != null) { // could if ever be null after preparing the headers?
                        for (Name parent : cell.immutableImportHeading.childOf) {
                            parent.addChildWillBePersisted(cell.name);
                        }
                    } else {
                        String childNameString = cell.value;
                        if (childNameString.length() > 0) {
                            for (Name parent : cell.immutableImportHeading.childOf) {
                                cell.name = includeInSet(azquoMemoryDBConnection, namesFound, childNameString, parent, cell.immutableImportHeading.local, attributeNames);
                            }
                        }
                    }
                }
                if (cell.immutableImportHeading.removeFrom != null) {
                    if (cell.name != null) {
                        for (Name remove : cell.immutableImportHeading.removeFrom) {
                            remove.removeFromChildrenWillBePersisted(cell.name);
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
                if (mutableImportHeading.name != null && mutableImportHeading.name.getPeers().size() > 0 && !mutableImportHeading.contextItem) {
                    for (Name peer : mutableImportHeading.name.getPeers().keySet()) {
                        //three possibilities to find the peer:
                        int peerHeading = findMutableHeading(peer.getDefaultDisplayName(), headings);
                        if (peerHeading == -1) {
                            peerHeading = findContextHeading(peer, headings);
                            if (peerHeading == -1) {
                                peerHeading = findLowerLevelHeading(azquoMemoryDBConnection, peer.getDefaultDisplayName(), headings);
                                if (peerHeading == -1) {
                                    throw new Exception("error: cannot find peer " + peer.getDefaultDisplayName() + " for " + mutableImportHeading.name.getDefaultDisplayName());
                                }
                            }
                        }
                        if (peerHeading >= 0) {
                            MutableImportHeading importPeer = headings.get(peerHeading);
                            if (importPeer.name == null) {
                                importPeer.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importPeer.heading, null, false);
                            }
                        }
                        mutableImportHeading.peerHeadings.add(peerHeading);
                    }
                }

                // having an attribute means the content of this column relates to a name in another column, need to find that name
                if (mutableImportHeading.attribute != null) { // && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
                    String headingName = mutableImportHeading.heading;
                    if (mutableImportHeading.equalsString != null) {// the equals string seems to be a way for a heading to have an alias, not completely clear on the usage but I'm guessing by this point the alias is no longer important so overwrite it with the equals value
                        headingName = mutableImportHeading.equalsString;
                    }
                    mutableImportHeading.identityHeading = findMutableHeading(headingName, headings); // so if it's Customer,Address1 we need to find customer. THis findheading will look for the Customter with identifier = true or the first one without an attribute
                    if (mutableImportHeading.identityHeading >= 0) {
                        headings.get(mutableImportHeading.identityHeading).identifier = true;//it may not be true (as in found due to no attribute rather than language), in which case set it true now . . .need to consider this logic
                        // so now we have an identifier for this name go through all columns for this name and set the identity heading and attribute to avoid ambiguity? Of course if it set this on more than one heading it would make no sense
                        // some unclear logic here, this needs refactoring
                        for (MutableImportHeading heading2 : headings) {
                            //this is for the cases where the default display name is not the identifier.
                            if (heading2.heading != null && heading2.heading.equals(mutableImportHeading.heading) && heading2.attribute == null) {
                                heading2.attribute = Constants.DEFAULT_DISPLAY_NAME;
                                heading2.identityHeading = mutableImportHeading.identityHeading;
                                break;
                            }
                        }
                    }
                }
                // child of being in Azquo context
                if (mutableImportHeading.childOfString != null) {
                    mutableImportHeading.childOf = new HashSet<Name>();
                    String[] parents = mutableImportHeading.childOfString.split(",");//TODO this does not take into account names with commas inside.......
                    for (String parent : parents) {
                        mutableImportHeading.childOf.add(nameService.findOrCreateNameInParent(azquoMemoryDBConnection, parent, null, false));
                    }
                }
                // reverse
                if (mutableImportHeading.removeFromString != null) {
                    mutableImportHeading.removeFrom = new HashSet<Name>();
                    String[] removes = mutableImportHeading.removeFromString.split(",");//TODO this does not take into account names with commas inside.......
                    for (String remove : removes) {// also not language specific. THis is a simple lookup, don't want a find or create
                        mutableImportHeading.removeFrom.add(nameService.findByName(azquoMemoryDBConnection, remove));
                    }
                }
                // parent of being in context of this upload, if you can't find the heading throw an exception
                if (mutableImportHeading.parentOf != null) {
                    mutableImportHeading.childHeading = findMutableHeading(mutableImportHeading.parentOf, headings);
                    if (mutableImportHeading.childHeading < 0) {
                        throw new Exception("error: cannot find column " + mutableImportHeading.parentOf + " for child of " + mutableImportHeading.heading);
                    }
                    //error = findTopParent(azquoMemoryDBConnection, importHeading, headings, attributeNames);
                    // if (error.length() > 0) return error;
                }
            }
        }
    }

    private List<String> setLocalLanguage(ImmutableImportHeading heading, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<String>();
        if (heading.attribute != null && !heading.attribute.equalsIgnoreCase(dateLang)) {
            languages.add(heading.attribute);
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;

    }

    // namesFound is a cache. Then the heading we care about then the list of all headings.
    private void handleParent(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int lineNo) throws Exception {
        if (cellWithHeading.value.length() == 0) { // so nothing to do
            return;
        }

        if (cellWithHeading.name != null) { // This function is called in two places in interpret line, the first time this will be null the second time not
            if (cellWithHeading.immutableImportHeading.childOf != null) {
                for (Name parent : cellWithHeading.immutableImportHeading.childOf) { // apparently there can be multiple childofs, put the name for the line in th appropriate sets.
                    parent.addChildWillBePersisted(cellWithHeading.name);
                }
            }
        } else {
            cellWithHeading.name = includeInParents(azquoMemoryDBConnection, namesFound, cellWithHeading.value
                    , cellWithHeading.immutableImportHeading.childOf, cellWithHeading.immutableImportHeading.local, setLocalLanguage(cellWithHeading.immutableImportHeading, attributeNames));
        }

        ImportCellWithHeading childCell = cells.get(cellWithHeading.immutableImportHeading.childHeading);
        if (childCell.value.length() == 0) {
            throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.value);
        }
        if (childCell.name == null) {
            childCell.name = includeInSet(azquoMemoryDBConnection, namesFound, childCell.value, cellWithHeading.name
                    , cellWithHeading.immutableImportHeading.local, setLocalLanguage(childCell.immutableImportHeading, attributeNames));
        }
        cellWithHeading.name.addChildWillBePersisted(childCell.name);
    }

    public void handleAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cell, List<ImportCellWithHeading> cells) throws Exception {
        // these two are the same, don't really understand, will just convert. Edd.
        ImportCellWithHeading identity = cells.get(cell.immutableImportHeading.identityHeading);
        ImportCellWithHeading identityCell = cells.get(cell.immutableImportHeading.identityHeading);
        if (identityCell.name == null) {
            if (identityCell.value.length() == 0) {
                return;
            }
            List<String> localAttributes = new ArrayList<String>();
            localAttributes.add(identity.immutableImportHeading.attribute);
            identityCell.name = includeInParents(azquoMemoryDBConnection, namesFound, identityCell.value, identityCell.immutableImportHeading.childOf, false, localAttributes);
        }
        String attribute = cell.immutableImportHeading.attribute;
        if (attribute == null) {
            attribute = Constants.DEFAULT_DISPLAY_NAME;
        }
        identityCell.name.setAttributeWillBePersisted(attribute, cell.value);
    }

    // ok what's notable here is that this will create names to complete the peers if it can't find them

    private boolean findPeers(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cell, List<ImportCellWithHeading> cells, Set<Name> namesForValue, List<String> attributeNames) throws Exception {
        //ImportHeading headingWithPeers = heading;
        boolean hasRequiredPeers = true;
        namesForValue.add(cell.immutableImportHeading.name); // the one at the top of this headingNo, the name with peers.
        for (int peerHeadingNo : cell.immutableImportHeading.peerHeadings) { // go looking for the peers
            ImportCellWithHeading peerCell = cells.get(peerHeadingNo);
            if (peerCell.immutableImportHeading.contextItem) {// can it be a context item? If so it seems just add it - we assume context items have names
                namesForValue.add(peerCell.immutableImportHeading.name);
            } else {// otherwise there may be a name there but we need to check first
                if (peerCell.name == null) {
                    if (peerCell.value.length() == 0) { // null name and no line value, I guess we can't find this peer
                        hasRequiredPeers = false;
                    } else { // we do have a value on this line
                        List<String> peerLanguages = new ArrayList<String>();
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
                        peerCell.name = includeInSet(azquoMemoryDBConnection, namesFound, peerCell.value, null, false, peerLanguages);
                    }
                }
                // add to the set of names we're going to store against this value
                if (peerCell.name != null) {
                    namesForValue.add(peerCell.name);
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
                if (cell.immutableImportHeading.composition != null) {
                    String result = cell.immutableImportHeading.composition;
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            int compItem = findHeading(result.substring(headingMarker + 1, headingEnd), cells);
                            if (compItem >= 0) {
                                result = result.replace(result.substring(headingMarker, headingEnd + 1), cells.get(compItem).value);
                            }
                        }
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    if (!result.equals(cell.value)) {
                        cell.value = result;
                        adjusted++;
                    }
                }
            }
        }
    }


    private boolean isZero(String text){
        try{
            double d = Double.parseDouble(text);
            if (d==0.0) return true;
            return false;
        }catch(Exception e){
            return true;
        }
    }

    public void setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final InputStream uploadFile, List<String> attributeNames, String fileName) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String sheetSetName = "";
        Name sheetSet = null;
        if (fileName.charAt(4) == '-') {
            sheetSetName = fileName.substring(5);
            sheetSet = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, sheetSetName, null, false, attributeNames);
        }
        String line;
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            MutableImportHeading mutableImportHeading = new MutableImportHeading();
            if (st.hasMoreTokens()) {
                List<Name> children = new ArrayList<Name>();
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
                                if (localPos > 0 || mutableImportHeading.local) {
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