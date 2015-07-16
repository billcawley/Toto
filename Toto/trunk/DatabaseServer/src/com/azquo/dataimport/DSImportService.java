package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.csvreader.CsvReader;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by cawley on 20/05/15.
 * <p/>
 * Has a fair bit of the logic that was in the original import service.
 */
public class DSImportService {

    private static final String headingDivider = "|";
    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private DSSpreadsheetService dsSpreadsheetService;

    public static final String CHILDOF = "child of ";
    public static final String REMOVEFROM = "remove from ";
    public static final String PARENTOF = "parent of ";
    public static final String ATTRIBUTE = "attribute ";
    public static final String LANGUAGE = "language ";
    public static final String PEERS = "peers";
    public static final String LOCAL = "local";
    public static final String EQUALS = "equals";
    public static final String COMPOSITION = "composition";
    public static final String headingsString = "headings";
    public static final String dateLang = "date";

    // a thought, should a line be a different object which has the line stuff (line value etc) and an import heading?
    static class ImportHeading {
        int column;
        String heading;
        Name name;
        String parentOf, childOfString, removeFromString;
        int identityHeading;
        int childHeading;
        Set<Name> childOf;
        Set<Name> removeFrom;
        String attribute;
        Set<Integer> peerHeadings;
        //Name topParent;
        boolean identifier;
        boolean contextItem;
        boolean local;
        String composition;
        String equalsString;
        String lineValue;
        Name lineName;

        public ImportHeading() {
            column = -1;
            heading = null;
            name = null;
            parentOf = null;
            childOfString = null;
            removeFromString = null;
            identityHeading = -1;
            childHeading = -1;
            childOf = null;
            removeFrom = null;
            attribute = null;
            peerHeadings = new HashSet<Integer>();
            // topParent = null;
            identifier = false;
            contextItem = false;
            local = false;
            composition = null;
            equalsString = null;
            lineValue = "";
            lineName = null;
        }
    }

    private Date tryDate(String maybeDate, SimpleDateFormat df) {
        try {
            return df.parse(maybeDate);
        } catch (Exception e) {
            return null;
        }
    }

    static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    static final SimpleDateFormat ukdf2 = new SimpleDateFormat("dd/MM/yy");
    static final SimpleDateFormat ukdf4 = new SimpleDateFormat("dd/MM/yyyy");
    static final SimpleDateFormat ukdf3 = new SimpleDateFormat("dd MMM yyyy");

