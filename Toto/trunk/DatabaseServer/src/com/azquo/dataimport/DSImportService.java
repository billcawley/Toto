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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by cawley on 20/05/15.
 * <p/>
 * Has a fair bit of the logic that was in the original import service.
 */
public class DSImportService {

    private static class NameParent {
        String name;
        Name parent;

        public NameParent(String name, Name parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    private static final String headingDivider = "|";
    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private DSSpreadsheetService dsSpreadsheetService;


    public static final String IDENTIFIER = "key";
    public static final String CHILDOF = "child of ";
    public static final String PARENTOF = "parent of ";
    public static final String ATTRIBUTE = "attribute ";
    public static final String LANGUAGE = "language ";
    public static final String PLURAL = "plural ";
    public static final String PEERS = "peers";
    public static final String LOCAL = "local";
    public static final String EQUALS = "equals";
    public static final String COMPOSITION = "composition";


    static class ImportHeading {
        int column;
        String heading;
        Name name;
        String parentOf;
        String childOfString;
        int identityHeading;
        int childHeading;
        Name childOf;
        String attribute;
        Set<Integer> peerHeadings;
        String plural;
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
            identityHeading = -1;
            childHeading = -1;
            childOf = null;
            attribute = null;
            peerHeadings = new HashSet<Integer>();
            plural = null;
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

    public void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames) throws Exception {
        if (fileType.toLowerCase().startsWith("sets")) {
            setsImport(dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken), new FileInputStream(filePath), attributeNames);
        } else {
            valuesImport(dsSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken), new FileInputStream(filePath), fileType, attributeNames);
        }
    }


    private String readClause(String keyName, String phrase) {
        if (phrase.length() >= keyName.length() && phrase.toLowerCase().startsWith(keyName)) {
            return phrase.substring(keyName.length()).trim();
        }
        return null;
    }

    private void interpretClause(AzquoMemoryDBConnection azquoMemoryDBConnection, ImportHeading heading, String clause) throws Exception {
        if (readClause(IDENTIFIER, clause) != null) {
            heading.identifier = true;
        }
        // Edd just tweaking things. Notably if fields like this were already null the logic is redundant, I'll assume they were not
        String readClause = readClause(PARENTOF, clause);
        if (readClause != null) {
            heading.parentOf = readClause;
        }
        readClause = readClause(CHILDOF, clause);
        if (readClause != null) {
            heading.childOfString = readClause;
        }
        readClause = readClause(LANGUAGE, clause);
        if (readClause != null) {
            heading.attribute = readClause;
            heading.identifier = true;
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        readClause = readClause(ATTRIBUTE, clause);
        if (readClause != null) {
            heading.attribute = readClause.replace("`", "");
            if (heading.attribute.length() == 0) {
                throw new Exception(clause + " not understood");
            }
            if (heading.attribute.equalsIgnoreCase("name")) {
                heading.attribute = Constants.DEFAULT_DISPLAY_NAME;
            }
        }
        if (readClause(LOCAL, clause) != null) {
            heading.local = true;
        }
        readClause = readClause(PLURAL, clause);
        if (readClause != null) {
            heading.plural = readClause;
            if (heading.plural.length() == 0) {
                throw new Exception(clause + " not understood");
            }
        }
        readClause = readClause(EQUALS, clause);
        if (readClause != null) {
            heading.equalsString = readClause;
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

    private void interpretHeading(AzquoMemoryDBConnection azquoMemoryDBConnection, String headingString, ImportHeading heading, List<String> attributeNames) throws Exception {
        StringTokenizer clauses = new StringTokenizer(headingString, ";");
        heading.heading = clauses.nextToken();
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
    public Name includeInSet(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<NameParent, Name> namesFound, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent
        NameParent np = new NameParent(name, parent);
        Name found = namesFound.get(np);
        if (found != null) {
            return found;
        }
        found = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, name, parent, local, attributeNames);
        namesFound.put(np, found);
        return found;
    }

    public void valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final InputStream uploadFile, String fileType, List<String> attributeNames) throws Exception {
        // little local cache just to speed things up
        final HashMap<NameParent, Name> namesFound = new HashMap<NameParent, Name>();
        if (fileType.indexOf(" ") > 0) {
            //filetype should be first word only
            fileType = fileType.substring(0, fileType.indexOf(" "));
        }
        long track = System.currentTimeMillis();
        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        csvReader.setUseTextQualifier(true);
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        // what we're doing here is going through the headers, First thing to do is to set up the peers if defined for a header
        // then we find or create each header as a name in the database. If the name has peers it's added to the nameimportheading map, a way to find the header for that name with peers
        // namesWithPeersHeaderMap is a map of the names which have peers, colums headed by such names will have the value in them, hence why we need to hold the header so we cna get the value
        //final HashMap<Name, String> namesWithPeersHeaderMap = new HashMap<Name, String>();
        final List<ImportHeading> headings = new ArrayList<ImportHeading>();
        readHeaders(azquoMemoryDBConnection, headers, headings, fileType, attributeNames);
        fillInHeaderInformation(azquoMemoryDBConnection, headings);
        int valuecount = 0; // purely for logging
        int lastReported = 0;
        // having read the headers go through each record
        int lineNo = 0;
        while (csvReader.readRecord()) {
            lineNo++;
            //ImportHeading contextPeersItem = null;
            if (csvReader.get(0).length() == 0) break;//break if the first line element is blank
            for (ImportHeading heading : headings) {
                heading.lineValue = csvReader.get(heading.column);
                heading.lineName = null;
            }
            getCompositeValues(headings);
            valuecount += interpretLine(azquoMemoryDBConnection, headings, namesFound, attributeNames);
            if (valuecount - lastReported > 5000) {
                System.out.println("imported value count " + valuecount);
                lastReported = valuecount;
            }

        }
        System.out.println("csv dataimport took " + (System.currentTimeMillis() - track) + "ms for " + lineNo + " lines");
        azquoMemoryDBConnection.persist();
    }


    private void readHeaders(AzquoMemoryDBConnection azquoMemoryDBConnection, String[] headers, List<ImportHeading> headings, String fileType, List<String> attributeNames) throws Exception {
        int col = 0;
        //if the file is of type (e.g.) 'sales' and there is a name 'dataimport sales', thisis uses as an interpreter.  It need not interpret every column heading, but
        // any attribute of the same name as a column heading will be used.
        Name importInterpreter = nameService.findByName(azquoMemoryDBConnection, "dataimport " + fileType, attributeNames);
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
                interpretHeading(azquoMemoryDBConnection, head, heading, attributeNames);
                headings.add(heading);
            } else {
                headings.add(new ImportHeading());
            }
            col++;
        }
    }


