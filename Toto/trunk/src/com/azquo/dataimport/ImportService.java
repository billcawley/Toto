package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.transport.UploadedFile;
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
import org.zkoss.poi.ss.util.CellReference;
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
import java.time.LocalDateTime;
import java.util.*;

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

    // will persist after
    public static List<UploadedFile> importTheFile(final LoggedInUser loggedInUser, final UploadedFile uploadedFile) throws Exception { // setup just to flag it
        List<UploadedFile> processedUploadedFiles = importTheFileNoPersist(loggedInUser, uploadedFile);
        SpreadsheetService.databasePersist(loggedInUser);
        return processedUploadedFiles;
    }

    // deals with pre processing of the uploaded file before calling readPreparedFile which in turn calls the main functions
    private static List<UploadedFile> importTheFileNoPersist(final LoggedInUser loggedInUser, final UploadedFile uploadedFile) throws Exception {
        InputStream uploadFile = new FileInputStream(uploadedFile.getPath());
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }
        String fileName = uploadedFile.getFileName();
        // is this overkill? Not a biggy
        File tempFile = ImportFileUtilities.tempFileWithoutDecoding(uploadFile, fileName); // ok this takes the file and moves it to a temp directory, required for unzipping - maybe only use then?
        uploadFile.close(); // windows requires this (though windows ideally should not be used in production), perhaps not a bad idea anyway
        String toReturn;
        UploadedFile uploadedFileWithAdjustedPath = new UploadedFile(tempFile.getPath(), uploadedFile.getFileNames(), uploadedFile.getParameters(), false);
        List<UploadedFile> processedUploadedFiles = new ArrayList<>();
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
            for (File f : files) {
                // need new upload file object now!
                List<String> names = new ArrayList<>(uploadedFile.getFileNames());
                names.add(f.getName());
                Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
                addFileNameParametersToMap(f.getName(), fileNameParams);
                UploadedFile zipEntryUploadFile = new UploadedFile(f.getPath(), names, fileNameParams, false);
                if (f.getName().endsWith(".zip") || f.getName().endsWith(".7z")) {
                    // this is the only recursive call . . .
                    processedUploadedFiles.addAll(importTheFileNoPersist(loggedInUser, zipEntryUploadFile));
                } else {
                    processedUploadedFiles.addAll(readBookOrFile(loggedInUser, zipEntryUploadFile));
                }
            }
        } else { // vanilla
            processedUploadedFiles.addAll(readBookOrFile(loggedInUser, uploadedFileWithAdjustedPath));
        }
        UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId(), fileName + (uploadedFile.getReportName() != null ? " - (" + uploadedFile.getReportName() + ")" : ""), "", "", uploadedFile.getPath());//should record the error? (in comment)
        UploadRecordDAO.store(uploadRecord);
        for (UploadedFile uf : processedUploadedFiles) {
            if (uf.getExecute() != null && !uf.isExecuted()) {
                uf.setExecute(ReportExecutor.runExecute(loggedInUser, uf.getExecute()).toString());
                uf.setExecuted(true);
            }
        }
        AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        return processedUploadedFiles;
    }

    // as it says, for when a user has downloaded a report, modified data, then uploaded
    // uses ZK as it will have to render a copy, third public entry point into the class
    public static String uploadDataInReport(LoggedInUser loggedInUser, String tempPath) throws Exception {
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

    private static List<UploadedFile> readBookOrFile(final LoggedInUser loggedInUser, UploadedFile uploadedFile) throws Exception {
        // going to zap these checks later I think
        if (uploadedFile.getFileName().startsWith(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
            uploadedFile.setError("Please upload the users file in the users tab.");
            return Collections.singletonList(uploadedFile);
        } else if (uploadedFile.getFileName().equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
            uploadedFile.setError("Please upload the report schedules file in the schedules tab.");
            return Collections.singletonList(uploadedFile);
        } else if (uploadedFile.getFileName().contains(".xls")) { // normal. I'm not entirely sure the code for users etc above should be in this file, maybe a different importer?
            return readBook(loggedInUser, uploadedFile);
        } else {
            return Collections.singletonList(readPreparedFile(loggedInUser, uploadedFile));
        }
    }

    private static void uploadReport(LoggedInUser loggedInUser, UploadedFile uploadedFile, String reportName, String identityCell) throws Exception {
        uploadedFile.setDataModified(true); // I'm not 100% sure this is correct
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
            or.setFilename(uploadedFile.getFileName()); // it might have changed, I don't think much else under these circumstances
            or.setIdentityCell(identityCell);
        } else {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), loggedInUser.getDatabase().getName(), reportName, uploadedFile.getFileName(), "", identityCell); // default to ZK now
        }
        OnlineReportDAO.store(or); // store before or.getFilenameForDisk() or the id will be wrong!
        Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk());
        Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
        Files.copy(Paths.get(uploadedFile.getPath()), fullPath); // and copy
        DatabaseReportLinkDAO.link(databaseId, or.getId());
    }

    private static List<UploadedFile> readBook(LoggedInUser loggedInUser, UploadedFile uploadedFile) throws Exception {
        long time = System.currentTimeMillis();
        Workbook book;
        try {
            FileInputStream fs = new FileInputStream(new File(uploadedFile.getPath()));
            if (uploadedFile.getFileName().endsWith("xlsx")) {
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
            uploadedFile.setError(e.getMessage());
            return Collections.singletonList(uploadedFile);
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
                uploadReport(loggedInUser, uploadedFile, reportName, identityCell);
                uploadedFile.setReportName(reportName);
                uploadedFile.setProcessingDuration(System.currentTimeMillis() - time);
                return Collections.singletonList(uploadedFile);
/*                if (isImportTemplate) {
                    return "Import uploaded : " + reportName;
                }
                return "Report uploaded : " + reportName;*/
            }
        }
        if (loggedInUser.getDatabase() == null) {
            uploadedFile.setError("no database set");
            return Collections.singletonList(uploadedFile);
        }
//        Map<String, String> knownValues = new HashMap<>();
        // add a share of the initial excel load time to each of the sheet convert and processing times
        long sheetExcelLoadTimeShare = (System.currentTimeMillis() - time) / book.getNumberOfSheets();
        // with sheets to convert this is why the function returns a list
        List<UploadedFile> toReturn = new ArrayList<>();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            UploadedFile uf = readSheet(loggedInUser, uploadedFile, sheet /*, knownValues*/);
            uf.addToProcessingDuration(sheetExcelLoadTimeShare);
            toReturn.add(uf);
        }
        return toReturn;
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


    private static UploadedFile readSheet(LoggedInUser loggedInUser, UploadedFile uploadedFile, Sheet sheet/*, Map<String, String> knownValues*/) {
        String sheetName = sheet.getSheetName();
        long time = System.currentTimeMillis();
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
                    }
                }
            }*/

            // I think it is path we want here
            File temp = File.createTempFile(uploadedFile.getPath() + sheetName, ".csv");
            String tempPath = temp.getPath();
            temp.deleteOnExit();
            //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(tempName), "UTF-8"));
            FileOutputStream fos = new FileOutputStream(tempPath);
            CsvWriter csvW = new CsvWriter(fos, '\t', Charset.forName("UTF-8"));
            csvW.setUseTextQualifier(false);
            ImportFileUtilities.convertRangeToCSV(sheet, csvW);
            csvW.close();
            fos.close();

            long convertTime = System.currentTimeMillis() - time;
            List<String> names = new ArrayList<>(uploadedFile.getFileNames());
            names.add(sheetName);
            Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
            addFileNameParametersToMap(sheetName, fileNameParams);
            UploadedFile fileFromWorksheet = new UploadedFile(tempPath, names, fileNameParams, true); // true, it IS converted from a worksheet
            UploadedFile toReturn = readPreparedFile(loggedInUser, fileFromWorksheet);
            toReturn.addToProcessingDuration(convertTime);
            return toReturn;
        } catch (Exception e) {
            e.printStackTrace();
            uploadedFile.setError(e.getMessage());
            return uploadedFile;
        }
    }

    private static String LOCALIP = "127.0.0.1";

    private static UploadedFile readPreparedFile(final LoggedInUser loggedInUser, UploadedFile uploadedFile) throws Exception {
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        UploadedFile adjustedUploadedFile = null;
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            adjustedUploadedFile = new UploadedFile(ImportFileUtilities.copyFileToDatabaseServer(new FileInputStream(uploadedFile.getPath()), databaseServer.getSftpUrl())
                    , uploadedFile.getFileNames(), uploadedFile.getParameters(), uploadedFile.isConvertedFromWorksheet());
        }
        return RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken
                , adjustedUploadedFile != null ? adjustedUploadedFile : uploadedFile
                , loggedInUser.getUser().getName());
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


    public static void addFileNameParametersToMap(String fileName, Map<String, String> map) {
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

    public static String formatUploadedFiles(List<UploadedFile> uploadedFiles) {
        StringBuilder toReturn = new StringBuilder();
        List<String> lastNames = null;
        for (UploadedFile uploadedFile : uploadedFiles) {
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            int indent = 0;
            if (lastNames != null && names.size() == lastNames.size()) { // for formatting don't repeat names (e.g. the zip name)
                for (int index = 0; index < lastNames.size(); index++) {
                    if (lastNames.get(index).equals(names.get(index))) {
                        indent++;
                    }
                }
            }
            for (int i = 0; i < indent; i++) {
                toReturn.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
            }
            for (int index = indent; index < names.size(); index++) {
                toReturn.append(names.get(index));
                if ((index + 1) != names.size()) {
                    toReturn.append("\n");
                    for (int i = 0; i <= index; i++) {
                        toReturn.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    }
                }
            }
            StringBuilder indentSb = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                indentSb.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
            }
            // jump it out past the end for actual info
            toReturn.append("\n");

            if (uploadedFile.getParameters() != null && !uploadedFile.getParameters().isEmpty()) {
                for (String key : uploadedFile.getParameters().keySet()) {
                    toReturn.append(indentSb);
                    toReturn.append(key).append(" = ").append(uploadedFile.getParameters().keySet() + "\n");
                }
            }

            if (uploadedFile.isConvertedFromWorksheet()) {
                toReturn.append(indentSb);
                toReturn.append("Converted from worksheet.\n");
            }

            toReturn.append(indentSb);
            toReturn.append("Time to process : " + uploadedFile.getProcessingDuration()  + " ms\n");

            toReturn.append(indentSb);
            toReturn.append("Number of lines imported : " + uploadedFile.getNoLinesImported()  + "\n");

            toReturn.append(indentSb);
            toReturn.append("Number of valuses adjusted: " + uploadedFile.getNoValuesAdjusted()  + "\n");

            if (uploadedFile.getLinesRejected() != null && !uploadedFile.getLinesRejected().isEmpty()){
                toReturn.append(indentSb);
                toReturn.append("Rejected lines : " + uploadedFile.getNoValuesAdjusted()  + "\n");
                for (String lineRejected : uploadedFile.getLinesRejected()){
                    toReturn.append(indentSb);
                    toReturn.append(lineRejected + "\n");
                }
            }

            if (uploadedFile.getError() != null){
                toReturn.append(indentSb);
                toReturn.append("ERROR : " + uploadedFile.getError()  + "\n");
            }

            if (!uploadedFile.isDataModified()) {
                toReturn.append(indentSb);
                toReturn.append("NO DATA MODIFIED.\n");
            }

            if (uploadedFile.getReportName() != null) {
                toReturn.append(indentSb);
                toReturn.append("Report uploaded : " + uploadedFile.getReportName() + "\n");
            }

            if (uploadedFile.getExecute() != null) {
                toReturn.append(indentSb);
                toReturn.append("Execute result : " + uploadedFile.getExecute() + "\n");
            }


            lastNames = uploadedFile.getFileNames();
        }
        return toReturn.toString();
    }
}