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
 *
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 *
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
    public static final String ATTRIBUTE = "attribute ";
    public static final String LANGUAGE = "language ";
    public static final String PEERS = "peers";
    public static final String LOCAL = "local";
    public static final String EQUALS = "equals";
    public static final String COMPOSITION = "composition";
    public static final String DEFAULT = "default";
    public static final String headingsString = "headings";
    public static final String dateLang = "date";

    /*
    To multi thread I wanted this to be immutable but there are things that are only set after in context of other headings so I can't
    do this initially. No problem, initially make this very simple and mutable then have an immutable version for the multi threaded stuff which is held against line.
    could of course copy all fields into line but this makes the constructor needlessly complex.
    */
    private class MutableImportHeading {
        int column = -1;
        String heading = null;
        Name name = null;
        String parentOf = null, childOfString = null, removeFromString = null;
        int identityHeading = -1;
        int childHeading = -1;
        Set<Name> childOf = null;
        Set<Name> removeFrom = null;
        String attribute = null;
        Set<Integer> peerHeadings = new HashSet<Integer>(); // why? I'm not sure
        //Name topParent;
        boolean identifier = false;
        boolean contextItem = false;
        boolean local = false;
        String composition = null;
        String defaultValue = null;
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
        final Set<Integer> peerHeadings; // why? I'm not sure
        //Name topParent;
        final boolean identifier;
        final boolean contextItem;
        final boolean local;
        final String composition;
        final String defaultValue;
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
        return tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
    }

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Sets being as mentioned at the top one of the two files that are needed along with import headers to set up a database ready to load data.
    */

    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames) throws Exception {
        System.out.println("reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
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
        return null;
    }

    // this is called for all the ; separated clauses in a header e.g. Gender; parent of Customer; child of Genders
    // Edd : it feels like an enum or array could help here but I'm not sure . . .
    private void interpretClause(AzquoMemoryDBConnection azquoMemoryDBConnection, MutableImportHeading heading, String clause) throws Exception {
        // not NOT parent of an existing name in the DB, parent of other data in the line
        String readClause = readClause(PARENTOF, clause); // parent of names in the specified column
        if (readClause != null) {
            heading.parentOf = readClause.replace(Name.QUOTE + "", "");
            if (heading.parentOf.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        // e.g. child of all orders
        readClause = readClause(CHILDOF, clause); // child of relates to a name in the database - the hook to existing data
        if (readClause != null) {
            heading.childOfString = readClause.replace(Name.QUOTE + "", "");
            if (heading.childOfString.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        // e.g. opposite of above
        readClause = readClause(REMOVEFROM, clause); // child of relates to a name in the database - the hook to existing data
        if (readClause != null) {
            heading.removeFromString = readClause.replace(Name.QUOTE + "", "");
            if (heading.removeFromString.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        // language being attribute
        readClause = readClause(LANGUAGE, clause); // default language for identifying the name
        if (readClause != null) {
            heading.attribute = readClause;
            heading.identifier = true;
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        // same as language really but .Name is special - it means default display name. Watch out for this.
        readClause = readClause(ATTRIBUTE, clause); // to add attributes to other columns so Customer; attribute address1, externally a . gets converted to ;attribute so Customer.address1. Can even go.address1 apparently
        if (readClause != null) {
            heading.attribute = readClause.replace("`", "");
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + " not understood");
            }
            if (heading.attribute.equalsIgnoreCase("name")) {
                heading.attribute = Constants.DEFAULT_DISPLAY_NAME;
            }
        }
        if (readClause(LOCAL, clause) != null) { // local names in child of, can work with parent of but then it's the subject that it affects
            heading.local = true;
        }
        readClause = readClause(EQUALS, clause); // the actual Azquo name if the heading name is not appropriate
        if (readClause != null) {
            heading.equalsString = readClause;
            if (heading.equalsString.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        // combine more than one row
        readClause = readClause(COMPOSITION, clause);
        if (readClause != null) {
            heading.composition = readClause;
            if (heading.composition.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        // if there's no value on the line a default
        readClause = readClause(DEFAULT, clause);
        if (readClause != null && readClause.length() > 0) {
            heading.defaultValue = readClause;
        }
        // peers, not 100% on this, guess the old peers idea. Which was a way of ensuring membership of certain sets for a value.
        if (readClause(PEERS, clause) != null) {
            // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
            heading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, heading.heading, null, false);
            String peersString = readClause(PEERS, clause);
            if (peersString != null && peersString.startsWith("{")) { // array, typically when creating in the first place, the spreadsheet call will insert after any existing
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

    // when dealing with peers I think, need to find the index of appropriate column in the uploaded file

    private int findContextHeading(Name name, List<MutableImportHeading> headings) {
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            if (heading.contextItem && heading.name.findAllParents().contains(name)) {
                return headingNo;
            }
        }
        return -1;
    }

    // find a heading by index but there are conditions. I'm not 100% on this. Allows equals and begins with the search term and a comma and certain clauses are not allowed

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

    /*
        public String findTopParent(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportHeading heading, List<ImportHeading> headings, List<String> attributeNames) throws Exception{
            //need to work out the topparent for use when classifing names found in this column
            ImportHeading identity = heading;
            if (heading.identityHeading >=0) {
                identity = headings.get(heading.identityHeading);
                if (identity.topParent != null) {
                    heading.topParent = identity.topParent;
                    return "";
                }
            }

            if (identity.name == null){
                identity.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,identity.heading, null, false, attributeNames);
            }
            heading.topParent = identity.name;//if no other parent found, this is the top parent.
            if (identity.childOf != null){
                heading.topParent = identity.childOf.findATopParent();
            }else {
                while (identity.parentOf != null){
                    identity.childHeading = findHeading(identity.parentOf, headings);
                    if (identity.childHeading < 0 ){
                        return "error: cannot find " + identity.parentOf;
                    }
                    identity = headings.get(identity.childHeading);
                }
                if (identity.name.getParents().size() > 0) {
                    heading.topParent = identity.name.findATopParent();
                }
            }
            return "";
        }
    */

    // as the name says, not completely sure how it all fits in but this is a function that actually modifies the db it doesn't defer to other functions in this class

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


    // The big function that deals with data importing

    public void valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames) throws Exception {
        // little local cache just to speed things up
        final Map<String, Name> namesFound = new ConcurrentHashMap<String, Name>();
        if (fileType.indexOf(" ") > 0) {
            //file type should be first word only
            fileType = fileType.substring(0, fileType.indexOf(" "));
        }
        if (fileType.contains("_")){
            fileType = fileType.substring(0, fileType.indexOf("_"));
        }
        // grab the first line to check on delimiters
        long track = System.currentTimeMillis();
        char delimiter = ',';
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String firstLine = br.readLine();
        br.close();
        if (firstLine != null){
            if (firstLine.contains("|")){
                delimiter = '|';
            }
            if (firstLine.contains("\t")){
                delimiter = '\t';
            }
        }else{
            return;//if he first line is blank, ignore the sheet
        }
        // now we know the delimiter can CSV read, I've read jackson is pretty quick
		CsvMapper csvMapper = new CsvMapper();
		csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
        CsvSchema schema = csvMapper.schemaFor(String[].class)
                .withColumnSeparator(delimiter)
                .withLineSeparator("\n");
		MappingIterator<String[]> lineIterator = csvMapper.reader(String[].class).with(schema).readValues(new File(filePath));
        String[] headers = null;
        // ok beginning to understand. It looks for a name for the file type, this name can have headers and/or the definitions for each header
        // in this case looking for a list of headers. Could maybe make this make a bit more sense . . .
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
        boolean skipTopLine = false;
        if (importInterpreter != null) {
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

        if (headers == null) {
            headers = lineIterator.next();
        } else {
            if (skipTopLine) {
                lineIterator.next();
            }
        }

        // correcting the comment : readHeaders is about creating a set of ImportHeadings
        // notable that internally it might use attributes from the relevant data import name to supplement the header information
        List<MutableImportHeading> mutableImportHeadings = new ArrayList<MutableImportHeading>();
        readHeaders(azquoMemoryDBConnection, headers, mutableImportHeadings, fileType, attributeNames);
        // further information put into the ImportHeadings based off the initial info
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
        int batchSize = 100000;
        ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<List<ImportCellWithHeading>>(batchSize);
        while (lineIterator.hasNext()) { // new Jackson call . . .
            String[] lineValues = lineIterator.next();
            lineNo++;
            List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<ImportCellWithHeading>();
            for (ImmutableImportHeading immutableImportHeading : immutableImportHeadings) {
//                trackers.put(heading.name.getDefaultDisplayName(), 0L);
//                String lineValue = csvReader.get(immutableImportHeading.column).intern();// since strings may be repeated intern, should save a bit of memory using the String pool
                String lineValue = immutableImportHeading.column != -1 && immutableImportHeading.column < lineValues.length ? lineValues[immutableImportHeading.column].intern() : "";// since strings may be repeated intern, should save a bit of memory using the String pool. Hopefully not a big performance hit?
                if (immutableImportHeading.defaultValue != null && lineValue.length() == 0) {
                    lineValue = immutableImportHeading.defaultValue;
                }
                if (immutableImportHeading.attribute != null && immutableImportHeading.attribute.equalsIgnoreCase(dateLang)) {
                    /*
                    interpret the date and change to standard form
                    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    edd switched to java 8 API calls, hope all will still work
                    */
                    LocalDate date = isADate(lineValue);
                    if (date != null) {
                        lineValue = dateTimeFormatter.format(date);
                    }
                }
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
        lineIterator.close();
        // edd adding a delete check for tomcat temp files, if read from the other temp directly then leave it alone
        if (filePath.contains("/usr/")){
            File test = new File(filePath);
            if (test.exists()){
                if (!test.delete()){
                    System.out.println("unable to delete " + filePath);
                }
            }
        }
        System.out.println("csv dataimport took " + (System.currentTimeMillis() - track) / 1000 + " second(s) for " + lineNo + " lines");
        System.out.println("---------- namesfound size " + namesFound.size());
    }

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
                if (importInterpreter != null) {
                    head = importInterpreter.getAttribute(header);
                }
                if (head == null) {
                    head = header;
                }
                head = head.replace(".", ";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                int dividerPos = head.lastIndexOf(headingDivider);
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
                //  seems for lasy shorthand where starting with ; will ass the previous heading name. Not sure off the top of my head of the advantages
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

    // todo - edd understand properly! Bet there's some factoring to do

    // each line of values (or names as it may practically be)
    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFound, List<String> attributeNames, int lineNo) throws Exception {
        List<Name> contextNames = new ArrayList<Name>();
        String value;
        int valueCount = 0;
        /*
        for (ImportHeading importHeading:headings){
             if (!importHeading.local){
                importHeading.lineName = includeInSet(azquoMemoryDBConnection,namesFound,importHeading.lineValue,null,false,attributeNames);
            }
        }
        */
        // it seems this will put some names into the database, not sure why only if parentof is not null?
        // prepare the local parent of columns. Customer is in all customers local
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.immutableImportHeading.local && importCellWithHeading.immutableImportHeading.parentOf != null) { // local and it is a parentof, inside this function it will use the childheading set up - should maybe check for that instead??
                handleParent(azquoMemoryDBConnection, namesFound, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        long toolong = 200000;
        long time = System.nanoTime();
        ImportCellWithHeading contextPeersItem = null;
        for (ImportCellWithHeading cell : cells) {
            //long track = System.currentTimeMillis();
            if (cell.immutableImportHeading.contextItem) {
                contextNames.add(cell.immutableImportHeading.name);
                if (cell.immutableImportHeading.name.getPeers().size() > 0) {
                    contextPeersItem = cell; // so skip this heading but now contextPeersItem is set?? I assume one name with peers allowed. Or the most recent one.
                }
            } else {
                if (contextNames.size() > 0 && cell.immutableImportHeading.name != null) { // ok so some context names and a name for this column? I guess as in not an attribute column for example
                    contextNames.add(cell.immutableImportHeading.name);
                    if (contextPeersItem != null) {
                        final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                        namesForValue.add(contextPeersItem.immutableImportHeading.name);
                        boolean foundAll = true;
                        for (Name peer : contextPeersItem.immutableImportHeading.name.getPeers().keySet()) {
                            //is this peer in the contexts?
                            Name possiblePeer = null;
                            for (Name contextPeer : contextNames) {
                                if (contextPeer.findAllParents().contains(peer)) {
                                    possiblePeer = contextPeer;
                                    break;
                                }
                            }
                            if (possiblePeer == null) {
                                //look at the headings
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
                        if (value.trim().length() > 0) { // no point storing if there's no value!
                            valueCount++;
                            // finally store our value and names for it
                            valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                        }

                    }
                    contextNames.remove(cell.immutableImportHeading.name);
                }
                if (cell.immutableImportHeading.peerHeadings.size() > 0) {
                    // intellij is right, it will never be tru after being set before! commenting . . .
                    /*if (cell.immutableImportHeading.contextItem) { // why does intellij think it always false? that's just wrong!
                        contextPeersItem = cell;
                    }
                    if (contextPeersItem != null) {
                        headingWithPeers = contextPeersItem;
                    }*/
                    final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value

                    boolean hasRequiredPeers = findPeers(azquoMemoryDBConnection, namesFound, cell, cells, namesForValue, attributeNames);
                    if (hasRequiredPeers) {
                        // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                        value = cell.value;
                    } else {
                        value = "";
                    }
                    if (value.trim().length() > 0) { // no point storing if there's no value!
                        valueCount++;
                        // finally store our value and names for it
                        valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                    }
                }
                if (cell.immutableImportHeading.identityHeading >= 0 && cell.immutableImportHeading.attribute != null
                        && (!cell.immutableImportHeading.attribute.equalsIgnoreCase(dateLang) || (isADate(cell.value) == null))) {
                    // funnily enough no longer using attributes
                    handleAttribute(azquoMemoryDBConnection, namesFound, cell, cells);
                }
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

    private boolean findPeers(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFound, ImportCellWithHeading cell, List<ImportCellWithHeading> cells, Set<Name> namesForValue, List<String> attributeNames) throws Exception {
        //ImportHeading headingWithPeers = heading;
        boolean hasRequiredPeers = true;
        namesForValue.add(cell.immutableImportHeading.name); // the one at the top of this headingNo, the name with peers.
        for (int peerHeadingNo : cell.immutableImportHeading.peerHeadings) { // go looking for the peers
            ImportCellWithHeading peerCell = cells.get(peerHeadingNo);
            if (peerCell.immutableImportHeading.contextItem) {
                namesForValue.add(peerCell.immutableImportHeading.name);
            } else {
                if (peerCell.name == null) {
                    if (peerCell.value.length() == 0) {
                        hasRequiredPeers = false;
                    } else {
                        List<String> peerLanguages = new ArrayList<String>();
                        //looking up in the correct language
                        if (peerCell.immutableImportHeading.attribute != null) {
                            peerLanguages.add(peerCell.immutableImportHeading.attribute);
                        } else {
                            peerLanguages.addAll(attributeNames);
                        }

                        peerCell.name = includeInSet(azquoMemoryDBConnection, namesFound, peerCell.value, cells.get(peerHeadingNo).name, false, peerLanguages);
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