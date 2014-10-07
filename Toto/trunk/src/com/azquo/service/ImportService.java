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

    private class NameParent{
        String name;
        Name parent;
        
        public NameParent(String name, Name parent){
            this.name= name;
            this.parent = parent;
        }
    }

    private class LineResult{
        String error;
        int valueCount;

        LineResult(){
            error="";
            valueCount = 0;
        }
    }

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
    public final String ATTRIBUTE = "attribute ";
    public final String LANGUAGE = "language ";
    public final String PLURAL = "plural ";
    public final String PEERS = "peers";
    public final String LOCAL = "local";
    public final String EQUALS = "equals";
    public final String COMPOSITION = "composition";


    class ImportHeading{
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
        Name topParent;
        boolean identifier;
        boolean contextItem;
        boolean local;
        String composition;
        String equalsString;
        String lineValue;

        public ImportHeading(){
            column=-1;
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
            topParent = null;
            identifier = false;
            contextItem = false;
            local = false;
            composition = null;
            equalsString = null;
            lineValue = "";


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
            result = valuesImport(loggedInConnection, uploadFile, fileType);
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
         }
       if (readClause(ATTRIBUTE, clause)!=null){
           heading.attribute = readClause(ATTRIBUTE, clause);

           if (heading.attribute.length() == 0){
               return "error: " + clause + " not understood";
           }
           if (heading.attribute.equalsIgnoreCase("name")){
               heading.attribute = Name.DEFAULT_DISPLAY_NAME;
           }
       }
       if (readClause(LOCAL, clause) !=null){
            heading.local = true;
        }

        if (readClause(PLURAL, clause) !=null){
            heading.plural= readClause(PLURAL, clause);
            if (heading.plural.length() == 0){
                return "error: " + clause + " not understood";
            }

        }
        if (readClause(EQUALS, clause) !=null){
            heading.equalsString = readClause(EQUALS, clause);

        }
        if (readClause(COMPOSITION, clause) !=null){
            heading.composition= readClause(COMPOSITION, clause);

            if (heading.composition.length() == 0){
                return "error: " + clause + " not understood";
            }

        }


        if (readClause(PEERS,clause)!=null){
            // TODO : address what happens if peer criteria intersect down the hierarchy, that is to say a child either directly or indirectly or two parent names with peer lists, I think this should not be allowed!
            heading.name = nameService.findOrCreateNameInParent(loggedInConnection, heading.heading, null, false);
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
                        Name peer = nameService.findOrCreateNameInParent(loggedInConnection,peerName, null, false);
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
            String error = interpretClause(loggedInConnection, heading, clauses.nextToken().trim());
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
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().indexOf(nameToFind.toLowerCase() + ",") == 0) && (heading.identifier || heading.attribute == null || heading.equalsString != null) ) {
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
                heading.name = nameService.findOrCreateNameInParent(loggedInConnection, heading.heading, null, false);//may need to create it

                return headingNo;
            }
        }
        return headingFound;
    }






    public String findTopParent(LoggedInConnection loggedInConnection, ImportHeading heading, List<ImportHeading> headings) throws Exception{
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
            identity.name = nameService.findOrCreateNameInParent(loggedInConnection,identity.heading, null, false);
        }
        heading.topParent = identity.name;//if no other parent found, this is the top parent.
        if (identity.childOf != null){
            heading.topParent = identity.childOf.findTopParent();
        }else {
            while (identity.parentOf != null){
                identity.childHeading = findHeading(identity.parentOf, headings);
                if (identity.childHeading < 0 ){
                    return "error: cannot find " + identity.parentOf;
                }
                identity = headings.get(identity.childHeading);
            }
            if (identity.name.getParents().size() > 0) {
                heading.topParent = identity.name.findTopParent();
            }
        }
        return "";
    }

    public Name includeInSet(LoggedInConnection loggedInConnection, Map<NameParent, Name> namesFound, String name, Name parent, boolean local) throws Exception{
        
        
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent
        NameParent np = new NameParent(name, parent);
        Name found = namesFound.get(np);
        if (found != null) {
            return found;
        }
        found = nameService.findOrCreateNameStructure(loggedInConnection, name, parent, local);
        namesFound.put(np, found);
        return found;
        
        
    }
 
    public String valuesImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, String fileType) throws Exception {

          // little local cache just to speed things up
        final HashMap<NameParent, Name> namesFound = new HashMap<NameParent, Name>();
        if (fileType.indexOf(" ") > 0){
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
        final HashMap<Name, String> namesWithPeersHeaderMap = new HashMap<Name, String>();
        final List<ImportHeading> headings = new ArrayList<ImportHeading>();

        String error = readHeaders(loggedInConnection, headers, headings, fileType);
        if (error.length() > 0) return error;

        error = fillInHeaderInformation(loggedInConnection, headings);
        if (error.length() > 0) return error;

        int valuecount = 0; // purely for logging
        int lastReported = 0;
        // having read the headers go through each record
          int lineNo = 0;
        while (csvReader.readRecord()) {
            lineNo++;
            ImportHeading contextPeersItem = null;
            if (csvReader.get(0).length()==0)break;//break if the first line element is blank
            for (ImportHeading heading:headings){
                heading.lineValue = csvReader.get(heading.column);
            }
            getCompositeValues(headings);
            LineResult result = interpretLine(loggedInConnection,headings,namesFound);
            if (result.error.length()> 0) return result.error;
            valuecount += result.valueCount;
            if (valuecount - lastReported > 5000){
                System.out.println("imported value count " + valuecount);
                lastReported = valuecount;
            }

         }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms for " + lineNo + " lines");

        nameService.persist(loggedInConnection);

        return "";
    }


    private String readHeaders(LoggedInConnection loggedInConnection, String[] headers, List<ImportHeading> headings, String fileType) throws Exception{

        int col = 0;
        String error = "";
        //if the file is of type (e.g.) 'sales' and there is a name 'import sales', thisis uses as an interpreter.  It need not interpret every column heading, but
        // any attribute of the same name as a column heading will be used.
        Name importInterpreter = nameService.findByName(loggedInConnection, "import " + fileType);
        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                ImportHeading heading = new ImportHeading();
                String head = null;
                if (importInterpreter != null){
                    head = importInterpreter.getAttribute(header);
                }
                if (head == null){
                    head=header;
                }
                head = head.replace(".",";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                int dividerPos = head.lastIndexOf(headingDivider);
                while (dividerPos > 0) {
                    ImportHeading contextHeading = new ImportHeading();
                    error = interpretHeading(loggedInConnection, head.substring(dividerPos + 1), contextHeading);
                    if (error.length()> 0) return error;
                    contextHeading.column = col;
                    contextHeading.contextItem = true;
                    headings.add(contextHeading);
                    head = head.substring(0, dividerPos);
                    dividerPos = head.lastIndexOf(headingDivider);


                }
                heading.column = col;

                error = interpretHeading(loggedInConnection, head, heading);
                if (error.length() > 0) return error;
                headings.add(heading);
            } else {
                headings.add(new ImportHeading());
            }
            col++;
        }
        return error;
    }


    private LineResult interpretLine(LoggedInConnection loggedInConnection, List<ImportHeading> headings, HashMap<NameParent, Name> namesFound)throws Exception{
        LineResult result = new LineResult();
        Map<Name,Name> contextNames = new HashMap<Name,Name>();
        String value = null;
        ImportHeading contextPeersItem = null;
        for (ImportHeading heading:headings) {
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
                                String peerValue = headings.get(colFound).lineValue;
                                possiblePeer = includeInSet(loggedInConnection,namesFound,peerValue, peer, heading.local);
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
                            value = heading.lineValue;
                        } else {
                            value = "";
                        }
                        if (value.trim().length() > 0) { // no point storing if there's no value!
                            result.valueCount++;
                            // finally store our value and names for it
                            valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, namesForValue);

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
                    final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value

                    boolean hasRequiredPeers = findPeers(loggedInConnection,namesFound, heading, headings, namesForValue);
                    if (hasRequiredPeers) {
                        // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                        value = heading.lineValue;
                    } else {
                        value = "";
                    }
                    if (value.trim().length() > 0) { // no point storing if there's no value!
                        result.valueCount++;
                        // finally store our value and names for it
                        valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, namesForValue);
                     }
                }
                if (heading.identityHeading >= 0) {
                    result.error = handleAttribute(loggedInConnection, namesFound, heading, headings);
                    if (result.error.length() > 0) return result;
                }
                if (heading.parentOf != null) {
                    handleParent(loggedInConnection, namesFound, heading, headings);
                }

                if (heading.childOf != null) {
                    String childName = heading.lineValue;
                    if (childName.length() > 0) {
                        Name name = includeInSet(loggedInConnection,namesFound, childName, heading.childOf, heading.local);
                    }
                }


            }
        }



        return result;
    }


    private String fillInHeaderInformation(LoggedInConnection loggedInConnection, List<ImportHeading> headings)throws Exception{

        String error = "";
        for (ImportHeading importHeading:headings) {
            if (importHeading.heading != null) {
                if (importHeading.name != null && importHeading.name.getPeers().size() > 0 && !importHeading.contextItem) {
                    for (Name peer : importHeading.name.getPeers().keySet()) {
                        //three possibilities to find the peer:
                        int peerHeading = findHeading(peer.getDefaultDisplayName(), headings);
                        if (peerHeading == -1) {
                            peerHeading = findContextHeading(peer, headings);
                            if (peerHeading == -1) {
                                peerHeading = findLowerLevelHeading(loggedInConnection, peer.getDefaultDisplayName(), headings);
                                if (peerHeading == -1) {
                                    return "error: cannot find peer " + peer.getDefaultDisplayName() + " for " + importHeading.name.getDefaultDisplayName();
                                }

                            }
                        }
                        if (peerHeading >= 0) {
                            ImportHeading importPeer = headings.get(peerHeading);
                            if (importPeer.name == null) {
                                importPeer.name = nameService.findOrCreateNameInParent(loggedInConnection, importPeer.heading, null, false);
                            }
                        }
                        importHeading.peerHeadings.add(peerHeading);
                    }
                }
                if (importHeading.attribute != null && !importHeading.attribute.equals(Name.DEFAULT_DISPLAY_NAME)) {
                    //first remove the parent
                    if (importHeading.equalsString != null) {
                        importHeading.name = nameService.findOrCreateNameInParent(loggedInConnection, importHeading.equalsString, null, false);//global name
                    } else {
                        importHeading.name = nameService.findOrCreateNameInParent(loggedInConnection, importHeading.heading, null, false);
                    }
                    // not sure why this line below is in the code.  If it is to remove the commas then 'findHeading' caters for this.  Removed to cater for 'equals'
                    //importHeading.heading = importHeading.name.getDefaultDisplayName();
                    importHeading.identityHeading = findHeading(importHeading.name.getDefaultDisplayName(), headings);
                    if (importHeading.identityHeading >= 0) {
                        headings.get(importHeading.identityHeading).identifier = true;//actively set the identifier as setting the attribute below might confuse the issue
                        for (ImportHeading heading2 : headings) {
                            //this is for the cases where the default display name is not the identifier.
                            if (heading2.heading != null && heading2.heading.equals(importHeading.heading) && heading2.attribute == null) {
                                heading2.attribute = Name.DEFAULT_DISPLAY_NAME;
                                heading2.identityHeading = importHeading.identityHeading;
                                break;
                            }
                        }

                    }
                }
                if (importHeading.childOfString != null) {
                    importHeading.childOf = nameService.findOrCreateNameInParent(loggedInConnection, importHeading.childOfString, null, false);
                }
                if (importHeading.parentOf != null) {
                    importHeading.childHeading = findHeading(importHeading.parentOf, headings);
                    if (importHeading.childHeading < 0) {
                        return "error: cannot find column " + importHeading.parentOf + " for child of " + importHeading.heading;
                    }
                    error = findTopParent(loggedInConnection, importHeading, headings);
                    if (error.length() > 0) return error;
                }
            }
        }
        //attribute topparents must be found after the identity heading topparent is found
        for (ImportHeading importHeading:headings) {
            if (importHeading.heading != null) {
                if (importHeading.attribute != null) {
                    findTopParent(loggedInConnection, importHeading, headings);

                }
            }
        }

        return error;



    }


    private String handleParent(LoggedInConnection loggedInConnection, HashMap<NameParent, Name> namesFound, ImportHeading heading, List<ImportHeading> headings)throws  Exception{

        ImportHeading childHeading = headings.get(heading.childHeading);
        String childName = childHeading.lineValue;
        String parentName = heading.lineValue;
        if (parentName.length() > 0) {
            Name parentSet = null;
            parentSet = includeInSet(loggedInConnection,namesFound, parentName, heading.childOf, heading.local);
            String origLanguage = loggedInConnection.getLanguage();
            if (childHeading.attribute != null) {
                loggedInConnection.setLanguage(childHeading.attribute);
            }
            //the child name is not local to the parent set, so find it in the class first
            Name name = includeInSet(loggedInConnection,namesFound, childName, childHeading.topParent, false);

            for (Name parent:parentSet.findAllParents()){
                if (parent.getChildren().contains(name)){//remove any direct links that can be assumed through the new link
                    parent.removeFromChildrenWillBePersisted(name);
                }
            }
            parentSet.addChildWillBePersisted(name);

            loggedInConnection.setLanguage(origLanguage);
        }

        return "";


    }


    public String handleAttribute(LoggedInConnection loggedInConnection, HashMap<NameParent,Name> namesFound, ImportHeading heading, List<ImportHeading> headings)throws Exception{

        ImportHeading identity = headings.get(heading.identityHeading);
        String itemName = headings.get(heading.identityHeading).lineValue;
        if (itemName.length() > 0) {
            String origLanguage = loggedInConnection.getLanguage();
            if (identity.identityHeading >= 0) {
                String attribute = identity.attribute;
                if (attribute == null){
                    attribute = Name.DEFAULT_DISPLAY_NAME;
                }
                loggedInConnection.setLanguage(attribute);
            }
            if (heading.topParent == null) {
                //may have escaped the checks...
                String error = findTopParent(loggedInConnection, identity, headings);
                if (error.length() > 0) return error;
            }

            Name name = includeInSet(loggedInConnection,namesFound, itemName, heading.topParent, heading.local);
            loggedInConnection.setLanguage(origLanguage);
            String attribute = heading.attribute;
            if (attribute==null){
                attribute= Name.DEFAULT_DISPLAY_NAME;
            }
            String attValue = heading.lineValue;
            name.setAttributeWillBePersisted(attribute, attValue);
            nameService.calcReversePolish(loggedInConnection, name);
        }
        return "";
    }

    private boolean findPeers(LoggedInConnection loggedInConnection, HashMap<NameParent, Name> namesFound, ImportHeading heading, List<ImportHeading> headings, Set<Name> namesForValue)throws  Exception{

        ImportHeading headingWithPeers = heading;
        boolean hasRequiredPeers = true;
        namesForValue.add(heading.name); // the one at the top of this headingNo, the name with peers.
        for (int peerHeadingNo : headingWithPeers.peerHeadings) { // go looking for the peers
            ImportHeading peerHeading = headings.get(peerHeadingNo);
            if (peerHeading.contextItem) {
                namesForValue.add(peerHeading.name);
            } else {
                final String peerVal = peerHeading.lineValue;
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

                        nameFound = includeInSet(loggedInConnection,namesFound, peerVal, headings.get(peerHeadingNo).name, heading.local);
                        loggedInConnection.setLanguage(origLanguage);
                    }
                    // add to the set of names we're going to store against this value
                    if (nameFound != null) {
                        namesForValue.add(nameFound);
                    }
                }
                //namesForValue.add(nameService.findOrCreateName(loggedInConnection,peerVal + "," + peer.getName())) ;
            }
        }
        return hasRequiredPeers;
    }


    private void getCompositeValues(List<ImportHeading> headings){

        int adjusted = 2;
        //loops in case there are multiple levels of dependencies
        while (adjusted > 1){
            adjusted = 0;
            for (ImportHeading heading:headings){
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




    public String setsImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile) throws Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String line;
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            ImportHeading importHeading = new ImportHeading();
            if (st.hasMoreTokens()) {
                String setName = st.nextToken();
                if (setName.length() > 0) {
                    String error = interpretHeading(loggedInConnection, setName, importHeading);
                    importHeading.name = nameService.findOrCreateNameInParent(loggedInConnection, importHeading.heading, null, false);
                    if (error.length() > 0) {
                        return error;
                    }
                    Name set = importHeading.name;
                    nameService.clearChildren(set);
                    while (st.hasMoreTokens()) {
                        String element = st.nextToken();
                        if (element.length() > 0) {
                             nameService.findOrCreateNameInParent(loggedInConnection, element, set, false);//this import currently not importing local names
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



