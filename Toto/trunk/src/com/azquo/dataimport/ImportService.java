package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportExecutor;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.csvreader.CsvWriter;
import org.apache.commons.io.FileUtils;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.format.CellDateFormatter;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.poi.ss.util.CellReference;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.CellData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 13/12/13.
 * <p>
 * Split and then refactored by EFC - does pre processing such as unzipping and extracting CSVs from sheets before being sent to the DB server.
 * <p>
 * Lists of UploadedFile contain both info required to upload a file and the results of an upload.
 * <p>
 * Deals with reports, set definitions and data files. Report schedules and users are now dealt with in dedicated classes.
 *
 * Uploading of reports is going to stay in here for the mo as they will likely be packaged in zip files along with data.
 */

public final class ImportService {

    public static final String dbPath = "/databases/";
    public static final String onlineReportsDir = "/onlinereports/";
    public static String LOCALIP = "127.0.0.1";

    /* external entry point, moves the file to a temp directory in case pre processing is required
    (decompress or sheets in a book to individual csv files before sending to the db server).
     After that a little housekeeping.
     */
    public static List<UploadedFile> importTheFile(final LoggedInUser loggedInUser, final UploadedFile uploadedFile) throws Exception { // setup just to flag it
        InputStream uploadFile = new FileInputStream(uploadedFile.getPath());
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }
        String fileName = uploadedFile.getFileName();

        // perhaps could make slightly cleaner API calls
        File tempFile = File.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        org.apache.commons.io.IOUtils.copy(uploadFile, fos);
        fos.close();

        uploadFile.close(); // windows requires this (though windows ideally should not be used in production), perhaps not a bad idea anyway
        UploadedFile uploadedFileWithAdjustedPath = new UploadedFile(tempFile.getPath(), uploadedFile.getFileNames(), uploadedFile.getParameters(), false);
        List<UploadedFile> processedUploadedFiles = checkForCompressionAndImport(loggedInUser, uploadedFileWithAdjustedPath);
        // persist on the database server
        SpreadsheetService.databasePersist(loggedInUser);
        // add to the uploaded list on the Manage Databases page
        // now jamming the import feedback in the comments

        UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId()
                , uploadedFile.getFileName() + (processedUploadedFiles.size() == 1 &&  processedUploadedFiles.get(0).getReportName() != null ? " - (" + processedUploadedFiles.get(0).getReportName() + ")" : ""), "", ManageDatabasesController.formatUploadedFiles(processedUploadedFiles), uploadedFile.getPath());//should record the error? (in comment)
        UploadRecordDAO.store(uploadRecord);
        // and update the counts on the manage database page
        AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        return processedUploadedFiles;
    }

    // deals with unzipping if required - recursive in case there's a zip in a zip
    private static List<UploadedFile> checkForCompressionAndImport(final LoggedInUser loggedInUser, final UploadedFile uploadedFile) throws Exception {
        List<UploadedFile> processedUploadedFiles = new ArrayList<>();
        if (uploadedFile.getFileName().endsWith(".zip") || uploadedFile.getFileName().endsWith(".7z")) {
            ZipUtil.explode(new File(uploadedFile.getPath()));
            // after exploding the original file is replaced with a directory
            File zipDir = new File(uploadedFile.getPath());
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
                processedUploadedFiles.addAll(checkForCompressionAndImport(loggedInUser, zipEntryUploadFile));
            }
        } else { // nothing to decompress
            // simple checks in case the wrong type of file is being uploaded here - will probably be removed later
            if (uploadedFile.getFileName().startsWith(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
                uploadedFile.setError("Please upload the users file in the users tab.");
                processedUploadedFiles.add(uploadedFile);
            } else if (uploadedFile.getFileName().equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
                uploadedFile.setError("Please upload the report schedules file in the schedules tab.");
                processedUploadedFiles.add(uploadedFile);
            } else if (uploadedFile.getFileName().contains(".xls")) {
                processedUploadedFiles.addAll(readBook(loggedInUser, uploadedFile));
            } else {
                processedUploadedFiles.add(readPreparedFile(loggedInUser, uploadedFile));
            }
        }
        return processedUploadedFiles;
    }

    // a book will be a report to upload or a workbook which has to be converted into a csv for each sheet
    private static List<UploadedFile> readBook(LoggedInUser loggedInUser, UploadedFile uploadedFile) throws Exception {
        long time = System.currentTimeMillis();
        Workbook book;
        // we now use apache POI which is faster than ZK but it has different implementations for .xls and .xlsx files
        try {
            FileInputStream fs = new FileInputStream(new File(uploadedFile.getPath()));
            if (uploadedFile.getFileName().endsWith("xlsx")) {
                OPCPackage opcPackage = OPCPackage.open(fs);
                book = new XSSFWorkbook(opcPackage);
            } else {
                book = new HSSFWorkbook(fs);
            }
        } catch (Exception e) {
            e.printStackTrace();
            uploadedFile.setError(e.getMessage());
            return Collections.singletonList(uploadedFile);
        }
        String reportName = null;
        /*EFC note - this import template stuff relates to extracting data from exel sheets which might not be arranged
        as standard columns. As of 22/10/18 it is parked as the code that actually uses these settings is commented
        the code needs to be sorted (it doesn't work with POI) and commented or deleted

        Further comments on this below
         */
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
            CellReference sheetNameCell = BookUtils.getNameCell(reportRange);
            if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                String identityCell = null;
                if (isImportTemplate) {
                    //identity cell in Excel format
                    identityCell = "" + (char) (sheetNameCell.getCol() + 65) + (sheetNameCell.getRow() + 1);
                }
                // ---- upload the report (pushing a function back into here)
                uploadedFile.setDataModified(true); // ok so it's not technically data modified but the file has been processed correctly. The report menu will have been modified
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
                // ---- end report uploading
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
        // with more than one sheet to convert this is why the function returns a list
        List<UploadedFile> toReturn = new ArrayList<>();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            UploadedFile uf = readSheet(loggedInUser, uploadedFile, sheet /*, knownValues*/);
            uf.addToProcessingDuration(sheetExcelLoadTimeShare);
            toReturn.add(uf);
        }
        return toReturn;
    }

    /*

    To import standardised workbooks

    A business has a whole lot of data in a given format which is NOT in a format which slots easily into a converted csv
    with headings/columns. It will have named ranges.

    To make Azquo understand this we upload one of these files with a named range AZIMPORTNAME which is the report name
    there will be other ranges tagged in that file - pairs of data and column headings currently

    This is used to extract data regions to files. How to detect this without annoying code.

    I shall park this for the moment - I think a generic solution to this is premature.

     */

    /*

    private static void rangeToCSV(Sheet sheet, CellRegion region, Map<String, String> knownNames, CsvWriter csvW) throws Exception {
        for (int rNo = region.getRow(); rNo < region.getRow() + region.getRowCount(); rNo++) {
            SRow row = sheet.getInternalSheet().getRow(rNo);
            if (row != null) {
                //System.out.println("Excel row " + r);
                //int colCount = 0;
                for (int cNo = region.getColumn(); cNo < region.getColumn() + region.getColumnCount(); cNo++) {
                    String val = getCellValue(sheet, rNo, cNo).getSecond();
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
        try {
            /*
            List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(loggedInUser.getDatabase().getId());
            for (OnlineReport report : reports) {
                String cell = report.getIdentityCell();
                if (cell != null && cell.length() > 0) {
                    int row = Integer.parseInt(cell.substring(1)) - 1;
                    int col = cell.charAt(0) - 65;
                    if (getCellValue(sheet, row).getCell( col).getSecond().equals(report.getReportName())) {
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
                                knownValues.put(name.getName(), getCellValue(sheet, rowNo, colNo).getSecond());
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

            File temp = File.createTempFile(uploadedFile.getPath() + sheetName, ".csv");
            String tempPath = temp.getPath();
            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempPath);
            CsvWriter csvW = new CsvWriter(fos, '\t', Charset.forName("UTF-8"));
            csvW.setUseTextQualifier(false);
            // poi convert - notably the iterators skip blank rows and cells hence the checking that indexes match
            int rowIndex = -1;
            for (Row row : sheet) {
                // turns out blank lines are important
                if (++rowIndex != row.getRowNum()) {
                    while (rowIndex != row.getRowNum()) {
                        csvW.endRecord();
                        rowIndex++;
                    }
                }
                int cellIndex = -1;
                for (Iterator<Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                    Cell cell = ri.next();
                    if (++cellIndex != cell.getColumnIndex()) {
                        while (cellIndex != cell.getColumnIndex()) {
                            csvW.write("");
                            cellIndex++;
                        }
                    }
                    final String cellValue = getCellValue(cell);
                    csvW.write(cellValue.replace("\n", "\\\\n").replace("\r", "")
                            .replace("\t", "\\\\t"));
                }
                csvW.endRecord();
            }
            csvW.close();
            fos.close();

            long convertTime = System.currentTimeMillis() - time;
            List<String> names = new ArrayList<>(uploadedFile.getFileNames());
            names.add(sheetName);
            // the sheet name might have parameters - try to get them
            Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
            addFileNameParametersToMap(sheetName, fileNameParams);
            UploadedFile fileFromWorksheet = new UploadedFile(tempPath, names, fileNameParams, true); // true, it IS converted from a worksheet
            UploadedFile toReturn = readPreparedFile(loggedInUser, fileFromWorksheet);
            // the UploadedFile will have the database server processing time, add the Excel stuff to it for better feedback to the user
            toReturn.addToProcessingDuration(convertTime);
            return toReturn;
        } catch (Exception e) {
            e.printStackTrace();
            uploadedFile.setError(e.getMessage());
            return uploadedFile;
        }
    }

    // copy the file to the database server if it's on a different physical machine then tell the database server to process it
    private static UploadedFile readPreparedFile(final LoggedInUser loggedInUser, UploadedFile uploadedFile) throws Exception {
        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        UploadedFile adjustedUploadedFile = null;
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            adjustedUploadedFile = new UploadedFile(SFTPUtilities.copyFileToDatabaseServer(new FileInputStream(uploadedFile.getPath()), databaseServer.getSftpUrl())
                    , uploadedFile.getFileNames(), uploadedFile.getParameters(), uploadedFile.isConvertedFromWorksheet());
        }
        UploadedFile processedFile = RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken
                , adjustedUploadedFile != null ? adjustedUploadedFile : uploadedFile
                , loggedInUser.getUser().getName());
        // run any executes defined in the file
        if (processedFile.getExecute() != null) {
            processedFile.setExecute(ReportExecutor.runExecute(loggedInUser, processedFile.getExecute()).toString());
        }
        return processedFile;
    }

    /* for parsing parameters out of a file name. Cumulative if files in zips or sheets in workbooks
     have parameters as well as their "parent" file */
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

    private static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyy-MM-dd");

    private static DataFormatter df = new DataFormatter();

    // POI Version, used when converting sheets to csv. Essentially get a value of the cell as either an unformatted number or as a string similar to how it
    // is rendered in Excel, Some hacking to standardise date formats and remove escape characters

    private static String getCellValue(Cell cell) {
        String returnString = "";
        //if (colCount++ > 0) bw.write('\t');
        if (cell.getCellType() == Cell.CELL_TYPE_STRING || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_STRING)) {
            try {
                returnString = cell.getStringCellValue();// I assume means formatted text?
            } catch (Exception ignored) {
            }
        } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_NUMERIC)) {
            // first we try to get it without locale - better match on built in formats it seems
            String dataFormat = BuiltinFormats.getBuiltinFormat(cell.getCellStyle().getDataFormat());
            if (dataFormat == null){
                dataFormat = cell.getCellStyle().getDataFormatString();
            }
            Double returnNumber = cell.getNumericCellValue();
            returnString = returnNumber.toString();
            if (returnString.contains("E")) {
                returnString = String.format("%f", returnNumber);
            }
            if (returnNumber%1 == 0) {
                // specific condition - integer and format all 000, then actually use the format. For zip codes
                if (dataFormat.contains("0") && dataFormat.replace("0","").isEmpty()){
                    returnString = df.formatCellValue(cell);
                } else {
                    returnString = returnNumber.longValue() + "";
                }
            }
            if (dataFormat.equals("h:mm") && returnString.length() == 4) {
                //ZK BUG - reads "hh:mm" as "h:mm"
                returnString = "0" + returnString;
            } else {
                if (dataFormat.toLowerCase().contains("m")) {
                    if (dataFormat.length() > 6){
                        try {
                            returnString = YYYYMMDD.format(cell.getDateCellValue());
                        } catch (Exception e) {
                            //not sure what to do here.
                        }
                    } else { // it's still a date - match the defauilt format
                        // this seems to be required as if the date is based off another cell then the normal formatter will return the formula
                        CellDateFormatter cdf = new CellDateFormatter(dataFormat, Locale.UK);
                        returnString = cdf.format(cell.getDateCellValue());
                    }
                }
            }
        } else if (cell.getCellType() == Cell.CELL_TYPE_BOOLEAN || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_BOOLEAN)) {
            returnString = cell.getBooleanCellValue() + "";
        } else if (cell.getCellType() != Cell.CELL_TYPE_BLANK) {
            if (cell.getCellType() == Cell.CELL_TYPE_FORMULA) {
                System.out.println("other forumla cell type : " + cell.getCachedFormulaResultType());
            }
            System.out.println("other cell type : " + cell.getCellType());
        }
        if (returnString.contains("\"\"") && returnString.startsWith("\"") && returnString.endsWith("\"")) {
            //remove spurious quote marks
            returnString = returnString.substring(1, returnString.length() - 1).replace("\"\"", "\"");
        }
        if (returnString.startsWith("`") && returnString.indexOf("`", 1) < 0) {
            returnString = returnString.substring(1);
        }
        if (returnString.startsWith("'") && returnString.indexOf("'", 1) < 0)
            returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
        return returnString.trim();
    }

    // ZK version of the above - still used by the "download a report, edit and upload it" functionality. Hopefully removed later.

    public static TypedPair<Double, String> getCellValue(org.zkoss.zss.api.model.Sheet sheet, int r, int c) {
        Double returnNumber = null;
        String returnString = null;
        Range range = Ranges.range(sheet, r, c);
        CellData cellData = range.getCellData();
        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
        //if (colCount++ > 0) bw.write('\t');
        if (cellData != null) {
            String stringValue = "";
            try {
                stringValue = cellData.getFormatText();// I assume means formatted text
                if (dataFormat.equals("h:mm") && stringValue.length() == 4) {
                    //ZK BUG - reads "hh:mm" as "h:mm"
                    stringValue = "0" + stringValue;
                } else {
                    if (dataFormat.toLowerCase().contains("m") && dataFormat.length() > 6) {
                        try {
                            Date javaDate = DateUtil.getJavaDate((cellData.getDoubleValue()));
                            stringValue = YYYYMMDD.format(javaDate);
                        } catch (Exception e) {
                            //not sure what to do here.
                        }
                    }
                }
                if ((stringValue.length() == 6 || stringValue.length() == 8) && stringValue.charAt(3) == ' ' && dataFormat.toLowerCase().contains("mm-")) {//another ZK bug
                    stringValue = stringValue.replace(" ", "-");//crude replacement of spaces in dates with dashes
                }
            } catch (Exception ignored) {
            }
            if (stringValue.endsWith("%") || (stringValue.contains(".") || !stringValue.startsWith("0")) && !dataFormat.toLowerCase().contains("m")) {//check that it is not a date or a time
                //if it's a number, remove all formatting
                try {
                    double d = cellData.getDoubleValue();
                    returnNumber = d;
                    String newStringValue = d + "";
                    if (newStringValue.contains("E")) {
                        newStringValue = String.format("%f", d);
                    }
                    if (newStringValue.endsWith(".0")) {
                        stringValue = newStringValue.substring(0, newStringValue.length() - 2);
                    } else {
                        if (!newStringValue.endsWith(".000000")) {
                            stringValue = newStringValue;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (stringValue.contains("\"\"") && stringValue.startsWith("\"") && stringValue.endsWith("\"")) {
                //remove spuriouse quote marks
                stringValue = stringValue.substring(1, stringValue.length() - 1).replace("\"\"", "\"");
            }
            returnString = stringValue;
        }
        assert returnString != null;
        if (returnString.startsWith("`") && returnString.indexOf("'", 1) < 0) {
            returnString = returnString.substring(1);
        }
        if (returnString.startsWith("'") && returnString.indexOf("'", 1) < 0)
            returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
        return new TypedPair<>(returnNumber, returnString.trim());
    }
}