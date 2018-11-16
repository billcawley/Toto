package com.azquo.dataimport;

import com.azquo.StringUtils;
import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.StringLiterals;
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
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;
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
 * Split and then refactored by EFC - does pre processing such as unzipping and extracting CSVs from sheets before being sent to the DB server.
 */

public final class ImportService {
    public static final String dbPath = "/databases/";
    public static final String onlineReportsDir = "/onlinereports/";
    public static final String databaseSetupSheetsDir = "/databasesetupsheets/";

    // just convenience
    public static String importTheFile(final LoggedInUser loggedInUser, final String fileName, final String filePath) throws Exception {
        return importTheFile(loggedInUser, fileName, filePath, null, true, new AtomicBoolean());
    }

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    public static String importTheFile(final LoggedInUser loggedInUser, final String fileName, final String filePath
            , final Map<String, String> inheritedFileNameParams
            , final boolean isLast, final AtomicBoolean dataChanged) throws Exception { // setup just to flag it
        Map<String, String> fileNameParams = inheritedFileNameParams != null ? new HashMap<>(inheritedFileNameParams) : new HashMap<>(); // copy, it might be modified
        addFileNameParametersToMap(fileName, fileNameParams);
        InputStream uploadFile = new FileInputStream(filePath);
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }
        // is this overkill? Not a biggy
        File tempFile = ImportFileUtilities.tempFileWithoutDecoding(uploadFile, fileName); // ok this takes the file and moves it to a temp directory, required for unzipping - maybe only use then?
        uploadFile.close(); // windows requires this (though windows ideally should not be used in production), perhaps not a bad idea anyway
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
                return f1.getName().compareTo(f2.getName());
            });
            StringBuilder sb = new StringBuilder();
            Iterator<File> fileIterator = files.iterator();
            while (fileIterator.hasNext()) {
                File f = fileIterator.next();
                if (f.getName().endsWith(".zip")) {
                    // added fileNameParams will be sorted internally - recursive call
                    sb.append(importTheFile(loggedInUser, f.getName(), f.getPath(), fileNameParams, !fileIterator.hasNext(), dataChanged));
                } else {
                    Map<String, String> mapCopy = new HashMap<>(fileNameParams); // must copy as the map might get changed by each file in the zip
                    addFileNameParametersToMap(f.getName(), mapCopy);
                    if (fileIterator.hasNext()) {
                        sb.append(readBookOrFile(loggedInUser, f.getName(), mapCopy, f.getPath(), false, dataChanged));
                    } else {
                        sb.append(fileName).append(": ").append(readBookOrFile(loggedInUser, f.getName(), mapCopy, f.getPath(), isLast, dataChanged)); // persist on the last one
                    }
                }
            }
            toReturn = sb.toString();
        } else { // vanilla
            toReturn = readBookOrFile(loggedInUser, fileName, fileNameParams, tempFile.getPath(), true, dataChanged);
        }
        // hacky way to get the report name so it can be seen on the list. I wonder if this should be removed . . .
        String reportName = null;
        if (!filePath.contains(".zip") && !filePath.contains(".7z") && toReturn.startsWith("Report uploaded : ")) {
            reportName = toReturn.substring("Report uploaded : ".length());
        }
        UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName + (reportName != null ? " - (" + reportName + ")" : ""), "", "", filePath);//should record the error? (in comment)
        UploadRecordDAO.store(uploadRecord);
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

    // as it says, for when a user has downloaded a report, modified data, then uploaded
    // uses ZK as it will have to render a copy, third public entry point into the class
    public static String uploadDataInReport(LoggedInUser loggedInUser, String tempPath) throws Exception{
        Book book;
        try {
            book = Importers.getImporter().imports(new File(tempPath), "Imported");
        } catch (Exception e) {
            e.printStackTrace();
            return "Import error - " + e.getMessage();
        }
        String reportName = null;
        SName reportRange = book.getInternalBook().getNameByName(ReportRenderer.AZREPORTNAME);
        if (reportRange == null) {
            reportRange = book.getInternalBook().getNameByName(ReportRenderer.AZIMPORTNAME);
        }
        if (reportRange != null) {
            reportName = BookUtils.getSnameCell(reportRange).getStringValue().trim();
        }
        if (reportName != null) {
            OnlineReport or = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName);
            Map<String, String> choices = uploadChoices(book);
            for (Map.Entry<String, String> choiceAndValue : choices.entrySet()) {
                SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceAndValue.getKey(), choiceAndValue.getValue());
            }
            checkEditableSets(book, loggedInUser);
            final Book reportBook = Importers.getImporter().imports(new File(tempPath), "Report name");
            reportBook.getInternalBook().setAttribute(OnlineController.BOOK_PATH, tempPath);
            reportBook.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
            reportBook.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
            // this REALLY should have been commented - the load before will populate the logged in users sent cells correctly, that's
            ReportRenderer.populateBook(reportBook, 0, false);
            return fillDataRangesFromCopy(loggedInUser, book, or);
        }
        return "file doesn't appear to be an Azquo report";
    }

    // EFC note - I don't like this, want to remove it. Public entry point

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

    private static String readBookOrFile(final LoggedInUser loggedInUser, final String fileName, final Map<String, String> fileNameParameters
            , final String filePath, final boolean persistAfter, final AtomicBoolean dataChanged) throws Exception {
        String toReturn;
        if (fileName.startsWith(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
            return "Please upload the users file in the users tab.";
        } else if (fileName.equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
            return "Please upload the report schedules file in the schedules tab.";
        } else if (fileName.contains(".xls")) { // normal. I'm not entirely sure the code for users etc above should be in this file, maybe a different importer?
            toReturn = readBook(loggedInUser, fileName, fileNameParameters, filePath, persistAfter, dataChanged);
        } else {
            toReturn = readPreparedFile(loggedInUser, filePath, fileName, "", fileNameParameters, persistAfter, false, dataChanged);
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

    private static String readBook(LoggedInUser loggedInUser, final String fileName, final Map<String, String> fileNameParameters
            , final String tempPath, boolean persistAfter, AtomicBoolean dataChanged) throws Exception {
        Workbook book;
        try {
            long time = System.currentTimeMillis();
            FileInputStream fs = new FileInputStream(new File(tempPath));
            if (fileName.endsWith("xlsx")) {
                OPCPackage opcPackage = OPCPackage.open(fs);
                book = new XSSFWorkbook(opcPackage);
            } else {
                book = new HSSFWorkbook(fs);
            }
            //book = Importers.getImporter().imports(new File(tempPath), "Imported");
            System.out.println("millis to read an Excel file for import new way" +
                    " " + (System.currentTimeMillis() - time));
        } catch (Exception e) {
            e.printStackTrace();
            return fileName + ": Import error - " + e.getMessage();
        }
        String reportName = null;
        boolean isImportTemplate = false;
        Name reportRange = BookUtils.getName(book, ReportRenderer.AZREPORTNAME);
        if (reportRange == null) {
            reportRange = book.getName(ReportRenderer.AZIMPORTNAME);
            isImportTemplate = true;
        }
        if (reportRange != null) {
            CellReference sheetNameCell = BookUtils.getNameCell(reportRange);
            reportName = book.getSheet(reportRange.getSheetName()).getRow(sheetNameCell.getRow()).getCell(sheetNameCell.getCol()).getStringCellValue();
        }
        if (reportName != null) {
            // todo sort duplicates later
            CellReference sheetNameCell = BookUtils.getNameCell(reportRange);
            if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                String identityCell = null;
                if (isImportTemplate) {
                    //identity cell in Excel format
                    identityCell = "" + (char) (sheetNameCell.getCol() + 65) + (sheetNameCell.getRow() + 1);
                }
                uploadReport(loggedInUser, tempPath, fileName, reportName, identityCell, dataChanged);
                if (isImportTemplate) {
                    return "Import uploaded : " + reportName;
                }
                return "Report uploaded : " + reportName;
            }
        }
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("no database set");
        }
        StringBuilder toReturn = new StringBuilder();
//        Map<String, String> knownValues = new HashMap<>();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            toReturn.append(readSheet(loggedInUser, fileName, fileNameParameters, sheet, tempPath/*, knownValues*/, sheetNo == book.getNumberOfSheets() - 1 && persistAfter, dataChanged)); // that last conditional means persist on the last one through (if we've been told to persist)
            toReturn.append("\n");
        }
        return toReturn.toString();
    }

    private static void checkEditableSets(Book book, LoggedInUser loggedInUser) {
        for (SName sName : book.getInternalBook().getNames()) {
            if (sName.getName().toLowerCase().startsWith(ReportRenderer.AZROWHEADINGS)) {
                String region = sName.getName().substring(ReportRenderer.AZROWHEADINGS.length());
                org.zkoss.zss.api.model.Sheet sheet = book.getSheet(sName.getRefersToSheetName());
                String rowHeading = ImportFileUtilities.getCellValue(sheet, sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getSecond();
                if (rowHeading.toLowerCase().endsWith(" children editable")) {
                    String setName = rowHeading.substring(0, rowHeading.length() - " children editable".length()).replace("`", "");
                    SName displayName = getNameByName(ReportRenderer.AZDISPLAYROWHEADINGS + region, sheet);
                    if (displayName != null) {
                        StringBuilder editLine = new StringBuilder();
                        editLine.append("edit:saveset ");
                        editLine.append("`").append(setName).append("` ");
                        CellRegion dispRegion = displayName.getRefersToCellRegion();
                        for (int rowNo = 0; rowNo < dispRegion.getRowCount(); rowNo++) {
                            editLine.append("`").append(ImportFileUtilities.getCellValue(sheet, dispRegion.getRow() + rowNo, dispRegion.getColumn()).getSecond()).append("`,");
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
                    String val = ImportFileUtilities.getCellValue(sheet, rNo, cNo).getSecond();
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


    private static String readSheet(LoggedInUser loggedInUser, String fileName, Map<String, String> fileNameParameters, Sheet sheet, final String tempFileName/*, Map<String, String> knownValues*/, boolean persistAfter, AtomicBoolean dataChanged) {
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
                    if (ImportFileUtilities.getCellValue(sheet, row).getCell( col).getSecond().equals(report.getReportName())) {
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
                                knownValues.put(name.getName(), ImportFileUtilities.getCellValue(sheet, rowNo, colNo).getSecond());
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
            ImportFileUtilities.convertRangeToCSV(sheet, csvW);
            csvW.close();
            fos.close();
            return fileName + ": " + readPreparedFile(loggedInUser, tempPath, fileName, sheetName, fileNameParameters, persistAfter, true, dataChanged);
        } catch (Exception e) {
            e.printStackTrace();
            return "\n" + fileName + ": " + e.getMessage();
        }
    }

    private static String LOCALIP = "127.0.0.1";

    // todo - address whether that last variable is the right way to do things!
    private static String readPreparedFile(final LoggedInUser loggedInUser, String filePath
            , final String fileName, final String fileSource, final Map<String, String> fileNameParameters, final boolean persistAfter
            , final boolean isSpreadsheet, final AtomicBoolean dataChanged) throws Exception {
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            filePath = ImportFileUtilities.copyFileToDatabaseServer(new FileInputStream(filePath), databaseServer.getSftpUrl());
        }
        //String s = "";
        String s = RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken, filePath, fileName, fileSource, fileNameParameters, loggedInUser.getUser().getName(), persistAfter, isSpreadsheet);
        if (s.startsWith(StringLiterals.DATABASE_UNMODIFIED)) {
            s = s.substring(StringLiterals.DATABASE_UNMODIFIED.length());
        } else {
            dataChanged.set(true);
        }
        return s;
    }

    // for the download, modify and upload the report
    // todo - can we convert to apache poi?
    private static String fillDataRangesFromCopy(LoggedInUser loggedInUser, Book sourceBook, OnlineReport onlineReport) {
        StringBuilder errorMessage = new StringBuilder();
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
                            errorMessage.append("- in region ").append(regionName).append(" -").append(result);
                        } else {
                            try {
                                saveCount += Integer.parseInt(result.substring(5));  //count follows the word 'true'
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMessage.append("- in region ").append(regionName).append(" -").append(e.getMessage());
                    }
                }
            }
        }
        return errorMessage + " - " + saveCount + " data items amended successfully";
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

    private static Map<String, String> uploadChoices(Book book) {
        //this routine extracts the useful information from an uploaded copy of a report.  The report will then be loaded and this information inserted.
        Map<String, String> choices = new HashMap<>();
        for (SName sName : book.getInternalBook().getNames()) {
            String rangeName = sName.getName().toLowerCase();
            if (rangeName.endsWith("chosen")) {
                //there is probably a more elegant solution than this....
                choices.put(rangeName.substring(0, rangeName.length() - 6), ImportFileUtilities.getCellValue(book.getSheet(sName.getRefersToSheetName()), sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getSecond());
            }
        }
        return choices;
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