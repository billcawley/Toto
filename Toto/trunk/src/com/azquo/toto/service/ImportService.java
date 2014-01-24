package com.azquo.toto.service;

import com.azquo.toto.admindao.UploadRecordDAO;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.adminentities.UploadRecord;
import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import com.csvreader.CsvReader;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
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


    public String importTheFile(final LoggedInConnection loggedInConnection, String fileName, InputStream uploadFile, String fileType, String separator, final String strCreate)
            throws Exception{

        if (separator == null || separator.length() == 0) separator = "\t";
        if (separator.equals("comma")) separator = ",";
        // separator not used??
        if (separator.equals("pipe")) separator = "|";
        String result = "";
        String tempFile = "";
        boolean create = false;
        if (strCreate != null && strCreate.equals("true")){
            create = true;
        }
        if (fileName.endsWith(".xls")){
            tempFile = decode64(uploadFile,fileName);
        }
        if (fileName.endsWith(".zip")) {
            tempFile = decode64(uploadFile, fileName);
            fileName = fileName.substring(0, fileName.length() - 4);

            tempFile = unzip(tempFile, fileName.substring(fileName.length() - 4));


        }
         if (fileName.contains(".xls")) {
            readExcel(loggedInConnection, tempFile, create);

        }else{
             if (tempFile.length() > 0){
                 uploadFile = new FileInputStream(tempFile);
             }
            readPreparedFile(loggedInConnection, uploadFile, fileType, create);
        }
         nameService.persist(loggedInConnection);
        Database db = loggedInConnection.getTotoMemoryDB().getDatabase();
        UploadRecord uploadRecord = new UploadRecord(0,new Date(),db.getBusinessId(), db.getId(), loggedInConnection.getUser().getId(),fileName, fileType, result);
        uploadRecordDAO.store(uploadRecord);

        return result;
    }

    private String readPreparedFile(LoggedInConnection loggedInConnection, InputStream uploadFile, String fileType, boolean create) throws Exception{

        if (fileType.toLowerCase().equals("values")){
            return dataImport(loggedInConnection,  uploadFile, create);
        }
        // we will pay attention onn the attribute import and replicate
        if (fileType.toLowerCase().equals("names")){
            return attributeImport(loggedInConnection,uploadFile, create);

        }
        return "error: unknown file type " + fileType;

    }




    public String dataImport(final LoggedInConnection loggedInConnection, final  InputStream uploadFile, final boolean create) throws Exception {
        // OK I think I'm supposed to use language in here but how??? Will go to default name for the moment
        final HashMap<Name, String> nameImportHeadingMap = new HashMap<Name, String>();
        //String filePath = "/home/bill/Downloads/exportcodes.csv";
        //TODO  set correct filepath
       InputStream is = null;

        long track = System.currentTimeMillis();
        String strCreate = "";
        if (create) strCreate = ";create";

         final CsvReader csvReader = new CsvReader(uploadFile,'\t',  Charset.forName("UTF-8"));
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        // are the following few lines necessary??
        final ArrayList<Name> headerNames = new ArrayList<Name>();

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
                    nameImportHeadingMap.put(name, header);
                    headerNames.add(name);
                }
            }
        }

        if (headerNames.size() == 0) {
            throw new Exception("unable to find any name with peers");
        }


        final Provenance provenance = loggedInConnection.getProvenance();
        int valuecount = 0;
        final HashMap<String, Name> namesFound = new HashMap<String, Name>();
        while (csvReader.readRecord()) {
            String value;
            for (Name headerName : headerNames) {
                final Set<Name> namesForValue = new HashSet<Name>();
                namesForValue.add(headerName);
                for (Name peer : headerName.getPeers().keySet()) {
                    final String peerVal = csvReader.get(peer.getDefaultDisplayName());
                    if (peerVal == null || peerVal.length() == 0) {
                        //throw new Exception("unable to find " + peer.getName() + " for " + headerName.getName());
                    } else {
                        //storeStructuredName(peer,peerVal, loggedInConnection);
                        final String nameToFind = peerVal + "," + peer.getDefaultDisplayName();
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
                        if (nameFound != null) {
                            namesForValue.add(nameFound);
                        }
                    }

                    //namesForValue.add(nameService.findOrCreateName(loggedInConnection,peerVal + "," + peer.getName())) ;

                }
                value = csvReader.get(nameImportHeadingMap.get(headerName));
                if (value.trim().length() > 0) { // no point storing if there's no value!
                    valuecount++;
                    valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, provenance, namesForValue);
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


    public String attributeImport(final LoggedInConnection loggedInConnection, final InputStream uploadFile, final boolean create) throws Exception {

        //String filePath = "/home/bill/Downloads/exportcodes.csv";
         final CsvReader csvReader = new CsvReader(uploadFile,'\t',  Charset.forName("UTF-8"));
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();


        while (csvReader.readRecord()) {
            final String searchName = csvReader.get(loggedInConnection.getLanguage());
            Name name = null;

            // ok this is going to search for things in the language column with the default name . . doesn't really make sense!

            if (searchName != null) {
                if (create) {
                    name = nameService.findOrCreateName(loggedInConnection, searchName);
                } else {
                    name = nameService.findByName(loggedInConnection, searchName);
                }
            }
            if (name != null) {
                for (String header : headers) {
                    if (header.length() > 0) {
                        String attName = header;
                        if (header.toLowerCase().equals("name")){
                            attName = name.DEFAULT_DISPLAY_NAME;
                        }
                        final String newName = getFirstName(csvReader.get(header));
                        final String oldName = name.getAttribute(attName);
                        if ((oldName == null && newName.length() > 0) || (oldName != null && !newName.equals(oldName))){
                            if (newName.length()==0){
                                name.removeAttributeWillBePersisted(attName);
                            }else{
                                name.setAttributeWillBePersisted(attName, getFirstName(newName));
                            }
                        }
                    }
                }

            }
        }
        return "";
    }



    private String getFirstName(final String nameGiven) {
        if (nameGiven == null) return null;
        int commaPos = nameGiven.indexOf(",");
        if (commaPos < 0) return nameGiven;
        int quotePos = nameGiven.indexOf("`");
        if (quotePos < commaPos && quotePos >= 0) {
            int endQuote = nameGiven.indexOf("`", quotePos + 1);
            return nameGiven.substring(quotePos + 1, endQuote);
        }
        return nameGiven.substring(0, commaPos);
    }


    private String unzip(String fileName, String suffix){

        String outputFile = fileName.substring(0, fileName.length() - 4);
        try {
            byte[] data = new byte[1000];
            int byteRead;


             ZipInputStream  zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileName)));
           // while((zin.getNextEntry()) != null){
           //READ ONE ENTRY ONLY...
                zin.getNextEntry();
                data = new byte[1000];
                File tmpOutput = File.createTempFile(outputFile,suffix);
                tmpOutput.deleteOnExit();
                BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(tmpOutput),1000);
                while ((byteRead = zin.read(data,0,1000)) != -1)
                {
                    bout.write(data,0,byteRead);
                }
                bout.flush();
                bout.close();
                return tmpOutput.getPath();
           // }
        }
        catch(Exception e)
        {
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



    private static String decode64(final InputStream data, final String fileName) {

         String tempName = "";
         try{

            String fileSuffix = fileName.substring(fileName.length() - 4);

            File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4), fileSuffix);
            tempName = temp.getPath();

            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempName);
            byte b[] = new byte[1];
             int shift = 0;   // # of excess bits stored in accum
             int accum = 0;   // excess bits
             int count = data.read(b);
             while (count > 0 ){
                 if (codes[b[0]] >= 0){
                     accum <<= 6;            // bits shift up by 6 each time thru
                    shift += 6;             // loop, with new bits being put in

                    accum |= codes[b[0]];         // at the bottom.
                    if (shift >= 8) {       // whenever there are 8 or more shifted in,
                        shift -= 8;         // write them out (from the top, leaving any
                        fos.write((accum >> shift) & 0xff);
                    }
                }
                count = data.read(b);
                if (count<= 0){
                    if (shift > 0){
                        //not sure here....
                        b[0] = ' ';
                    }
                }
            }


            //write it
            fos.close();

            System.out.println("Done");


        }catch (Exception e){
            System.out.println(e.getStackTrace());
         }
        return tempName;
     }

    private void readExcel(final LoggedInConnection loggedInConnection,final String fileName, boolean create){


        try {
            POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(fileName));
            HSSFWorkbook wb = new HSSFWorkbook(fs);
            HSSFRow row;
            HSSFCell cell;
            HSSFDataFormatter formatter = new HSSFDataFormatter();
            int sheetNo = 0;

            HSSFSheet sheet = wb.getSheetAt(0);
            while (sheet != null){
                String fileType = sheet.getSheetName();

                int rows; // No of rows
                rows = sheet.getPhysicalNumberOfRows();

                int cols = 0; // No of columns
                int tmp = 0;

                // This trick ensures that we get the data properly even if it doesn't start from first few rows
                for(int i = 0; i < 10 || i < rows; i++) {
                    row = sheet.getRow(i);
                    if(row != null) {
                        tmp = sheet.getRow(i).getPhysicalNumberOfCells();
                        if(tmp > cols) cols = tmp;
                    }
                }
                File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4), "." + fileType);
                String tempName = temp.getPath();

                temp.deleteOnExit();
                FileWriter fw = new FileWriter(tempName);
                BufferedWriter bw = new BufferedWriter(fw);

                for(int r = 0; r < rows; r++) {
                    row = sheet.getRow(r);
                    if(row != null) {
                        int colCount = 0;
                        for(int c = 0; c < cols; c++) {
                            cell = row.getCell((short)c);
                            if (colCount++ > 0) bw.write('\t');
                            if(cell != null) {
                                bw.write(formatter.formatCellValue(cell));
                                // Your code here
                            }
                        }
                        bw.write('\n');
                    }
                }
                bw.close();
                InputStream uploadFile = new FileInputStream(tempName);
                readPreparedFile(loggedInConnection,uploadFile,fileType,create);
                sheet = wb.getSheetAt(++sheetNo);
            }
        } catch(Exception ioe) {
            ioe.printStackTrace();
        }

    }


}



