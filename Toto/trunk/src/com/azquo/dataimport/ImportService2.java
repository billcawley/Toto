package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportExecutor;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.csvreader.CsvWriter;
import org.apache.commons.io.FileUtils;
/*import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;*/
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.util.AreaReference;
import org.zkoss.poi.ss.util.CellReference;
import org.zkoss.poi.xssf.usermodel.XSSFName;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 13/12/13.
 * <p>
 * Split and then refactored by EFC - does pre processing such as unzipping and extracting csvs from sheets before being sent to the DB server.
 */

public final class ImportService2 {
    public static final String dbPath = "/databases/";
    public static final String onlineReportsDir = "/onlinereports/";
    public static final String databaseSetupSheetsDir = "/databasesetupsheets/";

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    // EFC - 01/08/2018
    // this top one just used by backup seervice for the mo, it doesn't care about the feedback on whether data has changed or not
    public static String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, boolean isData) throws Exception {
        return importTheFile(loggedInUser, fileName, filePath, null, isData, false,  true, new AtomicBoolean());
    }

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public static String importTheFile(LoggedInUser loggedInUser, String fileName, String filePath, Map<String, String> inheritedFileNameParams, boolean isData, boolean setup, boolean isLast, AtomicBoolean dataChanged) throws Exception { // setup just to flag it
        Map<String, String> fileNameParams = inheritedFileNameParams != null ? new HashMap<>(inheritedFileNameParams) : new HashMap<>(); // copy, it might be modified
        addFileNameParametersToMap(fileName, fileNameParams);
        InputStream uploadFile = new FileInputStream(filePath);
        // todo : address provenance on an import
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }

        File tempFile = ImportFileUtilities2.tempFileWithoutDecoding(uploadFile, fileName); // ok this takes the file and moves it to a temp directory, required for unzipping - maybe only use then?
        uploadFile.close(); // windows requires this (though windows should not be used in production), perhaps not a bad idea anyway
        String toReturn;
        if (fileName.endsWith(".zip") || fileName.endsWith(".7z")) {
            ZipUtil.explode(tempFile);
            // after exploding the original file is replaced with a directory
            File zipDir = new File(tempFile.getPath());
            List<File> files = new ArrayList<>(FileUtils.listFiles(zipDir, null, true));
            // should be sorting by xls first then size ascending
            files.sort((f1, f2) -> {
                //note - this does not sort zip files.... should they be first?
                if ((f1.getName().endsWith(".xls") || f1.getName().endsWith(".xlsx")) && (!f2.getName().endsWith(".xls") && !f2.getName().endsWith(".xlsx"))) { // one is xls, the other is not
                    return -1;
                }
                if ((f2.getName().endsWith(".xls") || f2.getName().endsWith(".xlsx")) && (!f1.getName().endsWith(".xls") && !f1.getName().endsWith(".xlsx"))) { // otehr way round
                    return 1;
                }
                //now standard string order
                return  f1.getName().compareTo(f2.getName());
            });
            StringBuilder sb = new StringBuilder();
            Iterator<File> fileIterator = files.iterator();
            while (fileIterator.hasNext()) {
                File f = fileIterator.next();
                if (f.getName().endsWith(".zip")) {
                    // added fileNameParams will be sorted internally - recursive call
                    sb.append(importTheFile(loggedInUser, f.getName(), f.getPath(), fileNameParams, isData, setup, !fileIterator.hasNext(), dataChanged));
                } else {
                    Map<String, String> mapCopy = new HashMap<>(fileNameParams); // must copy as the map might get changed by each file in the zip
                    addFileNameParametersToMap(f.getName(), mapCopy);
                    if (fileIterator.hasNext()) {
                        sb.append(readBookOrFile(loggedInUser, f.getName(), mapCopy, f.getPath(), false, isData, dataChanged));
                    } else {
                        sb.append(stripTempSuffix(fileName) + ": " + readBookOrFile(loggedInUser, f.getName(), mapCopy, f.getPath(), isLast, isData, dataChanged)); // persist on the last one
                    }
                }
            }
            toReturn = sb.toString();
        } else { // vanilla
            toReturn = readBookOrFile(loggedInUser, fileName, fileNameParams, tempFile.getPath(), true, isData, dataChanged);
        }
        // hacky way to get the report name so it can be seen on the list. I wonder if this should be removed . . .
        String reportName = null;
        if (!filePath.contains(".zip") && !filePath.contains(".7z") && toReturn.startsWith("Report uploaded : ")) {
            reportName = toReturn.substring("Report uploaded : ".length());
        }
        if (reportName != null) {
            fileName = fileName + " - (" + reportName + ")";
        }
        UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName, setup ? "setup" : "", "", filePath);//should record the error? (in comment)
        // todo - uncomment when pasting back over!!
        System.out.println("********");
        System.out.println("********");
        System.out.println("********");
        System.out.println("********");
        System.out.println("not storing upload record (should only happen on ImportService2 test)");
        System.out.println("********");
        System.out.println("********");
        System.out.println("********");
        System.out.println("********");
        //UploadRecordDAO.store(uploadRecord);
        AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        int executePos = toReturn.toLowerCase().indexOf("execute:");
        if (executePos > 0) {
            String execute = toReturn.substring(executePos + "execute:".length());
            int executeEnd = execute.toLowerCase().indexOf("executeend");
            toReturn = toReturn.substring(0, executePos) + execute.substring(executeEnd + "executeend".length());
            toReturn += " " + ReportExecutor.runExecute(loggedInUser, execute.substring(0, executeEnd)).toString();
        }
        return toReturn;
    }

    private static String readBookOrFile(LoggedInUser loggedInUser, String fileName, Map<String, String> fileNameParameters, String filePath, boolean persistAfter, boolean isData, AtomicBoolean dataChanged) throws Exception {
        String toReturn = "";
        if (fileName.startsWith(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
            FileInputStream fs = new FileInputStream(new File(filePath));
            OPCPackage opcPackage = OPCPackage.open(fs);
            XSSFWorkbook book = new XSSFWorkbook(opcPackage);
            //Book book = Importers.getImporter().imports(new File(filePath), "Report name");
            List<String> notAllowed = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
            if (userSheet != null) {
                dataChanged.set(true);
                int row = 1;
                XSSFName listRegion = book.getName(ReportRenderer.AZLISTSTART);
//                SName listRegion = book.getInternalBook().getNameByName(ReportRenderer.AZLISTSTART);
                if (listRegion != null && listRegion.getRefersToFormula() != null) {
                    AreaReference aref = new AreaReference(listRegion.getRefersToFormula());
                        row = aref.getFirstCell().getRow();
                } else {
                    if ("Email/logon".equalsIgnoreCase(userSheet.getRow(4).getCell( 0).getStringCellValue())) {
                        row = 5;
                    } else {
                        throw new Exception("az_ListStart not found, typically it is A6");
                    }
                }
                // keep them to use if not set. Should I be updating records instead? I'm not sure.
                Map<String, String> oldPasswordMap = new HashMap<>();
                Map<String, String> oldSaltMap = new HashMap<>();
                List<User> userList = UserDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()); // don't use the admin call, it will just return for this user, we want all for the business so we can check not allowed
                //todo - work out what users DEVELOPERs can upload
                for (User user : userList) {
                    if (user.getId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                        if (loggedInUser.getUser().getBusinessId() != user.getBusinessId()
                                || (loggedInUser.getUser().getStatus().equals("MASTER") && !user.getCreatedBy().equals(loggedInUser.getUser().getEmail()))
                                || user.getStatus().equals(User.STATUS_ADMINISTRATOR)) { // don't zap admins
                            notAllowed.add(user.getEmail());
                        } else {
                            oldPasswordMap.put(user.getEmail(), user.getPassword());
                            oldSaltMap.put(user.getEmail(), user.getSalt());
                            UserDAO.removeById(user);
                        }
                    }
                }
                while (userSheet.getRow(row).getCell( 0).getStringCellValue() != null && userSheet.getRow(row).getCell( 0).getStringCellValue().length() > 0) {
                    //Email	Name  Password	End Date	Status	Database	Report
                    String user = userSheet.getRow(row).getCell( 1).getStringCellValue().trim();
                    String email = userSheet.getRow(row).getCell( 0).getStringCellValue().trim();
                    if (notAllowed.contains(email)) rejected.add(email);
                    if (!loggedInUser.getUser().getEmail().equals(email) && !notAllowed.contains(email)) { // leave the logged in user alone!
                        String salt = "";
                        String password = userSheet.getRow(row).getCell( 2).getStringCellValue();
                        String selections = userSheet.getRow(row).getCell( 7).getStringCellValue();
                        if (password == null) {
                            password = "";
                        }
                        LocalDate end = LocalDate.now().plusYears(10);
                        try {
                            end = LocalDate.parse(userSheet.getRow(row).getCell( 3).getStringCellValue(), CreateExcelForDownloadController.dateTimeFormatter);
                        } catch (Exception ignored) {
                        }
                        String status = userSheet.getRow(row).getCell( 4).getStringCellValue();
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
                        if (password.isEmpty()){
                            throw new Exception("Blank password for " + email);
                        }
                        Database d = DatabaseDAO.findForNameAndBusinessId(userSheet.getRow(row).getCell( 5).getStringCellValue(), loggedInUser.getUser().getBusinessId());
                        OnlineReport or = OnlineReportDAO.findForNameAndBusinessId(userSheet.getRow(row).getCell( 6).getStringCellValue(), loggedInUser.getUser().getBusinessId());
                        if (!status.equalsIgnoreCase(User.STATUS_ADMINISTRATOR) && !status.equalsIgnoreCase(User.STATUS_DEVELOPER) && or == null){
                            throw new Exception("Unable to find report " + userSheet.getRow(row).getCell( 6).getStringCellValue());
                        }
                        // todo - master and user types need to check for a report and error if it's not there
                        if (!loggedInUser.getUser().isAdministrator()) { // then I need to check against the session for allowable reports and databases
                            boolean stored = false;
                            if (d != null && or != null) {
                                final Map<String, TypedPair<OnlineReport, Database>> permissionsFromReport = loggedInUser.getPermissionsFromReport();
                                for (TypedPair<OnlineReport, Database> allowedCombo : permissionsFromReport.values()) {
                                    if (allowedCombo.getFirst().getId() == or.getId() && allowedCombo.getSecond().getId() == d.getId()) { // then we can add the user with this info
                                        User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail(), d.getId(), or.getId(), selections);
                                        UserDAO.store(user1);
                                        stored = true;
                                        break;
                                    }
                                }
                            }
                            if (!stored) { // default to the current users home menu
                                User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status,
                                        password, salt, loggedInUser.getUser().getEmail(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getReportId(), selections);
                                UserDAO.store(user1);
                            }
                        } else {
                            User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail(), d != null ? d.getId() : 0, or != null ? or.getId() : 0, selections);
                            UserDAO.store(user1);
                        }
                    }
                    row++;
                }
            }
            StringBuilder message = new StringBuilder("User file uploaded.");
            if (rejected.size() > 0) {
                message.append("  Some users rejected: ");
                for (String reject : rejected) {
                    message.append(reject).append(", ");
                }
            }
            return message.toString();
        } else if (fileName.equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
            FileInputStream fs = new FileInputStream(new File(filePath));
            OPCPackage opcPackage = OPCPackage.open(fs);
            XSSFWorkbook book = new XSSFWorkbook(opcPackage);
            //Book book = Importers.getImporter().imports(new File(fileName), "Report name");
            Sheet schedulesSheet = book.getSheet("ReportSchedules"); // literals not best practice, could it be factored between this and the xlsx file?
            if (schedulesSheet != null) {
                dataChanged.set(true);
                int row = 1;
//                SName listRegion = book.getInternalBook().getNameByName("data");
                XSSFName listRegion = book.getName("data");
                if (listRegion != null && listRegion.getRefersToFormula() != null) {
                    AreaReference aref = new AreaReference(listRegion.getRefersToFormula());
                        row = aref.getFirstCell().getRow();
                }
                
                final List<ReportSchedule> reportSchedules = AdminService.getReportScheduleList(loggedInUser);
                for (ReportSchedule reportSchedule : reportSchedules) {
                    ReportScheduleDAO.removeById(reportSchedule);
                }
                while (schedulesSheet.getRow(row).getCell( 0).getStringCellValue() != null && schedulesSheet.getRow(row).getCell( 0).getStringCellValue().length() > 0) {
                    String period = schedulesSheet.getRow(row).getCell( 0).getStringCellValue();
                    String recipients = schedulesSheet.getRow(row).getCell( 1).getStringCellValue();
                    LocalDateTime nextDue = LocalDateTime.now();
                    try {
                        nextDue = LocalDateTime.parse(schedulesSheet.getRow(row).getCell( 2).getStringCellValue(), CreateExcelForDownloadController.dateTimeFormatter);
                    } catch (Exception ignored) {
                    }
                    String database = schedulesSheet.getRow(row).getCell( 3).getStringCellValue();
                    Database database1 = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
                    if (database1 != null) {
                        String report = schedulesSheet.getRow(row).getCell( 4).getStringCellValue();
                        OnlineReport onlineReport = OnlineReportDAO.findForDatabaseIdAndName(database1.getId(), report);
                        if (onlineReport != null) {
                            String type = schedulesSheet.getRow(row).getCell( 5).getStringCellValue();
                            String parameters = schedulesSheet.getRow(row).getCell( 6).getStringCellValue();
                            String emailSubject = schedulesSheet.getRow(row).getCell( 7).getStringCellValue();
                            ReportSchedule rs = new ReportSchedule(0, period, recipients, nextDue, database1.getId(), onlineReport.getId(), type, parameters, emailSubject);
                            ReportScheduleDAO.store(rs);
                        }
                    }
                    row++;
                }
            }
            return "Report schedules file uploaded"; // I hope that's what it is looking for.
        } else if (fileName.contains(".xls")) { // normal. I'm not entirely sure the code for users etc above should be in this file, maybe a different importer?
            toReturn = readBook(loggedInUser, fileName, fileNameParameters, filePath, persistAfter, isData, dataChanged);
        } else {
            toReturn = readPreparedFile(loggedInUser, filePath, fileName, fileNameParameters, persistAfter, false, dataChanged);
        }
        int errorPos = toReturn.toLowerCase().lastIndexOf("exception:");
        if (errorPos > 0) {
            toReturn = toReturn.substring(errorPos + 10).trim();
        }
        return toReturn;
    }

    private static void uploadReport(LoggedInUser loggedInUser, String filePath, String fileName, String reportName, String identityCell, AtomicBoolean dataChanged) throws Exception {
        dataChanged.set(true);
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = loggedInUser.getDatabase().getId();
        String pathName = loggedInUser.getBusinessDirectory();
        OnlineReport or = OnlineReportDAO.findForNameAndUserId(reportName, loggedInUser.getUser().getId());
        if (or != null) {
            // zap the old one first
            try {
                Files.deleteIfExists(Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk()));
            } catch (Exception e) {
                System.out.println("problem deleting old report");
                e.printStackTrace();
            }
            or.setFilename(fileName); // it might have changed, I don't think much else under these circumstances
            or.setIdentityCell(identityCell);
        } else {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), loggedInUser.getDatabase().getName(), reportName, fileName, "", identityCell); // default to ZK now
        }
        OnlineReportDAO.store(or); // store before or.getFilenameForDisk() or the id will be wrong!
        Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk());
        Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
        Files.copy(Paths.get(filePath), fullPath); // and copy
        DatabaseReportLinkDAO.link(databaseId, or.getId());
    }

    private static String readBook(LoggedInUser loggedInUser, final String fileName, final Map<String, String> fileNameParameters, final String tempPath, boolean persistAfter, boolean isData, AtomicBoolean dataChanged) throws Exception {
        XSSFWorkbook book;
        try {
            long time = System.currentTimeMillis();
            FileInputStream fs = new FileInputStream(new File(tempPath));
            OPCPackage opcPackage = OPCPackage.open(fs);
            book = new XSSFWorkbook(opcPackage);
            //book = Importers.getImporter().imports(new File(tempPath), "Imported");
            System.out.println("millis to read an Excel file for import new way" +
                    " " + (System.currentTimeMillis() - time));
        } catch (Exception e) {
            e.printStackTrace();
            return stripTempSuffix(fileName) + ": Import error - " + e.getMessage();
        }
        String reportName = null;
        boolean isImportTemplate = false;
        XSSFName reportRange = book.getName(ReportRenderer.AZREPORTNAME);
        if (reportRange == null) {
            reportRange = book.getName(ReportRenderer.AZIMPORTNAME);
            isImportTemplate = true;
        }
        if (reportRange != null) {
            CellReference xssfNameCell = BookUtils.getXSSFNameCell(reportRange);
            reportName = book.getSheetAt(reportRange.getSheetIndex()).getRow(xssfNameCell.getRow()).getCell(xssfNameCell.getCol()).getStringCellValue();
        }
        if (reportName != null) {
            // todo sort duplicates later
            CellReference xssfNameCell = BookUtils.getXSSFNameCell(reportRange);
            if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) && !isData) {
                String identityCell = null;
                if (isImportTemplate) {
                    //identity cell in Excel format
                    identityCell = "" + (char) (xssfNameCell.getCol() + 65) + (xssfNameCell.getRow() + 1);
                }

                uploadReport(loggedInUser, tempPath, fileName, reportName, identityCell, dataChanged);
                if (isImportTemplate) {
                    return "Import uploaded : " + reportName;
                }

                return "Report uploaded : " + reportName;
            }
            OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName);
            Map<String, String> choices = uploadChoices(book);
            for (Map.Entry<String, String> choiceAndValue : choices.entrySet()) {
                SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceAndValue.getKey(), choiceAndValue.getValue());
            }
            checkEditableSets(book, loggedInUser);
            //String bookPath = spreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + or.getFilenameForDisk();
            final Book reportBook = Importers.getImporter().imports(new File(tempPath), "Report name");
            reportBook.getInternalBook().setAttribute(OnlineController.BOOK_PATH, tempPath);
            reportBook.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
            reportBook.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
            //the uploaded book will already have the repeat sheet repeated - so zap the name
            ReportRenderer.populateBook(reportBook, 0, false);
            // this REALLY should have been commented - the load before will populate the logged in users sent cells correctly, that's
            // why it was done
            return fillDataRangesFromCopy(loggedInUser, Importers.getImporter().imports(new File(tempPath), "Imported"), or);
        }
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("no database set");
        }
        StringBuilder toReturn = new StringBuilder();
        Map<String, String> knownValues = new HashMap<String, String>();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            toReturn.append(readSheet(loggedInUser, fileName, fileNameParameters, sheet, tempPath+"poi", knownValues, sheetNo == book.getNumberOfSheets() - 1 && persistAfter, dataChanged)); // that last conditional means persist on the last one through (if we've been told to persist)
            toReturn.append("\n");
        }
        return toReturn.toString();
    }

    private static void checkEditableSets(XSSFWorkbook book, LoggedInUser loggedInUser) {
        for (int i = 0; i <  book.getNumberOfNames(); i++) {
            XSSFName sName = book.getNameAt(i);
            if (sName.getNameName().toLowerCase().startsWith(ReportRenderer.AZROWHEADINGS)) {
                String region = sName.getNameName().substring(ReportRenderer.AZROWHEADINGS.length());
                Sheet sheet = book.getSheet(sName.getSheetName());
                CellReference xssfNameCell = BookUtils.getXSSFNameCell(sName);
                String rowHeading = ImportFileUtilities2.getCellValue(sheet, xssfNameCell.getRow(), xssfNameCell.getCol()).getSecond();
                if (rowHeading.toLowerCase().endsWith(" children editable")) {
                    String setName = rowHeading.substring(0, rowHeading.length() - " children editable".length()).replace("`", "");
                    XSSFName displayName = getNameByName(ReportRenderer.AZDISPLAYROWHEADINGS + region, sheet, book);
                    if (displayName != null) {
                        StringBuffer editLine = new StringBuffer();
                        editLine.append("edit:saveset ");
                        editLine.append("`" + setName + "` ");
                        if (displayName.getRefersToFormula() != null){
                            AreaReference aref = new AreaReference(displayName.getRefersToFormula());
                            int rowCount = aref.getLastCell().getRow() - aref.getLastCell().getRow();
                            rowCount++; // don't want it to be 0!
                            for (int rowNo = 0; rowNo <  rowCount; rowNo++) {
                                editLine.append("`" + ImportFileUtilities2.getCellValue(sheet, aref.getFirstCell().getRow() + rowNo, aref.getFirstCell().getCol()).getSecond() + "`,");
                            }
                        }
                        CommonReportUtils.getDropdownListForQuery(loggedInUser, editLine.toString());
                    }

                }
            }
        }
    }
