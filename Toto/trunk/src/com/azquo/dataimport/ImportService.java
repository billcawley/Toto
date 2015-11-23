package com.azquo.dataimport;

import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.UploadRecord;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.AzquoBook;
import org.apache.commons.collections.iterators.ArrayListIterator;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by bill on 13/12/13.
 * Preliminary processing before being sent over to the database server for loading.
 */

public final class ImportService {
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

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames) throws Exception {
        InputStream uploadFile = new FileInputStream(filePath);
        // todo : address provenance on an import
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("error: no database set");
        }
        String tempFile = tempFileWithoutDecoding(uploadFile, fileName);
        String toReturn;
        if (fileName.endsWith(".zip")) {
            fileName = fileName.substring(0, fileName.length() - 4);
            List<File> files = unZip(tempFile);
            // should be sorting by xls first then size ascending
            Collections.sort(files, (f1, f2) -> {
                if ((f1.getName().endsWith(".xls") || f1.getName().endsWith(".xlsx")) && (!f2.getName().endsWith(".xls") && !f2.getName().endsWith(".xlsx"))){ // one is xls, the otehr is not
                    return -1;
                }
                if ((f2.getName().endsWith(".xls") || f2.getName().endsWith(".xlsx")) && (!f1.getName().endsWith(".xls") && !f1.getName().endsWith(".xlsx"))){ // otehr way round
                    return 1;
                }
                // fall back to file size among the same types
                if (f1.length() < f2.length()){
                    return -1;
                }
                if (f1.length() > f2.length()){
                    return 1;
                }
                return 0;
                    }
            );
            // todo - sort the files, small to large xls and xlsx as a group first
            StringBuilder sb = new StringBuilder();
            Iterator<File> fileIterator = files.iterator();
            while (fileIterator.hasNext()){
                File f = fileIterator.next();
                if (fileIterator.hasNext()){
                    sb.append(readBookOrFile(loggedInUser, f.getName(), f.getPath(), attributeNames, false) + "\n");
                } else {
                    sb.append(readBookOrFile(loggedInUser, f.getName(), f.getPath(), attributeNames, true)); // persist on the last one
                }
            }
            toReturn = sb.toString();
        } else { // vanilla
            toReturn = readBookOrFile(loggedInUser, fileName, tempFile, attributeNames, true);
        }
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), loggedInUser.getUser().getBusinessId(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName, "", "");//should record the error? (last parameter)
        uploadRecordDAO.store(uploadRecord);
        return toReturn;
    }

    // factored off to deal with
    String readBookOrFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean persistAfter) throws Exception {
        if (fileName.contains(".xls")) {
            return readBook(loggedInUser, fileName, filePath, attributeNames, persistAfter);
        } else {
            return readPreparedFile(loggedInUser, filePath, "", attributeNames, persistAfter); // no file type
        }
    }

    public List<File> unZip(String zipFile) {
        String outputFolder = zipFile.substring(0, zipFile.lastIndexOf("/"));// same dir
        byte[] buffer = new byte[1024];
        List<File> toReturn = new ArrayList<>();
        try {

            //create output directory is not exists
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }

            //get the zip file content
            ZipInputStream zis =
                    new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                System.out.println("file unzip : " + newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                if (ze.isDirectory()){
                    newFile.mkdirs();
                } else {
                    toReturn.add(newFile);
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            System.out.println("Done");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return toReturn;
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

    private String uploadReport(LoggedInUser loggedInUser, String sourceName, String fileName, String reportName) throws Exception {
        int businessId = loggedInUser.getUser().getBusinessId();
            int databaseId = loggedInUser.getDatabase().getId();
        OnlineReport or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
        if (or == null) {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, databaseId, "", reportName, "", "", "", fileName, "", "", 1, true); // default to ZK now
        } else {
            or.setActive(false);
            onlineReportDAO.store(or);
        }
        or.setDateCreated(LocalDateTime.now());
        or.setId(0);
        or.setRenderer(1);
        or.setActive(true);
        String fullPath = spreadsheetService.getHomeDir() + dbPath + loggedInUser.getDatabase().getMySQLName() + "/onlinereports/" + fileName;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();

        FileOutputStream out = new FileOutputStream(fullPath);
//        azquoBook.saveBook(fullPath); no, aspose could have changed the sheet, especially if the license is not set . . .
        org.apache.commons.io.FileUtils.copyFile(new File(sourceName), out);// straight copy of the source
        out.close();
        onlineReportDAO.store(or);
        return reportName + " uploaded.";
    }

    private String readBook(LoggedInUser loggedInUser, final String fileName, final String tempName, List<String> attributeNames, boolean persistAfter) throws Exception {
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, userRegionOptionsDAO, spreadsheetService, rmiClient);
        azquoBook.loadBook(tempName, spreadsheetService.useAsposeLicense());
        String reportName = azquoBook.getReportName();
        if (reportName != null) {
            return uploadReport(loggedInUser, tempName, fileName, reportName);
        }
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("no database set");
        }
        int sheetNo = 0;
        StringBuilder toReturn = new StringBuilder();
        while (sheetNo < azquoBook.getNumberOfSheets()) {
            toReturn.append(readSheet(loggedInUser, azquoBook, tempName, sheetNo, attributeNames, sheetNo == azquoBook.getNumberOfSheets() - 1 && persistAfter)); // that last conditional means persist on the last one through (if we've been told to persist)
            toReturn.append("\n");
            sheetNo++;
        }
        return toReturn.toString();
    }

    private String readSheet(LoggedInUser loggedInUser, AzquoBook azquoBook, final String tempFileName, final int sheetNo, List<String> attributeNames, boolean persistAfter) throws Exception {
        String tempName = azquoBook.convertSheetToCSV(tempFileName, sheetNo);
        String fileType = tempName.substring(tempName.lastIndexOf(".") + 1);
        return readPreparedFile(loggedInUser, tempName, fileType, attributeNames, persistAfter);
    }

    static String LOCALIP = "127.0.0.1";

    public String readPreparedFile(LoggedInUser loggedInUser, String filePath, String fileType, List<String> attributeNames, boolean persistAfter) throws Exception {
        // right - here we're going to have to move the file if the DB server is not local.
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        if (databaseServer.getIp().equals(LOCALIP)) {
            return rmiClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames, loggedInUser.getUser().getName(), persistAfter);
        } else {
            // move it
            String remoteFilePath = copyFileToDatabaseServer(filePath, databaseServer.getSftpUrl());
            return rmiClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, remoteFilePath, fileType, attributeNames, loggedInUser.getUser().getName(), persistAfter);
        }
    }

    // modified internet example
    public String copyFileToDatabaseServer(String filePath, String sftpDestination) {
        StandardFileSystemManager manager = new StandardFileSystemManager();
        String toReturn = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
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