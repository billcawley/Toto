package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.zk.ReportExecutor;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.azquo.spreadsheet.zk.BookUtils;
import com.csvreader.CsvWriter;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 13/12/13.
 * <p>
 * Split and then refactored by EFC - does pre processing such as unzipping and extracting csvs from sheets before being sent to the DB server.
 */

public final class ImportService {
    public static final String dbPath = "/databases/";
    public static final String onlineReportsDir = "/onlinereports/";
    public static final String databaseSetupSheetsDir = "/databasesetupsheets/";

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public static String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean isData) throws Exception {
        return importTheFile(loggedInUser,fileName,filePath,attributeNames,isData, false, false);
    }
        // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public static String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean isData, boolean setup, boolean forceReportUpload) throws Exception { // setup just to flag it
        InputStream uploadFile = new FileInputStream(filePath);
        // todo : address provenance on an import
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }
        String tempFile = ImportFileUtilities.tempFileWithoutDecoding(uploadFile, fileName); // ok this takes the file and moves it to a temp directory, required for unzipping - maybe only use then?
        uploadFile.close(); // windows requires this (though windows should not be used in production), perhaps not a bad idea anyway
        String toReturn;
        if (fileName.endsWith(".zip")) {
            fileName = fileName.substring(0, fileName.length() - 4);
            List<File> files = ImportFileUtilities.unZip(tempFile);
            // should be sorting by xls first then size ascending
            files.sort((f1, f2) -> {
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
            });
            // todo - sort the files, small to large xls and xlsx as a group first
            StringBuilder sb = new StringBuilder();
            Iterator<File> fileIterator = files.iterator();
            while (fileIterator.hasNext()) {
                File f = fileIterator.next();
                if (fileIterator.hasNext()) {
                    sb.append(readBookOrFile(loggedInUser, f.getName(), f.getPath(), attributeNames, false, isData, false)).append("\n");
                } else {
                    sb.append(readBookOrFile(loggedInUser, f.getName(), f.getPath(), attributeNames, true, isData, false)); // persist on the last one
                }
            }
            toReturn = sb.toString();
        } else { // vanilla
            toReturn = readBookOrFile(loggedInUser, fileName, tempFile, attributeNames, true, isData, forceReportUpload);
        }
        // hacky way to get the report name so it can be seen on the list. I wonder if this should be removed . . .
        String reportName = null;
        if (!filePath.contains(".zip") && toReturn.startsWith("Report uploaded : ")){
            reportName = toReturn.substring("Report uploaded : ".length());
        }
        if (reportName != null){
            fileName = fileName + " - (" + reportName + ")";
        }
        UploadRecord uploadRecord = new UploadRecord(0, new Date(), loggedInUser.getUser().getBusinessId()
                , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName, setup ? "setup" : "", "", filePath);//should record the error? (in comment)
        UploadRecordDAO.store(uploadRecord);
        if (Objects.equals(toReturn, UPLOADEDBYANOTHERUSER)){ //  .equals to shut intelliJ up. Of little consequence.
            toReturn = UPLOADEDBYANOTHERUSER + " <a href=\"/api/ManageDatabases?uploadAnyway=" + uploadRecord.getId() + "\">Upload Anyway</a>"; // and should there be HTML in here? It will work I guess . . .
        }
        AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        int executePos = toReturn.toLowerCase().indexOf("execute:");
        if (executePos > 0){
            String execute = toReturn.substring(executePos + "execute:".length());
            int executeEnd = execute.toLowerCase().indexOf("executeend");
            toReturn = toReturn.substring(0, executePos) +execute.substring(executeEnd + "executeend".length());
            toReturn += " " + ReportExecutor.runExecute(loggedInUser, execute.substring(0,executeEnd)).toString();
        }
        return toReturn;
    }

    private static String readBookOrFile(LoggedInUser loggedInUser, String fileName, String filePath, List<String> attributeNames, boolean persistAfter, boolean isData, boolean forceReportUpload) throws Exception {
        if (fileName.equals(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
            Book book = Importers.getImporter().imports(new File(filePath), "Report name");
            List<String> notAllowed = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
            if (userSheet != null) {
                int row = 1;
                SName listRegion = book.getInternalBook().getNameByName("az_ListStart");
                if (listRegion != null) {
                    row = listRegion.getRefersToCellRegion().getRow();
                }
                // keep them to use if not set. Should I be updating records instead? I'm not sure.
                Map<String, String> oldPasswordMap = new HashMap<>();
                Map<String, String> oldSaltMap = new HashMap<>();
                List<User> userList = AdminService.getUserListForBusiness(loggedInUser);
                //todo - work out what users DEVELOPERs can upload
                for (User user : userList) {
                    if (user.getId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                        if (loggedInUser.getUser().getBusinessId() != user.getBusinessId() || (loggedInUser.getUser().getStatus().equals("MASTER") && !user.getCreatedBy().equals(loggedInUser.getUser().getEmail()))) {
                            notAllowed.add(user.getEmail());
                        } else {
                            oldPasswordMap.put(user.getEmail(), user.getPassword());
                            oldSaltMap.put(user.getEmail(), user.getSalt());
                            UserDAO.removeById(user);
                        }
                    }
                }
                while (userSheet.getInternalSheet().getCell(row, 0).getStringValue() != null && userSheet.getInternalSheet().getCell(row, 0).getStringValue().length() > 0) {
                    //Email	Name  Password	End Date	Status	Database	Report
                    String user = userSheet.getInternalSheet().getCell(row, 1).getStringValue();
                    String email = userSheet.getInternalSheet().getCell(row, 0).getStringValue();
                    if (notAllowed.contains(email)) rejected.add(email);
                    if (!loggedInUser.getUser().getEmail().equals(email) && !notAllowed.contains(email)) { // leave the logged in user alone!
                        String salt = "";
                        String password = userSheet.getInternalSheet().getCell(row, 2).getStringValue();
                        if (password == null) {
                            password = "";
                        }
                        LocalDate end = LocalDate.now().plusYears(10);
                        try {
                            end = LocalDate.parse(userSheet.getInternalSheet().getCell(row, 3).getStringValue(), CreateExcelForDownloadController.dateTimeFormatter);
                        } catch (Exception ignored) {
                        }
                        String status = userSheet.getInternalSheet().getCell(row, 4).getStringValue();
                        if (!loggedInUser.getUser().isAdministrator()) {
                            status = "USER"; // only admins can set status
                        }
                        // Probably could be factored somewhere
                        if (password.length() > 0) {
                            salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                            password = AdminService.encrypt(password, salt);
                        } else if (oldPasswordMap.get(email) != null) {
                            password = oldPasswordMap.get(email);
                            salt = oldSaltMap.get(email);
                        }
                        Database d = DatabaseDAO.findForNameAndBusinessId(userSheet.getInternalSheet().getCell(row, 5).getStringValue(), loggedInUser.getUser().getBusinessId());
                        OnlineReport or = OnlineReportDAO.findForNameAndBusinessId(userSheet.getInternalSheet().getCell(row, 6).getStringValue(), loggedInUser.getUser().getBusinessId());
                        if (!loggedInUser.getUser().isAdministrator()) { // then I need to check against the session for allowable reports and databases
                            boolean stored = false;
                            if (d != null && or != null) {
                                final Map<String, TypedPair<OnlineReport, Database>> permissionsFromReport = loggedInUser.getPermissionsFromReport();
                                for (TypedPair<OnlineReport, Database> allowedCombo : permissionsFromReport.values()) {
                                    if (allowedCombo.getFirst().getId() == or.getId() && allowedCombo.getSecond().getId() == d.getId()) { // then we can add the user with this info
                                        User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail(), d.getId(), or.getId());
                                        UserDAO.store(user1);
                                        stored = true;
                                        break;
                                    }
                                }
                            }
                            if (!stored) { // default to the current users home menu
                                User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status,
                                        password, salt, loggedInUser.getUser().getEmail(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getReportId());
                                UserDAO.store(user1);
                            }
                        } else {
                            User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail(), d != null ? d.getId() : 0, or != null ? or.getId() : 0);
                            UserDAO.store(user1);
                        }
                    }
                    row++;
                }
            }
            String message = "User file uploaded.";
            if (rejected.size() > 0) {
                message += "  Some users rejected: ";
                for (String reject : rejected) {
                    message += reject + ", ";
                }
            }
            return message;
        } else if (fileName.equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
            Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet schedulesSheet = book.getSheet("ReportSchedules"); // literals not best practice, could it be factored between this and the xlsx file?
            if (schedulesSheet != null) {
                int row = 1;
                SName listRegion = book.getInternalBook().getNameByName("data");
                if (listRegion != null) {
                    row = listRegion.getRefersToCellRegion().getRow();
                }
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
                    Database database1 = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
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
            return readBook(loggedInUser, fileName, filePath, attributeNames, persistAfter, isData, forceReportUpload);
        } else {
            return readPreparedFile(loggedInUser, filePath, fileName, attributeNames, persistAfter, false);
        }
    }

    private static void uploadReport(LoggedInUser loggedInUser, String filePath, String fileName, String reportName) throws Exception {
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = loggedInUser.getDatabase().getId();
        String pathName = loggedInUser.getBusinessDirectory();
        OnlineReport or = OnlineReportDAO.findForNameAndUserId(reportName, loggedInUser.getUser().getId());
        if (or != null) {
            // zap the old one first
            try {
                String oldPath = SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk();
                File old = new File(oldPath);
                old.delete();
            } catch (Exception e) {
                System.out.println("problem deleting old report");
                e.printStackTrace();
            }
            or.setFilename(fileName); // it might have changed, I don't think much else under these circumstances
        } else {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), loggedInUser.getDatabase().getName(), reportName, fileName, ""); // default to ZK now
        }
        OnlineReportDAO.store(or); // store before or.getFilenameForDisk() or the id will be wrong!
        String fullPath = SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk();
        File file = new File(fullPath);
        file.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(fullPath);
        org.apache.commons.io.FileUtils.copyFile(new File(filePath), out);// straight copy of the source
        out.close();
        DatabaseReportLinkDAO.link(databaseId, or.getId());
    }

    public static String UPLOADEDBYANOTHERUSER = "A report with that name has been uploaded by another user.";

    private static String readBook(LoggedInUser loggedInUser, final String fileName, final String tempPath, List<String> attributeNames, boolean persistAfter, boolean isData, boolean forceReportUpload) throws Exception {
        final Book book = Importers.getImporter().imports(new File(tempPath), "Imported");
        String reportName = null;
        SName reportRange = book.getInternalBook().getNameByName("az_ReportName");
        if (reportRange != null) {
            reportName = BookUtils.getSnameCell(reportRange).getStringValue().trim();
        }
        if (reportName != null) {
            if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) && !isData) {
                OnlineReport existing = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                if (existing != null && existing.getUserId() != loggedInUser.getUser().getId() && !forceReportUpload){
                    return UPLOADEDBYANOTHERUSER;
                }
                uploadReport(loggedInUser, tempPath, fileName, reportName);
                return "Report uploaded : " + reportName;
            }
            LoggedInUser loadingUser = new LoggedInUser(loggedInUser);
            OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(loadingUser.getDatabase().getId(), reportName);
            Map<String, String> choices = uploadChoices(book);
            for (String choice : choices.keySet()) {
                SpreadsheetService.setUserChoice(loadingUser.getUser().getId(), choice, choices.get(choice));
            }
            checkEditableSets(book,loggedInUser);
            //String bookPath = spreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + or.getFilenameForDisk();
            final Book reportBook = Importers.getImporter().imports(new File(tempPath), "Report name");
            reportBook.getInternalBook().setAttribute(OnlineController.BOOK_PATH, tempPath);
            reportBook.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
            reportBook.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
            //the uploaded book will already have the repeat sheet repeated - so zap the name
            ReportRenderer.populateBook(reportBook, 0, false);
            return fillDataRangesFromCopy(loggedInUser, book, or);
        }
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("no database set");
        }
        StringBuilder toReturn = new StringBuilder();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            toReturn.append(readSheet(loggedInUser, fileName, sheet, tempPath, attributeNames, sheetNo == book.getNumberOfSheets() - 1 && persistAfter)); // that last conditional means persist on the last one through (if we've been told to persist)
            toReturn.append("\n");
        }
        return toReturn.toString();
    }

    private static void checkEditableSets(Book book, LoggedInUser loggedInUser){
        for(SName sName:book.getInternalBook().getNames()){
            if (sName.getName().toLowerCase().startsWith(ReportRenderer.AZROWHEADINGS)){
                String region = sName.getName().substring(ReportRenderer.AZROWHEADINGS.length());
                Sheet sheet = book.getSheet(sName.getRefersToSheetName());
                String rowHeading = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getStringValue();
                if (rowHeading.toLowerCase().endsWith(" children editable")){
                    String setName = rowHeading.substring(0,rowHeading.length() - " children editable".length()).replace("`","");
                    SName displayName = getNameByName(ReportRenderer.AZDISPLAYROWHEADINGS+region, sheet);
                    if (displayName!=null) {
                        StringBuffer editLine = new StringBuffer();
                        editLine.append("edit:saveset ");
                        editLine.append("`" + setName + "` ");
                        CellRegion dispRegion = displayName.getRefersToCellRegion();
                        for (int rowNo = 0; rowNo < dispRegion.getRowCount(); rowNo++) {
                            String cellVal = "";
                            SCell cell = sheet.getInternalSheet().getCell(dispRegion.getRow() + rowNo, dispRegion.getColumn());
                            try {
                                cellVal = cell.getStringValue();

                            } catch (Exception e) {
                                cellVal = cell.getNumberValue() + "";
                            }
                            editLine.append("`" + cellVal + "`,");
                        }
                        CommonReportUtils.getDropdownListForQuery(loggedInUser, editLine.toString());
                    }

                }
            }
        }


    }
    private static String readSheet(LoggedInUser loggedInUser, String fileName, Sheet sheet, final String tempFileName, List<String> attributeNames, boolean persistAfter) throws Exception {
        boolean transpose = false;
        String sheetName = sheet.getInternalSheet().getSheetName();
        if (sheetName.toLowerCase().contains("transpose")) {
            transpose = true;
        }
        File temp = File.createTempFile(tempFileName, ".csv");
        String tempPath = temp.getPath();
        temp.deleteOnExit();
        //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(tempName), "UTF-8"));
        FileOutputStream fos = new FileOutputStream(tempPath);
        CsvWriter csvW = new CsvWriter(fos, '\t', Charset.forName("UTF-8"));
        csvW.setUseTextQualifier(false);
        ImportFileUtilities.convertRangeToCSV(sheet, csvW, transpose);
        csvW.close();
        fos.close();
        return readPreparedFile(loggedInUser, tempPath, fileName + ":" + sheetName , attributeNames, persistAfter, true);
    }

    private static String LOCALIP = "127.0.0.1";

    private static String readPreparedFile(LoggedInUser loggedInUser, String filePath, String fileName, List<String> attributeNames, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is hte same the question is whether the path refers to this machine or another
            filePath = ImportFileUtilities.copyFileToDatabaseServer(new FileInputStream(filePath), databaseServer.getSftpUrl());
        }
        return RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileName, attributeNames, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
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
            ImportFileUtilities.copyFileToDatabaseServer(sourceFile.getInputStream(), destinationPath);
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

    // for the download, modify and upload the report

    private static String fillDataRangesFromCopy(LoggedInUser loggedInUser, Book sourceBook, OnlineReport onlineReport) {
        String errorMessage = "";
        int saveCount = 0;
        for (SName sName : sourceBook.getInternalBook().getNames()) {
            String name = sName.getName();
            String regionName = getRegionName(name);
            Sheet sheet = sourceBook.getSheet(sName.getRefersToSheetName());
            if (regionName != null) {
                CellRegion sourceRegion = sName.getRefersToCellRegion();
                if (name.toLowerCase().contains(ReportRenderer.AZREPEATSCOPE)) { // then deal with the multiple data regions sent due to this
                    // need to gather associated names for calculations, the region and the data region, code copied and changewd from getRegionRowColForRepeatRegion, it needs to work well for a batch of cells not just one
                    SName repeatRegion = getNameByName(ReportRenderer.AZREPEATREGION + regionName, sheet);
                    SName repeatDataRegion = getNameByName("az_DataRegion" + regionName, sheet); // todo string literals ergh!
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
                            int repeatRow = row / regionHeight;
                            int rowInRegion = row % regionHeight;
                            for (int col = 0; col < sourceRegion.getColumnCount(); col++) {
                                int colInRegion = col % regionWitdh;
                                int repeatCol = col / regionWitdh;
                                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), sName.getRefersToSheetName(),regionName + "-" + repeatRow + "-" + repeatCol); // getting each time might be a little inefficient, can optimise if there is a performance problem here
                                if (colInRegion >= dataStartCol && rowInRegion >= dataStartRow
                                        && colInRegion <= dataStartCol + dataWitdh
                                        && rowInRegion <= dataStartRow + dataHeight
                                        && cellsAndHeadingsForDisplay != null) {
                                    final List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                                    final TypedPair<Double, String> cellValue = ImportFileUtilities.getCellValue(sheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
                                    data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setNewStringValue(cellValue.getSecond());
                                    // added by Edd, should sort some numbers being ignored!
                                    if (cellValue.getFirst() != null) {
                                        data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setNewDoubleValue(cellValue.getFirst());
                                    } else { // I think defaulting to zero is correct here?
                                        data.get(rowInRegion - dataStartRow).get(colInRegion - dataStartCol).setNewDoubleValue(0.0);
                                    }
                                }
                            }
                        }
                    }
                    return null;
                } else { // a normal data region. Note that the data region used by a repeat scope should be harmless here as it will return a null on getSentCells, no need to be clever
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), sName.getRefersToSheetName(), regionName);
                    if (cellsAndHeadingsForDisplay != null) {
                        //needs to be able to handle repeat regions here....
                        List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                        //NOTE - the import sheet may contain blank lines at the bottom and/or blank columns at the right where the original data region exceeds the size of the data found (row/column headings are sets).  This is acceptable
                        // TODO - WE SHOULD CHECK THAT THE HEADINGS MATCH
                        if (data.size() > 0 && data.size() <= sourceRegion.getRowCount() && data.get(0).size() <= sourceRegion.getColumnCount()) {//ignore region sizes which do not match (e.g. on transaction entries showing past entries)
                            //work on the original data size, not the uploaded size with the blank lines
                            for (int row = 0; row < data.size(); row++) {
                                for (int col = 0; col < data.get(0).size(); col++) {
                                    // note that this function might return a null double but no null string. Perhaps could be mroe consistent? THis area is a bit hacky . . .
                                    final TypedPair<Double, String> cellValue = ImportFileUtilities.getCellValue(sheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
                                    data.get(row).get(col).setNewStringValue(cellValue.getSecond());
                                    // added by Edd, should sort some numbers being ignored!
                                    if (cellValue.getFirst() != null) {
                                        data.get(row).get(col).setNewDoubleValue(cellValue.getFirst());
                                    } else { // I think defaulting to zero is correct here?
                                        data.get(row).get(col).setNewDoubleValue(0.0);
                                    }
                                }
                            }
                        }
                        //AND THE ROW HEADINGS IF EDITABLE.
                    }
                    try {
                        final String result = SpreadsheetService.saveData(loggedInUser, onlineReport.getId(), onlineReport.getReportName(), sName.getRefersToSheetName(), regionName);
                        if (!result.startsWith("true")) {// unlikely to fail here I think but catch it anyway . . .

                            errorMessage += "- in region " + regionName + " -" + result;
                        }else{
                            try{
                                saveCount += Integer.parseInt(result.substring(5));  //count follows the word 'true'
                            }catch(Exception e){

                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMessage += "- in region " + regionName + " -" + e.getMessage();
                    }
                }
            }
        }
        return errorMessage + " - " + saveCount + " data items amended successfully";
    }

    static SName getNameByName(String name, Sheet sheet){
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        if (toReturn != null){
            return toReturn;

        }
        // should we check the formula refers to the sheet here? I'm not sure. Applies will have been checked for above.
        return sheet.getBook().getInternalBook().getNameByName(name);

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

    // as in write a cell to csv

    private static Map<String, String> uploadChoices(Book book) {
        //this routine extracts the useful information from an uploaded copy of a report.  The report will then be loaded and this information inserted.
        Map<String, String> choices = new HashMap<>();
        for (SName sName : book.getInternalBook().getNames()) {
            String rangeName = sName.getName().toLowerCase();
            if (rangeName.endsWith("chosen")) {
                //there is probably a more elegant solution than this....
                choices.put(rangeName.substring(0, rangeName.length() - 6), ImportFileUtilities.getCellValue(book.getSheet(sName.getRefersToSheetName()),sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getSecond());
            }
        }
        return choices;
    }
}