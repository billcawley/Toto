package com.azquo.service;

import com.azquo.admindao.OnlineReportDAO;
import com.azquo.admindao.UploadRecordDAO;
import com.azquo.admindao.UserChoiceDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.adminentities.UploadRecord;
import com.azquo.memorydb.Name;
import com.azquo.view.AzquoBook;
import com.csvreader.CsvReader;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * Created by bill on 13/12/13.
 * service to process files used to import data into the database
 */

public final class ImportService {


    //private static final String reportPath = "/home/bill/apache-tomcat-7.0.47/import/";
    public static final String reportPath = "/home/azquo/onlinereports/";

    private static final String headingDivider = "|";
    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private UploadRecordDAO uploadRecordDAO;
    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private AdminService adminService;
    @Autowired
    private UserChoiceDAO userChoiceDAO;

    public final String IDENTIFIER = "key";
    public final String CHILDOF = "child of ";
    public final String PARENTOF = "parent of ";
    public final String STRUCTURE = "structure ";
    public final String ATTRIBUTE = "attribute ";
    public final String LANGUAGE = "language ";
    public final String PLURAL = "plural ";
    public final String PEERS = "peers";


    class ImportHeading{
        int column;
        String heading;
        Name name;
        Name structureName;
        String parentOf;
        String childOfString;
        int identityHeading;
        int childHeading;
        int structureHeading;
        Name childOf;
        String attribute;
        Set<Integer> peerHeadings;
        String plural;
        Name topParent;
        boolean identifier;
        boolean contextItem;

