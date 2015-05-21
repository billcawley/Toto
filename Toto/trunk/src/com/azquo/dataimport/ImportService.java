package com.azquo.dataimport;

import com.azquo.admin.AdminService;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.AzquoBook;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * Created by bill on 13/12/13.
 * spreadsheet to process files used to dataimport data into the database
 * <p/>
 * <p/>
 * edd : I don't really understand all this code but for the moment I'm not that concerned by that.
 */

public final class ImportService {



    //private static final String reportPath = "/home/bill/apache-tomcat-7.0.47/dataimport/";
//    public static final String homePath = "/home/cawley/";
    public static final String dbPath = "/databases/";

    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private AdminService adminService;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private RMIClient rmiClient;

    public void importTheFile(LoggedInUser loggedInUser, String fileName, String filePath) throws Exception {
        List<String> languages = new ArrayList<String>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        importTheFile(loggedInUser, fileName, filePath, "", true, languages);
    }


    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public void importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, String fileType, boolean skipBase64, List<String> attributeNames)
            throws Exception {

        //fileType is now always the first word of the spreadsheet/dataimport file name
        InputStream uploadFile = new FileInputStream(filePath);
        // todo : address provenance on an import
        //azquoMemoryDBConnection.setNewProvenance("import", fileName);
        if (loggedInUser.getDatabase() == null && !fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
            throw new Exception("error: no database set");
        }
        String tempFile = "";
        String lcName = fileName.toLowerCase();
        if (lcName.endsWith(".jpg") || lcName.endsWith(".png") || lcName.endsWith(".gif")) {
            imageImport(loggedInUser, uploadFile, fileName);
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
        if (fileName.contains(".xls")) {
            readBook(loggedInUser, fileName, tempFile, attributeNames);
        } else {
            if (tempFile.length() > 0) {
                filePath = tempFile;
            }
            readPreparedFile(loggedInUser.getDataAccessToken(), filePath, fileType, attributeNames);
        }
        /* todo - sort this upload record later . . .
        int databaseId = 0;
        if (azquoMemoryDBConnection.getAzquoMemoryDB() != null) {
            azquoMemoryDBConnection.persist();
            databaseId = azquoMemoryDBConnection.getAzquoMemoryDB().getDatabase().getId();
        }
        if (fileType == null) {
            fileType = "spreadsheet";
        }
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), azquoMemoryDBConnection.getBusinessId(), databaseId, azquoMemoryDBConnection.getUser().getId(), fileName, fileType, "");//;should record the error????
        uploadRecordDAO.store(uploadRecord);*/
    }

    private void imageImport(LoggedInUser loggedInUser, InputStream inputStream, String fileName) throws Exception {
        String targetFileName = "/home/azquo/databases/" + loggedInUser.getDatabase().getName() + "/images/" + fileName;
        File output = new File(targetFileName);
        output.getParentFile().mkdirs();
        if (!output.exists()) {
            output.createNewFile();
        }
        FileUtils.copyInputStreamToFile(inputStream, output);
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
                    if (shift > 0) {                        //not sure here....
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

    private void uploadReport(LoggedInUser loggedInUser, AzquoBook azquoBook, String fileName, String reportName) throws Exception {
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = 0;
        String pathName = adminService.getBusinessPrefix(loggedInUser);
        if (loggedInUser.getDatabase() != null) {
            databaseId = loggedInUser.getDatabase().getId();
            pathName = loggedInUser.getDatabase().getName();
        }
        OnlineReport or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
        int reportId = 0;
        if (or != null) {
            reportId = or.getId();
        }
        String fullPath = spreadsheetService.getHomeDir() + dbPath + pathName + "/onlinereports/" + fileName;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();

        FileOutputStream out = new FileOutputStream(fullPath);
        azquoBook.saveBook(fullPath);
        out.close();
        or = new OnlineReport(reportId, businessId, databaseId, "", reportName,"","", fileName, "", "");
        onlineReportDAO.store(or);
    }

    private void readBook(LoggedInUser loggedInUser, final String fileName, final String tempName, List<String> attributeNames) throws Exception {
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, spreadsheetService, this);
        azquoBook.loadBook(tempName, spreadsheetService.useAsposeLicense());
        String reportName = azquoBook.getReportName();
        if (reportName != null) {
            uploadReport(loggedInUser, azquoBook, fileName, reportName);
            return;
        }
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("no database set");
        }
        int sheetNo = 0;
        while (sheetNo < azquoBook.getNumberOfSheets()) {
            readSheet(loggedInUser, azquoBook, tempName, sheetNo, attributeNames);
            sheetNo++;
        }
    }

    private void readSheet(LoggedInUser loggedInUser, AzquoBook azquoBook, final String tempFileName, final int sheetNo, List<String> attributeNames) throws Exception {
        String tempName = azquoBook.convertSheetToCSV(tempFileName, sheetNo);
        String fileType = tempName.substring(tempName.lastIndexOf(".") + 1);
        readPreparedFile(loggedInUser.getDataAccessToken(), tempName, fileType, attributeNames);
    }

    private void readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileType, List<String> attributeNames) throws Exception {
        rmiClient.getServerInterface().readPreparedFile(databaseAccessToken,filePath,fileType,attributeNames);
    }
}