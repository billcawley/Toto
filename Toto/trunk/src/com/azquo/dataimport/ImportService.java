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
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.view.CellForDisplay;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import com.csvreader.CsvWriter;
import com.jcraft.jsch.*;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.io.*;
import java.text.SimpleDateFormat;
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

    private static SimpleDateFormat ukdflong = new SimpleDateFormat("dd/MM/yy hh:mm:ss");
    private static SimpleDateFormat ukdf = new SimpleDateFormat("dd/MM/yy");
    private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");


    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public static String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean isData) throws Exception {
        InputStream uploadFile = new FileInputStream(filePath);
        // todo : address provenance on an import
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("error: no database set");
        }
        String tempFile = tempFileWithoutDecoding(uploadFile, fileName); // ok this takes the file and moves it to a temp directory, required for unzipping - maybe only use then?
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
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), loggedInUser.getUser().getBusinessId(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName, "", "", filePath);//should record the error? (in comment)
        UploadRecordDAO.store(uploadRecord);
        return toReturn;
    }

    private static String readBookOrFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean persistAfter, boolean isData) throws Exception {
        if (fileName.equals(CreateExcelForDownloadController.USERSPERMISSIONSFILENAME) && loggedInUser.getUser().isAdministrator()) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
            Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
            Sheet permissionsSheet = book.getSheet("Permissions"); // literals not best practice, could it be factored between this and the xlsx file?
            if (userSheet != null && permissionsSheet != null) {
                int row = 1;
                // keep them to use if not set. Should I be updating records instead? I'm not sure.
                Map<String, String> oldPasswordMap = new HashMap<>();
                Map<String, String> oldSaltMap = new HashMap<>();
                List<User> userList = AdminService.getUserListForBusiness(loggedInUser);
                for (User user : userList) {
                    if (user.getId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                        oldPasswordMap.put(user.getEmail(), user.getPassword());
                        oldSaltMap.put(user.getEmail(), user.getSalt());
                        UserDAO.removeById(user);
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
                        Business b = BusinessDAO.findByName(userSheet.getInternalSheet().getCell(row, 4).getStringValue());
                        String status = userSheet.getInternalSheet().getCell(row, 5).getStringValue();
                        String salt = "";
                        String password = userSheet.getInternalSheet().getCell(row, 5).getStringValue();
                        if (password == null) {
                            password = "";
                        }
                        // Probably could be factored somewhere
                        if (password.length() > 0) {
                            salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                            password = AdminService.encrypt(password, salt);
                        } else if (oldPasswordMap.get(email) != null) {
                            password = oldPasswordMap.get(email);
                            salt = oldSaltMap.get(email);
                        }
                        User user1 = new User(0, end, b != null ? b.getId() : loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail());
                        UserDAO.store(user1);
                    }
                    row++;
                }
                List<Permission> permissionList = PermissionDAO.findByBusinessId(loggedInUser.getUser().getBusinessId());
                for (Permission permission : permissionList) {
                    if (permission.getUserId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                        PermissionDAO.removeById(permission);
                    }
                }
                row = 1;
                while (permissionsSheet.getInternalSheet().getCell(row, 0).getStringValue() != null && permissionsSheet.getInternalSheet().getCell(row, 0).getStringValue().length() > 0) {
                    String database = userSheet.getInternalSheet().getCell(row, 0).getStringValue();
                    String email = userSheet.getInternalSheet().getCell(row, 1).getStringValue();
                    if (!loggedInUser.getUser().getEmail().equals(email)) { // leave the logged in user alone!
                        User user = UserDAO.findByEmail(email);
                        if (user != null) {
                            Database database1 = DatabaseDAO.findForName(user.getBusinessId(), database);
                            if (database1 != null) {
                                String reportName = userSheet.getInternalSheet().getCell(row, 2).getStringValue();
                                final OnlineReport forDatabaseIdAndName = OnlineReportDAO.findForDatabaseIdAndName(database1.getId(), reportName);
                                String readList = userSheet.getInternalSheet().getCell(row, 3).getStringValue();
                                String writeList = userSheet.getInternalSheet().getCell(row, 4).getStringValue();
                                Permission newPermission = new Permission(0, forDatabaseIdAndName.getId(), user.getId(), database1.getId(), readList, writeList);
                                PermissionDAO.store(newPermission);
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
                List<User> userList = AdminService.getUserListForBusiness(loggedInUser); // this will switch logic internally given that the user is a master (only return users created by this one)
                for (User user : userList) {// as before cache the passwords in case new ones have not been entered
                    oldPasswordMap.put(user.getEmail(), user.getPassword());
                    oldSaltMap.put(user.getEmail(), user.getSalt());
                    UserDAO.removeById(user);
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
                        salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                        password = AdminService.encrypt(password, salt);
                    } else if (oldPasswordMap.get(email) != null) {
                        password = oldPasswordMap.get(email);
                        salt = oldSaltMap.get(email);
                    }
                    // copy details from the master user
                    User user1 = new User(0, master.getEndDate(), master.getBusinessId(), email, user, master.getStatus(), password, salt, master.getEmail());
                    UserDAO.store(user1);
                    row++;
                }
            }
            return "User Permissions file uploaded"; // I hope that's what it is looking for.
        } else if (fileName.equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
            Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet schedulesSheet = book.getSheet("ReportSchedules"); // literals not best practice, could it be factored between this and the xlsx file?
            if (schedulesSheet != null) {
                int row = 1;
                final List<ReportSchedule> reportSchedules = AdminService.getReportScheduleList(loggedInUser);
                for (ReportSchedule reportSchedule : reportSchedules) {
                    ReportScheduleDAO.removeById(reportSchedule);
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
                    Database database1 = DatabaseDAO.findForName(loggedInUser.getUser().getBusinessId(), database);
                    if (database1 != null) {
                        String report = schedulesSheet.getInternalSheet().getCell(row, 4).getStringValue();
                        OnlineReport onlineReport = OnlineReportDAO.findForDatabaseIdAndName(database1.getId(), report);
                        if (onlineReport != null) {
                            String type = schedulesSheet.getInternalSheet().getCell(row, 5).getStringValue();
                            String parameters = schedulesSheet.getInternalSheet().getCell(row, 6).getStringValue();
                            String emailSubject = schedulesSheet.getInternalSheet().getCell(row, 7).getStringValue();
                            ReportSchedule rs = new ReportSchedule(0, period, recipients, nextDue, database1.getId(), onlineReport.getId(), type, parameters, emailSubject);
                            ReportScheduleDAO.store(rs);
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

    private static List<File> unZip(String zipFile) {
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

    private static String tempFileWithoutDecoding(final InputStream data, final String fileName) {
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

    private static String uploadReport(LoggedInUser loggedInUser, String sourceName, String fileName, String reportName, String reportType) throws Exception {
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = 0;
        String pathName = reportType;
        if (pathName.length() == 0) {
            databaseId = loggedInUser.getDatabase().getId();
            pathName = loggedInUser.getBusinessDirectory();
        }
        OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
        // change in logic, no longer making a copy, want to update what's there
        if (or == null) {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, "", reportName, "", fileName, "", "", 1); // default to ZK now
        } else {
            or.setFilename(fileName); // it might have changed, I don't think much else under these circumstances
        }
        or.setFilename(fileName);
        String fullPath = SpreadsheetService.getHomeDir() + dbPath + pathName + "/onlinereports/" + fileName;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(fullPath);
//        azquoBook.saveBook(fullPath); no, aspose could have changed the sheet, especially if the license is not set . . .
        org.apache.commons.io.FileUtils.copyFile(new File(sourceName), out);// straight copy of the source
        out.close();
        OnlineReportDAO.store(or);
        DatabaseReportLinkDAO.link(databaseId, or.getId());
        return reportName + " uploaded.";
    }

    private static String  readBook(LoggedInUser loggedInUser, final String fileName, final String tempName, List<String> attributeNames, boolean persistAfter, boolean isData) throws Exception {
        final Book book = Importers.getImporter().imports(new File(tempName), "Imported");
        //AzquoBook azquoBook = new AzquoBook(userChoiceDAO, userRegionOptionsDAO, spreadsheetService, RMIClient);
        //azquoBook.loadBook(tempName, spreadsheetService.useAsposeLicense());
        String reportName = null;
        SName reportRange = book.getInternalBook().getNameByName("az_ReportName");
        if (reportRange != null) {
            reportName = ZKAzquoBookUtils.getSnameCell(reportRange).getStringValue();
        }
        if (reportName != null) {
            if (loggedInUser.getUser().isAdministrator() && !isData) {
                return uploadReport(loggedInUser, tempName, fileName, reportName, "");
            }
            LoggedInUser loadingUser = new LoggedInUser(loggedInUser);
            OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(loadingUser.getDatabase().getId(), reportName);
            if (or == null) return "no report named " + reportName + " found";
            Map<String, String> choices = uploadChoices(book);
            for (String choice : choices.keySet()) {
                SpreadsheetService.setUserChoice(loadingUser.getUser().getId(), choice, choices.get(choice));
            }
            //String bookPath = spreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + or.getFilename();
            String bookPath = tempName;//take the import sheet as a template.
            final Book reportBook = Importers.getImporter().imports(new File(bookPath), "Report name");
            reportBook.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
            reportBook.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
            reportBook.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
            ZKAzquoBookUtils.populateBook(reportBook, 0);
            String toReturn = fillDataRangesFromCopy(loggedInUser, book, or);

            return toReturn;
        }
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("no database set");
        }
        StringBuilder toReturn = new StringBuilder();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            toReturn.append(readSheet(loggedInUser, sheet, tempName, attributeNames, sheetNo == book.getNumberOfSheets() - 1 && persistAfter)); // that last conditional means persist on the last one through (if we've been told to persist)
            toReturn.append("\n");
        }
        return toReturn.toString();
    }

    private static String readSheet(LoggedInUser loggedInUser, Sheet sheet, final String tempFileName, List<String> attributeNames, boolean persistAfter) throws Exception {
        String tempName = convertSheetToCSV(tempFileName, sheet);
        String fileType = tempName.substring(tempName.lastIndexOf(".") + 1);
        return readPreparedFile(loggedInUser, tempName, fileType, attributeNames, persistAfter, true);
    }

    private static String LOCALIP = "127.0.0.1";

    private static String readPreparedFile(LoggedInUser loggedInUser, String filePath, String fileType, List<String> attributeNames, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        // right - here we're going to have to move the file if the DB server is not local.
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        if (databaseServer.getIp().equals(LOCALIP)) {
            return RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileType, attributeNames, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
        } else {
            // move it
            File f = new File(filePath);
            String remoteFilePath = copyFileToDatabaseServer(new FileInputStream(f), databaseServer.getSftpUrl());
            return RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, remoteFilePath, fileType, attributeNames, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
        }
    }

    private static String copyFileToDatabaseServer(InputStream inputStream, String sftpDestination) {
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

    public static String uploadImage(LoggedInUser loggedInUser, MultipartFile sourceFile, String fileName) throws Exception {
        String success = "image uploaded successfully";
        String sourceName = sourceFile.getOriginalFilename();
        String suffix = sourceName.substring(sourceName.indexOf("."));
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        String pathOffset = loggedInUser.getDatabase().getPersistenceName() + "/images/" + fileName + suffix;
        String destinationPath = SpreadsheetService.getHomeDir() + dbPath + pathOffset;
        if (databaseServer.getIp().equals(LOCALIP)) {
            File destination = new File(destinationPath);
            destination.getParentFile().mkdirs();
            sourceFile.transferTo(destination);
        } else {
            destinationPath = databaseServer.getSftpUrl() + pathOffset;
            copyFileToDatabaseServer(sourceFile.getInputStream(), destinationPath);
        }
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();

        String imageList = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images");
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
        RMIClient.getServerInterface(databaseAccessToken.getServerIp()).setNameAttribute(databaseAccessToken, loggedInUser.getImageStoreName(), "uploaded images", imageList);
        return success;
    }

    private static void sftpCd(ChannelSftp sftp, String path) throws SftpException {
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

    // for the download, modify and upload the report

    public static String fillDataRangesFromCopy(LoggedInUser loggedInUser, Book sourceBook, OnlineReport onlineReport) {
        int items = 0;
        String errorMessage = "";
        int nonBlankItems = 0;
        Sheet sourceSheet = sourceBook.getSheetAt(0);
        for (SName sName : sourceBook.getInternalBook().getNames()) {
            String name = sName.getName();
            String regionName = getRegionName(name);
            if (regionName != null) {
                CellRegion sourceRegion = sName.getRefersToCellRegion();
                if (name.toLowerCase().contains(ZKAzquoBookUtils.azRepeatScope.toLowerCase())) { // then deal with the multiple data regions sent due to this
                    // need to gather associated names for calculations, the region and the data region, code copied and changewd from getRegionRowColForRepeatRegion, it needs to work well for a batch of cells not just one
                    SName repeatRegion = sourceBook.getInternalBook().getNameByName(ZKAzquoBookUtils.azRepeatRegion + regionName);
                    SName repeatDataRegion = sourceBook.getInternalBook().getNameByName("az_DataRegion" + regionName); // todo string literals ergh!
                    // deal with repeat regions, it means getting sent cells that have been set as following : loggedInUser.setSentCells(reportId, region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay)
                    if (repeatRegion != null && repeatDataRegion != null) {
                        int regionHeight = repeatRegion.getRefersToCellRegion().getRowCount();
                        int regionWitdh = repeatRegion.getRefersToCellRegion().getColumnCount();
                        int dataHeight = repeatDataRegion.getRefersToCellRegion().getRowCount();
                        int dataWitdh = repeatDataRegion.getRefersToCellRegion().getColumnCount();
                        // where the data starts in each repeated region
                        int dataStartRow = repeatDataRegion.getRefersToCellRegion().getRow() - repeatRegion.getRefersToCellRegion().getRow();
                        int dataStartCol = repeatDataRegion.getRefersToCellRegion().getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
                        // we can't really do a size comparison as before, we can simply run the region and see where we think there should be repeat reagions in the scope
                        for (int row = 0; row < sourceRegion.getRowCount(); row++) {
                            int repeatRow = row/regionHeight;
                            int rowInRegion = row % regionHeight;
                            for (int col = 0; col < sourceRegion.getColumnCount(); col++) {
                                int colInRegion = col % regionWitdh;
                                int repeatCol = col / regionWitdh;
                                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), regionName + "-" + repeatRow + "-" + repeatCol); // getting each time might be a little inefficient, can optimise if there is a performance problem here
                                if (colInRegion >= dataStartCol && rowInRegion >= dataStartRow
                                        && colInRegion <= dataStartCol + dataWitdh
                                        && rowInRegion <= dataStartRow + dataHeight
                                        && cellsAndHeadingsForDisplay != null){
                                    final List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                                    final DoubleStringPair cellValue = getCellValue(sourceSheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
                                    data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setStringValue(cellValue.string);
                                    // added by Edd, should sort some numbers being ignored!
                                    if (cellValue.number != null){
                                        data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setDoubleValue(cellValue.number);
                                    } else { // I think defaulting to zero is correct here?
                                        data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setDoubleValue(0.0);
                                    }
                                    if (cellValue.string.length() > 0) nonBlankItems++;
                                    items++;
                                }
                            }
                        }
                    }
                    return null;
                } else { // a normal data region. Note that the data region used by a repeat scope should be harmless here as it will return a null on getSentCells, no need to be clever
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), regionName);
                    if (cellsAndHeadingsForDisplay != null) {
                        //needs to be able to handle repeat regions here....
                        List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                        if (data.size() == sourceRegion.getRowCount() && data.get(0).size() == sourceRegion.getColumnCount()) {//ignore region sizes which do not match (e.g. on transaction entries showing past entries)
                            for (int row = 0; row < sourceRegion.getRowCount(); row++) {
                                for (int col = 0; col < sourceRegion.getColumnCount(); col++) {
                                    // note that this function might return a null double but no null string. Perhaps could be mroe consistent? THis area is a bit hacky . . .
                                    final DoubleStringPair cellValue = getCellValue(sourceSheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
                                    data.get(row).get(col).setStringValue(cellValue.string);
                                    // added by Edd, should sort some numbers being ignored!
                                    if (cellValue.number != null){
                                        data.get(row).get(col).setDoubleValue(cellValue.number);
                                    } else { // I think defaulting to zero is correct here?
                                        data.get(row).get(col).setDoubleValue(0.0);
                                    }
                                    if (cellValue.string.length() > 0){
                                        nonBlankItems++;
                                    }

                                    items++;
                                }
                            }
                        }
                    }
                    try {
                        SpreadsheetService.saveData(loggedInUser, regionName, onlineReport.getId(), onlineReport.getReportName());
                    } catch (Exception e) {
                        errorMessage += "- in region " + regionName + " -" +  e.getMessage();
                    }
                }
            }

        }


        return errorMessage + " - " + nonBlankItems + " data items transferred successfully";
    }

    private static String getRegionName(String name) {
        if (name.toLowerCase().startsWith("az_dataregion")) {
            return name.substring("az_dataregion".length()).toLowerCase();
        }
        if (name.toLowerCase().startsWith("az_repeatscope")) {
            return name.substring("az_repeatscope".length()).toLowerCase();
        }
        return null;
    }

    public static String convertSheetToCSV(final String tempFileName, final Sheet sheet) throws Exception {
        boolean transpose = false;
        String fileType = sheet.getInternalSheet().getSheetName();
        if (fileType.toLowerCase().contains("transpose")) {
            transpose = true;
        }
        File temp = File.createTempFile(tempFileName, "." + fileType);
        String tempName = temp.getPath();
        temp.deleteOnExit();
        //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(tempName), "UTF-8"));
        CsvWriter csvW = new CsvWriter(new FileWriter(tempName), '\t');
        csvW.setUseTextQualifier(false);
        convertRangeToCSV(sheet, csvW, null, null, transpose);
        csvW.close();
        return tempName;
    }

    // the function below needs to return a number and string really so azquo knows what to do with the data

    static class DoubleStringPair {
        Double number = null;
        String string = null;
    }

    // EFC note : I'm not completely happy with this function, I'd like to rewrite. TODO

    private static DoubleStringPair getCellValue(Sheet sheet, int r, int c){
        DoubleStringPair toReturn = new DoubleStringPair();
        Range range = Ranges.range(sheet, r, c);
        CellData cellData = range.getCellData();
        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
        //if (colCount++ > 0) bw.write('\t');
        if (cellData != null) {
            String stringValue = "";
            try {
                stringValue = cellData.getFormatText();// I assume means formatted text
                if (dataFormat.toLowerCase().contains("mm-")) {//fix a ZK bug
                    stringValue = stringValue.replace(" ", "-");//crude replacement of spaces in dates with dashes
                }
            } catch (Exception ignored) {
            }
            if (!dataFormat.toLowerCase().contains("m")) {//check that it is not a date or a time
                //if it's a number, remove all formatting
                try {
                    double d = cellData.getDoubleValue();
                    toReturn.number = d;
                    stringValue = d + "";
                    if (stringValue.endsWith(".0")) {
                        stringValue = stringValue.substring(0, stringValue.length() - 2);
                    }
                } catch (Exception ignored) {
                }
            }
            if (stringValue.contains("\"\"") && stringValue.startsWith("\"") && stringValue.endsWith("\"")) {
                //remove spuriouse quote marks
                stringValue = stringValue.substring(1, stringValue.length() - 1).replace("\"\"", "\"");
            }
            toReturn.string = stringValue;
        }
        return toReturn;
    }

    private static void writeCell(Sheet sheet, int r, int c, CsvWriter csvW, Map<String, String> newNames) throws Exception {
        final DoubleStringPair cellValue = getCellValue(sheet, r, c);
        if (newNames != null && newNames.get(cellValue.string) != null) {
             csvW.write(newNames.get(cellValue.string));
        } else {
             csvW.write(cellValue.string.replace("\n","\\\\n").replace("\t","\\\\t"));//nullify the tabs and carriage returns.  Note that the double slash is deliberate so as not to confuse inserted \\n with existing \n
         }

    }


    public static void convertRangeToCSV(final Sheet sheet, final CsvWriter csvW, CellRegion range, Map<String, String> newNames, boolean transpose) throws Exception {

        /*  NewNames here is a very short list which will convert 'next...' into the next available number for that variable, the value being held as the attribute 'next' on that name - e.g. for invoices
        *   the code is now defunct, but was present at SVN version 1161
        *
        * */
        int rows = sheet.getLastRow();
        int maxCol = 0;
        for (int row = 0; row <= sheet.getLastRow(); row++) {
            if (maxCol < sheet.getLastColumn(row)) {
                maxCol = sheet.getLastColumn(row);
            }
        }
        int startRow = 0;
        int startCol = 0;
        if (range != null) {
            startRow = range.getRow();
            startCol = range.getColumn();
            rows = startRow + range.getRowCount() - 1;
            maxCol = startCol + range.getColumnCount() - 1;
        }

        if (!transpose) {
            for (int r = startRow; r <= rows; r++) {
                SRow row = sheet.getInternalSheet().getRow(r);
                if (row != null) {
                    //System.out.println("Excel row " + r);
                    //int colCount = 0;
                    for (int c = startCol; c <= maxCol; c++) {
                        writeCell(sheet, r, c, csvW, newNames);
                    }
                    csvW.endRecord();
                }
            }
        } else {
            for (int c = startCol; c <= maxCol; c++) {
                for (int r = startRow; r <= rows; r++) {
                    writeCell(sheet, r, c, csvW, newNames);
                }
                csvW.endRecord();
            }
        }
    }

    private static String convertDates(String possibleDate) {
        //this routine should probably be generalised to recognise more forms of date
        int slashPos = possibleDate.indexOf("/");
        if (slashPos < 0) return possibleDate;
        Date date;
        try {
            date = ukdflong.parse(possibleDate);// try with time
        } catch (Exception e) {
            try {
                date = ukdf.parse(possibleDate); //try without time
            } catch (Exception e2) {
                return possibleDate;
            }
        }
        return df.format(date);
    }

    public static Map<String, String> uploadChoices(Book book) {
        //this routine extracts the useful information from an uploaded copy of a report.  The report will then be loaded and this information inserted.
        Map<String, String> choices = new HashMap<>();
        for (SName sName : book.getInternalBook().getNames()) {
            String rangeName = sName.getName().toLowerCase();
            if (rangeName.endsWith("chosen")) {
                choices.put(rangeName.substring(0, rangeName.length() - 6), ZKAzquoBookUtils.getSnameCell(sName).getStringValue());
            }
        }
        return choices;
    }
}