package com.azquo.dataimport;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.view.AzquoBook;
import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 13/12/13.
 * <p>
 * Preliminary processing before being sent over to the database server for loading.
 */

public final class ImportService {
    public static final String dbPath = "/databases/";

    @Autowired
    private OnlineReportDAO onlineReportDAO;
    @Autowired
    private SpreadsheetService spreadsheetService;
    @Autowired
    private AdminService adminService;
    @Autowired
    private UserChoiceDAO userChoiceDAO;
    @Autowired
    private UserRegionOptionsDAO userRegionOptionsDAO;
    @Autowired
    private RMIClient rmiClient;
    @Autowired
    private UploadRecordDAO uploadRecordDAO;
    @Autowired
    private BusinessDAO businessDAO;
    @Autowired
    private PermissionDAO permissionDAO;
    @Autowired
    private UserDAO userDAO;
    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private ReportScheduleDAO reportScheduleDAO;
    @Autowired
    private DatabaseReportLinkDAO databaseReportLinkDAO;

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean isData) throws Exception {
        InputStream uploadFile = new FileInputStream(filePath);
        // todo : address provenance on an import
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("error: no database set");
        }
        String tempFile = tempFileWithoutDecoding(uploadFile, fileName);
        uploadFile.close(); // windows requires this (though windows should not be used in production), perhaps not a bad idea anyway
        String toReturn;
        if (fileName.endsWith(".zip")) {
            fileName = fileName.substring(0, fileName.length() - 4);
            List<File> files = unZip(tempFile);
            // should be sorting by xls first then size ascending
            Collections.sort(files, (f1, f2) -> {
                        if ((f1.getName().endsWith(".xls") || f1.getName().endsWith(".xlsx")) && (!f2.getName().endsWith(".xls") && !f2.getName().endsWith(".xlsx"))) { // one is xls, the otehr is not
                            return -1;
                        }
                        if ((f2.getName().endsWith(".xls") || f2.getName().endsWith(".xlsx")) && (!f1.getName().endsWith(".xls") && !f1.getName().endsWith(".xlsx"))) { // otehr way round
                            return 1;
                        }
                        // fall back to file size among the same types
                        if (f1.length() < f2.length()) {
                            return -1;
                        }
                        if (f1.length() > f2.length()) {
                            return 1;
                        }
                        return 0;
                    }
            );
            // todo - sort the files, small to large xls and xlsx as a group first
            StringBuilder sb = new StringBuilder();
            Iterator<File> fileIterator = files.iterator();
            while (fileIterator.hasNext()) {
                File f = fileIterator.next();
                if (fileIterator.hasNext()) {
                    sb.append(readBookOrFile(loggedInUser, f.getName(), f.getPath(), attributeNames, false, isData)).append("\n");
                } else {
                    sb.append(readBookOrFile(loggedInUser, f.getName(), f.getPath(), attributeNames, true, isData)); // persist on the last one
                }
            }
            toReturn = sb.toString();
        } else { // vanilla
            toReturn = readBookOrFile(loggedInUser, fileName, tempFile, attributeNames, true, isData);
        }
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), loggedInUser.getUser().getBusinessId(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName, "", "");//should record the error? (last parameter)
        uploadRecordDAO.store(uploadRecord);
        return toReturn;
    }

    private String readBookOrFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean persistAfter, boolean isData) throws Exception {
        if (fileName.equals(CreateExcelForDownloadController.USERSPERMISSIONSFILENAME) && loggedInUser.getUser().isAdministrator()) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
            Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
            Sheet permissionsSheet = book.getSheet("Permissions"); // literals not best practice, could it be factored between this and the xlsx file?
            if (userSheet != null && permissionsSheet != null) {
                int row = 1;
                // keep them to use if not set. Should I be updating records instead? I'm not sure.
                Map<String, String> oldPasswordMap = new HashMap<>();
                Map<String, String> oldSaltMap = new HashMap<>();
                List<User> userList = adminService.getUserListForBusiness(loggedInUser);
                for (User user : userList) {
                    if (user.getId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                        oldPasswordMap.put(user.getEmail(), user.getPassword());
                        oldSaltMap.put(user.getEmail(), user.getSalt());
                        userDAO.removeById(user);
                    }
                }
                while (userSheet.getInternalSheet().getCell(row, 0).getStringValue() != null && userSheet.getInternalSheet().getCell(row, 0).getStringValue().length() > 0) {
                    String user = userSheet.getInternalSheet().getCell(row, 0).getStringValue();
                    String email = userSheet.getInternalSheet().getCell(row, 1).getStringValue();
                    if (!loggedInUser.getUser().getEmail().equals(email)) { // leave the logged in user alone!
                        LocalDateTime end = LocalDateTime.now();
                        try {
                            end = LocalDateTime.parse(userSheet.getInternalSheet().getCell(row, 3).getStringValue(), CreateExcelForDownloadController.dateTimeFormatter);
                        } catch (Exception ignored) {
                        }
                        // should we allow them to change businesses?
                        Business b = businessDAO.findByName(userSheet.getInternalSheet().getCell(row, 4).getStringValue());
                        String status = userSheet.getInternalSheet().getCell(row, 5).getStringValue();
                        String salt = "";
                        String password = userSheet.getInternalSheet().getCell(row, 5).getStringValue();
                        if (password == null) {
                            password = "";
                        }
                        // Probably could be factored somewhere
                        if (password.length() > 0) {
                            salt = adminService.shaHash(System.currentTimeMillis() + "salt");
                            password = adminService.encrypt(password, salt);
                        } else if (oldPasswordMap.get(email) != null) {
                            password = oldPasswordMap.get(email);
                            salt = oldSaltMap.get(email);
                        }
                        User user1 = new User(0, end, b != null ? b.getId() : loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail());
                        userDAO.store(user1);
                    }
                    row++;
                }
                List<Permission> permissionList = permissionDAO.findByBusinessId(loggedInUser.getUser().getBusinessId());
                for (Permission permission : permissionList) {
                    if (permission.getUserId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                        permissionDAO.removeById(permission);
                    }
                }
                row = 1;
                while (permissionsSheet.getInternalSheet().getCell(row, 0).getStringValue() != null && permissionsSheet.getInternalSheet().getCell(row, 0).getStringValue().length() > 0) {
                    String database = userSheet.getInternalSheet().getCell(row, 0).getStringValue();
                    String email = userSheet.getInternalSheet().getCell(row, 1).getStringValue();
                    if (!loggedInUser.getUser().getEmail().equals(email)) { // leave the logged in user alone!
                        User user = userDAO.findByEmail(email);
                        if (user != null) {
                            Database database1 = databaseDAO.findForName(user.getBusinessId(), database);
                            if (database1 != null) {
                                String reportName = userSheet.getInternalSheet().getCell(row, 2).getStringValue();
                                final OnlineReport forDatabaseIdAndName = onlineReportDAO.findForDatabaseIdAndName(database1.getId(), reportName);
                                String readList = userSheet.getInternalSheet().getCell(row, 3).getStringValue();
                                String writeList = userSheet.getInternalSheet().getCell(row, 4).getStringValue();
                                Permission newPermission = new Permission(0, forDatabaseIdAndName.getId(), user.getId(), database1.getId(), readList, writeList);
                                permissionDAO.store(newPermission);
                            }
                        }
                    }
                    row++;
                }
            }
            return "User Permissions file uploaded"; // I hope that's what it is looking for.
        } else if (fileName.equals(CreateExcelForDownloadController.USERSFILENAME) && loggedInUser.getUser().isMaster()) {
            User master = loggedInUser.getUser();
            Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
            if (userSheet != null) {
                int row = 1;
                // keep them to use if not set. Should I be updating records instead? I'm not sure.
                Map<String, String> oldPasswordMap = new HashMap<>();
                Map<String, String> oldSaltMap = new HashMap<>();
                List<User> userList = adminService.getUserListForBusiness(loggedInUser); // this will switch logic internally given that the user is a master (only return users created by this one)
                for (User user : userList) {// as before cache the passwords in case new ones have not been entered
                    oldPasswordMap.put(user.getEmail(), user.getPassword());
                    oldSaltMap.put(user.getEmail(), user.getSalt());
                    userDAO.removeById(user);
                }
                while (userSheet.getInternalSheet().getCell(row, 0).getStringValue() != null && userSheet.getInternalSheet().getCell(row, 0).getStringValue().length() > 0) {
                    String user = userSheet.getInternalSheet().getCell(row, 0).getStringValue();
                    String email = userSheet.getInternalSheet().getCell(row, 1).getStringValue();
                    String salt = "";
                    String password = userSheet.getInternalSheet().getCell(row, 2).getStringValue();
                    if (password == null) {
                        password = "";
                    }
                    if (password.length() > 0) {
                        salt = adminService.shaHash(System.currentTimeMillis() + "salt");
                        password = adminService.encrypt(password, salt);
                    } else if (oldPasswordMap.get(email) != null) {
                        password = oldPasswordMap.get(email);
                        salt = oldSaltMap.get(email);
                    }
                    // copy details from the master user
                    User user1 = new User(0, master.getEndDate(), master.getBusinessId(), email, user, master.getStatus(), password, salt, master.getEmail());
                    userDAO.store(user1);
                    row++;
                }
            }
            return "User Permissions file uploaded"; // I hope that's what it is looking for.
        } else if (fileName.equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
            Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet schedulesSheet = book.getSheet("ReportSchedules"); // literals not best practice, could it be factored between this and the xlsx file?
            if (schedulesSheet != null) {
                int row = 1;
                final List<ReportSchedule> reportSchedules = adminService.getReportScheduleList(loggedInUser);
                for (ReportSchedule reportSchedule : reportSchedules) {
                    reportScheduleDAO.removeById(reportSchedule);
                }
                while (schedulesSheet.getInternalSheet().getCell(row, 0).getStringValue() != null && schedulesSheet.getInternalSheet().getCell(row, 0).getStringValue().length() > 0) {
                    String period = schedulesSheet.getInternalSheet().getCell(row, 0).getStringValue();
                    String recipients = schedulesSheet.getInternalSheet().getCell(row, 1).getStringValue();
                    LocalDateTime nextDue = LocalDateTime.now();
                    try {
                        nextDue = LocalDateTime.parse(schedulesSheet.getInternalSheet().getCell(row, 2).getStringValue(), CreateExcelForDownloadController.dateTimeFormatter);
                    } catch (Exception ignored) {
                    }
                    String database = schedulesSheet.getInternalSheet().getCell(row, 3).getStringValue();
                    Database database1 = databaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
                    if (database1 != null) {
                        String report = schedulesSheet.getInternalSheet().getCell(row, 4).getStringValue();
                        OnlineReport onlineReport = onlineReportDAO.findForDatabaseIdAndName(database1.getId(), report);
                        if (onlineReport != null) {
                            String type = schedulesSheet.getInternalSheet().getCell(row, 5).getStringValue();
                            String parameters = schedulesSheet.getInternalSheet().getCell(row, 6).getStringValue();
                            String emailSubject = schedulesSheet.getInternalSheet().getCell(row, 7).getStringValue();
                            ReportSchedule rs = new ReportSchedule(0, period, recipients, nextDue, database1.getId(), onlineReport.getId(), type, parameters, emailSubject);
                            reportScheduleDAO.store(rs);
                        }
                    }
                    row++;
                }
            }
            return "Report schedules file uploaded"; // I hope that's what it is looking for.
        } else if (fileName.contains(".xls")) { // normal. I'm not entirely sure the code for users etc above should be in this file, maybe a different importer?
            return readBook(loggedInUser, fileName, filePath, attributeNames, persistAfter, isData);
        } else {
             return readPreparedFile(loggedInUser, filePath, fileName.substring(0, fileName.indexOf(".")), attributeNames, persistAfter, false); // no file type
        }
    }

    private List<File> unZip(String zipFile) {
        String outputFolder;
        if (!zipFile.contains("/")) {
            outputFolder = zipFile.substring(0, zipFile.lastIndexOf("\\"));// same dir
        } else { // normal
            outputFolder = zipFile.substring(0, zipFile.lastIndexOf("/"));// same dir
        }
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
                if (ze.isDirectory()) {
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
            File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
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

    private String uploadReport(LoggedInUser loggedInUser, String sourceName, String fileName, String reportName, String reportType) throws Exception {
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = 0;
        String pathName = reportType;
        if (pathName.length() == 0) {
            databaseId = loggedInUser.getDatabase().getId();
            pathName = loggedInUser.getBusinessDirectory();
        }
        OnlineReport or = onlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
        if (or == null) {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, "", reportName, "", fileName, "", "", 1, true); // default to ZK now
        } else {
            or.setActive(false);
            onlineReportDAO.store(or);
        }
        or.setDateCreated(LocalDateTime.now());
        or.setId(0);
        or.setRenderer(1);
        or.setActive(true);
        String fullPath = spreadsheetService.getHomeDir() + dbPath + pathName + "/onlinereports/" + fileName;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();

        FileOutputStream out = new FileOutputStream(fullPath);
//        azquoBook.saveBook(fullPath); no, aspose could have changed the sheet, especially if the license is not set . . .
        org.apache.commons.io.FileUtils.copyFile(new File(sourceName), out);// straight copy of the source
        out.close();
        onlineReportDAO.store(or);
        databaseReportLinkDAO.link(databaseId, or.getId());
        return reportName + " uploaded.";
    }

    private String readBook(LoggedInUser loggedInUser, final String fileName, final String tempName, List<String> attributeNames, boolean persistAfter, boolean isData) throws Exception {
        AzquoBook azquoBook = new AzquoBook(userChoiceDAO, userRegionOptionsDAO, spreadsheetService, rmiClient);
        azquoBook.loadBook(tempName, spreadsheetService.useAsposeLicense());
        String reportName = azquoBook.getReportName();
        if (reportName != null) {
            if (loggedInUser.getUser().isAdministrator() && !isData) {
                return uploadReport(loggedInUser, tempName, fileName, reportName, "");
            }
            LoggedInUser loadingUser = new LoggedInUser(loggedInUser);
            OnlineReport or = onlineReportDAO.findForDatabaseIdAndName(loadingUser.getDatabase().getId(), reportName);
            if (or == null) return "no report named " + reportName + " found";
            azquoBook.calculateAll();
            Map<String, String> choices = azquoBook.uploadChoices();
            for (String choice : choices.keySet()) {
                spreadsheetService.setUserChoice(loadingUser.getUser().getId(), choice, choices.get(choice));
            }
            AzquoBook reportBook = spreadsheetService.loadAzquoBook(loadingUser, or);
            azquoBook.dataRegionPrefix = AzquoBook.azDataRegion;
            String toReturn = azquoBook.fillDataRangesFromCopy(loadingUser, or.getId());
            reportBook.saveData(loadingUser, or.getId());
            return toReturn;
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
        return readPreparedFile(loggedInUser, tempName, fileType, attributeNames, persistAfter, true);
    }

    private static String LOCALIP = "127.0.0.1";

    private String readPreparedFile(LoggedInUser loggedInUser, String filePath, String fileType, List<String> attributeNames, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        // right - here we're going to have to move the file if the DB server is not local.
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        if (databaseServer.getIp().equals(LOCALIP)) {
            return rmiClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
        } else {
            // move it
            File f = new File(filePath);
            String remoteFilePath = copyFileToDatabaseServer(new FileInputStream(f), databaseServer.getSftpUrl());
            return rmiClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, remoteFilePath, fileType, attributeNames, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
        }
    }

    private String copyFileToDatabaseServer(InputStream inputStream, String sftpDestination) {
        /*
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

    public  void send (String fileName) {
    */

        int userPos = sftpDestination.indexOf("//") + 2;
        int passPos = sftpDestination.indexOf(".", userPos);
        int passEnd = sftpDestination.indexOf("@", passPos);
        int pathPos = sftpDestination.indexOf("/", passEnd);
        int pathEnd = sftpDestination.lastIndexOf("/");

        String SFTPHOST = sftpDestination.substring(passEnd + 1, pathPos);
        int SFTPPORT = 22;
        String SFTPUSER = sftpDestination.substring(userPos, passPos);
        String SFTPPASS = sftpDestination.substring(passPos + 1, passEnd);
        String SFTPWORKINGDIR = sftpDestination.substring(pathPos, pathEnd);
        String fileName = sftpDestination.substring(pathEnd + 1);

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;
        System.out.println("preparing the host information for sftp.");
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
            session.setPassword(SFTPPASS);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            System.out.println("Host connected.");
            channel = session.openChannel("sftp");
            channel.connect();
            System.out.println("sftp channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            sftpCd(channelSftp, SFTPWORKINGDIR);
            channelSftp.put(inputStream, fileName);
            //log.info("File transfered successfully to host.");
        } catch (Exception ex) {
            System.out.println("Exception found while tranfer the response.");
        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
                System.out.println("sftp Channel exited.");
                channel.disconnect();
                System.out.println("Channel disconnected.");
                session.disconnect();
                System.out.println("Host Session disconnected.");
            }
        }
        return "file copied successfully";
    }

    public String uploadImage(LoggedInUser loggedInUser, MultipartFile sourceFile, String fileName) throws Exception {
        String success = "image uploaded successfully";
        String sourceName = sourceFile.getOriginalFilename();
        String suffix = sourceName.substring(sourceName.indexOf("."));
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        String pathOffset = loggedInUser.getDatabase().getPersistenceName() + "/images/" + fileName + suffix;
        String destinationPath = spreadsheetService.getHomeDir() + dbPath + pathOffset;
        if (databaseServer.getIp().equals(LOCALIP)) {
            File destination = new File(destinationPath);
            destination.getParentFile().mkdirs();
            sourceFile.transferTo(destination);
        } else {
            destinationPath = databaseServer.getSftpUrl() + pathOffset;
            copyFileToDatabaseServer(sourceFile.getInputStream(), destinationPath);
        }
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();

        String imageList = rmiClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images");
        if (imageList != null) {//check if it's already in the list
            String[] images = imageList.split(",");
            for (String image : images) {
                if (image.trim().equals(fileName + suffix)) {
                    return success;
                }
            }
            imageList += "," + fileName + suffix;
        } else {
            imageList = fileName + suffix;
        }
        rmiClient.getServerInterface(databaseAccessToken.getServerIp()).setNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images", imageList);
        return success;
    }

    private void sftpCd(ChannelSftp sftp, String path) throws SftpException {
        String[] folders = path.split("/");
        for (String folder : folders) {
            if (folder.length() > 0) {
                try {
                    sftp.cd(folder);
                } catch (SftpException e) {
                    sftp.mkdir(folder);
                    sftp.cd(folder);
                }
            }
        }
    }
}