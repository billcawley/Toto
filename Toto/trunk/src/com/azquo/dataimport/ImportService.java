package com.azquo.dataimport;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.UploadRecord;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.AzquoBook;
import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.time.LocalDateTime;
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
    private SpreadsheetService spreadsheetService;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private UserRegionOptionsDAO userRegionOptionsDAO;
    @Autowired
    private RMIClient rmiClient;
    @Autowired
    private UploadRecordDAO uploadRecordDAO;

    public void importTheFile(LoggedInUser loggedInUser, String fileName, String filePath) throws Exception {
        //used for Magento importing (data  - not report templates)
        List<String> languages = new ArrayList<String>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        importTheFile(loggedInUser, fileName, "", filePath, "", true, languages);
    }

    public void importTheFile(LoggedInUser loggedInUser, String fileName, String useType, String filePath, String fileType, boolean skipBase64, List<String> attributeNames) throws Exception {
        String error = "";
        importTheFile1(loggedInUser, fileName, useType, filePath, fileType, skipBase64, attributeNames);
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), loggedInUser.getUser().getBusinessId(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName, "", error);//;should record the error????
        uploadRecordDAO.store(uploadRecord);
    }

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public void importTheFile1(LoggedInUser loggedInUser, String fileName, String useType, String filePath, String fileType, boolean skipBase64, List<String> attributeNames)
            throws Exception {


        //fileType is now always the first word of the spreadsheet/dataimport file name
        if (fileType.length() == 0) {
            int slashpos = filePath.lastIndexOf("/");
            if (slashpos > 0) {
                int dotPos = filePath.indexOf(".", slashpos);
                if (dotPos > 0) {
                    fileType = filePath.substring(slashpos + 1, dotPos);
                }
            }
        }
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
        // todo - multiple files in zip file?
        if (fileName.contains(".xls")) {
            readBook(loggedInUser, fileName, tempFile, attributeNames, (useType != null && useType.length() > 0));
        } else {
            if (tempFile.length() > 0) {
                filePath = tempFile;
            }
            readPreparedFile(loggedInUser, filePath, fileType, attributeNames);
        }
    }

    private void imageImport(LoggedInUser loggedInUser, InputStream inputStream, String fileName) throws Exception {
        String targetFileName = spreadsheetService.getHomeDir() + "/databases/" + loggedInUser.getDatabase().getName() + "/images/" + fileName;
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
            byte[] data = new byte[1024*1024]; // changing this to 10k should it be more?
            int byteRead;


            ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileName)));
            // while((zin.getNextEntry()) != null){
            //READ ONE ENTRY ONLY...
            zin.getNextEntry();
            File tmpOutput = File.createTempFile(outputFile, suffix);
            tmpOutput.deleteOnExit();
            BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(tmpOutput), data.length);
            while ((byteRead = zin.read(data, 0, data.length)) != -1) {
                bout.write(data, 0, byteRead);
            }
            bout.flush();
            bout.close();
            return tmpOutput.getPath();
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
            //write it
            fos.close();
            System.out.println("Decode 64 Done");
        } catch (Exception e) {
            e.getStackTrace();
        }
        return tempName;
    }

    // todo - directory location should be the database code!

    private void uploadReport(LoggedInUser loggedInUser, AzquoBook azquoBook, String fileName, String reportName, String reportType) throws Exception {
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = 0;
        String pathName = reportType;
        if (pathName.length() == 0) {
            databaseId = loggedInUser.getDatabase().getId();
            pathName = loggedInUser.getDatabase().getMySQLName();
        }
        int renderer = OnlineReport.AZQUO_BOOK;
        if (azquoBook.getRangeData("az_ZKSheet")!=null){
            renderer = OnlineReport.ZK_AZQUO_BOOK;

        }
        OnlineReport or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
        if (or == null) {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, databaseId, "", reportName,"","", "", fileName, "", "", renderer,  true); // default to old for the moment
        } else {
            or.setActive(false);
            onlineReportDAO.store(or);
        }
        or.setDateCreated(LocalDateTime.now());
        or.setId(0);
        or.setActive(true);
        String fullPath = spreadsheetService.getHomeDir() + dbPath + pathName + "/onlinereports/" + fileName;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();

        FileOutputStream out = new FileOutputStream(fullPath);
        azquoBook.saveBook(fullPath);
        out.close();
        onlineReportDAO.store(or);
    }

    private void readBook(LoggedInUser loggedInUser, final String fileName, final String tempName, List<String> attributeNames, boolean useType) throws Exception {
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, userRegionOptionsDAO, spreadsheetService);
        azquoBook.loadBook(tempName, spreadsheetService.useAsposeLicense());
        String reportName = azquoBook.getReportName();
        if (reportName != null) {
            String reportType = "";
            if (useType) {
                reportType = loggedInUser.getDatabaseType();
            }
            uploadReport(loggedInUser, azquoBook, fileName, reportName, reportType);
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
        readPreparedFile(loggedInUser, tempName, fileType, attributeNames);
    }

    static String LOCALIP = "127.0.0.1";

    public void readPreparedFile(LoggedInUser loggedInUser, String filePath, String fileType, List<String> attributeNames) throws Exception {
        // right - here we're going to have to move the file if the DB server is not local.
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        if (databaseServer.getIp().equals(LOCALIP)){
            rmiClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames, loggedInUser.getUser().getName());
        } else {
            // move it
            String remoteFilePath = copyFileToDatabaseServer(filePath, databaseServer.getSftpUrl());
            rmiClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, remoteFilePath, fileType, attributeNames, loggedInUser.getUser().getName());
        }
    }


    // modified internet example
    public String copyFileToDatabaseServer(String filePath, String sftpDestination) {
        StandardFileSystemManager manager = new StandardFileSystemManager();
        String toReturn = null;
        try {
            File file = new File(filePath);
            if (!file.exists()){
                throw new RuntimeException("Error. Local file not found");
            }
            //Initializes the file manager
            manager.init();
            //Setup our SFTP configuration
            FileSystemOptions opts = new FileSystemOptions();
            SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no");
            SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, false);
            SftpFileSystemConfigBuilder.getInstance().setTimeout(opts, 10000);
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
            //Create the SFTP URI using the host name, userid, password,  remote path and file name
            String sftpUri = sftpDestination + fileName;
            // Create local file object
            FileObject localFile = manager.resolveFile(file.getAbsolutePath());
            // Create remote file object
            FileObject remoteFile = manager.resolveFile(sftpUri, opts);
            // Copy local file to sftp server
            remoteFile.copyFrom(localFile, Selectors.SELECT_SELF);
            System.out.println("File upload successful");
            int atPoint = sftpDestination.indexOf("@");
            toReturn = sftpDestination.substring(sftpDestination.indexOf("/", atPoint)) + fileName; // should be the path of the file on the remote machine.
            // I'm going to zap the file now
            localFile.delete();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            manager.close();
        }
        return toReturn;
    }

}