    // todo - probably a slightly nicer API call for this
    public Date isADate(String maybeDate) {
        Date date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, sdf);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf4);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf3);
        if (date != null) return date;
        return tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
    }

    // currently only two types of import supported and detection on file name (best idea?). Run the import and persist.

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
    // it feels like an enum or array could help here but I'm not sure . . .
    private void interpretClause(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportHeading heading, String clause) throws Exception {
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
        readClause = readClause(LANGUAGE, clause); // default language for itentifying the name
        if (readClause != null) {
            heading.attribute = readClause;
            heading.identifier = true;
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
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
        readClause = readClause(COMPOSITION, clause);
        if (readClause != null) {
            heading.composition = readClause;
            if (heading.composition.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
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

    // headings are clauses separated by semicolons, first is the heading name the onto the extra stuff
    // essentially parsing through all the relevant things in a heading to populate an ImportHeading

    private void interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, ImportHeading heading, List<String> attributeNames) throws Exception {
        StringTokenizer clauses = new StringTokenizer(headingString, ";");
        heading.heading = clauses.nextToken().replace(Name.QUOTE + "", "");
        heading.name = nameService.findByName(azquoMemoryDBConnection, heading.heading, attributeNames);//at this stage, look for a name, but don't create it unless necessary
        while (clauses.hasMoreTokens()) {
            interpretClause(azquoMemoryDBConnection, heading, clauses.nextToken().trim());
        }
    }

    private int findContextHeading(Name name, List<ImportHeading> headings) {
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            ImportHeading heading = headings.get(headingNo);
            if (heading.contextItem && heading.name.findAllParents().contains(name)) {
                return headingNo;
            }
        }
        return -1;
    }

    private int findHeading(String nameToFind, List<ImportHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            ImportHeading heading = headings.get(headingNo);
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

    private int findLowerLevelHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String peerName, List<ImportHeading> headings) throws Exception {
        //look for a column with a set name specified as a subset of the peer name
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            ImportHeading heading = headings.get(headingNo);
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

    Map<String, Long> trackers = new ConcurrentHashMap<String, Long>();

    // the big function that deals with data importing

    public void valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileType, List<String> attributeNames) throws Exception {
        trackers = new ConcurrentHashMap<String, Long>();
        // little local cache just to speed things up
        InputStream uploadFile = new FileInputStream(filePath);
        final HashMap<String, Name> namesFound = new HashMap<String, Name>();
        if (fileType.indexOf(" ") > 0) {
            //filetype should be first word only
            fileType = fileType.substring(0, fileType.indexOf(" "));
        }
        if (fileType.contains("_")) fileType = fileType.substring(0, fileType.indexOf("_"));
        long track = System.currentTimeMillis();
        CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        csvReader.setUseTextQualifier(true);
        csvReader.readHeaders();
        String[] headers2 = csvReader.getHeaders();
        //start again...
        csvReader.close();
        uploadFile = new FileInputStream(filePath);
        if (headers2.length < 2) {
            // new pipe support, hope it will work adding in here
            if (headers2.length == 1 && headers2[0].contains("|")) {
                csvReader = new CsvReader(uploadFile, '|', Charset.forName("UTF-8"));
            } else {
                csvReader = new CsvReader(uploadFile, ',', Charset.forName("UTF-8"));
            }
        } else {
            csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        }
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
                headers = importHeaders.split("¬");
            }
        }

        if (headers == null) {
            csvReader.readHeaders();
            headers = csvReader.getHeaders();
        } else {
            if (skipTopLine) {
                csvReader.readRecord();
            }
        }
        // correcting the comment : readHeaders is about creating a set of ImportHeadings
        // notable that internally it might use attributes from the relevant data import name to supplement the header information
        final List<ImportHeading> headings = new ArrayList<ImportHeading>();
        readHeaders(azquoMemoryDBConnection, headers, headings, fileType, attributeNames);
        // further information put into the ImportHeadings based off the initial info
        fillInHeaderInformation(azquoMemoryDBConnection, headings);
        int valuecount = 0; // purely for logging
        int lastReported = 0;
        // having read the headers go through each record
        int lineNo = 0;
        long trigger = 2000000;
        Long time = System.nanoTime();

        while (csvReader.readRecord()) { // now to the data itself, headers should have been sorted one way or another,
            lineNo++;
            // ImportHeading contextPeersItem = null;
            // if (csvReader.get(0).length() == 0) break;//break if the first line element is blank
            // ok it's worth noting here - the data for the line is being added to the ImportHeadings, thus after they are passed to functions that actually import the data.
            for (ImportHeading heading : headings) {
//                trackers.put(heading.name.getDefaultDisplayName(), 0L);
                heading.lineValue = csvReader.get(heading.column).intern();// since strings may be repeated intern, should save a bit of memory using the String pool
                if (heading.attribute != null && heading.attribute.equalsIgnoreCase(dateLang)) {
                    //interpret the date and change to standard form
                    //todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    // ok so the thing here is to try to standardise date formatting
                    Date date = isADate(heading.lineValue);
                    if (date != null) {
                        heading.lineValue = sdf.format(date);
                    }
                }
                heading.lineName = null;
            }

            if (headings.get(0).lineValue.length() > 0 || headings.get(0).column == -1) {//skip any line that has a blank in the first column unless we're not interested in that column
                getCompositeValues(headings);
                try {
                    valuecount += interpretLine(azquoMemoryDBConnection, headings, namesFound, attributeNames);
                } catch (Exception e) {
                    throw new Exception("error: line " + lineNo + " " + e.getMessage());
                }
                Long now = System.nanoTime();
                if (now - time > trigger) {
                    System.out.println("line no " + lineNo + " time = " + (now - time));
                }
                time = now;
                if (lineNo % 5000 == 0) {
                    System.out.println("imported line count " + lineNo);
                }
                if (valuecount - lastReported >= 5000) {
                    System.out.println("imported value count " + valuecount);
                    lastReported = valuecount;
                }
            }
        }
        System.out.println("csv dataimport took " + (System.currentTimeMillis() - track) + "ms for " + lineNo + " lines");
        System.out.println("---------- namesfound size " + namesFound.size());
        for (String trackName : trackers.keySet()) {
            System.out.println("---------- " + trackName + " \t\t" + trackers.get(trackName));
        }
    }

    private void readHeaders(AzquoMemoryDBConnection azquoMemoryDBConnection, String[] headers, List<ImportHeading> headings, String fileType, List<String> attributeNames) throws Exception {
        int col = 0;
        //if the file is of type (e.g.) 'sales' and there is a name 'dataimport sales', thisis uses as an interpreter.  It need not interpret every column heading, but
        // any attribute of the same name as a column heading will be used.
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
        String lastHeading = "";
        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                ImportHeading heading = new ImportHeading();
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
                    ImportHeading contextHeading = new ImportHeading();
                    interpretHeading(azquoMemoryDBConnection, head.substring(dividerPos + 1), contextHeading, attributeNames);
                    contextHeading.column = col;
                    contextHeading.contextItem = true;
                    headings.add(contextHeading);
                    head = head.substring(0, dividerPos);
                    dividerPos = head.lastIndexOf(headingDivider);
                }
                heading.column = col;
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
                headings.add(new ImportHeading());
            }
            col++;
        }
    }

    // each line of values (or names as it may practically be)
    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportHeading> headings, HashMap<String, Name> namesFound, List<String> attributeNames) throws Exception {
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
        for (ImportHeading importHeading : headings) {
            if (importHeading.local && importHeading.parentOf != null) { // local and it is a parentof, inside this function it will use the childheading set up - should maybe check for that instead??
                handleParent(azquoMemoryDBConnection, namesFound, importHeading, headings, attributeNames);
            }
        }
        long toolong = 200000;
        long time = System.nanoTime();
        ImportHeading contextPeersItem = null;
        for (ImportHeading heading : headings) {
            //long track = System.currentTimeMillis();
            if (heading.contextItem) {
                contextNames.add(heading.name);
                if (heading.name.getPeers().size() > 0) {
                    contextPeersItem = heading; // so skip this heading but now contextPeersItem is set?? I assume one name with peers allowed. Or the most recent one.
                }
            } else {
                if (contextNames.size() > 0 && heading.name != null) { // ok so some context names and a name for this column? I guess as in not an attribute column for example
                    contextNames.add(heading.name);
                    if (contextPeersItem != null) {
                        final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                        namesForValue.add(contextPeersItem.name);
                        boolean foundAll = true;
                        for (Name peer : contextPeersItem.name.getPeers().keySet()) {
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
                                int colFound = findHeading(peer.getDefaultDisplayName(), headings);
                                if (colFound < 0) {
                                    foundAll = false;
                                    break;
                                }
                                ImportHeading peerHeading = headings.get(colFound);
                                /*   UNNECESSARY CODE REMOVED BY WFC
                                if (peerHeading.lineName != null) {
                                    peer.addChildWillBePersisted(peerHeading.lineName);
                                } else {
                                    String peerValue = peerHeading.lineValue;
                                    peerHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, peerValue, peer, heading.local, attributeNames);
                                }
                                */
                                possiblePeer = peerHeading.lineName;
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
                            value = heading.lineValue;
                        } else {
                            value = "";
                        }
                        if (value.trim().length() > 0) { // no point storing if there's no value!
                            valueCount++;
                            // finally store our value and names for it
                            valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                        }

                    }
                    contextNames.remove(heading.name);
                }
                if (heading.peerHeadings.size() > 0) {
                    //ImportHeading headingWithPeers = heading;
                    if (heading.contextItem) {
                        contextPeersItem = heading;
                    }
                    /*if (contextPeersItem != null) {
                        headingWithPeers = contextPeersItem;
                    }*/
                    final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value

                    boolean hasRequiredPeers = findPeers(azquoMemoryDBConnection, namesFound, heading, headings, namesForValue, attributeNames);
                    if (hasRequiredPeers) {
                        // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                        value = heading.lineValue;
                    } else {
                        value = "";
                    }
                    if (value.trim().length() > 0) { // no point storing if there's no value!
                        valueCount++;
                        // finally store our value and names for it
                        valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                    }
                }
                if (heading.identityHeading >= 0 && heading.attribute != null && (!heading.attribute.equalsIgnoreCase(dateLang) || (isADate(heading.lineValue) == null))) {
                    // funnily enough no longer using attributes
                    handleAttribute(azquoMemoryDBConnection, namesFound, heading, headings);
                }
                if (heading.parentOf != null && !heading.local) {
                    handleParent(azquoMemoryDBConnection, namesFound, heading, headings, attributeNames);
                }
                if (heading.childOf != null) {
                    if (heading.lineName != null) { // could if ever be null after preparing the headers?
                        for (Name parent : heading.childOf) {
                            parent.addChildWillBePersisted(heading.lineName);
                        }
                    } else {
                        String childNameString = heading.lineValue;
                        if (childNameString.length() > 0) {
                            for (Name parent : heading.childOf) {
                                heading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, childNameString, parent, heading.local, attributeNames);
                            }
                        }
                    }
                }
                if (heading.removeFrom != null) {
                    if (heading.lineName != null) {
                        for (Name remove : heading.removeFrom) {
                            remove.removeFromChildrenWillBePersisted(heading.lineName);
                        }
                    }
                }
             }
            long now = System.nanoTime();
            if (now - time > toolong) {
                System.out.println(heading.heading + " took " + (now - time));
            }
            time = System.nanoTime();
        }
        return valueCount;
    }


    private void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportHeading> headings) throws Exception {
        for (ImportHeading importHeading : headings) {
            if (importHeading.heading != null) {
                // ok find the indexes of peers and get shirty if you can't find them
                if (importHeading.name != null && importHeading.name.getPeers().size() > 0 && !importHeading.contextItem) {
                    for (Name peer : importHeading.name.getPeers().keySet()) {
                        //three possibilities to find the peer:
                        int peerHeading = findHeading(peer.getDefaultDisplayName(), headings);
                        if (peerHeading == -1) {
                            peerHeading = findContextHeading(peer, headings);
                            if (peerHeading == -1) {
                                peerHeading = findLowerLevelHeading(azquoMemoryDBConnection, peer.getDefaultDisplayName(), headings);
                                if (peerHeading == -1) {
                                    throw new Exception("error: cannot find peer " + peer.getDefaultDisplayName() + " for " + importHeading.name.getDefaultDisplayName());
                                }
                            }
                        }
                        if (peerHeading >= 0) {
                            ImportHeading importPeer = headings.get(peerHeading);
                            if (importPeer.name == null) {
                                importPeer.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importPeer.heading, null, false);
                            }
                        }
                        importHeading.peerHeadings.add(peerHeading);
                    }
                }

                // having an attribute means the content of this column relates to a name in another column, need to find that name
                if (importHeading.attribute != null) { // && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
                    String headingName = importHeading.heading;
                    if (importHeading.equalsString != null) {// the equals string seems to be a way for a heading to have an alias, not completely clear on the usage but I'm guessing by this point the alias is no longer important so overwrite it with the equals value
                        headingName = importHeading.equalsString;
                    }
                    importHeading.identityHeading = findHeading(headingName, headings); // so if it's Customer,Address1 we need to find customer. THis findheading will look for the Customter with identifier = true or the first one without an attribute
                    if (importHeading.identityHeading >= 0) {
                        headings.get(importHeading.identityHeading).identifier = true;//it may not be true (as in found due to no attribute rather than language), in which case set it true now . . .need to consider this logic
                        // so now we have an identifier for this name go through all columns for this name and set the identity heading and attribute to avoid ambiguity? Of course if it set this on more than one heading it would make no sense
                        // some unclear logic here, this needs refactoring
                        for (ImportHeading heading2 : headings) {
                            //this is for the cases where the default display name is not the identifier.
                            if (heading2.heading != null && heading2.heading.equals(importHeading.heading) && heading2.attribute == null) {
                                heading2.attribute = Constants.DEFAULT_DISPLAY_NAME;
                                heading2.identityHeading = importHeading.identityHeading;
                                break;
                            }
                        }
                    }
                }
                // child of being in Azquo context
                if (importHeading.childOfString != null) {
                    importHeading.childOf = new HashSet<Name>();
                    String[] parents = importHeading.childOfString.split(",");//TODO this does not take into account names with commas inside.......
                    for (String parent : parents) {
                        importHeading.childOf.add(nameService.findOrCreateNameInParent(azquoMemoryDBConnection, parent, null, false));
                    }
                }
                // reverse
                if (importHeading.removeFromString != null) {
                    importHeading.removeFrom = new HashSet<Name>();
                    String[] removes = importHeading.removeFromString.split(",");//TODO this does not take into account names with commas inside.......
                    for (String remove : removes) {// also not language specific. THis is a simple lookup, don't want a find or create
                        importHeading.removeFrom.add(nameService.findByName(azquoMemoryDBConnection, remove));
                    }
                }
                // parent of being in context of this upload, if you can't find the heading throw an exception
                if (importHeading.parentOf != null) {
                    importHeading.childHeading = findHeading(importHeading.parentOf, headings);
                    if (importHeading.childHeading < 0) {
                        throw new Exception("error: cannot find column " + importHeading.parentOf + " for child of " + importHeading.heading);
                    }
                    //error = findTopParent(azquoMemoryDBConnection, importHeading, headings, attributeNames);
                    // if (error.length() > 0) return error;
                }
            }
        }
    }

    private List<String> setLocalLanguage(ImportHeading heading, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<String>();
        if (heading.attribute != null && !heading.attribute.equalsIgnoreCase(dateLang)) {
            languages.add(heading.attribute);
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;

    }

    // namesFound is a cache. Then the heading we care about then the list of all headings.
    private void handleParent(AzquoMemoryDBConnection azquoMemoryDBConnection, HashMap<String, Name> namesFound, ImportHeading heading, List<ImportHeading> headings, List<String> attributeNames) throws Exception {
        if (heading.lineValue.length() == 0) { // so nothing to do
            return;
        }
        ImportHeading childHeading = headings.get(heading.childHeading);
        if (heading.lineName != null) { // This function is called in two places in interpret line, the first time this will be null the second time not
            if (heading.childOf != null) {
                for (Name parent : heading.childOf) { // apparently there can be multiple childofs, put the name for the line in th appropriate sets.
                    parent.addChildWillBePersisted(heading.lineName);
                }
            }
        } else {
            heading.lineName = includeInParents(azquoMemoryDBConnection, namesFound, heading.lineValue, heading.childOf, heading.local, setLocalLanguage(heading, attributeNames));
        }
        if (childHeading.lineValue.length() == 0) {
            throw new Exception("blank value for parent of " + heading.lineValue);
        }
        if (childHeading.lineName == null) {
            childHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, childHeading.lineValue, heading.lineName, heading.local, setLocalLanguage(childHeading, attributeNames));
        }
        heading.lineName.addChildWillBePersisted(childHeading.lineName);
    }

    public void handleAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, HashMap<String, Name> namesFound, ImportHeading heading, List<ImportHeading> headings) throws Exception {
        ImportHeading identity = headings.get(heading.identityHeading);
        ImportHeading identityHeading = headings.get(heading.identityHeading);
        if (identityHeading.lineName == null) {
            if (identityHeading.lineValue.length() == 0) {
                return;
            }
            List<String> localAttributes = new ArrayList<String>();
            localAttributes.add(identity.attribute);
            identityHeading.lineName = includeInParents(azquoMemoryDBConnection, namesFound, identityHeading.lineValue, identityHeading.childOf, false, localAttributes);
        }
        String attribute = heading.attribute;
        if (attribute == null) {
            attribute = Constants.DEFAULT_DISPLAY_NAME;
        }
        identityHeading.lineName.setAttributeWillBePersisted(attribute, heading.lineValue);
    }

    private boolean findPeers(AzquoMemoryDBConnection azquoMemoryDBConnection, HashMap<String, Name> namesFound, ImportHeading heading, List<ImportHeading> headings, Set<Name> namesForValue, List<String> attributeNames) throws Exception {
        //ImportHeading headingWithPeers = heading;
        boolean hasRequiredPeers = true;
        namesForValue.add(heading.name); // the one at the top of this headingNo, the name with peers.
        for (int peerHeadingNo : heading.peerHeadings) { // go looking for the peers
            ImportHeading peerHeading = headings.get(peerHeadingNo);
            if (peerHeading.contextItem) {
                namesForValue.add(peerHeading.name);
            } else {
                if (peerHeading.lineName == null) {
                    if (peerHeading.lineValue.length() == 0) {
                        hasRequiredPeers = false;
                    } else {
                        List<String> peerLanguages = new ArrayList<String>();
                        //looking up in the correct language
                        if (peerHeading.attribute != null) {
                            peerLanguages.add(peerHeading.attribute);
                        } else {
                            peerLanguages.addAll(attributeNames);
                        }

                        peerHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, peerHeading.lineValue, headings.get(peerHeadingNo).name, false, peerLanguages);
                    }
                }
                // add to the set of names we're going to store against this value
                if (peerHeading.lineName != null) {
                    namesForValue.add(peerHeading.lineName);
                }
                //namesForValue.add(nameService.findOrCreateName(azquoMemoryDBConnection,peerVal + "," + peer.getName())) ;
            }
        }
        return hasRequiredPeers;
    }

    private void getCompositeValues(List<ImportHeading> headings) {
        int adjusted = 2;
        //loops in case there are multiple levels of dependencies
        while (adjusted > 1) {
            adjusted = 0;
            for (ImportHeading heading : headings) {
                if (heading.composition != null) {
                    String result = heading.composition;
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            int compItem = findHeading(result.substring(headingMarker + 1, headingEnd), headings);
                            if (compItem >= 0) {
                                result = result.replace(result.substring(headingMarker, headingEnd + 1), headings.get(compItem).lineValue);
                            }
                        }
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    if (!result.equals(heading.lineValue)) {
                        heading.lineValue = result;
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
            ImportHeading importHeading = new ImportHeading();
            if (st.hasMoreTokens()) {
                List<Name> children = new ArrayList<Name>();
                String setName = st.nextToken().replace("\"", "");//sometimes the last line of imported spreadsheets has come up as ""
                if (setName.length() > 0) {
                    interpretHeading(azquoMemoryDBConnection, setName, importHeading, attributeNames);
                    if (importHeading.heading.equalsIgnoreCase(sheetSetName)) {
                        importHeading.name = sheetSet;
                    } else {
                        importHeading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importHeading.heading, sheetSet, true, attributeNames);
                    }
                    if (importHeading.name != null) { // is this a concern? I'll throw an exception in case (based on IntelliJ warning)
                        Name set = importHeading.name;

                        while (st.hasMoreTokens()) {
                            String element = st.nextToken();
                            Name child;
                            if (element.length() > 0) {
                                int localPos = element.toLowerCase().indexOf(";local");
                                if (localPos > 0 || importHeading.local) {
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
                        throw new Exception("Import heading name was null : " + importHeading);
                    }
                }
            }
        }
    }
}