        public ImportHeading(){
            column=-1;
            heading = null;
            name = null;
            structureName = null;
            parentOf = null;
            childOfString = null;
            identityHeading = -1;
            childHeading = -1;
            structureHeading = -1;
            childOf = null;
            attribute = null;
            peerHeadings = new HashSet<Integer>();
            plural = null;
            topParent = null;
            identifier = false;
            contextItem = false;


        }


       }


    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public String importTheFile(final LoggedInConnection loggedInConnection, String fileName, InputStream uploadFile, String fileType, final String strCreate, boolean skipBase64)
            throws Exception {

        loggedInConnection.setNewProvenance("import", fileName);
        if (loggedInConnection.getAzquoMemoryDB() == null) {
            return "error: no database set";
        }
        String tempFile = "";
        boolean create = false;
        if (strCreate != null && strCreate.equals("true")) {
            create = true;
        }
        if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            if (skipBase64) {
                tempFile = tempFileWithoutDecoding(uploadFile, fileName);
            } else {
                tempFile = decode64(uploadFile, fileName);
            }
        } else {
            if (fileName.endsWith(".zip")) {
                if (skipBase64) {
                    tempFile = tempFileWithoutDecoding(uploadFile, fileName);
                } else {
                    tempFile = decode64(uploadFile, fileName);
                }
                fileName = fileName.substring(0, fileName.length() - 4);

                tempFile = unzip(tempFile, fileName.substring(fileName.length() - 4));
            }

        }
        String error = "";
        if (fileName.contains(".xlsx")) {
            error = readBook(loggedInConnection, fileName, tempFile);

        }else if (fileName.contains(".xls")){
            error = readBook(loggedInConnection, fileName, tempFile);

        } else {
            if (tempFile.length() > 0) {
                uploadFile = new FileInputStream(tempFile);
            }
            error = readPreparedFile(loggedInConnection, uploadFile, fileType);
        }
        nameService.persist(loggedInConnection);
        Database db = loggedInConnection.getAzquoMemoryDB().getDatabase();
        if (fileType==null){
            fileType = "spreadsheet";
        }
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), db.getBusinessId(), db.getId(), loggedInConnection.getUser().getId(), fileName, fileType, error);
        uploadRecordDAO.store(uploadRecord);

        return error;
    }

    private String readPreparedFile(LoggedInConnection loggedInConnection, InputStream uploadFile, String fileType) throws Exception {


        String origLanguage = loggedInConnection.getLanguage();
        String result = "";
                // we will pay attention onn the attribute import and replicate

       if (fileType.toLowerCase().startsWith("sets")) {
            result = setsImport(loggedInConnection, uploadFile);

       }else{
            result = valuesImport(loggedInConnection, uploadFile);
       }
       loggedInConnection.setLanguage(origLanguage);
       return result;

    }

    private String readClause(String keyName, String phrase){
        if (phrase.length() >= keyName.length() && phrase.toLowerCase().startsWith(keyName)){
            return phrase.substring(keyName.length()).trim();
        }
        return null;
    }

    private String interpretClause(LoggedInConnection loggedInConnection, ImportHeading heading, String clause)throws Exception{
         if (readClause(IDENTIFIER, clause)!= null){
            heading.identifier = true;
        }
        if (readClause(PARENTOF, clause) != null){
            heading.parentOf = readClause(PARENTOF, clause);
            if (heading.parentOf == null){
                return "error: " + clause + " not understood";
            }
        }
        if (readClause(CHILDOF,clause) != null){
            heading.childOfString =readClause(CHILDOF, clause);
         }
        if (readClause(LANGUAGE, clause)!=null){
            heading.attribute = readClause(LANGUAGE, clause);
            heading.identifier = true;

            if (heading.attribute.length() == 0){
                return "error: " + clause + " not understood";
            }
              //heading.name = nameService.findOrCreateName(loggedInConnection, heading.heading);
        }
       if (readClause(ATTRIBUTE, clause)!=null){
           heading.attribute = readClause(ATTRIBUTE, clause);

           if (heading.attribute.length() == 0){
               return "error: " + clause + " not understood";
           }
           if (heading.attribute.equalsIgnoreCase("name")){
               heading.attribute = Name.DEFAULT_DISPLAY_NAME;
           }
           //heading.name = nameService.findOrCreateName(loggedInConnection, heading.heading);
       }
       if (readClause(STRUCTURE, clause) !=null){
            heading.structureName = nameService.findOrCreateName(loggedInConnection,readClause(STRUCTURE, clause));
            if (heading.structureName == null){
                return "error: " + clause + " not understood";
            }

       }
        if (readClause(PLURAL, clause) !=null){
            heading.plural= readClause(PLURAL, clause);
            if (heading.plural.length() == 0){
                return "error: " + clause + " not understood";
            }

        }
        if (readClause(PEERS,clause)!=null){
            // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
            heading.name = nameService.findOrCreateName(loggedInConnection, heading.heading);
            String peersString = readClause(PEERS, clause);
            if (peersString.startsWith("{")) { // array, typically when creating in the first place, the service call will insert after any existing
                if (peersString.contains("}")) {
                    peersString = peersString.substring(1, peersString.indexOf("}"));
                    final StringTokenizer st = new StringTokenizer(peersString, ",");
                    final List<String> peersToAdd = new ArrayList<String>();
                    String notFoundError = "";
                    final LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        String peerName = st.nextToken().trim();
                        if (peerName.indexOf(Name.QUOTE) == 0) {
                            peerName = peerName.substring(1, peerName.length() - 1); // trim escape chars
                        }
                        Name peer = nameService.findOrCreateName(loggedInConnection,peerName);
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
                        return "error:name not found:`" + notFoundError + "`";
                    }
                    return "";
                } else {
                    return "error:Unclosed }";
                }
            }
       }
       return "";
    }

    private String interpretHeading(LoggedInConnection loggedInConnection, String headingString, ImportHeading heading) throws Exception{

        StringTokenizer clauses = new StringTokenizer(headingString, ";");

        heading.heading = clauses.nextToken();
        heading.name = nameService.findByName(loggedInConnection,heading.heading);//at this stage, look for a name, but don't create it unless necessary
        while (clauses.hasMoreTokens()){
            String error = interpretClause(loggedInConnection, heading, clauses.nextToken());
            if (error.length() > 0){
                return error;
            }
        }
        return "";
    }


    private int findContextHeading(Name name, List<ImportHeading> headings){

          for (int headingNo = 0;headingNo < headings.size();headingNo++){
            ImportHeading heading = headings.get(headingNo);
            if (heading.contextItem && heading.name.findAllParents().contains(name)){
                return headingNo;

            }
        }
        return -1;
    }

    private int findHeading(String nameToFind, List<ImportHeading> headings){
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size();headingNo++) {
            ImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().indexOf(nameToFind.toLowerCase() + ",") == 0) && (heading.identifier || heading.attribute == null) ) {
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

    private int findLowerLevelHeading(LoggedInConnection loggedInConnection, String peerName, List<ImportHeading> headings)throws  Exception{
        //look for a column with a set name specified as a subset of the peer name
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size();headingNo++) {
            ImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.toLowerCase().contains(","+ peerName.toLowerCase()))) {
                heading.name = nameService.findOrCreateName(loggedInConnection, heading.heading);//may need to create it

                return headingNo;
            }
        }
        return headingFound;
    }






    public String findTopParent(LoggedInConnection loggedInConnection, ImportHeading heading, List<ImportHeading> headings) throws Exception{
        //need to work out the topparent for use when classifing names found in this column
        ImportHeading child = heading;
        if (heading.identityHeading >=0){
            child = headings.get(heading.identityHeading);
        }
        while (child.parentOf != null){
            child.childHeading = findHeading(child.parentOf, headings);
            if (child.childHeading < 0 ){
                return "error: cannot find " + child.parentOf;
            }
            child = headings.get(child.childHeading);
        }
        if (child.name == null){
            child.name = nameService.findOrCreateName(loggedInConnection,child.heading);
        }
        heading.topParent = child.name;
        return "";
    }



    private Name includeInSet(LoggedInConnection loggedInConnection, String name, Name parent, Map<String, Name> namesFound)throws  Exception{

        //maybe should include topname in findName below
        String findName = name + ", " + parent.getDefaultDisplayName();
        //Name found = namesFound.get(findName);
       // if (found != null){
      //      return found;
       // }
        Name found = nameService.findOrCreateName(loggedInConnection, name, parent);
        //namesFound.put(findName, found);
        return found;

    }

    public String valuesImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile) throws Exception {

        //TODO  SPLIT THIS UP!

        long track = System.currentTimeMillis();

        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        csvReader.setUseTextQualifier(true);
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        // what we're doing here is going through the headers, First thing to do is to set up the peers if defined for a header
        // then we find or create each header as a name in the database. If the name has peers it's added to the nameimportheading map, a way to find the header for that name with peers
        // namesWithPeersHeaderMap is a map of the names which have peers, colums headed by such names will have the value in them, hence why we need to hold the header so we cna get the value
        final HashMap<Name, String> namesWithPeersHeaderMap = new HashMap<Name, String>();
        final List<ImportHeading> headings = new ArrayList<ImportHeading>();
        int col = 0;
        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                ImportHeading heading = new ImportHeading();
                String head = header;
                int dividerPos = head.lastIndexOf(headingDivider);
                while (dividerPos > 0){
                    ImportHeading contextHeading = new ImportHeading();
                    String result = interpretHeading(loggedInConnection,head.substring(dividerPos + 1), contextHeading);
                    contextHeading.column = col;
                    contextHeading.contextItem = true;
                    headings.add(contextHeading);
                    head = head.substring(0, dividerPos);
                    dividerPos = head.lastIndexOf(headingDivider);


                }
                heading.column = col;

                final String result = interpretHeading(loggedInConnection, head, heading);
                headings.add(heading);
                col++;
            }else{
                headings.add(new ImportHeading());
            }
        }
        for (ImportHeading importHeading:headings){
            if (importHeading.heading != null){
                if (importHeading.name != null && importHeading.name.getPeers().size() > 0 && !importHeading.contextItem){
                    for (Name peer:importHeading.name.getPeers().keySet()){
                        //three possibilities to find the peer:
                        int peerHeading = findHeading(peer.getDefaultDisplayName(), headings);
                        if (peerHeading == -1) {
                            peerHeading = findContextHeading(peer, headings);
                            if (peerHeading == -1) {
                                peerHeading = findLowerLevelHeading(loggedInConnection,peer.getDefaultDisplayName(), headings);
                                if (peerHeading == -1) {
                                    return "error: cannot find peer " + peer.getDefaultDisplayName() + " for " + importHeading.name.getDefaultDisplayName();
                                }

                            }
                        }
                        if (peerHeading>=0) {
                            ImportHeading importPeer = headings.get(peerHeading);
                            if (importPeer.name == null) {
                                importPeer.name = nameService.findOrCreateName(loggedInConnection, importPeer.heading);
                            }
                        }
                        importHeading.peerHeadings.add(peerHeading);
                      }
                }
                if (importHeading.attribute != null && !importHeading.attribute.equals(Name.DEFAULT_DISPLAY_NAME)){
                    //first remove the parent
                    importHeading.name = nameService.findOrCreateName(loggedInConnection,importHeading.heading);
                    importHeading.heading = importHeading.name.getDefaultDisplayName();
                    importHeading.identityHeading = findHeading(importHeading.heading, headings);
                    if (importHeading.identityHeading >=0){
                        for (ImportHeading heading2:headings) {
                            if (heading2.heading != null && heading2.heading.equals(importHeading.heading) && heading2.attribute == null) {
                                heading2.attribute = Name.DEFAULT_DISPLAY_NAME;
                                heading2.identityHeading = importHeading.identityHeading;
                                break;
                            }
                        }

                    }
                    findTopParent(loggedInConnection, importHeading, headings);
                }
                if (importHeading.parentOf != null){
                    importHeading.childHeading = findHeading(importHeading.parentOf, headings);
                          if (importHeading.childHeading < 0){
                        return "error: cannot find column " + importHeading.parentOf + " for child of " + importHeading.heading;
                    }
                    String error = findTopParent(loggedInConnection, importHeading, headings);
                    if (error.length() > 0) return error;
                }
                if (importHeading.structureName != null){
                    importHeading.structureHeading = findHeading(importHeading.structureName.getDefaultDisplayName(), headings);
                    if (importHeading.structureHeading < 0){
                        return "error: cannot find column " + importHeading.structureName.getDefaultDisplayName() + " for structure " + importHeading.name.getDefaultDisplayName();
                    }
                }
                if (importHeading.childOfString != null){
                    if (importHeading.topParent == null){
                        importHeading.childOf = nameService.findOrCreateName(loggedInConnection, importHeading.childOfString);
                    }else{
                        if (!importHeading.topParent.getDefaultDisplayName().equals(importHeading.childOfString)){
                           importHeading.childOf = nameService.findOrCreateName(loggedInConnection, importHeading.childOfString, importHeading.topParent);
                        }else{
                            importHeading.childOf = importHeading.topParent;
                        }
                    }
                }
            }
        }


        int valuecount = 0; // purely for logging
        // little local cache just to speed things up
        final HashMap<String, Name> namesFound = new HashMap<String, Name>();
        // having read the headers go through each record
        ImportHeading contextPeersItem = null;
        int lineNo = 0;
        while (csvReader.readRecord()) {
            lineNo++;
            if (csvReader.get(0).length()==0)break;//break if the first line element is blank
            Map<Name,Name> contextNames = new HashMap<Name,Name>();
            String value;
            for (int headingNo = 0; headingNo < headings.size();headingNo++) {
                 ImportHeading heading = headings.get(headingNo);
                if (heading.contextItem){
                    contextNames.put(heading.name.findTopParent(),heading.name);
                    if (heading.name.getPeers().size() > 0){
                        contextPeersItem = heading;
                    }
                }else {
                    if (contextNames.size() > 0 && heading.name != null){
                        contextNames.put(heading.name.findTopParent(),heading.name);
                        if (contextPeersItem != null){
                            final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                            namesForValue.add(contextPeersItem.name);
                            boolean foundAll = true;
                            for (Name peer:contextPeersItem.name.getPeers().keySet()){
                                Name possiblePeer = contextNames.get(peer.findTopParent());
                                if (possiblePeer == null){
                                    //look at the headings
                                    int colFound = findHeading(peer.getDefaultDisplayName(), headings);
                                    if (colFound<0){
                                        foundAll = false;
                                        break;
                                    }
                                    String peerValue = csvReader.get(headings.get(colFound).column);
                                    possiblePeer = includeInSet(loggedInConnection,peerValue, peer, namesFound);
                                }
                                if (nameService.inParentSet(possiblePeer, peer.getChildren())!=null){
                                    namesForValue.add(possiblePeer);
                                }else{
                                    foundAll = false;
                                    break;
                                }


                            }
                            if (foundAll){
                                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                                value = csvReader.get(heading.column);
                            } else {
                                value = "";
                            }
                            if (value.trim().length() > 0) { // no point storing if there's no value!
                                valuecount++;
                                // finally store our value and names for it
                                valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, namesForValue);
                                if (valuecount % 5000 == 0) {
                                    System.out.println("storing value " + valuecount);
                                }

                            }

                        }
                    }
                    if (heading.peerHeadings.size() > 0) {
                        ImportHeading headingWithPeers = heading;
                        if (heading.contextItem) {
                            contextPeersItem = heading;
                        }
                        if (contextPeersItem != null) {
                            headingWithPeers = contextPeersItem;
                        }
                        boolean hasRequiredPeers = true;
                        final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                        namesForValue.add(heading.name); // the one at the top of this headingNo, the name with peers.
                        for (int peerHeadingNo : headingWithPeers.peerHeadings) { // go looking for the peers
                            ImportHeading peerHeading = headings.get(peerHeadingNo);
                            if (peerHeading.contextItem) {
                                namesForValue.add(peerHeading.name);
                            } else {
                                final String peerVal = csvReader.get(peerHeading.column);
                                if (peerVal == null || peerVal.length() == 0) { // the file specified
                                    hasRequiredPeers = false;
                                } else {
                                    //storeStructuredName(peer,peerVal, loggedInConnection);
                                    // lower level names first so the syntax is something like Knightsbridge, London, UK
                                    // hence we're passing a multi level name lookup to the name service, whatever is in that headingNo with the header on the end
                                    // sometimes quotes are used in the middle of names to indicate inches - e.g. '4" pipe'      - store as '4inch pipe'
                                    final String nameToFind = Name.QUOTE + peerVal + Name.QUOTE + "," + Name.QUOTE + headings.get(peerHeadingNo).name.getDefaultDisplayName() + Name.QUOTE;
                                    // check the local cache first
                                    Name nameFound = namesFound.get(nameToFind);
                                    if (nameFound == null) {
                                        String origLanguage = loggedInConnection.getLanguage();
                                        if (peerHeading.attribute != null) {
                                            loggedInConnection.setLanguage(peerHeading.attribute);
                                        }

                                        nameFound = nameService.findOrCreateName(loggedInConnection, peerVal, headings.get(peerHeadingNo).name);
                                        loggedInConnection.setLanguage(origLanguage);
                                        if (nameFound != null) {
                                            namesFound.put(nameToFind, nameFound);
                                        }
                                    }
                                    // add to the set of names we're going to store against this value
                                    if (nameFound != null) {
                                        namesForValue.add(nameFound);
                                    }
                                }
                                //namesForValue.add(nameService.findOrCreateName(loggedInConnection,peerVal + "," + peer.getName())) ;
                            }
                        }
                        if (hasRequiredPeers) {
                            // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                            value = csvReader.get(heading.column);
                        } else {
                            value = "";
                        }
                        if (value.trim().length() > 0) { // no point storing if there's no value!
                            valuecount++;
                            // finally store our value and names for it
                            valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, namesForValue);
                            if (valuecount % 5000 == 0) {
                                System.out.println("storing value " + valuecount);
                            }
                        }
                    }
                    if (heading.structureName != null) {
                        String itemName = csvReader.get(headings.get(heading.structureHeading).column);
                        String category = csvReader.get(heading.column);
                        if (category.trim().length() > 0) {
                            if (heading.name == null) {
                                heading.name = nameService.findOrCreateName(loggedInConnection, heading.heading);
                                heading.heading = heading.name.getDefaultDisplayName();
                            }
                            ImportHeading structureName = headings.get(heading.structureHeading);
                            if (structureName.name == null) {
                                structureName.name = nameService.findOrCreateName(loggedInConnection, structureName.heading);
                            }
                            String plural = structureName.plural;
                            if (plural == null) {
                                plural = structureName.heading + "s";
                            }
                            //create the name and structure
                            Name byCategory = nameService.findOrCreateName(loggedInConnection, plural + " by " + heading.name.getDefaultDisplayName(), structureName.name);
                            //remove the pupil from any other sets in the structure - structures are exclusive
                            Name memberName = includeInSet(loggedInConnection, itemName, structureName.name, namesFound);
                            for (Name testCategory : byCategory.getChildren()) {
                                if (memberName.getParents().contains(testCategory)) {
                                    testCategory.removeFromChildrenWillBePersisted(memberName);
                                }
                            }
                            Name thisSet = nameService.findOrCreateName(loggedInConnection, category + " " + plural, byCategory);
                            includeInSet(loggedInConnection, itemName, thisSet, namesFound);
                            //and put the category in its set.
                            includeInSet(loggedInConnection, category, heading.name, namesFound);
                        }
                    }
                    if (heading.identityHeading >= 0) {
                        ImportHeading identity = headings.get(heading.identityHeading);
                        String itemName = csvReader.get(headings.get(heading.identityHeading).column);
                        if (itemName.length() > 0) {
                            String origLanguage = loggedInConnection.getLanguage();
                            if (identity.identityHeading >= 0) {
                                loggedInConnection.setLanguage(identity.attribute);
                            }
                            if (heading.topParent == null) {
                                //may have escaped the checks...
                                String error = findTopParent(loggedInConnection, heading, headings);
                                if (error.length() > 0) return error;
                            }

                            Name name = includeInSet(loggedInConnection, itemName, heading.topParent, namesFound);
                            loggedInConnection.setLanguage(origLanguage);
                            name.setAttributeWillBePersisted(heading.attribute, csvReader.get(heading.column));
                            nameService.calcReversePolish(loggedInConnection, name);

                        }
                    }
                    if (heading.parentOf != null) {
                        String childName = csvReader.get(headings.get(heading.childHeading).column);
                        String parentName = csvReader.get(heading.column);
                        if (parentName.length() > 0) {
                            Name parentSet = null;
                            if (heading.childOf != null) {
                                parentSet = includeInSet(loggedInConnection, parentName, heading.childOf, namesFound);
                            } else {
                                parentSet = nameService.findOrCreateName(loggedInConnection, parentName);
                            }
                            String origLanguage = loggedInConnection.getLanguage();
                            if (headings.get(heading.childHeading).attribute != null) {
                                loggedInConnection.setLanguage(headings.get(heading.childHeading).attribute);
                            }
                            Name name = includeInSet(loggedInConnection, childName, parentSet, namesFound);
                            loggedInConnection.setLanguage(origLanguage);
                        }
                    }

                    if (heading.childOf != null) {
                        String childName = csvReader.get(heading.column);
                        if (childName.length() > 0) {
                            Name name = includeInSet(loggedInConnection, childName, heading.childOf, namesFound);
                        }
                    }
                }
            }
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms for " + lineNo + " lines");

        nameService.persist(loggedInConnection);

        return "";
    }



    public String findExistingDate(LoggedInConnection loggedInConnection, String element) throws Exception{
        //PROBABLY NOT NEEDED - HAD A PROBLEM WITH EXCEL IMPORTS, MAYBE SOLVED
        Name name = nameService.findByName(loggedInConnection,element);
        if (name == null){
            String alternateDate = element;
            //may need to add zeros
            int firstSlash = element.indexOf("/");
            int secondSlash = element.indexOf("/", firstSlash + 1);
            if (secondSlash - firstSlash < 2){
                alternateDate = element.substring(0,firstSlash + 1) + "0" + element.substring(firstSlash + 1);
            }
            if (firstSlash < 2){
                alternateDate = "0" + alternateDate;
            }
            name = nameService.findByName(loggedInConnection,alternateDate);
            if (name != null){
                element = alternateDate;
            }
        }
        return element;

    }

    public String setsImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile) throws Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String line;
        System.out.println("input");
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            ImportHeading importHeading = new ImportHeading();
            if (st.hasMoreTokens()) {
                String setName = st.nextToken();
                if (setName.length() > 0) {
                    String error = interpretHeading(loggedInConnection, setName, importHeading);
                    importHeading.name = nameService.findOrCreateName(loggedInConnection, importHeading.heading);
                    if (error.length() > 0) {
                        return error;
                    }
                    Name set = importHeading.name;
                    nameService.clearChildren(set);
                    while (st.hasMoreTokens()) {
                        String element = st.nextToken();
                        if (element.length() > 0) {
                            //the Excel formatter seems to get some dates wrong.  check for this
                            //if (element.indexOf("/") > 0 && element.length() < 10){
                            //     element = findExistingDate(loggedInConnection, element);
                            //   }
                            nameService.findOrCreateName(loggedInConnection, element, set);
                        }
                    }
                }
            }
        }
        return "";
    }


    // File pre processing functions. SHould maybe be hived off into utils?


    private String unzip(String fileName, String suffix) {

        String outputFile = fileName.substring(0, fileName.length() - 4);
        try {
            byte[] data = new byte[1000];
            int byteRead;


            ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileName)));
            // while((zin.getNextEntry()) != null){
            //READ ONE ENTRY ONLY...
            zin.getNextEntry();
            File tmpOutput = File.createTempFile(outputFile, suffix);
            tmpOutput.deleteOnExit();
            BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(tmpOutput), 1000);
            while ((byteRead = zin.read(data, 0, 1000)) != -1) {
                bout.write(data, 0, byteRead);
            }
            bout.flush();
            bout.close();
            return tmpOutput.getPath();
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }




    /*
    public void unzip(String fileName, String fileSuffix) {

        String password = ""; //may be used in future

        try {
            ZipFile zipFile = new ZipFile(fileName);
            if (zipFile.isEncrypted()) {
                zipFile.setPassword(password);
            }
            zipFile.extractAll(System.getProperty("java.io.tmpdir"));
        } catch (ZipException e) {
            e.printStackTrace();
        }
    }
    */

            /*
        String outputFile = "";
        byte[] buffer = new byte[1024];
        try {
           ZipInputStream zis = new ZipInputStream(new FileInputStream(fileName));
            outputFile = fileName.substring(0, fileName.length() - 4);
                //get the zip file content
            //get the zipped file list entry - there should only be one
            ZipEntry ze = zis.getNextEntry();

            //while (ze != null) {
                FileOutputStream fos = new FileOutputStream(outputFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer);
                }
                fos.close();
                //ze = zis.getNextEntry();
           // }
            zis.closeEntry();
            zis.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return outputFile;
       }
        */


    private static byte[] codes = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            codes[i] = -1;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            codes[i] = (byte) (i - 'A');
        }
        for (int i = 'a'; i <= 'z'; i++) {
            codes[i] = (byte) (26 + i - 'a');
        }
        for (int i = '0'; i <= '9'; i++) {
            codes[i] = (byte) (52 + i - '0');
        }
        codes['+'] = 62;
        codes['/'] = 63;
    }

    private String tempFileWithoutDecoding(final InputStream data, final String fileName) {
        try {
            File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4), fileName.substring(fileName.length() - 4));
            String tempFile = temp.getPath();
            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempFile);
            org.apache.commons.io.IOUtils.copy(data, fos);
            fos.close();
            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    private String decode64(final InputStream data, final String fileName) {

        String tempName = "";
        try {

            String fileSuffix = fileName.substring(fileName.length() - 4);

            File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4), fileSuffix);
            tempName = temp.getPath();

            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempName);
            byte b[] = new byte[1];
            int shift = 0;   // # of excess bits stored in accum
            int accum = 0;   // excess bits
            int count = data.read(b);
            while (count > 0) {
                if (codes[b[0]] >= 0) {
                    accum <<= 6;            // bits shift up by 6 each time thru
                    shift += 6;             // loop, with new bits being put in

                    accum |= codes[b[0]];         // at the bottom.
                    if (shift >= 8) {       // whenever there are 8 or more shifted in,
                        shift -= 8;         // write them out (from the top, leaving any
                        fos.write((accum >> shift) & 0xff);
                    }
                }
                count = data.read(b);
                if (count <= 0) {
                    if (shift > 0) {
                        //not sure here....
                        b[0] = ' ';
                    }
                }
            }


            //write it
            fos.close();

            System.out.println("Decode 64 Done");


        } catch (Exception e) {
            e.getStackTrace();
        }
        return tempName;
    }


    private String uploadReport(LoggedInConnection loggedInConnection,AzquoBook azquoBook, String fileName, String reportName) throws Exception{
        int businessId = loggedInConnection.getBusinessId();
        int databaseId = loggedInConnection.getAzquoMemoryDB().getDatabase().getId();
        OnlineReport or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
        int reportId = 0;
        if (or!=null){
            reportId = or.getId();
        }

        String fullPath = reportPath + adminService.getBusinessPrefix(loggedInConnection) + "/" + fileName;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();

         FileOutputStream out = new FileOutputStream(fullPath);
         azquoBook.saveBook(fullPath);
         out.close();
         or = new OnlineReport(reportId, businessId,databaseId ,"", reportName, "", fullPath,"");
         onlineReportDAO.store(or);
        return "";


    }






private String readBook (final LoggedInConnection loggedInConnection, final String fileName, final String tempName) {


        try {
            AzquoBook azquoBook = new AzquoBook(valueService, adminService, nameService, userChoiceDAO);
            azquoBook.loadBook(tempName);
            String reportName = azquoBook.getReportName();
            if (reportName!=null){
                return uploadReport(loggedInConnection, azquoBook, fileName, reportName);
            }

            int sheetNo = 0;

            while (sheetNo < azquoBook.getNumberOfSheets()) {
                String error = readSheet(loggedInConnection, azquoBook, tempName, sheetNo);
                if (error.startsWith("error:")){
                    return error;
                }
                sheetNo++;
            }

        } catch (Exception ioe) {
            ioe.printStackTrace();
        }
        return "";
    }



    private String readSheet(final LoggedInConnection loggedInConnection, AzquoBook azquoBook, final String tempFileName, final int sheetNo) throws Exception{


        String tempName = azquoBook.convertSheetToCSV(tempFileName, sheetNo);

         InputStream uploadFile = new FileInputStream(tempName);
        String fileType = tempName.substring(tempName.lastIndexOf(".") + 1);
        return readPreparedFile(loggedInConnection, uploadFile, fileType);

     }





}



