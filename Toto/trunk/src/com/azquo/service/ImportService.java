package com.azquo.service;

import com.azquo.admindao.UploadRecordDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.UploadRecord;
import com.azquo.memorydb.Name;
import com.csvreader.CsvReader;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.xmlbeans.impl.xb.xsdschema.ImportDocument;
import org.springframework.beans.factory.annotation.Autowired;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * Created by bill on 13/12/13.
 * service to process files used to import data into the database
 */

public final class ImportService {

    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private UploadRecordDAO uploadRecordDAO;

    public final String IDENTIFIER = "key";
    public final String CHILDOF = "child of ";
    public final String PARENTOF = "parent of ";
    public final String STRUCTURE = "structure ";
    public final String ATTRIBUTE = "attribute ";
    public final String PLURAL = "plural ";
    public final String PEERS = "peers";


    class ImportHeading{
         String heading;
        Name name;
        Name structureName;
        String parentOf;
        String childOfString;
        int identityColumn;
        int childColumn;
        int structureColumn;
        Name childOf;
        String attribute;
        Set<Integer> peerColumns;
        String plural;
        Name topParent;
        boolean identifier;

        public ImportHeading(){
            heading = null;
            name = null;
            structureName = null;
            parentOf = null;
            childOfString = null;
            identityColumn = -1;
            childColumn = -1;
            structureColumn = -1;
            childOf = null;
            attribute = null;
            peerColumns = new HashSet<Integer>();
            plural = null;
            topParent = null;
            identifier = false;


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
            error = readxlsx(loggedInConnection, tempFile, create);

        }else if (fileName.contains(".xls")){
            error = readExcel(loggedInConnection, tempFile, create);

        } else {
            if (tempFile.length() > 0) {
                uploadFile = new FileInputStream(tempFile);
            }
            error = readPreparedFile(loggedInConnection, uploadFile, fileType, create);
        }
        nameService.persist(loggedInConnection);
        Database db = loggedInConnection.getAzquoMemoryDB().getDatabase();
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), db.getBusinessId(), db.getId(), loggedInConnection.getUser().getId(), fileName, fileType, error);
        uploadRecordDAO.store(uploadRecord);

        return error;
    }

    private String readPreparedFile(LoggedInConnection loggedInConnection, InputStream uploadFile, String fileType, boolean create) throws Exception {


        String origLanguage = loggedInConnection.getLanguage();
        String result = "";
                // we will pay attention onn the attribute import and replicate
        if (fileType.toLowerCase().startsWith("names")) {
            result=  namesImport(loggedInConnection, uploadFile, create);

        }else if (fileType.toLowerCase().startsWith("structure")) {
            result = structureImport(loggedInConnection, uploadFile);

        } else if (fileType.toLowerCase().startsWith("sets")) {
            result = setsImport(loggedInConnection, uploadFile, create);

        }else{
            result = valuesImport(loggedInConnection, uploadFile, create);
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
                        if (peerName.startsWith("\"")) {
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

    private int findColumn(String nameToFind, List<ImportHeading> headings){
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        for (int col = 0; col < headings.size();col++){
            ImportHeading heading = headings.get(col);
            if (heading.heading != null && heading.heading.equalsIgnoreCase(nameToFind) && heading.identifier){
                return col;
            }
        }
        for (int col = 0; col < headings.size();col++){
            ImportHeading heading = headings.get(col);
            if (heading.heading != null && heading.heading.equalsIgnoreCase(nameToFind) && (heading.attribute == null || heading.attribute.equals(Name.DEFAULT_DISPLAY_NAME))){
                return col;
            }
        }
        return -1;
    }

    public void findTopParent(LoggedInConnection loggedInConnection, ImportHeading heading, List<ImportHeading> headings) throws Exception{
        //need to work out the topparent for use when classifing names found in this column
        ImportHeading child = heading;
        if (heading.identityColumn >=0){
            child = headings.get(heading.identityColumn);
        }
        while (child.parentOf != null){
            child.childColumn = findColumn(child.parentOf, headings);
            child = headings.get(child.childColumn);
        }
        if (child.name == null){
            child.name = nameService.findOrCreateName(loggedInConnection,child.heading);
        }
        heading.topParent = child.name;

    }


    public String valuesImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {
        long track = System.currentTimeMillis();
       String strCreate = "";
        if (create) strCreate = ";create";

        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        csvReader.setUseTextQualifier(false);
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
                final String result = interpretHeading(loggedInConnection, header, heading);
                String nameToFind = header;
                headings.add(heading);
                col++;
            }else{
                headings.add(new ImportHeading());
            }
        }
        for (ImportHeading importHeading:headings){
            if (importHeading.heading != null){
                if (importHeading.name != null && importHeading.name.getPeers().size() > 0){
                    for (Name peer:importHeading.name.getPeers().keySet()){
                        int peerColumn = findColumn(peer.getDefaultDisplayName(), headings);
                        if (peerColumn< 0 ) {
                            return "error: cannot find peer " + peer.getDefaultDisplayName() + " for " + importHeading.name.getDefaultDisplayName();
                        }
                        ImportHeading importPeer = headings.get(peerColumn);
                        if (importPeer.name == null){
                            importPeer.name = nameService.findOrCreateName(loggedInConnection, importPeer.heading);
                        }
                        importHeading.peerColumns.add(peerColumn);
                    }
                }
                if (importHeading.attribute != null && !importHeading.attribute.equals(Name.DEFAULT_DISPLAY_NAME)){
                    importHeading.identityColumn = findColumn(importHeading.heading, headings);
                    if (importHeading.identityColumn < 0){
                        return "error: cannot find column " + importHeading.name.getDefaultDisplayName() + " for attribute " + importHeading.attribute;
                    }
                    //treat the default name in the row, if it exists, as an attribute
                    for (ImportHeading heading2:headings){
                        if (heading2.heading != null && heading2.heading.equals(importHeading.heading) && heading2.attribute == null){
                            heading2.attribute = Name.DEFAULT_DISPLAY_NAME;
                            heading2.identityColumn = importHeading.identityColumn;
                            break;
                        }

                    }
                    findTopParent(loggedInConnection, importHeading, headings);
                }
                if (importHeading.parentOf != null){
                    importHeading.childColumn = findColumn(importHeading.parentOf, headings);
                          if (importHeading.childColumn < 0){
                        return "error: cannot find column " + importHeading.parentOf + " for child of " + importHeading.name.getDefaultDisplayName();
                    }
                    findTopParent(loggedInConnection, importHeading, headings);
                  }
                if (importHeading.structureName != null){
                    importHeading.structureColumn = findColumn(importHeading.structureName.getDefaultDisplayName(), headings);
                    if (importHeading.structureColumn < 0){
                        return "error: cannot find column " + importHeading.structureName.getDefaultDisplayName() + " for structure " + importHeading.name.getDefaultDisplayName();
                    }
                }
                if (importHeading.childOfString != null){
                    if (importHeading.topParent == null){
                        importHeading.childOf = nameService.findOrCreateName(loggedInConnection, importHeading.childOfString);
                    }else{
                        if (!importHeading.topParent.getDefaultDisplayName().equals(importHeading.childOfString)){
                           importHeading.childOf = nameService.includeNameInSet(loggedInConnection, importHeading.childOfString, importHeading.topParent);
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
        while (csvReader.readRecord()) {
            String value;
            // for each record we want to find value name sets, most of the time there's only one but they are defined in namesWithPeersHeaderMap
            for (int column = 0; column < headings.size();column++) {
                ImportHeading heading = headings.get(column);
                if (heading.peerColumns.size() > 0){
                    boolean hasRequiredPeers = true;
                    final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                    namesForValue.add(heading.name); // the one at the top of this column, the name with peers.
                    for (int peerColumn : heading.peerColumns) { // go looking for the peers
                        ImportHeading peerHeading = headings.get(peerColumn);
                        final String peerVal = csvReader.get(peerColumn);
                        if (peerVal == null || peerVal.length() == 0) { // the file specified
                            hasRequiredPeers = false;
                        } else {
                            //storeStructuredName(peer,peerVal, loggedInConnection);
                            // lower level names first so the syntax is something like Knightsbridge, London, UK
                            // hence we're passing a multi level name lookup to the name service, whatever is in that column with the header on the end
                            // sometimes quotes are used in the middle of names to indicate inches - e.g. '4" pipe'      - store as '4inch pipe'
                            final String nameToFind = "\"" + peerVal + "\",\"" + headings.get(peerColumn).name.getDefaultDisplayName() + "\"";
                            // check the local cache first
                            Name nameFound = namesFound.get(nameToFind);
                            if (nameFound == null) {
                                String origLanguage = loggedInConnection.getLanguage();
                                if (peerHeading.attribute  != null){
                                    loggedInConnection.setLanguage(peerHeading.attribute);
                                }

                                nameFound = nameService.includeNameInSet(loggedInConnection, peerVal, headings.get(peerColumn).name);
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
                    if (hasRequiredPeers) {
                        // now we have the set of names for that name with peers get the value from that column it's a header for
                        value = csvReader.get(column);
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
                if (heading.structureName != null){
                    String itemName = csvReader.get(heading.structureColumn);
                    String category = csvReader.get(column);
                    if (category.trim().length() > 0){
                        if (heading.name == null){
                            heading.name = nameService.findOrCreateName(loggedInConnection,heading.heading);
                        }
                        ImportHeading structureName = headings.get(heading.structureColumn);
                        if (structureName.name == null){
                            structureName.name = nameService.findOrCreateName(loggedInConnection,structureName.heading);
                        }
                        String plural = structureName.plural;
                        if (plural == null){
                            plural = structureName.heading + "s";
                        }
                        //create the name and structure
                        Name byCategory = nameService.includeNameInSet(loggedInConnection,plural + " by " + heading.name.getDefaultDisplayName(), structureName.name);
                        Name thisSet = nameService.includeNameInSet(loggedInConnection,category + " " + plural,byCategory);
                        nameService.includeNameInSet(loggedInConnection, itemName,thisSet);
                          //and put the category in its set.
                        nameService.includeNameInSet(loggedInConnection, category, heading.name);
                    }
                }
                if (heading.identityColumn >= 0){
                    ImportHeading identity = headings.get(heading.identityColumn);
                     String itemName = csvReader.get(heading.identityColumn);
                    if (itemName.length() > 0){
                        String origLanguage = loggedInConnection.getLanguage();
                        if (identity.identityColumn >=0){
                            loggedInConnection.setLanguage(identity.attribute);
                        }
                        if (heading.topParent == null){
                            //may have escaped the checks...
                            findTopParent(loggedInConnection,heading,headings);
                        }

                        Name name = nameService.includeNameInSet(loggedInConnection,itemName,heading.topParent);
                        loggedInConnection.setLanguage(origLanguage);
                        name.setAttributeWillBePersisted(heading.attribute, csvReader.get(column));
                        nameService.calcReversePolish(loggedInConnection,name);

                    }
                }
                if (heading.parentOf !=null){
                    String childName = csvReader.get(heading.childColumn);
                    String parentName = csvReader.get(column);
                    if (parentName.length() > 0){
                        Name parentSet = null;
                        if (heading.childOf != null){
                            parentSet = nameService.includeNameInSet(loggedInConnection, parentName,heading.childOf);
                        }else{
                            parentSet = nameService.findOrCreateName(loggedInConnection,parentName);
                        }
                        Name name = nameService.includeNameInSet(loggedInConnection,childName,parentSet);
                    }
                }

                if (heading.childOf !=null){
                    String childName = csvReader.get(column);
                    if (childName.length() > 0){
                       Name name = nameService.includeNameInSet(loggedInConnection,childName,heading.childOf);
                    }
                }
            }
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");

        nameService.persist(loggedInConnection);

        return "";
    }


    public String namesImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {
        // should we have encoding options?? Leave for the mo . . .
        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
       //TODO WE NEED TO BE ABLE TO SET TextQualifier TO false

        csvReader.setUseTextQualifier(false);
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        String importLanguage = loggedInConnection.getLanguage();
        //if no name has been specified, then 'name' (case insensitive) should work
        String strCreate = "";
        if (create) strCreate = ";create";
        if (importLanguage.equals("DEFAULT_DISPLAY_NAME")) {
            for (String header : headers) {
                if (header.equalsIgnoreCase("name")) {
                    importLanguage = header;
                }
            }
        }
        while (csvReader.readRecord()) {
            // the language may have been adjusted by the import controller
            String searchName = csvReader.get(importLanguage);
            Name name = null;
            // so we try to find or create (depending on parameters) that name in the current language
            if (searchName != null && searchName.length() > 0) {
                ImportHeading heading = new ImportHeading();
                String error = interpretHeading(loggedInConnection,searchName,heading);
                heading.name = nameService.findOrCreateName(loggedInConnection,heading.heading);
                if (error.length() > 0){
                    return error;
                }
                name = heading.name;
            }
            // if we found or created that name we run through all the other names in the row setting as attributes against the name
            if (name != null) {
                for (String header : headers) {
                    if (header.length() > 0 && !header.equalsIgnoreCase(importLanguage)) { // ignore the column used for lookup and structure definitionn
                        String attName = header;
                        // name we see as equivalent to the display name. Could cause a mishap if language were passed as "name"
                        if (header.toLowerCase().equals("name")) {
                            attName = Name.DEFAULT_DISPLAY_NAME;
                        }
                        // we need to do this to theh attribute value if it was the one in
                        final String newAttributeValue = csvReader.get(header);
                        final String existingAttributeValue = name.getAttribute(attName); // existing
                        if ((existingAttributeValue == null && newAttributeValue.length() > 0)
                                || (existingAttributeValue != null && !newAttributeValue.equals(existingAttributeValue))) {
                            if (newAttributeValue.length() == 0) { // blank means we remove
                                name.removeAttributeWillBePersisted(attName);
                            } else {
                                String error = name.setAttributeWillBePersisted(attName, newAttributeValue);
                                if (error.length() > 0) return error;
                                //...in case this is a calculation
                                error = nameService.calcReversePolish(loggedInConnection,name);
                             }
                        }
                    }
                }

            }
        }
        return "";
    }

    // TODO : Edd understand what this does!

    private String structureImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile) throws Exception {
        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        String topName = headers[0];
        String plural = topName + "s";
        if (topName.endsWith(")")) {
            int openBracket = topName.lastIndexOf('(');
            plural = topName.substring(openBracket + 1, topName.length() - 1);
            topName = topName.substring(0, openBracket).trim();
        }
        Name topN = nameService.findOrCreateName(loggedInConnection, topName);
        while (csvReader.readRecord()) {
            String itemName = "";
            for (String headerName : headers) {
                if (headerName.length() > 0) {
                    String category = csvReader.get(headerName);
                    if (category.length() > 0) {
                        if (headerName.equals(headers[0])) {
                            itemName = category;
                        } else {
                            //create the name and structure
                            Name headerN = nameService.findOrCreateName(loggedInConnection,headerName);
                            Name byCategory = nameService.includeNameInSet(loggedInConnection,plural + " by " + headerName, topN);
                            Name thisSet = nameService.includeNameInSet(loggedInConnection,category + " " + plural,byCategory);
                            nameService.includeNameInSet(loggedInConnection, itemName,thisSet);
                            //and put the category in its set.
                            nameService.includeNameInSet(loggedInConnection, category,headerN);
                         }
                    }
                }
            }
        }
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

    public String setsImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String line;
        System.out.println("input");
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            //clear the set before re-instating
            ImportHeading importHeading = new ImportHeading();
            String error = interpretHeading(loggedInConnection,st.nextToken(), importHeading);
            importHeading.name = nameService.findOrCreateName(loggedInConnection, importHeading.heading);
            if (error.length() > 0){
                return error;
            }
            Name set = importHeading.name;
            nameService.clearChildren(set);
            while (st.hasMoreTokens()) {
               String element = st.nextToken();
               if (element.length() > 0 ){
                   //the Excel formatter seems to get some dates wrong.  check for this
                   //if (element.indexOf("/") > 0 && element.length() < 10){
                  //     element = findExistingDate(loggedInConnection, element);
                   //   }
                   nameService.includeNameInSet(loggedInConnection, element, set);
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

    private String readExcel(final LoggedInConnection loggedInConnection, final String fileName, boolean create) {


        try {
            POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(fileName));
            HSSFWorkbook wb = new HSSFWorkbook(fs);
            HSSFRow row;
            HSSFCell cell;
            HSSFDataFormatter formatter = new HSSFDataFormatter();
            int sheetNo = 0;

            while (sheetNo < wb.getNumberOfSheets()) {
                HSSFSheet sheet = wb.getSheetAt(sheetNo);
                String fileType = sheet.getSheetName();

                int rows; // No of rows
                rows = sheet.getPhysicalNumberOfRows();

                int cols = 0; // No of columns
                int tmp;

                // This trick ensures that we get the data properly even if it doesn't start from first few rows
                for (int i = 0; i < 10 || i < rows; i++) {
                    row = sheet.getRow(i);
                    if (row != null) {
                        tmp = sheet.getRow(i).getPhysicalNumberOfCells();
                        if (tmp > cols) cols = tmp;
                    }
                }
                File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4), "." + fileType);
                String tempName = temp.getPath();

                temp.deleteOnExit();
                FileWriter fw = new FileWriter(tempName);
                BufferedWriter bw = new BufferedWriter(fw);

                for (int r = 0; r < rows; r++) {
                    row = sheet.getRow(r);
                    if (row != null) {
                        //System.out.println("Excel row " + r);
                        int colCount = 0;
                        for (int c = 0; c < cols; c++) {
                            // this was cast to a short, why??
                            cell = row.getCell(c);
                            if (colCount++ > 0) bw.write('\t');
                            if (cell != null) {

                                String cellFormat = formatter.formatCellValue(cell);
                                //Integers seem to have '.0' appended, so this is a manual chop.  It might cause problems if someone wanted to import a version '1.0'
                                if (NumberUtils.isNumber(cellFormat) && cellFormat.endsWith(".0")) {
                                    cellFormat = cellFormat.substring(0, cellFormat.length() - 2);
                                }

                                bw.write(cellFormat);
                            }
                        }
                        bw.write('\n');
                    }
                }
                bw.close();
                InputStream uploadFile = new FileInputStream(tempName);
                String error = readPreparedFile(loggedInConnection, uploadFile, fileType, create);
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
private String readxlsx (final LoggedInConnection loggedInConnection, final String fileName, boolean create) {


        try {
            XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(fileName));
            int sheetNo = 0;

            while (sheetNo < wb.getNumberOfSheets()) {
                XSSFSheet sheet = wb.getSheetAt(sheetNo);
                String error = readSheet(loggedInConnection, fileName, sheet, create);
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



    private String readSheet(final LoggedInConnection loggedInConnection, final String fileName, final XSSFSheet sheet, boolean create) throws Exception{
        XSSFRow row;
        XSSFCell cell;
        HSSFDataFormatter formatter = new HSSFDataFormatter();

        String fileType = sheet.getSheetName();

        int rows; // No of rows
        rows = sheet.getPhysicalNumberOfRows();

        int cols = 0; // No of columns
        int tmp;

        // This trick ensures that we get the data properly even if it doesn't start from first few rows
        for (int i = 0; i < 10 || i < rows; i++) {
            row = sheet.getRow(i);
            if (row != null) {
                tmp = sheet.getRow(i).getPhysicalNumberOfCells();
                if (tmp > cols) cols = tmp;
            }
        }
        File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4), "." + fileType);
        String tempName = temp.getPath();

        temp.deleteOnExit();
        FileWriter fw = new FileWriter(tempName);
        BufferedWriter bw = new BufferedWriter(fw);

        for (int r = 0; r < rows; r++) {
            row = sheet.getRow(r);
            if (row != null) {
                //System.out.println("Excel row " + r);
                int colCount = 0;
                for (int c = 0; c < cols; c++) {
                    // this was cast to a short, why??
                    cell = row.getCell(c);
                    if (colCount++ > 0) bw.write('\t');
                    if (cell != null) {

                        String cellFormat = formatter.formatCellValue(cell);
                        //Integers seem to have '.0' appended, so this is a manual chop.  It might cause problems if someone wanted to import a version '1.0'
                        if (NumberUtils.isNumber(cellFormat) && cellFormat.endsWith(".0")) {
                            cellFormat = cellFormat.substring(0, cellFormat.length() - 2);
                        }

                        bw.write(cellFormat.replace("\"","''"));// remove double quotes and replace with two single quotes
                    }
                }
                bw.write('\n');
            }
        }
        bw.close();
        InputStream uploadFile = new FileInputStream(tempName);
        return readPreparedFile(loggedInConnection, uploadFile, fileType, create);

     }





}