    private int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportHeading> headings, HashMap<NameParent, Name> namesFound, List<String> attributeNames) throws Exception {
        List<Name> contextNames = new ArrayList<Name>();
        String value;
        int valueCount = 0;
        ImportHeading contextPeersItem = null;
        /*
        for (ImportHeading importHeading:headings){
             if (!importHeading.local){
                importHeading.lineName = includeInSet(azquoMemoryDBConnection,namesFound,importHeading.lineValue,null,false,attributeNames);
            }
        }
        */
        for (ImportHeading importHeading : headings) {
            if (importHeading.local && importHeading.parentOf != null) {
                handleParent(azquoMemoryDBConnection, namesFound, importHeading, headings, attributeNames);
            }
        }
        for (ImportHeading heading : headings) {
            if (heading.contextItem) {
                contextNames.add(heading.name);
                if (heading.name.getPeers().size() > 0) {
                    contextPeersItem = heading;
                }
            } else {
                if (contextNames.size() > 0 && heading.name != null) {
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
                                if (peerHeading.lineName != null) {
                                    peer.addChildWillBePersisted(peerHeading.lineName);
                                } else {
                                    String peerValue = peerHeading.lineValue;
                                    peerHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, peerValue, peer, heading.local, attributeNames);
                                }
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
                if (heading.identityHeading >= 0) {
                    handleAttribute(azquoMemoryDBConnection, namesFound, heading, headings, attributeNames);
                }
                if (heading.parentOf != null && !heading.local) {
                    handleParent(azquoMemoryDBConnection, namesFound, heading, headings, attributeNames);
                }

                if (heading.childOf != null) {
                    if (heading.lineName != null) {
                        heading.childOf.addChildWillBePersisted(heading.lineName);
                    } else {
                        String childNameString = heading.lineValue;
                        if (childNameString.length() > 0) {
                            heading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, childNameString, heading.childOf, heading.local, attributeNames);
                        }
                    }
                }
            }
        }
        return valueCount;
    }


    private void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportHeading> headings) throws Exception {
        for (ImportHeading importHeading : headings) {
            if (importHeading.heading != null) {
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
                if (importHeading.attribute != null && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
                    //first remove the parent
                    if (importHeading.equalsString != null) {
                        importHeading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importHeading.equalsString, null, false);//global name
                    } else {
                        importHeading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importHeading.heading, null, false);
                    }
                    // not sure why this line below is in the code.  If it is to remove the commas then 'findHeading' caters for this.  Removed to cater for 'equals'
                    //importHeading.heading = importHeading.name.getDefaultDisplayName();
                    importHeading.identityHeading = findHeading(importHeading.name.getDefaultDisplayName(), headings);
                    if (importHeading.identityHeading >= 0) {
                        headings.get(importHeading.identityHeading).identifier = true;//actively set the identifier as setting the attribute below might confuse the issue
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
                if (importHeading.childOfString != null) {
                    importHeading.childOf = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importHeading.childOfString, null, false);
                }
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
        /*
        //attribute topparents must be found after the identity heading topparent is found
        for (ImportHeading importHeading:headings) {
            if (importHeading.heading != null) {
                if (importHeading.attribute != null) {
                    findTopParent(azquoMemoryDBConnection, importHeading, headings, attributeNames);

                }
            }
        }
        */
    }

    private void handleParent(AzquoMemoryDBConnection azquoMemoryDBConnection, HashMap<NameParent, Name> namesFound, ImportHeading heading, List<ImportHeading> headings, List<String> attributeNames) throws Exception {
        ImportHeading childHeading = headings.get(heading.childHeading);
        if (heading.lineValue.length() == 0) {
            return;
        }
        if (heading.lineName != null && heading.childOf != null) {
            heading.childOf.addChildWillBePersisted(heading.lineName);
        } else {
            heading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, heading.lineValue, heading.childOf, heading.local, attributeNames);
        }
        if (childHeading.lineName == null) {
            childHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, childHeading.lineValue, heading.lineName, heading.local, attributeNames);
        }
        heading.lineName.addChildWillBePersisted(childHeading.lineName);
    }

    public void handleAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, HashMap<NameParent, Name> namesFound, ImportHeading heading, List<ImportHeading> headings, List<String> attributeNames) throws Exception {
        ImportHeading identity = headings.get(heading.identityHeading);
        ImportHeading identityHeading = headings.get(heading.identityHeading);
        if (identityHeading.lineName == null) {
            if (identityHeading.lineValue.length() == 0) {
                return;
            }
            List<String> localAttributes = new ArrayList<String>();
            localAttributes.add(identity.attribute);
            localAttributes.addAll(attributeNames);

            identityHeading.lineName = includeInSet(azquoMemoryDBConnection, namesFound, identityHeading.lineValue, identityHeading.name, false, localAttributes);
        }
        String attribute = heading.attribute;
        if (attribute == null) {
            attribute = Constants.DEFAULT_DISPLAY_NAME;
        }
        identityHeading.lineName.setAttributeWillBePersisted(attribute, heading.lineValue);
    }

    private boolean findPeers(AzquoMemoryDBConnection azquoMemoryDBConnection, HashMap<NameParent, Name> namesFound, ImportHeading heading, List<ImportHeading> headings, Set<Name> namesForValue, List<String> attributeNames) throws Exception {
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


    public void setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final InputStream uploadFile, List<String> attributeNames) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String line;
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            ImportHeading importHeading = new ImportHeading();
            if (st.hasMoreTokens()) {
                String setName = st.nextToken().replace("\"", "");//sometimes the last line of imported spreadsheets has come up as ""
                if (setName.length() > 0) {
                    interpretHeading(azquoMemoryDBConnection, setName, importHeading, attributeNames);
                    importHeading.name = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, importHeading.heading, null, false, attributeNames);
                    if (importHeading.name != null) { // is this a concern? I'll throw an exception in case (based on IntelliJ warning)
                        Name set = importHeading.name;
                        nameService.clearChildren(set);
                        while (st.hasMoreTokens()) {
                            String element = st.nextToken();
                            if (element.length() > 0) {
                                int localPos = element.toLowerCase().indexOf(";local");
                                if (localPos > 0 || importHeading.local) {
                                    if (localPos > 0) {
                                        element = element.substring(0, localPos);
                                    }
                                    nameService.findOrCreateNameInParent(azquoMemoryDBConnection, element, set, false, attributeNames);
                                } else {
                                    Name child = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, element, null, false, attributeNames);
                                    set.addChildWillBePersisted(child);
                                }
                            }
                        }
                    } else {
                        throw new Exception("Import heading name was null : " + importHeading);
                    }
                }
            }
        }
    }

}