/*

    private static void rangeToCSV(Sheet sheet, CellRegion region, Map<String, String> knownNames, CsvWriter csvW) throws Exception {
        for (int rNo = region.getRow(); rNo < region.getRow() + region.getRowCount(); rNo++) {
            SRow row = sheet.getInternalSheet().getRow(rNo);
            if (row != null) {
                //System.out.println("Excel row " + r);
                //int colCount = 0;
                for (int cNo = region.getColumn(); cNo < region.getColumn() + region.getColumnCount(); cNo++) {
                    String val = ImportFileUtilities2.getCellValue(sheet, rNo, cNo).getSecond();
                    if (knownNames != null) {
                        for (Map.Entry<String, String> knownNameValue : knownNames.entrySet()) {
                            val = val.replaceAll("`" + knownNameValue.getKey() + "`", knownNameValue.getValue());
                        }

                    }
                    csvW.write(val.replace("\n", "\\\\n").replace("\t", "\\\\t"));//nullify the tabs and carriage returns.  Note that the double slash is deliberate so as not to confuse inserted \\n with existing \n

                }
                csvW.endRecord();
            }
        }

    }*/


    private static String readSheet(LoggedInUser loggedInUser, String fileName, Map<String, String> fileNameParameters, Sheet sheet, final String tempFileName, Map<String, String> knownValues, boolean persistAfter, AtomicBoolean dataChanged) {
        String sheetName = sheet.getSheetName();
//        String toReturn = "";
        try {
            /*
            List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(loggedInUser.getDatabase().getId());
            for (OnlineReport report : reports) {
                String cell = report.getIdentityCell();
                if (cell != null && cell.length() > 0) {
                    int row = Integer.parseInt(cell.substring(1)) - 1;
                    int col = cell.charAt(0) - 65;
                    if (ImportFileUtilities2.getCellValue(sheet, row).getCell( col).getSecond().equals(report.getReportName())) {
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + report.getFilenameForDisk();
                        final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                        book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                        book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                        book.getInternalBook().setAttribute(OnlineController.REPORT_ID, report.getId());
                        Sheet template = book.getSheetAt(0);
                        //FIRST  glean information from range names
                        List<SName> namesForTemplate = BookUtils.getNamesForSheet(template);
                        List<SName> columnHeadings = new ArrayList<>();
                        for (SName name : namesForTemplate) {
                            if (name.getRefersToCellRegion().getRowCount() == 1 && name.getRefersToCellRegion().getColumnCount() == 1) {
                                int rowNo = name.getRefersToCellRegion().getRow();
                                int colNo = name.getRefersToCellRegion().getColumn();
                                knownValues.put(name.getName(), ImportFileUtilities2.getCellValue(sheet, rowNo, colNo).getSecond());
                            }
                        }
                        //now copy across the column headings in full
                        for (SName name : namesForTemplate) {

                            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZCOLUMNHEADINGS)) {
                                SName dataRegion = getNameByName(ReportRenderer.AZDATAREGION + name.getName().substring(ReportRenderer.AZCOLUMNHEADINGS.length()), template);
                                if (dataRegion != null) {
                                    Path tempFilePath = Paths.get(tempFileName); // where it was dumped after upload
                                    Path newTempFile = Files.createTempFile(tempFilePath.getParent(), "from" + tempFilePath.getFileName(), ".csv"); // now in the same place make teh csvs from that file
//                                File temp = File.createTempFile(tempFileName, ".csv");
//                                String tempPath = temp.getPath();
//                                temp.deleteOnExit();
//                                FileOutputStream fos = new FileOutputStream(tempPath);
                                    // will it be ok with the file created already?
                                    CsvWriter csvW = new CsvWriter(newTempFile.toString(), '\t', Charset.forName("UTF-8"));
                                    csvW.setUseTextQualifier(false);
                                    rangeToCSV(template, name.getRefersToCellRegion(), knownValues, csvW);
                                    rangeToCSV(sheet, dataRegion.getRefersToCellRegion(), null, csvW);
                                    csvW.close();
//                                fos.close();
                                    try {
                                        toReturn += fileName + ": " + readPreparedFile(loggedInUser, newTempFile.toString(), fileName + ":" + sheetName, fileNameParameters, persistAfter, true, dataChanged);
                                    } catch (Exception e) {
                                        throw e;
                                    }
                                    Files.delete(newTempFile);
                                }
                            }
                        }
                        return toReturn;
g
                    }
                }
            }*/


            File temp = File.createTempFile(tempFileName + sheetName, ".csv");
            String tempPath = temp.getPath();
            temp.deleteOnExit();
            //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(tempName), "UTF-8"));
            FileOutputStream fos = new FileOutputStream(tempPath);
            CsvWriter csvW = new CsvWriter(fos, '\t', Charset.forName("UTF-8"));
            csvW.setUseTextQualifier(false);
            ImportFileUtilities2.convertRangeToCSV(sheet, csvW);
            csvW.close();
            fos.close();
            return stripTempSuffix(fileName) + ": " + readPreparedFile(loggedInUser, tempPath, fileName + ":" + sheetName, fileNameParameters, persistAfter, true, dataChanged);
        } catch (Exception e) {
            e.printStackTrace();
            return "\n" + stripTempSuffix(fileName) + ": " + e.getMessage();
        }

    }

    private static String LOCALIP = "127.0.0.1";

    // todo - address whether that last variable is the right way to do things!
    private static String readPreparedFile(LoggedInUser loggedInUser, String filePath, String fileName, Map<String, String> fileNameParameters, boolean persistAfter, boolean isSpreadsheet, AtomicBoolean dataChanged) throws Exception {
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            filePath = ImportFileUtilities2.copyFileToDatabaseServer(new FileInputStream(filePath), databaseServer.getSftpUrl());
        }
        String s = "no db upload, testing";
