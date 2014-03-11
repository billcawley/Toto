package com.azquo.service;

import com.azquo.admindao.UploadRecordDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.UploadRecord;
import com.azquo.memorydb.Name;
import com.csvreader.CsvReader;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
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
        if (fileName.endsWith(".xls")) {
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

        if (fileType.toLowerCase().startsWith("values")) {
            if (fileType.length() > 7) {
                String language = fileType.substring(7).trim();
                String origLanguage = loggedInConnection.getLanguage();
                loggedInConnection.setLanguage(language);
                String result = valuesImport(loggedInConnection, uploadFile, create);
                loggedInConnection.setLanguage(origLanguage);
                return result;
            }
            return valuesImport(loggedInConnection, uploadFile, create);
        }
        // we will pay attention onn the attribute import and replicate
        if (fileType.toLowerCase().startsWith("names")) {
            return namesImport(loggedInConnection, uploadFile, create);

        }
        if (fileType.toLowerCase().startsWith("structure")) {
            return structureImport(loggedInConnection, uploadFile);

        }
        if (fileType.toLowerCase().startsWith("sets")) {
            return setsImport(loggedInConnection, uploadFile, create);

        }
        return "error: unknown file type " + fileType;

    }


    public String valuesImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {
        long track = System.currentTimeMillis();
        String strCreate = "";
        if (create) strCreate = ";create";

        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        // what we're doing here is going through the headers, First thing to do is to set up the peers if defined for a header
        // then we find or create each header as a name in the database. If the name has peers it's added to the nameimportheading map, a way to find the header for that name with peers
        // namesWithPeersHeaderMap is a map of the names which have peers, colums headed by such names will have the value in them, hence why we need to hold the header so we cna get the value
        final HashMap<Name, String> namesWithPeersHeaderMap = new HashMap<Name, String>();
        final HashMap<Name, Integer> headings = new HashMap<Name, Integer>();
        int col = 0;
        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                final String result = nameService.setPeersForImportHeading(loggedInConnection, header + strCreate);
                if (result.startsWith("error:")) {
                    throw new Exception("unable to understand " + header + " - " + result);
                }
                String nameToFind = header;
                if (header.contains(";")) nameToFind = header.substring(0, header.indexOf(";"));
                final Name name = nameService.findOrCreateName(loggedInConnection, nameToFind);
                if (name.getPeers().size() > 0) {
                    namesWithPeersHeaderMap.put(name, header);
                }
                headings.put(name, col);
                col++;
            }
        }

        if (namesWithPeersHeaderMap.keySet().isEmpty()) {
            throw new Exception("unable to find any name with peers");
        }


        int valuecount = 0; // purely for logging
        // little local cache just to speed things up
        final HashMap<String, Name> namesFound = new HashMap<String, Name>();
        // having read the headers go through each record
        while (csvReader.readRecord()) {
            String value;
            // for each record we want to find value name sets, most of the time there's only one but they are defined in namesWithPeersHeaderMap
            for (Name headerName : namesWithPeersHeaderMap.keySet()) {
                boolean hasRequiredPeers = true;
                final Set<Name> namesForValue = new HashSet<Name>(); // the names we're going to look for for this value
                namesForValue.add(headerName); // the one at the top of this column, the name with peers.
                for (Name peer : headerName.getPeers().keySet()) { // go looking for the peers
                    final String peerVal = csvReader.get(headings.get(peer));
                    if (peerVal == null || peerVal.length() == 0) { // the file specified
                        hasRequiredPeers = false;
                    } else {
                        //storeStructuredName(peer,peerVal, loggedInConnection);
                        // lower level names first so the syntax is something like Knightsbridge, London, UK
                        // hence we're passing a multi level name lookup to the name service, whatever is in that column with the header on the end
                        final String nameToFind = "\"" + peerVal + "\",\"" + peer.getDefaultDisplayName() + "\"";
                        // check the local cache first
                        Name nameFound = namesFound.get(nameToFind);
                        if (nameFound == null) {
                            if (create) {
                                nameFound = nameService.findOrCreateName(loggedInConnection, nameToFind);
                            } else {
                                nameFound = nameService.findByName(loggedInConnection, nameToFind);
                            }
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
                    value = csvReader.get(namesWithPeersHeaderMap.get(headerName));
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
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");

        nameService.persist(loggedInConnection);

        return "";
    }


    public String namesImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {
        // should we have encoding options?? Leave for the mo . . .
        final CsvReader csvReader = new CsvReader(uploadFile, '\t', Charset.forName("UTF-8"));
       //TODO WE NEED TO BE ABLE TO SET TextQualifier TO false

        csvReader.setUseTextQualifier(true);
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
                searchName = nameService.setPeersForImportHeading(loggedInConnection, searchName + strCreate);
                if (searchName.startsWith("error:")) return searchName;
                if (create) {
                    name = nameService.findOrCreateName(loggedInConnection, searchName);
                } else {
                    name = nameService.findByName(loggedInConnection, searchName);
                }
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
                            nameService.findOrCreateName(loggedInConnection, itemName + "," + category + " " + plural + "," + plural + " by " + headerName + "," + topName);
                            //and put the category in its set.
                            nameService.findOrCreateName(loggedInConnection, category + "," + headerName);
                        }
                    }
                }
            }
        }
        return "";
    }


    public String setsImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(uploadFile));
        String line;
        System.out.println("input");
        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            String setName = st.nextToken();
            //clear the set before re-instating
            Name set = nameService.findOrCreateName(loggedInConnection,setName);
            nameService.clearChildren(set);
            while (st.hasMoreTokens()) {
                if (create) {
                    nameService.findOrCreateName(loggedInConnection, st.nextToken() + "," + setName);
                } else {
                    nameService.findByName(loggedInConnection, st.nextToken() + "," + setName);
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
            Workbook wb = new HSSFWorkbook(fs);
             int sheetNo = 0;

            while (sheetNo < wb.getNumberOfSheets()) {
                Sheet sheet = wb.getSheetAt(sheetNo);
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


    private String readxlsx (final LoggedInConnection loggedInConnection, final String fileName, boolean create) {


        try {
            Workbook wb = new XSSFWorkbook(new FileInputStream(fileName));
            int sheetNo = 0;

            while (sheetNo < wb.getNumberOfSheets()) {
                Sheet sheet = wb.getSheetAt(sheetNo);
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


    private String readSheet(final LoggedInConnection loggedInConnection, final String fileName, final Sheet sheet, boolean create) throws Exception{
        Row row;
        Cell cell;
        DataFormatter formatter = new DataFormatter();

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
        readPreparedFile(loggedInConnection, uploadFile, fileType, create);

        return "";
    }





}