//        String s = RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileName, fileNameParameters, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
        if (s.startsWith(Constants.DATABASE_UNMODIFIED)){
            s = s.substring(Constants.DATABASE_UNMODIFIED.length());
        } else {
            dataChanged.set(true);
        }
        return s;
    }

    // EFC note - I don't like this

    public static String uploadImage(LoggedInUser loggedInUser, MultipartFile sourceFile, String fileName) throws Exception {
        String success = "image uploaded successfully";
        String sourceName = sourceFile.getOriginalFilename();
        String suffix = sourceName.substring(sourceName.indexOf("."));
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        String pathOffset = loggedInUser.getDatabase().getPersistenceName() + "/images/" + fileName + suffix;
        String destinationPath = SpreadsheetService.getHomeDir() + dbPath + pathOffset;
        if (databaseServer.getIp().equals(LOCALIP)) {
            Path fullPath = Paths.get(destinationPath);
            Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
            Files.copy(sourceFile.getInputStream(), fullPath); // and copy
        } else {
            destinationPath = databaseServer.getSftpUrl() + pathOffset;
            ImportFileUtilities2.copyFileToDatabaseServer(sourceFile.getInputStream(), destinationPath);
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
    // todo - can we convert to apache poi?
    private static String fillDataRangesFromCopy(LoggedInUser loggedInUser, Book sourceBook, OnlineReport onlineReport) {
        String errorMessage = "";
        int saveCount = 0;
        for (SName sName : sourceBook.getInternalBook().getNames()) {
            String name = sName.getName();
            String regionName = getRegionName(name);
            org.zkoss.zss.api.model.Sheet sheet = sourceBook.getSheet(sName.getRefersToSheetName());
            if (regionName != null) {
                CellRegion sourceRegion = sName.getRefersToCellRegion();
                if (name.toLowerCase().contains(ReportRenderer.AZREPEATSCOPE)) { // then deal with the multiple data regions sent due to this
                    // need to gather associated names for calculations, the region and the data region, code copied and changewd from getRegionRowColForRepeatRegion, it needs to work well for a batch of cells not just one
                    SName repeatRegion = getNameByName(ReportRenderer.AZREPEATREGION + regionName, sheet);
                    SName repeatDataRegion = getNameByName(ReportRenderer.AZDATAREGION + regionName, sheet);
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
                                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(onlineReport.getId(), sName.getRefersToSheetName(), regionName + "-" + repeatRow + "-" + repeatCol); // getting each time might be a little inefficient, can optimise if there is a performance problem here
                                if (colInRegion >= dataStartCol && rowInRegion >= dataStartRow
                                        && colInRegion <= dataStartCol + dataWitdh
                                        && rowInRegion <= dataStartRow + dataHeight
                                        && cellsAndHeadingsForDisplay != null) {
                                    final List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                                    final TypedPair<Double, String> cellValue = ImportFileUtilities2.getCellValue(sheet, sourceRegion.getRow() + row, sourceRegion.getColumn() + col);
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
                                    final TypedPair<Double, String> cellValue = ImportFileUtilities2.getCellValue(sheet, sourceRegion.getRow() + row,sourceRegion.getColumn() + col);
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
                        } else {
                            try {
                                saveCount += Integer.parseInt(result.substring(5));  //count follows the word 'true'
                            } catch (Exception e) {

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

    private static XSSFName getNameByName(String name, Sheet sheet, XSSFWorkbook book) {
        List<XSSFName> names = new ArrayList<>();
        for (int i = 0; i <  book.getNumberOfNames(); i++) {
            XSSFName name1 = book.getNameAt(i);
            if (name1.getNameName().equalsIgnoreCase(name)){
                if (name1.getSheetName().equals(sheet.getSheetName())){
                    return  name1;
                }
                names.add(name1);
            }
        }
        if (!names.isEmpty()){
            return names.get(0);
        }
        // should we check the formula refers to the sheet here? I'm not sure. Applies will have been checked for above.
        return null;
    }


    private static SName getNameByName(String name, org.zkoss.zss.api.model.Sheet sheet) {
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        if (toReturn != null) {
            return toReturn;
        }
        // should we check the formula refers to the sheet here? I'm not sure. Applies will have been checked for above.
        return sheet.getBook().getInternalBook().getNameByName(name);

    }
    private static String getRegionName(String name) {
        if (name.toLowerCase().startsWith(ReportRenderer.AZDATAREGION)) {
            return name.substring(ReportRenderer.AZDATAREGION.length()).toLowerCase();
        }
        if (name.toLowerCase().startsWith(ReportRenderer.AZREPEATSCOPE)) {
            return name.substring(ReportRenderer.AZREPEATSCOPE.length()).toLowerCase();
        }
        return null;
    }

    // as in write a cell to csv

    private static Map<String, String> uploadChoices(XSSFWorkbook book) {
        //this routine extracts the useful information from an uploaded copy of a report.  The report will then be loaded and this information inserted.
        Map<String, String> choices = new HashMap<>();
        for (int i = 0; i <  book.getNumberOfNames(); i++) {
            XSSFName sName = book.getNameAt(i);
            String rangeName = sName.getNameName().toLowerCase();
            if (rangeName.endsWith("chosen")) {
                //there is probably a more elegant solution than this....
                CellReference xssfNameCell = BookUtils.getXSSFNameCell(sName);
                choices.put(rangeName.substring(0, rangeName.length() - 6), ImportFileUtilities2.getCellValue(book.getSheet(sName.getSheetName()), xssfNameCell.getRow(), xssfNameCell.getCol()).getSecond());
            }
        }
        return choices;
    }

    private static String stripTempSuffix(String name) {
        int dotPos = name.lastIndexOf(".");
        boolean istimestamp = true;
        while (istimestamp && dotPos > 0) {
            istimestamp = false;
            int underscorePos = name.substring(0, dotPos).lastIndexOf("_");
            if (dotPos - underscorePos > 14) {
                istimestamp = true;
                for (int i = underscorePos + 1; i < dotPos; i++) {
                    if (name.charAt(i) < '0' || name.charAt(i) > '9') {
                        istimestamp = false;
                        break;
                    }
                }
                if (istimestamp) {
                    name = name.substring(0, underscorePos) + name.substring(dotPos);
                    dotPos = name.lastIndexOf(".");
                }
            }
        }
        return name;
    }

    private static void addFileNameParametersToMap(String fileName, Map<String, String> map) {
        try {
            if (fileName != null && fileName.contains("(") && fileName.contains(")")) { // there are parameters to add.
                String parseString = fileName.substring(fileName.indexOf("(") + 1, fileName.indexOf(")"));
                StringTokenizer stringTokenizer = new StringTokenizer(parseString, ";");
                while (stringTokenizer.hasMoreTokens()) {
                    String pair = stringTokenizer.nextToken().trim();
                    if (pair.contains("=")) {
                        String left = pair.substring(0, pair.indexOf("=")).trim();
                        String right = pair.substring(pair.indexOf("=") + 1).trim();
                        map.put(left.toLowerCase(), right);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Can't parse file name parameters correctly");
            e.printStackTrace();
        }
    }

}